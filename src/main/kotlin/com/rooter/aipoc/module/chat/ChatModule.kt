package com.rooter.aipoc.module.chat

import com.rooter.aipoc.module.chat.api.ChatApi
import org.koin.core.qualifier.named
import org.koin.dsl.module

fun ChatModule() = module {
    single { ChatStore() }
    single { ChatService(get(), get(), get(), get()) }
    single(named("chatApi")) { ChatApi(get()) }
}
