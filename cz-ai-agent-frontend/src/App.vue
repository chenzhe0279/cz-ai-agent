<script setup>
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
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
const VALID_APPS = ['love', 'manus', 'rag']

// 解析 URL hash：支持 #/home、#/chat/love、#/chat/manus、#/chat/rag 等
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
// 结构：{ love: { current: 会话ID, list: [会话] }, manus: { current, list: [会话] }, rag: { current, list: [会话] } }
// 会话：{ id, chatId(智能助手/AI 情感专家), title, draft, messages[], createdAt, updatedAt }
const CONVERSATIONS_KEY = 'cz_ai_conversations'
const RAG_STATUS_KEY = 'cz_ai_rag_status'
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
        rag: normalizeAppState(data.rag),
      }
    }
  } catch {
    // 存储解析失败时使用空状态
  }
  return { love: { current: null, list: [] }, manus: { current: null, list: [] }, rag: { current: null, list: [] } }
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
let savedRagStatus = '单身'
try {
  savedRagStatus = localStorage.getItem(RAG_STATUS_KEY) || '单身'
} catch {
  // 存储不可用时使用默认状态
}
const ragStatus = ref(savedRagStatus)
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

// ---------------- 对话活动轨道（Conversation Activity Rail / 轮次标记条） ----------------
// 规则：一个气泡 = 一个标记（用户问题、智能体的每一个 step 气泡都各占一根短线）；
// 悬停高亮并预览该气泡内容，点击平滑滚动（弹性波浪缓动）跳转并短暂高亮。
const turns = computed(() => {
  return messages.value.map((msg, index) => ({
    start: index,
    end: index,
    user: msg.role === 'user' ? msg.content : '',
    reply: msg.role === 'assistant' ? msg.content : '',
  }))
})

const hoverTurnIndex = ref(-1)
const activeTurnIndex = ref(-1)
const highlightTurnIndex = ref(-1)
const markerScales = reactive([]) // 波浪缩放：仅局部更新（高斯半径内），避免每帧重写全部标记样式
let highlightTimer = null
let activeScrollRaf = null
let scrollAnimRaf = null
let scrollAnimating = false // 滚动动画进行中：跳过 active 轮次的全量定位，动画结束后统一补一次
let waveFrom = -1 // 上一帧波浪更新范围，先还原再重算，只触碰半径内的标记
let waveTo = -1
const WAVE_RADIUS = 6 // 高斯波浪只作用于该半径内的标记，其余保持 1
let cachedMessageEls = [] // 消息 DOM 元素缓存（与 turns 索引一一对应），避免滚动/动画期间反复 querySelector

// 弹簧物理跟随（Spring-physics follow）：高亮位置以弹簧模拟跟随鼠标，
// 快速上下移动时产生滞后、过冲回弹的连锁波浪效果
const RAIL_STIFFNESS = 220
const RAIL_DAMPING = 16
const RAIL_FADE_ZONE = 64 // 轨道顶部渐隐区高度（px）：此区域内越靠上越透明
let railTarget = 0
let railFollow = 0
let railVelocity = 0
let railSpringRaf = null
let railHovering = false

function onRailMouseMove(e) {
  const rail = turnRailEl.value
  const n = turns.value.length
  if (!rail || n === 0) return
  const rect = rail.getBoundingClientRect()
  const y = e.clientY - rect.top
  if (!railHovering) {
    railHovering = true
    railFollow = nearestMarkerIndex(y)
    railVelocity = 0
  }
  railTarget = nearestMarkerIndex(y)
  if (!railSpringRaf) {
    railSpringRaf = requestAnimationFrame(tickRailSpring)
  }
}

function onRailMouseLeave() {
  railHovering = false
  if (railSpringRaf) {
    cancelAnimationFrame(railSpringRaf)
    railSpringRaf = null
  }
  railVelocity = 0
  hoverTurnIndex.value = -1
  const n = turns.value.length
  markerScales.length = n
  for (let i = 0; i < n; i++) markerScales[i] = 1
  waveFrom = -1
  waveTo = -1
}

// 返回与鼠标位置最近（线性插值）的标记索引，0..n-1 的浮点数
function nearestMarkerIndex(y) {
  const n = turns.value.length
  const positions = turnPositions.value
  if (n <= 1) return 0
  if (y <= positions[0]) return 0
  if (y >= positions[n - 1]) return n - 1
  for (let i = 0; i < n - 1; i++) {
    if (y >= positions[i] && y <= positions[i + 1]) {
      const span = Math.max(1, positions[i + 1] - positions[i])
      return i + (y - positions[i]) / span
    }
  }
  return n - 1
}

