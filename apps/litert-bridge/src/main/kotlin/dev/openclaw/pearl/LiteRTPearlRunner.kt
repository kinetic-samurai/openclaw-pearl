package dev.openclaw.pearl

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.random.Random

/**
 * A PearlRunner implementation that proxies requests to a local litert-lm serve instance.
 * Talks to litert-lm's /v1/responses endpoint (OpenAI Responses API format).
 */
class LiteRTPearlRunner : PearlRunner {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        engine {
            requestTimeout = 120_000
            // LiteRT BaseHTTP is single-threaded; limit connections to avoid overwhelming it
            endpoint {
                maxConnectionsPerRoute = 1
                keepAliveTime = 30_000
            }
        }
    }

    private val litertBaseUrl = System.getenv("LITERT_BASE_URL") ?: "http://127.0.0.1:9379"
    private val litertModelId = System.getenv("LITERT_MODEL_ID") ?: "gemma4"
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun complete(request: ChatCompletionRequest): ChatCompletionResponse {
        val prompt = GemmaFormatter.format(request.messages, request.tools)

        val proxyRequest = buildJsonObject {
            put("model", litertModelId)
            put("input", prompt)
            put("stream", false)
        }

        // Retry up to 5 times on transient errors (LiteRT cold start, GPU busy, malformed responses)
        var lastError: Exception? = null
        for (attempt in 1..5) {
            try {
                val rawResponse: String = client.post("$litertBaseUrl/v1/responses") {
                    contentType(ContentType.Application.Json)
                    setBody(proxyRequest.toString())
                    // LiteRT BaseHTTP/1.0 doesn't support keep-alive well; force close
                    header(HttpHeaders.Connection, "close")
                }.body()

                val trimmed = rawResponse.trim()
                if (!trimmed.startsWith("{")) {
                    val jsonStart = trimmed.indexOf("{")
                    if (jsonStart < 0) {
                        throw RuntimeException("LiteRT returned non-JSON response: ${trimmed.take(200)}")
                    }
                    val jsonBody = trimmed.substring(jsonStart)
                    return parseResponse(jsonBody, request.model)
                }

                return parseResponse(trimmed, request.model)
            } catch (e: Exception) {
                lastError = e
                if (attempt < 5) {
                    // Exponential backoff with jitter: 1s, 2s, 4s, 8s + random 0-500ms
                    val baseDelay = 1000L * (1L shl (attempt - 1))
                    val jitter = Random.nextLong(0, 500)
                    delay(baseDelay + jitter)
                }
            }
        }

        return ChatCompletionResponse(
            id = "error-${System.currentTimeMillis()}",
            created = System.currentTimeMillis() / 1000,
            model = request.model,
            choices = listOf(
                Choice(
                    index = 0,
                    message = ChatMessage(
                        role = "assistant",
                        content = JsonPrimitive("Error connecting to local LiteRT server after 5 attempts: ${lastError?.message}")
                    ),
                    finishReason = "error"
                )
            )
        )
    }

    private fun parseResponse(rawJson: String, modelName: String): ChatCompletionResponse {
        val response = json.decodeFromString(LiteRTResponse.serializer(), rawJson)

        val text = response.output
            .firstOrNull { it.type == "message" }
            ?.content
            ?.firstOrNull { it.type == "output_text" }
            ?.text ?: ""

        // Parse Gemma <tool_call> XML into OpenAI tool_calls format
        val toolCalls = parseGemmaToolCalls(text)
        val cleanText = stripGemmaToolCalls(text)

        return ChatCompletionResponse(
            id = "chatcmpl-${System.currentTimeMillis()}",
            created = System.currentTimeMillis() / 1000,
            model = modelName,
            choices = listOf(
                Choice(
                    index = 0,
                    message = ChatMessage(
                        role = "assistant",
                        content = if (cleanText.isBlank() && toolCalls != null && toolCalls.isNotEmpty()) null else JsonPrimitive(cleanText),
                        toolCalls = toolCalls
                    ),
                    finishReason = if (toolCalls != null && toolCalls.isNotEmpty()) "tool_calls" else "stop"
                )
            )
        )
    }

    /**
     * Parse Gemma's <tool_call>XML format into OpenAI tool_calls array.
     * Gemma format: <tool_call>{"tool_name": "...", "parameters": {...}}</tool_call>
     * OpenAI format: [{"id": "...", "type": "function", "function": {"name": "...", "arguments": "..."}}]
     */
    private fun parseGemmaToolCalls(text: String): List<ToolCall>? {
        // Match each <tool_call>...</tool_call> block individually
        // Use [\s\S]*? (non-greedy, dotall) to handle multiline JSON
        val toolCallPattern = Regex("<tool_call>([\\s\\S]*?)</tool_call>")
        val matches = toolCallPattern.findAll(text).toList()
        if (matches.isEmpty()) return null

        return matches.toList().mapIndexedNotNull { index, match ->
            val inner = match.groupValues[1].trim()
            val jsonStart = inner.indexOf('{')
            val jsonEnd = inner.lastIndexOf('}')
            if (jsonStart < 0 || jsonEnd <= jsonStart) return@mapIndexedNotNull null

            val jsonStr = inner.substring(jsonStart, jsonEnd + 1)
            val toolJson = try {
                json.parseToJsonElement(jsonStr).jsonObject
            } catch (e: Exception) {
                null
            } ?: return@mapIndexedNotNull null

            val name = toolJson["tool_name"]?.jsonPrimitive?.content
                ?: toolJson["name"]?.jsonPrimitive?.content
                ?: "unknown"
            val params = toolJson["parameters"]?.toString()
                ?: toolJson["arguments"]?.toString()
                ?: "{}"

            ToolCall(
                id = "call_${System.currentTimeMillis()}_$index",
                type = "function",
                function = ToolCallFunction(name = name, arguments = params)
            )
        }.ifEmpty { null }
    }

    /**
     * Strip <tool_call> blocks from text to avoid leaking XML to the client.
     */
    private fun stripGemmaToolCalls(text: String): String {
        return text.replace(Regex("<tool_call>[\\s\\S]*?</tool_call>"), "").trim()
    }

    /**
     * Stream completion by proxying LiteRT's native SSE stream.
     * Converts LiteRT SSE chunks into OpenAI Chat Completion chunk format.
     */
    override suspend fun completeStreaming(request: ChatCompletionRequest, onChunk: suspend (String) -> Unit) {
        val prompt = GemmaFormatter.format(request.messages, request.tools)

        val proxyRequest = buildJsonObject {
            put("model", litertModelId)
            put("input", prompt)
            put("stream", true)
        }

        var lastError: Exception? = null
        for (attempt in 1..5) {
            try {
                val httpResponse = client.preparePost("$litertBaseUrl/v1/responses") {
                    contentType(ContentType.Application.Json)
                    setBody(proxyRequest.toString())
                    header(HttpHeaders.Connection, "close")
                }.execute()

                val channel = httpResponse.body<ByteReadChannel>()
                while (!channel.isClosedForRead) {
                    val line = channel.readUTF8Line() ?: break
                    if (line.startsWith("data: ")) {
                        val data = line.removePrefix("data: ").trim()
                        if (data.isEmpty()) continue
                        try {
                            val litertChunk = json.parseToJsonElement(data).jsonObject
                            val content = extractStreamContent(litertChunk)
                            if (content != null) {
                                val openaiChunk = buildJsonObject {
                                    put("id", "chatcmpl-${System.currentTimeMillis()}")
                                    put("object", "chat.completion.chunk")
                                    put("created", System.currentTimeMillis() / 1000)
                                    put("model", request.model)
                                    put("choices", buildJsonArray {
                                        add(buildJsonObject {
                                            put("index", 0)
                                            put("delta", buildJsonObject { put("content", content) })
                                            put("finish_reason", null)
                                        })
                                    })
                                }
                                onChunk(openaiChunk.toString())
                            }
                        } catch (_: Exception) { }
                    }
                }
                return
            } catch (e: Exception) {
                lastError = e
                if (attempt < 5) {
                    val baseDelay = 1000L * (1L shl (attempt - 1))
                    val jitter = Random.nextLong(0, 500)
                    delay(baseDelay + jitter)
                }
            }
        }

        val errorChunk = buildJsonObject {
            put("id", "error-${System.currentTimeMillis()}")
            put("object", "chat.completion.chunk")
            put("created", System.currentTimeMillis() / 1000)
            put("model", request.model)
            put("choices", buildJsonArray {
                add(buildJsonObject {
                    put("index", 0)
                    put("delta", buildJsonObject { put("content", "Error: ${lastError?.message}") })
                })
            })
        }
        onChunk(errorChunk.toString())
    }
    /**
     * Extract text content from a LiteRT streaming chunk.
     * LiteRT streaming format: {"delta": {"text": "..."}}
     */
    private fun extractStreamContent(chunk: JsonObject): String? {
        return chunk["delta"]?.jsonObject?.get("text")?.jsonPrimitive?.content
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
    val type: String = "message",
    val role: String? = null,
    val status: String? = null,
    val content: List<LiteRTContent> = emptyList()
)

@Serializable
data class LiteRTContent(
    val type: String,
    val text: String,
    val annotations: List<JsonElement> = emptyList()
)
