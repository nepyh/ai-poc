package com.rooter.aipoc.module.chat.dto

import com.rooter.aipoc.module.studyplan.dto.DailyPlanResponse
import kotlinx.serialization.Serializable

@Serializable
data class ChatMessageRequest(
    val message: String
)

@Serializable
data class ChatTurnResponse(
    val role: String,
    val content: String,
    val timestamp: String
)

@Serializable
data class ChatMessageResponse(
    val reply: String,
    val planChanged: Boolean,
    val updatedDailyPlan: DailyPlanResponse? = null
)
