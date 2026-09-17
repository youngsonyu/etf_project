<template>
  <CrudPage :api="api" :columns="columns" :search-items="searchItems" :enable-row-delete="true">
    <template #toolbar="{ reload }">
      <el-button type="success" plain :loading="importing" @click="handleImport(reload)">
        银河证券数据导入
      </el-button>
      <el-button v-show="false" type="warning" plain :loading="calculating" @click="handleCalcTa(reload)">
        L2指标计算
      </el-button>
      <el-button v-if="!isNormalUser" type="warning" plain :loading="calculatingDaily" @click="handleCalcTaDaily(reload)">
        L2指标按天对齐
      </el-button>

      <el-divider v-show="false" direction="vertical" />

      <el-tooltip v-if="false && isNormalUser" content="普通用户无法刷新最新交易日" placement="top">
        <span>
          <el-button type="primary" plain :loading="refreshingResonance" disabled>刷新最新共振</el-button>
        </span>
      </el-tooltip>
      <el-button v-show="false" type="primary" plain :loading="refreshingResonance" @click="handleRefreshResonance(reload)">
        刷新最新共振
      </el-button>
      <el-button v-if="!isNormalUser" v-show="false" type="primary" plain :loading="backfillingResonance" @click="handleBackfillResonance(reload)">
        全量补算历史共振
      </el-button>

      <el-divider v-show="false" direction="vertical" />

      <el-date-picker
        v-model="flowDateRange"
        type="daterange"
        value-format="YYYY-MM-DD"
        range-separator="至"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        unlink-panels
      />
      <el-tooltip v-if="isNormalUser" content="普通用户无法计算资金流向金额" placement="top">
        <span>
          <el-button type="primary" :loading="calculatingFlow" disabled>计算资金流向金额</el-button>
        </span>
      </el-tooltip>
      <el-button v-else type="primary" :loading="calculatingFlow" @click="handleCalcFlow(reload)">
        计算资金流向金额
      </el-button>
    </template>
  </CrudPage>
</template>

<script setup>
import CrudPage from '@/shared/CrudPage.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ref } from 'vue'
import api from '@/api/etlBatchStatus'
import resonanceApi from '@/api/etfFiveDimensionResonance'
import fundFlowApi from '@/api/etfFundFlowSummary'

const importing = ref(false)
const calculating = ref(false)
const calculatingDaily = ref(false)
const refreshingResonance = ref(false)
const backfillingResonance = ref(false)
const calculatingFlow = ref(false)
const flowDateRange = ref([])

const isNormalUser = ref(localStorage.getItem('etf_login_type') === 'user')

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
    defaultValue: 'RUNNING',
    options: [
      { label: 'RUNNING', value: 'RUNNING' },
      { label: 'SUCCESS', value: 'SUCCESS' },
      { label: 'FAILED', value: 'FAILED' },
      { label: 'PARTIAL', value: 'PARTIAL' }
    ]
  }
]

function scheduleReload(reload) {
  if (typeof reload === 'function') {
    reload()
    setTimeout(() => reload(), 2000)
    setTimeout(() => reload(), 5000)
  }
}

// 轮询"全量补算共振"等异步任务；SUCCESS/FAILED 后弹结果
async function pollResonanceTask(taskId, { intervalMs = 2000, maxAttempts = 900 } = {}) {
  if (!taskId) return null
  for (let i = 0; i < maxAttempts; i++) {
    const res = await resonanceApi.taskStatus(taskId)
    const task = res?.data || {}
    if (task.status === 'SUCCESS') return task
    if (task.status === 'FAILED') {
      throw new Error(task.message || '后台任务执行失败')
    }
    await new Promise((resolve) => setTimeout(resolve, intervalMs))
  }
  throw new Error('轮询超时，请到 ETL 跑批状态表中查看批次号结果')
}

async function handleImport(reload) {
  await ElMessageBox.confirm('确认立即执行银河证券数据导入吗？', '提示', { type: 'warning' })
  importing.value = true
  try {
    const res = await api.importGalaxy()
    const message = res?.data || '导入任务已触发，请稍后刷新查看状态'
    ElMessage.info(message)
    scheduleReload(reload)
  } finally {
    importing.value = false
  }
}

