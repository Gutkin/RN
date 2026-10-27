@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.gutkin.frame

import android.content.Intent
import android.app.Activity
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.view.WindowCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.gutkin.frame.video.VideoStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

private val Cream = Color(0xFFF7F4EE)
private val Ink = Color(0xFF242521)
private val Coral = Color(0xFFFF624F)
private val Muted = Color(0xFF818176)
private val Paper = Color(0xFFEFEAE1)
private val Dark = Color(0xFF20211F)

@Composable fun FrameApp(model: FrameViewModel = viewModel()) {
    val s = model.state
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(model::importVideo) }
    var clearConfirm by remember { mutableStateOf(false) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) model.background() }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    BackHandler(s.screen != Screen.HOME || s.busy || s.exporting) {
        if (s.busy || s.exporting) model.message("Дождитесь завершения операции или отмените экспорт.") else model.back()
    }
    val isEditor = s.screen == Screen.EDITOR
    SideEffect {
        (context as? Activity)?.window?.let { window ->
            window.statusBarColor = (if (isEditor) Dark else Cream).toArgb()
            window.navigationBarColor = (if (isEditor) Dark else Cream).toArgb()
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !isEditor
                isAppearanceLightNavigationBars = !isEditor
            }
        }
    }
    MaterialTheme(colorScheme = if (isEditor) darkColorScheme(primary = Coral, background = Dark, surface = Dark) else lightColorScheme(primary = Coral, background = Cream, surface = Cream, onBackground = Ink, onSurface = Ink)) {
        Box(Modifier.fillMaxSize().background(if (isEditor) Dark else Cream)) {
            Scaffold(containerColor = if (isEditor) Dark else Cream, contentWindowInsets = WindowInsets(0), bottomBar = {
                if (s.screen !in listOf(Screen.EDITOR, Screen.EXPORT)) BottomBar(s.screen, model::navigate)
            }) { padding ->
                key(s.screen) {
                    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = if (isEditor) 20.dp else 24.dp).padding(top = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        when (s.screen) {
                            Screen.HOME -> {
                                Brand()
                                Heading("Твой стиль.", 40)
                                Body("Шаблоны для Stories, Reels и видеопостов.")
                                Featured(s.collection, model::collection, model::open)
                                Section("Найди вдохновение", "Все →") { model.navigate(Screen.TEMPLATES) }
                                TemplateGrid(Templates.collection(s.collection).drop(1), model::open)
                                LimitsCard()
                            }
                            Screen.TEMPLATES -> {
                                Eyebrow("КОЛЛЕКЦИЯ FRAME"); Heading("Найди свой стиль")
                                Body("Пять шаблонов. Бесконечно много историй.")
                                OutlinedTextField(s.search, model::search, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Поиск шаблонов…") }, leadingIcon = { Icon(Icons.Outlined.Search, null) })
                                Chips(listOf("Все", "Minimal", "Editorial", "Travel"), s.category, model::category)
                                val filtered = Templates.all.filter { (s.category == "Все" || it.category == s.category) && (it.name + it.subtitle).contains(s.search, ignoreCase = true) }
                                TemplateGrid(filtered, model::open)
                                if (filtered.isEmpty()) Body("Ничего не найдено. Попробуйте другой запрос.")
                                LimitsCard()
                            }
                            Screen.PROJECTS -> {
                                Eyebrow("ПРОДОЛЖИТЬ ИСТОРИЮ"); Heading("Мой проект")
                                s.project?.let { p ->
                                    Surface(onClick = model::restore, shape = RoundedCornerShape(16.dp), color = Paper, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Продолжить проект" }) {
                                        Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                            Thumb(p.video, Modifier.width(80.dp).height(120.dp).clip(RoundedCornerShape(10.dp)))
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Text(p.title.ifBlank { "Без названия" }, fontFamily = FontFamily.Serif, fontSize = 21.sp)
                                                Body(Templates.byId(p.templateId)!!.name)
                                                Note(DateFormat.getDateInstance().format(Date(p.updatedAt)))
                                                Text("Продолжить →", color = Coral)
                                            }
                                        }
                                    }
                                } ?: Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                                    Icon(Icons.Outlined.FolderOpen, null, Modifier.size(60.dp), tint = Color(0xFFC4BAAA))
                                    Text("Здесь начнётся твоя история", fontSize = 18.sp, textAlign = TextAlign.Center)
                                    Body("Выбери шаблон, добавь видео и сохрани проект в редакторе.")
                                    ActionButton("Выбрать шаблон", { model.navigate(Screen.TEMPLATES) })
                                }
                                Note("В этой версии доступен один сохранённый проект. Новое сохранение заменяет предыдущее.")
                            }
                            Screen.SETTINGS -> {
                                Brand(); Heading("Просто о важном")
                                InfoCard("Ограничения импорта") { Body("Android 10+\nH.264 или HEVC/H.265, SDR\nДо 1920 × 1080 / 1080 × 1920\nДо 60 кадров/с, 60 секунд и 200 МБ\n4K и HDR пока не поддерживаются") }
                                InfoCard("Что умеет эта версия") { Body("Один клип, пять вариантов оформления, заголовок, выбор фрагмента, отключение звука и экспорт в галерею.\n\nФотоколлажи, музыка и несколько клипов появятся позже.") }
                                ActionButton("Очистить локальные проекты", { clearConfirm = true }, outlined = true)
                                Note("FRAME ${BuildConfig.VERSION_NAME} · Kotlin / Jetpack Compose\nВидео обрабатываются только на устройстве.")
                            }
                            Screen.EDITOR -> {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = model::back) { Icon(Icons.Outlined.ArrowBack, "Назад") }
                                    Text(s.template.name, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 13.sp)
                                    TextButton(onClick = { model.navigate(Screen.EXPORT) }, enabled = s.trimProblem == null) { Text("Далее →") }
                                }
                                Box(Modifier.align(Alignment.CenterHorizontally).widthIn(max = 236.dp).fillMaxWidth().aspectRatio(9f / 16f).clip(RoundedCornerShape(3.dp)).background(Color.Black)) {
                                    val draft = s.draft()
                                    if (draft != null) NativeVideoPreview(draft, Modifier.fillMaxSize()) else {
                                        Image(painterResource(R.drawable.featured_collage), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                        Text(s.title, Modifier.fillMaxWidth().padding(top = 52.dp, start = 12.dp, end = 12.dp), color = Color.White, fontFamily = FontFamily.Serif, fontSize = 24.sp, textAlign = TextAlign.Center)
                                        Text("ПРИМЕР ШАБЛОНА", Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = .5f)).padding(10.dp), color = Color.White, fontSize = 10.sp, textAlign = TextAlign.Center)
                                    }
                                }
                                Note(if (s.video == null) "Добавьте своё видео — пример заменится вашим кадром" else "Нажмите на видео для управления просмотром")
                                ActionButton(if (s.video == null) "+ Добавить видео" else "Заменить видео", { picker.launch(arrayOf("video/*")) }, enabled = !s.busy)
                                OutlinedTextField(s.title, model::title, Modifier.fillMaxWidth(), label = { Text("Заголовок") }, maxLines = 3)
                                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    OutlinedTextField(s.start, model::start, Modifier.weight(1f), label = { Text("Начало, сек") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                                    OutlinedTextField(s.length, model::length, Modifier.weight(1f), label = { Text("Длительность, сек") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                                }
                                s.video?.let { v -> Text(s.trimProblem ?: "${v.width} × ${v.height} · ${v.fps.toInt()} кадров/с · ${"%.1f".format(v.bytes / 1048576.0)} МБ", color = if (s.trimProblem != null) Color(0xFFFFB3A8) else Color(0xFFADB0A5), fontSize = 12.sp) }
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("Отключить звук оригинала", Modifier.weight(1f), fontSize = 14.sp)
                                    Switch(s.muted, model::mute, modifier = Modifier.semantics { contentDescription = "Отключить звук оригинала" })
                                }
                                Chips(Templates.all.map { it.name }, s.template.name) { name -> model.template(Templates.all.first { it.name == name }) }
                                ActionButton("Сохранить проект", model::save, enabled = s.trimProblem == null, outlined = true)
                                Note("Экспорт: 9:16, 1080 × 1920, до 30 кадров/с. Кадр заполняется с обрезкой по центру.")
                            }
                            Screen.EXPORT -> {
                                TextButton(model::back, enabled = !s.exporting) { Text("‹ Редактор") }
                                Heading(if (s.savedUri != null) "Готово!" else "Готово к публикации")
                                Body(if (s.savedUri != null) "Видео сохранено в галерее, альбом Movies/FRAME." else "Ваша история, в лучшем качестве.")
                                Box(Modifier.align(Alignment.CenterHorizontally).width(146.dp).height(260.dp).clip(RoundedCornerShape(4.dp))) {
                                    s.video?.let { Thumb(it, Modifier.fillMaxSize()) }
                                    Text(s.title, Modifier.fillMaxWidth().padding(top = 36.dp).background(Color.Black.copy(alpha = .3f)), color = Color.White, fontFamily = FontFamily.Serif, fontSize = 20.sp, textAlign = TextAlign.Center)
                                }
                                InfoCard("Параметры видео") {
                                    Setting("Формат", "MP4 · H.264"); Setting("Разрешение", "1080 × 1920"); Setting("Частота кадров", "до 30 кадров/с"); Setting("Длительность", "${s.durationMs / 1000.0} сек")
                                }
                                if (s.exporting) { Text("Обработка видео… ${s.progress}%"); LinearProgressIndicator(progress = { s.progress / 100f }, modifier = Modifier.fillMaxWidth()); Note("Оставьте приложение открытым до завершения.") }
                                ActionButton(if (s.exporting) "Экспортируем…" else if (s.savedUri != null) "Сохранить ещё раз" else "Сохранить видео", model::export, enabled = !s.exporting)
                                if (s.exporting) ActionButton("Отменить экспорт", model::cancelExport, outlined = true)
                                s.savedUri?.let { uri -> ActionButton("Открыть видео", {
                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(uri), "video/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }.onFailure { model.message("Видео сохранено. Откройте галерею → альбом FRAME.") }
                                }, outlined = true) }
                                Note("Обработка происходит на телефоне. Видео не отправляется на сервер.")
                            }
                        }
                    }
                }
            }
            if (s.busy) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .65f)).pointerInput(Unit) { awaitPointerEventScope { while (true) { awaitPointerEvent().changes.forEach { it.consume() } } } }, contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) { CircularProgressIndicator(color = Coral); Text("Проверяем и сохраняем…", color = Color.White) }
            }
        }
        s.message?.let { text -> AlertDialog(onDismissRequest = { model.message(null) }, title = { Text("FRAME") }, text = { Text(text) }, confirmButton = { TextButton({ model.message(null) }) { Text("OK") } }) }
        if (clearConfirm) AlertDialog(onDismissRequest = { clearConfirm = false }, title = { Text("Очистить проекты?") }, text = { Text("Проект и импортированные копии будут удалены. Оригиналы и экспорт в галерее сохранятся.") }, confirmButton = { TextButton({ clearConfirm = false; model.clear() }) { Text("Удалить") } }, dismissButton = { TextButton({ clearConfirm = false }) { Text("Отмена") } })
    }
}

