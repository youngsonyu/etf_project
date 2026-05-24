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
import { useRouter } from 'vue-router'

const router = useRouter()

const menuItems = [
  { title: '首页', description: 'ETF数据分析平台首页', path: '/dashboard' },
  { title: 'ETF资金流向', description: '查看资金流向和累计资金流', path: '/etf_fund_flow_summary' },
  { title: 'ETF技术指标', description: '查看ETF技术指标数据', path: '/etf_ta_indicator' },
  { title: 'ETF五维共振报告', description: '查看五维共振报告内容', path: '/etf_five_dimension_report' },
  { title: 'ETF五维共振数据分析', description: '查看五维共振数据分析结果', path: '/etf_five_dimension_resonance' },
  { title: 'ETF资金流向图表', description: '查看资金流向图表', path: '/etf_fund_flow_chart' },
  { title: 'ETF智能助手', description: 'AI助手智能分析ETF数据', path: '/etf_ai_assistant' },
  { title: 'K线数据', description: '查看ETF行情K线数据', path: '/etf_market_kline' }
]

function go(path) {
  router.push(path)
}
</script>

<style scoped>
.user-menu-page {
  padding: 24px;
}

.user-menu-header {
  margin-bottom: 20px;
}

.user-menu-header h2 {
  margin: 0;
  font-size: 24px;
  color: #0f172a;
}

.user-menu-header p {
  margin: 8px 0 0;
  color: #475569;
}

.menu-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 20px;
}

.menu-card {
  cursor: pointer;
  min-height: 140px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.card-title {
  font-size: 18px;
  font-weight: 700;
  margin-bottom: 10px;
}

.card-description {
  color: #475569;
  line-height: 1.6;
  min-height: 3rem;
}
</style>
