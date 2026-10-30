# Changelog

## 0.1.6 · 2026-10-30

- Compact settings rows and local model cards; side-by-side update actions.
- A single share/export menu and continuous transcript reading surface.
- Redesigned API guides with numbered steps and linked cards.
- 90 ms character fade-in, tactile gradient recording controls and real microphone-driven tab equalizer at 30% opacity.
- Screenshot artifacts and UI checks for compact layouts, export menu and recording visualization.

## 0.1.5 · 2026-10-30

- Swipe navigation for Listen / Library / Settings; clearer English home copy.
- Dedicated speech and translation settings, with Local / Cloud switches inside each page.
- Verified English streaming Zipformer model with selectable local model preference.
- Qwen-ASR, OpenAI transcription and custom compatible cloud speech APIs with editable model IDs.
- Optional private WAV recording retention (off by default), 🎧 Library markers, in-app playback and audio deletion.
- Room v1 → v2 migration preserving existing transcripts; updated privacy documentation.
- Remove Library empty-state subtitle and rename source link to Github.

## 0.1.4 — 2026-10-30

- Replace the ambient sky with icy blue, soft drifting light and fine grain, with a matching dark palette.
- Add emoji help beside API configuration and inside credential dialogs, opening concise provider-specific guides with clickable official links.
- Keep unsaved API fields intact when opening and closing help.
- Correct merged-semantics lookup in the Library preview instrumentation check.

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
