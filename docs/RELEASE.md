# Release and signing

Package: `io.github.vexpaer.openlistener`. Version: `0.1.1`, versionCode `101`. Gradle is the single application-version source; release metadata is verified against the actual APK metadata. Public tags use `vMAJOR.MINOR.PATCH` and must match the APK.

## Signing contract

The first private PKCS#12 key is generated once outside the checkout and sent via standard input to these repository secrets:

- ANDROID_KEYSTORE_BASE64
- ANDROID_KEYSTORE_PASSWORD
- ANDROID_KEY_PASSWORD

Alias: `openlistener`. The private key/password must never appear in Git, artifacts, logs, issues or a release. The runner decodes the P12 under RUNNER_TEMP and deletes it after the job. Later releases reuse this key; do not regenerate it per run.

An encrypted P12 backup and its password must be preserved securely outside GitHub if long-term upgrades are required. Losing the certificate means that the same package cannot update installed copies normally. A test/debug APK is explicitly not a stable-signature production release.

## Hosted validation

Android CI validates the Wrapper, prepares pinned sherpa native dependencies, runs lint/JVM tests and assembles a debug test APK. Emulator tests run independently on supported API levels. Release runs lint/tests again, builds Universal and ARM64 APKs with the existing stable secrets, verifies every APK signature against the v0.1.0 certificate, checks actual package/version/minSdk/native ABIs, generates both APKs + SHA256SUMS + update.json and only then publishes the tag. `-PreleaseAbi=universal` includes arm64-v8a + x86_64; `-PreleaseAbi=arm64-v8a` includes only arm64-v8a. No 32-bit-only APK is supported.

`update.json` contains tag, versionName, versionCode, apkName, sha256, applicationId, minSdk and an artifacts array with per-APK ABI, supportedAbis, size and SHA-256. The legacy top-level APK fields point to Universal for v0.1.0 compatibility. The app validates these against the selected release and APK. AAB/source archives are not installation artifacts.

## Truthful evidence

Until the first CI run succeeds, no build success or downloadable signed APK is claimed. Emulator silence/JNI tests and provider mock tests are not physical-device/real-account validation. See IMPLEMENTATION_STATUS.md.
