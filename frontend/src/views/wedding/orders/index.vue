<template>
  <div class="app-container">
    <!-- 顶部 4 张核心营收与订单统计卡片 -->
    <el-row :gutter="16" class="mb8" style="margin-bottom: 16px;">
      <el-col :xs="12" :sm="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-label">累计订单总数</div>
          <div class="stat-value">{{ summary.totalOrders }}</div>
          <div class="stat-sub">当前环境范围全部订单</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-label">已支付笔数</div>
          <div class="stat-value-row">
            <span class="stat-value text-success">{{ summary.paidOrders }}</span>
            <el-tag type="success" effect="plain" size="small" class="rate-tag">
              转化率 {{ conversionRate }}
            </el-tag>
          </div>
          <div class="stat-sub">成功核销并解锁 Pro 空间</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-label">累计实收总额</div>
          <div class="stat-value text-primary">¥{{ summary.paidAmountYuan }}</div>
          <div class="stat-sub">¥28 终身完整版站内直付</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-label">待支付单数</div>
          <div class="stat-value text-warning">{{ summary.pendingOrders }}</div>
          <div class="stat-sub">已拉起收银台待支付确认</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 搜索筛选栏 -->
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch">
      <el-form-item label="商户单号" prop="outTradeNo">
        <el-input
          v-model="queryParams.outTradeNo"
          placeholder="如 VP20261124..."
          clearable
          style="width: 195px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="购买人 OpenID" prop="openid">
        <el-input
          v-model="queryParams.openid"
          placeholder="支持模糊检索"
          clearable
          style="width: 195px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="婚礼空间ID" prop="weddingId">
        <el-input
          v-model="queryParams.weddingId"
          placeholder="如 100"
          clearable
          style="width: 120px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="支付状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="全部状态" clearable style="width: 135px">
          <el-option label="已支付" value="PAID" />
          <el-option label="待支付" value="PENDING" />
          <el-option label="已关闭" value="CLOSED" />
          <el-option label="失败" value="FAILED" />
        </el-select>
      </el-form-item>
      <el-form-item label="运行环境" prop="envMode">
        <el-select v-model="queryParams.envMode" placeholder="全部环境" clearable style="width: 135px">
          <el-option label="现网 (prod)" value="prod" />
          <el-option label="沙箱 (sandbox)" value="sandbox" />
          <el-option label="模拟 (mock)" value="mock" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        <el-button
          type="success"
          plain
          icon="Download"
          :disabled="!orderList.length"
          @click="handleExportCsv"
        >导出当前列表 CSV</el-button>
      </el-form-item>
    </el-form>

    <!-- 工具栏 -->
    <el-row :gutter="10" class="mb8">
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <!-- 订单明细表格 -->
    <el-table v-loading="loading" :data="orderList">
      <el-table-column label="商户单号" prop="outTradeNo" min-width="205" show-overflow-tooltip>
        <template #default="{ row }">
          <span class="mono-code" @click="copyTradeNo(row.outTradeNo)" title="点击复制商户单号">
            {{ row.outTradeNo }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="微信虚拟支付单号" prop="wxOrderId" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">
          <span class="mono-sub">{{ row.wxOrderId || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="关联婚礼空间" min-width="185" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.weddingId">{{ row.weddingTitle || '-' }} (#{{ row.weddingId }})</span>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="购买人 OpenID" prop="openid" min-width="190" show-overflow-tooltip>
        <template #default="{ row }">
          <span>{{ row.openid || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="终端平台" prop="clientOs" width="125" align="center">
        <template #default="{ row }">
          <el-tag v-if="normalizeLower(row.clientOs) === 'ios'" type="info" effect="plain" size="small">iOS 苹果端</el-tag>
          <el-tag v-else-if="normalizeLower(row.clientOs) === 'android'" type="success" effect="plain" size="small">Android 端</el-tag>
          <el-tag v-else-if="normalizeLower(row.clientOs) === 'devtools'" type="warning" effect="plain" size="small">开发工具</el-tag>
          <el-tag v-else type="info" size="small">{{ row.clientOs || '未知' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="订单金额" prop="amountYuan" width="105" align="right">
        <template #default="{ row }">
          <span class="amount-text">¥{{ row.amountYuan ?? '0.00' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="环境" prop="envMode" width="90" align="center">
        <template #default="{ row }">
          <el-tag v-if="normalizeLower(row.envMode) === 'prod'" type="danger" effect="plain" size="small">现网</el-tag>
          <el-tag v-else-if="normalizeLower(row.envMode) === 'sandbox'" type="warning" effect="plain" size="small">沙箱</el-tag>
          <el-tag v-else-if="normalizeLower(row.envMode) === 'mock'" type="info" effect="plain" size="small">模拟</el-tag>
          <el-tag v-else type="info" size="small">{{ row.envMode || '-' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" prop="status" width="100" align="center">
        <template #default="{ row }">
          <el-tag v-if="normalizeUpper(row.status) === 'PAID'" type="success" size="small">已支付</el-tag>
          <el-tag v-else-if="normalizeUpper(row.status) === 'PENDING'" type="warning" size="small">待支付</el-tag>
          <el-tag v-else-if="normalizeUpper(row.status) === 'CLOSED'" type="info" size="small">已关闭</el-tag>
          <el-tag v-else-if="normalizeUpper(row.status) === 'FAILED'" type="danger" size="small">失败</el-tag>
          <el-tag v-else type="info" size="small">{{ row.status || '-' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="核销通道" prop="deliverSource" width="135" align="center">
        <template #default="{ row }">
          <el-tag v-if="normalizeUpper(row.deliverSource) === 'CLIENT_CONFIRM'" type="primary" effect="light" size="small">主动查单确认</el-tag>
          <el-tag v-else-if="normalizeUpper(row.deliverSource) === 'WX_NOTIFY'" type="success" effect="light" size="small">微信推送回调</el-tag>
          <el-tag v-else-if="normalizeUpper(row.deliverSource) === 'MOCK'" type="info" effect="light" size="small">本地模拟</el-tag>
          <span v-else-if="!row.deliverSource">-</span>
          <el-tag v-else type="info" size="small">{{ row.deliverSource }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="支付时间" prop="paidAt" width="170" align="center">
        <template #default="{ row }">
          <span>{{ formatDateTime(row.paidAt) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" prop="createdAt" width="170" align="center">
        <template #default="{ row }">
          <span>{{ formatDateTime(row.createdAt) }}</span>
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
  </div>
</template>

<script setup name="WeddingOrders">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listPayOrders } from '@/api/wedding/admin'

const loading = ref(false)
const showSearch = ref(true)
const total = ref(0)
const orderList = ref([])

const summary = reactive({
  totalOrders: 0,
  paidOrders: 0,
  pendingOrders: 0,
  paidAmountFen: 0,
  paidAmountYuan: '0.00'
})

const queryParams = reactive({
  pageNum: 1,
  pageSize: 20,
  outTradeNo: '',
  openid: '',
  weddingId: '',
  status: '',
  envMode: ''
})

const conversionRate = computed(() => {
  const totalCount = Number(summary.totalOrders) || 0
  const paidCount = Number(summary.paidOrders) || 0
  if (totalCount <= 0) return '0.0%'
  return `${((paidCount / totalCount) * 100).toFixed(1)}%`
})

function normalizeLower(val) {
  return String(val || '').trim().toLowerCase()
}

function normalizeUpper(val) {
  return String(val || '').trim().toUpperCase()
}

function formatDateTime(val) {
  if (!val) return '-'
  return String(val).replace('T', ' ').slice(0, 19)
}

function formatClientOsLabel(os) {
  const v = normalizeLower(os)
  if (v === 'ios') return 'iOS 苹果端'
  if (v === 'android') return 'Android 端'
  if (v === 'devtools') return '开发工具'
  return os || '未知'
}

function formatEnvLabel(env) {
  const v = normalizeLower(env)
  if (v === 'prod') return '现网'
  if (v === 'sandbox') return '沙箱'
  if (v === 'mock') return '模拟'
  return env || '-'
}

function formatStatusLabel(status) {
  const v = normalizeUpper(status)
  if (v === 'PAID') return '已支付'
  if (v === 'PENDING') return '待支付'
  if (v === 'CLOSED') return '已关闭'
  if (v === 'FAILED') return '失败'
  return status || '-'
}

function formatDeliverSourceLabel(src) {
  const v = normalizeUpper(src)
  if (v === 'CLIENT_CONFIRM') return '主动查单确认'
  if (v === 'WX_NOTIFY') return '微信推送回调'
  if (v === 'MOCK') return '本地模拟'
  return src || '-'
}

async function getList() {
  loading.value = true
  try {
    const res = await listPayOrders(queryParams)
    const payload = res.data || {}
    orderList.value = payload.rows || []
    total.value = payload.total || 0
    if (payload.summary) {
      summary.totalOrders = payload.summary.totalOrders ?? 0
      summary.paidOrders = payload.summary.paidOrders ?? 0
      summary.pendingOrders = payload.summary.pendingOrders ?? 0
      summary.paidAmountFen = payload.summary.paidAmountFen ?? 0
      summary.paidAmountYuan = payload.summary.paidAmountYuan ?? '0.00'
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
  queryParams.outTradeNo = ''
  queryParams.openid = ''
  queryParams.weddingId = ''
  queryParams.status = ''
  queryParams.envMode = ''
  handleQuery()
}

async function copyTradeNo(outTradeNo) {
  if (!outTradeNo) return
  try {
    await navigator.clipboard.writeText(outTradeNo)
    ElMessage.success(`已复制商户单号 ${outTradeNo}`)
  } catch {
    ElMessage.warning('浏览器剪贴板受限，请手动选中复制')
  }
}

function escapeCsvCell(val) {
  const str = String(val ?? '')
  if (str.includes(',') || str.includes('"') || str.includes('\n')) {
    return `"${str.replace(/"/g, '""')}"`
  }
  return str
}

function handleExportCsv() {
  const headers = [
    '商户单号',
    '微信虚拟支付单号',
    '关联婚礼空间',
    '购买人OpenID',
    '终端平台',
    '订单金额(元)',
    '环境',
    '状态',
    '核销通道',
    '支付时间',
    '创建时间'
  ]
  const lines = orderList.value.map((r) =>
    [
      r.outTradeNo || '',
      r.wxOrderId || '',
      r.weddingId ? `${r.weddingTitle || '-'} (#${r.weddingId})` : '-',
      r.openid || '',
      formatClientOsLabel(r.clientOs),
      r.amountYuan ?? '0.00',
      formatEnvLabel(r.envMode),
      formatStatusLabel(r.status),
      formatDeliverSourceLabel(r.deliverSource),
      formatDateTime(r.paidAt),
      formatDateTime(r.createdAt)
    ]
      .map(escapeCsvCell)
      .join(',')
  )
  const csvContent = '\uFEFF' + [headers.join(','), ...lines].join('\n')
  const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = `wuxi_pay_orders_${Date.now()}.csv`
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
.stat-value-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: #303133;
  line-height: 1.2;
}
.stat-sub {
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
}
.rate-tag {
  font-weight: 600;
}
.text-success {
  color: #67c23a;
}
.text-primary {
  color: #409eff;
}
.text-warning {
  color: #e6a23c;
}
.mono-code {
  font-family: Consolas, Monaco, monospace;
  font-weight: 600;
  color: #303133;
  cursor: pointer;
}
.mono-code:hover {
  color: #409eff;
}
.mono-sub {
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  color: #606266;
}
.amount-text {
  font-family: Consolas, Monaco, monospace;
  font-weight: 700;
  color: #303133;
}
</style>
