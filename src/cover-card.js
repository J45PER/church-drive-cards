// Blind Card: a blind, curtain, shutter or garage door (any cover).
// Title in the cover's colour with its state ("Open 60%", "Closed", "Position
// unknown"), a big icon with the position bar when the cover reports one,
// and Open / Stop / Close tiles like the alarm card's buttons: grey until
// selected, the selected one filled. A cover that only assumes its state
// (e.g. an RF blind) shows the last command instead.

import { createFormEditor } from './form-editor.js';
import { iconHtml, hydrateIcons } from './icons.js';
import { SUFFIX, LABEL } from './suffix.js';
import { KIT_COLOR, kitShell, kitHead, kitTiles, kitMoreInfo, kitHealthBanner } from './card-kit.js';

const COVER_ICONS = {
  curtain: ['mdi:curtains', 'mdi:curtains-closed'],
  shutter: ['mdi:window-shutter-open', 'mdi:window-shutter'],
  garage: ['mdi:garage-open', 'mdi:garage'],
  door: ['mdi:door-open', 'mdi:door-closed'],
  window: ['mdi:window-open', 'mdi:window-closed'],
  awning: ['mdi:awning-outline', 'mdi:awning-outline'],
  blind: ['mdi:blinds-horizontal', 'mdi:blinds-horizontal-closed'],
};

function coverDemo(config) {
  const kind = config.demo_state || 'unknown';
  return {
    entity_id: 'cover.demo',
    state: kind === 'unknown' ? 'unknown' : kind,
    attributes: {
      friendly_name: 'Blind',
      device_class: 'blind',
      supported_features: kind === 'unknown' ? 11 : 15,
      assumed_state: kind === 'unknown',
      ...(kind === 'open' ? { current_position: 60 } : kind === 'closed' ? { current_position: 0 } : {}),
    },
  };
}

export const CoverCardEditor = createFormEditor({
  schema: (config) => [
    ...(config.demo ? [] : [{ name: 'entity', selector: { entity: { domain: 'cover' } } }]),
    { name: 'name', selector: { text: {} } },
    { name: 'subtitle', selector: { text: {} } },
    { name: 'icon', selector: { icon: {} } },
    { name: 'show_position', selector: { boolean: {} }, default: true },
    {
      type: 'expandable',
      name: '',
      title: 'Demo mode (a pretend blind, for Design Presets)',
      flatten: true,
      schema: [
        { name: 'demo', selector: { boolean: {} } },
        {
          name: 'demo_state',
          selector: {
            select: {
              mode: 'dropdown',
              options: [
                { value: 'unknown', label: 'Position unknown (like an RF blind)' },
                { value: 'open', label: 'Open 60%' },
                { value: 'closed', label: 'Closed' },
              ],
            },
          },
        },
      ],
    },
  ],
  labels: {
    entity: 'Blind, curtain or other cover',
    name: 'Title (optional)',
    subtitle: 'Small text beside the icon (optional, e.g. the room)',
    icon: 'Icon (optional)',
    show_position: 'Position bar (if the cover reports one)',
    demo: 'Use a pretend blind instead of a real one',
    demo_state: 'Pretend blind starts',
  },
});

export class CoverCard extends HTMLElement {
  setConfig(config) {
    if (!config.entity && !config.demo) throw new Error('entity required (or set demo: true)');
    this.config = config;
    this._built = false;
    this._demo = config.demo ? coverDemo(config) : null;
    this._last = null;
  }

  set hass(hass) {
    this._hass = hass;
    this._render();
  }

  _state() {
    return this._demo || (this._hass && this._hass.states[this.config.entity]);
  }

