# Privacy and Android permissions

- `RECORD_AUDIO`: user-granted microphone capture. Start only after visible Activity interaction.
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MICROPHONE`: the already-started session continues with an ongoing notification in normal screen-off/background scenarios.
- `POST_NOTIFICATIONS`: requested on Android 13+; denial does not imply microphone permission denial or authorization to hide the foreground service.
- `WAKE_LOCK`: partial CPU wake lock only while capture is active; released on pause, stop and failures.
- `INTERNET`: explicit model/runtime language downloads, public update check/download, or the user's selected cloud engines.
- `REQUEST_INSTALL_PACKAGES`: requests Android's unknown-source authorization before handing a verified update APK to the system installer. No silent installation.

No location, broad storage, contacts, boot receiver or accessibility-service permission is used. SAF grants a user-selected output document; FileProvider grants temporary access only to export/update cache subdirectories (including user-requested ZIP archives with retained audio).

A foreground service is not an exemption from Android/OEM policies. There is no auto-start microphone on boot, background resurrection or forced battery allowlist. Confirmed Room text survives process loss; unknown/lost audio cannot be restored.

Data disclosure is determined independently for recognition and translation: local/local = no live content upload; cloud ASR = audio to its provider; cloud translation = confirmed text to its provider; both cloud = both paths, potentially to different providers. Model downloads and release checks still use the network.
