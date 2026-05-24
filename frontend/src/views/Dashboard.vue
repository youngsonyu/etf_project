<template>
  <div class="dashboard-page">
    <el-card class="hero-card">
      <h1>ETF 五维共振情报中心</h1>
      <p>追踪最新共振信号，聚焦关键ETF机会与风险提示。</p>
    </el-card>

    <el-card class="report-card">
      <template #header>
        <div class="header-row">
          <span>最新五维共振报告（最近5条）</span>
          <el-button type="primary" link @click="goReport">前往报告中心</el-button>
        </div>
      </template>

      <el-empty v-if="!reports.length" description="暂无报告" />
      <div v-else class="report-list">
        <div v-for="item in reports" :key="item.id" class="report-item">
          <el-link class="title-link" type="primary" @click="openViewer(item)">{{ item.title }}</el-link>
          <div class="meta">发布时间：{{ item.publishTime }} | 发布人：{{ item.publisher }}</div>
        </div>
      </div>
    </el-card>

    <el-dialog v-model="viewerVisible" title="" width="980px">
      <h2 class="dialog-title">{{ viewer.title }}</h2>
      <div class="dialog-meta">发布时间：{{ viewer.publishTime }} | 发布人：{{ viewer.publisher }}</div>
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
  gap: 14px;
}

.hero-card {
  background: linear-gradient(120deg, #0f766e, #0369a1);
  color: #f8fafc;
}

.hero-card h1 {
  margin: 0;
  font-size: 30px;
}

.hero-card p {
  margin: 10px 0 2px;
  color: #e0f2fe;
}

.header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.report-list {
  display: grid;
  gap: 10px;
}

.report-item {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 12px 14px;
}

.report-item .title {
  font-size: 16px;
  font-weight: 600;
  color: #0f172a;
}

.title-link {
  font-size: 16px;
  font-weight: 600;
}

.report-item .meta {
  margin-top: 4px;
  font-size: 12px;
  color: #64748b;
}

.dialog-title {
  margin: 0;
  font-size: 24px;
}

.dialog-meta {
  margin: 8px 0 16px;
  color: #64748b;
  font-size: 13px;
}

.md-body {
  line-height: 1.8;
}

:deep(.md-body h1),
:deep(.md-body h2),
:deep(.md-body h3) {
  margin: 14px 0 10px;
}

:deep(.md-body code) {
  background: #f1f5f9;
  padding: 2px 6px;
  border-radius: 4px;
}

:deep(.md-body pre) {
  background: #0f172a;
  color: #e2e8f0;
  padding: 12px;
  border-radius: 8px;
  overflow: auto;
}
</style>
