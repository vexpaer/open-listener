## Open Listener 0.1.0

Native Android live bilingual notes with text-only history, explicit local/cloud data paths, verified offline Zipformer installation, ML Kit on-device translation, Doubao streaming protocol and BYOK cloud text translation.

### Install

Download `OpenListener-v0.1.0-universal.apk` (ARM64 + x86_64), compare SHA256SUMS, then use the Android system installer. Android 8/API 26+ required. Models are downloaded separately in Settings. Runtime provider keys are never bundled.

### Boundaries

Cloud provider adapters require the user's own active credentials. Mock protocol tests are not real-account validation. Model initialization and synthetic PCM tests do not certify recognition accuracy. Physical-device microphone continuity, phone/Bluetooth routing and manufacturer screen-off policies still require the documented manual checklist. Aliyun/OpenAI realtime ASR are not delivered in this release; their text translation presets are independent.

The app never stores original microphone audio. Selecting cloud recognition uploads PCM to that provider; selecting cloud translation uploads confirmed text. ML Kit is proprietary, while application source is Apache-2.0.
