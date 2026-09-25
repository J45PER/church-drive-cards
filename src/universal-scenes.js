// The universal scene library (Bright, Relax, ...), defined once in the
// Church Drive integration (custom_components/church_drive/library.py) and
// read here over the websocket, once per page. A universal scene is applied
// with one light.turn_on on the card's room/zone/light, so it works in any
// room without a Hue scene. Card config refers to one as `universal:<key>`.

const EVENT = 'church-drive-universal-scenes';
export const UNIVERSAL_PREFIX = 'universal:';

let library = [];
let loading = null;

export function loadUniversalScenes(hass) {
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

export function universalScene(ref) {
  const key = String(ref || '').startsWith(UNIVERSAL_PREFIX) ? ref.slice(UNIVERSAL_PREFIX.length) : ref;
  const wanted = String(key || '').toLowerCase();
  return library.find((s) => s.key === wanted || s.name.toLowerCase() === wanted) || null;
}

export function onUniversalScenesChanged(callback) {
  window.addEventListener(EVENT, callback);
  return () => window.removeEventListener(EVENT, callback);
}

// light.turn_on data for a scene (everything but the key and name).
export function universalTurnOnData(scene) {
  const { key, name, ...data } = scene;
  return data;
}

// Whether lights are showing a scene: every lit light at its brightness and
// colour temperature (or colour), within what the bulbs report back.
export function universalSceneActive(hass, scene, lightIds) {
  const lit = lightIds.map((id) => hass.states[id]).filter((st) => st && st.state === 'on');
  if (!lit.length) return false;
  return lit.every((st) => {
    const a = st.attributes;
    const modes = a.supported_color_modes || [];
    if (a.brightness != null && Math.abs(a.brightness - scene.brightness) > 4) return false;
    // A light that can't show the scene's colour (e.g. an on/off plug or a
    // white-only bulb in a colour scene) only has to be on.
    if (scene.color_temp_kelvin && modes.includes('color_temp')) {
      if (a.color_mode !== 'color_temp' || a.color_temp_kelvin == null) return false;
      return Math.abs(a.color_temp_kelvin - scene.color_temp_kelvin) <= scene.color_temp_kelvin * 0.03;
    }
    if (scene.xy_color && modes.some((m) => ['xy', 'hs', 'rgb', 'rgbw', 'rgbww'].includes(m))) {
      const xy = a.xy_color;
      return !!xy && Math.abs(xy[0] - scene.xy_color[0]) < 0.02 && Math.abs(xy[1] - scene.xy_color[1]) < 0.02;
    }
    return true;
  });
}
