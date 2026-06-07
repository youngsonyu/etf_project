<template>
  <div class="user-mgmt-page">
    <el-card class="mb12">
      <el-form :inline="true" :model="queryForm">
        <el-form-item label="账号/姓名">
          <el-input v-model="queryForm.keyword" placeholder="请输入账号或姓名" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
          <el-button type="success" @click="openCreate">新增用户</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card>
      <el-table :data="tableData" border>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="账号" width="180" />
        <el-table-column prop="displayName" label="姓名" width="150" />
        <el-table-column prop="isActive" label="状态" width="100">
          <template #default="scope">
            <el-tag :type="scope.row.isActive === 1 ? 'success' : 'danger'">
              {{ scope.row.isActive === 1 ? '正常' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="scope">
            <el-button type="warning" link @click="openEdit(scope.row)">编辑</el-button>
            <el-button type="primary" link @click="openResetPwd(scope.row)">重置密码</el-button>
            <el-button type="danger" link @click="deleteUser(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          v-model:current-page="pagination.pageNum"
          v-model:page-size="pagination.pageSize"
          :total="pagination.total"
          @current-change="loadData"
          @size-change="loadData"
        />
      </div>
    </el-card>

    <el-dialog v-model="editorVisible" :title="editingId ? '编辑用户' : '新增用户'" width="500px">
      <el-form :model="editorForm" label-width="80px">
        <el-form-item label="账号" v-if="!editingId">
          <el-input v-model="editorForm.username" maxlength="32" placeholder="4-32位字母、数字或下划线" />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="editorForm.displayName" maxlength="64" />
        </el-form-item>
        <el-form-item label="密码" v-if="!editingId">
          <el-input v-model="editorForm.password" type="password" maxlength="64" placeholder="6-64位" show-password />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="editorForm.isActive">
            <el-radio :label="1">正常</el-radio>
            <el-radio :label="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editorVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveUser">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resetPwdVisible" title="重置密码" width="400px">
      <el-form :model="resetPwdForm" label-width="80px">
        <el-form-item label="账号">
          <span>{{ resetPwdForm.username }}</span>
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="resetPwdForm.password" type="password" maxlength="64" placeholder="6-64位" show-password />
        </el-form-item>
        <el-form-item label="确认密码">
          <el-input v-model="resetPwdForm.confirmPassword" type="password" maxlength="64" placeholder="再次输入新密码" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetPwdVisible = false">取消</el-button>
        <el-button type="primary" :loading="resetting" @click="confirmResetPwd">确认重置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '@/api/sysUser'

const tableData = ref([])
const queryForm = reactive({ keyword: '' })
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })

const editorVisible = ref(false)
const saving = ref(false)
const editingId = ref(null)
const editorForm = reactive({
  username: '',
  displayName: '',
  password: '',
  isActive: 1
})

const resetPwdVisible = ref(false)
const resetting = ref(false)
const resetPwdUserId = ref(null)
const resetPwdForm = reactive({
  username: '',
  password: '',
  confirmPassword: ''
})

async function loadData() {
  const res = await api.page({
    pageNum: pagination.pageNum,
    pageSize: pagination.pageSize,
    keyword: queryForm.keyword
  })
  const page = res?.data || {}
  tableData.value = page.records || []
  pagination.total = page.total || 0
  pagination.pageNum = page.current || pagination.pageNum
  pagination.pageSize = page.size || pagination.pageSize
}

function resetQuery() {
  queryForm.keyword = ''
  pagination.pageNum = 1
  loadData()
}

function openCreate() {
  editingId.value = null
  editorForm.username = ''
  editorForm.displayName = ''
  editorForm.password = ''
  editorForm.isActive = 1
  editorVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  editorForm.username = row.username || ''
  editorForm.displayName = row.displayName || ''
  editorForm.password = ''
  editorForm.isActive = row.isActive ?? 1
  editorVisible.value = true
}

function openResetPwd(row) {
  resetPwdUserId.value = row.id
  resetPwdForm.username = row.username || ''
  resetPwdForm.password = ''
  resetPwdForm.confirmPassword = ''
  resetPwdVisible.value = true
}

async function saveUser() {
  if (!editingId.value) {
    if (!editorForm.username.trim()) {
      ElMessage.warning('账号不能为空')
      return
    }
    if (!editorForm.password) {
      ElMessage.warning('密码不能为空')
      return
    }
  }
  try {
    saving.value = true
    const payload = {
      username: editorForm.username.trim(),
      displayName: editorForm.displayName.trim(),
      passwordHash: editorForm.password,
      isActive: editorForm.isActive
    }
    if (editingId.value) {
      delete payload.username
      delete payload.passwordHash
      if (payload.passwordHash === '') {
        delete payload.passwordHash
      }
      await api.update(editingId.value, payload)
      ElMessage.success('更新成功')
    } else {
      await api.add(payload)
      ElMessage.success('新增成功')
    }
    editorVisible.value = false
    await loadData()
  } finally {
    saving.value = false
  }
}

async function confirmResetPwd() {
  if (!resetPwdForm.password) {
    ElMessage.warning('新密码不能为空')
    return
  }
  if (resetPwdForm.password !== resetPwdForm.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  try {
    resetting.value = true
    await api.update(resetPwdUserId.value, { passwordHash: resetPwdForm.password })
    ElMessage.success('密码重置成功')
    resetPwdVisible.value = false
  } catch (error) {
    ElMessage.error(error?.message || '重置失败')
  } finally {
    resetting.value = false
  }
}

async function deleteUser(row) {
  await ElMessageBox.confirm(`确认删除用户「${row.username}」吗？`, '提示', { type: 'warning' })
  await api.remove(row.id)
  ElMessage.success('删除成功')
  await loadData()
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.user-mgmt-page {
  padding: 8px;
}
.mb12 {
  margin-bottom: 12px;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
</style>