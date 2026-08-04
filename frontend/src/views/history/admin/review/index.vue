<script setup>
defineOptions({ name: 'HistoryAdminReview' })

import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { auditHistoryClaims, listHistoryClaims } from '@/api/history/public'

const loading = ref(false)
const dataList = ref([])
const total = ref(0)
const selectedIds = ref([])
const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  documentId: undefined,
  taskId: undefined,
  claimType: undefined,
  auditStatus: 'PENDING_REVIEW'
})

async function getList() {
  loading.value = true
  try {
    const params = {
      ...queryParams,
      documentId: queryParams.documentId ? Number(queryParams.documentId) : undefined,
      taskId: queryParams.taskId ? Number(queryParams.taskId) : undefined
    }
    const res = await listHistoryClaims(params)
    dataList.value = res.rows || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

function onSelectionChange(rows) {
  selectedIds.value = rows.map(r => r.id)
}

async function audit(action, ids) {
  const claimIds = ids?.length ? ids : selectedIds.value
  if (!claimIds.length) {
    ElMessage.warning('请先选择主张')
    return
  }
  const label = action === 'APPROVE' ? '通过并发布关联知识' : '驳回'
  await ElMessageBox.confirm(`确认${label}选中的 ${claimIds.length} 条主张？`, '审核确认', { type: 'warning' })
  await auditHistoryClaims({ claimIds, action })
  ElMessage.success('已提交审核')
  getList()
}

onMounted(getList)
</script>

<template>
  <div class="app-container">
    <el-card shadow="never">
      <el-alert
        title="AI 抽取结果默认待审核。通过后会将关联事件/人物/地点/关系发布到学习端；驳回不会自动删除草稿实体。"
        type="info"
        :closable="false"
        class="mb8"
      />
      <el-form :inline="true" :model="queryParams">
        <el-form-item label="资料ID">
          <el-input v-model="queryParams.documentId" clearable style="width: 120px" />
        </el-form-item>
        <el-form-item label="任务ID">
          <el-input v-model="queryParams.taskId" clearable style="width: 120px" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="queryParams.claimType" clearable style="width: 140px">
            <el-option label="事件" value="EVENT" />
            <el-option label="人物" value="PERSON" />
            <el-option label="地点" value="PLACE" />
            <el-option label="关系" value="RELATION" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.auditStatus" clearable style="width: 160px">
            <el-option label="待审" value="PENDING_REVIEW" />
            <el-option label="已发布" value="PUBLISHED" />
            <el-option label="驳回" value="REJECTED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="() => { queryParams.pageNum = 1; getList() }">搜索</el-button>
        </el-form-item>
      </el-form>

      <el-row class="mb8" :gutter="10">
        <el-col :span="1.5">
          <el-button type="success" plain v-hasPermi="['history:claim:audit']" @click="audit('APPROVE')">批量通过</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button type="danger" plain v-hasPermi="['history:claim:audit']" @click="audit('REJECT')">批量驳回</el-button>
        </el-col>
      </el-row>

      <el-table v-loading="loading" :data="dataList" stripe @selection-change="onSelectionChange">
        <el-table-column type="selection" width="50" />
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="claimType" label="类型" width="90" />
        <el-table-column prop="claimText" label="主张" min-width="180" show-overflow-tooltip />
        <el-table-column prop="documentTitle" label="资料" min-width="140" show-overflow-tooltip />
        <el-table-column label="来源片段" min-width="220">
          <template #default="{ row }">
            <div class="frag">
              <span>#{{ row.fragmentSeqNo }} {{ row.fragmentLocator }}</span>
              <div class="preview">{{ row.fragmentPreview }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="uncertaintyNote" label="不确定性" min-width="140" show-overflow-tooltip />
        <el-table-column prop="auditStatus" label="状态" width="120" />
        <el-table-column prop="taskId" label="任务" width="80" />
        <el-table-column label="操作" width="160" fixed="right" align="center">
          <template #default="{ row }">
            <el-button
              link
              type="success"
              v-if="row.auditStatus === 'PENDING_REVIEW'"
              v-hasPermi="['history:claim:audit']"
              @click="audit('APPROVE', [row.id])"
            >通过</el-button>
            <el-button
              link
              type="danger"
              v-if="row.auditStatus === 'PENDING_REVIEW'"
              v-hasPermi="['history:claim:audit']"
              @click="audit('REJECT', [row.id])"
            >驳回</el-button>
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
  </div>
</template>

<style scoped>
.frag .preview {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
}
</style>
