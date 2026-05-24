<template>
  <div class="login-page">
    <div class="login-brand">
      <h1>ETF数据分析平台</h1>
      <p>聚合ETF行情、指标、资金流与五维共振分析</p>
    </div>

    <el-card class="login-card">
      <template #header>
        <div class="login-title">
          <h2>欢迎登录</h2>
          <span>{{ loginType === 'admin' ? '管理员入口' : '账号登录' }}</span>
        </div>
      </template>
      <el-radio-group v-model="loginType" class="login-switch">
        <el-radio-button label="user">普通用户登录</el-radio-button>
        <el-radio-button label="admin">管理员入口</el-radio-button>
      </el-radio-group>
      <el-form :model="form" label-width="70px" @submit.prevent>
        <el-form-item label="账号">
          <el-input v-model="form.username" placeholder="请输入账号" clearable />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" clearable />
        </el-form-item>
        <el-form-item label="验证码">
          <div class="captcha-wrap">
            <el-input v-model="form.captcha" placeholder="请输入4位验证码" maxlength="4" clearable />
            <div class="captcha-code" @click="refreshCaptcha" :title="'点击刷新验证码'">{{ captchaCode }}</div>
          </div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" class="login-btn" @click="handleLogin">登录</el-button>
        </el-form-item>
        <div v-if="loginType === 'user'" class="register-entry">
          <span>还没有账号？</span>
          <el-button link type="primary" :disabled="!registerEnabled" @click="openRegister">立即注册</el-button>
          <span v-if="!registerEnabled" class="register-closed-tip">（当前未开放）</span>
        </div>
        <div class="tips">提示：点击验证码可刷新</div>
      </el-form>
    </el-card>

    <el-dialog v-model="registerVisible" title="普通用户注册" width="420px" destroy-on-close>
      <el-form :model="registerForm" label-width="90px" @submit.prevent>
        <el-form-item label="账号">
          <el-input v-model="registerForm.username" placeholder="4-32位字母、数字或下划线" maxlength="32" clearable />
        </el-form-item>
        <el-form-item label="显示名称">
          <el-input v-model="registerForm.displayName" placeholder="可选，不填则默认使用账号" maxlength="32" clearable />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="registerForm.password" type="password" show-password placeholder="请输入密码" clearable />
        </el-form-item>
        <el-form-item label="确认密码">
          <el-input v-model="registerForm.confirmPassword" type="password" show-password placeholder="请再次输入密码" clearable />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="registerVisible = false">取消</el-button>
          <el-button type="primary" :loading="registering" @click="handleRegister">注册</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import authApi from '@/api/auth'

const router = useRouter()
const loading = ref(false)
const registering = ref(false)
const loginType = ref('user')
const registerVisible = ref(false)
const registerEnabled = ref(true)
const captchaCode = ref('')
const form = reactive({
  username: '',
  password: '',
  captcha: ''
})
const registerForm = reactive({
  username: '',
  displayName: '',
  password: '',
  confirmPassword: ''
})

function refreshCaptcha() {
  captchaCode.value = String(Math.floor(1000 + Math.random() * 9000))
}

async function handleLogin() {
  if (!form.username.trim() || !form.password.trim()) {
    ElMessage.warning('请输入账号和密码')
    return
  }

  if (!form.captcha.trim()) {
    ElMessage.warning('请输入验证码')
    return
  }

  if (form.captcha.trim() !== captchaCode.value) {
    ElMessage.error('验证码错误，请重试')
    form.captcha = ''
    refreshCaptcha()
    return
  }

  try {
    loading.value = true
    const res = await authApi.login({
      loginType: loginType.value,
      username: form.username,
      password: form.password
    })

    const account = res?.data?.username || form.username
    const displayName = res?.data?.displayName || account
    localStorage.setItem('etf_logged_in', '1')
    localStorage.setItem('etf_username', displayName)
    localStorage.setItem('etf_account', account)
    localStorage.setItem('etf_display_name', displayName)
    localStorage.setItem('etf_login_type', res?.data?.loginType || loginType.value)
    ElMessage.success('登录成功')
    const redirectPath = loginType.value === 'user' ? '/user_menu' : '/dashboard'
    router.replace(redirectPath)
  } catch (error) {
    // 错误消息由请求拦截器统一提示
    refreshCaptcha()
    form.captcha = ''
  } finally {
    loading.value = false
  }
}

