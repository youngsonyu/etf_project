<template>
  <CrudPage :api="api" :columns="columns" :search-items="searchItems" />
</template>

<script setup>
import CrudPage from '@/shared/CrudPage.vue'
import api from '@/api/etfMarketKline'

function getYesterday() {
  const date = new Date()
  date.setDate(date.getDate() - 1)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

const columns = [
  { prop: 'id', label: '主键ID', sortable: false },
  { prop: 'etfCode', label: 'ETF代码' },
  { prop: 'etfName', label: 'ETF简称' },
  { prop: 'period', label: '周期' },
  { prop: 'tradeTime', label: 'K线时间' },
  { prop: 'closePrice', label: '收盘价' },
  { prop: 'volume', label: '成交量' }
]
const searchItems = [
  { prop: 'etfCode', label: 'ETF代码', type: 'input' },
  { prop: 'etfName', label: 'ETF简称', type: 'input' },
  {
    prop: 'period',
    label: '周期',
    type: 'select',
    defaultValue: 'day',
    options: [
      { label: 'year', value: 'year' },
      { label: 'month', value: 'month' },
      { label: 'day', value: 'day' },
      { label: 'week', value: 'week' },
      { label: 'season', value: 'season' }
    ]
  },
  { prop: 'tradeDate', label: 'K线时间', type: 'date', defaultValue: getYesterday() }
]
</script>
