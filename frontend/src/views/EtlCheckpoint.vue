<template>
  <CrudPage :api="api" :columns="columns" :search-items="searchItems" />
</template>

<script setup>
import CrudPage from '@/shared/CrudPage.vue'
import api from '@/api/etlCheckpoint'

const columns = [
  { prop: 'id', label: '主键ID', sortable: false },
  { prop: 'checkpointKey', label: '检查点键' },
  { prop: 'lastTradeDate', label: '最后交易日' },
  { prop: 'lastBatchNo', label: '最后批次号' },
  { prop: 'updatedAt', label: '更新时间' }
]

const searchItems = [
  { prop: 'checkpointKey', label: '检查点键', type: 'input' },
  { prop: 'lastBatchNo', label: '最后批次号', type: 'input' }
]
</script>
