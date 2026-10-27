import React, {useEffect, useMemo, useState} from 'react';
import {ActivityIndicator, Alert, BackHandler, DeviceEventEmitter, Image, Linking, Pressable, SafeAreaView, ScrollView, StatusBar, StyleSheet, Switch, Text, TextInput, View} from 'react-native';
import {templates, Template, limitsLabel} from './src/templates';
import {Project, Video, restoreProject, trimProblem} from './src/project';
import {Source, videoEngine, VideoPreview} from './src/nativeVideo';

type Screen = 'home' | 'templates' | 'projects' | 'settings' | 'editor' | 'export';
const coral = '#FF624F', ink = '#242521', cream = '#F7F4EE';
const message = (e: unknown) => e instanceof Error ? e.message : String(e);

// The concept board is a local image atlas; no external image service is required.
function Art({template, height = 230}: {template: Template; height?: number}) {
  const [width, setWidth] = useState(160);
  const [x, y, w] = template.crop;
  const scale = Math.max(width / w, height / template.crop[3]);
  return <View onLayout={e => setWidth(e.nativeEvent.layout.width)} style={{height, overflow: 'hidden', backgroundColor: '#D2CEC4'}}>
    <Image source={require('./assets/concept.png')} accessible={false} style={{position: 'absolute', width: 1774 * scale, height: 887 * scale, left: -x * scale - (w * scale - width) / 2, top: -y * scale}} />
  </View>;
}
function Button({label, onPress, disabled, secondary = false}: {label: string; onPress: () => void; disabled?: boolean; secondary?: boolean}) {
  return <Pressable accessibilityRole="button" accessibilityState={{disabled: !!disabled}} disabled={disabled} onPress={onPress} style={({pressed}) => [s.button, secondary && s.secondary, {opacity: disabled ? 0.4 : pressed ? 0.75 : 1}]}><Text style={[s.buttonLabel, secondary && {color: coral}]}>{label}</Text></Pressable>;
}

