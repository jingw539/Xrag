<template>
  <div class="login-page">
    <div class="orb orb-1"></div>
    <div class="orb orb-2"></div>
    <div class="orb orb-3"></div>

    <div class="login-card">
      <div class="logo-section">
        <h1 class="system-name">胸部 X 光智能辅助诊断系统</h1>
        <p class="system-slogan">AI-Powered Chest X-Ray Diagnostic Platform</p>
      </div>

      <div class="form-section">
        <el-form
          :model="loginForm"
          :rules="rules"
          ref="loginFormRef"
          @keyup.enter="handleLogin"
          class="login-form"
        >
          <el-form-item prop="username">
            <el-input
              v-model="loginForm.username"
              placeholder="请输入用户名"
              prefix-icon="User"
              size="large"
            />
          </el-form-item>
          <el-form-item prop="password">
            <el-input
              v-model="loginForm.password"
              type="password"
              placeholder="请输入密码"
              prefix-icon="Lock"
              size="large"
              show-password
            />
          </el-form-item>
          <el-form-item style="margin-bottom:0">
            <el-button
              type="primary"
              size="large"
              class="login-btn"
              :loading="loading"
              @click="handleLogin"
            >
              登录
            </el-button>
          </el-form-item>
        </el-form>
      </div>

      <div class="footer-section">
        <p class="copyright">© 2026 胸部X光智能辅助诊断系统</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { login } from '@/api/auth'
import { ElMessage } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()
const loginFormRef = ref(null)
const loading = ref(false)
const loginForm = ref({ username: '', password: '' })

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const handleLogin = async () => {
  await loginFormRef.value.validate()
  loading.value = true
  try {
    const res = await login(loginForm.value)
    userStore.setToken(res.data.accessToken, res.data.refreshToken)
    userStore.setUserInfo(res.data.userInfo)
    ElMessage.success('登录成功')
    router.push('/')
  } catch (_) {
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
  width: 100vw;
  position: fixed;
  inset: 0;
  overflow: hidden;
  padding: 32px;
  background:
    radial-gradient(circle at 12% 16%, rgba(123, 137, 154, 0.14), transparent 24%),
    radial-gradient(circle at 86% 14%, rgba(148, 163, 181, 0.12), transparent 24%),
    linear-gradient(135deg, #ccd5de 0%, #d3dbe4 45%, #ced7e0 100%);
}

.login-page::before {
  content: '';
  position: absolute;
  inset: 0;
  background:
    linear-gradient(180deg, rgba(255,255,255,0.08), rgba(255,255,255,0.02)),
    radial-gradient(circle at 50% 20%, rgba(255,255,255,0.08), transparent 42%);
  pointer-events: none;
}

.login-page::after {
  content: '';
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255,255,255,0.05) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255,255,255,0.05) 1px, transparent 1px);
  background-size: 88px 88px;
  mask-image: linear-gradient(180deg, rgba(0,0,0,0.16), transparent 82%);
  opacity: 0.12;
  pointer-events: none;
}

.orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(120px);
  pointer-events: none;
  opacity: 0.12;
}

.orb-1 {
  width: 360px;
  height: 360px;
  background: rgba(123, 145, 173, 0.12);
  top: -120px;
  left: -80px;
}

.orb-2 {
  width: 280px;
  height: 280px;
  background: rgba(148, 169, 194, 0.1);
  bottom: -100px;
  right: -50px;
}

.orb-3 {
  width: 220px;
  height: 220px;
  background: rgba(202, 213, 227, 0.18);
  top: 22%;
  right: 18%;
}

.login-card {
  width: 100%;
  max-width: 438px;
  padding: 42px 34px 28px;
  position: relative;
  z-index: 1;
  border-radius: 28px;
  border: 1px solid rgba(214, 223, 232, 0.86);
  background: linear-gradient(180deg, rgba(232,237,242,0.94), rgba(226,232,238,0.94));
  box-shadow:
    0 10px 26px rgba(88, 103, 124, 0.08),
    inset 0 1px 0 rgba(255,255,255,0.28);
  backdrop-filter: blur(10px) saturate(102%);
}

