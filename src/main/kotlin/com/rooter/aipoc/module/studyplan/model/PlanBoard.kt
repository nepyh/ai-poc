package com.rooter.aipoc.module.studyplan.model

import java.time.LocalDate

data class PlanBoard(
    val id: Int,
    val subject: String,
    val grade: Int,
    val daysRemaining: Int,
    val targetScore: Int?,
    val scope: List<String>,
    val isCramMode: Boolean,
    val tips: List<String>,
    val dailyPlans: MutableList<DailyPlan>,
    val extractedScope: List<String>? = null,
    val sourceFileName: String? = null,
    val studyStyle: StudyStyleSettings = StudyStyleSettings(),
    val startDate: LocalDate,
    val examDate: LocalDate? = null,
    val unavailableSlots: List<UnavailableSlot> = emptyList(),
    val levelTier: String? = null // "상" | "중" | "하" — 실력 테스트 결과, null이면 "중"으로 간주
)

/** 요일별(1=월 ~ 7=일, ISO-8601 DayOfWeek 기준) 공부 불가능 시간 구간 하나. */
data class UnavailableSlot(
    val dayOfWeek: Int,
    val start: String,
    val end: String
)

/** 계획 생성 전 온보딩 설문(7문항) 응답. 필드가 null이면 표준값으로 간주한다. */
data class StudyStyleSettings(
    val focusSessionStyle: String? = null,
    val subjectDistribution: String? = null,
    val planIntensity: String? = null,
    val volatileTimeSlot: String? = null,
    val dayBeforeExamStyle: String? = null,
    val selfEstimationAccuracy: String? = null,
    val scoreGapCause: String? = null
)

data class DailyPlan(
    val day: Int,
    var dateOffset: String,
    val date: String,
    var topics: MutableList<String>,
    var goal: String,
    var estimatedMinutes: Int,
    val tasks: MutableList<PlanTask> = mutableListOf(),
    var quiz: MutableList<QuizQuestion> = mutableListOf(),
    var quizResult: QuizResultSummary? = null,
    var feedback: DailyFeedbackEntry? = null
)

data class PlanTask(
    val id: Int,
    var taskName: String,
    var estimatedMinutes: Int,
    var isCompleted: Boolean = false,
    var startTime: String = "00:00",
    var endTime: String = "00:00"
)

data class QuizQuestion(
    val id: Int,
    val topic: String,
    val questionText: String,
    val choices: List<String>,
    val correctIndex: Int,
    val explanation: String
)

data class QuizResultSummary(
    val correctCount: Int,
    val totalCount: Int,
    val wrongTopics: List<String>
)

data class DailyFeedbackEntry(
    val difficulty: String,
    val timeSpentMinutes: Int?,
    val focusLevel: Int?
)

/** 실력 테스트(한 학년 아래 수준, 국어/영어/수학 통합 5문항 내외). 계획 생성과 독립적으로 존재한다. */
data class LevelTest(
    val id: Int,
    val grade: Int,
    val questions: List<LevelTestQuestion>,
    var result: LevelTestResult? = null
)

data class LevelTestQuestion(
    val id: Int,
    val subject: String,
    val questionText: String,
    val choices: List<String>,
    val correctIndex: Int,
    val explanation: String
)

data class LevelTestResult(
    val correctCount: Int,
    val totalCount: Int,
    val tier: String // "상" | "중" | "하"
)
