<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { streamChat } from './services/chat'
import { http } from './services/http'
import { API_BASE_URL } from './services/http'
import { auth, initAuth, logout as authLogout } from './store/auth'
import LoginView from './components/LoginView.vue'
import RegisterView from './components/RegisterView.vue'
import ForgotPasswordView from './components/ForgotPasswordView.vue'
import ProfileView from './components/ProfileView.vue'

// ---------------- 页面/会话持久化（刷新后停留在原页面） ----------------
const VALID_VIEWS = ['home', 'login', 'register', 'forgot', 'profile', 'chat']
const VALID_APPS = ['love', 'manus']

// 解析 URL hash：支持 #/home、#/chat/love、#/chat/manus 等
function parseHash() {
  const raw = window.location.hash.replace(/^#\/?/, '')
  const [v, a] = raw.split('/').filter(Boolean)
  if (v === 'chat') {
    const appId = VALID_APPS.includes(a) ? a : null
    if (appId) return { view: 'chat', app: appId }
    return { view: 'home', app: null }
  }
  if (VALID_VIEWS.includes(v)) return { view: v, app: null }
  return { view: 'home', app: null }
}

function createChatId() {
  return typeof crypto?.randomUUID === 'function'
    ? crypto.randomUUID()
    : `chat-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`
}

// ---------------- 多会话管理（localStorage 持久化，刷新/关闭浏览器后保留） ----------------
// 结构：{ love: { current: 会话ID, list: [会话] }, manus: { current, list: [会话] } }
// 会话：{ id, chatId(仅恋爱大师), title, draft, messages[], createdAt, updatedAt }
const CONVERSATIONS_KEY = 'cz_ai_conversations'
const MAX_CONVERSATIONS_PER_APP = 30
const MAX_MESSAGES_PER_CONVERSATION = 200

function normalizeAppState(st) {
  if (!st || typeof st !== 'object') return { current: null, list: [] }
  return {
    current: st.current || null,
    list: Array.isArray(st.list)
      ? st.list.map((c) => ({
          id: c.id || createChatId(),
          chatId: c.chatId || '',
          title: c.title || '新对话',
          draft: c.draft || '',
          messages: Array.isArray(c.messages) ? c.messages : [],
          createdAt: c.createdAt || Date.now(),
          updatedAt: c.updatedAt || Date.now(),
        }))
      : [],
  }
}

function loadConversationsState() {
  try {
    const raw = localStorage.getItem(CONVERSATIONS_KEY)
    if (raw) {
      const data = JSON.parse(raw)
      return {
        love: normalizeAppState(data.love),
        manus: normalizeAppState(data.manus),
      }
    }
  } catch {
    // 存储解析失败时使用空状态
  }
  return { love: { current: null, list: [] }, manus: { current: null, list: [] } }
}

function saveConversations() {
  try {
    localStorage.setItem(CONVERSATIONS_KEY, JSON.stringify(conversationsState.value))
  } catch {
    // 存储不可用时忽略（隐私模式等）
  }
}

function trimConversations(appId) {
  const st = conversationsState.value[appId]
  if (!st) return
  if (st.list.length > MAX_CONVERSATIONS_PER_APP) {
    st.list.length = MAX_CONVERSATIONS_PER_APP
  }
  for (const conv of st.list) {
    if (conv.messages.length > MAX_MESSAGES_PER_CONVERSATION) {
      conv.messages = conv.messages.slice(-MAX_MESSAGES_PER_CONVERSATION)
    }
  }
}

const initial = parseHash()
const view = ref(initial.view) // home | login | register | forgot | profile | chat
const currentApp = ref(initial.app)
const conversationsState = ref(loadConversationsState())
const draft = ref('')
const isStreaming = ref(false)
const chatBody = ref(null)
const activeAssistantMessageIndex = ref(null)
const pendingStepContent = ref('')
const humanQuestion = ref(null)
const humanAnswer = ref('')
const isSubmittingHumanAnswer = ref(false)
const humanCountdown = ref(0)
const convPanelOpen = ref(false) // 移动端会话列表抽屉开关
const deleteTarget = ref(null) // 待删除的会话（非空时显示确认弹框）

// 当前 AI 应用下的会话列表与选中会话
const appConversations = computed(() => conversationsState.value[currentApp.value]?.list || [])
const currentConversationId = computed(() => conversationsState.value[currentApp.value]?.current ?? null)
const currentConversation = computed(
  () => appConversations.value.find((c) => c.id === currentConversationId.value) || appConversations.value[0] || null,
)
// 当前会话的消息数组（模板与业务代码通过 messages 读写）
const messages = computed(() => currentConversation.value?.messages || [])

// 确保当前 AI 应用下有一个可用的会话（优先继续最近会话，没有则新建）
function ensureConversation(appId) {
  const st = conversationsState.value[appId]
  if (!st) {
    conversationsState.value[appId] = { current: null, list: [] }
  }
  const cur = conversationsState.value[appId].list.find((c) => c.id === conversationsState.value[appId].current)
    || conversationsState.value[appId].list[0]
  if (cur) {
    conversationsState.value[appId].current = cur.id
    return cur
  }
  return newConversation(appId)
}

// 新建一个空白对话并切换过去
function newConversation(appId) {
  const st = conversationsState.value[appId] || { current: null, list: [] }
  const conv = {
    id: createChatId(),
    chatId: appId === 'love' ? createChatId() : '',
    title: '新对话',
    draft: '',
    messages: [],
    createdAt: Date.now(),
    updatedAt: Date.now(),
  }
  st.list.unshift(conv)
  st.current = conv.id
  conversationsState.value[appId] = st
  trimConversations(appId)
  saveConversations()
  return conv
}

// 切换到指定会话
function selectConversation(id) {
  if (isStreaming.value) return // 流式进行中不切换，避免消息写到错误会话
  const conv = appConversations.value.find((c) => c.id === id)
  if (!conv) return
  conversationsState.value[currentApp.value].current = id
  resetChatRuntimeState()
  draft.value = conv.draft || ''
  convPanelOpen.value = false
  saveConversations()
  scrollToBottom()
}

// 新建对话（供 ＋ 图标调用）
function startNewConversation() {
  if (isStreaming.value) return
  newConversation(currentApp.value)
  resetChatRuntimeState()
  convPanelOpen.value = false
  scrollToBottom()
}

// 请求删除会话：打开自定义确认弹框（不使用浏览器原生 confirm）
function deleteConversation(id) {
  if (isStreaming.value) return
  if (appConversations.value.length <= 1) return // 至少保留一个对话
  const conv = appConversations.value.find((c) => c.id === id)
  if (!conv) return
  deleteTarget.value = conv
}

// 取消删除
function cancelDeleteConversation() {
  deleteTarget.value = null
}

// 确认删除会话
function confirmDeleteConversation() {
  const target = deleteTarget.value
  deleteTarget.value = null
  if (!target) return
  const st = conversationsState.value[currentApp.value]
  if (!st) return
  if (st.list.length <= 1) return // 至少保留一个对话（兜底保护）
  const idx = st.list.findIndex((c) => c.id === target.id)
  if (idx === -1) return
  st.list.splice(idx, 1)
  if (st.current === target.id) {
    st.current = st.list[0]?.id || null
    resetChatRuntimeState()
    draft.value = st.list[0]?.draft || ''
    if (st.list[0]) scrollToBottom()
  }
  saveConversations()
}

// 重置与当前会话绑定的运行时状态（流式、提问弹窗等）
function resetChatRuntimeState() {
  draft.value = ''
  isStreaming.value = false
  activeAssistantMessageIndex.value = null
  pendingStepContent.value = ''
  humanQuestion.value = null
  humanAnswer.value = ''
  clearHumanTimeout()
}

// 会话标题：取第一条用户消息前 18 个字符
function ensureConversationTitle(conv, content) {
  if (!conv.title || conv.title === '新对话') {
    const text = content.replace(/\s+/g, ' ').trim()
    conv.title = text ? (text.length > 18 ? `${text.slice(0, 18)}…` : text) : '新对话'
  }
}

function formatConvTime(ts) {
  if (!ts) return ''
  const d = new Date(ts)
  const now = new Date()
  if (d.toDateString() === now.toDateString()) return d.toTimeString().slice(0, 5)
  return `${d.getMonth() + 1}/${d.getDate()}`
}

// 与后端 HumanInteractionService.ANSWER_TIMEOUT_SECONDS（180 秒）保持一致：
// 提问弹窗出现后若人类一直未回复，到时自动关闭并降级展示说明
const HUMAN_ANSWER_TIMEOUT_MS = 180_000
let humanTimeoutTimer = null
let humanCountdownTimer = null

const apps = [
  { id: 'love', icon: '♡', avatar: '恋', name: 'AI 恋爱大师', description: '懂你心意，也懂如何把话说得恰到好处。', color: 'rose', tag: 'RELATIONSHIP GUIDE' },
  { id: 'manus', icon: '✦', avatar: '智', name: 'AI 超级智能体', description: '把复杂的问题，转化为清晰的行动方案。', color: 'violet', tag: 'AUTONOMOUS AGENT' },
]
const app = computed(() => apps.find((item) => item.id === currentApp.value))
const userDisplayName = computed(() => auth.user?.userName || auth.user?.userAccount || '未登录')
const userInitial = computed(() => (auth.user?.userName || auth.user?.userAccount || 'U').slice(0, 1).toUpperCase())
const userAvatarUrl = computed(() => {
  const url = auth.user?.userAvatar
  if (!url) return ''
  const base = url.startsWith('http') ? url : `${API_BASE_URL}${url}`
  // 追加时间戳，绕过后端 7 天强缓存：主页/聊天页头像能及时展示最新上传的头像
  return `${base}${base.includes('?') ? '&' : '?'}v=${Date.now()}`
})
// 等待后端响应时显示“正在深度思考中……”气泡：
// - 超级智能体：流式进行中且尚未产出任何正文（工具执行/思考间隙，且非等待人工提问）
// - 恋爱大师：流式进行中且尚未收到第一个响应分片
const thinking = computed(() => {
  if (!isStreaming.value || humanQuestion.value) return false
  if (currentApp.value === 'manus') return !pendingStepContent.value
  if (currentApp.value === 'love') return activeAssistantMessageIndex.value === null
  return false
})
const welcome = computed(() => currentApp.value === 'love'
  ? '你好，我是你的恋爱军师。今天有什么心事想和我聊聊？'
  : '你好，我是 AI 超级智能体。告诉我你的目标，我会协助你一步步完成。')

watch(currentApp, (appId) => {
  const selectedApp = apps.find((item) => item.id === appId)
  const title = selectedApp ? `${selectedApp.name} · CZ AI 工作台` : 'CZ AI 工作台 · 探索你的智能伙伴'
  const description = selectedApp
    ? `与 ${selectedApp.name} 实时对话，获得清晰、可靠的 AI 协助。`
    : 'CZ AI 工作台：选择你的 AI 伙伴，开始一段实时、专属的智能对话。'
  document.title = title
  document.querySelector('meta[name="description"]')?.setAttribute('content', description)
})

// 视图变化时同步 URL hash（支持刷新停留与浏览器前进/后退）
watch([view, currentApp], () => {
  const appId = view.value === 'chat' ? currentApp.value : null
  const target = appId ? `#/chat/${appId}` : `#/${view.value}`
  if (window.location.hash !== target) {
    window.location.hash = target
  }
})

// 会话/草稿变化时持久化到 localStorage（消息为原地 push，需深度监听）
watch(
  [conversationsState, draft],
  () => {
    const conv = currentConversation.value
    if (conv) conv.draft = draft.value
    saveConversations()
  },
  { deep: true },
)

onMounted(async () => {
  window.addEventListener('hashchange', onHashChange)
  await initAuth()
  window.addEventListener('auth:unauthorized', handleUnauthorized)
  // 刷新后恢复：进入聊天页时确保有一个可用的会话
  if (view.value === 'chat') {
    const conv = ensureConversation(currentApp.value)
    draft.value = conv.draft || ''
    scrollToBottom()
  }
  // 刷新恢复后的登录态守卫：未登录不允许停留在超级智能体/个人中心
  if (!auth.user && (view.value === 'profile' || (view.value === 'chat' && currentApp.value === 'manus'))) {
    view.value = 'login'
  }
})

onUnmounted(() => {
  window.removeEventListener('auth:unauthorized', handleUnauthorized)
  window.removeEventListener('hashchange', onHashChange)
})

// 浏览器前进/后退时根据 hash 切换页面
function onHashChange() {
  const parsed = parseHash()
  if (parsed.view === view.value && parsed.app === currentApp.value) return
  view.value = parsed.view
  currentApp.value = parsed.app
  if (parsed.view === 'chat') {
    const conv = ensureConversation(parsed.app)
    resetChatRuntimeState()
    draft.value = conv.draft || ''
    scrollToBottom()
  }
}

function handleUnauthorized() {
  clearHumanTimeout()
  auth.user = null
  if (view.value === 'chat' || view.value === 'profile') {
    view.value = 'login'
  }
}

function openApp(id) {
  if (id === 'manus' && !auth.user) {
    view.value = 'login'
    return
  }
  currentApp.value = id
  resetChatRuntimeState()
  // 打开应用时继续最近一个会话（无会话则自动新建）
  const conv = ensureConversation(id)
  draft.value = conv.draft || ''
  view.value = 'chat'
  scrollToBottom()
}

function backHome() {
  view.value = 'home'
  currentApp.value = null
  resetChatRuntimeState()
}

async function handleLogout() {
  await authLogout()
  backHome()
}

async function send() {
  if (currentApp.value === 'manus' && !auth.user) {
    view.value = 'login'
    return
  }
  const conv = currentConversation.value
  const content = draft.value.trim()
  if (!content || isStreaming.value || !conv) return
  draft.value = ''
  ensureConversationTitle(conv, content)
  conv.messages.push({ role: 'user', content })
  conv.updatedAt = Date.now()
  activeAssistantMessageIndex.value = null
  pendingStepContent.value = ''
  isStreaming.value = true
  await scrollToBottom()

  try {
    await streamChat(currentApp.value, content, conv.chatId, ({ event, data }) => {
      if (currentApp.value === 'love') {
        if (activeAssistantMessageIndex.value === null) {
          conv.messages.push({ role: 'assistant', content: '' })
          activeAssistantMessageIndex.value = conv.messages.length - 1
        }
        conv.messages[activeAssistantMessageIndex.value].content += data
        scrollToBottom()
        return
      }

      if (event === 'human_question') {
        try {
          humanQuestion.value = JSON.parse(data)
          humanAnswer.value = ''
          startHumanTimeout()
        } catch {
          pendingStepContent.value += '\nAI 需要补充信息，但提问内容解析失败。'
        }
      } else if (event === 'assistant_message') {
        pendingStepContent.value += `${pendingStepContent.value ? '\n\n' : ''}${data}`
      } else if (/^Step\s+\d+\s*:/.test(data.trim())) {
        const contentForStep = [pendingStepContent.value, data]
          .filter(Boolean)
          .join(currentApp.value === 'manus' ? '\n\n\n' : '\n\n')
        conv.messages.push({ role: 'assistant', content: contentForStep })
        activeAssistantMessageIndex.value = conv.messages.length - 1
        pendingStepContent.value = ''
        scrollToBottom()
      } else {
        conv.messages.push({ role: 'assistant', content: data })
        activeAssistantMessageIndex.value = conv.messages.length - 1
        scrollToBottom()
      }
    })
    if (pendingStepContent.value) {
      conv.messages.push({ role: 'assistant', content: pendingStepContent.value })
      activeAssistantMessageIndex.value = conv.messages.length - 1
      pendingStepContent.value = ''
    } else if (activeAssistantMessageIndex.value === null) {
      conv.messages.push({ role: 'assistant', content: '本次对话没有返回内容，请稍后重试。' })
    }
  } catch (error) {
    conv.messages.push({ role: 'assistant', content: `抱歉，连接服务时出现问题：${error.message}` })
  } finally {
    isStreaming.value = false
    if (conv) conv.updatedAt = Date.now()
    await scrollToBottom()
  }
}

async function submitHumanAnswer() {
  const answer = humanAnswer.value.trim()
  if (!answer || !humanQuestion.value || isSubmittingHumanAnswer.value) return
  isSubmittingHumanAnswer.value = true
  try {
    messages.value.push({ role: 'user', content: `补充信息：${answer}` })
    await scrollToBottom()
    await http.post('/ai/manus/human-answer', {
      requestId: humanQuestion.value.requestId,
      answer,
    })
    humanQuestion.value = null
    humanAnswer.value = ''
    clearHumanTimeout()
    await scrollToBottom()
  } catch (error) {
    messages.value.push({ role: 'assistant', content: `提交补充信息失败：${error.message}` })
  } finally {
    isSubmittingHumanAnswer.value = false
  }
}

// 启动提问超时计时：到时自动关闭弹窗，并推送降级说明气泡
function startHumanTimeout() {
  clearHumanTimeout()
  humanCountdown.value = Math.round(HUMAN_ANSWER_TIMEOUT_MS / 1000)
  humanCountdownTimer = setInterval(() => {
    humanCountdown.value--
    if (humanCountdown.value <= 0) {
      clearInterval(humanCountdownTimer)
      humanCountdownTimer = null
    }
  }, 1000)
  humanTimeoutTimer = setTimeout(() => {
    humanTimeoutTimer = null
    if (humanCountdownTimer) {
      clearInterval(humanCountdownTimer)
      humanCountdownTimer = null
    }
    if (humanQuestion.value) {
      const question = humanQuestion.value.question || ''
      humanQuestion.value = null
      humanAnswer.value = ''
      messages.value.push({
        role: 'assistant',
        content: `待确认问题：${question}\n\n由于该问题人类并没有给出相关回复，我将基于自己的理解进行思考......`,
      })
      activeAssistantMessageIndex.value = messages.value.length - 1
      scrollToBottom()
    }
  }, HUMAN_ANSWER_TIMEOUT_MS)
}

function clearHumanTimeout() {
  if (humanTimeoutTimer) {
    clearTimeout(humanTimeoutTimer)
    humanTimeoutTimer = null
  }
  if (humanCountdownTimer) {
    clearInterval(humanCountdownTimer)
    humanCountdownTimer = null
  }
  humanCountdown.value = 0
}

async function scrollToBottom() {
  await nextTick()
  if (chatBody.value) chatBody.value.scrollTop = chatBody.value.scrollHeight
}
</script>

<template>
  <!-- 全局动态背景：彗星日落霞光（主页 / 聊天 / 登录注册 / 个人中心共用） -->
  <video
    class="app-bg-video"
    autoplay
    muted
    loop
    playsinline
    webkit-playsinline
    preload="auto"
    poster="/background/comet-poster.webp"
    aria-hidden="true"
    tabindex="-1"
  >
    <source src="/background/comet.mp4" type="video/mp4" />
  </video>
  <div class="app-bg-overlay" aria-hidden="true"></div>

  <!-- 启动加载：登录态恢复完成前不渲染业务页面，避免刷新时闪现“未登录” -->
  <main v-if="!auth.ready" class="shell app-home">
    <section class="home">
      <div class="space-field" aria-hidden="true"><i v-for="n in 42" :key="n"></i></div>
      <div class="aurora-field" aria-hidden="true">
        <span class="aurora aurora-a"></span>
        <span class="aurora aurora-b"></span>
        <span class="aurora aurora-c"></span>
      </div>
      <div class="diag-grid" aria-hidden="true"></div>
      <div class="home-copy loading-copy">
        <p class="eyebrow">PERSONAL INTELLIGENCE, EXPANDED</p>
        <h1>正在加载工作台<span class="load-dots"><i>.</i><i>.</i><i>.</i></span></h1>
      </div>
    </section>
  </main>

  <!-- 首页 -->
  <main v-else-if="view === 'home'" class="shell app-home">
    <section class="home">
      <div class="space-field" aria-hidden="true"><i v-for="n in 42" :key="n"></i></div>
      <div class="aurora-field" aria-hidden="true">
        <span class="aurora aurora-a"></span>
        <span class="aurora aurora-b"></span>
        <span class="aurora aurora-c"></span>
      </div>
      <div class="diag-grid" aria-hidden="true"></div>
      <header class="site-nav">
        <div class="brand"><span class="brand-mark">✦</span><span>CZ AI</span><small>WORKSPACE</small></div>
        <div class="nav-user">
          <template v-if="auth.user">
            <button class="nav-chip" @click="view = 'profile'">
              <span class="nav-chip-avatar">
                <img v-if="userAvatarUrl" :src="userAvatarUrl" alt="" />
                <span v-else>{{ userInitial }}</span>
              </span>
              <span>{{ userDisplayName }}</span>
              <em v-if="auth.user.userRole !== 'user'">{{ auth.user.userRole }}</em>
            </button>
            <button class="nav-link" @click="handleLogout">退出</button>
          </template>
          <template v-else>
            <button class="nav-link" @click="view = 'login'">登录</button>
            <button class="nav-link primary" @click="view = 'register'">注册</button>
          </template>
        </div>
      </header>
      <div class="hero">
        <p class="eyebrow">PERSONAL INTELLIGENCE, EXPANDED</p>
        <h1>驶入未知，<em>与星辰同频。</em></h1>
        <p class="hero-sub">{{ auth.user ? `欢迎回来，${userDisplayName}。选择一位 AI 伙伴，让灵感、情感与行动在此刻汇聚。` : '游客可直接体验「AI 恋爱大师」，登录后解锁「AI 超级智能体」。' }}</p>
        <div class="hero-cta">
          <button class="btn-primary" @click="openApp('love')"><span>开始对话</span><b>→</b></button>
          <button v-if="!auth.user" class="btn-ghost" @click="view = 'register'">创建账号</button>
          <button v-else class="btn-ghost" @click="view = 'profile'">个人中心</button>
        </div>
      </div>
      <div class="app-grid">
        <button v-for="item in apps" :key="item.id" class="app-card" :class="item.color" @click="openApp(item.id)">
          <span class="card-slice" aria-hidden="true"></span>
          <span class="card-orbit"></span><span class="card-icon">{{ item.icon }}</span>
          <span class="card-content">
            <em>{{ item.tag }}</em><strong>{{ item.name }}</strong><small>{{ item.description }}</small>
            <small v-if="item.id === 'manus' && !auth.user" class="card-lock">✦ 登录后可用</small>
          </span>
          <span class="card-arrow">↗</span>
        </button>
      </div>
      <div class="feature-strip">
        <div class="feature"><b>实时流式</b><span>逐字响应，对话如临其境</span></div>
        <div class="feature"><b>多会话管理</b><span>历史记录与草稿自动保存</span></div>
        <div class="feature"><b>双 AI 伙伴</b><span>恋爱军师 · 超级智能体</span></div>
        <div class="feature"><b>账号体系</b><span>邮箱验证 · 数据安全可靠</span></div>
      </div>
      <footer class="site-footer"><span>© {{ new Date().getFullYear() }} CZ AI WORKSPACE</span><span>智能对话 · 探索无限可能</span></footer>
    </section>
  </main>

  <!-- 登录 / 注册 -->
  <LoginView v-else-if="view === 'login'" @success="view = 'home'" @switch-register="view = 'register'" @switch-forgot="view = 'forgot'" @back="view = 'home'" />
  <RegisterView v-else-if="view === 'register'" @success="view = 'home'" @switch-login="view = 'login'" @back="view = 'home'" />
  <ForgotPasswordView v-else-if="view === 'forgot'" @switch-login="view = 'login'" @back="view = 'login'" />

  <!-- 个人中心 -->
  <ProfileView v-else-if="view === 'profile'" @back="view = 'home'" @logout="view = 'home'" />

  <!-- 聊天 -->
  <main v-else-if="view === 'chat'" class="shell" :class="`app-${currentApp}`">
    <section class="chat-page">
      <!-- 会话列表侧边栏（移动端为抽屉） -->
      <div v-if="convPanelOpen" class="conv-backdrop" @click="convPanelOpen = false"></div>
      <aside class="conv-sidebar" :class="{ open: convPanelOpen }">
        <div class="conv-sidebar-head">
          <span>对话</span>
          <button class="conv-new" title="创建新对话" @click="startNewConversation">＋</button>
        </div>
        <div class="conv-list">
          <button
            v-for="conv in appConversations"
            :key="conv.id"
            class="conv-item"
            :class="{ active: conv.id === currentConversationId }"
            :title="conv.title"
            @click="selectConversation(conv.id)"
          >
            <span v-if="appConversations.length > 1" class="conv-del" title="删除对话" @click.stop="deleteConversation(conv.id)">×</span>
            <span class="conv-title">{{ conv.title }}</span>
            <span class="conv-meta">{{ conv.messages.length }} 条 · {{ formatConvTime(conv.updatedAt) }}</span>
          </button>
          <p v-if="!appConversations.length" class="conv-empty">暂无对话，点击上方 ＋ 创建</p>
        </div>
      </aside>

      <!-- 主聊天区 -->
      <div class="chat-main">
        <header class="chat-header">
          <button class="back" aria-label="返回首页" @click="backHome">←</button>
          <button class="conv-toggle" aria-label="对话列表" @click="convPanelOpen = true">☰</button>
          <div class="chat-title"><span :class="['small-icon', app.color]">{{ app.avatar }}</span><div><strong>{{ app.name }}</strong><small><i></i> 在线 · 随时为你服务</small></div></div>
          <button class="chat-new-btn" title="创建新对话" @click="startNewConversation">＋</button>
          <div class="chat-user">
            <button class="nav-chip" @click="view = 'profile'">
              <span class="nav-chip-avatar">
                <img v-if="userAvatarUrl" :src="userAvatarUrl" alt="" />
                <span v-else>{{ userInitial }}</span>
              </span>
              <span>{{ userDisplayName }}</span>
            </button>
            <button class="nav-link" @click="handleLogout">退出</button>
          </div>
        </header>
        <div ref="chatBody" class="chat-body">
          <div class="intro"><span :class="['intro-icon', app.color]">{{ app.icon }}</span><p>{{ welcome }}</p><small v-if="currentApp === 'love'">会话 ID：{{ currentConversation?.chatId }}</small></div>
          <article v-for="(message, index) in messages" :key="index" class="message" :class="message.role">
            <span class="avatar" :class="message.role === 'assistant' ? app.color : ''">{{ message.role === 'user' ? '我' : app.avatar }}</span>
            <div class="bubble">{{ message.content }}<i v-if="message.role === 'assistant' && isStreaming && index === messages.length - 1" class="cursor"></i></div>
          </article>
          <article v-if="thinking" class="message assistant">
            <span class="avatar" :class="app.color">{{ app.avatar }}</span>
            <div class="bubble thinking-bubble">
              <span>正在深度思考中……</span>
              <span class="thinking-dots"><b></b><b></b><b></b></span>
            </div>
          </article>
        </div>
        <form class="composer" @submit.prevent="send">
          <textarea v-model="draft" rows="1" placeholder="输入你的问题…" @keydown.enter.exact.prevent="send"></textarea>
          <button type="submit" :disabled="!draft.trim() || isStreaming" aria-label="发送消息">↑</button>
        </form>
        <p class="hint">Enter 发送 · Shift + Enter 换行</p>
        <footer class="chat-footer">© {{ new Date().getFullYear() }} CZ AI WORKSPACE · 智能对话服务</footer>
      </div>

      <!-- 人工提问弹窗 -->
      <div v-if="humanQuestion" class="human-dialog-mask" role="dialog" aria-modal="true" aria-labelledby="human-question-title">
        <form class="human-dialog" @submit.prevent="submitHumanAnswer">
          <span class="dialog-icon">?</span>
          <p class="dialog-overline">AI 需要你的协助</p>
          <h2 id="human-question-title">{{ humanQuestion.question }}</h2>
          <textarea v-model="humanAnswer" rows="3" autofocus placeholder="请输入你的回答…"></textarea>
          <p class="dialog-timeout">等待回复中… {{ humanCountdown }}s 后自动继续</p>
          <button type="submit" :disabled="!humanAnswer.trim() || isSubmittingHumanAnswer">
            {{ isSubmittingHumanAnswer ? '正在提交…' : '提交回答 →' }}
          </button>
        </form>
      </div>

      <!-- 删除对话确认弹框 -->
      <div v-if="deleteTarget" class="human-dialog-mask" role="dialog" aria-modal="true" aria-labelledby="delete-dialog-title">
        <div class="human-dialog delete-dialog">
          <span class="dialog-icon">!</span>
          <p class="dialog-overline">删除对话</p>
          <h2 id="delete-dialog-title">确定删除「{{ deleteTarget.title }}」吗？删除后不可恢复。</h2>
          <div class="delete-dialog-actions">
            <button type="button" class="delete-cancel" @click="cancelDeleteConversation">取消</button>
            <button type="button" class="delete-confirm" @click="confirmDeleteConversation">删除</button>
          </div>
        </div>
      </div>
    </section>
  </main>
</template>
