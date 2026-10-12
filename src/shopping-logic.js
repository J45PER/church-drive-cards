// The plain bits of the Shopping card (no page in here, so they can be tested alone).

// The shopping list id a task's description points at (see shopping_logic.py in the integration), or ''.
export const shoppingIdOf = (description) => (String(description || '').match(/church-drive-shopping:([0-9a-f]{6,32})/) || [])[1] || '';

// Whether that task is a steps list (a checklist) rather than shopping: its marker has "steps" after the id.
export const isStepsTask = (description) => /church-drive-shopping:[0-9a-f]{6,32} steps/.test(String(description || ''));

// What a person typed or pasted into the add box: one item per line, comma or semicolon.
export const splitItems = (text) =>
  String(text || '')
    .split(/\n|,|;/)
    .map((s) => s.trim())
    .filter(Boolean);

// What to show under a shopping or steps task instead of its raw description: how many are left to get or do.
export const shoppingNote = (description) => {
  const n = String(description || '').split('\n').filter((l) => l.trim().startsWith('•')).length;
  if (isStepsTask(description)) return n ? `${n} to do` : 'Steps';
  return n ? `${n} to get` : 'Shopping list';
};

// A Google Maps link that starts directions to a place ({ lat, lon }).
export const mapsUrl = (place) => `https://www.google.com/maps/dir/?api=1&destination=${place.lat},${place.lon}`;

// The same place found twice (a search result picked again) is one place.
export const samePlace = (a, b) => Math.abs(a.lat - b.lat) < 1e-5 && Math.abs(a.lon - b.lon) < 1e-5;
