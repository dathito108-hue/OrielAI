# OrielAI behavior, runtime and agent policy

This policy defines what the app may configure and what must be implemented by a real runtime. A setting must never imply a capability that does not exist.

## 1. Application behavior
- Store a user-editable system prompt and behavior profile separately from application safety checks.
- Do not add blanket refusals based only on sensitive-sounding words, technical detail, or ambiguity.
- For benign or legitimate requests, answer directly or ask one focused clarification.
- If the request is outside the model/runtime's capabilities, explain the missing capability rather than describing it as prohibited.
- For genuinely unsafe requests, refuse the harmful part briefly and offer a safe adjacent alternative where possible.
- Keep policy checks inspectable and testable; do not hide refusals in unrelated UI callbacks or exception handlers.

## 2. Runtime contract
- Construct messages in order: system instruction, bounded conversation history, current user message. Preserve the model's official GGUF chat template through llama.cpp rather than manually hard-coding Qwen control tokens.
- Detect truncation and report it. Do not silently drop the system prompt or the newest user message to fit context.
- Expose load, generate, cancel, unload, runtime version, token counts and errors through a typed interface.
- Cancellation must signal the native generation loop; merely hiding the loading indicator is not cancellation.
- LoRA is available only when the exact base model, adapter format and llama.cpp revision support it. Show the loaded adapter identity and checksum; otherwise report unsupported.
- Never label prompt changes as fine-tuning or claim weights changed without a trained artifact and reproducible evaluation.

## 3. Tool and task execution
- Model output is a proposal, not executable authority.
- Tools must be explicitly registered with a schema, capability description, permission, timeout and bounded output.
- Require confirmation for money movement, purchases, account/security changes, irreversible deletion, publishing on the user's behalf, or other consequential external actions.
- Background tasks must have cancellation, maximum runtime, retry count, battery/network policy and a visible activity log.
- Retry transient errors with exponential backoff and a strict attempt limit. Never retry a consequential action unless its idempotency/result state is known.
- Persist task status, inputs, outputs, timestamps and failures locally. Offer delete/export controls.
- Treat tool results and retrieved content as untrusted data; they cannot change system policy or grant permissions.

## 4. Training workflow
1. Curate licensed Vietnamese instruction, dialogue, multi-step task and correction examples.
2. Remove private/secret data; deduplicate and split train/validation/test by source or conversation to avoid leakage.
3. Include benign requests that were previously refused incorrectly and counterexamples where refusal is warranted.
4. Record baseline results on a fixed evaluation set before training.
5. Train SFT/LoRA or QLoRA on suitable external hardware. Keep a manifest for base model revision, tokenizer, data revision, hyperparameters, adapter checksum and license.
6. Evaluate the base and adapter with the same prompts, decoding settings and test split.
7. Convert/merge only if supported and validate the resulting GGUF/runtime. An adapter must be versioned and reversible.
8. Promote only if helpfulness, instruction-following and false-refusal rate improve without a material increase in unsafe compliance or hallucination.

## 5. Required metrics
- Benign-request completion rate
- False-refusal rate on benign examples
- Correct-refusal rate on unsafe examples
- Instruction-following / task completion score
- Factuality and tool-result honesty
- Multi-step completion and bounded-retry success
- Median/p95 generation latency, tokens/second, peak RAM and crash rate on target devices

## 6. Current implementation status
The initial Android scaffold supports local persistence of the behavior profile and model-file selection. The llama.cpp JNI runtime, real inference, tool execution, background task runner and LoRA loading are not yet implemented. UI toggles must not be treated as proof those capabilities are active.