// Changes sent from the site: what waits for Clive to check, what was applied or declined (with the reason), and the past versions that can be restored.
const esc = (t) => String(t ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const KIND = { palette: 'Colour palettes', cards: 'Card colours', icons: 'Section icons', layouts: 'Section order', chats: 'Chat notes' };
import { circ, ic, pill, toneVars } from '../ui.js';
const when = (s) => (s ? new Date(s * 1000).toLocaleString('en-GB', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }) : '');

export function describe(change, kind = 'palette') {
  if (!change) return '';
  if (kind === 'cards') { const ids = Object.keys(change.cards || {}); return `Card picks: ${ids.slice(0, 4).join(', ')}${ids.length > 4 ? ` and ${ids.length - 4} more` : ''}`; }
  if (kind === 'icons') return `${change.panel} (${change.dashboard}/${change.view}): icon ${change.before ? `${change.before} → ` : ''}${change.icon}`;
  if (kind === 'chats') { const n = (change.notes || []).length; return `Chat notes: ${n} chat${n === 1 ? '' : 's'} marked for a change to how he answers`; }
  if (kind === 'layouts') return `${change.dashboard}/${change.view}: panel order${change.hidden?.length ? `, hiding ${change.hidden.join(', ')}` : ''}`;
  if (change.mode === 'reset') return `Put back ${change.keys.join(', ')} to the shipped colours`;
  const keys = Object.keys(change.palettes || {});
  if (change.mode === 'replace') return `Set the changed palettes to a past version (${keys.length} palette${keys.length === 1 ? '' : 's'} changed from shipped)`;
  return `Change ${keys.join(', ')}`;
}

export async function renderChanges(el, ctx) {
  el.innerHTML = '<p class="note">Reading the changes from the house…</p>';
  let items, versions;
  try { [items, versions] = await Promise.all([ctx.ha.call('church_drive/manager/proposals', { status: 'all' }),
      Promise.all(['palette', 'cards', 'icons', 'layouts'].map((kind) => ctx.ha.call('church_drive/manager/versions', { kind }).then((l) => l.map((v) => ({ ...v, kind })), () => [])))
        .then((all) => all.flat().sort((a, b) => b.at - a.at))]); }
  catch (e) { el.innerHTML = `<p class="err" role="alert">Could not read the changes: ${esc(e.code === 'unknown_command' ? 'Home Assistant is running an older Church Drive without proposals. Update Church Drive in HACS, then restart Home Assistant.' : e.message)}</p>`; return; }
  const waiting = items.filter((i) => i.status === 'pending'), done = items.filter((i) => i.status !== 'pending');
  const applied = done.filter((i) => i.status === 'applied').length, declined = done.length - applied;
  const notes = (i) => (i.kind === 'chats' ? (i.change.notes || []).slice(0, 4).map((n) => `<p class="sub">${n.rating === 'bad' ? '👎' : n.rating === 'good' ? '👍' : '•'} “${esc(n.asked)}”${n.note ? ` · ${esc(n.note)}` : ''}</p>`).join('') : '');
  const line = (i) => `<article class="tcard glow" style="${toneVars(ctx, i.status === 'applied' ? 'ok' : i.status === 'declined' ? 'bad' : 'tasks')}">
    <div class="wc-h"><h2>${esc(describe(i.change, i.kind))}</h2><span class="wc-p">${esc(i.status === 'pending' ? 'Waiting' : i.status === 'applied' ? 'Applied' : 'Declined')}</span></div>
    <p class="sub">${esc(i.by)} · ${esc(when(i.at))}${i.note ? ` · “${esc(i.note)}”` : ''}</p>${notes(i)}
    ${(i.checks || []).map((c) => `<p class="sub">⚠ ${esc(c)}</p>`).join('')}
    ${i.status === 'declined' && i.reason ? `<p class="sub">Why: ${esc(i.reason)}</p>` : ''}
    ${i.status === 'applied' ? `<p class="sub">Applied ${esc(when(i.resolved_at))} by ${esc(i.resolved_by)}</p>` : ''}</article>`;
  const step = (n, t, text) => `<article class="tcard glow" style="${toneVars(ctx, 'tasks')}"><div class="wc-h"><span class="wc-p">${n}</span><h2>${t}</h2></div><p class="sub">${text}</p></article>`;
  const section = (title, count, body, empty) => `<section class="stack" style="gap:8px"><div class="ph"><h2 class="sh">${title}</h2><span class="sub">${count}</span></div>${count ? body : `<p class="sub">${empty}</p>`}</section>`;
  el.innerHTML = `<div class="stack"><div class="hls">${pill(ctx, 'clock', waiting.length ? `<b>${waiting.length}</b> waiting for Clive` : 'Nothing waiting', circ(ctx, waiting.length ? 'warn' : 'ok'))}${pill(ctx, 'check', `<b>${applied}</b> applied`, circ(ctx, 'ok'))}${pill(ctx, 'alert', declined ? `<b>${declined}</b> declined` : 'None declined', circ(ctx, declined ? 'bad' : 'ok'))}</div>
    <div class="hdg">${step(1, 'You change something', 'On the Design or Clive pages, then press Send.')}${step(2, 'Clive checks it', 'That it reads well and nothing breaks. It waits below until then.')}${step(3, 'It goes live', 'In the apps and dashboards. Every change keeps what it replaced, so you can put it back.')}</div>
    ${section('Waiting for Clive', waiting.length, `<div class="hdg">${waiting.map(line).join('')}</div>`, 'Nothing is waiting.')}
    ${section('Recently done', done.length, `<div class="hdg">${done.slice(0, 30).map(line).join('')}</div>`, 'Nothing yet.')}
    ${section('Put something back', versions.length, `<div class="stack" style="gap:6px">${versions.map((v) => `<div class="rowc" style="--tone:${circ(ctx, 'tasks')}"><span class="ri">${ic('restore', 20)}</span><div class="rt"><b>${esc(KIND[v.kind])} · version ${esc(v.revision)}</b><span class="sub">Was live until ${esc(v.by)} · ${esc(when(v.at))}${v.kind === 'palette' ? ` · ${Object.keys(v.overrides || {}).length} changed from shipped` : ''}</span></div>
        <button class="btn s q" type="button" data-restore="${esc(v.revision)}" data-kind="${esc(v.kind)}">Restore</button></div>`).join('')}</div>
      <label class="sub"><input type="checkbox" id="urgent"> Urgent: apply a restore straight away instead of sending it for checking</label>`, 'Nothing to put back yet: an earlier version is kept each time a change goes live.')}
    <p class="note" id="msg" role="status"></p></div>`;
  el.querySelectorAll('[data-restore]').forEach((b) => b.addEventListener('click', async () => {
    const msg = el.querySelector('#msg'); b.disabled = true;
    try { await ctx.ha.call('church_drive/manager/propose', { kind: b.dataset.kind, restore: Number(b.dataset.restore), urgent: !!el.querySelector('#urgent')?.checked }); await renderChanges(el, ctx); }
    catch (e) { b.disabled = false; msg.textContent = e.message; }
  }));
}
