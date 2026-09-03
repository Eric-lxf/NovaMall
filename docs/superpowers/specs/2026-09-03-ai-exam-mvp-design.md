# AI 智能命题 MVP 详细设计（P0 工作基线）

日期：2026-09-03

项目：NovaMall
状态：技术设计草案与离线验证已形成；客户范围、预算、留存及真实模型验证尚未冻结。

实施更新：已按用户要求进入不依赖模型的 P1 任务基础。当前任务表使用自增 BIGINT 与显式 JDBC 条件更新；其他表及完整状态流程仍为目标设计，详见 [P1 实现与差异](../../ai-exam-p1-progress.md)。

关联：[开发计划](../../ai-exam-development-plan.md) · [验收标准](../../ai-exam-acceptance.md) · [P0 验证记录](../../ai-exam-p0-validation.md)

## 1. 本轮决定及边界

在既有管理后台增加独立 `ruoyi-exam` 模块，首版只做“授权资料 → 知识点 → 命题蓝图 → 四类题目 → 自动检查与人工审核 → 定版和导出”。不做在线考试、支付、多租户、OCR 或高风险考试自动发布。

暂按企业一般知识培训设计。当前没有确认的真实客户资料、审核专家或外部模型发送授权；本轮仅用自编虚构手册。本文是供开发评审使用的基线，不表示上述业务条件已经批准。

复用 Java 17、Spring Boot、MyBatis、Vue 3、Element Plus、既有用户/角色及数据库 AI Provider。仅参考 History 的分页解析和任务页面，不依赖其业务实体。P0 脚本不注册为后端业务模块。

## 2. 页面与最短操作流程

| 页面 | 主要内容和操作 | 必须可见的异常状态 |
|---|---|---|
| 资料库 `/exam/source` | 新建/上传、版本列表、解析预览、原文定位、停用；预览后确认可用片段 | 待解析、缺少文本层、加密、损坏、布局需确认、部分内容不支持 |
| 命题工作台 `/exam/workbench` | 依次确认资料、知识点、题型数量/分值/时长、槽位蓝图；自然语言只填表不直接执行 | 依据不足、题数/总分不符、模型未配置、未授权外发 |
| 题库 `/exam/question` | 题干与答案编辑、右侧原文依据、检查问题、另存版本、单题重做 | 草稿、待审、驳回、通过；另列来源失效风险 |
| 审核中心 `/exam/review` | 题目与原文对照、独立求解差异、逐项批准/驳回 | 版本已变化、依据不可用；不得把检查通过当成人工批准 |
| 试卷管理 `/exam/paper` | 选择已通过版本、排序与分值、学生/教师预览、定版、导出 | 未审题、来源停用、分值冲突、转换器不可用 |
| 任务中心 `/exam/task` | 步骤、成功/失败槽位数、脱敏错误、取消、有限重试和费用风险提示 | 部分成功、结果不确定、等待人工决定、取消不等于退费 |

上述是低保真交互说明，尚无实际页面。导航不增加独立学生端。页面按钮隐藏只是辅助，后端始终校验权限。

## 3. 数据模型与约束

### 3.1 公共约定

新表使用 `exam_` 前缀；主键采用项目适配的服务端生成 `BIGINT`，对前端以字符串传递，避免 JavaScript 精度问题。状态使用有界 `VARCHAR(32)`；摘要 `CHAR(64)`；分数 `DECIMAL(8,2)`；原文使用 `LONGTEXT`；结构化快照使用 MySQL JSON。审计时间沿用 `Asia/Shanghai`，明确创建人、更新时间及软停用状态。

所有主体有 `owner_user_id` 和 `revision`；从属表通过可验证父链确定所有者。更新主体必须带 `expectedRevision`，服务端条件更新失败返回版本冲突。JSON 只保存不可变内容，不替代可索引关系。迁移 DDL 在 P1 评审并形成独立 SQL；当前没有创建表或执行 SQL。

### 3.2 核心表

以下列出必需业务字段；`id`、审计列等公共字段省略。所有外部传入 ID 均需鉴权及关系一致性校验。

