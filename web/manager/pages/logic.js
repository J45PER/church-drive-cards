// The pure parts of the Device health, Energy and Activity pages (ported from the apps' DeviceHealthData.kt, EnergyData.kt, ActivityData.kt), kept apart so tests can run them.

export const BATTERY_LOW = 40, BATTERY_CRITICAL = 20, FILTER_LOW = 30, FILTER_CRITICAL = 10;
export const OK = 0, WARN = 1, BAD = 2;

export function percentLevel(p, low, critical) { return p <= critical ? BAD : p <= low ? WARN : OK; }
const pct = (s) => { const n = parseFloat(s?.state); return Number.isFinite(n) ? Math.min(100, Math.max(0, Math.round(n))) : null; };

/** A registry as the site holds it: entity ids to device/area/platform, devices to names and areas. Built from the house's list_for_display answers. */
export function buildRegistry(entityReg, deviceReg, areaReg) {
  const entities = new Map(); const byDevice = new Map();
  for (const e of entityReg?.entities || []) {
    const id = e.ei; if (!id) continue;
    entities.set(id, { device: e.di || null, area: e.ai || null, platform: e.pl || null });
    if (e.di) { if (!byDevice.has(e.di)) byDevice.set(e.di, []); byDevice.get(e.di).push(id); }
  }
  const devices = new Map((deviceReg || []).map((d) => [d.id, { name: d.name_by_user || d.name || null, area: d.area_id || null }]));
  const areas = new Map((areaReg || []).map((a) => [a.area_id, a.name]));
  return { entities, byDevice, devices, areas };
}

function areaOfDevice(reg, deviceId, ids) {
  const own = reg.devices.get(deviceId)?.area; if (own) return reg.areas.get(own) || own;
  const viaEntity = ids.map((i) => reg.entities.get(i)?.area).find(Boolean);
  return viaEntity ? reg.areas.get(viaEntity) || viaEntity : null;
}

/** Devices Church Drive's own watch says stopped responding: device id to reason. */
export function staleDevices(states, reg) {
  const out = {}; const devices = states['sensor.church_drive_device_health']?.attributes?.devices || {};
  for (const [id, d] of Object.entries(devices)) {
    if (d?.status === 'ok') continue;
    const device = id.includes('.') ? reg.entities.get(id)?.device : id;
    if (device) out[device] = d?.reason || 'Not responding';
  }
  return out;
}

export function healthOfDevice(deviceId, states, reg, stale) {
  const ids = reg.byDevice.get(deviceId) || []; if (!ids.length) return null;
  let battery = null, low = false, filter = null, heard = null;
  const present = ids.map((i) => states[i]).filter(Boolean);
  for (const e of present) {
    const id = e.entity_id, dc = e.attributes?.device_class;
    if (id.startsWith('sensor.') && dc === 'battery') { const p = pct(e); if (p != null) battery = battery == null ? p : Math.min(battery, p); }
    if (id.startsWith('binary_sensor.') && (dc === 'battery' || id.endsWith('_battery_plus_low')) && e.state === 'on') low = true;
    if (id.startsWith('sensor.') && id.includes('filter') && e.attributes?.unit_of_measurement === '%') { const p = pct(e); if (p != null) filter = filter == null ? p : Math.min(filter, p); }
    const at = Date.parse(e.last_changed); if (Number.isFinite(at) && (heard == null || at > heard)) heard = at;
  }
  const offline = present.length > 0 && present.every((e) => e.state === 'unavailable');
  const staleReason = stale[deviceId] || null;
  let batteryLevel = null;
  if (battery != null) { batteryLevel = percentLevel(battery, BATTERY_LOW, BATTERY_CRITICAL); if (low && batteryLevel === OK) batteryLevel = WARN; } else if (low) batteryLevel = BAD;
  const filterLevel = filter != null ? percentLevel(filter, FILTER_LOW, FILTER_CRITICAL) : null;
  const level = offline || batteryLevel === BAD || filterLevel === BAD ? BAD : staleReason || batteryLevel === WARN || filterLevel === WARN ? WARN : OK;
  const summary = [...new Set([offline ? 'Not responding' : staleReason, battery != null ? `Battery ${battery}%` : low ? 'Battery low' : null, filter != null ? `Filter ${filter}%` : null].filter(Boolean))].join(' · ') || 'All fine';
  return {
    id: deviceId, name: reg.devices.get(deviceId)?.name || present[0]?.attributes?.friendly_name || deviceId, area: areaOfDevice(reg, deviceId, ids),
    battery, low, filter, offline, staleReason, heard, level, summary, batteryLevel, filterLevel,
    tracked: battery != null || low || filter != null || offline || !!staleReason,
  };
}

