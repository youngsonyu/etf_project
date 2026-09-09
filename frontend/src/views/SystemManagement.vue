<template>
  <div class="system-page">
    <el-card class="system-card">
      <template #header>
        <span>系统管理</span>
      </template>
      <el-tabs v-model="activeTab">
        <el-tab-pane label="快捷入口" name="shortcuts">
          <div class="actions">
            <el-button v-if="!isNormalUser" type="primary" @click="go('/menu_config')">菜单配置</el-button>
            <el-button v-if="!isNormalUser" type="success" @click="go('/sys_param')">参数配置</el-button>
            <el-button v-if="!isNormalUser" @click="go('/trade_calendar')">交易日历</el-button>
            <el-button v-if="!isNormalUser" type="warning" @click="go('/etl_batch_status')">ETL跑批状态</el-button>
            <el-button v-if="!isNormalUser" type="warning" plain @click="go('/etl_checkpoint')">ETL跑批检查点</el-button>
            <el-empty v-if="isNormalUser" description="普通用户无管理员快捷入口" />
          </div>
        </el-tab-pane>
        <el-tab-pane label="修改密码" name="changePwd">
          <el-form :model="pwdForm" label-width="100px" style="max-width: 400px; margin-top: 16px;">
            <el-form-item label="当前账号">
              <el-input :model-value="currentUsername" disabled />
            </el-form-item>
            <el-form-item label="旧密码">
              <el-input v-model="pwdForm.oldPassword" type="password" placeholder="请输入旧密码" show-password />
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
            </el-form-item>
          </el-form>
        </el-tab-pane>
        <el-tab-pane label="用户管理" name="userMgmt" v-if="!isNormalUser">
          <UserManagement />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import UserManagement from './UserManagement.vue'
import userApi from '@/api/sysUser'

const router = useRouter()
const activeTab = ref('shortcuts')
const isNormalUser = computed(() => localStorage.getItem('etf_login_type') === 'user')
const currentUsername = localStorage.getItem('etf_account') || localStorage.getItem('etf_username') || ''

const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})
const changingPwd = ref(false)

function go(path) {
  router.push(path)
}

function resetPwdForm() {
  pwdForm.oldPassword = ''
  pwdForm.newPassword = ''
  pwdForm.confirmPassword = ''
}

async function submitChangePwd() {
  if (!pwdForm.oldPassword) {
    ElMessage.warning('请输入旧密码')
    return
  }
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
    await userApi.changePassword({
      username: currentUsername,
      oldPassword: pwdForm.oldPassword,
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
.system-page {
  padding: 8px;
}

.actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 16px;
}
</style>