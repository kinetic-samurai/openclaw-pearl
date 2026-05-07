# LiteRT-LM setup

The current bridge scaffold runs without LiteRT-LM. It uses `StubPearlRunner` so the HTTP/OpenAI-compatible plumbing can be tested first.

The next milestone is to install LiteRT-LM and replace the stub runner with a real `LiteRtPearlRunner`.

## 1. Check Python tooling

```bash
python3 --version
python3 -m pip --version
```

If `uv` is not installed yet:

```bash
curl -LsSf https://astral.sh/uv/install.sh | sh
source "$HOME/.local/bin/env"
```

## 2. Install the LiteRT-LM CLI

```bash
uv tool install litert-lm
litert-lm --help
```

If the package name has changed, check the current LiteRT-LM docs and update this file.

## 3. Download a Gemma LiteRT-LM model

Expected model shape:

```text
*.litertlm
```

For OpenClaw Pearl, start with a Gemma 4 E2B instruction model in LiteRT-LM format, for example:

```text
gemma-4-E2B-it.litertlm
```

Store it outside git, for example:

```bash
mkdir -p "$HOME/models/litert-lm"
```

Then set:

```bash
export LITERT_MODEL_PATH="$HOME/models/litert-lm/gemma-4-E2B-it.litertlm"
```

## 4. Smoke-test LiteRT-LM directly

Before wiring Kotlin, verify the model runs via the LiteRT-LM CLI:

```bash
litert-lm run "$LITERT_MODEL_PATH" --prompt "Say hello from the pearl."
```

## 5. Wire the Kotlin bridge

Once the CLI smoke test works, replace `StubPearlRunner` with `LiteRtPearlRunner` and enable the LiteRT-LM JVM dependency in:

```text
apps/litert-bridge/build.gradle.kts
```

The bridge should keep tool execution disabled locally. It should return tool-call intent to OpenClaw.
