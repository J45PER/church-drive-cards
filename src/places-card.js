// Places Card (Manager's Locations section): a card for each person with the
// places they go and what they call each one. Home is automatic; each other
// place is one of Home Assistant's zones (made and named in Settings > Areas,
// labels & zones) with the person's own name for it: Work, Gym, or anything.
// Diane might have three post offices all called Work.
//
// Then "Who's home" says "At work · Ashfield School", and the Church Drive
// people sensor's `place` says Work, for templates and notifications.
//
// Kept by the Church Drive integration (church_drive/people/places); only
// administrators can change them, with Edit on a person's card (pick a zone
// and name it, ✕ to delete, + to add) and then Save.

import { createFormEditor } from './form-editor.js';
import { iconHtml, hydrateIcons } from './icons.js';
import { SUFFIX, LABEL } from './suffix.js';
import { kitShell, kitHead, kitEsc, kitNavigate } from './card-kit.js';

const PEOPLE_SENSOR = 'sensor.church_drive_people';
const SUGGEST = ['Work', 'School', 'College', 'Gym', 'Family', 'Friends', 'Shops'];

const PL_CSS = `
  .pl-grid { display:grid; grid-template-columns:repeat(auto-fill, minmax(min(100%, 300px), 1fr)); gap:12px; }
  .pl-person .ck-card { height:100%; box-sizing:border-box; }
  .pl-row { display:flex; align-items:center; gap:8px; }
  .pl-row + .pl-row { margin-top:8px; }
  .pl-ico { flex:none; width:32px; height:32px; border-radius:10px; display:flex; align-items:center; justify-content:center; background:rgba(127,127,127,0.16); }
  .pl-fixed { flex:1; min-width:0; font-size:0.88rem; }
  .pl-fixed small { display:block; color:var(--secondary-text-color); font-size:0.75rem; }
  .pl-row select, .pl-row input { min-width:0; box-sizing:border-box; height:36px; border-radius:10px; border:1px solid var(--divider-color, rgba(127,127,127,0.3));
    background:var(--secondary-background-color, rgba(127,127,127,0.12)); color:var(--primary-text-color); font:inherit; font-size:0.85rem; padding:0 8px; }
  .pl-row select { flex:1.3 1 0; }
  .pl-row input { flex:1 1 0; }
  .pl-x { flex:none; width:32px; height:32px; border:none; border-radius:50%; background:transparent; color:var(--secondary-text-color); cursor:pointer; display:flex; align-items:center; justify-content:center; }
  .pl-x:hover { background:rgba(127,127,127,0.15); }
  .pl-add { margin-top:10px; border:1px dashed var(--divider-color, rgba(127,127,127,0.4)); background:transparent; color:var(--primary-text-color); border-radius:12px; height:38px; width:100%; cursor:pointer; font:inherit; font-size:0.85rem; font-weight:600; display:flex; align-items:center; justify-content:center; gap:6px; }
  .pl-foot { margin-top:12px; font-size:0.78rem; color:var(--secondary-text-color); }
  .pl-foot a { color:var(--primary-color); cursor:pointer; }
  .pl-none { font-size:0.82rem; color:var(--secondary-text-color); }
  .pl-acts { display:flex; justify-content:flex-end; gap:8px; margin-top:10px; }
  .pl-btn { border:none; cursor:pointer; font:inherit; font-size:0.82rem; font-weight:600; border-radius:999px; padding:7px 14px; display:flex; align-items:center; gap:6px;
    background:rgba(127,127,127,0.16); color:var(--primary-text-color); }
  .pl-save { background:#26a69a; color:#fff; }
`;

// "At work · Ashfield School", "Gym · PureGym", "Ashfield School", "Home", "Away".
export function placeText(place, zone) {
  if (!place) return '';
  const word = /^work$/i.test(place) ? 'At work' : place;
  return zone && zone !== place ? `${word} · ${zone}` : word;
}

export const PlacesCardEditor = createFormEditor({
  schema: () => [
    { name: 'title', selector: { text: {} } },
    { name: 'demo', selector: { boolean: {} } },
  ],
  labels: { title: 'Title (optional)', demo: 'Show pretend people (for Design Presets)' },
  helpers: { title: 'A card for each person. Zones are made and named in Settings > Areas, labels & zones.' },
});

export class PlacesCard extends HTMLElement {
  setConfig(config) {
    this.config = config || {};
    this._built = false;
    this._data = null;
    this._drafts = {}; // person → places being edited (not saved yet)
  }

