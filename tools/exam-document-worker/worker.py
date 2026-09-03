"""One bounded stdin/stdout job per isolated container. Never execute document instructions.

DOCX preset: standard_business_brief; exam_print_v1 overrides: A4, 20 mm
side margins / 22 mm top-bottom, Noto Serif CJK SC, black ink, 12pt body,
1.25 line spacing, 20pt centered title. Question numbering is real OOXML.
Only trusted server snapshots reach rendering; uploaded DOCX is never opened
in LibreOffice. The converter sees our freshly built DOCX, not the upload.
"""
import base64
import io
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

MAX_FILE = 10 * 1024 * 1024
W = "{http://schemas.openxmlformats.org/wordprocessingml/2006/main}"
TYPES = {"SINGLE_CHOICE": "单选题", "MULTIPLE_CHOICE": "多选题", "TRUE_FALSE": "判断题", "SHORT_ANSWER": "简答题"}


class Rejected(Exception):
    def __init__(self, code):
        self.code = code


def require(condition, code="EXAM_DOCUMENT_INVALID"):
    if not condition:
        raise Rejected(code)


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result)
        result[key] = value
    return result


def fragments(parts, warnings):
    output = []
    total = 0
    for text, locator in parts:
        text = text.replace("\r\n", "\n").replace("\r", "\n").strip()
        require("\x00" not in text)
        total += len(text)
        require(total <= 200000, "EXAM_DOCUMENT_TOO_LARGE")
        for offset in range(0, len(text), 1500):
            piece = text[offset:offset + 1500]
            if piece.strip():
                output.append({"text": piece, "locator": {**locator, "characterOffset": offset}})
        require(len(output) <= 2000, "EXAM_DOCUMENT_TOO_LARGE")
    require(output, "EXAM_DOCUMENT_NO_TEXT")
    return {"fragments": output, "warnings": warnings}


def parse_docx(data):
    from lxml import etree
    require(data.startswith(b"PK"))
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        entries = archive.infolist()
        names = [entry.filename for entry in entries]
        require(len(entries) <= 2000 and len(names) == len(set(names)), "EXAM_ZIP_INVALID")
        require(sum(entry.file_size for entry in entries) <= 30 * 1024 * 1024, "EXAM_ZIP_TOO_LARGE")
        for entry in entries:
            require(not entry.flag_bits & 1 and entry.file_size <= 8 * 1024 * 1024, "EXAM_ZIP_INVALID")
            require(entry.file_size <= max(1, entry.compress_size) * 200, "EXAM_ZIP_RATIO")
            require(not entry.filename.startswith(("/", "\\")) and ".." not in entry.filename.replace("\\", "/").split("/"))
            require(not any(value in entry.filename.lower() for value in ("vbaproject", "embeddings/", "activex/")), "EXAM_ACTIVE_CONTENT")
        require("word/document.xml" in names and "[Content_Types].xml" in names)
        parser = etree.XMLParser(resolve_entities=False, load_dtd=False, no_network=True, huge_tree=False)
        for name in names:
            if name.endswith(".rels"):
                rels = etree.fromstring(archive.read(name), parser)
                require(not rels.getroottree().docinfo.doctype, "EXAM_ACTIVE_CONTENT")
                require(not any(node.get("TargetMode", "").lower() == "external" for node in rels), "EXAM_EXTERNAL_LINK")
        root = etree.fromstring(archive.read("word/document.xml"), parser)
        require(not root.getroottree().docinfo.doctype, "EXAM_ACTIVE_CONTENT")
        require(not root.findall(".//" + W + "altChunk"), "EXAM_ACTIVE_CONTENT")
        parts = []
        paragraph_index = 0
        table_index = 0
        body = root.find(W + "body")
        require(body is not None)
        def text_of(node):
            return "".join(child.text or "" for child in node.iter(W + "t")
                           if not any(parent.tag in (W + "txbxContent", W + "del") for parent in child.iterancestors()))
        for block in body:
            if block.tag == W + "p":
                paragraph_index += 1
                parts.append((text_of(block), {"kind": "DOCX_PARAGRAPH", "paragraph": paragraph_index}))
            elif block.tag == W + "tbl":
                table_index += 1
                for row_index, row in enumerate(block.findall(W + "tr"), 1):
                    for cell_index, cell in enumerate(row.findall(W + "tc"), 1):
                        parts.append(("\n".join(text_of(p) for p in cell.iter(W + "p")),
                                      {"kind": "DOCX_TABLE_CELL", "table": table_index, "row": row_index, "cell": cell_index}))
        warnings = ["请对照原文件核对段落/表格顺序；图片、公式、页眉页脚、批注和文本框不参与命题。"]
        if root.findall(".//" + W + "drawing"):
            warnings.append("包含图片；图片内容未识别，请勿确认依赖图片的片段。")
        return fragments(parts, warnings)


