<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { auth, logout as storeLogout } from '../store/auth'
import * as userApi from '../services/user'
import { API_BASE_URL } from '../services/http'

const emit = defineEmits(['back', 'logout'])

const profileForm = ref({ userName: '', userProfile: '' })
const pwdForm = ref({ oldPassword: '', newPassword: '', checkPassword: '' })
const vipCode = ref('')
const notice = ref('')
const noticeType = ref('ok')
const busy = ref(false)
const avatarInput = ref(null)
const emailForm = ref({ email: '', verifyCode: '' })
const emailCountdown = ref(0)
let emailTimer = null

const isAdmin = computed(() => auth.user?.userRole === 'admin')
const isVip = computed(() => auth.user?.userRole === 'vip')
const emailBoundText = computed(() => auth.user?.email || '未绑定')
const vipExpireText = computed(() => {
  const t = auth.user?.vipExpireTime
  return t ? new Date(t).toLocaleString() : '未开通'
})
const initial = computed(() => (auth.user?.userName || auth.user?.userAccount || 'U').slice(0, 1).toUpperCase())
const avatarPreview = computed(() => {
  const url = auth.user?.userAvatar
  if (!url) return ''
  return url.startsWith('http') ? url : `${API_BASE_URL}${url}`
})

// 管理员面板
const userList = ref([])
const page = ref({ current: 1, pageSize: 10, total: 0 })
const searchAccount = ref('')
const addUserForm = ref({ userAccount: '', userName: '', userRole: 'user' })
const vipGenForm = ref({ count: 5, durationDays: 30 })
const generatedCodes = ref([])

onMounted(() => {
  syncProfileForm()
  if (isAdmin.value) loadUsers()
})

onUnmounted(() => clearInterval(emailTimer))

function syncProfileForm() {
  profileForm.value = {
    userName: auth.user?.userName || '',
    userProfile: auth.user?.userProfile || '',
  }
}

function showNotice(msg, type = 'ok') {
  notice.value = msg
  noticeType.value = type
}

async function saveProfile() {
  busy.value = true
  try {
    await userApi.updateUser({ id: auth.user.id, ...profileForm.value })
    auth.user = { ...auth.user, ...profileForm.value }
    showNotice('资料已保存')
  } catch (e) {
    showNotice(e.message || '保存失败', 'error')
  } finally {
    busy.value = false
  }
}

function triggerAvatarPick() {
  avatarInput.value?.click()
}

function validateEmail(value) {
  return /^[\w.+-]+@[\w-]+(\.[\w-]+)+$/.test(value.trim())
}

function startEmailCountdown() {
  emailCountdown.value = 60
  clearInterval(emailTimer)
  emailTimer = setInterval(() => {
    emailCountdown.value--
    if (emailCountdown.value <= 0) clearInterval(emailTimer)
  }, 1000)
}

async function sendBindCode() {
  if (!validateEmail(emailForm.value.email)) {
    showNotice('邮箱格式不正确', 'error')
    return
  }
  busy.value = true
  try {
    await userApi.sendEmailCode({ email: emailForm.value.email.trim() })
    showNotice('验证码已发送，请查收邮箱')
    startEmailCountdown()
  } catch (e) {
    showNotice(e.message || '发送失败', 'error')
  } finally {
    busy.value = false
  }
}

async function bindEmail() {
  if (!emailForm.value.verifyCode.trim()) {
    showNotice('请输入验证码', 'error')
    return
  }
  busy.value = true
  try {
    await userApi.bindEmail({
      email: emailForm.value.email.trim(),
      verifyCode: emailForm.value.verifyCode.trim(),
    })
    auth.user = await userApi.getCurrentUser()
    emailForm.value = { email: '', verifyCode: '' }
    showNotice('邮箱绑定成功')
  } catch (e) {
    showNotice(e.message || '绑定失败', 'error')
  } finally {
    busy.value = false
  }
}

