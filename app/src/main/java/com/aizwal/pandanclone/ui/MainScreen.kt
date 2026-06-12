package com.aizwal.pandanclone.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.aizwal.pandanclone.data.AppDatabase
import com.aizwal.pandanclone.data.SessionEntity
import com.aizwal.pandanclone.ui.theme.MintNeon
import com.aizwal.pandanclone.ui.theme.TealAqua
import com.aizwal.pandanclone.ui.theme.Obsidian
import com.aizwal.pandanclone.ui.theme.DeepSlate
import com.aizwal.pandanclone.ui.theme.GreyText
import com.aizwal.pandanclone.ui.theme.AlertCoral
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onStartService: () -> Unit,
    onStopService: () -> Unit
) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()
    
    val prefs = remember { context.getSharedPreferences("PandanPrefs", Context.MODE_PRIVATE) }
    var sessionLimit by remember { mutableIntStateOf(prefs.getInt("session_limit_minutes", 20)) }
    
    val calendar = Calendar.getInstance()
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    
    val sessions by database.sessionDao()
        .getSessionsForDay(calendar.timeInMillis)
        .collectAsState(initial = emptyList())

    // Fetch past 7 days of sessions for the chart
    val weeklySessionsFlow = remember {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -6)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        database.sessionDao().getSessionsFromDate(cal.timeInMillis)
    }
    val weeklySessions by weeklySessionsFlow.collectAsState(initial = emptyList())

    val dailyTotals = remember(weeklySessions) {
        val totals = mutableMapOf<String, Long>()
        val dayNames = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        
        for (i in 0..6) {
            val tempCal = Calendar.getInstance()
            tempCal.add(Calendar.DAY_OF_YEAR, -i)
            val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(tempCal.time)
            totals[dateKey] = 0L
        }
        
        weeklySessions.forEach { session ->
            val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(session.startTime))
            if (totals.containsKey(dateKey)) {
                totals[dateKey] = totals.getValue(dateKey) + session.durationMs
            }
        }
        
        val result = mutableListOf<Pair<String, Long>>()
        for (i in 6 downTo 0) {
            val tempCal = Calendar.getInstance()
            tempCal.add(Calendar.DAY_OF_YEAR, -i)
            val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(tempCal.time)
            val dayOfWeek = tempCal.get(Calendar.DAY_OF_WEEK)
            val dayName = dayNames[dayOfWeek - 1]
            result.add(Pair(dayName, totals[dateKey] ?: 0L))
        }
        result
    }

    val totalTimeToday = sessions.sumOf { it.durationMs }
    val unlockCount = sessions.size

    // Real-time ticking values
    var activeSessionElapsed by remember { mutableLongStateOf(0L) }
    var isTrackingActive by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            val startTime = prefs.getLong("current_session_start_time", 0L)
            isTrackingActive = startTime > 0L
            if (startTime > 0L) {
                activeSessionElapsed = System.currentTimeMillis() - startTime
            } else {
                activeSessionElapsed = 0L
            }
            kotlinx.coroutines.delay(1000L)
        }
    }

    val liveTotalToday = totalTimeToday + activeSessionElapsed

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "PANDAN", 
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        color = MintNeon
                    ) 
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Obsidian,
                    titleContentColor = MintNeon
                )
            )
        },
        containerColor = Obsidian
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Main Pulsing Progress Ring & Stats
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(190.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    MintNeon.copy(alpha = 0.12f * pulseScale),
                                    Color.Transparent
                                )
                            )
                        )
                )

                Box(
                    modifier = Modifier
                        .size(170.dp)
                        .border(
                            BorderStroke(4.dp, Brush.sweepGradient(listOf(MintNeon, TealAqua, MintNeon))),
                            CircleShape
                        )
                        .shadow(8.dp, CircleShape)
                        .background(DeepSlate, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "SCREEN TIME TODAY",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = GreyText,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = formatDuration(liveTotalToday),
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Black,
                            color = MintNeon
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "$unlockCount Unlocks",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TealAqua
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Control Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = onStartService,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MintNeon,
                        contentColor = Obsidian
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Start Tracking", fontWeight = FontWeight.Bold)
                }
                
                OutlinedButton(
                    onClick = onStopService,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = GreyText
                    ),
                    border = BorderStroke(1.dp, DeepSlate),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Stop Tracking", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Custom Canvas Weekly insights chart
            WeeklyBarChart(dailyTotals = dailyTotals)

            Spacer(modifier = Modifier.height(24.dp))

            // Settings & Options Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DeepSlate),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF222226))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Session Break Limit",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Alert when screen stays on continuously for:",
                        fontSize = 11.sp,
                        color = GreyText
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val presets = listOf(0, 5, 10, 15, 20, 30, 45, 60)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presets.forEach { preset ->
                            val isSelected = sessionLimit == preset
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) MintNeon else Obsidian)
                                    .border(
                                        1.dp,
                                        if (isSelected) MintNeon else DeepSlate,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        sessionLimit = preset
                                        prefs.edit().putInt("session_limit_minutes", preset).apply()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
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

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = Obsidian, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Export Data Option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val allSessions = database.sessionDao().getAllSessions().first()
                                        withContext(Dispatchers.Main) {
                                            if (allSessions.isEmpty()) {
                                                Toast.makeText(context, "No sessions to export", Toast.LENGTH_SHORT).show()
                                            } else {
                                                exportSessionsToCsv(context, allSessions)
                                            }
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Export Data",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                            Text(
                                "Share session history as a CSV file",
                                fontSize = 11.sp,
                                color = GreyText
                            )
                        }
                        Text(
                            "Export",
                            color = MintNeon,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Today's Sessions List Header
            Text(
                "Today's History",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color.White,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (sessions.isEmpty() && !isTrackingActive) {
                Text(
                    "No sessions recorded today.",
                    color = GreyText,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isTrackingActive && activeSessionElapsed > 0L) {
                        OngoingSessionItem(activeSessionElapsed)
                    }
                    sessions.forEach { session ->
                        SessionItem(session)
                    }
                }
            }
        }
    }
}

