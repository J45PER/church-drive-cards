// What the Watch panel of the Manager dashboard works out (no Home Assistant in here, so it is tested on its own).
// The settings it keeps are the ones the integration's watch_logic.py makes safe: which pages the watch has and in what order, what is
// hidden on each page (a list of what is hidden, so a new device shows up by itself), how Lights starts, the cars, and Clive's toggles.

export const PAGES = [
  { id: 'alarm', label: 'Alarm', icon: 'mdi:shield-home', what: 'the alarm and its modes' },
  { id: 'climate', label: 'Climate', icon: 'mdi:leaf', what: 'thermostats, air con, fans and air purifiers' },
  { id: 'lights', label: 'Lights', icon: 'mdi:lightbulb', what: 'rooms and zones: scenes and brightness' },
  { id: 'cleaning', label: 'Cleaning', icon: 'mdi:robot-vacuum', what: 'the vacuum: start, stop, home and its settings' },
  { id: 'car', label: 'Car charger', icon: 'mdi:ev-station', what: 'the charger modes, speed and smart charge' },
];

export const LIGHTS_TOP = [
  { id: 'floors', label: 'Floors' },
  { id: 'rooms', label: 'Rooms' },
  { id: 'zones', label: 'Zones' },
];

export const ALARM_MODES = [
  { id: 'armed_home', label: 'Home' },
  { id: 'armed_away', label: 'Away' },
  { id: 'armed_night', label: 'Night' },
];

export const CLEANING_FACES = [
  { id: 'control', label: 'Start, stop and home' },
  { id: 'suction', label: 'Suction' },
  { id: 'intensity', label: 'Cleaning intensity' },
  { id: 'mop', label: 'Mop intensity' },
];

export const CAR_FACES = [
  { id: 'modes', label: 'Modes (Stop, Eco, Eco+, Fast)' },
  { id: 'stats', label: 'Charging speed and amount' },
  { id: 'smart', label: 'Smart charge or charge now' },
];

export const CLIVE_FLAGS = [
  { id: 'voice', label: 'Spoken answers' },
  { id: 'follow', label: 'Follow-up listening' },
  { id: 'visual', label: 'Visual answers' },
  { id: 'buzz', label: 'Buzz on answer' },
];

export function defaultConfig() {
  return {
    pages: PAGES.map((p) => ({ id: p.id, on: true })),
    hidden: {},
    lights_top: 'rooms',
    cars: [],
    clive: { voice: true, visual: true, follow: false, buzz: true },
  };
}

// The same shape the integration keeps, with anything missing filled in.
export function normalise(raw) {
  const base = defaultConfig();
  const cfg = raw && typeof raw === 'object' ? raw : {};
  const known = PAGES.map((p) => p.id);
  const seen = new Set();
  const pages = [];
  for (const item of Array.isArray(cfg.pages) ? cfg.pages : []) {
    const id = typeof item === 'string' ? item : item && item.id;
    if (known.includes(id) && !seen.has(id)) {
      seen.add(id);
      pages.push({ id, on: typeof item === 'string' ? true : item.on !== false });
    }
  }
  for (const id of known) if (!seen.has(id)) pages.push({ id, on: true });
  const hidden = {};
  for (const id of known) if (Array.isArray(cfg.hidden && cfg.hidden[id]) && cfg.hidden[id].length) hidden[id] = [...new Set(cfg.hidden[id].filter((x) => typeof x === 'string'))];
  return {
    pages,
    hidden,
    lights_top: LIGHTS_TOP.some((t) => t.id === cfg.lights_top) ? cfg.lights_top : base.lights_top,
    cars: Array.isArray(cfg.cars) ? cfg.cars.filter((c) => c && c.name && c.battery && c.plugged) : [],
    clive: { ...base.clive, ...Object.fromEntries(CLIVE_FLAGS.filter((f) => typeof (cfg.clive || {})[f.id] === 'boolean').map((f) => [f.id, cfg.clive[f.id]])) },
  };
}

export function setPageOn(cfg, id, on) {
  return { ...cfg, pages: cfg.pages.map((p) => (p.id === id ? { ...p, on } : p)) };
}

// Moves a page one place (delta -1 up, +1 down); at an end it stays.
export function movePage(cfg, id, delta) {
  const pages = [...cfg.pages];
  const i = pages.findIndex((p) => p.id === id);
  const j = i + delta;
  if (i < 0 || j < 0 || j >= pages.length) return cfg;
  [pages[i], pages[j]] = [pages[j], pages[i]];
  return { ...cfg, pages };
}

