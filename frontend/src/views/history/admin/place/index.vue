<script setup>
defineOptions({ name: 'HistoryAdminPlace' })

import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  addHistoryPlace,
  delHistoryPlace,
  getHistoryPlace,
  listHistoryPlace,
  updateHistoryPlace
} from '@/api/history/place'

const loading = ref(false)
const dataList = ref([])
const total = ref(0)
const open = ref(false)
const title = ref('')
const formRef = ref()
const queryParams = reactive({ pageNum: 1, pageSize: 10, name: '', auditStatus: undefined })
const form = reactive({
  id: undefined,
  name: '',
  alias: '',
  modernName: '',
  region: '',
  summary: '',
  auditStatus: 'DRAFT',
  status: '0'
})
const rules = { name: [{ required: true, message: '地点名称不能为空', trigger: 'blur' }] }

async function getList() {
  loading.value = true
  try {
    const res = await listHistoryPlace(queryParams)
    dataList.value = res.rows || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

function resetForm() {
  Object.assign(form, {
    id: undefined, name: '', alias: '', modernName: '', region: '', summary: '', auditStatus: 'DRAFT', status: '0'
  })
  formRef.value?.clearValidate()
}

function handleAdd() {
  resetForm()
  title.value = '新增地点'
  open.value = true
}

async function handleUpdate(row) {
  resetForm()
  const res = await getHistoryPlace(row.id)
  Object.assign(form, res.data || row)
  title.value = '修改地点'
  open.value = true
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确认删除地点「${row.name}」吗？`, '提示', { type: 'warning' })
  await delHistoryPlace(row.id)
  ElMessage.success('删除成功')
  getList()
}

async function submitForm() {
  await formRef.value.validate()
  if (form.id) await updateHistoryPlace(form)
  else await addHistoryPlace(form)
  ElMessage.success('保存成功')
  open.value = false
  getList()
}

onMounted(getList)
</script>

<template>
  <div class="app-container">
    <el-card shadow="never">
      <el-form :inline="true" :model="queryParams">
        <el-form-item label="名称">
          <el-input v-model="queryParams.name" clearable @keyup.enter="() => { queryParams.pageNum = 1; getList() }" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="() => { queryParams.pageNum = 1; getList() }">搜索</el-button>
        </el-form-item>
      </el-form>
      <el-row class="mb8">
        <el-button type="primary" plain icon="Plus" v-hasPermi="['history:place:add']" @click="handleAdd">新增</el-button>
      </el-row>
      <el-table v-loading="loading" :data="dataList" stripe>
        <el-table-column prop="name" label="名称" width="140" />
        <el-table-column prop="modernName" label="今地名" width="120" />
        <el-table-column prop="region" label="区域" width="120" />
        <el-table-column prop="auditStatus" label="审核" width="120" />
        <el-table-column prop="summary" label="简介" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="150" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" v-hasPermi="['history:place:edit']" @click="handleUpdate(row)">修改</el-button>
            <el-button link type="danger" v-hasPermi="['history:place:remove']" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
    </el-card>

    <el-dialog v-model="open" :title="title" width="560px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="别名"><el-input v-model="form.alias" /></el-form-item>
        <el-form-item label="今地名"><el-input v-model="form.modernName" /></el-form-item>
        <el-form-item label="区域"><el-input v-model="form.region" /></el-form-item>
        <el-form-item label="简介"><el-input v-model="form.summary" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="审核">
          <el-select v-model="form.auditStatus" style="width: 100%">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已发布" value="PUBLISHED" />
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
