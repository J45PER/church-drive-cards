// Notifications Card (Manager): who gets each kind of notification, and
// which of each person's phones they go to.
//
// A table with the kinds down the side (grouped: Safety, Security, ...) and
// the house's people across the top, plus an "All" column: ticked, it goes
// to everyone, including people added later. Under it, each person's phones
// as chips (tap to stop or start notifications on that phone).
//
// Everything comes from the Church Drive integration (church_drive/people),
// which picks people up from Home Assistant's own people. A kind whose
// devices aren't set up yet (e.g. the Zappi) shows as waiting. Admin-only
// kinds (costs) can't be ticked for people who aren't administrators. Only
// administrators can change anything; everyone else sees it read-only.

import { createFormEditor } from './form-editor.js';
import { iconHtml, hydrateIcons } from './icons.js';
import { SUFFIX, LABEL } from './suffix.js';
import { kitShell, kitHead, kitEsc } from './card-kit.js';

const SHOW = [
  { value: 'all', label: 'Notifications, house jobs and phones' },
  { value: 'notifications', label: 'Notifications only' },
  { value: 'jobs', label: 'House jobs only' },
  { value: 'phones', label: 'Phones only' },
];

const NC_CSS = `
  .nc-wrap { overflow-x:auto; margin:0 -4px; padding:0 4px; }
  .nc-table { border-collapse:collapse; width:100%; font-size:0.85rem; }
  .nc-table th { color:var(--secondary-text-color); font-weight:600; font-size:0.75rem; padding:4px 2px 6px; text-align:center; white-space:nowrap; }
  .nc-table th.nc-kind, .nc-table td.nc-kind { text-align:left; padding-left:0; }
  .nc-table td { padding:6px 2px; border-top:1px solid var(--divider-color, rgba(127,127,127,0.18)); text-align:center; vertical-align:middle; }
  .nc-table tr.nc-group td { border-top:none; padding:14px 0 4px; font-size:0.7rem; letter-spacing:0.07em; text-transform:uppercase; font-weight:700; color:var(--secondary-text-color); text-align:left; }
  .nc-table tr.nc-group:first-child td { padding-top:2px; }
  .nc-name { font-weight:500; line-height:1.25; }
  .nc-note { display:block; font-size:0.72rem; color:var(--secondary-text-color); }
  .nc-crit { display:inline-flex; align-items:center; margin-left:4px; color:#ef5350; vertical-align:-2px; }
  .nc-tick { width:26px; height:26px; padding:0; border:none; border-radius:8px; background:rgba(127,127,127,0.18); color:transparent; cursor:pointer; display:inline-flex; align-items:center; justify-content:center; }
  .nc-tick[aria-pressed="true"] { color:#fff; }
  .nc-tick.nc-all { border-radius:50%; }
  .nc-tick:disabled { cursor:default; }
  .nc-tick:focus-visible, .nc-chip:focus-visible { outline:2px solid var(--primary-color); outline-offset:2px; }
  .nc-dash { color:var(--secondary-text-color); opacity:.5; }
  .nc-wait { font-size:0.78rem; font-style:italic; color:var(--secondary-text-color); text-align:left !important; }
  .nc-phones { display:flex; flex-direction:column; gap:10px; }
  .nc-person { display:flex; flex-direction:column; gap:6px; }
  .nc-person-name { font-weight:600; font-size:0.9rem; display:flex; align-items:center; gap:6px; }
  .nc-badge { font-size:0.68rem; font-weight:700; padding:1px 6px; border-radius:6px; background:rgba(127,127,127,0.18); color:var(--secondary-text-color); }
  .nc-chips { display:flex; flex-wrap:wrap; gap:6px; }
  .nc-chip { border:none; border-radius:999px; padding:6px 11px; font:inherit; font-size:0.8rem; font-weight:600; cursor:pointer; background:rgba(127,127,127,0.18); color:var(--primary-text-color); display:inline-flex; align-items:center; gap:5px; }
  .nc-chip[aria-pressed="true"] { color:#fff; }
  .nc-chip:disabled { cursor:default; }
  .nc-h { font-size:1rem; font-weight:600; margin-top:4px; }
  .nc-msg { font-size:0.8rem; color:#ffa726; }
`;

