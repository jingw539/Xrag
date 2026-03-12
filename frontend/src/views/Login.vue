<template>
  <div class="login-page">
    <div class="orb orb-1"></div>
    <div class="orb orb-2"></div>
    <div class="orb orb-3"></div>

    <div class="login-card">
      <div class="logo-section">
        <div class="logo-icon">XR</div>
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
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '登录失败')
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
    radial-gradient(circle at 15% 20%, rgba(74, 158, 255, 0.12), transparent 35%),
    radial-gradient(circle at 85% 15%, rgba(111, 134, 166, 0.18), transparent 40%),
    linear-gradient(180deg, #0b1220 0%, #0d1420 100%);
}

.login-page::before {
  content: '';
  position: absolute;
  inset: 0;
  background:
    linear-gradient(180deg, rgba(255,255,255,0.04), transparent 70%),
    radial-gradient(circle at 50% 15%, rgba(255,255,255,0.06), transparent 45%);
  pointer-events: none;
}

.login-page::after {
  content: '';
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255,255,255,0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255,255,255,0.04) 1px, transparent 1px);
  background-size: 96px 96px;
  mask-image: linear-gradient(180deg, rgba(0,0,0,0.25), transparent 80%);
  opacity: 0.18;
  pointer-events: none;
}

.orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(120px);
  pointer-events: none;
  opacity: 0.18;
}

.orb-1 {
  width: 320px;
  height: 320px;
  background: rgba(74, 158, 255, 0.2);
  top: -120px;
  left: -60px;
}

.orb-2 {
  width: 260px;
  height: 260px;
  background: rgba(111, 134, 166, 0.25);
  bottom: -100px;
  right: -60px;
}

.orb-3 {
  width: 200px;
  height: 200px;
  background: rgba(208, 220, 240, 0.16);
  top: 24%;
  right: 18%;
}

.login-card {
  width: 100%;
  max-width: 440px;
  padding: 40px 34px 28px;
  position: relative;
  z-index: 1;
  border-radius: 24px;
  border: 1px solid rgba(74, 158, 255, 0.22);
  background: rgba(15, 25, 40, 0.92);
  box-shadow:
    0 24px 60px rgba(0, 0, 0, 0.45),
    0 0 0 1px rgba(74, 158, 255, 0.08) inset;
  backdrop-filter: blur(18px) saturate(120%);
}

.logo-section {
  text-align: center;
  margin-bottom: 26px;
}

.logo-icon {
  width: 56px;
  height: 56px;
  margin: 0 auto 14px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  letter-spacing: 0.2em;
  color: #e9f1ff;
  background:
    linear-gradient(140deg, rgba(74, 158, 255, 0.3), rgba(74, 158, 255, 0.06));
  border: 1px solid rgba(74, 158, 255, 0.35);
  box-shadow:
    0 10px 20px rgba(12, 24, 40, 0.45),
    inset 0 1px 0 rgba(255, 255, 255, 0.08);
}

.system-name {
  margin: 0;
  font-size: 24px;
  line-height: 1.3;
  font-weight: 700;
  letter-spacing: .02em;
  color: var(--xrag-text);
}

.system-slogan {
  margin: 10px auto 0;
  max-width: 320px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--xrag-text-soft);
}

.form-section {
  margin-top: 8px;
}

:deep(.login-form .el-form-item) {
  margin-bottom: 18px;
}

:deep(.login-form .el-input__wrapper) {
  min-height: 52px;
  padding: 0 16px;
  border-radius: 14px;
  background: rgba(233, 238, 245, 0.1) !important;
  box-shadow: inset 0 0 0 1px rgba(111, 134, 166, 0.48) !important;
}

:deep(.login-form .el-input__wrapper:hover) {
  background: rgba(233, 238, 245, 0.1) !important;
  box-shadow: inset 0 0 0 1px rgba(74, 158, 255, 0.34) !important;
}

:deep(.login-form .el-input__wrapper.is-focus) {
  background: rgba(233, 238, 245, 0.12) !important;
  box-shadow: inset 0 0 0 1px #4a9eff, 0 0 0 2px rgba(74, 158, 255, 0.2) !important;
}

:deep(.login-form .el-input__prefix-inner),
:deep(.login-form .el-input__inner::placeholder) {
  color: rgba(208, 220, 240, 0.72) !important;
}

:deep(.login-form .el-input__inner) {
  color: #e9eef5 !important;
  font-size: 15px;
}

:deep(.login-form .el-form-item__error) {
  color: #ff9aa5 !important;
  font-size: 12px;
  padding-top: 4px;
}

.login-btn {
  width: 100%;
  height: 52px;
  border-radius: 14px;
  font-weight: 700;
  letter-spacing: .08em;
  background: linear-gradient(180deg, #4a9eff 0%, #3a86e8 100%);
  border: none;
  color: #fff;
  box-shadow: none;
}

.login-btn:hover,
.login-btn:focus {
  background: linear-gradient(180deg, #5aa7ff 0%, #428ff0 100%);
}

.footer-section {
  margin-top: 16px;
  text-align: center;
}

.copyright {
  margin: 0;
  color: var(--xrag-text-faint);
  font-size: 12px;
}
</style>
