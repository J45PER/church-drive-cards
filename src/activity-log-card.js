// Activity Log Card (Manager, administrators only): what people and the assistant did in the house, and when people came and went.
//
// Lines come from the Church Drive integration (activity.py, `church_drive/activity`): a person's own switching from the app or a
// dashboard, what the assistant did (and who for), and arrivals and departures. Filter by day (today, yesterday, 7 or 30 days, or
// pick dates), by time of day (morning, afternoon, evening, night, or pick times; a window may run over midnight), by kind and by
// person. New lines appear by themselves. The house keeps 30 days.

import { createFormEditor } from './form-editor.js';
import { iconHtml, hydrateIcons } from './icons.js';
import { SUFFIX, LABEL } from './suffix.js';
import { kitShell, kitHead, kitEsc } from './card-kit.js';
import { DAYS, TIMES, KINDS, kindLabel, defaultFilters, buildQuery, toggleKind, groupByDay, lineParts, timeText, summary, dayText } from './activity-logic.js';

const EVENT = 'church_drive_activity_changed';
const PAGE = 100;

const AL_CSS = `
  .al-filters { display:flex; flex-direction:column; gap:8px; }
  .al-group { display:flex; flex-wrap:wrap; align-items:center; gap:6px; }
  .al-gh { flex:none; width:64px; font-size:0.7rem; letter-spacing:0.07em; text-transform:uppercase; font-weight:700; color:var(--secondary-text-color); }
  .al-chip { border:none; border-radius:999px; padding:5px 11px; font:inherit; font-size:0.78rem; font-weight:600; cursor:pointer; background:rgba(127,127,127,0.18); color:var(--primary-text-color); }
  .al-chip[aria-pressed="true"] { color:#fff; }
  .al-chip:focus-visible, .al-more:focus-visible, .al-in:focus-visible { outline:2px solid var(--primary-color); outline-offset:2px; }
  .al-in { border:none; border-radius:10px; padding:5px 8px; font:inherit; font-size:0.8rem; background:rgba(127,127,127,0.18); color:var(--primary-text-color); color-scheme:dark light; min-width:0; }
  .al-sum { font-size:0.8rem; color:var(--secondary-text-color); }
  .al-day { margin-top:6px; font-size:0.72rem; letter-spacing:0.07em; text-transform:uppercase; font-weight:700; color:var(--secondary-text-color); }
  .al-line { display:flex; align-items:flex-start; gap:10px; padding:7px 0; border-top:1px solid var(--divider-color, rgba(127,127,127,0.18)); }
  .al-day + .al-line { border-top:none; }
  .al-time { flex:none; width:44px; font-size:0.8rem; font-variant-numeric:tabular-nums; color:var(--secondary-text-color); padding-top:2px; }
  .al-ic { flex:none; width:28px; height:28px; border-radius:50%; display:flex; align-items:center; justify-content:center; color:#fff; }
  .al-txt { flex:1; min-width:0; font-size:0.88rem; line-height:1.35; overflow-wrap:anywhere; }
  .al-txt b { font-weight:700; }
  .al-txt small { color:var(--secondary-text-color); margin-left:6px; font-size:0.76rem; }
  .al-more { align-self:center; border:none; border-radius:10px; padding:8px 16px; font:inherit; font-size:0.82rem; font-weight:600; cursor:pointer; background:rgba(127,127,127,0.18); color:var(--primary-text-color); }
  .al-msg { font-size:0.85rem; color:var(--secondary-text-color); line-height:1.5; padding:6px 0; }
`;

const kindOf = (id) => KINDS.find((k) => k.id === id);

function alDemo() {
  const t = (h, m) => new Date(new Date().setHours(h, m, 0, 0)).getTime() / 1000;
  return {
    assistant: 'Clive',
    people: ['Hayley', 'Ian', 'Jamie'],
    oldest: t(0, 0) - 86400 * 9,
    entries: [
      { ts: t(8, 12), kind: 'presence', who: 'Ian', text: 'left home', icon: 'mdi:exit-run' },
      { ts: t(7, 58), kind: 'user', who: 'Jamie', text: 'armed (away) Alarm', icon: 'mdi:shield-home' },
      { ts: t(7, 41), kind: 'clive', who: '', text: 'turned off Downstairs', icon: 'mdi:thermostat', by: 'Hayley' },
      { ts: t(7, 20), kind: 'clive_auto', who: '', text: 'turned on Downstairs', icon: 'mdi:thermostat', rule: 'take over again when a pause ends' },
      { ts: t(7, 5), kind: 'user', who: 'Hayley', text: 'turned on Kitchen at 60%', icon: 'mdi:lightbulb' },
      { ts: t(6, 30), kind: 'presence', who: 'Hayley', text: 'arrived home', icon: 'mdi:map-marker-account' },
    ],
  };
}

