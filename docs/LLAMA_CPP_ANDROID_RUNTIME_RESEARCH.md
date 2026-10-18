# OrielAI native runtime research (2026-10-18)

## Verified state
- The Android debug workflow run `39142973303` completed successfully, including the APK artifact upload.
- The current `LocalModelRuntime.kt` is intentionally a stub and returns a clear failure. Therefore the APK is a UI/model-downloader scaffold, not yet a local inference app.
- The official `ggml-org/llama.cpp` repository contains an Android GUI binding under `examples/llama.android`. Its documented flow parses GGUF metadata, creates an `InferenceEngine` through `AiChat`, and streams generated tokens through Kotlin Flow.
- Upstream revision inspected for integration planning: `5aeac37a35b44fde4bdffaec9159f2fcb253b622` (2026-10-18). This is an upstream reference point, not yet vendored into OrielAI.

## Implementation choice
Prefer adapting the upstream Android binding/API rather than writing an unverified low-level JNI loop from scratch. It already includes an Android-specific CMake/JNI layer and Kotlin inference wrapper. Integrate only the minimum library/source needed, with a pinned upstream commit and explicit license notices; avoid depending on a moving `master` branch in the reproducible build.

## Integration tasks
1. Vendor or pin the upstream Android binding at the exact revision, preserving its license notices.
2. Wire its Gradle/CMake module into OrielAI and limit the first release ABI to `arm64-v8a` (target device class).
3. Replace `RuntimeNotIntegrated` with an adapter that maps OrielAI's load/generate/cancel/unload contract to the verified upstream API.
4. Confirm model chat-template handling and stop conditions specifically for the selected Qwen3.5-0.8B GGUF.
5. Add JVM tests for message formatting/error states and an Android smoke test that loads a real GGUF and generates one short Vietnamese response.
6. Build in CI; do not call inference complete until a real model file has been loaded and a response has been observed. CI compilation alone does not prove model compatibility or device performance.

## Memory and performance guardrails
- Start with a conservative context window (e.g. 2048 tokens) and modest generation length; make settings explicit rather than silently maximizing context.
- Keep inference off the UI thread, batch streaming updates, and provide cancellation.
- Do not keep multiple model instances loaded concurrently on an 8 GB phone.
- Report model file, runtime revision, load result and measured tokens/second only when those values are actually available.
- No real-device smoke test has been performed by this repository workflow; GitHub Actions builds are compile/package evidence only.

## References
- Official Android integration guide: https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md
- Official Android sample: https://github.com/ggml-org/llama.cpp/tree/master/examples/llama.android
- Upstream commit inspected: https://github.com/ggml-org/llama.cpp/commit/5aeac37a35b44fde4bdffaec9159f2fcb253b622
