package com.za.callrecorder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

class CallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val prefs = context.getSharedPreferences("za_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("recording_enabled", true)) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: "Call"

        when (state) {
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                val serviceIntent = Intent(context, CallRecorderService::class.java).apply {
                    action = CallRecorderService.ACTION_START
                    putExtra("PHONE_NUMBER", incomingNumber)
                    putExtra("IS_CALL", true)
                }
                ContextCompat.startForegroundService(context, serviceIntent)
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                val serviceIntent = Intent(context, CallRecorderService::class.java).apply {
                    action = CallRecorderService.ACTION_STOP
                }
                context.startService(serviceIntent)
            }
        }
    }
}
