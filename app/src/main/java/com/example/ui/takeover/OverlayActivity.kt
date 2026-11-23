package com.example.ui.takeover

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.OverlayContent
import com.example.service.OverlayCoordinator
import com.example.ui.theme.ScreenStopTheme
import com.example.util.AppForegroundTracker

class OverlayActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        setContent {
            ScreenStopTheme {
                val request by OverlayCoordinator.activeRequest.collectAsStateWithLifecycle()

                LaunchedEffect(request) {
                    if (request == null) {
                        finishOverlayCleanly()
                    }
                }

                val current = request ?: return@ScreenStopTheme
                when (val content = current.content) {
                    is OverlayContent.Takeover -> {
                        TakeoverScreen(
                            session = content.session,
                            onDismissConfirmed = {
                                OverlayCoordinator.clearIfMatches(current.id)
                                current.onTakeoverConfirmed()
                                finishOverlayCleanly()
                            }
                        )
                    }

                    is OverlayContent.DetoxBlocked -> {
                        DetoxBlockedScreen(
                            appLabel = content.appLabel,
                            blockedUntilMillis = content.blockedUntilMillis,
                            onReturnHome = {
                                OverlayCoordinator.clearIfMatches(current.id)
                                current.onReturnHome()
                                finishOverlayCleanly()
                            }
                        )
                    }

                    is OverlayContent.HowLongPrompt -> {
                        HowLongPromptScreen(
                            appLabel = content.appLabel,
                            onSelectDurationMinutes = { minutes ->
                                OverlayCoordinator.clearIfMatches(current.id)
                                current.onDurationSelected(minutes)
                                finishOverlayCleanly()
                            },
                            onNeverMindGoHome = {
                                OverlayCoordinator.clearIfMatches(current.id)
                                current.onReturnHome()
                                finishOverlayCleanly()
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AppForegroundTracker.setOverlayActivityVisible(true)
    }

    override fun onStop() {
        AppForegroundTracker.setOverlayActivityVisible(false)
        super.onStop()
    }

    override fun onDestroy() {
        AppForegroundTracker.setOverlayActivityVisible(false)
        super.onDestroy()
    }

    private fun finishOverlayCleanly() {
        if (!isFinishing) {
            finish()
        }
    }
}
