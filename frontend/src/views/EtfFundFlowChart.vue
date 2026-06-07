<template>
  <div class="chart-page">
    <el-card class="control-card">
      <div class="control-row">
        <div class="control-block">
          <div class="control-label">对比日期一</div>
          <el-date-picker
            v-model="datePoint1"
            value-format="YYYY-MM-DD"
            placeholder="选择日期"
            class="date-single"
          />
        </div>

        <div class="control-block">
          <div class="control-label">对比日期二</div>
          <el-date-picker
            v-model="datePoint2"
            value-format="YYYY-MM-DD"
            placeholder="选择日期"
            class="date-single"
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
        :title="alertText"
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
          <span class="badge badge-red">对比日期一：红色</span>
          <span class="badge badge-green">对比日期二：绿色</span>
        </div>
        <div style="margin-left:auto;display:flex;align-items:center;gap:8px">
          <span style="font-size:13px;color:#64748b;">排序：</span>
          <el-select v-model="sortType" size="small" style="width:200px" @change="renderChart">
            <el-option label="对比日期一金额" value="point1_desc" />
            <el-option label="对比日期二金额" value="point2_desc" />
            <el-option label="变化量绝对值" value="diff_abs_desc" />
          </el-select>
        </div>
      </div>

      <div v-if="!hasData" class="empty-state">
        <el-empty description="请选择两个时间点并点击生成图表" />
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

const datePoint1 = ref('')
const datePoint2 = ref('')
const viewMode = ref('eightDim')
const flowMetric = ref('daily')
const loading = ref(false)
const dataPoint1 = ref([])
const dataPoint2 = ref([])
const chartRef = ref(null)
let chartInstance = null
const sortType = ref('point1_desc')

const resizeHandler = () => {
  if (chartInstance) {
    chartInstance.resize()
  }
}

const hasData = computed(() => dataPoint1.value.length > 0 && dataPoint2.value.length > 0)

const chartSubtitle = computed(() => {
  if (!hasData.value) {
    return '未生成数据'
  }
  const metricLabel = {
    daily: '当日流向',
    cumulative: '累计流向'
  }[flowMetric.value]
  const viewLabel = {
    eightDim: '八维分类',
    industry: '产业细分'
  }[viewMode.value]
  return `${viewLabel} - ${metricLabel}对比 (${datePoint1.value} vs ${datePoint2.value})`
})

