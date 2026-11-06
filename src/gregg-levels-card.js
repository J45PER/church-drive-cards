// Gregg Levels Card: the vacuum's level selects (suction, cleaning intensity,
// mop intensity, water) as rows of option buttons, each drawn with the house's
// own level icon (the Eufy-style turbine and water drops) and the current one
// lit. Tap a button to change the select. Home Assistant's own tile can only
// show one fixed icon for a select, so this card draws the icon for each level.

import { createFormEditor } from './form-editor.js';
import { iconHtml, hydrateIcons } from './icons.js';
import { SUFFIX, LABEL } from './suffix.js';
import { GREGG_LEVEL_ENTITIES, ggLevelIcon, ggRowIcon } from './gregg-levels-logic.js';
import { kitShell, kitEsc, kitCap } from './card-kit.js';

function ggDemo() {
  return [
    { id: 'select.gregg_suction_level', name: 'Suction', state: 'Turbo', options: ['Quiet', 'Standard', 'Turbo', 'Max', 'Boost_IQ'] },
    { id: 'select.gregg_cleaning_intensity', name: 'Cleaning intensity', state: 'Normal', options: ['Quick', 'Normal', 'Narrow'] },
    { id: 'select.gregg_mop_intensity', name: 'Mop intensity', state: 'Low', options: ['Low', 'Medium', 'High'] },
  ];
}

const GG_CSS = `
  .gg-block + .gg-block { border-top:1px solid var(--divider-color, rgba(127,127,127,0.18)); padding-top:10px; margin-top:10px; }
  .gg-head { display:flex; align-items:center; gap:10px; margin-bottom:8px; cursor:pointer; }
  .gg-name { font-weight:600; font-size:0.95rem; }
  .gg-state { font-size:0.8rem; color:var(--secondary-text-color); }
  .gg-opts { display:flex; gap:6px; flex-wrap:wrap; }
  .gg-opt { flex:1 1 0; min-width:56px; display:flex; flex-direction:column; align-items:center; gap:4px; padding:8px 4px; border:none; border-radius:12px; cursor:pointer;
    background:rgba(127,127,127,0.14); color:var(--primary-text-color); font:inherit; font-size:11px; font-weight:600; }
  .gg-opt.gg-on { background:var(--gg-color, #2196f3); color:#fff; }
  .gg-opt:disabled { opacity:.4; cursor:default; }
`;

export const GreggLevelsCardEditor = createFormEditor({
  schema: () => [
    { name: 'title', selector: { text: {} } },
    { name: 'entities', selector: { entity: { domain: 'select', multiple: true } } },
    { name: 'color', selector: { text: {} } },
    { name: 'demo', selector: { boolean: {} } },
  ],
  labels: { title: 'Title (optional)', entities: "Selects (empty for Gregg's suction, cleaning intensity and mop intensity)", color: 'Colour (optional)', demo: 'Show pretend levels (for Design Presets)' },
});

export class GreggLevelsCard extends HTMLElement {
  setConfig(config) {
    this.config = config || {};
    this._built = false;
    this._sig = null;
  }

  set hass(hass) {
    this._hass = hass;
    this._render();
  }

  _items() {
    if (this.config.demo) return ggDemo();
    const s = (this._hass && this._hass.states) || {};
    const ids = this.config.entities && this.config.entities.length ? this.config.entities : GREGG_LEVEL_ENTITIES;
    return ids
      .filter((id) => s[id])
      .map((id) => {
        const st = s[id];
        const name = String(st.attributes.friendly_name || id).replace(/^Gregg\s+/i, '');
        return { id, name, state: st.state, options: st.attributes.options || [], unavailable: st.state === 'unavailable' || st.state === 'unknown' };
      });
  }

  _render() {
    if (!this._hass && !this.config.demo) return;
    const c = this.config;
    if (!this._built) {
      this.innerHTML = kitShell(`<div class="gg-list"></div>`, GG_CSS);
      this._list = this.querySelector('.gg-list');
      this._list.addEventListener('click', (ev) => {
        const opt = ev.target.closest('[data-opt]');
        if (opt) {
          if (!c.demo) this._hass.callService('select', 'select_option', { entity_id: opt.dataset.id, option: opt.dataset.opt });
          return;
        }
        const head = ev.target.closest('[data-more]');
        if (head && !c.demo) this.dispatchEvent(new CustomEvent('hass-more-info', { detail: { entityId: head.dataset.more }, bubbles: true, composed: true }));
      });
      this._built = true;
    }
    const color = c.color || '#2196f3';
    this.querySelector('.ck-card').style.setProperty('--gg-color', color);
    this.querySelector('.ck-headrow').style.display = c.title ? '' : 'none';
    if (c.title) {
      this.querySelector('.ck-title').textContent = c.title;
      this.querySelector('.ck-word').textContent = '';
    }
    const items = this._items();
    const sig = JSON.stringify(items);
    if (sig === this._sig) return;
    this._sig = sig;
    this._list.innerHTML = items.length
      ? items
          .map(
            (i) => `<div class="gg-block">
              <div class="gg-head" data-more="${kitEsc(i.id)}" role="button" tabindex="0">
                <span style="display:inline-flex; color:${color};">${iconHtml(ggRowIcon(i.id, i.state, i.options), { size: '28px' })}</span>
                <div><div class="gg-name">${kitEsc(i.name)}</div><div class="gg-state">${kitEsc(i.unavailable ? 'Not available' : kitCap(i.state))}</div></div>
              </div>
              <div class="gg-opts">${i.options
                .map(
                  (o) => `<button class="gg-opt${o === i.state ? ' gg-on' : ''}" data-id="${kitEsc(i.id)}" data-opt="${kitEsc(o)}" ${i.unavailable ? 'disabled' : ''} aria-pressed="${o === i.state}">
                    ${iconHtml(ggLevelIcon(i.id, o, i.options), { size: '24px' })}<span>${kitEsc(kitCap(o))}</span></button>`
                )
                .join('')}</div>
            </div>`
          )
          .join('')
      : `<div class="ck-sub">No level selects found.</div>`;
    hydrateIcons(this);
  }

  getCardSize() {
    return 5;
  }

  getGridOptions() {
    return { columns: 'full', rows: 'auto' };
  }

  static getConfigElement() {
    return document.createElement(`gregg-levels-card-editor${SUFFIX}`);
  }

  static getStubConfig() {
    return {};
  }
}

export function registerGreggLevelsCard() {
  if (!customElements.get(`gregg-levels-card-editor${SUFFIX}`)) customElements.define(`gregg-levels-card-editor${SUFFIX}`, GreggLevelsCardEditor);
  if (!customElements.get(`gregg-levels-card${SUFFIX}`)) customElements.define(`gregg-levels-card${SUFFIX}`, GreggLevelsCard);
  window.customCards = window.customCards || [];
  window.customCards.push({
    type: `gregg-levels-card${SUFFIX}`,
    name: `Gregg Levels Card${LABEL}`,
    description: "Gregg's suction, cleaning intensity and mop levels as buttons, each drawn with the house's own level icon",
    preview: true,
    documentationURL: 'https://github.com/J45PER/church-drive-cards#readme',
  });
}
