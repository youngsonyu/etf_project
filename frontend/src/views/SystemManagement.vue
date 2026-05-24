<template>
  <div class="system-page">
    <el-card class="system-card">
      <template #header>
        <span>系统管理</span>
      </template>
      <div class="actions">
        <el-button type="primary" @click="go('/menu_config')">菜单配置</el-button>
        <el-button type="success" @click="go('/sys_param')">参数配置</el-button>
        <el-button @click="go('/trade_calendar')">交易日历</el-button>
        <el-button type="warning" @click="go('/etl_batch_status')">ETL跑批状态</el-button>
        <el-button type="warning" plain @click="go('/etl_checkpoint')">ETL跑批检查点</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()

function go(path) {
  router.push(path)
}
</script>

<style scoped>
.system-page {
  padding: 8px;
}

.actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}
</style>
