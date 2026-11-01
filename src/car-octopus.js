// What a car made by Octopus's Intelligent device (the ID.5) can say. Kept apart from the card so it can be tested without a browser.

export const hm = (iso) => {
  const t = Date.parse(iso);
  return Number.isFinite(t) ? new Date(t).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }) : '';
};

// What a car made by Octopus's Intelligent device can say. It has no plug or charging sensor of its own, so it is plugged in and charging
// when the Zappi is and this car is the Primary one on it (input_select.zappi_car_role); its plan is Octopus's current or next dispatch.
// Null for any other car.
export function octopusCar(hass, e) {
  const disp = e.dispatch && hass.states[e.dispatch];
  if (!disp) return null;
  const st = (id) => hass.states[id] || null;
  const role = st('input_select.zappi_car_role');
  const plug = st('sensor.zappi_plug_status');
  const plugged = !!role && role.state === 'Primary' && !!plug && !/disconnected|unavailable|unknown/i.test(plug.state);
  const power = st('sensor.zappi_charging_power');
  const charging = plugged && (((parseFloat(power && power.state) || 0)) > 100 || /^charging/i.test(plug.state));
  const a = disp.attributes || {};
  const plan = disp.state === 'on' && hm(a.current_end)
    ? `Octopus charging until ${hm(a.current_end)}`
    : hm(a.next_start) && hm(a.next_end) ? `Next Octopus charge ${hm(a.next_start)} to ${hm(a.next_end)}` : '';
  return { plugged, charging, plan };
}

// Octopus's charging plan for the car it controls: the planned dispatches (SMART, or BOOST while Boost is on) that have not finished, oldest
// first. Null when the house has no Octopus Intelligent device. kWh is positive (Octopus sends it as a negative number).
export function octopusPlan(hass, now = Date.now()) {
  const id = Object.keys(hass.states).find((k) => /^binary_sensor\.octopus_energy_.*_intelligent_dispatching$/.test(k));
  const st = id && hass.states[id];
  if (!st) return null;
  const list = Array.isArray(st.attributes && st.attributes.planned_dispatches) ? st.attributes.planned_dispatches : [];
  const slots = list
    .map((d) => ({ start: Date.parse(d.start), end: Date.parse(d.end), kwh: Math.abs(Number(d.charge_in_kwh)) || 0, source: String(d.source || '').toUpperCase() }))
    .filter((x) => Number.isFinite(x.start) && Number.isFinite(x.end) && x.end > now)
    .sort((a, b) => a.start - b.start);
  return { slots, active: st.state === 'on' };
}

// One line of the plan: "Now: 23:51 to 04:02 · 32.5 kWh · Boost".
export function planLine(x, now = Date.now()) {
  const t = (ms) => new Date(ms).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hourCycle: 'h23' });
  return `${x.start <= now ? 'Now: ' : ''}${t(x.start)} to ${t(x.end)}${x.kwh > 0 ? ` · ${x.kwh.toFixed(1)} kWh` : ''} · ${x.source === 'BOOST' ? 'Boost' : 'Smart'}`;
}
