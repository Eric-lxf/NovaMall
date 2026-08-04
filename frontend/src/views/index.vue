<template>
  <div class="app-container home">
    <el-row :gutter="20">
      <el-col :span="24">
        <h2 class="home-title">AI 博客管理后台</h2>
        <p class="home-desc">
          统一管理文章、评论、账单识别与 AI 能力。请从左侧菜单进入各业务模块。
        </p>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="mt16">
      <el-col :xs="24" :sm="12" :md="8">
        <el-card shadow="hover" class="entry-card" @click="openPublicBlog">
          <div class="entry-title">博客前台</div>
          <div class="entry-hint">公开文章列表与详情</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="8">
        <el-card shadow="hover" class="entry-card" @click="goPath('/blog-admin/article')">
          <div class="entry-title">文章管理</div>
          <div class="entry-hint">撰写、发布与编辑文章</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="8">
        <el-card shadow="hover" class="entry-card" @click="goPath('/bill/list')">
          <div class="entry-title">账单明细</div>
          <div class="entry-hint">上传识别微信/银行账单</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="8">
        <el-card shadow="hover" class="entry-card" @click="openPublicHistory">
          <div class="entry-title">历史学习</div>
          <div class="entry-hint">时间线、事件与知识审核</div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup name="Index">
const router = useRouter()

function openPublicBlog() {
  window.open('/blog', '_blank')
}

function openPublicHistory() {
  window.open('/history', '_blank')
}

function goPath(path) {
  router.push(path)
}
</script>

<style scoped lang="scss">
.home {
  .home-title {
    margin: 0 0 8px;
    font-size: 22px;
    font-weight: 600;
  }
  .home-desc {
    margin: 0;
    color: var(--el-text-color-secondary);
    line-height: 1.6;
  }
  .mt16 {
    margin-top: 16px;
  }
  .entry-card {
    margin-bottom: 16px;
    cursor: pointer;
  }
  .entry-title {
    font-size: 16px;
    font-weight: 600;
    margin-bottom: 6px;
  }
  .entry-hint {
    font-size: 13px;
    color: var(--el-text-color-secondary);
  }
}
</style>
