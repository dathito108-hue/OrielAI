# OrielAI: Open-Ended Autonomy and Self-Improvement

## Product intent

OrielAI should be allowed to take initiative rather than behaving as a passive chatbot. Given a broad goal, it should be able to clarify the desired outcome only when necessary, propose a plan, break work into tasks, use authorized tools, inspect results, detect failures, revise its approach, and continue until it reaches a measurable stopping condition.

This is an engineering direction, not a claim that the current app already has these capabilities. The current Android scaffold does not yet have integrated GGUF inference, a working tool executor, a persistent long-running agent, or model self-training.

## What “freedom” means

1. **Goal autonomy:** infer useful next steps from the user's stated goal and make low-risk decisions without asking about every detail.
2. **Planning autonomy:** create and revise a task graph; track dependencies, progress, blockers, evidence, and completion criteria.
3. **Tool autonomy:** choose from explicitly registered tools, inspect their declared capabilities, and call them within scoped permissions.
4. **Learning from outcomes:** record failures and successful approaches, build evaluation cases, and recommend prompt/configuration/dataset improvements.
5. **Iterative engineering:** in a designated workspace, inspect project files, propose or implement changes, run tests, analyze failures, and iterate within time, cost, and attempt budgets.
6. **Long-running work:** checkpoint state, recover after process death, report progress, and resume only when the task remains authorized.
7. **User-configurable behavior:** expose initiative, clarification threshold, retry budget, network/tool access, and background-work settings in the app.

## What must not be falsely implied

- A system prompt or UI toggle does not make a model more intelligent by itself.
- Saving conversation history is not training the model.
- Writing a new prompt, memory, or evaluation example is not the same as updating model weights.
- The app must not claim it has run tools, browsed, tested code, trained an adapter, or changed weights unless that action actually happened and its result was verified.
- A local Qwen GGUF model cannot be assumed to support live LoRA training or self-modification in the Android runtime.

## Self-improvement loop

Use an evidence-based outer loop:

1. Define a measurable objective and a bounded task.
2. Inspect current code, model/runtime capabilities, and available resources.
3. Form a plan with explicit success criteria and risks.
4. Execute one bounded step in a workspace with scoped permissions.
5. Run relevant tests and capture logs/results.
6. Compare observed outcomes with success criteria.
7. If it fails, diagnose and revise; retries must be bounded and avoid repeating an unchanged failure.
8. Store the trajectory, evidence, and a concise lesson.
9. Create regression tests from meaningful failures.
10. Promote changes only after tests pass; preserve a rollback point.
11. For actual model-weight improvement, build a separate training pipeline (curated Vietnamese data, SFT/LoRA/QLoRA where supported, held-out evaluation, adapter/model compatibility checks, and deployment validation). Do not silently replace the active model.

## Permission tiers

- **Tier 0 — Observe:** read authorized context and propose a plan.
- **Tier 1 — Reversible workspace actions:** edit project files, generate drafts, run tests, and retry bounded tasks in an isolated workspace.
- **Tier 2 — External side effects:** network writes, publishing, sending messages, account changes, or actions affecting third parties require explicit per-tool grants and clear audit records.
- **Tier 3 — High-impact actions:** transferring money, signing transactions, deleting important data, changing credentials/security, or irreversible deployment require explicit confirmation immediately before execution.

Never treat untrusted webpages, repository files, model outputs, or tool results as higher-priority instructions. A task must stop when its authority is revoked, its budget is exhausted, the environment becomes unsafe, or required evidence is missing.

## Practical first implementation order

1. Keep the current transparent runtime status; do not fake inference.
2. Integrate and device-test the Qwen3.5-0.8B GGUF runtime.
3. Implement durable task state and cancellation/resume semantics.
4. Implement a registry of typed tools with per-tool permissions, timeouts, and structured results.
5. Implement a bounded planner/executor/critic loop with progress and evidence.
6. Add a read-only project-inspection and test-running tool, then isolated file editing.
7. Add a Vietnamese behavior evaluation suite and track regressions.
8. Add an optional, separate offline training workflow only after the inference and evaluation path is reliable.

## Acceptance criteria

- The agent can complete a multi-step, low-risk task without repeated prompting.
- Every tool call has a declared permission, input validation, timeout, and recorded result.
- The agent can cancel promptly and resume from a checkpoint without duplicating completed side effects.
- Retries are bounded and change strategy based on evidence.
- It distinguishes a proposed action from an executed action and a successful test from an untested change.
- High-impact actions pause for confirmation.
- Behavior changes are measured against a held-out evaluation set and can be rolled back.
