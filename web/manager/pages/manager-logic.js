// The pure parts of the Manager pages (ported from ManagerPages.kt / ManagerKit.kt in the apps), kept apart so tests can run them.

/** The notification kinds by group, in the order the integration sends them. */
export function kindGroups(kinds) {
  const all = Array.isArray(kinds) ? kinds : [];
  return [...new Set(all.map((k) => k.group))].map((g) => [g, all.filter((k) => k.group === g)]);
}
/** Whether a kind goes to a person: everyone, or ticked. */
export const kindGoesTo = (kind, personId) => !!kind.all || (kind.people || []).includes(personId);
/** Whether a person may be ticked for a kind: costs and the like are for administrators only. */
export const canTick = (kind, person) => !kind.admin_only || !!person.admin;
export const validIconName = (name) => /^mdi:[a-z0-9]+(-[a-z0-9]+)*$/.test(String(name || '').trim());

/** The camera links of one alarm mode: trigger entity ids with their cameras and recording seconds. */
export function linksOf(links, mode) {
  const table = links?.[mode] || {};
  return Object.entries(table).map(([trigger, rule]) => ({ trigger, cams: rule?.cams || [], secs: Number(rule?.secs) || 30 }));
}
const TRIGGER_CLASSES = new Set(['motion', 'occupancy', 'door', 'window', 'opening', 'garage_door', 'sound', 'vibration', 'moving', 'presence', 'running']);
/** Things that can start a recording: movement, doors, doorbell and the like, that are not already linked. */
export function triggerChoices(states, taken) {
  return Object.values(states).filter((e) => e.entity_id.startsWith('binary_sensor.') && TRIGGER_CLASSES.has(e.attributes?.device_class) && !taken.has(e.entity_id))
    .sort((a, b) => name(a).localeCompare(name(b)));
}
export const name = (e) => e?.attributes?.friendly_name || e?.entity_id || '';
export const modeLabel = (m) => ({ disarmed: 'Disarmed', home: 'Home', away: 'Away' }[m] || m.replace(/_/g, ' ').replace(/^./, (c) => c.toUpperCase()));
export const presetLabel = (s) => String(s || '').replace(/[_-]+/g, ' ').replace(/^./, (c) => c.toUpperCase());

/** Zones other than home... home first, then by name, with the people in each. */
export function zoneRows(states) {
  const people = Object.values(states).filter((e) => e.entity_id.startsWith('person.'));
  return Object.values(states).filter((e) => e.entity_id.startsWith('zone.'))
    .map((z) => ({
      id: z.entity_id, name: name(z), home: z.entity_id === 'zone.home', radius: z.attributes?.radius != null ? Math.round(z.attributes.radius) : null,
      people: people.filter((p) => p.state === z.attributes?.friendly_name || (z.entity_id === 'zone.home' && p.state === 'home')).map(name),
    })).sort((a, b) => (b.home - a.home) || a.name.localeCompare(b.name));
}

/** How a person's presence reads: Home, a place they have named, a zone, or Away. */
export function presenceWord(state, place, zone) {
  if (state === 'home') return 'Home';
  if (place) return place;
  if (zone) return zone;
  return state === 'not_home' || !state ? 'Away' : presetLabel(state);
}
