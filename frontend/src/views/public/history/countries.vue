<script setup>
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { listPublicHistoryCountries } from '@/api/history/public'

const loading = ref(false)
const countries = ref([])

const grouped = computed(() => {
  const map = new Map()
  for (const item of countries.value) {
    const key = item.region || '其他'
    if (!map.has(key)) map.set(key, [])
    map.get(key).push(item)
  }
  return [...map.entries()]
})

onMounted(async () => {
  loading.value = true
  try {
    const res = await listPublicHistoryCountries()
    countries.value = res.data || []
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <section class="countries-page">
    <header class="page-head">
      <h1>国家与朝代</h1>
      <p>按国家进入朝代/时期，再跳转到对应时间线。</p>
    </header>
    <div v-if="loading" class="muted">加载中…</div>
    <div v-else-if="!countries.length" class="muted">暂无国家数据</div>
    <div v-else class="regions">
      <section v-for="[region, list] in grouped" :key="region" class="region">
        <h2>{{ region }}</h2>
        <ul class="grid">
          <li v-for="item in list" :key="item.id">
            <RouterLink :to="`/history/countries/${item.id}`">
              <h3>{{ item.name }}</h3>
              <p>{{ item.summary || `按${item.periodLabel || '时期'}组织历史` }}</p>
              <div class="meta">
                <span>{{ item.periodCount || 0 }} 个{{ item.periodLabel || '时期' }}</span>
                <span>{{ item.eventCount || 0 }} 个已发布事件</span>
              </div>
            </RouterLink>
          </li>
        </ul>
      </section>
    </div>
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
.region {
  margin-top: 28px;
}
.region h2 {
  margin: 0 0 12px;
  font-size: 16px;
  letter-spacing: 0.08em;
  color: #a8a29e;
  font-weight: 600;
}
.grid {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 12px;
}
.grid a {
  display: block;
  padding: 16px 4px 18px;
  text-decoration: none;
  color: inherit;
  border-bottom: 1px solid rgba(120, 113, 108, 0.22);
  transition: transform 0.2s ease, background 0.2s ease;
}
.grid a:hover {
  transform: translateX(4px);
  background: rgba(120, 53, 15, 0.04);
}
.grid h3 {
  margin: 0 0 8px;
  font-size: 22px;
  color: #78350f;
}
.grid p {
  margin: 0 0 10px;
  color: #57534e;
  line-height: 1.6;
}
.meta {
  display: flex;
  gap: 16px;
  font-size: 13px;
  color: #78716c;
}
</style>
