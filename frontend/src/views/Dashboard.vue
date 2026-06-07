<template>
  <div class="dashboard-page">
    <el-card class="hero-card">
      <h1>ETF 量化情报中心</h1>
      <p>追踪最新量化信号，聚焦关键ETF机会与风险提示。</p>
    </el-card>

    <el-card class="report-card">
      <template #header>
        <div class="header-row">
          <span>最新量化报告（最近5条）</span>
          <el-button type="primary" link @click="goReport">前往报告中心</el-button>
        </div>
      </template>

      <el-empty v-if="!reports.length" description="暂无报告" />
      <div v-else class="report-list">
        <div v-for="item in reports" :key="item.id" class="report-item">
          <el-link class="title-link" type="primary" @click="openViewer(item)">{{ item.title }}</el-link>
          <div class="meta">发布时间：{{ item.publishTime }} | 发布人：{{ formatPublisher(item.publisher) }}</div>
        </div>
      </div>
    </el-card>

    <el-dialog v-model="viewerVisible" title="" width="980px">
      <h2 class="dialog-title">{{ viewer.title }}</h2>
      <div class="dialog-meta">发布时间：{{ viewer.publishTime }} | 发布人：{{ formatPublisher(viewer.publisher) }}</div>
      <div class="md-body" v-html="viewerHtml"></div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import reportApi from '@/api/etfFiveDimensionReport'
import { renderMarkdown } from '@/utils/markdown'

const router = useRouter()
const reports = ref([])
const viewerVisible = ref(false)
const viewer = reactive({ title: '', publishTime: '', publisher: '', contentMd: '' })
const viewerHtml = computed(() => renderMarkdown(viewer.contentMd))

function formatPublisher(publisher) {
  if (!publisher) return ''
  const displayName = localStorage.getItem('etf_display_name') || ''
  const account = localStorage.getItem('etf_account') || ''
  if (displayName && publisher === account) {
    return displayName
  }
  if (/^1[3-9]\d{9}$/.test(publisher)) {
    return publisher.slice(0, 3) + '****' + publisher.slice(7)
  }
  return publisher
}

async function loadLatestReports() {
  const res = await reportApi.latest(5)
  reports.value = res?.data || []
}

function goReport() {
  router.push('/etf_five_dimension_report')
}

function openViewer(item) {
  viewer.title = item.title || ''
  viewer.publishTime = item.publishTime || ''
  viewer.publisher = item.publisher || ''
  viewer.contentMd = item.contentMd || ''
  viewerVisible.value = true
}

onMounted(() => {
  loadLatestReports()
})
</script>

<style scoped>
.dashboard-page {
  display: grid;
  gap: 16px;
}

:deep(.el-card) {
  border-radius: 14px;
  border: 1px solid #e4e7ed;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
}

.hero-card {
  background: linear-gradient(135deg, #0f766e 0%, #0891b2 100%);
  color: #f8fafc;
  border: none;
  border-radius: 16px;
  padding: 8px 0;
}

.hero-card h1 {
  margin: 0;
  font-size: 32px;
  font-weight: 700;
  letter-spacing: 0.5px;
}

.hero-card p {
  margin: 12px 0 4px;
  color: #e0f2fe;
  font-size: 15px;
}

.header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 4px;
}

.header-row span {
  font-weight: 600;
  color: #1e293b;
  font-size: 15px;
}

.report-list {
  display: grid;
  gap: 12px;
}

.report-item {
  border: 1px solid #e4e7ed;
  border-radius: 10px;
  padding: 14px 16px;
  transition: all 0.2s ease;
  background: #ffffff;
}

.report-item:hover {
  border-color: #0f766e;
  box-shadow: 0 4px 12px rgba(15, 118, 110, 0.1);
  transform: translateY(-1px);
}

.title-link {
  font-size: 15px;
  font-weight: 600;
  color: #0f766e;
}

.report-item .meta {
  margin-top: 6px;
  font-size: 12px;
  color: #94a3b8;
}

.dialog-title {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  color: #0f172a;
}

.dialog-meta {
  margin: 10px 0 20px;
  color: #64748b;
  font-size: 13px;
  padding-bottom: 12px;
  border-bottom: 1px solid #e4e7ed;
}

.md-body {
  line-height: 1.8;
  color: #334155;
}

:deep(.md-body h1),
:deep(.md-body h2),
:deep(.md-body h3) {
  margin: 18px 0 12px;
  color: #0f172a;
}

:deep(.md-body h2) {
  border-bottom: 1px solid #e4e7ed;
  padding-bottom: 8px;
}

:deep(.md-body code) {
  background: #f1f5f9;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 13px;
}

:deep(.md-body pre) {
  background: #1e293b;
  color: #e2e8f0;
  padding: 16px;
  border-radius: 10px;
  overflow: auto;
}

:deep(.el-dialog__header) {
  border-bottom: 1px solid #e4e7ed;
  padding: 18px 20px;
  margin-right: 0;
}

:deep(.el-dialog__body) {
  padding: 20px 24px;
}
</style>
