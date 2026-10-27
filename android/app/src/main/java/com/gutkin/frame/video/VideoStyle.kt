package com.gutkin.frame.video

import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.*
import com.gutkin.frame.EditSettings
import androidx.media3.common.Effect
import androidx.media3.effect.*

object VideoStyle {
    val ids = setOf("journal", "minimal", "film", "bold", "postcard")
    fun effects(style: String, title: String, edits: EditSettings = EditSettings()): List<Effect> {
        edits.validate()
        val effects = mutableListOf<Effect>(Presentation.createForWidthAndHeight(1080, 1920, if (edits.fit) Presentation.LAYOUT_SCALE_TO_FIT else Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP))
        if (edits.filter == "mono" || (edits.filter == "template" && style == "film")) effects.add(RgbFilter.createGrayscaleFilter())
        if (title.isNotBlank()) {
            val text = SpannableString(title.take(45))
            fun span(value: Any) = text.setSpan(value, 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            val color = when (edits.textColor) {
                "white" -> Color.WHITE; "black" -> Color.BLACK; "coral" -> Color.rgb(255, 98, 79)
                else -> if (style == "bold") Color.rgb(255, 98, 79) else Color.rgb(255, 250, 240)
            }
            span(ForegroundColorSpan(color))
            span(AbsoluteSizeSpan(((if (style == "bold") 100 else 78) * edits.textScale).toInt()))
            span(TypefaceSpan(if (style in setOf("journal", "postcard")) "serif" else "sans-serif"))
            if (style == "bold") span(StyleSpan(Typeface.BOLD))
            span(BackgroundColorSpan(Color.argb(95, 0, 0, 0)))
            val y = when (edits.textPosition) {
                "top" -> .65f; "center" -> 0f; "bottom" -> -.65f
                else -> when (style) { "journal" -> .65f; "postcard" -> -.62f; else -> 0f }
            }
            val settings = OverlaySettings.Builder().setBackgroundFrameAnchor(0f, y).build()
            effects.add(OverlayEffect(listOf(TextOverlay.createStaticTextOverlay(text, settings))))
        }
        return effects
    }
}
