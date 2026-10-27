package com.gutkin.frame.video

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.FrameDropEffect
import androidx.media3.transformer.*
import com.gutkin.frame.Project
import com.gutkin.frame.VideoData
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

/** Android-only media engine. No React context, JavaScript bridge, or Activity references. */
class VideoEngine(context: Context) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var transformer: Transformer? = null
    private var temporary: File? = null
    private var progressTask: Runnable? = null
    private var resultCallback: ((Result<String>) -> Unit)? = null
    private var publishing = false
    val isExporting get() = resultCallback != null

    fun inspect(uri: Uri): VideoData {
        var bytes = -1L
        app.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { if (it.moveToFirst() && !it.isNull(0)) bytes = it.getLong(0) }
        if (bytes <= 0) app.contentResolver.openAssetFileDescriptor(uri, "r")?.use { bytes = it.length }
        require(bytes in 1..MediaLimits.MAX_BYTES) { "Выберите локальное видео размером до 200 МБ" }
        val r = MediaMetadataRetriever(); val extractor = MediaExtractor()
        try {
            r.setDataSource(app, uri); extractor.setDataSource(app, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true } ?: error("В файле нет видеодорожки")
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME)
            val transfer = if (format.containsKey(MediaFormat.KEY_COLOR_TRANSFER)) format.getInteger(MediaFormat.KEY_COLOR_TRANSFER) else 0
            require(transfer != 6 && transfer != 7) { "HDR пока не поддерживается. Выберите обычное SDR-видео." }
            val width = format.getInteger(MediaFormat.KEY_WIDTH); val height = format.getInteger(MediaFormat.KEY_HEIGHT)
            val duration = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
            var fps = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull() ?: 0f
            if (fps <= 0 && format.containsKey(MediaFormat.KEY_FRAME_RATE)) fps = (format.getNumber(MediaFormat.KEY_FRAME_RATE) ?: 0).toFloat()
            extractor.selectTrack(track)
            var previous = -1L; var frames = 0; var first = -1L; var last = -1L; var shortest = Long.MAX_VALUE
            while (frames < 7201 && extractor.sampleTime >= 0) {
                val t = extractor.sampleTime
                if (first < 0) first = t
                if (previous >= 0 && t > previous) shortest = minOf(shortest, t - previous)
                previous = t; last = t; frames++
                if (!extractor.advance()) break
            }
            if (last > first && frames > 1) fps = maxOf(fps, (frames - 1) * 1_000_000f / (last - first))
            if (shortest in 1 until Long.MAX_VALUE) fps = maxOf(fps, 1_000_000f / shortest)
            MediaLimits.problem(bytes, width, height, duration, fps)?.let { error(it) }
            MediaInputSupport.problem(mime, MediaCodecList(MediaCodecList.REGULAR_CODECS).findDecoderForFormat(format) != null)?.let { error(it) }
            return VideoData("", uri.toString(), width, height, bytes, duration, fps, mime = mime)
        } finally { r.release(); extractor.release() }
    }

    /** Call on an I/O dispatcher. Validate before copying or decoding a thumbnail. */
    fun importVideo(uri: Uri): VideoData {
        val info = inspect(uri)
        val folder = File(app.filesDir, "media").apply { mkdirs() }
        require(app.filesDir.usableSpace > info.bytes + 350L * 1024 * 1024) { "Недостаточно свободного места на телефоне" }
        require((folder.listFiles()?.sumOf { it.length() } ?: 0L) + info.bytes <= 1024L * 1024 * 1024) { "Локальные исходники заняли 1 ГБ. Очистите проекты в разделе «О приложении»." }
        val file = File(folder, "${UUID.randomUUID()}.mp4")
        val thumb = File(folder, file.nameWithoutExtension + ".jpg")
        try {
            var total = 0L
            app.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Не удалось прочитать видео" }
                file.outputStream().use { output ->
                    val buffer = ByteArray(65536)
                    while (true) {
                        val n = input.read(buffer); if (n < 0) break
                        total += n; require(total <= MediaLimits.MAX_BYTES) { "Файл больше 200 МБ" }
                        output.write(buffer, 0, n)
                    }
                }
            }
            require(total > 0) { "Пустой файл" }
            // A thumbnail is optional; its failure must not discard otherwise valid media.
            runCatching {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(file.absolutePath)
                    r.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 360, 640)?.let { bitmap ->
                        thumb.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, it) }
                        bitmap.recycle()
                    }
                } finally { r.release() }
            }
            return info.copy(id = file.nameWithoutExtension, uri = Uri.fromFile(file).toString(), thumbnail = if (thumb.exists()) Uri.fromFile(thumb).toString() else null, bytes = total)
        } catch (e: Exception) { file.delete(); thumb.delete(); throw e }
    }

    fun startExport(project: Project, onProgress: (Int) -> Unit, onResult: (Result<String>) -> Unit) {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (isExporting) { onResult(Result.failure(IllegalStateException("Экспорт уже выполняется"))); return }
        try {
            val uri = Uri.parse(project.video.uri); val file = File(uri.path ?: "")
            require(uri.scheme == "file" && file.canonicalPath.startsWith(File(app.filesDir, "media").canonicalPath + "/") && file.isFile) { "Выберите видео заново" }
            require(project.title.length <= 45 && project.templateId in VideoStyle.ids)
            val r = MediaMetadataRetriever()
            val duration = try { r.setDataSource(file.absolutePath); r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0 } finally { r.release() }
            require(MediaLimits.validTrim(project.startMs, project.endMs, duration)) { "Выберите фрагмент от 1 до 30 секунд в пределах исходного видео" }
            require(app.cacheDir.usableSpace > 350L * 1024 * 1024) { "Для экспорта нужно хотя бы 350 МБ свободного места" }
            val item = MediaItem.Builder().setUri(uri).setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(project.startMs).setEndPositionMs(project.endMs).build()).build()
            val effects = VideoStyle.effects(project.templateId, project.title).toMutableList().apply { add(FrameDropEffect.createDefaultFrameDropEffect(30f)) }
            val edited = EditedMediaItem.Builder(item).setRemoveAudio(project.muted).setEffects(Effects(emptyList(), effects)).build()
            val output = File(app.cacheDir, "frame-${UUID.randomUUID()}.mp4")
            temporary = output; resultCallback = onResult
            transformer = Transformer.Builder(app).setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setEncoderFactory(DefaultEncoderFactory.Builder(app).setEnableFallback(false).build())
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        publishing = true; transformer = null
                        progressTask?.let { main.removeCallbacks(it) }
                        worker.execute { val result = runCatching { publish(output) }; main.post { complete(result) } }
                    }
                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) { complete(Result.failure(exportException)) }
                }).build()
            transformer!!.start(edited, output.absolutePath)
            progressTask = object : Runnable {
                override fun run() {
                    if (!isExporting || publishing) return
                    val holder = ProgressHolder(); transformer?.getProgress(holder); onProgress(holder.progress)
                    main.postDelayed(this, 400)
                }
            }.also { main.post(it) }
        } catch (e: Exception) {
            if (resultCallback != null) complete(Result.failure(e)) else onResult(Result.failure(e))
        }
    }
    private fun publish(file: File): String {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "FRAME-${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4"); put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/FRAME"); put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = app.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: error("Не удалось создать видео в галерее")
        try {
            app.contentResolver.openOutputStream(uri).use { output -> requireNotNull(output); file.inputStream().use { it.copyTo(output) } }
            app.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
            return uri.toString()
        } catch (e: Exception) { app.contentResolver.delete(uri, null, null); throw e }
    }
    fun cancelExport(message: String = "Экспорт отменён") {
        if (publishing || !isExporting) return
        transformer?.cancel(); complete(Result.failure(IllegalStateException(message)))
    }
    private fun complete(result: Result<String>) {
        progressTask?.let { main.removeCallbacks(it) }; progressTask = null
        transformer = null; temporary?.delete(); temporary = null; publishing = false
        val callback = resultCallback; resultCallback = null; callback?.invoke(result)
    }
    fun close() { cancelExport(); worker.shutdown() }
}
