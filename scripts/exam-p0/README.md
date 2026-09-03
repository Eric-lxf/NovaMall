# 智能命题 P0 离线验证

本目录是可重建的自编样本和技术探针，不是运行中的考试模块。只处理本脚本生成的文件；**不要向当前基于旧依赖的兼容性探针输入真实或不可信文档**。

查看 [验证结论](../../docs/ai-exam-p0-validation.md) 与 [验收范围](../../docs/ai-exam-acceptance.md)。本目录未接数据库、服务器、密钥或外部 AI。

## 1. 环境

从仓库根运行。Java 17、Maven 3.8+；Python 3.11+。生成文档需要 `python-docx`、`reportlab`、`pypdf`、`Pillow`；协议测试仅用标准库。此次使用 Codex 提供的本地运行时，没有安装系统软件。

若自行准备 Python 环境，应先用隔离虚拟环境安装上述依赖，并提供自己有权使用的中文 TTF 字体。字体不包含在仓库中。

## 2. 生成样本

PowerShell 示例（`python` 指向已安装依赖的解释器）：

```powershell
python scripts/exam-p0/build_fixtures.py --font 'C:\Windows\Fonts\simhei.ttf'
```

生成位置固定为 `tmp/exam-p0/fixtures/`；manifest 在其父目录。重复执行会重新生成本任务命名的样本文件，不清空整个 `tmp/`。DOCX 与 PDF 为彼此独立构建的输入样本，不执行 DOCX 转 PDF。

## 3. Java 兼容性测试

先生成样本，确认当前 Java 为 17，再运行：

```powershell
mvn -o -B -f scripts/exam-p0/pom.xml test
```

`-o` 使用本机 Maven 缓存；缺少依赖时会失败，首次准备环境需在允许联网的条件下去掉 `-o` 下载依赖。本项目继承 `backend/pom.xml` 的 POI 版本，没有修改主构建依赖。报告在本目录 `target/surefire-reports/`，应明确显示 9 项测试，而不是零测试。

## 4. 题目协议测试

```powershell
python -m unittest discover -s scripts/exam-p0 -p test_contract.py -v
```

当前为 24 项测试。`contract.py` 接受可信的槽位与片段目录，检查不可信输出；样本目录只覆盖单个资料版本。它不提供生产授权校验、不判断事实正确性，也没有使用完整 JSON Schema 引擎。未来 Java 实现可共享 JSON 黄金样本并增加数据库与跨版本用例。

## 5. PDF 样本视觉复核

已安装 Poppler 时可将页面渲染到专用目录：

```powershell
New-Item -ItemType Directory -Force tmp/exam-p0/pdf-render
pdftoppm -scale-to 1400 -png tmp/exam-p0/fixtures/source-text.pdf tmp/exam-p0/pdf-render/text
pdftoppm -scale-to 1400 -png tmp/exam-p0/fixtures/source-columns.pdf tmp/exam-p0/pdf-render/columns
pdftoppm -scale-to 1400 -png tmp/exam-p0/fixtures/source-image-only.pdf tmp/exam-p0/pdf-render/scan
```

逐页检查中文、裁切和版式。本轮 4 页检查完成；扫描样本应看得到文字但文本提取为空，双栏样本应看得到两栏但默认文本次序交错。

DOCX 排版与 DOCX→PDF 另需可用的隔离转换器。本轮缺少 LibreOffice，标准渲染尝试失败，不能用上述输入 PDF 的渲染结果替代转换验证。

## 6. 文件说明

- `build_fixtures.py`：创建文档输入样本和故意异常文件。
- `samples/`：虚构手册、四题型协议、原文片段、可信槽位。
- `src/test/java/`、`pom.xml`：现有 Java 依赖的独立解析测试。
- `contract.py`、`test_contract.py`：离线协议校验示例及反例。
- `.gitignore`：排除本目录构建产物与 Python 缓存；不改变用户其他临时文件策略。
