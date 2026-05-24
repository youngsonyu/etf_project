import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/', redirect: '/dashboard' },
  { path: '/login', name: 'Login', meta: { public: true }, component: () => import('../views/Login.vue') },
  { path: '/user_menu', name: 'UserMenu', component: () => import('../views/NormalUserMenu.vue') },
  { path: '/dashboard', name: 'Dashboard', component: () => import('../views/Dashboard.vue') },
  { path: '/etf_security_master', name: 'EtfSecurityMaster', component: () => import('../views/EtfSecurityMaster.vue') },
  { path: '/trade_calendar', name: 'TradeCalendar', component: () => import('../views/TradeCalendar.vue') },
  { path: '/etf_market_snapshot', name: 'EtfMarketSnapshot', component: () => import('../views/EtfMarketSnapshot.vue') },
  { path: '/etf_market_kline', name: 'EtfMarketKline', component: () => import('../views/EtfMarketKline.vue') },
  { path: '/etf_pcf_info', name: 'EtfPcfInfo', component: () => import('../views/EtfPcfInfo.vue') },
  { path: '/etf_pcf_constituent', name: 'EtfPcfConstituent', component: () => import('../views/EtfPcfConstituent.vue') },
  { path: '/etf_fund_share', name: 'EtfFundShare', component: () => import('../views/EtfFundShare.vue') },
  { path: '/etf_fund_iopv', name: 'EtfFundIopv', component: () => import('../views/EtfFundIopv.vue') },
  { path: '/etf_ta_indicator', name: 'EtfTaIndicator', component: () => import('../views/EtfTaIndicator.vue') },
  { path: '/etf_fund_flow_summary', name: 'EtfFundFlowSummary', component: () => import('../views/EtfFundFlowSummary.vue') },
  { path: '/etf_fund_flow_chart', name: 'EtfFundFlowChart', component: () => import('../views/EtfFundFlowChart.vue') },
  { path: '/etf_five_dimension_resonance', name: 'EtfFiveDimensionResonance', component: () => import('../views/EtfFiveDimensionResonance.vue') },
  { path: '/etf_five_dimension_report', name: 'EtfFiveDimensionReport', component: () => import('../views/EtfFiveDimensionReport.vue') },
  { path: '/etf_ai_assistant', name: 'EtfAiAssistant', component: () => import('../views/EtfAiAssistant.vue') },
  { path: '/system', name: 'SystemManagement', component: () => import('../views/SystemManagement.vue') },
  { path: '/menu_config', name: 'MenuConfigManagement', component: () => import('../views/MenuConfigManagement.vue') },
  { path: '/sys_param', name: 'SysParamManagement', component: () => import('../views/SysParamManagement.vue') },
  { path: '/etl_batch_status', name: 'EtlBatchStatus', component: () => import('../views/EtlBatchStatus.vue') },
  { path: '/etl_checkpoint', name: 'EtlCheckpoint', component: () => import('../views/EtlCheckpoint.vue') }
]

const normalUserAllowedPaths = new Set([
  '/dashboard',
  '/etf_fund_flow_summary',
  '/etf_ta_indicator',
  '/etf_five_dimension_report',
  '/etf_five_dimension_resonance',
  '/etf_fund_flow_chart',
  '/etf_ai_assistant',
  '/etf_market_kline',
  '/user_menu'
])

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const isLoggedIn = localStorage.getItem('etf_logged_in') === '1'
  const isPublicRoute = Boolean(to.meta && to.meta.public)
  const loginType = localStorage.getItem('etf_login_type') || ''
  const isNormalUser = loginType === 'user'

  if (!isLoggedIn && !isPublicRoute) {
    next('/login')
    return
  }

  if (isLoggedIn && to.path === '/login') {
    next(isNormalUser ? '/user_menu' : '/dashboard')
    return
  }

  if (isLoggedIn && isNormalUser && !normalUserAllowedPaths.has(to.path)) {
    next('/user_menu')
    return
  }

  next()
})

export default router
