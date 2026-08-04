<script setup>
defineOptions({ name: 'HistoryAdminPath' })

import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  addHistoryPath,
  delHistoryPath,
  getHistoryPath,
  listHistoryPath,
  publishHistoryPath,
  updateHistoryPath
} from '@/api/history/path'
import { listHistoryUnitOptions } from '@/api/history/unit'
import { listHistoryPeriodOptions } from '@/api/history/period'

const loading = ref(false)
const dataList = ref([])
const total = ref(0)
const open = ref(false)
const title = ref('')
const formRef = ref()
const periodOptions = ref([])
const unitOptions = ref([])
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
  summary: '',
  periodId: undefined,
  difficulty: 'BEGINNER',
  estimatedDays: 7,
  unitIds: [],
  auditStatus: 'DRAFT',
  status: '0',
  remark: ''
})
const rules = {
  title: [{ required: true, message: '路径标题不能为空', trigger: 'blur' }]
}

async function loadOptions() {
  const [periods, units] = await Promise.all([listHistoryPeriodOptions(), listHistoryUnitOptions()])
  periodOptions.value = periods.data || []
  unitOptions.value = units.data || []
}

async function getList() {
  loading.value = true
  try {
    const res = await listHistoryPath(queryParams)
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
    summary: '',
    periodId: undefined,
    difficulty: 'BEGINNER',
    estimatedDays: 7,
    unitIds: [],
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
  title.value = '新增学习路径'
  open.value = true
}

async function handleUpdate(row) {
  resetForm()
  const res = await getHistoryPath(row.id)
  const data = res.data || {}
  Object.assign(form, {
    id: data.id,
    title: data.title,
    summary: data.summary,
    periodId: data.periodId,
    difficulty: data.difficulty || 'BEGINNER',
    estimatedDays: data.estimatedDays,
    unitIds: data.unitIds || [],
    auditStatus: data.auditStatus,
    status: data.status,
    remark: data.remark
  })
  title.value = '修改学习路径'
  open.value = true
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确认删除路径「${row.title}」吗？`, '提示', { type: 'warning' })
  await delHistoryPath(row.id)
  ElMessage.success('删除成功')
  getList()
}

async function handlePublish(row) {
  await ElMessageBox.confirm(`确认发布路径「${row.title}」吗？路径内单元须已全部发布。`, '提示', { type: 'warning' })
  await publishHistoryPath(row.id)
  ElMessage.success('已发布')
  getList()
}

async function submitForm() {
  await formRef.value.validate()
  if (form.id) {
    await updateHistoryPath(form)
  } else {
    await addHistoryPath(form)
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
          <el-select v-model="queryParams.auditStatus" clearable style="width: 140px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已发布" value="PUBLISHED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        </el-form-item>
      </el-form>

      <el-row class="mb8">
        <el-button type="primary" plain icon="Plus" v-hasPermi="['history:path:add']" @click="handleAdd">新增</el-button>
      </el-row>

      <el-table v-loading="loading" :data="dataList" stripe>
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
        <el-table-column prop="difficulty" label="难度" width="110" />
        <el-table-column prop="estimatedDays" label="预计天数" width="100" align="center" />
        <el-table-column prop="auditStatus" label="审核" width="110" />
        <el-table-column prop="summary" label="简介" min-width="200" show-overflow-tooltip />
        <el-table-column label="操作" width="220" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" v-hasPermi="['history:path:edit']" @click="handleUpdate(row)">修改</el-button>
            <el-button
              link
              type="success"
              v-if="row.auditStatus !== 'PUBLISHED'"
              v-hasPermi="['history:path:publish']"
              @click="handlePublish(row)"
            >发布</el-button>
            <el-button link type="danger" v-hasPermi="['history:path:remove']" @click="handleDelete(row)">删除</el-button>
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

    <el-dialog v-model="open" :title="title" width="680px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" maxlength="200" />
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="form.summary" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="时期">
          <el-select v-model="form.periodId" clearable style="width: 100%">
            <el-option v-for="p in periodOptions" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="难度">
          <el-select v-model="form.difficulty" style="width: 100%">
            <el-option label="入门" value="BEGINNER" />
            <el-option label="进阶" value="INTERMEDIATE" />
            <el-option label="挑战" value="ADVANCED" />
          </el-select>
        </el-form-item>
        <el-form-item label="预计天数">
          <el-input-number v-model="form.estimatedDays" :min="1" :max="90" controls-position="right" />
        </el-form-item>
        <el-form-item label="学习单元">
          <el-select v-model="form.unitIds" multiple filterable style="width: 100%" placeholder="按选择顺序学习（仅已发布单元）">
            <el-option v-for="u in unitOptions" :key="u.id" :label="u.title" :value="u.id" />
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
