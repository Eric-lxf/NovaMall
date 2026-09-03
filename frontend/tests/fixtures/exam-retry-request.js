// 只在独立验收入口使用的内存接口；故意模拟响应丢失，不会连接后端或模型。
const params = new URLSearchParams(location.search)
const doc = params.has('doc'), uncertain = params.has('uncertain')
const original = { id: '5', kind: doc ? 'PAPER_EXPORT' : 'KNOWLEDGE_EXTRACT', title: doc ? '导出合成试卷' : '高考数学知识点抽取', status: uncertain ? 'NEEDS_CONFIRMATION' : 'FAILED', attemptNo: 1, revision: 3,
  errorCode: uncertain ? 'EXAM_RESULT_UNCERTAIN' : 'EXAM_KNOWLEDGE_BATCH_FAILED', resultSummary: '完成 0 批，失败 1 批', updatedAt: '2026-09-03 21:00:00' }
const policies = { extract: { maxOutputTokens: 8192, thinking: 'disabled', reasoningEffort: 'low' }, generate: { maxOutputTokens: 16384, thinking: 'enabled', reasoningEffort: 'low' }, verify: { maxOutputTokens: 16384, thinking: 'enabled', reasoningEffort: 'low' } }
const model = { providerName: 'DeepSeek（合成配置）', model: 'deepseek-v4-pro', baseUrl: 'https://api.deepseek.com', thinkingSupported: true, configFingerprint: 'synthetic-model', callPolicies: policies }
const plan = { ready: true, ai: !doc, kind: original.kind, itemCount: 9, expectedRevision: 3, planFingerprint: 'synthetic-plan', recommendedCalls: 9, recommendedTokens: 132000,
  generation: model, verification: model, requiresUncertainAcknowledgement: uncertain }
const detail = { callsReserved: 1, tokensReserved: 36652, result: {}, progress: {}, items: [{ slotId: 'knowledge-1', status: 'FAILED', errorCode: 'EXAM_OUTPUT_TRUNCATED' }],
  calls: [{ callNo: 1, model: model.model, status: 'RESPONDED', finishReason: 'length', usage: { completion_tokens: 4096, completion_tokens_details: { reasoning_tokens: 4096 } } }] }
let retry = null, lostResponse = false
window.__examRetryRequests = []
export default async function request(config) {
  const key = config.url.replace('/exam/', '')
  const ok = data => ({ code: 200, data: structuredClone(data) })
  if (key === 'capabilities') return ok({ enabled: true, taskReady: true, workerEnabled: true, aiEnabled: true })
  if (key === 'tasks') return { code: 200, rows: structuredClone(retry ? [retry, original] : [original]), total: retry ? 2 : 1 }
  if (key === 'tasks/5') return ok(original)
  if (key === 'tasks/6') return ok(retry)
  if (key === 'jobs/5') return ok(detail)
  if (key === 'jobs/6') return ok({ ...detail, retryOfTaskId: '5', progress: {}, calls: [], items: [] })
  if (key === 'jobs/5/retry-preview' || key === 'ai/preview' || key === 'ai/capabilities') return ok(plan)
  if (key === 'jobs/5/retry' && config.method === 'post') {
    // 和真实 HTTP 层一样序列化 JSON；Vue 的响应式请求对象不是 structuredClone 的合法输入。
    window.__examRetryRequests.push(JSON.parse(JSON.stringify(config)))
    if (!doc && !config.data.externalConsent) throw new Error('缺少资料授权')
    if (uncertain && !config.data.acknowledgeUncertain) throw new Error('缺少风险确认')
    retry ||= { ...original, id: '6', status: 'QUEUED', errorCode: '', resultSummary: '', revision: 0 }
    detail.progress.retryTaskId = '6'
    if (params.has('lose-response') && !lostResponse) { lostResponse = true; throw new Error('模拟响应丢失') }
    return ok(retry)
  }
  throw new Error(`未配置的离线请求：${config.method} ${key}`)
}
