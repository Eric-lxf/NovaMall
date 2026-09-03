<template>
  <div class="app-container">
    <el-alert :closable="false" type="info" class="mb12" title="在此配置多个 AI 服务商（OpenAI / ChatGPT、Claude、DeepSeek 及兼容接口）。调用时使用下方「默认 Provider」，未设置则取第一个启用项。" />

    <el-card class="mb12" shadow="never">
      <template #header><span>全局默认配置</span></template>
      <el-form :inline="true">
        <el-form-item label="全局数据库默认（模块未覆盖时使用）">
          <el-select v-model="defaultProviderId" clearable placeholder="使用首个启用项" style="width: 280px">
            <el-option
              v-for="item in enabledOptions"
              :key="item.id"
              :label="`${item.name} (${item.defaultModel})`"
              :value="String(item.id)"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="configLoading" v-hasPermi="['blog:ai:provider:edit']" @click="saveDefault">保存默认</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="mb12" shadow="never">
      <template #header><span>按功能模块配置</span></template>
      <el-table v-loading="moduleLoading" :data="moduleConfigs" border>
        <el-table-column label="模块" min-width="220">
          <template #default="{ row }">
            <div class="module-name">{{ moduleMeta(row.moduleCode).name }}</div>
            <div class="module-desc">{{ moduleMeta(row.moduleCode).desc }}</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="row.inherited ? 'info' : 'success'">
              {{ row.inherited ? '继承全局默认' : '已覆盖' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Provider" min-width="260">
          <template #default="{ row }">
            <el-select v-model="row.providerId" :disabled="!isModuleEditing(row)" placeholder="请选择 Provider" style="width: 100%">
              <el-option
                v-for="provider in moduleProviderOptions"
                :key="provider.id"
                :label="`${provider.name}（${provider.defaultModel}）`"
                :value="provider.id"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="模型" min-width="260">
          <template #default="{ row }">
            <el-input
              v-if="isVisionModule(row)"
              v-model="row.visionModel"
              :disabled="!isModuleEditing(row)"
              placeholder="视觉模型（可留空继承 Provider 视觉模型）"
            />
            <el-input
              v-else
              v-model="row.textModel"
              :disabled="!isModuleEditing(row)"
              placeholder="文本模型（可留空继承 Provider 默认模型）"
            />
          </template>
        </el-table-column>
        <el-table-column label="温度" width="170" align="center">
          <template #default="{ row }">
            <el-input-number
              v-if="!isVisionModule(row)"
              v-model="row.temperature"
              :disabled="!isModuleEditing(row)"
              :min="0"
              :max="2"
              :precision="2"
              :step="0.1"
              controls-position="right"
              style="width: 130px"
            />
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="220">
          <template #default="{ row }">
            <el-input v-model="row.remark" :disabled="!isModuleEditing(row)" maxlength="255" placeholder="可选备注" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.inherited && !isModuleEditing(row)"
              link
              type="primary"
              v-hasPermi="['blog:ai:provider:edit']"
              @click="enableModuleEdit(row)"
            >
              编辑配置
            </el-button>
            <el-button
              v-if="isModuleEditing(row)"
              link
              type="primary"
              :loading="moduleSavingCode === row.moduleCode"
              v-hasPermi="['blog:ai:provider:edit']"
              @click="saveModule(row)"
            >
              保存
            </el-button>
            <el-button
              v-if="row.inherited && isModuleEditing(row)"
              link
              v-hasPermi="['blog:ai:provider:edit']"
              @click="cancelModuleEdit(row)"
            >
              取消
            </el-button>
            <el-button
              v-if="!row.inherited"
              link
              type="danger"
              v-hasPermi="['blog:ai:provider:remove']"
              @click="restoreInheritance(row)"
            >
              恢复继承
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-form :inline="true" :model="queryParams" class="mb8">
      <el-form-item label="名称/模型">
        <el-input v-model="queryParams.keyword" placeholder="名称或模型" clearable style="width: 200px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="类型">
        <el-select v-model="queryParams.providerType" placeholder="全部" clearable style="width: 180px">
          <el-option label="OpenAI 兼容" value="openai_compatible" />
          <el-option label="Anthropic Claude" value="anthropic" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        <el-button type="primary" plain icon="Plus" v-hasPermi="['blog:ai:provider:add']" @click="openDialog()">新增</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="list">
      <el-table-column label="ID" prop="id" width="70" />
      <el-table-column label="名称" prop="name" min-width="140" />
      <el-table-column label="类型" prop="providerType" width="150">
        <template #default="{ row }">
          {{ typeLabel(row.providerType) }}
        </template>
      </el-table-column>
      <el-table-column label="Base URL" prop="baseUrl" min-width="200" show-overflow-tooltip />
      <el-table-column label="默认模型" prop="defaultModel" min-width="140" show-overflow-tooltip />
      <el-table-column label="凭据" prop="apiKeyMasked" width="160" show-overflow-tooltip />
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.enabled === 1 ? 'success' : 'info'">{{ row.enabled === 1 ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="更新时间" prop="updateTime" width="170" />
      <el-table-column label="操作" width="240" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" v-hasPermi="['blog:ai:provider:edit']" @click="openDialog(row)">编辑</el-button>
          <el-button link type="primary" v-hasPermi="['blog:ai:provider:test']" @click="handleTest(row)">测试</el-button>
          <el-button link type="danger" v-hasPermi="['blog:ai:provider:remove']" @click="handleDelete(row)">删除</el-button>
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

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑 AI Provider' : '新增 AI Provider'" width="640px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="快捷预设">
          <el-select v-model="preset" placeholder="选择预设自动填充" clearable style="width: 100%" @change="applyPreset">
            <el-option v-for="p in presets" :key="p.key" :label="p.label" :value="p.key" />
          </el-select>
        </el-form-item>
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" maxlength="100" placeholder="如 OpenAI 生产 / Claude 写作" />
        </el-form-item>
        <el-form-item label="厂商类型" prop="providerType">
          <el-radio-group v-model="form.providerType">
            <el-radio label="openai_compatible">OpenAI 兼容</el-radio>
            <el-radio label="anthropic">Anthropic Claude</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.providerType === 'anthropic'" label="认证方式">
          <el-radio-group v-model="form.authMode">
            <el-radio label="api_key">API Key</el-radio>
            <el-radio label="auth_token">ANTHROPIC_AUTH_TOKEN</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="form.providerType === 'anthropic' && form.authMode === 'auth_token' ? 'Auth Token' : 'API Key'" :prop="form.id ? undefined : 'apiKey'">
          <el-input v-model="form.apiKey" type="password" show-password maxlength="512" :placeholder="form.id ? '留空则不修改' : form.authMode === 'auth_token' ? '填写 ANTHROPIC_AUTH_TOKEN' : '必填'" />
        </el-form-item>
        <el-form-item label="Base URL" prop="baseUrl">
          <el-input v-model="form.baseUrl" maxlength="255" placeholder="https://api.openai.com" />
        </el-form-item>
        <el-form-item label="默认模型" prop="defaultModel">
          <el-input v-model="form.defaultModel" maxlength="128" placeholder="gpt-4o-mini / claude-sonnet-4-5 / deepseek-chat" />
        </el-form-item>
        <el-form-item label="视觉模型">
          <el-input v-model="form.visionModel" maxlength="128" placeholder="可选，账单图片识别等" />
        </el-form-item>
        <el-form-item label="超时(秒)" prop="timeoutSeconds">
          <el-input-number v-model="form.timeoutSeconds" :min="10" :max="600" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" maxlength="255" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import {
  listAiProvider,
  getAiProvider,
  saveAiProvider,
  deleteAiProvider,
  testAiProvider,
  getAiModuleConfig,
  saveAiModuleConfig,
  listAiProviderOptions,
  listFeatureModuleConfigs,
  saveFeatureModuleOverride,
  deleteFeatureModuleOverride
} from '@/api/blog/aiProvider'

defineOptions({ name: 'BlogAiProvider' })

const { proxy } = getCurrentInstance()
const formRef = ref()
const loading = ref(false)
const submitLoading = ref(false)
const configLoading = ref(false)
const dialogVisible = ref(false)
const list = ref([])
const total = ref(0)
const defaultProviderId = ref('')
const enabledOptions = ref([])
const moduleLoading = ref(false)
const moduleConfigs = ref([])
const moduleProviderOptions = ref([])
const moduleSavingCode = ref('')
const inheritedEditingCodes = ref([])
const inheritedEditingSnapshot = reactive({})
const preset = ref('')

const presets = [
  { key: 'openai', label: 'OpenAI / ChatGPT', providerType: 'openai_compatible', baseUrl: 'https://api.openai.com', defaultModel: 'gpt-4o-mini', visionModel: 'gpt-4o' },
  { key: 'deepseek', label: 'DeepSeek', providerType: 'openai_compatible', baseUrl: 'https://api.deepseek.com', defaultModel: 'deepseek-chat', visionModel: 'deepseek-vl2' },
  { key: 'claude', label: 'Anthropic Claude', providerType: 'anthropic', authMode: 'api_key', baseUrl: 'https://api.anthropic.com', defaultModel: 'claude-sonnet-4-5', visionModel: 'claude-sonnet-4-5' },
  { key: 'moonshot', label: 'Moonshot (Kimi)', providerType: 'openai_compatible', baseUrl: 'https://api.moonshot.cn', defaultModel: 'moonshot-v1-8k', visionModel: '' },
  { key: 'qwen', label: '通义千问（兼容模式）', providerType: 'openai_compatible', baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode', defaultModel: 'qwen-plus', visionModel: 'qwen3.5-ocr' }
]

const moduleMetaMap = {
  editor: { name: '编辑器增强', desc: '用于文章编辑器中的润色和续写。' },
  write: { name: '博客智写', desc: '用于博客标题、摘要与正文生成。' },
  optimize: { name: '文本优化', desc: '用于内容改写、提炼与增强。' },
  comment_moderate: { name: '评论审核', desc: '用于评论智能审核。' },
  bill_vision: { name: '账单识别', desc: '用于账单/微信交易明细图片视觉识别。请填写视觉模型（如 qwen3.5-ocr），不要填纯文本模型。' },
  bill_advice: { name: '账单建议', desc: '用于账单分析建议生成。' },
  history_extract: { name: '历史资料抽取', desc: '用于历史资料结构化抽取。' },
  exam_generate: { name: '智能命题', desc: '用于授权资料的知识点抽取与题目生成。' },
  exam_verify: { name: '命题独立复核', desc: '用于独立解题与质量复核；可配置与生成不同的模型。' }
}

const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  keyword: undefined,
  status: undefined,
  providerType: undefined
})

