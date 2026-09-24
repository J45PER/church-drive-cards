// Icons that don't depend on <ha-icon> for custom packs.
//
// Custom icon packs (e.g. `phu:` from Custom Brand Icons) register themselves
// on window.customIcons when their own resource loads. <ha-icon> resolves a
// pack icon once, when it's first drawn; if the pack wasn't registered at that
// moment, or the lookup races a redraw, it stays blank. So pack icons are
// resolved here straight from the pack's getIcon() and drawn as inline SVG,
// cached so rows redraw instantly. Built-in `mdi:`/`hass:` icons still use
// <ha-icon>, which handles them reliably.

const cache = new Map(); // icon -> { path, viewBox } | null (not in pack)
const pending = new Map(); // icon -> Promise

function packFor(icon) {
  const [prefix, name] = String(icon || '').split(':');
  if (!name || prefix === 'mdi' || prefix === 'hass') return null;
  const set = window.customIcons && window.customIcons[prefix];
  if (set && typeof set.getIcon === 'function') return { get: () => set.getIcon(name) };
  const legacy = window.customIconsets && window.customIconsets[prefix];
  if (typeof legacy === 'function') return { get: () => legacy(name) };
  return { waiting: true };
}

function isCustom(icon) {
  const [prefix, name] = String(icon || '').split(':');
  return !!name && prefix !== 'mdi' && prefix !== 'hass';
}

function svg(def, size) {
  const d = (def.path || '').replace(/"/g, '&quot;');
  return `<svg viewBox="${def.viewBox || '0 0 24 24'}" width="${size}" height="${size}" style="display:block; fill:currentColor;"><path d="${d}"></path></svg>`;
}

function escapeAttr(text) {
  return String(text).replace(/&/g, '&amp;').replace(/"/g, '&quot;');
}

// HTML for an icon. `style` is applied to the outer element; `size` is a CSS
// length (e.g. "24px" or "40cqw").
export function iconHtml(icon, { size = '24px', style = '', cls = '' } = {}) {
  if (!isCustom(icon)) {
    return `<ha-icon class="${cls}" icon="${escapeAttr(icon)}" style="--mdc-icon-size:${size}; ${style}"></ha-icon>`;
  }
  const def = cache.get(icon);
  const inner = def ? svg(def, '100%') : '';
  return `<span class="${cls} cdc-icon" data-icon="${escapeAttr(icon)}" style="display:inline-flex; width:${size}; height:${size}; ${style}">${inner}</span>`;
}

function resolve(icon) {
  if (cache.has(icon)) return Promise.resolve(cache.get(icon));
  if (pending.has(icon)) return pending.get(icon);
  const promise = new Promise((done) => {
    let tries = 0;
    const attempt = () => {
      const pack = packFor(icon);
      if (pack && pack.get) {
        Promise.resolve(pack.get())
          .then((def) => {
            const ok = def && def.path ? def : null;
            cache.set(icon, ok);
            done(ok);
          })
          .catch(() => {
            cache.set(icon, null);
            done(null);
          });
        return;
      }
      // Pack not registered yet: keep checking for ~20s.
      tries += 1;
      if (tries > 80) {
        pending.delete(icon);
        done(null);
        return;
      }
      setTimeout(attempt, 250);
    };
    attempt();
  });
  pending.set(icon, promise);
  return promise;
}

// Fill in any pack icons under `root` that aren't drawn yet. An icon the pack
// doesn't have falls back to a neutral mdi icon rather than a blank space.
export function hydrateIcons(root) {
  root.querySelectorAll('span.cdc-icon[data-icon]').forEach((el) => {
    if (el.firstChild) return;
    const icon = el.getAttribute('data-icon');
    resolve(icon).then((def) => {
      if (!el.isConnected && !el.parentNode) return;
      if (def) el.innerHTML = svg(def, '100%');
      else if (!el.firstChild) el.innerHTML = `<ha-icon icon="mdi:help-circle-outline" style="--mdc-icon-size:100%; width:100%; height:100%;"></ha-icon>`;
    });
  });
}
