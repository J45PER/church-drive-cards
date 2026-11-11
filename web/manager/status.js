// What the line to Home Assistant is doing, as the status capsule in the navigation bar says it (capsule.js draws it; the apps have the same rules in
// LinkRefresh.kt and the design reference is design/clive/link-pill-site.html): Connecting, Updating, No connection (after six seconds down),
// Home Assistant restarting, and "Up to date" after a refresh the person asked for by pressing the Manager icon.
//
// This file is rules, words and the controller that keeps the state; it draws nothing itself, so it can be tested without a page
// (test/manager-status.test.mjs). The page gives it a view with mount().

export { Anim, FOSE } from './motion.js';

// ---- Rules (the same as pillKind and LinkRefresh in the apps) ----

/** How long the line may be down before "Connecting" turns into "No connection" (a short blip is not worth a warning). */
export const NO_CONNECTION_AFTER_MS = 6000;
/** The WebSocket close codes Home Assistant uses when it shuts down (going away), restarts, or asks to try again later. */
export const RESTART_CLOSE_CODES = [1001, 1012, 1013];
export const isRestartClose = (code) => RESTART_CLOSE_CODES.includes(code);

/** Which pill the line calls for: 'none', 'connecting', 'updating', 'noconnection' or 'restarting'. [offlineFor] is how long it has been down, in ms. */
export function pillKind(s, offlineFor) {
  if (s.connection === 'AuthFailed') return 'none';
  if (s.restarting && (s.connection !== 'Connected' || !s.statesLoaded)) return 'restarting';
  if (s.connection !== 'Connected') return offlineFor >= NO_CONNECTION_AFTER_MS ? 'noconnection' : 'connecting';
  if (!s.statesLoaded || s.refreshing) return 'updating';
  return 'none';
}
const FACE_OF = { none: 'hidden', connecting: 'connecting', updating: 'updating', noconnection: 'noconn', restarting: 'restart' };
export const faceOf = (kind) => FACE_OF[kind] || 'hidden';

/**
 * The face to show: hidden (nothing to say), connecting, updating, done, noconn or restart. [phase] is the refresh the person asked for (0 none,
 * 1 refreshing, 2 done): it shows Updating, then Up to date; a line that is down while it runs keeps saying so.
 */
export function chooseFace(link, offlineFor, phase) {
  const kind = pillKind(link, offlineFor);
  if (phase === 2) return 'done';
  if (phase === 1) return kind === 'none' || kind === 'updating' ? 'updating' : faceOf(kind);
  return faceOf(kind);
}

// ---- Words ----

