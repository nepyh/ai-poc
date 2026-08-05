// 최종 위치: src/main/kotlin/study/backend_start/ai/provider/OpenRouterProvider.kt
package study.backend_start.ai.provider

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable

class OpenRouterProvider(private val client: HttpClient) : LlmProvider {

    private val apiKey = env["OPENROUTER_API_KEY"]
        ?: throw IllegalStateException("OPENROUTER_API_KEY가 .env에 없습니다")
    private val model = env["OPENROUTER_MODEL"] ?: "openrouter/free"

    // 무료 모델은 upstream 이 자주 붐벼서 429 가 뜸 → 몇 번 자동 재시도
    private val maxRetries = 4
    private val retryDelayMillis = 6000L

    override suspend fun generate(prompt: String): String {
        val body = Request(
            model = model,
            messages = listOf(Message(role = "user", content = prompt)),
        )

        repeat(maxRetries) { attempt ->
            val response = client.post("https://openrouter.ai/api/v1/chat/completions") {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(Request.serializer(), body))
            }

            val text = response.bodyAsText()

            // rate-limit → 잠시 대기 후 재시도
            if (response.status == HttpStatusCode.TooManyRequests) {
                if (attempt < maxRetries - 1) {
                    println("[재시도] rate-limit(429). ${attempt + 1}/$maxRetries 회, ${retryDelayMillis / 1000}초 대기...")
                    delay(retryDelayMillis)
                    return@repeat
                }
                error("OpenRouter rate-limit(429)으로 $maxRetries 회 재시도 실패. 잠시 후 다시 실행하거나 BYOK 키를 등록하세요.\n$text")
            }

            // 그 외 상태는 파싱 시도
            val parsed = json.decodeFromString(Response.serializer(), text)
            return parsed.choices.firstOrNull()?.message?.content
                ?: error("OpenRouter 응답에 content가 없습니다: $text")
        }

        error("OpenRouter 호출 실패 (재시도 소진)")
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