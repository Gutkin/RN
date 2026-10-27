@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.gutkin.frame

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.gutkin.frame.video.VideoStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToLong

private val EditorBackground = Color(0xFF20211F)
private val EditorPanel = Color(0xFF2C2D29)
private val Accent = Color(0xFFFF624F)
private val EditorNote = Color(0xFFBFC0B7)

private fun timeLabel(ms: Long): String = String.format(Locale.ROOT, "%02d:%04.1f", ms / 60000, (ms % 60000) / 1000.0)

internal class EditorPlayback {
    var player: ExoPlayer? = null
    var positionMs by mutableStateOf(0L)
    var isPlaying by mutableStateOf(false)
    var ready by mutableStateOf(false)
    fun toggle() { player?.let { if (it.isPlaying) it.pause() else it.play() } }
    fun seek(position: Long) { player?.seekTo(position); positionMs = position }
}

/** A single-clip editor. Trim, text, filter and geometry use the same effects as export. */
@Composable internal fun VideoEditor(model: FrameViewModel, pick: () -> Unit) {
    val s = model.state
    val focus = LocalFocusManager.current
    val draft = s.draft()
    val playback = remember { EditorPlayback() }
    var tool by remember { mutableStateOf("Монтаж") }
    val toolScroll = remember(tool) { ScrollState(0) }
    Column(Modifier.fillMaxSize().imePadding().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(model::back) { Icon(Icons.Outlined.ArrowBack, "Назад") }
            Text(s.template.name, Modifier.weight(1f), fontSize = 14.sp)
            TextButton({ model.navigate(Screen.EXPORT) }, enabled = s.trimProblem == null) { Text("Далее →") }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF171815), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            val width = minOf(maxWidth, maxHeight * 9f / 16f)
            Box(Modifier.width(width).aspectRatio(9f / 16f).clip(RoundedCornerShape(3.dp))) {
                if (draft != null) NativeVideoPreview(draft, Modifier.fillMaxSize(), playback) else {
                    Image(painterResource(R.drawable.featured_collage), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .55f)), contentAlignment = Alignment.Center) {
                        Text(if (s.video == null) "Добавьте видео\nдля монтажа" else s.trimProblem.orEmpty(), Modifier.padding(12.dp), color = Color.White, fontSize = 14.sp)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(playback::toggle, enabled = draft != null && playback.ready, modifier = Modifier.semantics { contentDescription = if (playback.isPlaying) "Пауза" else "Воспроизвести" }) { Text(if (playback.isPlaying) "Ⅱ" else "▶", fontSize = 20.sp) }
            Slider(value = playback.positionMs.toFloat().coerceIn(0f, maxOf(1, s.durationMs).toFloat()), onValueChange = { playback.seek(it.roundToLong()) }, valueRange = 0f..maxOf(1, s.durationMs).toFloat(), enabled = draft != null && playback.ready, modifier = Modifier.weight(1f).semantics { contentDescription = "Позиция воспроизведения" })
            Text(timeLabel(playback.positionMs), fontSize = 11.sp, color = EditorNote)
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Монтаж", "Текст", "Стиль", "Звук").forEach { name -> FilterChip(tool == name, { tool = name }, { Text(name) }, modifier = Modifier.semantics { contentDescription = "Инструмент $name" }) }
        }
        Column(Modifier.fillMaxWidth().height(188.dp).clip(RoundedCornerShape(12.dp)).background(EditorPanel).verticalScroll(toolScroll).padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when (tool) {
                "Монтаж" -> {
                    if (s.video != null) {
                        Timeline(s.video, s.startMs, s.endMs, model::trim)
                        Text("${timeLabel(s.startMs)} — ${timeLabel(s.endMs)} · ${s.durationMs / 1000.0} сек", fontSize = 12.sp, color = EditorNote)
                        Text("Потяните границы фрагмента. Экспорт: 1–30 сек.", fontSize = 11.sp, color = EditorNote)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(s.start, model::start, Modifier.weight(1f), label = { Text("Начало, сек") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }))
                            OutlinedTextField(s.length, model::length, Modifier.weight(1f), label = { Text("Длина, сек") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }))
                        }
                        s.trimProblem?.let { Text(it, color = Color(0xFFFFB3A8), fontSize = 12.sp) }
                    } else Text("Добавьте ролик: здесь появятся кадры и границы обрезки. Один исходник до 60 секунд, 1080p, 200 МБ.", color = EditorNote, fontSize = 13.sp)
                }
                "Текст" -> {
                    OutlinedTextField(s.title, model::title, Modifier.fillMaxWidth(), label = { Text("Заголовок") }, maxLines = 3, supportingText = { Text("${s.title.length}/45 · очистите поле, чтобы убрать текст") })
                    EditorChoices(listOf("template" to "Шаблон", "top" to "Сверху", "center" to "По центру", "bottom" to "Снизу"), s.edits.textPosition, "Положение текста") { model.edits(s.edits.copy(textPosition = it)) }
                    Text("Размер текста · ${(s.edits.textScale * 100).toInt()}%", fontSize = 12.sp, color = EditorNote)
                    Slider(s.edits.textScale, { model.edits(s.edits.copy(textScale = it)) }, valueRange = .7f..1.5f, modifier = Modifier.semantics { contentDescription = "Размер текста" })
                    EditorChoices(listOf("template" to "Шаблон", "white" to "Белый", "coral" to "Коралловый", "black" to "Чёрный"), s.edits.textColor, "Цвет текста") { model.edits(s.edits.copy(textColor = it)) }
                }
                "Стиль" -> {
                    Text("Оформление", fontSize = 12.sp, color = EditorNote)
                    EditorChoices(Templates.all.map { it.id to it.name }, s.template.id, "Шаблон") { id -> model.template(Templates.byId(id)!!) }
                    EditorChoices(listOf("template" to "По шаблону", "original" to "Оригинал", "mono" to "Ч/б"), s.edits.filter, "Фильтр") { model.edits(s.edits.copy(filter = it)) }
                    EditorChoices(listOf("crop" to "Заполнить кадр", "fit" to "Вписать целиком"), if (s.edits.fit) "fit" else "crop", "Масштаб") { model.edits(s.edits.copy(fit = it == "fit")) }
                    Text("9:16 · при вписывании добавляются чёрные поля", fontSize = 11.sp, color = EditorNote)
                }
                "Звук" -> {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Отключить звук оригинала", Modifier.weight(1f), fontSize = 14.sp)
                        Switch(s.muted, model::mute, modifier = Modifier.semantics { contentDescription = "Отключить звук оригинала" })
                    }
                    Text("${if (s.muted) "Видео сохранится без звука." else "В экспорт попадёт звук исходного ролика."}\n\nМузыка и дополнительные дорожки пока не поддерживаются.", color = EditorNote, fontSize = 12.sp)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val label = if (s.video == null) "+ Добавить видео" else "Заменить видео"
            OutlinedButton(pick, Modifier.weight(1f).heightIn(min = 48.dp).semantics { contentDescription = label }) { Text(label, fontSize = 12.sp) }
            Button(model::save, Modifier.weight(1f).heightIn(min = 48.dp).semantics { contentDescription = "Сохранить проект" }, enabled = draft != null, colors = ButtonDefaults.buttonColors(containerColor = Accent)) { Text("Сохранить проект", fontSize = 12.sp) }
        }
    }
}

