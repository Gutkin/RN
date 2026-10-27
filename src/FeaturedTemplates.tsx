import React, {useRef, useState} from 'react';
import {Image, Pressable, ScrollView, StyleSheet, Text, useWindowDimensions, View} from 'react-native';
import {Template, templates} from './templates';

export type Collection = 'Stories' | 'Reels' | 'Posts';
const orders: Record<Collection, string[]> = {
  Stories: ['journal', 'minimal', 'postcard', 'film', 'bold'],
  Reels: ['bold', 'film', 'journal', 'minimal', 'postcard'],
  Posts: ['postcard', 'journal', 'minimal', 'bold', 'film'],
};
export const collectionTemplates = (collection: Collection) => orders[collection].map(id => templates.find(t => t.id === id)!);

export function FeaturedTemplates({collection, onCollectionChange, onOpen}: {
  collection: Collection; onCollectionChange: (value: Collection) => void; onOpen: (template: Template) => void;
}) {
  const viewport = useWindowDimensions();
  const [width, setWidth] = useState(Math.max(1, viewport.width - 48));
  const [index, setIndex] = useState(0);
  const pager = useRef<ScrollView>(null);
  const items = collectionTemplates(collection);
  const arrowSize = Math.max(34, width * 0.105);
  const chooseCollection = (value: Collection) => {
    onCollectionChange(value); setIndex(0); pager.current?.scrollTo({x: 0, animated: false});
  };
  const choosePage = (value: number) => {
    setIndex(value); pager.current?.scrollTo({x: width * value, animated: true});
  };
  return <View testID="featured-templates" onLayout={e => {
    const nextWidth = e.nativeEvent.layout.width;
    if (nextWidth > 0 && nextWidth !== width) {
      setWidth(nextWidth);
      requestAnimationFrame(() => pager.current?.scrollTo({x: nextWidth * index, animated: false}));
    }
  }}>
    <View accessibilityRole="tablist" style={s.tabs}>
      {(['Stories', 'Reels', 'Posts'] as const).map((value, i) => <React.Fragment key={value}>
        {i > 0 && <View accessible={false} style={s.divider} />}
        <Pressable accessibilityRole="tab" accessibilityLabel={value} accessibilityState={{selected: collection === value}} testID={`collection-${value}`} onPress={() => chooseCollection(value)} style={[s.tab, collection === value && s.activeTab]}>
          <Text style={[s.tabText, collection === value && s.activeTabText]}>{value}</Text>
        </Pressable>
      </React.Fragment>)}
    </View>
    <View style={s.pagerClip}>
      <ScrollView key={collection} ref={pager} horizontal pagingEnabled bounces={false} showsHorizontalScrollIndicator={false} decelerationRate="fast" scrollEventThrottle={16} testID="featured-carousel"
        onMomentumScrollEnd={e => setIndex(Math.max(0, Math.min(items.length - 1, Math.round(e.nativeEvent.contentOffset.x / width))))}>
        {items.map((template, i) => <View key={`${collection}-${template.id}`} testID={`featured-card-${i}`} importantForAccessibility={i === index ? 'auto' : 'no-hide-descendants'} style={[s.card, {width, height: width * 199 / 330}]}>
          <Image accessible={false} source={require('../assets/featured-collage.png')} resizeMode="cover" fadeDuration={0} style={StyleSheet.absoluteFill} />
          <Text maxFontSizeMultiplier={1.2} style={[s.caption, {fontSize: width * 0.019, lineHeight: width * 0.030}]}>{collection === 'Reels' ? 'MAKE EVERY\nMOMENT COUNT' : collection === 'Posts' ? 'LITTLE NOTES\nFROM YOUR LIFE' : 'A QUIETER\nKIND OF DAY'}</Text>
          <Text testID={`featured-title-${i}`} maxFontSizeMultiplier={1.2} style={[s.title, {fontSize: width * 0.075, lineHeight: width * 0.080}]}>{template.title}</Text>
          <Pressable accessibilityRole="button" accessibilityLabel={`Открыть ${template.name}`} testID={`featured-open-${i}`} hitSlop={10} onPress={() => onOpen(template)} style={[s.open, {width: arrowSize, height: arrowSize}]}>
            <View accessible={false} style={s.arrow}><View style={s.arrowShaft} /><View style={s.arrowHead} /></View>
          </Pressable>
        </View>)}
      </ScrollView>
    </View>
    <View style={s.pagination}>{items.map((item, i) => <Pressable key={item.id} accessibilityRole="button" accessibilityLabel={`Слайд ${i + 1} из ${items.length}`} accessibilityState={{selected: i === index}} testID={`featured-dot-${i}`} onPress={() => choosePage(i)} hitSlop={{top: 6, bottom: 6}} style={s.dotTarget}>
      <View style={[s.dot, i === index && s.activeDot]} />
    </Pressable>)}</View>
  </View>;
}

const s = StyleSheet.create({
  tabs: {backgroundColor: '#E8E4DE', borderRadius: 13, flexDirection: 'row', alignItems: 'center', padding: 4, marginTop: 24, marginBottom: 16, height: 42},
  tab: {flex: 1, alignItems: 'center', justifyContent: 'center', height: 34, borderRadius: 10},
  activeTab: {backgroundColor: '#FFFDF9', elevation: 2, shadowColor: '#514A41', shadowOffset: {width: 0, height: 1}, shadowOpacity: 0.13, shadowRadius: 2},
  tabText: {color: '#373832', fontSize: 12}, activeTabText: {fontWeight: '600'}, divider: {height: 12, width: StyleSheet.hairlineWidth, backgroundColor: '#CDC8BF'},
  pagerClip: {borderRadius: 14, overflow: 'hidden'}, card: {backgroundColor: '#E9E5DC', overflow: 'hidden'},
  caption: {position: 'absolute', top: '12%', left: '6.5%', letterSpacing: 1.8, color: '#55594F'},
  title: {position: 'absolute', left: '6.5%', top: '31%', width: '54%', fontFamily: 'serif', color: '#23251F'},
  open: {position: 'absolute', right: '4%', bottom: '10%', borderRadius: 30, backgroundColor: '#FFFDF9', alignItems: 'center', justifyContent: 'center', elevation: 2, shadowColor: '#433C31', shadowOpacity: 0.12, shadowRadius: 2, shadowOffset: {width: 0, height: 1}},
  arrow: {width: 15, height: 14, alignItems: 'center', justifyContent: 'center'}, arrowShaft: {width: 13, height: 1.5, backgroundColor: '#282B24'}, arrowHead: {position: 'absolute', right: 1, width: 7, height: 7, borderTopWidth: 1.5, borderRightWidth: 1.5, borderColor: '#282B24', transform: [{rotate: '45deg'}]},
  pagination: {height: 32, flexDirection: 'row', alignItems: 'center', justifyContent: 'center'}, dotTarget: {width: 18, height: 28, alignItems: 'center', justifyContent: 'center'}, dot: {width: 6, height: 6, borderRadius: 3, backgroundColor: '#D6D2CB'}, activeDot: {backgroundColor: '#FF624F'},
});
