<script setup>
defineProps({ config: Object, operation: String })
function policy(model, operation) {
  const value = model?.callPolicies?.[operation]
  if (!value) return '请刷新以获取最新调用设置'
  const thinking = model.thinkingSupported ? (value.thinking === 'disabled' ? '关闭思考' : `思考强度 ${value.reasoningEffort}`) : '使用供应商默认推理设置'
  return `单次输出上限 ${value.maxOutputTokens} token（含推理）；${thinking}`
}
</script>
<template>
  <el-descriptions :column="1" border>
    <el-descriptions-item label="生成服务">{{ config.generation.providerName }} / {{ config.generation.model }}<br>{{ config.generation.baseUrl }}<br>{{ policy(config.generation, operation === 'extract' ? 'extract' : 'generate') }}</el-descriptions-item>
    <el-descriptions-item label="独立复核">{{ config.verification.providerName }} / {{ config.verification.model }}<br>{{ config.verification.baseUrl }}<br>{{ policy(config.verification, 'verify') }}</el-descriptions-item>
  </el-descriptions>
</template>
