package com.example

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.ui.ScreenStopApp
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

    override fun onStop() {
        AppForegroundTracker.setMainActivityVisible(false)
        super.onStop()
    }
}
