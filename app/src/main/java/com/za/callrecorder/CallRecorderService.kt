package com.za.callrecorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class CallRecorderService : Service() {

    private lateinit var engine: AudioRecordEngine

    companion object {
        const val ACTION_START = "ACTION_START_RECORDING"
        const val ACTION_STOP = "ACTION_STOP_RECORDING"
        const val CHANNEL_ID = "za_call_recorder_channel"
    }

    override fun onCreate() {
        super.onCreate()
        engine = AudioRecordEngine(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val label = intent.getStringExtra("PHONE_NUMBER") ?: "Call"
                val isCall = intent.getBooleanExtra("IS_CALL", false)
                startForeground(1, createNotification("Recording: $label"))
                engine.startRecording(label, isCall)
            }
            ACTION_STOP -> {
                val fallback = intent.getStringExtra("PHONE_NUMBER")
                val isCall = intent.getBooleanExtra("IS_CALL", false)
                engine.stopRecording(isCall, fallback)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun createNotification(contentText: String): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ZA Call Recorder",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ZA Call Recorder")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
