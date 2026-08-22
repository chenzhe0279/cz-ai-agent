import { API_BASE_URL, getToken } from './http'

const endpoints = {
  // Spring SseEmitter 接口，恋爱大师通过 chatId 维持独立会话。
  love: '/ai/love_app/chat/sse/emitter',
  manus: '/ai/manus/chat',
}

/** 以 SSE / chunked 文本方式读取后端实时回答。 */
export async function streamChat(type, message, chatId, onEvent) {
  const params = new URLSearchParams({ message })
  if (type === 'love') params.set('chatId', chatId)

  const headers = { Accept: 'text/event-stream' }
  const token = getToken()
  if (token) {
    headers.satoken = token
  }
  const response = await fetch(`${API_BASE_URL}${endpoints[type]}?${params}`, {
    method: 'GET',
    headers,
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
