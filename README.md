# OpenClaw Pearl

> A local Gemma pearl inside the OpenClaw agent loop. 🦞🦪

OpenClaw Pearl is a provider-plugin scaffold for routing OpenClaw model calls to a local edge model bridge. The first target is **Gemma 4 E2B** running through **LiteRT-LM** on a laptop.

The project is intentionally split in two:

```text
OpenClaw
  ↓ OpenAI-compatible chat/tool request
apps/openclaw-provider
  ↓ localhost HTTP
apps/litert-bridge
  ↓ LiteRT-LM runtime
Gemma edge model
```

## Status

This repository starts as a scaffold, not a finished provider. The Kotlin bridge currently includes an OpenAI-compatible server shell and a stub runner so the plumbing can be tested before wiring in the real LiteRT-LM SDK calls.

## Repository layout

```text
openclaw-pearl/
  apps/
    openclaw-provider/   TypeScript OpenClaw provider plugin
    litert-bridge/       Kotlin/JVM localhost bridge
  docs/                  Architecture and tool-call notes
  examples/              Example OpenClaw config
  scripts/               Local helper scripts
```

## Quick start

Clone the repo:

```bash
git clone https://github.com/kinetic-samurai/openclaw-pearl.git
cd openclaw-pearl
```

Install Java 21, Gradle, Node.js, and pnpm.

Run the local bridge:

```bash
cp .env.example .env
./scripts/run-bridge.sh
```

Check it:

```bash
curl http://127.0.0.1:8765/health
curl http://127.0.0.1:8765/v1/models
```

Build the OpenClaw provider package:

```bash
pnpm install
pnpm --filter @openclaw-pearl/openclaw-provider build
```

## Environment

```bash
OPENCLAW_PEARL_BASE_URL=http://127.0.0.1:8765/v1
OPENCLAW_PEARL_API_KEY=local-pearl
OPENCLAW_PEARL_MODEL=gemma-4-e2b-it
LITERT_MODEL_PATH=/absolute/path/to/gemma-4-E2B-it.litertlm
LITERT_BACKEND=gpu
```

## Safety model

OpenClaw Pearl should **not** execute host tools directly from the model bridge. The bridge translates model/tool-call intent and hands tool calls back to OpenClaw, where allowlists, permissions, and user approval can live.

```text
Gemma proposes → OpenClaw validates → OpenClaw executes → Gemma observes
```

That keeps the pearl shiny and the claws out of the power socket. ⚡
