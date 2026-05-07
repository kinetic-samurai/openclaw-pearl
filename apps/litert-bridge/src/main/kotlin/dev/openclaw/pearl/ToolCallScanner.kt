package dev.openclaw.pearl

/**
 * ScanResult represents the outcome of a chunk processing turn.
 */
sealed class ScanResult {
    data class Text(val content: String) : ScanResult()
    data class ToolStart(val name: String) : ScanResult()
    data class ToolComplete(val name: String, val arguments: String) : ScanResult()
    data object None : ScanResult()
}

/**
 * A streaming scanner that identifies <tool_call> blocks speculative.
 */
class ToolCallScanner {
    private var buffer = StringBuilder()
    private var inToolCall = false
    private val tagStart = "<tool_call>"
    private val tagEnd = "</tool_call>"

    fun push(chunk: String): List<ScanResult> {
        val results = mutableListOf<ScanResult>()
        buffer.append(chunk)
        
        var changed = true
        while (changed) {
            changed = false
            val current = buffer.toString()
            
            if (!inToolCall) {
                val startIdx = current.indexOf(tagStart)
                if (startIdx >= 0) {
                    // Emit text before the tag
                    if (startIdx > 0) {
                        results.add(ScanResult.Text(current.substring(0, startIdx)))
                    }
                    inToolCall = true
                    buffer.delete(0, startIdx + tagStart.length)
                    results.add(ScanResult.ToolStart("unknown")) // Initial signal
                    changed = true
                } else {
                    // No start tag found. 
                    // Be careful not to emit a partial tag if it's at the end of the buffer
                    val partialTagStart = current.lastIndexOf('<')
                    if (partialTagStart >= 0 && tagStart.startsWith(current.substring(partialTagStart))) {
                        // Keep the partial tag in buffer
                        if (partialTagStart > 0) {
                            results.add(ScanResult.Text(current.substring(0, partialTagStart)))
                            buffer.delete(0, partialTagStart)
                        }
                    } else {
                        // No partial tag, emit all
                        results.add(ScanResult.Text(current))
                        buffer.setLength(0)
                    }
                }
            } else {
                val endIdx = current.indexOf(tagEnd)
                if (endIdx >= 0) {
                    val jsonStr = current.substring(0, endIdx).trim()
                    // Try to parse the name even if it's incomplete (best effort)
                    val name = extractName(jsonStr)
                    results.add(ScanResult.ToolComplete(name, extractArgs(jsonStr)))
                    inToolCall = false
                    buffer.delete(0, endIdx + tagEnd.length)
                    changed = true
                } else {
                    // Still in tool call, just buffering
                    // But we can try to extract the name speculative if we have enough
                    val name = extractName(current)
                    if (name != "unknown") {
                        // Could emit updated ToolStart here if needed
                    }
                }
            }
        }
        
        return results
    }

    private fun extractName(json: String): String {
        val nameMatch = Regex("\"tool_name\"\\s*:\\s*\"([^\"]+)\"").find(json)
            ?: Regex("\"name\"\\s*:\\s*\"([^\"]+)\"").find(json)
        return nameMatch?.groupValues?.get(1) ?: "unknown"
    }

    private fun extractArgs(json: String): String {
        val argsIdx = json.indexOf("\"parameters\"")
        if (argsIdx >= 0) {
            val start = json.indexOf('{', argsIdx)
            val end = json.lastIndexOf('}')
            if (start >= 0 && end > start) {
                return json.substring(start, end + 1)
            }
        }
        return "{}"
    }
}
