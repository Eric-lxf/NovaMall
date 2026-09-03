# 智能命题运行与验收手册

版本：2026-09-03 候选版本。执行前阅读 [验证记录与未决门禁](ai-exam-mvp-progress.md)。具体环境是否已部署或启用，以对应流水线和后端能力查询为准；本手册不是已执行操作的证明。

## 1. 准备和迁移

需要 Java 17、MySQL 8、Redis、Node 20+；DOCX/PDF 另需可用的隔离文档镜像和 Docker。保留功能默认关闭，先备份数据库和私有文件，并验证可以恢复。

在**隔离测试库**先串行执行以下四个脚本。通过后，再由管理员安排实际数据库的维护窗口。脚本不会自动给普通角色授权，也没有加入现有 Compose 的自动初始化清单。

1. `sql/exam_schema.sql`：任务表。
2. `sql/exam_workflow_schema.sql`：19 个业务/关联/计量表。
3. `sql/exam_menu_seed.sql`：根目录和任务中心。
4. `sql/exam_workflow_menu_seed.sql`：工作台和细分权限。

使用 MySQL 客户端交互式 `SOURCE <已确认绝对路径>` 或安全的数据库管理工具导入，不在命令行或文档中放真实密码。确认菜单脚本返回的根/子菜单 ID 不是 NULL；如果 `/exam` 已属于其他功能，停止并处理冲突，不删除或重命名无关菜单。

DDL 使用 `CREATE TABLE IF NOT EXISTS`，**不会自动修复同名旧表的列/索引差异**。检查 `SHOW CREATE TABLE` 和索引；运行时 readiness 不是完整迁移审计。应验证幂等键、版本号及 task/slot 唯一约束存在。JSON 快照由应用严格校验后保存在 LONGTEXT 中，不依赖 MySQL 原生 JSON。

## 2. 配置与私有目录

| 环境变量 | 默认 | 用途 |
|---|---|---|
| `EXAM_ENABLED` | false | 创建命题服务及接口 |
| `EXAM_WORKER_ENABLED` | true | 开启后处理持久队列；故障排查可单独停 Worker |
| `EXAM_PRIVATE_ROOT` | 空 | 私有文件绝对路径，必须与所有公开资源目录分离 |
| `EXAM_AI_ENABLED` | false | 是否允许模型调用；还需逐资料和逐任务授权 |
| `EXAM_DOCUMENT_IMAGE` | 空 | 预先构建的可信文档镜像，例如 `novamall/exam-document:20260903` |

直接运行 JAR 时由启动脚本传入；`.env` 不会被 Java 自动加载。本地与生产 Compose 显式传入 `EXAM_ENABLED`、`EXAM_WORKER_ENABLED`、`EXAM_AI_ENABLED`，私有路径固定为 `/data/exam-private`，挂载仅供后端使用的 `exam_private_data` 命名卷，与公开的 `/data/uploads` 分离。容器以非 root 的 `app` 用户运行，私有根目录权限为 `0700`；升级和关闭功能保留该卷，不执行 `docker compose down -v`。

当前 Compose **不传入 `EXAM_DOCUMENT_IMAGE`，也不安装 Docker 客户端或挂载 socket**。仅启用基础模块时可用文本/TXT、人工审核与组卷、教师 XLSX；DOCX/PDF 解析和导出仍需单独接入经过审核的隔离运行环境。不要直接给现有公网后端挂载宿主机高权限 Docker socket。文档能力首次联调建议在专用测试主机使用专用服务账号、私有目录及受限/rootless Docker，禁止暴露未认证的 Docker TCP API。

私有根不能是 `/profile`、`/uploads` 对应目录及其父子目录，也不能通过链接指向公开目录。保留现有公开文件行为；考试文件只能经过带权限的下载接口。备份必须同时包含数据库和私有文件，恢复时核对 SHA-256。

### ECS / GitHub Actions 启用入口

先确认包含本配置修复的代码已合并。仓库 `Settings → Secrets and variables → Actions → Variables` 中设置以下普通变量；若 `production` 环境存在同名变量，以该环境的配置为准，避免两处值冲突。

| 变量 | 基础模块启用值 | 说明 |
|---|---|---|
| `EXAM_ENABLED` | `true` | 开启命题模块；未设置仍默认关闭 |
| `EXAM_SCHEMA_READY` | `true` | 仅在四份 SQL 执行并核对目标库后设置；这是人工迁移确认，不是自动结构校验 |
| `EXAM_WORKER_ENABLED` | `true` | 启用持久任务处理，也可以暂设 `false` 暂停队列 |
| `EXAM_AI_ENABLED` | `false` | 首次上线保持关闭；基础模块启用不等于允许真实模型调用 |

