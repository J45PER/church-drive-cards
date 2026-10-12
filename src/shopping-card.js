// Shopping Card: your shopping lists, and the full-page list you shop from. A "steps list" is the same thing for
// a checklist (kind "steps"): one task on a to-do list that you can't tick off by hand, finished by finishing its
// steps on this page. It has one tick per step (no "Not available") and can remind you at a place, such as Home.
//
// A shopping list is one task on a person's to-do list (see shopping.py in the integration). The card lists
// the open lists; tapping one (here, or its task on a to-do list, or a reminder's link ending ?list=<id>) opens
// the list as a full page you can scroll: tick each item Bought or Not available, add items, change who it is
// for, its date and the places it will be bought at. Back (the arrow, Escape, or the phone's back) closes it.
// Items that weren't available are offered again the next time a list is made.

import * as L from 'leaflet/dist/leaflet-src.esm.js';
import LEAFLET_CSS from 'leaflet/dist/leaflet.css';
import { MAP_LAYERS, mapStyle, googleTiles } from './map-style.js';
import { createFormEditor } from './form-editor.js';
import { iconHtml, hydrateIcons } from './icons.js';
import { SUFFIX, LABEL } from './suffix.js';
import { kitShell, kitHead, kitEsc } from './card-kit.js';
import { shoppingIdOf, isStepsTask, splitItems, mapsUrl, samePlace } from './shopping-logic.js';
import { checkStale } from './stale.js';

export { shoppingIdOf };

const COLOUR = '#5c6bc0';

// ---- the lists, shared by every card and the full page -----------------------------------------------------

const store = { data: null, error: '', listeners: new Set(), hass: null, sub: null };

async function refresh() {
  if (!store.hass) return;
  try {
    store.data = await store.hass.callWS({ type: 'church_drive/shopping' });
    store.error = '';
  } catch (err) {
    store.error = (err && err.message) || 'Shopping lists need Church Drive 0.42 or newer';
  }
  store.listeners.forEach((fn) => fn());
}

function watch(hass, fn) {
  store.hass = hass;
  store.listeners.add(fn);
  if (!store.sub) {
    store.sub = hass.connection.subscribeEvents(refresh, 'church_drive_shopping_changed').catch(() => null);
    refresh();
  } else if (!store.data && !store.error) refresh();
  return () => store.listeners.delete(fn);
}

const call = (msg) => store.hass.callWS(msg);
// The house as a place a steps list can remind at (Home's position from its zone), or null if it has none.
const homePlace = (hass) => {
  const z = hass && hass.states && hass.states['zone.home'];
  const lat = z && Number(z.attributes.latitude);
  const lon = z && Number(z.attributes.longitude);
  return z && Number.isFinite(lat) && Number.isFinite(lon) && !(lat === 0 && lon === 0) ? { name: 'Home', address: '', lat, lon } : null;
};
const people = (hass) =>
  (store.data && store.data.people && store.data.people.length ? store.data.people : Object.keys(hass.states)
    .filter((id) => id.startsWith('person.'))
    .map((id) => String(hass.states[id].attributes.friendly_name || id.slice(7)).split(' ')[0])
    .sort());
const day = (iso) => {
  if (!iso) return '';
  const d = new Date(`${iso}T12:00:00`);
  const n = Math.round((d - new Date(new Date().toDateString() + ' 12:00:00')) / 86400000);
  if (n === 0) return 'today';
  if (n === 1) return 'tomorrow';
  if (n === -1) return 'yesterday';
  return d.toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short' });
};

const CSS = `
  .sh-row { display:flex; align-items:center; gap:10px; padding:8px 2px; border-radius:12px; cursor:pointer; }
  .sh-row + .sh-row { border-top:1px solid var(--divider-color, rgba(127,127,127,0.22)); }
  .sh-row:hover { background:rgba(127,127,127,0.07); }
  .sh-main { flex:1; min-width:0; }
  .sh-name { font-size:0.95rem; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
  .sh-when { flex:none; font-size:0.78rem; font-weight:600; white-space:nowrap; color:var(--secondary-text-color); }
  .sh-missed { font-size:0.8rem; color:#ffa726; }
  .sh-add { border:none; border-radius:12px; padding:9px 14px; font:inherit; font-size:0.85rem; font-weight:600; cursor:pointer; display:flex; align-items:center; gap:6px; background:color-mix(in srgb, ${COLOUR} 22%, transparent); color:color-mix(in srgb, ${COLOUR} 45%, white); }
  .sh-row :focus-visible, .sh-add:focus-visible { outline:2px solid var(--primary-color); outline-offset:2px; }
`;