.login-card::before {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: inherit;
  padding: 1px;
  background: linear-gradient(180deg, rgba(245,248,250,0.46), rgba(203,214,226,0.18));
  -webkit-mask: linear-gradient(#fff 0 0) content-box, linear-gradient(#fff 0 0);
  -webkit-mask-composite: xor;
  mask-composite: exclude;
  pointer-events: none;
}

.logo-section {
  text-align: center;
  margin-bottom: 30px;
}

.logo-icon {
  width: 62px;
  height: 62px;
  margin: 0 auto 20px;
  border-radius: 18px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #6e86a3;
  background: linear-gradient(180deg, rgba(248,250,252,0.96), rgba(237,242,247,0.94));
  border: 1px solid rgba(211, 220, 230, 0.92);
  box-shadow: 0 10px 22px rgba(115, 130, 149, 0.1);
}

.system-name {
  margin: 0;
  font-size: 27px;
  line-height: 1.3;
  font-weight: 700;
  letter-spacing: .02em;
  color: #3b4958;
}

.system-slogan {
  margin: 10px auto 0;
  max-width: 300px;
  font-size: 13px;
  line-height: 1.7;
  color: #788796;
}

.form-section {
  margin-top: 8px;
}

:deep(.login-form .el-form-item) {
  margin-bottom: 16px;
}

:deep(.login-form .el-input__wrapper) {
  min-height: 54px;
  padding: 0 16px;
  border-radius: 16px;
  background: rgba(233,238,243,0.98) !important;
  box-shadow:
    inset 0 0 0 1px rgba(186, 198, 209, 0.96) !important;
}

:deep(.login-form .el-input__wrapper:hover) {
  box-shadow:
    inset 0 0 0 1px rgba(174, 187, 200, 0.98) !important;
}

:deep(.login-form .el-input__wrapper.is-focus) {
  box-shadow:
    inset 0 0 0 1px #7a8ea4,
    0 0 0 3px rgba(122,142,164,0.12) !important;
}

:deep(.login-form .el-input__prefix-inner) {
  color: #8797aa;
}

:deep(.login-form .el-input__inner) {
  color: #435367;
  font-size: 14px;
}

:deep(.login-form .el-input__inner::placeholder) {
  color: #8d9cae;
}

.login-btn {
  width: 100%;
  height: 52px;
  border: none;
  border-radius: 16px;
  background: linear-gradient(180deg, #7b8da1 0%, #6b7d90 100%);
  box-shadow:
    0 8px 18px rgba(97, 114, 136, 0.12),
    inset 0 1px 0 rgba(255,255,255,0.18);
  color: #fff;
  letter-spacing: .08em;
  font-weight: 700;
}

.login-btn:hover,
.login-btn:focus {
  background: linear-gradient(180deg, #8395a9 0%, #728396 100%);
}

.footer-section {
  margin-top: 20px;
  text-align: center;
}

.copyright {
  margin: 0;
  color: #7f8d9d;
  font-size: 12px;
}

.login-page {
  background: #0E1621 !important;
}

.login-page::before,
.login-page::after,
.orb,
.orb-1,
.orb-2,
.orb-3 {
  opacity: 0 !important;
}

.login-card {
  background: #B7C5D6 !important;
  border: 1px solid #6F86A6 !important;
  box-shadow: none !important;
  backdrop-filter: none !important;
  max-width: 460px;
  padding: 36px 34px 26px;
}

.login-card::before {
  background: none !important;
}

.system-name {
  color: #09111a !important;
  font-weight: 700;
  font-size: 24px;
  line-height: 1.25;
  letter-spacing: 0;
}

.system-slogan,
.copyright {
  color: #42556f !important;
}

.system-slogan {
  margin-top: 8px;
  font-size: 12px;
  opacity: 0.72;
}

.copyright {
  color: #5b6f89 !important;
}

:deep(.login-form .el-input__wrapper) {
  background: #E9EEF5 !important;
  box-shadow: inset 0 0 0 1px #6F86A6 !important;
  min-height: 56px;
  border-radius: 14px;
}

:deep(.login-form .el-input__wrapper:hover) {
  background: #edf2f7 !important;
  box-shadow: inset 0 0 0 1px #506884 !important;
}

:deep(.login-form .el-input__wrapper.is-focus) {
  background: #f2f5f9 !important;
  box-shadow: inset 0 0 0 2px #2C3E57, 0 0 0 2px rgba(111, 134, 166, 0.16) !important;
}

:deep(.login-form .el-input__prefix-inner),
:deep(.login-form .el-input__inner::placeholder) {
  color: #607792 !important;
}

:deep(.login-form .el-input__inner) {
  color: #101b28 !important;
  font-size: 15px;
}

:deep(.login-form .el-form-item__error) {
  color: #7f2f3b !important;
  font-size: 12px;
  padding-top: 4px;
}

.login-btn,
.login-btn:hover,
.login-btn:focus {
  background: #2C3E57 !important;
  color: #E9EEF5 !important;
  box-shadow: none !important;
  height: 56px;
  border-radius: 14px;
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.form-section {
  margin-top: 18px;
}

:deep(.login-form .el-form-item) {
  margin-bottom: 18px;
}

.footer-section {
  margin-top: 18px;
}

/* ===== Final login professional dark glass ===== */
.login-page {
  background: #0E1621 !important;
}

.login-page::before,
.login-page::after,
.orb,
.orb-1,
.orb-2,
.orb-3 {
  opacity: 0 !important;
}

.login-card {
  background: rgba(15, 25, 40, 0.85) !important;
  border: 1px solid rgba(111, 134, 166, 0.34) !important;
  box-shadow: 0 18px 48px rgba(0,0,0,0.28) !important;
  backdrop-filter: blur(18px) !important;
}

.login-card::before {
  display: none !important;
}

.system-name {
  color: #E9EEF5 !important;
}

.system-slogan,
.copyright {
  color: rgba(208, 220, 240, 0.68) !important;
}

:deep(.login-form .el-input__wrapper) {
  background: rgba(233, 238, 245, 0.08) !important;
  box-shadow: inset 0 0 0 1px rgba(111, 134, 166, 0.42) !important;
}

:deep(.login-form .el-input__wrapper:hover) {
  background: rgba(233, 238, 245, 0.1) !important;
  box-shadow: inset 0 0 0 1px rgba(74, 158, 255, 0.34) !important;
}

:deep(.login-form .el-input__wrapper.is-focus) {
  background: rgba(233, 238, 245, 0.12) !important;
  box-shadow: inset 0 0 0 1px #4A9EFF, 0 0 0 2px rgba(74, 158, 255, 0.14) !important;
}

:deep(.login-form .el-input__prefix-inner),
:deep(.login-form .el-input__inner::placeholder) {
  color: rgba(208, 220, 240, 0.52) !important;
}

:deep(.login-form .el-input__inner) {
  color: #E9EEF5 !important;
}

.login-btn,
.login-btn:hover,
.login-btn:focus {
  background: #4A9EFF !important;
  border-color: #4A9EFF !important;
  color: #ffffff !important;
  box-shadow: none !important;
}
</style>



