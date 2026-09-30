// Cleaning Schedule Card: the jobs on a to-do list (by default the
// "Cleaning" list) and when each one repeats, with a form to add and change
// them: which days, a time for each day, and who gets reminded.
//
// Each job is one to-do item. Its description holds the schedule in plain
// words, e.g. "Mon 09:00, Thu 18:30 · for Hayley", "Sat 10:00 · for everyone"
// or "Every day 08:00 · no reminders", and its due date is the next time it
// comes round. The "Church Drive: cleaning schedule" automation reads the
// same words: it reminds people when a job is due, and when a job is ticked
// off it sets it due again at its next time.

import { createFormEditor } from './form-editor.js';
import { iconHtml, hydrateIcons } from './icons.js';
import { SUFFIX, LABEL } from './suffix.js';
import { kitShell, kitHead, kitEsc } from './card-kit.js';

export const CLEANING_LIST = 'todo.cleaning';

// An icon for a job from its name (hoover, bathroom, sheets, …).
export function choreIcon(name) {
  const n = String(name || '').toLowerCase();
  if (/hoover|vacuum/.test(n)) return 'mdi:vacuum-outline';
  if (/bath|shower|en-?suite/.test(n)) return 'mdi:shower';
  if (/toilet|loo/.test(n)) return 'mdi:toilet';
  if (/sheet|bed/.test(n)) return 'mdi:bed-outline';
  if (/mop|floor/.test(n)) return 'mdi:spray-bottle';
  if (/dust|polish/.test(n)) return 'mdi:feather';
  if (/kitchen|oven|hob|fridge/.test(n)) return 'mdi:countertop-outline';
  if (/window|glass|mirror/.test(n)) return 'mdi:window-closed-variant';
  if (/bin|rubbish|recycl/.test(n)) return 'mdi:trash-can-outline';
  if (/wash|laundry|towel/.test(n)) return 'mdi:washing-machine';
  if (/garden|lawn|mow|weed/.test(n)) return 'mdi:flower-outline';
  return 'mdi:broom';
}

const DAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
const DAY_NAMES = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];
const pad = (n) => String(n).padStart(2, '0');

// "Mon 09:00, Thu 18:30 · for Hayley" -> { slots: [[0, '09:00'], [3, '18:30']], who }
// who is 'everyone', 'none' or a list of first names.
export function parseChore(description) {
  const parts = String(description || '').split(' · ');
  const slots = [];
  String(parts[0] || '')
    .split(',')
    .map((x) => x.trim())
    .forEach((x) => {
      const every = x.match(/^every day (\d{1,2}):(\d{2})$/i);
      if (every) {
        DAYS.forEach((_, d) => slots.push([d, `${pad(every[1])}:${every[2]}`]));
        return;
      }
      const m = x.match(/^(mon|tue|wed|thu|fri|sat|sun)[a-z]* (\d{1,2}):(\d{2})$/i);
      if (m) slots.push([DAYS.findIndex((d) => d.toLowerCase() === m[1].toLowerCase()), `${pad(m[2])}:${m[3]}`]);
    });
  const tail = String(parts[1] || '').trim();
  let who = 'none';
  if (/^for everyone$/i.test(tail)) who = 'everyone';
  else if (/^for /i.test(tail)) who = tail.slice(4).split(',').map((x) => x.trim()).filter(Boolean);
  return { slots, who, valid: slots.length > 0 };
}

export function formatChore(slots, who) {
  const sorted = [...slots].sort((a, b) => a[0] - b[0] || a[1].localeCompare(b[1]));
  const same = sorted.length === 7 && new Set(sorted.map((s) => s[0])).size === 7 && new Set(sorted.map((s) => s[1])).size === 1;
  const when = same ? `Every day ${sorted[0][1]}` : sorted.map(([d, t]) => `${DAYS[d]} ${t}`).join(', ');
  const whom = who === 'everyone' ? 'for everyone' : Array.isArray(who) && who.length ? `for ${who.join(', ')}` : 'no reminders';
  return `${when} · ${whom}`;
}

