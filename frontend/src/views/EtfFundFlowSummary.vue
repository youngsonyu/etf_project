<template>
  <CrudPage :api="api" :columns="columns" :search-items="searchItems" />
</template>

<script setup>
import CrudPage from '@/shared/CrudPage.vue'
import api from '@/api/etfFundFlowSummary'

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
  { prop: 'tradeDate', label: '交易日期', type: 'date' }
]
</script>
