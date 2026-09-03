<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { examGet, examWrite } from '@/api/exam/workflow'
import { newQuestion, latest } from './workflow-state'
const props = defineProps({ blueprints: Array, existing: Object })
const emit = defineEmits(['saved'])
const blueprintId = ref(props.existing ? latest(props.existing).blueprintId : '')
const content = ref(props.existing ? JSON.parse(JSON.stringify(latest(props.existing).content)) : null)
const blueprint = computed(() => props.blueprints.find(b => b.id === blueprintId.value))
const fragments = ref([]), saving = ref(false)
async function chooseSlot(id) {
  content.value = newQuestion(blueprint.value.slots.find(s => s.slotId === id))
  await loadEvidence()
}
async function loadEvidence() {
  const slot = blueprint.value?.slots.find(s => s.slotId === content.value?.slotId)
  if (!slot) return
  const all = await Promise.all(blueprint.value.settings.sourceVersionIds.map(id => examGet(`source-versions/${id}/fragments`)))
  fragments.value = all.flatMap(r => r.data).filter(f => slot.sourceFragmentIds.includes(f.id))
}
function addReference(fragment) {
  if (!content.value.sourceRefs.some(r => r.fragmentId === fragment.id)) content.value.sourceRefs.push({ sourceVersionId: fragment.sourceVersionId, fragmentId: fragment.id, quote: fragment.text.slice(0, 1000) })
}
async function save() {
  saving.value = true
  try {
    const data = { blueprintId: blueprintId.value, content: content.value, ...(props.existing ? { expectedRevision: props.existing.revision } : {}) }
    const result = await examWrite(props.existing ? `questions/${props.existing.id}` : 'questions', data, props.existing ? 'put' : 'post')
    ElMessage.success('新题目版本已保存为草稿，需重新审核'); emit('saved', result.data)
  } catch { /* request layer displays the validation error */ } finally { saving.value = false }
}
if (props.existing) loadEvidence()
</script>
<template>
  <el-form label-position="top">
    <el-form-item label="已确认蓝图"><el-select v-model="blueprintId" :disabled="!!existing" @change="content = null"><el-option v-for="b in blueprints" :key="b.id" :value="b.id" :label="b.title" /></el-select></el-form-item>
    <el-form-item v-if="blueprint" label="题目槽位"><el-select :model-value="content?.slotId" :disabled="!!existing" @change="chooseSlot"><el-option v-for="slot in blueprint.slots" :key="slot.slotId" :value="slot.slotId" :label="`${slot.slotId} · ${slot.type} · ${slot.score} 分`" /></el-select></el-form-item>
    <template v-if="content">
      <el-form-item label="题干"><el-input v-model="content.stem" type="textarea" :rows="3" maxlength="2000" show-word-limit /></el-form-item>
      <template v-if="content.options">
        <el-form-item v-for="option in content.options" :key="option.id" :label="`选项 ${option.id}`"><el-input v-model="option.text" maxlength="1000" /></el-form-item>
        <el-form-item label="正确答案（单选恰好一个，多选至少两个）"><el-checkbox-group v-model="content.correctOptionIds"><el-checkbox v-for="option in content.options" :key="option.id" :value="option.id">{{ option.id }}</el-checkbox></el-checkbox-group></el-form-item>
      </template>
      <el-form-item v-if="content.type === 'TRUE_FALSE'" label="判断答案"><el-radio-group v-model="content.answerBoolean"><el-radio :value="true">正确</el-radio><el-radio :value="false">错误</el-radio></el-radio-group></el-form-item>
      <template v-if="content.type === 'SHORT_ANSWER'">
        <el-form-item label="参考答案"><el-input v-model="content.referenceAnswer" type="textarea" :rows="3" maxlength="4000" /></el-form-item>
        <el-form-item v-for="(point, i) in content.rubric" :key="i" :label="`评分点 ${i + 1}（权重总和为 100%）`"><el-input v-model="point.point" placeholder="评分要点" maxlength="1000" /><el-input-number v-model="point.weight" :min="1" :max="100" /><el-button v-if="content.rubric.length > 1" link @click="content.rubric.splice(i, 1)">删除</el-button></el-form-item>
        <el-button :disabled="content.rubric.length >= 10" @click="content.rubric.push({ point: '', weight: 10 })">增加评分点</el-button>
      </template>
      <el-form-item label="答案解析"><el-input v-model="content.analysis" type="textarea" :rows="4" maxlength="4000" show-word-limit /></el-form-item>
      <el-form-item label="原文依据（必须逐字引用）">
        <div class="evidence"><div v-for="fragment in fragments" :key="fragment.id"><p>{{ fragment.text }}</p><el-button link type="primary" @click="addReference(fragment)">引用片段 {{ fragment.id }}</el-button></div></div>
      </el-form-item>
      <el-form-item v-for="(ref, i) in content.sourceRefs" :key="i" :label="`引文 / 片段 ${ref.fragmentId}`"><el-input v-model="ref.quote" type="textarea" maxlength="1000" /><el-button link type="danger" @click="content.sourceRefs.splice(i, 1)">移除引文</el-button></el-form-item>
      <el-button type="primary" :loading="saving" @click="save">保存为待审核草稿</el-button>
    </template>
  </el-form>
</template>
<style scoped>.evidence { max-height: 240px; overflow: auto; width: 100%; }.evidence p { white-space: pre-wrap; }</style>
