// Purifier Schedule Card: when the air purifier goes to Auto and when it goes to Sleep.
//
// Shows each schedule as one line, e.g. "Mon-Fri Auto: 07:00-23:30" (Auto from 07:00, Sleep from
// 23:30), and when the next change is. Nothing can be changed from that view: an Edit button opens
// the editor (day chips, the two times, add or remove a schedule, and the on/off switch) with Save
// and Cancel, so a stray tap on the dashboard can't alter it. The schedules run in the Church Drive
// integration (purifier_schedule.py); this card only sets them. Only administrators can edit.

import { createFormEditor } from './form-editor.js';
import { iconHtml, hydrateIcons } from './icons.js';
import { SUFFIX, LABEL } from './suffix.js';
import { kitShell, kitHead, kitEsc, KIT_COLOR } from './card-kit.js';
import { DAY_NAMES, DAY_LETTERS, summary, toggleDay, newSchedule, problem, overlaps } from './purifier-schedule-logic.js';

const WS = 'church_drive/purifier_schedule';
const EVENT = 'church_drive_purifier_schedule_changed';

const PS_CSS = `
  .ps-line { display:flex; align-items:center; gap:8px; padding:7px 0; border-top:1px solid var(--divider-color, rgba(127,127,127,0.18)); font-size:0.95rem; font-weight:500; }
  .ps-line:first-child { border-top:none; }
  .ps-line i { flex:none; width:10px; height:10px; border-radius:50%; background:${KIT_COLOR.sleep}; }
  .ps-sched { display:flex; flex-direction:column; gap:8px; padding:10px 12px; border-radius:14px; background:rgba(127,127,127,0.08); }
  .ps-sum { font-size:0.85rem; font-weight:600; }
  .ps-days { display:flex; gap:5px; }
  .ps-day { flex:1 1 0; min-width:0; height:36px; border:none; border-radius:10px; font:inherit; font-size:0.85rem; font-weight:700; cursor:pointer; background:rgba(127,127,127,0.18); color:var(--primary-text-color); }
  .ps-day[aria-pressed="true"] { background:${KIT_COLOR.good}; color:#fff; }
  .ps-times { display:flex; align-items:center; gap:8px; flex-wrap:wrap; font-size:0.85rem; }
  .ps-times label { display:flex; align-items:center; gap:6px; }
  .ps-in { font:inherit; font-size:0.9rem; padding:6px 9px; border-radius:9px; border:1px solid var(--divider-color, rgba(127,127,127,0.3)); background:transparent; color:var(--primary-text-color); }
  .ps-btn { border:none; border-radius:12px; padding:9px 14px; font:inherit; font-size:0.85rem; font-weight:600; cursor:pointer; background:rgba(127,127,127,0.18); color:var(--primary-text-color); display:inline-flex; align-items:center; justify-content:center; gap:6px; }
  .ps-btn:disabled { opacity:.45; cursor:default; }
  .ps-btn.ps-primary { background:${KIT_COLOR.good}; color:#fff; }
  .ps-btn.ps-remove { margin-left:auto; padding:6px 10px; color:#e57373; }
  .ps-btn:focus-visible, .ps-day:focus-visible, .ps-in:focus-visible { outline:2px solid var(--primary-color); outline-offset:2px; }
  .ps-actions { display:flex; gap:8px; flex-wrap:wrap; }
  .ps-switch { display:flex; align-items:center; gap:8px; font-size:0.9rem; }
  .ps-msg { font-size:0.8rem; color:#ffa726; }
`;

function psDemo() {
  return {
    enabled: true,
    entity: 'fan.demo_purifier',
    schedules: [
      { id: 's1', days: [0, 1, 2, 3, 4], auto: '07:00', sleep: '23:30' },
      { id: 's2', days: [5, 6], auto: '09:00', sleep: '23:59' },
    ],
    next: { mode: 'sleep', text: 'Sleep Mon 23:30' },
  };
}

export const PurifierScheduleCardEditor = createFormEditor({
  schema: () => [
    { name: 'title', selector: { text: {} } },
    { name: 'demo', selector: { boolean: {} } },
  ],
  labels: {
    title: 'Title (optional)',
    demo: 'Show a pretend schedule (for Design Presets; changes are switched off)',
  },
});

