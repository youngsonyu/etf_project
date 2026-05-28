<template>
  <CrudPage :api="api" :columns="columns" :search-items="searchItems" :enable-row-actions="true">
    <template #toolbar="{ openDialog }">
      <el-button type="primary" @click="openDialog()">新增</el-button>
    </template>
  </CrudPage>
</template>

<script setup>
import CrudPage from '@/shared/CrudPage.vue'
import api from '@/api/sysParam'

const columns = [
  { prop: 'id', label: '主键ID' },
  { prop: 'paramKey', label: '参数键' },
  { prop: 'paramValue', label: '参数值' },
  { prop: 'description', label: '参数描述' },
  { prop: 'isActive', label: '是否有效', valueMap: { 1: '是', 0: '否' } }
]

const searchItems = [
  { prop: 'paramKeyLike', label: '参数键', type: 'input' },
  { prop: 'descriptionLike', label: '参数描述', type: 'input' },
  {
    prop: 'isActive',
    label: '是否有效',
    type: 'select',
    options: [
      { label: '是', value: 1 },
      { label: '否', value: 0 }
    ]
  }
]
</script>
