<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import OutlineEditor from '@/components/ai/OutlineEditor.vue'
import {
  fetchAiTask,
  generateOutline,
  generateSummary,
  generateTitles,
  submitGenerateArticle,
} from '@/api/blog/aiWrite'
import { fetchCategories } from '@/api/blog/category'

const router = useRouter()
const step = ref(0)
const loading = ref(false)
const titles = ref([])
const categories = ref([])
const taskId = ref()
const pollTimer = ref(undefined)

const form = reactive({
  topic: '',
  audience: 'mid',
  length: 'medium',
  title: '',
  summary: '',
  outline: [],
  publish: false,
  categoryId: undefined,
  tagNames: [],
})

const tagInput = ref('')

function unwrapList(res) {
  const d = res?.data
  if (Array.isArray(d)) return d
  return res?.rows ?? []
}

async function loadCategories() {
  const res = await fetchCategories()
  categories.value = unwrapList(res)
}

async function stepTitles() {
  if (!form.topic.trim()) {
    ElMessage.warning('请输入技术主题')
    return
  }
  loading.value = true
  try {
    const res = await generateTitles(form)
    titles.value = Array.isArray(res.data) ? res.data : []
    if (titles.value.length) {
      form.title = titles.value[0]
    }
    step.value = 1
  } finally {
    loading.value = false
  }
}

async function stepSummary() {
  if (!form.title?.trim()) {
    ElMessage.warning('请选择或填写标题')
    return
  }
  loading.value = true
  try {
    const res = await generateSummary(form)
    form.summary = typeof res.data === 'string' ? res.data : ''
    step.value = 2
  } finally {
    loading.value = false
  }
}

async function stepOutline() {
  loading.value = true
  try {
    if (form.length === 'long' || form.audience === 'senior') {
      ElMessage.info('大纲生成中，资深读者/长文可能需要 1～3 分钟，请勿关闭页面')
    }
    const res = await generateOutline(form)
    form.outline = Array.isArray(res.data) ? res.data : []
    step.value = 3
  } finally {
    loading.value = false
  }
}

function stopPoll() {
  if (pollTimer.value) {
    clearInterval(pollTimer.value)
    pollTimer.value = undefined
  }
}

function startPoll(id) {
  stopPoll()
  pollTimer.value = window.setInterval(async () => {
    try {
      const res = await fetchAiTask(id)
      const task = res.data
      if (!task) return
      if (task.status === 2 && task.targetArticleId) {
        stopPoll()
        loading.value = false
        ElMessage.success(form.publish ? '文章已发布' : '草稿已生成')
        router.push({ path: '/blog-admin/article/edit', query: { id: String(task.targetArticleId) } })
      } else if (task.status === 3) {
        stopPoll()
        loading.value = false
        ElMessage.error(task.errorMessage || '生成失败')
      }
    } catch {
      stopPoll()
      loading.value = false
    }
  }, 2000)
}

async function stepGenerate() {
  if (!form.outline?.length) {
    ElMessage.warning('请至少保留一个大纲节点')
    return
  }
  if (tagInput.value.trim()) {
    form.tagNames = tagInput.value.split(/[,，]/).map(t => t.trim()).filter(Boolean)
  }
  loading.value = true
  try {
    if (form.length === 'long' || form.audience === 'senior' || (form.outline?.length ?? 0) >= 5) {
      ElMessage.info('正文将按章节分段生成，耗时可能较长，请保持页面打开')
    }
    const res = await submitGenerateArticle(form)
    const payload = res.data
    taskId.value = payload?.taskId ?? payload?.task_id
    if (!taskId.value) {
      ElMessage.error('任务创建失败')
      loading.value = false
      return
    }
    step.value = 4
    startPoll(taskId.value)
  } catch {
    loading.value = false
  }
}

loadCategories()
</script>

<template>
  <el-card v-loading="loading" shadow="never" class="write-wizard">
    <template #header>
      <span>博客智写 · 向导式创作</span>
    </template>

    <el-steps :active="step" finish-status="success" align-center class="steps">
      <el-step title="主题" />
      <el-step title="标题" />
      <el-step title="摘要" />
      <el-step title="大纲" />
      <el-step title="生成" />
    </el-steps>

    <div v-show="step === 0" class="step-panel">
      <el-form label-width="100px">
        <el-form-item label="技术主题" required>
          <el-input v-model="form.topic" placeholder="如：Java 21 虚拟线程实战" />
        </el-form-item>
        <el-form-item label="目标读者">
          <el-radio-group v-model="form.audience">
            <el-radio value="novice">新手</el-radio>
            <el-radio value="junior">1-3 年</el-radio>
            <el-radio value="mid">3-5 年</el-radio>
            <el-radio value="senior">5 年+</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="目标篇幅">
          <el-radio-group v-model="form.length">
            <el-radio value="short">简洁</el-radio>
            <el-radio value="medium">标准</el-radio>
            <el-radio value="long">专业长文</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="stepTitles">下一步：生成标题</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div v-show="step === 1" class="step-panel">
      <el-form label-width="100px">
        <el-form-item label="候选标题">
          <el-radio-group v-model="form.title" class="title-list">
            <el-radio v-for="t in titles" :key="t" :value="t" border>{{ t }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="自定义标题">
          <el-input v-model="form.title" placeholder="也可手动修改" />
        </el-form-item>
        <el-form-item>
          <el-button @click="step = 0">上一步</el-button>
          <el-button type="primary" @click="stepSummary">下一步：生成摘要</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div v-show="step === 2" class="step-panel">
      <el-form label-width="100px">
        <el-form-item label="文章摘要">
          <el-input v-model="form.summary" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item>
          <el-button @click="step = 1">上一步</el-button>
          <el-button @click="stepSummary">重新生成摘要</el-button>
          <el-button type="primary" @click="stepOutline">下一步：生成大纲</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div v-show="step === 3" class="step-panel">
      <OutlineEditor v-if="form.outline" v-model="form.outline" />
      <div class="step-actions">
        <el-button @click="step = 2">上一步</el-button>
        <el-button @click="stepOutline">重新生成大纲</el-button>
      </div>
      <el-divider />
      <el-form label-width="100px">
        <el-form-item label="分类">
          <el-select v-model="form.categoryId" clearable style="width: 200px">
            <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="tagInput" placeholder="逗号分隔，如：Java,Spring" />
        </el-form-item>
        <el-form-item label="发布选项">
          <el-checkbox v-model="form.publish">生成后直接发布（否则保存为草稿）</el-checkbox>
        </el-form-item>
        <el-form-item>
          <el-button type="success" @click="stepGenerate">开始生成全文</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div v-show="step === 4" class="step-panel center">
      <el-result icon="info" title="AI 正在撰写全文">
        <template #sub-title>
          <p>任务 ID：{{ taskId }}，请稍候（约 1-3 分钟）...</p>
          <p>完成后将自动跳转到编辑器</p>
        </template>
      </el-result>
    </div>
  </el-card>
</template>

<style scoped>
.write-wizard {
  max-width: 900px;
}

.steps {
  margin-bottom: 24px;
}

.step-panel {
  margin-top: 16px;
}

.title-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: flex-start;
}

.step-actions {
  margin-top: 16px;
  display: flex;
  gap: 8px;
}

.center {
  text-align: center;
}
</style>
