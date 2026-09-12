package com.rooter.aipoc.module.studyplan

import com.rooter.aipoc.ai.AiClient
import com.rooter.aipoc.ai.PromptTemplates
import com.rooter.aipoc.ai.extractJsonObject
import com.rooter.aipoc.ai.AiJsonFormat
import com.rooter.aipoc.document.DocumentTextExtractor
import com.rooter.aipoc.module.studyplan.dto.*
import com.rooter.aipoc.module.studyplan.exception.DailyPlanNotFoundException
import com.rooter.aipoc.module.studyplan.exception.PlanBoardNotFoundException
import com.rooter.aipoc.module.studyplan.exception.StudyPlanValidationException
import com.rooter.aipoc.module.studyplan.model.*
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.decodeFromString
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

private val VALID_DIFFICULTIES = setOf("쉬움", "적당", "어려움")

private val VALID_FOCUS_SESSION_STYLES = setOf("포모도로", "표준", "몰입형")
private val VALID_SUBJECT_DISTRIBUTIONS = setOf("한과목집중", "두과목집중", "여러과목전환")
private val VALID_PLAN_INTENSITIES = setOf("스파르타", "밸런스", "여유")
private val VALID_VOLATILE_TIME_SLOTS = setOf("방과후", "저녁이후", "주말")
private val VALID_DAY_BEFORE_EXAM_STYLES = setOf("가볍게훑기", "오답집중", "진도끝까지")
private val VALID_SELF_ESTIMATION_ACCURACIES = setOf("의욕과다형", "정확예측형", "과소평가형")
private val VALID_SCORE_GAP_CAUSES = setOf("시간부족형", "방법문제형", "실수컨디션형")
private val VALID_LEVEL_TIERS = setOf("상", "중", "하")

private const val DAY_MINUTES = 24 * 60

