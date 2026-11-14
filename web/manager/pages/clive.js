// The Clive group: his animations, his chats (not recorded yet), the activity log and his devices (not built yet). One page, a sub-page per part, chosen in the dock's second bar.
import { esc, ic } from '../ui.js';
import { renderAnimations } from './animations.js';
import { renderActivity } from './activity.js';
import { renderChats } from './chats.js';
import { renderCliveDevices } from './clive-devices.js';

export const CLIVE_SUBS = [['animations', 'Animations', 'animation-play'], ['chats', 'Chats', 'mic-message'], ['activity', 'Activity log', 'history'], ['devices', 'His devices', 'watch']];
export const subOf = (subs, arg) => subs.find((s) => s[0] === String(arg || '').split('/')[0]) || subs[0];

/** The second bar of the dock for a group's sub-pages. */
export const subDockHtml = (page, label, subs, current) => `<nav class="subdock" aria-label="${esc(label)}">${subs.map((s) => `<a class="si2${s[0] === current ? ' on' : ''}" href="#${page}/${s[0]}">${ic(s[2], 16)}${esc(s[1])}</a>`).join('')}</nav>`;


export function renderClive(el, ctx, arg) {
  const sub = subOf(CLIVE_SUBS, arg)[0];
  ctx.setSub?.(subDockHtml('clive', 'Clive', CLIVE_SUBS, sub));
  if (sub === 'activity') return renderActivity(el, ctx);
  if (sub === 'chats') return renderChats(el, ctx);
  if (sub === 'devices') return renderCliveDevices(el, ctx);
  return renderAnimations(el, ctx);
}