export default function App() {
  const [screen, setScreen] = useState<Screen>('home');
  const [template, setTemplate] = useState(templates[0]);
  const [video, setVideo] = useState<Video | null>(null);
  const [title, setTitle] = useState(templates[0].title);
  const [start, setStart] = useState('0');
  const [length, setLength] = useState('15');
  const [muted, setMuted] = useState(false);
  const [busy, setBusy] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [progress, setProgress] = useState(0);
  const [savedUri, setSavedUri] = useState<string | null>(null);
  const [project, setProject] = useState<Project | null>(null);
  const [category, setCategory] = useState('Все');
  const [search, setSearch] = useState('');
  const startMs = Math.round(Number(start.replace(',', '.')) * 1000);
  const endMs = startMs + Math.round(Number(length.replace(',', '.')) * 1000);
  const problem = video ? trimProblem(startMs, endMs, video.durationMs) : 'Добавьте видео';
  const source = useMemo<Source | null>(() => video && !problem ? {uri: video.uri, startMs, endMs, style: template.id, title, muted} : null, [video, problem, startMs, endMs, template.id, title, muted]);
  const [previewSource, setPreviewSource] = useState<Source | null>(null);
  useEffect(() => { const timer = setTimeout(() => setPreviewSource(source), 400); return () => clearTimeout(timer); }, [source]);
  useEffect(() => {
    videoEngine.loadProject().then(json => { if (json) setProject(restoreProject(json)); }).catch(() => {});
    const subscription = DeviceEventEmitter.addListener('FrameExportProgress', value => setProgress(Number(value) || 0));
    return () => subscription.remove();
  }, []);
  useEffect(() => {
    const subscription = BackHandler.addEventListener('hardwareBackPress', () => {
      if (exporting || busy) { Alert.alert('Подождите', 'Дождитесь завершения операции или отмените экспорт.'); return true; }
      if (screen === 'export') { setScreen('editor'); return true; }
      if (screen !== 'home') { setScreen('home'); return true; }
      return false;
    });
    return () => subscription.remove();
  }, [screen, exporting, busy]);

  function openTemplate(t: Template) { setTemplate(t); setTitle(t.title); setSavedUri(null); setScreen('editor'); }
  async function pick() {
    setBusy(true);
    try {
      const result = await videoEngine.pickVideo();
      if (result) { setVideo(result); setStart('0'); setLength(String(Math.min(15, Math.floor(result.durationMs / 1000)))); setSavedUri(null); }
    } catch (e) { Alert.alert('Видео не подходит', message(e)); }
    finally { setBusy(false); }
  }
  async function saveDraft() {
    if (!video || problem) { Alert.alert('Проверьте фрагмент', problem || 'Добавьте видео'); return; }
    const draft: Project = {version: 1, templateId: template.id, title, video, startMs, endMs, muted, updatedAt: Date.now()};
    setBusy(true);
    try { await videoEngine.saveProject(JSON.stringify(draft)); setProject(draft); Alert.alert('Проект сохранён', 'Его можно продолжить во вкладке «Проекты». В этой версии хранится один проект.'); }
    catch (e) { Alert.alert('Не удалось сохранить', message(e)); }
    finally { setBusy(false); }
  }
  function clearProjects() {
    Alert.alert('Очистить проекты?', 'Сохранённый проект и импортированные копии будут удалены. Видео в галерее останутся.', [
      {text: 'Отмена', style: 'cancel'},
      {text: 'Удалить', style: 'destructive', onPress: async () => {
        setBusy(true);
        try { await videoEngine.clearProjects(); setProject(null); setVideo(null); setPreviewSource(null); }
        catch (e) { Alert.alert('Не удалось очистить', message(e)); }
        finally { setBusy(false); }
      }},
    ]);
  }
  function openProject() {
    if (!project) return;
    setVideo(project.video); setTemplate(templates.find(t => t.id === project.templateId)!); setTitle(project.title);
    setStart(String(project.startMs / 1000)); setLength(String((project.endMs - project.startMs) / 1000)); setMuted(project.muted); setSavedUri(null); setScreen('editor');
  }
  async function exportVideo() {
    if (!source) return;
    setExporting(true); setProgress(0); setSavedUri(null);
    try { const uri = await videoEngine.exportVideo(source); setSavedUri(uri); }
    catch (e) { Alert.alert('Экспорт не завершён', message(e)); }
    finally { setExporting(false); }
  }
  const filtered = templates.filter(t => (category === 'Все' || category === t.category) && (t.name + t.subtitle).toLowerCase().includes(search.toLowerCase()));
  const dark = screen === 'editor';
  return <SafeAreaView style={[s.root, dark && {backgroundColor: '#20211F'}]}>
    <StatusBar barStyle={dark ? 'light-content' : 'dark-content'} backgroundColor={dark ? '#20211F' : cream} />
    {screen === 'editor' ? <>
      <View style={s.editorBar}><Pressable accessibilityRole="button" onPress={() => setScreen('home')}><Text style={s.light}>‹ Назад</Text></Pressable><Text style={s.light}>{template.name}</Text><Pressable disabled={!source || busy} onPress={() => setScreen('export')}><Text style={{color: source ? coral : '#777'}}>Далее →</Text></Pressable></View>
      <ScrollView contentContainerStyle={{padding: 20, paddingTop: 4, paddingBottom: 40}} keyboardShouldPersistTaps="handled">
        <View style={s.canvas}>{previewSource ? <VideoPreview style={StyleSheet.absoluteFill} source={previewSource} /> : <><Art template={template} height={420} /><View style={s.demoBadge}><Text style={s.demoText}>ПРИМЕР ШАБЛОНА</Text></View></>}</View>
        <Text style={s.editorHint}>{video ? 'Нажмите на видео для управления просмотром' : 'Добавьте своё видео — пример заменится вашим кадром'}</Text>
        <Button label={busy ? 'Проверяем видео…' : video ? 'Заменить видео' : '+ Добавить видео'} onPress={pick} disabled={busy} />
        <Text style={s.darkLabel}>ЗАГОЛОВОК</Text>
        <TextInput accessibilityLabel="Заголовок видео" value={title} onChangeText={setTitle} maxLength={45} multiline style={s.titleInput} placeholder="Ваша история" placeholderTextColor="#999" />
        <View style={s.row}>
          <View style={s.flex}><Text style={s.darkLabel}>НАЧАЛО, СЕК</Text><TextInput accessibilityLabel="Начало фрагмента в секундах" value={start} onChangeText={setStart} keyboardType="decimal-pad" style={s.numberInput} /></View>
          <View style={s.flex}><Text style={s.darkLabel}>ДЛИТЕЛЬНОСТЬ, СЕК</Text><TextInput accessibilityLabel="Длительность фрагмента в секундах" value={length} onChangeText={setLength} keyboardType="decimal-pad" style={s.numberInput} /></View>
        </View>
        {video && <Text style={{color: problem ? '#FFB3A8' : '#B3B8AD', marginTop: 10}}>{problem || `${video.width} × ${video.height} · ${Math.round(video.fps)} кадров/с · ${(video.bytes / 1048576).toFixed(1)} МБ`}</Text>}
        <View style={[s.between, {marginVertical: 16}]}><Text style={s.light}>Отключить звук оригинала</Text><Switch value={muted} onValueChange={setMuted} trackColor={{true: coral}} /></View>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{gap: 8, marginBottom: 18}}>{templates.map(t => <Pressable key={t.id} onPress={() => {setTemplate(t); setTitle(t.title);}} style={[s.styleChip, template.id === t.id && {borderColor: coral}]}><Text style={{color: template.id === t.id ? coral : '#DDD'}}>{t.name}</Text></Pressable>)}</ScrollView>
        <Button label="Сохранить проект" onPress={saveDraft} disabled={!source || busy} secondary />
        <Text style={s.editorHint}>Экспорт: 9:16, 1080 × 1920, до 30 кадров/с. Кадр заполняется с обрезкой по центру.</Text>
      </ScrollView>
    </> : screen === 'export' ? <ScrollView contentContainerStyle={s.page}>
      <Pressable disabled={exporting} onPress={() => setScreen('editor')}><Text style={s.back}>‹ Редактор</Text></Pressable>
      <Text style={[s.heading, {textAlign: 'center', marginTop: 28}]}>{savedUri ? 'Готово!' : 'Готово к публикации'}</Text><Text style={[s.body, {textAlign: 'center'}]}>{savedUri ? 'Видео сохранено в галерее, альбом Movies/FRAME.' : 'Ваша история, в лучшем качестве.'}</Text>
      <View style={s.exportThumb}>{video?.thumbnail ? <Image source={{uri: video.thumbnail}} style={StyleSheet.absoluteFill} resizeMode="cover" /> : <Art template={template} height={260} />}<Text style={s.thumbTitle}>{title}</Text></View>
      <View style={s.settingsCard}>{[['Формат', 'MP4 · H.264'], ['Разрешение', '1080 × 1920'], ['Частота кадров', 'до 30 кадров/с'], ['Длительность', `${(endMs - startMs) / 1000} сек`]].map(([label, value]) => <View key={label} style={s.settingRow}><Text style={{color: ink}}>{label}</Text><Text style={s.body}>{value}</Text></View>)}</View>
      {exporting && <View accessibilityLiveRegion="polite" style={{marginBottom: 18}}><Text style={s.body}>Обработка видео… {progress}%</Text><View style={s.progressTrack}><View style={{height: 5, width: `${Math.max(2, progress)}%`, backgroundColor: coral}} /></View><Text style={s.note}>Оставьте приложение открытым до завершения.</Text></View>}
      <Button label={exporting ? 'Экспортируем…' : savedUri ? 'Сохранить ещё раз' : 'Сохранить видео'} onPress={exportVideo} disabled={exporting} />
      {exporting ? <Button label="Отменить экспорт" onPress={() => videoEngine.cancelExport()} secondary /> : savedUri ? <Button label="Открыть видео" onPress={() => Linking.openURL(savedUri).catch(() => Alert.alert('Видео сохранено', 'Откройте галерею → альбом FRAME.'))} secondary /> : null}
      <Text style={s.note}>Обработка происходит на телефоне. Видео не отправляется на сервер.</Text>
    </ScrollView> : <>
      <ScrollView contentContainerStyle={s.page} keyboardShouldPersistTaps="handled">
        {screen === 'home' && <>
          <View style={s.between}><Text style={s.wordmark}>FRAME</Text><View style={s.pill}><Text style={s.pillText}>EARLY ACCESS</Text></View></View>
          <Text style={[s.heading, {fontSize: 40, marginTop: 28}]}>Твоя история.{`\n`}Твой стиль.</Text><Text style={s.body}>Маленькие моменты. Красивые истории.</Text>
          <View style={s.modeRow}><View style={s.modeActive}><Text style={{fontWeight: '600', color: ink}}>Stories & Reels</Text></View><Text style={s.body}>9:16 · видео</Text></View>
          <Pressable onPress={() => openTemplate(templates[0])} style={s.hero}><Art template={templates[0]} height={240} /><View style={s.heroFooter}><View><Text style={s.eyebrow}>ШАБЛОН НЕДЕЛИ</Text><Text style={s.heroTitle}>Sunday Journal</Text></View><Text style={s.roundArrow}>↗</Text></View></Pressable>
          <View style={[s.between, {marginTop: 28, marginBottom: 16}]}><Text style={s.sectionTitle}>Найди вдохновение</Text><Pressable onPress={() => setScreen('templates')}><Text style={{color: coral}}>Все →</Text></Pressable></View>
        </>}
        {screen === 'templates' && <><Text style={s.eyebrow}>КОЛЛЕКЦИЯ FRAME</Text><Text style={s.heading}>Найди свой стиль</Text><Text style={s.body}>Пять шаблонов. Бесконечно много историй.</Text><TextInput accessibilityLabel="Поиск шаблонов" placeholder="Поиск шаблонов…" placeholderTextColor="#92918A" value={search} onChangeText={setSearch} style={s.search} /><ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{gap: 8, marginBottom: 20}}>{['Все', 'Minimal', 'Editorial', 'Travel'].map(c => <Pressable key={c} onPress={() => setCategory(c)} style={[s.chip, category === c && {backgroundColor: coral}]}><Text style={{color: category === c ? '#FFF' : ink}}>{c}</Text></Pressable>)}</ScrollView></>}
        {(screen === 'home' || screen === 'templates') && <><View style={s.grid}>{(screen === 'home' ? templates.slice(1, 5) : filtered).map(t => <Pressable key={t.id} accessibilityRole="button" accessibilityLabel={`Открыть шаблон ${t.name}`} style={s.card} onPress={() => openTemplate(t)}><View style={s.cardArt}><Art template={t} /><View style={s.cardTag}><Text style={s.cardTagText}>{t.tag}</Text></View></View><Text style={s.cardTitle}>{t.name}</Text><Text style={s.cardSubtitle}>{t.subtitle}</Text></Pressable>)}</View>{screen === 'templates' && !filtered.length && <Text style={s.body}>Ничего не найдено. Попробуйте другой запрос.</Text>}<View style={s.limitCard}><Text style={s.sectionTitle}>Легко для телефона</Text><Text style={[s.body, {marginTop: 8}]}>{limitsLabel}</Text><Text style={s.note}>Оригиналы остаются у вас. Экспорт — до 30 секунд.</Text></View></>}
        {screen === 'projects' && <><Text style={s.eyebrow}>ПРОДОЛЖИТЬ ИСТОРИЮ</Text><Text style={s.heading}>Мой проект</Text>{project ? <Pressable onPress={openProject} style={s.projectCard}>{project.video.thumbnail && <Image source={{uri: project.video.thumbnail}} style={{width: 90, height: 130, borderRadius: 12}} />}<View style={s.flex}><Text style={s.sectionTitle}>{project.title || 'Без названия'}</Text><Text style={s.body}>{templates.find(t => t.id === project.templateId)?.name}</Text><Text style={s.note}>{new Date(project.updatedAt).toLocaleDateString('ru-RU')}</Text><Text style={{color: coral, marginTop: 12}}>Продолжить →</Text></View></Pressable> : <View style={s.empty}><Text style={s.emptyIcon}>▧</Text><Text style={s.sectionTitle}>Здесь начнётся твоя история</Text><Text style={[s.body, {textAlign: 'center', marginVertical: 18}]}>Выбери шаблон, добавь видео и сохрани проект в редакторе.</Text><Button label="Выбрать шаблон" onPress={() => setScreen('templates')} /></View>}<Text style={s.note}>В первой версии доступен один сохранённый проект. Новое сохранение заменяет предыдущее.</Text></>}
        {screen === 'settings' && <><Text style={s.wordmark}>FRAME</Text><Text style={s.heading}>Просто о важном</Text><View style={s.limitCard}><Text style={s.sectionTitle}>Ограничения импорта</Text><Text style={[s.body, {lineHeight: 28, marginTop: 14}]}>• Android 10 и новее{`\n`}• H.264, обычное SDR-видео{`\n`}• До 1920 × 1080 или 1080 × 1920{`\n`}• До 60 кадров/с{`\n`}• До 60 секунд и 200 МБ на файл{`\n`}• 4K, HDR и HEVC не принимаются</Text></View><View style={s.limitCard}><Text style={s.sectionTitle}>Что умеет эта версия</Text><Text style={[s.body, {lineHeight: 25, marginTop: 12}]}>Одно видео в проекте, пять вариантов оформления, заголовок, выбор фрагмента, отключение звука и экспорт в галерею.{`\n\n`}Фотоколлажи, несколько клипов и музыка появятся в следующих версиях.</Text></View><View style={{marginTop: 24}}><Button label="Очистить локальные проекты" onPress={clearProjects} secondary /></View><Text style={s.note}>FRAME 0.1.0 · Тестовая сборка{`\n`}Ваши видео обрабатываются только на устройстве.</Text></>}
      </ScrollView>
      <View style={s.nav}>{([{id: 'home', icon: '⌂', label: 'Главная'}, {id: 'templates', icon: '▦', label: 'Шаблоны'}, {id: 'projects', icon: '▧', label: 'Проекты'}, {id: 'settings', icon: '☷', label: 'О приложении'}] as const).map(tab => <Pressable key={tab.id} accessibilityRole="tab" accessibilityState={{selected: screen === tab.id}} onPress={() => setScreen(tab.id)} style={s.navItem}><Text style={[s.navIcon, screen === tab.id && {color: coral}]}>{tab.icon}</Text><Text style={[s.navLabel, screen === tab.id && {color: coral}]}>{tab.label}</Text></Pressable>)}</View>
    </>}
    {busy && <View style={s.busy}><ActivityIndicator size="large" color={coral} /><Text style={{color: '#FFF', marginTop: 14}}>Проверяем и сохраняем…</Text></View>}
  </SafeAreaView>;
}

