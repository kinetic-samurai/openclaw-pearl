# 🦾 OpenClaw Pearl: Agentic Tool Registry

This document tracks the agentic capabilities (tools) supported by the Pearl Bridge and mapped to the Gemma 4 model.

## 📡 Bridge Status
- **Bridge Port**: `8765`
- **Engine Port**: `9379`
- **Inference Strategy**: Persistent Proxy Mode
- **Model**: Gemma 4 (E2B-it)

---

## 🛠️ Core Workspace Tools
These tools allow the agent to interact with the local filesystem and terminal.

### 1. `read_file`
- **Description**: Reads the full content of a file from the workspace.
- **Parameters**:
  - `path` (string, required): Absolute or relative path to the file.
- **Gemma Intent**: Grounding/Reasoning.

### 2. `write_file`
- **Description**: Creates or overwrites a file with specific content.
- **Parameters**:
  - `path` (string, required): Path to save the file.
  - `content` (string, required): The content to write.
- **Gemma Intent**: Code generation/Task execution.

### 3. `run_command`
- **Description**: Executes a shell command in the local environment.
- **Parameters**:
  - `command` (string, required): The bash command to execute.
- **Gemma Intent**: Environment management/DevOps.

---

## 🔋 Edge-Specific Tools (LiteRT-LM)
Optimized tools inspired by the AI Edge Gallery and Gemma 4 architectural capabilities.

### 4. `query_npu_state`
- **Description**: Retrieves performance metrics for the local NPU/CPU.
- **Parameters**: none
- **Gemma Intent**: Performance-aware task scheduling.

### 5. `semantic_search`
- **Description**: Performs a vector-based search across local documentation.
- **Parameters**:
  - `query` (string, required): The search term.
- **Gemma Intent**: Knowledge retrieval (RAG).

---

## 🏗️ Protocol Mapping
How tool calls are translated across the bridge.

### OpenClaw Request (OpenAI Format)
```json
{
  "tools": [
    {
      "type": "function",
      "function": { "name": "read_file", "parameters": { ... } }
    }
  ]
}
```

### Gemma 4 Prompt (Bridge Formatted)
```text
<start_of_turn>user
Read the build file.<end_of_turn>
<start_of_turn>model
<thought>
I need to see the dependencies.
</thought>
<tool_call>
{"name": "read_file", "arguments": {"path": "build.gradle.kts"}}
</tool_call>
```

### Pearl Bridge Response (OpenAI Format)
```json
{
  "choices": [{
    "finish_reason": "tool_calls",
    "message": {
      "tool_calls": [{
        "id": "call_123",
        "function": { "name": "read_file", "arguments": "{\"path\":\"build.gradle.kts\"}" }
      }]
    }
  }]
}
```

---

## 📈 Roadmap
- [ ] Implement `tool_use` validation in Kotlin.
- [ ] Add support for Multi-tool parallel calls (Gemma 4 native).
- [ ] Integration with local Vector DB for `semantic_search`.
