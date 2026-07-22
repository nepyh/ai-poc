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
    cramming = false,
)
// ─────────────────────────────────────────────────────────────

private data class StudyInput(
    val subject: String,
    val daysRemaining: Int,
    val scope: List<String>,
    val cramming: Boolean = false,
)

private fun buildPrompt(input: StudyInput): String = """
당신은 학생의 시험 대비를 돕는 학습 계획 전문가입니다.
아래 정보를 바탕으로 ${input.daysRemaining}일간의 공부 계획을 세워주세요.

[입력 정보]
- 과목: ${input.subject}
- 남은 기간: ${input.daysRemaining}일
- 시험 범위: ${input.scope.joinToString(", ")}

[계획 수립 규칙]
1. 시험 범위의 모든 주제를 빠짐없이 배치하세요.
2. 어렵거나 분량이 많은 주제에는 더 많은 시간을 배분하세요.
3. 마지막 날(D-1)은 새로운 내용 학습 대신 전체 복습과 총정리로 구성하세요.
4. 기간이 충분하면 중간에 앞서 배운 내용을 복습하는 날을 넣으세요.
5. 하루 학습 시간은 4~8시간 사이로 현실적으로 배분하세요.
6. 각 날의 목표는 학생이 무엇을 끝내야 하는지 구체적으로 적으세요.

[출력 형식]
- 반드시 아래 JSON "하나만" 출력하세요.
- JSON 앞뒤로 설명, 인사말, 마크다운 코드블록 등 어떤 텍스트도 붙이지 마세요.
- 모든 문자열 값은 반드시 한국어로만 작성하세요. 영어/중국어/일본어를 섞지 마세요.

{
  "subject": "${input.subject}",
  "total_days": ${input.daysRemaining},
  "daily_plans": [
    {
      "day": 1,
      "date_offset": "D-${input.daysRemaining}",
      "topics": ["오늘 다룰 주제1", "주제2"],
      "goal": "오늘 반드시 끝내야 할 목표를 구체적으로 한 줄",
      "estimated_hours": 6,
      "is_review_day": false
    }
  ],
  "tips": ["이 과목 공부에 도움되는 팁1", "팁2"]
}
""".trimIndent()

private fun buildCrammingPrompt(input: StudyInput): String = """
당신은 시험이 임박한 학생을 돕는 벼락치기 전문가입니다.
남은 기간이 매우 짧습니다. 아래 정보를 바탕으로 ${input.daysRemaining}일간의 "벼락치기" 계획을 세워주세요.

[입력 정보]
- 과목: ${input.subject}
- 남은 기간: ${input.daysRemaining}일 (촉박함)
- 시험 범위: ${input.scope.joinToString(", ")}

[벼락치기 규칙]
1. 모든 주제를 똑같이 다루지 마세요. 시험에 자주 나오고 배점이 큰 "핵심 주제"를 우선 배치하세요.
2. 지엽적이거나 출제 빈도가 낮은 내용은 과감히 뒤로 미루거나 요약만 하세요.
3. 각 주제마다 "왜 중요한지(우선순위 이유)"를 한 줄로 밝히세요.
4. 이해보다 암기·문제풀이 위주로 짧고 강하게 반복하세요.
5. 하루 학습 시간은 최대치에 가깝게(6~9시간) 배분하되, 번아웃을 막을 짧은 휴식을 포함하세요.
6. 마지막 날은 가장 자주 틀리는 부분과 핵심 요약만 훑도록 하세요.

[출력 형식]
- 반드시 아래 JSON "하나만" 출력하세요.
- JSON 앞뒤로 설명, 인사말, 마크다운 코드블록 등 어떤 텍스트도 붙이지 마세요.
- 모든 문자열 값은 반드시 한국어로만 작성하세요. 영어/중국어/일본어를 섞지 마세요.

{
  "subject": "${input.subject}",
  "total_days": ${input.daysRemaining},
  "mode": "cramming",
  "daily_plans": [
    {
      "day": 1,
      "date_offset": "D-${input.daysRemaining}",
      "topics": ["최우선 핵심 주제1", "주제2"],
      "priority_reason": "이 주제를 오늘 먼저 하는 이유 한 줄",
      "goal": "오늘 반드시 끝내야 할 목표",
      "estimated_hours": 8
    }
  ],
  "tips": ["벼락치기 상황에 특화된 팁1", "팁2"]
}
""".trimIndent()

fun main() = runBlocking {
    val providerName = env["PROVIDER"] ?: "openrouter"
    val prompt = if (INPUT.cramming) buildCrammingPrompt(INPUT) else buildPrompt(INPUT)
    val modeLabel = if (INPUT.cramming) "벼락치기" else "일반"
    println("[PoC] provider=$providerName, mode=$modeLabel 로 호출합니다...\n")

    // LLM 응답이 느릴 수 있어 타임아웃을 넉넉히 (HttpTimeout 은 ktor-client-core 에 포함)
    val client = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            socketTimeoutMillis = 60_000
        }
    }

    try {
        val provider = getProvider(providerName, client)
        val raw = provider.generate(prompt)

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