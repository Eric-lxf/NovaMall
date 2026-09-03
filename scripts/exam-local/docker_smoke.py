"""Opt-in real MySQL/JWT/document smoke test. Never uses the project's .env.

Requires built ruoyi-admin.jar, Java 17+, Docker and pre-pulled mysql:8.0,
redis:7, and the configured document image. Creates uniquely labelled local
containers, random ephemeral secrets, RAM-only databases, synthetic data and
an AI-disabled app; removes only its own containers in finally. No model calls.
"""
import argparse
import io
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import socket
import subprocess
import time
import urllib.error
import urllib.request
import uuid
import zipfile

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "tmp/exam-docker-qa"
FLAGS = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0


class Smoke:
    def __init__(self, args):
        self.args = args
        self.run_id = uuid.uuid4().hex[:12]
        self.names = []
        self.process = None
        self.log = None
        self.passed = []
        self.env = os.environ.copy()
        self.env["MYSQL_ROOT_PASSWORD"] = secrets.token_hex(24)
        self.env["MYSQL_PWD"] = self.env["MYSQL_ROOT_PASSWORD"]
        self.tokens = {}
        self.out = OUT / self.run_id
        self.out.mkdir(parents=True)
        self.base = f"http://127.0.0.1:{args.http_port}"

    def command(self, command, data=None, timeout=120):
        result = subprocess.run(command, input=data, stdout=subprocess.PIPE,
                                stderr=subprocess.PIPE, env=self.env,
                                timeout=timeout, creationflags=FLAGS)
        if result.returncode:
            # Arguments/env can contain ephemeral secrets. Do not log either.
            error = result.stderr.decode("utf-8", errors="replace")
            for key in ("MYSQL_ROOT_PASSWORD", "MYSQL_PWD", "TOKEN_SECRET"):
                if self.env.get(key):
                    error = error.replace(self.env[key], "[redacted]")
            raise RuntimeError(f"Command {command[0]} failed: {error[-2000:]}")
        return result.stdout

    def check(self, name, condition):
        if not condition:
            raise AssertionError(name)
        self.passed.append(name)
        print("PASS " + name, flush=True)

    def sql(self, query, database="nova_mall"):
        return self.command(["docker", "exec", "-i", "-e", "MYSQL_PWD", self.mysql,
                             "mysql", "-uroot", "--default-character-set=utf8mb4",
                             "--batch", "--skip-column-names", database], query.encode("utf-8")).decode("utf-8")

    def sql_file(self, name, database="nova_mall"):
        return self.sql((ROOT / "sql" / name).read_text(encoding="utf-8-sig"), database)

    def start(self):
        for port in (self.args.mysql_port, self.args.redis_port, self.args.http_port):
            with socket.socket() as listener:
                listener.bind(("127.0.0.1", port))
        for image in ("mysql:8.0", "redis:7", self.args.image):
            self.command(["docker", "image", "inspect", image, "--format", "{{.Id}}"])
        for kind, image, port, internal, extra in (
            ("mysql", "mysql:8.0", self.args.mysql_port, 3306,
             ["--memory=1g", "--tmpfs=/var/lib/mysql:rw,size=1g", "-e", "MYSQL_ROOT_PASSWORD", "-e", "MYSQL_DATABASE=nova_mall"]),
            ("redis", "redis:7", self.args.redis_port, 6379,
             ["--memory=128m", "--tmpfs=/data:rw,size=64m"]),
        ):
            name = f"novamall-exam-qa-{kind}-{self.run_id}"
            tail = ["--default-time-zone=+08:00", "--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci", "--innodb-buffer-pool-size=128M"] if kind == "mysql" else ["redis-server", "--save", "", "--appendonly", "no"]
            self.command(["docker", "run", "-d", "--pull=never", "--name", name,
                          "--label", "novamall.exam.qa=" + self.run_id, "-e", "TZ=Asia/Shanghai",
                          "-p", f"127.0.0.1:{port}:{internal}", *extra, image, *tail])
            self.names.append(name)
            if kind == "mysql":
                self.mysql = name
        deadline = time.monotonic() + 120
        while time.monotonic() < deadline:
            try:
                self.sql("select 1")
                break
            except RuntimeError:
                time.sleep(1)
        else:
            raise RuntimeError("Disposable MySQL was not ready in 120 seconds")
        print("Isolated MySQL and Redis ready; initializing synthetic test database", flush=True)
        # Follow the project's ordered baseline, but never run its compose stack.
        compose = (ROOT / "docker-compose.yml").read_text(encoding="utf-8")
        baseline = re.findall(r"- ./sql/([^:]+):/docker-entrypoint-initdb.d/", compose)
        for name in baseline:
            if name == "mall_phase_b_migrate_front_category.sql":
                # Existing, unrelated migration has MySQL ERROR 1137 (temporary
                # table reopened). This exam test does not validate that migration.
                print("SKIP unrelated legacy category data migration (known MySQL 1137)", flush=True)
                continue
            self.sql_file(name)
        for _ in range(2):
            for name in ("exam_schema.sql", "exam_workflow_schema.sql", "exam_menu_seed.sql", "exam_workflow_menu_seed.sql"):
                self.sql_file(name)
        self.check("20 exam tables created on MySQL 8 and migration replay succeeds",
                   self.sql("select count(*) from information_schema.tables where table_schema='nova_mall' and table_name like 'exam\\_%'").strip() == "20")
        self.check("menu seeds do not auto-grant roles",
                   self.sql("select count(*) from sys_role_menu r join sys_menu m on m.menu_id=r.menu_id where m.perms like 'exam:%'").strip() == "0")
        self.check("menu seeds have no duplicate permission (task list intentionally shared)",
                   self.sql("select count(*) from (select perms from sys_menu where perms like 'exam:%' and perms <> 'exam:task:list' group by perms having count(*)>1) x").strip() == "0")
        self.sql("create database exam_conflict character set utf8mb4; create table exam_conflict.sys_menu like nova_mall.sys_menu; insert into exam_conflict.sys_menu(menu_name,parent_id,path,menu_type) values('Other feature',0,'exam','M')")
        self.sql_file("exam_menu_seed.sql", "exam_conflict")
        self.sql_file("exam_workflow_menu_seed.sql", "exam_conflict")
        self.check("existing unrelated /exam route preserved without child menu insertion",
                   self.sql("select count(*) from sys_menu", "exam_conflict").strip() == "1")
        self.check("database timezone is +08:00", self.sql("select @@session.time_zone").strip() == "+08:00")
        # Disable scheduled/demo side effects in this disposable database only.
        self.sql("update sys_job set status='1'; update sys_config set config_value='false' where config_key='sys.account.captchaEnabled'")
        for role, key in ((901, "exam_qa_author"), (902, "exam_qa_reviewer"), (903, "exam_qa_none")):
            self.sql(f"insert into sys_role(role_id,role_name,role_key,role_sort,status,del_flag) values({role},'{key}','{key}',99,'0','0')")
        self.sql("insert into sys_role_menu select 901,menu_id from sys_menu where perms like 'exam:%' and perms not in ('exam:review:list','exam:review:approve'); insert into sys_role_menu select 902,menu_id from sys_menu where perms in ('exam:task:list','exam:review:list','exam:review:approve')")
        self.sql("insert into sys_role_menu select 901,menu_id from sys_menu where parent_id=0 and path='exam'; insert into sys_role_menu select 902,menu_id from sys_menu where parent_id=0 and path='exam'")
        for user, name, role in ((901, "exam_qa_owner", 901), (902, "exam_qa_stranger", 901), (903, "exam_qa_reviewer", 902), (904, "exam_qa_none", 903)):
            self.sql(f"insert into sys_user(user_id,dept_id,user_name,nick_name,user_type,password,status,del_flag,create_time) select {user},103,'{name}','QA synthetic user','00',password,'0','0',now() from sys_user where user_id=1; insert into sys_user_role values({user},{role})")
        self.env.update({"TOKEN_SECRET": secrets.token_hex(32), "EXAM_ENABLED": "true", "EXAM_AI_ENABLED": "false",
                         "EXAM_DOCUMENT_IMAGE": self.args.image, "EXAM_PRIVATE_ROOT": str(self.out / "private")})
        # Do not lock Maven's output JAR while browser QA is running on Windows.
        runtime_jar = self.out / "ruoyi-admin-qa.jar"
        shutil.copy2(ROOT / "backend/ruoyi-admin/target/ruoyi-admin.jar", runtime_jar)
        args = [self.args.java, "-Duser.timezone=Asia/Shanghai", "-Dfile.encoding=UTF-8", "-Xmx768m", "-jar", str(runtime_jar),
                "--server.address=127.0.0.1", f"--server.port={self.args.http_port}",
                f"--spring.datasource.druid.master.url=jdbc:mysql://127.0.0.1:{self.args.mysql_port}/nova_mall?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
                "--spring.datasource.druid.master.username=root",
                "--spring.datasource.druid.master.password=${MYSQL_ROOT_PASSWORD}",
                "--spring.data.redis.host=127.0.0.1", f"--spring.data.redis.port={self.args.redis_port}",
                "--spring.data.redis.password=", "--spring.quartz.auto-startup=false",
                "--logging.level.com.ruoyi=INFO", "--blog.external-api.enabled=false", "--wechat.enabled=false", "--novamall.oss.enabled=false",
                f"--logging.config={ROOT / 'scripts/exam-local/logback-qa.xml'}",
                f"--ruoyi.profile={self.out / 'public'}", f"--blog.file.upload-dir={self.out / 'blog-public'}"]
        self.log = (self.out / "backend.log").open("wb")
        self.process = subprocess.Popen(args, cwd=self.out, env=self.env, stdout=self.log, stderr=subprocess.STDOUT, creationflags=FLAGS)
        deadline = time.monotonic() + 120
        while time.monotonic() < deadline:
            if self.process.poll() is not None:
                raise RuntimeError(f"Backend stopped during startup; inspect {self.out / 'backend.log'}")
            try:
                self.request("GET", "/captchaImage", user=None)
                break
            except (OSError, ValueError):
                time.sleep(1)
        else:
            raise RuntimeError("Backend not ready in 120 seconds")
        print("Real Spring Boot backend ready at " + self.base, flush=True)

    def request(self, method, path, body=None, user="owner", key=None, binary=False, content_type=None):
        headers = {}
        if user:
            headers["Authorization"] = "Bearer " + self.tokens[user]
        if key:
            headers["Idempotency-Key"] = key
        if isinstance(body, bytes):
            data = body
            headers["Content-Type"] = content_type
        elif body is not None:
            data = json.dumps(body, ensure_ascii=False).encode("utf-8")
            headers["Content-Type"] = "application/json"
        else:
            data = None
        request = urllib.request.Request(self.base + path, data=data, headers=headers, method=method)
        try:
            response = urllib.request.urlopen(request, timeout=30)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            payload = response.read()
            if binary and response.headers.get("Content-Disposition"):
                return payload, dict(response.headers)
            result = json.loads(payload)
            return result

    def ok(self, method, path, body=None, **kwargs):
        result = self.request(method, path, body, **kwargs)
        if result.get("code") != 200:
            raise AssertionError(f"{method} {path}: {result}")
        return result.get("data", result)

    def wait_task(self, task, expected="SUCCEEDED"):
        deadline = time.monotonic() + 110
        while time.monotonic() < deadline:
            current = self.ok("GET", "/exam/tasks/" + task["id"])
            if current["status"] in ("SUCCEEDED", "FAILED", "CANCELLED", "NEEDS_CONFIRMATION", "PARTIAL_SUCCESS"):
                self.check("task " + task["kind"] + " ends " + expected, current["status"] == expected)
                return current
            time.sleep(0.5)
        raise AssertionError("Task did not finish: " + task["id"])

    @staticmethod
    def revision(value):
        return {"expectedRevision": value["revision"]}

    def upload(self, filename, data, key=None):
        boundary = "ExamQa" + uuid.uuid4().hex
        body = (f'--{boundary}\r\nContent-Disposition: form-data; name="title"\r\n\r\nQA {filename}\r\n'
                f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{filename}"\r\nContent-Type: application/octet-stream\r\n\r\n').encode() + data + f"\r\n--{boundary}--\r\n".encode()
        return self.ok("POST", "/exam/sources/document", body, key=key or uuid.uuid4().hex, content_type="multipart/form-data; boundary=" + boundary)

    def test(self):
        for key in ("owner", "stranger", "reviewer", "none"):
            login = self.ok("POST", "/login", {"username": "exam_qa_" + key, "password": "admin123"}, user=None)
            self.tokens[key] = login["token"]
        self.check("four real JWT logins", len(self.tokens) == 4)
        self.check("anonymous exam request rejected", self.request("GET", "/exam/sources", user=None)["code"] == 401)
        self.check("JWT without exam permission rejected", self.request("GET", "/exam/sources", user="none")["code"] == 403)
        capabilities = self.ok("GET", "/exam/capabilities")
        self.check("task readiness with live MySQL worker", capabilities["taskReady"] and capabilities["workerEnabled"])
        self.check("AI explicitly disabled", capabilities["aiEnabled"] is False)
        self.check("workflow schema readiness", self.ok("GET", "/exam/workflow/capabilities")["workflowReady"])
        task = self.ok("POST", "/exam/tasks/check", {"title": "Docker QA"}, key="self-check-0000000001")
        duplicate = self.ok("POST", "/exam/tasks/check", {"title": "Docker QA"}, key="self-check-0000000001")
        self.check("idempotent task submission", task["id"] == duplicate["id"])
        self.wait_task(task)
        self.check("task owner isolation", self.request("GET", "/exam/tasks/" + task["id"], user="stranger")["code"] != 200)
        source = self.ok("POST", "/exam/sources/text", {"title": "安全培训（合成测试）", "text": "作业前必须检查防护设备。发现故障应停止作业并报告主管。"})
        sid, sv = source["id"], source["currentVersionId"]
        self.check("private source isolation", self.request("GET", "/exam/sources/" + sid, user="stranger")["code"] != 200)
        fid = self.ok("GET", f"/exam/source-versions/{sv}/fragments")[0]["id"]
        self.ok("POST", f"/exam/source-versions/{sv}/confirm", {"expectedRevision": 0, "externalAllowed": False, "fragmentIds": [fid]})
        point = self.ok("POST", "/exam/knowledge", {"sourceVersionId": sv, "name": "作业安全", "sourceRefs": [{"fragmentId": fid, "quote": "作业前必须检查防护设备。"}]})
        point = self.ok("POST", f"/exam/knowledge/{point['id']}/confirm", self.revision(point))
        types = ["SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE", "SHORT_ANSWER"]
        slots = [{"slotId": f"q{i+1}", "type": kind, "targetDifficulty": "EASY", "score": 25, "knowledgePointIds": [point["id"]], "sourceFragmentIds": [fid]} for i, kind in enumerate(types)]
        bp = self.ok("POST", "/exam/blueprints", {"title": "安全测试蓝图", "settings": {"durationMinutes": 30, "totalScore": 100, "sourceVersionIds": [sv]}, "slots": slots})
        bp = self.ok("POST", f"/exam/blueprints/{bp['id']}/confirm", self.revision(bp) | {"contentHash": bp["contentHash"]})
        approved = []
        for index, kind in enumerate(types):
            content = {"slotId": slots[index]["slotId"], "type": kind, "stem": ["作业前应采取哪项安全措施？", "发现设备故障应采取哪些措施？", "作业前必须检查防护设备，这一说法是否正确？", "请说明作业前的检查要求以及发现故障后的处理步骤。"][index],
                       "analysis": "依据培训资料，作业前检查设备；发现故障则停工并报告主管。", "knowledgePointIds": [point["id"]],
                       "sourceRefs": [{"sourceVersionId": sv, "fragmentId": fid, "quote": "作业前必须检查防护设备。"}]}
            if index < 2:
                labels = ["检查防护设备", "直接开始作业"] if index == 0 else ["停止作业", "报告主管"]
                content.update(options=[{"id": chr(65+i), "text": label} for i, label in enumerate(labels)], correctOptionIds=["A"] if index == 0 else ["A", "B"])
            elif index == 2:
                content["answerBoolean"] = True
            else:
                content.update(referenceAnswer="先检查设备；故障时停工并报告。", rubric=[{"point": "检查设备", "weight": 50}, {"point": "停工并报告", "weight": 50}])
            q = self.ok("POST", "/exam/questions", {"blueprintId": bp["id"], "content": content})
            review = self.revision(q) | {"contentHash": q["versions"][0]["contentHash"]}
            q = self.ok("POST", f"/exam/question-versions/{q['currentVersionId']}/submit", review)
            self.check("author cannot self-approve via missing permission " + kind,
                       self.request("POST", f"/exam/question-versions/{q['currentVersionId']}/review", self.revision(q) | {"contentHash": review["contentHash"], "decision": "APPROVED", "reason": "QA", "manualVerification": True})["code"] == 403)
            q = self.ok("POST", f"/exam/question-versions/{q['currentVersionId']}/review", self.revision(q) | {"contentHash": review["contentHash"], "decision": "APPROVED", "reason": "合成测试，逐项核对原文与答案", "manualVerification": True}, user="reviewer")
            approved.append(q)
        self.check("reviewer cannot download raw private source", self.request("GET", f"/exam/source-versions/{sv}/download", user="reviewer")["code"] == 403)
        paper = self.ok("POST", "/exam/papers", {"title": "安全培训测试卷", "draft": {"durationMinutes": 30, "totalScore": 100, "items": [{"questionVersionId": q["currentVersionId"], "score": 25} for q in approved]}})
        paper = self.ok("POST", f"/exam/papers/{paper['id']}/finalize", self.revision(paper))
        pv = paper["currentVersionId"]
        student = self.ok("GET", f"/exam/paper-versions/{pv}/student")
        teacher = self.ok("GET", f"/exam/paper-versions/{pv}/teacher")
        self.check("four approved question types finalized", paper["status"] == "FINALIZED" and len(student["items"]) == 4)
        self.check("student JSON contains no answer/evidence fields", not any(key in json.dumps(student) for key in ("analysis", "correctOptionIds", "answerBoolean", "referenceAnswer", "rubric", "sourceRefs")))
        (self.out / "paper-snapshot.json").write_text(json.dumps(teacher, ensure_ascii=False, indent=2), encoding="utf-8")
        exports = []
        for fmt, audience in (("DOCX", "STUDENT"), ("DOCX", "TEACHER"), ("PDF", "STUDENT"), ("PDF", "TEACHER"), ("XLSX", "TEACHER")):
            task = self.ok("POST", "/exam/exports", {"paperVersionId": pv, "format": fmt, "audience": audience}, key=uuid.uuid4().hex)
            self.wait_task(task)
            export = next(row for row in self.ok("GET", "/exam/exports") if row["taskId"] == task["id"])
            result = self.request("GET", f"/exam/exports/{export['id']}/download", binary=True)
            self.check(fmt + " " + audience + " download ready", isinstance(result, tuple))
            data, headers = result
            self.check(fmt + " " + audience + " private download headers", "no-store" in headers.get("Cache-Control", "") and headers.get("X-Content-Type-Options") == "nosniff")
            self.check(fmt + " " + audience + " owner isolation", self.request("GET", f"/exam/exports/{export['id']}/download", user="stranger")["code"] != 200)
            (self.out / f"paper-{audience.lower()}.{fmt.lower()}").write_bytes(data)
            if fmt == "DOCX":
                with zipfile.ZipFile(io.BytesIO(data)) as archive:
                    xml = archive.read("word/document.xml").decode()
                self.check("DOCX " + audience + " answer projection", ("参考答案：" in xml) == (audience == "TEACHER"))
            elif fmt == "PDF":
                self.check("PDF signature " + audience, data.startswith(b"%PDF-"))
            else:
                with zipfile.ZipFile(io.BytesIO(data)) as archive:
                    xml = archive.read("xl/worksheets/sheet1.xml").decode()
                self.check("XLSX has no executable formulas", "<f>" not in xml and "<f " not in xml)
            exports.append(export)
        for fmt in ("docx", "pdf"):
            data = (self.out / f"paper-student.{fmt}").read_bytes()
            key = uuid.uuid4().hex
            parsed = self.upload("input." + fmt, data, key)
            repeated = self.upload("input." + fmt, data, key)
            self.check(fmt + " upload idempotency", parsed["id"] == repeated["id"])
            self.wait_task(parsed)
        invalid = self.upload("invalid.pdf", b"%PDF-invalid")
        self.wait_task(invalid, "FAILED")
        # 手动重试只操作隔离库中的损坏合成文件；复用原文件，不会调用模型。
        retry_path = f"/exam/jobs/{invalid['id']}/retry"
        self.check("retry preview owner isolation", self.request("GET", retry_path + "-preview", user="stranger")["code"] != 200)
        self.check("retry preview permission required", self.request("GET", retry_path + "-preview", user="none")["code"] == 403)
        plan = self.ok("GET", retry_path + "-preview")
        self.check("document retry needs no model consent", plan["ai"] is False and plan["itemCount"] == 1)
        retry_body = {"expectedRevision": plan["expectedRevision"], "planFingerprint": plan["planFingerprint"]}
        file_count = self.sql("select count(*) from exam_file").strip()
        retry_key = uuid.uuid4().hex
        retried = self.ok("POST", retry_path, retry_body, key=retry_key)
        self.check("retry creates fresh task", retried["id"] != invalid["id"])
        repeated = self.ok("POST", retry_path, retry_body, key=retry_key)
        self.check("retry repeated key returns same task", repeated["id"] == retried["id"])
        self.check("retry different key cannot duplicate successor", self.request("POST", retry_path, retry_body, key=uuid.uuid4().hex)["code"] != 200)
        self.wait_task(retried, "FAILED")
        original_detail = self.ok("GET", f"/exam/jobs/{invalid['id']}")
        retry_detail = self.ok("GET", f"/exam/jobs/{retried['id']}")
        self.check("retry links both audit records", original_detail["progress"]["retryTaskId"] == retried["id"] and retry_detail["retryOfTaskId"] == invalid["id"])
        self.check("retry preserves original failure", self.ok("GET", f"/exam/tasks/{invalid['id']}")["status"] == "FAILED")
        self.check("retry reuses private upload", self.sql("select count(*) from exam_file").strip() == file_count)
        # Revocation must be checked at download time, even with a cached JWT.
        self.sql("delete r from sys_role_menu r join sys_menu m on r.menu_id=m.menu_id where r.role_id=901 and m.perms='exam:paper:answers'")
        teacher_export = next(e for e in exports if e["audience"] == "TEACHER")
        self.check("teacher download rejects revoked permission with still-valid JWT", self.request("GET", f"/exam/exports/{teacher_export['id']}/download")["code"] != 200)
        student_export = next(e for e in exports if e["audience"] == "STUDENT")
        self.check("student download remains available without teacher permission", isinstance(self.request("GET", f"/exam/exports/{student_export['id']}/download", binary=True), tuple))
        self.ok("POST", f"/exam/sources/{sid}/versions/text", self.revision(self.ok("GET", "/exam/sources/" + sid)) | {"title": "安全培训修订版", "text": "新的作业规定，需要重新审核旧题。"})
        self.check("source revision invalidates historical export download", self.request("GET", f"/exam/exports/{student_export['id']}/download")["code"] != 200)
        self.check("source revision does not mutate historical paper snapshot", self.ok("GET", f"/exam/paper-versions/{pv}/student") == student)
        self.check("no model calls persisted", self.sql("select count(*) from exam_ai_call").strip() == "0")
        self.check("audit log omits source text and model answers", self.sql("select count(*) from sys_oper_log where oper_url like '/exam/%' and (coalesce(oper_param,'')<>'' or coalesce(json_result,'')<>'')").strip() == "0")

    def close(self):
        if self.process and self.process.poll() is None:
            self.process.terminate()
            try:
                self.process.wait(timeout=20)
            except subprocess.TimeoutExpired:
                self.process.kill()
                self.process.wait(timeout=10)
        if self.log:
            self.log.close()
        for name in reversed(self.names):
            # Exact target + unique label guard; never compose-down the user's stack.
            label = self.command(["docker", "inspect", name, "--format", '{{index .Config.Labels "novamall.exam.qa"}}']).decode().strip()
            if label != self.run_id:
                raise RuntimeError("Cleanup label mismatch; leaving container intact")
            self.command(["docker", "rm", "-f", "-v", name])
        print("Removed this run's disposable containers and RAM-only databases; existing containers untouched", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java", default="java")
    parser.add_argument("--image", default="novamall/exam-document:20260903")
    parser.add_argument("--mysql-port", type=int, default=13306)
    parser.add_argument("--redis-port", type=int, default=16379)
    parser.add_argument("--http-port", type=int, default=18080)
    parser.add_argument("--hold-seconds", type=int, default=0, help="Optional bounded window for browser QA before cleanup")
    args = parser.parse_args()
    smoke = Smoke(args)
    success = False
    try:
        smoke.start()
        smoke.test()
        success = True
        if args.hold_seconds:
            print(f"Browser QA window: {args.hold_seconds}s; local synthetic login exam_qa_owner / admin123", flush=True)
            deadline = time.monotonic() + min(args.hold_seconds, 600)
            while time.monotonic() < deadline:
                time.sleep(1)
    finally:
        smoke.close()
        (smoke.out / "result.json").write_text(json.dumps({"passed": smoke.passed, "success": success, "liveModelCalls": 0}, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"Evidence: {smoke.out}; checks passed: {len(smoke.passed)}; success: {success}", flush=True)


if __name__ == "__main__":
    main()
