export const taskLabels = Object.freeze({
  QUEUED: '排队中', RUNNING: '执行中', SUCCEEDED: '已完成', PARTIAL_SUCCESS: '部分完成', FAILED: '失败',
  CANCEL_REQUESTED: '取消中', CANCELLED: '已取消', NEEDS_CONFIRMATION: '需人工确认'
})

export const isActiveTask = task => ['QUEUED', 'RUNNING', 'CANCEL_REQUESTED'].includes(task.status)
export const canCancelTask = task => ['QUEUED', 'RUNNING', 'NEEDS_CONFIRMATION'].includes(task.status)
export const isAiTask = task => ['KNOWLEDGE_EXTRACT', 'QUESTION_GENERATE', 'QUESTION_VERIFY'].includes(task.kind)
export const canRetryTask = task => task.kind === 'SYSTEM_CHECK'
  ? task.status === 'FAILED' && task.attemptNo < 3
  : ['KNOWLEDGE_EXTRACT', 'QUESTION_GENERATE', 'QUESTION_VERIFY', 'SOURCE_PARSE', 'PAPER_EXPORT'].includes(task.kind)
    && ['FAILED', 'PARTIAL_SUCCESS', 'NEEDS_CONFIRMATION'].includes(task.status)

export const errorLabels = Object.freeze({
  EXAM_OUTPUT_TRUNCATED: '输出达到单次上限，结果不完整；请调整输出/思考预算或缩小资料范围后手动重试',
  EXAM_KNOWLEDGE_BATCH_FAILED: '知识点抽取有失败批次，可手动重试未完成项',
  EXAM_MODEL_HTTP_403: '模型服务拒绝访问，请核对模型、接口地址和调用权限',
  EXAM_MODEL_HTTP_401: '模型服务鉴权失败，请检查 API Key 与接口地址是否匹配',
  EXAM_AI_DISABLED: '管理员尚未启用命题 AI',
  EXAM_MODEL_NOT_READY: '模型配置不可用，请检查命题和独立复核模块',
  EXAM_MODEL_INCOMPLETE: '模型未正常完成，结果未入库，请查看结束原因',
  EXAM_RESULT_UNCERTAIN: '调用结果不确定，可能已计费；重试前需再次确认',
  EXAM_BUDGET_EXCEEDED: '本任务调用次数、Token 预留预算或每日调用上限已用尽',
  EXAM_RETRY_ALREADY_CREATED: '已创建重试任务，请查看后继任务，勿重复提交',
  EXAM_SOURCE_AUTH_CHANGED: '资料授权或模型配置已变化，请重新确认',
  EXAM_PERMISSION_REVOKED: '任务所需权限已撤销'
})
export const errorLabel = code => errorLabels[code] || code || '无'
export const callStatusLabel = status => ({ RESPONDED: '已响应', DISPATCHING: '调用中', FAILED: '失败', UNCERTAIN: '结果不确定' }[status] || status)
export const finishReasonLabel = reason => ({ stop: '正常结束', length: '达到输出上限', content_filter: '内容被拦截', tool_calls: '返回工具调用', insufficient_system_resource: '供应商资源不足' }[reason] || reason || '未返回')

export function pollDelay({ active, visible, ready, failures, tasks }) {
  return active && visible && ready && failures < 3 && tasks.some(isActiveTask) ? 3000 : null
}

export function newRequestKey(cryptoApi = globalThis.crypto) {
  if (cryptoApi.randomUUID) return cryptoApi.randomUUID()
  const bytes = cryptoApi.getRandomValues(new Uint8Array(16))
  return Array.from(bytes, byte => byte.toString(16).padStart(2, '0')).join('')
}
