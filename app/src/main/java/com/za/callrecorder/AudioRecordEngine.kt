package com.za.callrecorder

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AudioRecordEngine(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var currentTimestamp: String = ""
    private var currentExt: String = "m4a"
    private var isRecording = false
    private var enabledSpeakerByEngine = false
    private val handler = Handler(Looper.getMainLooper())

    fun startRecording(initialLabel: String, isCall: Boolean) {
        if (isRecording) return

        val prefs = context.getSharedPreferences("za_prefs", Context.MODE_PRIVATE)
        val format = prefs.getString("audio_format", "AAC") ?: "AAC"
        val autoSpeaker = prefs.getBoolean("auto_speakerphone", false)
        val preferredSource = prefs.getString("audio_source", "VOICE_RECOGNITION") ?: "VOICE_RECOGNITION"

        val safeLabel = initialLabel.replace(Regex("[^a-zA-Z0-9_+-]"), "_").ifBlank { "Call" }
        currentTimestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        currentExt = if (format == "AMR") "amr" else "m4a"

        val dir = File(context.getExternalFilesDir(null), "Recordings").apply { mkdirs() }
        val outputFile = File(dir, "${safeLabel}_$currentTimestamp.$currentExt")
        currentFile = outputFile

        // Delay slightly during call setup so Android telecom finishes audio routing
        val startDelay = if (isCall) 600L else 0L
        handler.postDelayed({
            if (isCall && autoSpeaker) {
                enableSpeakerphone(true)
            }

            val sourcesToTry = if (isCall) {
                buildSourceOrder(preferredSource)
            } else {
                listOf(MediaRecorder.AudioSource.MIC)
            }

            for (source in sourcesToTry) {
                if (tryStartRecorder(source, format, outputFile)) {
                    isRecording = true
                    break
                }
            }
        }, startDelay)
    }

    private fun buildSourceOrder(preferred: String): List<Int> {
        val primary = when (preferred) {
            "VOICE_COMMUNICATION" -> MediaRecorder.AudioSource.VOICE_COMMUNICATION
            "MIC" -> MediaRecorder.AudioSource.MIC
            "VOICE_CALL" -> MediaRecorder.AudioSource.VOICE_CALL
            else -> MediaRecorder.AudioSource.VOICE_RECOGNITION
        }
        return listOf(
            primary,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION
        ).distinct()
    }

    private fun tryStartRecorder(audioSource: Int, format: String, outputFile: File): Boolean {
        return try {
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
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
            e.printStackTrace()
            mediaRecorder?.release()
            mediaRecorder = null
            false
        }
    }

    fun stopRecording(isCall: Boolean, fallbackNumber: String?) {
        handler.removeCallbacksAndMessages(null)
        if (enabledSpeakerByEngine) {
            enableSpeakerphone(false)
        }

        val recordedFile = currentFile
        if (isRecording) {
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

        // Wait 1.5s for Android CallLog to save the call, then rename file with Caller Name & Number
        if (isCall && recordedFile != null && recordedFile.exists()) {
            val ts = currentTimestamp
            val ext = currentExt
            handler.postDelayed({
                val details = CallerInfoHelper.getLatestCallDetails(context, fallbackNumber)
                val newName = "${details.directionPrefix}_${details.displayName}_$ts.$ext"
                val targetFile = File(recordedFile.parentFile, newName)
                if (!targetFile.exists()) {
                    recordedFile.renameTo(targetFile)
                }
            }, 1500L)
        }
    }

    private fun enableSpeakerphone(enable: Boolean) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (enable) {
                    val speaker = audioManager.availableCommunicationDevices
                        .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    if (speaker != null) {
                        audioManager.setCommunicationDevice(speaker)
                        enabledSpeakerByEngine = true
                    }
                } else {
                    audioManager.clearCommunicationDevice()
                    enabledSpeakerByEngine = false
                }
            } else {
                @Suppress("DEPRECATION")
                if (enable && !audioManager.isSpeakerphoneOn) {
                    audioManager.mode = AudioManager.MODE_IN_CALL
                    audioManager.isSpeakerphoneOn = true
                    enabledSpeakerByEngine = true
                } else if (!enable) {
                    audioManager.isSpeakerphoneOn = false
                    enabledSpeakerByEngine = false
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
