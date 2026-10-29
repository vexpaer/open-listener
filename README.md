<div align="center">

<img src="docs/images/mark.svg" width="76" alt="Open Listener original mark" />

# Open Listener

**Listen. Understand. Keep the words.**

Native Android · live bilingual text · no audio files

[中文说明](README.zh-CN.md) · [Build](#build) · [Privacy](PRIVACY.md) · [License](LICENSE)

**APK: not yet published. Cloud build and emulator verification are in progress.**

</div>

Open Listener is a text-first Android companion for lectures, classes and seminars. Tap once while the app is visible, follow original and translated segments, and keep the confirmed text as a searchable record. The app never writes microphone PCM to an audio file.

> **Status: source implementation, not yet a verified release.** [Hosted CI](https://github.com/vexpaer/open-listener/actions/workflows/android-ci.yml) is running and build issues are being corrected. No successful CI or release APK is claimed until verification completes. The implementation and outstanding checks are tracked in [`docs/IMPLEMENTATION_STATUS.md`](docs/IMPLEMENTATION_STATUS.md).

## What it does

- **Listen:** three top tabs, bilingual live text, partial/final distinction, pause/resume/stop notification actions.
- **Keep:** confirmed segments persist in Room; search, copy, rename, delete, Markdown/PDF export and Android sharing use text only.
- **Choose the data path:** local sherpa-onnx streaming ASR + on-device ML Kit translation, or BYOK cloud providers with disclosure.
- **Update safely:** latest stable release metadata, SHA-256, package identity and signing certificate are checked before the Android installer is opened.

The sample transcript in UI tests is labelled illustrative and is not a product claim.

## Privacy in one minute

- Audio is a bounded in-memory PCM pipeline. No audio URI, recording file or replay queue is stored.
- Local ASR and ML Kit translation keep the selected data path on device after their models are installed. ML Kit is a proprietary third-party SDK, not an open-source model.
- Doubao ASR sends live PCM to Volcengine. Cloud text providers receive confirmed text. Credentials are stored in Android encrypted preferences and never exported.
- Android permissions and foreground-service rules remain in force; OEM battery policies can still interrupt long sessions.

Read the full policy in [`PRIVACY.md`](PRIVACY.md) and provider details in [`docs/IMPLEMENTATION_STATUS.md`](docs/IMPLEMENTATION_STATUS.md).

## Build

The project is native Kotlin + Jetpack Compose + Material 3, `minSdk 26`, `targetSdk 36`, and Java 17. This repository intentionally does not require an Android SDK on the user's machine. GitHub Actions downloads the pinned Android toolchain and verifies the sherpa-onnx runtime archive before compiling.

```bash
./gradlew lintDebug testDebugUnitTest assembleDebug
```

The command is for a machine that already has the Android toolchain; the hosted CI workflow is the supported reproducible path for this project. See [`CONTRIBUTING.md`](CONTRIBUTING.md).

## Repository map

- `app/src/main/.../service` — microphone foreground service and runtime state
- `app/src/main/.../asr` — engine contracts, sherpa streaming runtime and Doubao protocol
- `app/src/main/.../data` — Room text-only history and DataStore preferences
- `app/src/main/.../translation` — ML Kit and OpenAI-compatible text adapters
- `app/src/test` — protocol, ordering, export, update-policy and downloader tests
- `app/src/androidTest` — Compose flow, Room, model/native, ML Kit, export and update checks (not yet executed)
- `tools/prepare_sherpa.py` — CI-only official archive/source verification

## Development status

The current version is `0.1.0`. A provider is shown as ready only after its model or credentials pass the local availability check. Real provider credentials are intentionally not included in CI; protocol tests use anonymized fixtures and mock servers. See [`CHANGELOG.md`](CHANGELOG.md) and [`docs/IMPLEMENTATION_STATUS.md`](docs/IMPLEMENTATION_STATUS.md).

## License

Application source is Apache-2.0. Upstream sherpa-onnx runtime/source and model files retain their own licenses; the pinned sources and model checksums are documented in [`docs/MODEL_CATALOG.md`](docs/MODEL_CATALOG.md). See [`LICENSE`](LICENSE).
