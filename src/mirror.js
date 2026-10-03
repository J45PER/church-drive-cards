// Mirror Card, the logic (no DOM, so `npm test` covers it): finding the cards on a
// dashboard page that can be mirrored, naming them for the editor's list, and
// finding one again from the key the Mirror Card saved.
//
// A card on a dashboard has no id, so it's known by what it's about: its area
// ("area:kitchen"), else its entity ("entity:vacuum.gregg"), else its name or
// title, else its type. Two cards that come out the same on one page get "#2",
// "#3" in page order. The key stays right when cards are moved or others are
// added; it only changes if the card's own area, entity or name does.
//
// The Android app does the same (DashboardLights.kt): keep the two in step.

// Cards that only hold other cards, or that aren't a thing to show twice.
const CONTAINERS = new Set([
  'custom:auto-layout-card', 'custom:section-panel-card', 'custom:nav-bar-card', 'custom:section-title-card',
  'vertical-stack', 'horizontal-stack', 'grid', 'conditional', 'custom:stack-in-card',
]);

export const MIRROR_TYPES = ['custom:mirror-card', 'custom:mirror-card-beta'];
const isMirror = (card) => MIRROR_TYPES.includes(card && card.type);

const slug = (v) => String(v).trim();

// What a card is about, as the first part of its key.
export function identity(card) {
  if (card.area) return `area:${slug(card.area)}`;
  if (typeof card.entity === 'string' && card.entity) return `entity:${slug(card.entity)}`;
  const name = card.name || card.title;
  if (name) return `name:${slug(name)}`;
  return `type:${slug(card.type || 'card')}`;
}

// Every card on a view that can be mirrored, in page order:
// [{ key, card, panel, section }]. `panel` is the title of the Section Panel it's in.
export function mirrorable(view) {
  const found = [];
  const seen = new Map();
  const walk = (node, panel) => {
    if (Array.isArray(node)) return node.forEach((n) => walk(n, panel));
    if (!node || typeof node !== 'object') return;
    if (node.type === 'custom:section-panel-card') panel = node.title || panel;
    if (node.type && !CONTAINERS.has(node.type) && !isMirror(node)) {
      const base = identity(node);
      const n = (seen.get(base) || 0) + 1;
      seen.set(base, n);
      found.push({ key: n > 1 ? `${base}#${n}` : base, card: node, panel: panel || '' });
    }
    // Containers (and any card that holds others) are searched inside.
    ['cards', 'card', 'sections'].forEach((k) => node[k] && walk(node[k], panel));
  };
  walk(view.sections || view.cards || [], '');
  return found;
}

// A view of a dashboard config: by its path, else by its position ("2" = the third).
export function findView(config, view) {
  const views = (config && Array.isArray(config.views) && config.views) || [];
  if (view == null || view === '') return null;
  return views.find((v) => v.path === view) || (/^\d+$/.test(String(view)) ? views[Number(view)] : null) || null;
}

// The card a Mirror Card points at: { card, label } or null if it's gone.
export function resolve(config, { view, source }) {
  const v = findView(config, view);
  const hit = v && mirrorable(v).find((m) => m.key === source);
  return hit ? hit.card : null;
}

const pretty = (s) => String(s).replace(/[_-]+/g, ' ').replace(/^./, (c) => c.toUpperCase());

// The list in the editor: "Ground Floor › Kitchen (Light Control)".
export function describe(entry, { areaName = () => '', entityName = () => '' } = {}) {
  const { card, panel } = entry;
  const what =
    card.name || card.title ||
    (card.area && (areaName(card.area) || pretty(card.area))) ||
    (typeof card.entity === 'string' && (entityName(card.entity) || card.entity)) ||
    pretty(card.type.replace(/^custom:/, '').replace(/-card$/, ''));
  const kind = pretty(card.type.replace(/^custom:/, '').replace(/-card$/, ''));
  return `${panel ? `${panel} › ` : ''}${what}${what === kind ? '' : ` (${kind})`}`;
}

// The editor's options for a view: [{ value: key, label }], in page order.
export function options(view, names) {
  return mirrorable(view).map((m) => ({ value: m.key, label: describe(m, names) }));
}
