package com.example.launcher

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.MainActivity
import com.example.ui.GuardViewModel
import com.example.ui.theme.ScreenStopTheme

class MinimalistLauncherActivity : ComponentActivity() {

    private val viewModel: GuardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            ScreenStopTheme {
                MinimalistHomeScreen(
                    viewModel = viewModel,
                    onLaunchApp = { packageName ->
                        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        if (launchIntent != null) {
                            runCatching { startActivity(launchIntent) }
                        }
                    },
                    onOpenScreenStop = {
                        val intent = Intent(this, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        startActivity(intent)
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissions()
        viewModel.refreshInstalledApps()
    }
}
