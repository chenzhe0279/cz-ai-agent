import { reactive } from 'vue'
import * as userApi from '../services/user'
import { clearToken, getToken, setToken } from '../services/http'

// 全局登录态
export const auth = reactive({
  user: null,
  ready: false,
})

// 应用启动时根据本地令牌恢复登录态
export async function initAuth() {
  if (!getToken()) {
    auth.ready = true
    return
  }
  try {
    auth.user = await userApi.getCurrentUser()
  } catch {
    auth.user = null
  } finally {
    auth.ready = true
  }
}

export async function login(payload) {
  const data = await userApi.login(payload)
  setToken(data.token)
  auth.user = data.user
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
}
