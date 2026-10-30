<div align="center">

<img src="docs/images/mark.svg" width="76" alt="Open Listener logo" />

# Open Listener

**Listen. Understand. Keep the words.**

Native Android · live bilingual text · optional audio retention

[中文说明](README.zh-CN.md) · [Build](#build) · [Privacy](PRIVACY.md) · [License](LICENSE)

**[Download v0.1.10](https://github.com/vexpaer/open-listener/releases/tag/v0.1.10) · ARM64 and Universal APKs**

</div>

Open Listener is a text-first Android companion for lectures, classes and seminars. Tap once while the app is visible, follow original and translated segments, and keep the confirmed text as a searchable record. Audio retention is off by default; enable Keep recordings to save audio alongside the transcript.

> **Hosted build and API 34/35 emulator tests passed for v0.1.0.** Physical-device microphone endurance and real cloud accounts remain unverified. The implementation and outstanding checks are tracked in [`docs/IMPLEMENTATION_STATUS.md`](docs/IMPLEMENTATION_STATUS.md).

## What it does

- **Make it yours:** choose a preset accent or tap the rainbow circle for a color wheel and brightness control (the original blue by default), enjoy a coordinated OKLCH background and animated glow, and switch between light and dark appearances. Your choice stays on your device.
- **Refined navigation:** translucent glass panels, accent-colored live audio bars and navigation selection, comfortable swipe spacing, and local/cloud model panels with square joining edges and a sliding selection. Settings panels use 30% opacity. Ambient movement uses a 40-second cycle.


- **Listen:** swipe between three top tabs, bilingual live text, partial/final distinction, pause/resume/stop notification actions.
- **Archive everything:** export all records and retained audio to one ZIP from the Library’s bottom-right button.
- **Keep:** confirmed segments persist in Room; search, copy, rename, delete, single-record Markdown/PDF exports contain text; ZIP archives also include retained audio.
- **Choose the data path:** local sherpa-onnx streaming ASR (including an English GigaSpeech option) + on-device ML Kit translation, or BYOK cloud providers with disclosure.
- **Update safely:** latest stable release metadata, SHA-256, package identity and signing certificate are checked before the Android installer is opened.

The sample transcript in UI tests is labelled illustrative and is not a product claim.

## Privacy in one minute

- Audio flows through a bounded PCM pipeline. Optional private WAV recordings are marked 🎧 in Library and can be played inside the app.
- Local ASR and ML Kit translation keep the selected data path on device after their models are installed. ML Kit is a proprietary third-party SDK, not an open-source model.
- Doubao ASR sends live PCM to Volcengine; Qwen-ASR, OpenAI and custom speech APIs receive short audio chunks. Cloud text providers receive confirmed text. Credentials are stored in Android encrypted preferences and never exported.
- Android permissions and foreground-service rules remain in force; OEM battery policies can still interrupt long sessions.

Read the full policy in [`PRIVACY.md`](PRIVACY.md) and provider details in [`docs/IMPLEMENTATION_STATUS.md`](docs/IMPLEMENTATION_STATUS.md).

## Build

The project is native Kotlin + Jetpack Compose + Material 3, `minSdk 26`, `targetSdk 36`, and Java 17. This repository intentionally does not require an Android SDK on the user's machine. GitHub Actions downloads the pinned Android toolchain and verifies the sherpa-onnx runtime archive before compiling.

```bash
./gradlew lintDebug testDebugUnitTest assembleDebug
```

CI runs compilation, Lint, JVM unit tests and APK assembly; emulator tests are disabled.

The command is for a machine that already has the Android toolchain; the hosted CI workflow is the supported reproducible path for this project. See [`CONTRIBUTING.md`](CONTRIBUTING.md).

## Repository map

- `app/src/main/.../service` — microphone foreground service and runtime state
- `app/src/main/.../asr` — engine contracts, sherpa streaming runtime and Doubao protocol
- `app/src/main/.../data` — Room transcript history and optional audio metadata and DataStore preferences
- `app/src/main/.../translation` — ML Kit and OpenAI-compatible text adapters
- `app/src/test` — protocol, ordering, export, update-policy and downloader tests
- `app/src/androidTest` — Compose flow, Room, model/native, ML Kit, export and update checks
- `tools/prepare_sherpa.py` — CI-only official archive/source verification

## Development status

The current version is `0.1.10`. Release downloads include a Universal APK and a smaller ARM64 APK; the in-app updater selects the compatible asset automatically. A provider is shown as ready only after its model or credentials pass the local availability check. Real provider credentials are intentionally not included in CI; protocol tests use anonymized fixtures and mock servers. See [`CHANGELOG.md`](CHANGELOG.md) and [`docs/IMPLEMENTATION_STATUS.md`](docs/IMPLEMENTATION_STATUS.md).

## License

Application source is Apache-2.0. Upstream sherpa-onnx runtime/source and model files retain their own licenses; the pinned sources and model checksums are documented in [`docs/MODEL_CATALOG.md`](docs/MODEL_CATALOG.md). See [`LICENSE`](LICENSE).
