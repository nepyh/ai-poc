package com.rooter.aipoc.ai

interface AiClient {
    suspend fun complete(prompt: String): String
}

class MissingApiKeyException :
    IllegalStateException("GEMINI_API_KEY가 설정되지 않았습니다. .env 파일에 GEMINI_API_KEY를 채워주세요.")
