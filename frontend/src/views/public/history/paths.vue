<script setup>
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { listPublicHistoryPaths } from '@/api/history/public'

const loading = ref(false)
const paths = ref([])

async function load() {
  loading.value = true
  try {
    const res = await listPublicHistoryPaths({ limit: 50 })
    paths.value = res.data || []
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="paths">
    <header class="head">
      <h1>学习路径</h1>
      <p>按课程节奏完成学习单元，建立事件脉络。</p>
    </header>
    <div v-if="loading" class="empty">加载中…</div>
    <div v-else-if="!paths.length" class="empty">暂无已发布路径</div>
    <ul v-else class="list">
      <li v-for="item in paths" :key="item.id">
        <RouterLink :to="`/history/paths/${item.id}`">
          <h2>{{ item.title }}</h2>
          <p>{{ item.summary || '暂无简介' }}</p>
          <div class="meta">
            <span>{{ item.difficulty || 'BEGINNER' }}</span>
            <span v-if="item.estimatedDays">约 {{ item.estimatedDays }} 天</span>
            <span v-if="item.totalUnitCount != null">{{ item.totalUnitCount }} 个单元</span>
          </div>
        </RouterLink>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.head h1 {
  margin: 0 0 8px;
  font-size: 28px;
  color: #1c1917;
}
.head p {
  margin: 0 0 24px;
  color: #57534e;
}
.list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 14px;
}
.list a {
  display: block;
  padding: 18px 16px;
  text-decoration: none;
  color: inherit;
  border-bottom: 1px solid rgba(120, 113, 108, 0.25);
  transition: background 0.2s ease, transform 0.2s ease;
}
.list a:hover {
  background: rgba(120, 53, 15, 0.04);
  transform: translateX(4px);
}
.list h2 {
  margin: 0 0 8px;
  font-size: 20px;
  color: #78350f;
}
.list p {
  margin: 0 0 10px;
  color: #57534e;
  line-height: 1.6;
}
.meta {
  display: flex;
  gap: 14px;
  font-size: 13px;
  color: #78716c;
}
.empty {
  color: #78716c;
  padding: 24px 0;
}
</style>
