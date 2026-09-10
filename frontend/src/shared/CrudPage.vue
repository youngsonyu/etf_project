<template>
  <div>
    <el-card class="mb12">
      <el-form :inline="true" :model="searchForm">
        <el-form-item v-for="item in normalizedSearchItems" :key="item.prop" :label="item.label">
          <el-select
            v-if="item.type === 'select'"
            v-model="searchForm[item.prop]"
            :placeholder="`请选择${item.label}`"
            :multiple="item.multiple === true"
            :collapse-tags="item.multiple === true"
            :collapse-tags-tooltip="item.multiple === true"
            class="search-select"
            clearable
          >
            <el-option
              v-for="option in item.options || []"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
          <el-input
            v-else-if="item.type !== 'date'"
            v-model="searchForm[item.prop]"
            :placeholder="`请输入${item.label}`"
            clearable
          />
          <el-date-picker
            v-else
            v-model="searchForm[item.prop]"
            type="date"
            value-format="YYYY-MM-DD"
            :placeholder="`请选择${item.label}`"
            clearable
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="data-card">
      <div class="data-card-body">
        <div class="toolbar">
          <slot name="toolbar" :reload="loadData" :openDialog="openDialog" />
          <!-- <el-button type="primary" @click="openDialog()">新增</el-button> -->
          <!-- <el-button type="danger" :disabled="!selectedIds.length" @click="handleBatchDelete">批量删除</el-button> -->
        </div>

        <el-table :data="tableData" @selection-change="onSelectionChange" @sort-change="handleSortChange" border class="crud-table">
          <el-table-column type="selection" width="50" />
          <el-table-column
            v-for="col in normalizedColumns"
            :key="col.prop"
            :prop="col.prop"
            :label="col.label"
            :sortable="col.sortable === false ? false : 'custom'"
            min-width="140"
          >
            <template v-if="col.valueMap" #default="scope">
              {{ col.valueMap[scope.row[col.prop]] !== undefined ? col.valueMap[scope.row[col.prop]] : scope.row[col.prop] }}
            </template>
          </el-table-column>
          <el-table-column v-if="enableRowActions || enableRowDelete" label="操作" width="180" fixed="right">
            <template #default="scope">
              <el-button v-if="enableRowActions" size="small" type="primary" link @click="openDialog(scope.row)">编辑</el-button>
              <el-button v-if="enableRowDelete" size="small" type="danger" link @click="handleDelete(scope.row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>

    <Teleport to="body">
      <div class="pager" :style="pagerStyle">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next, jumper"
          :page-sizes="[10, 20, 50, 100, 200]"
          v-model:current-page="pagination.pageNum"
          v-model:page-size="pagination.pageSize"
          :total="pagination.total"
          :pager-count="7"
          @current-change="loadData"
          @size-change="loadData"
        />
      </div>
    </Teleport>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑' : '新增'" width="720px">
      <el-form :model="form" label-width="140px">
        <el-form-item v-for="col in formColumns" :key="col.prop" :label="col.label">
          <el-input v-model="form[col.prop]" clearable />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const props = defineProps({
  api: { type: Object, required: true },
  columns: { type: Array, required: true },
  searchFields: { type: Array, default: () => [] },
  searchItems: { type: Array, default: null },
  enableRowDelete: { type: Boolean, default: false },
  enableRowActions: { type: Boolean, default: false }
})

const enableRowDelete = computed(() => props.enableRowDelete)
const enableRowActions = computed(() => props.enableRowActions)

const normalizedColumns = computed(() =>
  (props.columns || [])
    .map((item) => (typeof item === 'string' ? { prop: item, label: item } : item))
    .filter((item) => item.prop !== 'id')
)

const normalizedSearchFields = computed(() =>
  (props.searchFields || []).map((item) => {
    if (typeof item === 'string') {
      return { prop: item, label: item, type: 'input' }
    }
    return { type: 'input', ...item }
  })
)

const normalizedSearchItems = computed(() => {
  if (Array.isArray(props.searchItems)) {
    return props.searchItems.map((item) => ({ type: 'input', ...item }))
  }
  return [
    { prop: 'etfCode', label: 'ETF代码', type: 'input' },
    { prop: 'tradeDate', label: '日期', type: 'date' },
    ...normalizedSearchFields.value
  ]
})

const searchForm = reactive({})
const form = reactive({ id: null })
const dialogVisible = ref(false)
const tableData = ref([])
const selectedIds = ref([])
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const sortState = reactive({ field: '', order: '' })

normalizedSearchItems.value.forEach((item) => {
  searchForm[item.prop] = item.defaultValue ?? ''
})

watch(
  () => normalizedSearchItems.value.map((i) => i.defaultValue).join(','),
  () => {
    normalizedSearchItems.value.forEach((item) => {
      if (item.defaultValue !== undefined && (searchForm[item.prop] === '' || searchForm[item.prop] === undefined)) {
        searchForm[item.prop] = item.defaultValue
      }
    })
  },
  { immediate: true }
)

const formColumns = computed(() => normalizedColumns.value.filter((c) => c.prop !== 'id'))

function hasColumn(prop) {
  return normalizedColumns.value.some((item) => item.prop === prop)
}

function onSelectionChange(rows) {
  selectedIds.value = rows.map((item) => item.id)
}

function handleSearch() {
  pagination.pageNum = 1
  loadData()
}

