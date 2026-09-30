// House Tasks Card: the jobs the house has spotted for you (a low battery, a
// filter due, a device not responding, a vacuum message), read from the
// automatic to-do list ("Priorities Automatic"). There's nothing to tick:
// each one goes by itself once the device reports it's sorted.
//
// Items carry the description "Automatic · <kind> · <detail> · for <name>",
// where <name> is a first name or "Everyone". By default the card shows the
// signed-in person's plus everyone's; `show: all` lists them all with names.

import { createFormEditor } from './form-editor.js';
import { iconHtml, hydrateIcons } from './icons.js';
import { SUFFIX, LABEL } from './suffix.js';
import { KIT_COLOR, kitShell, kitHead } from './card-kit.js';

export const HOUSE_TASKS_LIST = 'todo.priorities_automatic';
// Their colour: a lighter purple than the person's own to-dos (#7e57c2).
export const HOUSE_TASKS_COLOR = '#ab47bc';

// Kind, detail, who and icon from an automatic item's description.
export function houseTask(item) {
  const parts = String((item && item.description) || '').split(' · ');
  const last = parts[parts.length - 1] || '';
  const who = /^for /.test(last) ? last.slice(4).trim() : 'Everyone';
  const rest = parts.slice(1, /^for /.test(last) ? -1 : undefined);
  const kind = rest[0] || '';
  const icon = /batter/i.test(kind)
    ? 'mdi:battery-alert-variant-outline'
    : /filter/i.test(kind)
      ? 'mdi:air-filter'
      : /vacuum/i.test(kind)
        ? 'mdi:robot-vacuum'
        : /respond|device/i.test(kind)
          ? 'mdi:heart-pulse'
          : 'mdi:home-alert-outline';
  return { kind, detail: rest.slice(1).join(' · '), who, icon };
}

// Whether a task is for this person (their own, or everyone's).
export function houseTaskFor(task, first) {
  const w = String(task.who || '').toLowerCase();
  return w === 'everyone' || (!!first && w === String(first).toLowerCase());
}

function htDemo() {
  return [
    { uid: 'd1', summary: 'Replace battery: Ring Front Doorbell', status: 'needs_action', description: 'Automatic · Low batteries · 17% · for Everyone' },
    { uid: 'd2', summary: 'Clean the air purifier pre-filter', status: 'needs_action', description: 'Automatic · Filters due · 8% left · for Jamie' },
  ];
}

export const HouseTasksCardEditor = createFormEditor({
  schema: () => [
    { name: 'title', selector: { text: {} } },
    { name: 'entity', selector: { entity: { domain: 'todo' } } },
    { name: 'show', selector: { select: { mode: 'dropdown', options: [{ value: 'mine', label: 'The signed-in person’s and everyone’s' }, { value: 'all', label: 'Everyone’s, with names' }] } } },
    { name: 'color', selector: { text: {} } },
    { name: 'demo', selector: { boolean: {} } },
  ],
  labels: {
    title: 'Title (optional)',
    color: 'Colour',
    entity: 'Automatic to-do list',
    show: 'Show',
    demo: 'Show pretend tasks (for Design Presets)',
  },
  helpers: {
    color: `Default ${HOUSE_TASKS_COLOR} (purple). Any CSS colour.`,
    entity: `Defaults to ${HOUSE_TASKS_LIST}. Who gets each kind of task is set in Manager → Automatic to-dos.`,
  },
});

export class HouseTasksCard extends HTMLElement {
  setConfig(config) {
    this.config = config || {};
    this._built = false;
    this._sig = null;
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
    return this.config.entity || HOUSE_TASKS_LIST;
  }

  _watch() {
    if (this.config.demo || this._unsub || !this.isConnected || !this._hass || !this._hass.states[this._entity()]) return;
    const id = this._entity();
    this._unsub = this._hass.connection
      .subscribeMessage(
        (msg) => {
          this._items = (msg && msg.items) || [];
          this._render();
        },
        { type: 'todo/item/subscribe', entity_id: id }
      )
      .catch(() => null);
  }

  _unwatch() {
    if (this._unsub) this._unsub.then((u) => u && u()).catch(() => {});
    this._unsub = null;
    this._items = null;
  }