async function handleCalcTa(reload) {
  await ElMessageBox.confirm(
    '将基于 etf_market_kline 中已有数据，按 day/week/month/season 四个周期重算 L2 技术指标，确认继续吗？',
    '提示',
    { type: 'warning' }
  )
  calculating.value = true
  try {
    const res = await api.calcTa()
    const message = res?.data || 'L2指标计算任务已触发，请稍后刷新查看状态'
    ElMessage.info(message)
    scheduleReload(reload)
  } finally {
    calculating.value = false
  }
}

async function handleCalcTaDaily(reload) {
  await ElMessageBox.confirm(
    '将基于 etf_market_kline 中已有数据，按天对齐生成 L2 指标（每个交易日 day/week/month/season 各一条），写入 etf_ta_indicator_daily，确认继续吗？',
    '提示',
    { type: 'warning' }
  )
  calculatingDaily.value = true
  try {
    const res = await api.calcTaDaily()
    const message = res?.data || '按天对齐L2指标计算任务已触发，请稍后刷新查看状态'
    ElMessage.info(message)
    scheduleReload(reload)
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(error?.message || '按天对齐L2指标计算失败')
  } finally {
    calculatingDaily.value = false
  }
}

async function handleRefreshResonance(reload) {
  await ElMessageBox.confirm('确认刷新最新交易日的五维共振结果吗？', '提示', { type: 'warning' })
  refreshingResonance.value = true
  try {
    const res = await resonanceApi.refreshLatest()
    const data = res?.data || {}
    const msg = data.tradeDate
      ? `刷新完成：交易日 ${data.tradeDate}，影响 ${data.inserted ?? 0} 行`
      : '最新共振刷新任务已提交'
    ElMessage.success(msg)
    scheduleReload(reload)
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(error?.message || '刷新最新共振失败')
  } finally {
    refreshingResonance.value = false
  }
}

async function handleBackfillResonance(reload) {
  await ElMessageBox.confirm(
    '将按 L2 表中的全部日线交易日全量补算五维共振数据，可能需要较长时间，确认继续吗？',
    '提示',
    { type: 'warning' }
  )
  backfillingResonance.value = true
  try {
    const res = await resonanceApi.backfillAll()
    const taskId = res?.data?.taskId
    if (!taskId) {
      ElMessage.warning('任务未成功提交，请稍后重试')
      return
    }
    ElMessage.info('全量补算任务已提交，正在后台计算...')
    const payload = await pollResonanceTask(taskId)
    ElMessage.success(
      `补算完成：共处理 ${payload.tradeDateCount || 0} 个交易日，影响 ${payload.affectedRows || 0} 行`
    )
    scheduleReload(reload)
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(error?.message || '历史共振补算失败')
  } finally {
    backfillingResonance.value = false
  }
}

async function handleCalcFlow(reload) {
  if (!Array.isArray(flowDateRange.value) || flowDateRange.value.length !== 2) {
    ElMessage.warning('请选择开始日期和结束日期')
    return
  }
  const [startDate, endDate] = flowDateRange.value
  try {
    await ElMessageBox.confirm(
      `确认按时间范围 ${startDate} 至 ${endDate} 计算资金流向金额吗？`,
      '提示',
      { type: 'warning' }
    )
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    return
  }
  calculatingFlow.value = true
  try {
    // 异步提交：立刻返回 batchNo，后台线程跑
    const res = await fundFlowApi.accumulate({ startDate, endDate })
    ElMessage.success(res?.data || '资金流向金额计算任务已提交，后台执行中')
    flowDateRange.value = []
    scheduleReload(reload)
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(error?.message || '资金流向金额计算任务提交失败')
  } finally {
    calculatingFlow.value = false
  }
}
</script>

<style scoped>
.el-divider--vertical {
  height: 24px;
  margin: 0 8px;
}
</style>
