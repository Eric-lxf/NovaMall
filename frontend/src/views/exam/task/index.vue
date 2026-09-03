<script setup>
import { computed, onActivated, onBeforeUnmount, onDeactivated, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { cancelExamTask, createExamCheck, getExamCapabilities, getExamTask, listExamTasks, retryExamTask } from '@/api/exam/task'
import { canCancelTask, canRetryTask, newRequestKey, pollDelay, taskLabels, errorLabel, callStatusLabel, finishReasonLabel } from './task-state'
import { examGet } from '@/api/exam/workflow'
import RetryTaskDialog from './RetryTaskDialog.vue'

defineOptions({ name: 'ExamTask' })
const capabilities = ref({ enabled: false, taskReady: false })
const loading = ref(false)
const submitting = ref(false)
const initialized = ref(false)
const loadError = ref(false)
const failures = ref(0)
const tasks = ref([])
const total = ref(0)
const detail = ref(null)
const jobDetail = ref(null)
const detailOpen = ref(false)
const pendingKey = ref(null)
const retryTarget = ref(null)
const retryOpen = ref(false)
const query = reactive({ pageNum: 1, pageSize: 10, status: undefined })
let active = false
let timer = null
let sequence = 0
const ready = computed(() => capabilities.value.enabled && capabilities.value.taskReady)

function stopPolling() { clearTimeout(timer); timer = null }

function schedulePoll() {
  stopPolling()
  const delay = pollDelay({ active, visible: !document.hidden, ready: ready.value, failures: failures.value, tasks: tasks.value })
  if (delay !== null) timer = setTimeout(loadList, delay)
}

async function loadList() {
  stopPolling()
  if (!active || !ready.value) return
  const current = ++sequence
  loading.value = true
  try {
    const result = await listExamTasks(query)
    if (!active || current !== sequence) return
    tasks.value = result.rows || []
    total.value = result.total || 0
    failures.value = 0
    loadError.value = false
  } catch {
    if (active && current === sequence) { failures.value++; loadError.value = true }
  } finally {
    if (active && current === sequence) { loading.value = false; schedulePoll() }
  }
}

async function refresh() {
  stopPolling()
  const current = ++sequence
  loading.value = true
  try {
    const result = await getExamCapabilities()
    if (!active || current !== sequence) return
    capabilities.value = result.data
    initialized.value = true
    loadError.value = false
    failures.value = 0
    if (ready.value) await loadList()
    else { tasks.value = []; total.value = 0; loading.value = false }
  } catch {
    if (active && current === sequence) { loadError.value = true; loading.value = false }
  }
}

async function createCheck() {
  if (submitting.value) return
  submitting.value = true
  // Retain the key after an uncertain response; a second click retrieves the original task.
  pendingKey.value ||= newRequestKey()
  try {
    const result = await createExamCheck(pendingKey.value)
    pendingKey.value = null
    ElMessage.success(`基础自检任务 ${result.data.id} 已提交，不会调用 AI`)
    query.pageNum = 1
    query.status = undefined
    await loadList()
  } catch { /* Request layer displays errors; preserve the idempotency key. */ }
  finally { submitting.value = false }
}

async function showDetail(row) {
  try {
    detail.value = (await getExamTask(row.id)).data
    jobDetail.value = row.kind === 'SYSTEM_CHECK' ? null : (await examGet(`jobs/${row.id}`)).data
    detailOpen.value = true
  } catch { /* Already displayed. */ }
}

async function changeTask(row, action) {
  if (action === 'retry' && row.kind !== 'SYSTEM_CHECK') { retryTarget.value = { ...row }; retryOpen.value = true; return }
  try {
    await ElMessageBox.confirm(action === 'cancel' ? '确认取消该任务？' : '确认重试该基础自检？总运行次数最多三次。', '任务操作')
    await (action === 'cancel' ? cancelExamTask(row.id, row.revision) : retryExamTask(row.id, row.revision))
    await loadList()
  } catch { /* Cancellation and stale-version errors do not trigger automatic retries. */ }
}
async function retried() {
  retryOpen.value = false; detailOpen.value = false
  query.pageNum = 1; query.status = undefined
  await loadList()
}

function activate() {
  if (active) return
  active = true
  refresh()
}
function deactivate() { active = false; sequence++; stopPolling(); loading.value = false }
function visibilityChanged() {
  if (document.hidden) stopPolling()
  else if (active) refresh()
}
onMounted(() => { document.addEventListener('visibilitychange', visibilityChanged); activate() })
onActivated(activate)
onDeactivated(deactivate)
onBeforeUnmount(() => { deactivate(); document.removeEventListener('visibilitychange', visibilityChanged) })
</script>

<template>
  <div class="app-container exam-task-page">
    <el-alert title="智能命题 · 持久任务中心" type="info" :closable="false" show-icon>
      资料解析、知识点抽取、出题、独立复核和导出均在此追踪。取消后停止写入结果；已发生的模型调用可能仍计费。不确定结果不会自动重试。
    </el-alert>
    <el-alert v-if="loadError" class="status-notice" title="读取失败；连续失败三次后暂停自动刷新，请手动重试。" type="warning" :closable="false" />
    <el-alert v-else-if="initialized && !capabilities.enabled" class="status-notice" title="智能命题默认关闭。完成测试数据库迁移后，由管理员启用 EXAM_ENABLED。" type="warning" :closable="false" />
    <el-alert v-else-if="initialized && !capabilities.taskReady" class="status-notice" title="模块已启用，但任务表或数据库连接尚未就绪；不会开始执行任务。" type="warning" :closable="false" />
    <el-alert v-else-if="ready && !capabilities.workerEnabled" class="status-notice" title="Worker 已暂停：可以查询与排队，任务不会自动执行。" type="warning" :closable="false" />

    <el-card shadow="never" class="task-card">
      <div class="task-heading">
        <div><h2>我的任务</h2><p>记录保存在数据库中。离开页面不会丢失已提交任务。</p></div>
        <div class="task-actions">
          <el-button :loading="loading" @click="refresh">刷新</el-button>
          <el-button v-hasPermi="['exam:task:create']" type="primary" :disabled="!ready" :loading="submitting" @click="createCheck">
            {{ pendingKey ? '重试上次提交' : '运行基础自检' }}
          </el-button>
        </div>
      </div>
      <el-form :inline="true">
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部状态" style="width: 170px" :disabled="!ready" @change="() => { query.pageNum = 1; loadList() }">
            <el-option v-for="(label, value) in taskLabels" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
      </el-form>
      <el-table v-loading="loading" :data="tasks" empty-text="暂无任务；启用后可运行基础自检" row-key="id">
        <el-table-column prop="id" label="任务 ID" min-width="110" show-overflow-tooltip />
        <el-table-column prop="title" label="任务名称" min-width="180" show-overflow-tooltip />
        <el-table-column label="状态" width="130"><template #default="{ row }"><el-tag :type="row.status === 'FAILED' ? 'danger' : row.status === 'SUCCEEDED' ? 'success' : 'info'">{{ taskLabels[row.status] || row.status }}</el-tag></template></el-table-column>
        <el-table-column prop="attemptNo" label="运行次数" width="100" align="center" />
        <el-table-column prop="errorCode" label="错误码" min-width="170" show-overflow-tooltip />
        <el-table-column prop="updatedAt" label="更新时间" width="180" />
        <el-table-column label="操作" min-width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="showDetail(row)">详情</el-button>
            <el-button v-if="canCancelTask(row)" v-hasPermi="['exam:task:cancel']" link type="warning" @click="changeTask(row, 'cancel')">取消</el-button>
            <el-button v-if="canRetryTask(row)" v-hasPermi="['exam:task:retry']" link type="primary" @click="changeTask(row, 'retry')">重试</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination v-show="total > 0" :total="total" v-model:page="query.pageNum" v-model:limit="query.pageSize" @pagination="loadList" />
      <p class="poll-hint">有进行中任务时每 3 秒刷新；页面隐藏、任务结束或连续失败后暂停。</p>
    </el-card>

    <el-dialog v-model="detailOpen" title="任务详情（查询时快照）" width="min(900px, 94vw)" append-to-body>
      <el-descriptions v-if="detail" :column="1" border>
        <el-descriptions-item label="任务 ID">{{ detail.id }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ taskLabels[detail.status] || detail.status }}</el-descriptions-item>
        <el-descriptions-item label="运行次数">{{ detail.attemptNo }}</el-descriptions-item>
        <el-descriptions-item label="结果">{{ detail.resultSummary || '尚无结果' }}</el-descriptions-item>
        <el-descriptions-item label="失败说明">{{ errorLabel(detail.errorCode) }}<span v-if="detail.errorCode">（{{ detail.errorCode }}）</span></el-descriptions-item>
      </el-descriptions>
      <template v-if="jobDetail">
        <p>调用预留 {{ jobDetail.callsReserved }} 次 / token 预留 {{ jobDetail.tokensReserved }}。供应商未返回 usage 时不显示为零用量。</p>
        <p v-if="jobDetail.result.sourceId">已导入资料 ID：{{ jobDetail.result.sourceId }}，请到资料库核对。</p>
        <p v-if="jobDetail.result.knowledgeIds?.length">已创建知识点 ID：{{ jobDetail.result.knowledgeIds.join('、') }}，请到知识点页确认。</p>
        <p v-if="jobDetail.retryOfTaskId">重试来源：任务 {{ jobDetail.retryOfTaskId }}，原任务审计与结果保持不变。</p>
        <p v-if="jobDetail.progress.retryTaskId">已创建后继重试任务：<el-button link type="primary" @click="showDetail({ id: jobDetail.progress.retryTaskId, kind: detail.kind })">{{ jobDetail.progress.retryTaskId }}</el-button></p>
        <el-table :data="jobDetail.items"><el-table-column prop="slotId" label="子任务 / 槽位" /><el-table-column label="状态"><template #default="{ row }">{{ taskLabels[row.status] || row.status }}</template></el-table-column><el-table-column prop="questionVersionId" label="结果题目版本" /><el-table-column label="失败说明" min-width="240"><template #default="{ row }">{{ errorLabel(row.errorCode) }}<br><small v-if="row.errorCode">{{ row.errorCode }}</small></template></el-table-column></el-table>
        <p>手动重试只处理未完成项；成功结果保留。AI 重试需要重新确认模型、资料授权和预算。</p>
        <el-table :data="jobDetail.calls"><el-table-column prop="callNo" label="调用" width="65" /><el-table-column prop="model" label="模型" min-width="140" /><el-table-column label="状态"><template #default="{ row }">{{ callStatusLabel(row.status) }}</template></el-table-column><el-table-column label="结束原因"><template #default="{ row }">{{ finishReasonLabel(row.finishReason) }}</template></el-table-column><el-table-column label="供应商 usage" min-width="170"><template #default="{ row }">{{ Object.keys(row.usage).length ? JSON.stringify(row.usage) : '未返回 / 尚未知' }}</template></el-table-column><el-table-column prop="requestId" label="请求 ID" min-width="180" show-overflow-tooltip /></el-table>
      </template>
      <template #footer><el-button @click="detailOpen = false">关闭</el-button><el-button v-if="detail && canRetryTask(detail) && !jobDetail?.progress?.retryTaskId" v-hasPermi="['exam:task:retry']" type="primary" @click="changeTask(detail, 'retry')">手动重试未完成项</el-button></template>
    </el-dialog>
    <el-dialog v-model="retryOpen" title="手动重试任务" width="min(850px, 94vw)" append-to-body destroy-on-close :close-on-click-modal="false">
      <RetryTaskDialog v-if="retryOpen && retryTarget" :key="retryTarget.id" :task="retryTarget" @submitted="retried" />
    </el-dialog>
  </div>
</template>

<style scoped>
.status-notice, .task-card { margin-top: 18px; }
.task-heading { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; margin-bottom: 20px; }
.task-heading h2 { margin: 0 0 8px; font-size: 20px; }
.task-heading p, .poll-hint { color: var(--el-text-color-secondary); font-size: 13px; line-height: 1.6; }
.task-heading p { margin: 0; }
.task-actions { display: flex; flex-shrink: 0; }
.poll-hint { margin-top: 22px; }
@media (max-width: 700px) { .task-heading { flex-direction: column; } }
</style>
