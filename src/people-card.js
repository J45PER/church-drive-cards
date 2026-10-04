// People Card ("Who's home"): everyone in Home Assistant's people, home or
// away (or the zone they're in), and each of their phones' battery. People
// and phones are found by themselves, so someone added later appears too.
// A person whose phone doesn't share its location shows as Unknown. In a zone
// they've named in Manager's Locations (Work, Gym), it says so: "At work ·
// Ashfield School".

import { createFormEditor } from './form-editor.js';
import { iconHtml, hydrateIcons } from './icons.js';
import { SUFFIX, LABEL } from './suffix.js';
import { kitShell, kitHead, kitEsc } from './card-kit.js';
import { placeText } from './places-card.js';
import { ageText } from './people-manager-card.js';

const PC_CSS = `
  .pc-row { display:flex; align-items:center; gap:10px; padding:7px 2px; cursor:pointer; }
  .pc-row + .pc-row { border-top:1px solid var(--divider-color, rgba(127,127,127,0.18)); }
  .pc-av { flex:none; width:38px; height:38px; border-radius:50%; display:flex; align-items:center; justify-content:center; font-weight:700; background:rgba(127,127,127,0.18) center/cover no-repeat; }
  .pc-body { flex:1; min-width:0; }
  .pc-name { font-weight:600; font-size:0.92rem; }
  .pc-sub { font-size:0.78rem; color:var(--secondary-text-color); white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
  .pc-pill { flex:none; max-width:55%; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; border-radius:999px; padding:2px 9px; font-size:0.72rem; font-weight:700; }
`;

function pcDemo() {
  return [
    { id: 'person.jamie', name: 'Jamie', state: 'Ashfield School', place: 'Work', zone: 'Ashfield School', phones: [['iPhone', 82], ['Pixel', 64]] },
    { id: 'person.hayley', name: 'Hayley', state: 'not_home', phones: [['Pixel 9', 47]] },
    { id: 'person.diane', name: 'Diane', state: 'unknown', phones: [['Phone', null]] },
    { id: 'person.ian', name: 'Ian', state: 'home', phones: [['Phone', 91]] },
  ];
}

export const PeopleCardEditor = createFormEditor({
  schema: () => [
    { name: 'title', selector: { text: {} } },
    { name: 'color', selector: { ui_color: {} } },
    { name: 'demo', selector: { boolean: {} } },
  ],
  labels: { title: 'Title (optional)', color: 'Colour', demo: 'Show pretend people (for Design Presets)' },
  helpers: { title: "Everyone in Settings > People, and their companion-app phones' batteries." },
});

export class PeopleCard extends HTMLElement {
  setConfig(config) {
    this.config = config || {};
    this._built = false;
    this._sig = null;
  }

  set hass(hass) {
    this._hass = hass;
    this._render();
  }

  _people() {
    if (this.config.demo) return pcDemo();
    const s = this._hass.states;
    const reg = this._hass.entities || {};
    const devices = this._hass.devices || {};
    const known = ((s['sensor.church_drive_people'] || {}).attributes || {}).people || [];
    return Object.keys(s)
      .filter((id) => id.startsWith('person.'))
      .sort((a, b) => String(s[a].attributes.friendly_name).localeCompare(String(s[b].attributes.friendly_name)))
      .map((id) => {
        const st = s[id];
        const phones = (st.attributes.device_trackers || [])
          .map((t) => reg[t])
          .filter((e) => e && e.platform === 'mobile_app' && e.device_id)
          .map((e) => {
            const dev = devices[e.device_id] || {};
            const bat = Object.keys(reg).find((x) => reg[x].device_id === e.device_id && x.startsWith('sensor.') && /battery_level$/.test(x) && s[x]);
            const v = bat ? Number(s[bat].state) : NaN;
            return [dev.name_by_user || dev.model || dev.name || 'Phone', isNaN(v) ? null : Math.round(v)];
          });
        const k = known.find((p) => p.entity_id === id) || {};
        return { id, name: st.attributes.friendly_name || id, state: st.state, place: k.place, zone: k.zone, picture: st.attributes.entity_picture, phones, stale: k.stale ? k.located : null };
      });
  }

