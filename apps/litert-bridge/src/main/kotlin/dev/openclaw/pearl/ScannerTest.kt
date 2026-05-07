import dev.openclaw.pearl.*

fun main() {
    val scanner = ToolCallScanner()
    val chunks = listOf(
        "Hello! ",
        "I will help. <tool",
        "_call>{\"tool_name\": \"read",
        "_file\", \"parameters\": {\"path\": \"test.txt\"}}</tool",
        "_call> Done."
    )
    
    println("=== Testing ToolCallScanner ===")
    chunks.forEach { chunk ->
        println("Pushing chunk: '$chunk'")
        val results = scanner.push(chunk)
        results.forEach { result ->
            println("  Result: $result")
        }
    }
}
