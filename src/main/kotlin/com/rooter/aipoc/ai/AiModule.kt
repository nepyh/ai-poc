package com.rooter.aipoc.ai

import com.rooter.aipoc.common.config.AppConfig
import org.koin.dsl.module

fun AiModule(appConfig: AppConfig) = module {
    single<AiClient> { GeminiAiClient(appConfig) }
}