const alertText = computed(() => {
  const base = '说明：选择两个任意时间点，系统将展示该两个时间点的资金流向对比柱状图，红色代表对比日期一，绿色代表对比日期二，便于直观对比。'
  const cumulativeNote = flowMetric.value === 'cumulative' ? ' 累计流向统计金额起始时间为2024年9月24日起累计。' : ''
  const disclaimer = ' 该图表数据由基金份额和每日ETF收盘价计算，存在一定微小误差，图表仅供参考。'
  return base + cumulativeNote + disclaimer
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
    const dailyFlow = Number(row.fundFlow || row.fund_flow || 0) / 10000
    const cumulativeFlow = Number(row.cumulativeFlow || row.cumulative_flow || 0) / 10000

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

  const summary1 = summarizeByCategory(dataPoint1.value, viewMode.value)
  const summary2 = summarizeByCategory(dataPoint2.value, viewMode.value)

  const allCategoriesSet = new Set([
    ...summary1.map((item) => item.category),
    ...summary2.map((item) => item.category)
  ])

  const map1 = new Map(summary1.map((item) => [item.category, item]))
  const map2 = new Map(summary2.map((item) => [item.category, item]))

  const metricKey = {
    daily: 'dailyFlow',
    cumulative: 'cumulativeFlow'
  }[flowMetric.value]

  const chartData = Array.from(allCategoriesSet)
    .map((category) => {
      const item1 = map1.get(category) || { dailyFlow: 0, cumulativeFlow: 0, etfCount: 0 }
      const item2 = map2.get(category) || { dailyFlow: 0, cumulativeFlow: 0, etfCount: 0 }
      return {
        category,
        value1: item1[metricKey] || 0,
        value2: item2[metricKey] || 0,
        diff: (item2[metricKey] || 0) - (item1[metricKey] || 0),
        etfCount: Math.max(item1.etfCount || 0, item2.etfCount || 0)
      }
    })

  if (sortType.value === 'point1_desc') {
    chartData.sort((a, b) => b.value1 - a.value1)
  } else if (sortType.value === 'point2_desc') {
    chartData.sort((a, b) => b.value2 - a.value2)
  } else if (sortType.value === 'diff_abs_desc') {
    chartData.sort((a, b) => Math.abs(b.diff) - Math.abs(a.diff))
  }

  const categories = chartData.map((item) => item.category)
  const values1 = chartData.map((item) => item.value1)
  const values2 = chartData.map((item) => item.value2)
  const etfCounts = chartData.map((item) => item.etfCount)

  const maxVal = Math.max(...values1, ...values2, 1)

  const metricLabel = { daily: '当日流向', cumulative: '累计流向' }[flowMetric.value]

  instance.setOption(
    {
      backgroundColor: '#fff',
      animationDuration: 600,
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        formatter(params) {
          if (!params || !params.length) {
            return ''
          }
          const idx = params[0].dataIndex
          const item = chartData[idx]
          return [
            `<strong>${item.category}</strong>`,
            `ETF数量：${item.etfCount} 只`,
            `对比日期一：${item.value1.toFixed(2)} 万元`,
            `对比日期二：${item.value2.toFixed(2)} 万元`,
            `变化量：${item.diff >= 0 ? '+' : ''}${item.diff.toFixed(2)} 万元`
          ].join('<br/>')
        }
      },
      legend: {
        data: ['对比日期一', '对比日期二'],
        top: 12,
        right: 24,
        textStyle: { fontSize: 12 }
      },
      grid: { left: 60, right: 58, top: 76, bottom: 48, containLabel: true },
      xAxis: {
        type: 'category',
        data: categories,
        axisLabel: { interval: 0, rotate: categories.length > 7 ? 26 : 0 },
        name: '分类',
        nameLocation: 'middle',
        nameGap: 30
      },
      yAxis: {
        type: 'value',
        name: `${metricLabel}(万元)`,
        axisLabel: { formatter: (value) => `${value.toFixed(0)}万` },
        splitLine: { lineStyle: { type: 'dashed', color: '#dbe4f0' } },
        max: maxVal * 1.2
      },
      series: [
        {
          name: '对比日期一',
          type: 'bar',
          data: values1,
          itemStyle: {
            color: '#ef4444',
            borderRadius: [4, 4, 0, 0]
          },
          barWidth: '28%',
          label: {
            show: true,
            position: 'top',
            formatter: (p) => (p.value === 0 ? '0' : p.value.toFixed(0) + '万'),
            fontSize: 9,
            color: '#991b1b'
          }
        },
        {
          name: '对比日期二',
          type: 'bar',
          data: values2,
          itemStyle: {
            color: '#10b981',
            borderRadius: [4, 4, 0, 0]
          },
          barWidth: '28%',
          label: {
            show: true,
            position: 'top',
            formatter: (p) => (p.value === 0 ? '0' : p.value.toFixed(0) + '万'),
            fontSize: 9,
            color: '#166534'
          }
        }
      ]
    },
    true
  )
}

async function generateChart() {
  if (!datePoint1.value || !datePoint2.value) {
    ElMessage.warning('请选择两个时间点')
    return
  }

  if (datePoint1.value === datePoint2.value) {
    ElMessage.warning('请选择两个不同的时间点')
    return
  }

  loading.value = true
  try {
    const res1 = await api.getFlowDataByDate(datePoint1.value)
    const res2 = await api.getFlowDataByDate(datePoint2.value)

    dataPoint1.value = res1.data || []
    dataPoint2.value = res2.data || []

    await nextTick()
    renderChart()

    if (!hasData.value) {
      ElMessage.warning('该时间范围内没有可用于生成图表的数据')
    } else {
      ElMessage.success(`图表已生成 (${datePoint1.value} vs ${datePoint2.value})`)
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

.date-single {
  width: 160px;
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

  .date-single {
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