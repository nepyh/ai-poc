package com.rooter.aipoc.module.studyplan

import com.rooter.aipoc.module.studyplan.api.StudyPlanApi
import org.koin.core.qualifier.named
import org.koin.dsl.module

fun StudyPlanModule() = module {
    single { StudyPlanStore() }
    single { StudyPlanService(get(), get()) }
    single(named("studyPlanApi")) { StudyPlanApi(get()) }
}
