export type PearlProviderConfig = {
  id: string;
  name: string;
  baseUrl: string;
  model: string;
};

const baseUrl = process.env.OPENCLAW_PEARL_BASE_URL ?? "http://127.0.0.1:8765/v1";
const model = process.env.OPENCLAW_PEARL_MODEL ?? "gemma-4-e2b-it";

export const openClawPearlProvider: PearlProviderConfig = {
  id: "openclaw-pearl",
  name: "OpenClaw Pearl",
  baseUrl,
  model,
};

/**
 * Placeholder provider export.
 *
 * The intended final shape is an OpenClaw provider plugin that exposes a local
 * OpenAI-compatible endpoint backed by apps/litert-bridge.
 *
 * Keep this module small: the Kotlin bridge owns LiteRT-LM, while OpenClaw owns
 * tool execution and policy.
 */
export default openClawPearlProvider;
