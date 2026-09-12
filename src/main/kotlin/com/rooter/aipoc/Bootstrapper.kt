package com.rooter.aipoc

import com.rooter.aipoc.common.config.AppConfig
import com.rooter.aipoc.module.AppModule
import com.rooter.aipoc.module.configureAppModule
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.http.content.staticResources
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.routing.routing
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import org.slf4j.event.Level

fun Application.appEntryModule() {
    val appConfig = AppConfig.fromApplicationConfig(environment.config)

    install(Koin) {
        slf4jLogger()
        modules(AppModule(appConfig))
    }

    install(CallLogging) {
        level = Level.INFO
    }

    install(ContentNegotiation) {
        json()
    }

    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.ContentType)
        anyHost()
    }

    routing {
        // PoC 프론트(정적 HTML)를 같은 오리진에서 서빙 -> CORS 걱정 없이 바로 확인 가능
        staticResources("/", "static")
    }

    configureAppModule()
}
