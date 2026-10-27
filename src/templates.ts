export type Template = {
  id: string; name: string; subtitle: string; tag: string; title: string;
  category: 'Editorial' | 'Minimal' | 'Travel';
  crop: [number, number, number, number];
};
export const templates: Template[] = [
  {id: 'journal', name: 'Sunday Journal', subtitle: 'Твоя маленькая история', tag: 'EDITORIAL', title: 'SUNDAY\nJOURNAL', category: 'Editorial', crop: [86, 317, 330, 199]},
  {id: 'minimal', name: 'Quiet Moment', subtitle: 'Красота простых моментов', tag: 'MINIMAL', title: 'A quiet moment', category: 'Minimal', crop: [681, 320, 150, 249]},
  {id: 'film', name: 'Film Diary', subtitle: 'Чёрно-белое кино о тебе', tag: 'MONOCHROME', title: 'THE DAILY EDIT', category: 'Editorial', crop: [515, 577, 152, 214]},
  {id: 'postcard', name: 'Coastal Days', subtitle: 'Привет из любимого места', tag: 'TRAVEL', title: 'Coastal days', category: 'Travel', crop: [258, 581, 155, 207]},
  {id: 'bold', name: 'Make It Bold', subtitle: 'Твой момент. Твои правила.', tag: 'STATEMENT', title: 'MAKE IT\nYOURS.', category: 'Minimal', crop: [87, 581, 156, 208]},
];
export const limitsLabel = 'H.264 / HEVC · SDR · до 1080p · 60 кадров/с · 60 сек · 200 МБ';
