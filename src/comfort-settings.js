// Comfort settings for the climate cards, the same as the phone app's thermostat sheet. A thermostat that Clive looks after
// (input_boolean.clive_climate_<zone>_auto exists) gets a status line on its card ("Clive controlled", "Manual") and a sheet from it:
// Clive controlled or Manual, the comfortable range, the pause (with a way to end it) and what he last did. A room with no thermostat
// can have just its range, kept in two number helpers the card is pointed at (low_entity and high_entity).
// The logic is in plain functions (tested in test/comfort-settings.test.mjs); the sheet is built on openPopup.

import { openPopup } from './popup.js';
import { iconHtml, hydrateIcons } from './icons.js';

const STEP = 0.5;
const GAP = 1;
const num = (st) => {
  const v = st && st.state !== 'unavailable' && st.state !== 'unknown' ? parseFloat(st.state) : NaN;
  return Number.isFinite(v) ? v : null;
};

// What the house holds about how Clive looks after one thermostat, or null when there is no switch for it.
export function zoneControl(states, climateId) {
  const zone = String(climateId || '').replace(/^climate\./, '');
  const auto = states[`input_boolean.clive_climate_${zone}_auto`];
  if (!zone || !auto || auto.state === 'unavailable' || auto.state === 'unknown') return null;
  const ids = { low: `input_number.clive_climate_${zone}_low`, high: `input_number.clive_climate_${zone}_high` };
  const resume = states[`input_datetime.clive_climate_${zone}_resume`];
  const note = states[`input_text.clive_climate_${zone}_note`];
  return {
    zone,
    climateId,
    autoId: auto.entity_id || `input_boolean.clive_climate_${zone}_auto`,
    lowId: ids.low,
    highId: ids.high,
    controlled: auto.state === 'on',
    low: num(states[ids.low]),
    high: num(states[ids.high]),
    // When a pause ends (ms), or null when he is not paused: a time in the past, or the year 2000 ("no pause"), is none.
    pausedUntil: ((ts, now) => (ts && ts * 1000 > now ? ts * 1000 : null))(resume && resume.attributes ? Number(resume.attributes.timestamp) : 0, Date.now()),
    note: note && note.state && !['unknown', 'unavailable'].includes(note.state) ? note.state : '',
  };
}

const clock = (ms) => new Date(ms).toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' });

// "Clive controlled", "Clive controlled · paused until 12:24" or "Manual".
export function zoneStatusText(zone, name, nowMs = Date.now()) {
  if (!zone.controlled) return 'Manual';
  return zone.pausedUntil && zone.pausedUntil > nowMs ? `${name} controlled · paused until ${clock(zone.pausedUntil)}` : `${name} controlled`;
}

// One step of one end of a range, keeping the two ends at least a degree apart (the end being moved gives way, never the other).
export function stepRange(end, value, other, delta) {
  const v = Math.round((value + delta) * 2) / 2;
  return end === 'low' ? Math.min(v, other - GAP) : Math.max(v, other + GAP);
}

const assistantName = (hass) => {
  const st = hass.states['sensor.church_drive_assistant'];
  return st && st.state && !['unknown', 'unavailable'].includes(st.state) ? st.state : 'Clive';
};

const BTN = 'border:none; border-radius:12px; cursor:pointer; background:rgba(127,127,127,0.18); color:var(--primary-text-color); font:inherit;';

