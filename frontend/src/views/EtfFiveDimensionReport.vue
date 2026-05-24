<template>
  <div class="report-page">
    <el-card class="mb12">
      <el-form :inline="true" :model="queryForm">
        <el-form-item label="标题">
          <el-input v-model="queryForm.keyword" placeholder="请输入标题或发布人" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
          <el-button type="warning" :loading="generating" @click="generateLatestReport">生成最新交易日报告</el-button>
          <el-button type="success" @click="openCreate">发布报告</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card>
      <el-table :data="tableData" border>
        <el-table-column prop="title" label="标题" min-width="260">
          <template #default="scope">
            <el-link type="primary" @click="viewReport(scope.row)">{{ scope.row.title }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="publishTime" label="发布时间" width="180" />
        <el-table-column prop="publisher" label="发布人" width="120" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="scope">
            <el-button type="warning" link @click="openEdit(scope.row)">编辑</el-button>
            <el-button type="danger" link @click="deleteReport(scope.row)">删除</el-button>
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

    <el-dialog v-model="editorVisible" :title="editingId ? '编辑报告' : '发布报告'" width="880px">
      <el-form :model="editorForm" label-width="90px">
        <el-form-item label="标题">
          <el-input v-model="editorForm.title" maxlength="255" show-word-limit />
        </el-form-item>
        <el-form-item label="发布人">
          <el-input v-model="editorForm.publisher" maxlength="64" />
        </el-form-item>
        <el-form-item label="发布时间">
          <el-date-picker
            v-model="editorForm.publishTime"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="请选择发布时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="内容(MD)">
          <el-input
            v-model="editorForm.contentMd"
            type="textarea"
            :rows="12"
            placeholder="请输入Markdown内容"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editorVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveReport">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="viewerVisible" title="" width="980px">
      <h2 class="report-title">{{ viewerData.title }}</h2>
      <div class="report-meta">发布时间：{{ viewerData.publishTime }} | 发布人：{{ viewerData.publisher }}</div>
      <div class="md-body" v-html="viewerHtml"></div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '@/api/etfFiveDimensionReport'
import { renderMarkdown } from '@/utils/markdown'

const tableData = ref([])
const queryForm = reactive({ keyword: '' })
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })

const editorVisible = ref(false)
const saving = ref(false)
const generating = ref(false)
const editingId = ref(null)
const editorForm = reactive({
  title: '',
  publisher: '',
  publishTime: '',
  contentMd: ''
})

const viewerVisible = ref(false)
const viewerData = reactive({ title: '', publisher: '', publishTime: '', contentMd: '' })
const viewerHtml = computed(() => renderMarkdown(viewerData.contentMd))

function nowDateTimeText() {
  const d = new Date()
  const pad = (n) => (n < 10 ? `0${n}` : `${n}`)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

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
  editorForm.title = ''
  editorForm.contentMd = ''
  editorForm.publisher = localStorage.getItem('etf_username') || 'dwb'
  editorForm.publishTime = nowDateTimeText()
  editorVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  editorForm.title = row.title || ''
  editorForm.contentMd = row.contentMd || ''
  editorForm.publisher = row.publisher || (localStorage.getItem('etf_username') || 'dwb')
  editorForm.publishTime = row.publishTime || nowDateTimeText()
  editorVisible.value = true
}

function viewReport(row) {
  viewerData.title = row.title || ''
  viewerData.publisher = row.publisher || ''
  viewerData.publishTime = row.publishTime || ''
  viewerData.contentMd = row.contentMd || ''
  viewerVisible.value = true
}

async function saveReport() {
  if (!editorForm.title.trim() || !editorForm.contentMd.trim()) {
    ElMessage.warning('标题和内容不能为空')
    return
  }
  try {
    saving.value = true
    const payload = {
      title: editorForm.title.trim(),
      contentMd: editorForm.contentMd,
      publisher: (editorForm.publisher || '').trim() || (localStorage.getItem('etf_username') || 'dwb'),
      publishTime: editorForm.publishTime || nowDateTimeText(),
      isActive: 1
    }
    if (editingId.value) {
      await api.update(editingId.value, payload)
      ElMessage.success('更新成功')
    } else {
      await api.add(payload)
      ElMessage.success('发布成功')
    }
    editorVisible.value = false
    await loadData()
  } finally {
    saving.value = false
  }
}

async function deleteReport(row) {
  await ElMessageBox.confirm(`确认删除《${row.title}》吗？`, '提示', { type: 'warning' })
  await api.remove(row.id)
  ElMessage.success('删除成功')
  await loadData()
}

async function generateLatestReport() {
  try {
    generating.value = true
    const publisher = localStorage.getItem('etf_account') || localStorage.getItem('etf_username') || 'AI智能体'
    const res = await api.generateLatest(publisher)
    ElMessage.success('已生成并发布最新交易日报告')
    await loadData()

    if (res?.data) {
      viewReport(res.data)
    }
  } catch (error) {
    const message = error?.message || ''
    if (message.includes('timeout')) {
      ElMessage.error('生成超时，请稍后重试或减少数据量')
    } else {
      ElMessage.error('生成失败，请查看后端日志')
    }
  } finally {
    generating.value = false
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.report-page {
  padding: 4px;
}

.mb12 {
  margin-bottom: 12px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.report-title {
  margin: 0;
  font-size: 24px;
}

.report-meta {
  margin: 8px 0 16px;
  font-size: 13px;
  color: #64748b;
}

.md-body {
  line-height: 1.8;
}

:deep(.md-body h1),
:deep(.md-body h2),
:deep(.md-body h3) {
  margin: 14px 0 10px;
}

:deep(.md-body code) {
  background: #f1f5f9;
  padding: 2px 6px;
  border-radius: 4px;
}

:deep(.md-body pre) {
  background: #0f172a;
  color: #e2e8f0;
  padding: 12px;
  border-radius: 8px;
  overflow: auto;
}
</style>