  _render() {
    const st = this._state();
    if (!st || !this._hass) return;
    const c = this.config;
    if (!this._built) {
      this.innerHTML = kitShell(`
        <div style="display:flex; align-items:center; gap:14px;">
          <div class="cv-icon" style="flex:none;"></div>
          <div class="ck-info cv-info"></div>
        </div>
        <div class="cv-pos"></div>
        <div class="ck-row cv-buttons"></div>`);
      // A pop-up only when it adds a control: the position slider.
      this.querySelector('.cv-icon').addEventListener('click', () => {
        const st = this._state();
        if (!this._demo && st && ((st.attributes.supported_features || 0) & 4)) kitMoreInfo(this, c.entity);
      });
      this._built = true;
    }
    const a = st.attributes;
    const pos = a.current_position;
    const known = ['open', 'closed', 'opening', 'closing'].includes(st.state);
    const word =
      st.state === 'unavailable' ? 'Unavailable'
      : !known ? 'Position unknown'
      : st.state === 'open' && pos != null && pos < 100 ? `Open ${pos}%`
      : st.state.charAt(0).toUpperCase() + st.state.slice(1);
    const closed = st.state === 'closed' || (!known && this._last === 'close');
    const color = st.state === 'unavailable' ? KIT_COLOR.off : KIT_COLOR.blind;
    kitHead(this, c.name || a.friendly_name || c.entity, word + (this._demo ? ' · demo' : ''), color);
    kitHealthBanner(this, this._hass, c.entity, !!(this._demo || c.demo));

    const icons = COVER_ICONS[a.device_class] || COVER_ICONS.blind;
    this.querySelector('.cv-icon').style.cursor = !this._demo && ((a.supported_features || 0) & 4) ? 'pointer' : 'default';
    this.querySelector('.cv-icon').innerHTML = iconHtml(c.icon || icons[closed ? 1 : 0], { size: '44px', style: `color:${color};` });
    const lines = [];
    if (c.subtitle) lines.push(`<span class="cv-sub"></span>`);
    if (!known && this._last) lines.push(`<span>Last command: ${this._last.charAt(0).toUpperCase() + this._last.slice(1)}</span>`);
    else if (!known && a.assumed_state) lines.push('<span>This blind doesn’t report where it is</span>');
    this.querySelector('.cv-info').innerHTML = lines.join('');
    const sub = this.querySelector('.cv-sub');
    if (sub) sub.textContent = c.subtitle;

    const posBox = this.querySelector('.cv-pos');
    const showPos = c.show_position !== false && pos != null;
    posBox.style.display = showPos ? 'block' : 'none';
    if (showPos) posBox.innerHTML = `<div class="ck-bar"><i style="width:${pos}%; background:${color};"></i></div><div class="ck-sub" style="display:flex; justify-content:space-between; margin-top:3px;"><span>Closed</span><span>${pos}% open</span></div>`;

    const f = a.supported_features || 0;
    const active = !known ? this._last : st.state === 'open' || st.state === 'opening' ? 'open' : 'close';
    const tiles = [
      ...(f & 1 ? [{ key: 'open', name: 'Open', icon: 'mdi:arrow-up', color, on: active === 'open' }] : []),
      ...(f & 8 ? [{ key: 'stop', name: 'Stop', icon: 'mdi:stop', color, on: !known && this._last === 'stop' }] : []),
      ...(f & 2 ? [{ key: 'close', name: 'Close', icon: 'mdi:arrow-down', color, on: active === 'close' }] : []),
    ];
    kitTiles(this.querySelector('.cv-buttons'), tiles, (t) => this._press(t.key));
    hydrateIcons(this);
  }

  _press(key) {
    this._last = key;
    if (this._demo) {
      const d = this._demo;
      if (!d.attributes.assumed_state) {
        if (key === 'open') { d.state = 'open'; d.attributes.current_position = 100; }
        if (key === 'close') { d.state = 'closed'; d.attributes.current_position = 0; }
      }
      this._render();
      return;
    }
    this._hass.callService('cover', `${key}_cover`, { entity_id: this.config.entity });
    this._render();
  }

  getCardSize() {
    return 3;
  }

  getGridOptions() {
    return { columns: 12, min_columns: 6, rows: 'auto' };
  }

  static getConfigElement() {
    return document.createElement(`cover-card-editor${SUFFIX}`);
  }

  static getStubConfig(hass) {
    const first = hass && Object.keys(hass.states).find((id) => id.startsWith('cover.'));
    return first ? { entity: first } : { demo: true };
  }
}

export function registerCoverCard() {
  if (!customElements.get(`cover-card-editor${SUFFIX}`)) customElements.define(`cover-card-editor${SUFFIX}`, CoverCardEditor);
  if (!customElements.get(`cover-card${SUFFIX}`)) customElements.define(`cover-card${SUFFIX}`, CoverCard);
  window.customCards = window.customCards || [];
  window.customCards.push({
    type: `cover-card${SUFFIX}`,
    name: `Blind Card${LABEL}`,
    description: 'A blind, curtain or other cover: Open, Stop and Close, with position when known',
    preview: true,
    documentationURL: 'https://github.com/J45PER/church-drive-cards#readme',
  });
}