function tickRailSpring() {
  railSpringRaf = null
  const n = turns.value.length
  if (!n) return
  const dt = 0.016
  railVelocity += (railTarget - railFollow) * RAIL_STIFFNESS * dt
  railVelocity *= Math.max(0, 1 - RAIL_DAMPING * dt)
  railFollow += railVelocity * dt
  if (Math.abs(railTarget - railFollow) < 0.005 && Math.abs(railVelocity) < 0.02) {
    railFollow = railTarget
    railVelocity = 0
  }
  const hovered = Math.round(railFollow)
  hoverTurnIndex.value = hovered >= 0 && hovered < n ? hovered : -1
  // 以跟随位置为中心的高斯波浪轮廓：只更新半径内的标记，其余还原为 1，
  // 避免每帧重写全部标记样式（历史很长时显著降低渲染开销）
  if (waveFrom >= 0 && waveFrom < n) {
    for (let i = waveFrom; i <= waveTo && i < n; i++) {
      if (markerScales[i] !== 1) markerScales[i] = 1
    }
  }
  const center = Math.max(0, Math.min(n - 1, hovered))
  waveFrom = Math.max(0, center - WAVE_RADIUS)
  waveTo = Math.min(n - 1, center + WAVE_RADIUS)
  for (let i = waveFrom; i <= waveTo; i++) {
    const d = Math.abs(i - railFollow)
    markerScales[i] = 1 + 0.55 * Math.exp(-(d * d) / 2.2)
  }
  if (railHovering && (Math.abs(railTarget - railFollow) > 0.005 || Math.abs(railVelocity) > 0.02)) {
    railSpringRaf = requestAnimationFrame(tickRailSpring)
  }
}

// 标记样式：位置 + 弹簧波浪缩放 + 顶部渐隐（早期标记接近上边界时逐渐透明）
function markerStyle(idx) {
  const top = turnPositions.value[idx] ?? 0
  const scale = markerScales[idx] ?? 1
  const fade = Math.min(1, Math.max(0, (top - railPaddingTop.value) / RAIL_FADE_ZONE))
  return { top: `${top}px`, transform: `scaleX(${scale})`, opacity: fade }
}

// 鼠标滚轮在轨道上滚动时，滚动聊天消息区（限制在聊天框范围内）
function onRailWheel(e) {
  const body = chatBody.value
  if (!body) return
  body.scrollTop += e.deltaY
  e.preventDefault()
}

function turnPreview(turn) {
  const text = (turn.user || turn.reply || '').replace(/\s+/g, ' ')
  return text.length > 40 ? `${text.slice(0, 40)}…` : text
}

function isMessageInHighlightedTurn(index) {
  const h = highlightTurnIndex.value
  if (h < 0 || h >= turns.value.length) return false
  const turn = turns.value[h]
  return index >= turn.start && index <= turn.end
}

