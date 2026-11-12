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
import { openPaletteEditor } from './pages/palette-editor.js';
import { renderCards, renderIcons, renderLayouts } from './pages/design.js';
import { renderDevices } from './pages/devices.js';
import { renderHealth } from './pages/health.js';
import { renderManager, renderManagerSection } from './pages/manager.js';
import { LOGO_PATH, LOGO_VIEWBOX } from './logo.js';
import { cleanup } from './kit.js';
import { h } from './pages/dom.js';
import { renderEnergyPage } from './pages/energy.js';
import { renderActivity } from './pages/activity.js';
import { renderColours } from './pages/colours.js';
import { renderHome as renderHomePage } from './pages/home.js';
import { dockHtml, dockAlerts, bindDock } from './dock.js';
import { renderIconLibrary } from './pages/icon-library.js';
import { renderReference } from './pages/reference.js';
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
  { id: 'home', title: 'Clive Manager', label: 'Home', icon: 'cd:home', render: (el, c) => renderHomePage(el, c, PAGES), live: true, ms: true },
  { id: 'energy', title: 'Energy', label: 'Energy', icon: 'cd:bolt', group: 'The house', render: (el, c) => renderEnergyPage(el, c), ms: true },
  { id: 'devices', title: 'Devices', label: 'Devices', icon: 'cd:television-play', group: 'The house', render: renderDevices, ms: true },
  { id: 'health', title: 'Device health', label: 'Device health', icon: 'cd:device-health', group: 'The house', render: renderHealth, live: true, ms: true },
  { id: 'activity', title: 'Activity log', label: 'Activity', icon: 'cd:history', group: 'The house', render: renderActivity, ms: true },
  { id: 'colours', title: 'Colour palettes', label: 'Colours', icon: 'cd:palette', group: 'Design', render: renderColours, ms: true },
  { id: 'design', title: 'Customise the dashboards', label: 'Customise', icon: 'cd:view-dashboard', group: 'Design', render: renderDesign },
  { id: 'iconlib', title: 'Icon library', label: 'Icon library', icon: 'cd:bulb-group', group: 'Design', render: (el, c) => renderIconLibrary(el, c) },
  { id: 'reference', title: 'Reference', label: 'Reference', icon: 'cd:image-text', group: 'Design', render: (el, c, arg) => renderReference(el, c, arg) },
  { id: 'manager', title: 'Settings', label: 'Settings', icon: 'cd:sliders', group: 'Manage', render: (el, c, arg) => renderManager(el, c, { glowStyle, go }, arg ? decodeURIComponent(arg) : '') },
  { id: 'changes', title: 'Edits and history', label: 'Edits', icon: 'cd:checklist', group: 'Manage', render: (el, c) => renderChanges(el, c, { glowStyle }) },
];
const GROUPS = ['The house', 'Design', 'Manage'];

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

// ---- The shell ----

let ctx = null;
function go(id) { location.hash = id === 'home' ? '' : id; }

function currentPage() { return PAGES.find((p) => p.id === location.hash.slice(1).split('/')[0]) || PAGES[0]; }

function showShell(user) {
  root.innerHTML = `<div class="shell"><header class="site"><div class="site-in">
      <a class="brand" href="#" aria-label="Clive Manager, home"><svg class="mark" viewBox="${LOGO_VIEWBOX}" aria-hidden="true"><defs>
        <radialGradient id="bk-p" gradientUnits="userSpaceOnUse" cx="150" cy="70" r="88"><stop offset="0" stop-color="#AB47BC"/><stop offset="1" stop-color="#AB47BC" stop-opacity="0"/></radialGradient>
        <radialGradient id="bk-g" gradientUnits="userSpaceOnUse" cx="70" cy="170" r="110"><stop offset="0" stop-color="#4FD08A"/><stop offset="1" stop-color="#4FD08A" stop-opacity="0"/></radialGradient>
        <radialGradient id="bk-i" gradientUnits="userSpaceOnUse" cx="40" cy="40" r="130"><stop offset="0" stop-color="#5C6BC0"/><stop offset="1" stop-color="#5C6BC0" stop-opacity="0"/></radialGradient></defs>
        <path class="mark-base" fill-rule="evenodd" d="${LOGO_PATH}"/><path class="mark-glow" fill="url(#bk-p)" fill-rule="evenodd" d="${LOGO_PATH}"/><path class="mark-glow" fill="url(#bk-g)" fill-rule="evenodd" d="${LOGO_PATH}"/><path class="mark-glow" fill="url(#bk-i)" fill-rule="evenodd" d="${LOGO_PATH}"/></svg><span class="brand-text"><b>Clive</b> Manager</span></a>
      ${capsuleHtml(user.name)}
      <button class="theme-btn" type="button" id="theme"></button>
    </div></header>
    <main><h1 id="title" class="page-title"></h1><div id="page" tabindex="-1"></div></main>
    <footer class="foot"><span>Clive Manager</span><span>Church Drive</span></footer><div id="dock-host"></div></div>`;
  const who = document.getElementById('who'), menu = document.getElementById('menu');
  status.mount(new CapsuleView(root.querySelector('.cap'), pressManager));
  mountThemeButton(document.getElementById('theme'));
  document.getElementById('theme').addEventListener('click', () => setTimeout(() => drawDock(), 0));
  const toggle = (open) => { menu.hidden = !open; who.setAttribute('aria-expanded', String(open)); };
  who.addEventListener('click', (e) => { e.stopPropagation(); toggle(menu.hidden); });
  document.addEventListener('click', (e) => { if (!menu.hidden && !menu.contains(e.target)) toggle(false); });
  document.addEventListener('keydown', (e) => { if (e.key === 'Escape') toggle(false); });
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
  document.getElementById('page').classList.toggle('ms', !!page.ms);
  document.getElementById('title').hidden = page.id === 'home';
  drawDock(page);
  document.body.classList.toggle('on-home', page.id === 'home');
  const arg = location.hash.slice(1).split('/').slice(1).join('/');
  cleanup(ctx);
  ctx.setSub = (html) => { const el = document.getElementById('subdock'); if (el) el.innerHTML = html || ''; };
  return page.render(document.getElementById('page'), ctx, arg);
}
let dockDots = {};
function drawDock(page = currentPage()) {
  const host = document.getElementById('dock-host'); if (!host || !ctx) return;
  const sub = document.getElementById('subdock')?.innerHTML || '';
  host.innerHTML = dockHtml(ctx, PAGES, page.id, dockDots); const el = document.getElementById('subdock'); if (el) el.innerHTML = sub;
  fillIcons(host); bindDock(host, () => drawDock());
}
const refreshDots = () => dockAlerts(ctx).then((d) => { dockDots = d; drawDock(); });
window.addEventListener('hashchange', () => { if (!ctx) return; const sub = document.getElementById('subdock'); if (sub) sub.innerHTML = ''; show(); window.scrollTo(0, 0); refreshDots(); });

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
  Promise.resolve(showShell(user)).catch(() => {}).then(() => { status.loaded(); refreshDots(); });
}
start();
