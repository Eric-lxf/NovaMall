<script setup>
import { questionTypes } from './workflow-state'
defineProps({ question: { type: Object, required: true }, answers: Boolean })
</script>
<template>
  <article class="question-view">
    <el-tag size="small">{{ questionTypes[question.type] }}</el-tag>
    <p class="stem">{{ question.stem }}</p>
    <p v-for="option in question.options" :key="option.id" class="option">{{ option.id }}．{{ option.text }}</p>
    <template v-if="answers">
      <el-divider content-position="left">答案与解析</el-divider>
      <p><strong>参考答案：</strong>{{ question.correctOptionIds?.join('、') ?? (question.type === 'TRUE_FALSE' ? (question.answerBoolean ? '正确' : '错误') : question.referenceAnswer) }}</p>
      <p>{{ question.analysis }}</p>
      <p v-for="(point, index) in question.rubric" :key="index">评分点 {{ point.weight }}%：{{ point.point }}</p>
      <blockquote v-for="(ref, index) in question.sourceRefs" :key="index">资料版本 {{ ref.sourceVersionId }} / 片段 {{ ref.fragmentId }}<br>{{ ref.quote }}</blockquote>
    </template>
  </article>
</template>
<style scoped>
.question-view { line-height: 1.7; overflow-wrap: anywhere; }
.question-view p, blockquote { white-space: pre-wrap; }
.stem { font-weight: 600; font-size: 16px; }.option { margin: 6px 0; }
blockquote { margin: 12px 0; padding: 8px 14px; border-left: 3px solid var(--el-color-primary-light-5); background: var(--el-fill-color-light); color: var(--el-text-color-secondary); }
</style>
