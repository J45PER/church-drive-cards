// Task List Card: a to-do list you can tick off, add to and change, where any
// task can repeat (daily, weekly or every few weeks, monthly, yearly, or a
// while after it's done) and remind people when it's due. It works on any
// to-do list: the cleaning schedule, someone's own list, the shared one.
//
// The repeat and who gets reminded are kept as plain words in the task's
// description (see repeat.js), e.g. "Every 2 weeks: Mon 09:00 · for Hayley ·
// use the blue mop". The "Church Drive: repeating tasks" automation reads the
// same words: it reminds people when a task comes due, and when a repeating
// task is ticked off it's due again at its next time.

import { createFormEditor } from './form-editor.js';
import { iconHtml, hydrateIcons } from './icons.js';
import { SUFFIX, LABEL } from './suffix.js';
import { kitShell, kitHead, kitEsc } from './card-kit.js';
import { openPopup } from './popup.js';
import { stcMe } from './section-title-card.js';
import { DAYS, MONTHS, parseTask, formatTask, formatRepeat, describeRepeat, firstDue } from './repeat.js';

const pad = (n) => String(n).padStart(2, '0');
const DAY_NAMES = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];

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

const dueDate = (due) => (due ? new Date(String(due).includes('T') ? due : `${due}T23:59:59`) : null);
const dateInput = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
const timeInput = (d) => `${pad(d.getHours())}:${pad(d.getMinutes())}`;
const localStamp = (d) => `${dateInput(d)} ${timeInput(d)}:00`;

export function whenText(due) {
  if (!due) return '';
  const d = dueDate(due);
  const now = new Date();
  const day = (x) => new Date(x.getFullYear(), x.getMonth(), x.getDate()).getTime();
  const days = Math.round((day(d) - day(now)) / 86400000);
  const time = String(due).includes('T') ? ` ${timeInput(d)}` : '';
  if (d < now) return days < 0 ? `${-days} day${days === -1 ? '' : 's'} overdue` : 'due now';
  if (days === 0) return `today${time}`;
  if (days === 1) return `tomorrow${time}`;
  if (days < 7) return `${DAY_NAMES[(d.getDay() + 6) % 7]}${time}`;
  return `${d.getDate()} ${MONTHS[d.getMonth()]}`;
}

function tlDemo() {
  const now = Date.now();
  const iso = (ms) => new Date(ms).toISOString();
  return [
    { uid: 'd1', summary: 'Hoover upstairs', status: 'needs_action', due: iso(now + 3 * 3600000), description: 'Mon 09:00, Thu 18:30 · for Hayley' },
    { uid: 'd2', summary: 'Clean bathrooms', status: 'needs_action', due: iso(now - 26 * 3600000), description: 'Every 2 weeks: Fri 18:00 · for everyone' },
    { uid: 'd3', summary: 'Change bed sheets', status: 'needs_action', due: iso(now + 4 * 86400000), description: 'Every 30 days after done 10:00 · no reminders' },
    { uid: 'd4', summary: 'Book boiler service', status: 'needs_action', due: iso(now + 20 * 86400000).slice(0, 10), description: 'Yearly on 20 Oct 09:00 · for Jamie' },
    { uid: 'd5', summary: 'Buy lightbulbs', status: 'needs_action', description: 'The warm white E27 ones' },
    { uid: 'd6', summary: 'Return the parcel', status: 'completed' },
  ];
}

const REPEATS = [
  ['none', 'Never'],
  ['days', 'Daily'],
  ['weekly', 'Weekly'],
  ['monthly', 'Monthly'],
  ['yearly', 'Yearly'],
  ['after', "After it's done"],
];

