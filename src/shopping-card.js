// Shopping Card: your shopping lists, and the full-page list you shop from.
//
// A shopping list is one task on a person's to-do list (see shopping.py in the integration). The card lists
// the open lists; tapping one (here, or its task on a to-do list, or a reminder's link ending ?list=<id>) opens
// the list as a full page you can scroll: tick each item Bought or Not available, add items, change who it is
// for, its date and the places it will be bought at. Back (the arrow, Escape, or the phone's back) closes it.
// Items that weren't available are offered again the next time a list is made.

import { createFormEditor } from './form-editor.js';
import { iconHtml, hydrateIcons } from './icons.js';
import { SUFFIX, LABEL } from './suffix.js';
import { kitShell, kitHead, kitEsc } from './card-kit.js';
import { shoppingIdOf, splitItems } from './shopping-logic.js';

export { shoppingIdOf };

const COLOUR = '#43a047';

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
const people = (hass) =>
  Object.keys(hass.states)
    .filter((id) => id.startsWith('person.'))
    .map((id) => String(hass.states[id].attributes.friendly_name || id.slice(7)).split(' ')[0])
    .sort();
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
  .sh-row { display:flex; align-items:center; gap:12px; padding:10px 6px; border-radius:12px; cursor:pointer; }
  .sh-row:hover { background:rgba(127,127,127,.12); }
  .sh-row .sh-main { flex:1; min-width:0; }
  .sh-name { font-weight:600; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
  .sh-sub { font-size:.8rem; color:var(--secondary-text-color); }
  .sh-badge { font-size:.8rem; font-weight:700; color:#fff; background:${COLOUR}; border-radius:999px; padding:2px 10px; }
  .sh-btn { border:none; border-radius:10px; padding:9px 14px; font:inherit; font-size:.85rem; font-weight:600; cursor:pointer; color:var(--primary-text-color); background:rgba(127,127,127,.18); display:inline-flex; align-items:center; gap:6px; }
  .sh-btn.sh-go { background:${COLOUR}; color:#fff; }
  .sh-missed { font-size:.82rem; color:#ffa726; }
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
         <div><button type="button" class="sh-btn sh-go sh-new"></button></div>`,
        CSS
      );
      this.querySelector('.ck-headrow').style.display = this.config.title ? '' : 'none';
      this.querySelector('.sh-new').innerHTML = `${iconHtml('mdi:plus', { size: '18px' })}New shopping list`;
      this.querySelector('.sh-new').addEventListener('click', () => openShoppingPage(this._hass, 'new'));
      this.querySelector('.sh-list').addEventListener('click', (ev) => {
        const row = ev.target.closest('[data-list]');
        if (row) openShoppingPage(this._hass, row.dataset.list);
      });
      this._built = true;
    }
    const d = store.data;
    const mine = d ? d.lists.filter((l) => !l.done && (this.config.all || !d.mine || l.people.includes(d.mine))) : [];
    if (this.config.title) kitHead(this, this.config.title, store.error ? 'Not set up' : mine.length ? `${mine.length} list${mine.length === 1 ? '' : 's'}` : 'No lists', COLOUR);
    this.querySelector('.sh-list').innerHTML = store.error
      ? `<div class="sh-sub">${kitEsc(store.error)}</div>`
      : mine.length
      ? mine
          .map(
            (l) => `<div class="sh-row" data-list="${l.id}" tabindex="0" role="button">
              ${iconHtml('mdi:cart-outline', { size: '26px', style: `color:${COLOUR}` })}
              <div class="sh-main"><div class="sh-name">${kitEsc(l.name || 'Shopping')}</div>
              <div class="sh-sub">${[day(l.date), l.people.join(', '), l.places.length ? `${l.places.length} place${l.places.length === 1 ? '' : 's'}` : ''].filter(Boolean).map(kitEsc).join(' · ')}</div></div>
              <span class="sh-badge">${l.counts.open} to get</span></div>`
          )
          .join('')
      : `<div class="sh-sub">No shopping lists. Share some items, or make one.</div>`;
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
  .shp { position:fixed; inset:0; z-index:9999; overflow-y:auto; overscroll-behavior:contain; background:var(--primary-background-color, #111); color:var(--primary-text-color); font-family:var(--paper-font-body1_-_font-family, inherit); }
  .shp * { box-sizing:border-box; }
  .shp-bar { position:sticky; top:0; z-index:2; display:flex; align-items:center; gap:8px; padding:calc(env(safe-area-inset-top, 0px) + 10px) 12px 10px; background:var(--card-background-color, #1c1c1c); box-shadow:0 2px 8px rgba(0,0,0,.35); }
  .shp-bar h1 { flex:1; margin:0; font-size:1.2rem; font-weight:600; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
  .shp-icon { border:none; background:none; color:inherit; width:44px; height:44px; border-radius:50%; cursor:pointer; display:inline-flex; align-items:center; justify-content:center; }
  .shp-icon:hover { background:rgba(127,127,127,.2); }
  .shp-body { max-width:640px; margin:0 auto; padding:14px 14px calc(env(safe-area-inset-bottom, 0px) + 60px); display:flex; flex-direction:column; gap:14px; }
  .shp-meta { display:flex; flex-wrap:wrap; gap:6px; }
  .shp-chip { font-size:.78rem; font-weight:600; border-radius:999px; padding:3px 10px; background:rgba(127,127,127,.2); }
  .shp-bar2 { height:8px; border-radius:99px; background:rgba(127,127,127,.25); overflow:hidden; }
  .shp-bar2 > i { display:block; height:100%; background:${COLOUR}; transition:width .3s; }
  .shp-h { font-size:.8rem; font-weight:700; text-transform:uppercase; letter-spacing:.04em; color:var(--secondary-text-color); margin:6px 0 -6px; }
  .shp-item { display:flex; align-items:center; gap:10px; padding:10px 12px; border-radius:14px; background:var(--card-background-color, #1c1c1c); }
  .shp-item .t { flex:1; min-width:0; font-size:1.05rem; word-break:break-word; }
  .shp-item.off .t { text-decoration:line-through; opacity:.6; }
  .shp-act { border:none; border-radius:12px; height:44px; min-width:44px; padding:0 12px; cursor:pointer; font:inherit; font-weight:600; display:inline-flex; align-items:center; justify-content:center; gap:4px; color:#fff; }
  .shp-buy { background:${COLOUR}; } .shp-na { background:#8d6e63; } .shp-undo { background:rgba(127,127,127,.35); }
  .shp-add { display:flex; gap:8px; }
  .shp-add input, .shp input, .shp textarea { font:inherit; color:inherit; background:rgba(127,127,127,.15); border:1px solid rgba(127,127,127,.35); border-radius:10px; padding:11px 12px; min-width:0; }
  .shp-add input { flex:1; font-size:1rem; }
  .shp textarea { width:100%; min-height:110px; }
  .shp-field { display:flex; flex-direction:column; gap:6px; }
  .shp-field > span { font-size:.8rem; font-weight:700; color:var(--secondary-text-color); }
  .shp-chips { display:flex; flex-wrap:wrap; gap:6px; }
  .shp-pick { border:none; border-radius:999px; padding:8px 14px; font:inherit; font-size:.85rem; font-weight:600; cursor:pointer; color:inherit; background:rgba(127,127,127,.2); }
  .shp-pick[aria-pressed="true"] { background:${COLOUR}; color:#fff; }
  .shp-banner { padding:12px 14px; border-radius:14px; background:color-mix(in srgb, ${COLOUR} 25%, transparent); font-size:.9rem; }
  .shp-warn { color:#ffa726; font-size:.85rem; }
  .shp-btn { border:none; border-radius:12px; padding:12px 16px; font:inherit; font-weight:700; cursor:pointer; color:inherit; background:rgba(127,127,127,.25); }
  .shp-btn.go { background:${COLOUR}; color:#fff; } .shp-btn.danger { background:#c62828; color:#fff; }
  .shp-row { display:flex; gap:8px; flex-wrap:wrap; }
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
    this.mode = id === 'new' ? 'form' : 'list';
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

  openForm(l) {
    const d = store.data;
    this.mode = 'form';
    this.form = l
      ? { edit: true, name: l.name, who: [...l.people], date: l.date, places: [...l.places], items: '', missed: false }
      : { edit: false, name: '', who: d && d.mine ? [d.mine] : [], date: '', places: [], items: '', missed: !!(d && d.missed.length) };
  }

  draw() {
    const bar = this.root.querySelector('.shp-bar');
    const body = this.root.querySelector('.shp-body');
    if (this.mode === 'form') return this.drawForm(bar, body);
    const l = this.list;
    const keep = body.querySelector('.shp-add input');
    const typed = keep ? keep.value : '';
    bar.innerHTML = `<button class="shp-icon" data-act="back" aria-label="Back">${iconHtml('mdi:arrow-left')}</button><h1>${kitEsc(l ? l.name || 'Shopping' : 'Shopping')}</h1>${l ? `<button class="shp-icon" data-act="edit" aria-label="Edit">${iconHtml('mdi:pencil-outline')}</button>` : ''}`;
    if (!l) {
      body.innerHTML = `<div class="shp-banner">${store.data || store.error ? 'This list has been deleted or finished.' : 'Loading…'}</div>`;
      hydrateIcons(this.root);
      return;
    }
    const items = (s) => l.items.filter((i) => i.status === s);
    const open = items('open');
    const row = (i, cls) =>
      `<div class="shp-item ${cls}" data-item="${i.id}"><div class="t">${kitEsc(i.text)}</div>` +
      (cls === ''
        ? `<button class="shp-act shp-na" data-set="unavailable" aria-label="Not available">${iconHtml('mdi:close', { size: '22px' })}</button><button class="shp-act shp-buy" data-set="bought" aria-label="Bought">${iconHtml('mdi:check', { size: '22px' })}</button>`
        : `<button class="shp-act shp-undo" data-set="open" aria-label="Put back">${iconHtml('mdi:undo', { size: '20px' })}</button>`) +
      '</div>';
    const c = l.counts;
    body.innerHTML = `
      <div class="shp-meta">${[l.date && `${day(l.date)}`, l.people.join(', '), ...l.places.map((p) => (store.data.zones.find((z) => z.zone === p) || {}).name || p)]
        .filter(Boolean)
        .map((t) => `<span class="shp-chip">${kitEsc(t)}</span>`)
        .join('')}</div>
      <div class="shp-bar2"><i style="width:${c.total ? Math.round(((c.total - c.open) / c.total) * 100) : 0}%"></i></div>
      ${l.done ? `<div class="shp-banner">All done. This list goes tomorrow.${c.unavailable ? ` ${c.unavailable} ${c.unavailable === 1 ? 'wasn’t' : 'weren’t'} available; you’ll be reminded next time.` : ''}</div>` : ''}
      <div class="shp-add"><input type="text" placeholder="Add an item (or several, separated by commas)" enterkeyhint="done" autocomplete="off"><button class="shp-btn go" data-act="add">Add</button></div>
      ${open.length ? `<div class="shp-h">To get (${open.length})</div>${open.map((i) => row(i, '')).join('')}` : ''}
      ${items('bought').length ? `<div class="shp-h">Bought (${items('bought').length})</div>${items('bought').map((i) => row(i, 'off')).join('')}` : ''}
      ${items('unavailable').length ? `<div class="shp-h">Not available (${items('unavailable').length})</div>${items('unavailable').map((i) => row(i, 'off')).join('')}` : ''}
      <div class="shp-warn"></div>
      <div class="shp-row"><button class="shp-btn danger" data-act="delete">Delete this list</button></div>`;
    const input = body.querySelector('.shp-add input');
    input.value = typed;
    if (keep && document.activeElement === keep) input.focus();
    hydrateIcons(this.root);
  }

  drawForm(bar, body) {
    const f = this.form;
    const d = store.data || { zones: [], missed: [], lists: [] };
    bar.innerHTML = `<button class="shp-icon" data-act="${f.edit ? 'cancel-edit' : 'back'}" aria-label="Back">${iconHtml('mdi:arrow-left')}</button><h1>${f.edit ? 'Edit list' : 'New shopping list'}</h1>`;
    const pick = (kind, value, label, on) => `<button type="button" class="shp-pick" data-${kind}="${kitEsc(value)}" aria-pressed="${on}">${kitEsc(label)}</button>`;
    const shopZones = d.zones.filter((z) => z.zone !== 'zone.home');
    // Redraw only on structural changes so typing is never interrupted.
    if (this._formDrawn !== f) {
      body.innerHTML = `
        <label class="shp-field"><span>Name</span><input class="f-name" type="text" placeholder="Weekly shop" value="${kitEsc(f.name)}"></label>
        <div class="shp-field"><span>Who is it for?</span><div class="shp-chips f-who"></div></div>
        <label class="shp-field"><span>Date to go shopping (optional)</span><input class="f-date" type="date" value="${kitEsc(f.date)}"></label>
        <div class="shp-field"><span>Where will you buy it? (optional)</span><div class="shp-chips f-places"></div></div>
        ${f.edit ? '' : `<label class="shp-field"><span>Items (one per line)</span><textarea class="f-items" placeholder="Milk&#10;Eggs&#10;Bread">${kitEsc(f.items)}</textarea></label>`}
        ${!f.edit && d.missed.length ? `<div class="shp-field"><div class="shp-chips f-missed"></div><div class="sh-sub">${kitEsc(d.missed.map((m) => m.text).join(', '))}</div></div>` : ''}
        <div class="shp-warn"></div>
        <div class="shp-row"><button class="shp-btn go" data-act="save">${f.edit ? 'Save' : 'Make the list'}</button><button class="shp-btn" data-act="${f.edit ? 'cancel-edit' : 'back'}">Cancel</button></div>`;
      body.querySelector('.f-name').addEventListener('input', (e) => (f.name = e.target.value));
      body.querySelector('.f-date').addEventListener('change', (e) => (f.date = e.target.value));
      const t = body.querySelector('.f-items');
      if (t) t.addEventListener('input', (e) => (f.items = e.target.value));
      this._formDrawn = f;
    }
    body.querySelector('.f-who').innerHTML = people(this.hass).map((p) => pick('who', p, p, f.who.includes(p))).join('');
    body.querySelector('.f-places').innerHTML = shopZones.length
      ? shopZones.map((z) => pick('place', z.zone, z.name, f.places.includes(z.zone))).join('')
      : '<span class="sh-sub">Add places in Home Assistant’s Zones to pick them here.</span>';
    const m = body.querySelector('.f-missed');
    if (m) m.innerHTML = pick('missed', '1', `Add the ${d.missed.length} item${d.missed.length === 1 ? '' : 's'} not available last time`, f.missed);
    hydrateIcons(this.root);
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
    const place = t.closest('[data-place]');
    if (place) {
      const p = place.dataset.place;
      this.form.places = this.form.places.includes(p) ? this.form.places.filter((x) => x !== p) : [...this.form.places, p];
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
      const r = await this.run({ type: 'church_drive/shopping/update', list: this.id, name: f.name, people: f.who, date: f.date, places: f.places });
      if (r) {
        this.mode = 'list';
        this._formDrawn = null;
        this.draw();
      }
      return;
    }
    const r = await this.run({ type: 'church_drive/shopping/create', name: f.name, people: f.who, date: f.date, places: f.places, items: splitItems(f.items), include_missed: f.missed });
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
