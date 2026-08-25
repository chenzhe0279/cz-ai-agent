import axios from 'axios'

// 后端地址：本地默认 localhost，云端部署时通过环境变量 VITE_API_BASE_URL 注入（见 .env.example / Dockerfile）
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8123/api'
export const TOKEN_KEY = 'cz_ai_token'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token) {
  if (token) {
    localStorage.setItem(TOKEN_KEY, token)
  } else {
    localStorage.removeItem(TOKEN_KEY)
  }
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY)
}

export const http = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30_000,
})

// 请求拦截：自动携带 Sa-Token 令牌
http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.satoken = token
  }
  return config
})

// 响应拦截：统一解包 BaseResponse，登录失效时通知全局
http.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 0) {
        return body.data
      }
      if (body.code === 40100 || body.code === 40101) {
        clearToken()
        window.dispatchEvent(new CustomEvent('auth:unauthorized', { detail: body.message || '' }))
      }
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return body
  },
  (error) => {
    if (error.response?.status === 401 || error.response?.status === 403) {
      clearToken()
      window.dispatchEvent(new CustomEvent('auth:unauthorized'))
    }
    const message = error.response?.data?.message || error.message || '网络请求失败'
    return Promise.reject(new Error(message))
  },
)