// Pretend data for Design Presets.
function ncDemo() {
  const people = [
    { entity_id: 'person.jamie', first: 'Jamie', name: 'Jamie', admin: true, home: 'home', phones: [
      { service: 'a', name: 'iPhone', on: true }, { service: 'b', name: 'Pixel 10 Pro XL', on: true }, { service: 'c', name: 'iPad', on: false }, { service: 'd', name: 'Watch', on: false }] },
    { entity_id: 'person.hayley', first: 'Hayley', name: 'Hayley', admin: true, home: 'not_home', phones: [{ service: 'e', name: 'Pixel 9', on: true }] },
    { entity_id: 'person.diane', first: 'Diane', name: 'Diane', admin: false, home: 'home', phones: [{ service: 'f', name: 'Phone', on: true }] },
    { entity_id: 'person.ian', first: 'Ian', name: 'Ian', admin: false, home: 'home', phones: [{ service: 'g', name: 'Phone', on: true }] },
  ];
  const k = (group, key, name, extra = {}) => ({ group, key, name, note: '', critical: false, admin_only: false, available: true, all: false, people: ['person.jamie'], ...extra });
  const kinds = [
    k('Safety', 'smoke', 'Smoke alarm', { critical: true, all: true, people: [] }),
    k('Safety', 'co', 'Carbon monoxide', { critical: true, all: true, people: [] }),
    k('Security', 'doorbell', 'Doorbell pressed', { note: 'With a photo', people: ['person.jamie', 'person.hayley'] }),
    k('Security', 'door_left_open', 'Door left open', { note: '10 minutes' }),
    k('Energy', 'cheap_rate', 'Cheap rate started', { all: true, people: [] }),
    k('Energy', 'energy_cost', "Yesterday's energy cost", { admin_only: true, people: ['person.jamie', 'person.hayley'] }),
    k('Car', 'car_charged', 'Car charged', { available: false }),
    k('House jobs', 'low_batteries', 'Low batteries', { people: ['person.jamie', 'person.hayley'] }),
  ];
  return { people, kinds };
}

export const NotificationsCardEditor = createFormEditor({
  schema: () => [
    { name: 'title', selector: { text: {} } },
    { name: 'show', selector: { select: { mode: 'dropdown', options: SHOW } } },
    { name: 'color', selector: { ui_color: {} } },
    { name: 'demo', selector: { boolean: {} } },
  ],
  labels: {
    title: 'Title (optional)',
    show: 'Show',
    color: 'Colour',
    demo: 'Show pretend people (for Design Presets; changes are switched off)',
  },
  helpers: {
    show: 'People are picked up from Home Assistant (Settings > People), and their phones from the companion app.',
    color: 'Default purple (#7e57c2).',
  },
});

export class NotificationsCard extends HTMLElement {
  setConfig(config) {
    this.config = config || {};
    this._built = false;
    this._data = this.config.demo ? ncDemo() : null;
    this._sig = null;
  }

  set hass(hass) {
    this._hass = hass;
    if (!this.config.demo) {
      const st = hass.states['sensor.church_drive_people'];
      const rev = st ? `${st.attributes.rev}|${st.last_updated}` : 'none';
      if (rev !== this._rev) {
        this._rev = rev;
        this._load();
      }
    }
    this._render();
  }

  async _load() {
    try {
      this._data = await this._hass.connection.sendMessagePromise({ type: 'church_drive/people' });
      this._error = '';
    } catch (err) {
      this._error = "Church Drive's people and notifications aren't running. Update Church Drive and restart Home Assistant.";
    }
    this._sig = null;
    this._render();
  }

  _colour() {
    return this.config.color || '#7e57c2';
  }

  _canEdit() {
    return !!(this.config.demo || (this._hass && this._hass.user && this._hass.user.is_admin));
  }

