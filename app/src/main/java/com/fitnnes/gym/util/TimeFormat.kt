package com.fitnnes.gym.util

object TimeFormat {

    /** Formatea segundos como MM:SS, p.ej. 75 -> "01:15". */
    fun mmss(totalSeconds: Int): String {
        val safe = totalSeconds.coerceAtLeast(0)
        val mins = safe / 60
        val secs = safe % 60
        return String.format("%02d:%02d", mins, secs)
    }

    /** Formatea segundos de forma legible corta, p.ej. 90 -> "1min 30s", 45 -> "45s". */
    fun readable(totalSeconds: Int): String {
        val safe = totalSeconds.coerceAtLeast(0)
        val mins = safe / 60
        val secs = safe % 60
        return when {
            mins <= 0 -> "${secs}s"
            secs == 0 -> "${mins}min"
            else -> "${mins}min ${secs}s"
        }
    }
}
