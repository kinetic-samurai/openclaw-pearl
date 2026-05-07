# 🎼 Starfish: Master Orchestration Plan

This document serves as the **Execution Blueprint** for parallel agents working on the OpenClaw Pearl project. It defines the roadmap for evolving the "Starfish" architecture without disrupting the current production run.

---

## 🏗️ Phase 1: The "Dual-Rail" Implementation
To avoid disrupting the current run, all new features (Binary, Speculative) must be implemented on parallel code paths, toggled via environment variables.

| Track | Lead Agent | Objective | Target |
| :--- | :--- | :--- | :--- |
| **Track A: Performance** | `backend-specialist` | Binary Bridge (Protobuf + UDS) | `apps/litert-bridge/src/main/proto` |
| **Track B: Latency** | `performance-optimizer` | Speculative Tool Parsing | `dev.openclaw.pearl.scanner` |
| **Track C: Verification** | `test-engineer` | Mock-Inference Testing | `apps/litert-bridge/src/test` |

---

## ⚡ Track A: The Binary Express (Protobuf)
Parallel agents should focus on implementing the `StarfishService` defined in `starfish.proto`.

### Implementation Steps:
1. **Schema Finalization**: Finalize `starfish.proto` (Done).
2. **Code Generation**: Configure Gradle to generate Java/Kotlin classes from Proto.
3. **UDS Connector**: Implement a Ktor-gRPC server using Unix Domain Sockets (`/tmp/starfish.sock`).
4. **Proxy Logic**: Create `BinaryPearlRunner.kt` as an alternative to `LiteRTPearlRunner.kt`.

---

## 🔍 Track B: Speculative Parsing (State Machine)
This track involves building the `ToolCallScanner` logic to identify XML tags in the stream.

### Implementation Steps:
1. **Scanner Core**: Implement a character-by-character scanner that identifies `<tool_call>` and `</tool_call>`.
2. **Buffer Management**: Logic to capture JSON inside tags while forwarding text outside tags.
3. **Early Signaling**: Emit a "Tool Found" signal as soon as the opening tag is closed.
4. **Safety Fallback**: If the JSON is malformed, re-inject the buffered text into the content stream.

---

## 🧪 Track C: The "Shadow Loop" (Testing)
Crucial for parallel work. We need to test the bridge without a real GPU or LiteRT-LM instance.

### Implementation Steps:
1. **Mock Runner**: Create a `MockPearlRunner` that simulates Gemma output with configurable delays.
2. **Protocol Validation**: Ensure that the bridge correctly converts various tool-calling scenarios (single, multiple, malformed).
3. **Benchmark Suite**: Measure the "String Tax" (JSON) vs the "Binary Gain" (Protobuf).

---

## 🛡️ Contributor Rules for Parallel Agents

1. **Isolation**: Do not modify `LiteRTPearlRunner.kt` directly. Create new implementations (e.g., `SpeculativePearlRunner.kt`) and use a Factory to switch.
2. **Zero Leakage**: Ensure `stripGemmaToolCalls` is always applied to the content field.
3. **Context Awareness**: Always read `STARFISH_SPEC.md` before making architectural changes.
4. **Approval**: All new tracks must be verified against `test-streaming.sh`.

---

## 🏁 Success Criteria
- [ ] Protobuf bridge achieves <5ms overhead (excluding inference).
- [ ] Speculative parsing signals tool intent within 10 tokens of detection.
- [ ] No XML tags ever reach the OpenClaw logs.
