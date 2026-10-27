package com.gutkin.frame.video

object MediaInputSupport {
    fun problem(mime: String?, decoderAvailable: Boolean): String? = when {
        mime !in setOf("video/avc", "video/hevc") -> "Этот видеокодек пока не поддерживается. Выберите видео H.264 или HEVC/H.265."
        !decoderAvailable -> "На этом телефоне нет декодера для этого видео. Пересохраните его в H.264, до 1080p."
        else -> null
    }
}