/** Devices worth listing, those needing attention first and then by name; phones are left out. */
export function deviceHealthList(states, reg) {
  const stale = staleDevices(states, reg);
  return [...reg.byDevice.keys()]
    .filter((d) => !reg.byDevice.get(d).some((i) => reg.entities.get(i)?.platform === 'mobile_app'))
    .map((d) => healthOfDevice(d, states, reg, stale)).filter((x) => x && x.tracked)
    .sort((a, b) => b.level - a.level || a.name.toLowerCase().localeCompare(b.name.toLowerCase()));
}

export function healthSummary(items) {
  if (!items.length) return 'Batteries, filters and devices that stop responding';
  const n = items.filter((i) => i.level !== OK).length;
  return n === 0 ? `All ${items.length} fine` : `${n} of ${items.length} need attention`;
}

export function ago(ms, now = Date.now()) {
  if (ms == null) return '';
  const s = Math.max(0, Math.round((now - ms) / 1000));
  if (s < 90) return 'just now';
  if (s < 5400) return `${Math.round(s / 60)} min ago`;
  if (s < 129600) return `${Math.round(s / 3600)} h ago`;
  return `${Math.round(s / 86400)} days ago`;
}

// ---- Energy ----

const find = (states, re) => Object.values(states).find((e) => re.test(e.entity_id));
export const OCTOPUS = {
  rate: /^sensor\.octopus_energy_electricity_.*_current_rate$/, offPeak: /^binary_sensor\.octopus_energy_electricity_.*_off_peak$/,
  demand: /^sensor\.octopus_energy_electricity_.*_current_demand$/, today: /^sensor\.octopus_energy_electricity_.*_current_accumulative_cost$/,
  todayKwh: /^sensor\.octopus_energy_electricity_.*_current_accumulative_consumption$/, yesterday: /^sensor\.octopus_energy_electricity_.*_previous_accumulative_cost$/,
  yesterdayKwh: /^sensor\.octopus_energy_electricity_.*_previous_accumulative_consumption$/, gasRate: /^sensor\.octopus_energy_gas_.*_current_rate$/,
  gasToday: /^sensor\.octopus_energy_gas_.*_current_accumulative_cost$/, gasYesterday: /^sensor\.octopus_energy_gas_.*_previous_accumulative_cost$/,
  points: /^sensor\.octopus_energy_a_.*_octoplus_points$/, currentDay: /^event\.octopus_energy_electricity_.*_current_day_rates$/, nextDay: /^event\.octopus_energy_electricity_.*_next_day_rates$/,
};

export function ratesOf(event) {
  return (event?.attributes?.rates || []).map((r) => ({ start: Date.parse(r.start), end: Date.parse(r.end), pence: Number(r.value_inc_vat) * 100 }))
    .filter((r) => Number.isFinite(r.start) && Number.isFinite(r.end) && Number.isFinite(r.pence)).sort((a, b) => a.start - b.start);
}
export function allRates(states) {
  const seen = new Set();
  return [...ratesOf(find(states, OCTOPUS.currentDay)), ...ratesOf(find(states, OCTOPUS.nextDay))].filter((r) => !seen.has(r.start) && seen.add(r.start)).sort((a, b) => a.start - b.start);
}
export const currentRate = (rates, now) => rates.find((r) => r.start <= now && now < r.end) || null;
export function cheapUntil(rates, now) {
  if (!rates.length) return null;
  const low = Math.min(...rates.map((r) => r.pence)); const cheap = (r) => r.pence <= low + 0.01;
  const cur = currentRate(rates, now); if (!cur || !cheap(cur)) return null;
  let end = cur.end; for (const r of rates) if (r.start === end && cheap(r)) end = r.end;
  return end;
}
export function energySnapshot(states, now = Date.now()) {
  const num = (re) => { const e = find(states, re); const n = parseFloat(e?.state); return Number.isFinite(n) ? n : null; };
  const pence = (n) => (n == null ? null : n * 100); // the sensors give pounds per kWh
  const rates = allRates(states);
  return {
    rate: pence(num(OCTOPUS.rate)), offPeak: find(states, OCTOPUS.offPeak)?.state === 'on', demand: num(OCTOPUS.demand),
    today: num(OCTOPUS.today), todayKwh: num(OCTOPUS.todayKwh), yesterday: num(OCTOPUS.yesterday), yesterdayKwh: num(OCTOPUS.yesterdayKwh),
    gasRate: pence(num(OCTOPUS.gasRate)), gasToday: num(OCTOPUS.gasToday), gasYesterday: num(OCTOPUS.gasYesterday), points: num(OCTOPUS.points),
    rates, current: currentRate(rates, now), cheapUntil: cheapUntil(rates, now), cheapest: rates.length ? Math.min(...rates.map((r) => r.pence)) : null,
  };
}

