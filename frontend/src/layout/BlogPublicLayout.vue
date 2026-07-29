<script setup>
import { onMounted } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'
import { trackPublicVisit } from '@/api/blog/public'

const route = useRoute()

onMounted(() => {
  if (route.path === '/blog' || route.path === '/blog/') {
    trackPublicVisit('HOME').catch(() => {})
  }
})
</script>

<template>
  <div class="blog-layout">
    <header class="blog-header">
      <RouterLink to="/blog" class="logo">AI 技术博客</RouterLink>
      <nav class="nav">
        <RouterLink to="/blog">首页</RouterLink>
        <RouterLink to="/blog/hn">Hacker News</RouterLink>
        <RouterLink to="/index">管理后台</RouterLink>
      </nav>
    </header>
    <main class="blog-main">
      <RouterView />
    </main>
    <footer class="blog-footer">
      <span>AI 技术博客</span>
    </footer>
  </div>
</template>

<style scoped>
.blog-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f8fafc;
}

.blog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  height: 60px;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
}

.logo {
  font-size: 20px;
  font-weight: 700;
  color: #059669;
  text-decoration: none;
}

.nav a {
  margin-left: 20px;
  color: #4b5563;
  text-decoration: none;
}

.nav a.router-link-active {
  color: #059669;
  font-weight: 600;
}

.blog-main {
  flex: 1;
  max-width: 1120px;
  width: 100%;
  margin: 0 auto;
  padding: 24px 16px 48px;
}

.blog-footer {
  text-align: center;
  padding: 16px;
  color: #9ca3af;
  font-size: 13px;
  border-top: 1px solid #e5e7eb;
  background: #fff;
}
</style>
