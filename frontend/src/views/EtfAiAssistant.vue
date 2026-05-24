<template>
  <div class="ai-assistant-page">
    <!-- 顶栏 -->
    <div class="ai-header">
      <div class="ai-header-title">
        <el-icon class="ai-icon"><MagicStick /></el-icon>
        <span>ETF 智能助手</span>
        <el-tag size="small" type="success" class="ml8">AI · 只读分析</el-tag>
      </div>
      <el-button size="small" plain @click="clearChat" :disabled="loading">清空对话</el-button>
    </div>

    <!-- 消息区域 -->
    <div class="chat-body" ref="chatBodyRef">
      <!-- 欢迎气泡 -->
      <div v-if="messages.length === 0" class="welcome-wrap">
        <div class="welcome-card">
          <p class="welcome-title">👋 你好，我是 ETF 智能助手</p>
          <p class="welcome-sub">我会优先按最新交易日分析ETF核心数据表，避免过多历史数据导致分析过慢。</p>
          <div class="quick-btns">
            <el-button
              v-for="q in quickQuestions"
              :key="q"
              size="small"
              plain
              @click="sendQuick(q)"
            >{{ q }}</el-button>
          </div>
        </div>
      </div>

      <!-- 对话消息列表 -->
      <div v-for="msg in messages" :key="msg.id" :class="['msg-row', msg.role]">
        <div class="msg-avatar">
          <el-icon v-if="msg.role === 'assistant'"><MagicStick /></el-icon>
          <el-icon v-else><User /></el-icon>
        </div>
        <div class="msg-content-wrap">
          <!-- 用户消息 -->
          <div v-if="msg.role === 'user'" class="msg-bubble user-bubble">{{ msg.content }}</div>

          <!-- AI 消息 -->
          <div v-else class="msg-bubble ai-bubble">
            <!-- 工具调用详情（可展开） -->
            <el-collapse v-if="msg.toolsUsed && msg.toolsUsed.length" class="tool-collapse">
              <el-collapse-item :title="`🔧 调用了 ${msg.toolsUsed.length} 个数据查询工具`" name="tools">
                <div v-for="(tool, idx) in msg.toolsUsed" :key="idx" class="tool-item">
                  <div class="tool-name">{{ toolLabel(tool.name) }}</div>
                  <div class="tool-args">参数：{{ JSON.stringify(tool.args) }}</div>
                  <pre class="tool-result">{{ tool.result }}</pre>
                </div>
              </el-collapse-item>
            </el-collapse>

            <!-- AI 回答（Markdown 渲染） -->
            <div class="ai-reply" v-html="renderMd(msg.content)" />
          </div>
        </div>
      </div>

      <!-- 加载中 -->
      <div v-if="loading" class="msg-row assistant">
        <div class="msg-avatar"><el-icon><MagicStick /></el-icon></div>
        <div class="msg-content-wrap">
          <div class="msg-bubble ai-bubble loading-bubble">
            <span class="dot-flash">●</span>
            <span class="dot-flash dot2">●</span>
            <span class="dot-flash dot3">●</span>
            <span class="loading-text ml8">正在分析数据，请稍候…</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 免责提示 -->
    <div class="disclaimer">⚠️ 以上内容由 AI 根据数据库数据自动生成，仅供参考，不构成任何投资建议。</div>

    <!-- 输入区 -->
    <div class="chat-input-area">
      <el-input
        v-model="inputText"
        type="textarea"
        :rows="3"
        placeholder="输入问题，例如：哪些ETF今日触发了五维共振？（Ctrl+Enter 发送）"
        :disabled="loading"
        @keydown.ctrl.enter.prevent="sendMessage"
        resize="none"
      />
      <div class="input-actions">
        <span class="input-tip">Ctrl+Enter 发送</span>
        <el-button type="primary" :loading="loading" @click="sendMessage">发 送</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { MagicStick, User } from '@element-plus/icons-vue'
import { marked } from 'marked'
import aiApi from '@/api/etfAiAssistant'

