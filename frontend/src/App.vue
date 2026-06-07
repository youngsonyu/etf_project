<template>
  <router-view v-if="isLoginPage" />
  <el-container v-else style="height: 100vh">
    <el-aside width="240px" class="app-sidebar">
      <div class="logo">ETF量化分析平台</div>
      <el-menu router :default-active="$route.path" class="menu">
        <template v-for="item in treeMenus" :key="item.menuCode">
          <el-sub-menu v-if="childrenMap[item.menuCode] && childrenMap[item.menuCode].length" :index="`group-${item.menuCode}`">
            <template #title>{{ item.label }}</template>
            <el-menu-item v-if="item.path" :index="item.path">{{ item.label }}首页</el-menu-item>
            <el-menu-item
              v-for="child in childrenMap[item.menuCode]"
              :key="child.menuCode"
              :index="child.path"
            >
              {{ child.label }}
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else :index="item.path">
            {{ item.label }}
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="app-header">
        <div class="header-right">
          <span class="trade-date">当前交易日期：{{ currentTradeDateDisplay }}</span>
          <span class="username">{{ currentUsername }}</span>
          <el-button type="danger" size="small" @click="handleLogout">退出登录</el-button>
        </div>
      </el-header>
      <el-main class="app-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import menuConfigApi from './api/menuConfig'
import etlProgressApi from './api/etlProgress'

const route = useRoute()
const router = useRouter()
const isLoginPage = computed(() => route.path === '/login')
const currentUsername = ref('未登录')
const currentTradeDateDisplay = ref('暂无')

const routeMap = {
  dashboard: '/dashboard',
  etf_fund_flow_summary: '/etf_fund_flow_summary',
  etf_fund_flow_chart: '/etf_fund_flow_chart',
  etf_fund_share: '/etf_fund_share',
  etf_fund_iopv: '/etf_fund_iopv',
  etf_market_kline: '/etf_market_kline',
  etf_market_snapshot: '/etf_market_snapshot',
  etf_pcf_constituent: '/etf_pcf_constituent',
  etf_pcf_info: '/etf_pcf_info',
  etf_security_master: '/etf_security_master',
  etf_ta_indicator: '/etf_ta_indicator',
  etf_five_dimension_resonance: '/etf_five_dimension_resonance',
  etf_five_dimension_report: '/etf_five_dimension_report',
  trade_calendar: '/trade_calendar',
  menu_config: '/menu_config',
  sys_param: '/sys_param',
  etl_batch_status: '/etl_batch_status',
  etl_checkpoint: '/etl_checkpoint',
  change_password: '/change-password'
}

const nonClickableGroupCodes = new Set(['system', 'etf_regular', 'etf_quant', 'etf_ai'])

const parentOverride = {
  trade_calendar: 'system',
  menu_config: 'system',
  sys_param: 'system',
  etl_batch_status: 'system',
  etl_checkpoint: 'system',
  dashboard: 'etf_home',
  etf_fund_flow_summary: 'etf_regular',
  etf_fund_flow_chart: 'etf_regular',
  etf_security_master: 'etf_regular',
  etf_market_snapshot: 'etf_regular',
  etf_market_kline: 'etf_regular',
  etf_pcf_info: 'etf_regular',
  etf_pcf_constituent: 'etf_regular',
  etf_fund_share: 'etf_regular',
  etf_fund_iopv: 'etf_regular',
  etf_ta_indicator: 'etf_regular',
  etf_five_dimension_resonance: 'etf_quant',
  etf_five_dimension_report: 'etf_quant',
  etf_ai_assistant: 'etf_ai',
  user_management: 'system'
}

const orderOverride = {
  etf_home: 1,
  etf_regular: 2,
  etf_quant: 3,
  etf_ai: 4,
  system: 5
}

const normalUserAllowedMenuCodes = new Set([
  'dashboard',
  'etf_fund_flow_summary',
  'etf_ta_indicator',
  'etf_five_dimension_report',
  'etf_five_dimension_resonance',
  'etf_fund_flow_chart',
  'etf_ai_assistant',
  'etf_market_kline',
  'change_password'
])

const isNormalUser = computed(() => localStorage.getItem('etf_login_type') === 'user')

