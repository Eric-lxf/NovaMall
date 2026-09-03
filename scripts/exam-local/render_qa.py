"""Render existing synthetic exports with the canonical document skill renderer.

Only its soffice process is adapted to Docker on hosts without LibreOffice.
QA mounts contain only the selected synthetic export and renderer's temp output;
production document tasks still use stdin/stdout and NO host mounts.
"""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import uuid
from pypdf import PdfReader

parser = argparse.ArgumentParser()
parser.add_argument("directory", type=Path)
parser.add_argument("--renderer", type=Path, required=True)
parser.add_argument("--image", default="novamall/exam-document:20260903")
args = parser.parse_args()
spec = importlib.util.spec_from_file_location("canonical_docx_renderer", args.renderer)
renderer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(renderer)


def docker_soffice(command, env, verbose):
    if command[0] != "soffice":
        raise ValueError("Only soffice adaptation is supported")
    source = Path(command[-1]).resolve()
    output = Path(command[command.index("--outdir") + 1]).resolve()
    converted = ["-env:UserInstallation=file:///tmp/lo-profile" if arg.startswith("-env:")
                 else "/input/" + source.name if arg == command[-1]
                 else "/output" if arg == str(output)
                 else arg for arg in command[1:]]
    name = "novamall-exam-render-qa-" + uuid.uuid4().hex
    invocation = ["docker", "run", "--rm", "--pull=never", "--name", name,
                  "--network=none", "--read-only", "--cap-drop=ALL", "--security-opt=no-new-privileges",
                  "--pids-limit=64", "--memory=512m", "--memory-swap=512m", "--cpus=1", "--user=65534:65534",
                  "--tmpfs=/tmp:rw,noexec,nosuid,size=192m,mode=1777", "-e", "HOME=/tmp",
                  "--mount", f"type=bind,source={source},target=/input/{source.name},readonly",
                  "--mount", f"type=bind,source={output},target=/output",
                  "--entrypoint", "soffice", args.image, *converted]
    try:
        return subprocess.run(invocation, capture_output=True, text=True, timeout=90,
                              creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)
    finally:
        subprocess.run(["docker", "rm", "-f", name], capture_output=True, timeout=15)


renderer._run_cmd = docker_soffice
results = {}
for audience in ("student", "teacher"):
    docx = args.directory / f"paper-{audience}.docx"
    pages = renderer.rasterize(str(docx), str(args.directory / f"{audience}-docx-render"), 120, False, True)
    pdf = args.directory / f"paper-{audience}.pdf"
    pdf_output = args.directory / f"{audience}-pdf-render"
    pdf_output.mkdir(exist_ok=True)
    subprocess.run(["pdftoppm", "-r", "120", "-png", str(pdf), str(pdf_output / "page")], check=True, timeout=60)
    reader = PdfReader(pdf)
    text = "\n".join(page.extract_text() for page in reader.pages)
    assert "安全培训测试卷" in text
    assert ("参考答案" in text) == (audience == "teacher")
    assert ("评分点" in text) == (audience == "teacher")
    assert ("依据（资料版本" in text) == (audience == "teacher")
    assert "检查防护设备" in text
    results[audience] = {"docxPages": len(pages), "pdfPages": len(reader.pages), "textAndAnswerProjection": "passed"}
(args.directory / "render-result.json").write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
print(json.dumps(results, ensure_ascii=False))
