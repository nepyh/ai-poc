package com.rooter.aipoc.module.studyplan.dto

import kotlinx.serialization.Serializable

/** 계획 생성 전 온보딩 설문(7문항) 응답. 안 보낸 항목은 null로 두면 표준값으로 처리된다. */
@Serializable
data class StudyStylePreferences(
    val focusSessionStyle: String? = null, // "포모도로" | "표준" | "몰입형"
    val subjectDistribution: String? = null, // "한과목집중" | "두과목집중" | "여러과목전환"
    val planIntensity: String? = null, // "스파르타" | "밸런스" | "여유"
    val volatileTimeSlot: String? = null, // "방과후" | "저녁이후" | "주말"
    val dayBeforeExamStyle: String? = null, // "가볍게훑기" | "오답집중" | "진도끝까지"
    val selfEstimationAccuracy: String? = null, // "의욕과다형" | "정확예측형" | "과소평가형"
    val scoreGapCause: String? = null // "시간부족형" | "방법문제형" | "실수컨디션형"
)

/** 요일별(1=월 ~ 7=일, ISO-8601 DayOfWeek 기준) 공부 불가능 시간 구간 하나. */
@Serializable
data class UnavailableSlotInput(
    val dayOfWeek: Int,
    val start: String, // "HH:mm"
    val end: String // "HH:mm"
)

@Serializable
data class GeneratePlanRequest(
    val subject: String,
    val grade: Int,
    val daysRemaining: Int? = null, // startDate/examDate를 주면 서버가 자동 계산하므로 그때는 생략 가능
    val targetScore: Int? = null,
    val scope: List<String>,
    val isCramMode: Boolean = false,
    val studyStyle: StudyStylePreferences? = null,
    val startDate: String? = null, // "yyyy-MM-dd", 생략하면 오늘
    val examDate: String? = null, // "yyyy-MM-dd", 주어지면 daysRemaining을 여기서 자동 계산 (시험 당일은 공부일에서 제외)
    val unavailableSlots: List<UnavailableSlotInput> = emptyList(), // 비어 있으면 평일(월~금) 08:30~16:30 기본값
    val levelTier: String? = null // "상" | "중" | "하", 실력 테스트 결과. null이면 "중"으로 간주
)

@Serializable
data class PlanTaskResponse(
    val id: Int,
    val taskName: String,
    val estimatedMinutes: Int,
    val isCompleted: Boolean,
    val startTime: String,
    val endTime: String
)

@Serializable
data class DailyPlanResponse(
    val day: Int,
    val dateOffset: String,
    val date: String,
    val topics: List<String>,
    val goal: String,
    val estimatedMinutes: Int,
    val tasks: List<PlanTaskResponse>,
    val hasQuiz: Boolean,
    val quizResult: QuizResultResponse? = null
)

@Serializable
data class PlanBoardResponse(
    val id: Int,
    val subject: String,
    val grade: Int,
    val daysRemaining: Int,
    val targetScore: Int? = null,
    val scope: List<String>,
    val isCramMode: Boolean,
    val tips: List<String>,
    val dailyPlans: List<DailyPlanResponse>,
    val extractedScope: List<String>? = null,
    val sourceFileName: String? = null,
    val startDate: String,
    val examDate: String? = null,
    val unavailableSlots: List<UnavailableSlotInput> = emptyList(),
    val levelTier: String? = null
)

@Serializable
data class QuizQuestionPublicResponse(
    val id: Int,
    val topic: String,
    val questionText: String,
    val choices: List<String>
)

@Serializable
data class QuizGenerateResponse(
    val day: Int,
    val questions: List<QuizQuestionPublicResponse>
)

@Serializable
data class QuizAnswer(
    val questionId: Int,
    val selectedIndex: Int
)

@Serializable
data class QuizSubmitRequest(
    val answers: List<QuizAnswer>
)

@Serializable
data class QuizQuestionResultResponse(
    val questionId: Int,
    val topic: String,
    val isCorrect: Boolean,
    val selectedIndex: Int,
    val correctIndex: Int,
    val explanation: String
)

@Serializable
data class QuizResultResponse(
    val correctCount: Int,
    val totalCount: Int,
    val wrongTopics: List<String>,
    val results: List<QuizQuestionResultResponse> = emptyList()
)

@Serializable
data class TaskCompleteRequest(
    val isCompleted: Boolean = true
)

@Serializable
data class FeedbackRequest(
    val difficulty: String, // "쉬움" | "적당" | "어려움"
    val timeSpentMinutes: Int? = null,
    val focusLevel: Int? = null
)

@Serializable
data class FeedbackResponse(
    val coachingMessage: String,
    val updatedDailyPlans: List<DailyPlanResponse>,
    val grass: GrassEntryResponse
)

@Serializable
data class GrassEntryResponse(
    val day: Int,
    val dateOffset: String,
    val completionRate: Double,
    val colorLevel: Int
)

@Serializable
data class GrassBoardResponse(
    val boardId: Int,
    val entries: List<GrassEntryResponse>
)

@Serializable
data class LevelTestGenerateRequest(
    val grade: Int // 중학교 학년(1~3). 실제 출제는 한 학년 아래 수준으로 나간다.
)

@Serializable
data class LevelTestQuestionPublicResponse(
    val id: Int,
    val subject: String,
    val questionText: String,
    val choices: List<String>
)

@Serializable
data class LevelTestGenerateResponse(
    val testId: Int,
    val referenceGradeLabel: String,
    val questions: List<LevelTestQuestionPublicResponse>
)

@Serializable
data class LevelTestAnswer(
    val questionId: Int,
    val selectedIndex: Int
)

@Serializable
data class LevelTestSubmitRequest(
    val answers: List<LevelTestAnswer>
)

@Serializable
data class LevelTestQuestionResultResponse(
    val questionId: Int,
    val subject: String,
    val isCorrect: Boolean,
    val selectedIndex: Int,
    val correctIndex: Int,
    val explanation: String
)

@Serializable
data class LevelTestSubmitResponse(
    val correctCount: Int,
    val totalCount: Int,
    val tier: String, // "상" | "중" | "하"
    val results: List<LevelTestQuestionResultResponse>
)