function animateChatScroll(targetTop, duration = 750) {
  const body = chatBody.value
  if (!body) return
  if (scrollAnimRaf) cancelAnimationFrame(scrollAnimRaf)
  scrollAnimating = true
  // 滚动动画期间暂停弹簧波浪循环：App.vue 为单组件，波浪每帧更新会触发
  // 整个聊天区重渲染，与滚动动画抢帧导致卡顿；动画结束后若鼠标仍在轨道上再恢复
  if (railSpringRaf) {
    cancelAnimationFrame(railSpringRaf)
    railSpringRaf = null
  }
  // 临时禁用 CSS 原生平滑（.chat-body 定义了 scroll-behavior: smooth）：
  // 否则浏览器会对每帧 scrollTop 赋值重新插值，把我们的缓动稀释成"慢启动"，
  // 动画结束后移除内联样式，恢复 CSS 原定义
  body.style.setProperty('scroll-behavior', 'auto')
  const start = body.scrollTop
  const delta = targetTop - start
  // 每帧最大位移限制（约 1.3 屏）：保持快速起步又不阻塞主线程（此前单帧跳 3~4 屏会卡）
  const maxStep = Math.max(700, Math.round(body.clientHeight * 1.3))
  const frames = Math.max(1, Math.ceil(Math.abs(delta) / maxStep))
  const animDuration = Math.max(duration, frames * 26)
  // 时间基准提前一帧：RAF 回调的时间戳常与调度时刻几乎相同，导致首帧 p≈0 几乎不动，
  // 表现为"点击后慢启动"；偏移一帧后首帧即有明显位移
  const t0 = performance.now() - 16
  // 快速起步 + 轻微弹性过冲回弹（easeOutBack）：点击后马上冲过去，末端干脆停住
  function easeOutBack(x) {
    const c1 = 1.70158
    const c3 = c1 + 1
    return 1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2)
  }
  function step(now) {
    const p = Math.min(1, (now - t0) / animDuration)
    const target = start + delta * easeOutBack(p)
    const prev = body.scrollTop
    const gap = target - prev
    body.scrollTop = Math.abs(gap) > maxStep ? prev + Math.sign(gap) * maxStep : target
    if (p < 1) {
      scrollAnimRaf = requestAnimationFrame(step)
    } else {
      body.scrollTop = targetTop // 收尾：精确落位（此时离目标已在一屏内）
      body.style.removeProperty('scroll-behavior') // 恢复 CSS 原生平滑定义
      scrollAnimRaf = null
      scrollAnimating = false
      updateActiveTurn() // 动画结束：一次性更新当前轮次
      if (railHovering && !railSpringRaf) {
        railSpringRaf = requestAnimationFrame(tickRailSpring) // 鼠标仍在轨道上：恢复弹簧跟随
      }
    }
  }
  scrollAnimRaf = requestAnimationFrame(step)
}

function jumpToTurn(index) {
  const turn = turns.value[index]
  const body = chatBody.value
  if (!turn || !body) return
  const el = getMessageEls()[turn.start] || body.querySelector(`[data-index="${turn.start}"]`)
  if (!el) return
  const bodyRect = body.getBoundingClientRect()
  let targetTop = el.getBoundingClientRect().top - bodyRect.top + body.scrollTop
  // 钳制到有效滚动范围，避免目标位置超出容器边界
  const maxTop = Math.max(0, body.scrollHeight - body.clientHeight)
  targetTop = Math.max(0, Math.min(targetTop, maxTop))
  animateChatScroll(targetTop)
  highlightTurnIndex.value = index
  if (highlightTimer) clearTimeout(highlightTimer)
  highlightTimer = setTimeout(() => {
    highlightTurnIndex.value = -1
  }, 1500)
}

// 点击轨道任意位置（不限于细短线）时，自动吸附到最近的对话记录并跳转
function onRailClick(e) {
  // 点击短线本身时由按钮的 click 处理，避免重复触发
  if (e.target.closest('.turn-marker')) return
  const rail = turnRailEl.value
  const n = turns.value.length
  if (!rail || n === 0) return
  const rect = rail.getBoundingClientRect()
  const y = e.clientY - rect.top
  const idx = Math.round(nearestMarkerIndex(y))
  jumpToTurn(Math.max(0, Math.min(n - 1, idx)))
}

// 消息 DOM 缓存：v-for 渲染的消息与 turns 索引一一对应，滚动/跳转时复用元素引用，
// 避免每次滚动帧对全部消息做 querySelector（消息增删或切换会话时失效重建）
function getMessageEls() {
  if (!cachedMessageEls.length && chatBody.value) {
    cachedMessageEls = Array.from(chatBody.value.querySelectorAll('.message'))
  }
  return cachedMessageEls
}

function invalidateMessageEls() {
  cachedMessageEls = []
}

function updateActiveTurn() {
  const list = turns.value
  const els = getMessageEls()
  const body = chatBody.value
  if (!list.length || !els.length || !body) return
  let active = list.length - 1
  const scrollTop = body.scrollTop
  const bodyRect = body.getBoundingClientRect()
  for (let i = 0; i < list.length; i++) {
    const el = els[list[i].start]
    if (!el) continue
    const top = el.getBoundingClientRect().top - bodyRect.top + scrollTop
    if (top <= scrollTop + 140) active = i
    else break
  }
  if (active !== activeTurnIndex.value) activeTurnIndex.value = active
}

function onChatBodyScroll() {
  if (scrollAnimating) return // 滚动动画进行中：不逐帧全量定位，动画结束后统一更新一次
  if (activeScrollRaf) return
  activeScrollRaf = requestAnimationFrame(() => {
    activeScrollRaf = null
    updateActiveTurn()
  })
}