// ---- the card ---------------------------------------------------------------------------------------------

export const ShoppingCardEditor = createFormEditor({
  schema: () => [
    { name: 'title', selector: { text: {} } },
    { name: 'all', selector: { boolean: {} } },
  ],
  labels: { title: 'Title (optional)', all: 'Show everyone’s lists, not just mine' },
  helpers: { all: 'Off shows the lists you are on.' },
});

export class ShoppingCard extends HTMLElement {
  setConfig(config) {
    this.config = config || {};
    this._built = false;
  }

  set hass(hass) {
    this._hass = hass;
    checkStale(hass);
    if (!this._off && this.isConnected) this._off = watch(hass, () => this._render());
    openFromUrl(hass);
    this._render();
  }

  connectedCallback() {
    if (this._hass && !this._off) this._off = watch(this._hass, () => this._render());
  }

  disconnectedCallback() {
    if (this._off) this._off();
    this._off = null;
  }

  _render() {
    if (!this._hass) return;
    if (!this._built) {
      this.innerHTML = kitShell(
        `<div class="sh-list" style="display:flex; flex-direction:column;"></div><div class="sh-missed"></div>
         <div class="sh-foot"><button type="button" class="sh-add sh-new"></button></div>`,
        CSS
      );
      this.querySelector('.ck-headrow').style.display = this.config.title ? '' : 'none';
      this.querySelector('.sh-new').innerHTML = `${iconHtml('mdi:plus', { size: '18px' })}New shopping list`;
      this.querySelector('.sh-new').addEventListener('click', () => openShoppingPage(this._hass, 'new'));
      const openRow = (ev) => {
        const row = ev.target.closest('[data-list]');
        if (row) openShoppingPage(this._hass, row.dataset.list);
      };
      this.querySelector('.sh-list').addEventListener('click', openRow);
      this.querySelector('.sh-list').addEventListener('keydown', (ev) => {
        if (ev.key === 'Enter' || ev.key === ' ') {
          ev.preventDefault();
          openRow(ev);
        }
      });
      this._built = true;
    }
    const d = store.data;
    const mine = d ? d.lists.filter((l) => l.kind !== 'steps' && !l.done && (this.config.all || !d.mine || l.people.includes(d.mine))) : [];
    if (this.config.title) kitHead(this, this.config.title, store.error ? 'Not set up' : mine.length ? `${mine.length} list${mine.length === 1 ? '' : 's'}` : 'No lists', COLOUR);
    this.querySelector('.sh-list').innerHTML = store.error
      ? `<div class="ck-sub" style="line-height:1.5;">${kitEsc(store.error)}</div>`
      : mine.length
      ? mine
          .map(
            (l) => `<div class="sh-row" data-list="${l.id}" tabindex="0" role="button">
              ${iconHtml('mdi:cart-outline', { size: '20px', style: `color:${COLOUR}; flex:none;` })}
              <div class="sh-main"><div class="sh-name">${kitEsc(l.name || 'Shopping')}</div>
              <div class="ck-sub" style="font-size:0.74rem; line-height:1.35;">${[day(l.date), l.people.join(', '), l.places.length ? `${l.places.length} place${l.places.length === 1 ? '' : 's'}` : ''].filter(Boolean).map(kitEsc).join(' · ')}</div></div>
              <span class="sh-when">${l.counts.open} to get</span></div>`
          )
          .join('')
      : `<div class="ck-sub" style="line-height:1.5; padding:4px 0;">No shopping lists. Share some items, or make one.</div>`;
    const miss = d ? d.missed : [];
    this.querySelector('.sh-missed').textContent = miss.length ? `Not available last time: ${miss.map((m) => m.text).join(', ')}` : '';
    hydrateIcons(this);
  }

  getCardSize() {
    return 3;
  }

  getGridOptions() {
    return { columns: 12, min_columns: 6, rows: 'auto' };
  }

  static getConfigElement() {
    return document.createElement(`shopping-card-editor${SUFFIX}`);
  }

  static getStubConfig() {
    return {};
  }
}

