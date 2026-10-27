package com.gutkin.frame.video

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.facebook.react.bridge.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class VideoExportTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun fixture(name: String): File = File(context.filesDir, "media/$name").apply {
        parentFile!!.mkdirs()
        instrumentation.context.assets.open(name).use { input -> outputStream().use { input.copyTo(it) } }
    }
    @Test fun validatesActualVideoAndRejects4K() {
        lateinit var module: VideoModule
        instrumentation.runOnMainSync { module = VideoModule(BridgeReactContext(context)) }
        try {
            val info = module.inspect(Uri.fromFile(fixture("sample.mp4")))
            assertEquals(640, info.getInt("width"))
            assertEquals(360, info.getInt("height"))
            assertTrue(info.getDouble("fps") in 23.0..25.0)
            try { module.inspect(Uri.fromFile(fixture("4k.mp4"))); fail("4K import must be rejected") }
            catch (e: IllegalStateException) { assertTrue(e.message!!.contains("1080p")) }
        } finally { instrumentation.runOnMainSync { module.invalidate() } }
    }
    @Test fun exportsTrimmedPortraitWithTextAndWithoutAudio() {
        val file = fixture("sample.mp4")
        lateinit var module: VideoModule
        instrumentation.runOnMainSync { module = VideoModule(BridgeReactContext(context)) }
        val done = CountDownLatch(1)
        var result: String? = null
        var failure: String? = null
        val resolve = object : Callback { override fun invoke(vararg args: Any?) { result = args.firstOrNull() as? String; done.countDown() } }
        val reject = object : Callback { override fun invoke(vararg args: Any?) { failure = args.joinToString(); done.countDown() } }
        val options = Arguments.createMap().apply {
            putString("uri", Uri.fromFile(file).toString()); putDouble("startMs", 500.0); putDouble("endMs", 2500.0)
            putString("style", "journal"); putString("title", "ТЕСТ FRAME"); putBoolean("muted", true)
        }
        try {
            module.exportVideo(options, PromiseImpl(resolve, reject))
            assertTrue("Timed out exporting native video", done.await(180, TimeUnit.SECONDS))
            assertNull("Native export rejected: $failure", failure)
            assertNotNull(result)
            val uri = Uri.parse(result)
            try {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(context, uri)
                    assertEquals("1080", r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH))
                    assertEquals("1920", r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT))
                    val duration = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
                    assertTrue("Unexpected output duration: $duration", duration in 1800..2200)
                    val frame = r.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    assertNotNull("Output video must be decodable", frame)
                    frame?.recycle()
                } finally { r.release() }
                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(context, uri, null)
                    val tracks = (0 until extractor.trackCount).map { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME) ?: "" }
                    assertTrue(tracks.any { it == "video/avc" })
                    assertFalse("Muted export must not contain audio", tracks.any { it.startsWith("audio/") })
                } finally { extractor.release() }
            } finally { context.contentResolver.delete(uri, null, null) }
        } finally { instrumentation.runOnMainSync { module.invalidate() } }
    }
}
