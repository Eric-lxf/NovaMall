<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch">
      <el-form-item label="微信 OpenID" prop="openid">
        <el-input
          v-model="queryParams.openid"
          placeholder="支持模糊检索"
          clearable
          style="width: 210px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="婚礼空间ID" prop="weddingId">
        <el-input
          v-model="queryParams.weddingId"
          placeholder="如 1"
          clearable
          style="width: 120px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="身份" prop="role">
        <el-select v-model="queryParams.role" placeholder="全部身份" clearable style="width: 130px">
          <el-option label="新娘 (BRIDE)" value="BRIDE" />
          <el-option label="新郎 (GROOM)" value="GROOM" />
          <el-option label="未选定 (UNSET)" value="UNSET" />
        </el-select>
      </el-form-item>
      <el-form-item label="版本权益" prop="isPro">
        <el-select v-model="queryParams.isPro" placeholder="全部" clearable style="width: 130px">
          <el-option label="Pro 终身版" :value="1" />
          <el-option label="免费体验版" :value="0" />
        </el-select>
      </el-form-item>
      <el-form-item label="账号状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
          <el-option label="正常" :value="1" />
          <el-option label="已封禁" :value="0" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <!-- 用户列表 -->
    <el-table v-loading="loading" :data="userList">
      <el-table-column label="OpenID" prop="openid" min-width="210" show-overflow-tooltip />
      <el-table-column label="身份角色" prop="role" width="110" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.role === 'BRIDE'" type="danger">新娘</el-tag>
          <el-tag v-else-if="row.role === 'GROOM'" type="primary">新郎</el-tag>
          <el-tag v-else type="info">未选定</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="关联婚礼空间" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.weddingId">#{{ row.weddingId }} · {{ row.weddingTitle }}</span>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="伴侣协同" width="100" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.partnerBound" type="success" size="small">已绑双人</el-tag>
          <el-tag v-else type="info" size="small">单人</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="Pro 权益" width="195" align="center">
        <template #default="{ row }">
          <div v-if="row.isPro === 1">
            <el-tag type="warning" effect="dark" size="small">Pro 终身版</el-tag>
            <div v-if="row.redeemCodeUsed === 'VPAY'" class="sub-vpay">已解锁 (VPAY · ¥28 站内直付)</div>
            <div v-else-if="row.redeemCodeUsed" class="sub-code">{{ row.redeemCodeUsed }}</div>
          </div>
          <el-tag v-else type="info" size="small">免费版</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="登录次数" prop="loginCount" width="90" align="center" />
      <el-table-column label="最近登录时间" prop="lastLoginAt" width="170" align="center" />
      <el-table-column label="账号状态" prop="status" width="95" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.status === 1" type="success">正常</el-tag>
          <el-tag v-else type="danger">已封禁</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="运营备注" prop="remark" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">
          <span>{{ row.remark || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" align="center" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.weddingId"
            link
            type="primary"
            @click="openSpaceDrawer(row.weddingId)"
          >空间详情</el-button>
          <el-button
            link
            type="primary"
            v-hasPermi="['wedding:user:edit']"
            @click="openEditDialog(row)"
          >编辑</el-button>
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

    <!-- 编辑用户状态与备注弹窗 -->
    <el-dialog title="编辑微信用户状态与备注" v-model="editOpen" width="460px" append-to-body>
      <el-form :model="editForm" label-width="85px">
        <el-form-item label="OpenID">
          <el-input :model-value="editForm.openid" disabled />
        </el-form-item>
        <el-form-item label="账号状态">
          <el-radio-group v-model="editForm.status">
            <el-radio :value="1">正常使用</el-radio>
            <el-radio :value="0">限制登录（封禁）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="运营备注">
          <el-input
            v-model="editForm.remark"
            type="textarea"
            :rows="3"
            maxlength="255"
            show-word-limit
            placeholder="记录售后跟进、发卡渠道或异常说明"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editOpen = false">取 消</el-button>
        <el-button type="primary" :loading="saving" @click="submitEdit">保 存</el-button>
      </template>
    </el-dialog>

    <!-- 婚礼空间运营详情抽屉 -->
    <el-drawer title="婚礼空间运营概览" v-model="drawerOpen" size="520px">
      <div v-loading="detailLoading">
        <template v-if="spaceDetail && spaceDetail.wedding">
          <el-descriptions :column="2" border size="small" title="基础信息">
            <el-descriptions-item label="空间ID">#{{ spaceDetail.wedding.id }}</el-descriptions-item>
            <el-descriptions-item label="标题">{{ spaceDetail.wedding.title }}</el-descriptions-item>
            <el-descriptions-item label="版本权益">
              <el-tag v-if="spaceDetail.wedding.isPro === 1" type="warning" size="small">Pro 终身版</el-tag>
              <el-tag v-else type="info" size="small">免费体验版</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="解锁凭证">
              <span v-if="spaceDetail.wedding.redeemCodeUsed === 'VPAY'">已解锁 (VPAY · ¥28 站内直付)</span>
              <span v-else>{{ spaceDetail.wedding.redeemCodeUsed || '-' }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="主建人 OpenID" :span="2">{{ spaceDetail.wedding.creatorOpenid }} ({{ spaceDetail.wedding.creatorRole }})</el-descriptions-item>
            <el-descriptions-item label="伴侣 OpenID" :span="2">{{ spaceDetail.wedding.partnerOpenid || '未绑定' }} ({{ spaceDetail.wedding.partnerRole }})</el-descriptions-item>
            <el-descriptions-item label="当前使用模板" :span="2">{{ spaceDetail.activeTemplateTitle || '系统默认工作区清单' }}</el-descriptions-item>
          </el-descriptions>

          <el-descriptions :column="2" border size="small" title="三池账本与礼金概况" style="margin-top: 18px;">
            <el-descriptions-item label="总预算">¥{{ spaceDetail.wedding.budgetTotal }}</el-descriptions-item>
            <el-descriptions-item label="支出笔数">{{ spaceDetail.expenseSummary?.expenseCount || 0 }} 笔</el-descriptions-item>
            <el-descriptions-item label="合同支出总额">¥{{ spaceDetail.expenseSummary?.expenseTotal || 0 }}</el-descriptions-item>
            <el-descriptions-item label="待结清尾款">{{ spaceDetail.expenseSummary?.unpaidTailCount || 0 }} 笔 (¥{{ spaceDetail.expenseSummary?.unpaidTailTotal || 0 }})</el-descriptions-item>
            <el-descriptions-item label="礼金记录">{{ spaceDetail.expenseSummary?.giftCount || 0 }} 笔</el-descriptions-item>
            <el-descriptions-item label="礼金总额">¥{{ spaceDetail.expenseSummary?.giftIncomeTotal || 0 }}</el-descriptions-item>
          </el-descriptions>

          <div style="margin-top: 18px; font-weight: 600; font-size: 14px; margin-bottom: 8px;">档期场次列表</div>
          <el-table :data="spaceDetail.events || []" size="small" border>
            <el-table-column label="场次" prop="name" width="100" />
            <el-table-column label="阳历日期" prop="eventDate" width="110" />
            <el-table-column label="酒店厅房" prop="hotelName" min-width="140" show-overflow-tooltip />
            <el-table-column label="桌数" width="85" align="center">
              <template #default="{ row }">{{ row.plannedTables }}+{{ row.backupTables }}</template>
            </el-table-column>
          </el-table>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<script setup name="WeddingUsers">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getWeddingAdminDetail, listWxUsers, updateWxUser } from '@/api/wedding/admin'

const loading = ref(false)
const saving = ref(false)
const showSearch = ref(true)
const total = ref(0)
const userList = ref([])

const queryParams = reactive({
  pageNum: 1,
  pageSize: 20,
  openid: '',
  weddingId: '',
  role: '',
  isPro: undefined,
  status: undefined
})

const editOpen = ref(false)
const editForm = reactive({
  openid: '',
  status: 1,
  remark: ''
})

const drawerOpen = ref(false)
const detailLoading = ref(false)
const spaceDetail = ref(null)

async function getList() {
  loading.value = true
  try {
    const res = await listWxUsers(queryParams)
    const payload = res.data || {}
    userList.value = payload.rows || []
    total.value = payload.total || 0
  } finally {
    loading.value = false
  }
}

function handleQuery() {
  queryParams.pageNum = 1
  getList()
}

function resetQuery() {
  queryParams.openid = ''
  queryParams.weddingId = ''
  queryParams.role = ''
  queryParams.isPro = undefined
  queryParams.status = undefined
  handleQuery()
}

function openEditDialog(row) {
  editForm.openid = row.openid
  editForm.status = row.status ?? 1
  editForm.remark = row.remark || ''
  editOpen.value = true
}

async function submitEdit() {
  saving.value = true
  try {
    await updateWxUser(editForm.openid, {
      status: editForm.status,
      remark: editForm.remark
    })
    ElMessage.success('用户信息已更新')
    editOpen.value = false
    await getList()
  } finally {
    saving.value = false
  }
}

async function openSpaceDrawer(weddingId) {
  drawerOpen.value = true
  detailLoading.value = true
  spaceDetail.value = null
  try {
    const res = await getWeddingAdminDetail(weddingId)
    spaceDetail.value = res.data || null
  } finally {
    detailLoading.value = false
  }
}

onMounted(() => {
  getList()
})
</script>

<style scoped>
.sub-code {
  font-size: 11px;
  color: #909399;
  font-family: Consolas, Monaco, monospace;
  margin-top: 2px;
}
.sub-vpay {
  font-size: 11px;
  color: #67c23a;
  font-weight: 500;
  margin-top: 2px;
}
</style>
