package dev.openclaw.pearl

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ModelsResponse(
    val `object`: String = "list",
    val data: List<ModelInfo>
)

@Serializable
data class ModelInfo(
    val id: String,
    val `object`: String = "model",
    val owned_by: String = "openclaw-pearl"
)

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val tools: List<ToolSpec> = emptyList(),
    val stream: Boolean = false,
    val temperature: Double? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    @SerialName("max_completion_tokens") val maxCompletionTokens: Int? = null,
    val store: Boolean = false,
    @SerialName("tool_choice") val toolChoice: JsonElement? = null,
    val n: Int? = null
)

@Serializable
data class ChatCompletionChunk(
    val id: String,
    val `object`: String = "chat.completion.chunk",
    val created: Long,
    val model: String,
    val choices: List<ChunkChoice>
)

@Serializable
data class ChunkChoice(
    val index: Int,
    val delta: ChunkDelta,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class ChunkDelta(
    val role: String? = null,
    val content: String? = null
)

@Serializable
data class ChatMessage(
    val role: String,
    val content: JsonElement? = null,
    val name: String? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCall>? = null
) {
    /**
     * Extracts plain text content from the flexible 'content' field.
     * Handles both simple JSON strings and complex content arrays.
     */
    val textContent: String
        get() = when {
            content == null -> ""
            content is kotlinx.serialization.json.JsonPrimitive && content.isString -> content.content
            content is kotlinx.serialization.json.JsonArray -> {
                content.filterIsInstance<kotlinx.serialization.json.JsonObject>()
                    .mapNotNull { it["text"] as? kotlinx.serialization.json.JsonPrimitive }
                    .joinToString("\n") { it.content }
            }
            else -> content.toString()
        }
}

@Serializable
data class ToolSpec(
    val type: String,
    val function: FunctionSpec
)

@Serializable
data class FunctionSpec(
    val name: String,
    val description: String? = null,
    val parameters: JsonElement? = null
)

@Serializable
data class ChatCompletionResponse(
    val id: String,
    val `object`: String = "chat.completion",
    val created: Long,
    val model: String,
    val choices: List<Choice>
)

@Serializable
data class Choice(
    val index: Int,
    val message: ChatMessage,
    @SerialName("finish_reason") val finishReason: String
)

@Serializable
data class ToolCall(
    val id: String,
    val type: String = "function",
    val function: ToolCallFunction
)

@Serializable
data class ToolCallFunction(
    val name: String,
    val arguments: String
)
