package com.example.service

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.Display
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.R
import com.example.data.OverlayContent
import com.example.ui.takeover.DetoxBlockedScreen
import com.example.ui.takeover.HowLongPromptScreen
import com.example.ui.takeover.TakeoverScreen
import com.example.ui.theme.ScreenStopTheme
import com.example.util.PermissionChecker

class TakeoverOverlayController(private val serviceContext: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var activeWindowManager: WindowManager? = null
    private var overlayRoot: FrameLayout? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null

    @Volatile
    var isShowing: Boolean = false
        private set

    fun show(
        content: OverlayContent,
        onTakeoverConfirmed: () -> Unit = {},
        onDurationSelected: (Int) -> Unit = {},
        onReturnHome: () -> Unit = {},
        onShowFailed: () -> Unit = {}
    ) {
        mainHandler.post {
            if (!PermissionChecker.hasOverlayPermission(serviceContext)) {
                onShowFailed()
                return@post
            }
            dismissInternal()

            val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            // Use a true WindowContext on Android 11+ (API 30+) so WindowManager and ComposeView
            // never crash from a non-visual application context.
            val visualContext = createOverlayWindowContext(windowType)
            val themedContext = ContextThemeWrapper(visualContext, R.style.Theme_MyApplication)
            val wm = visualContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            if (wm == null) {
                onShowFailed()
                return@post
            }

            val owner = OverlayLifecycleOwner()
            owner.onCreate()

            fun scheduleDismissAndRun(action: () -> Unit) {
                // CRITICAL: Never remove a ComposeView synchronously inside its own onClick
                // touch-event dispatch; defer to the next main-loop frame so Compose finishes cleanly.
                mainHandler.post {
                    dismissInternal()
                    action()
                }
            }

            val composeView = ComposeView(themedContext).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setViewTreeLifecycleOwner(owner)
                setViewTreeSavedStateRegistryOwner(owner)
                setViewTreeViewModelStoreOwner(owner)
                setViewTreeOnBackPressedDispatcherOwner(owner)
                setContent {
                    ScreenStopTheme {
                        when (content) {
                            is OverlayContent.Takeover -> {
                                TakeoverScreen(
                                    session = content.session,
                                    onDismissConfirmed = {
                                        scheduleDismissAndRun(onTakeoverConfirmed)
                                    }
                                )
                            }

                            is OverlayContent.DetoxBlocked -> {
                                DetoxBlockedScreen(
                                    appLabel = content.appLabel,
                                    blockedUntilMillis = content.blockedUntilMillis,
                                    onReturnHome = {
                                        scheduleDismissAndRun(onReturnHome)
                                    }
                                )
                            }

                            is OverlayContent.HowLongPrompt -> {
                                HowLongPromptScreen(
                                    appLabel = content.appLabel,
                                    onSelectDurationMinutes = { minutes ->
                                        scheduleDismissAndRun { onDurationSelected(minutes) }
                                    },
                                    onNeverMindGoHome = {
                                        scheduleDismissAndRun(onReturnHome)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            val container = object : FrameLayout(themedContext) {
                override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                    if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                        return true
                    }
                    return super.dispatchKeyEvent(event)
                }
            }.apply {
                setViewTreeLifecycleOwner(owner)
                setViewTreeSavedStateRegistryOwner(owner)
                setViewTreeViewModelStoreOwner(owner)
                setViewTreeOnBackPressedDispatcherOwner(owner)
                addView(
                    composeView,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )
            }

            @Suppress("DEPRECATION")
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                windowType,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }

            runCatching {
                wm.addView(container, params)
                owner.onResume()
                activeWindowManager = wm
                overlayRoot = container
                lifecycleOwner = owner
                isShowing = true
            }.onFailure {
                owner.onDestroy()
                activeWindowManager = null
                overlayRoot = null
                lifecycleOwner = null
                isShowing = false
                onShowFailed()
            }
        }
    }

    fun dismiss() {
        mainHandler.post { dismissInternal() }
    }

    private fun createOverlayWindowContext(windowType: Int): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return runCatching {
                val displayManager =
                    serviceContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                val defaultDisplay = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
                if (defaultDisplay != null) {
                    serviceContext.createDisplayContext(defaultDisplay)
                        .createWindowContext(windowType, null)
                } else {
                    serviceContext
                }
            }.getOrElse { serviceContext }
        }
        return serviceContext
    }

    private fun dismissInternal() {
        val wm = activeWindowManager
        val view = overlayRoot
        val owner = lifecycleOwner
        activeWindowManager = null
        overlayRoot = null
        lifecycleOwner = null
        isShowing = false

        if (wm != null && view != null && view.isAttachedToWindow) {
            runCatching { wm.removeView(view) }
        }
        owner?.onDestroy()
    }

    private class OverlayLifecycleOwner :
        LifecycleOwner,
        SavedStateRegistryOwner,
        ViewModelStoreOwner,
        OnBackPressedDispatcherOwner {

        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateRegistryController = SavedStateRegistryController.create(this)
        override val viewModelStore: ViewModelStore = ViewModelStore()
        override val onBackPressedDispatcher: OnBackPressedDispatcher = OnBackPressedDispatcher()

        override val lifecycle: Lifecycle
            get() = lifecycleRegistry

        override val savedStateRegistry: SavedStateRegistry
            get() = savedStateRegistryController.savedStateRegistry

        fun onCreate() {
            savedStateRegistryController.performAttach()
            savedStateRegistryController.performRestore(null)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        }

        fun onResume() {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        fun onDestroy() {
            when (lifecycleRegistry.currentState) {
                Lifecycle.State.RESUMED -> {
                    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
                    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
                    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
                }

                Lifecycle.State.STARTED -> {
                    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
                    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
                }

                Lifecycle.State.CREATED -> {
                    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
                }

                else -> Unit
            }
            viewModelStore.clear()
        }
    }
}
