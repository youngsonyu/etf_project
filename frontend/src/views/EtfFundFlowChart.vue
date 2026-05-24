<template>
  <div class="chart-page">
    <el-card class="control-card">
      <div class="control-row">
        <div class="control-block">
          <div class="control-label">时间范围（必须是连续两个交易日）</div>
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            unlink-panels
            class="date-range"
          />
        </div>

        <div class="control-block">
          <div class="control-label">分类维度</div>
          <el-radio-group v-model="viewMode" class="mode-group">
            <el-radio-button label="eightDim">八维分类</el-radio-button>
            <el-radio-button label="industry">产业细分</el-radio-button>
          </el-radio-group>
        </div>

        <div class="control-block">
          <div class="control-label">流向指标</div>
          <el-radio-group v-model="flowMetric" class="mode-group">
            <el-radio-button label="daily">当日流向</el-radio-button>
            <el-radio-button label="cumulative">累计流向</el-radio-button>
          </el-radio-group>
        </div>

        <el-button type="primary" :loading="loading" class="generate-btn" @click="generateChart">
          生成图表
        </el-button>
      </div>

      <el-alert
        :title="`说明：系统自动识别日期范围内最近两个交易日进行对比。当日流向差值 = 今日 - 昨日，红色表示增加，绿色表示减少。`"
        type="info"
        :closable="false"
        show-icon
      />
    </el-card>

    <el-card class="chart-card">
      <div class="chart-header">
        <div>
          <div class="chart-title">ETF资金流向对比分析</div>
          <div class="chart-subtitle">{{ chartSubtitle }}</div>
        </div>
        <div class="chart-badges">
          <span class="badge badge-red">📈 增加：红色</span>
          <span class="badge badge-green">📉 减少：绿色</span>
        </div>
        <div style="margin-left:auto;display:flex;align-items:center;gap:8px">
          <span style="font-size:13px;color:#64748b;">排序：</span>
          <el-select v-model="sortType" size="small" style="width:260px" @change="renderChart">
            <el-option label="{t-1日}当日金额" value="t_minus_1_daily_desc" />
            <el-option label="{t日}当日金额" value="t_daily_desc" />
            <el-option label="{t日}-{t-1日}当日金额差值" value="daily_diff_desc" />
          </el-select>
        </div>
      </div>

      <div v-if="!hasData" class="empty-state">
        <el-empty description="请选择时间范围并点击生成图表" />
      </div>

      <div v-else ref="chartRef" class="chart-body" />
    </el-card>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import api from '@/api/etfFundFlowChart'
import { classifyView } from '@/utils/etfFlowClassification'

const dateRange = ref([])
const viewMode = ref('eightDim')
const flowMetric = ref('daily')
const loading = ref(false)
const yesterdayData = ref([])
const todayData = ref([])
const chartRef = ref(null)
let chartInstance = null
const sortType = ref('t_minus_1_daily_desc')

const resizeHandler = () => {
  if (chartInstance) {
    chartInstance.resize()
  }
}

const hasData = computed(() => yesterdayData.value.length > 0 && todayData.value.length > 0)

const chartSubtitle = computed(() => {
  if (!yesterdayData.value.length || !todayData.value.length) {
    return '未生成数据'
  }
  const metricLabel = flowMetric.value === 'daily' ? '当日流向' : '累计流向'
  const viewLabel = viewMode.value === 'eightDim' ? '八维分类' : '产业细分'
  return `${viewLabel} - ${metricLabel}差值分析`
})

function ensureChart() {
  if (!chartRef.value) {
    return null
  }
  if (!chartInstance) {
    chartInstance = echarts.init(chartRef.value)
  }
  return chartInstance
}