@Composable private fun EditorChoices(items: List<Pair<String, String>>, selected: String, description: String, change: (String) -> Unit) {
    Row(Modifier.semantics { contentDescription = "Параметр $description" }.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (id, label) -> FilterChip(selected == id, { change(id) }, { Text(label, fontSize = 12.sp) }, modifier = Modifier.semantics { contentDescription = "$description: $label" }) }
    }
}

@Composable private fun Timeline(video: VideoData, start: Long, end: Long, trim: (Long, Long) -> Unit) {
    val context = LocalContext.current
    // Decode a small fixed number on I/O, only once per source. No full-resolution frames in UI memory.
    val frames by produceState<List<Bitmap>>(emptyList(), video.uri) {
        value = withContext(Dispatchers.IO) {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(context, android.net.Uri.parse(video.uri))
                (0 until 8).mapNotNull { i -> runCatching { r.getScaledFrameAtTime(i * (video.durationMs - 1) * 1000 / 7, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 96, 96) }.getOrNull() }
            } catch (_: Exception) { emptyList() } finally { r.release() }
        }
    }
    // Compose releases these tiny bitmaps with this timeline; never recycle one still drawn by a frame.
    val duration = video.durationMs.toFloat()
    val from = start.toFloat().coerceIn(0f, duration - 1000f)
    val to = end.toFloat().coerceIn(from + 1000f, minOf(duration, from + 30000f))
    var selection by remember(video.uri, from, to) { mutableStateOf(from..to) }
    Column(Modifier.semantics { contentDescription = "Лента кадров" }) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black)) {
            Row(Modifier.fillMaxSize()) { frames.forEach { Image(it.asImageBitmap(), null, Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop) } }
            Box(Modifier.width(maxWidth * (selection.start / duration)).fillMaxHeight().background(Color.Black.copy(alpha = .7f)))
            Box(Modifier.align(Alignment.CenterEnd).width(maxWidth * (1f - selection.endInclusive / duration)).fillMaxHeight().background(Color.Black.copy(alpha = .7f)))
        }
        RangeSlider(selection, { requested ->
            val movingStart = requested.start != selection.start
            val range = EditorTrim.move(selection.start.roundToLong(), selection.endInclusive.roundToLong(), movingStart, (if (movingStart) requested.start else requested.endInclusive).roundToLong(), video.durationMs)
            selection = range.first.toFloat()..range.last.toFloat()
        }, onValueChangeFinished = { trim(selection.start.roundToLong(), selection.endInclusive.roundToLong()) }, valueRange = 0f..duration, modifier = Modifier.fillMaxWidth().height(32.dp).semantics { contentDescription = "Границы фрагмента" })
    }
}

