// The Activity log, as signed off on the example site and the Admin app: each entry is a 64 px row whose ground is what was done (the palette of the device, or a
// light's own look), a plain icon, what happened on top and the time with its context below, and on the right a pill cut into the row with who did it (their own
// colour and initial, or Clive's three colours and mark). The glow turns behind both.
import { ACTIONS, DAYS, TIMES, actionLabel, activityDetail, activityHeadline, activityProblem, activityQuery, activityTone, activityWho, lookColours, parseActivity, SCENE_ICONS, dayLabel } from './logic.js';
import { circ, esc, ic, lumOf, mixHex, palette, pill, readableOn } from '../ui.js';
import { fillIcons } from '../icons.js';
import { cleanup } from '../kit.js';

const state = { day: 'week', time: 'all', action: 'all', who: '' };
const CLIVE_MARK = (() => { const rr = (x, y, w, r) => `M${x + r},${y}h${w - 2 * r}a${r},${r} 0 0 1 ${r},${r}v${w - 2 * r}a${r},${r} 0 0 1 -${r},${r}h-${w - 2 * r}a${r},${r} 0 0 1 -${r},-${r}v-${w - 2 * r}a${r},${r} 0 0 1 ${r},-${r}z`; return rr(54, 70, 76, 30) + rr(70, 54, 76, 30); })();
const personKey = (name) => `person_${String(name || '').trim().toLowerCase()}`;

/** A row's ground (two colours), whether its ink should be dark, and the colours of its glow. */
export function rowGround(ctx, line) {
  const look = line.kind === 'device' ? null : lookColours(line.look);
  if (look) return { a: look[0], b: look[1], dark: (lumOf(look[0]) + lumOf(look[1])) / 2 > 0.45, ga: look[1], gb: look[0] };
  const p = palette(ctx, activityTone(line)), a = mixHex(p[1], p[0], 0.3);
  return { a, b: p[0], dark: (lumOf(a) + lumOf(p[0])) / 2 > 0.45, ga: p[0], gb: p[2] };
}
function whoPill(ctx, line, assistant) {
  if (line.kind === 'clive' || line.kind === 'clive_auto') return { css: 'linear-gradient(100deg,#4FD08A,#5C6BC0 55%,#AB47BC)', ink: '#fff', av: 'rgba(255,255,255,.22)', inner: `<svg viewBox="40 40 120 120" aria-hidden="true"><path fill-rule="evenodd" d="${CLIVE_MARK}"/></svg>`, name: assistant, ga: '#AB47BC', gb: '#4FD08A', big: true };
  if (line.kind === 'device') { const p = palette(ctx, 'neutral'); return { css: p[0], ink: '#14181F', av: 'rgba(0,0,0,.18)', inner: ic('device-health', 22), name: 'House', ga: p[2], gb: p[3] }; }
  const p = palette(ctx, personKey(line.who)), dark = lumOf(p[0]) < 0.42;
  return { css: p[0], ink: dark ? '#fff' : '#14181F', av: dark ? 'rgba(255,255,255,.22)' : 'rgba(0,0,0,.14)', inner: `<b>${esc(activityWho(line, assistant)[0] || '?')}</b>`, name: activityWho(line, assistant), ga: p[2], gb: p[3] };
}

export function activityRow(ctx, line, assistant) {
  const g = rowGround(ctx, line), who = whoPill(ctx, line, assistant), ink = g.dark ? '#14181F' : '#fff';
  const scene = line.look?.look === 'scene' ? SCENE_ICONS[String(line.look.name || '').toLowerCase()] : null;
  const icon = scene || String(line.icon || 'mdi:home').replace(/^cd:/, '');
  const iconColour = line.state === 'unavailable' ? circ(ctx, 'bad') : line.state === 'online' ? circ(ctx, 'ok') : ink;
  return `<article class="ar" style="background:linear-gradient(100deg,${g.a},${g.b});--ri:${ink};--ga:${g.ga};--gb:${g.gb}"><span class="ai" style="color:${iconColour}">${ic(icon.startsWith('mdi:') ? 'home' : icon, 24)}</span><div class="ax"><b>${esc(activityHeadline(line))}</b><small>${esc(activityDetail(line, assistant))}</small></div>`
    + `<span class="who" style="background:${who.css};color:${who.ink};--ga:${who.ga};--gb:${who.gb}"><span class="wav${who.big ? ' big' : ''}" style="background:${who.av}">${who.inner}</span>${esc(who.name)}</span></article>`;
}

