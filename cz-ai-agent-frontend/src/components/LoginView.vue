<script setup>
import { ref } from 'vue'
import { login } from '../store/auth'

const emit = defineEmits(['success', 'switch-register', 'back'])

const account = ref('')
const password = ref('')
const loading = ref(false)
const error = ref('')

async function submit() {
  error.value = ''
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
        <span class="nav-status"><b></b> ACCOUNT ACCESS</span>
      </header>
      <form class="auth-card" @submit.prevent="submit">
        <p class="eyebrow">WELCOME BACK</p>
        <h2>登录你的账号</h2>
        <label>
          <span>账号</span>
          <input v-model="account" placeholder="请输入账号" autocomplete="username" />
        </label>
        <label>
          <span>密码</span>
          <input v-model="password" type="password" placeholder="请输入密码" autocomplete="current-password" />
        </label>
        <p v-if="error" class="auth-error">{{ error }}</p>
        <button class="auth-submit" type="submit" :disabled="loading">
          {{ loading ? '登录中…' : '登 录' }}
        </button>
        <p class="auth-switch">还没有账号？<a @click.prevent="emit('switch-register')">立即注册</a></p>
      </form>
      <footer class="site-footer">
        <span>© {{ new Date().getFullYear() }} CZ AI WORKSPACE</span>
        <span>账号 · 安全 · 权限</span>
      </footer>
    </section>
  </main>
</template>
