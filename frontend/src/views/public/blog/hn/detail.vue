<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchPublicHnItem } from '@/api/blog/publicHn'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const item = ref(null)

const hnId = computed(() => Number(route.params.hnId))

function formatHnTime(value) {
  if (!value) return '—'
  return String(value).slice(0, 16).replace('T', ' ')
}

async function loadItem() {
  if (!hnId.value || Number.isNaN(hnId.value)) {
    item.value = null
    return
  }
  loading.value = true
  try {
    const res = await fetchPublicHnItem(hnId.value)
    item.value = res.data || null
  } catch {
    item.value = null
  } finally {
    loading.value = false
  }
}

watch(hnId, loadItem, { immediate: true })
</script>

<template>
  <div v-loading="loading" class="hn-detail-page">
    <el-button link type="primary" @click="router.push('/blog/hn')">← 返回列表</el-button>

    <el-empty v-if="!loading && !item" description="内容不存在或未发布" />

    <article v-if="item" class="hn-detail">
      <h1 class="title">{{ item.titleZh || item.titleEn || '无标题' }}</h1>

      <p v-if="item.summaryZh" class="summary">{{ item.summaryZh }}</p>

      <div v-if="item.textZh" class="text-body">
        <p>{{ item.textZh }}</p>
      </div>

      <div class="original">
        <h2 class="section-label">原文信息</h2>
        <p v-if="item.titleEn" class="title-en">{{ item.titleEn }}</p>
        <div class="links">
          <a v-if="item.url" :href="item.url" target="_blank" rel="noopener noreferrer">访问原文</a>
          <a v-if="item.hnUrl" :href="item.hnUrl" target="_blank" rel="noopener noreferrer">Hacker News 讨论</a>
        </div>
      </div>

      <div class="meta">
        <span>{{ item.score ?? 0 }} 分</span>
        <span v-if="item.author">{{ item.author }}</span>
        <span>{{ formatHnTime(item.hnTime) }}</span>
      </div>
    </article>
  </div>
</template>

<style scoped>
.hn-detail-page {
  max-width: 800px;
  margin: 0 auto;
}

.hn-detail {
  margin-top: 16px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 24px;
}

.title {
  margin: 0 0 16px;
  font-size: 28px;
  line-height: 1.35;
  font-weight: 800;
  color: #0f172a;
  letter-spacing: -0.02em;
}

.summary {
  margin: 0 0 20px;
  color: #4b5563;
  font-size: 16px;
  line-height: 1.7;
}

.text-body {
  margin-bottom: 24px;
  color: #374151;
  font-size: 15px;
  line-height: 1.75;
  white-space: pre-wrap;
}

.original {
  margin-bottom: 20px;
  padding: 16px;
  background: #f8fafc;
  border-radius: 8px;
}

.section-label {
  margin: 0 0 8px;
  font-size: 13px;
  font-weight: 600;
  color: #6b7280;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.title-en {
  margin: 0 0 12px;
  font-size: 15px;
  color: #374151;
  line-height: 1.5;
}

.links {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}

.links a {
  color: #059669;
  font-size: 14px;
  text-decoration: none;
}

.links a:hover {
  text-decoration: underline;
}

.meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  color: #9ca3af;
  font-size: 14px;
  padding-top: 16px;
  border-top: 1px solid #e5e7eb;
}
</style>
