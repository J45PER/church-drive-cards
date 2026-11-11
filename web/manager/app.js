// Clive Manager: sign in with the Home Assistant login (administrators only), then the pages. Home Assistant is where the settings live; this
// site reads them over its WebSocket and (next) sends changes back for Clive to check and apply.
import { DEFAULT_HA, HaSocket, cleanAddress, finishSignIn, houseAddress, signOut, signedIn, startSignIn } from './ha.js';
import { glowStyle, inkOn } from './glow.js';
import { LOGO_PATH, LOGO_VIEWBOX } from './logo.js';

const root = document.getElementById('root');
const esc = (t) => String(t ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const ICONS = {
  home: 'M12 3 2 12h3v8h5v-6h4v6h5v-8h3z',
  palette: 'M12 3a9 9 0 0 0 0 18c1.1 0 2-.9 2-2 0-.5-.2-1-.5-1.3-.3-.4-.5-.8-.5-1.3 0-1.1.9-2 2-2h2.4A4.6 4.6 0 0 0 22 11.8C22 7 17.5 3 12 3zM6.5 12a1.5 1.5 0 1 1 0-3 1.5 1.5 0 0 1 0 3zm3-4a1.5 1.5 0 1 1 0-3 1.5 1.5 0 0 1 0 3zm5 0a1.5 1.5 0 1 1 0-3 1.5 1.5 0 0 1 0 3zm3 4a1.5 1.5 0 1 1 0-3 1.5 1.5 0 0 1 0 3z',
};
const icon = (name) => `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="${ICONS[name]}"/></svg>`;

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
  { id: 'home', title: 'Clive Manager', label: 'Home', icon: 'home', render: renderHome },
  { id: 'colours', title: 'Colour palettes', label: 'Colours', icon: 'palette', render: renderColours },
];

function renderHome(el, ctx) {
  el.innerHTML = `<div class="stack">
    <section class="stack" style="gap:12px"><div class="head"><h2>Colour palettes</h2></div>
      <button class="card" type="button" data-go="colours" style="${glowStyle('#2C2A6B')}"><p class="title">Every palette as the house has it now</p>
      <p class="sub">The five colours of each palette, the ones changed from the shipped set marked. Changes are sent for Clive to check, then applied everywhere.</p></button></section>
    <p class="note">More pages arrive here as they are built: cards, icons, layouts, device health, energy and the activity log. The app, Clive Admin, has device health, energy and the activity log already.</p></div>`;
  el.querySelectorAll('[data-go]').forEach((b) => b.addEventListener('click', () => go(b.dataset.go)));
}

async function renderColours(el, ctx) {
  el.innerHTML = '<p class="note">Reading the palettes from the house…</p>';
  let data, shipped = {};
  try { data = await ctx.ha.call('church_drive/colours'); } catch (e) { el.innerHTML = `<p class="err" role="alert">Could not read the palettes: ${esc(e.message)}</p>`; return; }
  // The shipped colours (a static file Home Assistant serves) tell which palettes have been changed; without them nothing is marked.
  try { const r = await fetch(`${houseAddress()}/church_drive/clive/cards.json`); shipped = (await r.json()).entity_colours?.types || {}; } catch { shipped = {}; }
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
      <div class="swatches">${p.colours.map((c, i) => `<div class="sw"><i style="background:${esc(c)}"></i><span>${NAMES[i]}</span><code>${esc(c)}</code></div>`).join('')}</div></article>`;
  };
  el.innerHTML = `<div class="stack">
    <p class="note">${palettes.length} palettes, revision ${esc(data.revision)}. View only for now; changing a palette comes next, sent for Clive to check before it goes live.</p>
    <input class="field" id="find" type="search" placeholder="Find a palette" aria-label="Find a palette">
    <div class="stack" id="groups">${[...groups].map(([g, list]) => `<section class="stack" style="gap:12px" data-group><div class="head"><h2>${esc(g)}</h2><span class="count">${list.length}</span></div><div class="grid">${list.map(card).join('')}</div></section>`).join('')}</div></div>`;
  document.getElementById('find').addEventListener('input', (e) => {
    const q = e.target.value.trim().toLowerCase();
    el.querySelectorAll('article.card').forEach((c) => { c.hidden = !!q && !c.textContent.toLowerCase().includes(q); });
    el.querySelectorAll('[data-group]').forEach((s) => { s.hidden = ![...s.querySelectorAll('article.card')].some((c) => !c.hidden); });
  });
}

// ---- The shell ----

let ctx = null;
function go(id) { location.hash = id === 'home' ? '' : id; }

function currentPage() { return PAGES.find((p) => p.id === location.hash.slice(1)) || PAGES[0]; }

function showShell(user) {
  root.innerHTML = `<div class="shell"><header class="bar"><h1 id="title"></h1><button class="person" type="button" id="who" title="Signed in as ${esc(user.name)}. Click to sign out." aria-label="Signed in as ${esc(user.name)}. Sign out.">${esc((user.name || '?').trim().charAt(0).toUpperCase())}</button></header>
    <nav class="nav" aria-label="Pages">${PAGES.map((p) => `<button type="button" data-page="${p.id}"><span class="pill">${icon(p.icon)}</span>${esc(p.label)}</button>`).join('')}</nav>
    <main id="page" tabindex="-1"></main></div>`;
  root.querySelectorAll('[data-page]').forEach((b) => b.addEventListener('click', () => go(b.dataset.page)));
  document.getElementById('who').addEventListener('click', () => { ctx?.ha.close(); signOut(); showSignIn(); });
  show();
}

function show() {
  const page = currentPage();
  document.getElementById('title').textContent = page.title;
  document.title = page.id === 'home' ? 'Clive Manager' : `${page.title} · Clive Manager`;
  root.querySelectorAll('[data-page]').forEach((b) => (b.dataset.page === page.id ? b.setAttribute('aria-current', 'page') : b.removeAttribute('aria-current')));
  page.render(document.getElementById('page'), ctx);
}
window.addEventListener('hashchange', () => { if (ctx) show(); });

// ---- Start ----

async function start() {
  const params = new URLSearchParams(location.search);
  if (params.has('code') || params.has('state')) {
    const problem = await finishSignIn(params);
    history.replaceState(null, '', location.pathname + location.hash);
    if (problem) return showSignIn(problem);
  }
  if (!signedIn()) return showSignIn();
  root.innerHTML = '<div class="center"><p class="note">Connecting to the house…</p></div>';
  const ha = new HaSocket();
  try { await ha.connect(); } catch (e) { signOut(); return showSignIn(e.message); }
  let user;
  try { user = await ha.call('auth/current_user'); } catch { signOut(); return showSignIn('Home Assistant did not say who you are. Sign in again.'); }
  if (!user.is_admin) {
    ha.close(); signOut();
    return showSignIn(`${user.name || 'That account'} is not an administrator in Home Assistant, so the Manager has nothing to show. Sign in with an administrator's login.`);
  }
  ctx = { ha, user };
  ha.onClose = () => { ctx = null; showSignIn('The line to Home Assistant dropped. Sign in again to reconnect.'); };
  showShell(user);
}
start();
