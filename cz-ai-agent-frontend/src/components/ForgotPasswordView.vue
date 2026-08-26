<script setup>
import { onUnmounted, ref } from 'vue'
import * as userApi from '../services/user'

const emit = defineEmits(['switch-login', 'back'])

const step = ref('email') // email | reset | done
const email = ref('')
const verifyCode = ref('')
const newPassword = ref('')
const checkPassword = ref('')
const countdown = ref(0)
let timer = null
const loading = ref(false)
const error = ref('')
const notice = ref('')
const codeSent = ref(false)
const sentEmail = ref('')

function validateEmail(value) {
  return /^[\w.+-]+@[\w-]+(\.[\w-]+)+$/.test(value.trim())
}

async function sendCode() {
  error.value = ''
  if (!validateEmail(email.value)) {
    error.value = '邮箱格式不正确'
    return
  }
  loading.value = true
  try {
    await userApi.sendPasswordResetCode({ email: email.value.trim() })
    notice.value = '验证码已发送，请查收邮箱（10 分钟内有效）'
    codeSent.value = true
    sentEmail.value = email.value.trim()
    startCountdown()
  } catch (e) {
    error.value = e.message || '发送失败'
  } finally {
    loading.value = false
  }
}

function startCountdown() {
  countdown.value = 60
  clearInterval(timer)
  timer = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0) clearInterval(timer)
  }, 1000)
}

async function reset() {
  error.value = ''
  if (!codeSent.value) {
    error.value = '请先获取验证码'
    return
  }
  if (email.value.trim() !== sentEmail.value) {
    error.value = '邮箱已变更，请重新获取验证码'
    step.value = 'email'
    codeSent.value = false
    return
  }
  if (!verifyCode.value.trim()) {
    error.value = '请输入验证码'
    return
  }
  if (!newPassword.value || newPassword.value.length < 8) {
    error.value = '新密码长度至少 8 位'
    return
  }
  if (newPassword.value !== checkPassword.value) {
    error.value = '两次输入的新密码不一致'
    return
  }
  loading.value = true
  try {
    await userApi.resetPassword({
      email: email.value.trim(),
      verifyCode: verifyCode.value.trim(),
      newPassword: newPassword.value,
      checkPassword: checkPassword.value,
    })
    step.value = 'done'
  } catch (e) {
    error.value = e.message || '重置失败'
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
        <button class="brand auth-back" @click="emit('back')">← 返回登录</button>
        <span class="nav-status"><b></b> ACCOUNT RECOVERY</span>
      </header>

      <form v-if="step !== 'done'" class="auth-card" @submit.prevent="step === 'email' ? sendCode() : reset()">
        <p class="eyebrow">ACCOUNT RECOVERY</p>
        <h2>{{ step === 'email' ? '找回密码' : '设置新密码' }}</h2>

        <template v-if="step === 'email'">
          <label>
            <span>绑定邮箱</span>
            <input v-model="email" type="email" placeholder="请输入注册时绑定的邮箱" />
          </label>
          <p v-if="notice" class="auth-notice">{{ notice }}</p>
          <button class="auth-submit" type="submit" :disabled="loading">
            {{ loading ? '发送中…' : countdown > 0 ? `${countdown}s 后重发` : '发送验证码' }}
          </button>
          <p v-if="codeSent" class="auth-switch"><a @click.prevent="step = 'reset'">已收到验证码，直接设置新密码 →</a></p>
        </template>

        <template v-else>
          <label>
            <span>验证码</span>
            <input v-model="verifyCode" placeholder="请输入 6 位验证码" />
          </label>
          <label>
            <span>新密码（至少 8 位）</span>
            <input v-model="newPassword" type="password" autocomplete="new-password" />
          </label>
          <label>
            <span>确认新密码</span>
            <input v-model="checkPassword" type="password" autocomplete="new-password" />
          </label>
          <p v-if="notice" class="auth-notice">{{ notice }}</p>
          <button class="auth-submit" type="submit" :disabled="loading">
            {{ loading ? '提交中…' : '重置密码' }}
          </button>
        </template>

        <p v-if="error" class="auth-error">{{ error }}</p>
      </form>

      <div v-else class="auth-card">
        <p class="eyebrow">SUCCESS</p>
        <h2>密码已重置</h2>
        <p class="auth-switch">请使用新密码重新登录。</p>
        <button class="auth-submit" @click="emit('switch-login')">去登录</button>
      </div>

      <footer class="site-footer">
        <span>© {{ new Date().getFullYear() }} 春日部当红小P的AI空间</span>
        <span>账号 · 安全 · 权限</span>
      </footer>
    </section>
  </main>
</template>
