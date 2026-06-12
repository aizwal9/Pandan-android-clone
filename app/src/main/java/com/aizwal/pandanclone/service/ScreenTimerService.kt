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
import android.content.pm.ServiceInfo
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ScreenTimerService : Service() {

    private val CHANNEL_ID = "ScreenTimerChannel"
    private val NOTIFICATION_ID = 1

    private var sessionStartTime: Long = 0L
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    
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
        createNotificationChannel()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        // Use RECEIVER_NOT_EXPORTED since Android 14 to receive system broadcasts
        val receiverFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Context.RECEIVER_NOT_EXPORTED
        } else {
            0
        }
        registerReceiver(screenReceiver, filter, receiverFlags)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        sessionStartTime = System.currentTimeMillis()
        val notification = createNotification(true, sessionStartTime)
        
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
        val notification = createNotification(true, sessionStartTime)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun handleScreenOff() {
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
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Screen Timer Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            serviceChannel.description = "Displays the current screen time"
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(screenReceiver)
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
