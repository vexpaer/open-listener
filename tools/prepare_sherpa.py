"""Prepare the pinned sherpa-onnx Android JNI and Kotlin sources in CI.

This script must be run from the repository checkout, never from an extracted
archive. It downloads only official release/source URLs, verifies the release
SHA-256, extracts regular files safely, and writes the exact Gradle inputs:
  <output>/jniLibs/{arm64-v8a,x86_64}/*.so
  <output>/kotlin/com/k2fsa/sherpa/onnx/*.kt

Example (GitHub Actions):
  python -I tools/prepare_sherpa.py --output app/libs/sherpa

It deliberately does not build Android, run Gradle, or execute downloaded code.
The official Android archive contains native libraries, not a jar; the Kotlin
API is sourced from the same upstream commit and compiled as project sources.
"""
from __future__ import annotations

import argparse
import hashlib
import os
import pathlib
import shutil
import tarfile
import tempfile
import time
import urllib.error
import urllib.request

RUNTIME_URL = (
    "https://github.com/k2-fsa/sherpa-onnx/releases/download/v2.0.1/"
    "sherpa-onnx-v2.0.1-android.tar.bz2"
)
RUNTIME_SHA256 = "39003753ec293425ed56723ea9b972b98841a490af3311670c3060548d654330"
RUNTIME_BYTES = 45_300_379
SOURCE_COMMIT = "af2d8d81dbfa61c4cea4c568da78f3982edca005"
SOURCE_BASE = (
    "https://raw.githubusercontent.com/k2-fsa/sherpa-onnx/"
    f"{SOURCE_COMMIT}/sherpa-onnx/kotlin-api/"
)
KOTLIN_FILES = (
    "OnlineRecognizer.kt", "OnlineStream.kt", "FeatureConfig.kt",
    "QnnConfig.kt", "HomophoneReplacerConfig.kt",
)
EXPECTED_SOURCE_SHA256 = {
    "OnlineRecognizer.kt": "3204ff816361d99d94dda1e8c26681f6d4a5ad749a68d8fee93b4e8bb2b21d17",
    "OnlineStream.kt": "6b9f8de317e6a0bb51f0f29d940617b781cb31b190e1c725dd438231836c06ab",
    "FeatureConfig.kt": "829c7adc4db1324f21b9a9cbf5bf10eacf759714fe50956f48317c1ffef044f6",
    "QnnConfig.kt": "86c963f27677f690eb0d6639735052eba5e60d9f81ccd4c1df0aa831fd680d6f",
    "HomophoneReplacerConfig.kt": "fe926b6413f4d87e67c41feb21b8363923a67cbd3a5eb687b76390a5f1f7db60",
}
ABIS = ("arm64-v8a", "x86_64")


def fetch(url: str, destination: pathlib.Path, expected_bytes: int | None = None) -> None:
    error = None
    for attempt in range(1, 4):
        try:
            request = urllib.request.Request(url, headers={"User-Agent": "open-listener-ci"})
            with urllib.request.urlopen(request, timeout=120) as response, destination.open("wb") as out:
                count = 0
                while True:
                    chunk = response.read(1024 * 1024)
                    if not chunk:
                        break
                    count += len(chunk)
                    out.write(chunk)
            if expected_bytes is not None and count != expected_bytes:
                raise RuntimeError(f"unexpected size for {url}: {count} != {expected_bytes}")
            return
        except (OSError, urllib.error.URLError, RuntimeError) as exc:
            error = exc
            if attempt != 3:
                time.sleep(attempt * 2)
    raise RuntimeError(f"unable to download {url}: {error}")


def sha256(path: pathlib.Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def safe_extract(archive: pathlib.Path, destination: pathlib.Path) -> None:
    root = destination.resolve()
    with tarfile.open(archive, mode="r:bz2") as tar:
        for member in tar.getmembers():
            target = (destination / member.name).resolve()
            if target != root and root not in target.parents:
                raise RuntimeError(f"unsafe archive path: {member.name}")
            if member.issym() or member.islnk() or not (member.isdir() or member.isfile()):
                raise RuntimeError(f"unsupported archive entry: {member.name}")
        tar.extractall(destination, filter="data")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", required=True, type=pathlib.Path)
    args = parser.parse_args()
    if os.environ.get("GITHUB_ACTIONS") != "true":
        raise SystemExit("Dependency preparation is restricted to GitHub Actions; no local Android setup is needed")
    output = args.output.resolve()
    if output.name != "sherpa" or output.parent.name != "libs":
        raise SystemExit("--output must name the dedicated app/libs/sherpa directory")
    output.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="open-listener-sherpa-") as raw:
        temp = pathlib.Path(raw)
        archive = temp / "sherpa-onnx-android.tar.bz2"
        fetch(RUNTIME_URL, archive, RUNTIME_BYTES)
        actual = sha256(archive)
        if actual != RUNTIME_SHA256:
            raise RuntimeError(f"runtime SHA-256 mismatch: {actual} != {RUNTIME_SHA256}")
        extracted = temp / "extracted"
        extracted.mkdir()
        safe_extract(archive, extracted)
        source_root = extracted / "jniLibs"
        if not source_root.is_dir():
            raise RuntimeError("official runtime archive has no jniLibs directory")
        staging = temp / "staging"
        native_out = staging / "jniLibs"
        kotlin_out = staging / "kotlin" / "com" / "k2fsa" / "sherpa" / "onnx"
        for abi in ABIS:
            abi_root = source_root / abi
            if not abi_root.is_dir():
                raise RuntimeError(f"official runtime archive has no {abi} directory")
            destination = native_out / abi
            destination.mkdir(parents=True)
            so_files = sorted(abi_root.glob("*.so"))
            if not so_files:
                raise RuntimeError(f"official runtime archive has no native files for {abi}")
            for source in so_files:
                shutil.copy2(source, destination / source.name)
        kotlin_out.mkdir(parents=True)
        for name in KOTLIN_FILES:
            # Each remote source goes into its own fresh directory; it is never imported.
            source_download = temp / ("download-" + name)
            source_download.mkdir()
            downloaded = source_download / name
            fetch(SOURCE_BASE + name, downloaded)
            expected = EXPECTED_SOURCE_SHA256[name]
            actual_source = sha256(downloaded)
            if actual_source != expected:
                raise RuntimeError(f"{name} source SHA-256 mismatch: {actual_source} != {expected}")
            shutil.copy2(downloaded, kotlin_out / name)
        manifest = staging / "PREPARED.txt"
        manifest.write_text(
            f"sherpa-onnx runtime v2.0.1\narchive_bytes={RUNTIME_BYTES}\n"
            f"archive_sha256={RUNTIME_SHA256}\nsource_commit={SOURCE_COMMIT}\n"
            "source_base=" + SOURCE_BASE + "\n",
            encoding="utf-8",
        )
        for child in output.iterdir():
            if child.is_dir():
                shutil.rmtree(child)
            else:
                child.unlink()
        for child in staging.iterdir():
            shutil.move(str(child), output / child.name)
    print(f"Prepared official sherpa-onnx v2.0.1 inputs under {output}")


if __name__ == "__main__":
    main()
