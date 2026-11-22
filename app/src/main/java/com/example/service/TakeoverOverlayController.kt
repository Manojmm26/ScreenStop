package com.example.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.FrameLayout
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

class TakeoverOverlayController(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

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
            if (!PermissionChecker.hasOverlayPermission(context)) {
                onShowFailed()
                return@post
            }
            dismissInternal()

            val themedContext = ContextThemeWrapper(context, R.style.Theme_MyApplication)
            val owner = OverlayLifecycleOwner()
            owner.onCreate()

            val composeView = ComposeView(themedContext).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    ScreenStopTheme {
                        when (content) {
                            is OverlayContent.Takeover -> {
                                TakeoverScreen(
                                    session = content.session,
                                    onDismissConfirmed = {
                                        dismissInternal()
                                        onTakeoverConfirmed()
                                    }
                                )
                            }

                            is OverlayContent.DetoxBlocked -> {
                                DetoxBlockedScreen(
                                    appLabel = content.appLabel,
                                    blockedUntilMillis = content.blockedUntilMillis,
                                    onReturnHome = {
                                        dismissInternal()
                                        onReturnHome()
                                    }
                                )
                            }

                            is OverlayContent.HowLongPrompt -> {
                                HowLongPromptScreen(
                                    appLabel = content.appLabel,
                                    onSelectDurationMinutes = { minutes ->
                                        dismissInternal()
                                        onDurationSelected(minutes)
                                    },
                                    onNeverMindGoHome = {
                                        dismissInternal()
                                        onReturnHome()
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
                addView(
                    composeView,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )
            }

            val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
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
                windowManager.addView(container, params)
                owner.onResume()
                overlayRoot = container
                lifecycleOwner = owner
                isShowing = true
            }.onFailure {
                owner.onDestroy()
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

    private fun dismissInternal() {
        val view = overlayRoot
        val owner = lifecycleOwner
        overlayRoot = null
        lifecycleOwner = null
        isShowing = false

        if (view != null) {
            runCatching { windowManager.removeViewImmediate(view) }
        }
        owner?.onDestroy()
    }

    private class OverlayLifecycleOwner :
        LifecycleOwner,
        SavedStateRegistryOwner,
        ViewModelStoreOwner {

        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateRegistryController = SavedStateRegistryController.create(this)
        override val viewModelStore: ViewModelStore = ViewModelStore()

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
