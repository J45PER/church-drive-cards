// Clive Manager: sign in with the Home Assistant login (administrators only), then the pages. Home Assistant is where the settings live; this
// site reads them over its WebSocket and (next) sends changes back for Clive to check and apply.
import { DEFAULT_HA, HaSocket, cleanAddress, finishSignIn, houseAddress, signOut, signedIn, startSignIn } from './ha.js';
import { glowStyle, inkOn, setTones } from './glow.js';
import { loadStyle, panel } from './kit.js';
import { createStatus } from './status.js';
import { CapsuleView, capsuleHtml } from './capsule.js';
import { initTheme, mountThemeButton } from './theme.js';
import { TICK_MS, dueOnReturn, duePeriodic, editing, idle } from './freshness.js';
import { fillIcons, icon, loadIcons } from './icons.js';

initTheme();   // a light or dark chosen with the button, before anything is drawn
import { renderChanges } from './pages/changes.js';
import { readVersions, summary as versionSummary } from './pages/versions.js';
import { openPaletteEditor } from './pages/palette-editor.js';
import { renderCards, renderIcons, renderLayouts } from './pages/design.js';
import { renderDevices } from './pages/devices.js';
import { renderHealth } from './pages/health.js';
import { renderManager, renderManagerSection } from './pages/manager.js';
import { LOGO_PATH, LOGO_VIEWBOX } from './logo.js';
import { cleanup } from './kit.js';
import { h } from './pages/dom.js';
import { renderEnergyPage } from './pages/energy.js';
import { renderIconLibrary } from './pages/icon-library.js';
import { renderReference } from './pages/reference.js';
import { readPeopleVersions } from './pages/versions.js';
import { PALETTE_PARTS, paletteWarnings, slugOk, ACTIONS, BAD, DAYS, OK, TIMES, WARN, activityNote, activityProblem, activityQuery, activityWho, actionLabel, ago, buildRegistry, clock, dayLabel, deviceHealthList, healthSummary, parseActivity } from './pages/logic.js';

