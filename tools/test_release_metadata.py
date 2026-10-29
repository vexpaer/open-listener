"""Fixture tests for the two-APK release metadata contract."""
import hashlib
import json
import tempfile
import zipfile
from pathlib import Path
import importlib.util

spec = importlib.util.spec_from_file_location('release_metadata', Path(__file__).with_name('create_release_metadata.py'))
metadata = importlib.util.module_from_spec(spec)
spec.loader.exec_module(metadata)


def apk(path, abis):
    with zipfile.ZipFile(path, 'w') as archive:
        for abi in abis:
            for library in metadata.NATIVE_LIBRARIES:
                archive.writestr(f'lib/{abi}/{library}', b'native')


def output_metadata(path, apk_name, version_code=101):
    path.write_text(json.dumps({'applicationId': metadata.APPLICATION_ID, 'elements': [{
        'outputFile': apk_name, 'versionName': '0.1.1', 'versionCode': version_code, 'filters': []
    }]}), encoding='utf-8')


def main():
    with tempfile.TemporaryDirectory() as raw:
        root = Path(raw)
        universal = root / 'universal.apk'
        arm64 = root / 'arm64.apk'
        apk(universal, ['arm64-v8a', 'x86_64'])
        apk(arm64, ['arm64-v8a'])
        um = root / 'universal.json'
        am = root / 'arm64.json'
        output_metadata(um, universal.name)
        output_metadata(am, arm64.name)
        out = root / 'release'
        result = metadata.create_release(universal, um, arm64, am, 'v0.1.1', out)
        assert result['versionCode'] == 101
        assert [item['abi'] for item in result['artifacts']] == ['universal', 'arm64-v8a']
        assert result['sha256'] == hashlib.sha256((out / result['apkName']).read_bytes()).hexdigest()
        assert len((out / 'SHA256SUMS').read_text().splitlines()) == 2
        bad = root / 'bad.apk'
        apk(bad, ['arm64-v8a', 'x86_64', 'x86'])
        try:
            metadata.create_release(bad, um, arm64, am, 'v0.1.1', root / 'bad-out')
        except ValueError:
            pass
        else:
            raise AssertionError('Unexpected ABI accepted')
    print('release metadata fixtures passed')


if __name__ == '__main__':
    main()
