package com.rooter.aipoc.module.chat

import com.rooter.aipoc.ai.AiClient
import com.rooter.aipoc.ai.AiJsonFormat
import com.rooter.aipoc.ai.PromptTemplates
import com.rooter.aipoc.ai.extractJsonObject
import com.rooter.aipoc.module.chat.dto.AiChatResult
import com.rooter.aipoc.module.chat.dto.ChatMessageResponse
import com.rooter.aipoc.module.chat.dto.ChatTurnResponse
import com.rooter.aipoc.module.chat.exception.ChatValidationException
import com.rooter.aipoc.module.chat.model.ChatTurn
import com.rooter.aipoc.module.studyplan.StudyPlanService
import com.rooter.aipoc.module.studyplan.StudyPlanStore
import com.rooter.aipoc.module.studyplan.dto.AiTask
import com.rooter.aipoc.module.studyplan.dto.DailyPlanResponse
import com.rooter.aipoc.module.studyplan.exception.DailyPlanNotFoundException
import com.rooter.aipoc.module.studyplan.exception.PlanBoardNotFoundException
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.decodeFromString
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** 한 번의 챗봇 호출에 함께 실어 보내는 최근 대화 턴 수. 너무 길면 프롬프트가 불필요하게 커진다. */
private const val HISTORY_LIMIT = 10

class ChatService(
    private val chatStore: ChatStore,
    private val studyPlanStore: StudyPlanStore,
    private val studyPlanService: StudyPlanService,
    private val aiClient: AiClient
) {

    suspend fun sendMessage(boardId: Int, day: Int, message: String): ChatMessageResponse {
        val trimmed = message.trim()
        if (trimmed.isBlank()) {
            throw ChatValidationException(HttpStatusCode.BadRequest, "MESSAGE_REQUIRED", "메시지를 입력해주세요.")
        }

        val board = studyPlanStore.get(boardId)
            ?: throw PlanBoardNotFoundException("plan board를 찾을 수 없습니다: $boardId")
        val dailyPlan = board.dailyPlans.find { it.day == day }
            ?: throw DailyPlanNotFoundException("${day}일차 계획을 찾을 수 없습니다.")

        val date = LocalDate.parse(dailyPlan.date)
        val dayOfWeekLabel = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN)
        val history = chatStore.history(boardId).takeLast(HISTORY_LIMIT)

        val prompt = PromptTemplates.chatPlanAdjustment(
            subject = board.subject,
            grade = board.grade,
            studyStyle = PromptTemplates.StudyStyleInput(
                focusSessionStyle = board.studyStyle.focusSessionStyle,
                subjectDistribution = board.studyStyle.subjectDistribution,
                planIntensity = board.studyStyle.planIntensity,
                volatileTimeSlot = board.studyStyle.volatileTimeSlot,
                dayBeforeExamStyle = board.studyStyle.dayBeforeExamStyle,
                selfEstimationAccuracy = board.studyStyle.selfEstimationAccuracy,
                scoreGapCause = board.studyStyle.scoreGapCause
            ),
            levelTier = board.levelTier ?: "중",
            targetDate = "${dailyPlan.date} ($dayOfWeekLabel)",
            currentDailyPlan = PromptTemplates.ChatDailyPlanInput(
                topics = dailyPlan.topics,
                goal = dailyPlan.goal,
                estimatedMinutes = dailyPlan.estimatedMinutes,
                tasks = dailyPlan.tasks.map { PromptTemplates.ChatTaskInput(it.taskName, it.estimatedMinutes) }
            ),
            chatHistory = history.map { PromptTemplates.ChatTurnInput(it.role, it.content) },
            userMessage = trimmed
        )

        val raw = aiClient.complete(prompt)
        val parsed = AiJsonFormat.decodeFromString<AiChatResult>(extractJsonObject(raw))

        chatStore.append(boardId, ChatTurn(role = "user", content = trimmed))

        var updatedDailyPlan: DailyPlanResponse? = null
        val update = parsed.plan_update
        if (parsed.plan_changed && update != null) {
            updatedDailyPlan = studyPlanService.applyChatPlanUpdate(
                boardId = boardId,
                day = day,
                topics = update.topics,
                goal = update.goal,
                estimatedMinutes = update.estimated_minutes,
                tasks = update.tasks.map { AiTask(it.task_name, it.estimated_minutes) },
                busyWindowStart = update.busy_window_start,
                busyWindowEnd = update.busy_window_end
            )
        }

        chatStore.append(boardId, ChatTurn(role = "assistant", content = parsed.reply_message))

        return ChatMessageResponse(
            reply = parsed.reply_message,
            planChanged = updatedDailyPlan != null,
            updatedDailyPlan = updatedDailyPlan
        )
    }

    fun getHistory(boardId: Int): List<ChatTurnResponse> {
        if (studyPlanStore.get(boardId) == null) {
            throw PlanBoardNotFoundException("plan board를 찾을 수 없습니다: $boardId")
        }
        return chatStore.history(boardId).map { ChatTurnResponse(it.role, it.content, it.timestamp.toString()) }
    }
}
