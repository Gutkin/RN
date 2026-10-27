import {NativeModules, requireNativeComponent, ViewProps} from 'react-native';
import {Video} from './project';
export type Source = {uri: string; startMs: number; endMs: number; style: string; title: string; muted: boolean};
export const videoEngine = NativeModules.FrameVideo as {
  pickVideo(): Promise<Video | null>;
  exportVideo(options: Source): Promise<string>;
  saveProject(json: string): Promise<boolean>;
  loadProject(): Promise<string | null>;
  clearProjects(): Promise<boolean>;
  cancelExport(): void;
};
export const VideoPreview = requireNativeComponent<ViewProps & {source: Source}>('FrameVideoPreview');
