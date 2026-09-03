<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { checkPermi } from '@/utils/permission'
import { examGet, examWrite, examUpload, examDownload } from '@/api/exam/workflow'
import { questionTypes, reviewLabels, latest, revisionBody, reviewBody, totalScore } from './workflow-state'
import { newRequestKey, taskLabels } from '../task/task-state'
import QuestionView from './QuestionView.vue'
import QuestionEditor from './QuestionEditor.vue'
import AiConfirm from './AiConfirm.vue'
import { parseRequirements } from './requirements'

defineOptions({ name: 'ExamWorkbench' })
const route = useRoute(), router = useRouter()
const tabs = [{ key: 'sources', title: '资料库', permission: 'exam:source:list' }, { key: 'knowledge', title: '知识点', permission: 'exam:knowledge:list' },
  { key: 'blueprints', title: '命题蓝图', permission: 'exam:blueprint:list' }, { key: 'questions', title: '我的题库', permission: 'exam:question:list' },
  { key: 'reviews', title: '人工审核', permission: 'exam:review:list' }, { key: 'papers', title: '试卷', permission: 'exam:paper:list' }, { key: 'exports', title: '导出中心', permission: 'exam:paper:export' }]
const permittedTabs = computed(() => tabs.filter(t => checkPermi([t.permission])))
const tab = ref(permittedTabs.value.some(t => t.key === route.query.tab) ? route.query.tab : permittedTabs.value[0]?.key || 'sources')
const capabilities = ref(null), ready = ref(false), loading = ref(false), busy = ref(false), loadError = ref(false)
const records = ref([]), sourceChoices = ref([]), points = ref([]), blueprintChoices = ref([]), questionChoices = ref([])
const hasMore = ref(false)
const catalogs = ref({})
const selectedSource = ref(null), sourceVersion = ref(''), fragments = ref([]), usableIds = ref([]), externalAllowed = ref(false)
const search = ref(''), filtered = computed(() => records.value.filter(row => JSON.stringify([row.id, row.title, row.name, latest(row)?.content?.stem]).toLowerCase().includes(search.value.toLowerCase())))
const clone = value => JSON.parse(JSON.stringify(value))
const importOpen = ref(false), importForm = ref({}), importKey = ref(''), importFile = ref(null)
const knowledgeOpen = ref(false), knowledgeForm = ref({}), knowledgeFragments = ref([])
const mergeOpen = ref(false), mergeIds = ref([])
const blueprintOpen = ref(false), blueprintForm = ref({}), addCount = ref(1), addType = ref('SINGLE_CHOICE'), bulkKnowledge = ref([])
const requirementText = ref(''), requirementPreview = ref(null)
const editorOpen = ref(false), editingQuestion = ref(null)
const detailOpen = ref(false), selectedQuestion = ref(null), selectedQuestionVersion = ref(''), reviewReason = ref(''), manualVerification = ref(false)
const versionView = computed(() => selectedQuestion.value?.versions?.find(v => v.id === selectedQuestionVersion.value))
const aiOpen = ref(false), aiOperation = ref('generate'), aiPayload = ref({}), aiCount = ref(1), generationBlueprint = ref(null), chosenSlots = ref([])
const paperOpen = ref(false), paperForm = ref({}), previewOpen = ref(false), preview = ref(null), previewAnswers = ref(false)