  set hass(hass) {
    this._hass = hass;
    if (this.config.demo) {
      if (!this._data) this._data = this._demo();
    } else {
      const st = hass.states[PEOPLE_SENSOR];
      const rev = st ? `${st.attributes.rev}|${st.last_updated}` : 'none';
      if (rev !== this._rev) {
        this._rev = rev;
        this._load();
      }
    }
    this._render();
  }

  _demo() {
    return [
      { entity_id: 'person.jamie', name: 'Jamie', first: 'Jamie', place: 'Work', zone: 'Ashfield School', places: [{ zone: 'zone.ashfield_school', name: 'Work' }] },
      { entity_id: 'person.diane', name: 'Diane', first: 'Diane', place: 'Home', zone: '', places: [{ zone: 'zone.po_1', name: 'Work' }, { zone: 'zone.po_2', name: 'Work' }, { zone: 'zone.po_3', name: 'Work' }] },
    ];
  }

  async _load() {
    try {
      const r = await this._hass.connection.sendMessagePromise({ type: 'church_drive/people' });
      this._data = r.people || [];
      this._sig = null;
      this._render();
    } catch (err) {
      this._data = this._data || [];
    }
  }

  _admin() {
    return !!(this.config.demo || (this._hass && this._hass.user && this._hass.user.is_admin));
  }

  _zones() {
    if (this.config.demo) return [['zone.ashfield_school', 'Ashfield School'], ['zone.po_1', 'Post office 1'], ['zone.po_2', 'Post office 2'], ['zone.po_3', 'Post office 3']];
    const s = this._hass.states;
    return Object.keys(s)
      .filter((id) => id.startsWith('zone.') && id !== 'zone.home')
      .map((id) => [id, String(s[id].attributes.friendly_name || id)])
      .sort((a, b) => a[1].localeCompare(b[1]));
  }

  async _save(person) {
    const places = (this._drafts[person] || []).filter((x) => x.zone).map((x) => ({ zone: x.zone, name: (x.name || '').trim() }));
    const p = (this._data || []).find((x) => x.entity_id === person);
    if (p) p.places = places;
    delete this._drafts[person];
    this._sig = null;
    this._render();
    if (this.config.demo) return;
    try {
      const r = await this._hass.connection.sendMessagePromise({ type: 'church_drive/people/places', person, places });
      this._data = r.people || this._data;
    } catch (err) {
      this._load();
      return;
    }
    this._sig = null;
    this._render();
  }

