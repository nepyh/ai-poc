// 최종 위치: src/main/kotlin/study/backend_start/ai/PoCMain.kt
// 실행: build.gradle 의 mainClass = 'study.backend_start.ai.PoCMainKt'
package study.backend_start.ai

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import study.backend_start.ai.provider.env
import study.backend_start.ai.provider.getProvider

// ── 입력값 (나중에 API 인자로 받을 예정) ──────────────────────
private val INPUT = StudyInput(
    subject = "운영체제",
    daysRemaining = 5,
    scope = listOf("프로세스 관리", "메모리 관리", "파일 시스템", "동기화 문제"),
)
// ─────────────────────────────────────────────────────────────

private data class StudyInput(
    val subject: String,
    val daysRemaining: Int,
    val scope: List<String>,
)

private fun buildPrompt(input: StudyInput): String = """
당신은 학습 계획 전문가입니다.
다음 정보를 바탕으로 ${input.daysRemaining}일 공부 계획을 만들어주세요.

과목: ${input.subject}
남은 기간: ${input.daysRemaining}일
시험 범위: ${input.scope.joinToString(", ")}

반드시 아래 JSON 형식으로만 응답하세요. 다른 텍스트는 포함하지 마세요.
반드시 모든 응답은 한국어로 작성하세요.
중국어, 일본어, 영어를 사용하지 마세요.

{
  "subject": "과목명",
  "total_days": 숫자,
  "daily_plans": [
    {
      "day": 1,
      "date_offset": "D-5",
      "topics": ["주제1", "주제2"],
      "goal": "오늘의 목표 한 줄",
      "estimated_hours": 숫자
    }
  ],
  "tips": ["팁1", "팁2"]
}
""".trimIndent()

fun main() = runBlocking {
    val providerName = env["PROVIDER"] ?: "openrouter"
    println("[PoC] provider=$providerName 로 호출합니다...\n")

    // LLM 응답이 느릴 수 있어 타임아웃을 넉넉히 (HttpTimeout 은 ktor-client-core 에 포함)
    val client = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            socketTimeoutMillis = 60_000
        }
    }

    try {
        val provider = getProvider(providerName, client)
        val raw = provider.generate(buildPrompt(INPUT))

        val plan = parseJson(raw)
        if (plan == null) {
            println("[오류] JSON을 찾지 못했습니다. 원본 응답:")
            println(raw)
            return@runBlocking
        }

        val pretty = Json { prettyPrint = true }
        println(pretty.encodeToString(JsonElement.serializer(), plan))
    } catch (e: Exception) {
        println("[오류] ${e.message}")
    } finally {
        client.close()
    }
}

/** 모델이 ```json ... ``` 로 감싸 줄 때 대응 (main.py 의 regex fallback) */
private fun parseJson(raw: String): JsonElement? {
    return try {
        Json.parseToJsonElement(raw)
    } catch (_: Exception) {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start == -1 || end == -1 || end < start) return null
        try {
            Json.parseToJsonElement(raw.substring(start, end + 1))
        } catch (_: Exception) {
            null
        }
    }
}