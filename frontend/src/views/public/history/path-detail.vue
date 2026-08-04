<script setup>
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getPublicHistoryPath } from '@/api/history/public'

const route = useRoute()
const loading = ref(false)
const path = ref(null)

const progressText = computed(() => {
  if (!path.value || path.value.pathProgress == null) return ''
  return `进度 ${path.value.pathProgress}% · 已完成 ${path.value.completedUnitCount || 0}/${path.value.totalUnitCount || 0}`
})

async function load() {
  loading.value = true
  try {
    const res = await getPublicHistoryPath(route.params.id)
    path.value = res.data
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <section v-if="loading" class="empty">加载中…</section>
  <section v-else-if="path" class="detail">
    <RouterLink class="back" to="/history/paths">← 返回路径列表</RouterLink>
    <h1>{{ path.title }}</h1>
    <p class="summary">{{ path.summary }}</p>
    <p v-if="progressText" class="progress">{{ progressText }}</p>
    <ol class="units">
      <li v-for="unit in path.units || []" :key="unit.id">
        <RouterLink :to="`/history/learn/${unit.id}?pathId=${path.id}`">
          <span class="idx">{{ (unit.sort ?? 0) + 1 }}</span>
          <div>
            <h2>{{ unit.title }}</h2>
            <p>{{ unit.oneLiner }}</p>
            <span class="status">{{ unit.completed ? '已完成' : `进度 ${unit.progress || 0}%` }}</span>
          </div>
        </RouterLink>
      </li>
    </ol>
  </section>
</template>

<style scoped>
.back {
  display: inline-block;
  margin-bottom: 16px;
  color: #78350f;
  text-decoration: none;
}
h1 {
  margin: 0 0 10px;
  font-size: 28px;
}
.summary {
  color: #57534e;
  line-height: 1.7;
}
.progress {
  color: #78350f;
  font-size: 14px;
}
.units {
  list-style: none;
  margin: 24px 0 0;
  padding: 0;
  display: grid;
  gap: 12px;
}
.units a {
  display: flex;
  gap: 14px;
  padding: 14px 0;
  text-decoration: none;
  color: inherit;
  border-bottom: 1px solid rgba(120, 113, 108, 0.2);
}
.idx {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #78350f;
  color: #fef3c7;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  font-size: 14px;
}
.units h2 {
  margin: 0 0 6px;
  font-size: 18px;
  color: #1c1917;
}
.units p {
  margin: 0 0 6px;
  color: #57534e;
}
.status {
  font-size: 13px;
  color: #78716c;
}
.empty {
  color: #78716c;
}
</style>
