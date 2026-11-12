// The Home page, as signed off on the example site: a greeting, a row of status tiles (each opens its page), the versions folded under them, then the pages in
// three groups as small tone cards (a number badge where something waits). The four status tiles are Versions, Device health, Energy now and Edits.
import { esc, NA, ic, palette, circ, toneVars, pf } from '../ui.js';
import { readVersions, summary as versionSummary, readPeopleVersions } from './versions.js';
import { energySnapshot, ago } from './logic.js';

const GROUPS = [['The house', 'home', 'ok'], ['Design', 'palette', 'tasks'], ['Manage', 'sliders', 'warn']];
const TONE = { energy: 'energy', devices: 'media', health: 'ok', activity: 'people', colours: 'tasks', design: 'people', iconlib: 'media', reference: 'gregg', manager: 'warn', changes: 'tasks' };
const SLOTS = ['versions', 'health', 'energy', 'edits'];
const LABEL = { versions: 'Versions', health: 'Device health', energy: 'Energy now', edits: 'Edits' };
const GO = { versions: '', health: 'health', energy: 'energy', edits: 'changes' };

/** What the status tiles say, from the house's own sensors (a tile with nothing to say reads "Data not available"). */
export function homeFacts(states, versions) {
  const by = (id) => states[id], num = (id) => { const n = Number(by(id)?.state); return Number.isFinite(n) ? n : null; };
  const out = {};
  const health = num('sensor.church_drive_device_health');
  if (health != null) out.health = { title: health ? `${health} need attention` : 'All fine', tone: health ? 'warn' : 'ok', badge: health };
  const e = energySnapshot(Object.values(states));
  if (e.rate != null) { const cheap = e.cheapest != null && e.rate <= e.cheapest + 0.01; out.energy = { title: e.cheapest == null ? pf(e.rate) : cheap ? 'Cheap rate' : 'Peak rate', tone: cheap ? 'ok' : '' }; }
  const edits = num('sensor.church_drive_proposals');
  if (edits != null) out.edits = { title: edits ? `${edits} waiting` : 'None waiting', tone: '', badge: edits };
  if (versions) out.versions = { title: versions.rows[0]?.version || '–', tone: versions.tone === 'ok' ? 'ok' : 'warn' };
  return out;
}

const factHtml = (id, f) => `<a class="hm-fact ${f?.tone || ''}" href="#${GO[id]}"><small><i></i>${LABEL[id]}</small><strong>${f ? esc(f.title) : NA}</strong></a>`;

const tag = (status) => ({ same: ['Same', 'ok'], behind: ['Behind', 'w'], ahead: ['Ahead', 'w'], unknown: ['Unknown', ''] }[status] || ['', '']);
const verRow = (name, note, version, status) => { const [t, k] = tag(status); return `<div class="hm-ver"><span class="hm-vname">${esc(name)}${note ? ` <span class="sub">${esc(note)}</span>` : ''}</span><code>${esc(version || '–')}</code><span class="tagp ${k}">${t}</span></div>`; };

export function renderHome(el, ctx, pages) {
  const hour = new Date().getHours(), first = (ctx.user?.name || '').split(' ')[0] || 'there';
  const hello = `${hour < 12 ? 'Good morning' : hour < 18 ? 'Good afternoon' : 'Good evening'}, ${esc(first)}`;
  let facts = {}, versions = null;
  const tile = (p) => {
    const key = p.id === 'health' ? (facts.health?.tone === 'warn' ? 'warn' : 'ok') : TONE[p.id] || 'neutral', badge = facts[p.id === 'changes' ? 'edits' : p.id]?.badge;
    return `<a class="hm-tile glow" href="#${p.id}" style="${toneVars(ctx, key)}"><span class="ico">${ic(p.icon.replace(/^cd:/, ''), 26)}</span><b>${esc(p.title)}</b>${badge ? `<span class="hm-badge">${badge}</span>` : ''}</a>`;
  };
  const groups = () => GROUPS.map(([g, icon, key]) => `<div><div class="hm-gh" style="--c:${circ(ctx, key)}"><span class="hm-gi">${ic(icon, 18)}</span><h2>${g}</h2></div><div class="hm-grid">${pages.filter((p) => p.group === g).map(tile).join('')}</div></div>`).join('');
  const draw = () => {
    const open = el.querySelector('#versions')?.open;
    el.innerHTML = `<div class="stack"><p class="hm-hello">${hello}</p><div class="hm-strip">${SLOTS.map((id) => factHtml(id, facts[id])).join('')}</div>
      <details class="hm-fold" id="versions"${open ? ' open' : ''}><summary><b>Versions</b><span class="sub">${versions ? esc(versions.text) : 'Reading the versions…'}</span></summary><div id="ver-body"></div></details>
      <div class="hm-groups">${groups()}</div></div>`;
    if (versions) el.querySelector('#ver-body').innerHTML = versions.html;
  };
  draw();
  (async () => {
    let states = {}, rows = null;
    try { states = Object.fromEntries((await ctx.ha.call('get_states')).map((e) => [e.entity_id, e])); } catch { /* the tiles say so */ }
    facts = homeFacts(states, null); if (!el.isConnected) return; draw();   // the tiles do not wait for the versions (those ask GitHub as well)
    try { rows = await readVersions(ctx); } catch (e) { versions = { text: `Could not read the versions: ${e.message}`, tone: 'warn', rows: [], html: '' }; }
    if (rows) {
      const sum = versionSummary(rows);
      versions = { text: sum.text, tone: sum.tone, rows, html: `<div class="hm-vgrid">${rows.map((r) => verRow(r.name, r.note.replace(/^latest test build /, 'build '), r.version, r.status)).join('')}</div><p class="label" style="margin-top:12px">On each phone</p><div class="hm-vgrid" id="who-versions"><p class="sub">Reading the phones…</p></div>` };
    }
    facts = homeFacts(states, versions); if (!el.isConnected) return; draw();
    if (!rows) return;
    try {
      const people = await readPeopleVersions(ctx, rows[0].version || rows[1]?.version), box = el.querySelector('#who-versions');
      const html = people.length ? people.map((r) => verRow(r.person, `${r.phone}${r.model && r.model !== r.phone ? `, ${r.model}` : ''} · ${r.seen ? ago(Date.parse(r.seen)) : 'not heard'}`, r.version, r.status)).join('') : '<p class="sub">No phone with a Church Drive app is known to the house yet.</p>';
      versions.html = versions.html.replace(/<p class="sub">Reading the phones…<\/p>/, html); if (box) box.innerHTML = html;
    } catch { const box = el.querySelector('#who-versions'); if (box) box.innerHTML = '<p class="sub">Could not read the people from the house.</p>'; }
  })();
}