| 表 | 必需字段 | 唯一键/主要索引 |
|---|---|---|
| `exam_file` | owner、用途、storage_key、sha256、MIME、size_bytes、状态、expires_at | storage_key 唯一；owner/用途/创建时间；状态/过期时间 |
| `exam_source` | owner、标题、current_version_id、enabled、revision | owner/enabled/更新时间 |
| `exam_source_version` | source_id、version_no、file_id、sha256、parse_status、warnings_json、parser_version、text_length | source_id/version_no 唯一；parse_status |
| `exam_source_fragment` | source_version_id、ordinal、text、text_sha256、locator_json、usable | source_version_id/ordinal 唯一 |
| `exam_knowledge_point` | owner、name、parent_id、revision、confirmed、enabled | owner/enabled；parent_id |
| `exam_knowledge_source` | knowledge_point_id、source_version_id、fragment_id、quote | knowledge_point_id/fragment_id 唯一；source_version_id |
| `exam_blueprint` | owner、root_id、version_no、status、requirements_json、source_versions_json、knowledge_snapshot_json、slots_json、content_hash | root_id/version_no 唯一；owner/status |
| `exam_task` | owner、kind、idempotency_key、request_hash、input_json、status、attempt_no、lease_owner、lease_until、heartbeat_at、next_run_at、budget_json、progress_json、error_code、revision | owner/kind/idempotency_key 唯一；status/next_run_at/lease_until |
| `exam_task_item` | task_id、slot_id、generation_no、status、attempt_no、result_version_id、error_code | task_id/slot_id/generation_no 唯一；task_id/status |
| `exam_question` | owner、current_version_id、enabled、source_risk、revision | owner/enabled/source_risk |
| `exam_question_version` | question_id、version_no、blueprint_id、slot_id、content_json、content_hash、review_state、prompt_version、schema_version | question_id/version_no 唯一；blueprint_id/slot_id |
| `exam_question_source` | question_version_id、source_version_id、fragment_id、quote、quote_hash | question_version_id/fragment_id/quote_hash 唯一；source_version_id；fragment_id |
| `exam_question_check` | question_version_id、content_hash、check_kind、checker_version、ai_call_id、result_json、status | question_version_id/check_kind/创建时间 |
| `exam_question_review` | question_version_id、content_hash、reviewer_id、decision、reason、request_key | reviewer_id/request_key 唯一；question_version_id/创建时间 |
| `exam_paper` | owner、title、current_version_id、draft_json、status、revision | owner/status |
| `exam_paper_version` | paper_id、version_no、content_hash、total_score、duration_minutes、template_version、source_risk | paper_id/version_no 唯一 |
| `exam_paper_item` | paper_version_id、ordinal、question_version_id、score、question_snapshot_json、rubric_snapshot_json | paper_version_id/ordinal 唯一；question_version_id |
| `exam_export` | owner、paper_version_id、format、audience、template_version、task_id、file_id、status、expires_at | task_id 唯一；paper_version_id/audience/format |
| `exam_ai_call` | task_id、task_item_id、call_no、provider_id、model、prompt_version、request_id、status、usage_json、usage_status、duration_ms、estimated_cost、error_code | task_id/call_no 唯一；task_item_id；provider_id/创建时间 |

索引名在 DDL 阶段统一。关系表避免依赖 JSON 扫描做来源影响分析。外键是否落库按仓库迁移规范评审；无论是否建外键，服务端都必须校验从属关系，禁止级联删除历史题目或审核证据。

### 3.3 不可变范围

- 资料版本的原始内容、解析快照和片段在完成后冻结；重新解析产生新版本，不让旧 `fragmentId` 指向新文字。
- 知识点可编辑，但冻结蓝图保存其名称、定义及引用快照。已确认蓝图不原地改槽位，创建新版本。
- 题目版本的 `content_json` 不变；审核状态单独更新且记录历史。修改任何答案、解析、选项或依据产生新内容版本。批准绑定版本 ID 与内容摘要。
- 试卷定版采用已批准的明确版本，复制内容、评分点与排序快照，不从“最新题目”动态渲染。
- 资料停用/升级标记受影响题目及历史卷；阻止新的正式定版。历史内容不静默改写，重新导出显示风险并要求有权限者确认。真正清除按受控流程处理。

