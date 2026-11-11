// The icon library: every icon the house draws (the `cd:` set the dashboards use, Clive's glyphs, Gregg and the satellites), searchable, with the code each one
// goes by and the ones still to draw. It moved here from the icon boards (design/clive and satellite/design) so the whole set can be seen in one place.
import { chips, h, soft } from './dom.js';
import { icon, iconLibrary, loadIcons } from '../icons.js';

const family = (code) => code.split(/[ :_]/)[0] || code;
const svgOf = (code, size) => { const tpl = document.createElement('template'); tpl.innerHTML = icon(code, size); return tpl.content.firstChild; };

export async function renderIconLibrary(el, ctx) {
  el.replaceChildren(h('p', { class: 'note' }, 'Loading the icon set…'));
  await loadIcons(ctx.house);
  const { icons, namespaces = {}, pending = [], count } = iconLibrary();
  const codes = Object.keys(icons);
  if (!codes.length) { el.replaceChildren(h('p', { class: 'err', role: 'alert' }, 'The icon set could not be loaded.')); return; }
  const state = { q: '', fam: 'all', size: 40, pick: null, tab: 'drawn' };
  const fams = ['all', ...[...new Set(codes.map(family))].sort()];
  const grid = h('div', { class: 'icon-grid' }), detail = h('article', { class: 'card plain icon-detail', hidden: true }), tabs = h('div'), famRow = h('div'), count$ = soft('');
  const search = h('input', { class: 'field', type: 'search', placeholder: 'Search icons by code or name', 'aria-label': 'Search icons', oninput: (e) => { state.q = e.target.value.trim().toLowerCase(); draw(); } });
  const size = h('input', { type: 'range', min: 24, max: 96, step: 8, value: state.size, 'aria-label': 'Icon size', oninput: (e) => { state.size = Number(e.target.value); draw(); } });
  const copy = async (code) => { try { await navigator.clipboard.writeText(code); } catch { /* no clipboard here */ } };
  const show = (code) => {
    state.pick = code; const i = icons[code] || {};
    detail.hidden = false;
    detail.replaceChildren(h('div', { class: 'row' }, h('span', { class: 'icon-big' }, svgOf(code, 96)),
      h('div', { style: 'flex:1' }, h('p', { class: 'title', style: 'margin:0' }, i.n || code), h('p', { class: 'mono' }, code), soft([i.s, namespaces[family(code)]].filter(Boolean).join(' · ')),
        h('div', { class: 'row', style: 'gap:8px;margin-top:8px' }, h('button', { class: 'btn quiet', type: 'button', onclick: () => copy(code) }, 'Copy code'),
          h('a', { class: 'btn quiet', href: '#design/icons' }, 'Use it on a panel'), h('button', { class: 'btn quiet', type: 'button', onclick: () => { detail.hidden = true; } }, 'Close')))));
  };
  const draw = () => {
    const fmatch = (c) => state.fam === 'all' || family(c) === state.fam;
    tabs.replaceChildren(chips([[`Drawn (${codes.length})`, state.tab === 'drawn', () => { state.tab = 'drawn'; draw(); }], [`Still to draw (${pending.length})`, state.tab === 'pending', () => { state.tab = 'pending'; draw(); }]]));
    famRow.replaceChildren(state.tab === 'drawn' ? chips(fams.map((f) => [f === 'all' ? 'All' : f, state.fam === f, () => { state.fam = f; draw(); }])) : '');
    if (state.tab === 'pending') {
      count$.textContent = `${pending.length} icons the apps use that are not drawn in the set yet.`;
      grid.replaceChildren(...pending.filter((c) => !state.q || String(c).toLowerCase().includes(state.q)).map((c) => h('div', { class: 'icon-tile todo' }, h('span', { class: 'mono' }, String(c)))));
      return;
    }
    const list = codes.filter((c) => fmatch(c) && (!state.q || c.toLowerCase().includes(state.q) || String(icons[c].n || '').toLowerCase().includes(state.q))).sort();
    count$.textContent = `${list.length} of ${count || codes.length} icons.`;
    grid.style.setProperty('--tile', `${Math.max(86, state.size + 46)}px`); grid.style.setProperty('--tile-h', `${state.size + 52}px`);
    grid.replaceChildren(...list.map((c) => h('div', { role: 'button', tabindex: 0, class: `icon-tile${state.pick === c ? ' on' : ''}`, title: icons[c].n || c, onclick: () => show(c), onkeydown: (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); show(c); } } }, h('span', { class: 'icon-art', style: `height:${state.size}px` }, svgOf(c, state.size)), h('span', { class: 'mono' }, c.replace(/^cd:/, '')))));
  };
  el.replaceChildren(h('div', { class: 'stack' }, h('p', { class: 'note' }, 'Every icon the house draws. Choose one to see its code, copy it, or use it on a dashboard panel (Design, Icons).'),
    h('div', { class: 'row', style: 'gap:12px;flex-wrap:wrap' }, h('div', { style: 'flex:1;min-width:220px' }, search), h('label', { class: 'row', style: 'gap:8px' }, h('span', { class: 'sub' }, 'Size'), size)),
    tabs, famRow, count$, detail, grid));
  draw();
}
