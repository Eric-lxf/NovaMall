<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { fetchPublicHnBoardItems } from '@/api/blog/publicHn'

const BOARDS = [
  { name: 'news', label: 'News' },
  { name: 'past', label: 'Past' },
  { name: 'show', label: 'Show' },
  { name: 'jobs', label: 'Jobs' },
]

const loading = ref(false)
const loadingMore = ref(false)
const items = ref([])
const total = ref(0)
const activeBoard = ref('news')

const query = ref({
  pageNum: 1,
  pageSize: 10,
})

const hasMore = computed(() => items.value.length < total.value)

function formatHnTime(value) {
  if (!value) return '—'
  return String(value).slice(0, 16).replace('T', ' ')
}

async function fetchPage({ append } = { append: false }) {
  if (append) {
    if (loadingMore.value || !hasMore.value) return
    loadingMore.value = true
  } else {
    loading.value = true
  }
  try {
    const res = await fetchPublicHnBoardItems(activeBoard.value, query.value)
    const rows = res.rows ?? res.data?.records ?? []
    total.value = res.total ?? res.data?.total ?? 0
    if (append) {
      items.value = items.value.concat(rows)
    } else {
      items.value = rows
    }
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

function resetAndLoad() {
  query.value.pageNum = 1
  return fetchPage({ append: false })
}

function onTabChange(board) {
  if (activeBoard.value === board) return
  activeBoard.value = board
}

function loadMore() {
  if (!hasMore.value || loadingMore.value || loading.value) return
  query.value.pageNum += 1
  fetchPage({ append: true })
}

watch(activeBoard, resetAndLoad)

onMounted(() => {
  resetAndLoad()
})
</script>

<template>
  <div class="hn-home">
    <section class="feed" v-loading="loading">
      <header class="feed-toolbar">
        <div class="tabs" role="tablist">
          <button
            v-for="board in BOARDS"
            :key="board.name"
            type="button"
            role="tab"
            class="tab"
            :class="{ active: activeBoard === board.name }"
            :aria-selected="activeBoard === board.name"
            @click="onTabChange(board.name)"
          >
            {{ board.label }}
          </button>
        </div>
      </header>

      <el-empty v-if="!loading && items.length === 0" description="暂无已发布内容" />

      <div v-else class="item-list">
        <article v-for="item in items" :key="item.hnId" class="hn-item">
          <div class="body">
            <h2 class="title">
              <RouterLink :to="`/blog/hn/${item.hnId}`">
                <span v-if="item.rank != null" class="rank">{{ item.rank }}.</span>
                {{ item.titleZh || item.titleEn || '无标题' }}
              </RouterLink>
            </h2>
            <p class="summary">{{ item.summaryZh || '暂无摘要' }}</p>
            <div class="meta">
              <span>{{ item.score ?? 0 }} 分</span>
              <span v-if="item.author">{{ item.author }}</span>
              <span>{{ formatHnTime(item.hnTime) }}</span>
            </div>
          </div>
        </article>
      </div>

      <div v-if="hasMore" class="load-more">
        <el-button :loading="loadingMore" :disabled="loading" @click="loadMore">
          加载更多
        </el-button>
      </div>
      <p v-else-if="!loading && items.length > 0" class="end-hint">没有更多了</p>

      <p class="footnote">
        内容来源于
        <a href="https://news.ycombinator.com/" target="_blank" rel="noopener noreferrer">Hacker News</a>；
        Past 榜单基于 HN beststories（精选/热门），非站点日历 Past。
      </p>
    </section>
  </div>
</template>

<style scoped>
.hn-home {
  display: flex;
  justify-content: center;
}

.feed {
  flex: 1;
  min-width: 0;
  max-width: 800px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 8px 20px 24px;
}

.feed-toolbar {
  padding: 12px 0 8px;
  border-bottom: 1px solid #e5e7eb;
  margin-bottom: 4px;
}

.tabs {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.tab {
  border: none;
  background: transparent;
  padding: 8px 14px;
  font-size: 15px;
  color: #6b7280;
  cursor: pointer;
  border-bottom: 2px solid transparent;
  margin-bottom: -9px;
}

.tab:hover {
  color: #0f172a;
}

.tab.active {
  color: #047857;
  font-weight: 700;
  border-bottom-color: #059669;
}

.hn-item {
  padding: 18px 0;
  border-bottom: 1px solid #e5e7eb;
}

.body {
  min-width: 0;
}

.title {
  margin: 0 0 8px;
  font-size: 18px;
  font-weight: 700;
  line-height: 1.4;
}

.title a {
  color: #0f172a;
  text-decoration: none;
}

.title a:hover {
  color: #047857;
}

.rank {
  color: #9ca3af;
  font-weight: 600;
  margin-right: 4px;
}

.summary {
  margin: 0 0 10px;
  color: #6b7280;
  font-size: 14px;
  line-height: 1.65;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 13px;
  color: #9ca3af;
}

.load-more {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}

.end-hint {
  text-align: center;
  margin: 20px 0 0;
  font-size: 13px;
  color: #9ca3af;
}

.footnote {
  margin: 24px 0 0;
  padding-top: 16px;
  border-top: 1px solid #f3f4f6;
  font-size: 12px;
  color: #9ca3af;
  line-height: 1.6;
}

.footnote a {
  color: #059669;
  text-decoration: none;
}

.footnote a:hover {
  text-decoration: underline;
}

@media (max-width: 640px) {
  .feed {
    padding: 8px 14px 20px;
  }
}
</style>
