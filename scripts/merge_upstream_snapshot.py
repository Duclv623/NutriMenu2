"""Prepare a checked three-way merge from a downloaded upstream snapshot.

The read-only baseline is the team's original checkout. Conflicts are staged
under .build-cache for review; no conflicted content is applied to the app.
"""
from pathlib import Path
import hashlib
import json
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]
WORK = ROOT / '.build-cache/upstream-760f0663'
BASE = Path('C:/testda/NutriMenu2')
if (WORK/'applied.json').exists():
    raise SystemExit('Snapshot already integrated; do not overwrite its before snapshots.')

def blob(data): return hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()
def normalized(path): return path.read_text('utf-8-sig').replace('\r\n','\n')

manifest = json.loads((WORK/'manifest.json').read_text('utf-8'))
baseline = json.loads(Path('C:/testda/migration-20261126/upstream-tree.json').read_text('utf-8'))
base_hashes = {row['path']:row['sha'] for row in baseline['tree'] if row['type']=='blob'}
results = []
for row in manifest['files']:
    name = row['filename']; previous = row.get('previous_filename') or name
    remote = WORK/'remote'/name; local = ROOT/previous; base = BASE/previous
    assert blob(remote.read_bytes()) == row['sha'], 'Upstream hash mismatch: '+name
    if base.exists(): assert blob(base.read_bytes()) == base_hashes[previous], 'Baseline changed: '+previous
    if local.exists():
        backup = WORK/'before'/previous; backup.parent.mkdir(parents=True,exist_ok=True); shutil.copy2(local,backup)
    destination = WORK/'merged'/name; destination.parent.mkdir(parents=True,exist_ok=True)
    if not base.exists():
        if local.exists() and normalized(local)!=normalized(remote):
            status='added-conflict'; shutil.copy2(local,destination)
        else: status='added'; shutil.copy2(remote,destination)
    elif normalized(local)==normalized(base):
        status='fast-forward'; shutil.copy2(remote,destination)
    else:
        inputs = []
        for label,path in [('local',local),('base',base),('upstream',remote)]:
            temp = WORK/'normalized'/label/previous; temp.parent.mkdir(parents=True,exist_ok=True)
            temp.write_text(normalized(path),encoding='utf-8'); inputs.append(temp)
        merge = subprocess.run(['git','merge-file','-p','-L','local','-L','base','-L','upstream',*[str(p) for p in inputs]],capture_output=True)
        if merge.returncode < 0 or merge.returncode > 127: raise RuntimeError(merge.stderr.decode('utf-8','replace'))
        destination.write_bytes(merge.stdout)
        status='merged' if merge.returncode==0 else 'conflict'
    results.append({'path':name,'previous':previous,'status':status})
(WORK/'merge-plan.json').write_text(json.dumps(results,indent=2),encoding='utf-8')
print(json.dumps(results,ensure_ascii=False,indent=2))
