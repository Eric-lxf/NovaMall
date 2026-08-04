<script setup>
import { onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getPublicHistoryCountry } from '@/api/history/public'

const route = useRoute()
const loading = ref(false)
const country = ref(null)

function formatYear(year) {
  if (year == null) return '?'
  if (year < 0) return `前${Math.abs(year)}`
  return `${year}`
}

onMounted(async () => {
  loading.value = true
  try {
    const res = await getPublicHistoryCountry(route.params.id)
    country.value = res.data
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <section v-if="loading" class="muted">加载中…</section>
  <section v-else-if="country" class="detail">
    <RouterLink class="back" to="/history/countries">← 返回国家列表</RouterLink>
    <p class="region">{{ country.region || '世界史' }}</p>
    <h1>{{ country.name }}</h1>
    <p class="summary">{{ country.summary }}</p>
    <p class="hint">以下按「{{ country.periodLabel || '时期' }}」排列，点击可查看该段时间线。</p>
    <ol class="periods">
      <li v-for="period in country.periods || []" :key="period.id">
        <RouterLink :to="`/history/timeline?countryId=${country.id}&periodId=${period.id}`">
          <div class="years">{{ formatYear(period.startYear) }} – {{ formatYear(period.endYear) }}</div>
          <div>
            <h2>{{ period.name }}</h2>
            <p v-if="period.alias" class="alias">{{ period.alias }}</p>
            <p class="desc">{{ period.summary || '暂无简介' }}</p>
            <span class="count">{{ period.eventCount || 0 }} 个已发布事件</span>
          </div>
        </RouterLink>
      </li>
      <li v-if="!(country.periods || []).length" class="muted">暂无{{ country.periodLabel || '时期' }}</li>
    </ol>
  </section>
</template>

<style scoped>
.back {
  display: inline-block;
  margin-bottom: 14px;
  color: #78350f;
  text-decoration: none;
}
.region {
  margin: 0;
  color: #a8a29e;
  letter-spacing: 0.08em;
  font-size: 13px;
}
h1 {
  margin: 8px 0 12px;
  font-size: clamp(26px, 4vw, 36px);
}
.summary,
.hint,
.muted {
  color: #57534e;
  line-height: 1.7;
}
.hint {
  font-size: 14px;
  color: #78716c;
}
.periods {
  list-style: none;
  margin: 28px 0 0;
  padding: 0;
  display: grid;
  gap: 12px;
}
.periods a {
  display: grid;
  grid-template-columns: 140px 1fr;
  gap: 14px;
  padding: 14px 0;
  text-decoration: none;
  color: inherit;
  border-bottom: 1px solid rgba(120, 113, 108, 0.2);
}
.years {
  font-weight: 700;
  color: #78350f;
}
.periods h2 {
  margin: 0 0 6px;
  font-size: 20px;
  color: #1c1917;
}
.alias,
.desc {
  margin: 0 0 6px;
  color: #57534e;
  line-height: 1.6;
}
.count {
  font-size: 13px;
  color: #78716c;
}
@media (max-width: 640px) {
  .periods a {
    grid-template-columns: 1fr;
  }
}
</style>
