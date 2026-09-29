// Section Title Card: a section heading for the dashboards. A larger title
// than HA's heading card, an icon in the section's colour, and an optional
// live summary on the right (a Home Assistant template, e.g. "1 room on").
// Pairs with a section background of the same colour.

import { createFormEditor } from './form-editor.js';
import { iconHtml, hydrateIcons } from './icons.js';
import { kitNavigate } from './card-kit.js';
import { SUFFIX, LABEL } from './suffix.js';

// A colour as HA's colour picker gives it ("green", "deep-orange") or any CSS
// colour ("#43a047").
const STC_FALLBACK = {
  red: '#f44336', pink: '#e91e63', purple: '#926bc7', 'deep-purple': '#6e41ab', indigo: '#3f51b5',
  blue: '#2196f3', 'light-blue': '#03a9f4', cyan: '#00bcd4', teal: '#009688', green: '#4caf50',
  'light-green': '#8bc34a', lime: '#cddc39', yellow: '#ffeb3b', amber: '#ffc107', orange: '#ff9800',
  'deep-orange': '#ff5722', brown: '#795548', grey: '#9e9e9e', 'blue-grey': '#607d8b',
};
export function stcColor(color) {
  if (!color) return 'var(--primary-text-color)';
  if (/^(#|rgb|hsl|var\()/.test(color)) return color;
  return `var(--${color}-color, ${STC_FALLBACK[color] || color})`;
}

// Last result of each template, kept in memory and on the device, so a card
// that's rebuilt (changing page, the app waking up) starts from what it last
// showed instead of flickering through its fixed colour or icon first.
const TPL_KEY = 'cd-tpl-cache';
let tplCache = null;
let tplSave = 0;
function tplStore() {
  if (tplCache) return tplCache;
  tplCache = new Map();
  try {
    const saved = JSON.parse(localStorage.getItem(TPL_KEY) || '[]');
    if (Array.isArray(saved)) saved.forEach(([k, v]) => tplCache.set(k, v));
  } catch (err) {
    /* storage blocked or bad data: start empty */
  }
  return tplCache;
}
function tplRemember(template, value) {
  const store = tplStore();
  if (store.get(template) === value) return;
  store.delete(template);
  store.set(template, value);
  while (store.size > 300) store.delete(store.keys().next().value);
  clearTimeout(tplSave);
  tplSave = setTimeout(() => {
    try {
      localStorage.setItem(TPL_KEY, JSON.stringify([...store]));
    } catch (err) {
      /* storage full or blocked: memory still works */
    }
  }, 1000);
}

// Render a template live, as HA's markdown card does; `done` gets the text,
// straight away with the last known result if there is one.
// Returns the unsubscribe promise, or null for plain text (sent straight away).
export function stcRender(hass, template, done) {
  if (!template || !hass || !hass.connection) return null;
  if (!/[{%]/.test(template)) {
    done(template);
    return null;
  }
  const store = tplStore();
  if (store.has(template)) done(store.get(template));
  return hass.connection
    .subscribeMessage(
      (msg) => {
        if (msg.result === undefined) return;
        const text = String(msg.result).trim();
        tplRemember(template, text);
        done(text);
      },
      { type: 'render_template', template, strict: false, report_errors: false }
    )
    .catch(() => null);
}

// Set a style, skipping its transition when `instant` (a card's first colour
// shouldn't fade in from the default).
export function stcSetInstantly(el, prop, value, instant) {
  if (!instant) {
    el.style[prop] = value;
    return;
  }
  const t = el.style.transition;
  el.style.transition = 'none';
  el.style[prop] = value;
  void el.offsetWidth;
  el.style.transition = t;
}

export const STC_COLOR_TEMPLATE_FIELD = { name: 'color_template', selector: { template: {} } };
export const STC_COLOR_TEMPLATE_LABEL = 'Colour from a template (optional; overrides the colour)';
export const STC_COLOR_TEMPLATE_HELPER =
  "Gives a colour name or code, e.g. {{ 'red' if is_state('alarm_control_panel.house', 'armed_away') else 'green' }}";

export const SectionTitleCardEditor = createFormEditor({
  schema: () => [
    { name: 'title', selector: { text: {} } },
    { name: 'icon', selector: { icon: {} } },
    { name: 'color', selector: { ui_color: {} } },
    STC_COLOR_TEMPLATE_FIELD,
    { name: 'summary', selector: { template: {} } },
    { name: 'link', selector: { navigation: {} } },
  ],
  labels: {
    title: 'Title',
    icon: 'Icon (optional)',
    link: 'Tapping the title opens (optional page)',
    color: 'Colour (for the icon)',
    color_template: STC_COLOR_TEMPLATE_LABEL,
    summary: 'Summary on the right (optional template)',
  },
  helpers: {
    color_template: STC_COLOR_TEMPLATE_HELPER,
    summary: `A Home Assistant template, e.g. {{ states('vacuum.gregg') | title }}`,
  },
});

export class SectionTitleCard extends HTMLElement {
  setConfig(config) {
    if (!config.title) throw new Error('title required');
    const changed =
      !this.config || this.config.summary !== config.summary || this.config.color_template !== config.color_template;
    this.config = config;
    this._build();
    if (changed) this._subscribe();
  }

  set hass(hass) {
    const first = !this._hass;
    this._hass = hass;
    if (first) this._subscribe();
  }

  connectedCallback() {
    if (this._hass && !this._unsub) this._subscribe();
  }

  disconnectedCallback() {
    this._unsubscribe();
  }

  _build() {
    const c = this.config;
    const color = stcColor(this._liveColor || c.color);
    this.innerHTML = `
      <div style="display:flex; align-items:center; gap:10px; padding:2px 4px 2px 4px; min-height:40px;">
        ${c.icon ? iconHtml(c.icon, { size: '26px', style: `color:${color}; flex:none; transition:color .6s ease;`, cls: 'stc-icon' }) : ''}
        <div class="stc-title" style="flex:1; min-width:0; font-size:1.6rem; font-weight:500; line-height:1.2; color:var(--primary-text-color); white-space:nowrap; overflow:hidden; text-overflow:ellipsis;"></div>
        <div class="stc-summary" style="flex:none; max-width:55%; font-size:0.9rem; color:var(--secondary-text-color); white-space:nowrap; overflow:hidden; text-overflow:ellipsis; text-align:right;"></div>
        ${c.link ? iconHtml('mdi:chevron-right', { size: '22px', style: 'flex:none; margin-left:-4px; color:var(--secondary-text-color);' }) : ''}
        ${c.collapsible ? `<button class="stc-tog" type="button" aria-label="Show less" aria-expanded="true" style="flex:none; width:34px; height:34px; margin:-6px -6px -6px -2px; border:none; border-radius:50%; background:transparent; color:var(--secondary-text-color); cursor:pointer; display:flex; align-items:center; justify-content:center; padding:0;">${iconHtml('mdi:chevron-down', { size: '24px', style: 'transition:transform .2s ease;', cls: 'stc-chev' })}</button>` : ''}
      </div>`;
    const row = this.firstElementChild;
    // The ⌄ asks the Section Panel to switch between open and compact.
    const toggle = () => this.dispatchEvent(new CustomEvent('stc-toggle', { bubbles: true }));
    this._tog = this.querySelector('.stc-tog');
    if (this._tog) {
      this._tog.addEventListener('click', (ev) => {
        ev.stopPropagation();
        toggle();
      });
      this.setOpen(this._open !== false);
    }
    if (c.link) {
      row.style.cursor = 'pointer';
      row.setAttribute('role', 'link');
      row.tabIndex = 0;
      const go = () => kitNavigate(c.link);
      row.addEventListener('click', go);
      row.addEventListener('keydown', (ev) => ev.target === row && (ev.key === 'Enter' || ev.key === ' ') && go());
    } else if (c.collapsible) {
      row.style.cursor = 'pointer';
      row.addEventListener('click', toggle);
    }
    this.querySelector('.stc-title').textContent = c.title;
    this._summaryEl = this.querySelector('.stc-summary');
    this._iconEl = this.querySelector('.stc-icon');
    if (this._summary) this._summaryEl.textContent = this._summary;
    hydrateIcons(this);
  }

  // Point the ⌄ down (open) or right (compact).
  setOpen(open) {
    this._open = open;
    if (!this._tog) return;
    this._tog.setAttribute('aria-expanded', String(open));
    this._tog.setAttribute('aria-label', open ? 'Show less' : 'Show more');
    const chev = this._tog.querySelector('.stc-chev');
    if (chev) chev.style.transform = open ? '' : 'rotate(-90deg)';
  }

  _unsubscribe() {
    [this._unsub, this._unsubColor].forEach((p) => p && p.then((unsub) => unsub && unsub()).catch(() => {}));
    this._unsub = null;
    this._unsubColor = null;
  }

  // The summary, and the colour when it comes from a template. A new colour is
  // also announced (stc-color) for a Section Panel to tint its background.
  _subscribe() {
    this._unsubscribe();
    this._summary = '';
    if (this._summaryEl) this._summaryEl.textContent = '';
    if (!this.config || !this._hass) return;
    this._unsub = stcRender(this._hass, this.config.summary, (text) => {
      this._summary = text;
      if (this._summaryEl) this._summaryEl.textContent = text;
    });
    this._liveColor = null;
    this._unsubColor = stcRender(this._hass, this.config.color_template, (color) => {
      this._liveColor = color || null;
      const css = stcColor(this._liveColor || this.config.color);
      if (this._iconEl) stcSetInstantly(this._iconEl, 'color', css, !this._colorShown);
      this._colorShown = true;
      this.dispatchEvent(new CustomEvent('stc-color', { detail: css, bubbles: true }));
    });
  }

  getCardSize() {
    return 1;
  }

  getGridOptions() {
    return { columns: 'full', rows: 'auto' };
  }

  static getConfigElement() {
    return document.createElement(`section-title-card-editor${SUFFIX}`);
  }

  static getStubConfig() {
    return { title: 'Lights', icon: 'mdi:lightbulb', color: 'amber' };
  }
}

export function registerSectionTitleCard() {
  if (!customElements.get(`section-title-card-editor${SUFFIX}`)) {
    customElements.define(`section-title-card-editor${SUFFIX}`, SectionTitleCardEditor);
  }
  if (!customElements.get(`section-title-card${SUFFIX}`)) {
    customElements.define(`section-title-card${SUFFIX}`, SectionTitleCard);
  }
  window.customCards = window.customCards || [];
  window.customCards.push({
    type: `section-title-card${SUFFIX}`,
    name: `Section Title Card${LABEL}`,
    description: 'A large section title with a coloured icon and a live summary',
    preview: true,
    documentationURL: 'https://github.com/J45PER/church-drive-cards#readme',
  });
}