// ---- the full page -----------------------------------------------------------------------------------------

const PAGE_CSS = `
  ${LEAFLET_CSS}
  .shp-map { height:280px; border-radius:12px; overflow:hidden; background:#2a2f2a; position:relative; z-index:0; }
  .shp-pin { background:none; border:none; }
  .shp-pin svg { width:28px; height:38px; margin:-36px 0 0 -14px; filter:drop-shadow(0 2px 3px rgba(0,0,0,0.5)); }
  .shp-pop b { display:block; } .shp-pop button { margin-top:6px; }
  .shp { position:fixed; inset:0; z-index:9999; overflow-y:auto; overscroll-behavior:contain; background:var(--primary-background-color, #111); color:var(--primary-text-color); font-family:var(--paper-font-body1_-_font-family, inherit); }
  .shp * { box-sizing:border-box; }
  .shp-bar { position:sticky; top:0; z-index:2; display:flex; align-items:center; gap:6px; padding:calc(env(safe-area-inset-top, 0px) + 8px) 12px 8px; background:var(--primary-background-color, #111); }
  .shp-title { flex:1; min-width:0; font-size:1.5rem; font-weight:500; line-height:1.2; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; color:${COLOUR}; }
  .shp-word { flex:none; font-size:0.85rem; color:var(--secondary-text-color); }
  .shp-icon { border:none; background:none; color:inherit; width:44px; height:44px; border-radius:50%; cursor:pointer; display:inline-flex; align-items:center; justify-content:center; flex:none; }
  .shp-icon:hover { background:rgba(127,127,127,.2); }
  .shp-body { max-width:640px; margin:0 auto; padding:6px 12px calc(env(safe-area-inset-bottom, 0px) + 60px); display:flex; flex-direction:column; gap:12px; }
  .shp-card { border-radius:16px; padding:16px; background:var(--card-background-color, #1c1c1c); box-shadow:0 3px 10px rgba(0,0,0,0.45); display:flex; flex-direction:column; gap:12px; }
  .shp-h { font-size:1rem; font-weight:500; }
  .shp-sub { font-size:0.8rem; color:var(--secondary-text-color); }
  .shp-row { display:flex; align-items:center; gap:10px; padding:8px 2px; border-radius:12px; }
  .shp-row + .shp-row { border-top:1px solid var(--divider-color, rgba(127,127,127,0.22)); }
  .shp-name { flex:1; min-width:0; font-size:0.95rem; word-break:break-word; }
  .shp-row.off .shp-name { text-decoration:line-through; color:var(--secondary-text-color); }
  .shp-tick { flex:none; width:24px; height:24px; padding:0; border-radius:50%; border:2px solid ${COLOUR}; background:transparent; color:#fff; cursor:pointer; display:flex; align-items:center; justify-content:center; }
  .shp-tick.bought { background:#4caf50; border-color:#4caf50; }
  .shp-tick.na { background:#8d6e63; border-color:#8d6e63; }
  .shp-chip { flex:none; border:none; border-radius:12px; padding:7px 10px; font:inherit; font-size:0.78rem; font-weight:600; cursor:pointer; display:flex; align-items:center; gap:4px; background:rgba(127,127,127,0.18); color:var(--primary-text-color); }
  .shp-btn { border:none; border-radius:12px; padding:9px 14px; font:inherit; font-size:0.85rem; font-weight:600; cursor:pointer; display:flex; align-items:center; gap:6px; background:rgba(127,127,127,0.18); color:var(--primary-text-color); }
  .shp-btn.go { background:color-mix(in srgb, ${COLOUR} 22%, transparent); color:color-mix(in srgb, ${COLOUR} 45%, white); }
  .shp-btn.danger { background:color-mix(in srgb, #e53935 22%, transparent); color:color-mix(in srgb, #e53935 45%, white); }
  .shp-link { border:none; background:none; padding:4px 2px; font:inherit; font-size:0.8rem; color:var(--secondary-text-color); text-decoration:underline; cursor:pointer; align-self:flex-start; }
  .shp-add { display:flex; gap:8px; }
  .shp input, .shp textarea { font:inherit; font-size:0.92rem; color:var(--primary-text-color); background:rgba(127,127,127,0.1); border:1px solid var(--divider-color, rgba(127,127,127,0.3)); border-radius:10px; padding:8px 10px; min-width:0; color-scheme:dark light; }
  .shp-add input { flex:1; }
  .shp textarea { width:100%; min-height:110px; }
  .shp-field { display:flex; flex-direction:column; gap:6px; font-size:0.8rem; color:var(--secondary-text-color); }
  .shp-field input { width:100%; }
  .shp-chips { display:flex; flex-wrap:wrap; gap:6px; }
  .shp-pick { border:none; border-radius:999px; padding:7px 12px; font:inherit; font-size:0.82rem; font-weight:600; cursor:pointer; background:rgba(127,127,127,0.18); color:var(--primary-text-color); }
  .shp-pick[aria-pressed="true"] { background:${COLOUR}; color:#fff; }
  .shp-banner { padding:10px 12px; border-radius:12px; background:color-mix(in srgb, ${COLOUR} 22%, transparent); font-size:0.88rem; }
  .shp-warn { color:#ffa726; font-size:0.8rem; min-height:1em; }
  .shp-actions { display:flex; flex-wrap:wrap; gap:8px; }
  .shp :focus-visible { outline:2px solid var(--primary-color); outline-offset:2px; }
`;

