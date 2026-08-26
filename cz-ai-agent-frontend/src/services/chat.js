import { API_BASE_URL, getToken } from './http'

const endpoints = {
  // Spring SseEmitter 接口，AI 智能助手通过 chatId 维持独立会话。
  love: '/ai/love_app/chat/sse/emitter',
  manus: '/ai/manus/chat',
  // RAG 检索增强对话：与智能助手一样通过 chatId 维持会话，status 过滤知识库文档状态
  rag: '/ai/rag/chat/sse',
}

/** 以 SSE / chunked 文本方式读取后端实时回答。
 * @param history 本次发送之前的对话历史（[{role:'user'|'assistant', content}]），供后端重建上下文 */
export async function streamChat(type, message, chatId, onEvent, signal, extraParams = {}, history = []) {
  const params = new URLSearchParams({ message })
  if (type === 'love' || type === 'rag') params.set('chatId', chatId)
  for (const [key, value] of Object.entries(extraParams)) {
    if (value !== undefined && value !== null && value !== '') params.set(key, value)
  }
  if (Array.isArray(history) && history.length) {
    params.set('history', JSON.stringify(history))
  }

  const headers = { Accept: 'text/event-stream' }
  const token = getToken()
  if (token) {
    headers.satoken = token
  }
  const response = await fetch(`${API_BASE_URL}${endpoints[type]}?${params}`, {
    method: 'GET',
    headers,
    signal,
  })
  if (response.status === 401) {
    window.dispatchEvent(new CustomEvent('auth:unauthorized'))
  }
  if (!response.ok) throw new Error(`请求失败（${response.status}）`)
  if (!response.body) throw new Error('浏览器不支持流式响应')

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  const consumeEvent = (eventBlock) => {
    if (!eventBlock.trim()) return
    let event = 'message'
    const dataLines = []
    for (const line of eventBlock.split(/\r?\n/)) {
      if (line.startsWith('event:')) event = line.slice(6).trim()
      if (line.startsWith('data:')) dataLines.push(line.slice(5).replace(/^\s/, ''))
    }
    if (dataLines.length) onEvent({ event, data: dataLines.join('\n') })
  }
  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const eventBlocks = buffer.split(/\r?\n\r?\n/)
      buffer = eventBlocks.pop() ?? ''
      for (const eventBlock of eventBlocks) {
        consumeEvent(eventBlock)
      }
    }
    // 容错：部分 SSE 实现可能未以换行收尾。
    consumeEvent(buffer)
  } finally {
    reader.releaseLock()
  }
}
