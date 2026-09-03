# 本地隔离联调（显式运行，不部署）

用途：真实 MySQL 8 + Redis + 完整 JAR + JWT + 文档容器验收。所有资料均为合成样本，AI 强制关闭。不会读取 `.env` 或操作现有 Compose 项目。

## 前提

Java 17、Python 3.12、Docker 可用，先按根 AGENTS.md 构建完整后端。文档渲染 QA 另需 Poppler、pypdf 和文档技能渲染器的运行依赖。

从仓库根执行：

```sh
docker pull mysql:8.0
docker pull redis:7
docker build -t novamall/exam-document:20260903 tools/exam-document-worker
python scripts/exam-local/docker_smoke.py --java /absolute/path/to/java
python scripts/exam-local/probe_isolation.py
```

Windows 请给 `--java` 传入已安装 JDK 的 `java.exe`，有空格时加引号。

- 默认只绑定本机 `13306`、`16379`、`18080`。端口被占用直接失败，不停止占用者。
- MySQL/Redis 使用独立容器及 tmpfs 数据目录；凭据随机产生，JWT 仅保留在测试进程内存。固定的合成用户密码仅用于这个临时库。
- 普通命题人、另一所有者、审核人、无权限用户执行真实登录及权限验证；没有隐式给生产角色授权。
- 数据库初始化沿用项目基线顺序，**跳过旧商城类目数据迁移** `mall_phase_b_migrate_front_category.sql`（MySQL 1137）。不宣称验证完整商城数据迁移。
- 四个命题迁移脚本执行两次，单独验证 `/exam` 路径冲突保护。
- 测试创建的应用进程、带唯一标签的容器结束时清理；临时库不可恢复，但合成测试可重新运行。
- 每次证据在 `tmp/exam-docker-qa/<run-id>/`：结果 JSON、应用日志、合成试卷。JAR 使用独立副本，避免 Windows 上锁住 Maven 输出。
- 中途强制结束 Python/宿主机异常时可能来不及清理。仅检查并清理该次打印 run-id 对应、且 `novamall.exam.qa` 标签一致的精确容器；不要批量删除其他容器或执行 `compose down -v`。

## 可选浏览器 QA

运行脚本时增加 `--hold-seconds 300`（上限 600）。测试结束后给浏览器留出有界检查窗口，随后自动清理。另一终端：

```sh
cd frontend
npx vite --config tests/exam-live.vite.config.mjs
```

打开 `http://127.0.0.1:15173`，使用脚本打印的合成账号。不要勾选记住密码，不修改密码，不导入真实资料。该配置没有请求 Mock，连接的是隔离的 18080 后端，不能当作部署入口。

## 逐页渲染

```sh
python scripts/exam-local/render_qa.py tmp/exam-docker-qa/<run-id> --renderer /absolute/path/to/documents/render_docx.py
```

该适配器运行原文档技能的 `rasterize` 流程，仅把 `soffice` 命令转到 Docker；随后还单独渲染 API 下载的 PDF，检查文字和学生答案投影。须人工打开所有 PNG 验证布局，JSON 的成功结果不等于视觉验收。

QA 转换器只读挂载选定合成 DOCX、写入渲染器临时输出目录。**生产工作进程仍为标准流输入输出、无任何宿主机目录挂载**，不要把 QA 挂载方式复制到上传处理链路。

## 命题部署配置回归（不启动服务）

从仓库根目录运行 `python -m unittest discover -s scripts/exam-local -p test_deploy_config.py -v`。需要 Python 3.11+、Docker Compose 插件和 Bash（Windows 使用 Git for Windows 的 Bash）。测试使用临时空环境文件与合成配置，不读取项目 `.env`，只渲染 Compose 和执行命题开关校验片段，不连接生产、不启动容器、不调用模型。