// 轨道定位：对话少时整组垂直居中；历史多了以后铺满整轨（最早的一条线在最上方，新的向下排）
const MARKER_H = 3
const MARKER_MAX_GAP = 14
const MARKER_MIN_GAP = 3
const turnRailEl = ref(null)
const railInnerHeight = ref(0)
const railPaddingTop = ref(0)
let railObserver = null

const turnPositions = computed(() => {
  const n = turns.value.length
  const h = railInnerHeight.value
  const padTop = railPaddingTop.value
  if (n === 0) return []
  const positions = new Array(n)
  if (h <= 0) {
    // 尚未测量到高度时的兜底：从上往下均分
    for (let i = 0; i < n; i++) positions[i] = padTop + (i * 100) / Math.max(1, n - 1)
    return positions
  }
  const naturalGap = (h - n * MARKER_H) / Math.max(1, n - 1)
  if (naturalGap >= MARKER_MIN_GAP) {
    // 居中状态：整组放在轨道中间，间距有上限
    const gap = Math.min(MARKER_MAX_GAP, naturalGap)
    const total = n * MARKER_H + (n - 1) * gap
    const start = Math.max(0, (h - total) / 2)
    for (let i = 0; i < n; i++) positions[i] = padTop + start + i * (MARKER_H + gap)
  } else {
    // 历史多了：铺满整轨，最早的一条线在最上方，新的向下排
    for (let i = 0; i < n; i++) positions[i] = padTop + (i * (h - MARKER_H)) / Math.max(1, n - 1)
  }
  return positions
})

function measureRail() {
  const el = turnRailEl.value
  if (!el) return
  const style = getComputedStyle(el)
  const pt = parseFloat(style.paddingTop) || 0
  const pb = parseFloat(style.paddingBottom) || 0
  railPaddingTop.value = pt
  railInnerHeight.value = Math.max(0, el.clientHeight - pt - pb)
}

function setupRail() {
  const el = turnRailEl.value
  if (!el) return
  if (railObserver) railObserver.disconnect()
  railObserver = new ResizeObserver(() => measureRail())
  railObserver.observe(el)
  measureRail()
}

// ---------------- 背景切换（三套动态背景，localStorage 持久化） ----------------
const BACKGROUNDS_KEY = 'cz_ai_background'
const BACKGROUND_AUTO_KEY = 'cz_ai_background_auto'
// 三套背景：彗星日落（视频）/ 星空流星（图片 + Ken Burns 动效）/ 彗星蓝天·你的名字（图片 + Ken Burns 动效）
const backgrounds = [
  { id: 'comet', name: '彗星日落', type: 'video', src: '/background/comet.mp4', poster: '/background/comet-poster.webp' },
  { id: 'stars', name: '星空流星', type: 'image', src: '/background/stars.webp' },
  { id: 'starry-eyes', name: '彗星蓝天', type: 'image', src: '/background/starry-eyes.webp' },
]

function loadBackgroundIndex() {
  try {
    const raw = Number(localStorage.getItem(BACKGROUNDS_KEY))
    if (Number.isInteger(raw) && raw >= 0 && raw < backgrounds.length) return raw
  } catch {
    // 存储不可用时使用默认背景
  }
  return 0
}

const bgIndex = ref(loadBackgroundIndex())
const bgAuto = ref(false)
let bgAutoTimer = null
const currentBackground = computed(() => backgrounds[bgIndex.value])

function saveBackground() {
  try {
    localStorage.setItem(BACKGROUNDS_KEY, String(bgIndex.value))
    localStorage.setItem(BACKGROUND_AUTO_KEY, bgAuto.value ? '1' : '0')
  } catch {
    // 存储不可用时忽略（隐私模式等）
  }
}

function nextBackground() {
  bgIndex.value = (bgIndex.value + 1) % backgrounds.length
  saveBackground()
}

function prevBackground() {
  bgIndex.value = (bgIndex.value - 1 + backgrounds.length) % backgrounds.length
  saveBackground()
}

function restartBackgroundAuto() {
  if (bgAutoTimer) clearInterval(bgAutoTimer)
  bgAutoTimer = null
  if (bgAuto.value) {
    bgAutoTimer = setInterval(() => {
      nextBackground()
    }, 10000)
  }
}

