<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { examGet, examWrite } from '@/api/exam/workflow'
import { newRequestKey } from './task-state'
import AiModelSummary from '../workbench/AiModelSummary.vue'
const props = defineProps({ task: { type: Object, required: true } })
const emit = defineEmits(['submitted'])
const plan = ref(null), error = ref(''), loading = ref(true), busy = ref(false)
const consent = ref(false), acknowledge = ref(false), pending = ref(null)
const maxCalls = ref(3), maxTokens = ref(60000), key = newRequestKey()
const operation = computed(() => props.task.kind === 'KNOWLEDGE_EXTRACT' ? 'extract' : props.task.kind === 'QUESTION_VERIFY' ? 'verify' : 'generate')
const ready = computed(() => plan.value?.ready && !busy.value && (!plan.value.ai || consent.value) && (!plan.value.requiresUncertainAcknowledgement || acknowledge.value))
onMounted(async () => {
  try {
    plan.value = (await examGet(`jobs/${props.task.id}/retry-preview`)).data
    if (plan.value.ai) { maxCalls.value = plan.value.recommendedCalls; maxTokens.value = plan.value.recommendedTokens }
  } catch (failure) { error.value = failure.message || '无法读取重试计划，请刷新任务列表' }
  finally { loading.value = false }
})
async function submit() {
  if (!ready.value) return
  busy.value = true
  try {
    if (!pending.value) {
      pending.value = { expectedRevision: plan.value.expectedRevision, planFingerprint: plan.value.planFingerprint, acknowledgeUncertain: acknowledge.value }
      if (plan.value.ai) Object.assign(pending.value, { externalConsent: true, maxCalls: maxCalls.value, maxTokens: maxTokens.value,
        generationFingerprint: plan.value.generation.configFingerprint, verificationFingerprint: plan.value.verification.configFingerprint })
    }
    const result = await examWrite(`jobs/${props.task.id}/retry`, pending.value, 'post', key)
    ElMessage.success(`已创建重试任务 ${result.data.id}，原任务记录保留`)
    emit('submitted', result.data)
  } catch { /* 不确定的提交响应沿用原请求和幂等键；不自动再次调用模型。 */ }
  finally { busy.value = false }
}
</script>
<template>
  <div v-loading="loading">
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <template v-if="plan">
      <el-alert :title="`原任务 ${task.id} 的结果与用量保留；本次仅重试 ${plan.itemCount} 个未完成项，创建新的关联任务。`" type="info" :closable="false" />
      <template v-if="plan.ai">
        <el-alert class="retry-section" title="重试会产生新的模型调用和费用，原调用费用不会撤销。" type="warning" :closable="false" />
        <AiModelSummary class="retry-section" :config="plan" :operation="operation" />
        <el-form class="retry-section" label-position="top">
          <el-form-item label="本次重试最多调用次数"><el-input-number v-model="maxCalls" :min="1" :max="150" :disabled="!!pending" /></el-form-item>
          <el-form-item label="本次总 Token 预留上限（含输入和输出；不同于单次输出上限）"><el-input-number v-model="maxTokens" :min="4096" :max="2000000" :step="10000" :disabled="!!pending" /></el-form-item>
        </el-form>
        <el-checkbox v-model="consent" :disabled="!!pending">我有权使用这些资料，同意发送至上方模型服务，并接受本次重试预算</el-checkbox>
      </template>
      <div v-if="plan.requiresUncertainAcknowledgement" class="retry-section">
        <el-alert title="原任务执行结果不确定，供应商可能已处理并计费；本次不会重复已入库的成功项，但未确认的远端调用可能再次发生。" type="warning" :closable="false" />
        <el-checkbox v-model="acknowledge" :disabled="!!pending">我已核对原任务记录，接受再次执行及可能重复计费的风险</el-checkbox>
      </div>
      <div class="retry-section"><el-button type="primary" :disabled="!ready" :loading="busy" @click="submit">{{ pending ? '确认上次重试提交' : '确认并创建重试任务' }}</el-button></div>
    </template>
  </div>
</template>
<style scoped>.retry-section { margin-top: 18px; }.el-checkbox { white-space: normal; height: auto; margin-top: 12px; }:deep(.el-checkbox__label) { white-space: normal; line-height: 1.6; }</style>
