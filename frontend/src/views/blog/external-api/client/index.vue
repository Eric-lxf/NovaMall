<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createExternalApiClient,
  listExternalApiClients,
  rotateExternalApiClientSecret,
  updateExternalApiClientStatus
} from '@/api/blog/externalApiClient'

defineOptions({ name: 'BlogExternalApiClient' })

const STATUS_ENABLED = '0'
const STATUS_DISABLED = '1'

const scopeOptions = [
  { label: '创建文章草稿', value: 'blog.article.create' },
  { label: '读取自有文章', value: 'blog.article.read.own' },
  { label: '读取分类与标签', value: 'blog.taxonomy.read' }
]

const loading = ref(false)
const submitLoading = ref(false)
const rotatingId = ref()
const statusChangingId = ref()
const clientList = ref([])
const total = ref(0)
const createOpen = ref(false)
const credentialOpen = ref(false)
const formRef = ref()

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  keyword: undefined,
  status: undefined
})

const form = reactive({
  clientName: '',
  scopes: scopeOptions.map(item => item.value),
  rateLimitPerMinute: 60,
  tokenTtlSeconds: 900,
  status: STATUS_ENABLED
})

const credential = reactive({
  clientId: '',
  clientSecret: ''
})

const rules = {
  clientName: [
    { required: true, message: '请输入客户端名称', trigger: 'blur' },
    { min: 2, max: 100, message: '名称长度应为 2 到 100 个字符', trigger: 'blur' }
  ],
  scopes: [{ type: 'array', required: true, min: 1, message: '请至少选择一个权限范围', trigger: 'change' }],
  rateLimitPerMinute: [{ required: true, message: '请输入每分钟请求上限', trigger: 'change' }],
  tokenTtlSeconds: [{ required: true, message: '请输入令牌有效期', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

const credentialText = computed(() => [
  `Client ID: ${credential.clientId}`,
  `Client Secret: ${credential.clientSecret}`
].join('\n'))

function normalizeStatus(status) {
  return String(status ?? STATUS_DISABLED)
}

function isEnabled(row) {
  return normalizeStatus(row.status) === STATUS_ENABLED
}

function normalizeScopes(scopes) {
  if (Array.isArray(scopes)) return scopes
  if (!scopes) return []
  if (typeof scopes === 'string') {
    const value = scopes.trim()
    if (!value) return []
    if (value.startsWith('[')) {
      try {
        const parsed = JSON.parse(value)
        return Array.isArray(parsed) ? parsed : []
      } catch {
        return []
      }
    }
    return value.split(',').map(item => item.trim()).filter(Boolean)
  }
  return []
}

function scopeLabel(scope) {
  return scopeOptions.find(item => item.value === scope)?.label || scope
}

function formatTokenTtl(seconds) {
  const value = Number(seconds)
  if (!Number.isFinite(value) || value <= 0) return '-'
  if (value % 86400 === 0) return `${value / 86400} 天`
  if (value % 3600 === 0) return `${value / 3600} 小时`
  if (value % 60 === 0) return `${value / 60} 分钟`
  return `${value} 秒`
}

async function getList() {
  loading.value = true
  try {
    const response = await listExternalApiClients(queryParams)
    const payload = response?.data || response || {}
    clientList.value = payload.rows || response?.rows || []
    total.value = Number(payload.total ?? response?.total ?? 0)
  } finally {
    loading.value = false
  }
}

function handleQuery() {
  queryParams.pageNum = 1
  getList()
}

function resetQuery() {
  Object.assign(queryParams, {
    pageNum: 1,
    pageSize: 10,
    keyword: undefined,
    status: undefined
  })
  getList()
}

function resetForm() {
  Object.assign(form, {
    clientName: '',
    scopes: scopeOptions.map(item => item.value),
    rateLimitPerMinute: 60,
    tokenTtlSeconds: 900,
    status: STATUS_ENABLED
  })
  nextTick(() => formRef.value?.clearValidate())
}

function handleAdd() {
  resetForm()
  createOpen.value = true
}

function extractCredential(response, fallbackClientId = '') {
  const data = response?.data || {}
  return {
    clientId: data.clientId || fallbackClientId,
    clientSecret: data.clientSecret || data.secret || ''
  }
}

function showCredential(response, fallbackClientId = '') {
  const value = extractCredential(response, fallbackClientId)
  if (!value.clientId || !value.clientSecret) {
    ElMessage.error('接口未返回完整的客户端凭证，请联系管理员')
    return false
  }
  Object.assign(credential, value)
  credentialOpen.value = true
  return true
}

async function submitForm() {
  await formRef.value.validate()
  submitLoading.value = true
  try {
    const response = await createExternalApiClient({
      clientName: form.clientName.trim(),
      scopes: [...form.scopes],
      rateLimitPerMinute: form.rateLimitPerMinute,
      tokenTtlSeconds: form.tokenTtlSeconds,
      status: form.status
    })
    createOpen.value = false
    if (showCredential(response)) {
      ElMessage.success('客户端创建成功，请立即保存凭证')
    }
    await getList()
  } finally {
    submitLoading.value = false
  }
}

async function handleStatusChange(row) {
  const nextStatus = isEnabled(row) ? STATUS_DISABLED : STATUS_ENABLED
  const action = nextStatus === STATUS_ENABLED ? '启用' : '停用'
  try {
    await ElMessageBox.confirm(
      `确认${action}客户端「${row.clientName}」吗？${nextStatus === STATUS_DISABLED ? '停用后将立即拒绝新的令牌和 API 请求。' : ''}`,
      `${action}客户端`,
      { type: 'warning' }
    )
    statusChangingId.value = row.id
    await updateExternalApiClientStatus(row.id, nextStatus)
    row.status = nextStatus
    ElMessage.success(`已${action}`)
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') throw error
  } finally {
    statusChangingId.value = undefined
  }
}

async function handleRotateSecret(row) {
  try {
    await ElMessageBox.confirm(
      `确认重置客户端「${row.clientName}」的 Secret 吗？旧 Secret 将立即失效，此操作不可撤销。`,
      '重置 Client Secret',
      {
        confirmButtonText: '确认重置',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    rotatingId.value = row.id
    const response = await rotateExternalApiClientSecret(row.id)
    if (showCredential(response, row.clientId)) {
      ElMessage.success('Secret 已重置，请立即保存新凭证')
    }
    await getList()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') throw error
  } finally {
    rotatingId.value = undefined
  }
}

function fallbackCopy(text) {
  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.style.position = 'fixed'
  textarea.style.opacity = '0'
  document.body.appendChild(textarea)
  textarea.select()
  const copied = document.execCommand('copy')
  document.body.removeChild(textarea)
  if (!copied) throw new Error('copy failed')
}

async function copyText(text, label = '内容') {
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text)
    } else {
      fallbackCopy(text)
    }
    ElMessage.success(`${label}已复制`)
  } catch {
    ElMessage.error('复制失败，请手动复制')
  }
}

function closeCredential() {
  credentialOpen.value = false
  Object.assign(credential, { clientId: '', clientSecret: '' })
}

onMounted(getList)
</script>

<template>
  <div class="app-container external-api-client-page">
    <el-alert
      class="page-alert"
      type="info"
      :closable="false"
      show-icon
      title="外部博客 API 客户端用于服务间安全调用。Client Secret 仅在创建或重置后展示一次，请妥善保管。"
    />

    <el-card shadow="never">
      <el-form :inline="true" :model="queryParams" @submit.prevent="handleQuery">
        <el-form-item label="客户端名称">
          <el-input
            v-model="queryParams.keyword"
            clearable
            maxlength="100"
            placeholder="请输入名称"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" clearable placeholder="全部" style="width: 120px">
            <el-option label="启用" :value="STATUS_ENABLED" />
            <el-option label="停用" :value="STATUS_DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-row class="mb8">
        <el-button
          type="primary"
          plain
          icon="Plus"
          v-hasPermi="['blog:external-api:client:add']"
          @click="handleAdd"
        >
          新建客户端
        </el-button>
      </el-row>

      <el-table v-loading="loading" :data="clientList" stripe>
        <el-table-column prop="clientName" label="客户端名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="clientId" label="Client ID" min-width="230" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="monospace">{{ row.clientId || '-' }}</span>
            <el-button
              v-if="row.clientId"
              class="copy-button"
              link
              type="primary"
              @click="copyText(row.clientId, 'Client ID')"
            >
              复制
            </el-button>
          </template>
        </el-table-column>
        <el-table-column label="权限范围" min-width="260">
          <template #default="{ row }">
            <div v-if="normalizeScopes(row.scopes).length" class="scope-list">
              <el-tag v-for="scope in normalizeScopes(row.scopes)" :key="scope" size="small" type="info">
                {{ scopeLabel(scope) }}
              </el-tag>
            </div>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="限流" width="120" align="center">
          <template #default="{ row }">{{ row.rateLimitPerMinute ?? '-' }} 次/分钟</template>
        </el-table-column>
        <el-table-column label="Token 有效期" width="130" align="center">
          <template #default="{ row }">{{ formatTokenTtl(row.tokenTtlSeconds) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="isEnabled(row) ? 'success' : 'info'">
              {{ isEnabled(row) ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="190" fixed="right" align="center">
          <template #default="{ row }">
            <el-button
              link
              :type="isEnabled(row) ? 'warning' : 'success'"
              :loading="statusChangingId === row.id"
              v-hasPermi="['blog:external-api:client:edit']"
              @click="handleStatusChange(row)"
            >
              {{ isEnabled(row) ? '停用' : '启用' }}
            </el-button>
            <el-button
              link
              type="danger"
              :loading="rotatingId === row.id"
              v-hasPermi="['blog:external-api:client:rotate']"
              @click="handleRotateSecret(row)"
            >
              重置 Secret
            </el-button>
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

    <el-dialog
      v-model="createOpen"
      title="新建外部博客 API 客户端"
      width="620px"
      append-to-body
      destroy-on-close
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="140px">
        <el-form-item label="客户端名称" prop="clientName">
          <el-input v-model="form.clientName" maxlength="100" show-word-limit placeholder="如：内容发布机器人" />
        </el-form-item>
        <el-form-item label="权限范围" prop="scopes">
          <el-checkbox-group v-model="form.scopes" class="scope-checkboxes">
            <el-checkbox v-for="item in scopeOptions" :key="item.value" :value="item.value">
              {{ item.label }}
              <span class="scope-code">{{ item.value }}</span>
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="每分钟请求上限" prop="rateLimitPerMinute">
          <el-input-number
            v-model="form.rateLimitPerMinute"
            :min="1"
            :max="100000"
            controls-position="right"
            style="width: 180px"
          />
          <span class="field-tip">次/分钟</span>
        </el-form-item>
        <el-form-item label="Token 有效期" prop="tokenTtlSeconds">
          <el-input-number
            v-model="form.tokenTtlSeconds"
            :min="60"
            :max="3600"
            :step="300"
            controls-position="right"
            style="width: 180px"
          />
          <span class="field-tip">秒（当前：{{ formatTokenTtl(form.tokenTtlSeconds) }}）</span>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :value="STATUS_ENABLED">启用</el-radio>
            <el-radio :value="STATUS_DISABLED">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createOpen = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="submitForm">创建客户端</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="credentialOpen"
      title="请立即保存客户端凭证"
      width="650px"
      append-to-body
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      :show-close="false"
    >
      <el-alert
        class="credential-alert"
        type="warning"
        :closable="false"
        show-icon
        title="Client Secret 仅展示本次，关闭后无法再次查看。若遗失，只能重置 Secret。"
      />
      <el-form label-width="120px">
        <el-form-item label="Client ID">
          <el-input :model-value="credential.clientId" readonly class="credential-input">
            <template #append>
              <el-button @click="copyText(credential.clientId, 'Client ID')">复制</el-button>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="Client Secret">
          <el-input :model-value="credential.clientSecret" readonly class="credential-input">
            <template #append>
              <el-button @click="copyText(credential.clientSecret, 'Client Secret')">复制</el-button>
            </template>
          </el-input>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button icon="CopyDocument" @click="copyText(credentialText, '全部凭证')">复制全部</el-button>
        <el-button type="primary" @click="closeCredential">我已保存，关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-alert,
.credential-alert {
  margin-bottom: 16px;
}

.mb8 {
  margin-bottom: 8px;
}

.monospace,
.credential-input :deep(.el-input__inner),
.scope-code {
  font-family: Consolas, Monaco, 'Courier New', monospace;
}

.copy-button {
  margin-left: 8px;
}

.scope-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.scope-checkboxes {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}

.scope-checkboxes :deep(.el-checkbox) {
  height: auto;
  margin: 5px 0;
}

.scope-code {
  margin-left: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.field-tip {
  margin-left: 10px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