function summarizeByCategory(dataList, viewMode) {
  const summaryMap = new Map()

  dataList.forEach((row) => {
    const etfName = row.etfName || row.etf_name || ''
    const category = classifyView(etfName, viewMode)
    if (!category) {
      return
    }

    const etfCode = row.etfCode || row.etf_code || ''
    // 金额单位转换为万元
    const dailyFlow = (Number(row.fundFlow || row.fund_flow || 0) || 0) / 10000
    const cumulativeFlow = (Number(row.cumulativeFlow || row.cumulative_flow || 0) || 0) / 10000

    const current = summaryMap.get(category) || {
      category,
      etfCountSet: new Set(),
      dailyFlow: 0,
      cumulativeFlow: 0
    }

    current.etfCountSet.add(etfCode)
    current.dailyFlow += dailyFlow
    current.cumulativeFlow += cumulativeFlow
    summaryMap.set(category, current)
  })

  return Array.from(summaryMap.values()).map((item) => ({
    category: item.category,
    etfCount: item.etfCountSet.size,
    dailyFlow: item.dailyFlow,
    cumulativeFlow: item.cumulativeFlow
  }))
}

function renderChart() {
  const instance = ensureChart()
  if (!instance) {
    return
  }

  const yesterdaySummary = summarizeByCategory(yesterdayData.value, viewMode.value)
  const todaySummary = summarizeByCategory(todayData.value, viewMode.value)

  // 合并两天的分类，构建diff数据
  const allCategoriesSet = new Set([
    ...yesterdaySummary.map((item) => item.category),
    ...todaySummary.map((item) => item.category)
  ])

  const yesterdayMap = new Map(yesterdaySummary.map((item) => [item.category, item]))
  const todayMap = new Map(todaySummary.map((item) => [item.category, item]))

  let diffData = Array.from(allCategoriesSet)
    .map((category) => {
      const yItem = yesterdayMap.get(category) || { dailyFlow: 0, cumulativeFlow: 0, etfCount: 0 }
      const tItem = todayMap.get(category) || { dailyFlow: 0, cumulativeFlow: 0, etfCount: 0 }

      const metric = flowMetric.value === 'daily' ? 'dailyFlow' : 'cumulativeFlow'
      const yesterdayVal = yItem[metric] || 0
      const todayVal = tItem[metric] || 0
      const diffVal = todayVal - yesterdayVal
      const etfCount = Math.max(yItem.etfCount || 0, tItem.etfCount || 0)

      return {
        category,
        yesterdayVal,
        todayVal,
        diffVal,
        etfCount
      }
    })

  // 排序逻辑
  if (sortType.value === 't_minus_1_daily_desc') {
    diffData.sort((a, b) => b.yesterdayVal - a.yesterdayVal)
  } else if (sortType.value === 't_daily_desc') {
    diffData.sort((a, b) => b.todayVal - a.todayVal)
  } else if (sortType.value === 'daily_diff_desc') {
    diffData.sort((a, b) => b.diffVal - a.diffVal)
  }

  const categories = diffData.map((item) => item.category)
  const diffValues = diffData.map((item) => item.diffVal)
  const yesterdayValues = diffData.map((item) => item.yesterdayVal)
  const todayValues = diffData.map((item) => item.todayVal)
  const etfCounts = diffData.map((item) => item.etfCount)

  const barColors = diffValues.map((val) => (val >= 0 ? '#ef4444' : '#10b981'))
  const maxVal = Math.max(...diffValues, 1)
  const minVal = Math.min(...diffValues, -1)

  const metricLabel = flowMetric.value === 'daily' ? '当日流向' : '累计流向'

  instance.setOption(
    {
      backgroundColor: '#fff',
      animationDuration: 600,
      title: {
        text: `${metricLabel}变化差值 (今日-昨日)`,
        left: 'center',
        top: 0,
        textStyle: { fontSize: 14, fontWeight: 'bold' }
      },
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        formatter(params) {
          if (!params || !params.length) {
            return ''
          }
          const idx = params[0].dataIndex
          const diff = diffValues[idx]
          const trend = diff >= 0 ? '📈 净流入增加 / 流出减少' : '📉 净流入减少 / 流出扩大'
          return [
            `<strong>${categories[idx]}</strong>`,
            `ETF数量：${etfCounts[idx]} 只`,
            `昨日${metricLabel}：${yesterdayValues[idx].toFixed(2)} 万元`,
            `今日${metricLabel}：${todayValues[idx].toFixed(2)} 万元`,
            `变化：${diff >= 0 ? '+' : ''}${diff.toFixed(2)} 万元`,
            trend
          ].join('<br/>')
        }
      },
      legend: {
        data: ['资金变化'],
        top: 12,
        right: 24
      },
      grid: { left: 52, right: 58, top: 76, bottom: 48, containLabel: true },
      xAxis: {
        type: 'category',
        data: categories,
        axisLabel: { interval: 0, rotate: categories.length > 7 ? 26 : 0 }
      },
      yAxis: {
        type: 'value',
        name: `${metricLabel}变化(万元)`,
        axisLabel: { formatter: (value) => `${value.toFixed(0)}万` },
        splitLine: { lineStyle: { type: 'dashed', color: '#dbe4f0' } },
        min: minVal < 0 ? minVal * 1.15 : -10,
        max: maxVal > 0 ? maxVal * 1.15 : 10
      },
      series: [
        {
          name: '资金变化',
          type: 'bar',
          data: diffValues,
          itemStyle: {
            color: (params) => barColors[params.dataIndex],
            borderRadius: [8, 8, 0, 0]
          },
          barWidth: '60%',
          label: {
            show: true,
            position: 'top',
            formatter: (p) => (p.value === 0 ? '持平' : (p.value > 0 ? '+' : '') + p.value.toFixed(0) + '万'),
            fontSize: 9
          }
        }
      ]
    },
    true
  )
}

