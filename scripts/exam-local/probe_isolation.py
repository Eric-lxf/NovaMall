"""Inspect the production worker sandbox limits without host mounts or network."""
import json
import subprocess
import uuid

name = "novamall-exam-probe-" + uuid.uuid4().hex
args = ["docker", "run", "--rm", "--pull=never", "--name", name, "--network=none", "--read-only",
        "--cap-drop=ALL", "--security-opt=no-new-privileges", "--pids-limit=64", "--memory=512m",
        "--memory-swap=512m", "--cpus=1", "--tmpfs=/tmp:rw,noexec,nosuid,size=192m,mode=1777",
        "--user=65534:65534", "-i", "--entrypoint=python", "novamall/exam-document:20260903", "-"]
probe = '''import json, os
from pathlib import Path
status = Path('/proc/self/status').read_text()
assert os.getuid() == 65534
assert 'CapEff:\\t0000000000000000' in status
assert 'NoNewPrivs:\\t1' in status
assert sorted(p.name for p in Path('/sys/class/net').iterdir()) == ['lo']
try:
    Path('/sandbox-write-probe').write_text('denied')
except OSError:
    pass
else:
    raise AssertionError('Root is writable')
Path('/tmp/sandbox-write-probe').write_text('allowed')
limits = {k: Path('/sys/fs/cgroup/' + k).read_text().strip() for k in ['memory.max', 'memory.swap.max', 'pids.max', 'cpu.max']}
assert limits == {'memory.max':'536870912','memory.swap.max':'0','pids.max':'64','cpu.max':'100000 100000'}, limits
tmp = next(line for line in Path('/proc/mounts').read_text().splitlines() if line.split()[1] == '/tmp')
assert 'noexec' in tmp and 'nosuid' in tmp
print(json.dumps({'uid':os.getuid(), 'capabilities':'none', 'noNewPrivileges':True,'network':'loopback only','root':'read-only','tmp':'writable/noexec/nosuid','limits':limits}))
'''
try:
    result = subprocess.run(args, input=probe.encode(), capture_output=True, timeout=20)
    if result.returncode:
        raise RuntimeError(result.stderr.decode(errors="replace"))
    print(result.stdout.decode())
finally:
    subprocess.run(["docker", "rm", "-f", name], capture_output=True, timeout=10)
