# Architecture

OpenClaw Pearl is a thin provider bridge with one rule: OpenClaw remains the tool executor.

```text
OpenClaw
  ↓ OpenAI-compatible request
OpenClaw Pearl provider plugin
  ↓ localhost HTTP
LiteRT bridge
  ↓ local model runtime
Gemma edge model
```

## Responsibilities

| Layer | Responsibility |
|---|---|
| OpenClaw | Agent loop, tool catalog, permission policy, tool execution |
| Provider plugin | Register the local model endpoint and model metadata |
| LiteRT bridge | Translate OpenAI-ish chat requests into local model calls |
| Gemma model | Produce text or proposed tool calls |

## Non-goals

- The bridge should not execute shell commands.
- The bridge should not mutate the filesystem.
- The bridge should not bypass OpenClaw's approval and policy layer.

## Tool-call flow

```text
1. OpenClaw sends messages and tool specs.
2. Bridge converts tool specs to the LiteRT-LM tool format.
3. Gemma proposes a tool call.
4. Bridge returns an OpenAI-compatible tool_calls message.
5. OpenClaw validates and executes the tool.
6. OpenClaw sends the tool result back as another message.
7. Gemma produces the final answer.
```
