import { CLIVE_COMMON } from './clive-common.js';
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
  const rule = def.evenodd ? ' fill-rule="evenodd"' : '';
  return `<svg viewBox="${def.viewBox || '0 0 24 24'}" width="${size}" height="${size}" style="display:block; fill:currentColor;"><path${rule} d="${d}"></path></svg>`;
}


// Clive's own icons, built in so Home Assistant draws them without an icon pack. They are the same shapes as the phone app's (CdIcons.kt):
// his microphone is the Ready pose of his mark, a capsule 72 tall over a flat bar, and is used for every microphone. The viewBox is cropped
// to 1/1.3 of the 200 unit box, which draws it 1.3x like the app.
const CLIVE_MIC_BOX = '23.08 23.08 153.85 153.85';
const MIC = 'M80,67 A20,20 0 0 1 120,67 L120,99 A20,20 0 0 1 80,99 Z M74,137 L126,137 A8,8 0 0 1 134,145 A8,8 0 0 1 126,153 L74,153 A8,8 0 0 1 66,145 A8,8 0 0 1 74,137 Z';
const CLIVE_ICONS = {
  'mdi:microphone': { path: MIC, viewBox: CLIVE_MIC_BOX, evenodd: true },
  'mdi:microphone-outline': { path: MIC, viewBox: CLIVE_MIC_BOX, evenodd: true },
  'mdi:microphone-variant': { path: MIC, viewBox: CLIVE_MIC_BOX, evenodd: true },
  'mdi:microphone-off': { path: MIC + ' M57.3,55.7 L66.7,48.3 L144.7,146.3 L135.3,153.7 Z', viewBox: CLIVE_MIC_BOX, evenodd: true },
  'mdi:microphone-variant-off': { path: MIC + ' M57.3,55.7 L66.7,48.3 L144.7,146.3 L135.3,153.7 Z', viewBox: CLIVE_MIC_BOX, evenodd: true },
  'mdi:microphone-message': { path: 'M60,70 A18,18 0 0 1 96,70 L96,99 A18,18 0 0 1 60,99 Z M54,127 L102,127 A7,7 0 0 1 109,134 A7,7 0 0 1 102,141 L54,141 A7,7 0 0 1 47,134 A7,7 0 0 1 54,127 Z M124,20 H160 A20,20 0 0 1 180,40 V60 A20,20 0 0 1 160,80 H104 V40 A20,20 0 0 1 124,20 Z M116,50 a6,6 0 1,0 12,0 a6,6 0 1,0 -12,0 Z M136,50 a6,6 0 1,0 12,0 a6,6 0 1,0 -12,0 Z M156,50 a6,6 0 1,0 12,0 a6,6 0 1,0 -12,0 Z', viewBox: '0 0 200 200', evenodd: true },
  // The signed-off common set (icon audit #1 to #26): plus, close, chevrons, eye, pin, battery, shields, bulbs, scenes, bolt and more.
  ...CLIVE_COMMON,
};

function escapeAttr(text) {
  return String(text).replace(/&/g, '&amp;').replace(/"/g, '&quot;');
}

// HTML for an icon. `style` is applied to the outer element; `size` is a CSS
// length (e.g. "24px" or "40cqw").
export function iconHtml(icon, { size = '24px', style = '', cls = '' } = {}) {
  // A picture instead of an icon: a web address, or brand:<integration> for
  // Home Assistant's official brand images (e.g. brand:octopus_energy).
  const pic = /^brand:/.test(icon || '') ? `https://brands.home-assistant.io/_/${encodeURIComponent(icon.slice(6))}/icon.png` : /^(https?:)?\/\//.test(icon || '') || /^\/[^/]/.test(icon || '') ? icon : null;
  if (pic) {
    return `<img class="${cls}" src="${escapeAttr(pic)}" alt="" onerror="this.style.visibility='hidden'" style="width:${size}; height:${size}; object-fit:contain; border-radius:6px; ${style}">`;
  }
  const own = CLIVE_ICONS[icon];
  if (own) {
    return `<span class="${cls}" style="display:inline-flex; width:${size}; height:${size}; ${style}">${svg(own, '100%')}</span>`;
  }
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
