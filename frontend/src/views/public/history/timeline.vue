<script setup>
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { getHistoryTimeline } from '@/api/history/public'

const loading = ref(false)
const items = ref([])

function formatYear(year) {
  if (year == null) return '年代不详'
  if (year < 0) return `公元前${Math.abs(year)}年`
  return `${year}年`
}

onMounted(async () => {
  loading.value = true
  try {
    const res = await getHistoryTimeline({ limit: 100 })
    items.value = res.data || []
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <section class="timeline-page">
    <header class="page-head">
      <h1>历史时间线</h1>
      <p>仅展示已审核发布的事件，按起始年排序。</p>
    </header>
    <div v-if="loading" class="muted">加载中…</div>
    <ol v-else class="timeline">
      <li v-for="item in items" :key="item.id" class="item">
        <div class="year">{{ formatYear(item.startYear) }}</div>
        <div class="card-body">
          <RouterLink class="title" :to="`/history/events/${item.id}`">{{ item.title }}</RouterLink>
          <p class="meta">
            <span v-if="item.periodName">{{ item.periodName }}</span>
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
