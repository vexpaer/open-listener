# Privacy

Open Listener is text-first. Audio retention is off by default. Turning on Keep recordings saves new sessions as private WAV files on this device; their Library entries display 🎧 and offer playback. Deleting a record also deletes its audio.

## Microphone data

After the user taps Start on the visible Listen page and grants `RECORD_AUDIO`, a microphone foreground service reads mono PCM16 frames into a small bounded memory queue. Frames are passed to the selected ASR engine. When Keep recordings is enabled, captured frames are also written to a private WAV file. Queue overflow stops the session with an error rather than writing audio to disk.

- **Offline sherpa-onnx:** frames stay on the device.
- **OpenAI, Qwen-ASR and custom speech APIs:** short audio chunks are uploaded to the selected HTTPS endpoint for transcription.
- **Doubao realtime ASR:** frames are sent to the configured Volcengine endpoint. The provider's terms, retention and billing policies apply.

The app does not log PCM, transcript bodies, API keys or cloud response bodies.

## Text records

Confirmed original and translated segments are stored in a local Room database until the user deletes them. Markdown/PDF export and Android sharing are initiated by the user. Shared documents are temporary text artifacts under the app's private cache and are cleaned after a TTL; a receiving app may retain a copy.

## Translation

ML Kit on-device translation downloads language models on demand. ML Kit is a proprietary Google SDK; its own terms and data practices apply. Cloud translation adapters send confirmed text to the provider selected by the user. No silent cloud fallback is used.

## Credentials

Provider credentials are stored with AndroidX Security encrypted preferences backed by Android Keystore. They are not stored in Room, exported records, logs, Git, CI artifacts or GitHub Actions secrets. CI signing secrets are separate from runtime provider credentials.

## Permissions and background use

The app requests microphone and, where applicable, notification permission. The microphone foreground service is started only from a visible user action and declares the microphone service type. Screen-off behavior follows Android and OEM policy; users should not assume that every manufacturer permits uninterrupted multi-hour recording.

## Deletion and limitations

Deleting a record removes its local text segments and retained audio. It cannot recall text or PCM already sent to a cloud provider, nor copies made by a sharing target. This document is product documentation, not a legal privacy notice for any third-party provider.
