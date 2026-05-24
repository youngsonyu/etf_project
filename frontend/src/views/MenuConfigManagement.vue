<template>
  <CrudPage :api="api" :columns="columns" :search-items="searchItems" />
</template>

<script setup>
import CrudPage from '@/shared/CrudPage.vue'
import api from '@/api/menuConfigCrud'

const columns = [
  { prop: 'id', label: '主键ID' },
  { prop: 'menuCode', label: '菜单编码' },
  { prop: 'menuName', label: '菜单名称' },
  { prop: 'parentCode', label: '父级菜单编码' },
  { prop: 'path', label: '路由路径' },
  { prop: 'icon', label: '图标' },
  { prop: 'sort', label: '排序' },
  { prop: 'isVisible', label: '是否显示', valueMap: { 1: '是', 0: '否' } },
  { prop: 'isActive', label: '是否有效', valueMap: { 1: '是', 0: '否' } },
  { prop: 'remark', label: '备注' }
]

const searchItems = [
  { prop: 'menuCode', label: '菜单编码', type: 'input' },
  { prop: 'menuName', label: '菜单名称', type: 'input' },
  {
    prop: 'isVisible',
    label: '是否显示',
    type: 'select',
    options: [
      { label: '是', value: 1 },
      { label: '否', value: 0 }
    ]
  },
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
