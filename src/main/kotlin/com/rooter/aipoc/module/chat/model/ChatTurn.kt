package com.rooter.aipoc.module.chat.model

import java.time.Instant

data class ChatTurn(
    val role: String, // "user" | "assistant"
    val content: String,
    val timestamp: Instant = Instant.now()
)