// The next time after `from` that the schedule comes round.
export function nextDue(slots, from = new Date()) {
  let best = null;
  for (let add = 0; add <= 7; add += 1) {
    const day = new Date(from.getFullYear(), from.getMonth(), from.getDate() + add);
    const wd = (day.getDay() + 6) % 7;
    slots
      .filter(([d]) => d === wd)
      .forEach(([, t]) => {
        const [h, m] = t.split(':').map(Number);
        const at = new Date(day.getFullYear(), day.getMonth(), day.getDate(), h, m);
        if (at > from && (!best || at < best)) best = at;
      });
    if (best) break;
  }
  return best;
}

const localStamp = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:00`;

// "Mon, Thu 09:00 · Sat 10:00" for the list.
function summarise(slots) {
  if (!slots.length) return 'No schedule yet';
  const byTime = {};
  slots.forEach(([d, t]) => (byTime[t] = byTime[t] || []).push(d));
  return Object.keys(byTime)
    .sort()
    .map((t) => {
      const days = byTime[t].sort((a, b) => a - b);
      return `${days.length === 7 ? 'Every day' : days.map((d) => DAYS[d]).join(', ')} ${t}`;
    })
    .join(' · ');
}

function whenText(due) {
  if (!due) return '';
  const d = new Date(String(due).includes('T') ? due : `${due}T23:59:59`);
  const now = new Date();
  const day = (x) => new Date(x.getFullYear(), x.getMonth(), x.getDate()).getTime();
  const days = Math.round((day(d) - day(now)) / 86400000);
  const time = String(due).includes('T') ? ` ${pad(d.getHours())}:${pad(d.getMinutes())}` : '';
  if (d < now) return days < 0 ? `${-days} day${days === -1 ? '' : 's'} overdue` : 'due now';
  if (days === 0) return `today${time}`;
  if (days === 1) return `tomorrow${time}`;
  if (days < 7) return `${DAY_NAMES[(d.getDay() + 6) % 7]}${time}`;
  return `${d.getDate()} ${d.toLocaleDateString([], { month: 'short' })}`;
}

function csDemo() {
  const now = Date.now();
  const iso = (ms) => new Date(ms).toISOString();
  return [
    { uid: 'd1', summary: 'Hoover upstairs', status: 'needs_action', due: iso(now + 3 * 3600000), description: 'Mon 09:00, Thu 18:30 · for Hayley' },
    { uid: 'd2', summary: 'Clean bathrooms', status: 'needs_action', due: iso(now - 26 * 3600000), description: 'Fri 18:00 · for everyone' },
    { uid: 'd3', summary: 'Change bed sheets', status: 'needs_action', due: iso(now + 4 * 86400000), description: 'Sat 10:00 · no reminders' },
  ];
}

const CS_CSS = `
  .cs-add[hidden], .cs-form[hidden] { display:none; }
  .cs-row { display:flex; align-items:center; gap:10px; padding:9px 4px; border-radius:12px; cursor:pointer; }
  .cs-row + .cs-row { border-top:1px solid var(--divider-color, rgba(127,127,127,0.22)); }
  .cs-row:hover { background:rgba(127,127,127,0.08); }
  .cs-row:focus-visible, .cs-form button:focus-visible, .cs-form input:focus-visible { outline:2px solid var(--primary-color); outline-offset:2px; }
  .cs-name { font-size:0.95rem; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
  .cs-when { flex:none; font-size:0.78rem; font-weight:600; white-space:nowrap; }
  .cs-add { align-self:flex-start; border:none; border-radius:12px; padding:9px 14px; font:inherit; font-size:0.85rem; font-weight:600; cursor:pointer; display:flex; align-items:center; gap:6px; }
  .cs-form { display:flex; flex-direction:column; gap:12px; padding:12px; border-radius:14px; background:rgba(127,127,127,0.1); }
  .cs-form label.cs-l { display:flex; flex-direction:column; gap:6px; font-size:0.8rem; color:var(--secondary-text-color); }
  .cs-form input[type=text], .cs-form input[type=time] { box-sizing:border-box; padding:8px 10px; border-radius:10px; border:1px solid var(--divider-color, rgba(127,127,127,0.3)); background:var(--card-background-color); color:var(--primary-text-color); font:inherit; font-size:0.95rem; }
  .cs-form input[type=time] { color-scheme:dark light; min-width:0; }
  .cs-chips { display:flex; flex-wrap:wrap; gap:6px; }
  .cs-chip { border:none; border-radius:999px; padding:7px 12px; font:inherit; font-size:0.82rem; font-weight:600; cursor:pointer; background:rgba(127,127,127,0.18); color:var(--primary-text-color); }
  .cs-chip[aria-pressed="true"] { color:#fff; }
  .cs-times { display:grid; grid-template-columns:repeat(auto-fill, minmax(150px, 1fr)); gap:6px 10px; }
  .cs-time { display:flex; align-items:center; gap:8px; font-size:0.85rem; }
  .cs-time span { width:2.6em; flex:none; font-weight:600; }
  .cs-time input { flex:1; }
  .cs-buttons { display:flex; flex-wrap:wrap; gap:8px; }
  .cs-buttons button { border:none; border-radius:10px; padding:9px 14px; font:inherit; font-size:0.85rem; font-weight:600; cursor:pointer; }
  .cs-msg { font-size:0.8rem; color:#ffa726; min-height:1em; }
  .cs-link { border:none; background:none; padding:0; font:inherit; font-size:0.78rem; color:var(--secondary-text-color); text-decoration:underline; cursor:pointer; }
`;

export const CleaningScheduleCardEditor = createFormEditor({
  schema: () => [
    { name: 'title', selector: { text: {} } },
    { name: 'entity', selector: { entity: { domain: 'todo' } } },
    { name: 'color', selector: { ui_color: {} } },
    { name: 'demo', selector: { boolean: {} } },
  ],
  labels: {
    title: 'Title (optional)',
    entity: 'To-do list',
    color: 'Colour',
    demo: 'Show pretend jobs (for Design Presets; saving is switched off)',
  },
  helpers: {
    entity: `Defaults to ${CLEANING_LIST}. Each job is one item on it; the schedule is kept in the item's description, and the "Church Drive: cleaning schedule" automation sends the reminders.`,
    color: 'Default blue (#2196f3).',
  },
});

