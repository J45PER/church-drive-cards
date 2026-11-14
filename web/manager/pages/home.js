// The Home page, as signed off on the example site: a greeting, a row of status tiles (each opens its page), the versions folded under them, then the pages in
// three groups as small tone cards (a number badge where something waits). The four status tiles are Versions, Device health, Energy now and Edits.
import { esc, NA, ic, circ, toneVars } from '../ui.js';
import { readVersions, summary as versionSummary, readPeopleVersions } from './versions.js';
import { ago, activityQuery, parseActivity } from './logic.js';

// Home's page tiles: [page and sub-page, title, icon, tone]. Clive's live state and the chats to review join the status tiles when the house reports them.
const GROUPS = [['Design', 'palette', 'tasks', [['design/look', 'Look', 'palette', 'tasks'], ['design/icons', 'Icons', 'bulb-group', 'media'], ['design/layout', 'Layout', 'view-dashboard', 'people'], ['reference', 'Reference', 'image-text', 'gregg']]],
  ['Clive', 'mic', 'people', [['clive/animations', 'Animations', 'animation-play', 'energy'], ['clive/chats', 'Chats', 'mic-message', 'ok'], ['clive/activity', 'Activity log', 'history', 'people'], ['clive/devices', 'His devices', 'watch', 'media']]],
  ['Manage', 'sliders', 'warn', [['manager', 'Settings', 'sliders', 'warn'], ['changes', 'Edits and history', 'checklist', 'tasks']]]];
const SLOTS = ['edits', 'versions', 'behind', 'lastact'];
const LABEL = { edits: 'Edits', versions: 'Versions', behind: 'Phones behind', lastact: 'Last activity' };
const GO = { edits: 'changes', versions: '', behind: '', lastact: 'clive/activity' };

/** What the status tiles say, from the house's own sensors (a tile with nothing to say reads "Data not available"). */
export function homeFacts(states, versions, extra = {}) {
  const num = (id) => { const n = Number(states[id]?.state); return Number.isFinite(n) ? n : null; };
  const out = {};
  const edits = num('sensor.church_drive_proposals');
  if (edits != null) out.edits = { title: edits ? `${edits} waiting` : 'None waiting', tone: '', badge: edits };
  if (versions) out.versions = { title: versions.rows[0]?.version || '–', tone: versions.tone === 'ok' ? 'ok' : 'warn' };
  if (extra.behind != null) out.behind = { title: extra.behind ? String(extra.behind) : 'None', tone: extra.behind ? 'warn' : 'ok' };
  if (extra.lastAt) out.lastact = { title: ago(extra.lastAt), tone: '' };
  return out;
}

const factHtml = (id, f) => `<a class="hm-fact ${f?.tone || ''}" href="#${GO[id]}"><small><i></i>${LABEL[id]}</small><strong>${f ? esc(f.title) : NA}</strong></a>`;

const tag = (status) => ({ same: ['Same', 'ok'], behind: ['Behind', 'w'], ahead: ['Ahead', 'w'], unknown: ['Unknown', ''] }[status] || ['', '']);
const verRow = (name, note, version, status) => { const [t, k] = tag(status); return `<div class="hm-ver"><span class="hm-vname">${esc(name)}${note ? ` <span class="sub">${esc(note)}</span>` : ''}</span><code>${esc(version || '–')}</code><span class="tagp ${k}">${t}</span></div>`; };

export function renderHome(el, ctx) {
  const hour = new Date().getHours(), first = (ctx.user?.name || '').split(' ')[0] || 'there';
  const hello = `${hour < 12 ? 'Good morning' : hour < 18 ? 'Good afternoon' : 'Good evening'}, ${esc(first)}`;
  let facts = {}, versions = null;
  const tile = (p) => {
    const [path, title, icon, key] = p, badge = path === 'changes' ? facts.edits?.badge : 0;
    return `<a class="hm-tile glow" href="#${path}" style="${toneVars(ctx, key)}"><span class="ico">${ic(icon, 26)}</span><b>${esc(title)}</b>${badge ? `<span class="hm-badge">${badge}</span>` : ''}</a>`;
  };
  const groups = () => GROUPS.map(([g, icon, key, items]) => `<div><div class="hm-gh" style="--c:${circ(ctx, key)}"><span class="hm-gi">${ic(icon, 18)}</span><h2>${g}</h2></div><div class="hm-grid">${items.map(tile).join('')}</div></div>`).join('');
  const draw = () => {
    const open = el.querySelector('#versions')?.open;
    el.innerHTML = `<div class="stack"><p class="hm-hello">${hello}</p><div class="hm-strip">${SLOTS.map((id) => factHtml(id, facts[id])).join('')}</div>
      <details class="hm-fold" id="versions"${open ? ' open' : ''}><summary><b>Versions</b><span class="sub">${versions ? esc(versions.text) : 'Reading the versions…'}</span></summary><div id="ver-body"></div></details>
      <div class="hm-groups">${groups()}</div></div>`;
    if (versions) el.querySelector('#ver-body').innerHTML = versions.html;
  };
  draw();
  (async () => {
    let states = {}, rows = null, extra = {};
    try { states = Object.fromEntries((await ctx.ha.call('get_states')).map((e) => [e.entity_id, e])); } catch { /* the tiles say so */ }
    facts = homeFacts(states, null); if (!el.isConnected) return; draw();
    ctx.ha.call('church_drive/activity', activityQuery({ day: 'all' }, new Date(), 1, 0)).then((r) => { const a = parseActivity(r); extra.lastAt = a?.lines[0]?.ts || null; facts = homeFacts(states, versions, extra); if (el.isConnected) draw(); }).catch(() => {});   // the tiles do not wait for the versions (those ask GitHub as well)
    try { rows = await readVersions(ctx); } catch (e) { versions = { text: `Could not read the versions: ${e.message}`, tone: 'warn', rows: [], html: '' }; }
    if (rows) {
      const sum = versionSummary(rows);
      versions = { text: sum.text, tone: sum.tone, rows, html: `<div class="hm-vgrid">${rows.map((r) => verRow(r.name, r.note.replace(/^latest test build /, 'build '), r.version, r.status)).join('')}</div><p class="label" style="margin-top:12px">On each phone</p><div class="hm-vgrid" id="who-versions"><p class="sub">Reading the phones…</p></div>` };
    }
    facts = homeFacts(states, versions, extra); if (!el.isConnected) return; draw();
    if (!rows) return;
    try {
      const people = await readPeopleVersions(ctx, rows[0].version || rows[1]?.version);
      const html = people.length ? people.map((r) => verRow(r.person, `${r.phone}${r.model && r.model !== r.phone ? `, ${r.model}` : ''} · ${r.seen ? ago(Date.parse(r.seen)) : 'not heard'}`, r.version, r.status)).join('') : '<p class="sub">No phone with a Church Drive app is known to the house yet.</p>';
      versions.html = versions.html.replace(/<p class="sub">Reading the phones…<\/p>/, html); extra.behind = people.filter((r) => r.status === 'behind').length;
      facts = homeFacts(states, versions, extra); if (el.isConnected) draw();
    } catch { const box = el.querySelector('#who-versions'); if (box) box.innerHTML = '<p class="sub">Could not read the people from the house.</p>'; }
  })();
}
