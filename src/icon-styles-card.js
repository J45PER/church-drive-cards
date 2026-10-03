// Icon Styles Card (Manager): which icon each mode gets on the fan, air
// purifier, thermostat, car charger, blind and CO alarm cards. Pick an icon
// for a mode and it changes on every card and in the Android app; clear the
// field to go back to the built-in icon. The list is kept by the Church Drive
// integration (icons.py) and read by the cards through icon-library.js. Only
// administrators can change icons.

import { createFormEditor } from './form-editor.js';
import { SUFFIX, LABEL } from './suffix.js';
import { kitShell, kitHead, kitEsc } from './card-kit.js';
import { loadIcons, iconSlots, iconLabel, onIconsChanged } from './icon-library.js';

const IS_CSS = `
  .is-group { margin-bottom:14px; }
  .is-group h4 { margin:0 0 6px; font-size:0.95rem; font-weight:700; }
  .is-note { font-size:0.78rem; color:var(--secondary-text-color); line-height:1.4; margin-top:8px; }
`;

export const IconStylesCardEditor = createFormEditor({
  schema: () => [{ name: 'title', selector: { text: {} } }],
  labels: { title: 'Title (optional)' },
  helpers: { title: 'Pick an icon for each mode. It changes on every card and in the Android app.' },
});

export class IconStylesCard extends HTMLElement {
  setConfig(config) {
    this.config = config || {};
    this._built = false;
  }

  set hass(hass) {
    this._hass = hass;
    loadIcons(hass);
    if (!this._off) this._off = onIconsChanged(() => this.isConnected && this._render(true));
    this._render();
  }

  disconnectedCallback() {
    if (this._off) this._off();
    this._off = null;
  }

  _render(refresh = false) {
    if (!this._hass) return;
    const c = this.config;
    if (!this._built) {
      this.innerHTML = kitShell(`<div class="is-body"></div>`, IS_CSS);
      this._body = this.querySelector('.is-body');
      this._built = true;
      this._shown = null;
    }
    this.querySelector('.ck-headrow').style.display = c.title ? '' : 'none';
    if (c.title) kitHead(this, c.title, '', 'var(--primary-text-color)');
    const { icons, defaults, groups } = iconSlots();
    const names = Object.keys(icons);
    const admin = !!(this._hass.user && this._hass.user.is_admin);
    if (!names.length) {
      if (this._shown !== 'none') this._body.innerHTML = `<div class="is-note">Icons need the latest Church Drive integration (and a restart).</div>`;
      this._shown = 'none';
      return;
    }
    const sig = JSON.stringify(icons) + admin;
    if (this._shown === sig && !refresh) return;
    if (this._shown && !refresh && this._shown !== 'none') return; // the pickers hold what's being typed
    this._shown = sig;
    this._body.innerHTML = '';
    names.forEach((group) => {
      const box = document.createElement('div');
      box.className = 'is-group';
      box.innerHTML = `<h4>${kitEsc(groups[group] || group)}</h4>`;
      const keys = Object.keys(icons[group]);
      const form = document.createElement('ha-form');
      form.hass = this._hass;
      form.schema = keys.map((k) => ({ name: k, selector: { icon: { placeholder: defaults[group][k] } } }));
      form.data = { ...icons[group] };
      form.disabled = !admin;
      form.computeLabel = (s) => iconLabel(s.name);
      form.computeHelper = (s) => `Built-in: ${defaults[group][s.name]}`;
      form.addEventListener('value-changed', (ev) => {
        const next = ev.detail.value || {};
        keys.forEach((k) => {
          if (next[k] === icons[group][k]) return;
          // Clearing the field puts the built-in icon back.
          this._hass.callWS({ type: 'church_drive/icons/set', group, key: k, icon: next[k] || null }).catch(() => {});
        });
      });
      box.appendChild(form);
      this._body.appendChild(box);
    });
    const note = document.createElement('div');
    note.className = 'is-note';
    note.textContent = admin
      ? 'Clear a field to go back to the built-in icon. Changes show on every dashboard and in the Android app.'
      : 'Only administrators can change icons.';
    this._body.appendChild(note);
  }

  getCardSize() {
    return 8;
  }

  getGridOptions() {
    return { columns: 'full', rows: 'auto' };
  }

  static getConfigElement() {
    return document.createElement(`icon-styles-card-editor${SUFFIX}`);
  }

  static getStubConfig() {
    return {};
  }
}

export function registerIconStylesCard() {
  if (!customElements.get(`icon-styles-card-editor${SUFFIX}`)) customElements.define(`icon-styles-card-editor${SUFFIX}`, IconStylesCardEditor);
  if (!customElements.get(`icon-styles-card${SUFFIX}`)) customElements.define(`icon-styles-card${SUFFIX}`, IconStylesCard);
  window.customCards = window.customCards || [];
  window.customCards.push({
    type: `icon-styles-card${SUFFIX}`,
    name: `Icon Styles Card${LABEL}`,
    description: 'The icon for each mode on the fan, air purifier, thermostat, charger, blind and CO cards, and in the Android app',
    preview: false,
    documentationURL: 'https://github.com/J45PER/church-drive-cards#readme',
  });
}