export class CleaningScheduleCard extends HTMLElement {
  setConfig(config) {
    this.config = config || {};
    this._built = false;
    this._sig = null;
    this._edit = null;
    if (this._unsub) this._unwatch();
    if (this._hass) this._watch();
  }

  set hass(hass) {
    this._hass = hass;
    this._watch();
    this._render();
  }

  connectedCallback() {
    if (this._hass) this._watch();
  }

  disconnectedCallback() {
    this._unwatch();
  }

  _entity() {
    return this.config.entity || CLEANING_LIST;
  }

  _colour() {
    return this.config.color || '#2196f3';
  }

  _watch() {
    if (this.config.demo || this._unsub || !this.isConnected || !this._hass || !this._hass.states[this._entity()]) return;
    this._unsub = this._hass.connection
      .subscribeMessage(
        (msg) => {
          this._items = (msg && msg.items) || [];
          this._render();
        },
        { type: 'todo/item/subscribe', entity_id: this._entity() }
      )
      .catch(() => null);
  }

  _unwatch() {
    if (this._unsub) this._unsub.then((u) => u && u()).catch(() => {});
    this._unsub = null;
    this._items = null;
  }

  // First names of everyone with a person entity, for "who gets reminded".
  _people() {
    const s = (this._hass && this._hass.states) || {};
    return Object.keys(s)
      .filter((id) => id.startsWith('person.'))
      .map((id) => String(s[id].attributes.friendly_name || id.slice(7)).split(' ')[0])
      .sort();
  }

