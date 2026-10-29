# Implementation status

Updated: 2026-10-29. This is a verification ledger, not a device certification claim.

## Implemented in source

- Native Kotlin / Compose / Material 3 with top Listen / Library / Settings tabs, light/dark, Chinese/English, reduced motion and lifecycle-aware ambient sky.
- Microphone foreground service with bounded PCM16 RAM queue; no original audio files. Room persists confirmed segments; partial text is transient and translation updates are revision-aware.
- Real sherpa-onnx online Zipformer bilingual model catalog, verified archive and per-file SHA-256, resume/retry, safe extraction, native validation before atomic activation and lease-protected deletion.
- ML Kit on-device translation with explicit language-model management and independent OpenAI-compatible cloud text presets.
- Doubao SAUC v3 binary WebSocket adapter with gzip/sequence/EOS, revisions, bounded queues, explicit error classes and mock/fixture tests.
- Text history, search, rename, deletion, copy, SAF Markdown/PDF and FileProvider sharing.
- Stable-release metadata policy, explicit versionCode and APK hash/package/signing identity verification before installation.

## Not delivered / not claimed

- Aliyun and OpenAI realtime ASR are not implemented and are disabled in the UI. Their text translation presets are separate capabilities.
- No real cloud account has been exercised. Runtime BYOK credentials belong to the user, not CI.
- No physical Android microphone, Bluetooth/phone-call route transition, OEM screen-off endurance or 30/60-minute lecture has been validated here.
- No performance, transcription accuracy or battery benchmark is claimed.
- CI model smoke tests use synthetic silence in RAM: they prove model loading/decoder API execution, not recognition accuracy.

## Current verification state

The public repository is now available at https://github.com/vexpaer/open-listener and the source is on `main`. The first Android CI run is in progress; its build, emulator and test conclusions are not yet recorded here. Android/Gradle builds have not been run locally, as required by the brief.

A test-only Application/DI path now exercises the microphone-type foreground service with synthetic in-memory PCM, Room persistence, notification pause/resume/stop actions and a simulated screen-off command. This validates service orchestration only; it is not physical microphone, lock-screen endurance, OEM or recognition-accuracy evidence.

Signing secrets, release tag and APK remain pending until CI passes and the stable certificate is configured.

## Evidence policy

Cloud build URLs, emulator reports, screenshots, signing fingerprint and release assets will be recorded only after they actually complete. Until then they remain pending.

## Physical-device checklist

1. Install the signed release on an ARM64 phone; grant microphone permission from the visible Listen page.
2. Download the speech model and English/Chinese translation languages; test spoken English and Chinese.
3. Record for 30 and 60 minutes, switch tabs/apps, lock for at least five minutes and return. Confirm text and notification state agree.
4. Pause/resume/stop using the notification, revoke microphone permission, test an incoming call and Bluetooth route change. Confirm failures preserve confirmed text and show interruption rather than inventing missing audio.
5. With private BYOK credentials, validate Doubao partial/final/revisions/EOS and one cloud translation account.
6. Export a long mixed Chinese/English record to SAF, open the PDF in another app and share both formats.
7. Install a newer build with the same certificate and larger versionCode; confirm history/preferences survive.

Android/OEM power restrictions, force-stop, revoked permission and resource pressure can still interrupt a foreground service. The app never bypasses those rules or promises universal uninterrupted operation.
