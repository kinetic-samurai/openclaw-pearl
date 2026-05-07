package dev.openclaw.pearl

import kotlinx.serialization.json.*
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * A PearlRunner implementation that uses the litert-lm CLI to perform inference.
 * 
 * It expects the LITERT_MODEL_PATH environment variable to point to a .litertlm model.
 */
class LiteRTPearlRunner : PearlRunner {
    private val modelPath = System.getenv("LITERT_MODEL_PATH") 
        ?: (System.getProperty("user.home") + "/models/litert-lm/gemma-4-E2B-it.litertlm")

    override suspend fun complete(request: ChatCompletionRequest): ChatCompletionResponse {
        if (!File(modelPath).exists()) {
            throw IllegalStateException("Model not found at $modelPath. Please ensure the download is complete.")
        }

        // Use the native Gemma 4 prompt format
        val prompt = GemmaFormatter.format(request.messages, request.tools)
        
        // Construct the CLI command
        val command = mutableListOf(
            "litert-lm", "run",
            modelPath,
            "--prompt", prompt,
            "--max-num-tokens", (request.maxTokens ?: 512).toString(),
            "--backend", "cpu"
        )

        // TODO: Map tools to the prompt or use --tools if supported by CLI
        
        val process = ProcessBuilder(command)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start()

        val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
        process.waitFor(30, TimeUnit.SECONDS)

        val assistantMessage = ChatMessage(
            role = "assistant",
            content = output
        )

        return ChatCompletionResponse(
            id = "chatcmpl-${System.nanoTime()}",
            created = System.currentTimeMillis() / 1000,
            model = request.model,
            choices = listOf(
                Choice(
                    index = 0,
                    message = assistantMessage,
                    finishReason = "stop"
                )
            )
        )
    }
}
