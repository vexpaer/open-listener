# Model catalog

This catalog contains three installable, checksum-verified local ASR models. Choose the model in Speech recognition → Local models.

## `zipformer-bilingual-zh-en-2023-02-20-mobile`

| Field | Value |
|---|---|
| Runtime | sherpa-onnx Android JNI **v2.0.1** |
| Model | Streaming Zipformer, Chinese + English, mobile INT8 encoder/joiner |
| Streaming | Yes: OnlineRecognizer / OnlineStream, endpoint enabled |
| Archive | [`sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20-mobile.tar.bz2`](https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20-mobile.tar.bz2) |
| Official release | [`asr-models`](https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models) |
| Archive bytes | `346965352` |
| Archive SHA-256 | `45b8d04fe8faf5146397ff2e71d90ca8effdfcbe9adc30fdce769e92cabd6b03` |
| Model card/license | [`Apache-2.0`](https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20/blob/main/README.md) |
| Runtime source | [`sherpa-onnx` v2.0.1](https://github.com/k2-fsa/sherpa-onnx/tree/v2.0.1/android) |
| Runtime archive bytes / SHA-256 | `45300379` / `39003753ec293425ed56723ea9b972b98841a490af3311670c3060548d654330` |
| Runtime archive metadata | [GitHub release API v2.0.1](https://api.github.com/repos/k2-fsa/sherpa-onnx/releases/tags/v2.0.1) (`digest` and `size`); [archive](https://github.com/k2-fsa/sherpa-onnx/releases/download/v2.0.1/sherpa-onnx-v2.0.1-android.tar.bz2) |

The model archive was downloaded into a new empty temporary directory and inspected without running any archive-provided code. The official `checksum.txt` records the model checksum above. The archive SHA and the required weight SHA-256 values below were independently measured during preparation work; the app verifies both archive and extracted files before activation.

Only these files are installed. `test_wavs`, `README.md`, and `notes.md` are intentionally not copied into the active model directory.

| Active relative path | Bytes | SHA-256 |
|---|---:|---|
| `encoder-epoch-99-avg-1.int8.onnx` | `105276499` | `f559f422d970323e0c266aaa3c56cf6d1a3a4bc0c0822124727686c1e43e9447` |
| `decoder-epoch-99-avg-1.onnx` | `13876452` | `2e3b5ec371f8899ee6acd829fd753ba45772df57a91bdf37cde3136354e7db7d` |
| `joiner-epoch-99-avg-1.int8.onnx` | `3228404` | `1ed689c5ed19dbaa725d9d191bb4822b5f4855a39e1ffd28cbc1f340d25b2ee0` |
| `tokens.txt` | `56317` | `a8e0e4ec53810e433789b54a5c0134a7eaa2ffca595a6334d54c00da858841d3` |

The archive also contains a non-mobile float32 encoder. It is not used by this catalog entry. The active model's expanded required weights occupy `122437672` bytes; the archive is larger because it contains the unused encoder and test WAV fixtures.

## Android runtime source and dependency contract

`tools/prepare_sherpa.py` downloads and verifies the official v2.0.1 Android JNI archive in CI, copies the `arm64-v8a` and `x86_64` shared libraries, and fetches the Kotlin API source files from the exact upstream commit `af2d8d81dbfa61c4cea4c568da78f3982edca005`. The release archive contains native libraries, **not a jar**; the Kotlin API is compiled as project source under `app/libs/sherpa/kotlin`.

The runtime archive's active ABIs contain these official shared libraries:

- `libsherpa-onnx-jni.so`
- `libsherpa-onnx-c-api.so`
- `libsherpa-onnx-cxx-api.so`
- `libonnxruntime.so`

The script does not build Android, install an SDK/NDK, execute downloaded files, or download model weights. Example CI invocation:

```bash
python -I tools/prepare_sherpa.py --output app/libs/sherpa
```

The app additionally needs Apache Commons Compress for safe `.tar.bz2` extraction and Commons IO as configured by the app module. The model store uses private app storage, HTTPS with explicit redirects, resumable `.part` files, retries, available-space checks, archive and extracted-file SHA-256 checks, bounded extraction, path/link rejection, a native initialization check, atomic staging activation, and an in-use lease that blocks deletion/replacement during recognition.

## Still requiring CI/device verification

This checkout deliberately does not install Android SDK/NDK/Gradle and does not run an Android build. CI must run `tools/prepare_sherpa.py`, compile the pinned Kotlin API/JNI inputs, and exercise `SherpaStreamingAsr` on an emulator. A physical device must still verify microphone capture, long-session power/OEM behavior, and recognition quality with real Chinese-English speech. The checksums and upstream API source are real; they are not a claim that this checkout has completed Android compilation or hardware testing.

## `zipformer-en-2023-06-26-mobile`

English-only streaming Zipformer, with INT8 encoder/joiner and FP32 decoder. Archive: [official mobile release](https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-zipformer-en-2023-06-26-mobile.tar.bz2), 305410008 bytes. Measured archive SHA-256: `4dad6f4849cad551042eb875505b3fdc0fac9f1c88d709165c48b921681e61dc`. Required installed weights total 70320726 bytes. Archive and individual file digests are pinned in `ModelCatalog.kt`; unused FP32 weights and test WAVs are excluded during installation. Upstream metadata selects the correct Zipformer runtime generation.

## Cloud speech protocols

- Doubao uses the existing live streaming ASR 2.0 protocol.
- OpenAI and custom providers use multipart WAV uploads at `/audio/transcriptions`. Model ID is editable (default OpenAI `gpt-transcribe`).
- Aliyun uses Qwen-ASR audio input at `/chat/completions`, default `qwen3-asr-flash`. Model ID and regional Base URL are editable.
- HTTP providers send ordered chunks at silence boundaries after 2 seconds or at a 12-second maximum; pause/stop flush the remaining audio. Requests use a separate worker with a bounded in-memory queue. They do not provide word-level live partials, and a hard chunk boundary can split a word. Models must support the selected provider protocol.
- API keys remain in separate encrypted `asr_*` namespaces, independent of translation credentials.

References: [Qwen-ASR API](https://www.alibabacloud.com/help/en/model-studio/qwen-asr-api-reference), [OpenAI speech-to-text](https://developers.openai.com/api/docs/guides/speech-to-text).

## Doubao simultaneous translation 2.0 (S2T)

Select **Doubao AST** under cloud speech settings. One authenticated WebSocket provides both source and translated subtitles; the separate text translator is bypassed. This adapter always sends `mode=s2t`, omits target audio / speaker configuration, and ignores audio events. Enable AST 2.0 in the provider console and save its API key under the separate `doubao_ast` credential namespace.

- Endpoint: `wss://openspeech.bytedance.com/api/v4/ast/v2/translate`
- Resource: `volc.service_type.10053`
- Transport: bare protobuf messages (not the SAUC v3 envelope). Core request/response field numbers were checked against the official Java demo linked in the [official AST documentation](https://docs.volcengine.com/docs/doubao-voice/simultaneous-interpretation2-0-api-access-documentation?lang=zh).
- Source and target languages are explicit; at least one side must be Chinese or English. The app supports its existing seven language choices.
- Sentence streams are paired using supplied start timestamps, falling back to sentence order. Source finals are retained immediately; translation deltas and finals update that same row. Unfinished translations are marked incomplete on interruption.
- Pause drains final subtitles and closes the wire session; resume opens a new wire session with continuous app IDs/audio offsets. Unexpected closure, acknowledgement timeouts and provider errors fail explicitly.
- Validation uses a local WebSocket mock, protobuf wire fixtures, real Room and Android instrumentation. No real provider account is used in CI.

## `zipformer-en-gigaspeech-2023-06-21-mobile`

English-only streaming Zipformer trained on LibriSpeech and GigaSpeech. This adds a broader English training corpus alongside the existing LibriSpeech and bilingual options. Mobile accuracy and throughput depend on the recording and device; no app-specific comparative benchmark has been run.

- [Official model documentation](https://k2-fsa.github.io/sherpa/onnx/pretrained_models/online-transducer/zipformer-transducer-models.html#csukuangfj-sherpa-onnx-streaming-zipformer-en-2023-06-21-english)
- [Model card and Apache-2.0 license](https://huggingface.co/marcoyang/icefall-libri-giga-pruned-transducer-stateless7-streaming-2023-04-04)
- Archive: `365748162` bytes; SHA-256: `b97e7ff75a27136f4dd33c99bb7d5f493094397501be4739578c6cd95ea7f422`.
- Only the INT8 encoder/joiner, float decoder and token table are installed. The larger float encoder in the archive is skipped.
- Archive and individual file hashes were measured from the official release download.

| Active relative path | Bytes | SHA-256 |
|---|---:|---|
| `joiner-epoch-99-avg-1.int8.onnx` | 259335 | `831477d390e59a61f1b6a6f763b9903e6c6366ff6034f1ddba613be82637122f` |
| `decoder-epoch-99-avg-1.onnx` | 2092566 | `9da02b77cb08826756ec6a88635f35a40374e4164e7c6359121a9145958a6ceb` |
| `tokens.txt` | 5048 | `49e3c2646595fd907228b3c6787069658f67b17377c60aeb8619c4551b2316fb` |
| `encoder-epoch-99-avg-1.int8.onnx` | 111205459 | `c0391141c0faf05142004b96dd5e66830a737386b00a5e638d0a228f051a7b60` |
