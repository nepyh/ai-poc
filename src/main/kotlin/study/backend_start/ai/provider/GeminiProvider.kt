// 최종 위치: src/main/kotlin/study/backend_start/ai/provider/GeminiProvider.kt
package study.backend_start.ai.provider

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

class GeminiProvider(private val client: HttpClient) : LlmProvider {

    private val apiKey = env["GEMINI_API_KEY"]
        ?: throw IllegalStateException("GEMINI_API_KEY가 .env에 없습니다")
    private val model = env["GEMINI_MODEL"] ?: "gemini-1.5-flash"

    override suspend fun generate(prompt: String): String {
        val body = Request(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
        )

        val response = client.post(
            "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
        ) {
            header("x-goog-api-key", apiKey)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(Request.serializer(), body))
        }

        val text = response.bodyAsText()
        println("[DEBUG] Gemini 원본 응답: $text")   // ← 이 줄 추가
        val parsed = json.decodeFromString(Response.serializer(), text)
        return parsed.candidates.firstOrNull()
            ?.content?.parts?.firstOrNull()?.text
            ?: error("Gemini 응답에 text가 없습니다: $text")
    }

    // 요청/응답 모두 contents/parts 구조를 공유
    @Serializable private data class Request(val contents: List<Content>)
    @Serializable private data class Content(val parts: List<Part>)
    @Serializable private data class Part(val text: String)

    @Serializable
    private data class Response(val candidates: List<Candidate>) {
        @Serializable
        data class Candidate(val content: Content)
    }
}