const root = document.getElementById('root');
const esc = (t) => String(t ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
/** The Clive Admin mark: the house's shield with Clive cut out, in the logo's glow (a single white shape on a dark page). */
function mark(size = 88) {
  const id = `g${Math.random().toString(36).slice(2, 8)}`;
  return `<svg class="mark" width="${size}" height="${size}" viewBox="${LOGO_VIEWBOX}" aria-hidden="true"><defs>
    <radialGradient id="${id}p" gradientUnits="userSpaceOnUse" cx="150" cy="70" r="88"><stop offset="0" stop-color="#AB47BC"/><stop offset="1" stop-color="#AB47BC" stop-opacity="0"/></radialGradient>
    <radialGradient id="${id}g" gradientUnits="userSpaceOnUse" cx="70" cy="170" r="110"><stop offset="0" stop-color="#4FD08A"/><stop offset="1" stop-color="#4FD08A" stop-opacity="0"/></radialGradient>
    <radialGradient id="${id}i" gradientUnits="userSpaceOnUse" cx="40" cy="40" r="130"><stop offset="0" stop-color="#5C6BC0"/><stop offset="1" stop-color="#5C6BC0" stop-opacity="0"/></radialGradient></defs>
    ${['#6A5BC2', `url(#${id}p)`, `url(#${id}g)`, `url(#${id}i)`].map((f) => `<path fill="${f}" fill-rule="evenodd" d="${LOGO_PATH}"/>`).join('')}</svg>`;
}

// ---- Sign in ----

function showSignIn(message) {
  root.innerHTML = `<div class="center"><form class="panel" id="signin">
    ${mark()}<h1>Clive Manager</h1>
    <p class="note">Sign in with your Home Assistant login, the same one the Church Drive apps use. Only administrators can use the Manager.</p>
    ${message ? `<p class="err" role="alert">${esc(message)}</p>` : ''}
    <button class="btn" type="submit">Sign in with Home Assistant</button>
    <details><summary>Use a different Home Assistant address</summary>
      <label class="sub" for="ha">Address</label><input class="field" id="ha" type="url" value="${esc(houseAddress() || DEFAULT_HA)}" autocomplete="url"></details>
  </form></div>`;
  document.getElementById('signin').addEventListener('submit', (e) => { e.preventDefault(); startSignIn(cleanAddress(document.getElementById('ha').value)); });
}

// ---- The pages ----
// A page marked `live` only shows things, so it is read again by itself now and then (see freshness.js); the editors are never redrawn under someone.

const PAGES = [
  { id: 'home', title: 'Clive Manager', label: 'Home', icon: 'cd:home', render: renderHome, live: true },
  { id: 'energy', title: 'Energy', label: 'Energy', icon: 'cd:bolt', group: 'The house', render: (el, c) => renderEnergyPage(el, c) },
  { id: 'devices', title: 'Devices', label: 'Devices', icon: 'cd:television-play', group: 'The house', render: renderDevices },
  { id: 'health', title: 'Device health', label: 'Health', icon: 'cd:device-health', group: 'The house', render: renderHealth, live: true },
  { id: 'activity', title: 'Activity log', label: 'Activity', icon: 'cd:history', group: 'The house', render: renderActivity },
  { id: 'colours', title: 'Colour palettes', label: 'Colours', icon: 'cd:palette', group: 'Design', render: renderColours },
  { id: 'design', title: 'Customise the dashboards', label: 'Customise', icon: 'cd:view-dashboard', group: 'Design', render: renderDesign },
  { id: 'iconlib', title: 'Icon library', label: 'Icon library', icon: 'cd:bulb-group', group: 'Design', render: (el, c) => renderIconLibrary(el, c) },
  { id: 'reference', title: 'Reference', label: 'Reference', icon: 'cd:image-text', group: 'Design', render: (el, c, arg) => renderReference(el, c, arg) },
  { id: 'manager', title: 'Settings', label: 'Settings', icon: 'cd:sliders', group: 'Manage', render: (el, c, arg) => renderManager(el, c, { glowStyle, go }, arg ? decodeURIComponent(arg) : '') },
  { id: 'changes', title: 'Edits and history', label: 'Edits', icon: 'cd:checklist', group: 'Manage', render: (el, c) => renderChanges(el, c, { glowStyle }) },
];
const GROUPS = ['The house', 'Design', 'Manage'];

const HOME_CARDS = {
  energy: ['energy', 'Octopus rates, cheap times, the week, the car charger'], devices: ['media', 'TVs, speakers, and how the house\'s systems are'],
  health: ['ok', 'Batteries, filters and devices that stop responding'], activity: ['people', 'What people and the assistant did, and when'],
  colours: ['tasks', 'Every palette as the house has it now, with the changes marked'], design: ['people', 'Card colours, section icons and the order of dashboard sections'],
  iconlib: ['media', 'Every icon the house draws, searchable'], reference: ['gregg', 'Logos, colours, type, shapes, the apps and the boards worth keeping'],
  manager: ['warn', 'People, notifications, camera links, alarm lights and more'], changes: ['tasks', 'What you sent for Clive to check, what went live, and put things back'],
};
const HOME_ICONS = { 'The house': 'cd:home', Design: 'cd:palette', Manage: 'cd:sliders' };

function renderHome(el, ctx) {
  const hello = `${new Date().getHours() < 12 ? 'Good morning' : new Date().getHours() < 18 ? 'Good afternoon' : 'Good evening'}, ${(ctx.user?.name || '').split(' ')[0] || 'there'}`;
  const group = (g) => panel(ctx, { title: g, icon: HOME_ICONS[g], color: g === 'Design' ? 'tasks' : g === 'Manage' ? 'warn' : 'ok' },
    ...PAGES.filter((p) => p.group === g).map((p) => { const [tone, line] = HOME_CARDS[p.id] || ['neutral', '']; const card = h('a', { class: 'card home-card', href: `#${p.id}`, style: glowStyle(tone) }, h('p', { class: 'title' }, p.title === 'Manager' ? 'Settings' : p.title), h('p', { class: 'sub' }, line)); return card; }));
  el.replaceChildren(h('div', { class: 'stack' }, h('section', { class: 'hero' }, h('p', { class: 'hero-hello' }, hello), h('p', { class: 'sub' }, 'Everything the house shares with its apps, in one place: how it is doing, how it looks, and what to change.')),
    ...GROUPS.map(group)));
  const rest = document.createElement('div'); rest.className = 'stack';
  rest.innerHTML = `<section class="stack" style="gap:12px" id="versions"><div class="head"><h2>Versions</h2><button class="btn quiet" type="button" id="versions-refresh">Check again</button></div>
      <article class="card plain" id="versions-card"><p class="sub">Reading the versions…</p></article></section>`;
  el.firstChild.append(rest);
  fillIcons(el);
  const drawVersions = async () => {
    const card = el.querySelector('#versions-card'); card.innerHTML = '<p class="sub">Reading the versions…</p>';
    let rows; try { rows = await readVersions(ctx); } catch (e) { card.innerHTML = `<p class="err">Could not read the versions: ${esc(e.message)}</p>`; return; }
    const sum = versionSummary(rows);
    const word = { same: 'Same', behind: 'Behind', ahead: 'Ahead', unknown: 'Unknown' };
    card.innerHTML = `<p class="${sum.tone === 'ok' ? 'sub' : 'warn-line'}">${esc(sum.text)}</p>${rows.map((r) => `<div class="row ver"><div style="flex:1"><p class="row-title">${esc(r.name)}</p><p class="sub">${esc(r.note)}</p></div><code>${esc(r.version || '–')}</code><span class="tag ${r.status}">${word[r.status]}</span></div>`).join('')}
      <p class="sub">The app rows show the newest published test build. Below is what each person's phone last told the house it has installed.</p><div id="who-versions"></div>`;
    try {
      const people = await readPeopleVersions(ctx, rows[0].version || rows[1].version);
      const box = card.querySelector('#who-versions');
      box.innerHTML = people.length ? people.map((r) => `<div class="row ver"><div style="flex:1"><p class="row-title">${esc(r.person)} <span class="sub">· ${esc(r.phone)}${r.model && r.model !== r.phone ? `, ${esc(r.model)}` : ''}</span></p><p class="sub">${r.seen ? `Last heard ${esc(ago(Date.parse(r.seen)))}` : 'Not heard from yet'}</p></div><code>${esc(r.version || '–')}</code> <span class="tag ${r.status}">${esc(word[r.status])}</span></div>`).join('') : '<p class="sub">No phone with a Church Drive app has registered yet.</p>';
    } catch { card.querySelector('#who-versions').innerHTML = '<p class="sub">Could not read the people from the house.</p>'; }
  };
  el.querySelector('#versions-refresh').addEventListener('click', drawVersions); drawVersions();
}

async function renderColours(el, ctx) {
  el.innerHTML = '<p class="note">Reading the palettes from the house…</p>';
  let data, shipped = {}, mine = {};
  try { data = await ctx.ha.call('church_drive/colours'); } catch (e) { el.innerHTML = `<p class="err" role="alert">Could not read the palettes: ${esc(e.message)}</p>`; return; }
  try { mine = (await ctx.ha.call('church_drive/manager/design')).groups || {}; } catch { /* groupings are optional: the palettes' own groups are used */ }
  // The shipped colours (a static file Home Assistant serves) tell which palettes have been changed; without them nothing is marked.
  for (const url of ['clive/cards.json', `${houseAddress()}/church_drive/clive/cards.json`]) { try { const r = await fetch(url); if (r.ok) { shipped = (await r.json()).entity_colours?.types || {}; break; } } catch { /* try the next place */ } }
  const palettes = Object.entries(data.palettes || {});
  const groupOf = ([key, p]) => mine[key] || p.group || 'Other';
  const groups = new Map();
  palettes.forEach((e) => { const g = groupOf(e); if (!groups.has(g)) groups.set(g, []); groups.get(g).push(e); });
  const names = [...groups.keys()];
  const NAMES = ['Colour', 'Dark', 'Accent', 'Light', 'Deep'];
  const save = async (next) => { try { await ctx.ha.call('church_drive/manager/groups/set', { groups: next }); mine = next; renderColours(el, ctx); } catch (e) { const m = el.querySelector('#group-msg'); if (m) m.textContent = `Could not save the groups: ${e.message}`; } };
  const card = ([key, p]) => {
    const was = shipped[key]?.colours;
    const changed = !p.custom && was && was.join().toLowerCase() !== p.colours.join().toLowerCase();
    return `<article class="card pal" style="${glowStyle(p.colours[1])}"><div class="row"><h3 class="title" style="margin:0">${esc(p.name)}</h3>
      ${changed ? '<span class="tag">Changed</span>' : ''}${p.custom ? '<span class="tag">Yours</span>' : ''}</div>
      <p class="sub"><code>${esc(key)}</code></p>
      <div class="swatches">${p.colours.map((c, i) => `<div class="sw" title="${NAMES[i]} ${esc(c)}"><i style="background:${esc(c)}"></i><code>${esc(c)}</code></div>`).join('')}</div>
      <div class="pal-foot"><select class="grp" data-key="${esc(key)}" aria-label="Group of ${esc(p.name)}">${names.map((g) => `<option${g === groupOf([key, p]) ? ' selected' : ''}>${esc(g)}</option>`).join('')}<option value="__new">New group…</option></select>
      <button class="btn quiet edit" type="button" data-edit="${esc(key)}">Edit</button></div><div class="edit-host"></div></article>`;
  };
  el.innerHTML = `<div class="stack">
    <p class="note">${palettes.length} ${palettes.length === 1 ? 'palette' : 'palettes'}, revision ${esc(data.revision)}. Palettes are the colours the apps, dashboards and this site use. Edit one and send it: it waits under Edits for Clive to check before it goes live. Groups are only for finding things: choose a group on any card, or pick New group… to make your own.</p>
    <div class="chips"><input class="field" id="find" type="search" placeholder="Find a palette" aria-label="Find a palette" style="flex:1"><button class="btn" type="button" id="add-palette">New palette</button></div><p class="err" id="group-msg" role="status"></p><div id="new-host"></div>
    <div class="stack" id="groups">${[...groups].map(([g, list]) => `<section class="stack" style="gap:12px" data-group><div class="head"><h2>${esc(g)}</h2><span class="count">${list.length}</span><button class="btn quiet rename" type="button" data-rename="${esc(g)}">Rename</button></div><div class="grid4">${list.map(card).join('')}</div></section>`).join('')}</div></div>`;
  const byKey = Object.fromEntries(palettes);
  el.querySelectorAll('[data-edit]').forEach((b) => b.addEventListener('click', () => openPaletteEditor(b.closest('article').querySelector('.edit-host'), ctx, { key: b.dataset.edit, palette: byKey[b.dataset.edit] })));
  el.querySelectorAll('select.grp').forEach((s) => s.addEventListener('change', () => {
    let g = s.value; if (g === '__new') { g = (prompt('Name of the new group') || '').trim(); if (!g) { renderColours(el, ctx); return; } }
    save({ ...mine, [s.dataset.key]: g });
  }));
  el.querySelectorAll('[data-rename]').forEach((b) => b.addEventListener('click', () => {
    const to = (prompt('New name for the group', b.dataset.rename) || '').trim(); if (!to || to === b.dataset.rename) return;
    const next = { ...mine }; groups.get(b.dataset.rename).forEach(([key]) => { next[key] = to; }); save(next);
  }));
  document.getElementById('add-palette').addEventListener('click', () => openPaletteEditor(document.getElementById('new-host'), ctx, { key: null, palette: null }));
  document.getElementById('find').addEventListener('input', (e) => {
    const q = e.target.value.trim().toLowerCase();
    el.querySelectorAll('article.card').forEach((c) => { c.hidden = !!q && !c.textContent.toLowerCase().includes(q); });
    el.querySelectorAll('[data-group]').forEach((s) => { s.hidden = ![...s.querySelectorAll('article.card')].some((c) => !c.hidden); });
  });
}


// ---- Design: cards, icons and layouts ----

const DESIGN_TABS = [
  ['cards', 'Card colours', 'Which colour theme each card uses, on the dashboards and in the apps.', 'tasks', renderCards],
  ['icons', 'Section icons', 'The small icon at the top of each dashboard section.', 'media', renderIcons],
  ['layouts', 'Section order', 'Move a dashboard page\'s sections up or down, or hide them.', 'people', renderLayouts],
];
async function renderDesign(el, ctx, arg) {
  const tab = DESIGN_TABS.find((t) => t[0] === arg) || DESIGN_TABS[0];
  const tiles = DESIGN_TABS.map((t) => `<a class="card design-tile${t[0] === tab[0] ? ' on' : ''}" href="#design/${t[0]}" style="${glowStyle(t[3])}"><p class="title">${esc(t[1])}</p><p class="sub">${esc(t[2])}</p></a>`).join('');
  el.innerHTML = `<div class="stack"><p class="note">Change how the house looks. Whatever you change here is sent to Clive to check first, and you can follow it, or put it back, under <a href="#changes">Edits</a>.</p>
    <div class="grid3">${tiles}</div><div id="design-body"></div></div>`;
  await tab[4](el.querySelector('#design-body'), ctx);
}

// ---- Device health, Energy, Activity ----

const activityState = { day: 'today', time: 'all', action: 'all', who: '' };
async function renderActivity(el, ctx) {
  const assistant = (ctx.assistant ||= 'Clive');
  const load = async (offset, into) => {
    let r;
    try { r = parseActivity(await ctx.ha.call('church_drive/activity', activityQuery({ ...activityState, who: activityState.who || null }, new Date(), 100, offset))); }
    catch (e) { into.innerHTML = `<p class="err" role="alert">${esc(activityProblem(e.code))}</p>`; return null; }
    if (!r) { into.innerHTML = `<p class="err" role="alert">${esc(activityProblem())}</p>`; return null; }
    ctx.assistant = r.assistant; return r;
  };
  const opts = (list, cur, label) => list.map((x) => `<option value="${x[0]}"${x[0] === cur ? ' selected' : ''}>${esc(label(x))}</option>`).join('');
  el.innerHTML = '<p class="note">Reading the log from the house…</p>';
  let people = [], shown = 0;
  const draw = async () => {
    const out = el.querySelector('#log'); out.innerHTML = '<p class="note">Reading the log…</p>'; shown = 0;
    const r = await load(0, out); if (!r) return;
    if (r.people.length) people = r.people;
    out.innerHTML = ''; add(r, out);
  };
  const add = (r, out) => {
    shown += r.lines.length; const groups = new Map();
    r.lines.forEach((l) => { const k = dayLabel(l.ts); if (!groups.has(k)) groups.set(k, []); groups.get(k).push(l); });
    out.querySelector('.more')?.remove();
    if (!r.lines.length && shown === 0) { out.innerHTML = '<p class="note">Nothing matches.</p>'; return; }
    out.insertAdjacentHTML('beforeend', [...groups].map(([day, lines]) => `<section class="stack" style="gap:8px"><div class="head"><h2 class="day-head">${esc(day)}</h2></div>${lines.map((l) => {
      const note = activityNote(l, r.assistant);
      return `<article class="card line compact" style="${glowStyle(l.kind === 'overruled' ? 'bad' : l.kind.startsWith('clive') ? 'tasks' : 'neutral')}"><span class="when">${esc(clock(l.ts))}</span><div><p class="title" style="margin:0"><b>${esc(activityWho(l, r.assistant))}</b> ${esc(l.text)}</p>${note ? `<p class="sub">${esc(note)}</p>` : ''}</div></article>`;
    }).join('')}</section>`).join(''));
    if (shown < r.total) {
      out.insertAdjacentHTML('beforeend', `<button class="btn quiet more" type="button">Show more (${shown} of ${r.total})</button>`);
      out.querySelector('.more').addEventListener('click', async (ev) => { ev.target.disabled = true; const next = await load(shown, out); if (next) add(next, out); });
    }
  };
  // First answer names the people and the assistant, so the pickers can be filled.
  const first = await load(0, el); if (!first) return;
  people = first.people;
  el.innerHTML = `<div class="stack"><div class="filters">
    <select id="f-day" aria-label="Dates">${opts(DAYS, activityState.day, (x) => x[1])}</select>
    <select id="f-time" aria-label="Time of day">${opts(TIMES, activityState.time, (x) => x[1])}</select>
    <select id="f-action" aria-label="Actions">${opts(ACTIONS, activityState.action, (x) => actionLabel(x[0], first.assistant))}</select>
    <select id="f-who" aria-label="Person"><option value="">Everyone</option>${people.map((n) => `<option${n === activityState.who ? ' selected' : ''}>${esc(n)}</option>`).join('')}</select></div>
    <div class="stack" id="log"></div></div>`;
  const log = el.querySelector('#log'); add(first, log);
  [['f-day', 'day'], ['f-time', 'time'], ['f-action', 'action'], ['f-who', 'who']].forEach(([id, key]) => el.querySelector(`#${id}`).addEventListener('change', (ev) => { activityState[key] = ev.target.value; draw(); }));
}

// ---- The shell ----

let ctx = null;
function go(id) { location.hash = id === 'home' ? '' : id; }

function currentPage() { return PAGES.find((p) => p.id === location.hash.slice(1).split('/')[0]) || PAGES[0]; }

function showShell(user) {
  const link = (p, cls = '') => `<a class="${cls}" href="#${p.id === 'home' ? '' : p.id}" data-page="${p.id}">${icon(p.icon, 20)}<span>${esc(p.label)}</span></a>`;
  const home = PAGES[0];
  const top = `${link(home)}${GROUPS.map((g) => `<div class="drop"><button type="button" class="drop-btn" data-group="${esc(g)}" aria-haspopup="true" aria-expanded="false">${esc(g)}<svg viewBox="0 0 20 20" width="14" height="14" aria-hidden="true"><path d="M5 7.5l5 5 5-5" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg></button>
      <div class="drop-list" hidden>${PAGES.filter((p) => p.group === g).map((p) => link(p)).join('')}</div></div>`).join('')}`;
  const drawer = `${link(home)}${GROUPS.map((g) => `<p class="drawer-head">${esc(g)}</p>${PAGES.filter((p) => p.group === g).map((p) => link(p)).join('')}`).join('')}`;
  root.innerHTML = `<div class="shell"><header class="site"><div class="site-in">
      <a class="brand" href="#" aria-label="Clive Manager, home"><svg class="mark" viewBox="${LOGO_VIEWBOX}" aria-hidden="true"><defs>
        <radialGradient id="bk-p" gradientUnits="userSpaceOnUse" cx="150" cy="70" r="88"><stop offset="0" stop-color="#AB47BC"/><stop offset="1" stop-color="#AB47BC" stop-opacity="0"/></radialGradient>
        <radialGradient id="bk-g" gradientUnits="userSpaceOnUse" cx="70" cy="170" r="110"><stop offset="0" stop-color="#4FD08A"/><stop offset="1" stop-color="#4FD08A" stop-opacity="0"/></radialGradient>
        <radialGradient id="bk-i" gradientUnits="userSpaceOnUse" cx="40" cy="40" r="130"><stop offset="0" stop-color="#5C6BC0"/><stop offset="1" stop-color="#5C6BC0" stop-opacity="0"/></radialGradient></defs>
        <path class="mark-base" fill-rule="evenodd" d="${LOGO_PATH}"/><path fill="url(#bk-p)" fill-rule="evenodd" d="${LOGO_PATH}"/><path fill="url(#bk-g)" fill-rule="evenodd" d="${LOGO_PATH}"/><path fill="url(#bk-i)" fill-rule="evenodd" d="${LOGO_PATH}"/></svg><span class="brand-text"><b>Clive</b> Manager</span></a>
      <nav class="topnav" aria-label="Pages">${top}</nav>
      ${capsuleHtml(user.name)}
      <button class="theme-btn" type="button" id="theme"></button>
      <button class="burger" type="button" id="burger" aria-label="Menu" aria-expanded="false" aria-controls="drawer"><svg viewBox="0 0 24 24" width="24" height="24" aria-hidden="true"><path d="M4 7h16M4 12h16M4 17h16" stroke="currentColor" stroke-width="2" stroke-linecap="round" fill="none"/></svg></button>
    </div><nav class="drawer" id="drawer" aria-label="Pages" hidden>${drawer}</nav></header>
    <main><h1 id="title" class="page-title"></h1><div id="page" tabindex="-1"></div></main>
    <footer class="foot"><span>Clive Manager</span><span>Church Drive</span></footer></div>`;
  const who = document.getElementById('who'), menu = document.getElementById('menu'), burger = document.getElementById('burger'), dr = document.getElementById('drawer');
  status.mount(new CapsuleView(root.querySelector('.cap'), pressManager));
  mountThemeButton(document.getElementById('theme'));
  const drops = [...root.querySelectorAll('.drop')];
  const closeDrops = (except) => drops.forEach((d) => { if (d !== except) { d.querySelector('.drop-list').hidden = true; d.querySelector('.drop-btn').setAttribute('aria-expanded', 'false'); } });
  const toggle = (open) => { menu.hidden = !open; who.setAttribute('aria-expanded', String(open)); };
  const toggleDrawer = (open) => { dr.hidden = !open; burger.setAttribute('aria-expanded', String(open)); document.body.classList.toggle('drawer-open', open); };
  drops.forEach((d) => d.querySelector('.drop-btn').addEventListener('click', (e) => {
    e.stopPropagation(); const list = d.querySelector('.drop-list'), open = list.hidden; closeDrops(d); toggle(false);
    list.hidden = !open; e.currentTarget.setAttribute('aria-expanded', String(open));
  }));
  who.addEventListener('click', (e) => { e.stopPropagation(); closeDrops(); toggle(menu.hidden); });
  burger.addEventListener('click', (e) => { e.stopPropagation(); toggle(false); toggleDrawer(dr.hidden); });
  root.querySelectorAll('a[data-page]').forEach((l) => l.addEventListener('click', () => { closeDrops(); toggleDrawer(false); }));
  document.addEventListener('click', (e) => { closeDrops(); if (!menu.hidden && !menu.contains(e.target)) toggle(false); });
  document.addEventListener('keydown', (e) => { if (e.key === 'Escape') { closeDrops(); toggle(false); toggleDrawer(false); } });
  document.getElementById('signout').addEventListener('click', () => { ctx?.ha.close(); ctx = null; status.hide(); signOut(); showSignIn(); });
  fillIcons(root);
  return show();
}

/** Draws the current page; gives back the page's own promise when it has one (so the status banner can say Updating until it is in). */
function show() {
  const page = currentPage();
  loadedAt = Date.now();
  document.getElementById('title').textContent = page.title;
  document.title = page.id === 'home' ? 'Clive Manager' : `${page.title} · Clive Manager`;
  root.querySelectorAll('a[data-page]').forEach((b) => (b.dataset.page === page.id ? b.setAttribute('aria-current', 'page') : b.removeAttribute('aria-current')));
  root.querySelectorAll('.drop').forEach((d) => d.classList.toggle('on', !!page.group && d.querySelector('.drop-btn').dataset.group === page.group));
  document.body.classList.remove('drawer-open');
  document.body.classList.toggle('on-home', page.id === 'home');
  const arg = location.hash.slice(1).split('/').slice(1).join('/');
  cleanup(ctx);
  return page.render(document.getElementById('page'), ctx, arg);
}
window.addEventListener('hashchange', () => { if (ctx) show(); });

// ---- Start ----

/** Opens the line to Home Assistant and learns who is on it. Throws with `fatal` when the sign-in itself is no good. */
async function connect() {
  const ha = new HaSocket();
  await ha.connect();
  let user;
  const t0 = performance.now();
  try { user = await ha.call('auth/current_user'); } catch { ha.close(); throw new Error('Home Assistant did not say who you are'); }
  // What the status banner can say about the house: how fast it answered, its version, and whether it is up yet (RUNNING) or still starting.
  const facts = { latency: Math.max(1, Math.round(performance.now() - t0)) };
  try { const cfg = await ha.call('get_config'); facts.version = cfg.version; facts.state = cfg.state; } catch { /* the banner just says less */ }
  return { ha, user, facts };
}

function showRetry(message) {
  root.innerHTML = `<div class="center"><div class="panel">${mark()}<h1>Clive Manager</h1><p class="note">${esc(message)} You are still signed in.</p>
    <button class="btn" type="button" id="retry">Try again</button><button class="btn quiet" type="button" id="out">Sign out</button></div></div>`;
  document.getElementById('retry').addEventListener('click', start);
  document.getElementById('out').addEventListener('click', () => { signOut(); showSignIn(); });
}

// ---- The status capsule (status.js, capsule.js): Connecting, Updating, No connection, Home Assistant restarting, and press the Manager icon to refresh ----

let wakeUp = null;
/** Waits [ms], or less when the person presses the Manager icon while it is offline. */
const sleep = (ms) => new Promise((resolve) => { const t = setTimeout(resolve, ms); wakeUp = () => { clearTimeout(t); resolve(); }; });
const retryNow = () => { if (wakeUp) wakeUp(); };
let online = false;

const status = createStatus({
  host: () => { try { return new URL(houseAddress()).hostname; } catch { return 'the house'; } },
  page: () => currentPage().title,
});
/** The Manager icon: refresh this page, or try the line again now when it is down. */
const pressManager = () => { if (!online) retryNow(); else refreshNow(); };

/** Draws the page again and says so on the banner. */
async function reloadPage() {
  status.loading();
  try { await show(); } catch { /* the page shows its own error */ }
  status.loaded();
}

let loadedAt = Date.now(), hiddenAt = 0, lastTouched = Date.now(), busy = false;

/** Reload the current page (with the Updating capsule), or hurry the reconnect when the line is down. */
async function refreshNow() {
  if (!ctx) return;
  if (!online) { retryNow(); return; }
  if (busy) return;
  busy = true;
  status.refreshing();
  try { await show(); } catch { /* the page shows its own error */ }
  status.refreshed();
  busy = false;
}

/** Reads a page that only shows things again, with the Updating pill, keeping the scroll place; never under someone typing or with a dialog open. */
async function autoRefresh() {
  if (!ctx || !online || busy || document.hidden || !currentPage().live || editing()) return;
  const y = scrollY;
  await refreshNow();
  scrollTo(0, y);
}
for (const ev of ['pointerdown', 'keydown', 'scroll', 'touchstart']) addEventListener(ev, () => { lastTouched = Date.now(); }, { passive: true });
document.addEventListener('visibilitychange', () => {
  if (document.hidden) { hiddenAt = Date.now(); return; }
  const away = hiddenAt; hiddenAt = 0;
  if (dueOnReturn(Date.now(), away)) autoRefresh();
});
setInterval(() => { const now = Date.now(); if (duePeriodic(now, loadedAt) && idle(now, lastTouched)) autoRefresh(); }, TICK_MS);

/** Home Assistant is up but still starting its integrations: say so until it is running, then load the page. */
async function waitForRunning() {
  for (let i = 0; i < 100; i++) {
    await new Promise((r) => setTimeout(r, 3000));
    if (!ctx) return;
    try { const cfg = await ctx.ha.call('get_config'); if (cfg.state === 'RUNNING') { status.connected({ version: cfg.version, state: cfg.state }); return; } } catch { return; }
  }
}

/** The line is back: say what Home Assistant told us, wait for it to finish starting if it is, then draw the page again. */
async function settle(facts) {
  online = true;
  status.connected(facts);
  if (facts.state && facts.state !== 'RUNNING') { status.restarting(true); await waitForRunning(); }
  await reloadPage();
}

/** The line dropped (the screen slept, the network changed, Home Assistant restarted): keep the page, say so, and reconnect quietly, trying again until it works. */
function keepConnected() {
  ctx.ha.onClose = async (code) => {
    online = false;
    status.lost(code);
    for (let attempt = 1, wait = 1000; ; attempt++, wait = Math.min(wait * 2, 15000)) {
      status.waiting(attempt, wait);
      await sleep(wait);
      if (!signedIn()) { status.hide(); return; }
      status.connecting(attempt);
      try {
        const next = await connect(); ctx = { ...ctx, ha: next.ha }; keepConnected();
        await settle(next.facts); return;
      } catch (e) { if (e.fatal) { ctx = null; status.hide(); return showSignIn(e.message); } }
    }
  };
}

async function start() {
  const params = new URLSearchParams(location.search);
  if (params.has('code') || params.has('state')) {
    const problem = await finishSignIn(params);
    history.replaceState(null, '', location.pathname + location.hash);
    if (problem) return showSignIn(problem);
  }
  if (!signedIn()) return showSignIn();
  loadIcons(houseAddress());
  root.innerHTML = '<div class="center"><p class="note">Connecting to the house…</p></div>';
  status.connecting(1);
  let link;
  try { link = await connect(); } catch (e) { status.hide(); return e.fatal ? showSignIn(e.message) : showRetry(`${e.message}.`); }
  const { ha, user } = link;
  if (!user.is_admin) {
    ha.close(); status.hide(); signOut();
    return showSignIn(`${user.name || 'That account'} is not an administrator in Home Assistant, so the Manager has nothing to show. Sign in with an administrator's login.`);
  }
  ctx = { ha, user, house: houseAddress() };
  try { await loadStyle(ctx); setTones(ctx.style.colours); } catch { /* the pages fall back to the neutral palette */ }
  keepConnected();
  online = true;
  status.connected(link.facts);
  status.loading();
  Promise.resolve(showShell(user)).catch(() => {}).then(() => status.loaded());
}
start();
