package com.rooter.aipoc.module.chat.exception

import io.ktor.http.HttpStatusCode

class ChatValidationException(
    val status: HttpStatusCode,
    val code: String,
    override val message: String
) : RuntimeException(message)
