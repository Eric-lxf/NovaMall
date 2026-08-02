<script setup>
defineOptions({ name: 'MallAdminSpu' })

import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listMallBrandOptions } from '@/api/mall/brand'
import { getMallCategoryAttrTemplate, listMallCategoryOptions } from '@/api/mall/category'
import {
  addMallSpu,
  delMallSpu,
  getMallSpu,
  listMallSpu,
  updateMallSpu,
  updateMallSpuStatus
} from '@/api/mall/spu'
import { adjustMallInventory } from '@/api/mall/inventory'
import { resolveUploadUrl } from '@/utils/blogAssets'

const loading = ref(false)
const saving = ref(false)
const templateLoading = ref(false)
const spuList = ref([])
const categoryList = ref([])
const brandList = ref([])
const total = ref(0)
const open = ref(false)
const title = ref('')
const formRef = ref()
const saleAttrs = ref([])
const descAttrs = ref([])
/** @type {import('vue').Ref<Record<number|string, Array<number|string>>>} */
const saleSelected = ref({})
/** @type {import('vue').Ref<Record<number|string, string|string[]>>} */
const descValues = ref({})
const adjustOpen = ref(false)
const adjusting = ref(false)
const adjustForm = reactive({
  skuId: undefined,
  skuCode: '',
  quantity: 1,
  reason: 'PURCHASE_IN',
  remark: ''
})
const adjustReasons = [
  { label: '采购入库', value: 'PURCHASE_IN' },
  { label: '手工入库', value: 'MANUAL_IN' },
  { label: '手工出库', value: 'MANUAL_OUT' },
  { label: '报损', value: 'DAMAGE' },
  { label: '退货入库', value: 'RETURN' },
  { label: '盘点校正', value: 'CORRECTION' }
]
const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  name: '',
  categoryId: undefined,
  brandId: undefined,
  status: undefined
})
const form = reactive({
  id: undefined,
  name: '',
  subtitle: '',
  categoryId: undefined,
  brandId: undefined,
  mainImage: '',
  detailHtml: '',
  status: 'DRAFT',
  sort: 0,
  remark: '',
  skus: []
})
const rules = {
  name: [{ required: true, message: '商品名称不能为空', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择类目', trigger: 'change' }]
}
const statusMap = {
  DRAFT: { label: '草稿', type: 'info' },
  ON: { label: '上架', type: 'success' },
  OFF: { label: '下架', type: 'warning' }
}

const categoryOptions = computed(() => categoryList.value)
const hasSaleAttrs = computed(() => saleAttrs.value.length > 0)
const hasDescAttrs = computed(() => descAttrs.value.length > 0)

function normalizeRows(res) {
  return res.rows || res.data?.records || res.data || []
}

function buildTree(list, parentId = 0) {
  return list
    .filter(item => Number(item.parentId ?? 0) === Number(parentId))
    .sort((a, b) => Number(a.sort ?? 0) - Number(b.sort ?? 0))
    .map(item => ({
      ...item,
      children: buildTree(list, item.id)
    }))
}

function normalizeTree(rows) {
  const tree = rows.some(item => Array.isArray(item.children)) ? rows : buildTree(rows)
  return markNonLeafDisabled(tree)
}

/** 非叶子类目禁用选择（发品必须挂后台叶子） */
function markNonLeafDisabled(nodes) {
  return (nodes || []).map(node => {
    const children = Array.isArray(node.children) && node.children.length
      ? markNonLeafDisabled(node.children)
      : undefined
    return {
      ...node,
      children,
      disabled: !!(children && children.length)
    }
  })
}

function createSku() {
  return {
    id: undefined,
    skuCode: '',
    specs: [],
    specsJson: '{}',
    price: 0,
    stock: 0,
    stockAvailable: 0,
    stockLocked: 0,
    stockTotal: 0,
    status: '0'
  }
}

function specsText(sku) {
  if (Array.isArray(sku.specs) && sku.specs.length) {
    return sku.specs.map(item => item.value).filter(Boolean).join(' / ')
  }
  try {
    const specs = JSON.parse(sku.specsJson || '{}')
    return Object.values(specs).join(' / ') || '-'
  } catch {
    return sku.specsJson || '-'
  }
}

function stockDisplay(sku) {
  return sku.stockAvailable ?? sku.stock ?? 0
}

function priceText(row) {
  const skus = row.skus || row.skuList || []
  if (!skus.length) return '-'
  const prices = skus.map(item => Number(item.price || 0))
  const min = Math.min(...prices).toFixed(2)
  const max = Math.max(...prices).toFixed(2)
  return min === max ? `¥${min}` : `¥${min} - ¥${max}`
}

function optionList(attr) {
  return (attr?.options || []).filter(item => item.status !== '1')
}

function clearTemplate() {
  saleAttrs.value = []
  descAttrs.value = []
  saleSelected.value = {}
  descValues.value = {}
}

function initDescValue(attr, existing) {
  if (attr.inputType === 'multi') {
    if (Array.isArray(existing)) return existing
    if (typeof existing === 'string' && existing) {
      return existing.split(',').map(s => s.trim()).filter(Boolean)
    }
    return []
  }
  return existing ?? ''
}

async function loadAttrTemplate(categoryId, existingAttrValues = []) {
  clearTemplate()
  if (!categoryId) return
  templateLoading.value = true
  try {
    const res = await getMallCategoryAttrTemplate(categoryId)
    const data = res.data || {}
    saleAttrs.value = data.saleAttrs || []
    descAttrs.value = data.descAttrs || []

    const existingMap = {}
    for (const item of existingAttrValues || []) {
      if (item?.attrId != null) {
        existingMap[item.attrId] = item.value
      }
    }

    const nextSale = {}
    for (const attr of saleAttrs.value) {
      nextSale[attr.id] = []
    }
    saleSelected.value = nextSale

    const nextDesc = {}
    for (const attr of descAttrs.value) {
      nextDesc[attr.id] = initDescValue(attr, existingMap[attr.id])
    }
    descValues.value = nextDesc

    // 从已有 SKU 回填销售属性多选（优先 optionId）
    if (form.skus?.length) {
      for (const attr of saleAttrs.value) {
        const selected = new Set()
        for (const sku of form.skus) {
          const structured = Array.isArray(sku.specs) ? sku.specs : []
          const hit = structured.find(item => Number(item.attrId) === Number(attr.id))
          if (hit?.optionId != null) {
            selected.add(hit.optionId)
            continue
          }
          try {
            const specs = JSON.parse(sku.specsJson || '{}')
            const raw = specs[attr.name]
            if (raw == null || raw === '') continue
            const opt = optionList(attr).find(item => item.value === String(raw))
            if (opt?.id != null) selected.add(opt.id)
          } catch {
            /* ignore */
          }
        }
        saleSelected.value[attr.id] = [...selected]
      }
    }
  } catch (e) {
    console.error('加载属性模板失败', e)
    clearTemplate()
  } finally {
    templateLoading.value = false
  }
}

function cartesian(arrays) {
  return arrays.reduce(
    (acc, curr) => {
      const next = []
      for (const prefix of acc) {
        for (const item of curr) {
          next.push([...prefix, item])
        }
      }
      return next
    },
    [[]]
  )
}

function buildSpecKey(specs) {
  return [...(specs || [])]
    .sort((a, b) => Number(a.attrId) - Number(b.attrId))
    .map(item => `attr:${item.attrId}=option:${item.optionId}`)
    .join('|')
}

function generateSkusFromSale() {
  if (!saleAttrs.value.length) {
    ElMessage.warning('当前类目无销售属性')
    return
  }
  const dims = []
  for (const attr of saleAttrs.value) {
    const selectedIds = saleSelected.value[attr.id] || []
    if (!selectedIds.length) {
      ElMessage.warning(`请为销售属性「${attr.name}」至少选择一个值`)
      return
    }
    const options = optionList(attr)
    const picked = selectedIds.map(id => {
      const opt = options.find(item => Number(item.id) === Number(id))
      if (!opt) return null
      return {
        attrId: attr.id,
        optionId: opt.id,
        value: opt.value,
        name: attr.name
      }
    }).filter(Boolean)
    if (!picked.length) {
      ElMessage.warning(`销售属性「${attr.name}」选项无效`)
      return
    }
    dims.push(picked)
  }
  const combos = cartesian(dims)
  const existingByKey = new Map()
  for (const sku of form.skus || []) {
    const key = buildSpecKey(sku.specs) || sku.specsJson || '{}'
    existingByKey.set(key, sku)
  }
  const nextSkus = combos.map((combo, index) => {
    const specs = combo.map(item => ({
      attrId: item.attrId,
      optionId: item.optionId,
      value: item.value
    }))
    const display = {}
    const codeParts = []
    for (const item of combo) {
      display[item.name] = item.value
      codeParts.push(item.value)
    }
    const specsJson = JSON.stringify(display)
    const key = buildSpecKey(specs)
    const prev = existingByKey.get(key)
    if (prev) {
      return { ...createSku(), ...prev, specs, specsJson }
    }
    const prefix = (form.name || 'SKU').replace(/\s+/g, '').slice(0, 16)
    return {
      ...createSku(),
      skuCode: `${prefix}-${codeParts.join('-') || index + 1}`.slice(0, 64),
      specs,
      specsJson
    }
  })
  const keys = nextSkus.map(sku => buildSpecKey(sku.specs))
  if (new Set(keys).size !== keys.length) {
    ElMessage.error('生成结果存在重复规格，请检查销售属性选择')
    return
  }
  form.skus = nextSkus
  ElMessage.success(`已生成 ${form.skus.length} 个 SKU`)
}

function buildAttrValues() {
  return descAttrs.value.map(attr => {
    const raw = descValues.value[attr.id]
    let value = ''
    if (Array.isArray(raw)) {
      value = raw.join(',')
    } else if (raw != null) {
      value = String(raw)
    }
    return { attrId: attr.id, value }
  })
}

async function loadOptions() {
  try {
    const categoryRes = await listMallCategoryOptions()
    categoryList.value = normalizeTree(normalizeRows(categoryRes))
  } catch (e) {
    console.error('加载类目下拉失败', e)
    categoryList.value = []
  }
  try {
    const brandRes = await listMallBrandOptions()
    brandList.value = normalizeRows(brandRes)
  } catch (e) {
    console.error('加载品牌下拉失败', e)
    brandList.value = []
  }
}

async function getList() {
  loading.value = true
  try {
    const res = await listMallSpu(queryParams)
    spuList.value = normalizeRows(res)
    total.value = res.total ?? res.data?.total ?? spuList.value.length
  } finally {
    loading.value = false
  }
}

function resetForm() {
  Object.assign(form, {
    id: undefined,
    name: '',
    subtitle: '',
    categoryId: undefined,
    brandId: undefined,
    mainImage: '',
    detailHtml: '',
    status: 'DRAFT',
    sort: 0,
    remark: '',
    skus: [createSku()]
  })
  clearTemplate()
  formRef.value?.clearValidate()
}

function handleQuery() {
  queryParams.pageNum = 1
  getList()
}

function resetQuery() {
  Object.assign(queryParams, {
    pageNum: 1,
    pageSize: 10,
    name: '',
    categoryId: undefined,
    brandId: undefined,
    status: undefined
  })
  getList()
}

async function handleAdd() {
  resetForm()
  title.value = '新增商品'
  open.value = true
  await loadOptions()
}

async function handleUpdate(row) {
  resetForm()
  await loadOptions()
  const res = await getMallSpu(row.id)
  const detail = res.data || row
  Object.assign(form, {
    ...detail,
    skus: (detail.skus || detail.skuList || []).map(item => ({
      ...createSku(),
      ...item,
      specs: Array.isArray(item.specs) ? item.specs : [],
      stock: item.stockAvailable ?? item.stock ?? 0
    }))
  })
  if (!form.skus.length) {
    form.skus.push(createSku())
  }
  title.value = '修改商品'
  open.value = true
  await loadAttrTemplate(form.categoryId, detail.attrValues || [])
}

function addSkuRow() {
  form.skus.push(createSku())
}

function removeSkuRow(index) {
  if (form.skus.length === 1) {
    ElMessage.warning('至少保留一个 SKU')
    return
  }
  form.skus.splice(index, 1)
}

function buildPayload() {
  return {
    ...form,
    attrValues: buildAttrValues(),
    skus: form.skus.map(item => {
      const payload = {
        id: item.id,
        skuCode: item.skuCode,
        specs: Array.isArray(item.specs) ? item.specs : [],
        specsJson: item.specsJson,
        price: Number(item.price || 0),
        status: item.status,
        remark: item.remark
      }
      // 仅新建 SKU 允许带初始库存；编辑已有 SKU 不传库存
      if (!item.id) {
        payload.stock = Number(item.stock || 0)
      }
      return payload
    })
  }
}

function skuMissingSaleSpecs(sku) {
  if (sku.status === '1') return false
  if (Array.isArray(sku.specs) && sku.specs.length) {
    return saleAttrs.value.some(attr => !sku.specs.some(item => Number(item.attrId) === Number(attr.id)))
  }
  let specs = {}
  try {
    specs = JSON.parse(sku.specsJson || '{}')
  } catch {
    return true
  }
  return saleAttrs.value.some(attr => {
    const v = specs[attr.name]
    return v == null || v === '' || (Array.isArray(v) && !v.length)
  })
}

function openAdjust(row) {
  if (!row.id) {
    ElMessage.warning('请先保存商品后再调整库存')
    return
  }
  Object.assign(adjustForm, {
    skuId: row.id,
    skuCode: row.skuCode,
    quantity: 1,
    reason: 'PURCHASE_IN',
    remark: ''
  })
  adjustOpen.value = true
}

async function submitAdjust() {
  if (!adjustForm.skuId || !adjustForm.quantity) {
    ElMessage.warning('请填写调整数量')
    return
  }
  const outbound = ['MANUAL_OUT', 'DAMAGE'].includes(adjustForm.reason)
  const quantity = outbound ? -Math.abs(Number(adjustForm.quantity)) : Math.abs(Number(adjustForm.quantity))
  adjusting.value = true
  try {
    const res = await adjustMallInventory({
      skuId: adjustForm.skuId,
      quantity,
      reason: adjustForm.reason,
      remark: adjustForm.remark
    })
    const sku = res.data || {}
    const target = form.skus.find(item => item.id === adjustForm.skuId)
    if (target) {
      target.stock = sku.stockAvailable ?? sku.stock ?? target.stock
      target.stockAvailable = sku.stockAvailable
      target.stockLocked = sku.stockLocked
      target.stockTotal = sku.stockTotal
    }
    ElMessage.success('库存调整成功')
    adjustOpen.value = false
  } catch (e) {
    console.error('库存调整失败', e)
  } finally {
    adjusting.value = false
  }
}

async function submitForm() {
  try {
    await formRef.value.validate()
  } catch {
    ElMessage.warning('请完善商品名称和类目（须选叶子类目）')
    return
  }
  if (!form.categoryId) {
    ElMessage.warning('请选择后台叶子类目')
    return
  }
  for (const attr of descAttrs.value) {
    if (attr.required !== '1') continue
    const raw = descValues.value[attr.id]
    const empty = Array.isArray(raw) ? !raw.length : !String(raw ?? '').trim()
    if (empty) {
      ElMessage.warning(`描述属性「${attr.name}」不能为空`)
      return
    }
  }
  if (hasSaleAttrs.value) {
    const needSelect = saleAttrs.value.find(attr => !(saleSelected.value[attr.id] || []).length)
    if (needSelect) {
      ElMessage.warning(`请为销售属性「${needSelect.name}」选择取值，并点击「生成 SKU」`)
      return
    }
    if (form.skus.some(skuMissingSaleSpecs)) {
      ElMessage.warning('SKU 规格未包含销售属性，请点击「生成 SKU」后再保存')
      return
    }
  }
  const invalidSku = form.skus.find(item => {
    if (!item.skuCode || Number(item.price) < 0) return true
    if (!item.id && Number(item.stock) < 0) return true
    return false
  })
  if (invalidSku) {
    ElMessage.warning('请完整填写 SKU 编码和价格（新建 SKU 还需填写初始库存）')
    return
  }
  const specKeys = form.skus
    .filter(item => item.status !== '1')
    .map(item => buildSpecKey(item.specs))
    .filter(Boolean)
  if (specKeys.length && new Set(specKeys).size !== specKeys.length) {
    ElMessage.warning('存在重复规格 SKU，请重新生成后再保存')
    return
  }
  saving.value = true
  try {
    if (form.id) {
      await updateMallSpu(buildPayload())
    } else {
      await addMallSpu(buildPayload())
    }
    ElMessage.success('保存成功')
    open.value = false
    getList()
  } catch (e) {
    // 业务错误已由 request 拦截器提示
    console.error('保存商品失败', e)
  } finally {
    saving.value = false
  }
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确认删除商品“${row.name}”吗？`, '提示', { type: 'warning' })
  await delMallSpu(row.id)
  ElMessage.success('删除成功')
  getList()
}

async function handleChangeStatus(row, status) {
  await updateMallSpuStatus(row.id, status)
  ElMessage.success(status === 'ON' ? '已上架' : '已下架')
  getList()
}

watch(
  () => form.categoryId,
  async (id, prev) => {
    if (!open.value) return
    if (id === prev) return
    // 编辑初次赋值时由 handleUpdate 加载，避免重复
    if (form.id && prev === undefined) return
    await loadAttrTemplate(id, [])
  }
)

onMounted(async () => {
  await loadOptions()
  await getList()
})
</script>

<template>
  <div class="app-container">
    <el-card shadow="never">
      <el-form :inline="true" :model="queryParams" @submit.prevent="handleQuery">
        <el-form-item label="商品名称">
          <el-input v-model="queryParams.name" placeholder="请输入商品名称" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="类目">
          <el-tree-select
            v-model="queryParams.categoryId"
            :data="categoryOptions"
            node-key="id"
            :props="{ label: 'name', children: 'children', disabled: 'disabled' }"
            clearable
            check-strictly
            style="width: 180px"
          />
        </el-form-item>
        <el-form-item label="品牌">
          <el-select v-model="queryParams.brandId" placeholder="全部" clearable filterable style="width: 160px">
            <el-option v-for="item in brandList" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 130px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="上架" value="ON" />
            <el-option label="下架" value="OFF" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-row :gutter="10" class="mb8">
        <el-col :span="1.5">
          <el-button type="primary" plain icon="Plus" v-hasPermi="['mall:spu:add']" @click="handleAdd">
            新增
          </el-button>
        </el-col>
      </el-row>

      <el-table v-loading="loading" :data="spuList" stripe>
        <el-table-column label="主图" width="88" align="center">
          <template #default="{ row }">
            <el-image
              v-if="row.mainImage"
              :src="resolveUploadUrl(row.mainImage)"
              fit="cover"
              class="spu-image"
              :preview-src-list="[resolveUploadUrl(row.mainImage)]"
            />
            <span v-else class="text-muted">无</span>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="商品名称" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="spu-name">{{ row.name }}</div>
            <div v-if="row.subtitle" class="spu-subtitle">{{ row.subtitle }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="categoryName" label="类目" width="140" show-overflow-tooltip />
        <el-table-column prop="brandName" label="品牌" width="120" show-overflow-tooltip />
        <el-table-column label="价格" width="150">
          <template #default="{ row }">{{ priceText(row) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusMap[row.status]?.type || 'info'">
              {{ statusMap[row.status]?.label || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="80" align="center" />
        <el-table-column prop="updateTime" label="更新时间" width="180" />
        <el-table-column label="操作" width="220" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" v-hasPermi="['mall:spu:edit']" @click="handleUpdate(row)">编辑</el-button>
            <el-button
              v-if="row.status !== 'ON'"
              link
              type="success"
              v-hasPermi="['mall:spu:publish']"
              @click="handleChangeStatus(row, 'ON')"
            >
              上架
            </el-button>
            <el-button
              v-else
              link
              type="warning"
              v-hasPermi="['mall:spu:publish']"
              @click="handleChangeStatus(row, 'OFF')"
            >
              下架
            </el-button>
            <el-button link type="danger" v-hasPermi="['mall:spu:remove']" @click="handleDelete(row)">删除</el-button>
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

    <el-dialog v-model="open" :title="title" width="960px" append-to-body>
      <el-form ref="formRef" v-loading="templateLoading" :model="form" :rules="rules" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="商品名称" prop="name">
              <el-input v-model="form.name" placeholder="请输入商品名称" maxlength="128" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="副标题">
              <el-input v-model="form.subtitle" placeholder="请输入副标题" maxlength="255" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="类目" prop="categoryId">
              <el-tree-select
                v-model="form.categoryId"
                :data="categoryOptions"
                node-key="id"
                :props="{ label: 'name', children: 'children', disabled: 'disabled' }"
                check-strictly
                filterable
                placeholder="请选择叶子类目"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="品牌">
              <el-select v-model="form.brandId" placeholder="请选择品牌" clearable filterable style="width: 100%">
                <el-option v-for="item in brandList" :key="item.id" :label="item.name" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-select v-model="form.status" style="width: 100%">
                <el-option label="草稿" value="DRAFT" />
                <el-option label="上架" value="ON" />
                <el-option label="下架" value="OFF" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="排序">
              <el-input-number v-model="form.sort" :min="0" controls-position="right" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="主图">
              <image-upload v-model="form.mainImage" :limit="1" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="详情HTML">
              <el-input v-model="form.detailHtml" type="textarea" :rows="4" placeholder="请输入商品详情 HTML" />
            </el-form-item>
          </el-col>
        </el-row>

        <template v-if="hasDescAttrs">
          <div class="section-header">描述属性</div>
          <el-row :gutter="16">
            <el-col v-for="attr in descAttrs" :key="attr.id" :span="12">
              <el-form-item :label="attr.name" :required="attr.required === '1'">
                <el-input
                  v-if="attr.inputType === 'text' || !attr.inputType"
                  v-model="descValues[attr.id]"
                  :placeholder="`请输入${attr.name}`"
                />
                <el-select
                  v-else-if="attr.inputType === 'select'"
                  v-model="descValues[attr.id]"
                  clearable
                  filterable
                  style="width: 100%"
                  :placeholder="`请选择${attr.name}`"
                >
                  <el-option
                    v-for="opt in optionList(attr)"
                    :key="opt.id || opt.value"
                    :label="opt.value"
                    :value="opt.value"
                  />
                </el-select>
                <el-select
                  v-else
                  v-model="descValues[attr.id]"
                  multiple
                  collapse-tags
                  collapse-tags-tooltip
                  clearable
                  filterable
                  style="width: 100%"
                  :placeholder="`请选择${attr.name}`"
                >
                  <el-option
                    v-for="opt in optionList(attr)"
                    :key="opt.id || opt.value"
                    :label="opt.value"
                    :value="opt.value"
                  />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
        </template>

        <template v-if="hasSaleAttrs">
          <div class="section-header">
            <span>销售属性</span>
            <el-button type="primary" link @click="generateSkusFromSale">生成 SKU</el-button>
          </div>
          <el-row :gutter="16">
            <el-col v-for="attr in saleAttrs" :key="attr.id" :span="12">
              <el-form-item :label="attr.name" required>
                <el-select
                  v-model="saleSelected[attr.id]"
                  multiple
                  collapse-tags
                  collapse-tags-tooltip
                  filterable
                  style="width: 100%"
                  :placeholder="optionList(attr).length ? `请选择${attr.name}（可多选）` : '暂无选项，请先在属性管理维护'"
                >
                  <el-option
                    v-for="opt in optionList(attr)"
                    :key="opt.id || opt.value"
                    :label="opt.value"
                    :value="opt.id"
                  />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
        </template>

        <div class="sku-header">
          <span>SKU 信息</span>
          <div>
            <el-button v-if="!hasSaleAttrs" link type="primary" @click="addSkuRow">新增 SKU</el-button>
            <span v-else class="text-muted">有销售属性时请通过上方选择器生成 SKU</span>
          </div>
        </div>
        <el-table :data="form.skus" border>
          <el-table-column label="SKU编码" min-width="170">
            <template #default="{ row }">
              <el-input v-model="row.skuCode" placeholder="如 DEMO-SKU-BLACK" />
            </template>
          </el-table-column>
          <el-table-column label="规格" min-width="200">
            <template #default="{ row }">
              <span>{{ specsText(row) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="价格" width="150">
            <template #default="{ row }">
              <el-input-number v-model="row.price" :min="0" :precision="2" controls-position="right" />
            </template>
          </el-table-column>
          <el-table-column label="可售库存" width="150">
            <template #default="{ row }">
              <template v-if="row.id">
                <span>{{ stockDisplay(row) }}</span>
                <div class="text-muted">锁定 {{ row.stockLocked ?? 0 }} / 实际 {{ row.stockTotal ?? stockDisplay(row) }}</div>
              </template>
              <el-input-number v-else v-model="row.stock" :min="0" :precision="0" controls-position="right" />
            </template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-select v-model="row.status">
                <el-option label="启用" value="0" />
                <el-option label="停用" value="1" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="140" align="center">
            <template #default="{ row, $index }">
              <el-button v-if="row.id" link type="primary" @click="openAdjust(row)">调库存</el-button>
              <el-button link type="danger" @click="removeSkuRow($index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-form>
      <template #footer>
        <el-button @click="open = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="adjustOpen" title="调整库存" width="480px" append-to-body>
      <el-form label-width="96px">
        <el-form-item label="SKU">
          <span>{{ adjustForm.skuCode }}</span>
        </el-form-item>
        <el-form-item label="原因" required>
          <el-select v-model="adjustForm.reason" style="width: 100%">
            <el-option v-for="item in adjustReasons" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="数量" required>
          <el-input-number v-model="adjustForm.quantity" :min="1" :precision="0" controls-position="right" />
          <div class="text-muted">出库类原因会自动按负数扣减可售库存</div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="adjustForm.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adjustOpen = false">取消</el-button>
        <el-button type="primary" :loading="adjusting" @click="submitAdjust">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.spu-image {
  width: 52px;
  height: 52px;
  border-radius: 6px;
}

.spu-name {
  font-weight: 600;
  color: #303133;
}

.spu-subtitle,
.text-muted {
  color: #909399;
  font-size: 12px;
}

.sku-header,
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 8px 0 12px;
  font-weight: 600;
}

.section-header {
  margin-top: 16px;
  padding-top: 8px;
  border-top: 1px solid #ebeef5;
}
</style>
