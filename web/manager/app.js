// Clive Manager: sign in with the Home Assistant login (administrators only), then the pages. Home Assistant is where the settings live; this
// site reads them over its WebSocket and (next) sends changes back for Clive to check and apply.
import { DEFAULT_HA, HaSocket, cleanAddress, finishSignIn, houseAddress, signOut, signedIn, startSignIn } from './ha.js';
import { glowStyle, inkOn } from './glow.js';
import { fillIcons, icon, loadIcons } from './icons.js';
import { renderChanges } from './pages/changes.js';
import { readVersions, summary as versionSummary } from './pages/versions.js';
import { openPaletteEditor } from './pages/palette-editor.js';
import { renderCards, renderIcons, renderLayouts } from './pages/design.js';
import { renderDevices } from './pages/devices.js';
import { renderManager, renderManagerSection } from './pages/manager.js';
import { LOGO_PATH, LOGO_VIEWBOX } from './logo.js';
import { PALETTE_PARTS, paletteWarnings, slugOk, ACTIONS, BAD, DAYS, OK, TIMES, WARN, activityNote, activityProblem, activityQuery, activityWho, actionLabel, ago, buildRegistry, clock, dayLabel, deviceHealthList, energySnapshot, healthSummary, parseActivity } from './pages/logic.js';

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

const PAGES = [
  { id: 'home', title: 'Clive Manager', label: 'Home', icon: 'cd:home', render: renderHome },
  { id: 'colours', title: 'Colour palettes', label: 'Colours', icon: 'cd:palette', render: renderColours },
  { id: 'design', title: 'Design', label: 'Design', icon: 'cd:view-dashboard', render: renderDesign },
  { id: 'changes', title: 'Changes', label: 'Changes', icon: 'cd:checklist', render: (el, c) => renderChanges(el, c, { glowStyle }) },
  { id: 'manager', title: 'Manager', label: 'Manager', icon: 'cd:sliders', render: (el, c, arg) => (arg ? renderManagerSection(el, c, { glowStyle, go }, decodeURIComponent(arg)) : renderManager(el, c, { glowStyle, go })) },
  { id: 'health', title: 'Device health', label: 'Health', icon: 'cd:device-health', render: renderHealth },
  { id: 'devices', title: 'Devices', label: 'Devices', icon: 'cd:television-play', render: renderDevices },
  { id: 'energy', title: 'Energy', label: 'Energy', icon: 'cd:bolt', render: renderEnergy },
  { id: 'activity', title: 'Activity log', label: 'Activity', icon: 'cd:history', render: renderActivity },
];