/** A length of time as "7 s" or "1 min 12 s". */
export function duration(ms) {
  const t = Math.max(0, Math.floor(ms / 1000));
  return t < 60 ? `${t} s` : `${Math.floor(t / 60)} min ${t % 60} s`;
}
/** A time of day as "12:04:11". */
export function wallClock(ms) { return new Date(ms).toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit', second: '2-digit' }); }

/**
 * The two lines of the capsule for a face: a title and the detail. Only what the site really knows: the address, the page, the tries, when the line
 * was last up, what Home Assistant says about itself. [short] is for a phone, where "Home Assistant restarting" is "Restarting".
 */
export function capsuleText(face, i, short = false) {
  const retry = i.retryIn > 0 ? `${Math.ceil(i.retryIn / 1000)} s` : '';
  switch (face) {
    case 'connecting': return ['Connecting', `Attempt ${Math.max(1, i.attempt)}${retry ? ` · next try in ${retry}` : ` · reaching ${i.host}`}`];
    case 'updating': return ['Updating', `Reloading ${i.page}`];
    case 'done': return ['Up to date', `${i.page} updated ${i.updatedAt ? wallClock(i.updatedAt) : 'just now'}`];
    case 'noconn': return ['No connection', `${i.lostAt ? `Last up ${wallClock(i.lostAt)}` : 'Can’t reach the house'}${retry ? ` · retry in ${retry}` : ' · retrying'}`];
    case 'restart': return [short ? 'Restarting' : 'Home Assistant restarting',
      !i.connected ? `Said so${i.restartedAt ? ` at ${wallClock(i.restartedAt)}` : ''}${i.restartedAt ? ` · ${duration(i.now - i.restartedAt)}` : ''}`
        : i.haState && i.haState !== 'RUNNING' ? 'Starting up' : `Loading ${i.page}`];
  }
  return ['', ''];
}

// ---- The controller ----

/**
 * Keeps the line's state and drives a view. opts: host() the house's name, page() the current page's title. The page calls mount(view) once the
 * capsule is on screen (a view has frame(now, face, lines), quiet and el), and the rest as the line changes.
 */
export function createStatus(opts) {
  const link = { connection: 'Connected', statesLoaded: true, refreshing: false, restarting: false };
  const world = { attempt: 0, retryAt: 0, lostAt: 0, restartedAt: 0, updatedAt: 0, version: '', latency: 0, haState: '' };
  let since = 0, phase = 0, phaseT = 0, view = null, raf = 0, restartTimer = 0, lastDraw = 0, face = 'rest';
  const clock = () => performance.now();
  const narrow = () => !!globalThis.matchMedia && globalThis.matchMedia('(max-width: 899px)').matches;

  function info() {
    return { now: Date.now(), host: opts.host(), page: opts.page(), attempt: world.attempt, retryIn: world.retryAt ? Math.max(0, world.retryAt - Date.now()) : 0, lostAt: world.lostAt,
      restartedAt: world.restartedAt, updatedAt: world.updatedAt, version: world.version, latency: world.latency, haState: world.haState, connected: link.connection === 'Connected' };
  }
  function step(now) {
    raf = 0;
    if (!view || !view.el.isConnected) { view = null; return; }                // the page was redrawn (signed out): nothing to draw
    // LaunchedEffect(phase): refreshing lasts at least 0.9 s, then until the house has answered; "Up to date" stays 1.3 s.
    if (phase === 1 && now - phaseT >= 900) {
      const waiting = link.refreshing || link.connection !== 'Connected' || !link.statesLoaded;
      if (!waiting || now - phaseT >= 12900) { phase = link.connection === 'Connected' ? 2 : 0; phaseT = now; }
    } else if (phase === 2 && now - phaseT >= 1300) { phase = 0; phaseT = now; }
    const offline = link.connection === 'Connected' ? 0 : Math.floor((now - since) / 1000) * 1000;
    const f = chooseFace(link, offline, phase), shown = f === 'hidden' ? 'rest' : f;
    // At rest the cloud only drifts, so it is drawn about fifteen times a second; anything moving is drawn every frame.
    if (shown === 'rest' && shown === face && view.quiet && now - lastDraw < 66) { kick(); return; }
    face = shown; lastDraw = now;
    view.frame(now, shown, shown === 'rest' ? ['', ''] : capsuleText(shown, info(), narrow()));
    kick();
  }
  function kick() { if (view && !raf) raf = requestAnimationFrame(step); }
  function set(patch) {
    const wasUp = link.connection === 'Connected';
    Object.assign(link, patch);
    if (wasUp !== (link.connection === 'Connected')) since = clock();
    kick();
  }

  return {
    /** The capsule is on screen: draw it from now on. */
    mount(v) { view = v; kick(); },
    /** Trying to reach the house: [attempt] counts from 1. */
    connecting(attempt) { world.attempt = attempt; world.retryAt = 0; set({ connection: 'Connecting' }); },
    /** The line is down and the next try is [retryIn] ms away. */
    waiting(attempt, retryIn) { world.attempt = attempt; world.retryAt = Date.now() + retryIn; set({ connection: 'Disconnected' }); },
    /** The line is up. [facts] is what Home Assistant said about itself: { version, state, latency }. */
    connected(facts = {}) {
      world.attempt = 0; world.retryAt = 0;
      if (facts.version) world.version = facts.version;
      if (facts.latency) world.latency = facts.latency;
      world.haState = facts.state || '';
      set({ connection: 'Connected', statesLoaded: false });
    },
    /** The line dropped; [code] is the WebSocket close code, which says whether Home Assistant is restarting. */
    lost(code) { world.lostAt = Date.now(); if (isRestartClose(code)) this.restarting(true); set({ connection: 'Disconnected' }); },
    /** Home Assistant said it is restarting (or is still starting up). It lets go by itself after five minutes. */
    restarting(on) {
      clearTimeout(restartTimer);
      if (on) { if (!link.restarting) world.restartedAt = Date.now(); restartTimer = setTimeout(() => set({ restarting: false }), 300000); }
      set({ restarting: on });
    },
    /** The page's data is in (or has been asked for again). */
    loading() { set({ statesLoaded: false }); },
    loaded() { world.updatedAt = Date.now(); set({ statesLoaded: true, restarting: false }); },
    /** A refresh the person asked for, by pressing the Manager icon: the page reloads, then refreshed() is called. */
    refreshing() { phase = 1; phaseT = clock(); set({ refreshing: true }); },
    refreshed() { world.updatedAt = Date.now(); set({ refreshing: false }); },
    /** Sign-in screens and the like: nothing to say. */
    hide() { clearTimeout(restartTimer); phase = 0; Object.assign(link, { connection: 'Connected', statesLoaded: true, refreshing: false, restarting: false }); kick(); },
    /** What the capsule is showing now: rest, connecting, updating, done, noconn or restart. */
    face() { return face; },
  };
}