let page = null;

export function closeShoppingPage(fromPopstate = false) {
  if (!page) return;
  const p = page;
  page = null;
  p.close();
  if (!fromPopstate && history.state && history.state.cdShopping) history.back();
}

// Open the full-page list. [id] is a list's id, or "new" for the make-a-list form.
export function openShoppingPage(hass, id) {
  closeShoppingPage();
  page = new ShoppingPage(hass, id);
}

class ShoppingPage {
  constructor(hass, id) {
    this.hass = hass;
    this.id = id;
    this.mode = id === 'new' || id === 'new-steps' ? 'form' : 'list';
    this.newKind = id === 'new-steps' ? 'steps' : 'shopping';
    this.form = null;
    this.root = document.createElement('div');
    this.root.className = 'shp';
    this.root.innerHTML = `<style>${PAGE_CSS}</style><div class="shp-bar"></div><div class="shp-body"></div>`;
    document.body.appendChild(this.root);
    history.pushState({ cdShopping: 1 }, '');
    this._pop = () => closeShoppingPage(true);
    this._key = (ev) => ev.key === 'Escape' && closeShoppingPage();
    window.addEventListener('popstate', this._pop);
    window.addEventListener('keydown', this._key);
    this.off = watch(hass, () => this.draw());
    this.root.addEventListener('click', (ev) => this.click(ev));
    this.root.addEventListener('keydown', (ev) => {
      if (ev.key === 'Enter' && ev.target.matches('.shp-add input')) this.add();
    });
    if (this.mode === 'form') this.openForm(null);
    this.draw();
  }

  close() {
    this.off();
    window.removeEventListener('popstate', this._pop);
    window.removeEventListener('keydown', this._key);
    if (this._pmap) this._pmap.remove();
    this.root.remove();
  }

  get list() {
    return store.data && store.data.lists.find((l) => l.id === this.id);
  }

  say(text) {
    const m = this.root.querySelector('.shp-warn');
    if (m) m.textContent = text;
  }

  async run(msg) {
    try {
      const r = await call(msg);
      await refresh(); // don't wait for the change event to show it
      return r;
    } catch (err) {
      this.say((err && err.message) || String(err));
      return null;
    }
  }

  get steps() {
    return this.mode === 'form' ? this.form.kind === 'steps' : !!this.list && this.list.kind === 'steps';
  }

  openForm(l) {
    const d = store.data;
    this.mode = 'form';
    this.form = l
      ? { edit: true, kind: l.kind || 'shopping', name: l.name, who: [...l.people], date: l.date, time: l.time || '', places: l.places.map((p) => ({ ...p })), items: '', missed: false, q: '', results: [], note: '' }
      : { edit: false, kind: this.newKind, name: '', who: d && d.mine ? [d.mine] : [], date: '', time: '', places: [], items: '', missed: this.newKind !== 'steps' && !!(d && d.missed.length), q: '', results: [], note: '' };
  }

