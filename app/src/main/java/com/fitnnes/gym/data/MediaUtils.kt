package com.fitnnes.gym.data

import com.fitnnes.gym.workoutdomain.MediaType

/**
 * Utilidades para detectar e interpretar enlaces/URIs de media (imagen, video o YouTube)
 * pegados por el usuario o elegidos desde el dispositivo.
 */
object MediaUtils {

    private val VIDEO_EXTENSIONS = listOf(".mp4", ".mov", ".webm", ".mkv", ".3gp", ".m4v")

    /**
     * Detecta el tipo de media a partir de una URL/URI. [fromFilePicker] indica si el valor
     * viene del selector de archivos del dispositivo (en cuyo caso ya conocemos si es
     * imagen o video por el mime-type elegido) en lugar de un link pegado a mano.
     */
    fun detectMediaType(value: String, isVideoHint: Boolean = false): MediaType {
        val lower = value.lowercase()
        return when {
            isYoutubeUrl(lower) -> MediaType.YOUTUBE
            isVideoHint -> MediaType.VIDEO_FILE
            VIDEO_EXTENSIONS.any { lower.contains(it) } -> MediaType.VIDEO_FILE
            else -> MediaType.IMAGE_BASE64
        }
    }

    fun isYoutubeUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("youtube.com") || lower.contains("youtu.be")
    }

    /** Extrae el ID de video de una URL de YouTube (watch, youtu.be, embed o shorts). */
    fun youtubeVideoId(url: String): String? {
        val regex = Regex(
            "(?:youtube\\.com/(?:watch\\?v=|embed/|shorts/)|youtu\\.be/)([a-zA-Z0-9_-]{6,})"
        )
        return regex.find(url)?.groupValues?.getOrNull(1)
    }

    /** URL de embed lista para cargar en un WebView, o null si no es un link de YouTube válido. */
    fun youtubeEmbedUrl(url: String): String? {
        val id = youtubeVideoId(url) ?: return null
        return "https://www.youtube.com/embed/$id?autoplay=1&playsinline=1&rel=0&modestbranding=1"
    }
}
