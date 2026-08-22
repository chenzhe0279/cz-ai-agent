<script setup>
import { ref } from 'vue'
import * as userApi from '../services/user'
import { login } from '../store/auth'

const emit = defineEmits(['success', 'switch-login', 'back'])

const account = ref('')
const password = ref('')
const checkPassword = ref('')
const loading = ref(false)
const error = ref('')

async function submit() {
  error.value = ''
  const acc = account.value.trim()
  if (!acc || acc.length < 4 || acc.length > 16) {
    error.value = '账号长度应为 4-16 位'
    return
  }
  if (!/^[A-Za-z0-9_]+$/.test(acc)) {
    error.value = '账号仅支持字母、数字和下划线'
    return
  }
  if (!password.value || password.value.length < 8) {
    error.value = '密码长度至少 8 位'
    return
  }
  if (password.value !== checkPassword.value) {
    error.value = '两次输入的密码不一致'
    return
  }
  loading.value = true
  try {
    await userApi.register({ userAccount: acc, userPassword: password.value, checkPassword: checkPassword.value })
    // 注册成功后自动登录
    await login({ userAccount: acc, userPassword: password.value })
    emit('success')
  } catch (e) {
    error.value = e.message || '注册失败'
  } finally {
    loading.value = false
  }
}
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
        <span class="nav-status"><b></b> CREATE ACCOUNT</span>
      </header>
      <form class="auth-card" @submit.prevent="submit">
        <p class="eyebrow">JOIN US</p>
        <h2>注册新账号</h2>
        <label>
          <span>账号（4-16 位字母、数字、下划线）</span>
          <input v-model="account" placeholder="请输入账号" autocomplete="username" />
        </label>
        <label>
          <span>密码（至少 8 位）</span>
          <input v-model="password" type="password" placeholder="请输入密码" autocomplete="new-password" />
        </label>
        <label>
          <span>确认密码</span>
          <input v-model="checkPassword" type="password" placeholder="请再次输入密码" autocomplete="new-password" />
        </label>
        <p v-if="error" class="auth-error">{{ error }}</p>
        <button class="auth-submit" type="submit" :disabled="loading">
          {{ loading ? '注册中…' : '注 册' }}
        </button>
        <p class="auth-switch">已有账号？<a @click.prevent="emit('switch-login')">直接登录</a></p>
      </form>
      <footer class="site-footer">
        <span>© {{ new Date().getFullYear() }} CZ AI WORKSPACE</span>
        <span>账号 · 安全 · 权限</span>
      </footer>
    </section>
  </main>
</template>
