package com.gutkin.frame

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import com.gutkin.frame.video.MediaLimits
import org.json.JSONObject
import java.io.File

data class Template(val id: String, val name: String, val subtitle: String, val tag: String, val title: String, val category: String)
object Templates {
    val all = listOf(
        Template("journal", "Sunday Journal", "Твоя маленькая история", "EDITORIAL", "SUNDAY\nJOURNAL", "Editorial"),
        Template("minimal", "Quiet Moment", "Красота простых моментов", "MINIMAL", "A quiet moment", "Minimal"),
        Template("film", "Film Diary", "Чёрно-белое кино о тебе", "MONOCHROME", "THE DAILY EDIT", "Editorial"),
        Template("postcard", "Coastal Days", "Привет из любимого места", "TRAVEL", "Coastal days", "Travel"),
        Template("bold", "Make It Bold", "Твой момент. Твои правила.", "STATEMENT", "MAKE IT\nYOURS.", "Minimal")
    )
    fun byId(id: String) = all.firstOrNull { it.id == id }
    fun collection(name: String): List<Template> {
        val ids = when (name) {
            "Reels" -> listOf("bold", "film", "journal", "minimal", "postcard")
            "Posts" -> listOf("postcard", "journal", "minimal", "bold", "film")
            else -> listOf("journal", "minimal", "postcard", "film", "bold")
        }
        return ids.map { byId(it)!! }
    }
}

data class VideoData(val id: String, val uri: String, val width: Int, val height: Int, val bytes: Long, val durationMs: Long, val fps: Float, val thumbnail: String? = null, val mime: String? = null) {
    fun json() = JSONObject().apply {
        put("id", id); put("uri", uri); put("width", width); put("height", height)
        put("bytes", bytes); put("durationMs", durationMs); put("fps", fps.toDouble())
        if (thumbnail != null) put("thumbnail", thumbnail)
        if (mime != null) put("mime", mime)
    }
    companion object {
        fun fromJson(j: JSONObject): VideoData {
            val v = VideoData(j.getString("id"), j.getString("uri"), j.getInt("width"), j.getInt("height"), j.getLong("bytes"), j.getLong("durationMs"), j.getDouble("fps").toFloat(), j.optString("thumbnail").takeIf { it.isNotBlank() }, j.optString("mime").takeIf { it.isNotBlank() })
            require(Uri.parse(v.uri).scheme == "file")
            require(v.width > 0 && v.height > 0 && v.durationMs > 0 && v.fps.isFinite())
            return v
        }
    }
}

data class Project(val templateId: String, val title: String, val video: VideoData, val startMs: Long, val endMs: Long, val muted: Boolean, val updatedAt: Long = System.currentTimeMillis()) {
    fun json() = JSONObject().apply {
        put("version", 1); put("templateId", templateId); put("title", title); put("video", video.json())
        put("startMs", startMs); put("endMs", endMs); put("muted", muted); put("updatedAt", updatedAt)
    }.toString()
    companion object {
        fun parse(text: String): Project {
            require(text.length < 100000)
            val j = JSONObject(text)
            require(j.getInt("version") == 1)
            val p = Project(j.getString("templateId"), j.getString("title"), VideoData.fromJson(j.getJSONObject("video")), j.getLong("startMs"), j.getLong("endMs"), j.getBoolean("muted"), j.getLong("updatedAt"))
            require(Templates.byId(p.templateId) != null && p.title.length <= 45)
            require(MediaLimits.validTrim(p.startMs, p.endMs, p.video.durationMs))
            return p
        }
    }
}

/** Retains the exact project.json schema and private media folder from versions 0.1.x. */
class ProjectStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "project.json"))
    fun load(): Project? = if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()) runCatching { Project.parse(file.openRead().use { it.readBytes().toString(Charsets.UTF_8) }) }.getOrNull() else null
    fun save(project: Project) {
        val text = project.json()
        Project.parse(text)
        val output = file.startWrite()
        try { output.write(text.toByteArray()); file.finishWrite(output) }
        catch (e: Exception) { file.failWrite(output); throw e }
    }
    fun clear() = file.delete()
}
