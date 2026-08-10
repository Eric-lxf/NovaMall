<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listPublicMallCategory, listPublicMallSpu } from '@/api/mall/public'
import { resolveUploadUrl } from '@/utils/blogAssets'

const router = useRouter()
const loading = ref(false)
const categories = ref([])
const products = ref([])
const keyword = ref('')
const query = reactive({
  pageNum: 1,
  pageSize: 8,
  status: 'ON'
})

const featured = computed(() => products.value.slice(0, 4))

function normalizeRows(res) {
  return res.rows || res.data?.records || res.data || []
}

/** 热门类目：取前台树一级节点（无 children 时退回扁平列表前若干项） */
function pickHomeCategories(rows) {
  if (!rows?.length) return []
  if (rows.some(item => Array.isArray(item.children))) {
    return rows.slice(0, 8)
  }
  return rows.slice(0, 8)
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
  return `¥${min} 起`
}

function goList(categoryId) {
  router.push({ path: '/mall/list', query: categoryId ? { categoryId } : {} })
}

function search() {
  router.push({ path: '/mall/list', query: { keyword: keyword.value.trim() } })
}

async function loadData() {
  loading.value = true
  try {
    const [categoryRes, productRes] = await Promise.all([
      listPublicMallCategory({ status: '0' }),
      listPublicMallSpu(query)
    ])
    categories.value = pickHomeCategories(normalizeRows(categoryRes))
    products.value = normalizeRows(productRes)
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<template>
  <div class="mall-home" v-loading="loading">
    <section class="hero">
      <div>
        <p class="eyebrow">NovaMall Phase 1</p>
        <h1>发现 NovaMall 精选好物</h1>
        <p class="hero-desc">从商品浏览、购物车、下单到 MOCK 支付，完整串起商城 C 端主流程。</p>
        <form class="search" @submit.prevent="search">
          <el-input v-model="keyword" size="large" placeholder="搜索商品名称" clearable />
          <el-button type="primary" size="large" native-type="submit">搜索</el-button>
        </form>
      </div>
      <div class="hero-card">
        <span>今日推荐</span>
        <strong>{{ featured[0]?.name || '无线耳机' }}</strong>
        <p>{{ featured[0]?.subtitle || '品质生活，从 NovaMall 开始' }}</p>
      </div>
    </section>

    <section class="section">
      <div class="section-title">
        <h2>热门类目</h2>
        <el-button link type="primary" @click="goList()">全部商品</el-button>
      </div>
      <div class="category-grid">
        <button v-for="item in categories" :key="item.id" type="button" class="category-card" @click="goList(item.id)">
          <span>{{ item.name }}</span>
          <small>立即选购</small>
        </button>
      </div>
    </section>

    <section class="section">
      <div class="section-title">
        <h2>精选商品</h2>
        <RouterLink to="/mall/list">查看更多</RouterLink>
      </div>
      <el-empty v-if="!loading && products.length === 0" description="暂无上架商品" />
      <div v-else class="product-grid">
        <RouterLink v-for="item in products" :key="item.id" :to="`/mall/detail/${item.id}`" class="product-card">
          <div class="image-wrap">
            <img v-if="productImage(item)" :src="productImage(item)" alt="" />
            <span v-else>NovaMall</span>
          </div>
          <h3>{{ item.name }}</h3>
          <p>{{ item.subtitle || item.categoryName || '精选好物' }}</p>
          <strong>{{ priceText(item) }}</strong>
        </RouterLink>
      </div>
    </section>
  </div>
</template>

<style scoped>
.hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 24px;
  padding: 42px;
  color: #fff;
  border-radius: 22px;
  background: linear-gradient(135deg, #1d4ed8, #7c3aed);
}

.eyebrow {
  margin: 0 0 10px;
  opacity: 0.85;
  font-weight: 700;
}

.hero h1 {
  margin: 0;
  font-size: 42px;
}

.hero-desc {
  max-width: 580px;
  color: #dbeafe;
  font-size: 16px;
}

.search {
  display: flex;
  gap: 10px;
  max-width: 500px;
  margin-top: 24px;
}

.hero-card {
  align-self: stretch;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  padding: 24px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.14);
  border: 1px solid rgba(255, 255, 255, 0.22);
}

.hero-card strong {
  margin: 12px 0 8px;
  font-size: 24px;
}

.section {
  margin-top: 28px;
}

.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.section-title h2 {
  margin: 0;
}

.category-grid,
.product-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.category-card,
.product-card {
  border: 1px solid #e5e7eb;
  border-radius: 16px;
  background: #fff;
  text-decoration: none;
}

.category-card {
  padding: 20px;
  text-align: left;
  cursor: pointer;
}

.category-card span {
  display: block;
  color: #111827;
  font-weight: 700;
}

.category-card small {
  color: #6b7280;
}

.product-card {
  display: block;
  overflow: hidden;
  color: inherit;
}

.image-wrap {
  height: 180px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #94a3b8;
  background: #eef2ff;
}

.image-wrap img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.product-card h3,
.product-card p,
.product-card strong {
  display: block;
  margin: 10px 14px;
}

.product-card p {
  color: #6b7280;
  min-height: 20px;
}

.product-card strong {
  color: #dc2626;
  font-size: 18px;
  margin-bottom: 16px;
}

@media (max-width: 900px) {
  .hero,
  .category-grid,
  .product-grid {
    grid-template-columns: 1fr;
  }
}
</style>