// ---- Activity ----

export const DAYS = [['today', 'Today'], ['yesterday', 'Yesterday'], ['24h', 'Last 24 hours'], ['week', 'Last 7 days'], ['month', 'Last 30 days'], ['all', 'All dates']];
export const TIMES = [['all', 'All day', null, null], ['morning', 'Morning (06:00 to 12:00)', '06:00', '12:00'], ['afternoon', 'Afternoon (12:00 to 18:00)', '12:00', '18:00'],
  ['evening', 'Evening (18:00 to 22:00)', '18:00', '22:00'], ['night', 'Night (22:00 to 06:00)', '22:00', '06:00']];
export const ACTIONS = [['all', []], ['people', ['user']], ['assistant', ['clive', 'clive_auto']], ['asked', ['clive']], ['automatic', ['clive_auto']], ['presence', ['presence']], ['overruled', ['overruled']], ['devices', ['device']]];

export function actionLabel(id, assistant) {
  return { all: 'All actions', people: 'People', assistant: `${assistant} (all)`, asked: `${assistant}: asked`, automatic: `${assistant}: automatic`, presence: 'Coming and going', overruled: `Overruled ${assistant}`, devices: 'Devices not answering' }[id];
}

const startOfDay = (d) => new Date(d.getFullYear(), d.getMonth(), d.getDate());
/** The parameters of `church_drive/activity`: whole local days as [since, until) in seconds, the time-of-day window, kinds and person. */
export function activityQuery({ day = 'today', time = 'all', action = 'all', who = null }, now = new Date(), limit = 100, offset = 0) {
  const q = { limit };
  const days = (back, last) => { const t = startOfDay(now); const a = new Date(t); a.setDate(a.getDate() - back); const b = new Date(t); b.setDate(b.getDate() - last + 1); q.since = Math.floor(a / 1000); q.until = Math.floor(b / 1000); };
  if (day === 'today') days(0, 0); else if (day === 'yesterday') days(1, 1); else if (day === '24h') q.since = Math.floor(now / 1000) - 24 * 3600; else if (day === 'week') days(6, 0); else if (day === 'month') days(29, 0);
  const t = TIMES.find((x) => x[0] === time); if (t?.[2]) { q.start = t[2]; q.end = t[3]; }
  const kinds = ACTIONS.find((x) => x[0] === action)?.[1] || []; if (kinds.length) q.kinds = kinds;
  if (who) q.who = who; if (offset > 0) q.offset = offset;
  return q;
}

export function parseActivity(result) {
  if (!result || !Array.isArray(result.entries)) return null;
  return {
    lines: result.entries.filter((e) => e && e.text).map((e) => ({ ts: (Number(e.ts) || 0) * 1000, kind: e.kind || 'user', who: e.who || '', text: e.text, by: e.by || null, rule: e.rule || null, icon: e.icon || null, headline: e.headline || null, look: e.look || null, state: e.state || null, clive: e.clive_ts ? e.clive_ts * 1000 : null })),
    total: result.total ?? result.entries.length, people: (result.people || []).filter(Boolean), assistant: result.assistant || 'Clive',
  };
}

export const clock = (ms) => new Date(ms).toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' });
export function dayLabel(ms, now = new Date()) {
  const d = startOfDay(new Date(ms)), t = startOfDay(now); const diff = Math.round((t - d) / 86400000);
  return diff === 0 ? 'Today' : diff === 1 ? 'Yesterday' : d.toLocaleDateString('en-GB', { weekday: 'long', day: 'numeric', month: 'short' });
}
export function activityNote(line, assistant = 'the assistant') {
  if (line.kind === 'clive') return line.by ? `asked by ${line.by}` : 'asked';
  if (line.kind === 'clive_auto') return line.rule ? `automatic: ${line.rule}` : 'automatic';
  if (line.kind === 'overruled') return `overruled ${assistant} from outside the app${line.clive ? ` (set at ${clock(line.clive)})` : ''}`;
  return null;
}
export const activityWho = (line, assistant) => (line.kind === 'clive' || line.kind === 'clive_auto' ? assistant : line.who || 'Someone');
export function activityProblem(code) {
  return code === 'unknown_command' ? "Home Assistant is running an older Church Drive that doesn't have the activity log yet. Update Church Drive in HACS, then restart Home Assistant."
    : code === 'unauthorized' ? 'Only administrators can see the activity log.' : "The activity log couldn't be read from the house. Check the connection, or that Church Drive is running in Home Assistant.";
}

// ---- Palette editing ----

