# OrielAI: Local AI Behavior and Autonomy Roadmap

## Goal

Improve OrielAI's local-model behavior across the Android application, GGUF/llama.cpp runtime, and model adaptation workflow. Priorities are fewer incorrect refusals, clearer instruction-following, and greater user-controlled task autonomy.

## Repository status

The initial repository inspection found only a minimal README and no discoverable Android application, runtime integration, model assets, or training pipeline. This document is a plan, not a claim that those components already exist or that an APK has been built.

## Principles

- Do not claim that prompts or runtime settings change model weights; distinguish application policy, inference configuration, and trained behavior.
- Preserve user control, privacy, and transparent task status.
- Reduce false refusals for benign requests; keep proportionate safeguards for harmful actions, privacy violations, and consequential operations.
- Require explicit confirmation for irreversible actions, transfers of value, credential changes, or destructive file operations.
- Never bundle model weights or API credentials without explicit licensing, size, and security review.
- Keep inference on-device by default when a compatible local model is installed; clearly label any network/API fallback.

## Workstreams

### A. Android application policy and orchestration

1. Inventory system prompts, refusal logic, input/output filters, permissions, and task state handling.
2. Separate policy decisions from UI and model-provider code.
3. Add configurable behavior profiles: concise/direct, exploratory, and task-oriented.
4. Add task planning, checkpoints, cancellation, bounded retries, and truthful progress reporting.
5. Gate sensitive or irreversible tool actions behind explicit user approval.

### B. GGUF / llama.cpp runtime

1. Identify the actual llama.cpp integration and supported model/template versions.
2. Validate chat template, stop tokens, context limits, sampling settings, cancellation, and error recovery.
3. Add capability checks before model load and report model/runtime incompatibilities clearly.
4. Measure peak memory, time-to-first-token, generation tokens/sec, thermal behavior, and Android process survival.
5. Do not assume changing a prompt or sampler removes behaviors learned during training.

### C. Persistent behavior adaptation

1. Build a Vietnamese evaluation set covering ordinary dialogue, coding, multi-step tasks, ambiguity, benign requests often refused, and safety-sensitive requests.
2. Record baseline quality, false-refusal rate, task completion, factuality, latency, and memory.
3. Prepare licensed, curated supervised fine-tuning examples; remove personal and secret data.
4. Experiment with LoRA/QLoRA on suitable external compute rather than attempting full fine-tuning on an 8 GB phone.
5. Compare adapter/base-model performance and regression-test before distribution.
6. Export/merge adapters only where supported by the model architecture and runtime; document provenance and license.

### D. Validation and release

- Unit tests for policy routing, tool permissions, cancellation, and retry limits.
- On-device smoke tests on representative Android versions and memory budgets.
- Regression tests for false refusals and unsafe over-compliance.
- No claim of successful build, training, or APK delivery until CI/device evidence exists.
- No real-world financial or irreversible automation without explicit user authorization.

## Suggested implementation order

1. Establish a buildable Android skeleton and CI.
2. Add a runtime/provider interface with a fake provider for tests.
3. Add policy configuration and evaluation fixtures.
4. Integrate a compatible local GGUF runtime.
5. Add bounded task orchestration and approval gates.
6. Build the training/evaluation pipeline outside the phone.
7. Publish an APK only after CI and device smoke tests pass.

## Acceptance criteria

- Benign test prompts are not refused without a specific reason.
- The app distinguishes unavailable capabilities from policy refusals.
- User can cancel a task and inspect tool actions before approval.
- Runtime reports model load failures and resource constraints accurately.
- Base model and adapted model are evaluated on the same fixed test set.
- Build/test results are linked to the exact commit; no unverified APK is described as ready.