function toggleBackgroundAuto() {
  bgAuto.value = !bgAuto.value
  saveBackground()
  restartBackgroundAuto()
}

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
    chatId: appId === 'love' || appId === 'rag' ? createChatId() : '',
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

// 重命名会话：点击 ✎ 进入内联编辑，Enter / 失焦保存，Esc 取消
const editingConvId = ref(null)
const editingConvTitle = ref('')

function startRenameConversation(conv) {
  editingConvId.value = conv.id
  editingConvTitle.value = conv.title || ''
  nextTick(() => {
    const input = document.querySelector('.conv-rename-input')
    input?.focus()
    input?.select()
  })
}

function commitRenameConversation() {
  if (editingConvId.value == null) return
  const conv = appConversations.value.find((c) => c.id === editingConvId.value)
  if (conv) {
    const title = editingConvTitle.value.replace(/\s+/g, ' ').trim()
    if (title) {
      conv.title = title.length > 30 ? `${title.slice(0, 30)}…` : title
      conv.updatedAt = Date.now()
      saveConversations()
    }
  }
  editingConvId.value = null
  editingConvTitle.value = ''
}

function cancelRenameConversation() {
  editingConvId.value = null
  editingConvTitle.value = ''
}

// ---------------- 生成中断 / 修改提问并重新生成 ----------------
const activeAbortController = ref(null)
const streamUserIndex = ref(-1)
const editingUserIndex = ref(-1)
const editingUserText = ref('')

