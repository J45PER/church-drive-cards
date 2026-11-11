// The Reference page: what the house's look is made of, kept as a place to look things up rather than a place to try ideas. The logos in use, the colours
// and glow, the type, the shapes, the apps and their names, and the few full boards worth keeping (the status pill, icon morphs, satellite flows, device
// codes and the splash screens). It comes from docs/CLIVE-STYLE.md and the shipped assets; the many idea boards it grew from are not kept on the site.
import { h, soft } from './dom.js';
import { icon, iconLibrary, loadIcons } from '../icons.js';
import { LOGO_PATH, LOGO_VIEWBOX } from '../logo.js';
import { fact, panel, toneCard } from '../kit.js';

export const BOARDS = [
  { id: 'link-pill', title: 'Status pill', note: 'Connecting, updating, no connection and restarting: the pill every app shows, and how it moves.' },
  { id: 'icon-morphs', title: 'Icon morphs', note: 'How Clive\'s icons change shape from one state to the next.' },
  { id: 'splash-final', title: 'Splash screens', note: 'The splash screens as chosen.' },
  { id: 'flows', title: 'Satellite flows', note: 'Set-up and pairing, step by step.' },
  { id: 'device-codes', title: 'Device codes', note: 'How a satellite shows the code that pairs it.' },
];
export const APPS = [
  ['Church Drive', 'com.churchdrive.app', 'The house on your phone, and on the watch', 'cd_logo_church_drive'],
  ['Clive Admin', 'com.churchdrive.admin', 'The administrator\'s view: health, energy, devices, activity, settings', null],
  ['Church Drive To-Do', 'com.churchdrive.todo', 'Everyone\'s lists and the house jobs', 'cd_logo_todo'],
  ['Church Drive Satellites', 'com.churchdrive.satellites', 'Set up and look after the voice satellites', 'cd_logo_satellites'],
];
export const TOKENS = [['Ground', '#090A14', 'the dark glass behind everything'], ['Text', '#FFFFFF', 'on dark cards'], ['Soft text', '#E8EAFF', 'card lines'], ['Error text', '#FFB4AB', 'something wrong'], ['On', '#4FD08A', 'a switch that is on'], ['Indigo', '#5C6BC0', 'glow, base palette'], ['Purple', '#AB47BC', 'glow, base palette'], ['Green', '#4FD08A', 'glow, base palette']];
const svg = (code, size) => { const t = document.createElement('template'); t.innerHTML = icon(code, size); return t.content.firstChild; };

function section(ctx, title, tone, ...kids) { return panel(ctx, { title, icon: 'cd:image-text', color: tone }, ...kids); }

export async function renderReference(el, ctx, arg) {
  const board = BOARDS.find((b) => b.id === arg);
  if (board) {
    document.getElementById('title').textContent = board.title;
    el.replaceChildren(h('div', { class: 'stack' }, h('div', { class: 'row', style: 'gap:12px' }, h('a', { class: 'btn quiet', href: '#reference' }, '← Reference'), soft(board.note),
      h('a', { class: 'btn quiet', href: `boards/${board.id}.html`, target: '_blank', rel: 'noopener', style: 'margin-left:auto' }, 'Open on its own')),
      h('iframe', { class: 'board-frame', src: `boards/${board.id}.html`, title: board.title, loading: 'lazy' })));
    return;
  }
  await loadIcons(ctx.house);
  const lib = iconLibrary().icons;
  const moods = Object.keys(lib).filter((k) => k.startsWith('AssistantGlyph')).sort();
  const logos = ['cd_logo_church_drive', 'cd_logo_satellites', 'cd_logo_todo'].filter((k) => lib[k]);
  const markEl = (() => { const t = document.createElement('template'); t.innerHTML = `<svg viewBox="${LOGO_VIEWBOX}" width="72" height="72" aria-hidden="true"><path fill="currentColor" fill-rule="evenodd" d="${LOGO_PATH}"/></svg>`; return t.content.firstChild; })();
  const tile = (art, name, sub) => h('div', { class: 'ref-tile' }, h('span', { class: 'ref-art' }, art), h('p', { class: 'row-title' }, name), sub ? soft(sub) : null);
  el.replaceChildren(h('div', { class: 'stack' }, h('p', { class: 'note' }, 'What the house looks like, kept to look things up. The full specification is docs/CLIVE-STYLE.md in the repository.'),
    h('div', { class: 'panel-cols' },
      section(ctx, 'Logos in use', 'tasks', h('div', { class: 'ref-grid' }, tile(markEl, 'Clive Admin', 'The shield with Clive cut out'),
        ...logos.map((k) => tile(svg(k, 72), lib[k].n || k, k)), ...moods.slice(0, 6).map((k) => tile(svg(k, 56), lib[k].n || k, 'Clive\'s mark, one state')))),
      section(ctx, 'Colour', 'media', toneCard(ctx, 'media', h('div', { class: 'ref-swatches' }, TOKENS.map(([n, c, w]) => h('div', { class: 'ref-sw' }, h('i', { style: `background:${c}` }), h('b', {}, n), h('code', {}, c), soft(w)))),
        soft('Cards sit on the palette\'s Dark colour with a glow of its Colour and Accent behind. Every palette is on the Colours page.'))),
      section(ctx, 'Type', 'people', toneCard(ctx, 'people', h('p', { class: 'ref-type' }, 'Instrument Sans'), soft('Every word in the phone, To-Do, Satellites and Admin apps and on this site. Regular, medium, semibold and bold.'),
        h('p', { class: 'ref-type mono' }, 'JetBrains Mono'), soft('Codes, versions and entity names. The watch uses the system font.'))),
      section(ctx, 'Shape and motion', 'energy', toneCard(ctx, 'energy', fact('Card corners', '28 px', true), fact('Smaller cards', '20 px'), fact('Buttons and chips', 'fully round'), fact('Glow', 'two blobs, 160 s a turn'),
        soft('A card is its palette\'s Dark colour; text on it is white. A panel is an icon, a title in the palette\'s colour and a summary pill, then its cards.'))),
      section(ctx, 'The apps', 'ok', h('div', { class: 'stack', style: 'gap:10px' }, APPS.map(([n, id, what, logo]) => toneCard(ctx, 'ok', h('div', { class: 'row' }, logo && lib[logo] ? h('span', { class: 'ref-art sm' }, svg(logo, 36)) : null,
        h('div', { style: 'flex:1' }, h('p', { class: 'title', style: 'margin:0' }, n), soft(what)), h('code', {}, id)))))),
      section(ctx, 'Full boards kept', 'gregg', h('div', { class: 'stack', style: 'gap:10px' }, BOARDS.map((b) => h('a', { class: 'card home-card', href: `#reference/${b.id}`, style: 'text-decoration:none' }, h('p', { class: 'title' }, b.title), soft(b.note))))))));
}