async function action(fn) { if (busy.value) return; busy.value = true; try { await fn() } catch { /* errors are displayed by the shared request handler */ } finally { busy.value = false } }
async function load(append = false) {
  if (!ready.value) return
  const current = tab.value; loading.value = true; loadError.value = false
  try { const response = await examGet(current, append && records.value.length ? { beforeId: records.value.at(-1).id } : undefined); if (tab.value === current) { records.value = append ? [...records.value, ...response.data] : response.data; hasMore.value = response.data.length === (current === 'knowledge' ? 500 : 200) } }
  catch { loadError.value = true } finally { loading.value = false }
}
async function initialize() {
  loading.value = true
  try {
    capabilities.value = (await examGet('capabilities')).data
    if (capabilities.value.enabled) { const workflow = (await examGet('workflow/capabilities')).data; Object.assign(capabilities.value, workflow); ready.value = workflow.workflowReady }
    if (ready.value) await load()
  } catch { loadError.value = true } finally { loading.value = false }
}
watch(tab, async value => { records.value = []; search.value = ''; selectedSource.value = null; await router.replace({ query: { ...route.query, tab: value } }); await load() })
onMounted(initialize)
async function loadCatalog(kind, append = false) {
  const previous = catalogs.value[kind]
  const response = await examGet(kind, append && previous?.beforeId ? { beforeId: previous.beforeId } : undefined)
  const rows = append ? [...(previous?.rows || []), ...response.data] : response.data
  catalogs.value[kind] = { rows, beforeId: response.data.at(-1)?.id, hasMore: response.data.length === (kind === 'knowledge' ? 500 : 200) }
  if (kind === 'sources') sourceChoices.value = rows
  if (kind === 'knowledge') points.value = rows.filter(k => k.confirmed && k.enabled)
  if (kind === 'blueprints') blueprintChoices.value = rows.filter(b => b.status === 'CONFIRMED')
  if (kind === 'questions') questionChoices.value = rows.filter(q => q.enabled && latest(q)?.reviewState === 'APPROVED' && latest(q)?.sourceAvailable)
}
async function getSources() { await loadCatalog('sources') }
async function getBlueprints() { await loadCatalog('blueprints') }
async function showSource(row) {
  selectedSource.value = (await examGet(`sources/${row.id}`)).data; sourceVersion.value = selectedSource.value.currentVersionId; await loadFragments()
}
async function loadFragments() {
  fragments.value = (await examGet(`source-versions/${sourceVersion.value}/fragments`)).data
  usableIds.value = fragments.value.filter(f => f.usable).map(f => f.id)
  externalAllowed.value = selectedSource.value.versions.find(v => v.id === sourceVersion.value)?.externalAllowed || false
}
function openImport(source) {
  importForm.value = { title: source?.title || '', text: '', sourceId: source?.id, expectedRevision: source?.revision, mode: 'text' }
  importFile.value = null; importKey.value = newRequestKey(); importOpen.value = true
}
async function saveImport() {
  await action(async () => {
    const form = importForm.value
    if (!form.title.trim()) { ElMessage.warning('请填写资料标题'); return }
    if (form.mode === 'text') {
      const response = await examWrite(form.sourceId ? `sources/${form.sourceId}/versions/text` : 'sources/text', { title: form.title, text: form.text, expectedRevision: form.expectedRevision })
      await showSource(response.data)
    } else {
      const file = importFile.value
      if (!file || file.size > 10 * 1024 * 1024) { ElMessage.warning('请选择不超过 10 MB 的 TXT、DOCX 或 PDF'); return }
      const data = new FormData(); data.append('title', form.title); data.append('file', file)
      if (form.sourceId) { data.append('sourceId', form.sourceId); data.append('expectedRevision', form.expectedRevision) }
      const txt = file.name.toLowerCase().endsWith('.txt')
      const response = await examUpload(txt ? 'sources/txt' : 'sources/document', data, importKey.value)
      if (txt) await showSource(response.data)
      else ElMessage.success(`解析任务 ${response.data.id} 已排队，请到任务中心查看`)
    }
    importOpen.value = false; await load()
  })
}
async function confirmSource() {
  await action(async () => {
    const version = selectedSource.value.versions.find(v => v.id === sourceVersion.value)
    await examWrite(`source-versions/${sourceVersion.value}/confirm`, { ...revisionBody(version), fragmentIds: usableIds.value, externalAllowed: externalAllowed.value })
    ElMessage.success('可用片段与外发授权已保存'); await showSource(selectedSource.value); await load()
  })
}
async function toggleSource(row) {
  await action(async () => { await ElMessageBox.confirm(row.enabled ? '停用后，引用它的题目将无法定版或下载导出，确认停用？' : '确认恢复此资料？', '资料状态'); await examWrite(`sources/${row.id}/status`, { ...revisionBody(row), enabled: !row.enabled }); await load() })
}
async function openKnowledge(row) {
  await getSources(); knowledgeForm.value = row ? clone(row) : { name: '', description: '', sourceVersionId: '', sourceRefs: [] }
  knowledgeOpen.value = true; knowledgeFragments.value = []; if (row) await loadKnowledgeFragments()
}
async function loadKnowledgeFragments() { knowledgeFragments.value = (await examGet(`source-versions/${knowledgeForm.value.sourceVersionId}/fragments`)).data.filter(f => f.usable) }
function addKnowledgeReference(id) {
  const fragment = knowledgeFragments.value.find(f => f.id === id)
  if (fragment && !knowledgeForm.value.sourceRefs.some(r => r.fragmentId === id)) knowledgeForm.value.sourceRefs.push({ fragmentId: id, quote: fragment.text.slice(0, 1000) })
}
async function saveKnowledge() {
  await action(async () => { const form = knowledgeForm.value;
    if (form.mergeFrom) await ElMessageBox.confirm('合并将创建新候选并停用原知识点，旧记录继续保留供历史蓝图追溯。确认依据覆盖了合并后的描述？', '合并知识点')
    await examWrite(form.mergeFrom ? 'knowledge/merge' : form.id ? `knowledge/${form.id}` : 'knowledge', { ...form, expectedRevision: form.revision }, form.id ? 'put' : 'post'); knowledgeOpen.value = false; await load() })
}
async function prepareMerge() {
  const selected = records.value.filter(k => mergeIds.value.includes(k.id))
  if (selected.length < 2 || new Set(selected.map(k => k.sourceVersionId)).size !== 1) { ElMessage.warning('请选择同一资料版本的 2–10 个启用知识点'); return }
  await openKnowledge(selected[0]); delete knowledgeForm.value.id; knowledgeForm.value.mergeFrom = selected.map(k => ({ id: k.id, ...revisionBody(k) }))
  knowledgeForm.value.name = selected.map(k => k.name).join(' / ').slice(0, 160); knowledgeForm.value.description = selected.map(k => k.description).join('\n').slice(0, 2000)
  knowledgeForm.value.sourceRefs = [...new Map(selected.flatMap(k => k.sourceRefs).map(r => [r.fragmentId, r])).values()]
  mergeOpen.value = false; ElMessage.info('同片段引用仅保留一条，请人工核对合并后的全部依据，最多 8 条。')
}
async function toggleKnowledge() { await action(async () => { const row = knowledgeForm.value; await ElMessageBox.confirm('启停变更会撤销该知识点的确认状态，历史快照保留。继续？', '知识点状态'); await examWrite(`knowledge/${row.id}/status`, { ...revisionBody(row), enabled: !row.enabled }); knowledgeOpen.value = false; await load() }) }
async function toggleQuestion() { await action(async () => { const row = selectedQuestion.value; await ElMessageBox.confirm('停用后禁止新的组卷和导出，已下载文件无法追回。继续？', '题目状态'); await examWrite(`questions/${row.id}/status`, { ...revisionBody(row), enabled: !row.enabled }); detailOpen.value = false; await load() }) }
async function confirmKnowledge(row) { await action(async () => { await examWrite(`knowledge/${row.id}/confirm`, revisionBody(row)); await load() }) }
async function openBlueprint(row) {
  await loadCatalog('knowledge')
  blueprintForm.value = row ? clone(row) : { title: '', settings: { durationMinutes: 60, totalScore: 100, audience: '一般培训' }, slots: [] }
  if (row?.status === 'CONFIRMED') { delete blueprintForm.value.id; blueprintForm.value.title += '（副本）' }
  bulkKnowledge.value = []; requirementText.value = blueprintForm.value.settings.rawRequirements || ''; requirementPreview.value = null; blueprintOpen.value = true
}
function previewRequirements() { try { requirementPreview.value = parseRequirements(requirementText.value) } catch (error) { requirementPreview.value = null; ElMessage.warning(error.message) } }
async function applyRequirements() {
  if (!bulkKnowledge.value.length) { ElMessage.warning('请先选择知识点，再将解析结果填入槽位'); return }
  try { await ElMessageBox.confirm('将按下方解析结果替换当前槽位，暂填分值与未识别要求需要你逐项核对；这不会确认蓝图。继续？', '填入待确认草稿') } catch { return }
  const parsed = requirementPreview.value; blueprintForm.value.slots = []
  for (const group of parsed.groups) for (let i = 0; i < group.count; i++) blueprintForm.value.slots.push({ slotId: `q${blueprintForm.value.slots.length + 1}`, type: group.type, score: group.score,
    targetDifficulty: 'MEDIUM', cognitiveLevel: 'UNDERSTAND', knowledgePointIds: [...bulkKnowledge.value], sourceFragmentIds: fragmentsForKnowledge(bulkKnowledge.value) })
  blueprintForm.value.settings.totalScore = parsed.totalScore
  if (parsed.durationMinutes !== null) blueprintForm.value.settings.durationMinutes = parsed.durationMinutes
  blueprintForm.value.settings.rawRequirements = parsed.raw
}
function fragmentsForKnowledge(ids) { return [...new Set(points.value.filter(k => ids.includes(k.id)).flatMap(k => k.sourceRefs.map(r => r.fragmentId)))] }
function addSlots() {
  if (!bulkKnowledge.value.length) { ElMessage.warning('先选择已确认的知识点'); return }
  for (let i = 0; i < addCount.value && blueprintForm.value.slots.length < 50; i++) {
    let number = 1; while (blueprintForm.value.slots.some(s => s.slotId === `q${number}`)) number++
    blueprintForm.value.slots.push({ slotId: `q${number}`, type: addType.value, targetDifficulty: 'MEDIUM', score: 10, knowledgePointIds: [...bulkKnowledge.value], sourceFragmentIds: fragmentsForKnowledge(bulkKnowledge.value) })
  }
}
async function saveBlueprint() {
  await action(async () => {
    const form = blueprintForm.value
    const used = new Set(form.slots.flatMap(s => s.knowledgePointIds))
    const settings = { durationMinutes: form.settings.durationMinutes, totalScore: form.settings.totalScore, audience: form.settings.audience,
      rawRequirements: form.settings.rawRequirements || '', analysisRequirements: form.settings.analysisRequirements || '',
      sourceVersionIds: [...new Set(points.value.filter(k => used.has(k.id)).map(k => k.sourceVersionId))] }
    await examWrite(form.id ? `blueprints/${form.id}` : 'blueprints', { title: form.title, settings, slots: form.slots, expectedRevision: form.revision }, form.id ? 'put' : 'post')
    blueprintOpen.value = false; await load()
  })
}
async function confirmBlueprint(row) {
  await action(async () => { await ElMessageBox.confirm(`确认「${row.title}」：${row.slots.length} 题、${row.settings.totalScore} 分？确认后不可原地修改。`, '锁定命题蓝图'); await examWrite(`blueprints/${row.id}/confirm`, { ...revisionBody(row), contentHash: row.contentHash }); await load() })
}
async function openEditor(row) { await getBlueprints(); editingQuestion.value = row ? (await examGet(`questions/${row.id}`)).data : null; editorOpen.value = true }
async function showQuestion(row) {
  selectedQuestion.value = (await examGet(`questions/${row.id}`)).data; selectedQuestionVersion.value = selectedQuestion.value.currentVersionId
  reviewReason.value = ''; manualVerification.value = false; detailOpen.value = true
}
async function submitQuestion(row) { await action(async () => { const q = (await examGet(`questions/${row.id}`)).data; await examWrite(`question-versions/${q.currentVersionId}/submit`, reviewBody(q)); await load() }) }
async function review(decision) {
  await action(async () => {
    const q = selectedQuestion.value
    await examWrite(`question-versions/${q.currentVersionId}/review`, { ...reviewBody(q), decision, reason: reviewReason.value, manualVerification: manualVerification.value })
    detailOpen.value = false; await load(); ElMessage.success(decision === 'APPROVED' ? '已批准此明确版本' : '已退回修改')
  })
}
function openAi(operation, payload, count = 1) { generationBlueprint.value = null; aiOperation.value = operation; aiPayload.value = payload; aiCount.value = count; aiOpen.value = true }
function generate(row) { generationBlueprint.value = row; chosenSlots.value = row.slots.map(s => s.slotId); aiOperation.value = 'generate'; aiPayload.value = { blueprintId: row.id, slotIds: chosenSlots.value }; aiCount.value = row.slots.length; aiOpen.value = true }
function selectSlots() { aiPayload.value = { blueprintId: generationBlueprint.value.id, slotIds: [...chosenSlots.value] }; aiCount.value = chosenSlots.value.length }
async function chooseExtraction() {
  await getSources()
  if (!sourceChoices.value.length) { ElMessage.warning('先导入并确认资料'); return }
  knowledgeForm.value = { sourceVersionId: '' }; extractionOpen.value = true
}
const extractionOpen = ref(false)
async function openPaper(row) {
  await loadCatalog('questions')
  paperForm.value = row ? clone(row) : { title: '', draft: { durationMinutes: 60, totalScore: 0, items: [] } }
  if (row?.status === 'FINALIZED') { delete paperForm.value.id; paperForm.value.title += '（副本）' }
  paperOpen.value = true
}
function includeQuestion(q, checked) {
  const id = q.currentVersionId, items = paperForm.value.draft.items
  if (checked && !items.some(i => i.questionVersionId === id)) items.push({ questionVersionId: id, score: 10 })
  else if (!checked) paperForm.value.draft.items = items.filter(i => i.questionVersionId !== id)
}
function moveItem(index, direction) { const items = paperForm.value.draft.items, target = index + direction; if (target >= 0 && target < items.length) [items[index], items[target]] = [items[target], items[index]] }
async function savePaper() {
  await action(async () => { const form = paperForm.value; form.draft.totalScore = totalScore(form.draft.items); await examWrite(form.id ? `papers/${form.id}` : 'papers', { title: form.title, draft: form.draft, expectedRevision: form.revision }, form.id ? 'put' : 'post'); paperOpen.value = false; await load() })
}
async function finalize(row) { await action(async () => { await ElMessageBox.confirm('定版会冻结题目内容、顺序与分值，此试卷不再可编辑。继续？', '试卷定版'); await examWrite(`papers/${row.id}/finalize`, revisionBody(row)); await load() }) }
async function previewPaper(row, answers) { preview.value = (await examGet(`paper-versions/${row.currentVersionId}/${answers ? 'teacher' : 'student'}`)).data; previewAnswers.value = answers; previewOpen.value = true }
const exportOpen = ref(false), exportForm = ref({}), exportKey = ref('')
function openExport(row) { exportForm.value = { paperVersionId: row.currentVersionId, format: 'DOCX', audience: 'STUDENT' }; exportKey.value = newRequestKey(); exportOpen.value = true }
async function submitExport() { await action(async () => { await examWrite('exports', exportForm.value, 'post', exportKey.value); exportOpen.value = false; ElMessage.success('导出已排队，请在导出中心查看'); tab.value = 'exports' }) }
async function downloadExport(row) { try { await examDownload(`exports/${row.id}/download`, `试卷-${row.paperVersionId}-${row.audience}.${row.format.toLowerCase()}`) } catch (error) { ElMessage.error(error.message || '下载失败') } }
async function downloadSource() { try { await examDownload(`source-versions/${sourceVersion.value}/download`, selectedSource.value.versions.find(v => v.id === sourceVersion.value).originalName) } catch (error) { ElMessage.error(error.message || '下载失败') } }
</script>