  _jobs() {
    const items = this.config.demo ? this._demo || (this._demo = csDemo()) : this._items || [];
    const rank = (t) => (t.due ? new Date(String(t.due).includes('T') ? t.due : `${t.due}T23:59:59`).getTime() : Infinity);
    return items
      .filter((t) => t.status === 'needs_action' || parseChore(t.description).valid)
      .map((t, n) => ({ t, n, p: parseChore(t.description) }))
      .sort((a, b) => rank(a.t) - rank(b.t) || a.n - b.n);
  }

  _render() {
    if (!this._hass) return;
    const c = this.config;
    const colour = this._colour();
    if (!this._built) {
      this.innerHTML = kitShell(`<div class="cs-list" style="display:flex; flex-direction:column;"></div><div class="cs-form" hidden></div><button type="button" class="cs-add"></button>`, CS_CSS);
      this._list = this.querySelector('.cs-list');
      this._form = this.querySelector('.cs-form');
      this._addBtn = this.querySelector('.cs-add');
      this._addBtn.addEventListener('click', () => this._open(null));
      this._list.addEventListener('click', (ev) => {
        const row = ev.target.closest('[data-uid]');
        if (row) this._open(row.dataset.uid);
      });
      this._list.addEventListener('keydown', (ev) => {
        const row = ev.target.closest('[data-uid]');
        if (row && (ev.key === 'Enter' || ev.key === ' ')) {
          ev.preventDefault();
          this._open(row.dataset.uid);
        }
      });
      this._built = true;
    }
    const missing = !c.demo && !this._hass.states[this._entity()];
    const jobs = missing ? [] : this._jobs();
    const late = jobs.filter(({ t }) => t.status === 'needs_action' && t.due && whenText(t.due).includes('overdue')).length;
    kitHead(this, c.title || 'Cleaning schedule', missing ? 'Not set up' : late ? `${late} overdue` : `${jobs.length} job${jobs.length === 1 ? '' : 's'}`, late ? '#e53935' : colour);
    this._addBtn.style.background = `color-mix(in srgb, ${colour} 22%, transparent)`;
    this._addBtn.style.color = `color-mix(in srgb, ${colour} 45%, white)`;
    this._addBtn.innerHTML = `${iconHtml('mdi:plus', { size: '18px' })}Add a job`;
    this._addBtn.hidden = missing || !!this._edit;
    const sig = JSON.stringify([jobs.map(({ t }) => [t.uid, t.summary, t.description, t.due, t.status]), missing, colour, this._edit ? this._edit.uid : null]);
    if (sig !== this._sig) {
      this._sig = sig;
      if (missing) {
        this._list.innerHTML = `<div class="ck-sub" style="line-height:1.5;">There's no ${kitEsc(this._entity())} list yet. Add a Local To-do list named "Cleaning".</div>`;
      } else if (!jobs.length) {
        this._list.innerHTML = `<div class="ck-sub" style="line-height:1.5;">No jobs yet. Add one, choose its days and times, and who gets reminded.</div>`;
      } else {
        this._list.innerHTML = jobs
          .map(({ t, p }) => {
            const when = t.status === 'needs_action' ? whenText(t.due) : '';
            const over = when.includes('overdue');
            const who = p.who === 'everyone' ? 'Everyone' : Array.isArray(p.who) ? p.who.join(', ') : 'No reminders';
            return `<div class="cs-row" data-uid="${kitEsc(t.uid)}" role="button" tabindex="0" aria-label="Change ${kitEsc(t.summary)}">
              ${iconHtml(choreIcon(t.summary), { size: '22px', style: `color:${over ? '#e53935' : colour}; flex:none;` })}
              <div style="flex:1; min-width:0;">
                <div class="cs-name">${kitEsc(t.summary)}</div>
                <div class="ck-sub" style="font-size:0.74rem; line-height:1.35;">${kitEsc(summarise(p.slots))} · ${kitEsc(who)}</div>
              </div>
              <span class="cs-when" style="color:${over ? '#e53935' : 'var(--secondary-text-color)'};">${kitEsc(when)}</span>
            </div>`;
          })
          .join('');
      }
      hydrateIcons(this._list);
    }
    hydrateIcons(this._addBtn);
  }