## 4. 来源协议与四类题目

### 4.1 原文位置

PDF 定位保存真实 `pageNumber`（从 1 开始）及页内片段序号；DOCX 保存 `bodyElementIndex` 与 `paragraphIndex`，表格使用 `tableIndex/rowIndex/cellIndex`，不伪造 Word 页码；TXT 保存行号范围。块内引用保存原文及偏移，片段合并不能跨页却只保留一个错误页码。

双栏、浮动文本框、公式和图片不能只因提取到了部分文字就报告成功。首版进入 `NEEDS_REVIEW`，展示限制；只允许用户明确确认的可用文本片段进入蓝图。纯扫描件、加密件、损坏件直接给出不支持/失败结果。本次只验证了简单 DOCX 段落/表格次序及 PDF 页界，不代表任意版式已支持。

### 4.2 模型返回协议 `exam.question.v1`

根字段仅 `schemaVersion`、`questions`；一次请求最多 5 个槽位，返回集合必须与本次请求槽位一致。完整黄金样本见 [question-batch.json](../../../scripts/exam-p0/samples/question-batch.json)，可信蓝图范围见 [slots.json](../../../scripts/exam-p0/samples/slots.json)。

| 适用题型 | 必需字段 | 规则 |
|---|---|---|
| 所有题目 | slotId、type、stem、analysis、knowledgePointIds、sourceRefs | 题干最多 2000 字符，解析最多 4000；知识点 1–8 个且属于槽位；引用 1–8 条 |
| 单选/多选 | options、correctOptionIds | 2–6 个选项，ID 顺序 A–F；文字非空且不重复；单选恰好 1 个正确项，多选至少 2 个，均为已有选项 |
| 判断 | answerBoolean | 真正 JSON boolean，不接受字符串或数字 |
| 简答 | referenceAnswer、rubric | 参考答案最多 4000 字符；1–10 个不重复评分点，正整数权重合计 100 |
| 每个引用 | sourceVersionId、fragmentId、quote | 来源版本/片段必须属于本次授权范围及槽位；引文须为归一化片段中的原文子串 |

严格 JSON 解析拒绝重复属性、额外字段、Markdown 包裹、NaN、未知题型、过大响应。分值来自蓝图，不由模型决定；模型不得返回 owner、审核状态或业务题目 ID。首版模型返回只允许纯文本内容，页面按文本转义；不渲染模型返回的 HTML。

代码验证“引用确实存在”不能证明“引用支持答案”。后续必须独立求解、对照依据并人工审题；材料中的指令只作为待分析资料，不赋予调用工具或修改系统配置的能力。提示词注入不能仅靠删除关键词解决。

P0 的 Python 校验器是可执行的协议样本，使用单个资料版本目录，未接数据库/权限系统，也不是完整 JSON Schema 引擎。生产 Java 校验器应复用同一黄金样本，并额外验证多资料版本、数据库归属、当前停用状态和模型截断/拒绝状态。

## 5. 接口基线

统一前缀 `/exam`，列表沿用 `rows/total/code/msg`；对象返回 `code/msg/data`。长操作提交响应示例：

```json
{"code":200,"msg":"任务已排队","data":{"taskId":"123456789012345","status":"QUEUED"}}
```

业务错误使用稳定 `errorCode` 和脱敏 `msg`。这是 JSON 业务码，不等同于 HTTP 状态；保留现有认证中间件及统一响应习惯，不擅自全局更改其他模块。前端考试 API 适配器需保留错误码而非只抛通用字符串。所有 ID 按字符串收发。

