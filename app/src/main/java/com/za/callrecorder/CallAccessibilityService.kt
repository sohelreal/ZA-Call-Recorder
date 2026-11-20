package com.za.callrecorder

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Runs MediaRecorder directly inside the AccessibilityService context using
 * VOICE_RECOGNITION so Android 10+ (including Pixel 7) does not silence the recording.
 */
class CallAccessibilityService : AccessibilityService() {

    private var engine: AudioRecordEngine? = null

    companion object {
        @Volatile
        var instance: CallAccessibilityService? = null

        val isRunning: Boolean
            get() = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        engine = AudioRecordEngine(this)
    }

    fun startCallRecording(initialLabel: String) {
        engine?.startRecording(initialLabel, isCall = true)
    }

    fun stopCallRecording(fallbackNumber: String?) {
        engine?.stopRecording(isCall = true, fallbackNumber = fallbackNumber)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        engine?.stopRecording(isCall = false, fallbackNumber = null)
        instance = null
    }
}
