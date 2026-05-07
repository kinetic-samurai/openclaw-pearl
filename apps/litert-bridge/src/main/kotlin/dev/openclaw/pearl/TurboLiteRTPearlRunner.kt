package dev.openclaw.pearl

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.serialization.json.*
import java.util.concurrent.atomic.AtomicLong

/**
 * TURBO VERSION of LiteRTPearlRunner.
 * Optimizations:
 * 1. Speculative Tool Parsing (identifies tools while streaming).
 * 2. Persistent Connections (Keep-Alive enabled).
 * 3. Zero-delay Chunk Forwarding.
 */
class TurboLiteRTPearlRunner : PearlRunner {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        engine {
            requestTimeout = 120_000
            endpoint {
                maxConnectionsPerRoute = 10
                keepAliveTime = 60_000
                connectTimeout = 5000
            }
        }
    }

    private val litertBaseUrl = System.getenv("LITERT_BASE_URL") ?: "http://127.0.0.1:9379"
    private val litertModelId = System.getenv("LITERT_MODEL_ID") ?: "gemma4"
    private val json = Json { ignoreUnknownKeys = true }
    private val messageCounter = AtomicLong(System.currentTimeMillis())

    override suspend fun complete(request: ChatCompletionRequest): ChatCompletionResponse {
        // Fallback to standard logic for unary (or optimize it too if needed)
        val delegate = LiteRTPearlRunner()
        return delegate.complete(request)
    }

    override suspend fun completeStreaming(request: ChatCompletionRequest, onChunk: suspend (String) -> Unit) {
        val prompt = GemmaFormatter.format(request.messages, request.tools)

        val proxyRequest = buildJsonObject {
            put("model", litertModelId)
            put("input", prompt)
            put("stream", true)
        }

        try {
            val httpResponse = client.preparePost("$litertBaseUrl/v1/responses") {
                contentType(ContentType.Application.Json)
                setBody(proxyRequest.toString())
                // F1 TWEAK: Keep-Alive enabled (removing Connection: close)
            }.execute()

            val channel = httpResponse.body<ByteReadChannel>()
            val scanner = ToolCallScanner()
            val requestId = "chatcmpl-${messageCounter.incrementAndGet()}"

            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break
                if (line.startsWith("data: ")) {
                    val data = line.removePrefix("data: ").trim()
                    if (data.isEmpty() || data == "[DONE]") continue
                    
                    try {
                        val litertChunk = json.parseToJsonElement(data).jsonObject
                        val text = litertChunk["delta"]?.jsonObject?.get("text")?.jsonPrimitive?.content ?: ""
                        
                        // F1 TWEAK: Speculative Scanning
                        val results = scanner.push(text)
                        results.forEach { result ->
                            when (result) {
                                is ScanResult.Text -> {
                                    sendChunk(onChunk, requestId, request.model, result.content)
                                }
                                is ScanResult.ToolStart -> {
                                    // Signal early! 
                                    // For OpenAI format, we don't have a perfect "start" chunk, 
                                    // but we can send an empty chunk with the tool_call index.
                                    sendToolStartChunk(onChunk, requestId, request.model)
                                }
                                is ScanResult.ToolComplete -> {
                                    sendToolCompleteChunk(onChunk, requestId, request.model, result.name, result.arguments)
                                }
                                else -> {}
                            }
                        }
                    } catch (e: Exception) {
                        println("ERROR parsing chunk: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            println("ERROR in streaming: ${e.message}")
            sendChunk(onChunk, "error", request.model, "Bridge Error: ${e.message}")
        }
    }

    private suspend fun sendChunk(onChunk: suspend (String) -> Unit, id: String, model: String, content: String) {
        if (content.isEmpty()) return
        val chunk = buildJsonObject {
            put("id", id)
            put("object", "chat.completion.chunk")
            put("created", System.currentTimeMillis() / 1000)
            put("model", model)
            put("choices", buildJsonArray {
                add(buildJsonObject {
                    put("index", 0)
                    put("delta", buildJsonObject { put("content", content) })
                    put("finish_reason", null)
                })
            })
        }
        onChunk(chunk.toString())
    }

    private suspend fun sendToolStartChunk(onChunk: suspend (String) -> Unit, id: String, model: String) {
        val chunk = buildJsonObject {
            put("id", id)
            put("object", "chat.completion.chunk")
            put("created", System.currentTimeMillis() / 1000)
            put("model", model)
            put("choices", buildJsonArray {
                add(buildJsonObject {
                    put("index", 0)
                    put("delta", buildJsonObject { 
                        put("tool_calls", buildJsonArray {
                            add(buildJsonObject {
                                put("index", 0)
                                put("id", "call_${System.currentTimeMillis()}")
                                put("type", "function")
                                put("function", buildJsonObject {
                                    put("name", "") // Name will come later
                                    put("arguments", "")
                                })
                            })
                        })
                    })
                })
            })
        }
        onChunk(chunk.toString())
    }

    private suspend fun sendToolCompleteChunk(onChunk: suspend (String) -> Unit, id: String, model: String, name: String, args: String) {
        val chunk = buildJsonObject {
            put("id", id)
            put("object", "chat.completion.chunk")
            put("created", System.currentTimeMillis() / 1000)
            put("model", model)
            put("choices", buildJsonArray {
                add(buildJsonObject {
                    put("index", 0)
                    put("delta", buildJsonObject { 
                        put("tool_calls", buildJsonArray {
                            add(buildJsonObject {
                                put("index", 0)
                                put("function", buildJsonObject {
                                    put("name", name)
                                    put("arguments", args)
                                })
                            })
                        })
                    })
                    put("finish_reason", "tool_calls")
                })
            })
        }
        onChunk(chunk.toString())
    }
}
