<script setup>
defineOptions({ name: 'BlogHnList' })

import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchHnItemPage, fetchHnSyncStatus, syncHnAll, syncHnBoard } from '@/api/blog/hn'

const BOARDS = ['news', 'past', 'show', 'jobs']

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const activeBoard = ref('news')
const syncStatus = ref({})
const pollTimer = ref(undefined)
const syncing = ref(false)

const query = ref({
  pageNum: 1,
  pageSize: 10,
})

const translateStatusMap = {
  pending: { label: '待翻译', type: 'info' },
  ok: { label: '已完成', type: 'success' },
  fail: { label: '失败', type: 'danger' },
}

const publishStatusMap = {
  0: { label: '未发布', type: 'info' },
  1: { label: '已发布', type: 'success' },
}

const currentBoardRunning = computed(() => syncStatus.value[activeBoard.value]?.running === true)
const anyBoardRunning = computed(() => BOARDS.some((board) => syncStatus.value[board]?.running === true))

function anyRunning(status) {
  return BOARDS.some((board) => status?.[board]?.running === true)
}

async function loadData() {
  loading.value = true
  try {
    const res = await fetchHnItemPage({
      ...query.value,
      board: activeBoard.value,
    })
    tableData.value = res.rows ?? res.data?.records ?? []
    total.value = res.total ?? res.data?.total ?? 0
  } finally {
    loading.value = false
  }
}

function stopStatusPoll() {
  if (pollTimer.value) {
    clearInterval(pollTimer.value)
    pollTimer.value = undefined
  }
  syncing.value = false
}

async function pollSyncStatus() {
  try {
    const res = await fetchHnSyncStatus()
    syncStatus.value = res.data ?? {}
    if (anyRunning(syncStatus.value)) {
      syncing.value = true
      return
    }
    stopStatusPoll()
    await loadData()
  } catch {
    stopStatusPoll()
  }
}

function startStatusPoll() {
  stopStatusPoll()
  syncing.value = true
  pollSyncStatus()
  pollTimer.value = window.setInterval(pollSyncStatus, 3000)
}

async function handleSyncBoard() {
  try {
    const res = await syncHnBoard(activeBoard.value, {})
    if (res.data?.started) {
      ElMessage.success('已开始同步')
      startStatusPoll()
    } else {
      ElMessage.warning('该榜单正在同步中')
      startStatusPoll()
    }
  } catch {
    // handled by request interceptor
  }
}

async function handleSyncAll() {
  try {
    const res = await syncHnAll({})
    if (res.data?.started) {
      ElMessage.success('已开始同步全部榜单')
      startStatusPoll()
    } else {
      ElMessage.warning('榜单正在同步中')
      startStatusPoll()
    }
  } catch {
    // handled by request interceptor
  }
}

function handleTabChange() {
  query.value.pageNum = 1
  loadData()
}

watch(activeBoard, handleTabChange)

onMounted(async () => {
  await loadData()
  try {
    const res = await fetchHnSyncStatus()
    syncStatus.value = res.data ?? {}
    if (anyRunning(syncStatus.value)) {
      startStatusPoll()
    }
  } catch {
    // ignore initial status failure
  }
})

onUnmounted(stopStatusPoll)
</script>

<template>
  <el-card shadow="never">
    <template #header>
      <div class="card-header">
        <span>HN 内容</span>
        <div class="header-actions">
          <el-button
            v-hasPermi="['blog:hn:sync']"
            type="primary"
            :loading="currentBoardRunning || syncing"
            @click="handleSyncBoard"
          >
            一键拉取
          </el-button>
          <el-button
            v-hasPermi="['blog:hn:sync']"
            :loading="anyBoardRunning"
            @click="handleSyncAll"
          >
            同步全部
          </el-button>
        </div>
      </div>
    </template>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="notice"
      title="说明"
      description="Past 基于 HN beststories（精选/热门），非站点日历 Past；Show/Jobs 数量以接口为准（约 200）。"
    />

    <el-tabs v-model="activeBoard">
      <el-tab-pane label="News" name="news" />
      <el-tab-pane label="Past" name="past" />
      <el-tab-pane label="Show" name="show" />
      <el-tab-pane label="Jobs" name="jobs" />
    </el-tabs>

    <el-table v-loading="loading || currentBoardRunning" :data="tableData" stripe>
      <el-table-column prop="rank" label="排名" width="80" align="center">
        <template #default="{ row }">
          {{ row.rank ?? '—' }}
        </template>
      </el-table-column>
      <el-table-column prop="hnId" label="HN ID" width="100" align="center" />
      <el-table-column prop="titleZh" label="中文标题" min-width="200" class-name="col-title" show-overflow-tooltip />
      <el-table-column prop="titleEn" label="英文标题" min-width="200" show-overflow-tooltip />
      <el-table-column prop="score" label="分数" width="80" align="center" />
      <el-table-column label="翻译状态" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="translateStatusMap[row.translateStatus]?.type || 'info'" size="small">
            {{ translateStatusMap[row.translateStatus]?.label || row.translateStatus || '未知' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="发布状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="publishStatusMap[row.status]?.type || 'info'" size="small">
            {{ publishStatusMap[row.status]?.label || '未知' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="hnTime" label="HN 时间" width="180" />
    </el-table>

    <div class="pagination">
      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="loadData"
        @size-change="loadData"
      />
    </div>
  </el-card>
</template>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.notice {
  margin-bottom: 16px;
}

.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

:deep(.col-title .cell) {
  color: #0f172a;
  font-weight: 600;
}
</style>
