// Look of a scene tile when no picture is set: a gradient in the scene's
// palette plus a matching icon. Hue doesn't expose scene artwork or colours to
// Home Assistant, so the standard Hue scenes are approximated by name here;
// anything unknown gets a stable colour pair derived from its name.

const PALETTES = {
  bright: ['#fff6e0', '#ffd98a'],
  dimmed: ['#8a6630', '#3b2a14'],
  nightlight: ['#ff8a2a', '#3a1a05'],
  relax: ['#ffb35c', '#e0702a'],
  rest: ['#ff9f4a', '#8f4416'],
  read: ['#fff1d6', '#ffc978'],
  concentrate: ['#f2f6ff', '#a9c7ff'],
  energise: ['#d9ecff', '#5d9eff'],
  'natural light': ['#ffe0a0', '#9fd0ff'],
  soho: ['#ff4f8b', '#ffb347', '#7b2ff7'],
  'lake placid': ['#0f5e9c', '#35baf6', '#9fe2bf'],
  'toil and trouble': ['#6a0dad', '#2e8b57', '#ff7f00'],
  'bright & blue': ['#e8f4ff', '#3d7dff'],
  arise: ['#ff7b39', '#ffd27f'],
  spellbound: ['#3a0ca3', '#f72585', '#4cc9f0'],
  storybook: ['#ffadad', '#ffd6a5', '#9bf6ff'],
  unwind: ['#ff9966', '#ff5e62'],
  'pumpkin patch': ['#ff7518', '#8b4513', '#ffb347'],
  shine: ['#fffbd6', '#ffd23f'],
  phantom: ['#2d0a4e', '#6c2bd9', '#0f0f2e'],
  'city blue': ['#0b1d51', '#2f6fd6', '#89c2ff'],
};

const ICONS = [
  [/night/, 'mdi:weather-night'],
  [/read/, 'mdi:book-open-variant'],
  [/concentrat/, 'mdi:head-lightbulb-outline'],
  [/energi/, 'mdi:lightning-bolt'],
  [/relax|unwind/, 'mdi:sofa-outline'],
  [/rest/, 'mdi:bed-outline'],
  [/dim/, 'mdi:brightness-5'],
  [/bright|shine|arise/, 'mdi:white-balance-sunny'],
  [/natural/, 'mdi:weather-sunset'],
];

function baseName(name) {
  // "Natural light 2" -> "natural light"
  return String(name || '').toLowerCase().replace(/\s+\d+$/, '').trim();
}

export function scenePalette(name) {
  const key = baseName(name);
  if (PALETTES[key]) return PALETTES[key];
  let h = 0;
  for (const ch of key) h = (h * 31 + ch.charCodeAt(0)) % 360;
  return [`hsl(${h} 70% 55%)`, `hsl(${(h + 50) % 360} 70% 35%)`];
}

export function sceneBackground(name) {
  const colours = scenePalette(name);
  return `linear-gradient(135deg, ${colours.join(', ')})`;
}

export function sceneIcon(name, isDynamic) {
  const key = baseName(name);
  for (const [re, icon] of ICONS) if (re.test(key)) return icon;
  return isDynamic ? 'mdi:animation-play-outline' : 'mdi:palette-outline';
}
