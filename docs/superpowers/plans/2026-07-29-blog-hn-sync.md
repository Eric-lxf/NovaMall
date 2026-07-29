# Blog HN Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 Hacker News 四榜（news/past/show/jobs）同步入库、增量中文翻译，并以类博客体验在 `/blog/hn` 只读展示；后台支持按榜一键拉取与定时任务。

**Architecture:** 独立表 `blog_hn_item` + `blog_hn_rank`；`HnClient` 调 Firebase API；`HnSyncService` 负责拉榜→upsert→写快照→增量翻译；公开 API 只查 DB；Quartz `blogHnTask.syncAll()` 每 30 分钟；管理端异步触发同服务。不写入 `blog_article`。

**Tech Stack:** Java 17、Spring Boot、MyBatis-Plus、OkHttp、现有 `DeepSeekService` + `ai_prompt_template`、Vue3 + Element Plus、MySQL `nova_mall`、Quartz

**Spec:** `docs/superpowers/specs/2026-07-29-blog-hn-sync-design.md`

## Global Constraints

- 在 `backend/` 下编译：`mvn -B -DskipTests package -pl ruoyi-admin -am`
- POST 用 `@RequestBody`（可用空 DTO）；GET 禁止 `@RequestBody`
- 公开读路径**禁止**调用 HN；仅同步路径可访问外网
- Past 文案须标明基于 `beststories`（精选/热门），非站点日历 Past
- Show/Jobs UI 注明「目标 500，实际以接口为准」
- 公开列表仅 `status=1` **且** `title_zh` 非空
- 时区 Asia/Shanghai
- 本仓库无统一 JUnit CI；验证以 **Maven 编译 + SQL 执行 + API/浏览器冒烟** 为主
- 实现前新建分支：`feat/blog-hn-sync`（基于最新 `master`）

## File Map

| Path | Responsibility |
|------|----------------|
| `sql/blog_hn_schema.sql` | DDL + TRANSLATE 提示词种子 |
| `sql/blog_hn_menu_seed.sql` | 菜单 2050 + 按钮权限 |
| `sql/blog_hn_job_seed.sql` | Quartz 任务种子 |
| `.../blog/domain/BlogHnItem.java` | 帖实体 |
| `.../blog/domain/BlogHnRank.java` | 榜快照实体 |
| `.../blog/constant/HnBoard.java` | board 枚举 + API path + cap |
| `.../blog/mapper/BlogHnItemMapper.java` | BaseMapper + 自定义查询 |
| `.../blog/mapper/BlogHnRankMapper.java` | BaseMapper + max snapshot |
| `.../blog/service/hn/HnClient.java` | Firebase HTTP |
| `.../blog/service/hn/HnSyncService.java` | 同步编排 + 防并发 |
| `.../blog/service/BlogHnItemService.java` | 管理/公开读 |
| `.../blog/task/BlogHnTask.java` | Quartz bean |
| `.../blog/controller/BlogHnController.java` | 管理 API |
| `.../blog/controller/PublicHnController.java` | `@Anonymous` 公开 API |
| `frontend/src/api/blog/hn.js` | 管理端 API |
| `frontend/src/api/blog/publicHn.js` | 公开 API |
| `frontend/src/views/blog/hn/index.vue` | 后台 HN 页 |
| `frontend/src/views/public/blog/hn/index.vue` | 前台列表 |
| `frontend/src/views/public/blog/hn/detail.vue` | 前台详情 |
| `frontend/src/router/index.js` | `/blog/hn` 路由（在 `:id` 前） |
| `frontend/src/layout/BlogPublicLayout.vue` | 导航入口 |
| `README.md` | SQL 执行顺序追加 |

**Quartz 白名单：** `ScheduleUtils.whiteList` 仅校验管理端新增/修改任务；SQL 种子与 `mallOrderTask` 一样可运行。本计划额外把 `com.ruoyi.blog.task` 加入 `Constants.JOB_WHITELIST_STR`，便于后台改 cron。

---

### Task 1: DDL + 菜单 + Job + TRANSLATE 种子

