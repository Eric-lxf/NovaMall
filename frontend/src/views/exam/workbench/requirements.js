// Local, deliberately bounded requirement parsing. No model call and no automatic confirmation.
export function parseRequirements(raw) {
  const text = String(raw || '').trim()
  if (!text || text.length > 4000) throw new Error('请输入不超过 4000 字的要求')
  const groups = [], warnings = ['本地规则只识别明确的题型、阿拉伯数字题量/分值、总分和时长；对象、范围、难度比例及其他要求请人工核对。']
  const types = { 单选: 'SINGLE_CHOICE', 多选: 'MULTIPLE_CHOICE', 判断: 'TRUE_FALSE', 简答: 'SHORT_ANSWER' }
  for (const [label, type] of Object.entries(types)) {
    const direct = new RegExp(`${label}(?:题)?\\s*(\\d+)\\s*(?:题|道)?(?=\\s|[，,。；;]|$)(?:\\s*[，,]?\\s*每题\\s*(\\d+(?:\\.\\d{1,2})?)\\s*分)?`, 'g')
    const reverse = new RegExp(`(\\d+)\\s*(?:道|题)\\s*${label}(?:题)?(?:\\s*[，,]?\\s*每题\\s*(\\d+(?:\\.\\d{1,2})?)\\s*分)?`, 'g')
    const matches = [...text.matchAll(direct), ...text.matchAll(reverse)]
    if (matches.length > 1) throw new Error(`${label}题出现多组数量，请保留一组明确要求`)
    if (!matches.length) continue
    const count = Number(matches[0][1]), score = Number(matches[0][2] || 1)
    if (count < 1 || count > 50 || score <= 0 || score > 10000) throw new Error(`${label}题数量或分值超出范围`)
    if (!matches[0][2]) warnings.push(`${label}题未明确每题分值，暂填 1 分，请修改后再保存`)
    groups.push({ type, count, score })
  }
  const count = groups.reduce((sum, g) => sum + g.count, 0)
  if (!count || count > 50) throw new Error('需明确 1–50 题，例如：单选题 10 道，每题 2 分；判断题 5 道，每题 1 分')
  const duration = [...text.matchAll(/(?:时长|限时|时间)\s*(\d+)\s*分钟/g)]
  const total = [...text.matchAll(/(?:总分|满分)\s*(\d+(?:\.\d{1,2})?)\s*分?/g)]
  if (duration.length > 1 || total.length > 1) throw new Error('时长或总分有多处定义，请消除歧义')
  const sum = groups.reduce((value, g) => value + g.count * Math.round(g.score * 100), 0) / 100
  const totalScore = total.length ? Number(total[0][1]) : sum
  const durationMinutes = duration.length ? Number(duration[0][1]) : null
  if (durationMinutes !== null && (durationMinutes < 1 || durationMinutes > 480)) throw new Error('时长须为 1–480 分钟')
  if (totalScore <= 0 || totalScore > 10000) throw new Error('总分须为 0–10000 之间的正数')
  if (sum !== totalScore) warnings.push(`每题分值合计 ${sum} 分，和目标 ${totalScore} 分不符；请手动调整，系统不会自动改分`)
  return { raw: text, groups, count, totalScore, durationMinutes, warnings }
}