export class PurifierScheduleCard extends HTMLElement {
  setConfig(config) {
    this.config = config || {};
    this._built = false;
    this._data = this.config.demo ? psDemo() : this._data || null;
    this._draft = null;
    this._msg = '';
  }

  set hass(hass) {
    this._hass = hass;
    if (!this.config.demo) {
      if (!this._watching && hass.connection && hass.connection.subscribeEvents) {
        this._watching = true;
        Promise.resolve(hass.connection.subscribeEvents(() => this._load(), EVENT))
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
  }

  async _load() {
    try {
      this._data = await this._hass.connection.sendMessagePromise({ type: WS });
      this._error = '';
    } catch (err) {
      this._error = "Church Drive's purifier schedule isn't running. Update Church Drive and restart Home Assistant.";
    }
    this._render();
  }

  _canEdit() {
    return !!(this.config.demo || (this._hass && this._hass.user && this._hass.user.is_admin));
  }

  _edit() {
    if (!this._data || !this._canEdit()) return;
    this._draft = { enabled: this._data.enabled, schedules: this._data.schedules.map((s) => ({ id: s.id, days: [...s.days], auto: s.auto, sleep: s.sleep })) };
    this._msg = '';
    this._render();
  }

  _cancel() {
    this._draft = null;
    this._msg = '';
    this._render();
  }

  async _save() {
    const d = this._draft;
    const why = problem(d.schedules);
    if (why) {
      this._msg = why;
      this._render();
      return;
    }
    if (this.config.demo) {
      Object.assign(this._data, { enabled: d.enabled, schedules: d.schedules });
      this._draft = null;
    } else {
      try {
        this._data = await this._hass.connection.sendMessagePromise({ type: `${WS}/set`, enabled: d.enabled, schedules: d.schedules });
        this._draft = null;
        this._msg = '';
      } catch (err) {
        this._msg = (err && err.message) || String(err);
      }
    }
    this._render();
  }

  _build() {
    this.innerHTML = kitShell(`<div class="ps-body" style="display:flex; flex-direction:column; gap:10px;"></div>`, PS_CSS);
    this._body = this.querySelector('.ps-body');
    this._built = true;
    // One listener for everything inside, so the buttons can be rebuilt freely.
    this._body.addEventListener('click', (ev) => {
      const el = ev.target.closest('[data-act]');
      if (!el || !this._body.contains(el)) return;
      const d = this._draft;
      const i = Number(el.dataset.i);
      switch (el.dataset.act) {
        case 'edit': return this._edit();
        case 'cancel': return this._cancel();
        case 'save': return this._save();
        case 'add':
          d.schedules.push(newSchedule(d.schedules));
          break;
        case 'remove':
          d.schedules.splice(i, 1);
          break;
        case 'day':
          d.schedules[i] = toggleDay(d.schedules[i], Number(el.dataset.day));
          break;
        default: return;
      }
      this._msg = '';
      this._render();
    });
    this._body.addEventListener('change', (ev) => {
      const el = ev.target;
      if (!this._draft || !el.dataset) return;
      if (el.dataset.field === 'enabled') this._draft.enabled = el.checked;
      else if (el.dataset.field === 'auto' || el.dataset.field === 'sleep') this._draft.schedules[Number(el.dataset.i)][el.dataset.field] = el.value;
      else return;
      this._msg = '';
      this._render();
    });
  }

  _render() {
    if (!this._hass && !this.config.demo) return;
    if (!this._built) this._build();
    const data = this._data;
    const title = this.config.title || 'Purifier schedule';
    if (!data) {
      kitHead(this, title, this._error ? '' : 'Loading…', KIT_COLOR.sleep);
      this._body.innerHTML = this._error ? `<div class="ps-msg">${kitEsc(this._error)}</div>` : '';
      return;
    }
    const editing = !!this._draft;
    const word = editing ? 'Editing' : !data.enabled ? 'Schedule off' : data.next ? `Next: ${data.next.text}` : '';
    kitHead(this, title, word, KIT_COLOR.sleep);
    const canEdit = this._canEdit();
    const msg = this._msg ? `<div class="ps-msg" role="alert">${kitEsc(this._msg)}</div>` : '';
    if (!editing) {
      const lines = data.schedules.length
        ? data.schedules.map((s) => `<div class="ps-line" style="${data.enabled ? '' : 'opacity:.55;'}"><i></i><span>${kitEsc(summary(s))}</span></div>`).join('')
        : '<div class="ck-sub">No schedules set.</div>';
      const last = data.last && !data.last.ok && !data.last.skipped ? `<div class="ps-msg">The last change to ${kitEsc(data.last.mode)} didn't go through.</div>` : '';
      this._body.innerHTML = `${lines}${last}${msg}${canEdit ? `<div class="ps-actions"><button type="button" class="ps-btn" data-act="edit" aria-label="Edit the schedule">${iconHtml('mdi:pencil', { size: '18px' })}Edit</button></div>` : ''}`;
      hydrateIcons(this);
      return;
    }
    const d = this._draft;
    const clash = overlaps(d.schedules);
    const rows = d.schedules
      .map(
        (s, i) => `<div class="ps-sched">
          <div class="ps-sum">${kitEsc(summary(s))}</div>
          <div class="ps-days" role="group" aria-label="Days">${DAY_LETTERS.map((l, day) => `<button type="button" class="ps-day" data-act="day" data-i="${i}" data-day="${day}" aria-pressed="${s.days.includes(day)}" aria-label="${DAY_NAMES[day]}">${l}</button>`).join('')}</div>
          <div class="ps-times">
            <label>Auto from <input class="ps-in" type="time" data-field="auto" data-i="${i}" value="${kitEsc(s.auto)}"></label>
            <label>Sleep from <input class="ps-in" type="time" data-field="sleep" data-i="${i}" value="${kitEsc(s.sleep)}"></label>
            <button type="button" class="ps-btn ps-remove" data-act="remove" data-i="${i}" aria-label="Remove this schedule">${iconHtml('mdi:delete', { size: '18px' })}</button>
          </div>
        </div>`
      )
      .join('');
    this._body.innerHTML = `
      <label class="ps-switch"><input type="checkbox" data-field="enabled" ${d.enabled ? 'checked' : ''}>Schedule on</label>
      ${rows || '<div class="ck-sub">No schedules. Add one, or the purifier is left as it is.</div>'}
      ${clash ? `<div class="ps-msg">${kitEsc(clash)} ${clash.includes(',') ? 'are' : 'is'} in more than one schedule. If both change at the same time, Auto wins.</div>` : ''}
      ${msg}
      <div class="ps-actions">
        <button type="button" class="ps-btn" data-act="add">${iconHtml('mdi:plus', { size: '18px' })}Add schedule</button>
        <span style="flex:1"></span>
        <button type="button" class="ps-btn" data-act="cancel">Cancel</button>
        <button type="button" class="ps-btn ps-primary" data-act="save">Save</button>
      </div>`;
    hydrateIcons(this);
  }

  getCardSize() {
    return 3;
  }

  getGridOptions() {
    return { columns: 12, min_columns: 6, rows: 'auto' };
  }

  static getConfigElement() {
    return document.createElement(`purifier-schedule-card-editor${SUFFIX}`);
  }

  static getStubConfig() {
    return { demo: true };
  }
}

export function registerPurifierScheduleCard() {
  if (!customElements.get(`purifier-schedule-card-editor${SUFFIX}`)) customElements.define(`purifier-schedule-card-editor${SUFFIX}`, PurifierScheduleCardEditor);
  if (!customElements.get(`purifier-schedule-card${SUFFIX}`)) customElements.define(`purifier-schedule-card${SUFFIX}`, PurifierScheduleCard);
  window.customCards = window.customCards || [];
  window.customCards.push({
    type: `purifier-schedule-card${SUFFIX}`,
    name: `Purifier Schedule Card${LABEL}`,
    description: 'When the air purifier goes to Auto and to Sleep, on which days (e.g. Mon-Fri Auto: 07:00-23:30), with an Edit button',
    preview: true,
    documentationURL: 'https://github.com/J45PER/church-drive-cards#readme',
  });
}
