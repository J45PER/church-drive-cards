import test from 'node:test';
import assert from 'node:assert/strict';
import { noticeIconSvg, parseSpec, specText } from '../web/manager/pages/notice-icon.js';

const lib = { icons: { 'cd:car': { p: '<path d="M0,0Z"/>' }, 'cd:ev-plug': { p: '<path d="M1,1Z"/>' }, 'cd:globe': { p: '<path d="M2,2Z"/>' }, cd_logo_todo_clock: { p: "<path fill-rule='evenodd' d='M1,1ZM2,2ZM3,3ZM4,4ZM5,5ZM6,6ZM7,7ZM8,8ZM9,9Z'/>" } }, octopus: 'M1 1Z' };

test('a spec is a base, a badge and an off slash', () => {
  assert.deepEqual(parseSpec('car+ev-plug'), { base: 'car', badge: 'ev-plug', off: false });
  assert.deepEqual(parseSpec(' Globe/off '), { base: 'globe', badge: null, off: true });
  for (const bad of ['', 'a+b+c', 'a+', null]) assert.equal(parseSpec(bad), null);
  assert.equal(specText(parseSpec('octopus+power')), 'octopus+power');
  assert.equal(specText({ base: 'globe', badge: null, off: true }), 'globe/off');
});

test('the icons are drawn: a plain one, one with the off slash, one with a badge', () => {
  assert.match(noticeIconSvg('car', lib), /<path d="M0,0Z"\/>/);
  assert.match(noticeIconSvg('globe/off', lib), /<line x1="40"/);
  const badged = noticeIconSvg('car+ev-plug', lib);
  assert.match(badged, /<rect x="108"/); // the EV plug sits in a rounded square
  assert.match(noticeIconSvg('octopus+power', { ...lib, icons: { ...lib.icons, 'cd:power': { p: '<path d="P"/>' } } }), /<circle cx="148" cy="148" r="40"\/>/);
});

test('the To-Do logo base is the first seven pieces of the clock logo', () => {
  const svg = noticeIconSvg('todo-logo+pin', lib);
  assert.match(svg, /M1,1ZM2,2ZM3,3ZM4,4ZM5,5ZM6,6ZM7,7Z"/);
  assert.doesNotMatch(svg, /M8,8Z/);
});

test('Gregg\'s status icons come from the set', () => { assert.match(noticeIconSvg('gregg-error', { icons: { 'GreggIcon Error': { p: '<path d="E"/>' } } }), /d="E"/); });

test('something unknown draws an empty box', () => { assert.doesNotMatch(noticeIconSvg('', lib), /<path|<g/); });
