import test from 'node:test'
import assert from 'node:assert/strict'
import { webcrypto } from 'node:crypto'
import { canCancelTask, canRetryTask, isActiveTask, newRequestKey, pollDelay, taskLabels } from '../src/views/exam/task/task-state.js'

test('active statuses exclude terminal and uncertain tasks', () => {
  assert.deepEqual(Object.keys(taskLabels).filter(status => isActiveTask({ status })), ['QUEUED', 'RUNNING', 'CANCEL_REQUESTED'])
})
test('only queued, running and uncertain tasks can be cancelled', () => {
  assert.deepEqual(Object.keys(taskLabels).filter(status => canCancelTask({ status })), ['QUEUED', 'RUNNING', 'NEEDS_CONFIRMATION'])
})
test('only failed checks below three attempts can retry', () => {
  assert.equal(canRetryTask({ kind: 'SYSTEM_CHECK', status: 'FAILED', attemptNo: 2 }), true)
  for (const task of [{ kind: 'AI_GENERATE', status: 'FAILED', attemptNo: 1 }, { kind: 'SYSTEM_CHECK', status: 'SUCCEEDED', attemptNo: 1 }, { kind: 'SYSTEM_CHECK', status: 'FAILED', attemptNo: 3 }]) assert.equal(canRetryTask(task), false)
})
test('polling stops for hidden/inactive/disabled pages, repeated errors and terminal tasks', () => {
  const input = { active: true, visible: true, ready: true, failures: 0, tasks: [{ status: 'RUNNING' }] }
  assert.equal(pollDelay(input), 3000)
  for (const change of [{ active: false }, { visible: false }, { ready: false }, { failures: 3 }, { tasks: [] }, { tasks: [{ status: 'SUCCEEDED' }] }]) assert.equal(pollDelay({ ...input, ...change }), null)
})
test('keys use cryptographic UUID or a secure-context-independent random bytes fallback', () => {
  assert.match(newRequestKey(webcrypto), /^[A-Za-z0-9_-]{16,64}$/)
  assert.match(newRequestKey({ getRandomValues: buffer => webcrypto.getRandomValues(buffer) }), /^[0-9a-f]{32}$/)
})
