<template>
  <div>
    <el-card class="mb12">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="ETF代码">
          <el-input v-model="searchForm.etfCode" placeholder="请输入ETF代码" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card>
      <div class="toolbar">
        <el-button type="primary" @click="openDialog()">新增</el-button>
        <el-button type="danger" :disabled="!selectedIds.length" @click="handleBatchDelete">批量删除</el-button>
      </div>

      <el-table :data="tableData" @selection-change="onSelectionChange" border>
        <el-table-column type="selection" width="50" />
        <el-table-column v-for="col in columns" :key="col.prop" :prop="col.prop" :label="col.label" min-width="140" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="scope">
            <el-button size="small" type="primary" link @click="openDialog(scope.row)">编辑</el-button>
            <el-button size="small" type="danger" link @click="handleDelete(scope.row)">删除</el-button>
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

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑' : '新增'" width="720px">
      <el-form ref="formRef" :model="form" label-width="140px">
        <el-form-item v-for="col in formColumns" :key="col.prop" :label="col.label">
          <el-input v-model="form[col.prop]" clearable />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const props = defineProps({
  api: { type: Object, required: true },
  columns: { type: Array, required: true },
  searchFields: { type: Array, default: () => [] }
})

// 规范化 searchFields - 支持字符串和对象两种格式
const normalizedSearchFields = computed(() => {
  return props.searchFields.map(item => {
    if (typeof item === 'string') {
      return { prop: item, label: item }
    }
    return item
  })
})

// 初始化搜索表单 - 包含所有搜索字段
const searchForm = reactive({ etfCode: '' })
watch(() => normalizedSearchFields.value, (newFields) => {
  newFields.forEach(field => {
    if (!(field.prop in searchForm)) {
      searchForm[field.prop] = ''
    }
  })
}, { immediate: true })

const form = reactive({ id: null })
const dialogVisible = ref(false)
const tableData = ref([])
const selectedIds = ref([])
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const formRef = ref()

const formColumns = computed(() => props.columns.filter((c) => c.prop !== 'id'))

function onSelectionChange(rows) {
  selectedIds.value = rows.map((item) => item.id)
}

function handleSearch() {
  pagination.pageNum = 1
  loadData()
}

function handleReset() {
  Object.keys(searchForm).forEach((k) => {
    searchForm[k] = ''
  })
  pagination.pageNum = 1
  loadData()
}

function openDialog(row) {
  Object.keys(form).forEach((k) => delete form[k])
  if (row) {
    Object.assign(form, row)
  } else {
    form.id = null
  }
  dialogVisible.value = true
}

async function submitForm() {
  if (form.id) {
    await props.api.update(form.id, form)
    ElMessage.success('修改成功')
  } else {
    await props.api.add(form)
    ElMessage.success('新增成功')
  }
  dialogVisible.value = false
  loadData()
}

async function handleDelete(row) {
  await ElMessageBox.confirm('确认删除该记录吗？', '提示', { type: 'warning' })
  await props.api.remove(row.id)
  ElMessage.success('删除成功')
  loadData()
}

async function handleBatchDelete() {
  await ElMessageBox.confirm('确认批量删除选中记录吗？', '提示', { type: 'warning' })
  await props.api.batchDelete(selectedIds.value)
  ElMessage.success('批量删除成功')
  loadData()
}

async function loadData() {
  const params = {
    pageNum: pagination.pageNum,
    pageSize: pagination.pageSize
  }

  Object.entries(searchForm).forEach(([key, value]) => {
    if (value === null || value === undefined || String(value).trim() === '') {
      return
    }
    if (key === 'etfCode') {
      return
    }
    params[key] = value
  })

  if (searchForm.etfCode && String(searchForm.etfCode).trim() !== '') {
    params.keyword = String(searchForm.etfCode).trim()
  }

  const res = await props.api.page(params)
  const pageData = res.data || {}
  tableData.value = pageData.records || []
  pagination.total = pageData.total || 0
  pagination.pageNum = pageData.current || pagination.pageNum
  pagination.pageSize = pageData.size || pagination.pageSize
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
:deep(.el-card) {
  border-radius: 12px;
  border: 1px solid #e4e7ed;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
}

.mb12 {
  margin-bottom: 16px;
}

.mb12 :deep(.el-card__body) {
  padding: 16px 20px;
}

.mb12 :deep(.el-form--inline .el-form-item) {
  margin-bottom: 0;
}

.toolbar {
  margin-bottom: 14px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.toolbar :deep(.el-button) {
  border-radius: 8px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid #f1f5f9;
}

:deep(.el-table) {
  border-radius: 10px;
  overflow: hidden;
}

:deep(.el-table th.el-table__cell) {
  background: #f8fafc;
  color: #475569;
  font-weight: 600;
  font-size: 13px;
  padding: 12px 0;
}

:deep(.el-table td.el-table__cell) {
  padding: 12px 0;
  color: #334155;
}

:deep(.el-table tr) {
  transition: background 0.2s ease;
}

:deep(.el-table tr:hover td.el-table__cell) {
  background: #f0fdfa;
}

:deep(.el-dialog) {
  border-radius: 16px;
  overflow: hidden;
}

:deep(.el-dialog__header) {
  background: linear-gradient(135deg, #f0fdfa 0%, #ffffff 100%);
  border-bottom: 1px solid #e4e7ed;
  padding: 18px 20px;
  margin-right: 0;
}

:deep(.el-dialog__title) {
  font-weight: 700;
  color: #0f172a;
}

:deep(.el-dialog__body) {
  padding: 24px 20px;
}

:deep(.el-form-item__label) {
  font-weight: 500;
  color: #475569;
}

:deep(.el-pagination) {
  font-weight: 500;
}
</style>