@Composable private fun BottomBar(current: Screen, navigate: (Screen) -> Unit) {
    NavigationBar(modifier = Modifier.height(72.dp), windowInsets = WindowInsets(0), containerColor = Color(0xFFFDFBF7), tonalElevation = 0.dp) {
        listOf(Triple(Screen.HOME, "Главная", Icons.Outlined.Home), Triple(Screen.TEMPLATES, "Шаблоны", Icons.Outlined.GridView), Triple(Screen.PROJECTS, "Проекты", Icons.Outlined.FolderOpen), Triple(Screen.SETTINGS, "О приложении", Icons.Outlined.Tune)).forEach { (screen, label, icon) ->
            NavigationBarItem(current == screen, { navigate(screen) }, icon = { Icon(icon, null) }, label = { Text(label, fontSize = 10.sp) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Coral, selectedTextColor = Coral, indicatorColor = Color.Transparent), modifier = Modifier.semantics { contentDescription = label })
        }
    }
}
@Composable private fun Brand() {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text("FRAME", fontFamily = FontFamily.Serif, fontSize = 29.sp, letterSpacing = 7.sp, color = Ink)
        Text("EARLY ACCESS", Modifier.border(1.dp, Color(0xFFDDD5CA), RoundedCornerShape(24.dp)).padding(horizontal = 10.dp, vertical = 8.dp), color = Color(0xFF8D7B68), fontSize = 8.sp, letterSpacing = 1.sp)
    }
}
@Composable private fun Heading(text: String, size: Int = 34) { Text(text, fontSize = size.sp, fontFamily = FontFamily.Serif, lineHeight = (size + 4).sp) }
@Composable private fun Body(text: String) { Text(text, color = Muted, fontSize = 14.sp, lineHeight = 22.sp) }
@Composable private fun Note(text: String) { Text(text, color = Muted, fontSize = 12.sp, lineHeight = 19.sp) }
@Composable private fun Eyebrow(text: String) { Text(text, color = Color(0xFF8D806D), fontSize = 10.sp, letterSpacing = 2.sp) }
@Composable private fun ActionButton(label: String, action: () -> Unit, enabled: Boolean = true, outlined: Boolean = false) {
    val modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).semantics { contentDescription = label }
    if (outlined) OutlinedButton(action, modifier, enabled = enabled, border = BorderStroke(1.dp, Coral)) { Text(label, color = if (enabled) Coral else Muted) }
    else Button(action, modifier, enabled = enabled, colors = ButtonDefaults.buttonColors(containerColor = Coral, contentColor = Color.White)) { Text(label, fontSize = 15.sp) }
}
@Composable private fun Chips(items: List<String>, selected: String, select: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { items.forEach { FilterChip(it == selected, { select(it) }, { Text(it) }) } }
}
@Composable private fun Section(text: String, action: String, click: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(text, Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.SemiBold); TextButton(click) { Text(action) } }
}
@Composable private fun InfoCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Paper).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink); content() }
}
@Composable private fun LimitsCard() = InfoCard("Легко для телефона") { Body("H.264 / HEVC · SDR · до 1080p · 60 кадров/с · 60 сек · 200 МБ"); Note("Оригиналы остаются у вас. Экспорт — до 30 секунд.") }
@Composable private fun Setting(label: String, value: String) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, fontSize = 13.sp); Note(value) } }

