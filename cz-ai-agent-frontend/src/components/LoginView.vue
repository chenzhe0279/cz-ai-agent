<script setup>
import { onUnmounted, ref } from 'vue'
import { login, loginByEmailCode } from '../store/auth'
import * as userApi from '../services/user'

const emit = defineEmits(['success', 'switch-register', 'switch-forgot', 'back'])

const mode = ref('account') // account | email
const account = ref('')
const password = ref('')
const email = ref('')
const verifyCode = ref('')
const countdown = ref(0)
let timer = null
const loading = ref(false)
const error = ref('')
const notice = ref('')

function validateEmail(value) {
  return /^[\w.+-]+@[\w-]+(\.[\w-]+)+$/.test(value.trim())
}

function startCountdown() {
  countdown.value = 60
  clearInterval(timer)
  timer = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0) clearInterval(timer)
  }, 1000)
}

function switchMode(next) {
  mode.value = next
  error.value = ''
  notice.value = ''
}

async function sendCode() {
  error.value = ''
  if (!validateEmail(email.value)) {
    error.value = '邮箱格式不正确'
    return
  }
  loading.value = true
  try {
    await userApi.sendLoginCode({ email: email.value.trim() })
    notice.value = '验证码已发送，请查收邮箱（10 分钟内有效）'
    startCountdown()
  } catch (e) {
    error.value = e.message || '发送失败'
  } finally {
    loading.value = false
  }
}

async function submit() {
  error.value = ''
  if (mode.value === 'account') {
    if (!account.value.trim() || !password.value) {
      error.value = '请输入账号和密码'
      return
    }
    loading.value = true
    try {
      await login({ userAccount: account.value.trim(), userPassword: password.value })
      emit('success')
    } catch (e) {
      error.value = e.message || '登录失败'
    } finally {
      loading.value = false
    }
    return
  }

  if (!validateEmail(email.value)) {
    error.value = '请输入正确的邮箱'
    return
  }
  if (!verifyCode.value.trim()) {
    error.value = '请输入邮箱验证码'
    return
  }
  loading.value = true
  try {
    await loginByEmailCode({ email: email.value.trim(), verifyCode: verifyCode.value.trim() })
    emit('success')
  } catch (e) {
    error.value = e.message || '登录失败'
  } finally {
    loading.value = false
  }
}

onUnmounted(() => clearInterval(timer))
</script>

<template>
  <main class="shell app-home">
    <div class="space-field" aria-hidden="true"><i v-for="n in 42" :key="n"></i></div>
    <div class="nebula nebula-1" aria-hidden="true"></div>
    <div class="nebula nebula-2" aria-hidden="true"></div>
    <div class="planet" aria-hidden="true"><span class="planet-ring"></span></div>
    <div class="shooting-star" aria-hidden="true"></div>
    <section class="auth-page">
      <header class="site-nav">
        <button class="brand auth-back" @click="emit('back')">← 返回首页</button>
        <span class="nav-status"><b></b> ACCOUNT ACCESS</span>
      </header>
      <form class="auth-card" @submit.prevent="submit">
        <p class="eyebrow">WELCOME BACK</p>
        <h2>登录你的账号</h2>

        <div class="login-tabs">
          <button type="button" :class="{ active: mode === 'account' }" @click="switchMode('account')">账号密码</button>
          <button type="button" :class="{ active: mode === 'email' }" @click="switchMode('email')">邮箱验证码</button>
        </div>

        <template v-if="mode === 'account'">
          <label>
            <span>账号</span>
            <input v-model="account" placeholder="请输入账号" autocomplete="username" />
          </label>
          <label>
            <span>密码</span>
            <input v-model="password" type="password" placeholder="请输入密码" autocomplete="current-password" />
          </label>
        </template>

        <template v-else>
          <label>
            <span>绑定邮箱</span>
            <div class="code-row">
              <input v-model="email" type="email" placeholder="请输入已绑定的邮箱" />
              <button type="button" class="code-btn" :disabled="loading || countdown > 0" @click="sendCode">
                {{ countdown > 0 ? `${countdown}s 后重发` : '发送验证码' }}
              </button>
            </div>
          </label>
          <label>
            <span>验证码</span>
            <input v-model="verifyCode" placeholder="请输入 6 位验证码" />
          </label>
          <p v-if="notice" class="auth-notice">{{ notice }}</p>
        </template>

        <p v-if="error" class="auth-error">{{ error }}</p>
        <button class="auth-submit" type="submit" :disabled="loading">
          {{ loading ? '登录中…' : '登 录' }}
        </button>
        <p class="auth-switch">
          还没有账号？<a @click.prevent="emit('switch-register')">立即注册</a>
          <span class="auth-sep">·</span>
          <a @click.prevent="emit('switch-forgot')">忘记密码？</a>
        </p>
      </form>
      <footer class="site-footer">
        <span>© {{ new Date().getFullYear() }} 春日部当红小P的AI空间</span>
        <span>账号 · 安全 · 权限</span>
      </footer>
    </section>
  </main>
</template>
