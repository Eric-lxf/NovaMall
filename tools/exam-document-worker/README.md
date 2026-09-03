# Isolated exam document worker

See [deployment and test runbook](../../docs/ai-exam-runbook.md) and [actual verification status](../../docs/ai-exam-mvp-progress.md).

The process reads one bounded JSON request from stdin and returns one JSON result on stdout. It is not an HTTP service. `mode=PARSE` accepts base64 DOCX/PDF; `mode=EXPORT` accepts a server-validated frozen paper snapshot and `STUDENT`/`TEACHER` audience. Uploaded documents are never opened in LibreOffice.

Production invocation must use the resource/security limits in `DockerExamDocumentRunner`, not a direct unsandboxed Python process. The requirements pins were tested locally; the Linux image and LibreOffice conversion remain separate release gates.

`test_worker.py` creates synthetic DOCX/PDF inputs and a four-type paper snapshot in memory. After installing `requirements.txt` in an isolated Python environment, run `python -m unittest -v test_worker` from this directory; no local fonts, Java-generated files, Docker, or live AI are required. It deliberately simulates missing-converter failure; that test passing does not mean PDF export works. Actual Chinese documents and LibreOffice conversion are checked separately by the Docker acceptance workflow in the runbook.
