package com.za.callrecorder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

class CallReceiver : BroadcastReceiver() {

    companion object {
        private var lastKnownNumber: String? = null
        private var isCallActive = false
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val prefs = context.getSharedPreferences("za_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("recording_enabled", true)) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        @Suppress("DEPRECATION")
        val numberExtra = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        if (!numberExtra.isNullOrBlank()) {
            lastKnownNumber = numberExtra
        }

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                // Number is often delivered here before OFFHOOK
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                if (!isCallActive) {
                    isCallActive = true
                    val label = lastKnownNumber ?: "Call"
                    val accService = CallAccessibilityService.instance
                    if (accService != null) {
                        // Record directly inside AccessibilityService to avoid Android 10+ muting
                        accService.startCallRecording(label)
                    } else {
                        val serviceIntent = Intent(context, CallRecorderService::class.java).apply {
                            action = CallRecorderService.ACTION_START
                            putExtra("PHONE_NUMBER", label)
                            putExtra("IS_CALL", true)
                        }
                        ContextCompat.startForegroundService(context, serviceIntent)
                    }
                }
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                if (isCallActive) {
                    isCallActive = false
                    val fallback = lastKnownNumber
                    lastKnownNumber = null
                    val accService = CallAccessibilityService.instance
                    if (accService != null) {
                        accService.stopCallRecording(fallback)
                    } else {
                        val serviceIntent = Intent(context, CallRecorderService::class.java).apply {
                            action = CallRecorderService.ACTION_STOP
                            putExtra("PHONE_NUMBER", fallback)
                            putExtra("IS_CALL", true)
                        }
                        context.startService(serviceIntent)
                    }
                }
            }
        }
    }
}
