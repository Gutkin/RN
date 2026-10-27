package com.gutkin.frame.video

import android.app.Activity
import android.content.*
import android.database.Cursor
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.*
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

class VideoModule(private val app: ReactApplicationContext) : ReactContextBaseJavaModule(app), LifecycleEventListener {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var pickPromise: Promise? = null
    private var exportPromise: Promise? = null
    private var transformer: Transformer? = null
    private var temporary: File? = null
    private var progressTask: Runnable? = null
    private var exporting = false
    private var publishing = false
    init {
        app.addLifecycleEventListener(this)
        app.addActivityEventListener(object : BaseActivityEventListener() {
            override fun onActivityResult(activity: Activity, requestCode: Int, resultCode: Int, data: Intent?) {
                if (requestCode != 7101) return
                val promise = pickPromise ?: return
                pickPromise = null
                val uri = data?.data
                if (resultCode != Activity.RESULT_OK || uri == null) { promise.resolve(null); return }
                worker.execute {
                    try {
                        val metadata = inspect(uri)
                        val media = File(app.filesDir, "media").apply { mkdirs() }
                        require(app.filesDir.usableSpace > metadata.getDouble("bytes").toLong() + 350L * 1024 * 1024) { "Недостаточно места: освободите память телефона" }
                        require((media.listFiles()?.sumOf { it.length() } ?: 0L) + metadata.getDouble("bytes").toLong() <= 1024L * 1024 * 1024) { "Локальные исходники заняли 1 ГБ. Очистите проекты в разделе «О приложении»." }
                        val out = File(media, UUID.randomUUID().toString() + ".mp4")
                        try {
                            var total = 0L
                            app.contentResolver.openInputStream(uri).use { input ->
                                requireNotNull(input) { "Не удалось прочитать файл" }
                                out.outputStream().use { target ->
                                    val buffer = ByteArray(65536)
                                    while (true) {
                                        val n = input.read(buffer); if (n < 0) break
                                        total += n
                                        require(total <= MediaLimits.MAX_BYTES) { "Файл больше 200 МБ" }
                                        target.write(buffer, 0, n)
                                    }
                                }
                            }
                            require(total > 0) { "Пустой файл" }
                            metadata.putString("uri", Uri.fromFile(out).toString())
                            metadata.putString("id", out.nameWithoutExtension)
                            val thumb = File(media, out.nameWithoutExtension + ".jpg")
                            val retriever = MediaMetadataRetriever()
                            try {
                                retriever.setDataSource(out.absolutePath)
                                retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 360, 640)?.let { bitmap ->
                                    thumb.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, it) }; bitmap.recycle()
                                    metadata.putString("thumbnail", Uri.fromFile(thumb).toString())
                                }
                            } finally { retriever.release() }
                            promise.resolve(metadata)
                        } catch (e: Exception) { out.delete(); throw e }
                    } catch (e: Exception) { promise.reject("IMPORT", e.message ?: "Не удалось открыть видео", e) }
                }
            }
        })
    }
    override fun getName() = "FrameVideo"
    override fun getConstants(): Map<String, Any> = mapOf("maxBytes" to MediaLimits.MAX_BYTES.toDouble(), "maxDurationMs" to MediaLimits.MAX_SOURCE_MS.toDouble(), "maxExportMs" to MediaLimits.MAX_EXPORT_MS.toDouble())
    @ReactMethod fun addListener(name: String) {}
    @ReactMethod fun removeListeners(count: Int) {}
    @ReactMethod fun pickVideo(promise: Promise) {
        main.post {
            if (pickPromise != null || exporting) { promise.reject("BUSY", "Дождитесь завершения текущей операции"); return@post }
            val activity = currentActivity ?: run { promise.reject("ACTIVITY", "Откройте приложение"); return@post }
            pickPromise = promise
            try { activity.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "video/*"; addCategory(Intent.CATEGORY_OPENABLE) }, 7101) }
            catch (e: Exception) { pickPromise = null; promise.reject("PICKER", e.message, e) }
        }
    }
    internal fun inspect(uri: Uri): WritableMap {
        var bytes = -1L
        app.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c -> if (c.moveToFirst() && !c.isNull(0)) bytes = c.getLong(0) }
        if (bytes <= 0) app.contentResolver.openAssetFileDescriptor(uri, "r")?.use { bytes = it.length }
        require(bytes in 1..MediaLimits.MAX_BYTES) { "Выберите локальное видео размером до 200 МБ" }
        val retriever = MediaMetadataRetriever()
        val extractor = MediaExtractor()
        try {
            retriever.setDataSource(app, uri)
            extractor.setDataSource(app, uri, null)
            val format = (0 until extractor.trackCount).map { extractor.getTrackFormat(it) }.firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
                ?: error("В файле нет видеодорожки")
            require(format.getString(MediaFormat.KEY_MIME) == "video/avc") { "Пока поддерживается видео H.264. В камере выберите режим «Совместимый», без HEVC/HDR." }
            val transfer = if (format.containsKey(MediaFormat.KEY_COLOR_TRANSFER)) format.getInteger(MediaFormat.KEY_COLOR_TRANSFER) else 0
            require(transfer != 6 && transfer != 7) { "HDR пока не поддерживается. Выберите обычное SDR-видео." }
            val width = format.getInteger(MediaFormat.KEY_WIDTH)
            val height = format.getInteger(MediaFormat.KEY_HEIGHT)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
            var fps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull() ?: 0f
            if (fps <= 0 && format.containsKey(MediaFormat.KEY_FRAME_RATE)) fps = (format.getNumber(MediaFormat.KEY_FRAME_RATE) ?: 0).toFloat()
            // Actual sample timestamps also catch slow-motion/VFR files with misleading metadata.
            extractor.selectTrack((0 until extractor.trackCount).first { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true })
            var previous = -1L; var frames = 0; var first = -1L; var last = -1L; var shortest = Long.MAX_VALUE
            while (frames < 7201 && extractor.sampleTime >= 0) {
                val t = extractor.sampleTime
                if (first < 0) first = t
                if (previous >= 0 && t > previous) shortest = minOf(shortest, t - previous)
                previous = t; last = t; frames++; if (!extractor.advance()) break
            }
            if (last > first && frames > 1) fps = maxOf(fps, (frames - 1) * 1_000_000f / (last - first))
            if (shortest < Long.MAX_VALUE && shortest > 0) fps = maxOf(fps, 1_000_000f / shortest)
            MediaLimits.problem(bytes, width, height, duration, fps)?.let { error(it) }
            return Arguments.createMap().apply { putDouble("bytes", bytes.toDouble()); putInt("width", width); putInt("height", height); putDouble("durationMs", duration.toDouble()); putDouble("fps", fps.toDouble()) }
        } finally { retriever.release(); extractor.release() }
    }
    @ReactMethod fun saveProject(json: String, promise: Promise) {
        worker.execute { try { require(json.length < 100000); File(app.filesDir, "project.json").writeText(json); promise.resolve(true) } catch (e: Exception) { promise.reject("SAVE", e.message, e) } }
    }
    @ReactMethod fun loadProject(promise: Promise) {
        worker.execute { try { val f = File(app.filesDir, "project.json"); promise.resolve(if (f.exists()) f.readText() else null) } catch (e: Exception) { promise.reject("LOAD", e.message, e) } }
    }
    @ReactMethod fun clearProjects(promise: Promise) {
        main.post {
            if (exporting || pickPromise != null) { promise.reject("BUSY", "Дождитесь завершения операции"); return@post }
            worker.execute {
                try { File(app.filesDir, "media").deleteRecursively(); File(app.filesDir, "project.json").delete(); promise.resolve(true) }
                catch (e: Exception) { promise.reject("CLEAR", e.message, e) }
            }
        }
    }
    @ReactMethod fun exportVideo(options: ReadableMap, promise: Promise) {
        main.post {
            if (exporting) { promise.reject("BUSY", "Экспорт уже выполняется"); return@post }
            try {
                val uri = Uri.parse(options.getString("uri")); val file = File(uri.path ?: "")
                require(uri.scheme == "file" && file.canonicalPath.startsWith(File(app.filesDir, "media").canonicalPath + "/") && file.isFile) { "Выберите видео заново" }
                val start = options.getDouble("startMs").toLong(); val end = options.getDouble("endMs").toLong()
                val title = options.getString("title") ?: ""; require(title.length <= 45) { "Заголовок — до 45 символов" }
                val style = options.getString("style") ?: "journal"; require(style in VideoStyle.ids)
                val r = MediaMetadataRetriever()
                val duration = try { r.setDataSource(file.absolutePath); r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0 } finally { r.release() }
                require(MediaLimits.validTrim(start, end, duration)) { "Выберите фрагмент от 1 до 30 секунд в пределах исходного видео" }
                require(app.cacheDir.usableSpace > 350L * 1024 * 1024) { "Для экспорта нужно хотя бы 350 МБ свободного места" }
                val item = MediaItem.Builder().setUri(uri).setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(start).setEndPositionMs(end).build()).build()
                val effects = VideoStyle.effects(style, title).toMutableList().apply { add(androidx.media3.effect.FrameDropEffect.createDefaultFrameDropEffect(30f)) }
                val edited = EditedMediaItem.Builder(item).setRemoveAudio(options.getBoolean("muted")).setEffects(Effects(emptyList(), effects)).build()
                val output = File(app.cacheDir, "frame-${UUID.randomUUID()}.mp4")
                temporary = output; exportPromise = promise; exporting = true
                transformer = Transformer.Builder(app).setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setEncoderFactory(DefaultEncoderFactory.Builder(app).setEnableFallback(false).build())
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            publishing = true
                            transformer = null
                            progressTask?.let { main.removeCallbacks(it) }
                            worker.execute {
                                try { val saved = publish(output); main.post { finish(); promise.resolve(saved) } }
                                catch (e: Exception) { main.post { finish(); promise.reject("SAVE_VIDEO", e.message, e) } }
                            }
                        }
                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) { finish(); promise.reject("EXPORT", "Не удалось обработать видео: ${exportException.message}", exportException) }
                    }).build()
                transformer!!.start(edited, output.absolutePath)
                progressTask = object : Runnable {
                    override fun run() {
                        if (!exporting) return
                        val holder = ProgressHolder(); transformer?.getProgress(holder)
                        if (app.hasActiveReactInstance()) app.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java).emit("FrameExportProgress", holder.progress)
                        main.postDelayed(this, 400)
                    }
                }.also { main.post(it) }
            } catch (e: Exception) { finish(); promise.reject("EXPORT", e.message, e) }
        }
    }
    private fun publish(file: File): String {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "FRAME-${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/FRAME")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = app.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: error("Не удалось создать файл в галерее")
        try {
            app.contentResolver.openOutputStream(uri).use { output -> requireNotNull(output); file.inputStream().use { it.copyTo(output) } }
            app.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
            return uri.toString()
        } catch (e: Exception) { app.contentResolver.delete(uri, null, null); throw e }
    }
    @ReactMethod fun cancelExport() { main.post { if (publishing) return@post; val p = exportPromise; transformer?.cancel(); finish(); p?.reject("CANCELLED", "Экспорт отменён") } }
    private fun finish() { progressTask?.let { main.removeCallbacks(it) }; progressTask = null; transformer = null; temporary?.delete(); temporary = null; exporting = false; publishing = false; exportPromise = null }
    override fun onHostResume() {}
    override fun onHostPause() { if (exporting && !publishing) { val p = exportPromise; transformer?.cancel(); finish(); p?.reject("BACKGROUND", "Экспорт остановлен. Оставьте приложение открытым до завершения.") } }
    override fun onHostDestroy() { onHostPause(); pickPromise?.resolve(null); pickPromise = null }
    override fun invalidate() { main.post { onHostDestroy(); worker.shutdown(); app.removeLifecycleEventListener(this) }; super.invalidate() }
}
