// Overnight Heating Card (Climate page, administrators only): what the heating does at night to save energy.
//
// Off, Eco or Smart for the whole house, and the two times it runs between (midnight to 6am to begin with). Smart leaves the heating to Clive;
// Eco and Off stop Clive adjusting it overnight and put it on Eco or turn it off, then he puts it back in the morning. The setting and the times
// are Home Assistant helpers (input_select.clive_overnight_mode, input_datetime.clive_overnight_start and _end); the work is done by Clive's
// zone script (ha/clive-climate-zone.yaml). The card is not shown to anyone but administrators. Wording and sums are in overnight-logic.js.

import { createFormEditor } from './form-editor.js';
import { iconHtml, hydrateIcons } from './icons.js';
import { SUFFIX, LABEL } from './suffix.js';
import { kitShell, kitHead, kitEsc } from './card-kit.js';
import { OVERNIGHT_MODE_ID, OVERNIGHT_START_ID, OVERNIGHT_END_ID, OVERNIGHT_MODES, hm, inWindow, describeOvernight, modeOf } from './overnight-logic.js';

const OH_COLOR = { Off: '#9aa4b2', Eco: '#55c47c', Smart: '#8a86ff' };

const OH_CSS = `
  .oh-modes { display:flex; gap:6px; }
  .oh-mode { flex:1 1 0; min-width:0; height:48px; border:none; border-radius:12px; cursor:pointer; background:rgba(127,127,127,0.16); color:var(--primary-text-color);
    font:inherit; font-size:0.9rem; font-weight:600; display:flex; align-items:center; justify-content:center; gap:6px; }
  .oh-mode[aria-pressed="true"] { color:#fff; }
  .oh-mode:focus-visible, .oh-time:focus-visible { outline:2px solid var(--primary-color); outline-offset:2px; }
  .oh-times { display:flex; gap:10px; }
  .oh-times label { flex:1 1 0; display:flex; flex-direction:column; gap:4px; font-size:0.75rem; color:var(--secondary-text-color); }
  .oh-time { height:44px; border:none; border-radius:10px; padding:0 10px; background:rgba(127,127,127,0.16); color:var(--primary-text-color); font:inherit; font-size:1rem; font-variant-numeric:tabular-nums; }
  .oh-note { font-size:0.82rem; color:var(--secondary-text-color); line-height:1.4; }
`;

export const OvernightHeatingCardEditor = createFormEditor({
  schema: () => [{ name: 'title', selector: { text: {} } }],
  labels: { title: 'Title (optional)' },
  helpers: { title: 'Only administrators see this card.' },
});

export class OvernightHeatingCard extends HTMLElement {
  setConfig(config) {
    this.config = config || {};
    this._built = false;
  }

  set hass(hass) {
    this._hass = hass;
    this._render();
  }

  _admin() {
    return !!(this.config.demo || (this._hass && this._hass.user && this._hass.user.is_admin));
  }

  _states() {
    if (this.config.demo) {
      return { [OVERNIGHT_MODE_ID]: { state: 'Eco' }, [OVERNIGHT_START_ID]: { state: '00:00:00' }, [OVERNIGHT_END_ID]: { state: '06:00:00' }, 'sensor.church_drive_assistant': { state: 'Clive' } };
    }
    return (this._hass && this._hass.states) || {};
  }

  _call(domain, service, data) {
    if (this.config.demo || !this._hass) return;
    this._hass.callService(domain, service, data);
  }

