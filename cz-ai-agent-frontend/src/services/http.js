import axios from 'axios'

export const API_BASE_URL = 'http://localhost:8123/api'

// 供后续非流式接口复用；聊天 SSE 由 streamChat 读取浏览器响应流。
export const http = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30_000,
})
