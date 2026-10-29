# Contributing

Thanks for helping improve Open Listener.

## Scope

Keep pull requests small and verifiable. Product behavior must remain text-first: do not add audio files, audio caches, replay queues or hidden cloud fallback. Provider work must use official protocol documentation and anonymized fixtures when no account is available.

## Local development

The supported CI path is GitHub Actions on Ubuntu with JDK 17 and the Gradle Wrapper. A local Android SDK is optional for contributors; no one is expected to install it to review the repository. Do not commit APKs, model archives, keystores, provider keys or generated `app/libs/sherpa` inputs.

## Checks

```bash
./gradlew lintDebug testDebugUnitTest assembleDebug
```

Instrumented tests need an Android emulator. The CI workflow runs the Compose and model/service checks on a hosted emulator; model download tests require network access and deliberately fail if the verified model cannot be initialized.

## Pull requests

Describe user-visible behavior, privacy/data path, test commands and device/API coverage. State what remains unverified on physical hardware. Do not claim that a mock server or emulator microphone is a real provider or device validation.
