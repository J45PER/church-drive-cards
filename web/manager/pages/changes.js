// Changes sent from the site: what waits for Clive to check, what was applied or declined (with the reason), and the past versions that can be restored.
const esc = (t) => String(t ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const KIND = { palette: 'Palettes', cards: 'Card picks', icons: 'Icons', layouts: 'Layouts' };
const when = (s) => (s ? new Date(s * 1000).toLocaleString('en-GB', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }) : '');

export function describe(change, kind = 'palette') {
  if (!change) return '';
  if (kind === 'cards') { const ids = Object.keys(change.cards || {}); return `Card picks: ${ids.slice(0, 4).join(', ')}${ids.length > 4 ? ` and ${ids.length - 4} more` : ''}`; }
  if (kind === 'icons') return `${change.panel} (${change.dashboard}/${change.view}): icon ${change.before ? `${change.before} → ` : ''}${change.icon}`;
  if (kind === 'layouts') return `${change.dashboard}/${change.view}: panel order${change.hidden?.length ? `, hiding ${change.hidden.join(', ')}` : ''}`;
  if (change.mode === 'reset') return `Put back ${change.keys.join(', ')} to the shipped colours`;
  const keys = Object.keys(change.palettes || {});
  if (change.mode === 'replace') return `Set the changed palettes to a past version (${keys.length} palette${keys.length === 1 ? '' : 's'} changed from shipped)`;
  return `Change ${keys.join(', ')}`;
}

export async function renderChanges(el, ctx, { glowStyle }) {
  el.innerHTML = '<p class="note">Reading the changes from the house…</p>';
  let items, versions;
  try { [items, versions] = await Promise.all([ctx.ha.call('church_drive/manager/proposals', { status: 'all' }),
      Promise.all(['palette', 'cards', 'icons', 'layouts'].map((kind) => ctx.ha.call('church_drive/manager/versions', { kind }).then((l) => l.map((v) => ({ ...v, kind })), () => [])))
        .then((all) => all.flat().sort((a, b) => b.at - a.at))]); }
  catch (e) { el.innerHTML = `<p class="err" role="alert">Could not read the changes: ${esc(e.code === 'unknown_command' ? 'Home Assistant is running an older Church Drive without proposals. Update Church Drive in HACS, then restart Home Assistant.' : e.message)}</p>`; return; }
  const waiting = items.filter((i) => i.status === 'pending'), done = items.filter((i) => i.status !== 'pending');
  const line = (i) => `<article class="card compact" style="${glowStyle(i.status === 'applied' ? '#1F5C3A' : i.status === 'declined' ? '#6B1E24' : '#2C2A6B')}">
    <div class="row"><h3 class="title" style="margin:0;flex:1">${esc(describe(i.change, i.kind))}</h3><span class="tag">${esc(i.status === 'pending' ? 'Waiting' : i.status === 'applied' ? 'Applied' : 'Declined')}</span></div>
    <p class="sub">${esc(i.by)} · ${esc(when(i.at))}${i.note ? ` · “${esc(i.note)}”` : ''}</p>
    ${(i.checks || []).map((c) => `<p class="sub">⚠ ${esc(c)}</p>`).join('')}
    ${i.status === 'declined' && i.reason ? `<p class="sub">Why: ${esc(i.reason)}</p>` : ''}
    ${i.status === 'applied' ? `<p class="sub">Applied ${esc(when(i.resolved_at))} by ${esc(i.resolved_by)}</p>` : ''}</article>`;
  el.innerHTML = `<div class="stack">
    <section class="stack" style="gap:10px"><div class="head"><h2>Waiting to be checked</h2><span class="count">${waiting.length}</span></div>
      ${waiting.length ? `<div class="grid tight">${waiting.map(line).join('')}</div>` : '<p class="note">Nothing is waiting. A change you send from the Colours page waits here until Clive checks it and applies it or says why not.</p>'}</section>
    <section class="stack" style="gap:10px"><div class="head"><h2>Done</h2><span class="count">${done.length}</span></div>
      ${done.length ? `<div class="grid tight">${done.slice(0, 30).map(line).join('')}</div>` : '<p class="note">Nothing yet.</p>'}</section>
    <section class="stack" style="gap:10px"><div class="head"><h2>Past versions</h2><span class="count">${versions.length}</span></div>
      ${versions.length ? `<div class="grid tight">${versions.map((v) => `<article class="card plain compact"><div class="row"><div style="flex:1"><p class="row-title">${esc(KIND[v.kind])} · version ${esc(v.revision)}</p>
        <p class="sub">Replaced by ${esc(v.by)} · ${esc(when(v.at))}${v.kind === 'palette' ? ` · ${Object.keys(v.overrides || {}).length} changed from shipped` : ''}</p></div>
        <button class="btn quiet" type="button" data-restore="${esc(v.revision)}" data-kind="${esc(v.kind)}">Restore</button></div></article>`).join('')}</div>
      <label class="sub"><input type="checkbox" id="urgent"> Urgent: apply a restore straight away instead of sending it for checking</label>` : '<p class="note">A version is kept each time a change is applied.</p>'}
      <p class="note" id="msg" role="status"></p></section></div>`;
  el.querySelectorAll('[data-restore]').forEach((b) => b.addEventListener('click', async () => {
    const msg = el.querySelector('#msg'); b.disabled = true;
    try { await ctx.ha.call('church_drive/manager/propose', { kind: b.dataset.kind, restore: Number(b.dataset.restore), urgent: !!el.querySelector('#urgent')?.checked }); await renderChanges(el, ctx, { glowStyle }); }
    catch (e) { b.disabled = false; msg.textContent = e.message; }
  }));
}
