<template>
  <CrudPage :api="api" :columns="columns" :search-fields="searchFields" />
</template>

<script setup>
import CrudPage from '@/shared/CrudPage.vue'
import api from '@/api/etfFundShare'

const columns = [
  { prop: 'id', label: '主键ID' },
  { prop: 'etfCode', label: 'ETF代码' },
  { prop: 'changeDate', label: '变动日期' },
  { prop: 'fundShare', label: '基金份额' },
  { prop: 'totalShare', label: '基金总份额' },
  { prop: 'changeReason', label: '份额变动原因' }
]
const searchFields = []
</script>
