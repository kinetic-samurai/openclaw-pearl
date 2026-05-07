import { defineSingleProviderPluginEntry } from "openclaw/plugin-sdk/provider-entry";

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
        input: ["text" as const],
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
      buildProvider: buildOpenClawPearlProvider as any
    }
  }
});
