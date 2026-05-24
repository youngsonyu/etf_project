<template>
  <CrudPage :api="api" :columns="columns" :search-items="searchItems" :enable-row-delete="true">
    <template #toolbar="{ reload }">
      <el-button type="success" plain :loading="importing" @click="handleImport(reload)">
        银河证券数据导入
      </el-button>
    </template>
  </CrudPage>
</template>

<script setup>
import CrudPage from '@/shared/CrudPage.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ref } from 'vue'
import api from '@/api/etlBatchStatus'

const importing = ref(false)

const columns = [
  { prop: 'id', label: '主键ID', sortable: false },
  { prop: 'batchNo', label: '批次号' },
  { prop: 'jobName', label: '任务名称' },
  { prop: 'status', label: '状态' },
  { prop: 'tradeDateStart', label: '起始交易日' },
  { prop: 'tradeDateEnd', label: '结束交易日' },
  { prop: 'etfCount', label: 'ETF数量' },
  { prop: 'klineCount', label: 'K线数量' },
  { prop: 'startTime', label: '开始时间' },
  { prop: 'endTime', label: '结束时间' },
  { prop: 'errorMessage', label: '错误信息', sortable: false }
]

const searchItems = [
  { prop: 'batchNo', label: '批次号', type: 'input' },
  { prop: 'jobName', label: '任务名称', type: 'input' },
  {
    prop: 'status',
    label: '状态',
    type: 'select',
    options: [
      { label: 'RUNNING', value: 'RUNNING' },
      { label: 'SUCCESS', value: 'SUCCESS' },
      { label: 'FAILED', value: 'FAILED' },
      { label: 'PARTIAL', value: 'PARTIAL' }
    ]
  }
]

async function handleImport(reload) {
  await ElMessageBox.confirm('确认立即执行银河证券数据导入吗？', '提示', { type: 'warning' })
  importing.value = true
  try {
    await api.importGalaxy()
    ElMessage.success('银河证券数据导入成功')
    if (typeof reload === 'function') {
      reload()
    }
  } finally {
    importing.value = false
  }
}
</script>
