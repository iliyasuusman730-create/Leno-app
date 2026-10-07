package com.example.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeUtils {
    fun formatLastSeen(isOnline: Boolean, lastSeenTimestamp: Long): String {
        if (isOnline) return "Online"
        if (lastSeenTimestamp <= 0) return "Offline"

        val now = System.currentTimeMillis()
        val diffMs = now - lastSeenTimestamp

        if (diffMs < 60_000) {
            return "Last seen just now"
        }
        val diffMins = diffMs / 60_000
        if (diffMins < 60) {
            return "Last seen ${diffMins}m ago"
        }
        val diffHours = diffMins / 60
        if (diffHours < 24) {
            return "Last seen ${diffHours}h ago"
        }

        val sdf = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
        return "Last seen " + sdf.format(Date(lastSeenTimestamp))
    }

    fun formatMessageTime(timestamp: Long): String {
        if (timestamp <= 0) return ""
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}
