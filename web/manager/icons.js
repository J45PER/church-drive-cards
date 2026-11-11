// The Clive house icon set (the `cd:` icons the apps and dashboards use), drawn on a 200 unit grid. The site reads the same file Home Assistant serves
// (copied into /clive/ when the site is published); the icons arrive after the first paint, so each icon is a placeholder until then.

let set = null, pending = null;
const waiting = new Set();

/** Loads the icon set once; resolves with true when it is available. */
export function loadIcons(fallbackBase = '') {
  if (set) return Promise.resolve(true);
  pending ||= (async () => {
    for (const url of ['clive/icons.json', fallbackBase && `${fallbackBase}/church_drive/clive/icons.json`].filter(Boolean)) {
      try { const r = await fetch(url); if (r.ok) { set = (await r.json()).icons || {}; break; } } catch { /* try the next place */ }
    }
    waiting.forEach((fn) => fn()); waiting.clear();
    return !!set;
  })();
  return pending;
}

/** The icon [name] (like `cd:home`) as an inline SVG that takes the text colour, or an empty box of the same size until the set has loaded. */
export function icon(name, size = 24) {
  const p = set?.[name]?.p;
  return `<svg class="ico" data-icon="${name}" width="${size}" height="${size}" viewBox="0 0 200 200" fill="currentColor" aria-hidden="true">${p || ''}</svg>`;
}

/** Fills in the icons of [root] that were drawn before the set arrived. */
export function fillIcons(root) {
  if (!set) { waiting.add(() => fillIcons(root)); return; }
  root.querySelectorAll('svg.ico').forEach((svg) => { if (!svg.firstChild) svg.innerHTML = set[svg.dataset.icon]?.p || ''; });
}

/** Every icon name in the set (once it has loaded), sorted. */
export function iconNames() { return set ? Object.keys(set).filter((k) => k.startsWith('cd:')).sort() : []; }
