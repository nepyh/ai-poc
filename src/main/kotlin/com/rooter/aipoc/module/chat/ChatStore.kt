package com.rooter.aipoc.module.chat

import com.rooter.aipoc.module.chat.model.ChatTurn
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/** PoC용 인메모리 대화 저장소. planBoard(boardId)별로 대화 이력을 쌓아두며, 서버를 재시작하면 초기화된다. */
class ChatStore {
    private val conversations = ConcurrentHashMap<Int, MutableList<ChatTurn>>()

    fun history(boardId: Int): List<ChatTurn> {
        val turns = conversations[boardId] ?: return emptyList()
        return synchronized(turns) { turns.toList() }
    }

    fun append(boardId: Int, turn: ChatTurn) {
        val turns = conversations.computeIfAbsent(boardId) { Collections.synchronizedList(mutableListOf()) }
        synchronized(turns) { turns.add(turn) }
    }
}