无需为 Compose 设置 `EXAM_PRIVATE_ROOT`，其容器路径和持久卷由编排固定。部署流水线依次将开关从 GitHub Variables 传到 SSH 会话、写入 ECS `.env`、传入后端容器；`TRUE`/`False` 等布尔值统一转为小写，其他非法值或未确认迁移时拒绝部署。它不会执行 SQL，也不会自动配置 Provider 或文档服务。

修改 GitHub Variables 本身不会重启运行中的容器。需运行 **Build, Push and Deploy to ECS**，使用已合并的 `master` 版本重新部署；流水线会初始化私有卷顶层目录权限并重建后端容器。不要仅修改服务器 `.env`，该文件会被下一次部署覆盖；普通 `docker restart` 也不会更新容器环境变量。

部署后重新登录，查看任务中心或浏览器已登录请求：`/exam/capabilities` 应返回 `enabled=true`、`taskReady=true`、`aiEnabled=false`，`/exam/workflow/capabilities` 应返回 `workflowReady=true`、`privateStorageReady=true`。请求需经过实际 API 代理前缀并携带正常登录认证。若只有 `enabled=true`，其他就绪项为 `false`，先核对后端实际连接的数据库、表结构和私有目录，再继续业务验收，不要重复导入整个商城初始化脚本。

## 3. 构建文档镜像

由管理员在专用环境从仓库根执行：

```sh
docker build -t novamall/exam-document:20260903 tools/exam-document-worker
```

镜像使用 python-docx 1.2.0、lxml 6.1.2、pypdf 6.16.2、LibreOffice Writer 和 Noto CJK 字体。运行时每个任务一个容器，无网络、只读根、非 root、无 capabilities、无主机目录挂载；输入输出只通过标准流，90 秒上限、512 MB 内存、1 CPU、64 PID。本地构建及实际解析/导出已通过，见 [Docker 联调记录](ai-exam-docker-validation.md)。最终生产仍需使用经过扫描并记录 digest 的镜像。

上传 DOCX 不交给 LibreOffice；先做 ZIP/安全 XML 文本提取。转换器只处理由服务端固定模板重新生成的 DOCX。扫描件无 OCR；加密、损坏、重复 ZIP 条目、活动内容和外部关系拒绝或明确报错。

`configured=true` 仅表示配置了合法镜像名，不保证 Docker、镜像或转换器可以运行。必须实际执行解析/导出验收。

## 4. 模型与权限

在现有「AI博客 → AI模型配置」中为 `exam_generate`、`exam_verify` 指定允许使用的 Provider/文本模型；不要在聊天或代码里粘贴密钥。当前仅支持 `openai_compatible` 类型的 HTTPS Chat Completions 接口，无自动跨供应商切换。独立复核可以配置另一模型，但不同模型也不等于答案一定正确。

任务记录模型和配置摘要，不复制密钥；密钥、模型或服务地址变化后需重新确认。UI 显示实际服务名/地址/模型；资料版本先授权外发，任务再确认调用和 token 预留上限。无授权不测试真实模型；不要把上传行为视为外发同意。

### DeepSeek 输出与思考预算（2026-09-03 修复）

知识点抽取按每批最多 4000 字符、8 个资料片段拆分，提示模型每批最多返回 8 个简洁知识点。提交和重试窗口会按实际批数推荐调用次数及总 Token 预留，不再给长资料固定预留 3 次调用。

| 环境变量 | 默认 | 用途 |
|---|---|---|
| `EXAM_EXTRACT_MAX_OUTPUT_TOKENS` | 8192 | 知识点抽取单次输出上限；DeepSeek V4 关闭思考 |
| `EXAM_GENERATE_MAX_OUTPUT_TOKENS` | 16384 | 题目生成及格式修复的单次输出上限 |
| `EXAM_VERIFY_MAX_OUTPUT_TOKENS` | 16384 | 独立求解和复核的单次输出上限 |
| `EXAM_GENERATE_REASONING_EFFORT` | low | DeepSeek V4 生成的思考强度 |
| `EXAM_VERIFY_REASONING_EFFORT` | low | DeepSeek V4 求解和复核的思考强度 |

