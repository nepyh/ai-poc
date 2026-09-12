package com.rooter.aipoc.ai

import kotlinx.serialization.json.Json

val AiJsonFormat = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
}

/**
 * 모델이 마크다운 코드펜스로 감싸서 응답하는 경우까지 대응해 JSON 객체 구간만 추출한다.
 */
fun extractJsonObject(raw: String): String {
    var text = raw.trim()
    if (text.startsWith("```")) {
        text = text.removePrefix("```json").removePrefix("```").trim()
    }
    if (text.endsWith("```")) {
        text = text.removeSuffix("```").trim()
    }

    val start = text.indexOf('{')
    val end = text.lastIndexOf('}')
    if (start == -1 || end == -1 || end < start) {
        throw IllegalStateException("AI 응답에서 JSON 객체를 찾지 못했습니다. 원본 응답: $raw")
    }
    return text.substring(start, end + 1)
}