const form = reactive({
  id: undefined,
  name: '',
  providerType: 'openai_compatible',
  apiKey: '',
  authMode: 'api_key',
  baseUrl: 'https://api.openai.com',
  defaultModel: 'gpt-4o-mini',
  visionModel: '',
  timeoutSeconds: 300,
  enabled: 1,
  remark: ''
})

const rules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  providerType: [{ required: true, message: '请选择类型', trigger: 'change' }],
  apiKey: [{ required: true, message: '请输入 API Key', trigger: 'blur' }],
  baseUrl: [{ required: true, message: '请输入 Base URL', trigger: 'blur' }],
  defaultModel: [{ required: true, message: '请输入默认模型', trigger: 'blur' }],
  timeoutSeconds: [{ required: true, message: '请输入超时', trigger: 'change' }]
}

function typeLabel(type) {
  if (type === 'anthropic') return 'Anthropic Claude'
  return 'OpenAI 兼容'
}

function applyPreset(key) {
  const p = presets.find(item => item.key === key)
  if (!p) return
  form.providerType = p.providerType
  form.authMode = p.authMode || 'api_key'
  form.baseUrl = p.baseUrl
  form.defaultModel = p.defaultModel
  form.visionModel = p.visionModel
  if (!form.name) {
    form.name = p.label
  }
}

