"""Validate ABI APK outputs and generate backwards-compatible release metadata."""
import argparse
import hashlib
import json
import pathlib
import re
import shutil
import subprocess
import zipfile

APPLICATION_ID = 'io.github.vexpaer.openlistener'
MIN_SDK = 26
RELEASE_CERTIFICATE = 'ccd5bbd505f4bdd3f3602f6aa30e88d2f8fe038738aa5ba832cebdb17722cf21'
NATIVE_LIBRARIES = (
    'libonnxruntime.so', 'libsherpa-onnx-jni.so', 'libsherpa-onnx-c-api.so',
    'libsherpa-onnx-cxx-api.so', 'libtranslate_jni.so',
)


def require(condition, message):
    if not condition:
        raise ValueError(message)


def read_metadata(path, apk, tag):
    data = json.loads(path.read_text(encoding='utf-8'))
    require(data['applicationId'] == APPLICATION_ID, 'Unexpected applicationId')
    require(len(data['elements']) == 1, 'Expected one APK output')
    element = data['elements'][0]
    require(element['outputFile'] == apk.name, 'Metadata names a different APK')
    require(element['versionName'] == tag[1:], 'APK version and tag differ')
    require(isinstance(element['versionCode'], int) and 0 < element['versionCode'] <= 2_100_000_000, 'Invalid versionCode')
    require(not element.get('filters'), 'Expected a complete APK, not a configuration split')
    return element


def verify_native_libraries(apk, abis):
    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        require(len(names) == len(set(names)), 'Duplicate APK entries')
        actual = {name.split('/')[1] for name in names if name.startswith('lib/') and name.endswith('.so')}
        require(actual == set(abis), f'Wrong native ABIs: {actual}, expected {abis}')
        for abi in abis:
            for library in NATIVE_LIBRARIES:
                name = f'lib/{abi}/{library}'
                require(name in names and archive.getinfo(name).file_size > 0, f'Missing native runtime: {name}')


def verify_android_identity(apk, element, aapt, apksigner):
    badging = subprocess.check_output([str(aapt), 'dump', 'badging', str(apk)], text=True)
    package = re.search(r"^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging, re.MULTILINE)
    minimum = re.search(r"^sdkVersion:'(\d+)'", badging, re.MULTILINE)
    require(package is not None and minimum is not None, 'Cannot read APK manifest identity')
    require(package.group(1) == APPLICATION_ID, 'APK manifest applicationId differs')
    require(int(package.group(2)) == element['versionCode'] and package.group(3) == element['versionName'], 'APK manifest version differs')
    require(int(minimum.group(1)) == MIN_SDK, 'APK manifest minSdk differs')
    signature = subprocess.check_output([str(apksigner), 'verify', '--verbose', '--print-certs', str(apk)], text=True)
    certificates = set(re.findall(r'certificate SHA-256 digest: ([0-9a-fA-F]{64})', signature))
    require({digest.lower() for digest in certificates} == {RELEASE_CERTIFICATE}, 'APK must use the existing v0.1.0 signing certificate')
    print(signature)


def create_release(universal_apk, universal_metadata, arm64_apk, arm64_metadata, tag, output, aapt=None, apksigner=None):
    require(re.fullmatch(r'v\d+\.\d+\.\d+', tag), 'Tag must be a stable vMAJOR.MINOR.PATCH tag')
    universal = read_metadata(universal_metadata, universal_apk, tag)
    arm64 = read_metadata(arm64_metadata, arm64_apk, tag)
    require(universal['versionCode'] == arm64['versionCode'], 'APK versionCode differs between variants')
    inputs = [
        ('universal', universal_apk, universal, ['arm64-v8a', 'x86_64']),
        ('arm64-v8a', arm64_apk, arm64, ['arm64-v8a']),
    ]
    require(not output.exists() or not any(output.iterdir()), 'Release output must be a new empty directory')
    require((aapt is None) == (apksigner is None), 'Both Android verification tools are required together')
    assets = []
    for abi, source, element, supported in inputs:
        require(source.is_file() and source.stat().st_size > 0, f'Missing or empty APK: {source}')
        verify_native_libraries(source, supported)
        if aapt is not None:
            verify_android_identity(source, element, aapt, apksigner)
        digest = hashlib.sha256(source.read_bytes()).hexdigest()
        assets.append(dict(abi=abi, apkName=f'OpenListener-{tag}-{abi}.apk', sha256=digest, size=source.stat().st_size, supportedAbis=supported))
    output.mkdir(parents=True, exist_ok=True)
    for asset, (_, source, _, _) in zip(assets, inputs):
        shutil.copyfile(source, output / asset['apkName'])
    fallback = assets[0]
    update = dict(tag=tag, versionName=universal['versionName'], versionCode=universal['versionCode'],
                  apkName=fallback['apkName'], sha256=fallback['sha256'], minSdk=MIN_SDK,
                  applicationId=APPLICATION_ID, artifacts=assets)
    (output / 'update.json').write_text(json.dumps(update, indent=2) + '\n', encoding='utf-8')
    (output / 'SHA256SUMS').write_text(''.join(f"{asset['sha256']}  {asset['apkName']}\n" for asset in assets), encoding='utf-8')
    print(f"Verified version {update['versionName']} ({update['versionCode']})")
    for asset in assets:
        print(f"{asset['apkName']} {asset['size']} bytes {asset['sha256']}")
    return update


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--universal-apk', required=True, type=pathlib.Path)
    parser.add_argument('--universal-metadata', required=True, type=pathlib.Path)
    parser.add_argument('--arm64-apk', required=True, type=pathlib.Path)
    parser.add_argument('--arm64-metadata', required=True, type=pathlib.Path)
    parser.add_argument('--tag', required=True)
    parser.add_argument('--output', required=True, type=pathlib.Path)
    parser.add_argument('--aapt', required=True, type=pathlib.Path)
    parser.add_argument('--apksigner', required=True, type=pathlib.Path)
    args = parser.parse_args()
    create_release(**vars(args))


if __name__ == '__main__':
    main()
