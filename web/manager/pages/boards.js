// The idea boards, moved across from satellite/design and design/clive: each is a finished page of options (logos, icons, splash screens, layouts, names) that stays
// as the record of what was considered and chosen. They are listed here by theme and open inside the Manager, so the navigation stays at hand.
import { h, soft } from './dom.js';

export const BOARDS = [
  { id: 'brand-logos', theme: 'Brand and names', title: 'Church Drive brand logos', note: 'The logo options for the house and what was chosen.' },
  { id: 'admin-logo', theme: 'Brand and names', title: 'Clive Admin logo ideas', note: 'Options for the Clive Admin mark.' },
  { id: 'app-names', theme: 'Brand and names', title: 'Clive app names', note: 'The names considered for the apps.' },
  { id: 'type-combos', theme: 'Brand and names', title: 'Type combinations', note: 'Font pairings tried for the apps and the site.' },
  { id: 'app-icons', theme: 'Brand and names', title: 'Satellites app icons', note: 'Launcher icon options for Satellites.' },
  { id: 'icons-board', theme: 'Icons', title: 'Church Drive icons', note: 'The full set of house icons on one board.' },
  { id: 'clive-icons', theme: 'Icons', title: 'Clive icons', note: 'Clive\'s own glyphs and moods.' },
  { id: 'icon-morphs', theme: 'Icons', title: 'Clive icon morphs', note: 'How icons change shape from one state to the next.' },
  { id: 'icon-audit', theme: 'Icons', title: 'Satellites icon audit', note: 'Which icons are drawn, approved or still to do.' },
  { id: 'icon-ideas', theme: 'Icons', title: 'Satellites in-app icons', note: 'First round of icons inside Satellites.' },
  { id: 'icon-ideas2', theme: 'Icons', title: 'Satellites icon ideas, round two', note: 'Second round, after review.' },
  { id: 'icon-cog', theme: 'Icons', title: 'Settings cog ideas', note: 'Cog shapes tried, and the approved one.' },
  { id: 'icon-device', theme: 'Icons', title: 'Satellite device icons', note: 'Icons for the kinds of satellite device.' },
  { id: 'splash-final', theme: 'Screens', title: 'Splash screens', note: 'The splash screens as chosen.' },
  { id: 'splash-ideas', theme: 'Screens', title: 'Splash ideas', note: 'The options that led to them.' },
  { id: 'idle-layouts', theme: 'Screens', title: 'Idle layouts', note: 'What a satellite shows when nothing is happening.' },
  { id: 'clive-round-ui', theme: 'Screens', title: 'Round screen', note: 'Clive on a round display.' },
  { id: 'link-pill', theme: 'Screens', title: 'Status pill', note: 'Connecting, updating, no connection and restarting, the pill every app shows.' },
  { id: 'flows', theme: 'Satellites', title: 'Satellite flows', note: 'Set-up and pairing flows, step by step.' },
  { id: 'device-codes', theme: 'Satellites', title: 'Device codes', note: 'How a satellite shows the code that pairs it.' },
  { id: 'scanner-ideas', theme: 'Satellites', title: 'QR scanner ideas', note: 'First options for scanning a code.' },
  { id: 'scanner-b', theme: 'Satellites', title: 'Scanner B', note: 'The scanner option taken forward.' },
];
const THEMES = ['Brand and names', 'Icons', 'Screens', 'Satellites'];

export async function renderBoards(el, ctx, arg) {
  const board = BOARDS.find((b) => b.id === arg);
  if (board) {
    document.getElementById('title').textContent = board.title;
    const frame = h('iframe', { class: 'board-frame', src: `boards/${board.id}.html`, title: board.title, loading: 'lazy' });
    el.replaceChildren(h('div', { class: 'stack' }, h('div', { class: 'row', style: 'gap:12px' }, h('a', { class: 'btn quiet', href: '#boards' }, '← All boards'), soft(board.note),
      h('a', { class: 'btn quiet', href: `boards/${board.id}.html`, target: '_blank', rel: 'noopener', style: 'margin-left:auto' }, 'Open on its own')), frame));
    return;
  }
  el.replaceChildren(h('div', { class: 'stack' }, h('p', { class: 'note' }, 'The idea boards, kept as the record of what was tried and what was chosen. Open one to look through it here.'),
    THEMES.map((t) => h('section', { class: 'stack', style: 'gap:12px' }, h('div', { class: 'head' }, h('h2', {}, t)),
      h('div', { class: 'grid' }, BOARDS.filter((b) => b.theme === t).map((b) => h('a', { class: 'card board-card', href: `#boards/${b.id}`, style: `--ground:var(--card)` }, h('p', { class: 'title' }, b.title), soft(b.note))))))));
}
