package com.rooter.aipoc.module.chat.dto

import kotlinx.serialization.Serializable

/** AI가 돌려주는 챗봇 응답 JSON을 그대로 역직렬화하기 위한 wire 전용 DTO들. */

@Serializable
data class AiChatTask(
    val task_name: String,
    val estimated_minutes: Int
)

@Serializable
data class AiChatPlanUpdate(
    val topics: List<String>,
    val goal: String,
    val estimated_minutes: Int,
    val tasks: List<AiChatTask> = emptyList(),
    val busy_window_start: String? = null,
    val busy_window_end: String? = null
)

@Serializable
data class AiChatResult(
    val reply_message: String,
    val plan_changed: Boolean = false,
    val plan_update: AiChatPlanUpdate? = null
)