def parse_pdf(data):
    from pypdf import PdfReader
    require(data.startswith(b"%PDF-"))
    reader = PdfReader(io.BytesIO(data), strict=True)
    require(not reader.is_encrypted, "EXAM_PDF_ENCRYPTED")
    require(1 <= len(reader.pages) <= 50, "EXAM_PDF_PAGE_LIMIT")
    parts, warnings = [], ["PDF 按页提取文本。多栏、表格和阅读顺序须人工核对；扫描页不做 OCR。"]
    for index, page in enumerate(reader.pages, 1):
        text = page.extract_text(extraction_mode="layout") or ""
        require(len(text) <= 200000, "EXAM_DOCUMENT_TOO_LARGE")
        if not text.strip():
            warnings.append(f"第 {index} 页未提取到文字，可能为扫描页。")
        parts.append((text, {"kind": "PDF_PAGE", "page": index}))
    return fragments(parts, warnings)


def student_projection(snapshot):
    return {key: snapshot[key] for key in ("title", "durationMinutes", "totalScore", "templateVersion")} | {
        "items": [{"ordinal": item["ordinal"], "score": item["score"], "content": {
            key: item["content"][key] for key in ("type", "stem", "options") if key in item["content"]}}
                  for item in snapshot["items"]]}


def answer_text(question):
    if "correctOptionIds" in question:
        return "、".join(question["correctOptionIds"])
    if "answerBoolean" in question:
        return "正确" if question["answerBoolean"] else "错误"
    return question.get("referenceAnswer", "")


