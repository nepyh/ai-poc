package com.rooter.aipoc.module.studyplan

import com.rooter.aipoc.module.studyplan.model.LevelTest
import com.rooter.aipoc.module.studyplan.model.PlanBoard
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** PoC용 인메모리 저장소. 서버를 재시작하면 초기화된다. */
class StudyPlanStore {
    private val boards = ConcurrentHashMap<Int, PlanBoard>()
    private val levelTests = ConcurrentHashMap<Int, LevelTest>()
    private val boardIdGenerator = AtomicInteger(0)
    private val taskIdGenerator = AtomicInteger(0)
    private val quizIdGenerator = AtomicInteger(0)
    private val levelTestIdGenerator = AtomicInteger(0)
    private val levelTestQuestionIdGenerator = AtomicInteger(0)

    fun nextBoardId(): Int = boardIdGenerator.incrementAndGet()
    fun nextTaskId(): Int = taskIdGenerator.incrementAndGet()
    fun nextQuizId(): Int = quizIdGenerator.incrementAndGet()
    fun nextLevelTestId(): Int = levelTestIdGenerator.incrementAndGet()
    fun nextLevelTestQuestionId(): Int = levelTestQuestionIdGenerator.incrementAndGet()

    fun save(board: PlanBoard) {
        boards[board.id] = board
    }

    fun get(boardId: Int): PlanBoard? = boards[boardId]

    fun saveLevelTest(test: LevelTest) {
        levelTests[test.id] = test
    }

    fun getLevelTest(testId: Int): LevelTest? = levelTests[testId]
}
