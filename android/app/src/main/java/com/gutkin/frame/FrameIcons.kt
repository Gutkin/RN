package com.gutkin.frame

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Only the three extra glyphs used by FRAME; avoids packaging the entire extended icon set. */
object FrameIcons {
    val Grid = ImageVector.Builder("Templates", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f) {
            moveTo(3f, 3f); lineTo(10f, 3f); lineTo(10f, 10f); lineTo(3f, 10f); close()
            moveTo(14f, 3f); lineTo(21f, 3f); lineTo(21f, 10f); lineTo(14f, 10f); close()
            moveTo(3f, 14f); lineTo(10f, 14f); lineTo(10f, 21f); lineTo(3f, 21f); close()
            moveTo(14f, 14f); lineTo(21f, 14f); lineTo(21f, 21f); lineTo(14f, 21f); close()
        }
    }.build()
    val Folder = ImageVector.Builder("Projects", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f) {
            moveTo(3f, 6f); lineTo(9f, 6f); lineTo(11f, 8f); lineTo(21f, 8f); lineTo(21f, 20f); lineTo(3f, 20f); close()
            moveTo(3f, 6f); lineTo(3f, 4f); lineTo(9f, 4f); lineTo(11f, 6f); lineTo(21f, 6f); lineTo(21f, 8f)
        }
    }.build()
    val Settings = ImageVector.Builder("Settings", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f) {
            moveTo(3f, 6f); lineTo(8f, 6f); moveTo(12f, 6f); lineTo(21f, 6f)
            moveTo(8f, 4f); lineTo(12f, 4f); lineTo(12f, 8f); lineTo(8f, 8f); close()
            moveTo(3f, 12f); lineTo(12f, 12f); moveTo(16f, 12f); lineTo(21f, 12f)
            moveTo(12f, 10f); lineTo(16f, 10f); lineTo(16f, 14f); lineTo(12f, 14f); close()
            moveTo(3f, 18f); lineTo(6f, 18f); moveTo(10f, 18f); lineTo(21f, 18f)
            moveTo(6f, 16f); lineTo(10f, 16f); lineTo(10f, 20f); lineTo(6f, 20f); close()
        }
    }.build()
}
