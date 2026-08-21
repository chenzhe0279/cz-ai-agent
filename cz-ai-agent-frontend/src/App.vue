<script setup>
import { computed, nextTick, ref } from 'vue'
import { streamChat } from './services/chat'
import { http } from './services/http'

const currentApp = ref(null)
const draft = ref('')
const isStreaming = ref(false)
const chatBody = ref(null)
const loveChatId = ref('')
const messages = ref([])
const activeAssistantMessageIndex = ref(null)
const humanQuestion = ref(null)
const humanAnswer = ref('')
const isSubmittingHumanAnswer = ref(false)

const apps = [
  { id: 'love', icon: '♡', name: 'AI 恋爱大师', description: '懂你心意，也懂如何把话说得恰到好处。', color: 'rose' },
  { id: 'manus', icon: '✦', name: 'AI 超级智能体', description: '把复杂的问题，转化为清晰的行动方案。', color: 'violet' },
]
const app = computed(() => apps.find((item) => item.id === currentApp.value))
const welcome = computed(() => currentApp.value === 'love'
  ? '你好，我是你的恋爱军师。今天有什么心事想和我聊聊？'
  : '你好，我是 AI 超级智能体。告诉我你的目标，我会协助你一步步完成。')

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
  messages.value.push({ role: 'assistant', content: '' })
  activeAssistantMessageIndex.value = messages.value.length - 1
  isStreaming.value = true
  await scrollToBottom()

  try {
    await streamChat(currentApp.value, content, loveChatId.value, ({ event, data }) => {
      if (event === 'human_question') {
        try {
          humanQuestion.value = JSON.parse(data)
          humanAnswer.value = ''
        } catch {
          messages.value[activeAssistantMessageIndex.value].content += '\nAI 需要补充信息，但提问内容解析失败。'
        }
      } else {
        // 必须通过响应式数组中的代理对象更新，才能在每个 SSE 分片到达时刷新界面。
        messages.value[activeAssistantMessageIndex.value].content += data
        scrollToBottom()
      }
    })
    if (!messages.value[activeAssistantMessageIndex.value].content) {
      messages.value[activeAssistantMessageIndex.value].content = '本次对话没有返回内容，请稍后重试。'
    }
  } catch (error) {
    messages.value[activeAssistantMessageIndex.value].content = `抱歉，连接服务时出现问题：${error.message}`
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
    // 后端收到回答后会继续在原 SSE 连接中推送数据；先创建新气泡，
    // 避免后续内容仍追加到“提问前”的 AI 回复中。
    messages.value.push({ role: 'user', content: `补充信息：${answer}` })
    messages.value.push({ role: 'assistant', content: '' })
    activeAssistantMessageIndex.value = messages.value.length - 1
    await scrollToBottom()

    await http.post('/ai/manus/human-answer', {
      requestId: humanQuestion.value.requestId,
      answer,
    })
    humanQuestion.value = null
    humanAnswer.value = ''
    await scrollToBottom()
  } catch (error) {
    messages.value[activeAssistantMessageIndex.value].content = `提交补充信息失败：${error.message}`
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
  <main class="shell">
    <section v-if="!currentApp" class="home">
      <div class="brand"><span class="brand-mark">✦</span> AI WORKSPACE</div>
      <div class="home-copy">
        <p class="eyebrow">YOUR PERSONAL AI CREW</p>
        <h1>想清楚，<em>再出发。</em></h1>
        <p>选择一个 AI 伙伴，开始一段专属于你的对话。</p>
      </div>
      <div class="app-grid">
        <button v-for="item in apps" :key="item.id" class="app-card" :class="item.color" @click="openApp(item.id)">
          <span class="card-icon">{{ item.icon }}</span>
          <span class="card-content"><strong>{{ item.name }}</strong><small>{{ item.description }}</small></span>
          <span class="card-arrow">↗</span>
        </button>
      </div>
      <p class="home-footer">POWERED BY CZ AI AGENT</p>
    </section>

    <section v-else class="chat-page">
      <header class="chat-header">
        <button class="back" aria-label="返回首页" @click="backHome">←</button>
        <div class="chat-title"><span :class="['small-icon', app.color]">{{ app.icon }}</span><div><strong>{{ app.name }}</strong><small>在线 · 随时为你服务</small></div></div>
        <span class="secure">● 对话已加密</span>
      </header>
      <div ref="chatBody" class="chat-body">
        <div class="intro"><span :class="['intro-icon', app.color]">{{ app.icon }}</span><p>{{ welcome }}</p><small v-if="currentApp === 'love'">会话 ID：{{ loveChatId }}</small></div>
        <article v-for="(message, index) in messages" :key="index" class="message" :class="message.role">
          <span class="avatar">{{ message.role === 'user' ? '我' : app.icon }}</span>
          <div class="bubble">{{ message.content }}<i v-if="message.role === 'assistant' && isStreaming && index === messages.length - 1" class="cursor"></i></div>
        </article>
      </div>
      <form class="composer" @submit.prevent="send">
        <textarea v-model="draft" rows="1" placeholder="输入你的问题…" @keydown.enter.exact.prevent="send"></textarea>
        <button type="submit" :disabled="!draft.trim() || isStreaming" aria-label="发送消息">↑</button>
      </form>
      <p class="hint">Enter 发送 · Shift + Enter 换行</p>
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
