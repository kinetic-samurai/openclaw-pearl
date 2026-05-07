# Codebase Map: OpenClaw Pearl

This document provides a technical map of the OpenClaw Pearl monorepo, detailing the architecture, component responsibilities, and integration flows.

## Architecture Overview

OpenClaw Pearl acts as a bridge between the OpenClaw agentic loop and local Gemma edge models running via LiteRT-LM.

### Data Flow
1. **Request**: OpenClaw (Agent) -> Provider Plugin (TS) -> localhost:8765 (Bridge)
2. **Translation**: Kotlin Bridge converts OpenAI-style `tool_calls` and `messages` into LiteRT-LM formats.
3. **Inference**: Gemma model proposes text or tool intent.
4. **Response**: Bridge converts model intent back into OpenAI-compatible JSON.
5. **Execution**: OpenClaw validates and executes the tool locally.

## Project Structure

### `/apps/litert-bridge` (Kotlin)
The core engine of the bridge.
- **Server**: Ktor/Netty listening on `BRIDGE_PORT` (default 8765).
- **Key Files**:
    - `Main.kt`: Server setup and routes (`/v1/models`, `/v1/chat/completions`).
    - `Models.kt`: Data models for OpenAI compatibility.
    - `PearlRunner.kt`: Abstraction for model inference (currently uses `StubPearlRunner`).

### `/apps/openclaw-provider` (TypeScript)
The OpenClaw-facing plugin.
- **Role**: Configuration layer that registers the bridge endpoint within the OpenClaw provider ecosystem.
- **Environment**: Uses `OPENCLAW_PEARL_BASE_URL` to point to the Kotlin service.

## Integration & Environment

| Variable | Description | Default |
|---|---|---|
| `BRIDGE_HOST` | Host for the bridge server | `127.0.0.1` |
| `BRIDGE_PORT` | Port for the bridge server | `8765` |
| `OPENCLAW_PEARL_MODEL` | Exposed model ID | `gemma-4-e2b-it` |
| `LITERT_MODEL_PATH` | Path to the `.litertlm` model file | (Defined in setup) |

## Tool Translation Rules
- **Intent Only**: The bridge must only translate "tool intent".
- **No Execution**: Real tool execution and permission checks MUST be handled by OpenClaw.
- **Format**: Maps `type: function` specs between OpenAI and LiteRT-LM schemas.
