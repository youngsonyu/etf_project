<template>
  <div class="user-menu-page">
    <div class="user-menu-header">
      <h2>普通用户菜单</h2>
      <p>以下为普通用户可用功能，点击卡片即可进入对应页面。</p>
    </div>

    <div class="menu-grid">
      <el-card
        v-for="item in menuItems"
        :key="item.path"
        class="menu-card"
        shadow="hover"
        @click="go(item.path)"
      >
        <div class="card-title">{{ item.title }}</div>
        <div class="card-description">{{ item.description }}</div>
        <el-button type="primary" size="small" @click.stop="go(item.path)">进入</el-button>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { useRouter, useRoute } from 'vue-router'
import { onMounted } from 'vue'
import { ElMessage } from 'element-plus'

const router = useRouter()
const route = useRoute()

const menuItems = [
  { title: '首页', description: 'ETF量化分析平台首页', path: '/dashboard' },
  { title: 'ETF资金流向', description: '查看资金流向和累计资金流', path: '/etf_fund_flow_summary' },
  { title: 'ETF技术指标', description: '查看ETF技术指标数据', path: '/etf_ta_indicator' },
  { title: 'ETF量化报告', description: '查看量化分析报告内容', path: '/etf_five_dimension_report' },
  { title: 'ETF量化数据分析', description: '查看量化数据分析结果', path: '/etf_five_dimension_resonance' },
  { title: 'ETF资金流向图表', description: '查看资金流向图表', path: '/etf_fund_flow_chart' },
  { title: 'ETF智能助手', description: 'AI助手智能分析ETF数据', path: '/etf_ai_assistant' },
  { title: 'K线数据', description: '查看ETF行情K线数据', path: '/etf_market_kline' }
]

function go(path) {
  router.push(path)
}

onMounted(() => {
  if (route.query.error === 'no_admin') {
    ElMessage.error('普通用户无管理员权限，请选择其他界面功能')
  }
})
</script>

<style scoped>
.user-menu-page {
  padding: 20px;
}

.user-menu-header {
  margin-bottom: 24px;
  padding: 16px 20px;
  background: linear-gradient(135deg, #f0fdfa 0%, #ffffff 100%);
  border-radius: 14px;
  border: 1px solid #e4e7ed;
}

.user-menu-header h2 {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  color: #0f172a;
}

.user-menu-header p {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 14px;
}

.menu-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 18px;
}

.menu-card {
  cursor: pointer;
  min-height: 150px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  border-radius: 14px;
  border: 1px solid #e4e7ed;
  transition: all 0.25s ease;
  padding: 4px 0;
}

.menu-card:hover {
  border-color: #0f766e;
  box-shadow: 0 8px 24px rgba(15, 118, 110, 0.12);
  transform: translateY(-3px);
}

.card-title {
  font-size: 16px;
  font-weight: 700;
  margin-bottom: 10px;
  color: #0f172a;
}

.card-description {
  color: #64748b;
  line-height: 1.6;
  flex: 1;
  font-size: 13px;
}

.menu-card :deep(.el-button) {
  margin-top: 12px;
  border-radius: 8px;
  width: fit-content;
}
</style>
