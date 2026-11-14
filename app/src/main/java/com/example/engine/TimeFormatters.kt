package com.example.engine

import java.util.Locale

object TimeFormatters {
    fun formatShort(ms: Long): String {
        val safe = ms.coerceAtLeast(0L)
        val totalSeconds = safe / 1000L
        val tenths = (safe % 1000L) / 100L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d.%d", hours, minutes, seconds, tenths)
        } else {
            String.format(Locale.US, "%02d:%02d.%d", minutes, seconds, tenths)
        }
    }

    fun formatSrt(ms: Long): String {
        val safe = ms.coerceAtLeast(0L)
        val hours = safe / 3_600_000L
        val minutes = (safe % 3_600_000L) / 60_000L
        val seconds = (safe % 60_000L) / 1_000L
        val millis = safe % 1_000L
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
    }

    fun formatVtt(ms: Long): String {
        val safe = ms.coerceAtLeast(0L)
        val hours = safe / 3_600_000L
        val minutes = (safe % 3_600_000L) / 60_000L
        val seconds = (safe % 60_000L) / 1_000L
        val millis = safe % 1_000L
        return String.format(Locale.US, "%02d:%02d:%02d.%03d", hours, minutes, seconds, millis)
    }

    fun parseShortTimeToMs(input: String): Long? {
        val trimmed = input.trim()
        val parts = trimmed.split(":")
        return try {
            when (parts.size) {
                2 -> {
                    val mins = parts[0].toLong()
                    val secsDouble = parts[1].toDouble()
                    ((mins * 60.0 + secsDouble) * 1000.0).toLong().coerceAtLeast(0L)
                }
                3 -> {
                    val hrs = parts[0].toLong()
                    val mins = parts[1].toLong()
                    val secsDouble = parts[2].toDouble()
                    ((hrs * 3600.0 + mins * 60.0 + secsDouble) * 1000.0).toLong().coerceAtLeast(0L)
                }
                else -> trimmed.toDoubleOrNull()?.let { (it * 1000.0).toLong().coerceAtLeast(0L) }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun formatEta(seconds: Long): String {
        if (seconds < 0L) return "Ginti chal rahi hai..."
        val m = seconds / 60L
        val s = seconds % 60L
        return when {
            m >= 60 -> "${m / 60}h ${m % 60}m baaki"
            m > 0 -> "${m}m ${s}s baaki"
            else -> "${s}s baaki"
        }
    }
}
