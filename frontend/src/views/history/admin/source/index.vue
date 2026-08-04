<script setup>
defineOptions({ name: 'HistoryAdminSource' })

import { onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  extractHistorySource,
  getHistorySource,
  getHistoryTask,
  importHistorySource,
  listHistorySource,
  uploadHistoryFile
} from '@/api/history/public'

const loading = ref(false)
const dataList = ref([])
const total = ref(0)
const open = ref(false)
const detailOpen = ref(false)
const detailLoading = ref(false)
const detail = ref(null)
const formRef = ref()
const uploadLoading = ref(false)
const pollTimer = ref(null)
const queryParams = reactive({ pageNum: 1, pageSize: 10, title: '', parseStatus: undefined })
const form = reactive({
  title: '',
  fileType: 'TEXT',
  contentText: '',
  fileUrl: '',
  fileName: '',
  fileSize: undefined,
  sourceDesc: '',
  remark: ''
})
const rules = {
  title: [{ required: true, message: '标题不能为空', trigger: 'blur' }],
  fileType: [{ required: true, message: '类型不能为空', trigger: 'change' }]
}

async function getList() {
  loading.value = true
  try {
    const res = await listHistorySource(queryParams)
    dataList.value = res.rows || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

function handleImport() {
  Object.assign(form, {
    title: '',
    fileType: 'TEXT',
    contentText: '',
    fileUrl: '',
    fileName: '',
    fileSize: undefined,
    sourceDesc: '',
    remark: ''
  })
  open.value = true
}

async function onFileChange(uploadFile) {
  const raw = uploadFile?.raw
  if (!raw) return
  uploadLoading.value = true
  try {
    const res = await uploadHistoryFile(raw)
    form.fileUrl = res.fileName || res.data?.fileName
    form.fileName = res.originalFilename || raw.name
    form.fileSize = raw.size
    if (!form.title) {
      form.title = form.fileName.replace(/\.[^.]+$/, '')
    }
    if (/\.pdf$/i.test(form.fileName)) {
      form.fileType = 'PDF'
    }
    ElMessage.success('文件已上传，提交后将异步解析')
  } finally {
    uploadLoading.value = false
  }
}

async function pollTask(taskId) {
  stopPoll()
  let tries = 0
  pollTimer.value = setInterval(async () => {
    tries += 1
    try {
      const res = await getHistoryTask(taskId)
      const status = res.data?.status
      if (status === 'SUCCEEDED' || status === 'PENDING_REVIEW') {
        ElMessage.success(status === 'PENDING_REVIEW' ? '抽取完成，请到知识审核处理草稿' : '解析完成')
        stopPoll()
        getList()
      } else if (status === 'FAILED') {
        ElMessage.error(res.data?.errorMessage || '解析失败')
        stopPoll()
        getList()
      } else if (tries >= 40) {
        stopPoll()
        getList()
      }
    } catch {
      stopPoll()
    }
  }, 1500)
}

function stopPoll() {
  if (pollTimer.value) {
    clearInterval(pollTimer.value)
    pollTimer.value = null
  }
}

async function submitForm() {
  await formRef.value.validate()
  if (form.fileType === 'PDF' && !form.fileUrl && !form.contentText) {
    ElMessage.warning('PDF 请先上传文件，或粘贴备用正文')
    return
  }
  if (form.fileType !== 'PDF' && !form.contentText && !form.fileUrl) {
    ElMessage.warning('请填写正文或上传文本文件')
    return
  }
  const res = await importHistorySource(form)
  ElMessage.success(`已排队解析 taskId=${res.data?.taskId}`)
  open.value = false
  getList()
  if (res.data?.taskId) {
    pollTask(res.data.taskId)
  }
}

async function openDetail(row) {
  detailOpen.value = true
  detailLoading.value = true
  try {
    const res = await getHistorySource(row.id, true)
    detail.value = res.data
  } finally {
    detailLoading.value = false
  }
}

async function handleExtract(row) {
  if (row.parseStatus !== 'SUCCEEDED') {
    ElMessage.warning('请先等待资料解析成功')
    return
  }
  const res = await extractHistorySource(row.id)
  ElMessage.success(`已排队 AI 抽取 taskId=${res.data?.taskId}`)
  if (res.data?.taskId) {
    pollTask(res.data.taskId)
  }
}

onMounted(getList)
onUnmounted(stopPoll)
</script>

<template>
  <div class="app-container">
    <el-card shadow="never">
      <el-alert
        title="导入只创建异步解析任务。TEXT/Markdown 即时切片；PDF 按页抽文本后切片，可在详情中回溯原文片段。"
        type="info"
        :closable="false"
        class="mb8"
      />
      <el-form :inline="true" :model="queryParams">
        <el-form-item label="标题">
          <el-input v-model="queryParams.title" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.parseStatus" clearable style="width: 150px">
            <el-option label="QUEUED" value="QUEUED" />
            <el-option label="PROCESSING" value="PROCESSING" />
            <el-option label="SUCCEEDED" value="SUCCEEDED" />
            <el-option label="FAILED" value="FAILED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="() => { queryParams.pageNum = 1; getList() }">搜索</el-button>
        </el-form-item>
      </el-form>
      <el-row class="mb8">
        <el-button type="primary" plain icon="Upload" v-hasPermi="['history:source:import']" @click="handleImport">导入资料</el-button>
      </el-row>
      <el-table v-loading="loading" :data="dataList" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
        <el-table-column prop="fileType" label="类型" width="100" />
        <el-table-column prop="parseStatus" label="解析状态" width="120" />
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="180" align="center">
          <template #default="{ row }">
            <el-button link type="primary" v-hasPermi="['history:source:query']" @click="openDetail(row)">原文</el-button>
            <el-button
              link
              type="warning"
              v-hasPermi="['history:source:extract']"
              :disabled="row.parseStatus !== 'SUCCEEDED'"
              @click="handleExtract(row)"
            >AI抽取</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
    </el-card>

    <el-dialog v-model="open" title="导入资料" width="680px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="标题" prop="title"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="类型" prop="fileType">
          <el-select v-model="form.fileType" style="width: 100%">
            <el-option label="纯文本" value="TEXT" />
            <el-option label="Markdown" value="MARKDOWN" />
            <el-option label="PDF" value="PDF" />
          </el-select>
        </el-form-item>
        <el-form-item label="上传文件">
          <el-upload :auto-upload="false" :show-file-list="false" :on-change="onFileChange" accept=".pdf,.txt,.md,.markdown">
            <el-button :loading="uploadLoading">选择文件</el-button>
          </el-upload>
          <div v-if="form.fileUrl" class="file-tip">已上传：{{ form.fileName || form.fileUrl }}</div>
        </el-form-item>
        <el-form-item label="正文">
          <el-input
            v-model="form.contentText"
            type="textarea"
            :rows="10"
            placeholder="粘贴 TEXT/Markdown；PDF 也可粘贴备用正文（抽字失败时使用）"
          />
        </el-form-item>
        <el-form-item label="来源说明"><el-input v-model="form.sourceDesc" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="open = false">取消</el-button>
        <el-button type="primary" @click="submitForm">提交任务</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailOpen" title="资料原文片段" size="50%">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <p><strong>{{ detail.title }}</strong>（{{ detail.fileType }} / {{ detail.parseStatus }}）</p>
          <p class="muted">片段数：{{ detail.fragmentCount ?? detail.fragments?.length ?? 0 }}</p>
          <el-empty v-if="!detail.fragments?.length" description="暂无片段，请等待解析完成或查看任务错误" />
          <div v-for="frag in detail.fragments || []" :key="frag.id" class="frag">
            <div class="frag-meta">
              #{{ frag.seqNo }}
              <span v-if="frag.pageNo"> · 第 {{ frag.pageNo }} 页</span>
              <span> · {{ frag.locator }}</span>
            </div>
            <pre class="frag-body">{{ frag.content }}</pre>
          </div>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.file-tip,
.muted {
  margin-top: 8px;
  color: #909399;
  font-size: 13px;
}
.frag {
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #ebeef5;
}
.frag-meta {
  font-size: 12px;
  color: #909399;
  margin-bottom: 6px;
}
.frag-body {
  white-space: pre-wrap;
  word-break: break-word;
  margin: 0;
  font-family: inherit;
  line-height: 1.7;
  color: #303133;
}
</style>
