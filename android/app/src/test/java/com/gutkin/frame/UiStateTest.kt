package com.gutkin.frame

import org.junit.Assert.*
import org.junit.Test

class UiStateTest {
    private val video = VideoData("sample", "file:///data/user/0/com.gutkin.frame/files/media/sample.mp4", 1920, 1080, 1024, 60000, 30f)
    @Test fun trimAcceptsThirtySecondsAndLastSecond() {
        assertNull(UiState(video = video, start = "1", length = "30").trimProblem)
        assertNull(UiState(video = video, start = "59", length = "1").trimProblem)
    }
    @Test fun invalidTimesCannotBeExported() {
        for ((start, length) in listOf("0" to "30.001", "-1" to "1", "59.001" to "1", "0" to ".999", "NaN" to "1", "0" to "Infinity", "" to "2", "9223372036854775807" to "2")) {
            assertNotNull("$start + $length must fail", UiState(video = video, start = start, length = length).trimProblem)
        }
    }
    @Test fun decimalCommaAndEditSettingsArePreservedInDraft() {
        val draft = UiState(video = video, start = "1,5", length = "2,25", title = "Привет", muted = true).draft()!!
        assertEquals(1500, draft.startMs); assertEquals(3750, draft.endMs)
        assertEquals("Привет", draft.title); assertTrue(draft.muted)
    }
    @Test fun editorOptionsTravelWithTheExportDraft() {
        val edits = EditSettings("bottom", 1.3f, "coral", "mono", true)
        edits.validate()
        val draft = UiState(video = video, edits = edits).draft()!!
        assertEquals(edits, draft.edits)
    }
    @Test fun invalidEditorSettingsAreRejected() {
        for (edits in listOf(EditSettings(textScale = Float.NaN), EditSettings(textScale = 10f), EditSettings(textPosition = "outside"), EditSettings(textColor = "unknown"), EditSettings(filter = "unknown"))) {
            try { edits.validate(); fail("Invalid edits must fail: $edits") } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun everyCollectionHasFiveUniqueValidTemplates() {
        for (name in listOf("Stories", "Reels", "Posts")) {
            assertEquals(5, Templates.collection(name).map { it.id }.toSet().size)
        }
        assertEquals("journal", Templates.collection("Stories")[0].id)
        assertEquals("bold", Templates.collection("Reels")[0].id)
        assertEquals("postcard", Templates.collection("Posts")[0].id)
    }
}
