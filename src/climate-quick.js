// What a quick setting does to a thermostat, as the steps to send in order.
// Kept apart from the card so it can be tested without a browser.

const noPreset = (p) => p == null || p === '' || p === 'none';

// The mode that means "on" for a thermostat that is off: Heat, else Auto, else the first one that isn't Off.
export function ccOnMode(modes) {
  const list = modes || [];
  return ['heat', 'auto', 'heat_cool'].find((m) => list.includes(m)) || list.find((m) => m !== 'off') || null;
}

// `state` is the thermostat's state (its mode), `attrs` its attributes, `q` the quick setting
// ({ hvac_mode?, preset_mode?, temperature? }). Returns [{ service, data }] in the order to send them.
//
// A preset such as Eco only takes effect while the thermostat is on, so an Eco button on a thermostat that is
// off switches it on (Heat) first: one tap, not Heat and then Eco.
export function quickPlan(state, attrs, q) {
  const a = attrs || {};
  const steps = [];
  if (q.temperature != null) {
    steps.push({ service: 'set_temperature', data: { temperature: Number(q.temperature), ...(q.hvac_mode ? { hvac_mode: q.hvac_mode } : {}) } });
  } else if (q.hvac_mode && q.hvac_mode !== state) {
    steps.push({ service: 'set_hvac_mode', data: { hvac_mode: q.hvac_mode } });
  }
  if (q.preset_mode) {
    if (!noPreset(q.preset_mode) && !q.hvac_mode && q.temperature == null && state === 'off') {
      const on = ccOnMode(a.hvac_modes);
      if (on) steps.unshift({ service: 'set_hvac_mode', data: { hvac_mode: on } });
    }
    steps.push({ service: 'set_preset_mode', data: { preset_mode: q.preset_mode } });
  } else if ((q.hvac_mode || q.temperature != null) && q.hvac_mode !== 'off' && !noPreset(a.preset_mode) && (a.preset_modes || []).includes('none')) {
    // A plain mode or temperature setting leaves any preset (e.g. Eco).
    steps.push({ service: 'set_preset_mode', data: { preset_mode: 'none' } });
  }
  return steps;
}
