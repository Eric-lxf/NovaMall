"""只读验证命题部署配置；不读取项目 .env、不启动服务、不连接生产或模型。"""
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import textwrap
import unittest


ROOT = Path(__file__).resolve().parents[2]
COMPOSE_FILES = ("docker-compose.yml", "docker-compose.prod.yml")
FLAGS = ("EXAM_ENABLED", "EXAM_SCHEMA_READY", "EXAM_WORKER_ENABLED", "EXAM_AI_ENABLED")
PRIVATE_ROOT = "/data/exam-private"
WORKFLOW = ROOT / ".github/workflows/deploy-ecs.yml"


def bash_command():
    if os.name == "nt":
        candidates = [Path(os.environ.get("ProgramFiles", r"C:\Program Files")) / "Git/bin/bash.exe"]
        git = shutil.which("git")
        if git:
            candidates.insert(0, Path(git).resolve().parent.parent / "bin/bash.exe")
        for candidate in candidates:
            if candidate.is_file():
                return str(candidate)
        raise RuntimeError("Windows 验证需要 Git for Windows 提供的 bash")
    found = shutil.which("bash")
    if not found:
        raise RuntimeError("验证需要 bash")
    return found


class ExamDeploymentConfigTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workflow = WORKFLOW.read_text(encoding="utf-8")
        cls.deploy_script = textwrap.dedent(cls.workflow.rsplit("          script: |", 1)[1]).strip()
        cls.guard = cls.deploy_script.split("# 智能命题配置校验开始：", 1)[1].split("\n", 1)[1]
        cls.guard = cls.guard.split("# 智能命题配置校验结束。", 1)[0]
        cls.bash = bash_command()
        cls.compose_variables = set()
        for filename in COMPOSE_FILES:
            cls.compose_variables.update(re.findall(r"\$\{([A-Z][A-Z0-9_]*)", (ROOT / filename).read_text(encoding="utf-8")))

    def render(self, filename, overrides=None):
        env = os.environ.copy()
        for key in self.compose_variables | set(FLAGS) | {"EXAM_PRIVATE_ROOT", "EXAM_DOCUMENT_IMAGE"}:
            env.pop(key, None)
        # 所有值均为合成配置，不使用真实密码或镜像凭证；config 命令不会拉取镜像。
        env.update({"TOKEN_SECRET": "synthetic-configuration-only-" * 4,
                    "BACKEND_IMAGE": "example.invalid/novamall/backend:test",
                    "FRONTEND_IMAGE": "example.invalid/novamall/frontend:test",
                    "MYSQL_HOST": "mysql", "MYSQL_PORT": "3306", "MYSQL_DATABASE": "nova_mall",
                    "MYSQL_USER": "test", "MYSQL_PASSWORD": "synthetic-only",
                    "REDIS_HOST": "redis", "REDIS_PORT": "6379", "REDIS_PASSWORD": "synthetic-only",
                    "NOVAMALL_OSS_ENABLED": "false", "NOVAMALL_OSS_BUCKET": "",
                    "NOVAMALL_OSS_ENDPOINT": "", "NOVAMALL_OSS_DOMAIN": "",
                    "BACKEND_PORT": "18080", "FRONTEND_PORT": "18081", "RUOYI_LOG_PATH": "/tmp/exam-config-logs",
                    "COMPOSE_DISABLE_ENV_FILE": "1"})
        env.update(overrides or {})
        temp_root = ROOT / "tmp/exam-docker-qa"
        temp_root.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(prefix="compose-config-", dir=temp_root) as temp:
            directory = Path(temp).resolve()
            self.assertEqual(directory.parent, temp_root.resolve())
            empty_env = directory / "empty.env"
            empty_env.touch()
            result = subprocess.run(["docker", "compose", "--env-file", str(empty_env), "-p", "exam-config-test",
                                     "-f", str(ROOT / filename), "config", "--format", "json"],
                                    cwd=directory, env=env, capture_output=True, text=True, encoding="utf-8", timeout=30)
        self.assertEqual(result.returncode, 0, result.stderr)
        return json.loads(result.stdout)

    def run_guard(self, values=None):
        env = os.environ.copy()
        for flag in FLAGS:
            env.pop(flag, None)
        env.update(values or {})
        script = "set -eu\n" + self.guard + '\nprintf "%s|%s|%s|%s" "$EXAM_ENABLED" "$EXAM_SCHEMA_READY" "$EXAM_WORKER_ENABLED" "$EXAM_AI_ENABLED"\n'
        return subprocess.run([self.bash, "-c", script], env=env, capture_output=True,
                              text=True, encoding="utf-8", timeout=10)

    def test_compose_defaults_remain_disabled(self):
        for filename in COMPOSE_FILES:
            with self.subTest(filename=filename):
                env = self.render(filename)["services"]["backend"]["environment"]
                self.assertEqual("false", env["EXAM_ENABLED"])
                self.assertEqual("true", env["EXAM_WORKER_ENABLED"])
                self.assertEqual("false", env["EXAM_AI_ENABLED"])

    def test_compose_passes_explicit_flags(self):
        for filename in COMPOSE_FILES:
            with self.subTest(filename=filename):
                env = self.render(filename, {"EXAM_ENABLED": "true", "EXAM_WORKER_ENABLED": "false",
                                             "EXAM_AI_ENABLED": "false"})["services"]["backend"]["environment"]
                self.assertEqual("true", env["EXAM_ENABLED"])
                self.assertEqual("false", env["EXAM_WORKER_ENABLED"])
                self.assertEqual("false", env["EXAM_AI_ENABLED"])

    def test_private_volume_is_persistent_and_backend_only(self):
        for filename in COMPOSE_FILES:
            with self.subTest(filename=filename):
                config = self.render(filename, {"EXAM_PRIVATE_ROOT": "/data/uploads/unsafe"})
                backend = config["services"]["backend"]
                self.assertEqual(PRIVATE_ROOT, backend["environment"]["EXAM_PRIVATE_ROOT"])
                self.assertIn("exam_private_data", config["volumes"])
                mounts = [mount for mount in backend["volumes"] if mount["target"] == PRIVATE_ROOT]
                self.assertEqual(1, len(mounts))
                self.assertEqual("volume", mounts[0]["type"])
                self.assertEqual("exam_private_data", mounts[0]["source"])
                for name, service in config["services"].items():
                    if name != "backend":
                        self.assertFalse(any(mount.get("source") == "exam_private_data" for mount in service.get("volumes", [])))

    def test_compose_does_not_grant_document_runner_host_access(self):
        for filename in COMPOSE_FILES:
            with self.subTest(filename=filename):
                backend = self.render(filename, {"EXAM_DOCUMENT_IMAGE": "unconfigured/example:latest"})["services"]["backend"]
                self.assertFalse(backend.get("privileged", False))
                self.assertNotIn("DOCKER_HOST", backend["environment"])
                self.assertNotIn("EXAM_DOCUMENT_IMAGE", backend["environment"])
                self.assertFalse(any("docker.sock" in json.dumps(mount) for mount in backend.get("volumes", [])))

    def test_actions_maps_persists_and_forwards_every_exam_flag(self):
        forwarding = re.search(r"^\s+envs: (.+)$", self.workflow, re.MULTILINE).group(1).split(",")
        for flag in FLAGS:
            with self.subTest(flag=flag):
                self.assertIn(f"{flag}: ${{{{ vars.{flag} }}}}", self.workflow)
                self.assertIn(flag, forwarding)
                self.assertIn(f"printf '{flag}=%s\\n' \"${flag}\"", self.deploy_script)

    def test_guard_defaults_and_confirmed_basic_enablement(self):
        default = self.run_guard()
        self.assertEqual(0, default.returncode, default.stderr)
        self.assertEqual("false|false|true|false", default.stdout)
        enabled = self.run_guard({"EXAM_ENABLED": "true", "EXAM_SCHEMA_READY": "true"})
        self.assertEqual(0, enabled.returncode, enabled.stderr)
        self.assertEqual("true|true|true|false", enabled.stdout)

    def test_guard_rejects_enablement_without_migration_confirmation(self):
        result = self.run_guard({"EXAM_ENABLED": "true"})
        self.assertNotEqual(0, result.returncode)
        self.assertIn("EXAM_SCHEMA_READY=true", result.stderr)

    def test_guard_rejects_invalid_or_multiline_flags(self):
        for flag in FLAGS:
            for value in ("invalid", "1", "yes", "true\nEXAM_AI_ENABLED=true"):
                with self.subTest(flag=flag, value=value):
                    self.assertNotEqual(0, self.run_guard({flag: value}).returncode)

    def test_guard_normalizes_existing_uppercase_github_values(self):
        result = self.run_guard({"EXAM_ENABLED": "TRUE", "EXAM_SCHEMA_READY": "True",
                                 "EXAM_WORKER_ENABLED": "TRUE", "EXAM_AI_ENABLED": "FALSE"})
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("true|true|true|false", result.stdout)

    def test_guard_keeps_ai_independent_and_requires_enabled_module(self):
        self.assertNotEqual(0, self.run_guard({"EXAM_AI_ENABLED": "true"}).returncode)
        accepted = self.run_guard({"EXAM_ENABLED": "true", "EXAM_SCHEMA_READY": "true", "EXAM_AI_ENABLED": "true"})
        self.assertEqual(0, accepted.returncode, accepted.stderr)

    def test_runtime_directory_ownership_and_deployment_shell_syntax(self):
        dockerfile = (ROOT / "backend/Dockerfile").read_text(encoding="utf-8")
        self.assertIn("chmod 0700 /data/exam-private", dockerfile)
        self.assertIn("chown -R app:app /data/uploads /data/exam-private", dockerfile)
        self.assertIn("USER app", dockerfile)
        self.assertIn('chown "$owner" -- "$target"', self.deploy_script)
        self.assertNotIn('chown -R "$owner" -- "$target"', self.deploy_script)
        self.assertIn('[ ! -L "$target" ]', self.deploy_script)
        self.assertIn('[ -w "$EXAM_PRIVATE_ROOT" ]', self.deploy_script)
        result = subprocess.run([self.bash, "-n"], input=self.deploy_script, capture_output=True,
                                text=True, encoding="utf-8", timeout=10)
        self.assertEqual(0, result.returncode, result.stderr)


if __name__ == "__main__":
    unittest.main()
