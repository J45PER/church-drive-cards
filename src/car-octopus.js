// What a car made by Octopus's Intelligent device (the ID.5) can say. Kept apart from the card so it can be tested without a browser.

const hm = (iso) => {
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