@Composable
fun WeeklyBarChart(dailyTotals: List<Pair<String, Long>>) {
    val maxDuration = remember(dailyTotals) { 
        val max = dailyTotals.maxOfOrNull { it.second } ?: 0L
        if (max == 0L) 1000L else max
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        colors = CardDefaults.cardColors(containerColor = DeepSlate),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF222226))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                "Weekly Insights",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                val width = size.width
                val height = size.height
                
                val numDays = dailyTotals.size
                val spacing = 24.dp.toPx()
                val barWidth = (width - (spacing * (numDays - 1))) / numDays
                
                // Draw 3 horizontal dashed gridlines
                val gridLines = 3
                for (i in 0 until gridLines) {
                    val y = height * (i + 1) / (gridLines + 1)
                    drawLine(
                        color = Color(0xFF222226),
                        start = androidx.compose.ui.geometry.Offset(0f, y),
                        end = androidx.compose.ui.geometry.Offset(width, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )
                }
                
                dailyTotals.forEachIndexed { index, (_, durationMs) ->
                    val isToday = index == dailyTotals.lastIndex
                    val barHeight = (durationMs.toFloat() / maxDuration) * (height * 0.75f)
                    
                    val x = index * (barWidth + spacing)
                    val y = height - barHeight
                    
                    if (durationMs > 0) {
                        // Draw rounded vertical bar
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = if (isToday) {
                                    listOf(MintNeon, TealAqua)
                                } else {
                                    listOf(TealAqua.copy(alpha = 0.8f), TealAqua.copy(alpha = 0.2f))
                                }
                            ),
                            topLeft = androidx.compose.ui.geometry.Offset(x, y),
                            size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx(), 6.dp.toPx())
                        )
                        
                        // Today's highlight stroke
                        if (isToday) {
                            drawRoundRect(
                                color = MintNeon,
                                topLeft = androidx.compose.ui.geometry.Offset(x, y),
                                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                                style = Stroke(width = 1.5.dp.toPx())
                            )
                        }
                    } else {
                        // Tiny placeholder dash for 0 sessions
                        drawRoundRect(
                            color = Color(0xFF222226),
                            topLeft = androidx.compose.ui.geometry.Offset(x, height - 4.dp.toPx()),
                            size = androidx.compose.ui.geometry.Size(barWidth, 4.dp.toPx()),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx())
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            
            // Weekly Labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                dailyTotals.forEachIndexed { index, (dayName, durationMs) ->
                    val isToday = index == dailyTotals.lastIndex
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(36.dp)
                    ) {
                        Text(
                            text = formatDurationMinutes(durationMs),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isToday) MintNeon else GreyText,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = dayName,
                            fontSize = 10.sp,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (isToday) MintNeon else GreyText,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OngoingSessionItem(elapsedMs: Long) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("PandanPrefs", Context.MODE_PRIVATE) }
    val limit = prefs.getInt("session_limit_minutes", 20)
    val limitExceeded = limit > 0 && elapsedMs > limit * 60 * 1000L

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DeepSlate),
        border = BorderStroke(
            width = 1.5.dp,
            color = if (limitExceeded) AlertCoral else MintNeon.copy(alpha = pulseAlpha)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (limitExceeded) AlertCoral else MintNeon)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (limitExceeded) "Limit Exceeded" else "Active Session",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (limitExceeded) AlertCoral else MintNeon
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Screen is currently on",
                    fontSize = 11.sp,
                    color = GreyText
                )
            }
            Text(
                text = formatDuration(elapsedMs),
                fontWeight = FontWeight.Black,
                fontSize = 16.sp,
                color = if (limitExceeded) AlertCoral else MintNeon
            )
        }
    }
}

