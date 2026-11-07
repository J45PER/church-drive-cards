// Which icon a Gregg level select shows for each of its options (no Home Assistant needed, so it can be tested).

export const GREGG_LEVEL_ENTITIES = ['select.gregg_suction_level', 'select.gregg_cleaning_intensity', 'select.gregg_mop_intensity'];

const SUCTION = { quiet: 'cd:suction-quiet', standard: 'cd:suction-standard', normal: 'cd:suction-standard', turbo: 'cd:suction-turbo', max: 'cd:suction-max', boost_iq: 'cd:suction-boost', boost: 'cd:suction-boost' };
const WATER = ['cd:water-low', 'cd:water-medium', 'cd:water-high'];
const key = (o) => String(o || '').toLowerCase().replace(/[\s-]+/g, '_');

// What a select is about, from its entity id.
export function ggKind(entityId) {
  const id = String(entityId || '');
  if (/suction/.test(id)) return 'suction';
  if (/water|mop/.test(id)) return 'water';
  return 'level';
}

// The icon for one option of a select. `options` is the select's full list (in order).
export function ggLevelIcon(entityId, option, options = []) {
  const kind = ggKind(entityId);
  const k = key(option);
  if (kind === 'suction') return SUCTION[k] || 'cd:fan';
  if (kind === 'water') {
    const list = (options || []).map(key);
    const i = list.indexOf(k);
    if (i < 0 || !list.length) return 'cd:water-medium';
    if (list.length === 1) return WATER[1];
    return WATER[Math.round((i / (list.length - 1)) * (WATER.length - 1))];
  }
  return 'cd:sliders';
}

// The icon for the row as a whole (the current option's).
export function ggRowIcon(entityId, state, options) {
  return ggLevelIcon(entityId, state, options);
}

