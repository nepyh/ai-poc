// 최종 위치: src/main/kotlin/study/backend_start/ai/provider/DeepSeekProvider.kt
package study.backend_start.ai.provider

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

class DeepSeekProvider(private val client: HttpClient) : LlmProvider {

    private val apiKey = env["DEEPSEEK_API_KEY"]
        ?: throw IllegalStateException("DEEPSEEK_API_KEY가 .env에 없습니다")
    private val model = env["DEEPSEEK_MODEL"] ?: "deepseek-chat"

    override suspend fun generate(prompt: String): String {
        val body = Request(
            model = model,
            messages = listOf(Message(role = "user", content = prompt)),
        )

        val response = client.post("https://api.deepseek.com/chat/completions") {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(Request.serializer(), body))
        }

        val text = response.bodyAsText()
        val parsed = json.decodeFromString(Response.serializer(), text)
        return parsed.choices.firstOrNull()?.message?.content
            ?: error("DeepSeek 응답에 content가 없습니다: $text")
    }

    @Serializable
    private data class Request(val model: String, val messages: List<Message>)

    @Serializable
    private data class Message(val role: String, val content: String)

    @Serializable
    private data class Response(val choices: List<Choice>) {
        @Serializable
        data class Choice(val message: Message)
    }
}