<script setup>
defineOptions({ name: 'HistoryAdminUnit' })

import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  addHistoryUnit,
  delHistoryUnit,
  generateHistoryUnitFromEvent,
  getHistoryUnit,
  listHistoryUnit,
  publishHistoryUnit,
  updateHistoryUnit
} from '@/api/history/unit'
import { listHistoryEvent } from '@/api/history/event'
import { listHistoryPeriodOptions } from '@/api/history/period'

const loading = ref(false)
const dataList = ref([])
const total = ref(0)
const open = ref(false)
const generateOpen = ref(false)
const title = ref('')
const formRef = ref()
const periodOptions = ref([])
const eventOptions = ref([])
const generateEventId = ref()
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
  eventId: undefined,
  periodId: undefined,
  oneLiner: '',
  objectives: '',
  prerequisites: '',
  timePlace: '',
  background: '',
  keyPeople: '',
  process: '',
  causeAnalysis: '',
  impact: '',
  sourcesAndViews: '',
  practiceHint: '',
  furtherReading: '',
  auditStatus: 'DRAFT',
  status: '0',
  remark: ''
})
const rules = {
  title: [{ required: true, message: '单元标题不能为空', trigger: 'blur' }]
}

async function loadOptions() {
  const [periods, events] = await Promise.all([
    listHistoryPeriodOptions(),
    listHistoryEvent({ pageNum: 1, pageSize: 100, auditStatus: 'PUBLISHED' })
  ])
  periodOptions.value = periods.data || []
  eventOptions.value = events.rows || []
}

async function getList() {
  loading.value = true
  try {
    const res = await listHistoryUnit(queryParams)
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
    eventId: undefined,
    periodId: undefined,
    oneLiner: '',
    objectives: '',
    prerequisites: '',
    timePlace: '',
    background: '',
    keyPeople: '',
    process: '',
    causeAnalysis: '',
    impact: '',
    sourcesAndViews: '',
    practiceHint: '',
    furtherReading: '',
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
  title.value = '新增学习单元'
  open.value = true
}

async function handleUpdate(row) {
  resetForm()
  const res = await getHistoryUnit(row.id)
  Object.assign(form, res.data || row)
  title.value = '修改学习单元'
  open.value = true
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确认删除单元「${row.title}」吗？`, '提示', { type: 'warning' })
  await delHistoryUnit(row.id)
  ElMessage.success('删除成功')
  getList()
}

async function handlePublish(row) {
  await ElMessageBox.confirm(`确认发布单元「${row.title}」吗？`, '提示', { type: 'warning' })
  await publishHistoryUnit(row.id)
  ElMessage.success('已发布')
  getList()
}

async function submitForm() {
  await formRef.value.validate()
  if (form.id) {
    await updateHistoryUnit(form)
  } else {
    await addHistoryUnit(form)
  }
  ElMessage.success('保存成功')
  open.value = false
  getList()
}

async function submitGenerate() {
  if (!generateEventId.value) {
    ElMessage.warning('请选择已发布事件')
    return
  }
  const res = await generateHistoryUnitFromEvent(generateEventId.value)
  ElMessage.success(`已生成草稿单元 #${res.data}`)
  generateOpen.value = false
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
          <el-select v-model="queryParams.auditStatus" clearable style="width: 140px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已发布" value="PUBLISHED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        </el-form-item>
      </el-form>

      <el-row class="mb8" :gutter="10">
        <el-col :span="1.5">
          <el-button type="primary" plain icon="Plus" v-hasPermi="['history:unit:add']" @click="handleAdd">新增</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button type="success" plain icon="MagicStick" v-hasPermi="['history:unit:add']" @click="generateOpen = true">从事件生成</el-button>
        </el-col>
      </el-row>

      <el-table v-loading="loading" :data="dataList" stripe>
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
        <el-table-column prop="eventId" label="事件ID" width="90" align="center" />
        <el-table-column prop="oneLiner" label="一句话" min-width="200" show-overflow-tooltip />
        <el-table-column prop="auditStatus" label="审核" width="110" />
        <el-table-column label="操作" width="220" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" v-hasPermi="['history:unit:edit']" @click="handleUpdate(row)">修改</el-button>
            <el-button
              link
              type="success"
              v-if="row.auditStatus !== 'PUBLISHED'"
              v-hasPermi="['history:unit:publish']"
              @click="handlePublish(row)"
            >发布</el-button>
            <el-button link type="danger" v-hasPermi="['history:unit:remove']" @click="handleDelete(row)">删除</el-button>
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

    <el-dialog v-model="open" :title="title" width="760px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" maxlength="200" />
        </el-form-item>
        <el-form-item label="关联事件">
          <el-select v-model="form.eventId" clearable filterable style="width: 100%">
            <el-option v-for="e in eventOptions" :key="e.id" :label="e.title" :value="e.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="时期">
          <el-select v-model="form.periodId" clearable style="width: 100%">
            <el-option v-for="p in periodOptions" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="一句话概括">
          <el-input v-model="form.oneLiner" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="学习目标">
          <el-input v-model="form.objectives" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="前置知识">
          <el-input v-model="form.prerequisites" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="时间与地点">
          <el-input v-model="form.timePlace" />
        </el-form-item>
        <el-form-item label="背景">
          <el-input v-model="form.background" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="关键人物">
          <el-input v-model="form.keyPeople" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="事件过程">
          <el-input v-model="form.process" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="原因分析">
          <el-input v-model="form.causeAnalysis" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="结果影响">
          <el-input v-model="form.impact" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="来源观点">
          <el-input v-model="form.sourcesAndViews" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="练习提示">
          <el-input v-model="form.practiceHint" />
        </el-form-item>
        <el-form-item label="延伸学习">
          <el-input v-model="form.furtherReading" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="open = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="generateOpen" title="从已发布事件生成单元草稿" width="480px" append-to-body>
      <el-select v-model="generateEventId" filterable placeholder="选择事件" style="width: 100%">
        <el-option v-for="e in eventOptions" :key="e.id" :label="e.title" :value="e.id" />
      </el-select>
      <template #footer>
        <el-button @click="generateOpen = false">取消</el-button>
        <el-button type="primary" @click="submitGenerate">生成草稿</el-button>
      </template>
    </el-dialog>
  </div>
</template>
