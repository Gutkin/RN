package com.gutkin.frame

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gutkin.frame.video.MediaLimits
import com.gutkin.frame.video.VideoEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class Screen { HOME, TEMPLATES, PROJECTS, SETTINGS, EDITOR, EXPORT }
data class UiState(
    val screen: Screen = Screen.HOME, val collection: String = "Stories",
    val template: Template = Templates.all[0], val title: String = Templates.all[0].title,
    val video: VideoData? = null, val start: String = "0", val length: String = "15", val muted: Boolean = false,
    val busy: Boolean = false, val exporting: Boolean = false, val progress: Int = 0,
    val savedUri: String? = null, val project: Project? = null, val message: String? = null,
    val search: String = "", val category: String = "Все"
) {
    val startMs get() = (start.replace(',', '.').toDoubleOrNull()?.times(1000))?.takeIf { it.isFinite() }?.toLong() ?: -1
    val durationMs get() = (length.replace(',', '.').toDoubleOrNull()?.times(1000))?.takeIf { it.isFinite() }?.toLong() ?: -1
    val endMs get() = startMs + durationMs
    val trimProblem get(): String? = when {
        video == null -> "Добавьте видео"
        startMs < 0 || durationMs < 0 -> "Укажите время числом"
        endMs > video.durationMs -> "Фрагмент должен быть в пределах исходного видео"
        !MediaLimits.validTrim(startMs, endMs, video.durationMs) -> "Длительность фрагмента — от 1 до 30 секунд"
        else -> null
    }
    fun draft(): Project? = video?.takeIf { trimProblem == null }?.let { Project(template.id, title, it, startMs, endMs, muted) }
}

class FrameViewModel(app: Application) : AndroidViewModel(app) {
    var state by mutableStateOf(UiState()); private set
    private val store = ProjectStore(app)
    private val engine = VideoEngine(app)
    init { viewModelScope.launch { val p = withContext(Dispatchers.IO) { store.load() }; state = state.copy(project = p) } }
    fun navigate(value: Screen) { if (!state.busy && !state.exporting) state = state.copy(screen = value) }
    fun back() = navigate(if (state.screen == Screen.EXPORT) Screen.EDITOR else Screen.HOME)
    fun collection(value: String) { state = state.copy(collection = value) }
    fun template(value: Template) { state = state.copy(template = value, title = value.title, savedUri = null) }
    fun open(value: Template) { template(value); navigate(Screen.EDITOR) }
    fun title(value: String) { state = state.copy(title = value.take(45), savedUri = null) }
    fun start(value: String) { state = state.copy(start = value, savedUri = null) }
    fun length(value: String) { state = state.copy(length = value, savedUri = null) }
    fun mute(value: Boolean) { state = state.copy(muted = value, savedUri = null) }
    fun search(value: String) { state = state.copy(search = value) }
    fun category(value: String) { state = state.copy(category = value) }
    fun message(value: String?) { state = state.copy(message = value) }
    fun importVideo(uri: Uri) {
        if (state.busy || state.exporting) return
        state = state.copy(busy = true)
        viewModelScope.launch {
            try {
                val v = withContext(Dispatchers.IO) { engine.importVideo(uri) }
                state = state.copy(video = v, start = "0", length = minOf(15, v.durationMs / 1000).toString(), savedUri = null)
            } catch (e: Exception) { state = state.copy(message = e.message ?: "Не удалось открыть видео") }
            finally { state = state.copy(busy = false) }
        }
    }
    fun save() {
        val draft = state.draft() ?: return
        state = state.copy(busy = true)
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { store.save(draft) }; state = state.copy(project = draft, message = "Проект сохранён. Его можно продолжить во вкладке «Проекты».") }
            catch (e: Exception) { state = state.copy(message = e.message) }
            finally { state = state.copy(busy = false) }
        }
    }
    fun restore() {
        val p = state.project ?: return
        state = state.copy(template = Templates.byId(p.templateId)!!, title = p.title, video = p.video, start = (p.startMs / 1000.0).toString(), length = ((p.endMs - p.startMs) / 1000.0).toString(), muted = p.muted, savedUri = null, screen = Screen.EDITOR)
    }
    fun clear() {
        if (state.busy || state.exporting) return
        state = state.copy(busy = true)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { require(File(getApplication<Application>().filesDir, "media").let { !it.exists() || it.deleteRecursively() }) { "Не удалось удалить локальные исходники" }; store.clear() }
                state = state.copy(video = null, project = null, savedUri = null)
            } catch (e: Exception) { state = state.copy(message = e.message) }
            finally { state = state.copy(busy = false) }
        }
    }
    fun export() {
        if (state.exporting) return
        val draft = state.draft() ?: return
        state = state.copy(exporting = true, progress = 0, savedUri = null)
        engine.startExport(draft, { state = state.copy(progress = it) }) { result -> state = state.copy(exporting = false, savedUri = result.getOrNull(), message = result.exceptionOrNull()?.message) }
    }
    fun cancelExport() = engine.cancelExport()
    fun background() = engine.cancelExport("Экспорт остановлен. Оставьте приложение открытым до завершения.")
    override fun onCleared() { engine.close(); super.onCleared() }
}