// 重置与当前会话绑定的运行时状态（流式、提问弹窗等）
function resetChatRuntimeState() {
  draft.value = ''
  isStreaming.value = false
  activeAssistantMessageIndex.value = null
  pendingStepContent.value = ''
  humanQuestion.value = null
  humanAnswer.value = ''
  clearHumanTimeout()
  editingConvId.value = null
  editingConvTitle.value = ''
  editingUserIndex.value = -1
  editingUserText.value = ''
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
  { id: 'love', icon: '◈', avatar: '助', name: 'AI 智能助手', description: '学习、工作、生活、编程，随时随地为你解答。', color: 'rose', tag: 'GENERAL ASSISTANT' },
  { id: 'rag', icon: '♥', avatar: '情', name: 'AI 情感专家', description: '围绕单身/恋爱/已婚各阶段的情感问题，基于知识库给出贴心解答。', color: 'sky', tag: 'EMOTION EXPERT' },
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
// 判断是否为“普通流式助手”（智能助手 / AI 情感专家）：纯文本 SSE，直接累积为一条助手消息
function isPlainStreamApp(appId) {
  return appId === 'love' || appId === 'rag'
}

// 等待后端响应时显示“正在深度思考中……”气泡：
// - 超级智能体：流式进行中且尚未产出任何正文（工具执行/思考间隙，且非等待人工提问）
// - 智能助手 / AI 情感专家：流式进行中且尚未收到第一个响应分片
const thinking = computed(() => {
  if (!isStreaming.value || humanQuestion.value) return false
  if (currentApp.value === 'manus') return !pendingStepContent.value
  if (isPlainStreamApp(currentApp.value)) return activeAssistantMessageIndex.value === null
  return false
})
const welcome = computed(() => {
  if (currentApp.value === 'love') return '你好，我是你的 AI 智能助手。有什么问题，尽管问我。'
  if (currentApp.value === 'rag') return '你好，我是 AI 情感专家。我会基于情感知识库，结合你的情感状态为你解答情感中的困惑。'
  return '你好，我是 AI 超级智能体。告诉我你的目标，我会协助你一步步完成。'
})

watch(currentApp, (appId) => {
  const selectedApp = apps.find((item) => item.id === appId)
  const title = selectedApp ? `${selectedApp.name} · CZ AI 工作台` : 'CZ AI 工作台 · 探索你的智能伙伴'
  const description = selectedApp
    ? `与 ${selectedApp.name} 实时对话，获得清晰、可靠的 AI 协助。`
    : 'CZ AI 工作台：选择你的 AI 伙伴，开始一段实时、专属的智能对话。'
  document.title = title
  document.querySelector('meta[name="description"]')?.setAttribute('content', description)
})

// 进入聊天视图后测量轨道高度并监听变化（窗口尺寸 / 轨道尺寸变化时自动重算）
watch(
  [view, currentApp],
  async () => {
    await nextTick()
    setupRail()
  },
  { flush: 'post' },
)

// 轨道元素从无到有（如新建对话后发出第一条消息）且尚未测量高度时，初始化轨道尺寸
watch(
  () => turns.value.length,
  async () => {
    if (turns.value.length > 0 && railInnerHeight.value === 0) {
      await nextTick()
      setupRail()
    }
  },
)

// 消息增删（发送/停止生成/切换会话）后 DOM 缓存失效，滚动定位时重建
watch(
  [() => messages.value.length, currentConversationId],
  () => invalidateMessageEls(),
)

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

// AI 情感专家婚恋状态选择持久化，刷新后保持
watch(ragStatus, (value) => {
  try {
    localStorage.setItem(RAG_STATUS_KEY, value)
  } catch {
    // 存储不可用时忽略
  }
})

onMounted(async () => {
  window.addEventListener('hashchange', onHashChange)
  await initAuth()
  bgAuto.value = localStorage.getItem(BACKGROUND_AUTO_KEY) === '1'
  restartBackgroundAuto()
  window.addEventListener('resize', measureRail)
  window.addEventListener('auth:unauthorized', handleUnauthorized)
  // 刷新后恢复：进入聊天页时确保有一个可用的会话
  if (view.value === 'chat') {
    const conv = ensureConversation(currentApp.value)
    draft.value = conv.draft || ''
    scrollToBottom()
    // 刷新时 view/currentApp 未变化，测量轨道的 watch 不会触发，需手动初始化
    await nextTick()
    setupRail()
  }
  // 刷新恢复后的登录态守卫：未登录不允许停留在 AI 情感专家/超级智能体/个人中心
  if (!auth.user && (view.value === 'profile' || (view.value === 'chat' && (currentApp.value === 'manus' || currentApp.value === 'rag')))) {
    view.value = 'login'
  }
})

onUnmounted(() => {
  if (bgAutoTimer) clearInterval(bgAutoTimer)
  if (highlightTimer) clearTimeout(highlightTimer)
  if (activeScrollRaf) cancelAnimationFrame(activeScrollRaf)
  if (scrollAnimRaf) cancelAnimationFrame(scrollAnimRaf)
  if (railSpringRaf) cancelAnimationFrame(railSpringRaf)
  if (railObserver) railObserver.disconnect()
  window.removeEventListener('resize', measureRail)
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
  if ((id === 'manus' || id === 'rag') && !auth.user) {
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

function send() {
  if ((currentApp.value === 'manus' || currentApp.value === 'rag') && !auth.user) {
    view.value = 'login'
    return
  }
  const content = draft.value.trim()
  if (!content || isStreaming.value || !currentConversation.value) return
  draft.value = ''
  runSend(content, -1)
}

// 中断当前 AI 生成：中止流式请求，并清掉本次生成产生的（未完成的）助手气泡，回到用户提问处
async function stopGeneration() {
  activeAbortController.value?.abort()
  const conv = currentConversation.value
  if (!conv || streamUserIndex.value < 0) return
  conv.messages = conv.messages.slice(0, streamUserIndex.value + 1)
  streamUserIndex.value = -1
  pendingStepContent.value = ''
  activeAssistantMessageIndex.value = null
  conv.updatedAt = Date.now()
  saveConversations()
  await scrollToBottom()
}

// 编辑用户提问：仅允许在未生成时进入编辑态
function startEditUserMessage(index) {
  if (isStreaming.value) return
  const msg = messages.value[index]
  if (!msg || msg.role !== 'user') return
  editingUserIndex.value = index
  editingUserText.value = msg.content
}

function cancelEditUserMessage() {
  editingUserIndex.value = -1
  editingUserText.value = ''
}

// 确认修改：若被修改的提问是"最后一条消息"（即刚发出、尚无回复），替换后自动重新生成；
// 否则仅更新历史文本
function confirmEditUserMessage() {
  const index = editingUserIndex.value
  const text = editingUserText.value.trim()
  editingUserIndex.value = -1
  editingUserText.value = ''
  if (index < 0 || !text) return
  const msg = messages.value[index]
  if (!msg || msg.role !== 'user') return
  if (index === messages.value.length - 1) {
    runSend(text, index)
  } else {
    msg.content = text
    const conv = currentConversation.value
    if (conv) {
      conv.updatedAt = Date.now()
      saveConversations()
    }
  }
}

// 统一发送/重发：replaceUserIndex >= 0 表示"修改已有提问并重新生成"
async function runSend(content, replaceUserIndex) {
  const conv = currentConversation.value
  if (!conv) return
  if (replaceUserIndex >= 0) {
    conv.messages[replaceUserIndex].content = content
    conv.messages = conv.messages.slice(0, replaceUserIndex + 1)
  } else {
    conv.messages.push({ role: 'user', content })
  }
  conv.updatedAt = Date.now()
  ensureConversationTitle(conv, content)
  activeAssistantMessageIndex.value = null
  pendingStepContent.value = ''
  isStreaming.value = true
  streamUserIndex.value = replaceUserIndex >= 0 ? replaceUserIndex : conv.messages.length - 1
  await scrollToBottom()

  const controller = new AbortController()
  activeAbortController.value = controller
  try {
    const extraParams = currentApp.value === 'rag' ? { status: ragStatus.value } : {}
    await streamChat(currentApp.value, content, conv.chatId, ({ event, data }) => {
      if (isPlainStreamApp(currentApp.value)) {
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
    }, controller.signal, extraParams)
    if (pendingStepContent.value) {
      conv.messages.push({ role: 'assistant', content: pendingStepContent.value })
      activeAssistantMessageIndex.value = conv.messages.length - 1
      pendingStepContent.value = ''
    } else if (activeAssistantMessageIndex.value === null) {
      conv.messages.push({ role: 'assistant', content: '本次对话没有返回内容，请稍后重试。' })
    }
  } catch (error) {
    if (error?.name !== 'AbortError') {
      conv.messages.push({ role: 'assistant', content: `抱歉，连接服务时出现问题：${error.message}` })
    }
  } finally {
    isStreaming.value = false
    activeAbortController.value = null
    streamUserIndex.value = -1
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
  <!-- 全局动态背景（三套可切换：彗星日落视频 / 星空流星 / 彗星蓝天·你的名字，主页 / 聊天 / 登录注册 / 个人中心共用） -->
  <video
    v-if="currentBackground.type === 'video'"
    :key="currentBackground.id"
    class="app-bg-video"
    autoplay
    muted
    loop
    playsinline
    webkit-playsinline
    preload="auto"
    :poster="currentBackground.poster"
    aria-hidden="true"
    tabindex="-1"
  >
    <source :src="currentBackground.src" type="video/mp4" />
  </video>
  <div
    v-else
    :key="currentBackground.id"
    class="app-bg-image"
    :style="{ backgroundImage: `url(${currentBackground.src})` }"
    aria-hidden="true"
  ></div>
  <div class="app-bg-overlay" aria-hidden="true"></div>

  <!-- 背景切换控件 -->
  <div class="bg-switcher" role="group" aria-label="背景切换">
    <button class="bg-switch-btn" title="上一个背景" @click="prevBackground">‹</button>
    <button class="bg-switch-label" title="点击切换背景" @click="nextBackground">{{ currentBackground.name }}</button>
    <button class="bg-switch-btn" title="下一个背景" @click="nextBackground">›</button>
    <button
      class="bg-switch-btn"
      :class="{ active: bgAuto }"
      :title="bgAuto ? '停止自动轮播' : '自动轮播背景（10 秒切换）'"
      @click="toggleBackgroundAuto"
    >{{ bgAuto ? '❚❚' : '▶' }}</button>
  </div>

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
        <p class="hero-sub">{{ auth.user ? `欢迎回来，${userDisplayName}。选择一位 AI 伙伴，让灵感、情感与行动在此刻汇聚。` : '游客可直接体验「AI 智能助手」，登录后解锁「AI 情感专家」「AI 超级智能体」。' }}</p>
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
            <small v-if="(item.id === 'manus' || item.id === 'rag') && !auth.user" class="card-lock">✦ 登录后可用</small>
          </span>
          <span class="card-arrow">↗</span>
        </button>
      </div>
      <div class="feature-strip">
        <div class="feature"><b>实时流式</b><span>逐字响应，对话如临其境</span></div>
        <div class="feature"><b>多会话管理</b><span>历史记录与草稿自动保存</span></div>
        <div class="feature"><b>三大 AI 伙伴</b><span>智能助手 · 超级智能体 · 情感专家</span></div>
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
          <div
            v-for="conv in appConversations"
            :key="conv.id"
            class="conv-item"
            :class="{ active: conv.id === currentConversationId }"
            role="button"
            tabindex="0"
            :title="conv.title"
            @click="selectConversation(conv.id)"
            @keydown.enter="selectConversation(conv.id)"
          >
            <span v-if="appConversations.length > 1" class="conv-del" title="删除对话" @click.stop="deleteConversation(conv.id)">×</span>
            <span class="conv-edit" title="重命名对话" @click.stop="startRenameConversation(conv)">✎</span>
            <input
              v-if="editingConvId === conv.id"
              v-model="editingConvTitle"
              class="conv-rename-input"
              :placeholder="conv.title"
              @keydown.enter.prevent="commitRenameConversation()"
              @keydown.esc.prevent="cancelRenameConversation()"
              @blur="commitRenameConversation()"
              @click.stop
            />
            <span v-else class="conv-title">{{ conv.title }}</span>
            <span class="conv-meta">{{ conv.messages.length }} 条 · {{ formatConvTime(conv.updatedAt) }}</span>
          </div>
          <p v-if="!appConversations.length" class="conv-empty">暂无对话，点击上方 ＋ 创建</p>
        </div>
      </aside>

      <!-- 主聊天区 -->
      <div class="chat-main">
        <!-- 对话活动轨道：每根短线对应一轮对话，悬停预览、点击跳转 -->
        <div
          v-if="turns.length"
          ref="turnRailEl"
          class="turn-rail"
          role="navigation"
          aria-label="对话轮次导航"
          @mousemove="onRailMouseMove"
          @mouseleave="onRailMouseLeave"
          @click="onRailClick"
          @wheel.prevent="onRailWheel"
        >
          <button
            v-for="(turn, idx) in turns"
            :key="idx"
            class="turn-marker"
            :class="{ hovered: idx === hoverTurnIndex, active: idx === activeTurnIndex, current: idx === turns.length - 1 }"
            :style="markerStyle(idx)"
            :title="turnPreview(turn)"
            @click="jumpToTurn(idx)"
          >
            <span class="turn-tip">{{ turnPreview(turn) }}</span>
          </button>
        </div>
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
        <div ref="chatBody" class="chat-body" @scroll="onChatBodyScroll">
          <div class="intro">
            <span :class="['intro-icon', app.color]">{{ app.icon }}</span>
            <p>{{ welcome }}</p>
            <small v-if="isPlainStreamApp(currentApp)">会话 ID：{{ currentConversation?.chatId }}</small>
            <label v-if="currentApp === 'rag'" class="rag-status">
              用户婚恋状态
              <select v-model="ragStatus" title="按用户婚恋状态过滤知识库文档">
                <option value="单身">单身</option>
                <option value="恋爱">恋爱</option>
                <option value="已婚">已婚</option>
              </select>
            </label>
          </div>
          <article
            v-for="(message, index) in messages"
            :key="index"
            class="message"
            :data-index="index"
            :class="{ [message.role]: true, flash: isMessageInHighlightedTurn(index) }"
          >
            <span class="avatar" :class="message.role === 'assistant' ? app.color : ''">{{ message.role === 'user' ? '我' : app.avatar }}</span>
            <div class="msg-body">
              <template v-if="editingUserIndex === index">
                <textarea
                  v-model="editingUserText"
                  class="msg-edit-input"
                  rows="2"
                  placeholder="修改提问内容…"
                  @keydown.enter.exact.prevent="confirmEditUserMessage"
                  @keydown.esc="cancelEditUserMessage"
                ></textarea>
                <div class="msg-edit-actions">
                  <button type="button" class="msg-edit-cancel" @click="cancelEditUserMessage">取消</button>
                  <button type="button" class="msg-edit-confirm" @click="confirmEditUserMessage">修改并重新生成</button>
                </div>
              </template>
              <template v-else>
                <div class="bubble">{{ message.content }}<i v-if="message.role === 'assistant' && isStreaming && index === messages.length - 1" class="cursor"></i></div>
                <button v-if="message.role === 'user'" class="msg-edit-btn" title="修改该提问" @click="startEditUserMessage(index)">✎</button>
              </template>
            </div>
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
          <button v-if="isStreaming" type="button" class="stop-btn" aria-label="停止生成" @click="stopGeneration">■</button>
          <button v-else type="submit" :disabled="!draft.trim() || isStreaming" aria-label="发送消息">↑</button>
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
