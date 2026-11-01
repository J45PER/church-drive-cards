// Octopus charging plan card: what Octopus has planned for the car it controls (Intelligent Octopus Go): the charges still to come, with the
// kWh and whether each is Smart or Boost, and "Now:" for one in progress. It has its own card so it can sit under the Cars and the
// charger, on the Home and Energy pages. It hides itself when the house has no Octopus Intelligent device.

import { SUFFIX, LABEL } from './suffix.js';
import { KIT_COLOR, kitShell, kitHead, kitEsc, kitCompact, kitCompactable } from './card-kit.js';
import { octopusPlan, planLine } from './car-octopus.js';

const PLAN_TEAL = '#00b8d4';

function demoPlan() {
  const now = Date.now();
  return {
    active: false,
    slots: [
      { start: now + 2 * 3600e3, end: now + 4 * 3600e3, kwh: 15.6, source: 'SMART' },
      { start: now + 4.5 * 3600e3, end: now + 5 * 3600e3, kwh: 2.1, source: 'SMART' },
    ],
  };
}

export class OctopusPlanCard extends HTMLElement {
  setConfig(config) {
    this.config = config || {};
    this._built = false;
    this._sig = null;
  }

  set hass(hass) {
    this._hass = hass;
    this._render();
  }

  _render() {
    if (!this._hass) return;
    const c = this.config;
    const now = Date.now();
    const plan = c.demo ? demoPlan() : octopusPlan(this._hass, now);
    // No Octopus Intelligent device: hide (except while editing the dashboard).
    this.style.display = !plan && !this.editMode ? 'none' : '';
    const first = plan && plan.slots[0];
    const word = !plan
      ? 'Not set up'
      : first && first.start <= now
        ? 'Charging now'
        : first
          ? `Next ${new Date(first.start).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hourCycle: 'h23' })}`
          : 'Nothing planned';
    if (this._compact) {
      return kitCompact(this, {
        name: c.title || 'Octopus plan',
        color: first && first.start <= now ? PLAN_TEAL : KIT_COLOR.off,
        value: '',
        valueColor: PLAN_TEAL,
        status: word + (c.demo ? ' · demo' : ''),
        buttons: [],
      });
    }
    if (!this._built) {
      this.innerHTML = kitShell(`<div class="op-list"></div>`, `
        .op-list { display:flex; flex-direction:column; gap:6px; }
        .op-row { font-size:.9rem; font-variant-numeric:tabular-nums; color:var(--primary-text-color); }
        .op-row.now { color:${PLAN_TEAL}; font-weight:600; }`);
      this._list = this.querySelector('.op-list');
      this._built = true;
    }
    kitHead(this, c.title || 'Octopus charging plan', word + (c.demo ? ' · demo' : ''), PLAN_TEAL);
    // The minute too, so "Now:" appears when a charge begins.
    const sig = JSON.stringify([plan, Math.floor(now / 60000)]);
    if (sig === this._sig) return;
    this._sig = sig;
    this._list.innerHTML = !plan
      ? `<div class="ck-sub">Octopus Intelligent isn't set up in Home Assistant.</div>`
      : plan.slots.length
        ? plan.slots.slice(0, 6).map((x) => `<div class="op-row${x.start <= now ? ' now' : ''}">${kitEsc(planLine(x, now))}</div>`).join('')
        : `<div class="ck-sub">Nothing planned yet. Octopus plans once the car is plugged in.</div>`;
  }

  getCardSize() {
    return 2;
  }

  getGridOptions() {
    return { columns: 12, min_columns: 6, rows: 'auto' };
  }

  static getStubConfig() {
    return {};
  }
}

kitCompactable(OctopusPlanCard, (card) => {
  card._built = false;
  card._sig = null;
});

export function registerOctopusPlanCard() {
  if (!customElements.get(`octopus-plan-card${SUFFIX}`)) customElements.define(`octopus-plan-card${SUFFIX}`, OctopusPlanCard);
  window.customCards = window.customCards || [];
  window.customCards.push({
    type: `octopus-plan-card${SUFFIX}`,
    name: `Octopus Charging Plan${LABEL}`,
    description: "What Octopus has planned for the car it controls: the charges to come, Smart or Boost, with the kWh",
    preview: true,
    documentationURL: 'https://github.com/J45PER/church-drive-cards#readme',
  });
}
