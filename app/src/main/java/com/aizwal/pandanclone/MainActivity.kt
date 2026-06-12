package com.aizwal.pandanclone

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import android.content.Context
import androidx.compose.runtime.*
import com.aizwal.pandanclone.ui.MainScreen
import com.aizwal.pandanclone.ui.OnboardingScreen
import com.aizwal.pandanclone.service.ScreenTimerService
import com.aizwal.pandanclone.ui.theme.PandanTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            PandanTheme {
                val prefs = remember { getSharedPreferences("PandanPrefs", Context.MODE_PRIVATE) }
                var showOnboarding by remember { 
                    mutableStateOf(!prefs.getBoolean("onboarding_completed", false)) 
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (showOnboarding) {
                        OnboardingScreen(onFinished = { showOnboarding = false })
                    } else {
                        MainScreen(
                            onStartService = {
                                val intent = Intent(this, ScreenTimerService::class.java)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    startForegroundService(intent)
                                } else {
                                    startService(intent)
                                }
                            },
                            onStopService = {
                                val intent = Intent(this, ScreenTimerService::class.java)
                                stopService(intent)
                            }
                        )
                    }
                }
            }
        }
    }
}