<template>
  <div class="app-container exam-workbench">
    <header class="hero"><div><p class="eyebrow">NOVAMALL · 智能命题</p><h1>从可靠资料，到可审核的试卷</h1><p>资料确认 → 知识点 → 蓝图 → 题目与复核 → 人工审核 → 试卷定版 → 导出</p></div><el-button @click="router.push('/exam/task')">任务中心</el-button></header>
    <el-alert v-if="capabilities && !capabilities.enabled" type="warning" title="智能命题尚未启用，请由管理员完成数据库迁移并设置 EXAM_ENABLED。" :closable="false" />
    <el-alert v-else-if="capabilities && !ready" type="warning" title="命题业务表未就绪，请执行 exam_schema.sql 与 exam_workflow_schema.sql。" :closable="false" />
    <el-alert v-else-if="ready && !capabilities.privateStorageReady" type="warning" title="私有文件目录未就绪。请配置独立于 /profile、/uploads 的 EXAM_PRIVATE_ROOT。" :closable="false" />
    <el-alert v-if="loadError" type="error" title="当前列表读取失败，请刷新重试。" :closable="false" />
    <el-card shadow="never" class="workspace">
      <el-tabs v-model="tab"><el-tab-pane v-for="item in permittedTabs" :key="item.key" :label="item.title" :name="item.key" /></el-tabs>
      <div class="toolbar"><el-input v-model="search" placeholder="筛选当前已加载记录：标题 / 题干 / ID" clearable class="search" /><el-button :loading="loading" @click="initialize">刷新</el-button>
        <el-button v-if="tab === 'knowledge'" v-hasPermi="['exam:knowledge:edit']" @click="() => { mergeIds = []; mergeOpen = true }">合并知识点</el-button>
        <el-button v-if="tab === 'sources'" v-hasPermi="['exam:source:edit']" type="primary" :disabled="!ready" @click="openImport()">导入资料</el-button>
        <template v-if="tab === 'knowledge'"><el-button v-hasPermi="['exam:knowledge:edit']" :disabled="!ready" @click="openKnowledge()">新建知识点</el-button><el-button v-hasPermi="['exam:knowledge:extract']" type="primary" :disabled="!ready" @click="chooseExtraction">AI 抽取候选</el-button></template>
        <el-button v-if="tab === 'blueprints'" v-hasPermi="['exam:blueprint:edit']" type="primary" :disabled="!ready" @click="openBlueprint()">新建蓝图</el-button>
        <el-button v-if="tab === 'questions'" v-hasPermi="['exam:question:edit']" type="primary" :disabled="!ready" @click="openEditor()">手工录题</el-button>
        <el-button v-if="tab === 'papers'" v-hasPermi="['exam:paper:edit']" type="primary" :disabled="!ready" @click="openPaper()">新建试卷</el-button>
      </div>
      <div v-if="tab === 'sources'" class="split">
        <el-table v-loading="loading" :data="filtered" highlight-current-row @row-click="showSource" empty-text="先导入一份你有权使用的资料"><el-table-column prop="title" label="资料" min-width="160" /><el-table-column label="状态" width="80"><template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag></template></el-table-column><el-table-column label="操作" width="140"><template #default="{ row }"><el-button v-hasPermi="['exam:source:edit']" link @click.stop="openImport(row)">新版本</el-button><el-button v-hasPermi="['exam:source:edit']" link @click.stop="toggleSource(row)">{{ row.enabled ? '停用' : '恢复' }}</el-button></template></el-table-column></el-table>
        <section v-if="selectedSource" class="source-panel"><h3>{{ selectedSource.title }}</h3><div class="inline"><el-select v-model="sourceVersion" @change="loadFragments"><el-option v-for="version in selectedSource.versions" :key="version.id" :value="version.id" :label="`版本 ${version.versionNo} · ${version.status === 'READY' ? '已确认' : '待核对'}`" /></el-select><el-button v-hasPermi="['exam:source:download']" @click="downloadSource">原文件</el-button></div>
          <el-alert v-for="warning in selectedSource.versions.find(v => v.id === sourceVersion)?.warnings || []" :key="warning" :title="warning" type="warning" :closable="false" />
          <p class="muted">勾选可用于命题的片段。请对照原文件核对顺序、数字和表格内容。</p><el-button link @click="usableIds = fragments.map(f => f.id)">全选</el-button><el-button link @click="usableIds = []">清空</el-button>
          <el-checkbox-group v-model="usableIds" class="fragment-list"><div v-for="fragment in fragments" :key="fragment.id" class="fragment"><el-checkbox :value="fragment.id">片段 {{ fragment.id }} · {{ fragment.locator }}</el-checkbox><p>{{ fragment.text }}</p></div></el-checkbox-group>
          <el-checkbox v-model="externalAllowed">允许把选定片段发送给经我确认的外部模型服务</el-checkbox><div class="footer-actions"><el-button v-hasPermi="['exam:source:edit']" type="primary" :loading="busy" @click="confirmSource">保存可用范围与授权</el-button></div>
        </section><el-empty v-else description="选择资料，核对原文片段" />
      </div>
      <el-table v-else-if="tab === 'knowledge'" v-loading="loading" :data="filtered"><el-table-column prop="name" label="知识点" min-width="150" /><el-table-column prop="description" label="描述" min-width="230" /><el-table-column prop="sourceVersionId" label="资料版本" width="110" /><el-table-column label="确认状态" width="110"><template #default="{ row }"><el-tag :type="row.confirmed ? 'success' : 'warning'">{{ row.confirmed ? '已确认' : '候选' }}</el-tag></template></el-table-column><el-table-column label="操作" width="160"><template #default="{ row }"><el-button v-hasPermi="['exam:knowledge:edit']" link @click="openKnowledge(row)">编辑 / 依据</el-button><el-button v-if="!row.confirmed" v-hasPermi="['exam:knowledge:edit']" link type="primary" @click="confirmKnowledge(row)">确认</el-button></template></el-table-column></el-table>
      <el-table v-else-if="tab === 'blueprints'" v-loading="loading" :data="filtered"><el-table-column prop="title" label="蓝图" min-width="160" /><el-table-column label="范围与分值" min-width="190"><template #default="{ row }">{{ row.slots.length }} 题 / {{ row.settings.totalScore }} 分 / {{ row.settings.durationMinutes }} 分钟</template></el-table-column><el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="row.status === 'CONFIRMED' ? 'success' : 'info'">{{ row.status === 'CONFIRMED' ? '已确认' : '草稿' }}</el-tag></template></el-table-column><el-table-column label="操作" min-width="240"><template #default="{ row }"><el-button v-hasPermi="['exam:blueprint:edit']" link @click="openBlueprint(row)">{{ row.status === 'CONFIRMED' ? '查看 / 复制' : '编辑' }}</el-button><el-button v-if="row.status === 'DRAFT'" v-hasPermi="['exam:blueprint:edit']" link type="primary" @click="confirmBlueprint(row)">确认蓝图</el-button><el-button v-else v-hasPermi="['exam:question:generate']" link type="primary" @click="generate(row)">选择槽位生成</el-button></template></el-table-column></el-table>
      <el-table v-else-if="tab === 'questions' || tab === 'reviews'" v-loading="loading" :data="filtered"><el-table-column label="题干" min-width="240"><template #default="{ row }">{{ latest(row)?.content.stem }}</template></el-table-column><el-table-column label="题型" width="100"><template #default="{ row }">{{ questionTypes[latest(row)?.content.type] }}</template></el-table-column><el-table-column label="状态" width="140"><template #default="{ row }"><el-tag :type="latest(row)?.reviewState === 'APPROVED' ? 'success' : 'info'">{{ reviewLabels[latest(row)?.reviewState] }}</el-tag><el-tag v-if="!latest(row)?.sourceAvailable" type="danger">依据已失效</el-tag></template></el-table-column><el-table-column label="操作" min-width="240"><template #default="{ row }"><el-button link type="primary" @click="showQuestion(row)">{{ tab === 'reviews' ? '核对与审核' : '详情 / 版本' }}</el-button><template v-if="tab === 'questions'"><el-button v-hasPermi="['exam:question:edit']" link @click="openEditor(row)">新版本</el-button><el-button v-if="['DRAFT', 'REJECTED'].includes(latest(row)?.reviewState)" v-hasPermi="['exam:question:edit']" link @click="submitQuestion(row)">提交审核</el-button><el-button v-if="['DRAFT', 'REJECTED'].includes(latest(row)?.reviewState)" v-hasPermi="['exam:question:verify']" link @click="openAi('verify', { questionVersionId: row.currentVersionId })">AI 复核</el-button></template></template></el-table-column></el-table>
      <el-table v-else-if="tab === 'papers'" v-loading="loading" :data="filtered"><el-table-column prop="title" label="试卷" min-width="180" /><el-table-column label="题数 / 总分" width="140"><template #default="{ row }">{{ row.draft.items.length }} / {{ row.draft.totalScore }}</template></el-table-column><el-table-column label="状态" width="100"><template #default="{ row }">{{ row.status === 'FINALIZED' ? '已定版' : '草稿' }}</template></el-table-column><el-table-column label="操作" min-width="340"><template #default="{ row }"><el-button v-hasPermi="['exam:paper:edit']" link @click="openPaper(row)">{{ row.status === 'DRAFT' ? '编辑' : '复制' }}</el-button><el-button v-if="row.status === 'DRAFT'" v-hasPermi="['exam:paper:edit']" link type="primary" @click="finalize(row)">定版</el-button><template v-else><el-button link @click="previewPaper(row, false)">学生预览</el-button><el-button v-hasPermi="['exam:paper:answers']" link @click="previewPaper(row, true)">教师预览</el-button><el-button v-hasPermi="['exam:paper:export']" link type="primary" @click="openExport(row)">导出</el-button></template></template></el-table-column></el-table>
      <el-table v-else-if="tab === 'exports'" v-loading="loading" :data="filtered"><el-table-column prop="id" label="导出 ID" /><el-table-column prop="paperVersionId" label="试卷版本" /><el-table-column prop="format" label="格式" /><el-table-column label="卷别"><template #default="{ row }">{{ row.audience === 'STUDENT' ? '学生卷' : '教师卷' }}</template></el-table-column><el-table-column label="状态"><template #default="{ row }">{{ taskLabels[row.status] || row.status }}</template></el-table-column><el-table-column prop="errorCode" label="错误码" min-width="180" /><el-table-column label="下载"><template #default="{ row }"><el-button v-if="row.status === 'SUCCEEDED'" link type="primary" @click="downloadExport(row)">私有下载</el-button></template></el-table-column></el-table>
      <el-button v-if="hasMore" :loading="loading" @click="load(true)">载入更早记录</el-button><p class="muted small">已加载 {{ records.length }} 条记录；搜索仅筛选已加载记录。AI 生成不会自动批准题目；试卷定版前会再次校验资料有效性。</p>
    </el-card>

    <el-dialog v-model="importOpen" :title="importForm.sourceId ? '导入资料新版本' : '导入资料'" width="min(720px, 94vw)" destroy-on-close>
      <el-alert title="仅限你有权使用的资料。新版本会使依赖旧资料的题目失去可定版/导出资格。" type="info" :closable="false" />
      <el-form label-position="top"><el-form-item label="资料标题"><el-input v-model="importForm.title" maxlength="160" /></el-form-item><el-form-item label="输入方式"><el-radio-group v-model="importForm.mode"><el-radio value="text">粘贴文本</el-radio><el-radio value="file">上传文件</el-radio></el-radio-group></el-form-item><el-form-item v-if="importForm.mode === 'text'" label="原文（最多 20 万字符）"><el-input v-model="importForm.text" type="textarea" :rows="12" maxlength="200000" show-word-limit /></el-form-item><el-form-item v-else label="UTF-8 TXT、DOCX 或文本 PDF，最大 10 MB，PDF 最多 50 页"><input type="file" accept=".txt,.docx,.pdf" @change="importFile = $event.target.files[0]"></el-form-item></el-form>
      <template #footer><el-button @click="importOpen = false">取消</el-button><el-button type="primary" :loading="busy" @click="saveImport">导入并核对</el-button></template>
    </el-dialog>
    <el-dialog v-model="knowledgeOpen" title="知识点与原文依据" width="min(760px, 94vw)" destroy-on-close>
      <el-button v-if="catalogs.sources?.hasMore" @click="loadCatalog('sources', true)">载入更早的可选资料</el-button>
      <el-alert v-if="knowledgeForm.id && !knowledgeForm.enabled" title="此知识点已停用；恢复后需重新确认。" type="warning" :closable="false" />
      <el-alert v-if="knowledgeForm.mergeFrom" title="请核对合并后的名称、描述和逐字依据；保存后会停用原知识点。" type="warning" :closable="false" />
      <el-form label-position="top"><el-form-item label="已确认资料的当前版本"><el-select v-model="knowledgeForm.sourceVersionId" :disabled="!!knowledgeForm.id" @change="() => { knowledgeForm.sourceRefs = []; loadKnowledgeFragments() }"><el-option v-for="s in sourceChoices.filter(s => s.enabled)" :key="s.id" :value="s.currentVersionId" :label="`${s.title} / 版本 ${s.currentVersionId}`" /></el-select></el-form-item><el-form-item label="知识点名称"><el-input v-model="knowledgeForm.name" maxlength="160" /></el-form-item><el-form-item label="描述"><el-input v-model="knowledgeForm.description" type="textarea" maxlength="2000" /></el-form-item><el-form-item label="选择依据片段"><el-select placeholder="添加原文引文" @change="addKnowledgeReference"><el-option v-for="f in knowledgeFragments" :key="f.id" :value="f.id" :label="`${f.id} · ${f.text.slice(0, 70)}`" /></el-select></el-form-item><el-form-item v-for="(ref, i) in knowledgeForm.sourceRefs" :key="i" :label="`逐字引文 / 片段 ${ref.fragmentId}`"><el-input v-model="ref.quote" type="textarea" maxlength="1000" /><el-button link @click="knowledgeForm.sourceRefs.splice(i, 1)">移除</el-button></el-form-item></el-form>
      <template #footer><el-button v-if="knowledgeForm.id" :loading="busy" @click="toggleKnowledge">{{ knowledgeForm.enabled ? '停用（保留记录）' : '恢复' }}</el-button><el-button type="primary" :loading="busy" @click="saveKnowledge">{{ knowledgeForm.mergeFrom ? '合并并停用原知识点' : '保存候选（需再次确认）' }}</el-button></template>
    </el-dialog>
    <el-dialog v-model="mergeOpen" title="选择合并的知识点" width="min(640px, 94vw)"><el-select v-model="mergeIds" multiple :multiple-limit="10"><el-option v-for="k in records.filter(k => k.enabled)" :key="k.id" :value="k.id" :label="`${k.name} · 资料版本 ${k.sourceVersionId}`" /></el-select><template #footer><el-button :disabled="mergeIds.length < 2" @click="prepareMerge">下一步：核对新候选与依据</el-button></template></el-dialog>
    <el-dialog v-model="blueprintOpen" title="命题蓝图" width="min(1100px, 96vw)" destroy-on-close>
      <el-button v-if="catalogs.knowledge?.hasMore" @click="loadCatalog('knowledge', true)">载入更早的可选知识点</el-button>
      <el-form label-position="top"><el-form-item label="自然语言要求（本地辅助解析，不调用 AI）"><el-input v-model="requirementText" type="textarea" maxlength="4000" placeholder="单选题 10 道，每题 2 分；判断题 5 道，每题 1 分；总分 25 分；时长 30 分钟" /></el-form-item><el-button @click="previewRequirements">解析并预览</el-button></el-form>
      <section v-if="requirementPreview"><p>{{ requirementPreview.count }} 题 · 目标 {{ requirementPreview.totalScore }} 分 · 时长 {{ requirementPreview.durationMinutes ?? '未识别，保留表单值' }}</p><p v-for="g in requirementPreview.groups" :key="g.type">{{ questionTypes[g.type] }} {{ g.count }} 道 × {{ g.score }} 分</p><el-alert v-for="warning in requirementPreview.warnings" :key="warning" :title="warning" type="warning" :closable="false" /><el-button @click="applyRequirements">已查看，填入草稿槽位</el-button></section>
      <el-form v-if="blueprintForm.settings" label-position="top"><el-form-item label="蓝图标题"><el-input v-model="blueprintForm.title" maxlength="160" /></el-form-item><div class="inline"><el-form-item label="适用对象"><el-input v-model="blueprintForm.settings.audience" maxlength="160" /></el-form-item><el-form-item label="时长（分钟）"><el-input-number v-model="blueprintForm.settings.durationMinutes" :min="1" :max="480" /></el-form-item><el-form-item label="目标总分"><el-input-number v-model="blueprintForm.settings.totalScore" :min="0.01" :max="10000" :precision="2" /></el-form-item></div>
        <el-form-item label="批量添加槽位使用的知识点"><el-select v-model="bulkKnowledge" multiple filterable><el-option v-for="point in points" :key="point.id" :value="point.id" :label="point.name" /></el-select></el-form-item><div class="inline"><el-select v-model="addType"><el-option v-for="(label, value) in questionTypes" :key="value" :value="value" :label="label" /></el-select><el-input-number v-model="addCount" :min="1" :max="50" /><el-button @click="addSlots">添加槽位</el-button></div>
        <el-table :data="blueprintForm.slots"><el-table-column prop="slotId" label="槽位" width="70" /><el-table-column label="题型" width="150"><template #default="{ row }"><el-select v-model="row.type"><el-option v-for="(label, value) in questionTypes" :key="value" :value="value" :label="label" /></el-select></template></el-table-column><el-table-column label="知识点" min-width="200"><template #default="{ row }"><el-select v-model="row.knowledgePointIds" multiple @change="row.sourceFragmentIds = fragmentsForKnowledge(row.knowledgePointIds)"><el-option v-for="point in points" :key="point.id" :value="point.id" :label="point.name" /></el-select></template></el-table-column><el-table-column label="目标难度" width="130"><template #default="{ row }"><el-select v-model="row.targetDifficulty"><el-option label="容易" value="EASY" /><el-option label="中等" value="MEDIUM" /><el-option label="较难" value="HARD" /></el-select></template></el-table-column><el-table-column label="分值" width="170"><template #default="{ row }"><el-input-number v-model="row.score" :min="0.01" :max="10000" :precision="2" /></template></el-table-column><el-table-column width="70"><template #default="{ $index }"><el-button link type="danger" @click="blueprintForm.slots.splice($index, 1)">移除</el-button></template></el-table-column></el-table>
        <p>当前 {{ blueprintForm.slots.length }} 题 / {{ totalScore(blueprintForm.slots) }} 分。目标难度是命题要求，不是经过考生数据校准的实测难度。</p>
        <el-form-item label="答案解析要求（随蓝图冻结）"><el-input v-model="blueprintForm.settings.analysisRequirements" type="textarea" maxlength="2000" placeholder="例如：说明依据；解释每个干扰项为何不正确；简答题给出可操作的评分点。" /></el-form-item>
        <el-collapse><el-collapse-item title="逐题认知层级（默认理解，可手动调整）"><div v-for="slot in blueprintForm.slots" :key="slot.slotId" class="inline"><span>{{ slot.slotId }}</span><el-select v-model="slot.cognitiveLevel" placeholder="理解"><el-option label="记忆" value="REMEMBER" /><el-option label="理解" value="UNDERSTAND" /><el-option label="应用" value="APPLY" /><el-option label="分析" value="ANALYZE" /></el-select></div></el-collapse-item></el-collapse>
        <el-form-item v-if="blueprintForm.settings.rawRequirements" label="已采用的原始要求"><el-input :model-value="blueprintForm.settings.rawRequirements" type="textarea" readonly /></el-form-item>
      </el-form><template #footer><el-button type="primary" :loading="busy" @click="saveBlueprint">保存蓝图草稿</el-button></template>
    </el-dialog>
    <el-dialog v-model="editorOpen" title="题目录入 / 新版本" width="min(860px, 94vw)" destroy-on-close><el-button v-if="catalogs.blueprints?.hasMore" @click="loadCatalog('blueprints', true)">载入更早的可选蓝图</el-button><QuestionEditor v-if="editorOpen" :blueprints="blueprintChoices" :existing="editingQuestion" @saved="() => { editorOpen = false; load() }" /></el-dialog>
    <el-dialog v-model="detailOpen" title="题目版本、原文与审核" width="min(1000px, 96vw)" destroy-on-close>
      <div v-if="selectedQuestion && tab === 'questions'"><el-tag :type="selectedQuestion.enabled ? 'success' : 'warning'">{{ selectedQuestion.enabled ? '题目启用中' : '题目已停用' }}</el-tag><el-button v-hasPermi="['exam:question:edit']" link :loading="busy" @click="toggleQuestion">{{ selectedQuestion.enabled ? '停用题目' : '恢复题目' }}</el-button></div>
      <template v-if="selectedQuestion && versionView"><el-select v-model="selectedQuestionVersion"><el-option v-for="version in selectedQuestion.versions" :key="version.id" :value="version.id" :label="`版本 ${version.versionNo} · ${reviewLabels[version.reviewState]}`" /></el-select><div class="review-grid"><QuestionView :question="versionView.content" answers /><section><h3>原文定位</h3><div v-for="e in versionView.evidence" :key="e.fragmentId" class="fragment"><small>{{ e.locator }}</small><p>{{ e.text }}</p></div></section></div>
        <h3>自动检查</h3><div v-for="(check, i) in versionView.checks" :key="i" class="check"><el-tag :type="check.passed ? 'success' : 'danger'">{{ check.kind }} · {{ check.passed ? '通过' : '未通过' }}</el-tag><pre>{{ JSON.stringify(check.result, null, 2) }}</pre></div>
        <h3>人工审核历史</h3><p v-for="(item, i) in versionView.reviews" :key="i">{{ item.createdAt }} · {{ reviewLabels[item.decision] }} · {{ item.reason }}</p><el-empty v-if="!versionView.reviews.length" description="尚无审核记录" :image-size="50" />
        <el-form v-if="tab === 'reviews' && selectedQuestionVersion === selectedQuestion.currentVersionId && versionView.reviewState === 'PENDING_REVIEW'" label-position="top"><el-alert title="请逐项核对题干、答案、解析、评分点与原文。自动检查通过不等于题目一定正确。" type="warning" :closable="false" /><el-form-item label="审核意见（必填）"><el-input v-model="reviewReason" type="textarea" maxlength="2000" /></el-form-item><el-checkbox v-model="manualVerification">未运行 AI 复核时，我已独立逐项人工核对答案和依据</el-checkbox><div class="footer-actions"><el-button v-hasPermi="['exam:review:approve']" type="danger" :loading="busy" @click="review('REJECTED')">退回修改</el-button><el-button v-hasPermi="['exam:review:approve']" type="success" :loading="busy" @click="review('APPROVED')">批准当前版本</el-button></div></el-form>
      </template>
    </el-dialog>
    <el-dialog v-model="extractionOpen" title="选择知识点抽取资料" width="min(600px, 94vw)"><el-button v-if="catalogs.sources?.hasMore" @click="loadCatalog('sources', true)">载入更早的可选资料</el-button><el-select v-model="knowledgeForm.sourceVersionId"><el-option v-for="s in sourceChoices.filter(s => s.enabled)" :key="s.id" :value="s.currentVersionId" :label="s.title" /></el-select><template #footer><el-button :disabled="!knowledgeForm.sourceVersionId" type="primary" @click="() => { extractionOpen = false; openAi('extract', { sourceVersionId: knowledgeForm.sourceVersionId }) }">下一步：服务与预算</el-button></template></el-dialog>
    <el-dialog v-model="aiOpen" title="AI 任务确认" width="min(760px, 94vw)" destroy-on-close><template v-if="generationBlueprint"><p>仅选择本次需要生成/重新生成的槽位；重复提交新任务可能增加费用。</p><el-checkbox-group v-model="chosenSlots" @change="selectSlots"><el-checkbox v-for="slot in generationBlueprint.slots" :key="slot.slotId" :value="slot.slotId">{{ slot.slotId }} · {{ questionTypes[slot.type] }}</el-checkbox></el-checkbox-group></template><AiConfirm v-if="aiOpen && (!generationBlueprint || chosenSlots.length)" :key="`${aiOperation}-${aiCount}`" :operation="aiOperation" :payload="aiPayload" :count="aiCount" @submitted="aiOpen = false" /></el-dialog>
    <el-dialog v-model="paperOpen" title="试卷编排（仅已批准题目）" width="min(1000px, 96vw)" destroy-on-close><el-button v-if="catalogs.questions?.hasMore" @click="loadCatalog('questions', true)">载入更早的已批准题目</el-button><el-form v-if="paperForm.draft" label-position="top"><el-form-item label="试卷标题"><el-input v-model="paperForm.title" maxlength="160" /></el-form-item><el-form-item label="考试时长（分钟）"><el-input-number v-model="paperForm.draft.durationMinutes" :min="1" :max="480" /></el-form-item><div class="paper-picker"><el-checkbox v-for="q in questionChoices" :key="q.id" :model-value="paperForm.draft.items.some(i => i.questionVersionId === q.currentVersionId)" @change="checked => includeQuestion(q, checked)">{{ latest(q).content.stem }}</el-checkbox></div><el-table :data="paperForm.draft.items"><el-table-column label="顺序" width="80"><template #default="{ $index }">{{ $index + 1 }}</template></el-table-column><el-table-column label="题目" min-width="220"><template #default="{ row }">{{ latest(questionChoices.find(q => q.currentVersionId === row.questionVersionId))?.content.stem || `历史版本 ${row.questionVersionId}` }}</template></el-table-column><el-table-column label="分值" width="180"><template #default="{ row }"><el-input-number v-model="row.score" :min="0.01" :max="10000" :precision="2" /></template></el-table-column><el-table-column label="调整" width="180"><template #default="{ $index }"><el-button link @click="moveItem($index, -1)">上移</el-button><el-button link @click="moveItem($index, 1)">下移</el-button><el-button link type="danger" @click="paperForm.draft.items.splice($index, 1)">移除</el-button></template></el-table-column></el-table><p>{{ paperForm.draft.items.length }} 题 / 总分 {{ totalScore(paperForm.draft.items) }} 分</p></el-form><template #footer><el-button type="primary" :loading="busy" @click="savePaper">保存试卷草稿</el-button></template></el-dialog>
    <el-dialog v-model="previewOpen" :title="previewAnswers ? '教师卷预览（含答案）' : '学生卷预览'" width="min(900px, 96vw)" destroy-on-close><template v-if="preview"><h2>{{ preview.title }}</h2><p>{{ preview.durationMinutes }} 分钟 · {{ preview.totalScore }} 分</p><section v-for="item in preview.items" :key="item.ordinal" class="paper-question"><p>第 {{ item.ordinal }} 题 · {{ item.score }} 分</p><QuestionView :question="item.content" :answers="previewAnswers" /></section></template></el-dialog>
    <el-dialog v-model="exportOpen" title="导出试卷" width="min(540px, 94vw)"><el-form label-position="top"><el-form-item label="格式"><el-select v-model="exportForm.format" @change="value => { if (value === 'XLSX') exportForm.audience = 'TEACHER' }"><el-option label="Word DOCX" value="DOCX" /><el-option label="PDF（由同一 DOCX 模板转换）" value="PDF" /><el-option v-if="checkPermi(['exam:paper:answers'])" label="Excel 题库 XLSX（含答案）" value="XLSX" /></el-select></el-form-item><el-form-item label="卷别"><el-radio-group v-model="exportForm.audience"><el-radio value="STUDENT" :disabled="exportForm.format === 'XLSX'">学生卷</el-radio><el-radio v-if="checkPermi(['exam:paper:answers'])" value="TEACHER">教师卷 / 含答案解析</el-radio></el-radio-group></el-form-item><el-alert title="文件只通过鉴权接口下载，不生成公开链接；依据失效后停止下载。" type="info" :closable="false" /></el-form><template #footer><el-button type="primary" :loading="busy" @click="submitExport">生成导出文件</el-button></template></el-dialog>
  </div>
