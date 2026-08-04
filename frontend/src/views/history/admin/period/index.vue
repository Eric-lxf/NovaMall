<script setup>
defineOptions({ name: 'HistoryAdminPeriod' })

import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  addHistoryPeriod,
  delHistoryPeriod,
  getHistoryPeriod,
  listHistoryPeriod,
  updateHistoryPeriod
} from '@/api/history/period'

const loading = ref(false)
const dataList = ref([])
const total = ref(0)
const open = ref(false)
const title = ref('')
const formRef = ref()
const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  name: '',
  status: undefined
})
const form = reactive({
  id: undefined,
  name: '',
  alias: '',
  startYear: undefined,
  endYear: undefined,
  datePrecision: 'YEAR',
  originalDateText: '',
  calendarType: '中国传统纪年',
  isApproximate: false,
  summary: '',
  sort: 0,
  status: '0',
  remark: ''
})
const rules = {
  name: [{ required: true, message: '时期名称不能为空', trigger: 'blur' }]
}

async function getList() {
  loading.value = true
  try {
    const res = await listHistoryPeriod(queryParams)
    dataList.value = res.rows || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

function resetForm() {
  Object.assign(form, {
    id: undefined,
    name: '',
    alias: '',
    startYear: undefined,
    endYear: undefined,
    datePrecision: 'YEAR',
    originalDateText: '',
    calendarType: '中国传统纪年',
    isApproximate: false,
    summary: '',
    sort: 0,
    status: '0',
    remark: ''
  })
  formRef.value?.clearValidate()
}

function handleQuery() {
  queryParams.pageNum = 1
  getList()
}

function resetQuery() {
  Object.assign(queryParams, { pageNum: 1, pageSize: 10, name: '', status: undefined })
  getList()
}

function handleAdd() {
  resetForm()
  title.value = '新增时期'
  open.value = true
}

async function handleUpdate(row) {
  resetForm()
  const res = await getHistoryPeriod(row.id)
  Object.assign(form, res.data || row)
  title.value = '修改时期'
  open.value = true
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确认删除时期「${row.name}」吗？`, '提示', { type: 'warning' })
  await delHistoryPeriod(row.id)
  ElMessage.success('删除成功')
  getList()
}

async function submitForm() {
  await formRef.value.validate()
  if (form.id) {
    await updateHistoryPeriod(form)
  } else {
    await addHistoryPeriod(form)
  }
  ElMessage.success('保存成功')
  open.value = false
  getList()
}

onMounted(getList)
</script>

<template>
  <div class="app-container">
    <el-card shadow="never">
      <el-form :inline="true" :model="queryParams" @submit.prevent="handleQuery">
        <el-form-item label="名称">
          <el-input v-model="queryParams.name" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" clearable style="width: 120px">
            <el-option label="正常" value="0" />
            <el-option label="停用" value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-row class="mb8">
        <el-button type="primary" plain icon="Plus" v-hasPermi="['history:period:add']" @click="handleAdd">新增</el-button>
      </el-row>

      <el-table v-loading="loading" :data="dataList" stripe>
        <el-table-column prop="name" label="名称" min-width="120" />
        <el-table-column prop="alias" label="别名" min-width="140" show-overflow-tooltip />
        <el-table-column label="起止年" width="140" align="center">
          <template #default="{ row }">{{ row.startYear }} ~ {{ row.endYear }}</template>
        </el-table-column>
        <el-table-column prop="datePrecision" label="精度" width="110" />
        <el-table-column prop="sort" label="排序" width="80" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === '0' ? 'success' : 'info'">{{ row.status === '0' ? '正常' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" v-hasPermi="['history:period:edit']" @click="handleUpdate(row)">修改</el-button>
            <el-button link type="danger" v-hasPermi="['history:period:remove']" @click="handleDelete(row)">删除</el-button>
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

    <el-dialog v-model="open" :title="title" width="640px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" maxlength="64" />
        </el-form-item>
        <el-form-item label="别名">
          <el-input v-model="form.alias" maxlength="128" />
        </el-form-item>
        <el-form-item label="起始年">
          <el-input-number v-model="form.startYear" controls-position="right" />
        </el-form-item>
        <el-form-item label="结束年">
          <el-input-number v-model="form.endYear" controls-position="right" />
        </el-form-item>
        <el-form-item label="时间精度">
          <el-select v-model="form.datePrecision" style="width: 100%">
            <el-option label="YEAR" value="YEAR" />
            <el-option label="CENTURY" value="CENTURY" />
            <el-option label="APPROXIMATE" value="APPROXIMATE" />
            <el-option label="PERIOD" value="PERIOD" />
          </el-select>
        </el-form-item>
        <el-form-item label="原始纪年">
          <el-input v-model="form.originalDateText" />
        </el-form-item>
        <el-form-item label="历法">
          <el-input v-model="form.calendarType" />
        </el-form-item>
        <el-form-item label="约数">
          <el-switch v-model="form.isApproximate" />
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="form.summary" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" controls-position="right" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio value="0">正常</el-radio>
            <el-radio value="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="open = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>
