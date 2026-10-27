package com.gutkin.frame

import com.gutkin.frame.video.MediaLimits

/** A handle may travel across the full source; the other follows if the window exceeds 30s. */
object EditorTrim {
    fun move(start: Long, end: Long, moveStart: Boolean, requested: Long, duration: Long): LongRange {
        require(MediaLimits.validTrim(start, end, duration))
        return if (moveStart) {
            val a = requested.coerceIn(0, end - 1000)
            a..minOf(end, a + MediaLimits.MAX_EXPORT_MS)
        } else {
            val b = requested.coerceIn(start + 1000, duration)
            maxOf(start, b - MediaLimits.MAX_EXPORT_MS)..b
        }
    }
}
