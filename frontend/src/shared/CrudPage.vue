<template>
  <div>
    <el-card class="mb12">
      <el-form :inline="true" :model="searchForm">
        <el-form-item v-for="item in normalizedSearchItems" :key="item.prop" :label="item.label">
          <el-select
            v-if="item.type === 'select'"
            v-model="searchForm[item.prop]"
            :placeholder="`请选择${item.label}`"
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

    <el-card>
      <div class="toolbar">
        <slot name="toolbar" :reload="loadData" :openDialog="openDialog" />
        <!-- <el-button type="primary" @click="openDialog()">新增</el-button> -->
        <!-- <el-button type="danger" :disabled="!selectedIds.length" @click="handleBatchDelete">批量删除</el-button> -->
      </div>

      <el-table :data="tableData" @selection-change="onSelectionChange" @sort-change="handleSortChange" border>
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

      <div class="pager">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          v-model:current-page="pagination.pageNum"
          v-model:page-size="pagination.pageSize"
          :total="pagination.total"
          @current-change="loadData"
          @size-change="loadData"
        />
      </div>
    </el-card>

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
import { computed, onMounted, reactive, ref } from 'vue'
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
    params[key] = value
  })

  if (searchForm.etfCode && String(searchForm.etfCode).trim() !== '') {
    params.keyword = String(searchForm.etfCode).trim()
  }

  if (searchForm.tradeDate && String(searchForm.tradeDate).trim() !== '') {
    const selectedDate = String(searchForm.tradeDate).trim()
    const selectedPeriod = searchForm.period ? String(searchForm.period).trim() : ''
    if (hasColumn('tradeTime') && (!selectedPeriod || selectedPeriod === 'day')) {
      params.tradeTimeDate = selectedDate
    } else if (hasColumn('tradeDate') && (!selectedPeriod || selectedPeriod === 'day')) {
      params.tradeDate = selectedDate
    } else if (hasColumn('tradingDay') && (!selectedPeriod || selectedPeriod === 'day')) {
      params.tradingDay = selectedDate
    } else if (hasColumn('priceDate') && (!selectedPeriod || selectedPeriod === 'day')) {
      params.priceDate = selectedDate
    } else if (hasColumn('changeDate') && (!selectedPeriod || selectedPeriod === 'day')) {
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

  const res = await props.api.page(params)
  const pageData = res.data || {}
  tableData.value = pageData.records || []
  pagination.total = pageData.total || 0
  pagination.pageNum = pageData.current || pagination.pageNum
  pagination.pageSize = pageData.size || pagination.pageSize
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
  margin-top: 12px;
}

.search-select {
  width: 160px;
}
</style>
