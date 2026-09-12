package com.rooter.aipoc.module.studyplan.api

import com.rooter.aipoc.ai.AiJsonFormat
import com.rooter.aipoc.common.ApiRoute
import com.rooter.aipoc.module.studyplan.StudyPlanService
import com.rooter.aipoc.module.studyplan.dto.*
import com.rooter.aipoc.module.studyplan.exception.StudyPlanValidationException
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.*
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.receiveNullable
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import io.ktor.utils.io.toByteArray
import kotlinx.serialization.decodeFromString

private const val MAX_UPLOAD_BYTES = 20 * 1024 * 1024

private fun ApplicationCall.intParam(name: String): Int =
    parameters[name]?.toIntOrNull()
        ?: throw StudyPlanValidationException(HttpStatusCode.BadRequest, "INVALID_PATH_PARAM", "$name 값이 올바르지 않습니다.")

fun StudyPlanApi(service: StudyPlanService) = ApiRoute("study-plan") {
    post("/generate") {
        val request = call.receive<GeneratePlanRequest>()
        call.respond(HttpStatusCode.Created, service.generatePlan(request))
    }

    post("/generate-from-document") {
        var fileBytes: ByteArray? = null
        var fileName: String? = null
        var subject: String? = null
        var grade = 2
        var daysRemaining: Int? = 30
        var targetScore: Int? = null
        var isCramMode = false
        var focusSessionStyle: String? = null
        var subjectDistribution: String? = null
        var planIntensity: String? = null
        var volatileTimeSlot: String? = null
        var dayBeforeExamStyle: String? = null
        var selfEstimationAccuracy: String? = null
        var scoreGapCause: String? = null
        var startDate: String? = null
        var examDate: String? = null
        var unavailableSlots: List<UnavailableSlotInput> = emptyList()
        var levelTier: String? = null

        call.receiveMultipart().forEachPart { part ->
            when (part) {
                is PartData.FormItem -> when (part.name) {
                    "subject" -> subject = part.value.trim().ifBlank { null }
                    "grade" -> part.value.toIntOrNull()?.let { grade = it }
                    "daysRemaining" -> part.value.toIntOrNull()?.let { daysRemaining = it }
                    "targetScore" -> targetScore = part.value.toIntOrNull()
                    "isCramMode" -> isCramMode = part.value.toBoolean()
                    "focusSessionStyle" -> focusSessionStyle = part.value.trim().ifBlank { null }
                    "subjectDistribution" -> subjectDistribution = part.value.trim().ifBlank { null }
                    "planIntensity" -> planIntensity = part.value.trim().ifBlank { null }
                    "volatileTimeSlot" -> volatileTimeSlot = part.value.trim().ifBlank { null }
                    "dayBeforeExamStyle" -> dayBeforeExamStyle = part.value.trim().ifBlank { null }
                    "selfEstimationAccuracy" -> selfEstimationAccuracy = part.value.trim().ifBlank { null }
                    "scoreGapCause" -> scoreGapCause = part.value.trim().ifBlank { null }
                    "startDate" -> startDate = part.value.trim().ifBlank { null }
                    "examDate" -> examDate = part.value.trim().ifBlank { null }
                    "levelTier" -> levelTier = part.value.trim().ifBlank { null }
                    "unavailableSlots" -> part.value.trim().ifBlank { null }?.let {
                        unavailableSlots = try {
                            AiJsonFormat.decodeFromString<List<UnavailableSlotInput>>(it)
                        } catch (e: Exception) {
                            throw StudyPlanValidationException(
                                HttpStatusCode.BadRequest, "INVALID_UNAVAILABLE_SLOT", "unavailableSlots JSON 형식이 올바르지 않습니다."
                            )
                        }
                    }
                }

                is PartData.FileItem -> {
                    val bytes = part.provider().toByteArray()
                    if (bytes.size > MAX_UPLOAD_BYTES) {
                        throw StudyPlanValidationException(
                            HttpStatusCode.PayloadTooLarge, "FILE_TOO_LARGE", "파일 용량은 20MB 이하여야 합니다."
                        )
                    }
                    fileBytes = bytes
                    fileName = part.originalFileName
                }

                else -> {}
            }
            part.dispose()
        }

        val bytes = fileBytes
            ?: throw StudyPlanValidationException(HttpStatusCode.BadRequest, "FILE_REQUIRED", "파일을 첨부해주세요.")
        val name = fileName
            ?: throw StudyPlanValidationException(HttpStatusCode.BadRequest, "FILE_REQUIRED", "파일 이름을 확인할 수 없습니다.")

        val studyStyle = StudyStylePreferences(
            focusSessionStyle = focusSessionStyle,
            subjectDistribution = subjectDistribution,
            planIntensity = planIntensity,
            volatileTimeSlot = volatileTimeSlot,
            dayBeforeExamStyle = dayBeforeExamStyle,
            selfEstimationAccuracy = selfEstimationAccuracy,
            scoreGapCause = scoreGapCause
        )
        val response = service.generatePlanFromDocument(
            bytes, name, subject, grade, daysRemaining, targetScore, isCramMode, studyStyle,
            startDate, examDate, unavailableSlots, levelTier
        )
        call.respond(HttpStatusCode.Created, response)
    }

    post("/level-test/generate") {
        val request = call.receive<LevelTestGenerateRequest>()
        call.respond(HttpStatusCode.Created, service.generateLevelTest(request))
    }

    post("/level-test/{testId}/submit") {
        val testId = call.intParam("testId")
        val request = call.receive<LevelTestSubmitRequest>()
        call.respond(service.submitLevelTest(testId, request))
    }

    get("/{boardId}") {
        val boardId = call.intParam("boardId")
        call.respond(service.getBoard(boardId))
    }

    get("/{boardId}/grass") {
        val boardId = call.intParam("boardId")
        call.respond(service.getGrass(boardId))
    }

    post("/{boardId}/daily/{day}/quiz/generate") {
        val boardId = call.intParam("boardId")
        val day = call.intParam("day")
        call.respond(HttpStatusCode.Created, service.generateQuiz(boardId, day))
    }

    post("/{boardId}/daily/{day}/quiz/submit") {
        val boardId = call.intParam("boardId")
        val day = call.intParam("day")
        val request = call.receive<QuizSubmitRequest>()
        call.respond(service.submitQuiz(boardId, day, request))
    }

    post("/{boardId}/daily/{day}/tasks/{taskId}/complete") {
        val boardId = call.intParam("boardId")
        val day = call.intParam("day")
        val taskId = call.intParam("taskId")
        val request = call.receiveNullable<TaskCompleteRequest>() ?: TaskCompleteRequest()
        call.respond(service.completeTask(boardId, day, taskId, request.isCompleted))
    }

    post("/{boardId}/daily/{day}/feedback") {
        val boardId = call.intParam("boardId")
        val day = call.intParam("day")
        val request = call.receive<FeedbackRequest>()
        call.respond(service.submitFeedback(boardId, day, request))
    }
}
