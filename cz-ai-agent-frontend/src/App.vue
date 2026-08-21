<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { streamChat } from './services/chat'
import { http } from './services/http'

const currentApp = ref(null)
const draft = ref('')
const isStreaming = ref(false)
const chatBody = ref(null)
const loveChatId = ref('')
const messages = ref([])
const activeAssistantMessageIndex = ref(null)
const pendingStepContent = ref('')
const humanQuestion = ref(null)
const humanAnswer = ref('')
const isSubmittingHumanAnswer = ref(false)

const apps = [
  { id: 'love', icon: '♡', avatar: '恋', name: 'AI 恋爱大师', description: '懂你心意，也懂如何把话说得恰到好处。', color: 'rose', tag: 'RELATIONSHIP GUIDE' },
  { id: 'manus', icon: '✦', avatar: '智', name: 'AI 超级智能体', description: '把复杂的问题，转化为清晰的行动方案。', color: 'violet', tag: 'AUTONOMOUS AGENT' },
]
const app = computed(() => apps.find((item) => item.id === currentApp.value))
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

function createChatId() {
  return typeof crypto?.randomUUID === 'function'
    ? crypto.randomUUID()
    : `chat-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`
}

async function openApp(id) {
  currentApp.value = id
  draft.value = ''
  isStreaming.value = false
  messages.value = []
  pendingStepContent.value = ''
  humanQuestion.value = null
  if (id === 'love') loveChatId.value = createChatId()
  await scrollToBottom()
}

function backHome() {
  currentApp.value = null
  messages.value = []
}

async function send() {
  const content = draft.value.trim()
  if (!content || isStreaming.value) return
  draft.value = ''
  messages.value.push({ role: 'user', content })
  activeAssistantMessageIndex.value = null
  pendingStepContent.value = ''
  isStreaming.value = true
  await scrollToBottom()

  try {
    await streamChat(currentApp.value, content, loveChatId.value, ({ event, data }) => {
      // 恋爱大师接口返回的是连续文本流：所有 SSE 分片必须写入同一个气泡，
      // 让 Vue 随分片刷新，从而形成打字机效果。
      if (currentApp.value === 'love') {
        if (activeAssistantMessageIndex.value === null) {
          messages.value.push({ role: 'assistant', content: '' })
          activeAssistantMessageIndex.value = messages.value.length - 1
        }
        messages.value[activeAssistantMessageIndex.value].content += data
        scrollToBottom()
        return
      }

      if (event === 'human_question') {
        try {
          humanQuestion.value = JSON.parse(data)
          humanAnswer.value = ''
        } catch {
          pendingStepContent.value += '\nAI 需要补充信息，但提问内容解析失败。'
        }
      } else if (event === 'assistant_message') {
        // AI 正文属于即将完成的步骤，等收到 Step N 再一起放入独立气泡。
        pendingStepContent.value += `${pendingStepContent.value ? '\n\n' : ''}${data}`
      } else if (/^Step\s+\d+\s*:/.test(data.trim())) {
        // 每个后端 Step 都强制创建一个新的 AI 气泡，绝不和其他 Step 合并。
        const contentForStep = [pendingStepContent.value, data]
          .filter(Boolean)
          .join(currentApp.value === 'manus' ? '\n\n\n' : '\n\n')
        messages.value.push({ role: 'assistant', content: contentForStep })
        activeAssistantMessageIndex.value = messages.value.length - 1
        pendingStepContent.value = ''
        scrollToBottom()
      } else {
        // 非步骤事件（例如异常或结束提示）单独显示，避免混入任何 Step。
        messages.value.push({ role: 'assistant', content: data })
        activeAssistantMessageIndex.value = messages.value.length - 1
        scrollToBottom()
      }
    })
    if (pendingStepContent.value) {
      messages.value.push({ role: 'assistant', content: pendingStepContent.value })
      activeAssistantMessageIndex.value = messages.value.length - 1
      pendingStepContent.value = ''
    } else if (activeAssistantMessageIndex.value === null) {
      messages.value.push({ role: 'assistant', content: '本次对话没有返回内容，请稍后重试。' })
    }
  } catch (error) {
    messages.value.push({ role: 'assistant', content: `抱歉，连接服务时出现问题：${error.message}` })
  } finally {
    isStreaming.value = false
    await scrollToBottom()
  }
}

