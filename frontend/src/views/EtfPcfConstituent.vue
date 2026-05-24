<template>
  <CrudPage :api="api" :columns="columns" :search-fields="searchFields" />
</template>

<script setup>
import CrudPage from '@/shared/CrudPage.vue'
import api from '@/api/etfPcfConstituent'

const columns = [
  { prop: 'id', label: '主键ID' },
  { prop: 'etfCode', label: 'ETF代码' },
  { prop: 'tradingDay', label: '交易日' },
  { prop: 'constituentCode', label: '成分证券代码' },
  { prop: 'componentShare', label: '成分证券数量' },
  { prop: 'substituteFlag', label: '现金替代标志' }
]
const searchFields = []
</script>