输出上限允许 1024–32768，思考强度允许 `low`、`high`、`max`。根据 [DeepSeek 思考模式文档](https://api-docs.deepseek.com/zh-cn/guides/thinking_mode/)，适配器显式设置思考模式，避免默认高强度思考耗尽旧版 4096 输出上限。只对 `api.deepseek.com` 上的 `deepseek-v4-*` 模型发送这些专用参数；其他兼容服务不发送 DeepSeek 参数，但仍使用配置的输出上限。

这些可选变量已接入本地/生产 Compose 和 ECS 部署流水线，未设置时使用上表默认值。ECS 修改同名 GitHub Actions Variables 后需重新部署；仅在页面增加“总 Token 预留”不会改变后端的“单次输出上限”。策略纳入任务模型配置摘要，改动后需要重新确认任务授权。本次修复没有新增 SQL；已有命题表和菜单就绪时，只需更新前后端。

按最小权限分配，菜单查询需要 `exam:task:list`：

在若依角色菜单树中同时勾选「智能命题」父菜单和相应页面，再分配功能权限；只赋予按钮权限不保证动态路由可见。

- 命题人：自身资料/知识点/蓝图/题目/试卷操作；需要下载原件时另给 `exam:source:download`。
- 审核人：`exam:review:list` 查看队列和题目依据；批准还需要 `exam:review:approve`。这不会自动授予他人的原件下载或编辑权限。
- 交付人：`exam:paper:export`；教师卷和答案还需要 `exam:paper:answers`。
- AI 操作：单独授予 `exam:knowledge:extract`、`exam:question:generate`、`exam:question:verify`。
- 手动重试：需要已有 `exam:task:retry`，并保留对应的业务操作权限；管理员也不能替其他所有者复制任务。

适用于单组织内部使用，不是多客户隔离 SaaS。学生预览是后台无答案投影视图，不是公开学生答题网站。批准人会被记录，但没有强制“出题与审核必须不同人员”的四眼规则；可通过角色分离实施。

## 5. 操作流程

1. 导入授权资料 → 对照原文件核对片段，确认可用范围。文档解析在任务中心查看结果。
2. 手工建立知识点或按整份可用片段分批抽取。核对候选的逐字出处并确认；合并会生成新候选并停用原记录。
3. 建立蓝图，选择知识点，设置槽位的题型/难度/认知层级/分值及解析要求。本地要求解析仅作预填，未知字段和暂填分值必须人工处理。
4. 保存草稿并确认蓝图。确认后修改用“复制”；蓝图冻结资料/知识点/要求，模型配置在每次任务授权时冻结。
5. 手工录题或选择需要的槽位生成；通常每题 3 次模型调用，格式错误最多额外 1 次。先独立解题，再复核答案解析及评分点。预算包含修复调用。
6. 查看来源/复核结果，编辑会生成新版本。提交人工审核，逐题批准或退回；失败的 AI 复核不能直接批准原版。
7. 选择已批准题目编排试卷，核对排序、分值、时长，再定版。定版内容不可原地编辑。
8. 分别预览学生/教师卷，创建 DOCX/PDF/XLSX 导出任务，从导出中心私有下载。XLSX 含答案，仅教师版；不是用户 Excel 导入器。

失败或部分完成后，在“任务中心 → 重试”或“详情 → 手动重试未完成项”操作。知识点抽取、题目生成、独立复核、资料解析和试卷导出均支持；原任务及其已保存结果、调用账目不变，创建关联的新任务，只处理未成功的批次/槽位。旧版大批次抽取失败的任务也可使用新策略重试，不必重新导入资料。

AI 重试须重新核对当前模型、资料外发授权及预算；原调用费用不会撤销。若为“需人工确认”或存在不确定调用，还须确认可能重复远端执行/计费的风险。已经成功落库的项目不会重放；全部子项都成功时拒绝重试。资料版本、蓝图或题目状态不再满足要求时，先按提示核对，不绕过审核门禁。

双击或网络响应丢失时，继续点击“确认上次重试提交”，沿用相同请求与幂等键；每个原任务最多建立一个直接重试后继。如果新任务再次失败，应对新任务重试。排队中/执行中/已完成/已取消的业务任务不允许重试。基础自检保留原有最多运行三次的规则。

## 6. 硬限制和留存

- 单文件 10 MB；纯文本 20 万字符；PDF 50 页；最多 2000 个片段。图片、公式、文本框、页眉页脚不参与命题，表格和阅读顺序必须核对。
- 每张蓝图/试卷 1–50 题；时长 1–480 分钟；分值正数、最多两位小数；蓝图最多 10 个资料版本。
- 每人最多 3 个活动任务，全局最多 100 个；Worker 并发 2。每用户每日最多 500 次 AI 调用；单任务最多 150 次、200 万 token 保守预留。
- token 预留按输入字节加输出上限计算，不是精确 token 计数或货币封顶。供应商未报告 usage 时记为未知；不确定计费不能当成 0。
- 私有文件每用户 512 MB、命题模块总计 10 GB。已上传/已导出文件默认保留，不做自动到期删除；停用是逻辑操作。自动归档、法律留存期限、管理员清除工具尚未实现，试点前需要确认策略，达到配额时阻断新增写入。
- 页面主列表及表单候选可游标载入较早记录；每批 200 条，知识点 500 条。搜索仅筛选已加载记录；大规模题库仍需更完善的索引检索，不承诺当前适合海量数据。
- XLSX 为可编辑题库而非打印试卷；极长内容可能超过 Excel 单行显示高度，内容不截断，可在编辑栏读取/调整行高。正式打印请使用经过分页验收的 DOCX/PDF。

## 7. 测试命令

以下不使用真实模型。Java 测试在临时 H2 库执行，不连接生产库。

```sh
cd backend
mvn -B -pl ruoyi-exam -am test -DfailIfNoTests=false
mvn -B -DskipTests package -pl ruoyi-admin -am
```

```sh
cd frontend
npm ci
npm run test:exam
npm run build:prod
```

文档用例先按 `scripts/exam-p0/README.md` 用合适中文字体生成自建输入样本。用隔离 Python 环境安装 `tools/exam-document-worker/requirements.txt`，另备样本生成所需 ReportLab/Pillow。然后生成 Java 试卷快照并执行工作进程测试（`examSampleOutput` 使用已确认的仓库绝对路径）：

```sh
cd backend
mvn -B -pl ruoyi-exam -am test -DfailIfNoTests=false -DexamSampleOutput=/absolute/NovaMall/tmp/exam-mvp-qa
```

```sh
cd tools/exam-document-worker
python -m unittest -v test_worker
```

前端只读夹具：`cd frontend` 后执行 `npx vite --config tests/exam-ui.vite.config.mjs`，打开 `http://127.0.0.1:5193/tests/exam-ui.html`。页面有明确测试标识；所有修改请求拒绝，不能当成已完成前后端联调。不要将此夹具作为部署入口。

重试页面使用独立的内存夹具，模拟失败、部分提交响应丢失和不确定计费确认，不访问后端或模型：先启动 `npx vite --config tests/exam-retry-ui.vite.config.mjs`，再用已有 Playwright 安装运行 `node tests/exam-retry-browser.mjs`。可用 `PLAYWRIGHT_PACKAGE` 指定包路径；没有捆绑浏览器时可设置 `PLAYWRIGHT_CHANNEL=msedge` 或 `chrome` 使用已安装浏览器。截图写入 `tmp/exam-ai-retry-qa/`，不纳入生产构建。

真实 Docker/MySQL/JWT 验收：使用 [scripts/exam-local/README.md](../scripts/exam-local/README.md)。该脚本仅创建独立、唯一命名的本地测试容器及 RAM 数据库，不读取项目 `.env`，关闭 AI，最后清理自己的容器。它不是生产部署脚本。真实浏览器 QA 配置 `tests/exam-live.vite.config.mjs` 仅代理到隔离后端 18080，无请求 Mock。

## 8. 故障处理与回退

| 错误/状态 | 处理 |
|---|---|
| 表未就绪 | 保持功能关闭，核对四个迁移脚本；不自动改生产表 |
| `EXAM_STORAGE_NOT_READY` / `EXAM_STORAGE_QUOTA` | 检查私有绝对路径、权限、空间及留存；不得改成公共上传目录 |
| `EXAM_DOCUMENT_WORKER_NOT_READY` / `EXAM_CONVERTER_MISSING` | 检查镜像、Docker 和 LibreOffice；失败不是有效 PDF |
| `EXAM_SOURCE_AUTH_CHANGED` / `EXAM_SOURCE_UNAVAILABLE` | 核对版本/可用片段/外发授权，再创建新的明确任务 |
| `EXAM_BUDGET_EXCEEDED` | 已完成结果保留，核对调用账目后决定是否追加；不自动增加预算 |
| `EXAM_OUTPUT_TRUNCATED` / 结束原因 `length` | 模型达到单次输出上限；检查上述输出/思考策略。部署修复后通过任务中心手动重试未完成项；仍失败时进一步缩小资料范围或经确认调整单次上限，不把截断内容当成有效结果 |
| `EXAM_RETRY_ALREADY_CREATED` | 已有直接重试后继，进入原任务详情查看新任务，不反复重试原任务 |
| `NEEDS_CONFIRMATION` / `EXAM_RESULT_UNCERTAIN` | 可能已调用或计费；先查供应商请求记录和本地已保存结果，不盲目重试 |
| 遗留的 `DISPATCHING` 调用记录 | 进程中断或取消可能使使用量无法写回；按计费未知处理，不等于请求未发出或费用为零 |
| `EXAM_VERSION_CONFLICT` | 刷新并比较新版本，不能覆盖他人修改 |

先设置 `EXAM_AI_ENABLED=false` 阻止新模型调用，必要时暂停 Worker 并检查在途任务；模型服务器已收到的请求无法追回。回退应用设置 `EXAM_ENABLED=false`，保留新增表、审计和私有文件，不执行 DROP/清空目录。旧版本应用不依赖这些新增表。

真实试点前还需执行两用户登录、匿名下载拒绝、重启/撤权/取消竞争、数据库迁移重跑、资源压力、中文 DOCX/PDF 逐页检查、备份恢复，以及领域审核的事实与成本评估。当前未记录“上线验收通过”。
