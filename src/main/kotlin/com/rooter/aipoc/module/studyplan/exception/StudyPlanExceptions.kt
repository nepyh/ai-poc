package com.rooter.aipoc.module.studyplan.exception

import io.ktor.http.HttpStatusCode

class PlanBoardNotFoundException(message: String) : RuntimeException(message)

class DailyPlanNotFoundException(message: String) : RuntimeException(message)

class StudyPlanValidationException(
    val status: HttpStatusCode,
    val code: String,
    override val message: String
) : RuntimeException(message)