const fallbackMenus = [
  { menuCode: 'etf_home', menuName: '首页', path: null },
  { menuCode: 'dashboard', menuName: '首页', path: '/dashboard' },
  { menuCode: 'etf_regular', menuName: 'ETF常规指标', path: null },
  { menuCode: 'etf_fund_flow_summary', menuName: 'ETF资金流向', path: '/etf_fund_flow_summary' },
  { menuCode: 'etf_fund_flow_chart', menuName: 'ETF资金流向图表', path: '/etf_fund_flow_chart' },
  { menuCode: 'etf_security_master', menuName: 'ETF基础信息', path: '/etf_security_master' },
  { menuCode: 'etf_market_snapshot', menuName: 'ETF行情快照', path: '/etf_market_snapshot' },
  { menuCode: 'etf_market_kline', menuName: 'K线数据', path: '/etf_market_kline' },
  { menuCode: 'etf_pcf_info', menuName: 'ETF PCF头信息', path: '/etf_pcf_info' },
  { menuCode: 'etf_pcf_constituent', menuName: 'ETF PCF成分券', path: '/etf_pcf_constituent' },
  { menuCode: 'etf_fund_share', menuName: 'ETF基金份额', path: '/etf_fund_share' },
  { menuCode: 'etf_fund_iopv', menuName: 'ETF IOPV', path: '/etf_fund_iopv' },
  { menuCode: 'etf_ta_indicator', menuName: 'ETF技术指标', path: '/etf_ta_indicator' },
  { menuCode: 'etf_quant', menuName: 'ETF量化模型', path: null },
  { menuCode: 'etf_five_dimension_resonance', menuName: 'ETF量化数据分析', path: '/etf_five_dimension_resonance' },
  { menuCode: 'etf_five_dimension_report', menuName: 'ETF量化报告', path: '/etf_five_dimension_report' },
  { menuCode: 'etf_ai', menuName: 'ETF智能助手', path: null },
  { menuCode: 'etf_ai_assistant', menuName: 'ETF智能助手', path: '/etf_ai_assistant' },
  { menuCode: 'system', menuName: '系统管理', path: '/system' },
  { menuCode: 'trade_calendar', menuName: '交易日历', path: '/trade_calendar' },
  { menuCode: 'menu_config', menuName: '菜单配置', path: '/menu_config' },
  { menuCode: 'sys_param', menuName: '参数配置', path: '/sys_param' },
  { menuCode: 'etl_batch_status', menuName: 'ETL跑批状态', path: '/etl_batch_status' },
  { menuCode: 'etl_checkpoint', menuName: 'ETL跑批检查点', path: '/etl_checkpoint' },
  { menuCode: 'change_password', menuName: '修改密码', path: '/change-password' },
  { menuCode: 'user_management', menuName: '用户管理', path: '/user_management' }
]

const alwaysVisibleMenus = [
  { menuCode: 'etf_fund_flow_chart', menuName: 'ETF资金流向图表', path: '/etf_fund_flow_chart' }
]

const menus = ref([])
const childrenMap = computed(() => {
  const map = {}
  menus.value.forEach((item) => {
    if (!item.parentCode) {
      return
    }
    if (!map[item.parentCode]) {
      map[item.parentCode] = []
    }
    map[item.parentCode].push(item)
  })
  Object.keys(map).forEach((key) => {
    map[key].sort(compareMenu)
  })
  return map
})

const treeMenus = computed(() => {
  const codes = new Set(menus.value.map((item) => item.menuCode))
  return menus.value
    .filter((item) => !item.parentCode || !codes.has(item.parentCode))
    .sort(compareMenu)
})

function compareMenu(a, b) {
  const orderA = orderOverride[a.menuCode] || (a.sort ?? 9999)
  const orderB = orderOverride[b.menuCode] || (b.sort ?? 9999)
  if (orderA !== orderB) {
    return orderA - orderB
  }
  return String(a.label || '').localeCompare(String(b.label || ''))
}

function handleLogout() {
  localStorage.removeItem('etf_logged_in')
  localStorage.removeItem('etf_username')
  localStorage.removeItem('etf_account')
  localStorage.removeItem('etf_display_name')
  localStorage.removeItem('etf_login_type')
  router.replace('/login')
}