const TL_CSS = `
  .tl-add[hidden], .tl-done-list[hidden] { display:none; }
  .tl-row { display:flex; align-items:center; gap:10px; padding:8px 2px; border-radius:12px; }
  .tl-row + .tl-row { border-top:1px solid var(--divider-color, rgba(127,127,127,0.22)); }
  .tl-body { flex:1; min-width:0; cursor:pointer; border-radius:8px; }
  .tl-body:hover { background:rgba(127,127,127,0.07); }
  .tl-name { font-size:0.95rem; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
  .tl-when { flex:none; font-size:0.78rem; font-weight:600; white-space:nowrap; }
  .tl-tick { flex:none; width:24px; height:24px; padding:0; border-radius:50%; border:2px solid; background:transparent; color:#fff; cursor:pointer; display:flex; align-items:center; justify-content:center; }
  .tl-row :focus-visible, .tl-form :focus-visible, .tl-foot :focus-visible { outline:2px solid var(--primary-color); outline-offset:2px; }
  .tl-foot { display:flex; flex-wrap:wrap; align-items:center; gap:8px; }
  .tl-add { border:none; border-radius:12px; padding:9px 14px; font:inherit; font-size:0.85rem; font-weight:600; cursor:pointer; display:flex; align-items:center; gap:6px; }
  .tl-link { border:none; background:none; padding:4px 2px; font:inherit; font-size:0.8rem; color:var(--secondary-text-color); text-decoration:underline; cursor:pointer; }
  .tl-form { display:flex; flex-direction:column; gap:14px; }
  .tl-l { display:flex; flex-direction:column; gap:6px; font-size:0.8rem; color:var(--secondary-text-color); }
  .tl-inline { display:flex; flex-wrap:wrap; align-items:center; gap:8px; font-size:0.85rem; color:var(--primary-text-color); }
  .tl-form input, .tl-form select { box-sizing:border-box; padding:8px 10px; border-radius:10px; border:1px solid var(--divider-color, rgba(127,127,127,0.3)); background:rgba(127,127,127,0.1); color:var(--primary-text-color); font:inherit; font-size:0.92rem; color-scheme:dark light; min-width:0; }
  .tl-form input[type=number] { width:4.2em; }
  .tl-form input.tl-wide { width:100%; }
  .tl-chips { display:flex; flex-wrap:wrap; gap:6px; }
  .tl-chip { border:none; border-radius:999px; padding:7px 12px; font:inherit; font-size:0.82rem; font-weight:600; cursor:pointer; background:rgba(127,127,127,0.18); color:var(--primary-text-color); }
  .tl-chip[aria-pressed="true"] { color:#fff; }
  .tl-times { display:grid; grid-template-columns:repeat(auto-fill, minmax(150px, 1fr)); gap:6px 10px; }
  .tl-time { display:flex; align-items:center; gap:8px; font-size:0.85rem; color:var(--primary-text-color); }
  .tl-time span { width:2.6em; flex:none; font-weight:600; }
  .tl-time input { flex:1; }
  .tl-buttons { display:flex; flex-wrap:wrap; gap:8px; }
  .tl-buttons button { border:none; border-radius:10px; padding:9px 14px; font:inherit; font-size:0.85rem; font-weight:600; cursor:pointer; }
  .tl-msg { font-size:0.8rem; color:#ffa726; min-height:1em; }
`;

export const TaskListCardEditor = createFormEditor({
  schema: (config) => [
    { name: 'title', selector: { text: {} } },
    { name: 'own', selector: { boolean: {} } },
    ...(config.own || config.entity === 'mine' ? [] : [{ name: 'entity', selector: { entity: { domain: 'todo' } } }]),
    { name: 'color', selector: { ui_color: {} } },
    {
      name: 'assign',
      selector: {
        select: {
          mode: 'list',
          options: [
            { value: 'people', label: 'Pick who it reminds (shared lists)' },
            { value: 'me', label: 'Just the signed-in person (a personal list)' },
          ],
        },
      },
    },
    config.assign === 'me' ? { name: 'remind_me', selector: { boolean: {} } } : { name: 'remind_default', selector: { text: {} } },
    { name: 'icons', selector: { boolean: {} } },
    { name: 'show_done', selector: { boolean: {} } },
    { name: 'demo', selector: { boolean: {} } },
  ],
  labels: {
    title: 'Title (optional)',
    own: "The signed-in person's own list",
    entity: 'To-do list',
    color: 'Colour',
    assign: 'Who tasks are for',
    remind_me: 'New tasks remind me (when they have a due time or repeat)',
    remind_default: 'New tasks remind (a first name, "everyone", or empty for no one)',
    icons: 'Show an icon for each task from its name (hoover, bathroom, …)',
    show_done: 'Show ticked-off tasks under the list',
    demo: 'Show pretend tasks (for Design Presets; saving is switched off)',
  },
  helpers: {
    entity: 'Any to-do list. A task\'s repeat and who it reminds are kept in its description, and "Church Drive: repeating tasks" sends the reminders and brings repeating tasks back.',
    color: 'Default purple (#7e57c2).',
    own: 'Each person sees their own "Priorities <first name>" list (made for everyone automatically), so one card serves everyone.',
    assign: '"Just the signed-in person" swaps the list of people for a simple "Remind me" choice.',
  },
  normalize: (c) => (c.assign ? c : { ...c, assign: 'people' }),
  display: (c) => {
    const d = c.assign === 'me' && c.remind_me === undefined ? { ...c, remind_me: true } : { ...c };
    if (d.entity === 'mine') {
      d.own = true;
      delete d.entity;
    }
    return d;
  },
  store: (c) => {
    const { own, ...rest } = c;
    if (own) return { ...rest, entity: 'mine' };
    return rest.entity === 'mine' ? { ...rest, entity: undefined } : rest;
  },
});

