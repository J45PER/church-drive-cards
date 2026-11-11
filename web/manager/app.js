// Clive Manager: sign in with the Home Assistant login (administrators only), then the pages. Home Assistant is where the settings live; this
// site reads them over its WebSocket and (next) sends changes back for Clive to check and apply.
import { DEFAULT_HA, HaSocket, cleanAddress, finishSignIn, houseAddress, signOut, signedIn, startSignIn } from './ha.js';
import { glowStyle, inkOn } from './glow.js';
import { LOGO_PATH, LOGO_VIEWBOX } from './logo.js';
import { ACTIONS, BAD, DAYS, OK, TIMES, WARN, activityNote, activityProblem, activityQuery, activityWho, actionLabel, ago, buildRegistry, clock, dayLabel, deviceHealthList, energySnapshot, healthSummary, parseActivity } from './pages/logic.js';

const root = document.getElementById('root');
const esc = (t) => String(t ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const ICONS = {
  home: 'M12 3 2 12h3v8h5v-6h4v6h5v-8h3z',
  health: 'M12 21.35 10.55 20C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54z',
  energy: 'M11 21h-1l1-7H7.5c-.58 0-.57-.32-.38-.66.19-.34.05-.08.07-.12C8.48 10.94 10.42 7.54 13 3h1l-1 7h3.5c.49 0 .56.33.47.51l-.07.15C12.96 17.55 11 21 11 21z',
  activity: 'M13 3a9 9 0 0 0-9 9H1l3.89 3.89.07.14L9 12H6a7 7 0 1 1 2.05 4.95l-1.42 1.42A9 9 0 1 0 13 3zm-1 5v5l4.28 2.54.72-1.21-3.5-2.08V8z',
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
  { id: 'health', title: 'Device health', label: 'Health', icon: 'health', render: renderHealth },
  { id: 'energy', title: 'Energy', label: 'Energy', icon: 'energy', render: renderEnergy },
  { id: 'activity', title: 'Activity log', label: 'Activity', icon: 'activity', render: renderActivity },
];

function renderHome(el, ctx) {
  el.innerHTML = `<div class="stack">
    <section class="stack" style="gap:12px"><div class="head"><h2>Colour palettes</h2></div>
      <button class="card" type="button" data-go="colours" style="${glowStyle('#2C2A6B')}"><p class="title">Every palette as the house has it now</p>
      <p class="sub">The five colours of each palette, the ones changed from the shipped set marked. Changes are sent for Clive to check, then applied everywhere.</p></button></section>
    <section class="stack" style="gap:12px"><div class="head"><h2>The house</h2></div><div class="grid">
      <button class="card" type="button" data-go="health" style="${glowStyle('#1F5C3A')}"><p class="title">Device health</p><p class="sub">Batteries, filters and devices that stop responding</p></button>
      <button class="card" type="button" data-go="energy" style="${glowStyle('#6A4B12')}"><p class="title">Energy</p><p class="sub">Octopus rates, cheap times, today and yesterday</p></button>
      <button class="card" type="button" data-go="activity" style="${glowStyle('#1E4A6B')}"><p class="title">Activity log</p><p class="sub">What people and the assistant did, and when</p></button></div></section>
    <p class="note">More pages arrive here as they are built: cards, icons and layouts.</p></div>`;
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
  const row = (d) => `<article class="card" style="${glowStyle(TONE[d.level])}"><div class="row"><h3 class="title" style="margin:0">${esc(d.name)}</h3>${d.area ? `<span class="tag">${esc(d.area)}</span>` : ''}</div>
    <p class="sub">${esc(d.summary)}</p>${d.heard ? `<p class="sub">Last heard ${esc(ago(d.heard))}</p>` : ''}</article>`;
  el.innerHTML = `<div class="stack"><p class="note">${esc(healthSummary(items))}.</p>${items.length ? `<div class="grid">${items.map(row).join('')}</div>` : ''}</div>`;
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
      return `<article class="card line" style="${glowStyle(l.kind === 'overruled' ? '#6B1E24' : l.kind.startsWith('clive') ? '#2C2A6B' : '#1E3A4B')}"><span class="when">${esc(clock(l.ts))}</span><div><p class="title" style="margin:0;font-size:1.05rem"><b>${esc(activityWho(l, r.assistant))}</b> ${esc(l.text)}</p>${note ? `<p class="sub">${esc(note)}</p>` : ''}</div></article>`;
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