| 方法与路径 | 核心请求/返回 | 约束 |
|---|---|---|
| `POST /sources` | multipart：title、file；或 JSON：title、text（二选一） → sourceId/versionId/taskId | 10 MB 上限；类型不符拒绝；服务端推导 owner；不接受路径/远程 URL |
| `POST /sources/{id}/versions` | 新资料内容、expectedRevision → versionId/taskId | 新版本不覆盖旧文件 |
| `GET /sources`、`GET /sources/{id}` | 受限分页、资料和版本摘要 | 不泄露跨用户标题、计数 |
| `GET /source-versions/{id}/fragments` | 带位置的可分页原文 | 数据权限；不返回内部物理路径 |
| `POST /source-versions/{id}/confirm` | 可用片段 IDs、expectedRevision、限制确认 | 未支持内容不能假装已解析 |
| `GET /source-versions/{id}/download` | 原件 | 独立下载权限，附件响应、不内联执行 |
| `POST /knowledge-points/extract` | sourceVersionIds、外发确认记录引用、预算 → taskId | AI 授权和片段权限 |
| `GET/POST /knowledge-points`、`PUT /knowledge-points/{id}` | 知识点及原文引用；更新带 expectedRevision | 完全支持人工建立；确认操作记录版本 |
| `POST /requirements/parse` | 自然语言、sourceVersionIds、预算 → taskId | 仅产出建议表单；用户确认后生效 |
| `POST /blueprints`、`PUT /blueprints/{id}` | 要求、知识点、槽位、expectedRevision | 校验题数、分值、范围；仅草稿可更新 |
| `POST /blueprints/{id}/confirm` | expectedRevision、contentHash | 冻结资料、知识点和槽位快照 |
| `POST /blueprints/{id}/generate` | expectedRevision、modelApprovalId、budget、`Idempotency-Key` 请求头 → taskId | 同键同参返回原任务；同键异参拒绝 |
| `GET /tasks`、`GET /tasks/{id}` | 状态、进度、脱敏错误及风险 | 限定 owner 或显式运维范围 |
| `POST /tasks/{id}/cancel`、`POST /tasks/{id}/retry` | expectedRevision；重试含确认及新幂等键 | 仅失败项；不确定计费需明确确认 |
| `GET /questions`、`GET /questions/{id}` | 列表与有权读取的版本详情 | 来源与答案分别受资源和操作权限约束 |
| `POST /questions`、`POST /questions/{id}/versions` | 四类内容、expectedRevision | 手工题与 AI 题均校验结构及依据 |
| `POST /question-versions/{id}/check`、`POST /question-versions/{id}/submit-review` | contentHash；检查可返回 taskId | 绑定内容版本，检查不改写答案 |
| `POST /question-versions/{id}/reviews` | contentHash、decision、reason、requestKey | 只允许人工身份；不能重复/过期批准 |
| `POST /papers`、`PUT /papers/{id}` | 标题、时长、题目版本/排序/分值、expectedRevision | 草稿阶段也逐项鉴权 |
| `POST /papers/{id}/finalize` | expectedRevision → paperVersionId | 短事务再次检查全部审核与来源状态 |
| `POST /paper-versions/{id}/exports` | format：DOCX/PDF/XLSX；audience：STUDENT/TEACHER；幂等键 → taskId/exportId | XLSX 仅教师题库格式；转换器未就绪拒绝 PDF |
| `GET /exports/{id}`、`GET /exports/{id}/download` | 状态或附件流 | 每次鉴权；学生视图不读取/序列化答案 DTO |

蓝图槽位必含 `slotId/type/knowledgePointIds/sourceFragmentIds/score/targetDifficulty`；题目分数最多两位小数，总题数 ≤ 50。总分精确按十进制计算。目标难度是设计标签，不是经答题数据校准的测量结果。

主要错误码：`EXAM_DISABLED`、`EXAM_NOT_READY`、`EXAM_RESOURCE_NOT_FOUND`（资源不存在与无数据范围统一）、`EXAM_VERSION_CONFLICT`、`EXAM_IDEMPOTENCY_CONFLICT`、`EXAM_FILE_UNSUPPORTED`、`EXAM_PARSE_LIMIT`、`EXAM_LAYOUT_REVIEW_REQUIRED`、`EXAM_AI_NOT_AUTHORIZED`、`EXAM_OUTPUT_INVALID`、`EXAM_EVIDENCE_INVALID`、`EXAM_BUDGET_EXCEEDED`、`EXAM_RESULT_UNCERTAIN`、`EXAM_REVIEW_REQUIRED`、`EXAM_SOURCE_UNAVAILABLE`、`EXAM_EXPORT_NOT_READY`。

