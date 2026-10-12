// Run with: npm test
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { splitSteps, joinSteps, stepsCount, flipStep, resetSteps } from '../src/task-steps.js';
import { parseTask, formatTask, parseRepeat } from '../src/repeat.js';

test('notes with steps come apart and go back together', () => {
  const notes = 'Do it at home\n- [x] Power-cycle the Hub\n- [ ] Power-cycle the eero';
  const s = splitSteps(notes);
  assert.equal(s.text, 'Do it at home');
  assert.deepEqual(s.steps, [{ text: 'Power-cycle the Hub', done: true }, { text: 'Power-cycle the eero', done: false }]);
  assert.equal(joinSteps(s.text, s.steps), notes);
  assert.equal(stepsCount(s.steps), '1 of 2');
});

test('plain notes have no steps, and blank steps are dropped', () => {
  assert.deepEqual(splitSteps('just words'), { text: 'just words', steps: [] });
  assert.equal(stepsCount([]), '');
  assert.equal(joinSteps('x', [{ text: '  ', done: false }, { text: 'a · b\nc', done: false }]), 'x\n- [ ] a - b c');
});

test('steps survive a description with a repeat and who', () => {
  const steps = [{ text: 'one', done: false }, { text: 'two', done: true }];
  const d = formatTask(parseRepeat('Once'), ['Jamie'], joinSteps('', steps));
  assert.equal(d, 'Once · for Jamie · - [ ] one\n- [x] two');
  assert.deepEqual(splitSteps(parseTask(d).notes).steps, steps);
});

test('flipStep ticks one step and touches nothing else', () => {
  const d = 'Once · for Jamie · - [ ] one\n- [x] two\n- [ ] three';
  assert.equal(flipStep(d, 0), 'Once · for Jamie · - [x] one\n- [x] two\n- [ ] three');
  assert.equal(flipStep(d, 1), 'Once · for Jamie · - [ ] one\n- [ ] two\n- [ ] three');
  assert.equal(flipStep(d, 3), null);
  assert.equal(flipStep('notes\n- [ ] a', 0), 'notes\n- [x] a');
  assert.equal(flipStep('- [ ] a', 0), '- [x] a');
  assert.equal(flipStep('no steps', 0), null);
});

test('resetSteps unticks them all', () => {
  assert.equal(resetSteps('Once · for Jamie · - [x] one\n- [x] two'), 'Once · for Jamie · - [ ] one\n- [ ] two');
});
