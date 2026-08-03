<script setup>
defineOptions({ name: 'BlogAiOptimize' })

import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import MarkdownViewer from '@/components/MarkdownViewer.vue'
import { fetchAiTemplates } from '@/api/blog/ai'
import { fetchPromptTemplate, optimizeArticle } from '@/api/blog/aiOptimize'
import { saveArticle } from '@/api/blog/article'

const router = useRouter()
const loading = ref(false)
const source = ref('')
const optimized = ref('')
const scene = ref('REWRITE')
const systemPrompt = ref('')
const temperature = ref(0.7)
const templates = ref([])

async function loadTemplates() {
  try {
    const res = await fetchAiTemplates()
    const list = res?.data
    templates.value = Array.isArray(list) ? list : []
    if (!templates.value.length) {
      templates.value = [
        { sceneType: 'REWRITE', templateName: '技术文档润色' },
        { sceneType: 'EXPAND', templateName: '扩写' },
        { sceneType: 'SHORTEN', templateName: '缩写' },
      ]
    }
  } catch {
    templates.value = [
      { sceneType: 'REWRITE', templateName: '技术文档润色' },
      { sceneType: 'EXPAND', templateName: '扩写' },
      { sceneType: 'SHORTEN', templateName: '缩写' },
    ]
  }
}

async function loadPrompt() {
  try {
    const res = await fetchPromptTemplate(scene.value)
    systemPrompt.value = res.data?.systemPrompt || ''
    if (res.data?.temperature != null) {
      temperature.value = Number(res.data.temperature)
    }
  } catch {
    // 模板接口无权限时仍可手工填写 System Prompt
  }
}

async function handleOptimize() {
  if (!source.value.trim()) {
    ElMessage.warning('请粘贴待优化文章')
    return
  }
  loading.value = true
  optimized.value = ''
  try {
    const res = await optimizeArticle({
      content: source.value,
      scene: scene.value,
      customSystemPrompt: systemPrompt.value,
      temperature: temperature.value,
    })
    optimized.value = res.data?.content || ''
    ElMessage.success('优化完成')
  } finally {
    loading.value = false
  }
}

async function saveAsDraft() {
  if (!optimized.value.trim()) return
  const title = optimized.value.split('\n')[0].replace(/^#+\s*/, '').slice(0, 80) || '优化后的文章'
  loading.value = true
  try {
    const res = await saveArticle({
      title,
      summary: optimized.value.slice(0, 200),
      content: optimized.value,
      status: 0,
    })
    if (res.data) {
      ElMessage.success('已保存为草稿')
      router.push({ path: '/blog-admin/article/edit', query: { id: String(res.data) } })
    }
  } finally {
    loading.value = false
  }
}

function handleFile(file) {
  const reader = new FileReader()
  reader.onload = () => {
    source.value = String(reader.result || '')
  }
  reader.readAsText(file)
  return false
}

watch(scene, loadPrompt)

onMounted(async () => {
  await loadTemplates()
  await loadPrompt()
})
</script>

<template>
  <div v-loading="loading" class="optimize-page">
    <el-row :gutter="16">
      <el-col :span="14">
        <el-card shadow="never">
          <template #header>待优化文章</template>
          <el-upload
            drag
            :show-file-list="false"
            accept=".txt,.md,.markdown"
            :before-upload="handleFile"
          >
            <div class="upload-tip">拖拽 TXT / Markdown 到此处，或直接在下方粘贴</div>
          </el-upload>
          <el-input
            v-model="source"
            type="textarea"
            :rows="16"
            placeholder="粘贴待优化的正文..."
            class="source-input"
          />
        </el-card>
      </el-col>

      <el-col :span="10">
        <el-card shadow="never">
          <template #header>高阶配置</template>
          <el-form label-width="90px">
            <el-form-item label="模板">
              <el-select v-model="scene" style="width: 100%">
                <el-option
                  v-for="t in templates"
                  :key="t.sceneType"
                  :label="t.templateName"
                  :value="t.sceneType"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="Temperature">
              <el-slider v-model="temperature" :min="0" :max="2" :step="0.1" show-input />
            </el-form-item>
            <el-form-item label="System Prompt">
              <el-input v-model="systemPrompt" type="textarea" :rows="8" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleOptimize">开始优化</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>

    <el-card v-if="optimized" shadow="never" class="diff-card">
      <template #header>
        <div class="diff-header">
          <span>对比结果</span>
          <el-button type="primary" @click="saveAsDraft">保存为新草稿</el-button>
        </div>
      </template>
      <el-row :gutter="16">
        <el-col :span="12">
          <h4>原文</h4>
          <div class="diff-pane">
            <MarkdownViewer :value="source" />
          </div>
        </el-col>
        <el-col :span="12">
          <h4>优化后</h4>
          <div class="diff-pane optimized">
            <MarkdownViewer :value="optimized" />
          </div>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<style scoped>
.optimize-page {
  min-height: 400px;
}

.upload-tip {
  padding: 12px;
  color: #6b7280;
  font-size: 13px;
}

.source-input {
  margin-top: 12px;
}

.diff-card {
  margin-top: 16px;
}

.diff-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.diff-pane {
  max-height: 480px;
  overflow: auto;
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fafafa;
}

.diff-pane.optimized {
  background: #ecfdf5;
}

h4 {
  margin: 0 0 8px;
  font-size: 14px;
  color: #374151;
}
</style>
