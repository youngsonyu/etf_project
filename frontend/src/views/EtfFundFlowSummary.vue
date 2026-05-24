<template>
  <CrudPage ref="crudPageRef" :api="api" :columns="columns" :search-items="searchItems">
    <template #toolbar>
      <div class="accumulate-toolbar">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          unlink-panels
        />
        <el-tooltip v-if="isNormalUser" content="普通用户无法新增累计" placement="top">
          <span>
            <el-button type="primary" :loading="submitting" disabled>确认新增累计</el-button>
          </span>
        </el-tooltip>
        <el-button v-else type="primary" :loading="submitting" @click="submitAccumulate">确认新增累计</el-button>
      </div>
    </template>
  </CrudPage>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import CrudPage from '@/shared/CrudPage.vue'
import api from '@/api/etfFundFlowSummary'

function getYesterday() {
  const date = new Date()
  date.setDate(date.getDate() - 1)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

const columns = [
  { prop: 'id', label: '主键ID' },
  { prop: 'etfCode', label: 'ETF代码' },
  { prop: 'etfName', label: 'ETF名称' },
  { prop: 'tradeDate', label: '交易日期' },
  { prop: 'fundFlow', label: '当日资金流向' },
  { prop: 'cumulativeFlow', label: '累计资金流向' },
  { prop: 'changeReason', label: '变动原因' }
]
const searchItems = [
  { prop: 'etfCode', label: 'ETF代码', type: 'input' },
  { prop: 'etfName', label: 'ETF简称', type: 'input' },
  { prop: 'tradeDate', label: '交易日期', type: 'date', defaultValue: getYesterday() }
]

const crudPageRef = ref()
const submitting = ref(false)
const dateRange = ref([])
const isNormalUser = ref(localStorage.getItem('etf_login_type') === 'user')

async function submitAccumulate() {
  if (!Array.isArray(dateRange.value) || dateRange.value.length !== 2) {
    ElMessage.warning('请选择开始日期和结束日期')
    return
  }

  try {
    const [startDate, endDate] = dateRange.value
    await ElMessageBox.confirm(
      `确认按时间范围 ${startDate} 至 ${endDate} 新增累计资金流向吗？`,
      '提示',
      { type: 'warning' }
    )
    submitting.value = true
    const res = await api.accumulate({ startDate, endDate })
    ElMessage.success(res.data || '累计新增成功')
    crudPageRef.value?.loadData()
    dateRange.value = []
  } catch (error) {
    if (error === 'cancel' || error === 'close') {
      return
    }
    throw error
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.accumulate-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
}
</style>
