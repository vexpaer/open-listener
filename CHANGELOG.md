# Changelog

## 0.1.3 — 2026-10-30

- Use matching icon-only pause/resume and transparent stop controls.
- Remove listening status, timestamps and redundant introductory copy; use a 20% opacity live transcript surface.
- Show titles with a single-line transcript preview in Library and simplify Search.
- Group speech and translation settings into Local models / Cloud models detail pages.
- Reduce settings copy and fix inherited black text on translucent dark-theme surfaces.

## 0.1.2 — 2026-10-30

- Remove the top wordmark/status row and move navigation upward.
- Use capsule navigation and a sliding capsule indicator without pressed shading.
- Move spoken/translation language choices to the first Target language section.
- Move theme, app language and subtitle size into Appearance; remove system-choice and reduced-motion controls.
- Remove the Privacy & storage card and redundant listening labels.

## 0.1.1 — 2026-10-30

- Replace the launcher and documentation logo with the supplied Open Listener artwork.
- Publish Universal (ARM64 + x86_64) and smaller ARM64 APKs using the same stable signing certificate.
- Select the compatible update APK automatically, with per-artifact SHA-256/size checks and a Universal fallback for v0.1.0 clients.
- Display the selected APK architecture and download size in Settings.

## 0.1.0 — 2026-10-29

- Native Android Compose shell with top Listen / Library / Settings tabs.
- Text-only Room history, search, copy, rename, delete, Markdown/PDF export and sharing paths.
- Microphone foreground-service state machine with pause/resume/stop notification actions.
- Verified sherpa-onnx bilingual streaming model catalog and CI-only runtime preparation.
- Doubao SAUC v3 binary protocol parser, bounded WebSocket sending and error classification.
- ML Kit on-device translation and OpenAI-compatible cloud text translation adapters.
- GitHub Release update metadata, checksum and signing identity validation.
- CI, emulator tests and honest physical-device/provider validation notes.
