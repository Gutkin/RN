package com.gutkin.frame.video

import org.junit.Assert.*
import org.junit.Test

class MediaLimitsTest {
    @Test fun accepts1080pPortraitAndLandscapeAtLimits() {
        assertNull(MediaLimits.problem(MediaLimits.MAX_BYTES, 1080, 1920, 60000, 60f))
        assertNull(MediaLimits.problem(1024, 1920, 1080, 1000, 29.97f))
    }
    @Test fun rejects4KAndOversizedShortEdge() {
        assertNotNull(MediaLimits.problem(1024, 3840, 2160, 1000, 30f))
        assertNotNull(MediaLimits.problem(1024, 1920, 1200, 1000, 30f))
    }
    @Test fun rejectsSizeDurationAndUnknownMetadata() {
        assertNotNull(MediaLimits.problem(MediaLimits.MAX_BYTES + 1, 1920, 1080, 1000, 30f))
        assertNotNull(MediaLimits.problem(1024, 1920, 1080, 60001, 30f))
        assertNotNull(MediaLimits.problem(-1, 1920, 1080, 1000, 30f))
        assertNotNull(MediaLimits.problem(1024, 0, 0, 1000, 30f))
        assertNotNull(MediaLimits.problem(1024, 1920, 1080, 1000, Float.NaN))
        assertNotNull(MediaLimits.problem(1024, 1920, 1080, 1000, 120f))
    }
    @Test fun trimCannotExceedSourceOrThirtySeconds() {
        assertTrue(MediaLimits.validTrim(0, 30000, 60000))
        assertTrue(MediaLimits.validTrim(59000, 60000, 60000))
        assertFalse(MediaLimits.validTrim(0, 30001, 60000))
        assertFalse(MediaLimits.validTrim(-1, 1000, 60000))
        assertFalse(MediaLimits.validTrim(59001, 60001, 60000))
        assertFalse(MediaLimits.validTrim(0, 999, 60000))
    }
}
