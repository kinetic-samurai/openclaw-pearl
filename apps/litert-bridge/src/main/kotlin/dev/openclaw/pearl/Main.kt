package dev.openclaw.pearl

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.request.*
import io.ktor.server.response.respond
import io.ktor.server.response.respondTextWriter
import io.ktor.server.response.*
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json

fun main() {
    val host = System.getenv("BRIDGE_HOST") ?: "127.0.0.1"
    val port = System.getenv("BRIDGE_PORT")?.toIntOrNull() ?: 8765
    val runner = TurboLiteRTPearlRunner()

    embeddedServer(Netty, port = port, host = host) {
        pearlModule(runner)
    }.start(wait = true)
}

fun Application.pearlModule(runner: PearlRunner) {
    val jsonConfig = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
        isLenient = true
    }

    install(ContentNegotiation) {
        json(jsonConfig)
    }

    routing {
        get("/health") {
            call.respond(mapOf("status" to "ok", "service" to "openclaw-pearl"))
        }

        get("/v1/models") {
            val model = System.getenv("OPENCLAW_PEARL_MODEL") ?: "gemma-4-e2b-it"
            call.respond(ModelsResponse(data = listOf(ModelInfo(id = model))))
        }

        post("/v1/chat/completions") {
            val rawBody = call.receiveText()
            val request = try {
                jsonConfig.decodeFromString(ChatCompletionRequest.serializer(), rawBody)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Deserialization failed: ${e.message}")
                return@post
            }

            if (request.stream) {
                call.respondTextWriter(contentType = io.ktor.http.ContentType.Text.EventStream) {
                    runner.completeStreaming(request) { chunkJson ->
                        write("data: $chunkJson\n\n")
                        flush()
                    }
                    write("data: [DONE]\n\n")
                    flush()
                }
                return@post
            }

            val result = runner.complete(request)
            call.respond(result)
        }
    }
}
