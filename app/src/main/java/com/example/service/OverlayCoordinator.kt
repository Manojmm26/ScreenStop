package com.example.service

import com.example.data.OverlayContent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ActiveOverlayRequest(
    val id: Long = System.nanoTime(),
    val content: OverlayContent,
    val launchHomeOnReturn: Boolean = true,
    val onTakeoverConfirmed: () -> Unit = {},
    val onDurationSelected: (Int) -> Unit = {},
    val onReturnHome: () -> Unit = {},
    val onAbandonedToBackground: () -> Unit = {}
)

object OverlayCoordinator {
    private val _activeRequest = MutableStateFlow<ActiveOverlayRequest?>(null)
    val activeRequest: StateFlow<ActiveOverlayRequest?> = _activeRequest.asStateFlow()

    fun publish(request: ActiveOverlayRequest) {
        _activeRequest.value = request
    }

    fun clear() {
        _activeRequest.value = null
    }

    fun clearIfMatches(id: Long) {
        if (_activeRequest.value?.id == id) {
            _activeRequest.value = null
        }
    }
}
