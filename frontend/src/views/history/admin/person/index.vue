<script setup>
defineOptions({ name: 'HistoryAdminPerson' })

import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  addHistoryPerson,
  delHistoryPerson,
  getHistoryPerson,
  listHistoryPerson,
  updateHistoryPerson
} from '@/api/history/person'
import { listHistoryPeriodOptions } from '@/api/history/period'

const loading = ref(false)
const dataList = ref([])
const total = ref(0)
const open = ref(false)
const title = ref('')
const formRef = ref()
const periodOptions = ref([])
const queryParams = reactive({ pageNum: 1, pageSize: 10, name: '', periodId: undefined, auditStatus: undefined })
const form = reactive({
  id: undefined,
  name: '',
  alias: '',
  periodId: undefined,
  birthYear: undefined,
  deathYear: undefined,
  datePrecision: 'YEAR',
  originalDateText: '',
  calendarType: '中国传统纪年',
  isApproximate: false,
  summary: '',
  uncertaintyNote: '',
  auditStatus: 'DRAFT',
  status: '0'
})
const rules = { name: [{ required: true, message: '姓名不能为空', trigger: 'blur' }] }

async function getList() {
  loading.value = true
  try {
    const res = await listHistoryPerson(queryParams)
    dataList.value = res.rows || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

function resetForm() {
  Object.assign(form, {
    id: undefined, name: '', alias: '', periodId: undefined, birthYear: undefined, deathYear: undefined,
    datePrecision: 'YEAR', originalDateText: '', calendarType: '中国传统纪年', isApproximate: false,
    summary: '', uncertaintyNote: '', auditStatus: 'DRAFT', status: '0'
  })
  formRef.value?.clearValidate()
}

function handleAdd() {
  resetForm()
  title.value = '新增人物'
  open.value = true
}

async function handleUpdate(row) {
  resetForm()
  const res = await getHistoryPerson(row.id)
  Object.assign(form, res.data || row)
  title.value = '修改人物'
  open.value = true
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确认删除人物「${row.name}」吗？`, '提示', { type: 'warning' })
  await delHistoryPerson(row.id)
  ElMessage.success('删除成功')
  getList()
}

async function submitForm() {
  await formRef.value.validate()
  if (form.id) await updateHistoryPerson(form)
  else await addHistoryPerson(form)
  ElMessage.success('保存成功')
  open.value = false
  getList()
}

onMounted(async () => {
  const res = await listHistoryPeriodOptions()
  periodOptions.value = res.data || []
  await getList()
})
</script>

<template>
  <div class="app-container">
    <el-card shadow="never">
      <el-form :inline="true" :model="queryParams" @submit.prevent="() => { queryParams.pageNum = 1; getList() }">
        <el-form-item label="姓名">
          <el-input v-model="queryParams.name" clearable @keyup.enter="getList" />
        </el-form-item>
        <el-form-item label="时期">
          <el-select v-model="queryParams.periodId" clearable style="width: 160px">
            <el-option v-for="p in periodOptions" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="() => { queryParams.pageNum = 1; getList() }">搜索</el-button>
        </el-form-item>
      </el-form>
      <el-row class="mb8">
        <el-button type="primary" plain icon="Plus" v-hasPermi="['history:person:add']" @click="handleAdd">新增</el-button>
      </el-row>
      <el-table v-loading="loading" :data="dataList" stripe>
        <el-table-column prop="name" label="姓名" width="120" />
        <el-table-column prop="alias" label="别名" min-width="120" show-overflow-tooltip />
        <el-table-column label="生卒" width="140" align="center">
          <template #default="{ row }">{{ row.birthYear }} ~ {{ row.deathYear }}</template>
        </el-table-column>
        <el-table-column prop="auditStatus" label="审核" width="120" />
        <el-table-column prop="summary" label="简介" min-width="200" show-overflow-tooltip />
        <el-table-column label="操作" width="150" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" v-hasPermi="['history:person:edit']" @click="handleUpdate(row)">修改</el-button>
            <el-button link type="danger" v-hasPermi="['history:person:remove']" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
    </el-card>

    <el-dialog v-model="open" :title="title" width="640px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="姓名" prop="name"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="别名"><el-input v-model="form.alias" /></el-form-item>
        <el-form-item label="时期">
          <el-select v-model="form.periodId" clearable style="width: 100%">
            <el-option v-for="p in periodOptions" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="生年"><el-input-number v-model="form.birthYear" controls-position="right" /></el-form-item>
        <el-form-item label="卒年"><el-input-number v-model="form.deathYear" controls-position="right" /></el-form-item>
        <el-form-item label="简介"><el-input v-model="form.summary" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="不确定性"><el-input v-model="form.uncertaintyNote" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="审核">
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