// Opens the sheet. spec is { title, climateId } for a thermostat, or { title, lowId, highId } for a room's own range.
// Returns { update } so the card can refresh it as Home Assistant changes things.
export function openComfortSettings(host, hass, spec) {
  let h = hass;
  const root = document.createElement('div');
  root.style.cssText = 'display:flex; flex-direction:column; gap:14px;';
  const call = (domain, service, data) => h.callService(domain, service, data);
  const pop = openPopup(host, { title: spec.title, icon: 'mdi:thermostat', content: root });

  const render = () => {
    const zone = spec.climateId ? zoneControl(h.states, spec.climateId) : null;
    const lowId = zone ? zone.lowId : spec.lowId;
    const highId = zone ? zone.highId : spec.highId;
    const lo = num(h.states[lowId]);
    const hi = num(h.states[highId]);
    const name = assistantName(h);
    const parts = [];
    if (zone) {
      parts.push(`<div style="display:flex; gap:8px;">
        ${[['on', `${name} controlled`], ['off', 'Manual']]
          .map(([v, label]) => `<button type="button" data-auto="${v}" style="${BTN} flex:1; height:48px; ${(v === 'on') === zone.controlled ? 'background:var(--primary-color); color:#fff; font-weight:600;' : ''}">${label}</button>`)
          .join('')}</div>
        <div style="font-size:0.85rem; color:var(--secondary-text-color);">${
          zone.controlled
            ? `${name} keeps the room ${lo != null && hi != null ? `between ${lo}° and ${hi}°` : 'comfortable'}. He only changes the target of heating that is already on${lo != null && hi != null ? `, and waits until it is below ${lo - 1.5}° (or above ${hi + 1}°) before he does` : ''}.`
            : `${name} leaves this thermostat alone.`
        }</div>`);
    } else {
      parts.push(`<div style="font-size:0.85rem; color:var(--secondary-text-color);">The comfortable range for this room: its graph and colours follow it.</div>`);
    }
    if (lo != null && hi != null) {
      const row = (end, label, value) => `<div style="display:flex; align-items:center; gap:10px;">
        <span style="flex:1;">${label}</span>
        <button type="button" data-step="${end}:-1" aria-label="Lower ${label}" style="${BTN} width:44px; height:44px; font-size:22px;">−</button>
        <b style="min-width:56px; text-align:center; font-size:1.05rem; font-variant-numeric:tabular-nums;">${value}°</b>
        <button type="button" data-step="${end}:1" aria-label="Raise ${label}" style="${BTN} width:44px; height:44px; font-size:22px;">+</button></div>`;
      parts.push(row('low', 'Comfortable from', lo), row('high', 'Comfortable up to', hi));
    } else {
      parts.push(`<div style="color:var(--secondary-text-color);">The range numbers aren’t available yet.</div>`);
    }
    if (zone && zone.pausedUntil) {
      parts.push(`<div style="display:flex; flex-direction:column; gap:8px;">
        <b>Paused until ${clock(zone.pausedUntil)}</b>
        <span style="font-size:0.85rem; color:var(--secondary-text-color);">The thermostat was changed by hand, so ${name} leaves it alone until then.</span>
        <button type="button" data-resume="1" style="${BTN} height:48px; display:flex; align-items:center; justify-content:center; gap:8px;">${iconHtml('mdi:play', { size: '20px' })}Resume ${name} now</button></div>`);
    }
    if (zone && zone.note) parts.push(`<div style="font-size:0.75rem; color:var(--secondary-text-color);">Last: ${zone.note.replace(/[<>&]/g, '')}</div>`);
    root.innerHTML = parts.join('');
    hydrateIcons(root);
    root.querySelectorAll('[data-auto]').forEach((b) => b.addEventListener('click', () => call('input_boolean', b.dataset.auto === 'on' ? 'turn_on' : 'turn_off', { entity_id: zone.autoId })));
    root.querySelectorAll('[data-step]').forEach((b) =>
      b.addEventListener('click', () => {
        const [end, dir] = b.dataset.step.split(':');
        const cur = num(h.states[lowId]), other = num(h.states[highId]);
        if (cur == null || other == null) return;
        const value = end === 'low' ? stepRange('low', cur, other, STEP * Number(dir)) : stepRange('high', other, cur, STEP * Number(dir));
        call('input_number', 'set_value', { entity_id: end === 'low' ? lowId : highId, value });
      }),
    );
    const resume = root.querySelector('[data-resume]');
    if (resume) resume.addEventListener('click', () => call('script', 'clive_climate_control', { command: 'resume', zone: zone.zone }));
  };
  render();
  return {
    close: pop.close,
    update(next) {
      h = next;
      render();
    },
  };
}