function openRegister() {
  if (!registerEnabled.value) {
    ElMessage.warning('当前未开放新用户注册，请联系管理员')
    return
  }
  registerVisible.value = true
}

function resetRegisterForm() {
  registerForm.username = ''
  registerForm.displayName = ''
  registerForm.password = ''
  registerForm.confirmPassword = ''
}

async function handleRegister() {
  if (!registerForm.username.trim() || !registerForm.password.trim() || !registerForm.confirmPassword.trim()) {
    ElMessage.warning('请完整填写注册信息')
    return
  }

  try {
    registering.value = true
    await authApi.register({
      username: registerForm.username,
      displayName: registerForm.displayName,
      password: registerForm.password,
      confirmPassword: registerForm.confirmPassword
    })
    ElMessage.success('注册成功，请使用普通用户登录')
    loginType.value = 'user'
    form.username = registerForm.username
    form.password = ''
    form.captcha = ''
    registerVisible.value = false
    resetRegisterForm()
    refreshCaptcha()
  } finally {
    registering.value = false
  }
}

onMounted(() => {
  refreshCaptcha()
  authApi.registerEnabled()
    .then((res) => {
      registerEnabled.value = !!res?.data?.enabled
    })
    .catch(() => {
      registerEnabled.value = true
    })
  if (localStorage.getItem('etf_logged_in') === '1') {
    const redirectPath = localStorage.getItem('etf_login_type') === 'user' ? '/user_menu' : '/dashboard'
    router.replace(redirectPath)
  }
})
</script>

<style scoped>
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 56px;
  padding: 24px;
  box-sizing: border-box;
  background:
    radial-gradient(circle at 15% 20%, rgba(14, 116, 144, 0.3), transparent 36%),
    radial-gradient(circle at 85% 80%, rgba(20, 184, 166, 0.25), transparent 42%),
    linear-gradient(130deg, #dbeafe, #f8fafc 42%, #ecfeff);
}

.login-brand {
  max-width: 460px;
  color: #0f172a;
}

.login-brand h1 {
  margin: 0;
  font-size: 40px;
  line-height: 1.2;
  letter-spacing: 1px;
}

.login-brand p {
  margin: 14px 0 0;
  font-size: 15px;
  color: #334155;
  line-height: 1.7;
}

.login-card {
  width: 460px;
  border-radius: 16px;
  border: 1px solid rgba(255, 255, 255, 0.6);
  background: rgba(255, 255, 255, 0.78);
  backdrop-filter: blur(8px);
  box-shadow: 0 24px 56px rgba(15, 23, 42, 0.2);
}

.login-title {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.login-title h2 {
  margin: 0;
  font-size: 24px;
  color: #0f172a;
}

.login-title span {
  font-size: 13px;
  color: #475569;
}

.login-switch {
  display: flex;
  margin-bottom: 18px;
}

.login-switch :deep(.el-radio-button__inner) {
  min-width: 140px;
}

.captcha-wrap {
  width: 100%;
  display: grid;
  grid-template-columns: 1fr 120px;
  gap: 8px;
}

.captcha-code {
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  font-weight: 700;
  letter-spacing: 3px;
  color: #0f172a;
  background: linear-gradient(135deg, #a7f3d0, #67e8f9);
  border: 1px solid #5eead4;
  cursor: pointer;
  user-select: none;
  transition: all 0.2s ease;
}

.captcha-code:hover {
  transform: translateY(-1px);
  box-shadow: 0 8px 14px rgba(45, 212, 191, 0.35);
}

.login-btn {
  width: 100%;
  height: 38px;
  font-weight: 600;
}

.tips {
  margin-top: -8px;
  font-size: 12px;
  color: #64748b;
}

.register-entry {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 4px;
  margin-top: -8px;
  margin-bottom: 8px;
  font-size: 12px;
  color: #64748b;
}

.register-closed-tip {
  color: #ef4444;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

@media (max-width: 900px) {
  .login-page {
    flex-direction: column;
    gap: 24px;
  }

  .login-brand {
    text-align: center;
  }

  .login-brand h1 {
    font-size: 30px;
  }

  .login-card {
    width: min(460px, 100%);
  }
}
</style>
