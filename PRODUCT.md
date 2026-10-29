# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Stack

delegated: Kotlin, Jetpack Compose, Material 3, Room, DataStore, Coroutines and GitHub Actions; the project remains a native Android app.

## Users

People attending lectures, classes, workshops and seminars who need readable bilingual text while listening.

## Product Purpose

Open Listener turns a live lecture into ordered bilingual text. The user starts one visible listening session, follows original and translated segments, then keeps the confirmed text as a searchable record.

## Positioning

Text-first privacy: microphone PCM exists only in a bounded in-memory pipeline, while confirmed text can be retained. Offline and cloud engines are selected independently and are disclosed honestly.

## Operating Context

The app is used on Android phones during long sessions, including screen-off or background operation after a visible user action. Users need to return to the latest text, inspect history, export Markdown or PDF, and share text documents.

## Capabilities and Constraints

- Three fixed top-level areas: Listen, Library and Settings; Listen is the first screen.
- Native Android only, minSdk 26, applicationId `io.github.vexpaer.openlistener`.
- The microphone is never saved as an audio file or logged. A microphone foreground service owns capture and recognition.
- Confirmed transcript segments are persisted and translated asynchronously by segment id.
- A real provider or model is shown as available only when it is implemented and configured.
- The first release records protocol-tested adapters and honest device-validation limits in `docs/IMPLEMENTATION_STATUS.md`.

## Brand Commitments

The product name is Open Listener. The experience follows the supplied Ambient Precision direction: quiet atmospheric sky layers, clear editorial text, one primary listening action, and no imitation of Hark assets or layout.

## Evidence on Hand

The source brief is `OPEN_LISTENER_ONESHOT.md`. No production screenshots, customer claims, API keys, audio fixtures, or model weights are supplied; synthetic test transcript data must be labelled as such.

## Product Principles

1. Confirmed words are more valuable than decorative activity.
2. Privacy claims describe data paths precisely.
3. Background recognition must follow Android permissions and lifecycle rules.
4. Offline and cloud choices remain explicit and independent.
5. A failure state always explains a recoverable next action.

## Accessibility & Inclusion

Use Material 3 semantics, 48dp touch targets, system font scaling, light and dark themes, sufficient contrast, reduced-motion support, and Chinese/English copy throughout the primary flows.
