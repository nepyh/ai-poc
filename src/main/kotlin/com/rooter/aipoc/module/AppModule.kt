package com.rooter.aipoc.module

import com.rooter.aipoc.ai.AiModule
import com.rooter.aipoc.ai.GeminiApiException
import com.rooter.aipoc.ai.MissingApiKeyException
import com.rooter.aipoc.common.ApiRoute
import com.rooter.aipoc.common.ErrorResponse
import com.rooter.aipoc.common.config.AppConfig
import com.rooter.aipoc.document.DocumentParseException
import com.rooter.aipoc.document.UnsupportedDocumentTypeException
import com.rooter.aipoc.module.chat.ChatModule
import com.rooter.aipoc.module.chat.exception.ChatValidationException
import com.rooter.aipoc.module.health.HealthModule
import com.rooter.aipoc.module.studyplan.StudyPlanModule
import com.rooter.aipoc.module.studyplan.exception.DailyPlanNotFoundException
import com.rooter.aipoc.module.studyplan.exception.PlanBoardNotFoundException
import com.rooter.aipoc.module.studyplan.exception.StudyPlanValidationException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.ktor.ext.inject

fun AppModule(appConfig: AppConfig): Module = module {
    includes(
        AiModule(appConfig),
        HealthModule(),
        StudyPlanModule(),
        ChatModule()
    )

    single<List<ApiRoute>> { getAll() }
}

fun Application.configureAppModule() {
    val apiRoutes: List<ApiRoute> by inject()
    routing {
        route("api") {
            apiRoutes.forEach { apiRoute ->
                with(apiRoute) { configureRoute() }
            }
        }
    }

    install(StatusPages) {
        exception<PlanBoardNotFoundException> { call, cause ->
            call.respondError(HttpStatusCode.NotFound, "PLAN_BOARD_NOT_FOUND", cause.message)
        }
        exception<DailyPlanNotFoundException> { call, cause ->
            call.respondError(HttpStatusCode.NotFound, "DAILY_PLAN_NOT_FOUND", cause.message)
        }
        exception<StudyPlanValidationException> { call, cause ->
            call.respondError(cause.status, cause.code, cause.message)
        }
        exception<ChatValidationException> { call, cause ->
            call.respondError(cause.status, cause.code, cause.message)
        }
        exception<MissingApiKeyException> { call, cause ->
            call.respondError(HttpStatusCode.BadRequest, "AI_KEY_NOT_CONFIGURED", cause.message)
        }
        exception<GeminiApiException> { call, cause ->
            call.respondError(HttpStatusCode.BadGateway, "AI_PROVIDER_ERROR", cause.message)
        }
        exception<UnsupportedDocumentTypeException> { call, cause ->
            call.respondError(HttpStatusCode.BadRequest, "UNSUPPORTED_DOCUMENT_TYPE", cause.message)
        }
        exception<DocumentParseException> { call, cause ->
            call.respondError(HttpStatusCode.BadRequest, "DOCUMENT_PARSE_FAILED", cause.message)
        }
        exception<BadRequestException> { call, _ ->
            call.respondError(HttpStatusCode.BadRequest, "INVALID_REQUEST_BODY", "요청 형식이 올바르지 않습니다.")
        }
        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled exception", cause)
            call.respondError(
                HttpStatusCode.InternalServerError,
                "INTERNAL_SERVER_ERROR",
                "서버 오류가 발생했습니다: ${cause.message}"
            )
        }
    }
}

private suspend fun ApplicationCall.respondError(status: HttpStatusCode, code: String, message: String?) {
    respond(status, ErrorResponse(code, message ?: "알 수 없는 오류입니다."))
}