**Files:**
- Create: `sql/blog_hn_schema.sql`
- Create: `sql/blog_hn_menu_seed.sql`
- Create: `sql/blog_hn_job_seed.sql`
- Modify: `README.md`（在 blog 相关 SQL 段落后追加三脚本）
- Modify: `backend/ruoyi-common/src/main/java/com/ruoyi/common/constant/Constants.java` — `JOB_WHITELIST_STR` 增加 `"com.ruoyi.blog.task"`（可选同时补 `"com.ruoyi.mall.trade.task"` 与现有商城任务对齐）

- [ ] **Step 1: 写 `blog_hn_schema.sql`**

```sql
SET NAMES utf8mb4;
USE nova_mall;

CREATE TABLE IF NOT EXISTS `blog_hn_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `hn_id` bigint NOT NULL COMMENT 'HN 条目 ID',
  `item_type` varchar(32) DEFAULT NULL COMMENT 'story/job/...',
  `title_en` varchar(512) DEFAULT NULL,
  `title_zh` varchar(512) DEFAULT NULL,
  `url` varchar(1024) DEFAULT NULL,
  `hn_url` varchar(256) NOT NULL,
  `text_en` mediumtext,
  `text_zh` mediumtext,
  `summary_zh` varchar(1000) DEFAULT NULL,
  `score` int DEFAULT 0,
  `author` varchar(64) DEFAULT NULL,
  `comment_count` int DEFAULT 0,
  `hn_time` datetime DEFAULT NULL COMMENT 'HN 发布时间(东八区换算后)',
  `fetched_at` datetime DEFAULT NULL,
  `translated_at` datetime DEFAULT NULL,
  `translate_status` varchar(16) NOT NULL DEFAULT 'pending' COMMENT 'pending|ok|fail',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0未发布 1已发布',
  `create_by` varchar(64) DEFAULT '',
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) DEFAULT '',
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_hn_id` (`hn_id`),
  KEY `idx_status_translate` (`status`, `translate_status`),
  KEY `idx_fetched_at` (`fetched_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HN 帖缓存';

