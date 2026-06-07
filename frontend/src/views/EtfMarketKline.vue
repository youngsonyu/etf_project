<template>
  <CrudPage :api="api" :columns="columns" :search-items="searchItems" />
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import CrudPage from '@/shared/CrudPage.vue'
import api from '@/api/etfMarketKline'
import etlProgressApi from '@/api/etlProgress'

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
  { prop: 'id', label: '主键ID', sortable: false },
  { prop: 'etfCode', label: 'ETF代码' },
  { prop: 'etfName', label: 'ETF简称' },
  { prop: 'period', label: '周期' },
  { prop: 'tradeTime', label: 'K线时间' },
  { prop: 'closePrice', label: '收盘价' },
  { prop: 'volume', label: '成交量' }
]

const searchItems = computed(() => [
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
  { prop: 'tradeDate', label: 'K线时间', type: 'date', defaultValue: defaultTradeDate.value }
])
</script>