const lum = (hex) => { const c = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255).map((v) => (v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4)); return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]; };
/** WCAG contrast ratio of two #rrggbb colours. */
export function contrast(a, b) { const [hi, lo] = [lum(a), lum(b)].sort((x, y) => y - x); return (hi + 0.05) / (lo + 0.05); }
export const HEX = /^#[0-9a-f]{6}$/i;
export const PALETTE_PARTS = ['Colour', 'Dark', 'Accent', 'Light', 'Deep'];
/** What is wrong with a palette's five colours (the same checks the house runs): empty when it is fine. */
export function paletteWarnings(colours) {
  const out = [];
  if (!Array.isArray(colours) || colours.length !== 5 || !colours.every((c) => HEX.test(c))) return ['Every colour needs to be a full #rrggbb value.'];
  const ratio = contrast('#FFFFFF', colours[1]);
  if (ratio < 4.5) out.push(`White text on the Dark colour is hard to read (contrast ${ratio.toFixed(1)}, wants 4.5).`);
  if (new Set(colours.map((c) => c.toLowerCase())).size < 5) out.push('Two of the five colours are the same.');
  return out;
}
export const slugOk = (key) => /^[a-z][a-z0-9_]{0,39}$/.test(key);

// ---- Activity rows: the palette of what was done, and a light's own look (as the Admin app: ActivityData.kt) ----

/** The palette key a row takes: what was done to (read from the row's icon), else coming and going, an override, or a device not answering. */
export function activityTone(line) {
  if (line.kind === 'overruled') return 'warn';
  if (line.kind === 'presence') return 'ok';
  if (line.kind === 'device') return line.state === 'online' ? 'neutral' : 'unavailable';
  const i = String(line.icon || '').toLowerCase(); const has = (...p) => p.some((x) => i.includes(x));
  if (has('alarm', 'shield', 'smoke')) return 'alarm';
  if (has('lock', 'door', 'garage')) return 'doors';
  if (has('blind', 'curtain', 'window', 'roller')) return 'blinds';
  if (has('bulb', 'lamp', 'light')) return 'lights';
  if (has('thermostat', 'radiator', 'fire', 'heat')) return 'heating';
  if (has('snowflake', 'air-conditioner', 'cool')) return 'cooling';
  if (has('fan', 'purifier', 'air-filter')) return 'air';
  if (has('vac', 'robot')) return 'gregg';
  if (has('ev-', 'car', 'charger')) return 'car';
  if (has('television', 'speaker', 'cast', 'music', 'volume', 'radio', 'play', 'pause')) return 'media';
  if (has('plug', 'power')) return 'plugs';
  if (has('cart', 'shopping')) return 'shopping';
  if (has('check', 'clipboard', 'todo', 'format-list')) return 'tasks';
  if (has('calendar')) return 'calendar';
  return 'neutral';
}
const SCENE_LOOKS = { relax: ['#F0A060', '#E0703A'], bright: ['#FFF3C9', '#FFD36E'], soho: ['#7E3FF2', '#FF4FA3'], movie: ['#2B2F6B', '#E8A33B'], nightlight: ['#3A2B7E', '#7E57C2'] };
export const SCENE_ICONS = { relax: 'room-living', bright: 'sun', soho: 'palette', movie: 'television-play', nightlight: 'moon' };
/** A light's own look as two ground colours (brightness, a white, a scene, a chosen colour), or null to use the palette of what was done. */
export function lookColours(look) {
  if (!look) return null;
  if (look.look === 'bright') return ['#FFF3C9', '#FFD36E'];
  if (look.look === 'cool') return ['#E4F0FF', '#9FC9F2'];
  if (look.look === 'warm') return ['#FFDDA6', '#F2A24E'];
  if (look.look === 'scene') return SCENE_LOOKS[String(look.name || '').toLowerCase()] || ['#FFF3C9', '#FFD36E'];
  if (look.look === 'colour' && HEX.test(look.hex || '')) { const c = look.hex; const k = (x) => Math.round(parseInt(x, 16) * 0.35); return [`#${[1, 3, 5].map((i) => k(c.slice(i, i + 2)).toString(16).padStart(2, '0')).join('')}`, c]; }
  return null;
}
/** The top line of a row: what happened with the thing first when the house says so, else its text with a capital. */
export const activityHeadline = (line) => line.headline || `${line.text.charAt(0).toUpperCase()}${line.text.slice(1)}`;
/** The second line: the time, then who asked or which rule ran (the house cannot tell a dial from the app). */
export function activityDetail(line, assistant = 'the assistant') {
  if (line.kind === 'device') return `${clock(line.ts)} · ${line.state === 'online' ? 'Answering again' : 'Not answering for ten minutes or more'}`;
  const note = activityNote(line, assistant) || (line.kind === 'presence' ? 'Home Assistant noticed their phone' : 'Done by hand or in the app');
  return `${clock(line.ts)} · ${note.charAt(0).toUpperCase()}${note.slice(1)}`;
}
