package dev.openclaw.pearl

interface PearlRunner {
    suspend fun complete(request: ChatCompletionRequest): ChatCompletionResponse
}

class StubPearlRunner : PearlRunner {
    override suspend fun complete(request: ChatCompletionRequest): ChatCompletionResponse {
        val lastUserMessage = request.messages.lastOrNull { it.role == "user" }?.content.orEmpty()
        val availableTool = request.tools.firstOrNull()?.function

        val assistantMessage = if (availableTool != null && lastUserMessage.contains("tool", ignoreCase = true)) {
            ChatMessage(
                role = "assistant",
                content = null,
                toolCalls = listOf(
                    ToolCall(
                        id = "call_openclaw_pearl_stub_1",
                        function = ToolCallFunction(
                            name = availableTool.name,
                            arguments = "{}"
                        )
                    )
                )
            )
        } else {
            ChatMessage(
                role = "assistant",
                content = "OpenClaw Pearl bridge is alive. LiteRT-LM wiring goes here. Last user message: ${lastUserMessage.take(240)}"
            )
        }

        return ChatCompletionResponse(
            id = "chatcmpl-openclaw-pearl-stub",
            created = System.currentTimeMillis() / 1000,
            model = request.model,
            choices = listOf(
                Choice(
                    index = 0,
                    message = assistantMessage,
                    finishReason = if (assistantMessage.toolCalls.isNullOrEmpty()) "stop" else "tool_calls"
                )
            )
        )
    }
}