## 6. 权限与私有存储

| 身份 | 默认允许 | 默认不允许 |
|---|---|---|
| 命题人 | 操作自己的资料、知识点、题目、任务和试卷 | 读取他人资源、替他人批准、仅凭 ID 下载文件 |
| 审核人 | 持有 `exam:review:list/approve` 后查看组织内待审题及必要引用 | 自动拥有全部原件下载、修改他人题库、导出全部教师卷 |
| 考试业务管理员 | 显式授予 `exam:admin:manage` 后管理本组织考试资源及任务 | 用考试权限绕过外发授权、留存或审计规则 |
| 系统超级管理员 | 遵循既有平台超级管理员策略；其特权必须在部署文档说明 | 不宣传为对平台管理员不可见的加密隔离 |

普通权限示例：`exam:source:list/add/edit/download`、`exam:question:list/edit`、`exam:paper:list/edit/finalize`、`exam:export:student/teacher`、`exam:task:list/cancel/retry`。权限点与数据范围取交集；批量请求中混入任何越界 ID 时整批拒绝且不泄漏该 ID 的细节。

用户只提交资源 ID，服务器解析私有路径。配置 `exam.storage.privateRoot` 必须位于公开 `/profile/**`、`/uploads/**` 映射之外，启动时检查重叠路径与目录权限。文件名用服务端随机 key，保留原名仅作显示；下载进行路径归一化、符号链接/重解析点逃逸检查，附件头安全编码，`Cache-Control: no-store`。私有临时文件采用独立任务目录，不允许输入文件名指定输出路径。

原件、题目依据和定版记录默认不自动物理删除；用户“删除”先停用。开发建议导出缓存 7 天、失败/取消的无引用临时文件 24 小时后清理；这些是待业务确认的运行参数，清理器在确认前关闭。清理仅针对已登记、无引用、无有效租约且处于指定私有临时根的文件。备份和彻底删除另有授权流程，不承诺缓存清理等于所有副本清除。

## 7. 任务状态与可靠性

任务采用 DB 持久化与有界执行器，内存只做唤醒提示。所有领取/落库是短事务，网络与转换进程不占用数据库事务。

| 当前状态/事件 | 下一状态/处理 |
|---|---|
| QUEUED 被领取 | CAS 更新为 RUNNING，增加 attempt_no，写租约和 Worker 标识 |
| RUNNING 正常结束 | 全部成功 SUCCEEDED；有成功有失败 PARTIAL_SUCCESS；全失败 FAILED |
| QUEUED 取消 | CANCELLED；禁止再领取 |
| RUNNING 取消 | CANCEL_REQUESTED，阻止新调用和结果提交；完成回收后 CANCELLED |
| RUNNING 租约过期 | 先核对调用日志；可安全重放则重新排队；已发外部调用且结果未知则 NEEDS_CONFIRMATION |
| NEEDS_CONFIRMATION | 用户明确决定重试或取消；保留前次可能发生费用的记录 |
| FAILED/PARTIAL_SUCCESS 重试 | 为失败槽位创建新运行代次/尝试，保留成功结果与审计；幂等键去重 |
| 任意旧 Worker 回来 | attempt_no/租约不匹配，不得写入题目或覆盖新状态；记录迟到结果元信息 |

建议初值：心跳 15 秒、租约 60 秒、单次模型调用 120 秒；租约由独立心跳刷新，不能把长调用误判为僵死。更新结果必须同时匹配 RUNNING、当前代次和有效租约，取消后迟到结果也不得提交。

外部调用前写 `exam_ai_call` 为 DISPATCHING；调用/响应落库间的崩溃可能无法判断是否计费，保守进入人工确认。只有确认尚未发出、或服务商提供已验证的可靠幂等语义时才安全自动重放。数据库唯一键不保证外部调用恰好一次。

