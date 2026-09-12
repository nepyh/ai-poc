package com.rooter.aipoc.common.config

import io.ktor.server.config.ApplicationConfig

data class AppConfig(
    val geminiApiKey: String?,
    val geminiModelReasoning: String,
    val geminiModelFast: String
) {
    companion object {
        fun fromApplicationConfig(config: ApplicationConfig): AppConfig {
            return AppConfig(
                geminiApiKey = config.propertyOrNull("ai.geminiApiKey")?.getString()
                    ?.takeIf { it.isNotBlank() },
                geminiModelReasoning = config.propertyOrNull("ai.modelReasoning")?.getString()
                    ?: "gemini-3.1-flash-lite",
                geminiModelFast = config.propertyOrNull("ai.modelFast")?.getString()
                    ?: "gemini-3.1-flash-lite"
            )
        }
    }
}
