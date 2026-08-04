<script setup>
import { RouterLink } from 'vue-router'
</script>

<template>
  <section class="hero">
    <p class="brand">NovaHistory</p>
    <h1>以时间与证据理解中国历史</h1>
    <p class="lead">从秦汉到隋唐的主线事件、人物与地点，配套资料溯源与测验闭环。</p>
    <div class="actions">
      <RouterLink class="btn primary" to="/history/timeline">进入时间线</RouterLink>
      <RouterLink class="btn" to="/index">管理后台</RouterLink>
    </div>
  </section>
</template>

<style scoped>
.hero {
  padding: 48px 8px 24px;
  animation: rise 0.7s ease both;
}

.brand {
  margin: 0 0 12px;
  font-size: clamp(40px, 8vw, 64px);
  font-weight: 800;
  letter-spacing: 0.06em;
  color: #78350f;
  line-height: 1.1;
}

h1 {
  margin: 0 0 14px;
  font-size: clamp(22px, 4vw, 32px);
  font-weight: 600;
  color: #1c1917;
}

.lead {
  margin: 0 0 28px;
  max-width: 36em;
  font-size: 17px;
  line-height: 1.7;
  color: #57534e;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.btn {
  display: inline-flex;
  align-items: center;
  padding: 10px 18px;
  border-radius: 8px;
  text-decoration: none;
  border: 1px solid #a8a29e;
  color: #44403c;
  transition: transform 0.25s ease, background 0.25s ease;
}

.btn:hover {
  transform: translateY(-2px);
}

.btn.primary {
  background: #78350f;
  border-color: #78350f;
  color: #fef3c7;
}

@keyframes rise {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
