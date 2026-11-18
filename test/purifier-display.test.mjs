import test from 'node:test';
import assert from 'node:assert/strict';
import { DISPLAY_LEVELS, displayLevels, findDisplay, levelIndex, levelWord, levelBrightness } from '../src/purifier-display-logic.js';

test('the default notches are dim and bright, and a card can list its own', () => {
  assert.deepEqual(displayLevels({}), DISPLAY_LEVELS);
  assert.deepEqual(displayLevels(null), DISPLAY_LEVELS);
  assert.deepEqual(displayLevels({ display_levels: [100, 25, 50, 50] }), [25, 50, 100]);
  assert.deepEqual(displayLevels({ display_levels: ['x', 0, 101, -5] }), DISPLAY_LEVELS);
  assert.deepEqual(displayLevels({ display_levels: [] }), DISPLAY_LEVELS);
});

test('the display light is the one next to the purifier, unless the card says otherwise', () => {
  const states = {
    'fan.hayleys_bedroom_air_purifier': {},
    'light.hayleys_bedroom_air_purifier_display_backlight': {},
    'light.hayleys_bedroom_air_purifier': {},
    'light.other_purifier_display_backlight': {},
  };
  assert.equal(findDisplay(states, 'fan.hayleys_bedroom_air_purifier'), 'light.hayleys_bedroom_air_purifier_display_backlight');
  assert.equal(findDisplay(states, 'fan.hayleys_bedroom_air_purifier', 'light.somewhere_else'), 'light.somewhere_else');
  assert.equal(findDisplay(states, 'fan.hayleys_bedroom_air_purifier', ''), null);
  assert.equal(findDisplay(states, 'fan.no_light_here'), null);
  assert.equal(findDisplay(null, 'fan.x'), null);
  assert.equal(findDisplay(states, ''), null);
});

test('the notch follows the light: off has none, 128 is dim and 255 is bright', () => {
  const l = [50, 100];
  assert.equal(levelIndex('off', null, l), null);
  assert.equal(levelIndex('unavailable', 255, l), null);
  assert.equal(levelIndex('on', 128, l), 0);
  assert.equal(levelIndex('on', 255, l), 1);
  assert.equal(levelIndex('on', 1, l), 0);
  assert.equal(levelIndex('on', 200, l), 1);
  // On with no brightness given: the brightest.
  assert.equal(levelIndex('on', undefined, l), 1);
  // Three notches, and a tie goes to the higher one.
  assert.equal(levelIndex('on', 64, [25, 50, 100]), 0);
  // 153 is exactly 60%, halfway between 40% and 80%.
  assert.equal(levelIndex('on', 153, [40, 80]), 1);
  assert.equal(levelIndex('on', 191, [50, 100]), 0);
});

test('notches are named for people, and ask the light for the right brightness', () => {
  assert.equal(levelWord(null, [50, 100]), 'Off');
  assert.equal(levelWord(0, [50, 100]), 'Dim');
  assert.equal(levelWord(1, [50, 100]), 'Bright');
  assert.equal(levelWord(1, [25, 50, 100]), 'Medium');
  assert.equal(levelWord(2, [20, 40, 60, 80]), '60%');
  assert.equal(levelWord(0, [100]), 'On');
  assert.equal(levelBrightness(0, [50, 100]), 128);
  assert.equal(levelBrightness(1, [50, 100]), 255);
  assert.equal(levelBrightness(9, [50, 100]), 255);
  assert.equal(levelBrightness(-1, [50, 100]), 128);
});
