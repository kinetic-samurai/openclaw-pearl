package dev.openclaw.pearl

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.io.File

/**
 * A PearlRunner implementation that proxies requests to a local litert-lm serve instance.
 */
class LiteRTPearlRunner : PearlRunner {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    override suspend fun complete(request: ChatCompletionRequest): ChatCompletionResponse {
        val modelId = "gemma4" // Using the imported model ID
        val prompt = GemmaFormatter.format(request.messages, request.tools)
        
        val proxyRequest = mapOf(
            "model" to modelId,
            "input" to prompt
        )

        return try {
            val response: LiteRTResponse = client.post("http://127.0.0.1:9379/v1/responses") {
                contentType(ContentType.Application.Json)
                setBody(proxyRequest)
            }.body()

            val text = response.output.firstOrNull()?.content?.firstOrNull()?.text ?: ""
            
            ChatCompletionResponse(
                id = "chatcmpl-${System.currentTimeMillis()}",
                created = System.currentTimeMillis() / 1000,
                model = request.model,
                choices = listOf(
                    Choice(
                        index = 0,
                        message = ChatMessage(role = "assistant", content = text),
                        finishReason = "stop"
                    )
                )
            )
        } catch (e: Exception) {
            ChatCompletionResponse(
                id = "error-${System.currentTimeMillis()}",
                created = System.currentTimeMillis() / 1000,
                model = request.model,
                choices = listOf(
                    Choice(
                        index = 0,
                        message = ChatMessage(role = "assistant", content = "Error connecting to local LiteRT server: ${e.message}"),
                        finishReason = "error"
                    )
                )
            )
        }
    }
}

@Serializable
data class LiteRTResponse(
    val id: String,
    val output: List<LiteRTOutput>
)

@Serializable
data class LiteRTOutput(
    val id: String,
    val content: List<LiteRTContent>
)

@Serializable
data class LiteRTContent(
    val type: String,
    val text: String
)
