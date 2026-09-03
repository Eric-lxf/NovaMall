<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { examGet, examWrite } from '@/api/exam/workflow'
import { newRequestKey } from '../task/task-state'
const props = defineProps({ operation: String, payload: Object, count: { type: Number, default: 1 } })
const emit = defineEmits(['submitted'])
const config = ref(null), consent = ref(false), busy = ref(false), key = ref(newRequestKey())
const pendingRequest = ref(null)
const maxCalls = ref(Math.min(150, Math.max(3, props.count * 3))), maxTokens = ref(Math.min(2000000, Math.max(60000, props.count * 60000)))
const ready = computed(() => config.value?.ready && consent.value)
onMounted(async () => { try { config.value = (await examGet('ai/capabilities')).data } catch {} })
async function submit() {
  busy.value = true
  try {
    pendingRequest.value ||= { ...JSON.parse(JSON.stringify(props.payload)), externalConsent: true, maxCalls: maxCalls.value, maxTokens: maxTokens.value,
      generationFingerprint: config.value.generation.configFingerprint, verificationFingerprint: config.value.verification.configFingerprint }
    const result = await examWrite(`ai/${props.operation}`, pendingRequest.value, 'post', key.value)
    ElMessage.success(`任务 ${result.data.id} 已排队，可在任务中心查看进度`); emit('submitted', result.data)
  } catch { /* Preserve the same key across an uncertain enqueue response. */ } finally { busy.value = false }
}
</script>
<template>
  <div>
    <el-alert title="真实 AI 调用可能产生费用；只发送已授权的选定资料" type="warning" :closable="false" />
    <el-descriptions v-if="config?.ready" :column="1" border class="models">
      <el-descriptions-item label="生成服务">{{ config.generation.providerName }} / {{ config.generation.model }}<br>{{ config.generation.baseUrl }}</el-descriptions-item>
      <el-descriptions-item label="独立复核">{{ config.verification.providerName }} / {{ config.verification.model }}<br>{{ config.verification.baseUrl }}</el-descriptions-item>
    </el-descriptions>
    <el-alert v-else title="命题 AI 未启用或模块模型尚未配置，请联系管理员。" type="info" :closable="false" class="models" />
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
