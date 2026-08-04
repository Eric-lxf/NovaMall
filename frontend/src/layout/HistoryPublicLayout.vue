<script setup>
import { RouterLink, RouterView } from 'vue-router'
</script>

<template>
  <div class="history-layout">
    <header class="history-header">
      <RouterLink to="/history" class="logo">
        <span class="logo-mark">史</span>
        <span>NovaHistory</span>
      </RouterLink>
      <nav class="nav">
        <RouterLink to="/history">首页</RouterLink>
        <RouterLink to="/history/timeline">时间线</RouterLink>
        <RouterLink to="/history/paths">学习路径</RouterLink>
        <RouterLink to="/index">管理后台</RouterLink>
      </nav>
    </header>
    <main class="history-main">
      <RouterView />
    </main>
    <footer class="history-footer">
      <span>NovaHistory · 时间、空间、事件因果与来源证据</span>
    </footer>
  </div>
</template>

<style scoped>
.history-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background:
    radial-gradient(ellipse 80% 50% at 20% -10%, rgba(180, 83, 9, 0.12), transparent),
    linear-gradient(180deg, #f8f4ef 0%, #f1ebe3 100%);
  color: #1c1917;
  font-family: "Source Han Serif SC", "Noto Serif SC", "Songti SC", Georgia, serif;
}

.history-header {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 28px;
  height: 64px;
  background: rgba(248, 244, 239, 0.92);
  border-bottom: 1px solid rgba(120, 113, 108, 0.25);
  backdrop-filter: blur(10px);
}

.logo {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  font-size: 22px;
  font-weight: 700;
  color: #292524;
  text-decoration: none;
  letter-spacing: 0.04em;
}

.logo-mark {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border-radius: 8px;
  background: #78350f;
  color: #fef3c7;
  font-size: 16px;
}

.nav {
  display: flex;
  gap: 20px;
}

.nav a {
  color: #57534e;
  text-decoration: none;
  font-size: 15px;
}

.nav a.router-link-active {
  color: #78350f;
  font-weight: 600;
}

.history-main {
  flex: 1;
  width: min(960px, calc(100% - 32px));
  margin: 0 auto;
  padding: 28px 0 48px;
}

.history-footer {
  padding: 20px;
  text-align: center;
  color: #78716c;
  font-size: 13px;
  border-top: 1px solid rgba(120, 113, 108, 0.2);
}
</style>
