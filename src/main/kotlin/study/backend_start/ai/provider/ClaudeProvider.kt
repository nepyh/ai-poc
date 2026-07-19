// 최종 위치: src/main/kotlin/study/backend_start/ai/provider/ClaudeProvider.kt
package study.backend_start.ai.provider

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class ClaudeProvider(private val client: HttpClient) : LlmProvider {

    private val apiKey = env["ANTHROPIC_API_KEY"]
        ?: throw IllegalStateException("ANTHROPIC_API_KEY가 .env에 없습니다")
    private val model = env["CLAUDE_MODEL"] ?: "claude-haiku-4-5-20251001"

    override suspend fun generate(prompt: String): String {
        val body = Request(
            model = model,
            maxTokens = 1024,
            messages = listOf(Message(role = "user", content = prompt)),
        )

        val response = client.post("https://api.anthropic.com/v1/messages") {
            header("x-api-key", apiKey)
            header("anthropic-version", "2023-06-01")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(Request.serializer(), body))
        }

        val text = response.bodyAsText()
        val parsed = json.decodeFromString(Response.serializer(), text)
        return parsed.content.firstOrNull { it.type == "text" }?.text
            ?: error("Claude 응답에 text가 없습니다: $text")
    }

    @Serializable
    private data class Request(
        val model: String,
        @SerialName("max_tokens") val maxTokens: Int,
        val messages: List<Message>,
    )

    @Serializable
    private data class Message(val role: String, val content: String)

    @Serializable
    private data class Response(val content: List<Block>) {
        @Serializable
        data class Block(val type: String, val text: String? = null)
    }
}