export const isHidden = (cfg, page, item) => ((cfg.hidden || {})[page] || []).includes(item);

// Show or hide one item of a page.
export function setItemShown(cfg, page, item, shown) {
  const list = ((cfg.hidden || {})[page] || []).filter((x) => x !== item);
  if (!shown) list.push(item);
  const hidden = { ...(cfg.hidden || {}) };
  if (list.length) hidden[page] = list;
  else delete hidden[page];
  return { ...cfg, hidden };
}

export const setLightsTop = (cfg, top) => ({ ...cfg, lights_top: LIGHTS_TOP.some((t) => t.id === top) ? top : cfg.lights_top });

export const setCliveFlag = (cfg, flag, on) => ({ ...cfg, clive: { ...cfg.clive, [flag]: !!on } });

// A car on or off the charger page.
export function setCar(cfg, car, use) {
  const rest = cfg.cars.filter((c) => c.battery !== car.battery);
  return { ...cfg, cars: use ? [...rest, { name: car.name, battery: car.battery, plugged: car.plugged }] : rest };
}

// ---------------------------------------------------------------------------------------------------- what the house has

const entityOf = (item) => (typeof item === 'string' ? item : (item && item.entity) || '');

function findAll(node, match, found = []) {
  if (Array.isArray(node)) node.forEach((n) => findAll(n, match, found));
  else if (node && typeof node === 'object') {
    if (match(node.type)) found.push(node);
    else Object.values(node).forEach((n) => findAll(n, match, found));
  }
  return found;
}

// The rooms and zones of the Lights panels of the Mobile dashboard, as the watch reads them: a floor is a panel of light cards, a room is a
// card (its group light, then the zones one level down; lights further in are left out). `names` is the friendly name of an entity.
export function parseLights(config, names = () => '') {
  const views = (config && config.views) || [];
  const isRoom = (t) => t === 'custom:light-control-card' || t === 'custom:light-control-card-beta';
  const room = (card) => {
    const items = Array.isArray(card.entities) ? card.entities : [];
    const mode = card.mode || 'room';
    const head = mode === 'light' ? card.entity || '' : items.map(entityOf).find(Boolean) || (mode === 'group' ? card.entity || '' : '');
    if (!head) return null;
    const zones = mode === 'light' ? [] : items.slice(1).filter((it) => ((it && it.level) == null ? 1 : it.level) === 1).map((it) => ({ entity: entityOf(it), name: (it && it.name) || names(entityOf(it)) || entityOf(it) })).filter((z) => z.entity);
    const first = items[0];
    const title = card.name || (first && typeof first === 'object' && first.name) || names(head) || head;
    return { head, title, zones };
  };
  const rooms = (panel) => findAll(panel.cards, isRoom).map(room).filter(Boolean);
  let floors = null;
  let home = null;
  for (const view of views) {
    const panels = findAll(view, (t) => t === 'custom:section-panel-card');
    if (view.path === 'lighting') floors = panels.map((p) => ({ name: p.title || '', rooms: rooms(p) })).filter((f) => f.rooms.length);
    else if (view.path === 'home') {
      const lights = panels.find((p) => String(p.title || '').toLowerCase() === 'lights');
      if (lights) home = rooms(lights);
    }
  }
  if (floors && floors.length) return floors;
  return home && home.length ? [{ name: 'Home', rooms: home }] : [];
}

// Cars the charger page can use: a binary sensor that says a car is plugged in (X_battery_plugged) with its battery sensor (X_battery).
export function carCandidates(states) {
  const out = [];
  for (const id of Object.keys(states || {})) {
    const m = /^binary_sensor\.(.+)_battery_plugged$/.exec(id);
    if (!m) continue;
    const battery = `sensor.${m[1]}_battery`;
    if (!states[battery]) continue;
    const name = String(((states[battery].attributes || {}).friendly_name || m[1]).replace(/\s+battery$/i, '')).trim();
    out.push({ name, battery, plugged: id });
  }
  return out.sort((a, b) => a.name.localeCompare(b.name));
}

// Thermostats, air con, fans and purifiers the climate page would list.
export function climateDevices(states) {
  return Object.keys(states || {})
    .filter((id) => id.startsWith('climate.') || id.startsWith('fan.'))
    .map((id) => ({ id, name: ((states[id].attributes || {}).friendly_name || id).trim(), kind: id.startsWith('climate.') ? 'Thermostat or air con' : id.toLowerCase().includes('purifier') ? 'Air purifier' : 'Fan' }))
    .sort((a, b) => a.kind.localeCompare(b.kind) || a.name.localeCompare(b.name));
}
