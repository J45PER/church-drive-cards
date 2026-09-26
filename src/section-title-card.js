// Section Title Card: a section heading for the dashboards. A larger title
// than HA's heading card, an icon in the section's colour, and an optional
// live summary on the right (a Home Assistant template, e.g. "1 room on").
// Pairs with a section background of the same colour.

import { createFormEditor } from './form-editor.js';
import { iconHtml, hydrateIcons } from './icons.js';
import { SUFFIX, LABEL } from './suffix.js';

// A colour as HA's colour picker gives it ("green", "deep-orange") or any CSS
// colour ("#43a047").
export function stcColor(color) {
  if (!color) return 'var(--primary-text-color)';
  return /^(#|rgb|hsl|var\()/.test(color) ? color : `var(--${color}-color, ${color})`;
}

export const SectionTitleCardEditor = createFormEditor({
  schema: () => [
    { name: 'title', selector: { text: {} } },
    { name: 'icon', selector: { icon: {} } },
    { name: 'color', selector: { ui_color: {} } },
    { name: 'summary', selector: { template: {} } },
  ],
  labels: {
    title: 'Title',
    icon: 'Icon (optional)',
    color: 'Colour (for the icon; use the same for the section background)',
    summary: 'Summary on the right (optional template)',
  },
  helpers: {
    summary: `A Home Assistant template, e.g. {{ states('vacuum.gregg') | title }}`,
  },
});

export class SectionTitleCard extends HTMLElement {
  setConfig(config) {
    if (!config.title) throw new Error('title required');
    const changed = !this.config || this.config.summary !== config.summary;
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
    const color = stcColor(c.color);
    this.innerHTML = `
      <div style="display:flex; align-items:center; gap:10px; padding:2px 4px 2px 4px; min-height:40px;">
        ${c.icon ? iconHtml(c.icon, { size: '26px', style: `color:${color}; flex:none;` }) : ''}
        <div class="stc-title" style="flex:1; min-width:0; font-size:1.6rem; font-weight:500; line-height:1.2; color:var(--primary-text-color); white-space:nowrap; overflow:hidden; text-overflow:ellipsis;"></div>
        <div class="stc-summary" style="flex:none; max-width:55%; font-size:0.9rem; color:var(--secondary-text-color); white-space:nowrap; overflow:hidden; text-overflow:ellipsis; text-align:right;"></div>
      </div>`;
    this.querySelector('.stc-title').textContent = c.title;
    this._summaryEl = this.querySelector('.stc-summary');
    if (this._summary) this._summaryEl.textContent = this._summary;
    hydrateIcons(this);
  }

  _unsubscribe() {
    if (this._unsub) {
      this._unsub.then((unsub) => unsub()).catch(() => {});
      this._unsub = null;
    }
  }

  // Render the summary template live, as HA's own markdown card does.
  _subscribe() {
    this._unsubscribe();
    this._summary = '';
    if (this._summaryEl) this._summaryEl.textContent = '';
    const template = this.config && this.config.summary;
    if (!template || !this._hass || !this._hass.connection) return;
    if (!/[{%]/.test(template)) {
      this._summary = template;
      if (this._summaryEl) this._summaryEl.textContent = template;
      return;
    }
    this._unsub = this._hass.connection
      .subscribeMessage(
        (msg) => {
          if (msg.result === undefined) return;
          this._summary = String(msg.result).trim();
          if (this._summaryEl) this._summaryEl.textContent = this._summary;
        },
        { type: 'render_template', template, strict: false, report_errors: false }
      )
      .catch(() => null);
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