  async _assign(kind, person, on) {
    if (this.config.demo) {
      const k = this._data.kinds.find((x) => x.key === kind);
      if (!person) {
        if (!on && k.all) k.people = this._data.people.map((p) => p.entity_id);
        k.all = on;
        if (on) k.people = [];
      } else {
        if (k.all) {
          k.all = false;
          k.people = this._data.people.map((p) => p.entity_id);
        }
        k.people = k.people.filter((x) => x !== person).concat(on ? [person] : []);
      }
    } else {
      try {
        const r = await this._hass.connection.sendMessagePromise({ type: 'church_drive/people/assign', kind, ...(person ? { person } : {}), on });
        this._data = { ...this._data, kinds: r.kinds };
      } catch (err) {
        this._msg = `Couldn't save: ${(err && err.message) || err}`;
      }
    }
    this._sig = null;
    this._render();
  }

  async _phone(person, service, on) {
    if (this.config.demo) {
      const p = this._data.people.find((x) => x.entity_id === person);
      p.phones.find((f) => f.service === service).on = on;
    } else {
      try {
        const r = await this._hass.connection.sendMessagePromise({ type: 'church_drive/people/phone', person, service, on });
        this._data = { ...this._data, people: r.people };
      } catch (err) {
        this._msg = `Couldn't save: ${(err && err.message) || err}`;
      }
    }
    this._sig = null;
    this._render();
  }

