import test from 'node:test'
import assert from 'node:assert/strict'
import { parseRequirements } from '../src/views/exam/workbench/requirements.js'

test('local requirements preserve explicit counts and score without a paid call', () => {
  const result = parseRequirements('单选题 10 道，每题 2 分；5 道判断题，每题 1 分；总分 25 分；时长 30 分钟')
  assert.equal(result.count, 15); assert.equal(result.totalScore, 25); assert.equal(result.durationMinutes, 30)
  assert.equal(result.groups[1].score, 1); assert.equal(result.warnings.length, 1)
})
test('missing scores and contradictory totals remain visible for human confirmation', () => {
  const result = parseRequirements('多选题 5 道；总分 100 分')
  assert.equal(result.groups[0].score, 1); assert.equal(result.totalScore, 100); assert.equal(result.warnings.length, 3)
})
test('ambiguous, oversized and unsupported requirements do not silently become a blueprint', () => {
  for (const value of ['单选题 60 道', '单选题 2 道；单选题 5 道', '出一套高难度卷子', '单选题 1.5 道']) assert.throws(() => parseRequirements(value))
})