export class TaskListCard extends HTMLElement {
  setConfig(config) {
    if (!config || (!config.entity && !config.demo)) throw new Error('entity required');
    this.config = config;
    this._built = false;
    this._sig = null;
    if (this._pop) this._pop.close();
    this._edit = null;
    if (this._unsub) this._unwatch();
    if (this._hass) this._watch();
  }

  set hass(hass) {
    this._hass = hass;
    if (!this._placesLoaded) {
      this._placesLoaded = true;
      this._loadPlaces();
    }
    this._watch();
    this._render();
  }

  connectedCallback() {
    if (this._hass) this._watch();
  }

  disconnectedCallback() {
    this._unwatch();
    if (this._pop) this._pop.close();
  }

  // The list: config.entity, or "mine" for the signed-in person's own list.
  _entity() {
    const e = this.config.entity;
    return e === 'mine' ? stcMe(this._hass).my_list : e;
  }

  _colour() {
    return this.config.color || '#7e57c2';
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

  _all() {
    return this.config.demo ? this._demo || (this._demo = tlDemo()) : this._items || [];
  }

  // First names of everyone with a person entity, for "who gets reminded".
  _people() {
    const s = (this._hass && this._hass.states) || {};
    return Object.keys(s)
      .filter((id) => id.startsWith('person.'))
      .map((id) => String(s[id].attributes.friendly_name || id.slice(7)).split(' ')[0])
      .sort();
  }

  // The signed-in person's first name: from their person entity, else their user name.
  _me() {
    const u = this._hass && this._hass.user;
    if (!u) return '';
    const s = this._hass.states || {};
    const id = Object.keys(s).find((x) => x.startsWith('person.') && s[x].attributes.user_id === u.id);
    return String((id && s[id].attributes.friendly_name) || u.name || '').split(' ')[0];
  }

  _justMe() {
    return this.config.assign === 'me';
  }

  // The places a task can be tied to (a reminder when someone arrives there, instead of at a time), and which
  // tasks are tied to one: Church Drive's place reminders. An older Church Drive without them has no place row.
  async _loadPlaces() {
    if (this.config.demo) {
      this._zones = [
        { zone: 'zone.home', name: 'Home' },
        { zone: 'zone.tesco', name: 'Tesco' },
      ];
      this._places = {};
      return;
    }
    if (!this._hass || this._placesBusy) return;
    this._placesBusy = true;
    try {
      const r = await this._hass.callWS({ type: 'church_drive/reminders' });
      this._zones = r.zones || [];
      this._places = r.reminders || {};
    } catch (err) {
      /* no place reminders here */
    }
    this._placesBusy = false;
    this._sig = null;
    this._render();
    if (this._form && this._edit) this._drawForm();
  }

  _placeOf(uid) {
    const r = this._places && this._places[`${this._entity()}|${uid}`];
    return r ? r.zone : '';
  }

  _placeName(zone) {
    const z = (this._zones || []).find((x) => x.zone === zone);
    return z ? z.name : '';
  }

  // The uid of a task just added (its list reports it a moment after the add).
  async _findNew(name, before) {
    for (let i = 0; i < 6; i++) {
      const r = await this._hass.callWS({
        type: 'call_service',
        domain: 'todo',
        service: 'get_items',
        target: { entity_id: this._entity() },
        service_data: { status: ['needs_action'] },
        return_response: true,
      });
      const items = (((r && r.response) || {})[this._entity()] || {}).items || [];
      const found = items.find((t) => t.summary === name && !before.has(t.uid));
      if (found) return found.uid;
      await new Promise((done) => setTimeout(done, 300));
    }
    return null;
  }

  // Tie the task to the chosen place (or clear it), for the people the form reminds.
  async _savePlace(e, name, before) {
    if (this.config.demo || !this._zones || (!e.place && !e.placeWas)) return;
    const uid = e.uid || (await this._findNew(name, before));
    if (!uid) throw new Error('the new task was not found to set its place');
    const who = e.who === 'none' ? [] : e.who === 'everyone' ? ['everyone'] : e.who;
    await this._hass.callWS({ type: 'church_drive/reminders/set', list: this._entity(), uid, zone: e.place || null, who });
    await this._loadPlaces();
  }

  async _call(service, data) {
    if (this.config.demo) {
      const items = this._all();
      if (service === 'add_item') items.push({ uid: `d${Date.now()}`, summary: data.item, status: 'needs_action', description: data.description, due: data.due_datetime ? data.due_datetime.replace(' ', 'T') : data.due_date });
      if (service === 'update_item') {
        const t = items.find((x) => x.uid === data.item);
        if (t) {
          if (data.rename) t.summary = data.rename;
          if ('description' in data) t.description = data.description;
          if (data.status) t.status = data.status;
          if (data.due_datetime) t.due = data.due_datetime.replace(' ', 'T');
          if (data.due_date) t.due = data.due_date;
        }
      }
      if (service === 'remove_item') this._demo = items.filter((x) => ![].concat(data.item).includes(x.uid));
      this._sig = null;
      this._render();
      return;
    }
    await this._hass.callService('todo', service, data, { entity_id: this._entity() });
  }

  _render() {
    if (!this._hass) return;
    const c = this.config;
    const colour = this._colour();
    if (!this._built) {
      this.innerHTML = kitShell(
        `<div class="tl-list" style="display:flex; flex-direction:column;"></div>
        <div class="tl-foot"><button type="button" class="tl-add"></button><button type="button" class="tl-link tl-done-toggle" hidden></button></div>
        <div class="tl-done-list" hidden style="display:flex; flex-direction:column;"></div>`,
        TL_CSS
      );
      this._list = this.querySelector('.tl-list');
      this._addBtn = this.querySelector('.tl-add');
      this._doneBtn = this.querySelector('.tl-done-toggle');
      this._doneList = this.querySelector('.tl-done-list');
      this.querySelector('.ck-headrow').style.display = c.title ? '' : 'none';
      this._addBtn.addEventListener('click', () => this._open(null));
      this._doneBtn.addEventListener('click', () => {
        this._showDone = !this._showDone;
        this._sig = null;
        this._render();
      });
      const onClick = (ev) => {
        const tick = ev.target.closest('[data-tick]');
        if (tick) return this._tick(tick.dataset.tick, tick.dataset.to);
        if (ev.target.closest('[data-clear]')) return this._clearDone();
        const row = ev.target.closest('[data-uid]');
        if (row) this._open(row.dataset.uid);
      };
      this._list.addEventListener('click', onClick);
      this._doneList.addEventListener('click', onClick);
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
    const all = missing ? [] : this._all();
    const rank = (t) => (t.due ? dueDate(t.due).getTime() : Infinity);
    const open = all
      .filter((t) => t.status === 'needs_action')
      .map((t, n) => ({ t, n, p: parseTask(t.description) }))
      .sort((a, b) => rank(a.t) - rank(b.t) || a.n - b.n);
    const done = all.filter((t) => t.status === 'completed' && !(parseTask(t.description).repeat || {}).type);
    const late = open.filter(({ t }) => t.due && whenText(t.due).includes('overdue')).length;
    if (c.title) kitHead(this, c.title, missing ? 'Not set up' : late ? `${late} overdue` : open.length ? `${open.length} to do` : 'All done', late ? '#e53935' : colour);
    this._addBtn.style.background = `color-mix(in srgb, ${colour} 22%, transparent)`;
    this._addBtn.style.color = `color-mix(in srgb, ${colour} 45%, white)`;
    this._addBtn.innerHTML = `${iconHtml('mdi:plus', { size: '18px' })}Add a task`;
    this._addBtn.hidden = missing || !!this._edit;
    const showDone = c.show_done !== false && done.length > 0 && !this._edit;
    this._doneBtn.hidden = !showDone;
    this._doneBtn.textContent = `${this._showDone ? 'Hide' : 'Show'} done (${done.length})`;
    const sig = JSON.stringify([all.map((t) => [t.uid, t.summary, t.description, t.due, t.status]), missing, colour, !!this._edit, this._showDone, c.icons, this._places, this._zones]);
    if (sig === this._sig) return;
    this._sig = sig;
    const row = ({ t, p }, isDone) => {
      const when = isDone ? '' : whenText(t.due);
      const over = when.includes('overdue');
      const mine = this._justMe() && Array.isArray(p.who) && p.who.length === 1 && p.who[0] === this._me();
      const who = !p.repeat ? '' : mine ? 'reminds you' : p.who === 'everyone' ? 'reminds everyone' : Array.isArray(p.who) ? `reminds ${p.who.join(', ')}` : '';
      const place = this._placeName(this._placeOf(t.uid));
      const sub = [p.repeat && p.repeat.type !== 'once' ? describeRepeat(p.repeat) : '', who, place ? `at ${place}` : '', p.notes].filter(Boolean).join(' · ');
      const ring = isDone ? '#4caf50' : over ? '#e53935' : colour;
      return `<div class="tl-row">
        <button type="button" class="tl-tick" data-tick="${kitEsc(t.uid)}" data-to="${isDone ? 'needs_action' : 'completed'}" aria-label="${isDone ? 'Not done' : 'Done'}: ${kitEsc(t.summary)}" style="border-color:${ring}; ${isDone ? `background:${ring};` : ''}">${isDone ? iconHtml('mdi:check', { size: '15px' }) : ''}</button>
        ${c.icons ? iconHtml(choreIcon(t.summary), { size: '20px', style: `color:${over ? '#e53935' : colour}; flex:none;` }) : ''}
        <div class="tl-body" data-uid="${kitEsc(t.uid)}" role="button" tabindex="0" aria-label="Change ${kitEsc(t.summary)}">
          <div class="tl-name" style="${isDone ? 'text-decoration:line-through; color:var(--secondary-text-color);' : ''}">${kitEsc(t.summary)}</div>
          ${sub ? `<div class="ck-sub" style="font-size:0.74rem; line-height:1.35;">${kitEsc(sub)}</div>` : ''}
        </div>
        ${when ? `<span class="tl-when" style="color:${over ? '#e53935' : 'var(--secondary-text-color)'};">${kitEsc(when)}</span>` : ''}
      </div>`;
    };
    if (missing) this._list.innerHTML = `<div class="ck-sub" style="line-height:1.5;">There's no ${kitEsc(this._entity() || 'to-do')} list yet.</div>`;
    else if (!open.length) this._list.innerHTML = `<div class="ck-sub" style="line-height:1.5; padding:4px 0;">Nothing to do. Add a task, and choose if it repeats and who it reminds.</div>`;
    else this._list.innerHTML = open.map((x) => row(x, false)).join('');
    this._doneList.hidden = !(showDone && this._showDone);
    this._doneList.innerHTML =
      showDone && this._showDone
        ? done.map((t) => row({ t, p: parseTask(t.description) }, true)).join('') + `<button type="button" class="tl-link" data-clear style="align-self:flex-start;">Clear done tasks</button>`
        : '';
    hydrateIcons(this);
  }

  async _tick(uid, to) {
    try {
      await this._call('update_item', { item: uid, status: to });
    } catch (err) {
      /* the list refreshes itself */
    }
  }

  async _clearDone() {
    const done = this._all().filter((t) => t.status === 'completed' && !(parseTask(t.description).repeat || {}).type);
    if (done.length) await this._call('remove_item', { item: done.map((t) => t.uid) }).catch(() => {});
  }

  // The add/change form. uid null = a new task.
  _open(uid) {
    const t = uid ? this._all().find((x) => x.uid === uid) : null;
    const p = t ? parseTask(t.description) : { repeat: null, who: 'none', notes: '' };
    const r = p.repeat || {};
    const due = t && t.due ? dueDate(t.due) : null;
    const now = new Date();
    const def = this._justMe() ? (this.config.remind_me === false ? '' : this._me()) : String(this.config.remind_default || '').trim();
    this._edit = {
      uid,
      name: t ? t.summary : '',
      notes: p.notes,
      kind: !r.type || r.type === 'once' ? 'none' : r.type,
      n: r.n || 1,
      time: r.time || (due && String(t.due).includes('T') ? timeInput(due) : '09:00'),
      times: r.type === 'weekly' ? Object.fromEntries(r.slots.map(([d, tt]) => [d, tt])) : {},
      dom: r.type === 'monthly' ? r.day : due ? due.getDate() : now.getDate(),
      ymd: r.type === 'yearly' ? `${now.getFullYear()}-${pad(r.month + 1)}-${pad(r.day)}` : dateInput(due || now),
      unit: r.unit || 'day',
      date: due ? dateInput(due) : '',
      start: dateInput(due || now),
      who: t ? (p.who === 'everyone' ? 'everyone' : Array.isArray(p.who) ? [...p.who] : 'none') : !def ? 'none' : def.toLowerCase() === 'everyone' ? 'everyone' : [def],
      was: t ? t.description || '' : '',
      due: t ? t.due : null,
      place: uid ? this._placeOf(uid) : '',
      placeWas: uid ? this._placeOf(uid) : '',
    };
    this._loadPlaces();
    if (this._pop) this._pop.close();
    this._form = document.createElement('div');
    this._form.className = 'tl-form';
    this._pop = openPopup(this, {
      title: uid ? 'Change task' : 'New task',
      icon: uid ? 'mdi:pencil-outline' : 'mdi:plus',
      color: this._colour(),
      content: this._form,
      onClose: () => {
        this._pop = null;
        this._form = null;
        this._edit = null;
        this._sig = null;
        this._render();
      },
    });
    this._drawForm();
    this._sig = null;
    this._render();
    const name = this._form.querySelector('.tl-f-name');
    if (name && !uid) name.focus();
  }

  _close() {
    if (this._pop) this._pop.close();
  }

  _drawForm() {
    const e = this._edit;
    const colour = this._colour();
    const on = `background:${colour};`;
    const chip = (attr, value, label, pressed) => `<button type="button" class="tl-chip" ${attr}="${kitEsc(value)}" aria-pressed="${pressed}" style="${pressed ? on : ''}">${label}</button>`;
    const people = this._justMe() ? [] : this._people();
    const me = this._me();
    const whoIs = (x) => (x === 'everyone' ? e.who === 'everyone' : x === 'none' ? e.who === 'none' : Array.isArray(e.who) && e.who.includes(x));
    const every = (unit, max) => `<label class="tl-inline">Every <input type="number" class="tl-f-n" min="1" max="${max}" value="${e.n}"> ${unit}${e.n === 1 ? '' : 's'}</label>`;
    const at = `<label class="tl-inline">at <input type="time" class="tl-f-time" value="${e.time}"></label>`;
    const start = `<label class="tl-inline">starting <input type="date" class="tl-f-start" value="${e.start}"></label>`;
    const chosen = Object.keys(e.times).map(Number).sort((a, b) => a - b);
    let how = '';
    if (e.kind === 'none')
      how = `<div class="tl-inline">Due <input type="date" class="tl-f-date" value="${e.date}"><input type="time" class="tl-f-time" value="${e.date ? e.time : ''}" aria-label="Time (optional)"></div>`;
    else if (e.kind === 'days') how = `<div class="tl-inline">${every('day', 365)} ${at} ${start}</div>`;
    else if (e.kind === 'weekly')
      how = `<div class="tl-inline"><select class="tl-f-n" aria-label="How often">${[1, 2, 3, 4]
        .map((n) => `<option value="${n}" ${e.n === n ? 'selected' : ''}>${n === 1 ? 'Every week' : n === 2 ? 'Every 2 weeks (fortnightly)' : `Every ${n} weeks`}</option>`)
        .join('')}</select> ${start}</div>
        <div class="tl-chips" role="group" aria-label="Days">${DAYS.map((d, i) => chip('data-day', i, d, i in e.times)).join('')}</div>
        ${
          chosen.length
            ? `<div class="tl-l"><span style="display:flex; flex-wrap:wrap; gap:4px 12px;">Times${chosen.length > 1 ? `<button type="button" class="tl-link tl-same">Use ${DAYS[chosen[0]]}'s time for all</button>` : ''}</span>
              <div class="tl-times">${chosen.map((d) => `<label class="tl-time"><span>${DAYS[d]}</span><input type="time" data-time="${d}" value="${e.times[d]}"></label>`).join('')}</div></div>`
            : ''
        }`;
    else if (e.kind === 'monthly')
      how = `<div class="tl-inline">${every('month', 24)} on the <select class="tl-f-dom" aria-label="Day of the month">${[...Array(31)]
        .map((_, i) => `<option value="${i + 1}" ${e.dom === i + 1 ? 'selected' : ''}>${i + 1}</option>`)
        .join('')}<option value="last" ${e.dom === 'last' ? 'selected' : ''}>last day</option></select> ${at} ${start}</div>`;
    else if (e.kind === 'yearly') how = `<div class="tl-inline">On <input type="date" class="tl-f-ymd" value="${e.ymd}" aria-label="Date (the year is ignored)"> ${at}</div>`;
    else if (e.kind === 'after')
      how = `<div class="tl-inline">Every <input type="number" class="tl-f-n" min="1" max="365" value="${e.n}"> <select class="tl-f-unit" aria-label="Days, weeks or months">${['day', 'week', 'month']
        .map((u) => `<option value="${u}" ${e.unit === u ? 'selected' : ''}>${u}${e.n === 1 ? '' : 's'}</option>`)
        .join('')}</select> after it's done, ${at}</div>`;
    this._form.innerHTML = `
      <label class="tl-l">Task<input type="text" class="tl-f-name tl-wide" maxlength="80" placeholder="e.g. Hoover upstairs" value="${kitEsc(e.name)}"></label>
      <div class="tl-l">Repeats<div class="tl-chips" role="group" aria-label="Repeats">${REPEATS.map(([k, label]) => chip('data-kind', k, label, e.kind === k)).join('')}</div></div>
      ${how}
      <div class="tl-l">${e.kind === 'none' && !e.date && !e.place && e.who !== 'none' ? `${this._justMe() ? 'Remind me' : 'Reminds'} (at the due time, so choose one above)` : this._justMe() ? 'Remind me' : 'Reminds'}<div class="tl-chips" role="group" aria-label="Who gets reminded">${
        this._justMe()
          ? chip('data-me', 'yes', 'Yes', e.who !== 'none') + chip('data-me', 'no', 'No', e.who === 'none')
          : ['everyone', ...people, 'none'].map((x) => chip('data-who', x, x === 'everyone' ? 'Everyone' : x === 'none' ? 'No one' : kitEsc(x), whoIs(x))).join('')
      }</div></div>
      ${
        this._zones && this._zones.length
          ? `<label class="tl-l">Remind at a place instead of a time<select class="tl-f-place tl-wide" aria-label="Remind when they arrive at a place"><option value="">No place</option>${this._zones
              .map((z) => `<option value="${kitEsc(z.zone)}" ${e.place === z.zone ? 'selected' : ''}>${kitEsc(z.name)}</option>`)
              .join('')}</select><span class="ck-sub" style="line-height:1.35;">${e.place ? 'Reminds each time they arrive there, until the task is done. The reminder has Done and Snooze buttons.' : ''}</span></label>`
          : ''
      }
      <label class="tl-l">Notes<input type="text" class="tl-f-notes tl-wide" maxlength="200" placeholder="Optional" value="${kitEsc(e.notes)}"></label>
      <div class="tl-msg" role="status"></div>
      <div class="tl-buttons">
        <button type="button" class="tl-save" style="${on} color:#fff;">${e.uid ? 'Save' : 'Add task'}</button>
        <button type="button" class="tl-cancel" style="background:rgba(127,127,127,0.18); color:var(--primary-text-color);">Cancel</button>
        ${e.uid ? `<button type="button" class="tl-delete" style="margin-left:auto; background:color-mix(in srgb, #e53935 20%, transparent); color:#ef9a9a;">Delete</button>` : ''}
      </div>`;
    const f = this._form;
    const bind = (sel, ev, fn) => f.querySelectorAll(sel).forEach((el) => el.addEventListener(ev, () => fn(el)));
    bind('.tl-f-name', 'input', (el) => (e.name = el.value));
    bind('.tl-f-notes', 'input', (el) => (e.notes = el.value));
    bind('.tl-f-place', 'change', (el) => {
      e.place = el.value;
      this._drawForm();
    });
    bind('.tl-f-n', 'change', (el) => {
      e.n = Math.max(1, Number(el.value) || 1);
      this._drawForm();
    });
    bind('.tl-f-time', 'change', (el) => (e.time = el.value || '09:00'));
    bind('.tl-f-start', 'change', (el) => (e.start = el.value || dateInput(new Date())));
    bind('.tl-f-date', 'change', (el) => (e.date = el.value));
    bind('.tl-f-dom', 'change', (el) => (e.dom = el.value === 'last' ? 'last' : Number(el.value)));
    bind('.tl-f-ymd', 'change', (el) => (e.ymd = el.value || e.ymd));
    bind('.tl-f-unit', 'change', (el) => (e.unit = el.value));
    bind('[data-kind]', 'click', (el) => {
      e.kind = el.dataset.kind;
      if (e.kind === 'weekly' && !Object.keys(e.times).length) e.times[(new Date().getDay() + 6) % 7] = e.time;
      if (e.kind === 'weekly') e.n = Math.min(4, e.n);
      this._drawForm();
    });
    bind('[data-day]', 'click', (el) => {
      const d = Number(el.dataset.day);
      if (d in e.times) delete e.times[d];
      else e.times[d] = Object.values(e.times)[0] || e.time;
      this._drawForm();
    });
    bind('[data-time]', 'change', (el) => (e.times[Number(el.dataset.time)] = el.value || '09:00'));
    bind('.tl-same', 'click', () => {
      const t = e.times[chosen[0]];
      Object.keys(e.times).forEach((d) => (e.times[d] = t));
      this._drawForm();
    });
    bind('[data-who]', 'click', (el) => {
      const x = el.dataset.who;
      if (x === 'everyone' || x === 'none') e.who = x;
      else {
        const list = Array.isArray(e.who) ? e.who : [];
        e.who = list.includes(x) ? list.filter((y) => y !== x) : [...list, x];
        if (!e.who.length) e.who = 'none';
      }
      this._drawForm();
    });
    bind('[data-me]', 'click', (el) => {
      if (el.dataset.me === 'no') e.who = 'none';
      else if (e.who === 'none') e.who = me ? [me] : 'everyone';
      this._drawForm();
    });
    bind('.tl-cancel', 'click', () => this._close());
    bind('.tl-save', 'click', () => this._save());
    bind('.tl-delete', 'click', (el) => {
      if (el.dataset.sure) this._delete();
      else {
        el.dataset.sure = '1';
        el.textContent = 'Tap again to delete';
      }
    });
  }

  _say(text) {
    const m = this._form && this._form.querySelector('.tl-msg');
    if (m) m.textContent = text;
  }

  // The repeat the form describes, or null for "Never".
  _repeat() {
    const e = this._edit;
    if (e.kind === 'days') return { type: 'days', n: e.n, time: e.time };
    if (e.kind === 'weekly') return { type: 'weekly', n: Math.min(4, e.n), slots: Object.keys(e.times).map((d) => [Number(d), e.times[d]]) };
    if (e.kind === 'monthly') return { type: 'monthly', n: e.n, day: e.dom, time: e.time };
    if (e.kind === 'yearly') {
      const [, m, d] = e.ymd.split('-').map(Number);
      return { type: 'yearly', month: m - 1, day: d, time: e.time };
    }
    if (e.kind === 'after') return { type: 'after', n: e.n, unit: e.unit, time: e.time };
    return null;
  }

  async _save() {
    const e = this._edit;
    const name = String(e.name || '').trim();
    if (!name) return this._say('Give the task a name.');
    if (e.kind === 'weekly' && !Object.keys(e.times).length) return this._say('Choose at least one day.');
    if (e.place && e.who === 'none') return this._say('Choose who to remind at that place.');
    const before = new Set(this._all().map((t) => t.uid));
    // A one-off task reminds at its due time, so without one there's nothing to remind about.
    const reminds = e.who !== 'none' && (e.kind !== 'none' || !!e.date);
    const repeat = this._repeat() || (reminds ? { type: 'once' } : null);
    const description = formatTask(repeat, e.who, e.notes);
    const data = { description };
    if (repeat && repeat.type !== 'once') {
      // Keep the current due time if the repeat itself didn't change (so a
      // fortnightly task keeps its fortnight); otherwise start afresh.
      const same = e.due && parseTask(e.was).repeat && formatRepeat(parseTask(e.was).repeat) === formatRepeat(repeat);
      if (!same) {
        const [y, m, d] = e.start.split('-').map(Number);
        data.due_datetime = localStamp(firstDue(repeat, new Date(y, m - 1, d)));
      }
    } else if (e.date) {
      if (reminds || this._form.querySelector('.tl-f-time')?.value) data.due_datetime = `${e.date} ${e.time || '09:00'}:00`;
      else data.due_date = e.date;
    }
    try {
      if (e.uid) await this._call('update_item', { item: e.uid, rename: name, status: 'needs_action', ...data });
      else await this._call('add_item', { item: name, ...data });
    } catch (err) {
      return this._say(`Couldn't save: ${(err && err.message) || err}`);
    }
    try {
      await this._savePlace(e, name, before);
    } catch (err) {
      return this._say(`The task is saved, but its place wasn't: ${(err && err.message) || err}`);
    }
    this._close();
  }

  async _delete() {
    try {
      await this._call('remove_item', { item: this._edit.uid });
      if (this._edit.placeWas && !this.config.demo)
        await this._hass.callWS({ type: 'church_drive/reminders/set', list: this._entity(), uid: this._edit.uid, zone: null, who: [] }).catch(() => {});
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
    return document.createElement(`task-list-card-editor${SUFFIX}`);
  }

  static getStubConfig(hass) {
    const s = (hass && hass.states) || {};
    return { entity: Object.keys(s).find((id) => id.startsWith('todo.')) || 'todo.cleaning' };
  }
}

export function registerTaskListCard() {
  if (!customElements.get(`task-list-card-editor${SUFFIX}`)) customElements.define(`task-list-card-editor${SUFFIX}`, TaskListCardEditor);
  if (!customElements.get(`task-list-card${SUFFIX}`)) customElements.define(`task-list-card${SUFFIX}`, TaskListCard);
  window.customCards = window.customCards || [];
  window.customCards.push({
    type: `task-list-card${SUFFIX}`,
    name: `Task List Card${LABEL}`,
    description: 'A to-do list where tasks can repeat (weekly, fortnightly, monthly, yearly, after done) and remind people',
    preview: true,
    documentationURL: 'https://github.com/J45PER/church-drive-cards#readme',
  });
}