@Composable
fun SessionItem(session: SessionEntity) {
    val timeFormat = remember { SimpleDateFormat("hh:mm:ss a", Locale.getDefault()) }
    val startTimeStr = timeFormat.format(Date(session.startTime))
    val endTimeStr = timeFormat.format(Date(session.endTime))
    
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("PandanPrefs", Context.MODE_PRIVATE) }
    val limit = prefs.getInt("session_limit_minutes", 20)
    val limitExceeded = limit > 0 && session.durationMs > limit * 60 * 1000L

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DeepSlate),
        border = if (limitExceeded) BorderStroke(1.dp, AlertCoral) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (limitExceeded) "Limit Exceeded" else "Session",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (limitExceeded) AlertCoral else Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$startTimeStr - $endTimeStr",
                    fontSize = 11.sp,
                    color = GreyText
                )
            }
            Text(
                text = formatDuration(session.durationMs),
                fontWeight = FontWeight.Black,
                fontSize = 16.sp,
                color = if (limitExceeded) AlertCoral else MintNeon
            )
        }
    }
}

fun formatDurationMinutes(durationMs: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs)
    return "${minutes}m"
}

fun formatDuration(durationMs: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(durationMs)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60
    
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

fun exportSessionsToCsv(context: Context, sessions: List<SessionEntity>) {
    val csvString = StringBuilder()
    csvString.append("id,start_time,end_time,duration_seconds\n")
    
    val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    sessions.forEach { session ->
        val start = timeFormat.format(Date(session.startTime))
        val end = timeFormat.format(Date(session.endTime))
        val durationSec = session.durationMs / 1000
        csvString.append("${session.id},\"$start\",\"$end\",$durationSec\n")
    }

    try {
        val cacheDir = context.cacheDir
        val file = File(cacheDir, "pandan_sessions.csv")
        file.writeText(csvString.toString())
        
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "com.aizwal.pandanclone.fileprovider",
            file
        )
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        context.startActivity(Intent.createChooser(intent, "Export Session Logs"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
