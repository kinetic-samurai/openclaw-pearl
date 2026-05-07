package dev.openclaw.pearl

/**
 * Utility to format messages for Gemma 4 Chat/Instruction models.
 */
object GemmaFormatter {
    fun format(messages: List<ChatMessage>, tools: List<ToolSpec> = emptyList()): String {
        val sb = StringBuilder()
        
        // Add tools description if present
        if (tools.isNotEmpty()) {
            sb.append("<start_of_turn>user\n")
            sb.append("You have access to the following tools:\n")
            tools.forEach { tool ->
                sb.append("- ${tool.function.name}: ${tool.function.description}\n")
                sb.append("  Parameters: ${tool.function.parameters}\n")
            }
            sb.append("\nTo use a tool, respond with a JSON object inside <tool_call> tags.\n")
            sb.append("<end_of_turn>\n")
        }

        messages.forEach { msg ->
            val role = when (msg.role) {
                "user" -> "user"
                "assistant" -> "model"
                "system" -> "user" // Gemma 4 often maps system to user or prepends it
                else -> "user"
            }
            
            sb.append("<start_of_turn>$role\n")
            if (msg.content != null) {
                sb.append(msg.content)
            }
            
            // Handle tool calls from previous turns
            msg.toolCalls?.forEach { call ->
                sb.append("\n<tool_call>{\"name\": \"${call.function.name}\", \"arguments\": ${call.function.arguments}}</tool_call>")
            }
            
            sb.append("<end_of_turn>\n")
        }
        
        // Ensure the model knows it's its turn
        if (messages.lastOrNull()?.role != "assistant") {
            sb.append("<start_of_turn>model\n")
        }
        
        return sb.toString()
    }
}
