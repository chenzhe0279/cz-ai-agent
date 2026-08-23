import { reactive } from 'vue'
import * as userApi from '../services/user'
import { clearToken, getToken, setToken } from '../services/http'

// 本地缓存的登录用户信息：刷新页面时先据此恢复登录态，
// 即使后端校验偶发失败（网络抖动、服务瞬时不可用），也不会把用户“闪”成未登录
const USER_KEY = 'cz_ai_user'

function readCachedUser() {
  try {
    const raw = localStorage.getItem(USER_KEY)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

function writeCachedUser(user) {
  try {
    if (user) {
      localStorage.setItem(USER_KEY, JSON.stringify(user))
    } else {
      localStorage.removeItem(USER_KEY)
    }
  } catch {
    // 忽略存储异常（如隐私模式）
  }
}

// 全局登录态
export const auth = reactive({
  user: null,
  ready: false,
})

// 应用启动时根据本地令牌恢复登录态：
// 1. 先立刻用本地缓存恢复用户信息（刷新页面不闪烁、不掉登录）
// 2. 再向后端校验令牌；成功则刷新缓存；只有明确 401/403 才清除登录态
export async function initAuth() {
  if (!getToken()) {
    auth.user = null
    writeCachedUser(null)
    auth.ready = true
    return
  }
  auth.user = readCachedUser()
  try {
    const current = await userApi.getCurrentUser()
    auth.user = current
    writeCachedUser(current)
  } catch (e) {
    // 401/403 时响应拦截器已清除 token，这里保持未登录；
    // 其它错误（网络/500 等）保留缓存用户，视觉上不掉登录
    if (!getToken()) {
      auth.user = null
      writeCachedUser(null)
    }
  } finally {
    auth.ready = true
  }
}

export async function login(payload) {
  const data = await userApi.login(payload)
  setToken(data.token)
  auth.user = data.user
  writeCachedUser(data.user)
  return data
}

export async function loginByEmailCode(payload) {
  const data = await userApi.loginByEmailCode(payload)
  setToken(data.token)
  auth.user = data.user
  writeCachedUser(data.user)
  return data
}

export async function logout() {
  try {
    await userApi.logout()
  } catch {
    // 令牌失效等情况忽略
  }
  clearToken()
  auth.user = null
  writeCachedUser(null)
}
