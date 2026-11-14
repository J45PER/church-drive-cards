// The four status tiles at the top of Home: what can be shown there, and which four (chosen under Settings, Home page; kept in this browser).
export const SLOT_CATALOG = [
  { id: 'edits', group: 'Control panel', label: 'Edits', go: 'changes' },
  { id: 'versions', group: 'Control panel', label: 'Versions', go: '' },
  { id: 'behind', group: 'Control panel', label: 'Phones behind', go: '' },
  { id: 'clive', group: 'Clive', label: 'Clive now', go: 'clive/devices' },
  { id: 'chats', group: 'Clive', label: 'Chats to review', go: 'clive/chats' },
  { id: 'lastact', group: 'Clive', label: 'Last activity', go: 'clive/activity' },
];
export const DEFAULT_SLOTS = ['edits', 'versions', 'clive', 'chats'];
const KEY = 'manager-home-slots';
const known = (id) => SLOT_CATALOG.some((c) => c.id === id);

/** The chosen four (the usual four when none was chosen or the choice is not usable). */
export function getSlots() {
  try { const s = JSON.parse(localStorage.getItem(KEY) || 'null'); if (Array.isArray(s) && s.length === 4 && s.every(known)) return s; } catch { /* the usual four */ }
  return [...DEFAULT_SLOTS];
}
export function setSlots(slots) {
  try { if (Array.isArray(slots) && slots.length === 4 && slots.every(known)) localStorage.setItem(KEY, JSON.stringify(slots)); } catch { /* not kept */ }
}
export const resetSlots = () => { try { localStorage.removeItem(KEY); } catch { /* nothing to reset */ } };
