# Architecture

One native `app` module, explicit Application-owned factories (manual DI), coroutines/Flow, Room and DataStore. Compose observes ViewModel state and emits commands; it does not own AudioRecord, a WebSocket or database writes.

```text
Visible Activity → microphone foreground service
AudioRecord → bounded RAM PCM queue → selected ASR
partial → transient UI state
final → revision-aware Room text store → bounded translation workers
translated result → session / segment / revision update → UI / Library / export
```

Only confirmed words are durable. There are no audio columns, audio URIs or replay queues. Capture overload is an explicit error. The complete history is in Room; the in-memory assembler retains only a bounded UI tail.

The service prepares the selected engines, creates the session, collects ASR events, starts capture and holds a partial wake lock while active. Pause releases capture/wake lock. Stop flushes with a deadline, persists confirmed results and drains translation for a bounded interval; uncompleted rows remain retryable. Process restart marks orphaned text sessions interrupted rather than automatically starting a new microphone service.

Models live under app-private no-backup storage. Official runtime preparation occurs only in CI; archives and Kotlin sources are SHA-pinned. Installation validates native inference before a same-filesystem atomic rename and cannot delete a model while its lease is active.

Room schema version 1 is the first public schema. No destructive migration fallback is configured. Any future schema version must add a tested explicit migration and preserve text history. FTS is indexed alongside segment content; search supports mixed Chinese/English substring queries.

Credentials are separate from Room and DataStore and encrypted using Android Keystore-backed AndroidX Security. Release backup is disabled. Updates accept the exact configured public repository only, and same-package/same-certificate APKs only; the Android system owns user-confirmed installation.