  // The add/change form. uid null = a new job.
  _open(uid) {
    const job = uid ? this._jobs().find(({ t }) => t.uid === uid) : null;
    const p = job ? job.p : { slots: [], who: 'none' };
    const times = {};
    p.slots.forEach(([d, t]) => (times[d] = times[d] || t));
    this._edit = { uid, name: job ? job.t.summary : '', times, who: p.who === 'everyone' ? 'everyone' : Array.isArray(p.who) ? [...p.who] : 'none' };
    this._drawForm();
    this._sig = null;
    this._render();
    const name = this._form.querySelector('.cs-f-name');
    if (name && !uid) name.focus();
  }

  _close() {
    this._edit = null;
    this._form.hidden = true;
    this._form.innerHTML = '';
    this._sig = null;
    this._render();
  }

  _drawForm() {
    const e = this._edit;
    const colour = this._colour();
    const on = `background:${colour};`;
    const people = this._people();
    const whoIs = (x) => (x === 'everyone' ? e.who === 'everyone' : x === 'none' ? e.who === 'none' : Array.isArray(e.who) && e.who.includes(x));
    const chosen = Object.keys(e.times).map(Number).sort((a, b) => a - b);
    this._form.hidden = false;
    this._form.innerHTML = `
      <label class="cs-l">Job<input type="text" class="cs-f-name" maxlength="60" placeholder="e.g. Hoover upstairs" value="${kitEsc(e.name)}"></label>
      <div class="cs-l" style="display:flex; flex-direction:column; gap:6px; font-size:0.8rem; color:var(--secondary-text-color);">Repeats on
        <div class="cs-chips" role="group" aria-label="Days">${DAYS.map((d, i) => `<button type="button" class="cs-chip" data-day="${i}" aria-pressed="${i in e.times}" style="${i in e.times ? on : ''}">${d}</button>`).join('')}</div>
      </div>
      ${
        chosen.length
          ? `<div class="cs-l" style="display:flex; flex-direction:column; gap:6px; font-size:0.8rem; color:var(--secondary-text-color);">
              <span style="display:flex; flex-wrap:wrap; gap:4px 12px;">Times${chosen.length > 1 ? `<button type="button" class="cs-link cs-same">Use ${DAYS[chosen[0]]}'s time for all</button>` : ''}</span>
              <div class="cs-times">${chosen.map((d) => `<label class="cs-time"><span>${DAYS[d]}</span><input type="time" data-time="${d}" value="${e.times[d]}"></label>`).join('')}</div>
            </div>`
          : ''
      }
      <div class="cs-l" style="display:flex; flex-direction:column; gap:6px; font-size:0.8rem; color:var(--secondary-text-color);">Reminds
        <div class="cs-chips" role="group" aria-label="Who gets reminded">${['everyone', ...people, 'none']
          .map((x) => `<button type="button" class="cs-chip" data-who="${kitEsc(x)}" aria-pressed="${whoIs(x)}" style="${whoIs(x) ? on : ''}">${x === 'everyone' ? 'Everyone' : x === 'none' ? 'No one' : kitEsc(x)}</button>`)
          .join('')}</div>
      </div>
      <div class="cs-msg" role="status"></div>
      <div class="cs-buttons">
        <button type="button" class="cs-save" style="${on} color:#fff;">${e.uid ? 'Save' : 'Add job'}</button>
        <button type="button" class="cs-cancel" style="background:rgba(127,127,127,0.18); color:var(--primary-text-color);">Cancel</button>
        ${e.uid ? `<button type="button" class="cs-delete" style="margin-left:auto; background:color-mix(in srgb, #e53935 20%, transparent); color:#ef9a9a;">Delete</button>` : ''}
      </div>`;
    const f = this._form;
    f.querySelector('.cs-f-name').addEventListener('input', (ev) => (e.name = ev.target.value));
    f.querySelectorAll('[data-day]').forEach((b) =>
      b.addEventListener('click', () => {
        const d = Number(b.dataset.day);
        if (d in e.times) delete e.times[d];
        else e.times[d] = Object.values(e.times)[0] || '09:00';
        this._drawForm();
      })
    );
    f.querySelectorAll('[data-time]').forEach((i) => i.addEventListener('change', () => (e.times[Number(i.dataset.time)] = i.value || '09:00')));
    const same = f.querySelector('.cs-same');
    if (same)
      same.addEventListener('click', () => {
        const t = e.times[chosen[0]];
        Object.keys(e.times).forEach((d) => (e.times[d] = t));
        this._drawForm();
      });
    f.querySelectorAll('[data-who]').forEach((b) =>
      b.addEventListener('click', () => {
        const x = b.dataset.who;
        if (x === 'everyone' || x === 'none') e.who = x;
        else {
          const list = Array.isArray(e.who) ? e.who : [];
          e.who = list.includes(x) ? list.filter((y) => y !== x) : [...list, x];
          if (!e.who.length) e.who = 'none';
        }
        this._drawForm();
      })
    );
    f.querySelector('.cs-cancel').addEventListener('click', () => this._close());
    f.querySelector('.cs-save').addEventListener('click', () => this._save());
    const del = f.querySelector('.cs-delete');
    if (del)
      del.addEventListener('click', () => {
        if (del.dataset.sure) this._delete();
        else {
          del.dataset.sure = '1';
          del.textContent = 'Tap again to delete';
        }
      });
  }

