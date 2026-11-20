package com.za.callrecorder

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

data class CallDetails(
    val displayName: String,
    val number: String,
    val directionPrefix: String
)

object CallerInfoHelper {

    fun getLatestCallDetails(context: Context, fallbackNumber: String?): CallDetails {
        var number = fallbackNumber?.takeIf { it.isNotBlank() } ?: ""
        var cachedName = ""
        var direction = "CALL"

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG)
            == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                val projection = arrayOf(
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.TYPE
                )
                context.contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    projection,
                    null,
                    null,
                    "${CallLog.Calls.DATE} DESC"
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val logNum = cursor.getString(0) ?: ""
                        val logName = cursor.getString(1) ?: ""
                        val logType = cursor.getInt(2)
                        if (logNum.isNotBlank()) number = logNum
                        if (logName.isNotBlank()) cachedName = logName
                        direction = when (logType) {
                            CallLog.Calls.INCOMING_TYPE -> "IN"
                            CallLog.Calls.OUTGOING_TYPE -> "OUT"
                            else -> "CALL"
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (cachedName.isBlank() && number.isNotBlank()) {
            cachedName = lookupContactName(context, number)
        }

        val cleanNum = number.replace(Regex("[^0-9+]"), "")
        val label = when {
            cachedName.isNotBlank() && cleanNum.isNotBlank() -> "${cachedName}_$cleanNum"
            cachedName.isNotBlank() -> cachedName
            cleanNum.isNotBlank() -> cleanNum
            else -> "Unknown"
        }

        return CallDetails(
            displayName = sanitizeFileName(label),
            number = cleanNum,
            directionPrefix = direction
        )
    }

    fun lookupContactName(context: Context, phoneNumber: String): String {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return ""
        }
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) ?: "" else ""
            } ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun sanitizeFileName(input: String): String {
        return input.replace(Regex("[^a-zA-Z0-9_+ -]"), "").trim().replace(" ", "_")
            .ifBlank { "Unknown" }
    }
}
