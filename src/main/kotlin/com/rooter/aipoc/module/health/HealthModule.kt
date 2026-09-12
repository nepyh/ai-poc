package com.rooter.aipoc.module.health

import com.rooter.aipoc.common.ApiRoute
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import org.koin.core.qualifier.named
import org.koin.dsl.module

fun HealthModule() = module {
    single(named("healthApi")) { HealthApi() }
}

fun HealthApi() = ApiRoute("health") {
    get {
        call.respondText("OK")
    }
}