  _render() {
    if (!this._hass) return;
    const c = this.config;
    if (!this._built) {
      this.innerHTML = kitShell(`<div class="ht-list" style="display:flex; flex-direction:column;"></div>`);
      this._list = this.querySelector('.ht-list');
      this.querySelector('.ck-headrow').style.display = c.title ? '' : 'none';
      this._built = true;
    }
    const first = this._hass.user && this._hass.user.name ? String(this._hass.user.name).split(' ')[0] : '';
    const all = c.show === 'all';
    const colour = c.color || HOUSE_TASKS_COLOR;
    const missing = !c.demo && !this._hass.states[this._entity()];
    const tasks = (c.demo ? htDemo() : this._items || [])
      .filter((t) => t.status === 'needs_action')
      .map((t) => ({ t, h: houseTask(t) }))
      .filter(({ h }) => all || houseTaskFor(h, first));
    if (c.title) kitHead(this, c.title, missing ? 'Not set up' : tasks.length ? `${tasks.length} to sort` : 'All sorted', tasks.length ? colour : KIT_COLOR.good);
    const sig = JSON.stringify([tasks, missing, all, first, colour]);
    if (sig === this._sig) return;
    this._sig = sig;
    if (missing) {
      this._list.innerHTML = `<div class="ck-sub" style="line-height:1.5;">There's no ${this._entity()} list yet. Add a Local To-do list named "Priorities Automatic".</div>`;
      return;
    }
    if (!tasks.length) {
      this._list.innerHTML = `<div style="display:flex; align-items:center; gap:10px; padding:4px 0;">${iconHtml('mdi:check-circle-outline', { size: '22px', style: `color:${KIT_COLOR.good}; flex:none;` })}<span class="ck-sub">Nothing needs doing. Jobs like a low battery or a filter due show here, and go by themselves once they're done.</span></div>`;
      hydrateIcons(this);
      return;
    }
    this._list.innerHTML =
      tasks
        .map(({ h }, i) => {
          const tag = all || h.who.toLowerCase() === 'everyone' ? `<span class="ck-chip ht-who" style="color:var(--secondary-text-color); background:rgba(127,127,127,0.16);"></span>` : '';
          return `<div class="ht-row" style="display:flex; align-items:center; gap:10px; padding:9px 0;${i ? ' border-top:1px solid var(--divider-color, rgba(127,127,127,0.22));' : ''}">
            ${iconHtml(h.icon, { size: '22px', style: `color:${colour}; flex:none;` })}
            <div style="flex:1; min-width:0;">
              <div class="ht-name" style="font-size:0.95rem; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;"></div>
              <div class="ht-sub ck-sub" style="font-size:0.74rem; line-height:1.35;"></div>
            </div>${tag}
          </div>`;
        })
        .join('') + `<div class="ck-sub" style="font-size:0.72rem; padding-top:6px;">These go by themselves once the device reports they're done.</div>`;
    this._list.querySelectorAll('.ht-row').forEach((el, i) => {
      const { t, h } = tasks[i];
      el.querySelector('.ht-name').textContent = t.summary;
      el.querySelector('.ht-sub').textContent = [h.kind, h.detail].filter(Boolean).join(' · ');
      const who = el.querySelector('.ht-who');
      if (who) who.textContent = h.who;
    });
    hydrateIcons(this);
  }

  getCardSize() {
    return 3;
  }

  getGridOptions() {
    return { columns: 12, min_columns: 6, rows: 'auto' };
  }

  static getConfigElement() {
    return document.createElement(`house-tasks-card-editor${SUFFIX}`);
  }

  static getStubConfig() {
    return { title: 'From the house' };
  }
}

export function registerHouseTasksCard() {
  if (!customElements.get(`house-tasks-card-editor${SUFFIX}`)) customElements.define(`house-tasks-card-editor${SUFFIX}`, HouseTasksCardEditor);
  if (!customElements.get(`house-tasks-card${SUFFIX}`)) customElements.define(`house-tasks-card${SUFFIX}`, HouseTasksCard);
  window.customCards = window.customCards || [];
  window.customCards.push({
    type: `house-tasks-card${SUFFIX}`,
    name: `House Tasks Card${LABEL}`,
    description: 'Jobs the house has spotted (batteries, filters, devices), which clear themselves once done',
    preview: true,
    documentationURL: 'https://github.com/J45PER/church-drive-cards#readme',
  });
}