每子任务最多追加 2 次尝试，结构修复、限流和网络重试共享次数与预算；鉴权失败、模型禁用、超限文件不自动重试。单实例最多 2 个活跃模型调用、单用户最多 1 个活跃 AI 任务；重试不会重建整张卷。

## 8. AI 授权、成本及质量门禁

`ExamAiGateway` 适配既有 AI Provider。新增 `exam_generate/exam_verify` 模块编码；扩展底层响应保留实际 Provider/模型、usage、停止原因及 request ID，同时兼容旧字符串接口。密钥仍由既有配置管理，任务和日志不复制密钥。

每次任务固定获准服务商、模型、提示模板、资料版本和预算，工作线程执行前重新核对用户权限与外发授权。配置变化或被禁用时暂停/失败，不静默跨服务商发送私有资料。没有获准模型时，人工资料/知识点/命题编辑仍可用。

预算请求必须包含最大调用数、最大输入/输出 Token 预算及总时限；有已确认价格配置才显示金额上限。具体数值在选定模型及样本实测后冻结，不能假定每种模型上下文/价格相同。每次调用前原子预留预算，结束后结算实际 usage；缺失 usage 记 UNKNOWN，保守保留预留量，不按零费用继续无限调用。估算与实计分开保存。

质量次序为：结构检查 → 来源有效性 → 规则/重复检查 → 独立求解与依据复核 → 人工批准。独立复核不读取原答案后直接附和；先依据题干与来源求解，再比较。可使用同一获准模型独立调用，但不宣传为真正独立专家。矛盾、答案错误和无法解释的依据问题阻断定版；人工修改后重新执行受影响检查。

## 9. 解析与导出技术门槛

当前 POI 4.1.2/PDFBox 2.0.32 的自编样本兼容性探针通过，不构成不可信文档处理安全保证。依赖安全调查、隔离和许可问题见 [P0 验证记录](../../ai-exam-p0-validation.md)。接收真实 DOCX 前必须评估/更新受影响依赖并回归既有 Excel 等功能，或采用经审核的隔离解析组件，不能仅增加一个重复 ZIP 名检测就称已修复全部风险。

生产解析/转换进程要求：非特权用户、禁止网络、只读运行镜像、输入只读挂载、独立可写临时目录、禁用宏与外链更新、墙钟超时、内存/CPU/进程数限制。文件魔数、ZIP 项数、解压总量、压缩比例和递归深度均设上限；初始解析超时 30 秒、解压后内容 100 MB、ZIP 项数 10000，最终按测试校准。仅 Java 线程超时不能保证终止异常解析，应具备可终止的进程边界。

DOCX 用明确模板生成；PDF 优先从同一 DOCX 经隔离 LibreOffice 转换，避免两套排版语义不一致。当前环境缺少 LibreOffice，Docker 引擎不可用，尚未验证该路线的字体、分页、资源占用和稳定性。本轮直接生成的 PDF 只验证输入解析，不是转换替代品。若无法部署转换器，需重新评估独立 PDF 模板的成本并明确缩减/调整 P6，不能把缺少 PDF 的版本宣称为原 MVP 完成。

学生版采用独立白名单 DTO，仅允许题干、选项、分值、顺序和作答空间；不从含答案的 HTML 隐藏字段后转出。教师版附答案、解析、评分点和依据。XLSX 为教师题库固定列，所有用户/模型文本写字符串单元格，防止公式注入。所有格式均检查隐藏字段、批注、修订、书签/链接与元数据是否携带答案或内部路径。

## 10. P0 出口与 P1 入口

已有：样本构建脚本、9 项 Java 解析探针、24 项离线题目协议测试、PDF 样本视觉检查、页面与数据/API/权限基线。

尚缺：首批实际使用者和审核者、真实资料授权、外部 AI 处理范围、人工命题基线与业务质量阈值、真实模型试验、转换器验证、解析依赖整改方案及留存/预算参数确认。详细 DDL 在这些边界评审后落地。

当前 P0 为“部分完成”，不宣布正式冻结或进入试点。后续可先评审本设计并做 Mock AI 的模块基础；不应在未补齐安全与授权条件时开放真实上传、调用付费模型或部署生产。
