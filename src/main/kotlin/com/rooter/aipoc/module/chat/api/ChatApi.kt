package com.rooter.aipoc.module.chat.api

import com.rooter.aipoc.common.ApiRoute
import com.rooter.aipoc.module.chat.ChatService
import com.rooter.aipoc.module.chat.dto.ChatMessageRequest
import com.rooter.aipoc.module.chat.exception.ChatValidationException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*

private fun ApplicationCall.intParam(name: String): Int =
    parameters[name]?.toIntOrNull()
        ?: throw ChatValidationException(HttpStatusCode.BadRequest, "INVALID_PATH_PARAM", "$name 값이 올바르지 않습니다.")

fun ChatApi(service: ChatService) = ApiRoute("chat") {
    post("/{boardId}/daily/{day}/message") {
        val boardId = call.intParam("boardId")
        val day = call.intParam("day")
        val request = call.receive<ChatMessageRequest>()
        call.respond(service.sendMessage(boardId, day, request.message))
    }

    get("/{boardId}/history") {
        val boardId = call.intParam("boardId")
        call.respond(service.getHistory(boardId))
    }
}