class StudyPlanService(
    private val store: StudyPlanStore,
    private val aiClient: AiClient
) {

    suspend fun generatePlan(request: GeneratePlanRequest): PlanBoardResponse {
        if (request.subject.isBlank()) {
            throw StudyPlanValidationException(HttpStatusCode.BadRequest, "SUBJECT_REQUIRED", "과목명을 입력해주세요.")
        }
        validateGrade(request.grade)
        validateStudyStyle(request.studyStyle)
        validateLevelTier(request.levelTier)
        if (request.scope.isEmpty()) {
            throw StudyPlanValidationException(HttpStatusCode.BadRequest, "SCOPE_REQUIRED", "시험 범위를 1개 이상 입력해주세요.")
        }

        val (startDate, examDate, daysRemaining) =
            resolvePlanDates(request.daysRemaining, request.startDate, request.examDate)
        val studyStyle = request.studyStyle.toDomain()
        val unavailableSlots = resolveUnavailableSlots(request.unavailableSlots)
        val breakMinutes = breakMinutesFor(studyStyle.focusSessionStyle)
        val levelTier = request.levelTier ?: "중"

        val prompt = PromptTemplates.planGeneration(
            PromptTemplates.PlanGenerationInput(
                subject = request.subject,
                grade = request.grade,
                daysRemaining = daysRemaining,
                targetScore = request.targetScore,
                scope = request.scope,
                isCramMode = request.isCramMode,
                studyStyle = studyStyle.toPromptInput(),
                levelTier = levelTier
            )
        )

        val raw = aiClient.complete(prompt)
        val parsed = AiJsonFormat.decodeFromString<AiPlanGenerationResult>(extractJsonObject(raw))

        val boardId = store.nextBoardId()
        val dailyPlans = parsed.daily_plans.map { it.toDomain(startDate, unavailableSlots, breakMinutes) }
        val board = PlanBoard(
            id = boardId,
            subject = parsed.subject,
            grade = request.grade,
            daysRemaining = daysRemaining,
            targetScore = request.targetScore,
            scope = request.scope,
            isCramMode = parsed.is_cram_mode,
            tips = parsed.tips,
            dailyPlans = dailyPlans.toMutableList(),
            studyStyle = studyStyle,
            startDate = startDate,
            examDate = examDate,
            unavailableSlots = unavailableSlots,
            levelTier = levelTier
        )
        store.save(board)
        return board.toResponse()
    }

    suspend fun generatePlanFromDocument(
        fileBytes: ByteArray,
        fileName: String,
        subject: String?,
        grade: Int,
        daysRemaining: Int?,
        targetScore: Int?,
        isCramMode: Boolean,
        studyStyleRequest: StudyStylePreferences?,
        startDateRequest: String?,
        examDateRequest: String?,
        unavailableSlotsRequest: List<UnavailableSlotInput>,
        levelTierRequest: String?
    ): PlanBoardResponse {
        validateGrade(grade)
        validateStudyStyle(studyStyleRequest)
        validateLevelTier(levelTierRequest)

        val (startDate, examDate, resolvedDaysRemaining) =
            resolvePlanDates(daysRemaining, startDateRequest, examDateRequest, maxDays = 31)

        val sourceMaterial = DocumentTextExtractor.extract(fileBytes, fileName)
        if (sourceMaterial.isBlank()) {
            throw StudyPlanValidationException(
                HttpStatusCode.BadRequest, "DOCUMENT_EMPTY", "문서에서 텍스트를 추출하지 못했습니다. 다른 파일을 시도해주세요."
            )
        }

        val studyStyle = studyStyleRequest.toDomain()
        val unavailableSlots = resolveUnavailableSlots(unavailableSlotsRequest)
        val breakMinutes = breakMinutesFor(studyStyle.focusSessionStyle)
        val levelTier = levelTierRequest ?: "중"

        val prompt = PromptTemplates.planGenerationFromDocument(
            PromptTemplates.DocumentPlanGenerationInput(
                subject = subject,
                grade = grade,
                daysRemaining = resolvedDaysRemaining,
                targetScore = targetScore,
                isCramMode = isCramMode,
                studyStyle = studyStyle.toPromptInput(),
                levelTier = levelTier,
                sourceMaterial = sourceMaterial
            )
        )

        val raw = aiClient.complete(prompt)
        val parsed = AiJsonFormat.decodeFromString<AiDocumentPlanGenerationResult>(extractJsonObject(raw))

        val boardId = store.nextBoardId()
        val dailyPlans = parsed.daily_plans.map { it.toDomain(startDate, unavailableSlots, breakMinutes) }
        val board = PlanBoard(
            id = boardId,
            subject = parsed.subject,
            grade = grade,
            daysRemaining = resolvedDaysRemaining,
            targetScore = targetScore,
            scope = parsed.extracted_scope,
            isCramMode = parsed.is_cram_mode,
            tips = parsed.tips,
            dailyPlans = dailyPlans.toMutableList(),
            extractedScope = parsed.extracted_scope,
            sourceFileName = fileName,
            studyStyle = studyStyle,
            startDate = startDate,
            examDate = examDate,
            unavailableSlots = unavailableSlots,
            levelTier = levelTier
        )
        store.save(board)
        return board.toResponse()
    }

    fun getBoard(boardId: Int): PlanBoardResponse = findBoard(boardId).toResponse()

    suspend fun generateLevelTest(request: LevelTestGenerateRequest): LevelTestGenerateResponse {
        validateGrade(request.grade)
        val referenceGradeLabel = referenceGradeLabelFor(request.grade)

        val prompt = PromptTemplates.levelTestGeneration(
            PromptTemplates.LevelTestGenerationInput(grade = request.grade, referenceGradeLabel = referenceGradeLabel)
        )

        val raw = aiClient.complete(prompt)
        val parsed = AiJsonFormat.decodeFromString<AiLevelTestGenerationResult>(extractJsonObject(raw))

        val questions = parsed.questions.map { q ->
            LevelTestQuestion(
                id = store.nextLevelTestQuestionId(),
                subject = q.subject,
                questionText = q.question_text,
                choices = q.choices,
                correctIndex = q.correct_index,
                explanation = q.explanation
            )
        }

        val test = LevelTest(id = store.nextLevelTestId(), grade = request.grade, questions = questions)
        store.saveLevelTest(test)

        return LevelTestGenerateResponse(
            testId = test.id,
            referenceGradeLabel = referenceGradeLabel,
            questions = questions.map { LevelTestQuestionPublicResponse(it.id, it.subject, it.questionText, it.choices) }
        )
    }

    fun submitLevelTest(testId: Int, request: LevelTestSubmitRequest): LevelTestSubmitResponse {
        val test = store.getLevelTest(testId)
            ?: throw StudyPlanValidationException(HttpStatusCode.NotFound, "LEVEL_TEST_NOT_FOUND", "실력 테스트를 찾을 수 없습니다: $testId")

        val byId = test.questions.associateBy { it.id }
        val results = request.answers.mapNotNull { answer ->
            val question = byId[answer.questionId] ?: return@mapNotNull null
            LevelTestQuestionResultResponse(
                questionId = question.id,
                subject = question.subject,
                isCorrect = answer.selectedIndex == question.correctIndex,
                selectedIndex = answer.selectedIndex,
                correctIndex = question.correctIndex,
                explanation = question.explanation
            )
        }

        val correctCount = results.count { it.isCorrect }
        val totalCount = test.questions.size
        val tier = computeTier(correctCount, totalCount)

        test.result = LevelTestResult(correctCount = correctCount, totalCount = totalCount, tier = tier)
        store.saveLevelTest(test)

        return LevelTestSubmitResponse(correctCount = correctCount, totalCount = totalCount, tier = tier, results = results)
    }

    suspend fun generateQuiz(boardId: Int, day: Int): QuizGenerateResponse {
        val board = findBoard(boardId)
        val dailyPlan = findDailyPlan(board, day)

        val prompt = PromptTemplates.quizGeneration(
            PromptTemplates.TodayStudyInput(
                subject = board.subject,
                topics = dailyPlan.topics,
                goal = dailyPlan.goal
            )
        )

        val raw = aiClient.complete(prompt)
        val parsed = AiJsonFormat.decodeFromString<AiQuizGenerationResult>(extractJsonObject(raw))

        val questions = parsed.questions.map { q ->
            QuizQuestion(
                id = store.nextQuizId(),
                topic = q.topic,
                questionText = q.question_text,
                choices = q.choices,
                correctIndex = q.correct_index,
                explanation = q.explanation
            )
        }

        dailyPlan.quiz = questions.toMutableList()
        dailyPlan.quizResult = null
        store.save(board)

        return QuizGenerateResponse(
            day = dailyPlan.day,
            questions = questions.map { QuizQuestionPublicResponse(it.id, it.topic, it.questionText, it.choices) }
        )
    }

    fun submitQuiz(boardId: Int, day: Int, request: QuizSubmitRequest): QuizResultResponse {
        val board = findBoard(boardId)
        val dailyPlan = findDailyPlan(board, day)

        if (dailyPlan.quiz.isEmpty()) {
            throw StudyPlanValidationException(
                HttpStatusCode.BadRequest, "QUIZ_NOT_GENERATED", "이 날짜의 퀴즈가 아직 생성되지 않았습니다."
            )
        }

        val byId = dailyPlan.quiz.associateBy { it.id }
        val results = request.answers.mapNotNull { answer ->
            val question = byId[answer.questionId] ?: return@mapNotNull null
            QuizQuestionResultResponse(
                questionId = question.id,
                topic = question.topic,
                isCorrect = answer.selectedIndex == question.correctIndex,
                selectedIndex = answer.selectedIndex,
                correctIndex = question.correctIndex,
                explanation = question.explanation
            )
        }

        val correctCount = results.count { it.isCorrect }
        val wrongTopics = results.filter { !it.isCorrect }.map { it.topic }.distinct()

        val summary = QuizResultSummary(
            correctCount = correctCount,
            totalCount = dailyPlan.quiz.size,
            wrongTopics = wrongTopics
        )
        dailyPlan.quizResult = summary
        store.save(board)

        return QuizResultResponse(
            correctCount = summary.correctCount,
            totalCount = summary.totalCount,
            wrongTopics = summary.wrongTopics,
            results = results
        )
    }

    fun completeTask(boardId: Int, day: Int, taskId: Int, isCompleted: Boolean): DailyPlanResponse {
        val board = findBoard(boardId)
        val dailyPlan = findDailyPlan(board, day)
        val task = dailyPlan.tasks.find { it.id == taskId }
            ?: throw StudyPlanValidationException(HttpStatusCode.NotFound, "TASK_NOT_FOUND", "해당 할 일을 찾을 수 없습니다.")
        task.isCompleted = isCompleted
        store.save(board)
        return dailyPlan.toResponse()
    }

    suspend fun submitFeedback(boardId: Int, day: Int, request: FeedbackRequest): FeedbackResponse {
        val board = findBoard(boardId)
        val dailyPlan = findDailyPlan(board, day)

        if (request.difficulty !in VALID_DIFFICULTIES) {
            throw StudyPlanValidationException(
                HttpStatusCode.BadRequest, "INVALID_DIFFICULTY", "difficulty는 쉬움/적당/어려움 중 하나여야 합니다."
            )
        }
        val quizResult = dailyPlan.quizResult
            ?: throw StudyPlanValidationException(
                HttpStatusCode.BadRequest, "QUIZ_NOT_SUBMITTED", "설문 전에 오늘의 퀴즈를 먼저 제출해주세요."
            )

        dailyPlan.feedback = DailyFeedbackEntry(request.difficulty, request.timeSpentMinutes, request.focusLevel)

        val remainingPlans = board.dailyPlans.filter { it.day > day }
        val coachingMessage: String

        if (remainingPlans.isEmpty()) {
            coachingMessage = "마지막 날짜의 계획입니다. 남은 일정이 없어 재조정할 계획이 없습니다."
        } else {
            val prompt = PromptTemplates.replan(
                subject = board.subject,
                grade = board.grade,
                studyStyle = board.studyStyle.toPromptInput(),
                levelTier = board.levelTier ?: "중",
                quizResult = PromptTemplates.QuizResultInput(
                    correctCount = quizResult.correctCount,
                    totalCount = quizResult.totalCount,
                    wrongTopics = quizResult.wrongTopics
                ),
                feedback = PromptTemplates.DailyFeedbackInput(
                    difficulty = request.difficulty,
                    timeSpentMinutes = request.timeSpentMinutes,
                    focusLevel = request.focusLevel
                ),
                remainingPlan = remainingPlans.map {
                    PromptTemplates.RemainingDailyPlanInput(
                        day = it.day,
                        dateOffset = it.dateOffset,
                        topics = it.topics,
                        goal = it.goal,
                        estimatedMinutes = it.estimatedMinutes
                    )
                }
            )

            val raw = aiClient.complete(prompt)
            val parsed = AiJsonFormat.decodeFromString<AiReplanResult>(extractJsonObject(raw))
            coachingMessage = parsed.coaching_message

            val breakMinutes = breakMinutesFor(board.studyStyle.focusSessionStyle)

            val updatesByDay = parsed.daily_plans.associateBy { it.day }
            board.dailyPlans.replaceAll { plan ->
                val update = updatesByDay[plan.day] ?: return@replaceAll plan
                plan.dateOffset = update.date_offset
                plan.topics = update.topics.toMutableList()
                plan.goal = update.goal
                plan.estimatedMinutes = update.estimated_minutes
                val freeIntervals = freeIntervalsForDate(LocalDate.parse(plan.date), board.unavailableSlots)
                plan.tasks.clear()
                plan.tasks.addAll(
                    buildTasks(update.tasks, update.topics, update.estimated_minutes, freeIntervals, breakMinutes)
                )
                plan
            }
        }

        store.save(board)

        return FeedbackResponse(
            coachingMessage = coachingMessage,
            updatedDailyPlans = board.dailyPlans.filter { it.day > day }.map { it.toResponse() },
            grass = dailyPlan.toGrassEntry()
        )
    }

    /**
     * 챗봇 대화로 새로 생긴 하루짜리 일정(busyWindow)을 반영해 해당 날짜 하나만 재구성한다.
     * busyWindow는 그 날짜에 한해서만 빈 시간에서 제외할 뿐 board.unavailableSlots(요일별 반복 규칙)에는
     * 저장하지 않는다 — replan과 마찬가지로 시각(startTime/endTime)은 AI가 아니라 서버가 계산한다.
     */
    fun applyChatPlanUpdate(
        boardId: Int,
        day: Int,
        topics: List<String>,
        goal: String,
        estimatedMinutes: Int,
        tasks: List<AiTask>,
        busyWindowStart: String?,
        busyWindowEnd: String?
    ): DailyPlanResponse {
        val board = findBoard(boardId)
        val dailyPlan = findDailyPlan(board, day)

        var freeIntervals = freeIntervalsForDate(LocalDate.parse(dailyPlan.date), board.unavailableSlots)
        if (busyWindowStart != null && busyWindowEnd != null) {
            validateTimeRange(busyWindowStart, busyWindowEnd)
            freeIntervals = subtractInterval(freeIntervals, parseHHmm(busyWindowStart) to parseHHmm(busyWindowEnd))
        }

        val breakMinutes = breakMinutesFor(board.studyStyle.focusSessionStyle)

        dailyPlan.topics = topics.toMutableList()
        dailyPlan.goal = goal
        dailyPlan.estimatedMinutes = estimatedMinutes
        dailyPlan.tasks.clear()
        dailyPlan.tasks.addAll(buildTasks(tasks, topics, estimatedMinutes, freeIntervals, breakMinutes))

        store.save(board)
        return dailyPlan.toResponse()
    }

    fun getGrass(boardId: Int): GrassBoardResponse {
        val board = findBoard(boardId)
        return GrassBoardResponse(
            boardId = board.id,
            entries = board.dailyPlans.map { it.toGrassEntry() }
        )
    }

    private fun findBoard(boardId: Int): PlanBoard =
        store.get(boardId) ?: throw PlanBoardNotFoundException("plan board를 찾을 수 없습니다: $boardId")

    private fun findDailyPlan(board: PlanBoard, day: Int): DailyPlan =
        board.dailyPlans.find { it.day == day }
            ?: throw DailyPlanNotFoundException("${day}일차 계획을 찾을 수 없습니다.")

    private fun AiDailyPlan.toDomain(
        startDate: LocalDate,
        unavailableSlots: List<UnavailableSlot>,
        breakMinutes: Int
    ): DailyPlan {
        val date = startDate.plusDays((day - 1).toLong())
        val freeIntervals = freeIntervalsForDate(date, unavailableSlots)
        return DailyPlan(
            day = day,
            dateOffset = date_offset,
            date = date.toString(),
            topics = topics.toMutableList(),
            goal = goal,
            estimatedMinutes = estimated_minutes,
            tasks = buildTasks(tasks, topics, estimated_minutes, freeIntervals, breakMinutes).toMutableList()
        )
    }

    private fun validateGrade(grade: Int) {
        if (grade !in 1..3) {
            throw StudyPlanValidationException(HttpStatusCode.BadRequest, "INVALID_GRADE", "학년은 1~3 중 하나여야 합니다.")
        }
    }

    private fun validateLevelTier(levelTier: String?) {
        if (levelTier != null && levelTier !in VALID_LEVEL_TIERS) {
            throw StudyPlanValidationException(
                HttpStatusCode.BadRequest, "INVALID_LEVEL_TIER", "levelTier는 상/중/하 중 하나여야 합니다."
            )
        }
    }

    /** 실력 테스트는 현재 학년보다 한 단계 아래 수준으로 출제한다(중1은 초6 수준까지 내려간다). */
    private fun referenceGradeLabelFor(grade: Int): String = when (grade) {
        1 -> "초등학교 6학년"
        2 -> "중학교 1학년"
        3 -> "중학교 2학년"
        else -> "중학교 ${grade - 1}학년"
    }

    /** 정답률로 등급을 매긴다: 80% 이상 상, 40~79% 중, 그 미만 하. 문항이 0개면 안전하게 "중". */
    private fun computeTier(correctCount: Int, totalCount: Int): String {
        if (totalCount <= 0) return "중"
        val rate = correctCount.toDouble() / totalCount
        return when {
            rate >= 0.8 -> "상"
            rate >= 0.4 -> "중"
            else -> "하"
        }
    }

    /**
     * startDate/examDate가 둘 다 주어지면 실제 달력 날짜로 daysRemaining을 계산한다(시험 당일은
     * 공부일에서 제외 — 8/1~8/31 시험이면 daysRemaining=30, 마지막 공부일은 8/30). 둘 다 없으면
     * daysRemainingInput(필수)을 그대로 쓰고 startDate는 오늘로 간주한다.
     */
    private fun resolvePlanDates(
        daysRemainingInput: Int?,
        startDateInput: String?,
        examDateInput: String?,
        maxDays: Int? = null
    ): Triple<LocalDate, LocalDate?, Int> {
        val daysRemaining: Int
        val startDate: LocalDate
        val examDate: LocalDate?

        if (startDateInput != null && examDateInput != null) {
            startDate = parseISODate(startDateInput, "startDate")
            examDate = parseISODate(examDateInput, "examDate")
            daysRemaining = ChronoUnit.DAYS.between(startDate, examDate).toInt()
            if (daysRemaining <= 0) {
                throw StudyPlanValidationException(
                    HttpStatusCode.BadRequest, "INVALID_DATE_RANGE", "examDate는 startDate보다 뒤여야 합니다."
                )
            }
        } else {
            daysRemaining = daysRemainingInput ?: throw StudyPlanValidationException(
                HttpStatusCode.BadRequest,
                "DAYS_REMAINING_OR_DATES_REQUIRED",
                "daysRemaining 또는 startDate/examDate 중 하나는 필요합니다."
            )
            if (daysRemaining <= 0) {
                throw StudyPlanValidationException(
                    HttpStatusCode.BadRequest, "INVALID_DAYS_REMAINING", "남은 기간은 1일 이상이어야 합니다."
                )
            }
            startDate = LocalDate.now()
            examDate = null
        }

        if (maxDays != null && daysRemaining > maxDays) {
            throw StudyPlanValidationException(
                HttpStatusCode.BadRequest, "INVALID_DAYS_REMAINING", "남은 기간은 최대 ${maxDays}일까지 가능합니다."
            )
        }

        return Triple(startDate, examDate, daysRemaining)
    }

    private fun parseISODate(value: String, field: String): LocalDate = try {
        LocalDate.parse(value)
    } catch (e: DateTimeParseException) {
        throw StudyPlanValidationException(
            HttpStatusCode.BadRequest, "INVALID_DATE_FORMAT", "$field 는 yyyy-MM-dd 형식이어야 합니다: $value"
        )
    }

    /**
     * 비어 있으면 기본값으로 대체한다: 매일 수면 시간(00:00~06:30, 23:00~24:00) + 평일(월~금)
     * 학교 시간(08:30~16:30). 수면 시간을 기본으로 막아두지 않으면, 그날 남은 빈 시간 중 자정
     * 직후가 항상 가장 먼저 비는 구간이라 tasks가 매번 새벽부터 배정되는 문제가 있어 추가했다.
     */
    private fun resolveUnavailableSlots(slots: List<UnavailableSlotInput>): List<UnavailableSlot> {
        if (slots.isEmpty()) {
            val sleep = (1..7).flatMap { day ->
                listOf(
                    UnavailableSlot(dayOfWeek = day, start = "00:00", end = "06:30"),
                    UnavailableSlot(dayOfWeek = day, start = "23:00", end = "24:00")
                )
            }
            val school = (1..5).map { UnavailableSlot(dayOfWeek = it, start = "08:30", end = "16:30") }
            return sleep + school
        }
        return slots.map { slot ->
            if (slot.dayOfWeek !in 1..7) {
                throw StudyPlanValidationException(
                    HttpStatusCode.BadRequest, "INVALID_UNAVAILABLE_SLOT", "dayOfWeek는 1(월)~7(일) 중 하나여야 합니다."
                )
            }
            validateTimeRange(slot.start, slot.end)
            UnavailableSlot(dayOfWeek = slot.dayOfWeek, start = slot.start, end = slot.end)
        }
    }

    /** "HH:mm"을 자정 기준 분(0~1440)으로 변환한다. "24:00"은 하루의 끝(1440분)을 뜻하는 특수값으로 허용한다. */
    private fun parseHHmm(value: String): Int {
        if (value == "24:00") return DAY_MINUTES
        val parts = value.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull()
        val minute = parts.getOrNull(1)?.toIntOrNull()
        if (parts.size != 2 || hour == null || minute == null || hour !in 0..23 || minute !in 0..59) {
            throw StudyPlanValidationException(
                HttpStatusCode.BadRequest, "INVALID_TIME_FORMAT", "시간은 \"HH:mm\" 형식이어야 합니다: $value"
            )
        }
        return hour * 60 + minute
    }

    private fun formatHHmm(totalMinutes: Int): String {
        val wrapped = ((totalMinutes % DAY_MINUTES) + DAY_MINUTES) % DAY_MINUTES
        return "%02d:%02d".format(wrapped / 60, wrapped % 60)
    }

    private fun validateTimeRange(start: String, end: String) {
        if (parseHHmm(start) >= parseHHmm(end)) {
            throw StudyPlanValidationException(
                HttpStatusCode.BadRequest, "INVALID_TIME_RANGE", "시작 시각은 종료 시각보다 빨라야 합니다: $start ~ $end"
            )
        }
    }

    /** studyStyle.focusSessionStyle에 따라 task 사이에 둘 휴식 시간(분)을 정한다. */
    private fun breakMinutesFor(focusSessionStyle: String?): Int = when (focusSessionStyle) {
        "포모도로" -> 5
        "몰입형" -> 0
        else -> 10 // "표준" 또는 응답 없음
    }

    /**
     * date가 속한 요일에 해당하는 unavailableSlots를 병합해 하루([0, 1440)분) 중 비어 있는
     * 시간 구간(반열림구간, start 포함 end 미포함) 목록을 계산한다.
     */
    private fun freeIntervalsForDate(date: LocalDate, slots: List<UnavailableSlot>): List<Pair<Int, Int>> {
        val dayOfWeek = date.dayOfWeek.value // 1=월 ... 7=일
        val busy = slots.filter { it.dayOfWeek == dayOfWeek }
            .map { parseHHmm(it.start) to parseHHmm(it.end) }
            .sortedBy { it.first }

        val merged = mutableListOf<Pair<Int, Int>>()
        for ((start, end) in busy) {
            val last = merged.lastOrNull()
            if (last != null && start <= last.second) {
                merged[merged.size - 1] = last.first to maxOf(last.second, end)
            } else {
                merged.add(start to end)
            }
        }

        val free = mutableListOf<Pair<Int, Int>>()
        var cursor = 0
        for ((start, end) in merged) {
            if (start > cursor) free.add(cursor to start)
            cursor = maxOf(cursor, end)
        }
        if (cursor < DAY_MINUTES) free.add(cursor to DAY_MINUTES)
        return free
    }

    /** intervals(반열림구간 목록)에서 busy 구간과 겹치는 부분을 잘라낸다. */
    private fun subtractInterval(intervals: List<Pair<Int, Int>>, busy: Pair<Int, Int>): List<Pair<Int, Int>> {
        val result = mutableListOf<Pair<Int, Int>>()
        for ((start, end) in intervals) {
            if (busy.second <= start || busy.first >= end) {
                result.add(start to end)
                continue
            }
            if (busy.first > start) result.add(start to busy.first)
            if (busy.second < end) result.add(busy.second to end)
        }
        return result
    }

    private fun validateStudyStyle(studyStyle: StudyStylePreferences?) {
        if (studyStyle == null) return

        fun check(value: String?, valid: Set<String>, field: String) {
            if (value != null && value !in valid) {
                throw StudyPlanValidationException(
                    HttpStatusCode.BadRequest, "INVALID_STUDY_STYLE", "studyStyle.$field 값이 올바르지 않습니다."
                )
            }
        }

        check(studyStyle.focusSessionStyle, VALID_FOCUS_SESSION_STYLES, "focusSessionStyle")
        check(studyStyle.subjectDistribution, VALID_SUBJECT_DISTRIBUTIONS, "subjectDistribution")
        check(studyStyle.planIntensity, VALID_PLAN_INTENSITIES, "planIntensity")
        check(studyStyle.volatileTimeSlot, VALID_VOLATILE_TIME_SLOTS, "volatileTimeSlot")
        check(studyStyle.dayBeforeExamStyle, VALID_DAY_BEFORE_EXAM_STYLES, "dayBeforeExamStyle")
        check(studyStyle.selfEstimationAccuracy, VALID_SELF_ESTIMATION_ACCURACIES, "selfEstimationAccuracy")
        check(studyStyle.scoreGapCause, VALID_SCORE_GAP_CAUSES, "scoreGapCause")
    }

    private fun StudyStylePreferences?.toDomain(): StudyStyleSettings {
        if (this == null) return StudyStyleSettings()
        return StudyStyleSettings(
            focusSessionStyle = focusSessionStyle,
            subjectDistribution = subjectDistribution,
            planIntensity = planIntensity,
            volatileTimeSlot = volatileTimeSlot,
            dayBeforeExamStyle = dayBeforeExamStyle,
            selfEstimationAccuracy = selfEstimationAccuracy,
            scoreGapCause = scoreGapCause
        )
    }

    private fun StudyStyleSettings.toPromptInput(): PromptTemplates.StudyStyleInput = PromptTemplates.StudyStyleInput(
        focusSessionStyle = focusSessionStyle,
        subjectDistribution = subjectDistribution,
        planIntensity = planIntensity,
        volatileTimeSlot = volatileTimeSlot,
        dayBeforeExamStyle = dayBeforeExamStyle,
        selfEstimationAccuracy = selfEstimationAccuracy,
        scoreGapCause = scoreGapCause
    )

    /**
     * AI가 tasks(개념 학습/문제 풀이 등 세부 활동)를 내려주면 그대로 쓰고,
     * 혹시 비어 있으면(모델이 스키마를 안 지킨 경우) topics를 균등 분배하는 방식으로 보수적으로 대체한다.
     * 시각(startTime/endTime)은 AI가 아니라 서버가 직접 계산한다 — freeIntervals(그 날짜·요일에
     * 실제로 비어 있는 시간 구간들)를 순서대로 채워나가며 task를 절대 쪼개지 않고 배치하고,
     * task 사이에 breakMinutes만큼 휴식을 끼워 넣는다. 구간이 다 차면 다음 구간으로 넘어간다.
     */
    private fun buildTasks(
        aiTasks: List<AiTask>,
        topics: List<String>,
        totalMinutes: Int,
        freeIntervals: List<Pair<Int, Int>>,
        breakMinutes: Int
    ): List<PlanTask> {
        val items: List<Pair<String, Int>> = when {
            aiTasks.isNotEmpty() -> aiTasks.map { it.task_name to it.estimated_minutes }
            topics.isNotEmpty() -> {
                val perTopicMinutes = maxOf(totalMinutes / topics.size, 1)
                topics.map { it to perTopicMinutes }
            }
            else -> emptyList()
        }
        if (items.isEmpty()) return emptyList()

        var intervalIndex = 0
        var cursor = freeIntervals.getOrNull(0)?.first ?: 0

        val result = mutableListOf<PlanTask>()
        for ((taskName, minutes) in items) {
            while (intervalIndex < freeIntervals.size && cursor + minutes > freeIntervals[intervalIndex].second) {
                intervalIndex++
                cursor = freeIntervals.getOrNull(intervalIndex)?.first ?: cursor
            }
            val start = cursor
            val end = start + minutes
            cursor = end + breakMinutes
            result.add(
                PlanTask(
                    id = store.nextTaskId(),
                    taskName = taskName,
                    estimatedMinutes = minutes,
                    startTime = formatHHmm(start),
                    endTime = formatHHmm(end)
                )
            )
        }
        return result
    }

    private fun DailyPlan.toGrassEntry(): GrassEntryResponse {
        val rate = if (tasks.isEmpty()) 0.0 else tasks.count { it.isCompleted }.toDouble() / tasks.size
        val colorLevel = when {
            rate <= 0.0 -> 0
            rate < 0.25 -> 1
            rate < 0.5 -> 2
            rate < 0.75 -> 3
            else -> 4
        }
        return GrassEntryResponse(day, dateOffset, rate, colorLevel)
    }

    private fun DailyPlan.toResponse(): DailyPlanResponse = DailyPlanResponse(
        day = day,
        dateOffset = dateOffset,
        date = date,
        topics = topics,
        goal = goal,
        estimatedMinutes = estimatedMinutes,
        tasks = tasks.map { PlanTaskResponse(it.id, it.taskName, it.estimatedMinutes, it.isCompleted, it.startTime, it.endTime) },
        hasQuiz = quiz.isNotEmpty(),
        quizResult = quizResult?.let { QuizResultResponse(it.correctCount, it.totalCount, it.wrongTopics) }
    )

    private fun PlanBoard.toResponse(): PlanBoardResponse = PlanBoardResponse(
        id = id,
        subject = subject,
        grade = grade,
        daysRemaining = daysRemaining,
        targetScore = targetScore,
        scope = scope,
        isCramMode = isCramMode,
        tips = tips,
        dailyPlans = dailyPlans.map { it.toResponse() },
        extractedScope = extractedScope,
        sourceFileName = sourceFileName,
        startDate = startDate.toString(),
        examDate = examDate?.toString(),
        unavailableSlots = unavailableSlots.map { UnavailableSlotInput(it.dayOfWeek, it.start, it.end) },
        levelTier = levelTier
    )
}
