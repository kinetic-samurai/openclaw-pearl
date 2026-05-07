# OpenClaw × Gemma 4 E2B how-to

This document cross-references the OpenClaw provider-plugin pattern with the current OpenClaw Pearl bridge design for running a local Gemma 4 E2B edge model.

## Goal

Run OpenClaw against a local OpenAI-compatible endpoint:

```text
OpenClaw
  -> OpenClaw provider plugin
  -> OpenClaw Pearl bridge on localhost:8765
  -> LiteRT-LM server on localhost:9379
  -> Gemma 4 E2B model
```

## Current repo state

OpenClaw Pearl already has two pieces:

1. `apps/openclaw-provider`
   - TypeScript provider package.
   - Currently a placeholder config export.
   - Needs conversion to the real OpenClaw provider SDK entry shape.

2. `apps/litert-bridge`
   - Kotlin/Ktor bridge.
   - Exposes OpenAI-compatible endpoints at `/v1/models` and `/v1/chat/completions`.
   - Proxies requests to a local LiteRT-LM server using `LiteRTPearlRunner`.
   - Converts Gemma `<tool_call>...</tool_call>` blocks back into OpenAI-style `tool_calls`.

## OpenClaw provider pattern

A provider plugin should have:

```json
{
  "type": "module",
  "main": "./dist/index.js",
  "openclaw": {
    "extensions": ["./dist/index.js"],
    "providers": ["openclaw-pearl"],
    "compat": {
      "pluginApi": ">=2026.3.24-beta.2",
      "minGatewayVersion": "2026.3.24-beta.2"
    }
  }
}
```

The provider entry should use `defineSingleProviderPluginEntry` from:

```ts
import { defineSingleProviderPluginEntry } from "openclaw/plugin-sdk/provider-entry";
```

For OpenClaw Pearl, the provider catalog should return an OpenAI-compatible provider:

```ts
function buildOpenClawPearlProvider() {
  return {
    baseUrl: process.env.OPENCLAW_PEARL_BASE_URL ?? "http://127.0.0.1:8765/v1",
    api: "openai-completions" as const,
    models: [
      {
        id: "gemma-4-e2b-it",
        name: "Gemma 4 E2B IT via LiteRT-LM",
        contextWindow: 32768,
        maxTokens: 4096,
        input: ["text"],
        cost: {
          input: 0,
          output: 0,
          cacheRead: 0,
          cacheWrite: 0
        },
        reasoning: false
      }
    ]
  };
}
```

Then export:

```ts
export default defineSingleProviderPluginEntry({
  id: "openclaw-pearl",
  name: "OpenClaw Pearl",
  description: "Local Gemma 4 E2B provider bridge via LiteRT-LM",
  provider: {
    label: "OpenClaw Pearl",
    docsPath: "/providers/openclaw-pearl",
    auth: [
      {
        methodId: "local",
        label: "Local bridge",
        optionKey: "openclawPearlLocal",
        flagName: "--openclaw-pearl-local",
        envVar: "OPENCLAW_PEARL_LOCAL",
        promptMessage: "Use local OpenClaw Pearl bridge",
        defaultModel: "openclaw-pearl/gemma-4-e2b-it"
      }
    ],
    catalog: {
      buildProvider: buildOpenClawPearlProvider
    }
  }
});
```

## Bridge pattern

OpenClaw talks to OpenClaw Pearl as if it were an OpenAI-compatible chat model.

OpenClaw Pearl then talks to LiteRT-LM through a local responses endpoint:

```text
OPENCLAW_PEARL_BASE_URL=http://127.0.0.1:8765/v1
LITERT_BASE_URL=http://127.0.0.1:9379
LITERT_MODEL_ID=gemma4
```

The bridge owns format translation only. Tool execution remains in OpenClaw.

## Tool calling contract

OpenClaw sends tools in OpenAI-style function schema:

```text
OpenClaw tool specs
  -> GemmaFormatter turns them into prompt instructions
  -> Gemma emits <tool_call>{...}</tool_call>
  -> LiteRTPearlRunner converts that into OpenAI tool_calls
  -> OpenClaw validates and executes
```

The bridge must not execute shell commands, mutate files, or run OpenClaw tools directly.

## Local run order

1. Start LiteRT-LM with Gemma 4 E2B on port `9379`.
2. Start the OpenClaw Pearl Kotlin bridge on port `8765`.
3. Configure OpenClaw to use provider `openclaw-pearl/gemma-4-e2b-it`.
4. Send a chat request from OpenClaw.
5. Confirm tool calls come back as OpenAI-compatible `tool_calls`.

## Implementation checklist

- [ ] Install LiteRT-LM locally.
- [ ] Download Gemma 4 E2B `.litertlm` model.
- [ ] Confirm LiteRT-LM serves `/v1/responses` on `127.0.0.1:9379`.
- [ ] Build `apps/litert-bridge`.
- [ ] Replace placeholder OpenClaw provider export with SDK provider entry.
- [ ] Add OpenClaw provider metadata and model catalog.
- [ ] Test non-streaming chat.
- [ ] Test streaming chat.
- [ ] Test one OpenClaw tool call round-trip.

## Notes

Gemma 4 E2B is treated as the local model behind the bridge. OpenClaw Pearl should remain a translator and provider adapter, not a second agent runtime.
