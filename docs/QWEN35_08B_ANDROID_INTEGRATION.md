# OrielAI: Qwen3.5-0.8B local Android integration

Status: implementation specification; this file does not claim that an Android app or APK has been built.

## Product decision
- App: native Android, Kotlin + Jetpack Compose.
- Default local model: Qwen3.5-0.8B-Instruct in GGUF format, served by an in-process llama.cpp JNI layer (not Termux, not a remote server).
- Initial quantization target: Q4_0 for lower storage use; keep model selection/import open so users can replace it with a compatible GGUF build. Verify the exact architecture/template against the llama.cpp revision before shipping.
- Model weights are not bundled in the initial APK. The user downloads/imports the model separately from a clearly identified source and sees file size, license, checksum if published, and storage requirements.
- All inference runs on-device after model download. Network is used only for an optional model download or explicitly enabled online tools.

## Important distinction: what can be edited
1. **Behavior profile (editable in-app):** system instruction, tone, response length, language, initiative level, and task-planning preferences. Save as app configuration and apply to every new conversation.
2. **Runtime settings (editable with safe bounds):** context length, generation limit, temperature, top-p, CPU thread count, and keep-alive policy. Show recommended defaults and warn that larger context consumes more RAM.
3. **Model weights (not changed by editing a prompt):** persistent learned changes require a separate fine-tuning workflow (SFT/LoRA) using curated examples and suitable compute, followed by evaluation and export to a format/runtime supported by the app. Do not attempt full fine-tuning on the phone.
4. **Capabilities:** model text generation is not the same as app permissions/tools. Files, web access, device actions and other tools must be separately implemented, shown to the user, and permission-gated.

## Android components
- ModelLibrary: import a GGUF through Android Storage Access Framework; copy to app-managed storage; check extension, readable size and available disk space; show loading/download/import progress; allow delete/replace.
- LocalInferenceEngine: a Kotlin interface around JNI (loadModel, generate, cancel, unload, getRuntimeInfo). Implement with a pinned llama.cpp revision and native build for Android ABIs. Never pretend inference works if the native library is missing.
- ConversationRepository: local conversation history with clear/delete controls.
- BehaviorProfileRepository: persist editable instructions and generation settings locally.
- ChatViewModel: loading/error/cancel states; run inference off the UI thread; prevent duplicate loads; handle Activity recreation and low-memory failures.
- SettingsScreen: model status, prompt editor, sampling settings, context/RAM guidance, reset-to-default, and export/import of behavior profiles.
- Diagnostics: model file name/size, runtime version, load failures, generation duration and tokens/sec when available; do not collect or upload these by default.

## Safe default behavior profile
> You are OrielAI, a helpful local assistant. Try to satisfy benign requests directly and concretely. Do not refuse merely because a request is unusual, technical, or ambiguous; ask one concise clarification when needed. Be honest about uncertainty and about tools/capabilities that are unavailable. Break larger tasks into steps, make progress visible, and ask before consequential external actions, purchases, deletion, or financial transactions. Do not claim that an action or test succeeded unless the runtime confirms it.

This is a behavior instruction, not a guarantee that the model will follow it. A 0.8B model has limited capacity and may ignore instructions or hallucinate; evaluate it rather than promising parity with a large hosted model.

## Implementation sequence
1. Add a buildable Kotlin/Compose Android shell and CI build.
2. Add model import/library UI and persistent behavior/settings.
3. Pin llama.cpp and build/load JNI for Android arm64-v8a first; add other ABIs only after tests.
4. Validate actual Qwen3.5 chat template, stop tokens, cancellation, context handling and error propagation on device.
5. Add a Vietnamese regression set: benign requests, coding, multi-step tasks, ambiguous requests, false refusals, tool honesty and unsafe over-compliance.
6. Add a separate training folder/pipeline for SFT/LoRA data preparation and evaluation. No model-weight claim until an adapter is trained and measured.
7. Produce a signed/debug APK only after CI and a real-device smoke test; publish the exact build artifact and commit.

## Acceptance criteria
- App works offline for chat after the GGUF is imported.
- No model is silently bundled or downloaded.
- Prompt and runtime settings persist after app restart.
- Cancel stops generation; model load failures appear as actionable errors.
- The app reports whether inference is local and which model file is loaded.
- Tests compare baseline and modified behavior on the same Vietnamese evaluation set.
- No real-money action or destructive device action occurs without explicit user confirmation.

## Upstream model reference
The ggml-org/Qwen3.5-0.8B-GGUF repository provides GGUF quantizations and llama.cpp usage guidance. Before release, confirm the selected file and license in the upstream model card, pin a known-good file/revision, and test the model on the target Android device.