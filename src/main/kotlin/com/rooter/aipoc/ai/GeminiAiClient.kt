package com.rooter.aipoc.ai

import com.rooter.aipoc.common.config.AppConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString

@Serializable
data class GeminiPart(val text: String)

@Serializable
data class GeminiContent(val parts: List<GeminiPart>, val role: String? = null)

@Serializable
data class GeminiRequest(val contents: List<GeminiContent>)

@Serializable
data class GeminiCandidate(val content: GeminiContent? = null)

@Serializable
data class GeminiResponse(val candidates: List<GeminiCandidate> = emptyList())

@Serializable
data class GeminiErrorDetail(val code: Int? = null, val message: String? = null, val status: String? = null)

@Serializable
data class GeminiErrorEnvelope(val error: GeminiErrorDetail? = null)

class GeminiApiException(status: HttpStatusCode, errorStatus: String?, message: String?) :
    RuntimeException("Gemini API 오류 (HTTP ${status.value}${errorStatus?.let { ", $it" } ?: ""}): ${message ?: "알 수 없는 오류"}")

/**
 * geminiModelFast는 향후 프롬프트별(빠른 작업 vs 복잡한 설계) 모델 라우팅을 위해 설정만 받아두고,
 * 지금은 아직 라우팅 로직이 없어 모든 요청에 geminiModelReasoning 하나만 사용한다.
 */
class GeminiAiClient(private val appConfig: AppConfig) : AiClient {

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(AiJsonFormat)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
        }
    }

    override suspend fun complete(prompt: String): String {
        val apiKey = appConfig.geminiApiKey ?: throw MissingApiKeyException()
        val model = appConfig.geminiModelReasoning

        val httpResponse: HttpResponse = client.post(
            "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
        ) {
            header("x-goog-api-key", apiKey)
            contentType(ContentType.Application.Json)
            setBody(
                GeminiRequest(
                    contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt))))
                )
            )
        }

        if (!httpResponse.status.isSuccess()) {
            val rawBody = httpResponse.bodyAsText()
            val errorDetail = runCatching { AiJsonFormat.decodeFromString<GeminiErrorEnvelope>(rawBody).error }
                .getOrNull()
            throw GeminiApiException(httpResponse.status, errorDetail?.status, errorDetail?.message ?: rawBody)
        }

        val response: GeminiResponse = httpResponse.body()

        return response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw IllegalStateException("Gemini 응답에서 텍스트 콘텐츠를 찾지 못했습니다.")
    }
}
