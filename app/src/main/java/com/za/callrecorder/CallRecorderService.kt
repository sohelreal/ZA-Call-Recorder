package com.za.callrecorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CallRecorderService : Service() {

    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var enabledSpeakerByService = false

    companion object {
        const val ACTION_START = "ACTION_START_RECORDING"
        const val ACTION_STOP = "ACTION_STOP_RECORDING"
        const val CHANNEL_ID = "za_call_recorder_channel"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val label = intent.getStringExtra("PHONE_NUMBER") ?: "Call"
                val isCall = intent.getBooleanExtra("IS_CALL", false)
                startForeground(1, createNotification("Recording: $label"))
                startRecording(label, isCall)
            }
            ACTION_STOP -> {
                stopRecording()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startRecording(label: String, isCall: Boolean) {
        if (isRecording) return
        val prefs = getSharedPreferences("za_prefs", MODE_PRIVATE)
        val format = prefs.getString("audio_format", "AAC") ?: "AAC"
        val autoSpeaker = prefs.getBoolean("auto_speakerphone", false)

        if (isCall && autoSpeaker) {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (!audioManager.isSpeakerphoneOn) {
                audioManager.mode = AudioManager.MODE_IN_CALL
                audioManager.isSpeakerphoneOn = true
                enabledSpeakerByService = true
            }
        }

        val safeLabel = label.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val ext = if (format == "AMR") "amr" else "m4a"

        val dir = File(getExternalFilesDir(null), "Recordings").apply { mkdirs() }
        val outputFile = File(dir, "${safeLabel}_$timestamp.$ext")

        // Fallback chain to maximize two-sided capture across different Android OEMs
        val sourcesToTry = if (isCall) {
            listOf(
                MediaRecorder.AudioSource.VOICE_CALL,
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MediaRecorder.AudioSource.MIC
            )
        } else {
            listOf(MediaRecorder.AudioSource.MIC)
        }

        for (source in sourcesToTry) {
            if (tryStartRecorder(source, format, outputFile)) {
                isRecording = true
                return
            }
        }

        stopSelf()
    }

    private fun tryStartRecorder(audioSource: Int, format: String, outputFile: File): Boolean {
        return try {
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            recorder.apply {
                setAudioSource(audioSource)
                if (format == "AMR") {
                    setOutputFormat(MediaRecorder.OutputFormat.AMR_NB)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                } else {
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioEncodingBitRate(128000)
                    setAudioSamplingRate(44100)
                }
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }
            mediaRecorder = recorder
            true
        } catch (e: Exception) {
            mediaRecorder?.release()
            mediaRecorder = null
            false
        }
    }

    private fun stopRecording() {
        if (enabledSpeakerByService) {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.isSpeakerphoneOn = false
            enabledSpeakerByService = false
        }
        if (!isRecording) return
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaRecorder = null
            isRecording = false
        }
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
