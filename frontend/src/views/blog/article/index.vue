<script setup>
defineOptions({ name: 'BlogArticleList' })

import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteArticle, fetchArticlePage } from '@/api/blog/article'

const router = useRouter()
const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const query = ref({
  pageNum: 1,
  pageSize: 10,
  keyword: '',
  status: undefined,
})

const statusMap = {
  0: { label: '草稿', type: 'info' },
  1: { label: '已发布', type: 'success' },
  2: { label: 'AI生成中', type: 'warning' },
}

async function loadData() {
  loading.value = true
  try {
    const res = await fetchArticlePage(query.value)
    tableData.value = res.rows ?? res.data?.records ?? []
    total.value = res.total ?? res.data?.total ?? 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.value.pageNum = 1
  loadData()
}

function handleEdit(id) {
  router.push({ path: '/blog-admin/article/edit', query: { id: String(id) } })
}

function goComments(row) {
  router.push({
    path: '/blog-ops/comment-manage',
    query: { articleId: String(row.id), articleTitle: row.title || '' },
  })
}

async function handleDelete(id) {
  await ElMessageBox.confirm('确定将该文章移入回收站吗？', '提示', { type: 'warning' })
  await deleteArticle(id)
  ElMessage.success('已移入回收站')
  loadData()
}

onMounted(loadData)
</script>

<template>
  <el-card shadow="never">
    <template #header>
      <div class="card-header">
        <span>文章列表</span>
        <div>
          <el-button v-hasPermi="['blog:article:recycle']" @click="router.push('/blog-admin/article/recycle')">
            回收站
          </el-button>
          <el-button type="primary" v-hasPermi="['blog:article:add']" @click="router.push('/blog-admin/article/edit')">
            新建
          </el-button>
        </div>
      </div>
    </template>

    <el-form :inline="true" @submit.prevent="handleSearch">
      <el-form-item label="关键词">
        <el-input v-model="query.keyword" placeholder="标题搜索" clearable />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
          <el-option label="草稿" :value="0" />
          <el-option label="已发布" :value="1" />
          <el-option label="AI生成中" :value="2" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleSearch">查询</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="tableData" stripe>
      <el-table-column prop="title" label="标题" min-width="200" class-name="col-title" />
      <el-table-column prop="categoryName" label="分类" width="120" />
      <el-table-column label="来源" width="120">
        <template #default="{ row }">
          <el-tooltip v-if="row.sourceType === 'EXTERNAL_API' && row.externalId" :content="`外部 ID：${row.externalId}`">
            <el-tag type="warning">外部 API</el-tag>
          </el-tooltip>
          <el-tag v-else-if="row.sourceType === 'EXTERNAL_API'" type="warning">外部 API</el-tag>
          <el-tag v-else type="info">管理后台</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="statusMap[row.status]?.type || 'info'">
            {{ statusMap[row.status]?.label || '未知' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="评论" width="90" align="center">
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            v-hasPermi="['blog:comment:list']"
            @click="goComments(row)"
          >
            {{ row.commentCount ?? 0 }}
          </el-button>
        </template>
      </el-table-column>
      <el-table-column prop="updateTime" label="更新时间" width="180" />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" v-hasPermi="['blog:comment:list']" @click="goComments(row)">
            评论
          </el-button>
          <el-button link type="primary" v-hasPermi="['blog:article:edit']" @click="handleEdit(row.id)">
            编辑
          </el-button>
          <el-button link type="danger" v-hasPermi="['blog:article:remove']" @click="handleDelete(row.id)">
            移入回收站
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination">
      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="loadData"
        @size-change="loadData"
      />
    </div>
  </el-card>
</template>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

:deep(.col-title .cell) {
  color: #0f172a;
  font-weight: 600;
}
</style>
