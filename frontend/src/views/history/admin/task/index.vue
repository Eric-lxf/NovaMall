<script setup>
defineOptions({ name: 'HistoryAdminTask' })

import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getHistoryTask, listHistoryTask, retryHistoryTask } from '@/api/history/public'

const loading = ref(false)
const dataList = ref([])
const total = ref(0)
const detailOpen = ref(false)
const detail = ref(null)
const queryParams = reactive({ pageNum: 1, pageSize: 10, status: undefined, taskType: undefined })

async function getList() {
  loading.value = true
  try {
    const res = await listHistoryTask(queryParams)
    dataList.value = res.rows || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

async function showDetail(row) {
  const res = await getHistoryTask(row.id)
  detail.value = res.data
  detailOpen.value = true
}

async function handleRetry(row) {
  const res = await retryHistoryTask(row.id)
  ElMessage.success(`已重新排队：${res.data?.status}`)
  getList()
}

onMounted(getList)
</script>

<template>
  <div class="app-container">
    <el-card shadow="never">
      <el-form :inline="true" :model="queryParams">
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" clearable style="width: 160px">
            <el-option label="QUEUED" value="QUEUED" />
            <el-option label="PROCESSING" value="PROCESSING" />
            <el-option label="RETRYING" value="RETRYING" />
            <el-option label="SUCCEEDED" value="SUCCEEDED" />
            <el-option label="FAILED" value="FAILED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="() => { queryParams.pageNum = 1; getList() }">搜索</el-button>
        </el-form-item>
      </el-form>
      <el-table v-loading="loading" :data="dataList" stripe>
        <el-table-column prop="id" label="任务ID" width="90" />
        <el-table-column prop="taskType" label="类型" width="140" />
        <el-table-column prop="documentId" label="资料ID" width="90" />
        <el-table-column prop="status" label="状态" width="120" />
        <el-table-column prop="retryCount" label="重试" width="80" align="center" />
        <el-table-column prop="errorMessage" label="错误" min-width="160" show-overflow-tooltip />
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="160" align="center">
          <template #default="{ row }">
            <el-button link type="primary" v-hasPermi="['history:task:query']" @click="showDetail(row)">详情</el-button>
            <el-button
              link
              type="warning"
              v-if="row.status === 'FAILED'"
              v-hasPermi="['history:task:retry']"
              @click="handleRetry(row)"
            >重试</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
    </el-card>

    <el-dialog v-model="detailOpen" title="任务详情" width="720px" append-to-body>
      <pre v-if="detail" class="task-json">{{ JSON.stringify(detail, null, 2) }}</pre>
    </el-dialog>
  </div>
</template>

<style scoped>
.task-json {
  max-height: 480px;
  overflow: auto;
  background: #0f172a;
  color: #e2e8f0;
  padding: 16px;
  border-radius: 8px;
  font-size: 12px;
}
</style>
