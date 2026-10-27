package com.gutkin.frame.video

import org.junit.Assert.*
import org.junit.Test

class MediaInputSupportTest {
    @Test fun acceptsAvcAndHevcWhenDecodable() {
        assertNull(MediaInputSupport.problem("video/avc", true))
        assertNull(MediaInputSupport.problem("video/hevc", true))
    }
    @Test fun givesDeviceSpecificErrorForMissingDecoder() {
        assertTrue(MediaInputSupport.problem("video/hevc", false)!!.contains("телефоне"))
        assertNotNull(MediaInputSupport.problem("video/avc", false))
    }
    @Test fun doesNotAcceptUnimplementedCodecs() {
        assertNotNull(MediaInputSupport.problem("video/unknown", true))
        assertNotNull(MediaInputSupport.problem(null, true))
    }
}