function renderHome(el, ctx) {
  el.innerHTML = `<div class="stack">
    <section class="stack" style="gap:12px"><div class="head"><h2>Colour palettes</h2></div>
      <button class="card" type="button" data-go="colours" style="${glowStyle('#2C2A6B')}"><p class="title">Every palette as the house has it now</p>
      <p class="sub">The five colours of each palette, the ones changed from the shipped set marked. Changes are sent for Clive to check, then applied everywhere.</p></button></section>
    <section class="stack" style="gap:12px"><div class="head"><h2>The house</h2></div><div class="grid">
      <button class="card" type="button" data-go="health" style="${glowStyle('#1F5C3A')}"><p class="title">Device health</p><p class="sub">Batteries, filters and devices that stop responding</p></button>
      <button class="card" type="button" data-go="devices" style="${glowStyle('#3F2A6B')}"><p class="title">Devices</p><p class="sub">TVs, speakers, and how the house's systems are</p></button>
      <button class="card" type="button" data-go="energy" style="${glowStyle('#6A4B12')}"><p class="title">Energy</p><p class="sub">Octopus rates, cheap times, today and yesterday</p></button>
      <button class="card" type="button" data-go="activity" style="${glowStyle('#1E4A6B')}"><p class="title">Activity log</p><p class="sub">What people and the assistant did, and when</p></button></div></section>
    <section class="stack" style="gap:12px" id="versions"><div class="head"><h2>Versions</h2><button class="btn quiet" type="button" id="versions-refresh">Check again</button></div>
      <article class="card plain" id="versions-card"><p class="sub">Reading the versions…</p></article></section>
    <p class="note">More pages arrive here as they are built: cards, icons and layouts.</p></div>`;
  const drawVersions = async () => {
    const card = el.querySelector('#versions-card'); card.innerHTML = '<p class="sub">Reading the versions…</p>';
    let rows; try { rows = await readVersions(ctx); } catch (e) { card.innerHTML = `<p class="err">Could not read the versions: ${esc(e.message)}</p>`; return; }
    const sum = versionSummary(rows);
    const word = { same: 'Same', behind: 'Behind', ahead: 'Ahead', unknown: 'Unknown' };
    card.innerHTML = `<p class="${sum.tone === 'ok' ? 'sub' : 'warn-line'}">${esc(sum.text)}</p>${rows.map((r) => `<div class="row ver"><div style="flex:1"><p class="row-title">${esc(r.name)}</p><p class="sub">${esc(r.note)}</p></div><code>${esc(r.version || '–')}</code><span class="tag ${r.status}">${word[r.status]}</span></div>`).join('')}
      <p class="sub">What each phone has installed is kept by the phone and Google Play; the app rows show the newest published test build.</p>`;
  };
  el.querySelector('#versions-refresh').addEventListener('click', drawVersions); drawVersions();
  el.querySelectorAll('[data-go]').forEach((b) => b.addEventListener('click', () => go(b.dataset.go)));
}

async function renderColours(el, ctx) {
  el.innerHTML = '<p class="note">Reading the palettes from the house…</p>';
  let data, shipped = {};
  try { data = await ctx.ha.call('church_drive/colours'); } catch (e) { el.innerHTML = `<p class="err" role="alert">Could not read the palettes: ${esc(e.message)}</p>`; return; }
  // The shipped colours (a static file Home Assistant serves) tell which palettes have been changed; without them nothing is marked.
  for (const url of ['clive/cards.json', `${houseAddress()}/church_drive/clive/cards.json`]) { try { const r = await fetch(url); if (r.ok) { shipped = (await r.json()).entity_colours?.types || {}; break; } } catch { /* try the next place */ } }
  const palettes = Object.entries(data.palettes || {});
  const groups = new Map();
  palettes.forEach(([key, p]) => { const g = p.group || 'Other'; if (!groups.has(g)) groups.set(g, []); groups.get(g).push([key, p]); });
  const NAMES = ['Colour', 'Dark', 'Accent', 'Light', 'Deep'];
  const card = ([key, p]) => {
    const was = shipped[key]?.colours;
    const changed = !p.custom && was && was.join() .toLowerCase() !== p.colours.join().toLowerCase();
    return `<article class="card" style="${glowStyle(p.colours[1])}"><div class="row"><h3 class="title" style="margin:0">${esc(p.name)}</h3>
      ${changed ? '<span class="tag">Changed</span>' : ''}${p.custom ? '<span class="tag">Yours</span>' : ''}</div>
      <p class="sub"><code>${esc(key)}</code></p>
      <div class="swatches">${p.colours.map((c, i) => `<div class="sw"><i style="background:${esc(c)}"></i><span>${NAMES[i]}</span><code>${esc(c)}</code></div>`).join('')}</div>
      <div class="chips"><button class="btn quiet edit" type="button" data-edit="${esc(key)}">Edit</button></div><div class="edit-host"></div></article>`;
  };
  el.innerHTML = `<div class="stack">
    <p class="note">${palettes.length} ${palettes.length === 1 ? 'palette' : 'palettes'}, revision ${esc(data.revision)}. Edit a palette and send it: it waits on the Changes page for Clive to check it before it goes live (an urgent change is applied at once).</p>
    <div class="chips"><input class="field" id="find" type="search" placeholder="Find a palette" aria-label="Find a palette" style="flex:1"><button class="btn" type="button" id="add-palette">New palette</button></div><div id="new-host"></div>
    <div class="stack" id="groups">${[...groups].map(([g, list]) => `<section class="stack" style="gap:12px" data-group><div class="head"><h2>${esc(g)}</h2><span class="count">${list.length}</span></div><div class="grid">${list.map(card).join('')}</div></section>`).join('')}</div></div>`;
  const byKey = Object.fromEntries(palettes);
  el.querySelectorAll('[data-edit]').forEach((b) => b.addEventListener('click', () => openPaletteEditor(b.closest('article').querySelector('.edit-host'), ctx, { key: b.dataset.edit, palette: byKey[b.dataset.edit] })));
  document.getElementById('add-palette').addEventListener('click', () => openPaletteEditor(document.getElementById('new-host'), ctx, { key: null, palette: null }));
  document.getElementById('find').addEventListener('input', (e) => {
    const q = e.target.value.trim().toLowerCase();
    el.querySelectorAll('article.card').forEach((c) => { c.hidden = !!q && !c.textContent.toLowerCase().includes(q); });
    el.querySelectorAll('[data-group]').forEach((s) => { s.hidden = ![...s.querySelectorAll('article.card')].some((c) => !c.hidden); });
  });
}