export const ActivityLogCardEditor = createFormEditor({
  schema: () => [
    { name: 'title', selector: { text: {} } },
    { name: 'color', selector: { ui_color: {} } },
    { name: 'demo', selector: { boolean: {} } },
  ],
  labels: { title: 'Title (optional)', color: 'Colour', demo: 'Show pretend lines (for Design Presets)' },
  helpers: { color: 'Default teal (#26a69a).' },
});

export class ActivityLogCard extends HTMLElement {
  setConfig(config) {
    this.config = config || {};
    this._built = false;
    this._sig = null;
    this._filters = this._filters || defaultFilters();
    this._demo = this.config.demo ? alDemo() : null;
    this._data = this._demo ? { ...this._demo, total: this._demo.entries.length } : this._data || null;
  }

  set hass(hass) {
    this._hass = hass;
    if (!this.config.demo && this._canSee()) {
      if (!this._watching && hass.connection && hass.connection.subscribeEvents) {
        this._watching = true;
        Promise.resolve(hass.connection.subscribeEvents(() => this._soon(), EVENT))
          .then((unsub) => {
            this._unsub = unsub;
          })
          .catch(() => {
            this._watching = false;
          });
      }
      if (!this._loaded) {
        this._loaded = true;
        this._load();
      }
    }
    this._render();
  }

  disconnectedCallback() {
    if (this._unsub) this._unsub();
    this._unsub = null;
    this._watching = false;
    clearTimeout(this._timer);
  }

  _canSee() {
    return !!(this.config.demo || (this._hass && this._hass.user && this._hass.user.is_admin));
  }

  _colour() {
    return this.config.color || '#26a69a';
  }

  // New lines can arrive in bursts (a slider, a script's steps): ask once they settle.
  _soon() {
    clearTimeout(this._timer);
    this._timer = setTimeout(() => this._load(), 1200);
  }

  async _load(more = false) {
    if (this.config.demo) return;
    const asked = (this._ask = (this._ask || 0) + 1);
    const offset = more && this._data ? this._data.entries.length : 0;
    try {
      const got = await this._hass.connection.sendMessagePromise(buildQuery(this._filters, new Date(), PAGE, offset));
      if (asked !== this._ask) return; // a newer question is on its way
      this._data = more && this._data ? { ...got, entries: [...this._data.entries, ...got.entries] } : got;
      this._error = '';
    } catch (err) {
      if (asked !== this._ask) return;
      this._error = err && err.code === 'unauthorized' ? 'Only administrators can see the activity log.' : "Church Drive's activity log isn't running. Update Church Drive and restart Home Assistant.";
    }
    this._sig = null;
    this._render();
  }

  _set(next) {
    this._filters = next;
    this._sig = null;
    if (!this.config.demo) this._load();
    this._render();
  }

  _build() {
    this.innerHTML = kitShell(`<div class="al-filters"></div><div class="al-sum" role="status"></div><div class="al-list"></div>`, AL_CSS);
    this.addEventListener('click', (ev) => {
      const b = ev.target.closest('[data-act]');
      if (!b) return;
      const [act, v] = b.dataset.act.split('|');
      const f = this._filters;
      if (act === 'day') this._set({ ...f, day: v, ...(v === 'custom' && !f.from ? { from: dayText(new Date()) } : {}) });
      else if (act === 'time') this._set({ ...f, time: v, ...(v === 'custom' && !f.start ? { start: '08:00', end: '18:00' } : {}) });
      else if (act === 'kind') this._set(toggleKind(f, v));
      else if (act === 'who') this._set({ ...f, who: f.who === v ? '' : v });
      else if (act === 'more') this._load(true);
    });
    this.addEventListener('change', (ev) => {
      const i = ev.target.closest('[data-in]');
      if (i) this._set({ ...this._filters, [i.dataset.in]: i.value });
    });
    this._built = true;
  }

  _chips(act, list, current, colour, label = (x) => x.label) {
    return list.map((x) => `<button type="button" class="al-chip" data-act="${act}|${kitEsc(x.id)}" aria-pressed="${current(x)}" style="${current(x) ? `background:${x.colour || colour};` : ''}">${kitEsc(label(x))}</button>`).join('');
  }

