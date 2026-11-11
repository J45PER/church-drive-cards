// The Clive Manager's line to Home Assistant: the same sign-in as the apps (Home Assistant's own login page, then a token), then its WebSocket.
// Nothing here writes to Firebase: the site only talks to Home Assistant, which checks every call (administrators only for changes).

/** The house's address for everyone (the same one the apps use); the sign-in screen lets an administrator change it. */
export const DEFAULT_HA = 'https://z4wyrbgulhsd63dfyimjem41urgk1lek.ui.nabu.casa';
const KEY = 'clive-manager-session';
const STATE_KEY = 'clive-manager-signin';

/** This site's own address with a trailing slash: Home Assistant's client id and where it sends the browser back. */
export function siteId(loc = globalThis.location) { return `${loc.origin}/`; }

export function cleanAddress(text) {
  let a = String(text || '').trim().replace(/\/+$/, '');
  if (!a) return '';
  if (!/^[a-z][a-z0-9+.-]*:\/\//i.test(a)) a = `https://${a}`;
  return a;
}

/** The URL of Home Assistant's own login page, for a given site address, house address and state. */
export function authorizeUrl(ha, site, state) {
  const q = new URLSearchParams({ client_id: site, redirect_uri: site, state });
  return `${ha}/auth/authorize?${q}`;
}

/** The form body for swapping a sign-in code for tokens, or a refresh token for a new access token. */
export function tokenForm(site, grant) {
  const f = new URLSearchParams({ client_id: site });
  if (grant.code) { f.set('grant_type', 'authorization_code'); f.set('code', grant.code); } else { f.set('grant_type', 'refresh_token'); f.set('refresh_token', grant.refresh); }
  return f;
}

/** The WebSocket address for a house address. */
export function socketUrl(ha) { return `${ha.replace(/^http/i, 'ws')}/api/websocket`; }

/** What a token answer gives, or null when it has no access token. [now] is in milliseconds. */
export function parseTokens(body, now = Date.now()) {
  if (!body || typeof body.access_token !== 'string') return null;
  return { access: body.access_token, refresh: body.refresh_token || null, expires: now + Math.max(60, Number(body.expires_in) || 1800) * 1000 };
}

const store = {
  read(k, area = 'localStorage') { try { return JSON.parse(globalThis[area].getItem(k)); } catch { return null; } },
  write(k, v, area = 'localStorage') { try { globalThis[area].setItem(k, JSON.stringify(v)); } catch { /* private window: signed in for this visit only */ } },
  clear(k, area = 'localStorage') { try { globalThis[area].removeItem(k); } catch { /* nothing to clear */ } },
};

let session = null;

export function signedIn() { return !!(session || store.read(KEY)); }

export function signOut() { session = null; store.clear(KEY); }

/** Sends the browser to Home Assistant's login page. */
export function startSignIn(haAddress) {
  const ha = cleanAddress(haAddress) || DEFAULT_HA;
  const state = Array.from(crypto.getRandomValues(new Uint8Array(12)), (b) => b.toString(16).padStart(2, '0')).join('');
  store.write(STATE_KEY, { state, ha }, 'sessionStorage');
  location.assign(authorizeUrl(ha, siteId(), state));
}

/** Home Assistant sent the browser back with ?code=...&state=...: swaps the code for tokens. Returns an error text, or null when signed in. */
export async function finishSignIn(params) {
  const saved = store.read(STATE_KEY, 'sessionStorage');
  store.clear(STATE_KEY, 'sessionStorage');
  if (!saved || saved.state !== params.get('state')) return 'That sign-in did not start here. Start again.';
  const code = params.get('code');
  if (!code) return 'Home Assistant did not give a sign-in code. Start again.';
  let tokens;
  try {
    const res = await fetch(`${saved.ha}/auth/token`, { method: 'POST', body: tokenForm(siteId(), { code }) });
    tokens = parseTokens(await res.json());
  } catch { return 'Could not reach Home Assistant. Check the address and your connection.'; }
  if (!tokens) return 'Home Assistant refused the sign-in. Start again.';
  session = { ha: saved.ha, ...tokens };
  store.write(KEY, { ha: saved.ha, refresh: tokens.refresh });
  return null;
}

/** A current access token (refreshed when it is nearly out of date). Null when there is no sign-in; throws (with `fatal` when the sign-in has really ended) when it cannot be refreshed. */
export async function accessToken() {
  const saved = store.read(KEY);
  if (!session && saved) session = { ha: saved.ha, refresh: saved.refresh, access: null, expires: 0 };
  if (!session) return null;
  if (session.access && session.expires - Date.now() > 60_000) return session.access;
  if (!session.refresh) return null;
  let res;
  try { res = await fetch(`${session.ha}/auth/token`, { method: 'POST', body: tokenForm(siteId(), { refresh: session.refresh }) }); }
  catch { throw new Error('Could not reach Home Assistant'); } // a dropped connection is not a sign-out: the sign-in is kept
  // Only Home Assistant saying the refresh token is no good ends the sign-in.
  if (res.status === 400 || res.status === 401 || res.status === 403) { signOut(); throw Object.assign(new Error('Your Home Assistant sign-in has ended. Sign in again.'), { fatal: true }); }
  if (!res.ok) throw new Error(`Home Assistant answered ${res.status}`);
  const tokens = parseTokens(await res.json().catch(() => null));
  if (!tokens) throw new Error('Home Assistant gave no sign-in token');
  session = { ...session, access: tokens.access, expires: tokens.expires };
  return session.access;
}

export function houseAddress() { return (session || store.read(KEY) || {}).ha || DEFAULT_HA; }

/**
 * Home Assistant's WebSocket: [call] sends one command and resolves with its result (rejects with the error Home Assistant gave),
 * [onEvent] gets every event of a subscription, and [onClose] hears when the line drops (with the WebSocket close code: 1001, 1012 and 1013 are Home Assistant restarting).
 */
export class HaSocket {
  constructor() { this.id = 0; this.waiting = new Map(); this.events = new Map(); this.onClose = () => {}; }

  async connect() {
    const token = await accessToken();
    if (!token) throw new Error('Not signed in');
    await new Promise((resolve, reject) => {
      const ws = new WebSocket(socketUrl(houseAddress()));
      this.ws = ws;
      // A line that closes before it is signed in, or never answers (a proxy that accepts and then says nothing), is a failed try to go again,
      // not a wait without end: the reconnect loop only tries again once this has settled.
      const timer = setTimeout(() => { try { ws.close(); } catch { /* already closed */ } reject(new Error('Home Assistant did not answer')); }, 15000);
      const ok = () => { clearTimeout(timer); resolve(); };
      const fail = (e) => { clearTimeout(timer); reject(e); };
      ws.onerror = () => fail(new Error('Could not reach Home Assistant'));
      let up = false;
      ws.onclose = (ev) => {
        this.waiting.forEach((w) => w.reject(new Error('The line to Home Assistant dropped'))); this.waiting.clear();
        if (up) this.onClose(ev.code); else fail(new Error('Could not reach Home Assistant'));
      };
      ws.onmessage = (e) => {
        const m = JSON.parse(e.data);
        if (m.type === 'auth_required') ws.send(JSON.stringify({ type: 'auth', access_token: token }));
        else if (m.type === 'auth_ok') { up = true; ws.onmessage = (ev) => this.#message(JSON.parse(ev.data)); ok(); }
        else if (m.type === 'auth_invalid') fail(Object.assign(new Error('Home Assistant refused the sign-in'), { fatal: true }));
      };
    });
  }

  #message(m) {
    if (m.type === 'result') {
      const w = this.waiting.get(m.id); this.waiting.delete(m.id);
      if (w) m.success ? w.resolve(m.result) : w.reject(Object.assign(new Error(m.error?.message || 'Home Assistant refused that'), { code: m.error?.code }));
    } else if (m.type === 'event') this.events.get(m.id)?.(m.event);
  }

  call(type, params = {}) {
    return new Promise((resolve, reject) => {
      const id = ++this.id;
      this.waiting.set(id, { resolve, reject });
      this.ws.send(JSON.stringify({ id, type, ...params }));
    });
  }

  async subscribe(type, params, onEvent) {
    const id = ++this.id;
    this.events.set(id, onEvent);
    await new Promise((resolve, reject) => { this.waiting.set(id, { resolve, reject }); this.ws.send(JSON.stringify({ id, type, ...params })); });
    return () => { this.events.delete(id); try { this.ws.send(JSON.stringify({ id: ++this.id, type: 'unsubscribe_events', subscription: id })); } catch { /* line already down */ } };
  }

  close() { try { this.ws.close(); } catch { /* already closed */ } }
}