  _render() {
    if (!this._hass && !this.config.demo) return;
    // Nobody but an administrator sees it: the card takes no room on the page.
    if (!this._admin()) {
      this.style.display = 'none';
      return;
    }
    this.style.display = '';
    if (!this._built) {
      this.innerHTML = kitShell(`<div class="oh-body"></div>`, OH_CSS);
      this._body = this.querySelector('.oh-body');
      this._body.style.cssText = 'display:flex; flex-direction:column; gap:12px;';
      this._built = true;
      this._sig = null;
    }
    const st = this._states();
    const modeState = st[OVERNIGHT_MODE_ID];
    const mode = modeOf(modeState && modeState.state);
    const start = hm(st[OVERNIGHT_START_ID] && st[OVERNIGHT_START_ID].state);
    const end = hm(st[OVERNIGHT_END_ID] && st[OVERNIGHT_END_ID].state);
    const nameState = st['sensor.church_drive_assistant'];
    const name = nameState && nameState.state && !['unknown', 'unavailable'].includes(nameState.state) ? nameState.state : 'Clive';
    const colour = OH_COLOR[mode];
    const now = new Date();
    const active = mode !== 'Smart' && inWindow(start, end, `${now.getHours()}:${now.getMinutes()}`);
    const word = !modeState ? '' : active ? `${mode} now` : mode;
    this.querySelector('.ck-headrow').style.display = '';
    kitHead(this, this.config.title || 'Overnight heating', word, colour);
    const sig = JSON.stringify([mode, start, end, name, !!modeState, active]);
    if (sig === this._sig) return;
    this._sig = sig;
    if (!modeState || !start || !end) {
      this._body.innerHTML = `<div class="oh-note">Overnight heating needs its settings in Home Assistant (${kitEsc(OVERNIGHT_MODE_ID)}, ${kitEsc(OVERNIGHT_START_ID)} and ${kitEsc(OVERNIGHT_END_ID)}).</div>`;
      return;
    }
    this._body.innerHTML = `
      <div class="oh-modes" role="group" aria-label="Overnight heating">${OVERNIGHT_MODES.map((m) => `<button type="button" class="oh-mode" data-mode="${m.id}" aria-pressed="${m.id === mode}" style="${m.id === mode ? `background:${OH_COLOR[m.id]};` : ''}">${iconHtml(m.icon, { size: '20px' })}${kitEsc(m.label)}</button>`).join('')}</div>
      <div class="oh-note">${kitEsc(describeOvernight(mode, start, end, name))}</div>
      <div class="oh-times">
        <label>From<input type="time" class="oh-time" data-time="start" value="${start}"></label>
        <label>Until<input type="time" class="oh-time" data-time="end" value="${end}"></label>
      </div>
      <div class="oh-note">Zones set to Manual are left alone. A person changing the thermostat in the night keeps it until the morning.</div>`;
    this._body.querySelectorAll('.oh-mode').forEach((b) => b.addEventListener('click', () => this._call('input_select', 'select_option', { entity_id: OVERNIGHT_MODE_ID, option: b.dataset.mode })));
    this._body.querySelectorAll('.oh-time').forEach((i) =>
      i.addEventListener('change', () => {
        const v = hm(i.value);
        if (v) this._call('input_datetime', 'set_datetime', { entity_id: i.dataset.time === 'start' ? OVERNIGHT_START_ID : OVERNIGHT_END_ID, time: `${v}:00` });
      }),
    );
    hydrateIcons(this);
  }

  getCardSize() {
    return 4;
  }

  getGridOptions() {
    return { columns: 'full', rows: 'auto' };
  }

  static getConfigElement() {
    return document.createElement(`overnight-heating-card-editor${SUFFIX}`);
  }

  static getStubConfig() {
    return {};
  }
}

export function registerOvernightHeatingCard() {
  if (!customElements.get(`overnight-heating-card-editor${SUFFIX}`)) customElements.define(`overnight-heating-card-editor${SUFFIX}`, OvernightHeatingCardEditor);
  if (!customElements.get(`overnight-heating-card${SUFFIX}`)) customElements.define(`overnight-heating-card${SUFFIX}`, OvernightHeatingCard);
  window.customCards = window.customCards || [];
  window.customCards.push({
    type: `overnight-heating-card${SUFFIX}`,
    name: `Overnight Heating Card${LABEL}`,
    description: 'Administrators only: Off, Eco or Smart for the heating overnight, and the times it runs between',
    preview: true,
    documentationURL: 'https://github.com/J45PER/church-drive-cards#readme',
  });
}
