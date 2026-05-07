package dev.openclaw.pearl

import kotlinx.serialization.json.*

/**
 * Utility to format messages for Gemma 4 Chat/Instruction models.
 * Uses TOON-style (Token-Oriented Object Notation) compaction to save tokens.
 */
object GemmaFormatter {
    fun format(messages: List<ChatMessage>, tools: List<ToolSpec> = emptyList()): String {
        val sb = StringBuilder()
        
        // TOON-style Tool Compaction
        if (tools.isNotEmpty()) {
            sb.append("<start_of_turn>user\n")
            sb.append("Tools[${tools.size}]{name, desc, params}:\n")
            tools.forEach { tool ->
                val paramsCompact = tool.function.parameters?.toString()?.replace(Regex("\\s+"), "") ?: "{}"
                sb.append("${tool.function.name}, ${tool.function.description}, $paramsCompact\n")
            }
            sb.append("\nUse <tool_call>{\"tool_name\":\"...\",\"parameters\":{...}}</tool_call>\n")
            sb.append("<end_of_turn>\n")
        }

        // Message Compaction: Prune older messages if history is huge (simple head-tail retention)
        val maxMessages = 10
        val displayMessages = if (messages.size > maxMessages) {
            val head = messages.take(2) // Keep system/initial context
            val tail = messages.takeLast(maxMessages - 2)
            head + tail
        } else {
            messages
        }

        displayMessages.forEach { msg ->
            val role = when (msg.role) {
                "user" -> "user"
                "assistant" -> "model"
                "system" -> "user"
                else -> "user"
            }
            
            sb.append("<start_of_turn>$role\n")
            sb.append(msg.textContent.trim())
            
            msg.toolCalls?.forEach { call ->
                // Compact JSON for tool calls
                val args = call.function.arguments.replace(Regex("\\s+"), "")
                sb.append("\n<tool_call>{\"tool_name\":\"${call.function.name}\",\"parameters\":$args}</tool_call>")
            }
            
            sb.append("<end_of_turn>\n")
        }
        
        if (messages.lastOrNull()?.role != "assistant") {
            sb.append("<start_of_turn>model\n")
        }
        
        return sb.toString()
    }
}