  _render() {
    if (!this._hass && !this.config.demo) return;
    const c = this.config;
    const colour = this._colour();
    if (!this._built) {
      this.innerHTML = kitShell(`<div class="nc-body" style="display:flex; flex-direction:column; gap:12px;"></div>`, NC_CSS);
      this._body = this.querySelector('.nc-body');
      this._body.addEventListener('click', (ev) => {
        const tick = ev.target.closest('[data-kind]');
        if (tick && !tick.disabled) return this._assign(tick.dataset.kind, tick.dataset.person || null, tick.getAttribute('aria-pressed') !== 'true');
        const chip = ev.target.closest('[data-phone]');
        if (chip && !chip.disabled) return this._phone(chip.dataset.person, chip.dataset.phone, chip.getAttribute('aria-pressed') !== 'true');
        return undefined;
      });
      this._built = true;
    }
    this.querySelector('.ck-headrow').style.display = c.title ? '' : 'none';
    if (c.title) kitHead(this, c.title, '', colour);
    const sig = JSON.stringify([this._data, this._error, this._msg, colour, c.show, this._canEdit()]);
    if (sig === this._sig) return;
    this._sig = sig;
    if (this._error) {
      this._body.innerHTML = `<div class="ck-sub" style="line-height:1.5;">${kitEsc(this._error)}</div>`;
      return;
    }
    if (!this._data) {
      this._body.innerHTML = `<div class="ck-sub">Loading…</div>`;
      return;
    }
    const show = c.show || 'all';
    const edit = this._canEdit();
    const people = this._data.people || [];
    const on = `background:${colour};`;
    // A person's box while All is ticked: ticked but grey (it's All that sends it);
    // tapping it unticks that person and All, leaving everyone else ticked.
    const viaAll = 'background:rgba(127,127,127,0.42); color:rgba(255,255,255,0.85);';
    const tick = (kind, person, pressed, enabled, all, grey = false) =>
      `<button type="button" class="nc-tick${all ? ' nc-all' : ''}" data-kind="${kitEsc(kind.key)}"${person ? ` data-person="${kitEsc(person.entity_id)}"` : ''} aria-pressed="${pressed}" ${enabled && edit ? '' : 'disabled'}
        aria-label="${kitEsc(kind.name)}: ${person ? kitEsc(person.first) : 'everyone'}${grey ? ' (through All)' : ''}" title="${grey ? 'Ticked through All. Tap to untick this person and All.' : ''}" style="${grey ? viaAll : pressed ? on : ''}${enabled ? '' : 'opacity:.45;'}">${iconHtml('mdi:check', { size: '16px' })}</button>`;
    const kinds = (this._data.kinds || []).filter((k) => (show === 'jobs' ? k.group === 'House jobs' : show === 'notifications' ? k.group !== 'House jobs' : true));
    let html = '';
    if (show !== 'phones') {
      const groups = [...new Set(kinds.map((k) => k.group))];
      const cols = people.length + 2;
      html += `<div class="nc-wrap"><table class="nc-table"><thead><tr><th class="nc-kind"></th><th title="Everyone, including people added later">All</th>${people
        .map((p) => `<th>${kitEsc(p.first)}</th>`)
        .join('')}</tr></thead><tbody>`;
      for (const g of groups) {
        html += `<tr class="nc-group"><td colspan="${cols}">${kitEsc(g === 'House jobs' ? 'House jobs (make a to-do)' : g)}</td></tr>`;
        for (const k of kinds.filter((x) => x.group === g)) {
          const name = `<td class="nc-kind"><span class="nc-name">${kitEsc(k.name)}${k.critical ? `<span class="nc-crit" title="Sounds even on silent">${iconHtml('mdi:alarm-light', { size: '14px' })}</span>` : ''}</span>${
            k.admin_only ? '<span class="nc-note">Admins only</span>' : k.note ? `<span class="nc-note">${kitEsc(k.note)}</span>` : ''
          }</td>`;
          if (!k.available) {
            html += `<tr>${name}<td colspan="${cols - 1}" class="nc-wait">${k.group === 'Car' ? 'Waiting for the Zappi to be set up' : 'Waiting for its devices to be set up'}</td></tr>`;
            continue;
          }
          const cells = people
            .map((p) => {
              const allowed = !k.admin_only || p.admin;
              if (!allowed) return `<td><span class="nc-dash" title="Admins only">–</span></td>`;
              return `<td>${tick(k, p, k.all || k.people.includes(p.entity_id), true, false, k.all)}</td>`;
            })
            .join('');
          html += `<tr>${name}<td>${k.admin_only ? '<span class="nc-dash">–</span>' : tick(k, null, k.all, true, true)}</td>${cells}</tr>`;
        }
      }
      html += `</tbody></table></div>`;
    }
    if (show === 'all' || show === 'phones') {
      html += `${show === 'all' ? '<div class="nc-h">Phones</div>' : ''}<div class="nc-phones">${people
        .map(
          (p) => `<div class="nc-person"><div class="nc-person-name">${kitEsc(p.name)}${p.admin ? '<span class="nc-badge">Admin</span>' : ''}</div><div class="nc-chips">${
            p.phones && p.phones.length
              ? p.phones
                  .map(
                    (f) =>
                      `<button type="button" class="nc-chip" data-person="${kitEsc(p.entity_id)}" data-phone="${kitEsc(f.service)}" aria-pressed="${!!f.on}" ${edit ? '' : 'disabled'} style="${f.on ? on : ''}">${iconHtml(
                        /ipad|tab/i.test(f.name) ? 'mdi:tablet' : /watch/i.test(f.name) ? 'mdi:watch' : 'mdi:cellphone',
                        { size: '15px' }
                      )}${kitEsc(f.name)}</button>`
                  )
                  .join('')
              : '<span class="ck-sub">No companion app yet, so no notifications</span>'
          }</div></div>`
        )
        .join('')}</div>`;
    }
    if (!edit) html += `<div class="ck-sub">Only administrators can change these.</div>`;
    if (this._msg) html += `<div class="nc-msg" role="status">${kitEsc(this._msg)}</div>`;
    this._body.innerHTML = html;
    hydrateIcons(this);
  }

  getCardSize() {
    return 8;
  }

  getGridOptions() {
    return { columns: 'full', rows: 'auto' };
  }

  static getConfigElement() {
    return document.createElement(`notifications-card-editor${SUFFIX}`);
  }

  static getStubConfig() {
    return { title: 'Notifications' };
  }
}

export function registerNotificationsCard() {
  if (!customElements.get(`notifications-card-editor${SUFFIX}`)) customElements.define(`notifications-card-editor${SUFFIX}`, NotificationsCardEditor);
  if (!customElements.get(`notifications-card${SUFFIX}`)) customElements.define(`notifications-card${SUFFIX}`, NotificationsCard);
  window.customCards = window.customCards || [];
  window.customCards.push({
    type: `notifications-card${SUFFIX}`,
    name: `Notifications Card${LABEL}`,
    description: 'Who gets each kind of notification and house job, and which phones they go to (from Home Assistant people)',
    preview: true,
    documentationURL: 'https://github.com/J45PER/church-drive-cards#readme',
  });
}