function resetForm() {
  Object.assign(form, {
    id: undefined,
    name: '',
    providerType: 'openai_compatible',
    apiKey: '',
    authMode: 'api_key',
    baseUrl: 'https://api.openai.com',
    defaultModel: 'gpt-4o-mini',
    visionModel: '',
    timeoutSeconds: 300,
    enabled: 1,
    remark: ''
  })
  preset.value = ''
  formRef.value?.clearValidate()
}

function getList() {
  loading.value = true
  listAiProvider(queryParams.value).then(res => {
    list.value = res.rows || []
    total.value = res.total || 0
  }).finally(() => {
    loading.value = false
  })
}

function moduleMeta(moduleCode) {
  return moduleMetaMap[moduleCode] || { name: moduleCode, desc: '未识别的模块编码' }
}

function isVisionModule(row) {
  return row.moduleCode === 'bill_vision'
}

function isModuleEditing(row) {
  return !row.inherited || inheritedEditingCodes.value.includes(row.moduleCode)
}

function clearInheritedEdit(moduleCode) {
  inheritedEditingCodes.value = inheritedEditingCodes.value.filter(code => code !== moduleCode)
  delete inheritedEditingSnapshot[moduleCode]
}

function preferredModuleProviderId() {
  const preferred = moduleProviderOptions.value.find(item => String(item.id) === String(defaultProviderId.value))
  if (preferred) return preferred.id
  return moduleProviderOptions.value[0]?.id
}

async function loadConfig() {
  try {
    const [cfgRes, optRes] = await Promise.all([getAiModuleConfig(), listAiProviderOptions()])
    defaultProviderId.value = cfgRes.data?.defaultProviderId || ''
    enabledOptions.value = (optRes.data || []).filter(i => i.enabled === 1)
  } catch (error) {
    // ignore
  }
}

