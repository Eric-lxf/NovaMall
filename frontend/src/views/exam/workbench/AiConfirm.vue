<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { examGet, examWrite } from '@/api/exam/workflow'
import { newRequestKey, errorLabel } from '../task/task-state'
import AiModelSummary from './AiModelSummary.vue'
const props = defineProps({ operation: String, payload: Object, count: { type: Number, default: 1 } })
const emit = defineEmits(['submitted'])
const config = ref(null), consent = ref(false), busy = ref(false), key = ref(newRequestKey())
const pendingRequest = ref(null)
const preview = ref(null), loadError = ref('')
const maxCalls = ref(Math.min(150, Math.max(3, props.count * 3))), maxTokens = ref(Math.min(2000000, Math.max(60000, props.count * 60000)))
const ready = computed(() => config.value?.ready && preview.value && consent.value && !busy.value)
onMounted(async () => {
  try {
    config.value = (await examGet('ai/capabilities')).data
    if (!config.value?.ready) return
    preview.value = (await examWrite('ai/preview', { operation: props.operation, payload: props.payload })).data
    config.value = preview.value
    maxCalls.value = preview.value.recommendedCalls
    maxTokens.value = preview.value.recommendedTokens
  } catch (error) { loadError.value = error.message || '读取调用计划失败，请关闭窗口后重试' }
})
async function submit() {
  if (!ready.value) return
  busy.value = true
  try {
    pendingRequest.value ||= { ...JSON.parse(JSON.stringify(props.payload)), externalConsent: true, maxCalls: maxCalls.value, maxTokens: maxTokens.value,
      generationFingerprint: config.value.generation.configFingerprint, verificationFingerprint: config.value.verification.configFingerprint,
      planFingerprint: preview.value.planFingerprint }
    const result = await examWrite(`ai/${props.operation}`, pendingRequest.value, 'post', key.value)
    ElMessage.success(`任务 ${result.data.id} 已排队，可在任务中心查看进度`); emit('submitted', result.data)
  } catch { /* Preserve the same key across an uncertain enqueue response. */ } finally { busy.value = false }
}
</script>
<template>
  <div>
    <el-alert title="真实 AI 调用可能产生费用；只发送已授权的选定资料" type="warning" :closable="false" />
    <AiModelSummary v-if="config?.ready" :config="config" :operation="operation" class="models" />
    <el-alert v-else :title="config?.errorCode ? errorLabel(config.errorCode) : '正在读取命题 AI 配置…'" type="info" :closable="false" class="models" />
    <el-alert v-if="loadError" :title="loadError" type="error" :closable="false" class="models" />
    <p v-if="preview">本次 {{ preview.itemCount }} 个{{ operation === 'extract' ? '资料批次' : '子任务' }}。已按实际计划填写建议预算，可在提交前调整；预算不足时会停止，不会自动追加调用。</p>
    <el-form label-position="top" class="models">
      <el-form-item label="本次最多调用次数（每题通常含生成、独立解题、解析/评分复核共 3 次）"><el-input-number v-model="maxCalls" :min="1" :max="150" :disabled="!!pendingRequest" /></el-form-item>
      <el-form-item label="Token 预留上限（含输入和最大输出，不是货币金额）"><el-input-number v-model="maxTokens" :min="4096" :max="2000000" :step="10000" :disabled="!!pendingRequest" /></el-form-item>
      <p>预留按输入字节保守估算；实际用量以供应商为准。每道题格式错误最多额外修复 1 次，计入以上预算；不确定响应不会自动重试。长资料按全部可用片段分批抽取，预算不足时停止并保留已完成批次。生成结果必须人工审核。</p>
      <el-checkbox v-model="consent">我有权使用这些资料，同意发送给上方显示的模型服务，并接受以上调用预算</el-checkbox>
      <div class="models"><el-button type="primary" :disabled="!ready" :loading="busy" @click="submit">确认并提交</el-button></div>
    </el-form>
  </div>
</template>
<style scoped>.models { margin-top: 18px; }p { color: var(--el-text-color-secondary); line-height: 1.6; }</style>
