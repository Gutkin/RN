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
import com.gutkin.frame.FrameViewModel
import androidx.lifecycle.ViewModelProvider
import android.view.View
import android.view.ViewGroup
import androidx.media3.ui.PlayerView
import androidx.media3.common.Player
import com.gutkin.frame.Project
import com.gutkin.frame.EditSettings
import androidx.compose.ui.semantics.SemanticsActions
import com.gutkin.frame.ProjectStore
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class VideoExportTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    @get:Rule val compose = createEmptyComposeRule()
    private fun awaitNode(label: String, selected: Boolean? = null): SemanticsNodeInteraction {
        compose.waitUntil(10000) {
            compose.onAllNodesWithContentDescription(label).fetchSemanticsNodes().any { node ->
                selected == null || (node.config.contains(SemanticsProperties.Selected) && node.config[SemanticsProperties.Selected] == selected)
            }
        }
        val node = compose.onNodeWithContentDescription(label).assertExists()
        if (selected == true) node.assertIsSelected()
        if (selected == false) node.assertIsNotSelected()
        return node
    }
    private fun tap(label: String) { awaitNode(label).performClick(); compose.waitForIdle() }
    private fun playerView(view: View): PlayerView? {
        if (view is PlayerView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) playerView(view.getChildAt(i))?.let { return it }
        return null
    }
    @Test fun composeScreensAndNativePlaybackWork() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
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
            assertTrue("Native Compose home screen did not render", found)
            awaitNode("Stories", true); awaitNode("Reels", false); awaitNode("Posts", false)
            for (i in 1..5) awaitNode("Слайд $i из 5", i == 1)
            awaitNode("Открыть Sunday Journal")
            tap("Reels"); awaitNode("Reels", true); awaitNode("Открыть Make It Bold")
            tap("Слайд 3 из 5"); awaitNode("Слайд 3 из 5", true); awaitNode("Открыть Sunday Journal")
            tap("Posts"); awaitNode("Posts", true); awaitNode("Слайд 1 из 5", true); awaitNode("Открыть Coastal Days")
            tap("Stories"); awaitNode("Stories", true); awaitNode("Открыть Sunday Journal")
            screenshot("frame-home.png")
            tap("Слайд 2 из 5"); awaitNode("Слайд 2 из 5", true)
            tap("Открыть Quiet Moment")
            awaitNode("+ Добавить видео")
            // Exercise the new ActivityResult-independent UI state path and the real native player.
            val source = fixture("sample.mp4")
            scenario.onActivity { ViewModelProvider(it)[FrameViewModel::class.java].importVideo(Uri.fromFile(source)) }
            awaitNode("Заменить видео")
            val readyDeadline = SystemClock.uptimeMillis() + 30000
            var ready = false
            while (SystemClock.uptimeMillis() < readyDeadline && !ready) {
                compose.mainClock.advanceTimeBy(200)
                scenario.onActivity { activity ->
                    val player = playerView(activity.window.decorView)?.player
                    ready = player?.playbackState == Player.STATE_READY && player.playerError == null
                    if (ready) player?.play()
                }
                if (!ready) SystemClock.sleep(200)
            }
            assertTrue("Compose editor must prepare the real video player", ready)
            SystemClock.sleep(350)
            scenario.onActivity { activity ->
                val player = playerView(activity.window.decorView)?.player!!
                assertNull(player.playerError)
                assertTrue("Native video must actually advance", player.currentPosition > 0)
                player.pause()
            }
            tap("Воспроизвести")
            compose.waitUntil(5000) { compose.onAllNodesWithContentDescription("Пауза").fetchSemanticsNodes().isNotEmpty() }
            tap("Пауза")
            compose.onNodeWithContentDescription("Позиция воспроизведения").performSemanticsAction(SemanticsActions.SetProgress) { it(750f) }
            scenario.onActivity { activity ->
                assertTrue(playerView(activity.window.decorView)!!.player!!.currentPosition in 650L..850L)
            }
            // Real controls, not a ViewModel-only setup: change every persistent visual option.
            tap("Инструмент Текст")
            compose.onNodeWithText("Заголовок").performTextReplacement("МОЙ МОНТАЖ")
            compose.onNodeWithContentDescription("Положение текста: Снизу").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Размер текста").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(1.3f) }
            compose.onNodeWithContentDescription("Цвет текста: Коралловый").performScrollTo().performClick()
            tap("Инструмент Стиль")
            compose.onNodeWithContentDescription("Фильтр: Ч/б").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Масштаб: Вписать целиком").performScrollTo().performClick()
            tap("Инструмент Звук")
            tap("Отключить звук оригинала")
            tap("Инструмент Монтаж")
            awaitNode("Границы фрагмента")
            // Move both handles through their accessibility actions, as a user would.
            val handles = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress) and hasAnyAncestor(hasContentDescription("Границы фрагмента")), useUnmergedTree = true)
            handles.assertCountEquals(2)
            handles[0].performSemanticsAction(SemanticsActions.SetProgress) { it(500f) }
            handles[1].performSemanticsAction(SemanticsActions.SetProgress) { it(2500f) }
            scenario.onActivity { activity ->
                val model = ViewModelProvider(activity)[FrameViewModel::class.java]
                assertEquals(EditSettings("bottom", 1.3f, "coral", "mono", true), model.state.edits)
                assertEquals("МОЙ МОНТАЖ", model.state.title)
                assertTrue(model.state.muted)
            }
            compose.onNodeWithText("Начало, сек").performScrollTo().performTextReplacement("0.5")
            compose.onNodeWithText("Длина, сек").performScrollTo().performTextReplacement("2")
            compose.onNodeWithText("Длина, сек").performImeAction()
            compose.waitForIdle()
            compose.onNodeWithContentDescription("Лента кадров").performScrollTo()
            compose.mainClock.advanceTimeBy(700)
            SystemClock.sleep(700)
            screenshot("frame-editor.png")
            compose.onNodeWithContentDescription("Сохранить проект").performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("OK").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("OK").performClick()
            compose.onNodeWithContentDescription("Назад").performClick()
            tap("Шаблоны")
            compose.onNodeWithText("Найди свой стиль").assertIsDisplayed()
            compose.onNode(hasSetTextAction()).performTextInput("Film")
            compose.onNodeWithText("Film Diary").assertExists()
            tap("Проекты")
            tap("Продолжить проект")
            awaitNode("Заменить видео")
            scenario.onActivity { activity ->
                val restored = ViewModelProvider(activity)[FrameViewModel::class.java].state
                assertEquals(EditSettings("bottom", 1.3f, "coral", "mono", true), restored.edits)
                assertEquals(500L, restored.startMs); assertEquals(2500L, restored.endMs)
                assertTrue(restored.muted)
            }
            compose.onNodeWithContentDescription("Назад").performClick()
            tap("О приложении")
            compose.onNodeWithText("Просто о важном").assertIsDisplayed()
        }
    }
    private fun screenshot(name: String) {
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/FRAME-tests")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
            try {
                context.contentResolver.openOutputStream(uri).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it!!) }
                context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            } finally { bitmap.recycle() }
        }
    }
    @Test fun editorSettingsRoundTripAndLegacyDefaultsWork() {
        val source = fixture("sample.mp4")
        val engine = VideoEngine(context)
        try {
            val p = Project("journal", "МОНТАЖ", engine.inspect(Uri.fromFile(source)), 0, 2000, false, edits = EditSettings("top", .8f, "white", "original", true))
            assertEquals(p, Project.parse(p.json()))
            val legacy = JSONObject(p.json()).apply { remove("edits") }.toString()
            assertEquals(EditSettings(), Project.parse(legacy).edits)
        } finally { instrumentation.runOnMainSync { engine.close() } }
    }
    private fun fixture(name: String): File = File(context.filesDir, "media/$name").apply {
        parentFile!!.mkdirs()
        instrumentation.context.assets.open(name).use { input -> outputStream().use { input.copyTo(it) } }
    }
    @Test fun importsHevcWithoutChangingTheSource() {
        val source = fixture("sample-hevc.mp4")
        val engine = VideoEngine(context)
        try {
            val imported = engine.importVideo(Uri.fromFile(source))
            val copy = File(Uri.parse(imported.uri).path!!)
            try {
                assertEquals("video/hevc", imported.mime)
                assertTrue(copy.canonicalPath.startsWith(File(context.filesDir, "media").canonicalPath + "/"))
                assertArrayEquals(source.readBytes(), copy.readBytes())
                assertTrue(source.exists())
            } finally { copy.delete(); imported.thumbnail?.let { File(Uri.parse(it).path!!).delete() } }
        } finally { instrumentation.runOnMainSync { engine.close() } }
    }
    @Test fun readsLegacyReactNativeProjectAndPreservesItsMedia() {
        val file = File(context.filesDir, "project.json")
        val backup = if (file.exists()) file.readBytes() else null
        val source = fixture("sample.mp4")
        val engine = VideoEngine(context)
        try {
            val metadata = engine.inspect(Uri.fromFile(source)).copy(id = source.nameWithoutExtension)
            val legacyVideo = metadata.json().apply { remove("mime"); remove("thumbnail") }
            // 0.1.x JavaScript serializes the numeric fields as doubles.
            val legacy = JSONObject().put("version", 1).put("templateId", "journal").put("title", "Старый проект")
                .put("video", legacyVideo.put("durationMs", metadata.durationMs.toDouble()).put("bytes", metadata.bytes.toDouble()))
                .put("startMs", 500.0).put("endMs", 2500.0).put("muted", true).put("updatedAt", 1700000000000.0).toString()
            file.writeText(legacy)
            val store = ProjectStore(context)
            val restored = store.load()!!
            assertEquals("Старый проект", restored.title)
            assertEquals(500, restored.startMs); assertEquals(2500, restored.endMs); assertTrue(restored.muted)
            assertEquals(Uri.fromFile(source).toString(), restored.video.uri); assertTrue(source.exists())
            store.save(restored)
            assertEquals(restored, store.load())
            assertEquals(1, JSONObject(file.readText()).getInt("version"))
            file.writeText("{broken")
            assertNull(store.load())
        } finally {
            if (backup == null) file.delete() else file.writeBytes(backup)
            instrumentation.runOnMainSync { engine.close() }
        }
    }
    @Test fun validatesActualVideoAndRejects4K() {
        lateinit var module: VideoEngine
        instrumentation.runOnMainSync { module = VideoEngine(context) }
        try {
            val info = module.inspect(Uri.fromFile(fixture("sample.mp4")))
            assertEquals(640, info.width)
            assertEquals(360, info.height)
            assertTrue(info.fps.toDouble() in 23.0..25.0)
            try { module.inspect(Uri.fromFile(fixture("4k.mp4"))); fail("4K import must be rejected") }
            catch (e: IllegalStateException) { assertTrue(e.message!!.contains("1080p")) }
        } finally { instrumentation.runOnMainSync { module.close() } }
    }
    @Test fun acceptsActualHevcFromCamera() {
        lateinit var module: VideoEngine
        instrumentation.runOnMainSync { module = VideoEngine(context) }
        try {
            val info = module.inspect(Uri.fromFile(fixture("sample-hevc.mp4")))
            assertEquals("video/hevc", info.mime)
            assertEquals(640, info.width)
        } finally { instrumentation.runOnMainSync { module.close() } }
    }
    @Test fun exportsTrimmedPortraitWithTextAndWithoutAudio() = assertNativeExport("sample.mp4")
    @Test fun convertsHevcToCompatibleH264() = assertNativeExport("sample-hevc.mp4")
    @Test fun exportsEditorGeometryFilterAndColoredText() = assertNativeExport("sample.mp4", EditSettings("bottom", 1.3f, "coral", "mono", true))
    private fun assertNativeExport(name: String, edits: EditSettings = EditSettings()) {
        val file = fixture(name)
        lateinit var module: VideoEngine
        instrumentation.runOnMainSync { module = VideoEngine(context) }
        val done = CountDownLatch(1)
        var result: String? = null
        var failure: String? = null
        val project = Project("journal", "ТЕСТ FRAME", module.inspect(Uri.fromFile(file)).copy(id = file.nameWithoutExtension), 500, 2500, true, edits = edits)
        try {
            instrumentation.runOnMainSync {
                module.startExport(project, {}) { outcome ->
                    result = outcome.getOrNull(); failure = outcome.exceptionOrNull()?.toString(); done.countDown()
                }
            }
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
                    if (edits.fit) {
                        val b = frame!!
                        fun red(c: Int) = android.graphics.Color.red(c)
                        fun green(c: Int) = android.graphics.Color.green(c)
                        fun blue(c: Int) = android.graphics.Color.blue(c)
                        val corner = b.getPixel(b.width / 8, b.height / 8)
                        assertTrue("Fit must preserve black letterbox bars", red(corner) < 12 && green(corner) < 12 && blue(corner) < 12)
                        var gray = 0; var coral = 0
                        for (y in b.height * 2 / 5 until b.height * 3 / 5 step 8) for (x in b.width / 8 until b.width * 7 / 8 step 8) {
                            val c = b.getPixel(x, y)
                            if (red(c) in 25..230 && kotlin.math.abs(red(c) - green(c)) < 12 && kotlin.math.abs(red(c) - blue(c)) < 12) gray++
                        }
                        for (y in b.height * 3 / 4 until b.height step 3) for (x in 0 until b.width step 3) {
                            val c = b.getPixel(x, y)
                            if (red(c) > 100 && green(c) < red(c) * .7 && blue(c) < red(c) * .65) coral++
                        }
                        assertTrue("Monochrome filter must affect the exported video", gray > 30)
                        assertTrue("Bottom coral title must appear in the exported frame", coral > 30)
                    }
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
        } finally { instrumentation.runOnMainSync { module.close() } }
    }
}
