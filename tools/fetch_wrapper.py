"""Fetch only official wrapper files through GitHub's contents API; no local build."""
import base64
import hashlib
import json
import pathlib
import subprocess
import tempfile

root = pathlib.Path(__file__).resolve().parent.parent
with tempfile.TemporaryDirectory(prefix='open-listener-wrapper-') as temporary:
    staging = pathlib.Path(temporary)
    for source, target in [
        ('gradle/wrapper/gradle-wrapper.jar', 'gradle/wrapper/gradle-wrapper.jar'),
        ('gradlew', 'gradlew'),
        ('gradlew.bat', 'gradlew.bat'),
    ]:
        for attempt in range(5):
            try:
                response = subprocess.check_output([
                    'gh', 'api', f'repos/gradle/gradle/contents/{source}?ref=v8.13.0'
                ])
                break
            except subprocess.CalledProcessError:
                if attempt == 4:
                    raise
        metadata = json.loads(response)
        data = base64.b64decode(metadata['content'])
        git_sha = hashlib.sha1(f'blob {len(data)}\0'.encode() + data).hexdigest()
        if git_sha != metadata['sha']:
            raise SystemExit(f'Git object integrity mismatch for {source}')
        staged = staging / pathlib.Path(target).name
        staged.write_bytes(data)
        destination = root / target
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(staged.read_bytes())
        print(f'{target} SHA256 {hashlib.sha256(data).hexdigest()}')