</template>

<style scoped>
.exam-workbench { --exam-accent: #23685b; }.hero { display: flex; justify-content: space-between; align-items: center; gap: 20px; padding: 24px 28px; margin-bottom: 22px; background: linear-gradient(110deg, #eef7f3, #f6f8fb); border: 1px solid #deebe5; border-radius: 12px; color: #173d33; }.hero h1 { font-size: 26px; margin: 10px 0; }.hero p { margin: 0; line-height: 1.7; }.eyebrow { font-size: 12px; letter-spacing: 2px; }.workspace { margin-top: 18px; }.toolbar, .inline { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; margin: 14px 0 22px; }.search { max-width: 360px; }.split, .review-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 24px; }.source-panel { border-left: 1px solid var(--el-border-color-light); padding-left: 24px; min-width: 0; }.fragment-list { max-height: 420px; overflow: auto; display: block; }.fragment { padding: 10px 12px; margin: 10px 0; border-radius: 6px; background: var(--el-fill-color-light); }.fragment p { white-space: pre-wrap; font-size: 13px; line-height: 1.7; overflow-wrap: anywhere; }.muted, .small { color: var(--el-text-color-secondary); }.small { font-size: 12px; margin: 22px 0 0; }.footer-actions { margin-top: 20px; }.check { margin: 12px 0; }.check pre { white-space: pre-wrap; overflow-wrap: anywhere; padding: 12px; background: var(--el-fill-color-light); font-size: 12px; }.paper-picker { max-height: 250px; overflow: auto; display: flex; flex-direction: column; align-items: flex-start; }.paper-question { padding: 20px 0; border-bottom: 1px solid var(--el-border-color-light); } :deep(.el-select) { min-width: 180px; } :deep(.el-form-item) { margin-top: 14px; } :deep(.el-checkbox__label) { white-space: normal; line-height: 1.5; } :deep(.el-checkbox) { height: auto; min-height: 32px; }
@media(max-width: 900px) { .split, .review-grid { grid-template-columns: 1fr; }.source-panel { border-left: 0; padding-left: 0; }.hero { align-items: flex-start; padding: 20px; }.hero h1 { font-size: 21px; } }
</style>
