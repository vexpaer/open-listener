# Implementation status

Updated: 2026-10-29. This is a verification ledger, not a claim of device certification.

## Implemented in source, awaiting the first cloud run

- Native Kotlin / Compose / Material 3 with three top tabs, light/dark, Chinese/English, reduced motion and lifecycle-aware ambient sky.
- Microphone foreground service; bounded PCM16 RAM queue; no original audio files. A service-created Room session owns confirmed segments. Partial text is transient; translation updates are revision-aware.
- Real sherpa-onnx online Zipformer bilingual model catalog, verified archive and per-file SHA-256, resume/retry, bounded safe extraction, native initialization before atomic activation and lease-protected deletion.
- ML Kit translation language management (proprietary Google SDK), independent cloud text presets for Doubao Ark, Qwen, OpenAI and custom OpenAI-compatible endpoints.
- Doubao SAUC v3 binary WebSocket adapter with gzip/sequence/EOS, revisions, errors, bounded queues and fixture/mock tests.
- Text history, search, title changes, deletion, copying, SAF Markdown/PDF and FileProvider sharing.
- Stable-release metadata policy, explicit versionCode and SemVer, APK hash/package/signing identity verification before system installation.

## Not delivered / not claimed

- Aliyun and OpenAI realtime ASR are not implemented; the UI must not mark them ready. Their text translation presets are a separate implemented capability.
- No real cloud account has been exercised. Runtime BYOK credentials belong to the user, not CI.
- No physical Android microphone, Bluetooth/phone-call route transition, OEM screen-off endurance or 30/60-minute lecture has been validated here.
- No performance, transcription accuracy or battery benchmark is claimed.
- A CI model smoke test uses synthetic silence in RAM: it proves model loading/decoder API execution, not recognition accuracy.

## Automated evidence

Cloud build, emulator test URLs, screenshots, signing fingerprint and release artifact verification will be recorded here after they actually complete. Until then, they are pending, not passed.

## Physical-device checklist

1. Install the signed release on an ARM64 phone; grant microphone permission from the visible Listen screen.
2. Download the speech model and English/Chinese translation languages; test spoken English and Chinese.
3. Record for 30 and 60 minutes, switch tabs/apps, lock for at least five minutes and return. Confirm text and notification state agree.
4. Pause/resume/stop using the notification, revoke microphone permission, test an incoming call and Bluetooth route change. Confirm failures preserve confirmed text and show interruption rather than inventing missing audio.
5. With private BYOK credentials, validate Doubao partial/final/revisions/EOS and one cloud translation account. Inspect provider billing independently.
6. Export a long mixed Chinese/English record to SAF, open the PDF in another app and share both formats.
7. Install a newer build with the same certificate and larger versionCode; confirm history/preferences survive. A debug build cannot be overwritten by a differently signed release.

## Platform limitations

Android or an OEM may stop a service under extreme power restrictions, force-stop, revoked permission or resource pressure. Open Listener does not bypass those rules or promise universal uninterrupted operation. It restores text state, never lost audio.