// ---- Design: cards, icons and layouts ----

const DESIGN_TABS = [['cards', 'Cards', renderCards], ['icons', 'Icons', renderIcons], ['layouts', 'Layouts', renderLayouts]];
async function renderDesign(el, ctx, arg) {
  const tab = DESIGN_TABS.find((t) => t[0] === arg) || DESIGN_TABS[0];
  el.innerHTML = `<div class="stack"><nav class="tabs2" aria-label="Design">${DESIGN_TABS.map((t) => `<button type="button" data-tab="${t[0]}" ${t[0] === tab[0] ? 'aria-current="page"' : ''}>${t[1]}</button>`).join('')}</nav><div id="design-body"></div></div>`;
  el.querySelectorAll('[data-tab]').forEach((b) => b.addEventListener('click', () => go(`design/${b.dataset.tab}`)));
  await tab[2](el.querySelector('#design-body'), ctx);
}

// ---- Device health, Energy, Activity ----

const TONE = { [OK]: '#1F5C3A', [WARN]: '#6A4B12', [BAD]: '#6B1E24' };
async function houseRegistry(ha) {
  const [entityReg, deviceReg, areaReg] = await Promise.all([ha.call('config/entity_registry/list_for_display'), ha.call('config/device_registry/list'), ha.call('config/area_registry/list')]);
  return buildRegistry(entityReg, deviceReg, areaReg);
}

async function renderHealth(el, ctx) {
  el.innerHTML = '<p class="note">Reading the devices from the house…</p>';
  let items;
  try {
    const [list, reg] = await Promise.all([ctx.ha.call('get_states'), houseRegistry(ctx.ha)]);
    items = deviceHealthList(Object.fromEntries(list.map((e) => [e.entity_id, e])), reg);
  } catch (e) { el.innerHTML = `<p class="err" role="alert">Could not read the devices: ${esc(e.message)}</p>`; return; }
  const row = (d) => `<article class="card compact" style="${glowStyle(TONE[d.level])}"><div class="row"><h3 class="title" style="margin:0">${esc(d.name)}</h3>${d.area ? `<span class="tag">${esc(d.area)}</span>` : ''}</div>
    <p class="sub">${esc(d.summary)}</p>${d.heard ? `<p class="sub">Last heard ${esc(ago(d.heard))}</p>` : ''}</article>`;
  el.innerHTML = `<div class="stack"><p class="note">${esc(healthSummary(items))}.</p>${items.length ? `<div class="grid tight">${items.map(row).join('')}</div>` : ''}</div>`;
}

