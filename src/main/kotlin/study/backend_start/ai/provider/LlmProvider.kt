// 최종 위치: src/main/kotlin/study/backend_start/ai/provider/LlmProvider.kt
package study.backend_start.ai.provider

import io.github.cdimascio.dotenv.dotenv
import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json

/**
 * .env 로더 (main.py 의 load_dotenv 대응)
 * 네 .env 는 현재 ai-poc/ 안에 있어서 directory 를 지정함.
 * .env 를 프로젝트 루트로 옮기면 directory 줄을 지우면 됨.
 */
val env = dotenv {
    directory = "./ai-poc"
    ignoreIfMissing = true   // .env 없으면 시스템 환경변수만 사용
}

/** API 응답엔 우리가 안 쓰는 필드가 많으므로 ignoreUnknownKeys 필수 */
val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/** 모든 provider 가 구현하는 인터페이스 (providers.py 의 BaseProvider 대응) */
interface LlmProvider {
    suspend fun generate(prompt: String): String
}

/**
 * 레지스트리 (providers.py 의 get_provider 대응)
 * 새 provider 추가법: 클래스 작성 후 아래 when 에 한 줄 등록 → PoCMain 은 안 건드려도 됨
 */
fun getProvider(name: String, client: HttpClient): LlmProvider =
    when (name.lowercase()) {
        "openrouter" -> OpenRouterProvider(client)
        "gemini" -> GeminiProvider(client)
        "claude" -> ClaudeProvider(client)
        "deepseek" -> DeepSeekProvider(client)
        else -> throw IllegalArgumentException(
            "알 수 없는 provider: '$name'. 가능한 값: [openrouter, gemini, claude]"
        )
    }