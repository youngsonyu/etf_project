<template>
  <CrudPage :api="api" :columns="columns" :search-fields="searchFields" />
</template>

<script setup>
import CrudPage from '@/shared/CrudPage.vue'
import api from '@/api/etfSecurityMaster'

const columns = [
  { prop: 'id', label: '主键ID' },
  { prop: 'etfCode', label: 'ETF代码' },
  { prop: 'market', label: '市场' },
  { prop: 'symbol', label: 'ETF简称' },
  { prop: 'isActive', label: '是否有效' },
  { prop: 'source', label: '数据来源' }
]
const searchFields = []
</script>
