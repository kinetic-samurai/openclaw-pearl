# Design Doc: Speculative Tool Parsing (Starfish Option B)

## 🎯 Goal
Reduce the "Perceived Latency" of tool calling by identifying and signaling tool intent as it streams, rather than waiting for the entire XML block to finish.

---

## 🏗️ The Problem
In the current "Starfish" implementation:
1. Model streams: `I will read the file. <tool_call>{"tool_name": "read_file", ...`
2. Bridge waits for `</tool_call>`.
3. Only then does it send the `tool_calls` array to OpenClaw.
4. Total delay = (Tokens in Tool Call JSON) * (Latency Per Token).

For large JSON payloads, this can add **500ms - 2s** of dead air.

---

## 🚀 The Solution: Speculative Signals

The Bridge will implement a **Streaming State Machine** that scans the UTF-8 buffer in real-time.

### State 1: `TEXT`
- Default state. 
- All tokens are forwarded to OpenClaw as standard chat chunks.

### State 2: `TOOL_DETECTED` (Speculative)
- **Trigger**: Stream contains the sequence `<tool_call>`.
- **Action**: 
    1. **HALT** text forwarding to OpenClaw (prevent XML leakage).
    2. Emit a `tool_call_start` signal (via Protobuf or a specialized SSE header).
    3. OpenClaw receives the tool name early and can begin "pre-warming" (e.g., checking file permissions or initializing a sandbox).

### State 3: `TOOL_PARSING`
- **Action**: Bridge buffers tokens inside the `<tool_call>` block.
- **Incremental Validation**: If the model is outputting the `tool_name`, the bridge can validate it against the `AGENTIC_REGISTRY.md` immediately.

### State 4: `TOOL_EMISSION`
- **Trigger**: Stream contains `</tool_call>`.
- **Action**: Bridge parses the buffer, converts to OpenAI JSON, and sends the final `tool_calls` chunk.

---

## 📈 Impact Analysis

| Metric | Without Speculation | With Speculation | Improvement |
| :--- | :--- | :--- | :--- |
| **TTFT (Time to First Tool)** | End of JSON block | Detection of `<tool_call>` | **~80% reduction** |
| **Verification Delay** | After model finishes | During model generation | **Parallel execution** |

---

## 🛠️ Implementation Strategy (Kotlin)

We will replace the line-based `readUTF8Line()` with a **Chunk-based Scanner**:

```kotlin
val scanner = ToolCallScanner()
channel.consumeEach { chunk ->
    val result = scanner.push(chunk)
    when (result) {
        is ScanResult.Text -> emitChunk(result.text)
        is ScanResult.ToolStart -> emitSpeculativeSignal(result.name)
        is ScanResult.ToolComplete -> emitFinalToolCall(result.json)
    }
}
```

---

> [!TIP]
> **Edge Case**: If the model fails to output valid JSON after `<tool_call>`, the bridge can "Fall Back" by emitting the buffered text as raw content, ensuring no information is lost even if the model hallucinates the XML format.
