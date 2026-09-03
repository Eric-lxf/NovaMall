export const questionTypes = Object.freeze({ SINGLE_CHOICE: '单选题', MULTIPLE_CHOICE: '多选题', TRUE_FALSE: '判断题', SHORT_ANSWER: '简答题' })
export const reviewLabels = Object.freeze({ DRAFT: '草稿', PENDING_REVIEW: '待人工审核', APPROVED: '已批准', REJECTED: '已退回' })
export const latest = question => question?.versions?.[0]
export const revisionBody = entity => ({ expectedRevision: entity.revision })
export const reviewBody = question => ({ ...revisionBody(question), contentHash: latest(question).contentHash })
export function totalScore(items) { return items.reduce((sum, item) => sum + Math.round(Number(item.score || 0) * 100), 0) / 100 }
export function newQuestion(slot) {
  const common = { slotId: slot.slotId, type: slot.type, stem: '', analysis: '', knowledgePointIds: [...slot.knowledgePointIds], sourceRefs: [] }
  if (slot.type.endsWith('CHOICE')) return { ...common, options: ['A', 'B', 'C', 'D'].map(id => ({ id, text: '' })), correctOptionIds: [] }
  if (slot.type === 'TRUE_FALSE') return { ...common, answerBoolean: true }
  return { ...common, referenceAnswer: '', rubric: [{ point: '', weight: 100 }] }
}
export function safeStudentQuestion(question) {
  const result = { type: question.type, stem: question.stem }
  if (question.options) result.options = question.options.map(({ id, text }) => ({ id, text }))
  return result
}
export function missingSlots(blueprint, questionVersions) {
  const occupied = new Set(questionVersions.filter(v => v.blueprintId === blueprint.id).map(v => v.content.slotId))
  return blueprint.slots.filter(slot => !occupied.has(slot.slotId)).map(slot => slot.slotId)
}
