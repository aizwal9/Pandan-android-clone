package com.aizwal.pandanclone.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.aizwal.pandanclone.ui.theme.MintNeon
import com.aizwal.pandanclone.ui.theme.TealAqua
import com.aizwal.pandanclone.ui.theme.Obsidian
import com.aizwal.pandanclone.ui.theme.DeepSlate
import com.aizwal.pandanclone.ui.theme.GreyText

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("PandanPrefs", Context.MODE_PRIVATE) }
    var currentSlide by remember { mutableIntStateOf(0) }
    var sessionLimit by remember { mutableIntStateOf(prefs.getInt("session_limit_minutes", 20)) }
    
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Proceed regardless of grant, but typically they grant
        prefs.edit().putBoolean("onboarding_completed", true).apply()
        onFinished()
    }

    Scaffold(
        containerColor = Obsidian
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Section (Page Indicator)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 16.dp)
            ) {
                repeat(3) { index ->
                    Box(
                        modifier = Modifier
                            .size(if (index == currentSlide) 24.dp else 8.dp, 8.dp)
                            .clip(CircleShape)
                            .background(if (index == currentSlide) MintNeon else DeepSlate)
                    )
                }
            }

            // Middle Section (Slide Content)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                when (currentSlide) {
                    0 -> OnboardingSlideContent(
                        title = "Mindful Screen Time",
                        description = "Pandan helps you build a healthier relationship with your phone by tracking individual continuous sessions instead of just daily aggregate totals."
                    )
                    1 -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Set Session Limits",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = MintNeon,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Choose a continuous usage limit. When exceeded, Pandan will play a custom software chime to remind you to take a break.",
                            fontSize = 14.sp,
                            color = GreyText,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(32.dp))

                        val presets = listOf(0, 5, 10, 15, 20, 30, 45, 60)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            presets.forEach { preset ->
                                val isSelected = sessionLimit == preset
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) MintNeon else DeepSlate)
                                        .border(
                                            1.dp,
                                            if (isSelected) MintNeon else Obsidian,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            sessionLimit = preset
                                            prefs.edit().putInt("session_limit_minutes", preset).apply()
                                        }
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = if (preset == 0) "Off" else "${preset}m",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Obsidian else Color.White
                                    )
                                }
                            }
                        }
                    }
                    2 -> OnboardingSlideContent(
                        title = "Enable Notifications",
                        description = "Pandan runs a low-power service in the background to detect lock/unlock events. We require Notification permission to display the timer in the status bar and play session reminders."
                    )
                }
            }

            // Bottom Section (Navigation Buttons)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (currentSlide > 0) {
                    OutlinedButton(
                        onClick = { currentSlide-- },
                        border = BorderStroke(1.dp, DeepSlate),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = GreyText),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Back", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Button(
                    onClick = {
                        if (currentSlide < 2) {
                            currentSlide++
                        } else {
                            // Request notification permission and finish onboarding
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                prefs.edit().putBoolean("onboarding_completed", true).apply()
                                onFinished()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MintNeon, contentColor = Obsidian),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (currentSlide == 2) "Get Started" else "Next",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun OnboardingSlideContent(title: String, description: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App logo or placeholder vector
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(DeepSlate)
                .border(2.dp, MintNeon, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "P",
                fontSize = 48.sp,
                fontWeight = FontWeight.Black,
                color = MintNeon
            )
        }
        Spacer(modifier = Modifier.height(40.dp))
        Text(
            text = title,
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            color = MintNeon,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = description,
            fontSize = 14.sp,
            color = GreyText,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}
