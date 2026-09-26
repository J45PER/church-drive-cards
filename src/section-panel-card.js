// Section Panel Card: a group of cards on a faint panel in the section's
// colour, headed by a Section Title (large title, coloured icon, live
// summary). Several can sit in one dashboard section, so panels of different
// heights stack without the gaps HA's row-aligned section backgrounds leave.

import { createFormEditor } from './form-editor.js';
import { SUFFIX, LABEL } from './suffix.js';
import { stcColor } from './section-title-card.js';

let helpersPromise;
function cardHelpers() {
  if (!helpersPromise) helpersPromise = window.loadCardHelpers ? window.loadCardHelpers() : Promise.reject(new Error('no card helpers'));
  return helpersPromise;
}

const PanelFields = createFormEditor({
  schema: () => [
    { name: 'title', selector: { text: {} } },
    { name: 'icon', selector: { icon: {} } },
    { name: 'color', selector: { ui_color: {} } },
    { name: 'summary', selector: { template: {} } },
  ],
  labels: {
    title: 'Title',
    icon: 'Icon (optional)',
    color: 'Colour (icon and panel)',
    summary: 'Summary on the right (optional template)',
  },
  helpers: {
    summary: `A Home Assistant template, e.g. {{ states('vacuum.gregg') | title }}`,
  },
});

// The panel's own fields, then HA's stack editor for the cards inside it.
export class SectionPanelCardEditor extends HTMLElement {
  setConfig(config) {
    this._config = config;
    this._render();
  }

  set hass(hass) {
    this._hass = hass;
    this._render();
  }

  set lovelace(lovelace) {
    this._lovelace = lovelace;
    if (this._stack) this._stack.lovelace = lovelace;
  }

  _emit(config) {
    this._config = config;
    this.dispatchEvent(new CustomEvent('config-changed', { detail: { config }, bubbles: true, composed: true }));
  }

  async _render() {
    if (!this._config || !this._hass) return;
    if (!this._fields) {
      this._fields = document.createElement(`section-panel-fields${SUFFIX}`);
      this._fields.addEventListener('config-changed', (ev) => {
        ev.stopPropagation();
        const { cards, ...fields } = ev.detail.config;
        this._emit({ ...this._config, ...fields, cards: this._config.cards || [] });
      });
      const label = document.createElement('div');
      label.textContent = 'Cards in this panel';
      label.style.cssText = 'margin:20px 0 8px; font-weight:500;';
      this.append(this._fields, label);
    }
    const { cards, ...fields } = this._config;
    this._fields.hass = this._hass;
    this._fields.setConfig(fields);
    if (!this._stack && !this._stackLoading) {
      this._stackLoading = true;
      try {
        const helpers = await cardHelpers();
        helpers.createCardElement({ type: 'vertical-stack', cards: [] });
        await customElements.whenDefined('hui-vertical-stack-card');
        this._stack = await customElements.get('hui-vertical-stack-card').getConfigElement();
        this._stack.addEventListener('config-changed', (ev) => {
          ev.stopPropagation();
          this._emit({ ...this._config, cards: ev.detail.config.cards || [] });
        });
        this.appendChild(this._stack);
      } catch (err) {
        const note = document.createElement('p');
        note.textContent = 'The card list editor could not load; use the code editor to change the cards.';
        this.appendChild(note);
      }
      this._stackLoading = false;
    }
    if (this._stack) {
      this._stack.hass = this._hass;
      if (this._lovelace) this._stack.lovelace = this._lovelace;
      this._stack.setConfig({ type: 'vertical-stack', cards: this._config.cards || [] });
    }
  }
}

export class SectionPanelCard extends HTMLElement {
  setConfig(config) {
    if (!config.title) throw new Error('title required');
    this.config = config;
    this._built = false;
    this._build();
  }

  set hass(hass) {
    this._hass = hass;
    if (this._title) this._title.hass = hass;
    (this._cards || []).forEach((card) => {
      card.hass = hass;
    });
  }

  _build() {
    const c = this.config;
    const color = stcColor(c.color);
    this.innerHTML = `
      <div class="spc-panel" style="position:relative; border-radius:24px; padding:12px; display:flex; flex-direction:column; gap:8px; isolation:isolate;">
        <div style="position:absolute; inset:0; border-radius:inherit; background:${color}; opacity:0.1; z-index:-1; pointer-events:none;"></div>
      </div>`;
    const panel = this.querySelector('.spc-panel');
    this._title = document.createElement(`section-title-card${SUFFIX}`);
    this._title.setConfig({ title: c.title, icon: c.icon, color: c.color, summary: c.summary });
    if (this._hass) this._title.hass = this._hass;
    panel.appendChild(this._title);
    const token = (this._token = {});
    this._cards = [];
    cardHelpers()
      .then((helpers) => {
        if (token !== this._token) return;
        (c.cards || []).forEach((conf) => {
          const el = helpers.createCardElement(conf);
          if (this._hass) el.hass = this._hass;
          // A card that fails to load (e.g. a custom card still downloading)
          // asks to be rebuilt.
          el.addEventListener('ll-rebuild', (ev) => {
            ev.stopPropagation();
            const fresh = helpers.createCardElement(conf);
            if (this._hass) fresh.hass = this._hass;
            el.replaceWith(fresh);
            this._cards[this._cards.indexOf(el)] = fresh;
          });
          this._cards.push(el);
          panel.appendChild(el);
        });
      })
      .catch(() => {});
  }

  getCardSize() {
    return 1 + (this._cards || []).reduce((n, card) => n + (card.getCardSize ? Number(card.getCardSize()) || 1 : 1), 0);
  }

  getGridOptions() {
    return { columns: 'full', rows: 'auto' };
  }

  static getConfigElement() {
    return document.createElement(`section-panel-card-editor${SUFFIX}`);
  }

  static getStubConfig() {
    return { title: 'Lights', icon: 'mdi:lightbulb', color: 'amber', cards: [] };
  }
}

export function registerSectionPanelCard() {
  if (!customElements.get(`section-panel-fields${SUFFIX}`)) {
    customElements.define(`section-panel-fields${SUFFIX}`, PanelFields);
  }
  if (!customElements.get(`section-panel-card-editor${SUFFIX}`)) {
    customElements.define(`section-panel-card-editor${SUFFIX}`, SectionPanelCardEditor);
  }
  if (!customElements.get(`section-panel-card${SUFFIX}`)) {
    customElements.define(`section-panel-card${SUFFIX}`, SectionPanelCard);
  }
  window.customCards = window.customCards || [];
  window.customCards.push({
    type: `section-panel-card${SUFFIX}`,
    name: `Section Panel Card${LABEL}`,
    description: 'A group of cards on a coloured panel with a large title, icon and live summary',
    preview: false,
    documentationURL: 'https://github.com/J45PER/church-drive-cards#readme',
  });
}
