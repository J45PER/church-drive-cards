// The Clive group: his animations, his chats (not recorded yet), the activity log and his devices (not built yet). One page, a sub-page per part, chosen in the dock's second bar.
import { esc, ic, toneVars } from '../ui.js';
import { renderAnimations } from './animations.js';
import { renderActivity } from './activity.js';

export const CLIVE_SUBS = [['animations', 'Animations', 'animation-play'], ['chats', 'Chats', 'mic-message'], ['activity', 'Activity log', 'history'], ['devices', 'His devices', 'watch']];
export const subOf = (subs, arg) => subs.find((s) => s[0] === String(arg || '').split('/')[0]) || subs[0];

/** The second bar of the dock for a group's sub-pages. */
export const subDockHtml = (page, label, subs, current) => `<nav class="subdock" aria-label="${esc(label)}">${subs.map((s) => `<a class="si2${s[0] === current ? ' on' : ''}" href="#${page}/${s[0]}">${ic(s[2], 16)}${esc(s[1])}</a>`).join('')}</nav>`;

const soon = (ctx, title, lines) => `<div class="stack"><article class="tcard glow" style="${toneVars(ctx, 'tasks')}"><div class="wc-h"><h2>${title}</h2><span class="wc-p">Not built yet</span></div>${lines.map((l) => `<p class="sub">${l}</p>`).join('')}</article></div>`;

export function renderClive(el, ctx, arg) {
  const sub = subOf(CLIVE_SUBS, arg)[0];
  ctx.setSub?.(subDockHtml('clive', 'Clive', CLIVE_SUBS, sub));
  if (sub === 'activity') return renderActivity(el, ctx);
  if (sub === 'chats') { el.innerHTML = soon(ctx, 'Chats', ['The house will record each chat (who, which device, what was said, what he answered, which ability he used, which card he showed, how long it took), for administrators only and kept 30 days.', 'Here you will read them, mark each answer good or bad and add a note. Notes collect into prompt and rule changes that go through Edits.']); return; }
  if (sub === 'devices') { el.innerHTML = soon(ctx, 'His devices', ['The paired watches, satellites and phones: app version, when each was last heard, whether it is answering, and its wake-word state. No pairing from the site.']); return; }
  return renderAnimations(el, ctx);
}
