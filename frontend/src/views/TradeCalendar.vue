<template>
  <CrudPage :api="api" :columns="columns" :search-fields="searchFields" />
</template>

<script setup>
import CrudPage from '@/shared/CrudPage.vue'
import api from '@/api/tradeCalendar'

const columns = [
  { prop: 'id', label: '主键ID' },
  { prop: 'market', label: '市场' },
  { prop: 'tradeDate', label: '交易日' },
  { prop: 'isOpen', label: '是否开市' },
  { prop: 'source', label: '数据来源' }
]
const searchFields = []
</script>