CREATE TABLE IF NOT EXISTS `blog_hn_rank` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `board` varchar(16) NOT NULL COMMENT 'news|past|show|jobs',
  `hn_id` bigint NOT NULL,
  `rank` int NOT NULL,
  `snapshot_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_board_snapshot` (`board`, `snapshot_at`),
  KEY `idx_board_hn` (`board`, `hn_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HN 榜单快照';

INSERT INTO `ai_prompt_template` (`template_name`, `scene_type`, `system_prompt`, `model_name`, `temperature`, `is_active`)
SELECT 'HN 中英翻译', 'TRANSLATE',
  '你是中英技术内容翻译助手。将用户给出的 Hacker News 标题/正文译为流畅简体中文。严格只输出 JSON 对象，字段：title_zh（必填）、summary_zh（1～2 句中文摘要）、text_zh（若无英文正文则空字符串）。不要解释、不要 Markdown 代码围栏。',
  'deepseek-chat', 0.30, 1
WHERE NOT EXISTS (SELECT 1 FROM `ai_prompt_template` WHERE `scene_type` = 'TRANSLATE' LIMIT 1);
```

- [ ] **Step 2: 写菜单种子 `blog_hn_menu_seed.sql`**

挂在 `2000` AI博客下；menu_id：

| id | 名称 | 权限 |
|----|------|------|
| 2050 | HN 内容 | `blog:hn:list`，component `blog/hn/index` |
| 2230 | HN 查询 | `blog:hn:query` |
| 2231 | HN 同步 | `blog:hn:sync` |

`INSERT IGNORE` + `sys_role_menu` 给 role_id=1。

- [ ] **Step 3: 写 `blog_hn_job_seed.sql`**

```sql
INSERT INTO sys_job (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
SELECT 'HN 四榜同步', 'BLOG', 'blogHnTask.syncAll()', '0 0/30 * * * ?', '3', '1', '0', 'admin', sysdate(), '每 30 分钟同步 HN 四榜并增量翻译'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_job WHERE invoke_target = 'blogHnTask.syncAll()');
```

- [ ] **Step 4: 更新 README SQL 顺序 + JOB_WHITELIST**

- [ ] **Step 5: Commit**

```bash
git add sql/blog_hn_*.sql README.md backend/ruoyi-common/src/main/java/com/ruoyi/common/constant/Constants.java
git commit -m "feat(blog): HN 同步 DDL、菜单、定时任务与 TRANSLATE 提示词"
```

---

### Task 2: Domain / Mapper / HnBoard / HnClient

**Files:**
- Create: `backend/ruoyi-blog/src/main/java/com/ruoyi/blog/constant/HnBoard.java`
- Create: `backend/ruoyi-blog/src/main/java/com/ruoyi/blog/domain/BlogHnItem.java`
- Create: `backend/ruoyi-blog/src/main/java/com/ruoyi/blog/domain/BlogHnRank.java`
- Create: `backend/ruoyi-blog/src/main/java/com/ruoyi/blog/mapper/BlogHnItemMapper.java`
- Create: `backend/ruoyi-blog/src/main/java/com/ruoyi/blog/mapper/BlogHnRankMapper.java`
- Create: `backend/ruoyi-blog/src/main/java/com/ruoyi/blog/service/hn/HnClient.java`
- Create: `backend/ruoyi-blog/src/main/java/com/ruoyi/blog/dto/hn/HnItemDto.java`（API 反序列化用：id, type, title, url, text, score, by, time, descendants）

**Interfaces:**
- `HnBoard`: `NEWS("news","topstories",500)`, `PAST("past","beststories",500)`, `SHOW("show","showstories",200)`, `JOBS("jobs","jobstories",200)`；`fromCode(String)` 非法抛 `ServiceException`
- `HnClient.fetchStoryIds(HnBoard board): List<Long>` → `GET https://hacker-news.firebaseio.com/v0/{path}.json`，截断至 `board.getCap()`
- `HnClient.fetchItem(long hnId): HnItemDto` → `/v0/item/{id}.json`；404/null 返回 null
- 限速：客户端内 `Semaphore(5)` 或两次请求间 `Thread.sleep(100)`，失败重试最多 2 次

- [ ] **Step 1: 实体与枚举**

风格对齐 `BlogArticle`（`@Data`、`@TableName`、`@TableId`）。`BlogHnItem.hnUrl` 写入时统一 `https://news.ycombinator.com/item?id=` + hnId。`hn_time`：`Instant.ofEpochSecond(time).atZone(ZoneId.of("Asia/Shanghai")).toLocalDateTime()`。

- [ ] **Step 2: Mapper**

```java
public interface BlogHnItemMapper extends BaseMapper<BlogHnItem> {}

public interface BlogHnRankMapper extends BaseMapper<BlogHnRank> {
    @Select("SELECT MAX(snapshot_at) FROM blog_hn_rank WHERE board = #{board}")
    LocalDateTime selectMaxSnapshotAt(@Param("board") String board);
}
```

- [ ] **Step 3: HnClient**

新建独立 `OkHttpClient`（connect 10s、read 30s），**不要**复用 `deepSeekOkHttpClient`（其 readTimeout 300s 过长且语义不同）。用 `ObjectMapper` 解析 JSON。

- [ ] **Step 4: Compile**

```bash
cd backend && mvn -B -DskipTests compile -pl ruoyi-blog -am
```

Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add backend/ruoyi-blog/src/main/java/com/ruoyi/blog/{constant/HnBoard.java,domain/BlogHn*.java,mapper/BlogHn*.java,service/hn/HnClient.java,dto/hn/}
git commit -m "feat(blog): HN 客户端与领域模型"
```

---

### Task 3: HnSyncService（拉取 + upsert + 快照 + 翻译）

**Files:**
- Create: `backend/ruoyi-blog/src/main/java/com/ruoyi/blog/service/hn/HnSyncService.java`
- Create: `backend/ruoyi-blog/src/main/java/com/ruoyi/blog/service/hn/HnTranslateHelper.java`（或内嵌 private 方法）
- Create: `backend/ruoyi-blog/src/main/java/com/ruoyi/blog/task/BlogHnTask.java`
- Modify: `backend/ruoyi-blog/src/main/java/com/ruoyi/blog/constant/AiModuleCode.java` — 增加 `HN_TRANSLATE = "write"` 直接复用 WRITE 模块码（**不要**新增模块配置除非必要；调用时用已有 `AiModuleCode.WRITE`）

**Interfaces:**
```java
public interface HnSyncService {
    /** @return false if board already syncing */
    boolean syncBoardAsync(String board);
    void syncBoard(String board);   // 同步阻塞实现
    void syncAll();                 // 串行四榜
    Map<String, Object> syncStatus(); // board -> {running, lastSyncAt}
}
```

- [ ] **Step 1: 防并发与状态**

用 `ConcurrentHashMap<String, Boolean>` 或 `Set` 记录 running boards；`syncBoardAsync`：`@Async("aiTaskExecutor")` 包装调用 `syncBoard`；若已在跑则返回 false。内存记录 `lastSyncAt` per board。

- [ ] **Step 2: syncBoard 核心流程**

```
1. board = HnBoard.fromCode
2. ids = hnClient.fetchStoryIds(board)
3. snapshotAt = LocalDateTime.now()
4. for each id (index i):
     dto = hnClient.fetchItem(id); skip if null/blank title
     existing = selectByHnId
     titleChanged = existing!=null && !Objects.equals(existing.titleEn, dto.title)
     upsert 英文字段 + score/author/comment_count/fetched_at
     if new || titleChanged: translate_status=pending, clear title_zh optional
5. insert blog_hn_rank rows (board, hn_id, rank=i+1, snapshotAt) 批量
6. translatePendingForBoard(board, snapshotAt) — 仅本快照中的 hn_id，且 status pending|fail 或 title 变更
```

历史快照**可保留**（V1 不删旧 rank）；公开查询只用 `MAX(snapshot_at)`。若担心表膨胀，可在写入后删该 board 上 `snapshot_at < 本次` 的旧行（推荐 V1 做删除，保持表小）。

- [ ] **Step 3: 翻译**

对每条待译构造 prompt（含 title_en、text_en 可选），调用：

```java
AiCompletionRequest req = new AiCompletionRequest();
req.setScene("TRANSLATE");
req.setPrompt(prompt);
String raw = deepSeekService.chatCompletion(req, AiModuleCode.WRITE);
// 解析 JSON → title_zh, summary_zh, text_zh
// ok: translate_status=ok, translated_at=now, status=1
// fail: translate_status=fail，保留旧中文（若有）
```

无 `text_en` 时仍要求 `summary_zh`（可由标题生成）。单条失败 catch 后标记 fail，继续下一条。

- [ ] **Step 4: BlogHnTask**

```java
@Component("blogHnTask")
@RequiredArgsConstructor
public class BlogHnTask {
    private final HnSyncService hnSyncService;
    public void syncAll() { hnSyncService.syncAll(); }
}
```

包名必须为 `com.ruoyi.blog.task`。

- [ ] **Step 5: Compile + Commit**

```bash
cd backend && mvn -B -DskipTests compile -pl ruoyi-blog -am
git add backend/ruoyi-blog/src/main/java/com/ruoyi/blog/service/hn/ backend/ruoyi-blog/src/main/java/com/ruoyi/blog/task/
git commit -m "feat(blog): HN 同步编排与增量翻译"
```

---

### Task 4: Admin + Public API

**Files:**
- Create: `.../dto/HnSyncRequest.java`（可空字段，如 `Boolean force`；禁止裸 List）
- Create: `.../dto/HnItemAdminQuery.java`（pageNum/pageSize/board/keyword/translateStatus）
- Create: `.../vo/BlogHnItemVO.java`、`BlogHnListItemVO.java`
- Create: `.../service/BlogHnItemService.java` + `impl/BlogHnItemServiceImpl.java`
- Create: `.../controller/BlogHnController.java`
- Create: `.../controller/PublicHnController.java`

**Endpoints:**

| Method | Path | Perm | 行为 |
|--------|------|------|------|
| GET | `/blog/hn/items` | `blog:hn:list` | 运营分页；可选 board 过滤（join 最新快照） |
| GET | `/blog/hn/items/{id}` | `blog:hn:query` | 按主键详情 |
| GET | `/blog/hn/sync/status` | `blog:hn:list` | 各榜 running/lastSyncAt |
| POST | `/blog/hn/sync/{board}` | `blog:hn:sync` | body=`HnSyncRequest`；异步；返回 `{started:true/false}` |
| POST | `/blog/hn/sync` | `blog:hn:sync` | 串行四榜异步（整体一个 running 标志或逐榜） |
| GET | `/public/blog/hn/boards/{board}/items` | `@Anonymous` | 最新快照 join item；`status=1` 且 `title_zh` 非空；按 rank 升序分页 |
| GET | `/public/blog/hn/items/{hnId}` | `@Anonymous` | 按 **hn_id** 详情；同样发布条件，否则 404 |

公开 VO 字段：`hnId, titleZh, summaryZh, textZh, titleEn, url, hnUrl, score, author, commentCount, hnTime, rank(board list only)`。

`PublicHnController` 类级 `@Anonymous` + `@RequestMapping("/public/blog/hn")`，对齐 `PublicArticleController`。

- [ ] **Step 1: Service 读路径**

公开列表 SQL 思路：先 `maxSnapshot = rankMapper.selectMaxSnapshotAt(board)`；若 null 返回空页；再 join `blog_hn_rank r` + `blog_hn_item i` on `r.hn_id=i.hn_id` where `r.board=? and r.snapshot_at=? and i.status=1 and i.title_zh is not null and i.title_zh<>''` order by `r.rank`，MyBatis-Plus 分页。

- [ ] **Step 2: Controllers**

`BlogHnController` 继承 `BlogControllerSupport`；`@Log` 记同步操作。

- [ ] **Step 3: Compile**

```bash
cd backend && mvn -B -DskipTests package -pl ruoyi-admin -am
```

- [ ] **Step 4: Commit**

```bash
git commit -m "feat(blog): HN 管理端与公开只读 API"
```

---

### Task 5: 管理前端「HN 内容」页

**Files:**
- Create: `frontend/src/api/blog/hn.js`
- Create: `frontend/src/views/blog/hn/index.vue`

- [ ] **Step 1: API 模块**

```js
import request from '@/utils/request'

export function fetchHnItemPage(params) {
  return request({ url: '/blog/hn/items', method: 'get', params })
}
export function fetchHnSyncStatus() {
  return request({ url: '/blog/hn/sync/status', method: 'get' })
}
export function syncHnBoard(board, data = {}) {
  return request({ url: `/blog/hn/sync/${board}`, method: 'post', data })
}
export function syncHnAll(data = {}) {
  return request({ url: '/blog/hn/sync', method: 'post', data })
}
```

- [ ] **Step 2: 页面**

四 Tab：`news | past | show | jobs`。每 Tab：
- 「一键拉取」按钮 `v-hasPermi="['blog:hn:sync']"`，调用 `syncHnBoard`，成功提示「已开始同步」
- 顶部说明：Past=精选(best)；Show/Jobs 数量以接口为准
- 表格列：rank/hnId/titleZh/titleEn/score/translateStatus/status/hnTime
- 轮询 `fetchHnSyncStatus`（同步中每 3s）刷新 loading 态
- 「同步全部」按钮可选

风格对齐 `views/blog/article/index.vue`。

- [ ] **Step 3: 本地验证**

执行 SQL 种子后登录后台，确认菜单「HN 内容」可打开（数据可为空）。

- [ ] **Step 4: Commit**

```bash
git add frontend/src/api/blog/hn.js frontend/src/views/blog/hn/
git commit -m "feat(blog): HN 管理端一键拉取页面"
```

---

### Task 6: 公开前端 `/blog/hn`

**Files:**
- Create: `frontend/src/api/blog/publicHn.js`
- Create: `frontend/src/views/public/blog/hn/index.vue`
- Create: `frontend/src/views/public/blog/hn/detail.vue`
- Modify: `frontend/src/router/index.js` — 在 `path: ':id(\\d+)'` **之前**插入 hn 路由
- Modify: `frontend/src/layout/BlogPublicLayout.vue` — 增加 `RouterLink to="/blog/hn"`

- [ ] **Step 1: 路由**

```js
{
  path: 'hn',
  name: 'BlogHnList',
  component: () => import('@/views/public/blog/hn/index.vue'),
  meta: { title: 'Hacker News' }
},
{
  path: 'hn/:hnId(\\d+)',
  name: 'BlogHnDetail',
  component: () => import('@/views/public/blog/hn/detail.vue'),
  meta: { title: 'HN 详情' }
},
```

- [ ] **Step 2: 列表页**

四 Tab；卡片视觉对齐 `BlogArticleItem`（可新建轻量 `HnItemCard` 或内联样式）：标题用 `titleZh`，摘要 `summaryZh`，meta 显示 score / author / hnTime；链接到 `/blog/hn/${hnId}`。页脚小字注明数据来源 HN、Past 为精选近似。

- [ ] **Step 3: 详情页**

展示 `titleZh`、`summaryZh`、`textZh`（若有）、原文标题、外链 `url`、`hnUrl`。无外链正文爬取。

- [ ] **Step 4: Commit**

```bash
git add frontend/src/api/blog/publicHn.js frontend/src/views/public/blog/hn/ frontend/src/router/index.js frontend/src/layout/BlogPublicLayout.vue
git commit -m "feat(blog): 公开 HN 列表与详情页"
```

---

### Task 7: 联调冒烟 + 设计文档收尾

**Files:**
- Modify: `docs/superpowers/specs/2026-07-29-blog-hn-sync-design.md` — Status → `Implemented`
- Modify: 本 plan 勾选全部 Task

- [ ] **Step 1: 执行 SQL**

```bash
mysql -u root -p nova_mall < sql/blog_hn_schema.sql
mysql -u root -p nova_mall < sql/blog_hn_menu_seed.sql
mysql -u root -p nova_mall < sql/blog_hn_job_seed.sql
```

- [ ] **Step 2: 启动并冒烟**

```bash
# 编译启动后端后：
# 1) 登录拿 token，POST /blog/hn/sync/news （body {}）
# 2) 轮询 GET /blog/hn/sync/status 至 running=false
# 3) GET /public/blog/hn/boards/news/items?pageNum=1&pageSize=10  — 应有中文标题（需已配置 AI）
# 4) 浏览器打开 /blog/hn 、后台「HN 内容」
# 5) 确认公开接口抓包无 firebaseio.com 请求
```

若本地无 LLM Key：至少验证英文 upsert + rank 写入；翻译失败条目标 `fail`，公开列表为空或仅已成功条目——属预期。

- [ ] **Step 3: 全量编译**

```bash
cd backend && mvn -B -DskipTests package -pl ruoyi-admin -am
cd frontend && npm run build:prod
```

- [ ] **Step 4: 更新 Spec Status + Commit**

```bash
git add docs/superpowers/specs/2026-07-29-blog-hn-sync-design.md docs/superpowers/plans/2026-07-29-blog-hn-sync.md
git commit -m "docs(blog): 标记 HN 同步设计已实现并完成联调清单"
```

---

## Spec Coverage Checklist

| Spec 要求 | Task |
|-----------|------|
| 独立表 + `/blog/hn` | 1, 4, 6 |
| 四榜映射 + cap | 2 `HnBoard` |
| Past = beststories | 2, 5/6 文案 |
| 30 分钟 Quartz | 1 job seed + 3 Task |
| 一键拉取异步 + 防并发 | 3, 5 |
| 增量翻译 TRANSLATE | 1 prompt + 3 |
| 公开只读 DB | 4, 6, 7 |
| 不写 blog_article | 全任务遵守 |
| 有 title_zh 才公开 | 4 |

## Deferred（V1 不做）

- 真 Past 按日爬取、评论树、外链正文、混入 `/blog`、运营人工审核流
