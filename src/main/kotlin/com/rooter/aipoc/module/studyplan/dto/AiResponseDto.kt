package com.rooter.aipoc.module.studyplan.dto

import kotlinx.serialization.Serializable

/** AI가 돌려주는 JSON을 그대로 역직렬화하기 위한 wire 전용 DTO들. */

@Serializable
data class AiTask(
    val task_name: String,
    val estimated_minutes: Int
)

@Serializable
data class AiDailyPlan(
    val day: Int,
    val date_offset: String,
    val topics: List<String>,
    val goal: String,
    val estimated_minutes: Int,
    val tasks: List<AiTask> = emptyList()
)

@Serializable
data class AiPlanGenerationResult(
    val subject: String,
    val total_days: Int,
    val is_cram_mode: Boolean = false,
    val daily_plans: List<AiDailyPlan>,
    val tips: List<String> = emptyList()
)

@Serializable
data class AiDocumentPlanGenerationResult(
    val subject: String,
    val total_days: Int,
    val is_cram_mode: Boolean = false,
    val extracted_scope: List<String> = emptyList(),
    val daily_plans: List<AiDailyPlan>,
    val tips: List<String> = emptyList()
)

@Serializable
data class AiQuizQuestion(
    val topic: String,
    val question_text: String,
    val choices: List<String>,
    val correct_index: Int,
    val explanation: String
)

@Serializable
data class AiQuizGenerationResult(
    val questions: List<AiQuizQuestion>
)

@Serializable
data class AiReplanResult(
    val daily_plans: List<AiDailyPlan>,
    val coaching_message: String
)

@Serializable
data class AiLevelTestQuestion(
    val subject: String,
    val question_text: String,
    val choices: List<String>,
    val correct_index: Int,
    val explanation: String
)

@Serializable
data class AiLevelTestGenerationResult(
    val questions: List<AiLevelTestQuestion>
)