def render_docx(snapshot, audience):
    from docx import Document
    from docx.shared import Mm, Inches, Pt, RGBColor
    from docx.enum.text import WD_ALIGN_PARAGRAPH
    from docx.oxml import OxmlElement
    from docx.oxml.ns import qn
    from docx.enum.style import WD_STYLE_TYPE
    require(audience in ("STUDENT", "TEACHER"))
    require(snapshot.get("templateVersion") == "exam-paper.v1" and 1 <= len(snapshot["items"]) <= 50)
    if audience == "STUDENT":
        snapshot = student_projection(snapshot)  # Defense-in-depth: no answer-bearing object is serialized into this DOCX.
    doc = Document()
    section = doc.sections[0]
    section.page_width, section.page_height = Mm(210), Mm(297)
    section.top_margin = section.bottom_margin = Mm(22)
    section.left_margin = section.right_margin = Mm(20)
    section.header_distance = section.footer_distance = Inches(0.492)
    font_name = "Noto Serif CJK SC"
    for name, size, before, after in [("Normal", 12, 0, 6), ("Title", 20, 0, 8),
                                       ("Heading 1", 16, 16, 8), ("Heading 2", 13, 12, 6), ("Heading 3", 12, 8, 4)]:
        style = doc.styles[name]
        style.font.name, style.font.size, style.font.color.rgb = font_name, Pt(size), RGBColor(0, 0, 0)
        fonts = style.element.get_or_add_rPr().get_or_add_rFonts()
        fonts.set(qn("w:eastAsia"), font_name)
        # Theme fonts can override explicit CJK fonts in Word/LibreOffice.
        for attribute in ("asciiTheme", "hAnsiTheme", "eastAsiaTheme", "cstheme"):
            fonts.attrib.pop(qn("w:" + attribute), None)
        # The bundled Word Title style may carry a blue bottom rule. The
        # exam_print_v1 print preset is deliberately monochrome and borderless.
        if name == "Title":
            props = style.element.get_or_add_pPr()
            for border in list(props.findall(qn("w:pBdr"))):
                props.remove(border)
        style.paragraph_format.space_before, style.paragraph_format.space_after = Pt(before), Pt(after)
        style.paragraph_format.line_spacing = 1.25
    # Real list numbering with explicit alignment/indentation; no fixed-height containers.
    numbering = doc.part.numbering_part.element
    abstract_id = max([int(n.get(qn("w:abstractNumId"))) for n in numbering.findall(qn("w:abstractNum"))] + [0]) + 1
    abstract = OxmlElement("w:abstractNum"); abstract.set(qn("w:abstractNumId"), str(abstract_id))
    level = OxmlElement("w:lvl"); level.set(qn("w:ilvl"), "0")
    for tag, value in (("start", "1"), ("numFmt", "decimal"), ("lvlText", "%1."), ("lvlJc", "left")):
        child = OxmlElement("w:" + tag); child.set(qn("w:val"), value); level.append(child)
    props = OxmlElement("w:pPr"); tabs = OxmlElement("w:tabs"); tab = OxmlElement("w:tab")
    tab.set(qn("w:val"), "num"); tab.set(qn("w:pos"), "720"); tabs.append(tab); props.append(tabs)
    indent = OxmlElement("w:ind"); indent.set(qn("w:left"), "720"); indent.set(qn("w:hanging"), "360"); props.append(indent)
    level.append(props); abstract.append(level); numbering.append(abstract)
    num_id = max([int(n.get(qn("w:numId"))) for n in numbering.findall(qn("w:num"))] + [0]) + 1
    num = OxmlElement("w:num"); num.set(qn("w:numId"), str(num_id)); reference = OxmlElement("w:abstractNumId"); reference.set(qn("w:val"), str(abstract_id)); num.append(reference); numbering.append(num)
    question_style = doc.styles.add_style("Exam Question", WD_STYLE_TYPE.PARAGRAPH)
    question_style.base_style = doc.styles["Normal"]
    question_style.paragraph_format.space_after = Pt(8)
    question_style.paragraph_format.line_spacing = 1.167
    question_style.paragraph_format.keep_with_next = True
    question_style.paragraph_format.widow_control = True
    for label in ("Header", "Footer"):
        doc.styles[label].font.name = font_name; doc.styles[label].font.size = Pt(9)
    section.header.paragraphs[0].text = "NovaMall · " + ("学生卷" if audience == "STUDENT" else "教师卷 / 含答案解析")
    footer = section.footer.paragraphs[0]; footer.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    footer.add_run("第 "); field = OxmlElement("w:fldSimple"); field.set(qn("w:instr"), "PAGE"); footer._p.append(field); footer.add_run(" 页")
    title = doc.add_paragraph(snapshot["title"], "Title"); title.alignment = WD_ALIGN_PARAGRAPH.CENTER
    meta = doc.add_paragraph(f"考试时长：{snapshot['durationMinutes']} 分钟    满分：{snapshot['totalScore']} 分")
    meta.alignment = WD_ALIGN_PARAGRAPH.CENTER
    if audience == "STUDENT":
        doc.add_paragraph("姓名：________________    班级/部门：________________")
    else:
        doc.add_paragraph("使用说明：本卷包含答案、解析与原文引文。请勿向考生分发。")
    for item in snapshot["items"]:
        q = item["content"]
        p = doc.add_paragraph(f"【{TYPES[q['type']]} · {item['score']} 分】{q['stem']}", "Exam Question")
        num_props = p._p.get_or_add_pPr().get_or_add_numPr(); num_props.get_or_add_ilvl().val = 0; num_props.get_or_add_numId().val = num_id
        for option in q.get("options", []):
            op = doc.add_paragraph(f"{option['id']}．{option['text']}")
            op.paragraph_format.left_indent = Inches(0.5)
        if audience == "TEACHER":
            doc.add_paragraph("参考答案：" + answer_text(q))
            doc.add_paragraph("解析：" + q["analysis"])
            for point in q.get("rubric", []):
                doc.add_paragraph(f"评分点（{point['weight']}%）：{point['point']}")
            for ref in q.get("sourceRefs", []):
                doc.add_paragraph(f"依据（资料版本 {ref['sourceVersionId']} / 片段 {ref['fragmentId']}）：{ref['quote']}")
        elif q["type"] == "SHORT_ANSWER":
            for line in range(4):
                answer_line = doc.add_paragraph("________________________________________________________________")
                # Keep the response area together; otherwise a lone blank line
                # can be orphaned at the top of the following exam page.
                answer_line.paragraph_format.keep_with_next = line < 3
        else:
            doc.add_paragraph("作答：________________")
    doc.core_properties.author = "NovaMall"
    doc.core_properties.title = snapshot["title"]
    doc.core_properties.comments = ""
    output = io.BytesIO(); doc.save(output)
    return output.getvalue()


