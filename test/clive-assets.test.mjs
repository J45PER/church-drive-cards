import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const dir = 'custom_components/church_drive/frontend/clive';
const icons = JSON.parse(readFileSync(`${dir}/icons.json`, 'utf8'));
const cards = JSON.parse(readFileSync(`${dir}/cards.json`, 'utf8'));
const pkg = JSON.parse(readFileSync('package.json', 'utf8'));

test('the served files carry the app version', () => {
  assert.equal(icons.app, pkg.version);
  assert.equal(cards.app, pkg.version);
});

test('every served icon has path data and no placeholder boxes', () => {
  assert.ok(icons.count > 250);
  for (const [code, v] of Object.entries(icons.icons)) {
    assert.ok(v.p && v.p.includes(' d='), `${code} has path data`);
    assert.ok(!v.p.includes('stroke-dasharray'), `${code} is not a placeholder`);
  }
});

test('the icons the cards and animations name exist in the house set', () => {
  const names = new Set();
  for (const k of Object.values(cards.kinds)) if (typeof k.icon === 'string' && k.icon.startsWith('cd')) names.add(k.icon);
  for (const a of Object.values(cards.icons)) for (const n of [a.from, a.to, a.icon, a.start, a.end, ...(a.icons || [])]) if (n) names.add(n);
  for (const s of Object.values(cards.gregg.states)) names.add(s.icon);
  for (const n of names) assert.ok(icons.icons[n], `${n} is in icons.json`);
});

test('palettes are three hex colours and the morph recipes are valid', () => {
  for (const [name, p] of Object.entries(cards.palettes)) { assert.equal(p.length, 3, name); for (const c of p) assert.match(c, /^#[0-9A-Fa-f]{6}$/); }
  assert.ok(Object.keys(cards.morph.recipes).length > 30, 'every icon family has a morph recipe');
  for (const [k, r] of Object.entries(cards.morph.recipes)) assert.ok(!r.d || ['pop', 'drop', 'rise', 'left', 'right', 'spin'].includes(r.d), `${k} has a known arrival`);
});

test('every entity type has five hex colours and every kind palette is three', () => {
  const ec = cards.entity_colours;
  assert.ok(Object.keys(ec.types).length >= 30);
  for (const [k, v] of Object.entries(ec.types)) { assert.equal(v.colours.length, 5, k); for (const c of v.colours) assert.match(c, /^#[0-9A-Fa-f]{6}$/, k); }
  for (const k of ec.alarm_states) assert.ok(ec.types[k], k);
  assert.equal(ec.types.energy.colours[0].toLowerCase(), '#f050f8', 'electricity is Octopus pink');
  assert.equal(ec.types.gas.colours[0].toLowerCase(), '#7b61ff', 'gas is Octopus violet');
  for (const [kind, p] of Object.entries(cards.kind_palettes.kinds)) { assert.equal(p.length, 3, kind); for (const c of p) assert.match(c, /^#[0-9A-Fa-f]{6}$/, kind); }
});

test('the generated colour files are up to date with cards.json', async () => {
  const { execFileSync } = await import('node:child_process');
  execFileSync('node', ['tools/gen-clive-colours.mjs', '--check'], { stdio: 'pipe' });
});

test('the dashboard theme sets the named colours and the status colours from the palette', async () => {
  const { cliveThemeCss } = await import('../src/clive-theme.js');
  const css = cliveThemeCss();
  assert.ok(css.includes(`--green-color: ${cards.entity_colours.types.ok.colours[0].toLowerCase()};`));
  assert.ok(css.includes(`--error-color: ${cards.entity_colours.types.bad.colours[0].toLowerCase()};`));
  assert.match(css, /--rgb-primary-color: \d+, \d+, \d+;/);
});

test('a card colour can be a palette name: each name has its own variable', async () => {
  const { cliveThemeCss } = await import('../src/clive-theme.js');
  const css = cliveThemeCss();
  assert.ok(css.includes(`--lights-color: ${cards.entity_colours.types.lights.colours[0].toLowerCase()};`));
  assert.ok(css.includes(`--gregg-color: ${cards.entity_colours.types.gregg.colours[0].toLowerCase()};`));
  assert.ok(css.includes(`--car-color: ${cards.entity_colours.types.car.colours[0].toLowerCase()};`));
});

test('the list of colour names is one list: aliases point at real names and the apps know every name', async () => {
  const { readFileSync } = await import('node:fs');
  const ec = cards.entity_colours;
  for (const [alias, key] of Object.entries(ec.aliases.names)) assert.ok(ec.types[key], `${alias} -> ${key} is a palette name`);
  const kt = readFileSync('companion/android/core/src/main/java/com/churchdrive/app/ui/StateColors.kt', 'utf8');
  const body = kt.slice(kt.indexOf('fun toneForKey'), kt.indexOf('fun toneFromColour'));
  for (const key of Object.keys(ec.types)) assert.ok(body.includes(`"${key}" ->`), `toneForKey knows ${key}`);
  for (const k of ['ok', 'warn', 'bad']) assert.ok(body.includes(`"${k}" ->`), `toneForKey knows ${k}`);
  const md = readFileSync('docs/CLIVE-COLOURS.md', 'utf8');
  for (const key of Object.keys(ec.types)) assert.ok(md.includes(`\`${key}\``), `the names document lists ${key}`);
});

test('the dashboard theme defines a variable for every palette name and every Home Assistant alias', async () => {
  const { cliveThemeCss } = await import('../src/clive-theme.js');
  const css = cliveThemeCss();
  for (const key of Object.keys(cards.entity_colours.types)) assert.ok(css.includes(`--${key}-color:`), `--${key}-color`);
  for (const alias of Object.keys(cards.entity_colours.aliases.names)) assert.ok(css.includes(`--${alias}-color:`), `--${alias}-color`);
});

test('live palettes from Home Assistant replace the shipped ones and add their variables', async () => {
  const { applyLive, cliveThemeCss } = await import('../src/clive-theme.js');
  const { CLIVE } = await import('../src/clive-colours.js');
  const before = CLIVE.lights.slice();
  try {
    applyLive({ lights: { colours: ['#112233', '#223344', '#334455', '#445566', '#556677'] }, mine: { colours: ['#AABBCC', '#223344', '#334455', '#445566', '#556677'] }, broken: { colours: ['nope'] }, 'Bad Key': { colours: before } });
    const css = cliveThemeCss();
    assert.match(css, /--lights-color: #112233;/);
    assert.match(css, /--mine-color: #aabbcc;/);
    assert.ok(!css.includes('--broken-color'));
    assert.ok(!css.includes('--Bad Key-color'));
  } finally {
    CLIVE.lights = before;
    delete CLIVE.mine;
  }
});

test('cards moved onto the palette have no colour of their own: every colour is a palette name', async () => {
  const { readFileSync } = await import('node:fs');
  for (const f of ['climate-card.js', 'alarm-panel-card.js']) {
    const code = readFileSync(`src/${f}`, 'utf8').split('\n').filter((l) => !l.trim().startsWith('//')).join('\n');
    assert.deepEqual(code.match(/#[0-9a-fA-F]{6}\b/g) || [], [], `${f} has a hard-coded colour`);
  }
});
