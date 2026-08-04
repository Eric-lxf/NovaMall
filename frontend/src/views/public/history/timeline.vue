<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import {
  getHistoryTimeline,
  getPublicHistoryCountry,
  listPublicHistoryCountries
} from '@/api/history/public'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const items = ref([])
const countries = ref([])
const periods = ref([])
const filters = reactive({
  countryId: undefined,
  periodId: undefined
})

function formatYear(year) {
  if (year == null) return '年代不详'
  if (year < 0) return `公元前${Math.abs(year)}年`
  return `${year}年`
}

function syncFiltersFromRoute() {
  const countryId = route.query.countryId ? Number(route.query.countryId) : undefined
  const periodId = route.query.periodId ? Number(route.query.periodId) : undefined
  filters.countryId = Number.isFinite(countryId) ? countryId : undefined
  filters.periodId = Number.isFinite(periodId) ? periodId : undefined
}

async function loadCountries() {
  const res = await listPublicHistoryCountries()
  countries.value = res.data || []
}

async function loadPeriods() {
  if (!filters.countryId) {
    periods.value = []
    return
  }
  const res = await getPublicHistoryCountry(filters.countryId)
  periods.value = res.data?.periods || []
}

async function loadTimeline() {
  loading.value = true
  try {
    const params = { limit: 100 }
    if (filters.periodId) params.periodId = filters.periodId
    else if (filters.countryId) params.countryId = filters.countryId
    const res = await getHistoryTimeline(params)
    items.value = res.data || []
  } finally {
    loading.value = false
  }
}

function applyFilters() {
  const query = {}
  if (filters.countryId) query.countryId = String(filters.countryId)
  if (filters.periodId) query.periodId = String(filters.periodId)
  router.replace({ path: '/history/timeline', query })
}

function onCountryChange() {
  filters.periodId = undefined
  applyFilters()
}

function resetFilters() {
  filters.countryId = undefined
  filters.periodId = undefined
  applyFilters()
}

watch(
  () => route.query,
  async () => {
    syncFiltersFromRoute()
    await loadPeriods()
    await loadTimeline()
  }
)

onMounted(async () => {
  syncFiltersFromRoute()
  await loadCountries()
  await loadPeriods()
  await loadTimeline()
})
</script>

<template>
  <section class="timeline-page">
    <header class="page-head">
      <h1>历史时间线</h1>
      <p>仅展示已审核发布的事件，可按国家与朝代/时期筛选。</p>
    </header>

    <div class="filters">
      <label>
        <span>国家</span>
        <select v-model="filters.countryId" @change="onCountryChange">
          <option :value="undefined">全部</option>
          <option v-for="c in countries" :key="c.id" :value="c.id">{{ c.name }}</option>
        </select>
      </label>
      <label>
        <span>朝代/时期</span>
        <select v-model="filters.periodId" :disabled="!filters.countryId" @change="applyFilters">
          <option :value="undefined">全部</option>
          <option v-for="p in periods" :key="p.id" :value="p.id">{{ p.name }}</option>
        </select>
      </label>
      <button type="button" class="reset" @click="resetFilters">清空</button>
    </div>

    <div v-if="loading" class="muted">加载中…</div>
    <ol v-else class="timeline">
      <li v-for="item in items" :key="item.id" class="item">
        <div class="year">{{ formatYear(item.startYear) }}</div>
        <div class="card-body">
          <RouterLink class="title" :to="`/history/events/${item.id}`">{{ item.title }}</RouterLink>
          <p class="meta">
            <span v-if="item.countryName">{{ item.countryName }}</span>
            <span v-if="item.periodName"> · {{ item.periodName }}</span>
            <span v-if="item.placeName"> · {{ item.placeName }}</span>
            <span v-if="item.originalDateText"> · {{ item.originalDateText }}</span>
          </p>
          <p class="summary">{{ item.summary }}</p>
          <p v-if="item.uncertaintyNote" class="uncertain">不确定：{{ item.uncertaintyNote }}</p>
        </div>
      </li>
      <li v-if="!items.length" class="muted">暂无已发布事件，请先在管理端维护并发布。</li>
    </ol>
  </section>
</template>

<style scoped>
.page-head h1 {
  margin: 0 0 8px;
  font-size: 28px;
}

.page-head p,
.muted {
  color: #78716c;
}

.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: end;
  margin: 20px 0 8px;
}

.filters label {
  display: grid;
  gap: 6px;
  font-size: 13px;
  color: #57534e;
}

.filters select {
  min-width: 160px;
  padding: 8px 10px;
  border: 1px solid #d6d3d1;
  border-radius: 8px;
  background: #fffaf5;
  color: #1c1917;
}

.reset {
  padding: 8px 14px;
  border: 1px solid #a8a29e;
  border-radius: 8px;
  background: transparent;
  color: #44403c;
  cursor: pointer;
}

.timeline {
  list-style: none;
  margin: 28px 0 0;
  padding: 0;
  border-left: 2px solid #d6d3d1;
}

.item {
  position: relative;
  display: grid;
  grid-template-columns: 120px 1fr;
  gap: 16px;
  padding: 0 0 28px 24px;
  animation: fade-in 0.5s ease both;
}

.item::before {
  content: "";
  position: absolute;
  left: -6px;
  top: 8px;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #b45309;
}

.year {
  font-weight: 700;
  color: #78350f;
}

.title {
  font-size: 20px;
  font-weight: 700;
  color: #1c1917;
  text-decoration: none;
}

.title:hover {
  color: #b45309;
}

.meta,
.summary,
.uncertain {
  margin: 8px 0 0;
  line-height: 1.6;
  color: #57534e;
}

.uncertain {
  color: #a16207;
  font-size: 14px;
}

@keyframes fade-in {
  from {
    opacity: 0;
    transform: translateX(-8px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

@media (max-width: 640px) {
  .item {
    grid-template-columns: 1fr;
  }
}
</style>