def convert_pdf(docx_bytes):
    executable = os.environ.get("EXAM_SOFFICE") or shutil.which("soffice")
    require(executable, "EXAM_CONVERTER_MISSING")
    with tempfile.TemporaryDirectory(prefix="exam-render-") as directory:
        root = Path(directory); source = root / "paper.docx"; source.write_bytes(docx_bytes)
        subprocess.run([executable, "--headless", "--nologo", "--nodefault", "--norestore",
                        "-env:UserInstallation=" + (root / "profile").as_uri(), "--convert-to", "pdf:writer_pdf_Export",
                        "--outdir", str(root), str(source)], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                       timeout=60, check=True)
        result = root / "paper.pdf"; require(result.is_file(), "EXAM_CONVERSION_FAILED")
        require(0 < result.stat().st_size <= MAX_FILE, "EXAM_EXPORT_TOO_LARGE")
        data = result.read_bytes(); require(data.startswith(b"%PDF-"), "EXAM_CONVERSION_FAILED")
        return data


def run(request):
    mode = request.get("mode")
    if mode == "PARSE":
        data = base64.b64decode(request["data"], validate=True)
        require(0 < len(data) <= MAX_FILE, "EXAM_DOCUMENT_TOO_LARGE")
        kind = request.get("format")
        require(kind in ("DOCX", "PDF"), "EXAM_FORMAT_UNSUPPORTED")
        return parse_docx(data) if kind == "DOCX" else parse_pdf(data)
    require(mode == "EXPORT", "EXAM_FORMAT_UNSUPPORTED")
    require(request.get("format") in ("DOCX", "PDF"), "EXAM_FORMAT_UNSUPPORTED")
    data = render_docx(request["snapshot"], request["audience"])
    if request["format"] == "PDF":
        data = convert_pdf(data)
    require(len(data) <= MAX_FILE, "EXAM_EXPORT_TOO_LARGE")
    return {"data": base64.b64encode(data).decode("ascii")}


if __name__ == "__main__":
    try:
        raw = sys.stdin.buffer.read(16 * 1024 * 1024 + 1)
        require(len(raw) <= 16 * 1024 * 1024, "EXAM_DOCUMENT_TOO_LARGE")
        value = json.loads(raw.decode("utf-8"), object_pairs_hook=unique_object)
        response = {"ok": True, "result": run(value)}
    except Rejected as error:
        response = {"ok": False, "errorCode": error.code}
    except Exception:
        response = {"ok": False, "errorCode": "EXAM_DOCUMENT_FAILED"}
    sys.stdout.buffer.write(json.dumps(response, ensure_ascii=False).encode("utf-8"))
