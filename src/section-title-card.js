// Section Title Card: a section heading for the dashboards. A larger title
// than HA's heading card, an icon in the section's colour, and an optional
// live summary on the right (a Home Assistant template, e.g. "1 room on").
// Pairs with a section background of the same colour.

import { createFormEditor } from './form-editor.js';
import { iconHtml, hydrateIcons } from './icons.js';
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

// Render a template live, as HA's markdown card does; `done` gets the text.
// Returns the unsubscribe promise, or null for plain text (sent straight away).
export function stcRender(hass, template, done) {
  if (!template || !hass || !hass.connection) return null;
  if (!/[{%]/.test(template)) {
    done(template);
    return null;
  }
  return hass.connection
    .subscribeMessage(
      (msg) => {
        if (msg.result !== undefined) done(String(msg.result).trim());
      },
      { type: 'render_template', template, strict: false, report_errors: false }
    )
    .catch(() => null);
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
  ],
  labels: {
    title: 'Title',
    icon: 'Icon (optional)',
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
      </div>`;
    this.querySelector('.stc-title').textContent = c.title;
    this._summaryEl = this.querySelector('.stc-summary');
    this._iconEl = this.querySelector('.stc-icon');
    if (this._summary) this._summaryEl.textContent = this._summary;
    hydrateIcons(this);
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
      if (this._iconEl) this._iconEl.style.color = css;
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