export async function renderActivity(el, ctx) {
  cleanup(ctx);
  const assistant = (ctx.assistant ||= 'Clive');
  const load = async (offset) => {
    let r;
    try { r = parseActivity(await ctx.ha.call('church_drive/activity', activityQuery({ ...state, who: state.who || null }, new Date(), 100, offset))); }
    catch (e) { return { problem: activityProblem(e.code) }; }
    if (!r) return { problem: activityProblem() };
    ctx.assistant = r.assistant; return r;
  };
  el.innerHTML = '<p class="note">Reading the log from the house…</p>';
  let people = [], lines = [], total = 0;
  const first = await load(0);
  if (first.problem) { el.innerHTML = `<p class="err" role="alert">${esc(first.problem)}</p>`; return; }
  people = first.people; lines = first.lines; total = first.total;
  const opts = (list, cur, lab) => list.map((x) => `<option value="${esc(x[0])}"${x[0] === cur ? ' selected' : ''}>${esc(lab(x))}</option>`).join('');
  const draw = () => {
    const A = ctx.assistant, over = lines.filter((l) => l.kind === 'overruled').length, by = {};
    lines.forEach((l) => { if (l.kind === 'user' && people.includes(l.who)) by[l.who] = (by[l.who] || 0) + 1; });
    const top = Object.entries(by).sort((a, b) => b[1] - a[1])[0], tp = top ? palette(ctx, personKey(top[0])) : null;
    const pills = pill(ctx, 'history', `<b>${total}</b> ${total === 1 ? 'entry' : 'entries'}`, circ(ctx, 'tasks'))
      + pill(ctx, 'person', top ? `<b>${esc(top[0])}</b> most active · ${top[1]}` : 'Nobody active', tp ? readableOn(tp[0], tp[3], palette(ctx, 'neutral')[1]) : null)
      + pill(ctx, 'exclamation', over ? `<b>${over}</b> changed after ${esc(A)} set it` : 'Nothing overruled', over ? circ(ctx, 'warn') : circ(ctx, 'ok'));
    const field = (k, inner, label) => `<label class="af"><small>${label}</small><select data-act="${k}" aria-label="${label}">${inner}</select></label>`;
    const groups = []; lines.forEach((l) => { const k = dayLabel(l.ts); const g = groups.find((x) => x[0] === k); if (g) g[1].push(l); else groups.push([k, [l]]); });
    el.innerHTML = `<div class="stack"><div class="hls">${pills}</div>
      <div class="filters afs">${field('day', opts(DAYS, state.day, (x) => x[1]), 'Dates')}${field('time', opts(TIMES, state.time, (x) => x[1]), 'Time of day')}${field('action', opts(ACTIONS, state.action, (x) => actionLabel(x[0], A)), 'Actions')}${field('who', `<option value="">Everyone</option>${people.map((n) => `<option${n === state.who ? ' selected' : ''}>${esc(n)}</option>`).join('')}`, 'Who')}</div>
      ${groups.length ? groups.map(([day, l]) => `<section class="stack" style="gap:6px"><h2 class="sh dayh2">${esc(day)}</h2>${l.map((x) => activityRow(ctx, x, A)).join('')}</section>`).join('') : '<p class="sub">Nothing matches. The house keeps 30 days.</p>'}
      ${lines.length < total ? `<div class="chips"><button class="chip" type="button" data-more>Show older (${lines.length} of ${total})</button></div>` : ''}</div>`;
    fillIcons(el);
    el.querySelectorAll('[data-act]').forEach((s) => s.addEventListener('change', async () => { state[s.dataset.act] = s.value; const r = await load(0); if (r.problem) { el.insertAdjacentHTML('afterbegin', `<p class="err" role="alert">${esc(r.problem)}</p>`); return; } lines = r.lines; total = r.total; if (r.people.length) people = r.people; draw(); }));
    el.querySelector('[data-more]')?.addEventListener('click', async (e) => { e.currentTarget.disabled = true; const r = await load(lines.length); if (!r.problem) { lines = lines.concat(r.lines); total = r.total; } draw(); });
  };
  draw();
}
