# Design: Hacker News 同步至博客模块

**Status**: Approved  
**Author**: Alex / Cursor  
**Last Updated**: 2026-07-29  
**Branch**: （实现时新建，如 `feat/blog-hn-sync`）  
**Stakeholders**: Eng / Product

---

## 1. Problem Statement

希望把 [Hacker News](https://news.ycombinator.com/) 的 **News / Past / Show / Jobs** 内容引入 NovaMall 博客侧，以接近现有 `/blog` 的列表+详情体验展示，并翻译为中文。展示时必须**只读本库**，不能在用户访问时实时请求 HN。

**Evidence**
- 现有 `blog_article` 无外部来源/去重字段，不适合直接承载抓取中间态（见代码库博客模块调研）。
- HN 官方提供 Firebase 只读 API，无鉴权；Past 页无对应列表接口。
- 仓库已有 Quartz、OkHttp、LLM 调用能力，无现成爬虫/翻译场景。

---

## 2. Goals & Success Metrics

| Goal | Acceptance |
|------|------------|
| 四榜入库 | News / Past / Show / Jobs 可同步到本地表 |
| Top 规模 | News/Past 目标 Top 500；Show/Jobs 以 API 实际上限为准（约 200） |
| 定时同步 | Quartz **每 30 分钟**执行一轮 |
| 一键拉取 | 后台每个榜单具备「一键拉取」；可触发单榜同步 |
| 中文展示 | 标题必译；摘要/自有正文按策略译成中文 |
| 读路径离线 | 公开接口与页面只查 DB，不调用 HN |
| 类博客体验 | `/blog/hn` 列表+详情视觉与交互对齐现有博客公开页 |

**Non-Goals（V1）**
- 按日爬取站点真正的 Past 页
- 同步完整评论树
- 抓取外链文章全文
- 用户访问时代理/实时拉 HN
- 自动写入或混排进 `blog_article` / 普通 `/blog` 流

---

## 3. Decisions（已确认）

| 议题 | 决策 |
|------|------|
| 总体架构 | **方案 A**：独立源表 + 公开 `/blog/hn`；不直接塞 `blog_article` |
| Past | **用 `/beststories` 近似** Past（语义为历史/精选热门，非站点日历 Past） |
| 调度 | 每 **30 分钟**定时；另加后台一键 |
| 翻译 | 复用现有 LLM；新增 TRANSLATE（或等价）提示场景；增量翻译 |

---

## 4. Data Source Mapping

Base: `https://hacker-news.firebaseio.com/v0/`

| UI Board | API | Cap |
|----------|-----|-----|
| `news` | `/topstories.json` | ≤ 500 |
| `past` | `/beststories.json` | ≤ 500 |
| `show` | `/showstories.json` | ≤ ~200 |
| `jobs` | `/jobstories.json` | ≤ ~200 |

Item: `/item/{id}.json` → `title`, `url`, `text`, `score`, `by`, `time`, `type`, `descendants`, …

文档与 UI 需注明 Show/Jobs「目标 500、实际以接口为准」。

---

## 5. Data Model

### 5.1 `blog_hn_item`

一条 HN 帖（`hn_id` UNIQUE）。

| 字段 | 说明 |
|------|------|
| `hn_id` | HN 数字 ID |
| `item_type` | story / job / … |
| `title_en` / `title_zh` | 原文 / 中文标题 |
| `url` | 外链（可空） |
| `hn_url` | 固定 `https://news.ycombinator.com/item?id={hn_id}` |
| `text_en` / `text_zh` | self-text（Ask/Show/Job 等） |
| `summary_zh` | 列表用中文摘要 |
| `score`, `author`, `comment_count` | 元数据 |
| `hn_time` | HN 发布时间 |
| `fetched_at`, `translated_at` | 同步/翻译时间 |
| `translate_status` | `pending` / `ok` / `fail` |
| `status` | `0` 未发布 / `1` 已发布（前台仅 `1` 且译文可用策略见下） |
| 审计字段 | 与博客表风格一致 |

### 5.2 `blog_hn_rank`

榜单位次快照。

| 字段 | 说明 |
|------|------|
| `board` | `news` \| `past` \| `show` \| `jobs` |
| `hn_id` | 关联 item |
| `rank` | 1..N |
| `snapshot_at` | 本轮快照时间 |

前台：对每个 `board` 取 `MAX(snapshot_at)` 的 rank 列表，join `blog_hn_item`。

---

## 6. Sync Pipeline

```text
Trigger: Quartz(30min) | Admin 一键(单榜|全部)
  → 拉 board ID 列表（截断至 cap）
  → 限流并发拉 /item/{id}（建议 5～10 QPS）
  → upsert blog_hn_item（英文与元数据）
  → 写入本轮 blog_hn_rank（新 snapshot_at）
  → 筛选需翻译：新条目 / title_en 变更 / translate_status=fail
  → LLM 写 title_zh、summary_zh、（有 text 则）text_zh
  → translate_status=ok|fail；V1 默认可自动 status=1（译文 ok 时）
```

**一键拉取**
- 管理端「HN 内容」页，四 Tab 各一按钮，只同步当前 `board`
- 可选「同步全部」串行四榜
- 权限：`blog:hn:sync`（及 list/query 等）
- 异步执行：立即返回「已开始」；展示 `last_sync_at` / 进行中 loading；同 board 防并发

**定时**
- `invokeTarget` 示例：`blogHnTask.syncAll()`
- Cron：`0 0/30 * * * ?`（每小时的 0、30 分；以项目 Quartz 语义为准）

**翻译**
- 标题必译；无 self-text 时 `summary_zh` 可由标题生成 1～2 句
- 不爬外链正文
- 失败保留英文，标记 `fail`，下轮可重试；前台优先中文，无中文可降级英文或隐藏（实现时二选一，推荐：**有 title_zh 才进公开列表**）

---

## 7. API & UI

### 7.1 Public（Anonymous）

| Method | Path | 说明 |
|--------|------|------|
| GET | `/public/blog/hn/boards/{board}/items` | 分页列表（最新快照） |
| GET | `/public/blog/hn/items/{id}` | 详情（库内主键或 hn_id，实现时统一） |

### 7.2 Admin

| Method | Path | 说明 |
|--------|------|------|
| GET | `/blog/hn/items` | 运营列表/筛选 |
| POST | `/blog/hn/sync/{board}` | 一键拉取单榜（body 可用空对象或选项 DTO，**禁止**裸 List） |
| POST | `/blog/hn/sync` | 同步全部 |

### 7.3 Frontend

- 公开：`/blog/hn`，Tab = 四榜；列表卡片对齐 `BlogArticleItem` 风格；详情含中文内容 + 原文/HN 链接
- 博客导航增加「Hacker News」入口
- 后台：`blog/admin/hn/index`（或等价路径）+ 菜单种子；每榜一键拉取

---

## 8. Module Placement

- 代码：`ruoyi-blog` 内 `hn` 子包（client / sync / service / controller）
- 任务：`@Component("blogHnTask")`
- SQL：`sql/blog_hn_schema.sql`、`sql/blog_hn_menu_seed.sql`（及可选 job 种子）
- 不新建 Maven 模块（V1）

---

## 9. Risks & Mitigations

| Risk | Mitigation |
|------|------------|
| Show/Jobs &lt; 500 | 文档与 UI 标明实际上限 |
| Past ≠ 站点 Past | 文案用「精选/热门」或注明基于 best |
| LLM 费用与耗时 | 增量翻译；半小时任务超时可分段/续跑 |
| HN 限流 | 客户端限速 + 失败重试 |
| 与自写博客混淆 | 独立路由 `/blog/hn`，V1 不混排 |

---

## 10. Phasing

| Phase | Scope |
|-------|--------|
| **V1（本设计）** | 上表全能力 |
| V2 | 真 Past 按日、运营审核流、评论数高频刷新、可选混入 `/blog` |

---

## 11. Effort

约 **2～3 人周**（DDL + 同步 + 翻译 + 公开/后台 UI + 菜单与定时任务）。

---

## 12. Next Step

用户确认本文件后 → `writing-plans` → `docs/superpowers/plans/2026-07-29-blog-hn-sync.md` → 再实现。
