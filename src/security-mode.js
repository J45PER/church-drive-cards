// Away "security mode" for lights: while the house is away, Clive keeps chosen
// lights on (labelled away_security_light) and flickers one like a TV
// (labelled away_tv_simulation). Those lights show "Security mode" with a full
// bar in a fixed colour, and the card ignores their attribute changes so it
// does not redraw on every flicker.
import { cvar } from './clive-colours.js';

export const SECURITY_ACTIVE = 'input_boolean.clive_away_lights_active';
export const SECURITY_LABELS = ['away_security_light', 'away_tv_simulation'];
export const SECURITY_COLOR = cvar('alarm_home'); // the Armed Home blue, from the palette

const labelled = (hass, id) => {
  const e = hass && hass.entities && hass.entities[id];
  return !!e && Array.isArray(e.labels) && e.labels.some((l) => SECURITY_LABELS.includes(l));
};

export function securityActive(hass) {
  const s = hass && hass.states && hass.states[SECURITY_ACTIVE];
  return !!s && s.state === 'on';
}

// True when this light is itself one of the labelled lights, and security mode is running.
export function isSecurityLight(hass, id) {
  return securityActive(hass) && labelled(hass, id);
}

// True for that light, or a group containing one (a group's brightness moves with the flicker too).
export function isSecurityRow(hass, id) {
  if (!securityActive(hass)) return false;
  if (labelled(hass, id)) return true;
  const st = hass.states[id];
  const members = st && Array.isArray(st.attributes && st.attributes.entity_id) ? st.attributes.entity_id : [];
  return members.some((m) => labelled(hass, m));
}

// What the card compares to decide whether to redraw: the on/off state only for
// security rows, so brightness and colour flicker never triggers a rebuild.
export function watchValue(hass, id) {
  const st = hass.states[id];
  if (!st) return st;
  return isSecurityRow(hass, id) ? `security:${st.state}` : st;
}
