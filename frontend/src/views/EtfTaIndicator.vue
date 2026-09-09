<template>
  <CrudPage ref="crudPageRef" :api="api" :columns="columns" :search-items="searchItems">
    <template #toolbar>
      <el-button type="success" :loading="exporting" @click="exportCsv">导出CSV</el-button>
    </template>
  </CrudPage>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import CrudPage from '@/shared/CrudPage.vue'
import api from '@/api/etfTaIndicator'
import { exportToCsv } from '@/utils/csvExport'
import etlProgressApi from '@/api/etlProgress'

const crudPageRef = ref()
const exporting = ref(false)
const defaultTradeDate = ref('')

onMounted(async () => {
  try {
    const res = await etlProgressApi.currentTradeDate()
    const raw = res?.data
    if (raw && /^\d{8}$/.test(String(raw))) {
      const s = String(raw)
      defaultTradeDate.value = `${s.slice(0, 4)}-${s.slice(4, 6)}-${s.slice(6, 8)}`
    }
  } catch {}
})

const columns = [
  { prop: 'etfCode', label: 'ETF代码' },
  { prop: 'etfName', label: 'ETF简称' },
  { prop: 'period', label: '周期' },
  { prop: 'tradeTime', label: 'K线时间' },
  { prop: 'macd', label: 'MACD' },
  { prop: 'isMacdRed', label: 'MACD红柱', valueMap: { 1: '是', 0: '否' } },
  { prop: 'macdHistDirection', label: 'MACD方向', valueMap: { 1: '增强', 0: '持平', '-1': '减弱' } },
  { prop: 'sarValue', label: 'SAR值' },
  { prop: 'isSarBullish', label: 'SAR多头', valueMap: { 1: '是', 0: '否' } },
  { prop: 'sarTrend', label: 'SAR趋势', valueMap: { 1: '多头', 0: '未知', '-1': '空头' } },
  { prop: 'signalTrendLong', label: '综合趋势多头', valueMap: { 1: '是', 0: '否' } },
  { prop: 'signalMomentumLong', label: '综合动量多头', valueMap: { 1: '是', 0: '否' } },
  { prop: 'signalWarning', label: '风险预警', valueMap: { 1: '存在风险', 0: '暂未发现风险' } }
]

const searchItems = computed(() => [
  { prop: 'etfCode', label: 'ETF代码', type: 'input' },
  { prop: 'etfName', label: 'ETF简称', type: 'input' },
  {
    prop: 'period',
    label: '周期',
    type: 'select',
    multiple: true,
    defaultValue: ['day'],
    options: [
      { label: 'year', value: 'year' },
      { label: 'month', value: 'month' },
      { label: 'day', value: 'day' },
      { label: 'week', value: 'week' },
      { label: 'season', value: 'season' }
    ]
  },
  { prop: 'tradeDate', label: 'K线时间', type: 'date', defaultValue: defaultTradeDate.value },
  {
    prop: 'signalTrendLong',
    label: '综合趋势多头',
    type: 'select',
    options: [{ label: '是', value: 1 }, { label: '否', value: 0 }]
  },
  {
    prop: 'isMacdRed',
    label: 'MACD红柱',
    type: 'select',
    options: [{ label: '是', value: 1 }, { label: '否', value: 0 }]
  },
  {
    prop: 'macdHistDirection',
    label: 'MACD方向',
    type: 'select',
    options: [{ label: '增强', value: 1 }, { label: '持平', value: 0 }, { label: '减弱', value: -1 }]
  },
  {
    prop: 'isSarBullish',
    label: 'SAR多头',
    type: 'select',
    options: [{ label: '是', value: 1 }, { label: '否', value: 0 }]
  },
  {
    prop: 'sarTrend',
    label: 'SAR趋势',
    type: 'select',
    options: [{ label: '多头', value: 1 }, { label: '未知', value: 0 }, { label: '空头', value: -1 }]
  },
  {
    prop: 'signalMomentumLong',
    label: '综合动量多头',
    type: 'select',
    options: [{ label: '是', value: 1 }, { label: '否', value: 0 }]
  },
  {
    prop: 'signalWarning',
    label: '风险预警',
    type: 'select',
    options: [{ label: '存在风险', value: 1 }, { label: '暂未发现风险', value: 0 }]
  }
])

function mapValue(valueMap, raw) {
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
        row[col.label] = col.valueMap ? mapValue(col.valueMap, item[col.prop]) : (item[col.prop] ?? '')
      })
      return row
    })

    const dateText = new Date().toISOString().slice(0, 10)
    exportToCsv(`ETF技术指标_${dateText}.csv`, rows)
    ElMessage.success(`导出成功，共 ${rows.length} 条`)
  } catch (error) {
    ElMessage.error(error?.message || '导出失败')
  } finally {
    exporting.value = false
  }
}
</script>