marked.setOptions({ gfm: true, breaks: true })

// ── 状态 ──────────────────────────────────────────
const messages   = ref([])  // { id, role: 'user'|'assistant', content, toolsUsed }
const inputText  = ref('')
const loading    = ref(false)
const chatBodyRef = ref(null)

let msgSeq = 0

const quickQuestions = [
  '哪些ETF今日触发了五维共振？',
  '给我看看沪深300ETF最近的技术指标',
  '近期资金净流入最多的ETF有哪些？',
  '帮我分析510050的走势',
  '今天有哪些ETF风险预警？'
]

const toolNameMap = {
  query_etf_list: 'ETF基础信息查询',
  query_market_snapshot: 'ETF行情快照查询',
  query_kline_data: 'K线数据查询',
  query_ta_indicator: '技术指标查询',
  query_five_dimension_resonance: '五维共振查询',
  query_fund_flow: '资金流向查询',
  query_fund_share: '基金份额查询',
  query_fund_iopv: 'IOPV净值查询',
  query_trade_calendar: '交易日历查询'
}

function toolLabel(name) {
  return toolNameMap[name] || name
}

function renderMd(content) {
  if (!content) return ''
  return marked.parse(content)
}

// ── 滚动到底部 ────────────────────────────────────
async function scrollToBottom() {
  await nextTick()
  if (chatBodyRef.value) {
    chatBodyRef.value.scrollTop = chatBodyRef.value.scrollHeight
  }
}

// ── 快速问题 ──────────────────────────────────────
function sendQuick(q) {
  inputText.value = q
  sendMessage()
}

// ── 发送消息 ──────────────────────────────────────
async function sendMessage() {
  const text = inputText.value.trim()
  if (!text) return
  if (loading.value) return

  // 添加用户消息
  messages.value.push({ id: ++msgSeq, role: 'user', content: text })
  inputText.value = ''
  await scrollToBottom()

  // 构建历史（最近 10 轮，避免 token 超限）
  const history = []
  const recentMsgs = messages.value.slice(-21, -1) // 去掉刚加的用户消息
  for (const m of recentMsgs) {
    if (m.role === 'user' || m.role === 'assistant') {
      history.push({ role: m.role, content: m.content })
    }
  }

  loading.value = true
  try {
    const res = await aiApi.chat({ message: text, history })
    const data = res?.data || {}
    messages.value.push({
      id: ++msgSeq,
      role: 'assistant',
      content: data.reply || '（AI 未返回内容）',
      toolsUsed: data.toolsUsed || []
    })
  } catch (err) {
    const errMsg = err?.response?.data?.message || err?.message || '请求失败'
    messages.value.push({
      id: ++msgSeq,
      role: 'assistant',
      content: `❌ 请求出错：${errMsg}\n\n请检查 AI API Key 是否已配置（系统管理 → 参数配置 → ai.dashscope.api-key）`,
      toolsUsed: []
    })
    ElMessage.error('AI 请求失败')
  } finally {
    loading.value = false
    await scrollToBottom()
  }
}

// ── 清空对话 ──────────────────────────────────────
function clearChat() {
  messages.value = []
  inputText.value = ''
}
</script>

<style scoped>
.ai-assistant-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 60px);
  background: #f5f7fa;
  overflow: hidden;
}

