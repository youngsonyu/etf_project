<template>
  <CrudPage :key="searchItemsVersion" ref="crudPageRef" :api="api" :columns="columns" :search-items="searchItems">
    <template #toolbar>
      <el-tooltip v-if="isNormalUser" content="普通用户无法刷新最新交易日" placement="top">
      <span>
        <el-button type="primary" :loading="refreshing" disabled>刷新最新交易日</el-button>
      </span>
    </el-tooltip>
    <el-button v-else type="primary" :loading="refreshing" @click="confirmRefreshLatest">刷新最新交易日</el-button>
    <el-button type="success" :loading="exporting" @click="exportCsv">导出CSV</el-button>
    </template>
  </CrudPage>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import CrudPage from '@/shared/CrudPage.vue'
import api from '@/api/etfFiveDimensionResonance'
import { exportToCsv } from '@/utils/csvExport'

const crudPageRef = ref()
const exporting = ref(false)
const refreshing = ref(false)
const latestTradeDate = ref('')
const lastTriggeredDefault = ref('')
const searchItemsVersion = ref(0)
const isNormalUser = ref(localStorage.getItem('etf_login_type') === 'user')

const columns = [
  { prop: 'id', label: '主键ID' },
  { prop: 'etfCode', label: 'ETF代码' },
  { prop: 'etfName', label: 'ETF简称' },
  { prop: 'tradeTime', label: 'K线时间' },
  { prop: 'signalTrendLong', label: '综合趋势多头信号', valueMap: { 1: '是', 0: '否' } },
  { prop: 'signalMomentumLong', label: '综合动量多头信号', valueMap: { 1: '是', 0: '否' } },
  { prop: 'signalWarning', label: '风险预警信号', valueMap: { 1: '是', 0: '否' } },
  { prop: 'openPrice', label: '开盘价' },
  { prop: 'highPrice', label: '最高价' },
  { prop: 'lowPrice', label: '最低价' },
  { prop: 'closePrice', label: '收盘价' },
  { prop: 'volume', label: '成交量' },
  { prop: 'amount', label: '成交额' },
  { prop: 'dif', label: 'MACD_DIF' },
  { prop: 'dea', label: 'MACD_DEA' },
  { prop: 'macd', label: 'MACD柱值' },
  { prop: 'isMacdGoldenState', label: '是否金叉状态', valueMap: { 1: '是', 0: '否' } },
  { prop: 'isMacdRed', label: '是否红柱', valueMap: { 1: '是', 0: '否' } },
  { prop: 'macdHistDirection', label: 'MACD柱方向', valueMap: { 1: '增强', 0: '持平', '-1': '减弱' } },
  { prop: 'sarValue', label: 'SAR值' },
  { prop: 'isSarBullish', label: '是否SAR多头', valueMap: { 1: '是', 0: '否' } },
  { prop: 'sarTrend', label: 'SAR趋势方向', valueMap: { 1: '多头', 0: '未知', '-1': '空头' } },
  { prop: 'ma5', label: 'MA5' },
  { prop: 'ma10', label: 'MA10' },
  { prop: 'ma20', label: 'MA20' },
  { prop: 'ma30', label: 'MA30' },
  { prop: 'ma60', label: 'MA60' },
  { prop: 'isMa5AboveMa10', label: 'MA5在MA10之上', valueMap: { 1: '是', 0: '否' } },
  { prop: 'isMa10AboveMa20', label: 'MA10在MA20之上', valueMap: { 1: '是', 0: '否' } },
  { prop: 'isCloseAboveMa20', label: '收盘价在MA20之上', valueMap: { 1: '是', 0: '否' } },
  { prop: 'isCloseAboveMa60', label: '收盘价在MA60之上', valueMap: { 1: '是', 0: '否' } },
  { prop: 'rsi6', label: 'RSI6' },
  { prop: 'rsi12', label: 'RSI12' },
  { prop: 'rsi24', label: 'RSI24' },
  { prop: 'kValue', label: 'KDJ_K' },
  { prop: 'dValue', label: 'KDJ_D' },
  { prop: 'jValue', label: 'KDJ_J' },
  { prop: 'bollMid', label: 'BOLL中轨' },
  { prop: 'bollUpper', label: 'BOLL上轨' },
  { prop: 'bollLower', label: 'BOLL下轨' },
  { prop: 'atr14', label: 'ATR14' },
  { prop: 'adx14', label: 'ADX14' },
  { prop: 'isYesterdayTriggered', label: '昨日是否触发五维共振', valueMap: { 1: '是', 0: '否' } },
  { prop: 'isTodayTriggered', label: '最新K线日是否触发五维共振', valueMap: { 1: '是', 0: '否' } },
  { prop: 'firstTriggeredDateOfYear', label: '今年首次触发五维共振日期' },
  { prop: 'previousTriggeredDate', label: '上次触发五维共振日期' },
  { prop: 'lastTriggeredDate', label: '最后触发五维共振日期' },
  { prop: 'source', label: '数据来源' }
]

