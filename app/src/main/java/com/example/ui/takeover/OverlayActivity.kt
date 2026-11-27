package com.example.ui.takeover

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.OverlayContent
import com.example.service.ActiveOverlayRequest
import com.example.service.OverlayCoordinator
import com.example.ui.theme.ScreenStopTheme
import com.example.util.AppForegroundTracker
import com.example.util.PermissionChecker

class OverlayActivity : ComponentActivity() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var handledRequestId: Long? = null
    private var pendingFinishAfterHome = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        disableTransitionAnimation()

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        applyImmersiveSystemBars()

        setContent {
            ScreenStopTheme {
                val request by OverlayCoordinator.activeRequest.collectAsStateWithLifecycle()

                LaunchedEffect(request) {
                    if (request == null && !pendingFinishAfterHome) {
                        finishOverlayCleanly()
                    }
                }

                val current = request ?: return@ScreenStopTheme
                when (val content = current.content) {
                    is OverlayContent.Takeover -> {
                        TakeoverScreen(
                            session = content.session,
                            onDismissConfirmed = {
                                handleAction(current) {
                                    current.onTakeoverConfirmed()
                                    finishOverlayCleanly()
                                }
                            }
                        )
                    }

                    is OverlayContent.DetoxBlocked -> {
                        DetoxBlockedScreen(
                            appLabel = content.appLabel,
                            blockedUntilMillis = content.blockedUntilMillis,
                            isDeepWork = content.isDeepWork,
                            categoryLabel = content.categoryLabel,
                            onReturnHome = {
                                handleReturnHome(current)
                            }
                        )
                    }

                    is OverlayContent.HowLongPrompt -> {
                        HowLongPromptScreen(
                            appLabel = content.appLabel,
                            onSelectDurationMinutes = { minutes ->
                                handleAction(current) {
                                    current.onDurationSelected(minutes)
                                    finishOverlayCleanly()
                                }
                            },
                            onNeverMindGoHome = {
                                handleReturnHome(current)
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingFinishAfterHome = false
        disableTransitionAnimation()
    }

    override fun onStart() {
        super.onStart()
        AppForegroundTracker.setOverlayActivityVisible(true)
    }

    override fun onResume() {
        super.onResume()
        applyImmersiveSystemBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyImmersiveSystemBars()
        }
    }

    override fun onStop() {
        AppForegroundTracker.setOverlayActivityVisible(false)

        if (pendingFinishAfterHome) {
            // Home is now visible on top of OverlayActivity, so finishing cleanly never exposes X.
            pendingFinishAfterHome = false
            finishOverlayCleanly()
        } else if (!isFinishing && !isChangingConfigurations) {
            // User pressed the physical Home or Recents button while an overlay was showing.
            val active = OverlayCoordinator.activeRequest.value
            if (active != null && active.id != handledRequestId) {
                handledRequestId = active.id
                OverlayCoordinator.clearIfMatches(active.id)
                when (active.content) {
                    is OverlayContent.HowLongPrompt,
                    is OverlayContent.DetoxBlocked -> active.onReturnHome()
                    is OverlayContent.Takeover -> active.onAbandonedToBackground()
                }
                finishOverlayCleanly()
            }
        }
        super.onStop()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        AppForegroundTracker.setOverlayActivityVisible(false)
        super.onDestroy()
    }

    private fun handleAction(
        request: ActiveOverlayRequest,
        block: () -> Unit
    ) {
        if (handledRequestId == request.id) return
        handledRequestId = request.id
        OverlayCoordinator.clearIfMatches(request.id)
        block()
    }

    private fun handleReturnHome(request: ActiveOverlayRequest) {
        if (handledRequestId == request.id) return
        handledRequestId = request.id

        // 1. Stamp dismissal watermark and update service state BEFORE any Activity transition.
        request.onReturnHome()
        OverlayCoordinator.clearIfMatches(request.id)

        if (request.launchHomeOnReturn) {
            // 2. Launch Home directly from this foreground Activity so Android never blocks it
            //    and the underlying app never resumes even briefly before Home appears.
            pendingFinishAfterHome = true
            runCatching {
                startActivity(PermissionChecker.createHomeIntent())
            }.onFailure {
                pendingFinishAfterHome = false
                finishOverlayCleanly()
                return
            }
            // Safety fallback in case Home is already top or onStop does not fire immediately.
            mainHandler.postDelayed({
                if (pendingFinishAfterHome && !isFinishing) {
                    pendingFinishAfterHome = false
                    finishOverlayCleanly()
                }
            }, 300L)
        } else {
            finishOverlayCleanly()
        }
    }

    private fun finishOverlayCleanly() {
        if (!isFinishing) {
            finish()
            disableTransitionAnimation()
        }
    }

    private fun applyImmersiveSystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(
            WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars()
        )
    }

    private fun disableTransitionAnimation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }
}