  _pill(state, place, zone) {
    if (state === 'home') return ['Home', '#4caf50'];
    if (place && zone) return [placeText(place, zone), '#26a69a']; // a named zone, e.g. At work · Ashfield School
    if (state === 'not_home') return ['Away', '#9aa0ad'];
    if (state === 'unknown' || state === 'unavailable') return ['Unknown', '#ffa726'];
    return [state, '#26a69a']; // a named zone, e.g. Work
  }

  _render() {
    if (!this._hass && !this.config.demo) return;
    const c = this.config;
    const colour = c.color || '#26a69a';
    if (!this._built) {
      this.innerHTML = kitShell(`<div class="pc-list"></div>`, PC_CSS);
      this._list = this.querySelector('.pc-list');
      this._list.addEventListener('click', (ev) => {
        const row = ev.target.closest('[data-id]');
        if (row && !c.demo) this.dispatchEvent(new CustomEvent('hass-more-info', { detail: { entityId: row.dataset.id }, bubbles: true, composed: true }));
      });
      this._built = true;
    }
    const people = this._people();
    const home = people.filter((p) => p.state === 'home').length;
    this.querySelector('.ck-headrow').style.display = c.title ? '' : 'none';
    if (c.title) kitHead(this, c.title, `${home} of ${people.length} home`, colour);
    const sig = JSON.stringify(people);
    if (sig === this._sig) return;
    this._sig = sig;
    this._list.innerHTML = people
      .map((p) => {
        const [word, col] = this._pill(p.state, p.place, p.zone);
        const phones = p.phones.length
          ? p.phones.map(([n, b]) => `${n}${b != null ? ` ${b}%` : ''}`).join(' · ') + (p.state === 'unknown' ? ' · location not shared' : p.stale ? ` · location ${ageText(p.stale).replace(/ ago$/, '')} old` : '')
          : 'No companion app';
        return `<div class="pc-row" data-id="${kitEsc(p.id)}" role="button" tabindex="0" aria-label="${kitEsc(p.name)}: ${kitEsc(word)}">
          <div class="pc-av" style="${p.picture ? `background-image:url('${kitEsc(p.picture)}');` : `color:${colour};`}">${p.picture ? '' : kitEsc(String(p.name).charAt(0))}</div>
          <div class="pc-body"><div class="pc-name">${kitEsc(p.name)}</div><div class="pc-sub">${kitEsc(phones)}</div></div>
          <span class="pc-pill" style="background:color-mix(in srgb, ${col} 22%, transparent); color:${col};">${kitEsc(word)}</span>
        </div>`;
      })
      .join('');
    hydrateIcons(this);
  }

  getCardSize() {
    return 3;
  }

  getGridOptions() {
    return { columns: 'full', rows: 'auto' };
  }

  static getConfigElement() {
    return document.createElement(`people-card-editor${SUFFIX}`);
  }

  static getStubConfig() {
    return { title: "Who's home" };
  }
}

export function registerPeopleCard() {
  if (!customElements.get(`people-card-editor${SUFFIX}`)) customElements.define(`people-card-editor${SUFFIX}`, PeopleCardEditor);
  if (!customElements.get(`people-card${SUFFIX}`)) customElements.define(`people-card${SUFFIX}`, PeopleCard);
  window.customCards = window.customCards || [];
  window.customCards.push({
    type: `people-card${SUFFIX}`,
    name: `People Card${LABEL}`,
    description: "Who's home: everyone home or away, and their phones' batteries",
    preview: true,
    documentationURL: 'https://github.com/J45PER/church-drive-cards#readme',
  });
}
