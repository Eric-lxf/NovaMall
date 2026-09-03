import test from 'node:test'
import assert from 'node:assert/strict'
import { newQuestion, totalScore, safeStudentQuestion, missingSlots, questionTypes, reviewBody } from '../src/views/exam/workbench/workflow-state.js'

test('all four templates contain only their answer shape', () => {
  for (const type of Object.keys(questionTypes)) {
    const question = newQuestion({ slotId: 'q1', type, knowledgePointIds: ['10'] })
    assert.equal(question.type, type)
    assert.deepEqual(question.sourceRefs, [])
    assert.equal('answerBoolean' in question, type === 'TRUE_FALSE')
    assert.equal('referenceAnswer' in question, type === 'SHORT_ANSWER')
  }
})
test('fractional score sums use cents, not accumulated binary rounding', () => assert.equal(totalScore([{ score: 0.1 }, { score: 0.2 }]), 0.3))
test('student view has a strict allowlist', () => {
  const q = { type: 'SINGLE_CHOICE', stem: 'Question', options: [{ id: 'A', text: 'Choice', correct: true }], analysis: 'SECRET', correctOptionIds: ['A'], sourceRefs: ['SECRET'] }
  assert.deepEqual(safeStudentQuestion(q), { type: 'SINGLE_CHOICE', stem: 'Question', options: [{ id: 'A', text: 'Choice' }] })
})
test('only missing slots are selected for a supplemental run', () => {
  assert.deepEqual(missingSlots({ id: '1', slots: [{ slotId: 'q1' }, { slotId: 'q2' }] }, [{ blueprintId: '1', content: { slotId: 'q1' } }]), ['q2'])
})
test('review submits the visible hash and optimistic revision', () => assert.deepEqual(reviewBody({ revision: 3, versions: [{ contentHash: 'hash' }] }), { expectedRevision: 3, contentHash: 'hash' }))
