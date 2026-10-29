"""Read Gradle's APK output metadata; never infer Android versionCode from a tag."""
import argparse
import hashlib
import json
import pathlib
import re
import shutil

parser = argparse.ArgumentParser()
parser.add_argument('--apk-dir', required=True, type=pathlib.Path)
parser.add_argument('--tag', required=True)
parser.add_argument('--output', required=True, type=pathlib.Path)
args = parser.parse_args()
if not re.fullmatch(r'v\d+\.\d+\.\d+', args.tag):
    raise SystemExit('Ref must be a stable vMAJOR.MINOR.PATCH tag; dispatch main cannot publish')
metadata = json.loads((args.apk_dir / 'output-metadata.json').read_text(encoding='utf-8'))
assert metadata['applicationId'] == 'io.github.vexpaer.openlistener'
assert len(metadata['elements']) == 1, 'A single universal signed APK is expected'
element = metadata['elements'][0]
assert element['versionName'] == args.tag[1:], 'APK version and tag differ'
assert element['versionCode'] > 0
apk = args.apk_dir / element['outputFile']
assert apk.is_file() and apk.stat().st_size > 0
sha = hashlib.sha256(apk.read_bytes()).hexdigest()
name = f'OpenListener-{args.tag}-universal.apk'
args.output.mkdir(parents=True, exist_ok=True)
shutil.copyfile(apk, args.output / name)
update = dict(tag=args.tag, versionName=element['versionName'], versionCode=element['versionCode'],
              apkName=name, sha256=sha, minSdk=26, applicationId=metadata['applicationId'])
(args.output / 'update.json').write_text(json.dumps(update, indent=2) + '\n', encoding='utf-8')
(args.output / 'SHA256SUMS').write_text(f'{sha}  {name}\n', encoding='utf-8')
print(f'Verified version {element["versionName"]} ({element["versionCode"]}), SHA256 {sha}')