async function submitHumanAnswer() {
  const answer = humanAnswer.value.trim()
  if (!answer || !humanQuestion.value || isSubmittingHumanAnswer.value) return
  isSubmittingHumanAnswer.value = true
  try {
    // 后端会在原 SSE 连接继续推送；后续内容将按新的 Step 自动生成独立气泡。
    messages.value.push({ role: 'user', content: `补充信息：${answer}` })
    await scrollToBottom()

    await http.post('/ai/manus/human-answer', {
      requestId: humanQuestion.value.requestId,
      answer,
    })
    humanQuestion.value = null
    humanAnswer.value = ''
    await scrollToBottom()
  } catch (error) {
    messages.value.push({ role: 'assistant', content: `提交补充信息失败：${error.message}` })
  } finally {
    isSubmittingHumanAnswer.value = false
  }
}

async function scrollToBottom() {
  await nextTick()
  if (chatBody.value) chatBody.value.scrollTop = chatBody.value.scrollHeight
}
</script>

<template>
  <main class="shell" :class="currentApp ? `app-${currentApp}` : 'app-home'">
    <section v-if="!currentApp" class="home">
      <div class="space-field" aria-hidden="true"><i v-for="n in 42" :key="n"></i></div>
      <header class="site-nav"><div class="brand"><span class="brand-mark">✦</span><span>CZ AI</span><small>WORKSPACE</small></div><span class="nav-status"><b></b> SYSTEM ONLINE</span></header>
      <div class="home-copy">
        <p class="eyebrow">PERSONAL INTELLIGENCE, EXPANDED</p>
        <h1>驶入未知，<em>与星辰同频。</em></h1>
        <p>选择一位 AI 伙伴，让灵感、情感与行动在此刻汇聚。</p>
      </div>
      <div class="app-grid">
        <button v-for="item in apps" :key="item.id" class="app-card" :class="item.color" @click="openApp(item.id)">
          <span class="card-orbit"></span><span class="card-icon">{{ item.icon }}</span>
          <span class="card-content"><em>{{ item.tag }}</em><strong>{{ item.name }}</strong><small>{{ item.description }}</small></span>
          <span class="card-arrow">↗</span>
        </button>
      </div>
      <footer class="site-footer"><span>© {{ new Date().getFullYear() }} CZ AI WORKSPACE</span><span>智能对话 · 探索无限可能</span></footer>
    </section>

    <section v-else class="chat-page">
      <header class="chat-header">
        <button class="back" aria-label="返回首页" @click="backHome">←</button>
        <div class="chat-title"><span :class="['small-icon', app.color]">{{ app.avatar }}</span><div><strong>{{ app.name }}</strong><small><i></i> 在线 · 随时为你服务</small></div></div>
        <span class="secure">✦ PRIVATE SESSION</span>
      </header>
      <div ref="chatBody" class="chat-body">
        <div class="intro"><span :class="['intro-icon', app.color]">{{ app.icon }}</span><p>{{ welcome }}</p><small v-if="currentApp === 'love'">会话 ID：{{ loveChatId }}</small></div>
        <article v-for="(message, index) in messages" :key="index" class="message" :class="message.role">
          <span class="avatar" :class="message.role === 'assistant' ? app.color : ''">{{ message.role === 'user' ? '我' : app.avatar }}</span>
          <div class="bubble">{{ message.content }}<i v-if="message.role === 'assistant' && isStreaming && index === messages.length - 1" class="cursor"></i></div>
        </article>
      </div>
      <form class="composer" @submit.prevent="send">
        <textarea v-model="draft" rows="1" placeholder="输入你的问题…" @keydown.enter.exact.prevent="send"></textarea>
        <button type="submit" :disabled="!draft.trim() || isStreaming" aria-label="发送消息">↑</button>
      </form>
      <p class="hint">Enter 发送 · Shift + Enter 换行</p>
      <footer class="chat-footer">© {{ new Date().getFullYear() }} CZ AI WORKSPACE · 智能对话服务</footer>
      <div v-if="humanQuestion" class="human-dialog-mask" role="dialog" aria-modal="true" aria-labelledby="human-question-title">
        <form class="human-dialog" @submit.prevent="submitHumanAnswer">
          <span class="dialog-icon">?</span>
          <p class="dialog-overline">AI 需要你的协助</p>
          <h2 id="human-question-title">{{ humanQuestion.question }}</h2>
          <textarea v-model="humanAnswer" rows="3" autofocus placeholder="请输入你的回答…"></textarea>
          <button type="submit" :disabled="!humanAnswer.trim() || isSubmittingHumanAnswer">
            {{ isSubmittingHumanAnswer ? '正在提交…' : '提交回答 →' }}
          </button>
        </form>
      </div>
    </section>
  </main>
</template>
