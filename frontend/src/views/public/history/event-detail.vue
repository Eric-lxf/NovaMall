<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getPublicHistoryEvent } from '@/api/history/public'

const route = useRoute()
const loading = ref(false)
const event = ref(null)
const error = ref('')

function formatYear(year) {
  if (year == null) return '年代不详'
  if (year < 0) return `公元前${Math.abs(year)}年`
  return `${year}年`
}

onMounted(async () => {
  loading.value = true
  try {
    const res = await getPublicHistoryEvent(route.params.id)
    event.value = res.data
  } catch (e) {
    error.value = e?.message || '加载失败'
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <article class="event-detail">
    <div v-if="loading" class="muted">加载中…</div>
    <div v-else-if="error" class="muted">{{ error }}</div>
    <template v-else-if="event">
      <p class="year">{{ formatYear(event.startYear) }}
        <span v-if="event.originalDateText"> · {{ event.originalDateText }}</span>
      </p>
      <h1>{{ event.title }}</h1>
      <p class="summary">{{ event.summary }}</p>
      <section v-if="event.background">
        <h2>背景</h2>
        <p>{{ event.background }}</p>
      </section>
      <section v-if="event.process">
        <h2>过程</h2>
        <p>{{ event.process }}</p>
      </section>
      <section v-if="event.causeAnalysis">
        <h2>原因分析</h2>
        <p>{{ event.causeAnalysis }}</p>
      </section>
      <section v-if="event.impact">
        <h2>结果与影响</h2>
        <p>{{ event.impact }}</p>
      </section>
      <p v-if="event.uncertaintyNote" class="uncertain">不确定性提示：{{ event.uncertaintyNote }}</p>
    </template>
  </article>
</template>

<style scoped>
.year {
  color: #78350f;
  font-weight: 700;
}

h1 {
  margin: 8px 0 16px;
  font-size: 30px;
}

h2 {
  margin: 24px 0 8px;
  font-size: 18px;
}

.summary,
section p {
  line-height: 1.75;
  color: #44403c;
}

.uncertain,
.muted {
  color: #a16207;
  margin-top: 20px;
}
</style>
