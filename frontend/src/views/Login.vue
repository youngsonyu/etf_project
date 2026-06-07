<template>
  <div class="login-page">
    <div class="login-brand">
      <h1>ETF量化分析平台</h1>
      <p>聚合ETF行情、指标、资金流与量化分析</p>
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
        <el-form-item label="手机号">
          <el-input v-model="registerForm.username" placeholder="仅支持手机号注册" maxlength="11" clearable />
        </el-form-item>
        <el-form-item label="用户名">
          <el-input v-model="registerForm.displayName" placeholder="请输入用户名" maxlength="32" clearable />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="registerForm.password" type="password" show-password placeholder="大小写字母+数字组合，8-20位" clearable />
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
    const redirectPath = '/dashboard'
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
  const phoneReg = /^1[3-9]\d{9}$/
  if (!phoneReg.test(registerForm.username.trim())) {
    ElMessage.warning('请输入正确的手机号')
    return
  }

  if (!registerForm.displayName.trim()) {
    ElMessage.warning('请输入用户名')
    return
  }

  const passwordReg = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)[a-zA-Z\d]{8,20}$/
  if (!passwordReg.test(registerForm.password)) {
    ElMessage.warning('密码须为大小写字母+数字组合，8-20位')
    return
  }

  if (!registerForm.confirmPassword.trim()) {
    ElMessage.warning('请再次输入密码')
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
    router.replace('/dashboard')
  }
})
</script>

<style scoped>
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 64px;
  padding: 24px;
  box-sizing: border-box;
  background:
    radial-gradient(circle at 20% 30%, rgba(15, 118, 110, 0.12), transparent 50%),
    radial-gradient(circle at 80% 70%, rgba(8, 145, 178, 0.1), transparent 45%),
    linear-gradient(135deg, #f0fdfa 0%, #f8fafc 40%, #ecfeff 100%);
}

.login-brand {
  max-width: 480px;
  color: #0f172a;
}

.login-brand h1 {
  margin: 0;
  font-size: 42px;
  font-weight: 800;
  line-height: 1.2;
  letter-spacing:1px;
  color: #0f766e;
  text-shadow: 0 2px 4px rgba(15, 118, 110, 0.1);
}

.login-brand p {
  margin: 16px 0 0;
  font-size: 16px;
  color: #475569;
  line-height: 1.7;
}

.login-card {
  width: 440px;
  border-radius: 20px;
  border: 1px solid rgba(255, 255, 255, 0.8);
  background: rgba(255, 255, 255, 0.88);
  backdrop-filter: blur(12px);
  box-shadow: 0 25px 60px rgba(15, 23, 42, 0.15);
  overflow: hidden;
}

.login-card :deep(.el-card__header) {
  background: linear-gradient(135deg, #f0fdfa 0%, #ffffff 100%);
  border-bottom: 1px solid #e4e7ed;
  padding: 18px 20px;
}

.login-title {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.login-title h2 {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  color: #0f172a;
}

.login-title span {
  font-size: 13px;
  color: #64748b;
}

.login-switch {
  display: flex;
  margin-bottom: 20px;
}

.login-switch :deep(.el-radio-button__inner) {
  min-width: 140px;
  border-radius: 8px 8px 0 0;
}

.captcha-wrap {
  width: 100%;
  display: grid;
  grid-template-columns: 1fr 120px;
  gap: 10px;
}

.captcha-code {
  height: 34px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  font-weight: 700;
  letter-spacing: 4px;
  font-size: 16px;
  color: #0f172a;
  background: linear-gradient(145deg, #99f6e40%, #a5f3fc 100%);
  border: 1px solid #5eead4;
  cursor: pointer;
  user-select: none;
  transition: all 0.25s ease;
}

.captcha-code:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(45, 212, 191, 0.3);
}

.login-btn {
  width: 100%;
  height: 40px;
  font-weight: 600;
  border-radius: 10px;
  font-size: 15px;
}

.tips {
  margin-top: -6px;
  font-size: 12px;
  color: #94a3b8;
  text-align: center;
}

.register-entry {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
  margin-top: -6px;
  margin-bottom: 6px;
  font-size: 13px;
  color: #64748b;
}

.register-closed-tip {
  color: #ef4444;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

@media (max-width: 900px) {
  .login-page {
    flex-direction: column;
    gap: 28px;
  }

  .login-brand {
    text-align: center;
  }

  .login-brand h1 {
    font-size: 28px;
  }

  .login-card {
    width: min(440px, 100%);
  }
}
</style>