async function generateChart() {
  if (!Array.isArray(dateRange.value) || dateRange.value.length !== 2) {
    ElMessage.warning('请选择开始日期和结束日期')
    return
  }

  const [startDate, endDate] = dateRange.value
  loading.value = true
  try {
    const res = await api.flowComparison({ startDate, endDate })
    const payload = res.data || {}
    if (payload.error) {
      ElMessage.warning(payload.error)
      return
    }
    yesterdayData.value = payload.yesterdayData || []
    todayData.value = payload.todayData || []
    await nextTick()
    renderChart()
    if (!hasData.value) {
      ElMessage.warning('该时间范围内没有可用于生成图表的数据')
    } else {
      ElMessage.success(
        `图表已生成 (昨日: ${payload.yesterday}, 今日: ${payload.today})`
      )
    }
  } catch (error) {
    ElMessage.error(error?.message || '生成图表失败')
  } finally {
    loading.value = false
  }
}

watch(() => [viewMode.value, flowMetric.value], async () => {
  if (!hasData.value) {
    return
  }
  await nextTick()
  renderChart()
})

onMounted(() => {
  window.addEventListener('resize', resizeHandler)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeHandler)
  if (chartInstance) {
    chartInstance.dispose()
    chartInstance = null
  }
})
</script>

<style scoped>
.chart-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.control-card,
.chart-card {
  border-radius: 18px;
}

.control-row {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  align-items: end;
  margin-bottom: 14px;
}

.control-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.control-label {
  font-size: 13px;
  color: #475569;
  font-weight: 600;
}

.date-range {
  min-width: 320px;
}

.mode-group {
  display: flex;
}

.generate-btn {
  height: 40px;
}

.chart-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: start;
  margin-bottom: 12px;
}

.chart-title {
  font-size: 20px;
  font-weight: 700;
  color: #0f172a;
}

.chart-subtitle {
  margin-top: 4px;
  color: #64748b;
  font-size: 13px;
}

.chart-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.badge {
  padding: 6px 10px;
  border-radius: 999px;
  font-size: 12px;
  background: #dbeafe;
  color: #1d4ed8;
}

.badge-red {
  background: #fee2e2;
  color: #dc2626;
}

.badge-green {
  background: #dcfce7;
  color: #16a34a;
}

.chart-body {
  width: 100%;
  height: 620px;
}

.empty-state {
  min-height: 560px;
  display: flex;
  align-items: center;
  justify-content: center;
}

@media (max-width: 768px) {
  .chart-header {
    flex-direction: column;
  }

  .date-range {
    min-width: 100%;
  }

  .control-row {
    flex-direction: column;
  }

  .chart-body {
    height: 520px;
  }
}
</style>