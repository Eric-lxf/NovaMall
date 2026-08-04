<script setup>
defineOptions({ name: 'HistoryAdminEvent' })

import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  addHistoryEvent,
  delHistoryEvent,
  getHistoryEvent,
  listHistoryEvent,
  publishHistoryEvent,
  updateHistoryEvent
} from '@/api/history/event'
import { generateHistoryUnitFromEvent } from '@/api/history/unit'
import { listHistoryPeriodOptions } from '@/api/history/period'
import { listHistoryPlaceOptions } from '@/api/history/place'

const loading = ref(false)
const dataList = ref([])
const total = ref(0)
const open = ref(false)
const title = ref('')
const formRef = ref()
const periodOptions = ref([])
const placeOptions = ref([])
const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  title: '',
  periodId: undefined,
  auditStatus: undefined
})
const form = reactive({
  id: undefined,
  title: '',
  periodId: undefined,
  placeId: undefined,
  startYear: undefined,
  endYear: undefined,
  datePrecision: 'YEAR',
  originalDateText: '',
  calendarType: '中国传统纪年',
  isApproximate: false,
  summary: '',
  background: '',
  process: '',
  causeAnalysis: '',
  impact: '',
  uncertaintyNote: '',
  auditStatus: 'DRAFT',
  status: '0',
  remark: ''
})
const rules = {
  title: [{ required: true, message: '事件标题不能为空', trigger: 'blur' }]
}

async function loadOptions() {
  const [periods, places] = await Promise.all([listHistoryPeriodOptions(), listHistoryPlaceOptions()])
  periodOptions.value = periods.data || []
  placeOptions.value = places.data || []
}

async function getList() {
  loading.value = true
  try {
    const res = await listHistoryEvent(queryParams)
    dataList.value = res.rows || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

function resetForm() {
  Object.assign(form, {
    id: undefined,
    title: '',
    periodId: undefined,
    placeId: undefined,
    startYear: undefined,
    endYear: undefined,
    datePrecision: 'YEAR',
    originalDateText: '',
    calendarType: '中国传统纪年',
    isApproximate: false,
    summary: '',
    background: '',
    process: '',
    causeAnalysis: '',
    impact: '',
    uncertaintyNote: '',
    auditStatus: 'DRAFT',
    status: '0',
    remark: ''
  })
  formRef.value?.clearValidate()
}

function handleQuery() {
  queryParams.pageNum = 1
  getList()
}

function handleAdd() {
  resetForm()
  title.value = '新增事件'
  open.value = true
}

async function handleUpdate(row) {
  resetForm()
  const res = await getHistoryEvent(row.id)
  Object.assign(form, res.data || row)
  title.value = '修改事件'
  open.value = true
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确认删除事件「${row.title}」吗？`, '提示', { type: 'warning' })
  await delHistoryEvent(row.id)
  ElMessage.success('删除成功')
  getList()
}

async function handlePublish(row) {
  await ElMessageBox.confirm(`确认发布事件「${row.title}」吗？发布后将出现在学习端时间线。`, '提示', { type: 'warning' })
  await publishHistoryEvent(row.id)
  ElMessage.success('已发布')
  getList()
}

async function handleGenerateUnit(row) {
  await ElMessageBox.confirm(`根据事件「${row.title}」生成学习单元草稿？`, '提示', { type: 'info' })
  const res = await generateHistoryUnitFromEvent(row.id)
  ElMessage.success(`已生成草稿单元 #${res.data}，请到「学习单元」编辑并发布`)
}

async function submitForm() {
  await formRef.value.validate()
  if (form.id) {
    await updateHistoryEvent(form)
  } else {
    await addHistoryEvent(form)
  }
  ElMessage.success('保存成功')
  open.value = false
  getList()
}

onMounted(async () => {
  await loadOptions()
  await getList()
})
</script>

<template>
  <div class="app-container">
    <el-card shadow="never">
      <el-form :inline="true" :model="queryParams" @submit.prevent="handleQuery">
        <el-form-item label="标题">
          <el-input v-model="queryParams.title" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="时期">
          <el-select v-model="queryParams.periodId" clearable style="width: 160px">
            <el-option v-for="p in periodOptions" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="审核">
          <el-select v-model="queryParams.auditStatus" clearable style="width: 150px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="待审" value="PENDING_REVIEW" />
            <el-option label="已发布" value="PUBLISHED" />
            <el-option label="驳回" value="REJECTED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        </el-form-item>
      </el-form>

      <el-row class="mb8">
        <el-button type="primary" plain icon="Plus" v-hasPermi="['history:event:add']" @click="handleAdd">新增</el-button>
      </el-row>

      <el-table v-loading="loading" :data="dataList" stripe>
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
        <el-table-column label="年份" width="120" align="center">
          <template #default="{ row }">{{ row.startYear }}{{ row.endYear && row.endYear !== row.startYear ? `~${row.endYear}` : '' }}</template>
        </el-table-column>
        <el-table-column prop="auditStatus" label="审核" width="120" />
        <el-table-column prop="summary" label="摘要" min-width="200" show-overflow-tooltip />
        <el-table-column label="操作" width="280" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" v-hasPermi="['history:event:edit']" @click="handleUpdate(row)">修改</el-button>
            <el-button
              link
              type="success"
              v-if="row.auditStatus !== 'PUBLISHED'"
              v-hasPermi="['history:event:publish']"
              @click="handlePublish(row)"
            >发布</el-button>
            <el-button
              link
              type="warning"
              v-if="row.auditStatus === 'PUBLISHED'"
              v-hasPermi="['history:unit:add']"
              @click="handleGenerateUnit(row)"
            >生成单元</el-button>
            <el-button link type="danger" v-hasPermi="['history:event:remove']" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination
        v-show="total > 0"
        :total="total"
        v-model:page="queryParams.pageNum"
        v-model:limit="queryParams.pageSize"
        @pagination="getList"
      />
    </el-card>

    <el-dialog v-model="open" :title="title" width="720px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" maxlength="200" />
        </el-form-item>
        <el-form-item label="时期">
          <el-select v-model="form.periodId" clearable style="width: 100%">
            <el-option v-for="p in periodOptions" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="地点">
          <el-select v-model="form.placeId" clearable style="width: 100%">
            <el-option v-for="p in placeOptions" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="起始年">
          <el-input-number v-model="form.startYear" controls-position="right" />
        </el-form-item>
        <el-form-item label="结束年">
          <el-input-number v-model="form.endYear" controls-position="right" />
        </el-form-item>
        <el-form-item label="原始纪年">
          <el-input v-model="form.originalDateText" />
        </el-form-item>
        <el-form-item label="摘要">
          <el-input v-model="form.summary" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="背景">
          <el-input v-model="form.background" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="过程">
          <el-input v-model="form.process" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="原因">
          <el-input v-model="form.causeAnalysis" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="影响">
          <el-input v-model="form.impact" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="不确定性">
          <el-input v-model="form.uncertaintyNote" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="审核状态">
          <el-select v-model="form.auditStatus" style="width: 100%">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="待审" value="PENDING_REVIEW" />
            <el-option label="已发布" value="PUBLISHED" />
            <el-option label="驳回" value="REJECTED" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="open = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>
