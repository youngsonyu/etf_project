<template>
  <div class="change-pwd-page">
    <el-card style="max-width: 480px; margin: 80px auto;">
      <template #header>
        <span>修改密码</span>
      </template>
      <el-form :model="pwdForm" label-width="100px">
        <el-form-item label="当前账号">
          <el-input :model-value="currentUsername" disabled />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="pwdForm.newPassword" type="password" placeholder="6-64位" show-password />
        </el-form-item>
        <el-form-item label="确认新密码">
          <el-input v-model="pwdForm.confirmPassword" type="password" placeholder="再次输入新密码" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="changingPwd" @click="submitChangePwd">确认修改</el-button>
          <el-button @click="resetPwdForm">重置</el-button>
          <el-button @click="goBack">返回</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import userApi from '@/api/sysUser'

const router = useRouter()
const currentUsername = localStorage.getItem('etf_account') || localStorage.getItem('etf_username') || ''

const pwdForm = reactive({
  newPassword: '',
  confirmPassword: ''
})
const changingPwd = ref(false)

function resetPwdForm() {
  pwdForm.newPassword = ''
  pwdForm.confirmPassword = ''
}

function goBack() {
  router.back()
}

async function submitChangePwd() {
  if (!pwdForm.newPassword) {
    ElMessage.warning('请输入新密码')
    return
  }
  if (pwdForm.newPassword !== pwdForm.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  try {
    changingPwd.value = true
    await userApi.resetPassword({
      username: currentUsername,
      newPassword: pwdForm.newPassword,
      confirmPassword: pwdForm.confirmPassword
    })
    ElMessage.success('密码修改成功')
    resetPwdForm()
  } catch (error) {
    ElMessage.error(error?.message || '修改失败')
  } finally {
    changingPwd.value = false
  }
}
</script>

<style scoped>
.change-pwd-page {
  min-height: 100vh;
  background: #f5f7fa;
}
</style>