async function loadFeatureModuleConfigs() {
  moduleLoading.value = true
  try {
    const { data = {} } = await listFeatureModuleConfigs()
    moduleConfigs.value = (data.items || data.modules || []).map(item => ({ ...item }))
    moduleProviderOptions.value = (data.providerOptions || []).filter(item => item.enabled === 1)
    inheritedEditingCodes.value = []
    Object.keys(inheritedEditingSnapshot).forEach(key => delete inheritedEditingSnapshot[key])
  } finally {
    moduleLoading.value = false
  }
}

function enableModuleEdit(row) {
  if (!row.inherited || inheritedEditingCodes.value.includes(row.moduleCode)) {
    return
  }
  inheritedEditingSnapshot[row.moduleCode] = { ...row }
  inheritedEditingCodes.value.push(row.moduleCode)
  if (!row.providerId) {
    row.providerId = preferredModuleProviderId()
  }
  if (!isVisionModule(row) && row.temperature == null) {
    row.temperature = 1
  }
}

function cancelModuleEdit(row) {
  if (!row.inherited) return
  const snapshot = inheritedEditingSnapshot[row.moduleCode]
  if (snapshot) {
    Object.assign(row, snapshot)
  }
  clearInheritedEdit(row.moduleCode)
}

async function saveModule(row) {
  if (!row.providerId) {
    proxy.$modal.msgError('请选择 Provider')
    return
  }
  const payload = {
    providerId: row.providerId,
    remark: row.remark || ''
  }
  if (isVisionModule(row)) {
    payload.visionModel = row.visionModel || ''
  } else {
    payload.textModel = row.textModel || ''
    payload.temperature = row.temperature
  }
  moduleSavingCode.value = row.moduleCode
  try {
    await saveFeatureModuleOverride(row.moduleCode, payload)
    proxy.$modal.msgSuccess('模块配置已保存')
    await loadFeatureModuleConfigs()
    clearInheritedEdit(row.moduleCode)
  } finally {
    moduleSavingCode.value = ''
  }
}

function restoreInheritance(row) {
  proxy.$modal.confirm(`确定恢复模块「${moduleMeta(row.moduleCode).name}」为继承全局默认吗？`).then(() => {
    return deleteFeatureModuleOverride(row.moduleCode)
  }).then(() => {
    proxy.$modal.msgSuccess('已恢复继承全局默认')
    loadFeatureModuleConfigs()
  }).catch(() => {})
}

function saveDefault() {
  configLoading.value = true
  saveAiModuleConfig({ defaultProviderId: defaultProviderId.value || '' }).then(() => {
    proxy.$modal.msgSuccess('默认 Provider 已保存')
  }).finally(() => {
    configLoading.value = false
  })
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  queryParams.value = { pageNum: 1, pageSize: 10, keyword: undefined, status: undefined, providerType: undefined }
  getList()
}

function openDialog(row) {
  resetForm()
  dialogVisible.value = true
  if (row?.id) {
    getAiProvider(row.id).then(res => {
      const data = res.data || {}
      Object.assign(form, {
        id: data.id,
        name: data.name,
        providerType: data.providerType,
        apiKey: '',
        authMode: data.authMode || 'api_key',
        baseUrl: data.baseUrl,
        defaultModel: data.defaultModel,
        visionModel: data.visionModel || '',
        timeoutSeconds: data.timeoutSeconds || 300,
        enabled: data.enabled,
        remark: data.remark || ''
      })
    })
  }
}

function submitForm() {
  formRef.value.validate(valid => {
    if (!valid) return
    submitLoading.value = true
    const payload = { ...form }
    if (payload.id && !payload.apiKey) {
      delete payload.apiKey
    }
    saveAiProvider(payload).then(() => {
      proxy.$modal.msgSuccess('保存成功')
      dialogVisible.value = false
      getList()
      Promise.all([loadConfig(), loadFeatureModuleConfigs()])
    }).finally(() => {
      submitLoading.value = false
    })
  })
}

function handleDelete(row) {
  proxy.$modal.confirm(`确定删除 Provider「${row.name}」吗？`).then(() => {
    return deleteAiProvider(row.id)
  }).then(() => {
    proxy.$modal.msgSuccess('删除成功')
    getList()
    Promise.all([loadConfig(), loadFeatureModuleConfigs()])
  }).catch(() => {})
}

function handleTest(row) {
  testAiProvider(row.id).then(() => {
    proxy.$modal.msgSuccess('连接成功')
  })
}

getList()
loadConfig().then(() => {
  loadFeatureModuleConfigs()
})
</script>

<style scoped>
.mb12 { margin-bottom: 12px; }
.mb8 { margin-bottom: 8px; }
.module-name { font-weight: 600; margin-bottom: 2px; }
.module-desc { color: #909399; font-size: 12px; line-height: 1.3; }
</style>