  _render() {
    if (!this._hass && !this.config.demo) return;
    const c = this.config;
    if (!this._built) {
      this.innerHTML = `<style>${PL_CSS}</style>${c.title ? `<div style="font-size:1.1rem; font-weight:600; margin:0 0 10px 4px;">${kitEsc(c.title)}</div>` : ''}<div class="pl-grid"></div>`;
      this._grid = this.querySelector('.pl-grid');
      // Typing and picking only change the draft; Save stores it.
      const keep = (ev) => {
        const wrap = ev.target.closest('[data-person]');
        const d = wrap && this._drafts[wrap.dataset.person];
        const i = Number(ev.target.dataset.i);
        if (!d || !d[i]) return;
        if (ev.target.matches('select')) d[i].zone = ev.target.value;
        else if (ev.target.matches('input')) d[i].name = ev.target.value;
      };
      this._grid.addEventListener('change', keep);
      this._grid.addEventListener('input', keep);
      this._grid.addEventListener('click', (ev) => this._click(ev));
      this._built = true;
    }
    const people = this._data || [];
    const zones = this._zones();
    const admin = this._admin();
    const sig = JSON.stringify([people, zones, admin, Object.keys(this._drafts)]);
    if (sig === this._sig) return;
    this._sig = sig;
    const zoneName = (id) => (zones.find((z) => z[0] === id) || [id, id.replace(/^zone\./, '').replace(/_/g, ' ')])[1];
    const icon = (name) => iconHtml(/^work$/i.test(name || '') ? 'mdi:briefcase-outline' : 'mdi:map-marker-outline', { size: '18px' });
    const datalist = `<datalist id="pl-names${SUFFIX}">${SUGGEST.map((n) => `<option value="${n}">`).join('')}</datalist>`;
    const cards = people.map((p) => {
      const draft = this._drafts[p.entity_id];
      let rows;
      if (draft) {
        rows = draft
          .map(
            (pl, i) => `<div class="pl-row"><div class="pl-ico">${icon(pl.name)}</div>
              <select data-i="${i}" aria-label="Zone">${zones.map(([id, n]) => `<option value="${kitEsc(id)}"${id === pl.zone ? ' selected' : ''}>${kitEsc(n)}</option>`).join('')}${zones.some((z) => z[0] === pl.zone) ? '' : `<option value="${kitEsc(pl.zone)}" selected>${kitEsc(zoneName(pl.zone))} (gone)</option>`}</select>
              <input data-i="${i}" list="pl-names${SUFFIX}" value="${kitEsc(pl.name)}" placeholder="Called" aria-label="What ${kitEsc(p.first)} calls it">
              <button class="pl-x" data-act="remove" data-i="${i}" aria-label="Delete this place">${iconHtml('mdi:close', { size: '18px' })}</button></div>`,
          )
          .join('');
        rows += `<button class="pl-add" data-act="add">${iconHtml('mdi:plus', { size: '18px' })}Add a place</button>
          <div class="pl-acts"><button class="pl-btn" data-act="cancel">Cancel</button><button class="pl-btn pl-save" data-act="save">${iconHtml('mdi:check', { size: '18px' })}Save</button></div>`;
      } else {
        rows = (p.places || [])
          .map((pl) => `<div class="pl-row"><div class="pl-ico">${icon(pl.name)}</div><div class="pl-fixed">${kitEsc(pl.name || zoneName(pl.zone))}<small>${kitEsc(zoneName(pl.zone))}</small></div></div>`)
          .join('') || '<div class="pl-none">No other places yet.</div>';
        if (admin) rows += `<div class="pl-acts"><button class="pl-btn" data-act="edit">${iconHtml('mdi:pencil', { size: '16px' })}Edit</button></div>`;
      }
      return `<div class="pl-person" data-person="${kitEsc(p.entity_id)}">${kitShell(
        `<div class="pl-row"><div class="pl-ico" style="color:#4caf50;">${iconHtml('mdi:home', { size: '18px' })}</div><div class="pl-fixed">Home<small>Automatic</small></div></div>${rows}`,
      )}</div>`;
    });
    this._grid.innerHTML = datalist + (people.length ? cards.join('') : '<div class="pl-none">Loading people…</div>');
    // Each person's card heading: their name and where they are now.
    this._grid.querySelectorAll('[data-person]').forEach((el) => {
      const p = people.find((x) => x.entity_id === el.dataset.person);
      kitHead(el, p.first || p.name, placeText(p.place, p.zone), /^home$/i.test(p.place) ? '#4caf50' : '#26a69a');
    });
    if (admin && people.length) {
      this._grid.insertAdjacentHTML('beforeend', `<div class="pl-foot" style="grid-column:1/-1;">Places are Home Assistant's zones. <a data-act="zones">Make or rename zones</a> in Settings › Areas, labels &amp; zones.</div>`);
    }
    hydrateIcons(this);
  }

  _click(ev) {
    const b = ev.target.closest('[data-act]');
    if (!b) return;
    const act = b.dataset.act;
    if (act === 'zones') return kitNavigate('/config/zone');
    const wrap = b.closest('[data-person]');
    if (!wrap || !this._admin()) return;
    const person = wrap.dataset.person;
    const p = this._data.find((x) => x.entity_id === person);
    if (act === 'edit') {
      this._drafts[person] = (p.places || []).map((x) => ({ ...x }));
    } else if (act === 'cancel') {
      delete this._drafts[person];
    } else if (act === 'save') {
      return this._save(person);
    } else if (act === 'add') {
      const d = this._drafts[person];
      const used = new Set(d.map((x) => x.zone));
      const free = this._zones().find(([id]) => !used.has(id));
      if (!free) return kitNavigate('/config/zone');
      d.push({ zone: free[0], name: 'Work' });
    } else if (act === 'remove') {
      this._drafts[person].splice(Number(b.dataset.i), 1);
    }
    this._sig = null;
    this._render();
  }

  getCardSize() {
    return 4;
  }

  getGridOptions() {
    return { columns: 'full', rows: 'auto' };
  }

  static getConfigElement() {
    return document.createElement(`places-card-editor${SUFFIX}`);
  }

  static getStubConfig() {
    return {};
  }
}

export function registerPlacesCard() {
  if (!customElements.get(`places-card-editor${SUFFIX}`)) customElements.define(`places-card-editor${SUFFIX}`, PlacesCardEditor);
  if (!customElements.get(`places-card${SUFFIX}`)) customElements.define(`places-card${SUFFIX}`, PlacesCard);
  window.customCards = window.customCards || [];
  window.customCards.push({
    type: `places-card${SUFFIX}`,
    name: `Places Card${LABEL}`,
    description: "Each person's places: the zones they go to and what they call them (Work, Gym…)",
    preview: true,
    documentationURL: 'https://github.com/J45PER/church-drive-cards#readme',
  });
}