function syncCurrentUsername() {
  const storedAccount = (localStorage.getItem('etf_account') || '').trim()
  const fallback = (localStorage.getItem('etf_username') || '').trim()
  const account = storedAccount || fallback

  if (account) {
    currentUsername.value = account
  } else {
    currentUsername.value = '未登录'
  }
}

function formatTradeDate(value) {
  if (!value) {
    return '暂无'
  }
  const text = String(value)
  if (/^\d{8}$/.test(text)) {
    return `${text.slice(0, 4)}-${text.slice(4, 6)}-${text.slice(6, 8)}`
  }
  return text
}

async function loadCurrentTradeDate() {
  const response = await etlProgressApi.currentTradeDate()
  currentTradeDateDisplay.value = formatTradeDate(response.data)
}

function normalizeMenu(menu) {
  const path = routeMap[menu.menuCode] || menu.path || '/'
  const parentCode = parentOverride[menu.menuCode] || menu.parentCode || null
  return {
    menuCode: menu.menuCode,
    label: menu.menuName,
    path: nonClickableGroupCodes.has(menu.menuCode) ? null : path,
    parentCode,
    sort: Number(menu.sort ?? 9999)
  }
}

async function loadMenus() {
  const response = await menuConfigApi.visible()
  const remoteMenus = Array.isArray(response.data) ? response.data : []
  const sourceMenus = remoteMenus.length > 0 ? remoteMenus : fallbackMenus
  const filteredMenus = sourceMenus.map(normalizeMenu)

  const mergedMenus = [...filteredMenus, ...alwaysVisibleMenus.map(normalizeMenu)]
  const deduped = []
  const seen = new Set()
  mergedMenus.forEach((item) => {
    if (seen.has(item.menuCode)) {
      return
    }
    seen.add(item.menuCode)
    deduped.push(item)
  })
  if (isNormalUser.value) {
    const parentsWithAllowedChildren = new Set()
    deduped.forEach((item) => {
      if (normalUserAllowedMenuCodes.has(item.menuCode) && item.parentCode) {
        parentsWithAllowedChildren.add(item.parentCode)
      }
    })
    menus.value = deduped.filter(
      (item) =>
        normalUserAllowedMenuCodes.has(item.menuCode) ||
        parentsWithAllowedChildren.has(item.menuCode)
    )
  } else {
    menus.value = deduped
  }
}

onMounted(() => {
  syncCurrentUsername()
  loadMenus().catch(() => {
    menus.value = fallbackMenus.map(normalizeMenu)
  })
  loadCurrentTradeDate().catch(() => {
    currentTradeDateDisplay.value = '暂无'
  })
})

watch(
  () => route.fullPath,
  () => {
    syncCurrentUsername()
  }
)
</script>

<style scoped>
.app-sidebar {
  border-right: 1px solid #e4e7ed;
  background: linear-gradient(180deg, #fafbfc 0%, #f1f3f5 100%);
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.04);
}
.logo {
  font-size: 17px;
  font-weight: 700;
  padding: 20px 18px;
  color: #0f766e;
  letter-spacing: 0.5px;
  border-bottom: 1px solid #e4e7ed;
  background: linear-gradient(135deg, #f0fdfa 0%, #ffffff 100%);
}
.app-header {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  background: #ffffff;
  border-bottom: 1px solid #e4e7ed;
  padding: 0 20px;
  height: 56px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.username {
  font-size: 14px;
  color: #1e293b;
  font-weight: 600;
  padding: 6px 12px;
  background: #f1f5f9;
  border-radius: 6px;
}

.trade-date {
  font-size: 13px;
  color: #64748b;
  padding: 6px 12px;
  background: #f8fafc;
  border-radius: 6px;
  border: 1px solid #e4e7ed;
}

.app-main {
  background: #f8fafc;
  padding: 16px 20px;
}
.menu {
  border-right: none;
  background: transparent;
}

.menu :deep(.el-menu-item),
.menu :deep(.el-sub-menu__title) {
  border-radius: 8px;
  margin: 2px 8px;
  padding-left: 16px !important;
}

.menu :deep(.el-menu-item:hover),
.menu :deep(.el-sub-menu__title:hover) {
  background: #e0f2fe;
}

.menu :deep(.el-menu-item.is-active) {
  background: linear-gradient(135deg, #0f766e 0%, #0891b2 100%);
  color: #ffffff;
}
</style>