async function renderEnergy(el, ctx) {
  el.innerHTML = '<p class="note">Reading the energy figures from the house…</p>';
  let e;
  try { const list = await ctx.ha.call('get_states'); e = energySnapshot(Object.fromEntries(list.map((s) => [s.entity_id, s]))); }
  catch (err) { el.innerHTML = `<p class="err" role="alert">Could not read the energy figures: ${esc(err.message)}</p>`; return; }
  const p = (n) => (n == null ? '—' : `${n.toFixed(1)}p`), pound = (n) => (n == null ? '—' : `£${n.toFixed(2)}`);
  const card = (title, big, sub, tone) => `<article class="card" style="${glowStyle(tone)}"><p class="sub">${esc(title)}</p><p class="big">${esc(big)}</p><p class="sub">${esc(sub)}</p></article>`;
  const cheap = e.cheapUntil ? `Cheap until ${clock(e.cheapUntil)}` : e.offPeak ? 'Off-peak now' : e.cheapest != null ? `Cheapest today ${p(e.cheapest)}` : '';
  let chart = '';
  if (e.rates.length) {
    const max = Math.max(...e.rates.map((r) => r.pence), 1); const now = Date.now();
    chart = `<article class="card" style="${glowStyle('#2C2A6B')}"><p class="sub">Rates per kWh, half hour by half hour</p><svg class="bars" viewBox="0 0 ${e.rates.length * 8} 100" preserveAspectRatio="none" role="img" aria-label="Electricity rates">${
      e.rates.map((r, i) => `<rect x="${i * 8 + 1}" width="6" y="${100 - (r.pence / max) * 96}" height="${(r.pence / max) * 96}" rx="2" fill="${r.start <= now && now < r.end ? '#fff' : r.pence <= e.cheapest + 0.01 ? '#4FD08A' : '#8C9CFF'}"/>`).join('')}</svg>
      <p class="sub">Today and tomorrow, once Octopus has published it. White is now; green is the cheapest.</p></article>`;
  }
  el.innerHTML = `<div class="stack"><div class="grid">
    ${card('Electricity now', p(e.rate), [cheap, e.demand != null ? `${Math.round(e.demand)} W` : ''].filter(Boolean).join(' · '), '#6A4B12')}
    ${card('Electricity today', pound(e.today), e.todayKwh != null ? `${e.todayKwh.toFixed(1)} kWh` : '', '#6A4B12')}
    ${card('Electricity yesterday', pound(e.yesterday), e.yesterdayKwh != null ? `${e.yesterdayKwh.toFixed(1)} kWh` : '', '#6A4B12')}
    ${e.gasRate != null || e.gasToday != null ? card('Gas', pound(e.gasToday), `Yesterday ${pound(e.gasYesterday)}${e.gasRate != null ? ` · ${p(e.gasRate)}/kWh` : ''}`, '#1F5C3A') : ''}
    ${e.points != null ? card('Octoplus points', String(Math.round(e.points)), '', '#1E4A6B') : ''}</div>${chart}</div>`;
}

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
    out.insertAdjacentHTML('beforeend', [...groups].map(([day, lines]) => `<section class="stack" style="gap:8px"><div class="head"><h2>${esc(day)}</h2></div>${lines.map((l) => {
      const note = activityNote(l, r.assistant);
      return `<article class="card line compact" style="${glowStyle(l.kind === 'overruled' ? '#6B1E24' : l.kind.startsWith('clive') ? '#2C2A6B' : '#1E3A4B')}"><span class="when">${esc(clock(l.ts))}</span><div><p class="title" style="margin:0"><b>${esc(activityWho(l, r.assistant))}</b> ${esc(l.text)}</p>${note ? `<p class="sub">${esc(note)}</p>` : ''}</div></article>`;
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
  root.innerHTML = `<div class="shell"><header class="bar"><h1 id="title"></h1><div class="menu-wrap"><button class="person" type="button" id="who" aria-haspopup="true" aria-expanded="false" aria-label="Account: ${esc(user.name)}">${esc((user.name || '?').trim().charAt(0).toUpperCase())}</button>
      <div class="menu" id="menu" hidden role="menu"><p class="menu-name">${esc(user.name || 'Signed in')}</p><p class="sub">Administrator</p>
        <button class="btn quiet" type="button" id="signout" role="menuitem">${icon('cd:sign-out', 20)} Sign out</button></div></div></header>
    <div class="banner" id="banner" hidden role="status"></div>
    <nav class="nav" aria-label="Pages">${PAGES.map((p) => `<button type="button" data-page="${p.id}"><span class="pill">${icon(p.icon)}</span>${esc(p.label)}</button>`).join('')}</nav>
    <main id="page" tabindex="-1"></main></div>`;
  root.querySelectorAll('[data-page]').forEach((b) => b.addEventListener('click', () => go(b.dataset.page)));
  const who = document.getElementById('who'), menu = document.getElementById('menu');
  const toggle = (open) => { menu.hidden = !open; who.setAttribute('aria-expanded', String(open)); };
  who.addEventListener('click', (e) => { e.stopPropagation(); toggle(menu.hidden); });
  document.addEventListener('click', (e) => { if (!menu.hidden && !menu.contains(e.target)) toggle(false); });
  document.addEventListener('keydown', (e) => { if (e.key === 'Escape') toggle(false); });
  document.getElementById('signout').addEventListener('click', () => { ctx?.ha.close(); ctx = null; signOut(); showSignIn(); });
  fillIcons(root);
  show();
}

function show() {
  const page = currentPage();
  document.getElementById('title').textContent = page.title;
  document.title = page.id === 'home' ? 'Clive Manager' : `${page.title} · Clive Manager`;
  root.querySelectorAll('[data-page]').forEach((b) => (b.dataset.page === page.id ? b.setAttribute('aria-current', 'page') : b.removeAttribute('aria-current')));
  const arg = location.hash.slice(1).split('/').slice(1).join('/');
  if (arg && page.id === 'manager') document.getElementById('title').textContent = decodeURIComponent(arg);
  page.render(document.getElementById('page'), ctx, arg);
}
window.addEventListener('hashchange', () => { if (ctx) show(); });

// ---- Start ----

/** Opens the line to Home Assistant and learns who is on it. Throws with `fatal` when the sign-in itself is no good. */
async function connect() {
  const ha = new HaSocket();
  await ha.connect();
  let user;
  try { user = await ha.call('auth/current_user'); } catch { ha.close(); throw new Error('Home Assistant did not say who you are'); }
  return { ha, user };
}

function showRetry(message) {
  root.innerHTML = `<div class="center"><div class="panel">${mark()}<h1>Clive Manager</h1><p class="note">${esc(message)} You are still signed in.</p>
    <button class="btn" type="button" id="retry">Try again</button><button class="btn quiet" type="button" id="out">Sign out</button></div></div>`;
  document.getElementById('retry').addEventListener('click', start);
  document.getElementById('out').addEventListener('click', () => { signOut(); showSignIn(); });
}

/** The line dropped (the screen slept, the network changed): keep the page, say so, and reconnect quietly, trying again until it works. */
function keepConnected() {
  ctx.ha.onClose = async () => {
    const banner = document.getElementById('banner'); if (banner) { banner.textContent = 'Reconnecting to the house…'; banner.hidden = false; }
    for (let wait = 1000; ; wait = Math.min(wait * 2, 15000)) {
      await new Promise((r) => setTimeout(r, wait));
      if (!signedIn()) return;
      try {
        const next = await connect(); ctx = { ...ctx, ha: next.ha }; keepConnected();
        if (banner) banner.hidden = true; show(); return;
      } catch (e) { if (e.fatal) { ctx = null; return showSignIn(e.message); } }
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
  let link;
  try { link = await connect(); } catch (e) { return e.fatal ? showSignIn(e.message) : showRetry(`${e.message}.`); }
  const { ha, user } = link;
  if (!user.is_admin) {
    ha.close(); signOut();
    return showSignIn(`${user.name || 'That account'} is not an administrator in Home Assistant, so the Manager has nothing to show. Sign in with an administrator's login.`);
  }
  ctx = { ha, user, house: houseAddress() };
  keepConnected();
  showShell(user);
}
start();