const s = StyleSheet.create({
  root: {flex: 1, backgroundColor: cream}, page: {padding: 24, paddingTop: 24, paddingBottom: 32}, flex: {flex: 1}, row: {flexDirection: 'row', gap: 14}, between: {flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between'},
  wordmark: {fontFamily: 'serif', fontSize: 29, letterSpacing: 7, color: ink}, heading: {fontFamily: 'serif', fontSize: 34, color: ink, marginBottom: 12, marginTop: 12, lineHeight: 42}, body: {color: '#7C7D75', fontSize: 14, lineHeight: 21},
  pill: {borderWidth: 1, borderColor: '#DDD5CA', borderRadius: 20, paddingHorizontal: 10, paddingVertical: 7}, pillText: {fontSize: 9, letterSpacing: 1.2, color: '#8D7B68'}, modeRow: {backgroundColor: '#EBE7E0', borderRadius: 13, padding: 5, marginVertical: 24, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingRight: 16}, modeActive: {backgroundColor: '#FFFDF9', paddingVertical: 10, paddingHorizontal: 20, borderRadius: 10},
  hero: {borderRadius: 18, overflow: 'hidden', backgroundColor: '#EAE4D9'}, heroFooter: {padding: 18, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center'}, eyebrow: {fontSize: 10, letterSpacing: 2, color: '#8D806D', marginBottom: 5}, heroTitle: {fontSize: 27, fontFamily: 'serif', color: ink}, roundArrow: {backgroundColor: '#FFFDF9', borderRadius: 24, width: 44, height: 44, textAlign: 'center', textAlignVertical: 'center', fontSize: 26, color: ink}, sectionTitle: {fontSize: 18, fontWeight: '600', color: ink},
  grid: {flexDirection: 'row', flexWrap: 'wrap', gap: 16}, card: {width: '47%', marginBottom: 8}, cardArt: {borderRadius: 13, overflow: 'hidden'}, cardTitle: {color: ink, fontFamily: 'serif', fontSize: 19, marginTop: 12}, cardSubtitle: {color: '#929087', fontSize: 11, lineHeight: 17, marginTop: 4}, cardTag: {position: 'absolute', top: 10, left: 8, backgroundColor: '#FCFAF1E8', borderRadius: 4, padding: 5}, cardTagText: {fontSize: 8, letterSpacing: 1, color: '#58584C'}, search: {marginVertical: 20, backgroundColor: '#EBE7DF', borderRadius: 12, paddingHorizontal: 17, paddingVertical: 13, color: ink}, chip: {backgroundColor: '#EAE6DE', paddingHorizontal: 18, paddingVertical: 11, borderRadius: 24}, limitCard: {backgroundColor: '#EFEAE1', borderRadius: 16, padding: 20, marginTop: 26}, note: {fontSize: 12, lineHeight: 19, color: '#8E8D82', marginTop: 12},
  nav: {flexDirection: 'row', paddingTop: 10, paddingBottom: 13, borderTopWidth: 1, borderColor: '#E6E1D8', backgroundColor: '#FDFBF7'}, navItem: {flex: 1, alignItems: 'center', gap: 4}, navIcon: {fontSize: 25, color: '#85857C'}, navLabel: {fontSize: 10, color: '#85857C'},
  editorBar: {flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: 20}, light: {color: '#F8F5EC', fontSize: 13}, canvas: {height: 420, aspectRatio: 9 / 16, alignSelf: 'center', backgroundColor: '#111', overflow: 'hidden', borderRadius: 3}, editorHint: {color: '#999E93', fontSize: 11, textAlign: 'center', lineHeight: 17, marginVertical: 14}, demoBadge: {position: 'absolute', bottom: 12, left: 12, right: 12, backgroundColor: '#20211FAA', padding: 8}, demoText: {textAlign: 'center', fontSize: 10, color: '#FFF', letterSpacing: 2}, darkLabel: {color: '#ADB0A5', fontSize: 10, letterSpacing: 1.4, marginTop: 18, marginBottom: 9}, titleInput: {backgroundColor: '#30322D', borderRadius: 12, color: '#FFF9ED', padding: 14, fontFamily: 'serif', fontSize: 22, minHeight: 75}, numberInput: {color: '#FFF9ED', fontSize: 18, backgroundColor: '#30322D', paddingHorizontal: 15, paddingVertical: 10, borderRadius: 10}, styleChip: {borderWidth: 1, borderColor: '#45483E', borderRadius: 20, padding: 12},
  button: {backgroundColor: coral, paddingVertical: 15, paddingHorizontal: 18, borderRadius: 26, alignItems: 'center', marginBottom: 12}, secondary: {backgroundColor: 'transparent', borderColor: coral, borderWidth: 1}, buttonLabel: {color: '#FFF', fontSize: 15, fontWeight: '600'}, back: {fontSize: 15, color: '#77776C'}, exportThumb: {height: 260, width: 146, alignSelf: 'center', overflow: 'hidden', borderRadius: 4, backgroundColor: '#DED8CA', marginVertical: 25}, thumbTitle: {position: 'absolute', top: 35, left: 12, right: 12, textAlign: 'center', color: '#FFF', fontFamily: 'serif', fontSize: 19, backgroundColor: '#0005'}, settingsCard: {backgroundColor: '#FFFDF8', borderRadius: 15, paddingHorizontal: 15, marginBottom: 24}, settingRow: {flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 15, borderBottomWidth: 0.5, borderColor: '#E7E3D9'}, progressTrack: {height: 5, borderRadius: 4, overflow: 'hidden', backgroundColor: '#DED8CC', marginTop: 10}, projectCard: {backgroundColor: '#EFEAE1', borderRadius: 15, padding: 18, gap: 18, flexDirection: 'row', marginTop: 24, alignItems: 'center'}, empty: {alignItems: 'center', paddingVertical: 55}, emptyIcon: {fontSize: 60, color: '#C4BAAA', marginBottom: 20}, busy: {...StyleSheet.absoluteFillObject, backgroundColor: '#161815CC', alignItems: 'center', justifyContent: 'center'},
});
