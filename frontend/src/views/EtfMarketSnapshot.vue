<template>
  <CrudPage :api="api" :columns="columns" :search-fields="searchFields" />
</template>

<script setup>
import CrudPage from '@/shared/CrudPage.vue'
import api from '@/api/etfMarketSnapshot'

const columns = [
  { prop: 'id', label: '主键ID' },
  { prop: 'etfCode', label: 'ETF代码' },
  { prop: 'tradeTime', label: '交易所行情时间' },
  { prop: 'lastPrice', label: '最新价' },
  { prop: 'volume', label: '成交总量' },
  { prop: 'amount', label: '成交总金额' }
]
const searchFields = []
</script>