  _say(text) {
    const m = this._form.querySelector('.cs-msg');
    if (m) m.textContent = text;
  }

  async _save() {
    const e = this._edit;
    const name = String(e.name || '').trim();
    const slots = Object.keys(e.times).map((d) => [Number(d), e.times[d]]);
    if (!name) return this._say('Give the job a name.');
    if (!slots.length) return this._say('Choose at least one day.');
    const description = formatChore(slots, e.who);
    const due = nextDue(slots);
    if (this.config.demo) {
      this._demo = [...(this._demo || csDemo()).filter((t) => t.uid !== e.uid), { uid: e.uid || `d${Date.now()}`, summary: name, status: 'needs_action', due: due.toISOString(), description }];
      return this._close();
    }
    const target = { entity_id: this._entity() };
    try {
      if (e.uid) await this._hass.callService('todo', 'update_item', { item: e.uid, rename: name, description, due_datetime: localStamp(due), status: 'needs_action' }, target);
      else await this._hass.callService('todo', 'add_item', { item: name, description, due_datetime: localStamp(due) }, target);
      this._close();
    } catch (err) {
      this._say(`Couldn't save: ${(err && err.message) || err}`);
    }
  }

  async _delete() {
    const e = this._edit;
    if (this.config.demo) {
      this._demo = (this._demo || csDemo()).filter((t) => t.uid !== e.uid);
      return this._close();
    }
    try {
      await this._hass.callService('todo', 'remove_item', { item: e.uid }, { entity_id: this._entity() });
      this._close();
    } catch (err) {
      this._say(`Couldn't delete: ${(err && err.message) || err}`);
    }
  }

  getCardSize() {
    return 4;
  }

  getGridOptions() {
    return { columns: 12, min_columns: 6, rows: 'auto' };
  }

  static getConfigElement() {
    return document.createElement(`cleaning-schedule-card-editor${SUFFIX}`);
  }

  static getStubConfig() {
    return { title: 'Cleaning schedule' };
  }
}

export function registerCleaningScheduleCard() {
  if (!customElements.get(`cleaning-schedule-card-editor${SUFFIX}`)) customElements.define(`cleaning-schedule-card-editor${SUFFIX}`, CleaningScheduleCardEditor);
  if (!customElements.get(`cleaning-schedule-card${SUFFIX}`)) customElements.define(`cleaning-schedule-card${SUFFIX}`, CleaningScheduleCard);
  window.customCards = window.customCards || [];
  window.customCards.push({
    type: `cleaning-schedule-card${SUFFIX}`,
    name: `Cleaning Schedule Card${LABEL}`,
    description: 'Repeating jobs with days, times and who gets reminded',
    preview: true,
    documentationURL: 'https://github.com/J45PER/church-drive-cards#readme',
  });
}
