# Security Policy

## Supported versions

The latest `main` branch and latest stable release receive security fixes while the project is experimental.

## Reporting

Please do not publish credentials, captured audio, private transcripts or a working exploit in an issue. Use GitHub's private vulnerability reporting for this repository when available, or open a minimal issue asking for a private contact route. Include reproduction steps, affected version, Android API level and whether the issue requires a configured provider.

## Security boundaries

- Runtime provider credentials use Android Keystore-backed encrypted preferences.
- Audio is bounded in memory and is not persisted.
- Model archives require HTTPS, exact SHA-256 and safe extraction; unsafe archive paths are rejected.
- Release updates require trusted repository metadata, APK SHA-256, package/version/minSdk checks and the current signing certificate before installation.
- Logs and test fixtures must not contain API keys, PCM or private transcript text.
