<script setup>
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getPublicHistoryUnit, saveHistoryProgress } from '@/api/history/public'
import { getToken } from '@/utils/auth'

const route = useRoute()
const loading = ref(false)
const saving = ref(false)
const unit = ref(null)

const pathId = computed(() => {
  const raw = route.query.pathId
  if (!raw) return undefined
  const n = Number(raw)
  return Number.isFinite(n) ? n : undefined
})

const sections = computed(() => {
  const c = unit.value?.content || {}
  return [
    { title: '时间与地点', text: c.timePlace },
    { title: '背景', text: c.background },
    { title: '关键人物', text: c.keyPeople },
    { title: '事件过程', text: c.process },
    { title: '原因分析', text: c.causeAnalysis },
    { title: '结果与长期影响', text: c.impact },
    { title: '来源与不同观点', text: c.sourcesAndViews },
    { title: '章节练习', text: c.practiceHint },
    { title: '延伸学习', text: c.furtherReading }
  ].filter((s) => s.text)
})

async function load() {
  loading.value = true
  try {
    const res = await getPublicHistoryUnit(route.params.id)
    unit.value = res.data
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function markComplete() {
  if (!getToken()) {
    ElMessage.warning('请先登录管理后台账号后再记录进度')
    return
  }
  saving.value = true
  try {
    await saveHistoryProgress({
      pathId: pathId.value,
      unitId: Number(route.params.id),
      progress: 100,
      completed: true
    })
    ElMessage.success('已标记完成本单元')
  } catch (e) {
    ElMessage.error(e.message || '保存进度失败')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <section v-if="loading" class="empty">加载中…</section>
  <article v-else-if="unit" class="unit">
    <RouterLink v-if="pathId" class="back" :to="`/history/paths/${pathId}`">← 返回路径</RouterLink>
    <RouterLink v-else class="back" to="/history/paths">← 学习路径</RouterLink>
    <p class="brand-line">学习单元</p>
    <h1>{{ unit.title }}</h1>
    <p class="one-liner">{{ unit.oneLiner }}</p>
    <section v-if="unit.objectives" class="block">
      <h2>学习目标</h2>
      <p>{{ unit.objectives }}</p>
    </section>
    <section v-if="unit.prerequisites" class="block">
      <h2>前置知识</h2>
      <p>{{ unit.prerequisites }}</p>
    </section>
    <section v-for="sec in sections" :key="sec.title" class="block">
      <h2>{{ sec.title }}</h2>
      <p>{{ sec.text }}</p>
    </section>
    <div class="actions">
      <button class="btn" :disabled="saving" @click="markComplete">标记完成</button>
      <RouterLink v-if="unit.eventId" class="link" :to="`/history/events/${unit.eventId}`">查看关联事件</RouterLink>
    </div>
  </article>
</template>

<style scoped>
.back {
  display: inline-block;
  margin-bottom: 12px;
  color: #78350f;
  text-decoration: none;
}
.brand-line {
  margin: 0;
  color: #a8a29e;
  letter-spacing: 0.08em;
  font-size: 13px;
}
h1 {
  margin: 8px 0 12px;
  font-size: clamp(24px, 4vw, 34px);
}
.one-liner {
  margin: 0 0 24px;
  font-size: 17px;
  color: #57534e;
  line-height: 1.7;
}
.block {
  margin-bottom: 20px;
}
.block h2 {
  margin: 0 0 8px;
  font-size: 18px;
  color: #78350f;
}
.block p {
  margin: 0;
  white-space: pre-wrap;
  line-height: 1.75;
  color: #292524;
}
.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  align-items: center;
  margin-top: 28px;
}
.btn {
  border: none;
  background: #78350f;
  color: #fef3c7;
  padding: 10px 18px;
  border-radius: 8px;
  cursor: pointer;
}
.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.link {
  color: #78350f;
}
.empty {
  color: #78716c;
}
</style>
