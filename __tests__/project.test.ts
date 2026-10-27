import {describe, it, expect} from '@jest/globals';
import {restoreProject, trimProblem, Project} from '../src/project';

const project: Project = {
  version: 1, templateId: 'journal', title: 'Привет', muted: false,
  video: {id: 'video', uri: 'file:///data/user/0/com.gutkin.frame/files/media/video.mp4', durationMs: 60000, fps: 30, width: 1920, height: 1080, bytes: 1024},
  startMs: 1000, endMs: 31000, updatedAt: 1700000000000,
};
describe('trim boundaries', () => {
  it('allows exactly 30 seconds and the last second of a source', () => {
    expect(trimProblem(1000, 31000, 60000)).toBeNull();
    expect(trimProblem(59000, 60000, 60000)).toBeNull();
  });
  it.each([[0, 30001, 60000], [-1, 1000, 60000], [59001, 60001, 60000], [0, 999, 60000], [NaN, 1000, 60000], [0, Infinity, 60000]])('rejects invalid trim %s..%s', (a, b, duration) => {
    expect(trimProblem(a, b, duration)).not.toBeNull();
  });
});
describe('saved projects', () => {
  it('restores a valid project without altering edit settings', () => expect(restoreProject(JSON.stringify(project))).toEqual(project));
  it('rejects damaged data and incompatible schemas', () => {
    expect(restoreProject('{')).toBeNull();
    expect(restoreProject(JSON.stringify({...project, version: 2}))).toBeNull();
    expect(restoreProject(JSON.stringify({...project, endMs: 70000}))).toBeNull();
    expect(restoreProject(JSON.stringify({...project, templateId: 'nonexistent'}))).toBeNull();
    expect(restoreProject(JSON.stringify({...project, video: {...project.video, uri: 'https://example.com/video.mp4'}}))).toBeNull();
  });
});
