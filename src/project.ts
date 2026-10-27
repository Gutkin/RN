import {templates} from './templates';
export type Video = {id: string; uri: string; thumbnail?: string; width: number; height: number; bytes: number; durationMs: number; fps: number};
export type Project = {version: 1; templateId: string; title: string; video: Video; startMs: number; endMs: number; muted: boolean; updatedAt: number};
export function trimProblem(startMs: number, endMs: number, durationMs: number): string | null {
  if (![startMs, endMs, durationMs].every(Number.isFinite)) return 'Укажите время числом';
  if (startMs < 0 || endMs > durationMs) return 'Фрагмент должен быть в пределах исходного видео';
  if (endMs - startMs < 1000 || endMs - startMs > 30000) return 'Длительность фрагмента — от 1 до 30 секунд';
  return null;
}
export function restoreProject(json: string): Project | null {
  try {
    const p = JSON.parse(json);
    if (p.version !== 1 || !templates.some(t => t.id === p.templateId) || typeof p.title !== 'string' || p.title.length > 45 || typeof p.muted !== 'boolean') return null;
    if (!p.video || typeof p.video.uri !== 'string' || !p.video.uri.startsWith('file://') || typeof p.video.durationMs !== 'number' || !Number.isFinite(p.updatedAt)) return null;
    if (trimProblem(p.startMs, p.endMs, p.video.durationMs)) return null;
    return p as Project;
  } catch { return null; }
}
