import React, {useState} from 'react';
import TestRenderer, {act} from 'react-test-renderer';
import {StyleSheet} from 'react-native';
import {describe, it, expect, jest, beforeEach, afterEach} from '@jest/globals';
beforeEach(() => { jest.useFakeTimers(); });
afterEach(() => { act(() => jest.runOnlyPendingTimers()); jest.useRealTimers(); });
import {Collection, FeaturedTemplates} from '../src/FeaturedTemplates';

function mount() {
  const open = jest.fn();
  function Host() {
    const [collection, setCollection] = useState<Collection>('Stories');
    return <FeaturedTemplates collection={collection} onCollectionChange={setCollection} onOpen={open} />;
  }
  let renderer!: TestRenderer.ReactTestRenderer;
  act(() => { renderer = TestRenderer.create(<Host />); });
  const node = (testID: string) => renderer.root.findAll(n => n.props.testID === testID)[0];
  return {open, node, renderer};
}

describe('featured template navigation', () => {
  it('includes all three tabs, five indicators and a working arrow', () => {
    const {open, node, renderer} = mount();
    for (const tab of ['Stories', 'Reels', 'Posts']) expect(node(`collection-${tab}`)).toBeDefined();
    for (let i = 0; i < 5; i++) expect(node(`featured-dot-${i}`)).toBeDefined();
    expect(node('collection-Stories').props.accessibilityState.selected).toBe(true);
    act(() => node('featured-open-0').props.onPress());
    expect(open).toHaveBeenLastCalledWith(expect.objectContaining({id: 'journal'}));
    act(() => renderer.unmount());
  });
  it('switches collections, opens the selected page and resets pagination on a tab change', () => {
    const {open, node, renderer} = mount();
    act(() => node('collection-Reels').props.onPress());
    expect(node('collection-Reels').props.accessibilityState.selected).toBe(true);
    act(() => node('featured-open-0').props.onPress());
    expect(open).toHaveBeenLastCalledWith(expect.objectContaining({id: 'bold'}));
    act(() => node('featured-dot-2').props.onPress());
    expect(node('featured-dot-2').props.accessibilityState.selected).toBe(true);
    act(() => node('featured-open-2').props.onPress());
    expect(open).toHaveBeenLastCalledWith(expect.objectContaining({id: 'journal'}));
    act(() => node('collection-Posts').props.onPress());
    expect(node('featured-dot-0').props.accessibilityState.selected).toBe(true);
    act(() => node('featured-open-0').props.onPress());
    expect(open).toHaveBeenLastCalledWith(expect.objectContaining({id: 'postcard'}));
    act(() => renderer.unmount());
  });
  it('preserves the full banner aspect ratio on a narrow screen and tracks a swipe', () => {
    const {node, renderer} = mount();
    act(() => node('featured-templates').props.onLayout({nativeEvent: {layout: {width: 280}}}));
    const card = StyleSheet.flatten(node('featured-card-0').props.style);
    expect(card.width).toBe(280);
    expect(card.height).toBeCloseTo(280 * 199 / 330);
    act(() => node('featured-carousel').props.onMomentumScrollEnd({nativeEvent: {contentOffset: {x: 280 * 4}}}));
    expect(node('featured-dot-4').props.accessibilityState.selected).toBe(true);
    act(() => renderer.unmount());
  });
});