@Composable internal fun NativeVideoPreview(project: Project, modifier: Modifier, control: EditorPlayback? = null) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val player = remember(context) { ExoPlayer.Builder(context).build().apply { repeatMode = Player.REPEAT_MODE_ONE } }
    var error by remember { mutableStateOf<String?>(null) }
    var loadedClip by remember { mutableStateOf("") }
    DisposableEffect(player, owner, control) {
        control?.player = player
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) player.pause() }
        val listener = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) { error = "Не удалось воспроизвести видео: ${e.message}" }
        }
        player.addListener(listener); owner.lifecycle.addObserver(observer)
        onDispose { control?.player = null; control?.ready = false; control?.isPlaying = false; control?.positionMs = 0; owner.lifecycle.removeObserver(observer); player.removeListener(listener); player.release() }
    }
    LaunchedEffect(player, control) {
        while (true) {
            control?.positionMs = player.currentPosition.coerceAtLeast(0)
            control?.isPlaying = player.isPlaying
            control?.ready = player.playbackState == Player.STATE_READY
            delay(100)
        }
    }
    LaunchedEffect(project.muted) { player.volume = if (project.muted) 0f else 1f }
    LaunchedEffect(project.video.uri, project.startMs, project.endMs, project.title, project.templateId, project.edits) {
        delay(250); error = null
        val clip = "${project.video.uri}:${project.startMs}:${project.endMs}"
        val position = if (clip == loadedClip) player.currentPosition else 0L
        val resume = player.playWhenReady
        player.pause(); player.setVideoEffects(VideoStyle.effects(project.templateId, project.title, project.edits))
        player.setMediaItem(MediaItem.Builder().setUri(project.video.uri).setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(project.startMs).setEndPositionMs(project.endMs).build()).build(), position)
        loadedClip = clip
        player.prepare(); player.playWhenReady = resume
    }
    Box(modifier) {
        AndroidView(factory = { PlayerView(it).apply { this.player = player; useController = control == null; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT } }, modifier = Modifier.fillMaxSize())
        error?.let { Text(it, Modifier.align(Alignment.Center).background(EditorBackground.copy(alpha = .85f)).padding(16.dp), color = Color.White, fontSize = 12.sp) }
    }
}
