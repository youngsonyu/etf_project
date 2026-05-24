<template>
  <CrudPage :api="api" :columns="columns" :search-fields="searchFields" />
</template>

<script setup>
import CrudPage from '@/shared/CrudPage.vue'
import api from '@/api/etfPcfInfo'

const columns = [
  { prop: 'id', label: '主键ID' },
  { prop: 'etfCode', label: 'ETF代码' },
  { prop: 'tradingDay', label: '当前交易日' },
  { prop: 'creation', label: '是否允许申购' },
  { prop: 'redemption', label: '是否允许赎回' },
  { prop: 'symbol', label: '基金名称' }
]
const searchFields = []
</script>
