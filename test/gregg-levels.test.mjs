import { test } from 'node:test';
import assert from 'node:assert/strict';
import { ggKind, ggLevelIcon } from '../src/gregg-levels-logic.js';

test('suction options have their own icons', () => {
  const o = ['Quiet', 'Standard', 'Turbo', 'Max', 'Boost_IQ'];
  assert.equal(ggLevelIcon('select.gregg_suction_level', 'Quiet', o), 'cd:suction-quiet');
  assert.equal(ggLevelIcon('select.gregg_suction_level', 'Turbo', o), 'cd:suction-turbo');
  assert.equal(ggLevelIcon('select.gregg_suction_level', 'Boost_IQ', o), 'cd:suction-boost');
  assert.equal(ggLevelIcon('select.gregg_suction_level', 'Whatever', o), 'cd:fan');
});

test('water and mop levels run low, medium, high by position', () => {
  const o = ['Low', 'Medium', 'High'];
  assert.equal(ggLevelIcon('select.gregg_water_level', 'Low', o), 'cd:water-low');
  assert.equal(ggLevelIcon('select.gregg_water_level', 'Medium', o), 'cd:water-medium');
  assert.equal(ggLevelIcon('select.gregg_mop_intensity', 'High', o), 'cd:water-high');
  assert.equal(ggLevelIcon('select.gregg_mop_intensity', 'Max', ['Quiet', 'Normal', 'Max']), 'cd:water-high');
});

test('anything else is the sliders', () => {
  assert.equal(ggKind('select.gregg_cleaning_intensity'), 'level');
  assert.equal(ggLevelIcon('select.gregg_cleaning_intensity', 'Normal', ['Quick', 'Normal']), 'cd:sliders');
});
