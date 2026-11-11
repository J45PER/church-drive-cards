// The Clive glow, as the apps draw it (core/.../Glow.kt): behind a card, two soft blobs of the card's own colour turning slowly. The colours
// are the ground's hue, bright and clear, with the second turned 28 degrees; a grey ground (off, unavailable) has none.

export function hexToRgb(hex) {
  const m = /^#?([0-9a-f]{6})$/i.exec(hex || '');
  if (!m) return null;
  const n = parseInt(m[1], 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
}

export function rgbToHsv([r, g, b]) {
  r /= 255; g /= 255; b /= 255;
  const max = Math.max(r, g, b), min = Math.min(r, g, b), d = max - min;
  let h = 0;
  if (d) h = max === r ? ((g - b) / d) % 6 : max === g ? (b - r) / d + 2 : (r - g) / d + 4;
  return [(h * 60 + 360) % 360, max ? d / max : 0, max];
}

export function hsvToHex(h, s, v) {
  const f = (n) => { const k = (n + h / 60) % 6; return v - v * s * Math.max(0, Math.min(k, 4 - k, 1)); };
  return `#${[f(5), f(3), f(1)].map((x) => Math.round(x * 255).toString(16).padStart(2, '0')).join('')}`;
}

/** A glow colour for a ground: its hue turned [shift] degrees, or null for a grey ground. */
export function glowColour(ground, shift) {
  const rgb = hexToRgb(ground);
  if (!rgb) return null;
  const [h, s] = rgbToHsv(rgb);
  if (s < 0.18) return null;
  return hsvToHex((h + shift + 360) % 360, Math.min(0.9, Math.max(0.55, s)), 0.9);
}

let tones = null;
/** Gives the glow the house's palettes: [lookup] turns a palette key or HA colour name into [Colour, Dark, Accent, Light, Deep]. */
export function setTones(lookup) { tones = lookup; }

/** CSS custom properties for a card on [ground]: its two glow colours (or none, so the card stays plain). */
export function glowStyle(ground) {
  if (ground && !String(ground).startsWith('#') && tones) {
    const [c, d, a] = tones(ground);
    return ground === 'neutral' || ground === 'unavailable' ? `--ground:${d};` : `--ground:${d};--ga:${c};--gb:${a};`;
  }
  const a = glowColour(ground, 0), b = glowColour(ground, 28);
  return a && b ? `--ground:${ground};--ga:${a};--gb:${b};` : `--ground:${ground};`;
}

/** Whether text on [ground] should be light or dark (the apps keep text white on their dark cards; a pale ground gets dark text). */
export function inkOn(ground) {
  const rgb = hexToRgb(ground);
  if (!rgb) return '#fff';
  const [r, g, b] = rgb.map((c) => { c /= 255; return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4; });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b > 0.55 ? '#14181f' : '#ffffff';
}
