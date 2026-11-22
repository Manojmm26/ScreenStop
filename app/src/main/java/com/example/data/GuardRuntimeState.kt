package com.example.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object GuardRuntimeState {
    private val _snapshot = MutableStateFlow(GuardRuntimeSnapshot())
    val snapshot: StateFlow<GuardRuntimeSnapshot> = _snapshot.asStateFlow()

    @Volatile
    private var lastMessage: String? = null

    @Volatile
    private var lastChallengeWords: List<String> = emptyList()

    fun createSession(
        messages: List<String>,
        consecutiveOverrides: Int = 0,
        escalatingEnabled: Boolean = true,
        appLabel: String? = null,
        isPreview: Boolean = false
    ): TakeoverSession {
        val message = GuardDefaults.pickRandomMessage(messages, lastMessage)
        val wordCount = GuardDefaults.challengeWordCountForOverrides(
            consecutiveOverrides = consecutiveOverrides,
            escalatingEnabled = escalatingEnabled
        )
        val words = GuardDefaults.pickRandomChallengeWords(wordCount, lastChallengeWords)
        lastMessage = message
        lastChallengeWords = words
        return TakeoverSession(
            message = message,
            challengeWords = words,
            escalationLevel = if (escalatingEnabled) (consecutiveOverrides + 1).coerceAtMost(4) else 1,
            consecutiveOverrides = consecutiveOverrides,
            appLabel = appLabel,
            isPreview = isPreview
        )
    }

    fun update(transform: (GuardRuntimeSnapshot) -> GuardRuntimeSnapshot) {
        _snapshot.update(transform)
    }

    fun resetToDisarmed(intervalMinutes: Int) {
        _snapshot.value = GuardRuntimeSnapshot(
            status = GuardStatus.DISARMED,
            elapsedSeconds = 0,
            targetSeconds = intervalMinutes * 60,
            consecutiveOverrides = 0,
            currentForegroundPackage = null,
            activeSessionPackage = null,
            activeSessionLabel = null,
            activeTakeover = null
        )
    }
}
