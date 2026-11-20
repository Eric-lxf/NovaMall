<template>
  <div class="app-container">
    <!-- 顶部批次汇总统计卡 -->
    <el-row :gutter="16" class="mb8" style="margin-bottom: 16px;">
      <el-col :xs="12" :sm="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-label">激活码总数</div>
          <div class="stat-value">{{ summary.totalCount }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-label">未使用（可发卡）</div>
          <div class="stat-value text-success">{{ summary.unusedCount }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-label">已核销（已激活Pro）</div>
          <div class="stat-value text-primary">{{ summary.usedCount }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-label">已作废</div>
          <div class="stat-value text-muted">{{ summary.voidedCount }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 搜索栏 -->
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch">
      <el-form-item label="批次号" prop="batchNo">
        <el-input
          v-model="queryParams.batchNo"
          placeholder="如 XHS_202611_01"
          clearable
          style="width: 190px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="全部状态" clearable style="width: 140px">
          <el-option label="未使用" value="unused" />
          <el-option label="已核销" value="used" />
          <el-option label="已作废" value="voided" />
        </el-select>
      </el-form-item>
      <el-form-item label="兑换码" prop="code">
        <el-input
          v-model="queryParams.code"
          placeholder="6位激活码"
          clearable
          style="width: 150px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="婚礼空间ID" prop="weddingId">
        <el-input
          v-model="queryParams.weddingId"
          placeholder="空间ID"
          clearable
          style="width: 130px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <!-- 工具栏 -->
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
          type="primary"
          plain
          icon="Plus"
          @click="openGenerateDialog"
          v-hasPermi="['wedding:redeem:create']"
        >批量生成激活码</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="Download"
          :disabled="!codeList.length"
          @click="handleExportCsv"
        >导出当前页 CSV</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <!-- 数据表格 -->
    <el-table v-loading="loading" :data="codeList">
      <el-table-column label="ID" prop="id" width="80" align="center" />
      <el-table-column label="6位激活码" prop="code" width="140" align="center">
        <template #default="{ row }">
          <span class="mono-code">{{ row.code }}</span>
        </template>
      </el-table-column>
      <el-table-column label="批次号" prop="batchNo" min-width="160" show-overflow-tooltip />
      <el-table-column label="状态" prop="status" width="110" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.status === 'unused'" type="success">未使用</el-tag>
          <el-tag v-else-if="row.status === 'used'" type="primary">已核销</el-tag>
          <el-tag v-else type="info">已作废</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="核销空间ID" prop="usedByWeddingId" width="110" align="center">
        <template #default="{ row }">
          <span>{{ row.usedByWeddingId || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="核销人 OpenID" prop="usedByOpenid" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">
          <span>{{ row.usedByOpenid || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="核销时间" prop="usedAt" width="170" align="center">
        <template #default="{ row }">
          <span>{{ row.usedAt || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="渠道备注" prop="note" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          <span>{{ row.note || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" prop="createdAt" width="170" align="center" />
      <el-table-column label="操作" width="130" align="center" fixed="right">
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            @click="copySingleCode(row.code)"
          >复制</el-button>
          <el-button
            v-if="row.status === 'unused'"
            link
            type="danger"
            v-hasPermi="['wedding:redeem:void']"
            @click="handleVoid(row)"
          >作废</el-button>
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

    <!-- 批量生成激活码弹窗 -->
    <el-dialog title="批量生成 6 位 Pro 激活码" v-model="genOpen" width="520px" append-to-body>
      <el-form ref="genFormRef" :model="genForm" :rules="genRules" label-width="95px">
        <el-form-item label="发卡批次号" prop="batchNo">
          <el-input v-model="genForm.batchNo" placeholder="例如 XHS_202611_01（字母/数字/下划线）" maxlength="32" />
        </el-form-item>
        <el-form-item label="生成数量" prop="count">
          <el-input-number v-model="genForm.count" :min="1" :max="500" :step="10" style="width: 180px" />
          <span class="form-tip">单次最多 500 个，自动剔除易混淆字符 0/O/1/I</span>
        </el-form-item>
        <el-form-item label="渠道备注" prop="note">
          <el-input v-model="genForm.note" placeholder="如：小红书店铺自动发货卡密池" maxlength="128" />
        </el-form-item>
      </el-form>

      <div v-if="generatedCodes.length" class="generated-box">
        <div class="generated-header">
          <span>本次成功生成 <b>{{ generatedCodes.length }}</b> 枚激活码（可直接粘贴至发卡平台）：</span>
          <el-button type="primary" link @click="copyGeneratedBatch">一键复制全部</el-button>
        </div>
        <el-input
          type="textarea"
          :rows="6"
          readonly
          :model-value="generatedCodes.join('\n')"
        />
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="genOpen = false">关 闭</el-button>
          <el-button type="primary" :loading="submitting" @click="submitGenerate">立即生成</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="WeddingRedeem">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { generateRedeemBatch, listRedeemCodes, voidRedeemCode } from '@/api/wedding/admin'

const loading = ref(false)
const submitting = ref(false)
const showSearch = ref(true)
const total = ref(0)
const codeList = ref([])
const summary = reactive({
  totalCount: 0,
  unusedCount: 0,
  usedCount: 0,
  voidedCount: 0
})

const queryParams = reactive({
  pageNum: 1,
  pageSize: 20,
  batchNo: '',
  status: '',
  code: '',
  weddingId: ''
})

const genOpen = ref(false)
const genFormRef = ref(null)
const generatedCodes = ref([])
const genForm = reactive({
  batchNo: '',
  count: 50,
  skuType: 'pro_lifetime',
  note: ''
})

const genRules = {
  batchNo: [
    { required: true, message: '请输入批次号', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_-]{1,32}$/, message: '仅允许 1~32 位字母、数字、下划线或短横线', trigger: 'blur' }
  ],
  count: [{ required: true, message: '请输入生成数量', trigger: 'change' }]
}

async function getList() {
  loading.value = true
  try {
    const res = await listRedeemCodes(queryParams)
    const payload = res.data || {}
    codeList.value = payload.rows || []
    total.value = payload.total || 0
    if (payload.summary) {
      summary.totalCount = payload.summary.totalCount || 0
      summary.unusedCount = payload.summary.unusedCount || 0
      summary.usedCount = payload.summary.usedCount || 0
      summary.voidedCount = payload.summary.voidedCount || 0
    }
  } finally {
    loading.value = false
  }
}

function handleQuery() {
  queryParams.pageNum = 1
  getList()
}

function resetQuery() {
  queryParams.batchNo = ''
  queryParams.status = ''
  queryParams.code = ''
  queryParams.weddingId = ''
  handleQuery()
}

function openGenerateDialog() {
  const today = new Date().toISOString().slice(0, 10).replace(/-/g, '')
  genForm.batchNo = `XHS_${today}_01`
  genForm.count = 50
  genForm.note = ''
  generatedCodes.value = []
  genOpen.value = true
}

function submitGenerate() {
  genFormRef.value?.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      const res = await generateRedeemBatch(genForm)
      const data = res.data || {}
      generatedCodes.value = data.codes || []
      ElMessage.success(`成功生成 ${data.generatedCount || generatedCodes.value.length} 枚激活码`)
      queryParams.batchNo = genForm.batchNo
      queryParams.pageNum = 1
      await getList()
    } finally {
      submitting.value = false
    }
  })
}

async function handleVoid(row) {
  try {
    await ElMessageBox.confirm(
      `确认将激活码【${row.code}】标记为作废吗？作废后小程序将无法兑换。`,
      '作废确认',
      { type: 'warning' }
    )
    await voidRedeemCode(row.code)
    ElMessage.success(`激活码 ${row.code} 已作废`)
    await getList()
  } catch {
    // 取消或已由拦截器提示
  }
}

async function copyText(text, tip) {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success(tip)
  } catch {
    ElMessage.warning('浏览器剪贴板受限，请手动选中复制')
  }
}

function copySingleCode(code) {
  copyText(code, `已复制激活码 ${code}`)
}

function copyGeneratedBatch() {
  copyText(generatedCodes.value.join('\n'), `已复制全部 ${generatedCodes.value.length} 枚激活码`)
}

function handleExportCsv() {
  const headers = ['ID', '激活码', '批次号', '状态', '核销婚礼ID', '核销OpenID', '核销时间', '备注', '创建时间']
  const lines = codeList.value.map((r) =>
    [
      r.id,
      r.code,
      r.batchNo,
      r.status,
      r.usedByWeddingId || '',
      r.usedByOpenid || '',
      r.usedAt || '',
      `"${String(r.note || '').replace(/"/g, '""')}"`,
      r.createdAt || ''
    ].join(',')
  )
  const csvContent = '\uFEFF' + [headers.join(','), ...lines].join('\n')
  const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = `wuxi_redeem_codes_${Date.now()}.csv`
  link.click()
  URL.revokeObjectURL(link.href)
}

onMounted(() => {
  getList()
})
</script>

<style scoped>
.stat-card {
  border-radius: 8px;
}
.stat-label {
  font-size: 13px;
  color: #606266;
  margin-bottom: 6px;
}
.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: #303133;
}
.text-success {
  color: #67c23a;
}
.text-primary {
  color: #409eff;
}
.text-muted {
  color: #909399;
}
.mono-code {
  font-family: Consolas, Monaco, monospace;
  font-weight: 700;
  letter-spacing: 1px;
}
.form-tip {
  margin-left: 10px;
  font-size: 12px;
  color: #909399;
}
.generated-box {
  margin-top: 12px;
  padding: 12px;
  background: #f8f9fb;
  border-radius: 6px;
}
.generated-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  font-size: 13px;
}
</style>
