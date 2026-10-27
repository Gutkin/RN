package com.gutkin.frame.video

object MediaLimits {
    const val MAX_BYTES = 200L * 1024 * 1024
    const val MAX_SOURCE_MS = 60_000L
    const val MAX_EXPORT_MS = 30_000L
    fun problem(bytes: Long, width: Int, height: Int, durationMs: Long, fps: Float): String? = when {
        bytes <= 0 -> "Не удалось определить размер файла. Выберите локальное видео."
        bytes > MAX_BYTES -> "Файл больше 200 МБ. Сначала уменьшите его размер."
        width <= 0 || height <= 0 -> "Не удалось определить разрешение видео."
        maxOf(width, height) > 1920 || minOf(width, height) > 1080 -> "Максимум 1080p (1920 × 1080). Видео 4K не поддерживается."
        durationMs < 1000 || durationMs > MAX_SOURCE_MS -> "Длительность исходного видео — от 1 до 60 секунд."
        !fps.isFinite() || fps <= 0 -> "Не удалось определить частоту кадров. Пересохраните видео как MP4 H.264, 30 кадров/с."
        fps > 60.1f -> "Максимум 60 кадров/с. Отключите замедленную съёмку."
        else -> null
    }
    fun validTrim(start: Long, end: Long, duration: Long) = start >= 0 && end <= duration && end - start in 1000..MAX_EXPORT_MS
}
