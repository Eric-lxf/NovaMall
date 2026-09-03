export const taskLabels = Object.freeze({
  QUEUED: '排队中', RUNNING: '执行中', SUCCEEDED: '已完成', PARTIAL_SUCCESS: '部分完成', FAILED: '失败',
  CANCEL_REQUESTED: '取消中', CANCELLED: '已取消', NEEDS_CONFIRMATION: '需人工确认'
})

export const isActiveTask = task => ['QUEUED', 'RUNNING', 'CANCEL_REQUESTED'].includes(task.status)
export const canCancelTask = task => ['QUEUED', 'RUNNING', 'NEEDS_CONFIRMATION'].includes(task.status)
export const canRetryTask = task => task.kind === 'SYSTEM_CHECK' && task.status === 'FAILED' && task.attemptNo < 3

export function pollDelay({ active, visible, ready, failures, tasks }) {
  return active && visible && ready && failures < 3 && tasks.some(isActiveTask) ? 3000 : null
}

export function newRequestKey(cryptoApi = globalThis.crypto) {
  if (cryptoApi.randomUUID) return cryptoApi.randomUUID()
  const bytes = cryptoApi.getRandomValues(new Uint8Array(16))
  return Array.from(bytes, byte => byte.toString(16).padStart(2, '0')).join('')
}
