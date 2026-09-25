// The universal scene library (Bright, Relax, ...), defined once in the
// Church Drive integration (custom_components/church_drive/library.py) and
// read here over the websocket, once per page. A universal scene is applied
// with one light.turn_on on the card's room/zone/light, so it works in any
// room without a Hue scene. Card config refers to one as `universal:<key>`.

const EVENT = 'church-drive-universal-scenes';
export const UNIVERSAL_PREFIX = 'universal:';

let library = [];
let loading = null;

export function loadUniversalScenes(hass, force = false) {
  if (force) loading = null;
  if (loading || !hass || !hass.callWS) return loading;
  loading = hass
    .callWS({ type: 'church_drive/library' })
    .then((res) => {
      library = (res && res.scenes) || [];
      window.dispatchEvent(new CustomEvent(EVENT));
    })
    .catch(() => {
      // Integration not installed: no universal scenes, Hue scenes still work.
    });
  return loading;
}

export function universalScenes() {
  return library;
}

// A card's reference to a universal scene: `universal:<key>` for the card's
// room, or `universal:<key>@<light entity>` for one zone/group/light in it.
export function universalRef(key, target) {
  return `${UNIVERSAL_PREFIX}${key}${target ? `@${target}` : ''}`;
}

export function universalTarget(ref) {
  const at = String(ref || '').indexOf('@');
  return at === -1 ? null : ref.slice(at + 1);
}

export function universalScene(ref) {
  let key = String(ref || '').startsWith(UNIVERSAL_PREFIX) ? ref.slice(UNIVERSAL_PREFIX.length) : String(ref || '');
  if (key.includes('@')) key = key.slice(0, key.indexOf('@'));
  const wanted = String(key || '').toLowerCase();
  return library.find((s) => s.key === wanted || s.name.toLowerCase() === wanted) || null;
}

export function onUniversalScenesChanged(callback) {
  window.addEventListener(EVENT, callback);
  return () => window.removeEventListener(EVENT, callback);
}

// light.turn_on data for a white scene.
export function universalTurnOnData(scene) {
  const data = { brightness: scene.brightness };
  if (scene.color_temp_kelvin) data.color_temp_kelvin = scene.color_temp_kelvin;
  if (scene.xy_color) data.xy_color = scene.xy_color;
  return data;
}

const COLOUR_MODES = ['xy', 'hs', 'rgb', 'rgbw', 'rgbww'];

// A colour scene's colours dealt round lights (for the pretend home and as a
// fallback): [{ entity_id, xy_color, brightness }].
export function universalDealColours(scene, lightIds) {
  return [...lightIds].sort().map((id, i) => ({
    entity_id: id,
    xy_color: scene.colors[i % scene.colors.length],
    brightness: scene.brightness,
  }));
}

// Any of the lights animating (Hue reports dynamics on lit bulbs).
export function universalScenePlaying(hass, lightIds) {
  return lightIds.some((id) => hass.states[id] && hass.states[id].state === 'on' && hass.states[id].attributes.dynamics === 'dynamic_palette');
}

// Whether lights are showing a scene: every lit light at its brightness and
// colour temperature (or colour), within what the bulbs report back.
export function universalSceneActive(hass, scene, lightIds) {
  const lit = lightIds.map((id) => hass.states[id]).filter((st) => st && st.state === 'on');
  if (!lit.length) return false;
  return lit.every((st) => {
    const a = st.attributes;
    const modes = a.supported_color_modes || [];
    const animating = scene.kind === 'colour' && a.dynamics === 'dynamic_palette';
    if (!animating && a.brightness != null && Math.abs(a.brightness - scene.brightness) > 4) return false;
    // A light that can't show the scene's colour (e.g. an on/off plug or a
    // white-only bulb in a colour scene) only has to be on.
    if (scene.color_temp_kelvin && modes.includes('color_temp')) {
      if (a.color_mode !== 'color_temp' || a.color_temp_kelvin == null) return false;
      return Math.abs(a.color_temp_kelvin - scene.color_temp_kelvin) <= scene.color_temp_kelvin * 0.03;
    }
    const colours = scene.kind === 'colour' ? scene.colors : scene.xy_color ? [scene.xy_color] : null;
    if (colours && modes.some((m) => COLOUR_MODES.includes(m))) {
      if (scene.kind === 'colour' && a.dynamics === 'dynamic_palette') return true;
      const xy = a.xy_color;
      return !!xy && colours.some((c) => Math.abs(xy[0] - c[0]) < 0.06 && Math.abs(xy[1] - c[1]) < 0.06);
    }
    return true;
  });
}
