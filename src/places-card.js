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
// administrators can change them.

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
      // Don't throw away a row someone is part-way through adding.
      if (this._editing) return;
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

  async _save(person, places) {
    const p = (this._data || []).find((x) => x.entity_id === person);
    if (p) p.places = places;
    if (this.config.demo) return this._render();
    try {
      const r = await this._hass.connection.sendMessagePromise({ type: 'church_drive/people/places', person, places: places.filter((x) => x.zone) });
      this._data = r.people || this._data;
    } catch (err) {
      /* the sensor update will bring it back in line */
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
      this._grid.addEventListener('change', (ev) => this._edit(ev));
      this._grid.addEventListener('input', (ev) => {
        if (ev.target.matches('input')) {
          this._editing = true;
          clearTimeout(this._typing);
          this._typing = setTimeout(() => this._edit(ev), 900);
        }
      });
      this._grid.addEventListener('click', (ev) => {
        const b = ev.target.closest('[data-act]');
        if (!b) return;
        if (b.dataset.act === 'zones') return kitNavigate('/config/zone');
        const person = b.closest('[data-person]').dataset.person;
        const p = this._data.find((x) => x.entity_id === person);
        const places = [...(p.places || [])];
        if (b.dataset.act === 'add') {
          const used = new Set(places.map((x) => x.zone));
          const free = this._zones().find(([id]) => !used.has(id));
          if (!free) return kitNavigate('/config/zone');
          places.push({ zone: free[0], name: 'Work' });
          this._save(person, places);
        } else if (b.dataset.act === 'remove') {
          places.splice(Number(b.dataset.i), 1);
          this._save(person, places);
        }
      });
      this._built = true;
    }
    const people = this._data || [];
    const zones = this._zones();
    const admin = this._admin();
    const sig = JSON.stringify([people, zones, admin]);
    if (sig === this._sig) return;
    this._sig = sig;
    const zoneName = (id) => (zones.find((z) => z[0] === id) || [id, id.replace(/^zone\./, '').replace(/_/g, ' ')])[1];
    const datalist = `<datalist id="pl-names${SUFFIX}">${SUGGEST.map((n) => `<option value="${n}">`).join('')}</datalist>`;
    this._grid.innerHTML =
      datalist +
      (people.length
        ? people
            .map((p) => {
              const rows = (p.places || [])
                .map((pl, i) =>
                  admin
                    ? `<div class="pl-row"><div class="pl-ico">${iconHtml(/^work$/i.test(pl.name) ? 'mdi:briefcase-outline' : 'mdi:map-marker-outline', { size: '18px' })}</div>
                        <select data-i="${i}" aria-label="Zone">${zones.map(([id, n]) => `<option value="${kitEsc(id)}"${id === pl.zone ? ' selected' : ''}>${kitEsc(n)}</option>`).join('')}${zones.some((z) => z[0] === pl.zone) ? '' : `<option value="${kitEsc(pl.zone)}" selected>${kitEsc(zoneName(pl.zone))} (gone)</option>`}</select>
                        <input data-i="${i}" list="pl-names${SUFFIX}" value="${kitEsc(pl.name)}" placeholder="Called" aria-label="What ${kitEsc(p.first)} calls it">
                        <button class="pl-x" data-act="remove" data-i="${i}" aria-label="Remove">${iconHtml('mdi:close', { size: '18px' })}</button></div>`
                    : `<div class="pl-row"><div class="pl-ico">${iconHtml('mdi:map-marker-outline', { size: '18px' })}</div><div class="pl-fixed">${kitEsc(pl.name || zoneName(pl.zone))}<small>${kitEsc(zoneName(pl.zone))}</small></div></div>`,
                )
                .join('');
              return `<div class="pl-person" data-person="${kitEsc(p.entity_id)}">${kitShell(
                `<div class="pl-row"><div class="pl-ico" style="color:#4caf50;">${iconHtml('mdi:home', { size: '18px' })}</div><div class="pl-fixed">Home<small>Automatic</small></div></div>
                ${rows || (admin ? '' : '<div class="pl-none">No other places yet.</div>')}
                ${admin ? `<button class="pl-add" data-act="add">${iconHtml('mdi:plus', { size: '18px' })}Add a place</button>` : ''}`,
              )}</div>`;
            })
            .join('')
        : '<div class="pl-none">Loading people…</div>');
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

  // A zone picked or a name typed: save that person's places.
  _edit(ev) {
    const row = ev.target.closest('.pl-row');
    const wrap = ev.target.closest('[data-person]');
    if (!row || !wrap || !ev.target.matches('select, input')) return;
    const person = wrap.dataset.person;
    const p = this._data.find((x) => x.entity_id === person);
    const i = Number(ev.target.dataset.i);
    const places = (p.places || []).map((x) => ({ ...x }));
    if (!places[i]) return;
    if (ev.target.matches('select')) places[i].zone = ev.target.value;
    else places[i].name = ev.target.value.trim();
    clearTimeout(this._typing);
    this._editing = false;
    this._save(person, places);
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