/* ── 顶栏 ── */
.ai-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  flex-shrink: 0;
}
.ai-header-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}
.ai-icon { color: #409eff; font-size: 20px; }
.ml8 { margin-left: 4px; }

/* ── 消息区 ── */
.chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 欢迎卡 */
.welcome-wrap {
  display: flex;
  justify-content: center;
  margin-top: 40px;
}
.welcome-card {
  background: #fff;
  border-radius: 12px;
  padding: 28px 36px;
  text-align: center;
  max-width: 560px;
  box-shadow: 0 2px 12px rgba(0,0,0,.06);
}
.welcome-title { font-size: 18px; font-weight: 600; color: #303133; margin-bottom: 8px; }
.welcome-sub   { color: #909399; font-size: 14px; margin-bottom: 20px; }
.quick-btns    { display: flex; flex-wrap: wrap; gap: 8px; justify-content: center; }

/* 消息行 */
.msg-row {
  display: flex;
  gap: 10px;
  max-width: 100%;
}
.msg-row.user {
  flex-direction: row-reverse;
}

.msg-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: #ecf5ff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 16px;
  color: #409eff;
}
.msg-row.user .msg-avatar {
  background: #f0f9eb;
  color: #67c23a;
}

.msg-content-wrap {
  max-width: calc(100% - 56px);
  display: flex;
  flex-direction: column;
}

/* 气泡 */
.msg-bubble {
  padding: 12px 16px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.7;
  word-break: break-word;
}
.user-bubble {
  background: #409eff;
  color: #fff;
  border-bottom-right-radius: 4px;
  white-space: pre-wrap;
}
.ai-bubble {
  background: #fff;
  color: #303133;
  border-bottom-left-radius: 4px;
  box-shadow: 0 1px 4px rgba(0,0,0,.08);
}

/* 工具调用折叠 */
.tool-collapse {
  margin-bottom: 10px;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  overflow: hidden;
}
.tool-item  { padding: 8px 0; border-bottom: 1px dashed #eee; }
.tool-item:last-child { border-bottom: none; }
.tool-name  { font-weight: 600; color: #409eff; font-size: 13px; margin-bottom: 4px; }
.tool-args  { color: #909399; font-size: 12px; margin-bottom: 4px; }
.tool-result {
  background: #f8f9fa;
  padding: 8px;
  border-radius: 4px;
  font-size: 12px;
  color: #606266;
  white-space: pre-wrap;
  max-height: 200px;
  overflow-y: auto;
  margin: 0;
}

/* AI 回答 Markdown */
.ai-reply :deep(h1), .ai-reply :deep(h2), .ai-reply :deep(h3) {
  margin: 8px 0 6px;
  font-weight: 600;
}
.ai-reply :deep(p)  { margin: 4px 0; }
.ai-reply :deep(ul), .ai-reply :deep(ol) { padding-left: 20px; margin: 4px 0; }
.ai-reply :deep(li) { margin: 2px 0; }
.ai-reply :deep(table) {
  border-collapse: collapse;
  width: 100%;
  font-size: 13px;
  margin: 8px 0;
}
.ai-reply :deep(th), .ai-reply :deep(td) {
  border: 1px solid #e4e7ed;
  padding: 6px 10px;
  text-align: left;
}
.ai-reply :deep(th) { background: #f5f7fa; font-weight: 600; }
.ai-reply :deep(code) {
  background: #f0f2f5;
  padding: 2px 6px;
  border-radius: 3px;
  font-size: 12px;
}
.ai-reply :deep(blockquote) {
  border-left: 3px solid #409eff;
  padding-left: 10px;
  color: #909399;
  margin: 4px 0;
}

/* 加载动画 */
.loading-bubble {
  display: flex;
  align-items: center;
  padding: 12px 18px;
}
.dot-flash {
  font-size: 8px;
  color: #409eff;
  animation: flash 1.2s infinite;
  margin-right: 3px;
}
.dot2 { animation-delay: 0.4s; }
.dot3 { animation-delay: 0.8s; }
.loading-text { font-size: 13px; color: #909399; }
@keyframes flash {
  0%, 80%, 100% { opacity: 0.2; }
  40%           { opacity: 1; }
}

/* ── 免责提示 ── */
.disclaimer {
  text-align: center;
  font-size: 12px;
  color: #c0c4cc;
  padding: 4px 20px;
  flex-shrink: 0;
}

/* ── 输入区 ── */
.chat-input-area {
  background: #fff;
  border-top: 1px solid #e4e7ed;
  padding: 12px 20px;
  flex-shrink: 0;
}
.input-actions {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 10px;
  margin-top: 8px;
}
.input-tip {
  font-size: 12px;
  color: #c0c4cc;
}
</style>