const searchItems = computed(() => [
  { prop: 'etfCode', label: 'ETF代码', type: 'input' },
  { prop: 'etfName', label: 'ETF简称', type: 'input' },
  {
    prop: 'signalWarning',
    label: '风险预警',
    type: 'select',
    defaultValue: 0,
    options: [
      { label: '暂未发现风险', value: 0 },
      { label: '发现风险预警', value: 1 }
    ]
  },
  {
    prop: 'isYesterdayTriggered',
    label: '昨日是否触发五维共振',
    type: 'select',
    options: [
      { label: '是', value: 1 },
      { label: '否', value: 0 }
    ]
  },
  {
    prop: 'isTodayTriggered',
    label: '最新K线日是否触发五维共振',
    type: 'select',
    defaultValue: 1,
    options: [
      { label: '是', value: 1 },
      { label: '否', value: 0 }
    ]
  },
  { prop: 'lastTriggeredDate', label: '最后触发五维共振日期', type: 'date', defaultValue: lastTriggeredDefault.value }
])

async function loadLatestTradeDate() {
  const res = await api.latestTradeDate()
  latestTradeDate.value = res?.data || ''
  lastTriggeredDefault.value = latestTradeDate.value || ''
  searchItemsVersion.value += 1
}

async function confirmRefreshLatest() {
  try {
    if (!latestTradeDate.value) {
      await loadLatestTradeDate()
    }
    if (!latestTradeDate.value) {
      ElMessage.warning('未找到可刷新的最新交易日')
      return
    }

    await ElMessageBox.confirm(
      `你确定你要刷新${latestTradeDate.value}的五维共振ETF数据么？`,
      '确认刷新',
      { type: 'warning' }
    )

    refreshing.value = true
    const res = await api.refreshLatest()
    const payload = res?.data || {}
    if (payload.skipped) {
      ElMessage.info(`${payload.tradeDate || latestTradeDate.value} 数据已存在，无需刷新`)
    } else {
      ElMessage.success(`刷新完成：${payload.tradeDate || latestTradeDate.value}，共更新 ${payload.inserted || 0} 条`)
    }
    await loadLatestTradeDate()
    crudPageRef.value?.loadData()
  } catch (error) {
    if (error === 'cancel' || error === 'close') {
      return
    }
    ElMessage.error(error?.message || '刷新失败')
  } finally {
    refreshing.value = false
  }
}

function mapValueStr(valueMap, raw) {
  if (!valueMap) return raw ?? ''
  const key = String(raw)
  return valueMap[key] !== undefined ? valueMap[key] : (valueMap[raw] !== undefined ? valueMap[raw] : (raw ?? ''))
}

async function exportCsv() {
  try {
    exporting.value = true
    const params = { ...crudPageRef.value.buildParams(), pageNum: 1, pageSize: 999999 }
    const res = await api.page(params)
    const records = res?.data?.records || []

    if (!records.length) {
      ElMessage.warning('暂无可导出的数据')
      return
    }

    const rows = records.map((item) => {
      const row = {}
      columns.forEach((col) => {
        row[col.label] = col.valueMap ? mapValueStr(col.valueMap, item[col.prop]) : (item[col.prop] ?? '')
      })
      return row
    })

    const dateText = new Date().toISOString().slice(0, 10)
    exportToCsv(`ETF五维共振数据分析_${dateText}.csv`, rows)
    ElMessage.success(`导出成功，共 ${rows.length} 条`)
  } catch (error) {
    ElMessage.error(error?.message || '导出失败')
  } finally {
    exporting.value = false
  }
}

onMounted(() => {
  loadLatestTradeDate().catch(() => {
    latestTradeDate.value = ''
    lastTriggeredDefault.value = ''
    searchItemsVersion.value += 1
  })
})
</script>
