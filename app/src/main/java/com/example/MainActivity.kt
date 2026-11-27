package com.example

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.data.OverlayContent
import com.example.service.OverlayCoordinator
import com.example.ui.ScreenStopApp
import com.example.ui.takeover.OverlayActivity
import com.example.ui.theme.ScreenStopTheme
import com.example.util.AppForegroundTracker

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            ScreenStopTheme {
                ScreenStopApp()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AppForegroundTracker.setMainActivityVisible(true)
    }

    override fun onResume() {
        super.onResume()
        // If a real (non-preview) Takeover is currently active, route right back to it
        // so opening ScreenStop via recents or launcher cannot bypass an active Takeover.
        val activeTakeover = OverlayCoordinator.activeRequest.value?.content as? OverlayContent.Takeover
        if (activeTakeover != null && !activeTakeover.session.isPreview) {
            val overlayIntent = Intent(this, OverlayActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
            runCatching { startActivity(overlayIntent) }
        }
    }

    override fun onStop() {
        AppForegroundTracker.setMainActivityVisible(false)
        super.onStop()
    }
}
