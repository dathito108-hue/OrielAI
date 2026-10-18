# OrielAI

OrielAI is being developed as a native Android app using Qwen3.5-0.8B locally through GGUF and llama.cpp.

## Current status

The repository now contains an initial Kotlin/Jetpack Compose Android scaffold, persistent behavior-profile settings, a GGUF file picker, a runtime interface contract, a starter Vietnamese evaluation set, and an Android debug-APK CI workflow.

**Important:** real llama.cpp JNI inference, tool execution, task orchestration, LoRA loading and fine-tuning are not implemented yet. The app explicitly reports that runtime status instead of fabricating AI responses. A successful APK build is not proof that local inference works.

## Build

The GitHub Actions workflow builds app-debug.apk and publishes it as the OrielAI-debug-apk artifact when the workflow succeeds. The project uses Android SDK 35 and Java 17.

## Model

Use a compatible Qwen3.5-0.8B GGUF from the upstream [ggml-org model repository](https://huggingface.co/ggml-org/Qwen3.5-0.8B-GGUF). Review the model card and license before redistribution. The model weights are not bundled in the APK.

## Design documents

- [Behavior reform plan](docs/AI_BEHAVIOR_REFORM_PLAN.md)
- [Qwen3.5-0.8B Android integration](docs/QWEN35_08B_ANDROID_INTEGRATION.md)
- [Behavior and agent policy](docs/BEHAVIOR_AND_AGENT_POLICY.md)
- [Vietnamese behavior evaluation set](training/eval_vi.jsonl)

## Non-negotiable implementation rules

- Distinguish application behavior settings from model-weight changes.
- Do not claim a model was trained or an adapter loaded without evidence.
- Do not execute consequential actions without explicit confirmation.
- Measure behavior changes on a fixed evaluation set.
