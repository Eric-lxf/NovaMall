// Read-only visual/interaction fixtures. This module is only aliased by tests/exam-ui.vite.config.mjs.
const fragment = { id: '101', sourceVersionId: '11', text: '作业前必须检查防护设备。发现故障应停止作业并报告主管。', usable: true, locator: { kind: 'TXT_LINE', lineStart: 1, lineEnd: 1 } }
const source = { id: '1', title: '作业安全培训手册（验收样例）', currentVersionId: '11', enabled: true, revision: 1, versions: [{ id: '11', versionNo: 1, revision: 1, status: 'READY', externalAllowed: false, originalName: '安全培训.txt', warnings: ['请对照原文件检查数字、顺序及适用范围。'] }] }
const point = { id: '21', sourceVersionId: '11', name: '防护设备检查与故障处置', description: '检查设备；发现故障立即停工并报告。', confirmed: true, enabled: true, revision: 1, sourceRefs: [{ fragmentId: '101', sourceVersionId: '11', quote: fragment.text }] }
const slot = { slotId: 'q1', type: 'SINGLE_CHOICE', targetDifficulty: 'EASY', score: 25, knowledgePointIds: ['21'], sourceFragmentIds: ['101'] }
const blueprint = { id: '31', title: '作业安全基础测试', status: 'CONFIRMED', revision: 1, contentHash: 'fixture', settings: { durationMinutes: 30, totalScore: 25, audience: '新入职员工', sourceVersionIds: ['11'], knowledgeSnapshot: [point] }, slots: [slot] }
const content = { slotId: 'q1', type: 'SINGLE_CHOICE', stem: '作业前应当先进行哪项操作？', options: [{ id: 'A', text: '检查防护设备' }, { id: 'B', text: '直接开始作业' }, { id: 'C', text: '跳过设备检查' }, { id: 'D', text: '发现故障仍继续工作' }], correctOptionIds: ['A'], analysis: '培训手册明确要求作业前检查防护设备。其余选项不符合安全要求。', knowledgePointIds: ['21'], sourceRefs: point.sourceRefs }
const version = { id: '41', versionNo: 1, blueprintId: '31', reviewState: 'APPROVED', contentHash: 'fixture', sourceAvailable: true, origin: 'MANUAL', content, evidence: [fragment], checks: [{ kind: 'RULES', passed: true, result: { passed: true, note: '结构检查通过，不代表答案一定正确' } }], reviews: [{ createdAt: '2026-09-03 10:00:00', decision: 'APPROVED', reason: '已核对原文与答案。' }] }
const question = { id: '4', currentVersionId: '41', enabled: true, revision: 3, versions: [version] }
const pendingQuestion = { ...question, id: '6', currentVersionId: '62', versions: [{ ...version, id: '62', reviewState: 'PENDING_REVIEW', reviews: [] }] }
const paper = { id: '5', title: '新员工安全测试卷', status: 'FINALIZED', revision: 1, currentVersionId: '51', draft: { durationMinutes: 30, totalScore: 25, items: [{ questionVersionId: '41', score: 25 }] } }
export default async function request(config) {
  if (config.method !== 'get') throw new Error('前端验收夹具不执行写操作，请在集成测试环境验收保存流程。')
  const key = config.url.replace('/exam/', '')
  const resources = { capabilities: { enabled: true, taskReady: true, workerEnabled: true }, 'workflow/capabilities': { workflowReady: true, privateStorageReady: true },
    'ai/capabilities': { ready: false, errorCode: 'EXAM_AI_DISABLED' }, sources: [source], 'sources/1': source, 'source-versions/11/fragments': [fragment], knowledge: [point], blueprints: [blueprint], 'blueprints/31': blueprint,
    questions: [question], 'questions/4': question, 'questions/6': pendingQuestion, reviews: [pendingQuestion], papers: [paper], 'papers/5': paper,
    exports: [{ id: '61', taskId: '71', paperVersionId: '51', audience: 'STUDENT', format: 'DOCX', status: 'SUCCEEDED', errorCode: '' }] }
  if (key.startsWith('paper-versions/51/')) {
    const c = key.endsWith('student') ? { type: content.type, stem: content.stem, options: content.options } : content
    return { code: 200, data: { title: paper.title, durationMinutes: 30, totalScore: 25, templateVersion: 'exam-paper.v1', items: [{ ordinal: 1, score: 25, content: c }] } }
  }
  if (!(key in resources)) throw new Error(`未配置只读前端夹具：${key}`)
  return { code: 200, data: JSON.parse(JSON.stringify(resources[key])) }
}