  _filtersHtml(colour) {
    const f = this._filters;
    const name = (this._data && this._data.assistant) || 'Clive';
    const dates = f.day === 'custom'
      ? `<input type="date" class="al-in" data-in="from" value="${kitEsc(f.from)}" aria-label="From day"> to <input type="date" class="al-in" data-in="to" value="${kitEsc(f.to)}" aria-label="To day">`
      : '';
    const times = f.time === 'custom'
      ? `<input type="time" class="al-in" data-in="start" value="${kitEsc(f.start)}" aria-label="From time"> to <input type="time" class="al-in" data-in="end" value="${kitEsc(f.end)}" aria-label="To time">`
      : '';
    const people = ((this._data && this._data.people) || []).map((p) => ({ id: p, label: p }));
    return `
      <div class="al-group"><span class="al-gh">Day</span>${this._chips('day', DAYS, (x) => f.day === x.id, colour)}${dates}</div>
      <div class="al-group"><span class="al-gh">Time</span>${this._chips('time', TIMES, (x) => f.time === x.id, colour)}${times}</div>
      <div class="al-group"><span class="al-gh">Show</span>${this._chips('kind', KINDS, (x) => f.kinds.includes(x.id), colour, (x) => kindLabel(x, name))}</div>
      ${people.length ? `<div class="al-group"><span class="al-gh">Who</span>${this._chips('who', people, (x) => f.who === x.id, colour)}</div>` : ''}`;
  }

  _lineHtml(e) {
    const kind = kindOf(e.kind) || KINDS[0];
    const p = lineParts(e, this._data && this._data.assistant);
    return `<div class="al-line"><div class="al-time">${kitEsc(timeText(e.ts))}</div>
      <div class="al-ic" style="background:${kind.colour};">${iconHtml(e.icon || kind.icon, { size: '16px' })}</div>
      <div class="al-txt"><b>${kitEsc(p.who)}</b> ${kitEsc(p.text)}${p.note ? `<small>${kitEsc(p.note)}</small>` : ''}</div></div>`;
  }

  _render() {
    if (!this._hass && !this.config.demo) return;
    if (!this._built) this._build();
    const colour = this._colour();
    const head = this.querySelector('.ck-headrow');
    if (head) {
      head.style.display = this.config.title ? '' : 'none';
      if (this.config.title) kitHead(this, this.config.title, '', colour);
    }
    const sig = JSON.stringify([this._filters, this._data, this._error, colour, this._canSee()]);
    if (sig === this._sig) return;
    this._sig = sig;
    const filters = this.querySelector('.al-filters');
    const sum = this.querySelector('.al-sum');
    const list = this.querySelector('.al-list');
    if (!this._canSee()) {
      filters.innerHTML = '';
      sum.textContent = '';
      list.innerHTML = '<div class="al-msg">Only administrators can see the activity log.</div>';
      return;
    }
    // Keep a date or time box's focus out of the rebuild: the filters only change when a choice does.
    filters.innerHTML = this._filtersHtml(colour);
    if (this._error) {
      sum.textContent = '';
      list.innerHTML = `<div class="al-msg">${kitEsc(this._error)}</div>`;
      return;
    }
    if (!this._data) {
      sum.textContent = '';
      list.innerHTML = '<div class="al-msg">Loading…</div>';
      return;
    }
    const { entries, total } = this._data;
    sum.textContent = summary(this._filters, total != null ? total : entries.length);
    if (!entries.length) {
      const kept = this._data.oldest ? ` The house keeps 30 days; the oldest line is from ${new Date(this._data.oldest * 1000).toLocaleDateString('en-GB', { day: 'numeric', month: 'short' })}.` : ' Nothing has been noted yet.';
      list.innerHTML = `<div class="al-msg">Nothing matches these choices.${kitEsc(kept)}</div>`;
      return;
    }
    const groups = groupByDay(entries);
    const more = entries.length < (total || 0) ? '<button type="button" class="al-more" data-act="more|">Show older</button>' : '';
    list.innerHTML = groups.map((g) => `<div class="al-day">${kitEsc(g.heading)}</div>${g.entries.map((e) => this._lineHtml(e)).join('')}`).join('') + more;
    hydrateIcons(this);
  }

  getCardSize() {
    return 10;
  }

  static getConfigElement() {
    return document.createElement(`activity-log-card-editor${SUFFIX}`);
  }

  static getStubConfig() {
    return { title: 'Activity' };
  }
}

export function registerActivityLogCard() {
  if (!customElements.get(`activity-log-card-editor${SUFFIX}`)) customElements.define(`activity-log-card-editor${SUFFIX}`, ActivityLogCardEditor);
  if (!customElements.get(`activity-log-card${SUFFIX}`)) customElements.define(`activity-log-card${SUFFIX}`, ActivityLogCard);
  window.customCards = window.customCards || [];
  window.customCards.push({
    type: `activity-log-card${SUFFIX}`,
    name: `Activity Log Card${LABEL}`,
    description: 'Administrators: what people and the assistant did in the house, and who came and went, filtered by day and time of day',
    preview: true,
    documentationURL: 'https://github.com/J45PER/church-drive-cards#readme',
  });
}
