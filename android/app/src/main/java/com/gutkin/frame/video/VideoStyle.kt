package com.gutkin.frame.video

import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.*
import androidx.media3.common.Effect
import androidx.media3.effect.*

object VideoStyle {
    val ids = setOf("journal", "minimal", "film", "bold", "postcard")
    fun effects(style: String, title: String): List<Effect> {
        val effects = mutableListOf<Effect>(Presentation.createForWidthAndHeight(1080, 1920, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP))
        if (style == "film") effects.add(RgbFilter.createGrayscaleFilter())
        if (title.isNotBlank()) {
            val text = SpannableString(title.take(45))
            fun span(value: Any) = text.setSpan(value, 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            span(ForegroundColorSpan(if (style == "bold") Color.rgb(255, 98, 79) else Color.rgb(255, 250, 240)))
            span(AbsoluteSizeSpan(if (style == "bold") 100 else 78))
            span(TypefaceSpan(if (style in setOf("journal", "postcard")) "serif" else "sans-serif"))
            if (style == "bold") span(StyleSpan(Typeface.BOLD))
            span(BackgroundColorSpan(Color.argb(95, 0, 0, 0)))
            val y = when (style) { "journal" -> 0.65f; "postcard" -> -0.62f; else -> 0f }
            val settings = OverlaySettings.Builder().setBackgroundFrameAnchor(0f, y).build()
            effects.add(OverlayEffect(listOf(TextOverlay.createStaticTextOverlay(text, settings))))
        }
        return effects
    }
}
