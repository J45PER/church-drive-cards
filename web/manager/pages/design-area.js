// The Design area: Look (which palette each card uses, then the palettes themselves), Icons (section icons, then the icon library) and Layout (section order).
// Each part is a page that already existed; this puts them together under one icon in the dock.
import { renderCards, renderIcons, renderLayouts } from './design.js';
import { renderColours } from './colours.js';
import { renderIconLibrary } from './icon-library.js';
import { subDockHtml, subOf } from './clive.js';

export const DESIGN_SUBS = [['look', 'Look', 'palette'], ['icons', 'Icons', 'bulb-group'], ['layout', 'Layout', 'view-dashboard']];

export async function renderDesignArea(el, ctx, arg) {
  const sub = subOf(DESIGN_SUBS, arg)[0];
  ctx.setSub?.(subDockHtml('design', 'Design', DESIGN_SUBS, sub));
  const part = (title) => { const box = document.createElement('div'); box.className = 'stack'; if (title) box.innerHTML = `<div class="ph"><h2 class="sh">${title}</h2></div>`; const body = document.createElement('div'); box.append(body); return [box, body]; };
  const top = document.createElement('div'); const quiet = { ...ctx, setSub: () => {} };   // the palettes' own group bar would replace this one
  el.replaceChildren(top);
  if (sub === 'layout') return renderLayouts(top, ctx);
  const [second, body] = part(sub === 'look' ? 'Palettes' : 'Icon library');
  const first = document.createElement('div'); top.replaceWith(first); first.after(second);
  await (sub === 'look' ? renderCards(first, ctx) : renderIcons(first, ctx));
  await (sub === 'look' ? renderColours(body, quiet) : renderIconLibrary(body, ctx));
}
