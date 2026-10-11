// The plain bits of the Shopping card (no page in here, so they can be tested alone).

// The shopping list id a task's description points at (see shopping_logic.py in the integration), or ''.
export const shoppingIdOf = (description) => (String(description || '').match(/church-drive-shopping:([0-9a-f]{6,32})/) || [])[1] || '';

// What a person typed or pasted into the add box: one item per line, comma or semicolon.
export const splitItems = (text) =>
  String(text || '')
    .split(/\n|,|;/)
    .map((s) => s.trim())
    .filter(Boolean);
