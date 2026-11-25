// The icons notifications carry, as the Church Drive app draws them (NoticeIcons.kt) and the house sends them (notice_icons_logic.py):
//   base                a house icon (the `cd:` name without the prefix)
//   base+badge          the base with a solid circle badge bottom-right, the symbol knocked out (a rounded square for the EV plug)
//   base/off            the base with the common off slash
// Own marks as a base: octopus, todo-logo (room for a badge), todo-clock-logo, gregg-idle/cleaning/paused/returning/docked/error (his status icon). Clive's mark can be a badge: clive.

const CLIVE_104 = 'M32.7,25.3H48.7A30,30 0 0 1 78.7,55.3V71.3A30,30 0 0 1 48.7,101.3H32.7A30,30 0 0 1 2.7,71.3V55.3A30,30 0 0 1 32.7,25.3ZM55.3,2.7H71.3A30,30 0 0 1 101.3,32.7V48.7A30,30 0 0 1 71.3,78.7H55.3A30,30 0 0 1 25.3,48.7V32.7A30,30 0 0 1 55.3,2.7Z';
const GREGG = { 'gregg-idle': 'Idle', 'gregg-cleaning': 'Cleaning', 'gregg-paused': 'Paused', 'gregg-returning': 'Returning', 'gregg-docked': 'Charged', 'gregg-error': 'Error' };
export const OWN_BASES = ['octopus', 'todo-logo', 'todo-clock-logo', ...Object.keys(GREGG)];
export const OWN_BADGES = ['clive'];

/** A spec taken apart: {base, badge, off}, or null when it is not one. */
export function parseSpec(text) {
  const raw = String(text || '').trim().toLowerCase();
  if (!raw || raw.length > 60) return null;
  const off = raw.endsWith('/off'); const parts = raw.replace(/\/off$/, '').split('+');
  if (parts.length > 2 || parts.some((p) => !p)) return null;
  return { base: parts[0], badge: parts[1] || null, off };
}
/** A spec put together; the inverse of parseSpec. */
export const specText = ({ base, badge, off }) => (base ? `${base}${badge ? `+${badge}` : ''}${off && !badge ? '/off' : ''}` : '');

let counter = 0;
const inner = (icons, name) => icons?.[`cd:${name}`]?.p || '';
const pathOf = (d) => `<path fill-rule="evenodd" d="${d}"/>`;
const dOf = (p) => /\sd=['"]([^'"]+)['"]/.exec(p || '')?.[1] || '';

function baseMarkup(base, lib) {
  if (base === 'octopus') return lib.octopus ? `<g transform="translate(10 10) scale(7.08)">${pathOf(lib.octopus)}</g>` : '';
  if (base === 'todo-clock-logo') return lib.icons?.cd_logo_todo_clock?.p || '';
  if (base === 'todo-logo') return pathOf(dOf(lib.icons?.cd_logo_todo_clock?.p).split(/(?=M)/).slice(0, 7).join(''));
  if (GREGG[base]) return lib.icons?.[`GreggIcon ${GREGG[base]}`]?.p || '';
  return inner(lib.icons, base);
}

/** The icon [spec] as an inline SVG in the text colour ([lib]: {icons, octopus}); an empty box when it cannot be drawn. */
export function noticeIconSvg(spec, lib, size = 28) {
  const s = parseSpec(spec); const open = `<svg class="nico" width="${size}" height="${size}" viewBox="0 0 200 200" fill="currentColor" aria-hidden="true"`;
  if (!s) return `${open}></svg>`;
  const base = baseMarkup(s.base, lib); const id = `ni${++counter}`;
  if (!s.badge) {
    if (!s.off) return `${open}>${base}</svg>`;
    return `${open}><defs><mask id="${id}"><rect width="200" height="200" fill="#fff"/><line x1="43.9" y1="36.1" x2="168.9" y2="161.1" stroke="#000" stroke-width="25" stroke-linecap="round"/></mask></defs><g mask="url(#${id})">${base}</g><line x1="40" y1="40" x2="165" y2="165" stroke="currentColor" stroke-width="14" stroke-linecap="round"/></svg>`;
  }
  const square = s.badge === 'ev-plug'; const logo = s.base === 'todo-logo';
  const gap = logo ? '' : (square ? '<rect x="100" y="100" width="96" height="96" rx="28.8" fill="#000"/>' : '<circle cx="148" cy="148" r="48" fill="#000"/>');
  const body = `<g mask="url(#${id})"><g transform="${logo ? 'translate(0 0)' : 'translate(11 7) scale(.85)'}">${base}</g></g>`;
  let badge;
  if (s.badge === 'alert-circle-solid') badge = `<g transform="translate(148 148) scale(.533) translate(-100 -100)">${inner(lib.icons, s.badge)}</g>`;
  else {
    const mark = s.badge === 'clive'
      ? `<g transform="translate(148 148) scale(.27) translate(-100 -100)" fill="#000"><g transform="translate(10 10) scale(1.73)">${pathOf(CLIVE_104)}</g></g>`
      : `<g transform="translate(148 148) scale(.38) translate(-100 -100)" fill="#000">${inner(lib.icons, s.badge)}</g>`;
    const disc = square ? '<rect x="108" y="108" width="80" height="80" rx="24"/>' : '<circle cx="148" cy="148" r="40"/>';
    badge = `<mask id="${id}b"><rect width="200" height="200" fill="#fff"/>${mark}</mask><g mask="url(#${id}b)">${disc}</g>`;
  }
  return `${open}><defs><mask id="${id}"><rect width="200" height="200" fill="#fff"/>${gap}</mask></defs>${body}${badge}</svg>`;
}
