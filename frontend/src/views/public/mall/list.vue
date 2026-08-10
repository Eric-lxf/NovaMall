<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listPublicMallCategory, listPublicMallSpu } from '@/api/mall/public'
import { resolveUploadUrl } from '@/utils/blogAssets'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const categories = ref([])
const products = ref([])
const total = ref(0)
const query = reactive({
  pageNum: 1,
  pageSize: 12,
  keyword: '',
  categoryId: undefined,
  sort: 'latest',
  status: 'ON'
})

function normalizeRows(res) {
  return res.rows || res.data?.records || res.data || []
}

/** 前台类目树 → 侧栏扁平列表（保留层级缩进） */
function flattenCategoryTree(nodes, depth = 0) {
  const result = []
  for (const node of nodes || []) {
    result.push({ id: node.id, name: node.name, depth })
    if (node.children?.length) {
      result.push(...flattenCategoryTree(node.children, depth + 1))
    }
  }
  return result
}

function syncRouteQuery() {
  query.keyword = route.query.keyword || ''
  query.categoryId = route.query.categoryId ? Number(route.query.categoryId) : undefined
}

function productImage(item) {
  return resolveUploadUrl(item.mainImage || item.image || item.images?.[0]?.url || '')
}

function priceText(item) {
  if (item.minPrice != null) {
    const min = Number(item.minPrice).toFixed(2)
    const max = Number(item.maxPrice ?? item.minPrice).toFixed(2)
    return min === max ? `¥${min}` : `¥${min} 起`
  }
  const skus = item.skus || item.skuList || []
  if (!skus.length) return '价格待定'
  const prices = skus.map(sku => Number(sku.price || 0))
  const min = Math.min(...prices).toFixed(2)
  const max = Math.max(...prices).toFixed(2)
  return min === max ? `¥${min}` : `¥${min} 起`
}

async function loadCategories() {
  const res = await listPublicMallCategory({ status: '0' })
  const rows = normalizeRows(res)
  categories.value = flattenCategoryTree(rows)
}

async function getList() {
  loading.value = true
  try {
    const res = await listPublicMallSpu(query)
    products.value = normalizeRows(res)
    total.value = res.total ?? res.data?.total ?? products.value.length
  } finally {
    loading.value = false
  }
}

function updateRoute() {
  router.replace({
    path: '/mall/list',
    query: {
      ...(query.keyword ? { keyword: query.keyword } : {}),
      ...(query.categoryId ? { categoryId: query.categoryId } : {})
    }
  })
}

function handleSearch() {
  query.pageNum = 1
  updateRoute()
  getList()
}

function chooseCategory(id) {
  query.categoryId = id
  handleSearch()
}

watch(() => route.query, () => {
  syncRouteQuery()
  query.pageNum = 1
  getList()
})

onMounted(async () => {
  syncRouteQuery()
  await loadCategories()
  await getList()
})
</script>

<template>
  <div class="mall-list">
    <aside class="filters">
      <h3>商品类目</h3>
      <button type="button" :class="{ active: !query.categoryId }" @click="chooseCategory(undefined)">全部商品</button>
      <button
        v-for="item in categories"
        :key="item.id"
        type="button"
        :class="{ active: query.categoryId === item.id }"
        :style="{ paddingLeft: `${12 + item.depth * 14}px` }"
        @click="chooseCategory(item.id)"
      >
        {{ item.name }}
      </button>
    </aside>

    <section class="content" v-loading="loading">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="搜索商品" clearable @keyup.enter="handleSearch" @clear="handleSearch" />
        <el-select v-model="query.sort" style="width: 140px" @change="handleSearch">
          <el-option label="最新上架" value="latest" />
          <el-option label="价格优先" value="price" />
        </el-select>
        <el-button type="primary" @click="handleSearch">搜索</el-button>
      </div>

      <el-empty v-if="!loading && products.length === 0" description="暂无上架商品" />
      <div v-else class="product-grid">
        <RouterLink v-for="item in products" :key="item.id" :to="`/mall/detail/${item.id}`" class="product-card">
          <div class="image-wrap">
            <img v-if="productImage(item)" :src="productImage(item)" alt="" />
            <span v-else>NovaMall</span>
          </div>
          <div class="product-info">
            <h3>{{ item.name }}</h3>
            <p>{{ item.subtitle || item.categoryName || '精选商品' }}</p>
            <strong>{{ priceText(item) }}</strong>
          </div>
        </RouterLink>
      </div>

      <div class="pager" v-if="total > 0">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="getList"
        />
      </div>
    </section>
  </div>
</template>

<style scoped>
.mall-list {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 22px;
}

.filters,
.content {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 16px;
}

.filters {
  position: sticky;
  top: 86px;
  align-self: start;
  padding: 16px;
}

.filters h3 {
  margin: 0 0 12px;
}

.filters button {
  display: block;
  width: 100%;
  padding: 10px 12px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  text-align: left;
  cursor: pointer;
  color: #4b5563;
}

.filters button.active,
.filters button:hover {
  color: #2563eb;
  background: #eff6ff;
  font-weight: 700;
}

.content {
  padding: 18px;
}

.toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 18px;
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.product-card {
  overflow: hidden;
  border: 1px solid #e5e7eb;
  border-radius: 14px;
  color: inherit;
  text-decoration: none;
}

.image-wrap {
  height: 180px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #94a3b8;
  background: #f1f5f9;
}

.image-wrap img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.product-info {
  padding: 12px 14px 16px;
}

.product-info h3 {
  margin: 0 0 8px;
}

.product-info p {
  margin: 0 0 12px;
  color: #6b7280;
}

.product-info strong {
  color: #dc2626;
  font-size: 18px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}

@media (max-width: 900px) {
  .mall-list {
    grid-template-columns: 1fr;
  }

  .filters {
    position: static;
  }

  .product-grid {
    grid-template-columns: 1fr;
  }
}
</style>
