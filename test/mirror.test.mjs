// Run with: npm test
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { identity, mirrorable, findView, resolve, options } from '../src/mirror.js';

const light = (area, extra = {}) => ({ type: 'custom:light-control-card', mode: 'room', area, entity: 'light.spot', ...extra });
const config = {
  views: [
    {
      path: 'home',
      sections: [{ cards: [{ type: 'custom:auto-layout-card', cards: [
        { type: 'custom:section-panel-card', title: 'Lights', cards: [{ type: 'custom:mirror-card', view: 'lighting', source: 'area:kitchen' }] },
        { type: 'custom:nav-bar-card', pages: [] },
      ] }] }],
    },
    {
      path: 'lighting',
      sections: [{ cards: [{ type: 'custom:auto-layout-card', cards: [
        { type: 'custom:section-panel-card', title: 'Ground Floor', cards: [light('kitchen'), light('living_room', { name: 'Lounge' })] },
        { type: 'custom:section-panel-card', title: 'Garden', cards: [{ type: 'vertical-stack', cards: [light('garden'), { type: 'tile', entity: 'vacuum.gregg' }] }] },
      ] }] }],
    },
    { path: 'extra', cards: [{ type: 'tile', entity: 'sensor.a' }, { type: 'tile', entity: 'sensor.a' }, { type: 'markdown' }] },
  ],
};

test('a card is known by its area, then entity, then name, then type', () => {
  assert.equal(identity(light('kitchen')), 'area:kitchen');
  assert.equal(identity({ type: 'tile', entity: 'vacuum.gregg' }), 'entity:vacuum.gregg');
  assert.equal(identity({ type: 'x', name: 'Bob' }), 'name:Bob');
  assert.equal(identity({ type: 'markdown' }), 'type:markdown');
});

test('lists the cards on a page, not the layout cards or other mirrors', () => {
  const lighting = mirrorable(findView(config, 'lighting')).map((m) => [m.key, m.panel]);
  assert.deepEqual(lighting, [
    ['area:kitchen', 'Ground Floor'], ['area:living_room', 'Ground Floor'],
    ['area:garden', 'Garden'], ['entity:vacuum.gregg', 'Garden'],
  ]);
  // The Home page has only a mirror and layout cards.
  assert.deepEqual(mirrorable(findView(config, 'home')), []);
});

test('two alike cards on a page are told apart by order', () => {
  assert.deepEqual(mirrorable(findView(config, 'extra')).map((m) => m.key), ['entity:sensor.a', 'entity:sensor.a#2', 'type:markdown']);
});

test('finds a card again from its key, and a view by path or position', () => {
  assert.equal(resolve(config, { view: 'lighting', source: 'area:living_room' }).name, 'Lounge');
  assert.equal(resolve(config, { view: '1', source: 'area:garden' }).area, 'garden');
  assert.equal(resolve(config, { view: 'lighting', source: 'area:gone' }), null);
  assert.equal(resolve(config, { view: 'nope', source: 'area:kitchen' }), null);
});

test('the key survives cards being moved or added', () => {
  const moved = structuredClone(config);
  const stack = moved.views[1].sections[0].cards[0].cards;
  stack.unshift({ type: 'custom:section-panel-card', title: 'New', cards: [light('study')] });
  assert.equal(resolve(moved, { view: 'lighting', source: 'area:kitchen' }).area, 'kitchen');
});

test('editor labels read well', () => {
  const names = { areaName: (a) => ({ kitchen: 'Kitchen' }[a]), entityName: (e) => ({ 'vacuum.gregg': 'Gregg' }[e]) };
  assert.deepEqual(options(findView(config, 'lighting'), names).map((o) => o.label), [
    'Ground Floor › Kitchen (Light control)',
    'Ground Floor › Lounge (Light control)',
    'Garden › Garden (Light control)',
    'Garden › Gregg (Tile)',
  ]);
});