async function onAvatarChange(event) {
  const file = event.target.files?.[0]
  if (!file) return
  if (!/^image\/(jpeg|png|gif|webp)$/.test(file.type)) {
    showNotice('仅支持 jpg / png / gif / webp 图片', 'error')
    event.target.value = ''
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    showNotice('图片不能超过 5MB', 'error')
    event.target.value = ''
    return
  }
  busy.value = true
  try {
    const formData = new FormData()
    formData.append('file', file)
    await userApi.uploadAvatar(formData)
    auth.user = await userApi.getCurrentUser()
    showNotice('头像已更新')
  } catch (e) {
    showNotice(e.message || '头像上传失败', 'error')
  } finally {
    busy.value = false
    event.target.value = ''
  }
}

async function savePassword() {
  if (!pwdForm.value.oldPassword || !pwdForm.value.newPassword) {
    showNotice('请填写完整密码信息', 'error')
    return
  }
  if (pwdForm.value.newPassword.length < 8) {
    showNotice('新密码长度至少 8 位', 'error')
    return
  }
  if (pwdForm.value.newPassword !== pwdForm.value.checkPassword) {
    showNotice('两次输入的新密码不一致', 'error')
    return
  }
  busy.value = true
  try {
    await userApi.updatePassword(pwdForm.value)
    pwdForm.value = { oldPassword: '', newPassword: '', checkPassword: '' }
    showNotice('密码已修改，请重新登录')
    await storeLogout()
    emit('logout')
  } catch (e) {
    showNotice(e.message || '修改失败', 'error')
  } finally {
    busy.value = false
  }
}

async function exchangeVip() {
  if (!vipCode.value.trim()) {
    showNotice('请输入兑换码', 'error')
    return
  }
  busy.value = true
  try {
    await userApi.exchangeVipCode({ vipCode: vipCode.value.trim() })
    vipCode.value = ''
    auth.user = await userApi.getCurrentUser()
    showNotice('VIP 兑换成功，角色已升级为 vip')
  } catch (e) {
    showNotice(e.message || '兑换失败', 'error')
  } finally {
    busy.value = false
  }
}

async function loadUsers() {
  try {
    const data = await userApi.listUsers({
      current: page.value.current,
      pageSize: page.value.pageSize,
      userAccount: searchAccount.value.trim() || undefined,
    })
    userList.value = data.records
    page.value.total = data.total
  } catch (e) {
    showNotice(e.message || '加载用户列表失败', 'error')
  }
}

async function changeRole(row) {
  try {
    await userApi.updateUserRole({ id: row.id, userRole: row.userRole })
    showNotice(`用户 ${row.userAccount} 角色已更新为 ${row.userRole}`)
  } catch (e) {
    showNotice(e.message || '角色更新失败', 'error')
    loadUsers()
  }
}

async function removeUser(row) {
  if (!window.confirm(`确认删除用户 ${row.userAccount}？该操作不可恢复。`)) return
  try {
    await userApi.deleteUser({ id: row.id })
    showNotice(`用户 ${row.userAccount} 已删除`)
    loadUsers()
  } catch (e) {
    showNotice(e.message || '删除失败', 'error')
  }
}

async function addUser() {
  if (!addUserForm.value.userAccount.trim()) {
    showNotice('请输入账号', 'error')
    return
  }
  try {
    await userApi.addUser({ ...addUserForm.value, userAccount: addUserForm.value.userAccount.trim() })
    addUserForm.value = { userAccount: '', userName: '', userRole: 'user' }
    showNotice('用户已创建，初始密码为 12345678')
    loadUsers()
  } catch (e) {
    showNotice(e.message || '创建失败', 'error')
  }
}

async function generateCodes() {
  try {
    generatedCodes.value = await userApi.generateVipCodes(vipGenForm.value)
    showNotice(`已生成 ${generatedCodes.value.length} 个兑换码`)
  } catch (e) {
    showNotice(e.message || '生成失败', 'error')
  }
}
</script>