@Composable private fun Featured(collection: String, change: (String) -> Unit, open: (Template) -> Unit) {
    val items = Templates.collection(collection)
    val pager = rememberPagerState(pageCount = { items.size })
    val scope = rememberCoroutineScope()
    LaunchedEffect(collection) { pager.scrollToPage(0) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(Color(0xFFE8E4DE)).padding(4.dp)) {
            listOf("Stories", "Reels", "Posts").forEach { label ->
                Box(Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(10.dp)).background(if (collection == label) Color(0xFFFFFDF9) else Color.Transparent).selectable(collection == label, role = Role.Tab, onClick = { change(label) }).semantics { contentDescription = label }, contentAlignment = Alignment.Center) { Text(label, color = Ink, fontSize = 12.sp, fontWeight = if (collection == label) FontWeight.SemiBold else FontWeight.Normal) }
            }
        }
        HorizontalPager(pager, Modifier.fillMaxWidth().aspectRatio(330f / 199f).clip(RoundedCornerShape(14.dp)), key = { collection + items[it].id }) { i ->
            BoxWithConstraints(Modifier.fillMaxSize().background(Paper)) {
                Image(painterResource(R.drawable.featured_collage), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                val density = LocalDensity.current
                val captionSize = with(density) { (maxWidth * .019f).toSp() }
                val titleSize = with(density) { (maxWidth * .075f).toSp() }
                Text(when (collection) { "Reels" -> "MAKE EVERY\nMOMENT COUNT"; "Posts" -> "LITTLE NOTES\nFROM YOUR LIFE"; else -> "A QUIETER\nKIND OF DAY" }, Modifier.offset(x = maxWidth * .065f, y = maxHeight * .12f), color = Color(0xFF55594F), fontSize = captionSize, lineHeight = with(density) { (maxWidth * .03f).toSp() }, letterSpacing = 1.8.sp)
                Text(items[i].title, Modifier.offset(x = maxWidth * .065f, y = maxHeight * .31f).width(maxWidth * .54f), color = Ink, fontFamily = FontFamily.Serif, fontSize = titleSize, lineHeight = with(density) { (maxWidth * .08f).toSp() }, maxLines = 3)
                FilledIconButton({ open(items[i]) }, Modifier.align(Alignment.BottomEnd).padding(end = maxWidth * .04f, bottom = maxHeight * .1f).size(36.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFFFFDF9), contentColor = Ink)) { Icon(Icons.Outlined.ArrowForward, "Открыть ${items[i].name}", Modifier.size(18.dp)) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            items.forEachIndexed { i, _ -> Box(Modifier.size(20.dp, 28.dp).selectable(pager.currentPage == i, role = Role.Button, onClick = { scope.launch { pager.animateScrollToPage(i) } }).semantics { contentDescription = "Слайд ${i + 1} из ${items.size}" }, contentAlignment = Alignment.Center) { Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(if (pager.currentPage == i) Coral else Color(0xFFD6D2CB))) } }
        }
    }
}
@Composable private fun TemplateGrid(items: List<Template>, open: (Template) -> Unit) {
    items.chunked(2).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        row.forEach { t -> Column(Modifier.weight(1f).clickable { open(t) }.semantics { contentDescription = "Открыть шаблон ${t.name}" }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(.70f).clip(RoundedCornerShape(13.dp))) {
                Image(painterResource(R.drawable.featured_collage), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Text(t.tag, Modifier.padding(8.dp).background(Cream.copy(alpha = .9f), RoundedCornerShape(4.dp)).padding(5.dp), color = Ink, fontSize = 8.sp, letterSpacing = 1.sp)
                Text(t.title, Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color.Black.copy(alpha = .3f)).padding(12.dp), color = Color.White, fontFamily = FontFamily.Serif, fontSize = 20.sp)
            }
            Text(t.name, fontFamily = FontFamily.Serif, fontSize = 19.sp)
            Note(t.subtitle)
        } }
        if (row.size == 1) Spacer(Modifier.weight(1f))
    } }
}
@Composable private fun Thumb(video: VideoData, modifier: Modifier) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, video.thumbnail) { value = withContext(Dispatchers.IO) { video.thumbnail?.let { runCatching { BitmapFactory.decodeFile(Uri.parse(it).path) }.getOrNull() } } }
    val b = bitmap
    if (b != null) Image(b.asImageBitmap(), null, modifier, contentScale = ContentScale.Crop) else Image(painterResource(R.drawable.featured_collage), null, modifier, contentScale = ContentScale.Crop)
}
@Composable private fun NativeVideoPreview(project: Project, modifier: Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val player = remember(context) { ExoPlayer.Builder(context).build().apply { repeatMode = Player.REPEAT_MODE_ONE } }
    var error by remember { mutableStateOf<String?>(null) }
    DisposableEffect(player, owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) player.pause() }
        val listener = object : Player.Listener { override fun onPlayerError(e: PlaybackException) { error = "Не удалось воспроизвести видео: ${e.message}" } }
        player.addListener(listener); owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); player.removeListener(listener); player.release() }
    }
    LaunchedEffect(project.video.uri, project.startMs, project.endMs, project.title, project.templateId, project.muted) {
        delay(350); error = null
        player.pause(); player.setVideoEffects(VideoStyle.effects(project.templateId, project.title))
        player.volume = if (project.muted) 0f else 1f
        player.setMediaItem(MediaItem.Builder().setUri(project.video.uri).setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(project.startMs).setEndPositionMs(project.endMs).build()).build())
        player.prepare()
    }
    Box(modifier) {
        AndroidView(factory = { PlayerView(it).apply { this.player = player; useController = true; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM } }, modifier = Modifier.fillMaxSize())
        error?.let { Text(it, Modifier.align(Alignment.Center).background(Dark.copy(alpha = .85f)).padding(16.dp), color = Color.White, fontSize = 13.sp) }
    }
}
