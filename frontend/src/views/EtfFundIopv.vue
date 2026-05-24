<template>
  <CrudPage :api="api" :columns="columns" :search-fields="searchFields" />
</template>

<script setup>
import CrudPage from '@/shared/CrudPage.vue'
import api from '@/api/etfFundIopv'

const columns = [
  { prop: 'id', label: '主键ID' },
  { prop: 'etfCode', label: 'ETF代码' },
  { prop: 'priceDate', label: '日期' },
  { prop: 'iopvNav', label: 'IOPV收盘净值' },
  { prop: 'source', label: '数据来源' },
  { prop: 'etlBatchNo', label: 'ETL批次号' }
]
const searchFields = []
</script>