function handleSortChange({ prop, order }) {
  sortState.field = prop || ''
  sortState.order = order === 'ascending' ? 'asc' : order === 'descending' ? 'desc' : ''
  pagination.pageNum = 1
  loadData()
}

function handleReset() {
  normalizedSearchItems.value.forEach((item) => {
    searchForm[item.prop] = item.defaultValue ?? ''
  })
  sortState.field = ''
  sortState.order = ''
  pagination.pageNum = 1
  loadData()
}

function openDialog(row) {
  Object.keys(form).forEach((key) => delete form[key])
  if (row) {
    Object.assign(form, row)
  } else {
    form.id = null
  }
  dialogVisible.value = true
}

async function submitForm() {
  if (form.id) {
    await props.api.update(form.id, form)
    ElMessage.success('修改成功')
  } else {
    await props.api.add(form)
    ElMessage.success('新增成功')
  }
  dialogVisible.value = false
  loadData()
}

async function handleDelete(row) {
  await ElMessageBox.confirm('确认删除该记录吗？', '提示', { type: 'warning' })
  await props.api.remove(row.id)
  ElMessage.success('删除成功')
  loadData()
}

async function handleBatchDelete() {
  await ElMessageBox.confirm('确认批量删除选中记录吗？', '提示', { type: 'warning' })
  await props.api.batchDelete(selectedIds.value)
  ElMessage.success('批量删除成功')
  loadData()
}

function buildParams() {
  const params = {}

  if (sortState.field && sortState.order) {
    params.sortField = sortState.field
    params.sortOrder = sortState.order
  }

  Object.entries(searchForm).forEach(([key, value]) => {
    if (value === null || value === undefined || String(value).trim() === '') {
      return
    }
    if (key === 'etfCode' || key === 'tradeDate') {
      return
    }
    // 多选下拉框（数组）转成逗号分隔字符串，后端用 FIND_IN_SET 匹配
    if (Array.isArray(value)) {
      if (value.length === 0) {
        return
      }
      params[key] = value.join(',')
    } else {
      params[key] = value
    }
  })

  if (searchForm.etfCode && String(searchForm.etfCode).trim() !== '') {
    params.keyword = String(searchForm.etfCode).trim()
  }

  if (searchForm.tradeDate && String(searchForm.tradeDate).trim() !== '') {
    const selectedDate = String(searchForm.tradeDate).trim()
    // period 无论是单值还是多选数组，trade_time 都是按天对齐（D 日 4 周期都写 D 00:00:00），
    // 因此 K 线时间筛选对所有 period 都生效
    if (hasColumn('tradeTime')) {
      params.tradeTimeDate = selectedDate
    } else if (hasColumn('tradeDate')) {
      params.tradeDate = selectedDate
    } else if (hasColumn('tradingDay')) {
      params.tradingDay = selectedDate
    } else if (hasColumn('priceDate')) {
      params.priceDate = selectedDate
    } else if (hasColumn('changeDate')) {
      params.changeDate = selectedDate
    }
  }

  return params
}

async function loadData() {
  const params = {
    pageNum: pagination.pageNum,
    pageSize: pagination.pageSize,
    ...buildParams()
  }

  try {
    const res = await props.api.page(params)
    const pageData = res.data || {}
    tableData.value = pageData.records || []
    // 后端 JacksonConfig 把所有 long 转 string 序列化（防 JS 精度丢失）
    // 前端需要强制转 number，否则 el-pagination 看到 "1633" 不会当数字
    const toNum = (v, fallback = 0) => {
      const n = Number(v)
      return Number.isFinite(n) ? n : fallback
    }
    pagination.total = toNum(pageData.total, 0)
    pagination.pageNum = toNum(pageData.current, pagination.pageNum)
    pagination.pageSize = toNum(pageData.size, pagination.pageSize)
  } catch (error) {
    ElMessage.error(error?.message || '加载列表失败')
  }
}

onMounted(() => {
  loadData()
})

defineExpose({
  loadData,
  buildParams,
  openDialog
})
</script>

<style scoped>
.mb12 {
  margin-bottom: 12px;
}

.toolbar {
  margin-bottom: 12px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  margin-top: 12px;
  padding: 12px 16px;
  background: #fafbfc;
  position: sticky;
  bottom: 0;
  z-index: 5;
  border-top: 1px solid #ebeef5;
  border-radius: 4px;
  box-shadow: 0 -2px 6px rgba(0, 0, 0, 0.03);
  min-height: 56px;
}

.data-card .data-card-body {
  display: flex;
  flex-direction: column;
  min-height: 0;
  height: calc(100vh - 240px);
  padding-bottom: 80px; /* 给 fixed pager 留位置 */
}

.data-card .data-card-body .crud-table {
  flex: 1 1 auto;
  min-height: 0;
}

.data-card .data-card-body :deep(.el-table__inner-wrapper) {
  max-height: 100%;
  overflow: auto;
}

.pager {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  margin: 0;
  padding: 10px 16px;
  background: #ffffff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
  min-height: 48px;
  position: fixed;
  bottom: 16px;
  left: 264px;            /* 240px 侧边栏 + 24px 边距 */
  right: 56px;            /* 给 el-main 滚动条留 40px */
  z-index: 9999;
}

.search-select {
  width: 160px;
}
</style>