  draw() {
    const bar = this.root.querySelector('.shp-bar');
    const body = this.root.querySelector('.shp-body');
    if (this.mode === 'form') return this.drawForm(bar, body);
    const l = this.list;
    const keep = body.querySelector('.shp-add input');
    const typed = keep ? keep.value : '';
    const st = !!l && l.kind === 'steps';
    bar.innerHTML = `<button class="shp-icon" data-act="back" aria-label="Back">${iconHtml('mdi:arrow-left')}</button><div class="shp-title">${kitEsc(l ? l.name || (st ? 'Steps' : 'Shopping') : 'Shopping')}</div>${l ? `<span class="shp-word">${l.done ? 'All done' : `${l.counts.open} to ${st ? 'do' : 'get'}`}</span><button class="shp-icon" data-act="edit" aria-label="Edit">${iconHtml('mdi:pencil-outline')}</button>` : ''}`;
    if (!l) {
      body.innerHTML = `<div class="shp-card"><div class="shp-sub">${store.data || store.error ? 'This list has been deleted or finished.' : 'Loading…'}</div></div>`;
      hydrateIcons(this.root);
      return;
    }
    const items = (s) => l.items.filter((i) => i.status === s);
    const open = items('open');
    const finished = l.items.filter((i) => i.status !== 'open');
    const openRow = (i) =>
      `<div class="shp-row" data-item="${i.id}"><button type="button" class="shp-tick" data-set="bought" aria-label="${st ? 'Done' : 'Bought'}: ${kitEsc(i.text)}"></button><div class="shp-name">${kitEsc(i.text)}</div>${st ? '' : `<button type="button" class="shp-chip" data-set="unavailable" aria-label="Not available: ${kitEsc(i.text)}">${iconHtml('mdi:close', { size: '14px' })}Not available</button>`}</div>`;
    const doneRow = (i) =>
      `<div class="shp-row off" data-item="${i.id}"><button type="button" class="shp-tick ${i.status === 'bought' ? 'bought' : 'na'}" data-set="open" aria-label="Put back: ${kitEsc(i.text)}">${iconHtml(i.status === 'bought' ? 'mdi:check' : 'mdi:close', { size: '15px' })}</button><div class="shp-name">${kitEsc(i.text)}</div>${i.status === 'unavailable' ? '<span class="shp-sub" style="text-decoration:none;">not available</span>' : ''}</div>`;
    const c = l.counts;
    const meta = [l.date && day(l.date), l.people.join(', '), l.time && !l.done ? l.time : '', ...l.places.map((p) => p.name)].filter(Boolean).join(' · ');
    body.innerHTML = `
      <div class="shp-card">
        ${meta ? `<div class="shp-sub">${kitEsc(meta)}</div>` : ''}
        ${l.places.length ? `<div class="shp-chips">${l.places.map((p, n) => `<a class="shp-btn go" href="${kitEsc(mapsUrl(p))}" target="_blank" rel="noopener" data-nav="${n}">${iconHtml('mdi:navigation-variant', { size: '18px' })}Navigate to ${kitEsc(p.name)}</a>`).join('')}</div>` : ''}
        ${l.done ? `<div class="shp-banner">All done. This list goes tomorrow.${c.unavailable && !st ? ` ${c.unavailable} ${c.unavailable === 1 ? 'wasn’t' : 'weren’t'} available; you’ll be reminded next time.` : ''}</div>` : ''}
        <div class="shp-add"><input type="text" placeholder="${st ? 'Add a step (or several, with commas)' : 'Add an item (or several, with commas)'}" enterkeyhint="done" autocomplete="off"><button class="shp-btn go" data-act="add">${iconHtml('mdi:plus', { size: '18px' })}Add</button></div>
        ${open.length ? `<div>${open.map(openRow).join('')}</div>` : `<div class="shp-sub">${c.total ? 'Everything is ticked off.' : st ? 'No steps yet.' : 'Nothing on the list yet.'}</div>`}
      </div>
      ${finished.length ? `<div class="shp-card"><div class="shp-h">Done (${finished.length})</div><div>${finished.map(doneRow).join('')}</div></div>` : ''}
      <div class="shp-warn"></div>
      <div class="shp-actions"><button class="shp-btn danger" data-act="delete">${iconHtml('mdi:delete-outline', { size: '18px' })}Delete this list</button></div>`;
    const input = body.querySelector('.shp-add input');
    input.value = typed;
    if (keep && document.activeElement === keep) input.focus();
    hydrateIcons(this.root);
  }

