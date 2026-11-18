// The air purifier's display: its brightness light, as a few notches between Off and On.
//
// A Philips purifier's display ("Display backlight") is a Home Assistant light, but the device only has a few real steps: off, dim and bright
// (anything from 1 to 254 comes back as 128, 255 is bright). So the card shows Off and a button for each step, not a free
// brightness slider. The notches are percentages of full brightness; the default is dim (50%) and bright (100%), and a card can list others
// in `display_levels` for a purifier with more steps. The phone app has the same rules (PurifierDisplayData.kt): change both together.

export const DISPLAY_LEVELS = [50, 100];

/** The notches, as whole percentages from low to high; a list that gives none (or nonsense) is the default. */
export function displayLevels(config) {
  const raw = Array.isArray(config && config.display_levels) ? config.display_levels : [];
  const list = [...new Set(raw.map((n) => Math.round(Number(n))).filter((n) => Number.isFinite(n) && n >= 1 && n <= 100))].sort((a, b) => a - b);
  return list.length ? list : DISPLAY_LEVELS.slice();
}

/**
 * The display light that belongs to a purifier fan: the one the card names (`display_entity`; an empty string means the card has none), else
 * the light that starts with the fan's name and has "display" in it (light.<name>_display_backlight), or null.
 */
export function findDisplay(states, fanId, configured) {
  if (configured === '') return null;
  if (configured) return configured;
  const base = String(fanId || '').split('.')[1];
  if (!base || !states) return null;
  const ids = Object.keys(states).filter((id) => id.startsWith(`light.${base}_`) && /display/.test(id));
  return ids.find((id) => /backlight/.test(id)) || ids[0] || null;
}

/** Which notch the display is at (0 for the lowest), or null when it is off or its brightness is not known; the nearest notch, the higher one on a tie. */
export function levelIndex(state, brightness, levels) {
  if (state !== 'on' || !levels.length) return null;
  const b = Number(brightness);
  if (!Number.isFinite(b)) return levels.length - 1;
  const pct = (Math.max(0, Math.min(255, b)) / 255) * 100;
  let best = 0;
  levels.forEach((l, i) => {
    if (Math.abs(l - pct) <= Math.abs(levels[best] - pct)) best = i;
  });
  return best;
}

/** What a notch is called: Dim and Bright for two, Dim, Medium and Bright for three, else its percentage. */
export function levelWord(index, levels) {
  if (index == null || index < 0 || index >= levels.length) return 'Off';
  if (levels.length === 1) return 'On';
  if (levels.length === 2) return ['Dim', 'Bright'][index];
  if (levels.length === 3) return ['Dim', 'Medium', 'Bright'][index];
  return `${levels[index]}%`;
}

/** The brightness (0 to 255) a notch asks the light for. */
export function levelBrightness(index, levels) {
  const l = levels[Math.max(0, Math.min(levels.length - 1, index))];
  return Math.round((l / 100) * 255);
}
