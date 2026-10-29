## Open Listener 0.1.1

### Changes

- New launcher logo using the supplied Open Listener artwork.
- Two signed APKs: a smaller ARM64 build for phones, and Universal for ARM64 + x86_64.
- Settings automatically selects the matching update asset and shows its architecture and size before download. Download and system installation still require user action.
- Per-APK SHA-256/size verification; legacy v0.1.0 clients can still upgrade through the Universal asset.

### Install

Choose `OpenListener-v0.1.1-arm64-v8a.apk` for an ARM64 phone, or `OpenListener-v0.1.1-universal.apk` for ARM64/x86_64. Compare `SHA256SUMS`, then use the Android system installer. Android 8/API 26+ required. Both packages use the v0.1.0 certificate and versionCode 101, allowing an in-place update without deleting text history. No separate x86 APK is published. 32-bit-only ARM/x86 devices are not supported.

An installed v0.1.0 updater only understands the legacy Universal entry; ABI-specific auto-selection becomes available after installing v0.1.1. Models are downloaded separately in Settings. Provider keys are never bundled.

### Boundaries

Cloud providers require your own credentials; real accounts and physical-device microphone endurance have not been validated here. Model initialization and synthetic PCM tests do not certify recognition accuracy. Aliyun/OpenAI realtime ASR are not delivered; their text translation presets are independent.

The app never stores original microphone audio. Cloud recognition uploads live PCM to the chosen provider; cloud translation uploads confirmed text. ML Kit is proprietary; application source is Apache-2.0.
