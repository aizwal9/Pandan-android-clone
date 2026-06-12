package com.aizwal.pandanclone.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.aizwal.pandanclone.MainActivity
import com.aizwal.pandanclone.R
import com.aizwal.pandanclone.data.AppDatabase
import com.aizwal.pandanclone.data.SessionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ScreenTimerService : Service() {

    private val CHANNEL_ID = "ScreenTimerChannel"
    private val ALERT_CHANNEL_ID = "ScreenTimerAlertChannel_v2"
    private val NOTIFICATION_ID = 1
    private val ALERT_NOTIFICATION_ID = 2

    private var sessionStartTime: Long = 0L
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var limitJob: Job? = null
    
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "session_limit_minutes") {
            if (sessionStartTime > 0L) {
                startLimitTracker()
            }
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_ON) {
                handleScreenOn()
            } else if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                handleScreenOff()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        
        val prefs = getSharedPreferences("PandanPrefs", Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(prefListener)

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        val receiverFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Context.RECEIVER_NOT_EXPORTED
        } else {
            0
        }
        registerReceiver(screenReceiver, filter, receiverFlags)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        sessionStartTime = System.currentTimeMillis()
        updateActiveSessionPref(sessionStartTime)
        val notification = createNotification(true, sessionStartTime)
        
        startLimitTracker()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this, 
                NOTIFICATION_ID, 
                notification, 
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) 
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE 
                else 
                    0
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        return START_STICKY
    }

    private fun handleScreenOn() {
        sessionStartTime = System.currentTimeMillis()
        updateActiveSessionPref(sessionStartTime)
        val notification = createNotification(true, sessionStartTime)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
        
        startLimitTracker()
    }

    private fun handleScreenOff() {
        cancelLimitTracker()
        updateActiveSessionPref(0L)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(ALERT_NOTIFICATION_ID)

        val sessionEndTime = System.currentTimeMillis()
        val duration = sessionEndTime - sessionStartTime
        
        if (sessionStartTime > 0 && duration > 1000) {
            val session = SessionEntity(
                startTime = sessionStartTime,
                endTime = sessionEndTime,
                durationMs = duration
            )
            serviceScope.launch {
                AppDatabase.getDatabase(applicationContext).sessionDao().insertSession(session)
            }
        }
        
        sessionStartTime = 0L
        val notification = createNotification(false, 0L)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun updateActiveSessionPref(startTime: Long) {
        val prefs = getSharedPreferences("PandanPrefs", Context.MODE_PRIVATE)
        prefs.edit().putLong("current_session_start_time", startTime).apply()
    }

    private fun startLimitTracker() {
        limitJob?.cancel()
        val prefs = getSharedPreferences("PandanPrefs", Context.MODE_PRIVATE)
        val limitMinutes = prefs.getInt("session_limit_minutes", 20)
        
        if (limitMinutes > 0) {
            limitJob = serviceScope.launch {
                delay(limitMinutes * 60 * 1000L)
                triggerLimitAlert(limitMinutes)
            }
        }
    }

    private fun cancelLimitTracker() {
        limitJob?.cancel()
        limitJob = null
    }

    private fun triggerLimitAlert(limitMinutes: Int) {
        val pendingIntent: PendingIntent =
            Intent(this, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(
                    this, 0, notificationIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            }

        val soundUri = Uri.parse("android.resource://" + packageName + "/" + R.raw.limit_alert)

        val builder = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle("Session Limit Reached!")
            .setContentText("You have been using your device for $limitMinutes minutes. Consider taking a break.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 250, 250, 250))

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(ALERT_NOTIFICATION_ID, builder.build())
    }

    private fun createNotification(isScreenOn: Boolean, startTime: Long): Notification {
        val pendingIntent: PendingIntent =
            Intent(this, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(
                    this, 0, notificationIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(if (isScreenOn) "Screen Time" else "Screen Off - Waiting")
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)

        if (isScreenOn) {
            builder.setUsesChronometer(true)
            builder.setWhen(startTime)
        } else {
            builder.setUsesChronometer(false)
            builder.setContentText("Waiting for screen on...")
        }

        return builder.build()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Screen Timer Service Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Displays the current screen time"
            }

            val soundUri = Uri.parse("android.resource://" + packageName + "/" + R.raw.limit_alert)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .build()

            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "Screen Timer Alert Channel",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when screen session exceeds limit"
                enableLights(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 250, 250)
                setSound(soundUri, audioAttributes)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
            manager.createNotificationChannel(alertChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(screenReceiver)
        val prefs = getSharedPreferences("PandanPrefs", Context.MODE_PRIVATE)
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        prefs.edit().putLong("current_session_start_time", 0L).apply()
        cancelLimitTracker()
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