  drawForm(bar, body) {
    const f = this.form;
    const d = store.data || { zones: [], missed: [], lists: [] };
    bar.innerHTML = `<button class="shp-icon" data-act="${f.edit ? 'cancel-edit' : 'back'}" aria-label="Back">${iconHtml('mdi:arrow-left')}</button><div class="shp-title">${f.kind === 'steps' ? (f.edit ? 'Edit steps' : 'New steps list') : f.edit ? 'Edit list' : 'New shopping list'}</div>`;
    const steps = f.kind === 'steps';
    const pick = (kind, value, label, on) => `<button type="button" class="shp-pick" data-${kind}="${kitEsc(value)}" aria-pressed="${on}">${kitEsc(label)}</button>`;
    // Redraw only on structural changes so typing is never interrupted.
    if (this._formDrawn !== f) {
      body.innerHTML = `<div class="shp-card">
        <label class="shp-field"><span>Name</span><input class="f-name" type="text" placeholder="${steps ? 'Set up Ring Talk' : 'Weekly shop'}" value="${kitEsc(f.name)}"></label>
        <div class="shp-field"><span>Who is it for?</span><div class="shp-chips f-who"></div></div>
        <div class="shp-field"><span>${steps ? 'Date and time (optional)' : 'Date and time to go shopping (optional)'}</span><div class="shp-add"><input class="f-date" type="date" value="${kitEsc(f.date)}"><input class="f-time" type="time" value="${kitEsc(f.time)}"></div></div>
        <div class="shp-field"><span>${steps ? 'Remind me at a place, as I arrive (optional). Search Google Maps, or use Home' : 'Where will you buy it? Search Google Maps (optional)'}</span>
          <div class="shp-chips f-places"></div>
          ${steps && homePlace(this.hass) ? `<div class="shp-chips"><button type="button" class="shp-pick" data-home="1" aria-pressed="${f.places.some((p) => samePlace(p, homePlace(this.hass)))}">Home</button></div>` : ''}
          <div class="shp-add"><input class="f-q" type="search" placeholder="e.g. Lidl Protaras Cyprus" enterkeyhint="search" autocomplete="off" value="${kitEsc(f.q)}"><button type="button" class="shp-btn go" data-act="search">${iconHtml('mdi:magnify', { size: '18px' })}Search</button></div>
          <div class="shp-map f-map" style="display:none;"></div>
          <div class="f-results"></div>
          <div class="shp-sub f-note"></div></div>
        ${f.edit ? '' : `<label class="shp-field"><span>${steps ? 'Steps (one per line)' : 'Items (one per line)'}</span><textarea class="f-items" placeholder="${steps ? 'Check the Hub&#10;Reserve the address&#10;Forward the port' : 'Milk&#10;Eggs&#10;Bread'}">${kitEsc(f.items)}</textarea></label>`}
        ${!f.edit && !steps && d.missed.length ? `<div class="shp-field"><div class="shp-chips f-missed"></div><div class="shp-sub">${kitEsc(d.missed.map((m) => m.text).join(', '))}</div></div>` : ''}
        <div class="shp-warn"></div>
        <div class="shp-actions"><button class="shp-btn go" data-act="save">${iconHtml('mdi:check', { size: '18px' })}${f.edit ? 'Save' : 'Make the list'}</button><button class="shp-btn" data-act="${f.edit ? 'cancel-edit' : 'back'}">Cancel</button></div></div>`;
      body.querySelector('.f-name').addEventListener('input', (e) => (f.name = e.target.value));
      body.querySelector('.f-date').addEventListener('change', (e) => (f.date = e.target.value));
      body.querySelector('.f-time').addEventListener('change', (e) => (f.time = e.target.value));
      const q = body.querySelector('.f-q');
      q.addEventListener('input', (e) => (f.q = e.target.value));
      q.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') {
          e.preventDefault();
          this.search();
        }
      });
      const t = body.querySelector('.f-items');
      if (t) t.addEventListener('input', (e) => (f.items = e.target.value));
      this._formDrawn = f;
    }
    body.querySelector('.f-who').innerHTML = people(this.hass).map((p) => pick('who', p, p, f.who.includes(p))).join('');
    body.querySelector('.f-places').innerHTML = f.places
      .map((p, n) => `<button type="button" class="shp-pick" data-unplace="${n}" aria-pressed="true" title="${kitEsc(p.address)}">${kitEsc(p.name)} ${iconHtml('mdi:close', { size: '13px' })}</button>`)
      .join('');
    body.querySelector('.f-results').innerHTML = f.results
      .map((p, n) => `<button type="button" class="shp-btn" style="width:100%; text-align:left; margin-bottom:6px;" data-addplace="${n}"><span><b>${kitEsc(p.name)}</b><br><span class="shp-sub">${kitEsc(p.address)}</span></span></button>`)
      .join('');
    body.querySelector('.f-note').textContent = f.note;
    this.drawMap(f, body.querySelector('.f-map'));
    const m = body.querySelector('.f-missed');
    if (m) m.innerHTML = pick('missed', '1', `Add the ${d.missed.length} item${d.missed.length === 1 ? '' : 's'} not available last time`, f.missed);
    hydrateIcons(this.root);
  }

  // The search results (red pins) and the places already chosen (indigo pins) on a map: tap a red pin, then "Use this place".
  drawMap(f, el) {
    const show = f.results.length > 0 || f.places.length > 0;
    el.style.display = show ? '' : 'none';
    if (!show) return;
    if (!this._pmap || this._pmapEl !== el) {
      if (this._pmap) this._pmap.remove();
      this._pmapEl = el;
      this._pmap = L.map(el, { zoomControl: true, attributionControl: true }).setView([35, 33], 5);
      this._pmap.attributionControl.setPrefix(false);
      const style = mapStyle() === 'street' ? 'street' : 'satellite';
      const g = googleTiles(style);
      const spec = MAP_LAYERS[style];
      (g ? [L.tileLayer(g.url, { maxNativeZoom: 20, maxZoom: 21, attribution: g.attribution })] : [L.tileLayer(spec.base, { maxNativeZoom: 19, maxZoom: 20, attribution: 'Imagery &copy; Esri' }), ...spec.labels.map((u) => L.tileLayer(u, { maxNativeZoom: 19, maxZoom: 20, zIndex: 5 }))]).forEach((t) => t.addTo(this._pmap));
      this._pins = L.layerGroup().addTo(this._pmap);
    }
    const pin = (colour) => L.divIcon({ className: 'shp-pin', html: `<svg viewBox="0 0 30 40"><path d="M15 0C6.7 0 0 6.6 0 14.8 0 25.9 15 40 15 40s15-14.1 15-25.2C30 6.6 23.3 0 15 0z" fill="${colour}"/><circle cx="15" cy="15" r="5.5" fill="#fff"/></svg>` });
    this._pins.clearLayers();
    const all = [];
    f.places.forEach((p) => {
      L.marker([p.lat, p.lon], { icon: pin(COLOUR) }).bindTooltip(kitEsc(p.name)).addTo(this._pins);
      all.push([p.lat, p.lon]);
    });
    f.results.forEach((p, n) => {
      const m = L.marker([p.lat, p.lon], { icon: pin('#ea4335'), zIndexOffset: 500 }).addTo(this._pins);
      m.bindPopup(`<div class="shp-pop"><b>${kitEsc(p.name)}</b>${kitEsc(p.address)}<br><button type="button" class="shp-btn go" data-addplace="${n}">Use this place</button></div>`);
      all.push([p.lat, p.lon]);
    });
    // Zoom to the new results once; a later redraw (picking who it is for) leaves the view where it is.
    const key = JSON.stringify(f.results.map((p) => [p.lat, p.lon]));
    if (all.length && this._pmapKey !== key) {
      this._pmapKey = key;
      this._pmap.fitBounds(L.latLngBounds(all), { padding: [30, 30], maxZoom: 16 });
    }
    setTimeout(() => this._pmap && this._pmap.invalidateSize(), 0);
  }

  async search() {
    const f = this.form;
    if (f.q.trim().length < 2) return;
    f.note = 'Searching…';
    f.results = [];
    this.draw();
    try {
      f.results = await call({ type: 'church_drive/maps/search', query: f.q.trim(), anywhere: true });
      f.note = f.results.length ? '' : 'Nothing found. Try the shop name and the town.';
    } catch (err) {
      f.note = err && err.code === 'no_key' ? 'Add a Google Places key in the Church Drive options to search.' : (err && err.message) || 'The search failed.';
    }
    this.draw();
  }

  async add() {
    const input = this.root.querySelector('.shp-add input');
    const items = splitItems(input.value);
    if (!items.length) return;
    input.value = '';
    await this.run({ type: 'church_drive/shopping/add', list: this.id, items });
    const next = this.root.querySelector('.shp-add input');
    if (next) next.focus();
  }

  async click(ev) {
    const t = ev.target;
    const act = t.closest('[data-act]');
    const set = t.closest('[data-set]');
    if (set) {
      const item = set.closest('[data-item]');
      return item && this.run({ type: 'church_drive/shopping/item', list: this.id, item: item.dataset.item, status: set.dataset.set });
    }
    const who = t.closest('[data-who]');
    if (who) {
      const w = who.dataset.who;
      this.form.who = this.form.who.includes(w) ? this.form.who.filter((x) => x !== w) : [...this.form.who, w];
      return this.draw();
    }
    const found = t.closest('[data-addplace]');
    if (found) {
      const p = this.form.results[Number(found.dataset.addplace)];
      if (p && !this.form.places.some((x) => samePlace(x, p)) && this.form.places.length < 5) this.form.places = [...this.form.places, p];
      this.form.results = [];
      return this.draw();
    }
    if (t.closest('[data-home]')) {
      const h = homePlace(this.hass);
      if (h) this.form.places = this.form.places.some((x) => samePlace(x, h)) ? this.form.places.filter((x) => !samePlace(x, h)) : [...this.form.places, h];
      this._formDrawn = null;
      return this.draw();
    }
    const drop = t.closest('[data-unplace]');
    if (drop) {
      this.form.places = this.form.places.filter((_, n) => n !== Number(drop.dataset.unplace));
      return this.draw();
    }
    if (t.closest('[data-missed]')) {
      this.form.missed = !this.form.missed;
      return this.draw();
    }
    if (!act) return;
    switch (act.dataset.act) {
      case 'back':
        return closeShoppingPage();
      case 'edit':
        this.openForm(this.list);
        this._formDrawn = null;
        return this.draw();
      case 'cancel-edit':
        this.mode = 'list';
        this._formDrawn = null;
        return this.draw();
      case 'add':
        return this.add();
      case 'search':
        return this.search();
      case 'delete':
        if (!act.dataset.sure) {
          act.dataset.sure = '1';
          act.textContent = 'Tap again to delete';
          return;
        }
        if (await this.run({ type: 'church_drive/shopping/delete', list: this.id })) closeShoppingPage();
        return;
      case 'save':
        return this.save();
    }
  }

  async save() {
    const f = this.form;
    if (!f.who.length) return this.say('Choose who the list is for.');
    if (f.edit) {
      const r = await this.run({ type: 'church_drive/shopping/update', list: this.id, name: f.name, people: f.who, date: f.date, time: f.time, places: f.places });
      if (r) {
        this.mode = 'list';
        this._formDrawn = null;
        this.draw();
      }
      return;
    }
    const r = await this.run({ type: 'church_drive/shopping/create', name: f.name, people: f.who, date: f.date, time: f.time, places: f.places, items: splitItems(f.items), include_missed: f.missed && f.kind !== 'steps', kind: f.kind });
    if (r) {
      this.id = r.list.id;
      this.mode = 'list';
      this._formDrawn = null;
      await refresh();
      this.draw();
    }
  }
}

// A page opened from a reminder's link (…/shopping?list=<id>) shows that list.
let urlOpened = false;
export function openFromUrl(hass) {
  if (urlOpened) return;
  const id = new URLSearchParams(location.search).get('list');
  if (id && /^[0-9a-f]{6,32}$/.test(id) && !page) {
    urlOpened = true;
    store.hass = store.hass || hass;
    openShoppingPage(hass, id);
  }
}

export function registerShoppingCard() {
  if (!customElements.get(`shopping-card-editor${SUFFIX}`)) customElements.define(`shopping-card-editor${SUFFIX}`, ShoppingCardEditor);
  if (!customElements.get(`shopping-card${SUFFIX}`)) customElements.define(`shopping-card${SUFFIX}`, ShoppingCard);
  window.customCards = window.customCards || [];
  window.customCards.push({
    type: `shopping-card${SUFFIX}`,
    name: `Shopping Card${LABEL}`,
    description: 'Shopping lists: tick items bought or not available on a full page, with dates, people and places',
    preview: false,
    documentationURL: 'https://github.com/J45PER/church-drive-cards#readme',
  });
}