<template>
  <main class="shell app-profile">
    <div class="space-field" aria-hidden="true"><i v-for="n in 42" :key="n"></i></div>
    <div class="nebula nebula-1" aria-hidden="true"></div>
    <div class="nebula nebula-2" aria-hidden="true"></div>
    <div class="planet" aria-hidden="true"><span class="planet-ring"></span></div>
    <div class="shooting-star" aria-hidden="true"></div>
    <section class="profile-page">
      <header class="site-nav">
        <button class="brand auth-back" @click="emit('back')">← 返回首页</button>
        <span class="nav-status"><b></b> PERSONAL CENTER</span>
      </header>

      <p v-if="notice" class="profile-notice" :class="noticeType">{{ notice }}</p>

      <div class="profile-layout">
        <aside class="profile-card">
          <div class="profile-avatar-wrap">
            <div class="profile-avatar">
              <img v-if="avatarPreview" :src="avatarPreview" alt="头像" />
              <span v-else>{{ initial }}</span>
            </div>
            <button type="button" class="avatar-plus" title="上传本地图片作为头像" :disabled="busy" @click="triggerAvatarPick">+</button>
            <input ref="avatarInput" type="file" accept="image/jpeg,image/png,image/gif,image/webp" hidden @change="onAvatarChange" />
          </div>
          <h3>{{ auth.user?.userName || auth.user?.userAccount }}</h3>
          <p class="profile-account">@{{ auth.user?.userAccount }}</p>
          <div class="profile-tags">
            <span class="tag" :class="auth.user?.userRole">{{ auth.user?.userRole }}</span>
            <span v-if="isVip" class="tag vip">VIP</span>
          </div>
          <dl class="profile-meta">
            <dt>邮箱</dt>
            <dd>{{ emailBoundText }}</dd>
            <dt>会员过期</dt>
            <dd>{{ vipExpireText }}</dd>
            <dt>会员编号</dt>
            <dd>{{ auth.user?.vipNumber || '—' }}</dd>
            <dt>注册时间</dt>
            <dd>{{ auth.user?.createTime ? new Date(auth.user.createTime).toLocaleDateString() : '—' }}</dd>
          </dl>
        </aside>

        <div class="profile-main">
          <section class="panel">
            <h4>基本资料</h4>
            <label><span>昵称</span><input v-model="profileForm.userName" placeholder="请输入昵称" /></label>
            <label><span>个人简介</span><textarea v-model="profileForm.userProfile" rows="2" placeholder="介绍一下自己"></textarea></label>
            <button class="profile-btn" :disabled="busy" @click="saveProfile">保存资料</button>
          </section>

          <section class="panel">
            <h4>修改密码</h4>
            <label><span>原密码</span><input v-model="pwdForm.oldPassword" type="password" autocomplete="current-password" /></label>
            <label><span>新密码</span><input v-model="pwdForm.newPassword" type="password" autocomplete="new-password" /></label>
            <label><span>确认新密码</span><input v-model="pwdForm.checkPassword" type="password" autocomplete="new-password" /></label>
            <button class="profile-btn" :disabled="busy" @click="savePassword">修改密码</button>
          </section>

          <section class="panel">
            <h4>邮箱绑定</h4>
            <p class="panel-tip">当前邮箱：{{ emailBoundText }}。绑定后可用于找回密码。</p>
            <div class="vip-row">
              <input v-model="emailForm.email" type="email" placeholder="请输入邮箱" />
              <button class="profile-btn slim" :disabled="busy || emailCountdown > 0" @click="sendBindCode">
                {{ emailCountdown > 0 ? `${emailCountdown}s 后重发` : '发送验证码' }}
              </button>
            </div>
            <div class="vip-row">
              <input v-model="emailForm.verifyCode" placeholder="请输入 6 位验证码" />
              <button class="profile-btn slim" :disabled="busy" @click="bindEmail">绑定邮箱</button>
            </div>
          </section>

          <section class="panel">
            <h4>VIP 会员</h4>
            <p class="panel-tip">当前角色：{{ auth.user?.userRole }}，会员状态：{{ isVip ? '已开通（' + vipExpireText + '）' : '未开通' }}</p>
            <div class="vip-row">
              <input v-model="vipCode" placeholder="请输入 VIP 兑换码" />
              <button class="profile-btn" :disabled="busy" @click="exchangeVip">兑换</button>
            </div>
          </section>

          <section v-if="isAdmin" class="panel admin-panel">
            <h4>管理员面板 · 新增用户</h4>
            <div class="grid-3">
              <label><span>账号</span><input v-model="addUserForm.userAccount" placeholder="4-16 位" /></label>
              <label><span>昵称</span><input v-model="addUserForm.userName" placeholder="可选" /></label>
              <label><span>角色</span>
                <select v-model="addUserForm.userRole">
                  <option value="user">user</option>
                  <option value="vip">vip</option>
                  <option value="admin">admin</option>
                </select>
              </label>
            </div>
            <button class="profile-btn" @click="addUser">创建用户（初始密码 12345678）</button>

            <h4 class="admin-sub">用户列表</h4>
            <div class="admin-toolbar">
              <input v-model="searchAccount" placeholder="按账号搜索" @keyup.enter="page.current = 1; loadUsers()" />
              <button class="profile-btn slim" @click="page.current = 1; loadUsers()">查询</button>
            </div>
            <div class="admin-table-wrap">
              <table class="admin-table">
                <thead>
                  <tr><th>ID</th><th>账号</th><th>昵称</th><th>角色</th><th>VIP 过期</th><th>操作</th></tr>
                </thead>
                <tbody>
                  <tr v-for="row in userList" :key="row.id">
                    <td>{{ row.id }}</td>
                    <td>{{ row.userAccount }}</td>
                    <td>{{ row.userName || '—' }}</td>
                    <td>
                      <select :value="row.userRole" @change="row.userRole = $event.target.value; changeRole(row)">
                        <option value="user">user</option>
                        <option value="vip">vip</option>
                        <option value="admin">admin</option>
                      </select>
                    </td>
                    <td>{{ row.vipExpireTime ? new Date(row.vipExpireTime).toLocaleDateString() : '—' }}</td>
                    <td><button class="danger" @click="removeUser(row)">删除</button></td>
                  </tr>
                  <tr v-if="!userList.length"><td colspan="6" class="empty">暂无数据</td></tr>
                </tbody>
              </table>
            </div>
            <div class="admin-pager">
              <button :disabled="page.current <= 1" @click="page.current--; loadUsers()">上一页</button>
              <span>第 {{ page.current }} 页 / 共 {{ Math.max(1, Math.ceil(page.total / page.pageSize)) }} 页（{{ page.total }} 条）</span>
              <button :disabled="page.current * page.pageSize >= page.total" @click="page.current++; loadUsers()">下一页</button>
            </div>

            <h4 class="admin-sub">生成 VIP 兑换码</h4>
            <div class="grid-3">
              <label><span>数量</span><input v-model.number="vipGenForm.count" type="number" min="1" max="100" /></label>
              <label><span>时长（天）</span><input v-model.number="vipGenForm.durationDays" type="number" min="1" max="3650" /></label>
            </div>
            <button class="profile-btn" @click="generateCodes">生成兑换码</button>
            <div v-if="generatedCodes.length" class="codes-box">
              <p v-for="c in generatedCodes" :key="c" class="code-line">{{ c }}</p>
            </div>
          </section>
        </div>
      </div>

      <button class="profile-logout" @click="storeLogout(); emit('logout')">退出登录</button>
      <footer class="site-footer">
        <span>© {{ new Date().getFullYear() }} CZ AI WORKSPACE</span>
        <span>账号 · 安全 · 权限</span>
      </footer>
    </section>
  </main>
</template>
