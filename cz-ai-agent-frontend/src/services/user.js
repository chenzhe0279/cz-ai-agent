import { http } from './http'

export function register(data) {
  return http.post('/user/register', data)
}

export function sendRegisterCode(data) {
  return http.post('/user/register/code', data)
}

export function login(data) {
  return http.post('/user/login', data)
}

export function sendLoginCode(data) {
  return http.post('/user/login/code/send', data)
}

export function loginByEmailCode(data) {
  return http.post('/user/login/code', data)
}

export function logout() {
  return http.post('/user/logout')
}

export function getCurrentUser() {
  return http.get('/user/current')
}

export function updateUser(data) {
  return http.post('/user/update', data)
}

export function updatePassword(data) {
  return http.post('/user/update/password', data)
}

export function exchangeVipCode(data) {
  return http.post('/user/vip/exchange', data)
}

export function listUsers(params) {
  return http.get('/user/list', { params })
}

export function addUser(data) {
  return http.post('/user/add', data)
}

export function deleteUser(data) {
  return http.post('/user/delete', data)
}

export function updateUserRole(data) {
  return http.post('/user/update/role', data)
}

export function generateVipCodes(data) {
  return http.post('/user/vip/code/generate', data)
}

export function uploadAvatar(formData) {
  return http.post('/user/avatar/upload', formData)
}

export function sendEmailCode(data) {
  return http.post('/user/email/send-code', data)
}

export function bindEmail(data) {
  return http.post('/user/email/bind', data)
}

export function sendPasswordResetCode(data) {
  return http.post('/user/password/reset/code', data)
}

export function resetPassword(data) {
  return http.post('/user/password/reset', data)
}
