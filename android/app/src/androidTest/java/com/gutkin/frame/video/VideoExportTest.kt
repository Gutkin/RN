package com.gutkin.frame.video

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.content.ContentValues
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ActivityScenario
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.gutkin.frame.MainActivity
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
    private fun findNode(label: String): AccessibilityNodeInfo? {
        val queue = java.util.ArrayDeque<AccessibilityNodeInfo>()
        instrumentation.uiAutomation.rootInActiveWindow?.let { queue.add(it) }
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.contentDescription?.toString() == label) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
        }
        return null
    }
    private fun awaitNode(label: String, selected: Boolean? = null): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 10000
        while (SystemClock.uptimeMillis() < deadline) {
            val node = findNode(label)
            if (node != null && (selected == null || node.isSelected == selected)) return node
            SystemClock.sleep(100)
        }
        throw AssertionError("Missing UI control: $label, selected=$selected")
    }
    private fun tap(label: String) {
        assertTrue("Control must be clickable: $label", awaitNode(label).performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
    }
    @Test fun appStartsWithBundledJavaScript() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val deadline = SystemClock.uptimeMillis() + 45000
            var found = false
            while (SystemClock.uptimeMillis() < deadline && !found) {
                val queue = java.util.ArrayDeque<AccessibilityNodeInfo>()
                instrumentation.uiAutomation.rootInActiveWindow?.let { root -> queue.add(root) }
                while (queue.isNotEmpty()) {
                    val node = queue.removeFirst()
                    if (node.text?.toString()?.contains("Твой стиль.") == true) found = true
                    for (i in 0 until node.childCount) node.getChild(i)?.let { child -> queue.add(child) }
                }
                if (!found) SystemClock.sleep(250)
            }
            assertTrue("Bundled React Native home screen did not render", found)
            awaitNode("Stories", true); awaitNode("Reels", false); awaitNode("Posts", false)
            for (i in 1..5) awaitNode("Слайд $i из 5", i == 1)
            awaitNode("Открыть Sunday Journal")
            tap("Reels"); awaitNode("Reels", true); awaitNode("Открыть Make It Bold")
            tap("Слайд 3 из 5"); awaitNode("Слайд 3 из 5", true); awaitNode("Открыть Sunday Journal")
            tap("Posts"); awaitNode("Posts", true); awaitNode("Слайд 1 из 5", true); awaitNode("Открыть Coastal Days")
            tap("Stories"); awaitNode("Stories", true); awaitNode("Открыть Sunday Journal")
            instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                // Public test output survives target-app uninstall at the end of connected tests.
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "frame-home.png")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/FRAME-tests")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
                try {
                    context.contentResolver.openOutputStream(uri).use { output -> bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output!!) }
                    context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                } finally { bitmap.recycle() }
            }
            tap("Слайд 2 из 5"); awaitNode("Слайд 2 из 5", true)
            tap("Открыть Quiet Moment")
            awaitNode("+ Добавить видео")
        }
    }
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
    @Test fun acceptsActualHevcFromCamera() {
        lateinit var module: VideoModule
        instrumentation.runOnMainSync { module = VideoModule(BridgeReactContext(context)) }
        try {
            val info = module.inspect(Uri.fromFile(fixture("sample-hevc.mp4")))
            assertEquals("video/hevc", info.getString("mime"))
            assertEquals(640, info.getInt("width"))
        } finally { instrumentation.runOnMainSync { module.invalidate() } }
    }
    @Test fun exportsTrimmedPortraitWithTextAndWithoutAudio() = assertNativeExport("sample.mp4")
    @Test fun convertsHevcToCompatibleH264() = assertNativeExport("sample-hevc.mp4")
    private fun assertNativeExport(name: String) {
        val file = fixture(name)
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
                    val encodedWidth = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)!!.toInt()
                    val encodedHeight = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)!!.toInt()
                    val rotation = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toInt() ?: 0
                    assertTrue("Invalid MP4 rotation: $rotation", rotation in listOf(0, 90, 180, 270))
                    // Media3 defaults to landscape encoding + MP4 rotation for encoder compatibility.
                    // Check displayed geometry, keeping the full 1080x1920 resolution requirement.
                    val displayWidth = if (rotation % 180 == 0) encodedWidth else encodedHeight
                    val displayHeight = if (rotation % 180 == 0) encodedHeight else encodedWidth
                    assertEquals(1080, displayWidth)
                    assertEquals(1920, displayHeight)
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
