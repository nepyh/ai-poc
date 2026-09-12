package com.rooter.aipoc.ai

import java.util.concurrent.ConcurrentHashMap

/**
 * 프롬프트 원문을 classpath 리소스(src/main/resources/prompts 디렉터리의 md 파일)에서 읽어온다.
 * 프롬프트를 코드에서 분리해두면 재컴파일 없이(리소스 리로드 시) 문구를 다듬을 수 있고,
 * 마크다운 파일 자체로 diff/리뷰가 쉬워진다.
 */
object PromptLoader {
    private val cache = ConcurrentHashMap<String, String>()

    fun load(path: String): String = cache.getOrPut(path) {
        val stream = javaClass.classLoader.getResourceAsStream(path)
            ?: throw IllegalStateException("프롬프트 리소스를 찾을 수 없습니다: $path")
        stream.bufferedReader(Charsets.UTF_8).use { it.readText() }.trimEnd()
    }

    /** path의 템플릿에서 {{KEY}} 형태의 자리표시자를 replacements 값으로 치환한다. */
    fun render(path: String, vararg replacements: Pair<String, String>): String {
        var text = load(path)
        for ((key, value) in replacements) {
            text = text.replace("{{$key}}", value)
        }
        return text
    }
}
