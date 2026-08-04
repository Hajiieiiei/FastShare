package com.fastshare.app.core.util

import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow

/** Binary (IEC) byte formatting: 1024-based, matching what file managers report. */
fun Long.formatBytes(locale: Locale = Locale.getDefault()): String {
    if (this < 0) return "0 B"
    if (this < 1024) return "$this B"
    val units = arrayOf("KB", "MB", "GB", "TB", "PB")
    val exp = (ln(toDouble()) / ln(1024.0)).toInt().coerceIn(1, units.size)
    val value = this / 1024.0.pow(exp.toDouble())
    val pattern = if (value < 10) "%.2f %s" else if (value < 100) "%.1f %s" else "%.0f %s"
    return String.format(locale, pattern, value, units[exp - 1])
}

fun Long.formatSpeed(locale: Locale = Locale.getDefault()): String = "${formatBytes(locale)}/s"

/** Human readable duration; returns null-safe placeholder for unknown ETA. */
fun Long.formatDuration(): String {
    if (this <= 0) return "--:--"
    val totalSeconds = this / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

fun Float.formatPercent(): String = String.format(Locale.US, "%.0f%%", (this * 100f).coerceIn(0f, 100f))

/**
 * Formats a fingerprint as human-verifiable groups, e.g. `A1B2 C3D4 E5F6 7890`.
 * Truncated to [groups] groups of 4 hex chars which is what the pairing UI shows.
 */
fun String.formatFingerprint(groups: Int = 4): String =
    filter { !it.isWhitespace() && it != ':' }
        .uppercase(Locale.US)
        .take(groups * 4)
        .chunked(4)
        .joinToString(" ")

fun Long.relativeTimeLabel(now: Long = System.currentTimeMillis()): String {
    val delta = abs(now - this)
    val minutes = delta / 60_000
    val hours = delta / 3_600_000
    val days = delta / 86_400_000
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> "${days / 7}w ago"
    }
}
