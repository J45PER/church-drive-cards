// The status banner: the website's large version of the pill every Church Drive app shows at the top of its page (design/clive/link-pill.html is the
// reference, android: core/.../LinkRefresh.kt). It starts as Clive's round button, widens into a banner 120 px tall and 464 px wide, and keeps his
// colour cloud drifting behind it. It says what the line to Home Assistant is doing: Connecting, Updating, No connection (with a Try now button),
// Home Assistant restarting, and, while pulling down on a touch screen, Pull to refresh, Release to refresh and Up to date.
//
// The top half of this file is rules and words (no page needed, tested in test/manager-status.test.mjs); the bottom half draws.

// ---- Rules (the same as pillKind and LinkRefresh in the apps) ----

/** How long the line may be down before "Connecting" turns into "No connection" (a short blip is not worth a warning). */
export const NO_CONNECTION_AFTER_MS = 6000;
/** The WebSocket close codes Home Assistant uses when it shuts down (going away), restarts, or asks to try again later. */
export const RESTART_CLOSE_CODES = [1001, 1012, 1013];
export const isRestartClose = (code) => RESTART_CLOSE_CODES.includes(code);
/** How far the pull has to go (in pixels of pill travel) before letting go refreshes. The finger's own travel counts for half, as in the apps. */
export const PULL_THRESHOLD = 80;
export const pullFraction = (dragPx) => Math.max(0, dragPx) * 0.5 / PULL_THRESHOLD;

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

/** The face to show. [phase] is the refresh after a pull (0 none, 1 refreshing, 2 done) and [pull] how far the finger has dragged (a fraction of the threshold). */
export function chooseFace(link, offlineFor, phase, pull) {
  const kind = pillKind(link, offlineFor);
  if (phase === 2) return 'done';
  if (phase === 1) return kind === 'none' || kind === 'updating' ? 'updating' : faceOf(kind);
  if (pull > 0.0005 && kind !== 'restarting') return pull >= 1 ? 'release' : 'pull';
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
 * What each face says: a title, two lines, and a progress bar ('indeterminate', or null) or a button. Only things the site really knows are said:
 * the address, the page, the tries, when the line was last up, what Home Assistant reports about itself and how fast it answered.
 */
export function bigText(face, i) {
  const retry = i.retryIn > 0 ? `${Math.ceil(i.retryIn / 1000)} s` : '';
  const updated = i.updatedAt ? `${wallClock(i.updatedAt)} (${duration(i.now - i.updatedAt)} ago)` : 'just now';
  switch (face) {
    case 'connecting': return { t: 'Connecting', a: `Reaching ${i.host}`, b: `Attempt ${Math.max(1, i.attempt)}${retry ? ` · next try in ${retry}` : ''}`, bar: 'indeterminate' };
    case 'updating': return { t: 'Updating', a: `Reloading ${i.page}`, b: i.version ? `Home Assistant ${i.version}` : 'Asking the house for the latest', bar: 'indeterminate' };
    case 'pull': return { t: 'Pull to refresh', a: `${i.page} · last updated ${updated}`, b: 'Keep pulling to update', bar: null };
    case 'release': return { t: 'Release to refresh', a: `${i.page} · last updated ${updated}`, b: `Let go to reload ${i.page}`, bar: null };
    case 'done': return { t: 'Up to date', a: `${i.page} updated ${i.updatedAt ? wallClock(i.updatedAt) : 'just now'}`, b: i.version ? `Home Assistant ${i.version}${i.latency ? ` · ${i.latency} ms` : ''}` : 'Home Assistant is answering', bar: null };
    case 'noconn': return { t: 'No connection', a: `Can’t reach the house${i.lostAt ? ` · last up at ${wallClock(i.lostAt)}` : ''}`, b: retry ? `Trying again in ${retry} · attempt ${Math.max(1, i.attempt)}` : `Trying again now · attempt ${Math.max(1, i.attempt)}`, bar: null, btn: 'Try now' };
    case 'restart': return {
      t: 'Home Assistant restarting',
      a: `Said it was restarting${i.restartedAt ? ` at ${wallClock(i.restartedAt)} · ${duration(i.now - i.restartedAt)}` : ''}`,
      b: !i.connected ? 'Waiting for it to come back' : i.haState && i.haState !== 'RUNNING' ? 'Home Assistant is starting up' : `Loading ${i.page}`,
      bar: 'indeterminate',
    };
  }
  return { t: '', a: '', b: '', bar: null };
}

// ---- Animation (Compose's tween and spring, as in the design page) ----

const TAU = Math.PI * 2;
const clamp = (v, a, b) => Math.min(b, Math.max(a, v));
const bez = (x1, y1, x2, y2) => (t) => {
  if (t <= 0) return 0; if (t >= 1) return 1;
  let lo = 0, hi = 1, x;
  for (let i = 0; i < 28; i++) { x = (lo + hi) / 2; const cx = 3 * x1 * (1 - x) * (1 - x) * x + 3 * x2 * (1 - x) * x * x + x * x * x; if (cx < t) lo = x; else hi = x; }
  const s = (lo + hi) / 2; return 3 * y1 * (1 - s) * (1 - s) * s + 3 * y2 * (1 - s) * s * s + s * s * s;
};
export const FOSE = bez(0.4, 0, 0.2, 1);                       // Material's FastOutSlowIn, the default of tween()
const triangle = (t, period) => { const q = (t / period) % 2; return q < 1 ? q : 2 - q; };   // reversing repeat

/** One animated value: set(target, spec) starts from wherever it is now. spec: {dur, delay?} or {spring: omega}. */
export class Anim {
  constructor(v) { this.from = v; this.to = v; this.t0 = 0; this.spec = null; }
  jump(v) { this.from = this.to = v; this.spec = null; }
  set(to, spec, now) { if (to === this.to) return; this.from = this.get(now); this.to = to; this.t0 = now; this.spec = spec; }
  get(now) {
    const s = this.spec; if (!s) return this.to;
    const t = now - this.t0 - (s.delay || 0); if (t <= 0) return this.from;
    let p;
    if (s.spring) { const x = s.spring * t / 1000; p = 1 - (1 + x) * Math.exp(-x); if (p > 0.9995) p = 1; } else p = t >= s.dur ? 1 : FOSE(t / s.dur);
    return this.from + (this.to - this.from) * p;
  }
}

// ---- Colours ----

const hex = (h) => { const n = parseInt(h.slice(1), 16); return [n >> 16 & 255, n >> 8 & 255, n & 255]; };
const rgb = (c, a = 1) => `rgba(${c[0] | 0},${c[1] | 0},${c[2] | 0},${a})`;
const mix = (a, b, t) => a.map((v, i) => v + (b[i] - v) * t);
const RED = { colour: '#F25962', dark: '#491D20', accent: '#EF61AC', light: '#F7BABE', deep: '#6C2327' };       // CliveColours.Bad
const AMBER = { colour: '#F19655', dark: '#491D20'.replace('491D20', '492F1D'), accent: '#EF6168', light: '#F7D4BA', deep: '#6C4123' };   // CliveColours.Warn
const CLASSIC = [hex('#5C6BC0'), hex('#AB47BC'), hex('#4FD08A')];                                           // Clive's cloud
const WHITE = [255, 255, 255];
/** A card's container and text colour in the house's tones: a soft tint of Light with Deep text, or its Dark ground with white text. */
const tone = (set, dark) => (dark ? { bg: hex(set.dark), ink: hex('#EEF1F5') } : { bg: mix(WHITE, hex(set.light), 0.38), ink: hex(set.deep) });

/** The banner's colours from the page's own tokens, so it follows the site's light or dark theme. */
function themeNow() {
  const root = document.documentElement, cs = getComputedStyle(root);
  const read = (name, fallback) => { const v = cs.getPropertyValue(name).trim(); return /^#[0-9a-f]{6}$/i.test(v) ? hex(v) : fallback; };
  const bg = read('--bg', hex('#F3F5F9')), dark = (bg[0] * 0.2126 + bg[1] * 0.7152 + bg[2] * 0.0722) < 128;
  const line = read('--line', hex('#DDE1E8'));
  return {
    dark,
    normal: { bg: read('--bar', hex('#E7EAF0')), ink: read('--ink', hex('#14181F')), line, cloud: CLASSIC },
    noconn: { ...tone(RED, dark), line, cloud: [hex(RED.colour), hex(RED.accent), hex(RED.colour)] },
    restart: { ...tone(AMBER, dark), line, cloud: [hex(AMBER.colour), hex(AMBER.accent), hex(AMBER.colour)] },
  };
}

// ---- Faces ----

const FACES = {
  connecting: { icon: 'mark', tone: 'normal' }, updating: { icon: 'mark', tone: 'normal' }, pull: { icon: 'mark', tone: 'normal' }, release: { icon: 'mark', tone: 'normal' },
  done: { icon: 'tick', tone: 'normal' }, noconn: { icon: 'bars', tone: 'noconn' }, restart: { icon: 'ring', tone: 'restart' },
};
const H = 120, SLOT = 120, K = 3, ORB = 96, BODY = 344;       // 3 times as tall as the pill, a 120 px circle then a 344 px body: 464 px wide
export const BANNER_WIDTH = SLOT + BODY;

// ---- Drawing: Clive's mark and cloud ----

function roundRect(x, y, w, h, r) {
  return `M${x + r} ${y}H${x + w - r}A${r} ${r} 0 0 1 ${x + w} ${y + r}V${y + h - r}A${r} ${r} 0 0 1 ${x + w - r} ${y + h}H${x + r}A${r} ${r} 0 0 1 ${x} ${y + h - r}V${y + r}A${r} ${r} 0 0 1 ${x + r} ${y}Z`;
}
/** AssistantGlyph's idle pose: two squircles sliding along the diagonal (4 s), overlap cut out by the even-odd fill. */
function markPath(now) {
  const together = 1 - 0.55 * (1 - (Math.cos(((now % 4000) / 4000) * TAU) + 1) / 2);
  const s = 11.3 * together;
  return roundRect(62 - s, 62 + s, 76, 76, 30) + roundRect(62 + s, 62 - s, 76, 76, 30);
}
/** AssistOrb, idle: three soft clouds on a 9 s drift and a 4.5 s breathe, stretched over [wDp] by [sx]. */
function drawCloud(ctx, now, colours, px, wDp, sx) {
  const phase = ((now % 9000) / 9000) * TAU, breathe = -1 + 2 * FOSE(triangle(now, 4500));
  const u = ORB / 120 * (1 + 0.05 * breathe), drift = 11 * u;
  ctx.setTransform(1, 0, 0, 1, 0, 0); ctx.clearRect(0, 0, ctx.canvas.width, ctx.canvas.height);
  ctx.setTransform(px * sx, 0, 0, px, wDp * px / 2, H * px / 2);
  ctx.globalCompositeOperation = 'screen';
  const cloud = (col, dx, dy, disc, a, b) => {
    const ax = dx * u + Math.cos(phase * a) * drift, ay = dy * u + Math.sin(phase * b) * drift, radius = disc * u, outer = radius + 26 * u;
    const g = ctx.createRadialGradient(ax, ay, 0, ax, ay, outer);
    g.addColorStop(0, rgb(col, 1));
    g.addColorStop(clamp(Math.max(radius - 26 * u, 0) / outer, 0, 1), rgb(col, 1));
    g.addColorStop(clamp(radius / outer, 0, 1), rgb(col, 0.5));
    g.addColorStop(1, rgb(col, 0));
    ctx.fillStyle = g; ctx.beginPath(); ctx.arc(ax, ay, outer, 0, TAU); ctx.fill();
  };
  cloud(colours[0], -13, -1, 42, 1, 2); cloud(colours[1], 15.5, -13.5, 39.5, 2, 1); cloud(colours[2], -3, 15, 37, 1, 1);
}

const NS = 'http://www.w3.org/2000/svg';
const svgEl = (tag, attrs, parent) => { const e = document.createElementNS(NS, tag); for (const k in attrs) e.setAttribute(k, attrs[k]); if (parent) parent.appendChild(e); return e; };
const div = (cls, parent) => { const e = document.createElement('div'); e.className = cls; if (parent) parent.appendChild(e); return e; };

/** The picture at the left: Clive's mark, an offline signal, a restart ring or a tick. */
class IconLayer {
  constructor(parent) {
    this.root = div('st-ic', parent);
    this.mark = svgEl('svg', { viewBox: '48 48 104 104' }, this.root); this.markPath = svgEl('path', { 'fill-rule': 'evenodd', fill: 'currentColor' }, this.mark);
    this.bars = svgEl('svg', { viewBox: '0 0 22 22' }, this.root);
    this.barLines = svgEl('g', { stroke: 'currentColor', 'stroke-linecap': 'round', fill: 'none' }, this.bars);
    this.barEls = [0, 1, 2].map(() => svgEl('line', {}, this.barLines));
    this.slash = svgEl('line', { stroke: 'currentColor', 'stroke-linecap': 'round' }, this.bars);
    this.ring = svgEl('svg', { viewBox: '0 0 20 20' }, this.root); this.arc = svgEl('path', { fill: 'none', stroke: 'currentColor', 'stroke-width': 2, 'stroke-linecap': 'round' }, this.ring);
    this.tick = svgEl('svg', { viewBox: '0 0 22 22' }, this.root);
    this.t1 = svgEl('line', { stroke: 'currentColor', 'stroke-linecap': 'round', 'stroke-width': 2.64 }, this.tick);
    this.t2 = svgEl('line', { stroke: 'currentColor', 'stroke-linecap': 'round', 'stroke-width': 2.64 }, this.tick);
  }
  draw(face, now, dp, ctx) {
    const f = FACES[face];
    if (!f) { this.root.style.display = 'none'; return; }
    this.root.style.display = 'grid';
    const svgs = { mark: this.mark, bars: this.bars, ring: this.ring, tick: this.tick };
    for (const k in svgs) svgs[k].style.display = k === f.icon ? 'block' : 'none';
    const size = { mark: 22, bars: 22, ring: 20, tick: 22 }[f.icon] * K * dp;
    svgs[f.icon].style.width = svgs[f.icon].style.height = `${size}px`;
    this.root.style.transform = '';
    if (f.icon === 'mark') {
      this.markPath.setAttribute('d', markPath(ctx.still ? 0 : now));
      // Turns with the finger while pulling and has made half a turn by Release (upside down it looks the same); loaded faces only drift.
      this.root.style.transform = `rotate(${face === 'pull' ? ctx.pull * 180 : face === 'release' ? 180 : 0}deg)`;
    } else if (f.icon === 'bars') {
      const w = 22, h = 22, bar = w * 0.16, pulse = 0.35 + 0.65 * FOSE(triangle(ctx.still ? 0 : now, 900));
      [0.35, 0.6, 0.85].forEach((top, n) => { const x = w * (0.18 + n * 0.27), l = this.barEls[n]; l.setAttribute('x1', x); l.setAttribute('x2', x); l.setAttribute('y1', h * 0.9); l.setAttribute('y2', h * (1 - top) + h * 0.1); l.setAttribute('stroke-width', bar); });
      this.barLines.setAttribute('opacity', pulse);
      this.slash.setAttribute('x1', w * 0.08); this.slash.setAttribute('y1', h * 0.08); this.slash.setAttribute('x2', w * 0.92); this.slash.setAttribute('y2', h * 0.92); this.slash.setAttribute('stroke-width', bar * 0.9);
    } else if (f.icon === 'ring') {
      const t = ctx.still ? 0 : now, turn = (t % 1100) / 1100 * 360, sweep = FOSE(triangle(t, 1400));
      const start = (turn * 0.8 - 90 + 190 * sweep * 0.5) * Math.PI / 180, span = (90 + 190 * sweep) * Math.PI / 180, r = 9;
      const at = (a) => [10 + r * Math.cos(a), 10 + r * Math.sin(a)], a0 = at(start), a1 = at(start + span);
      this.arc.setAttribute('d', `M${a0[0]} ${a0[1]}A${r} ${r} 0 ${span > Math.PI ? 1 : 0} 1 ${a1[0]} ${a1[1]}`);
    } else {
      const draw = FOSE(clamp((now - ctx.tickT) / 380, 0, 1)), w = 22, a = [w * 0.2, w * 0.55], b = [w * 0.42, w * 0.76], c = [w * 0.82, w * 0.28];
      const first = Math.min(draw * 2, 1), second = clamp((draw - 0.5) * 2, 0, 1);
      this.t1.setAttribute('x1', a[0]); this.t1.setAttribute('y1', a[1]); this.t1.setAttribute('x2', a[0] + (b[0] - a[0]) * first); this.t1.setAttribute('y2', a[1] + (b[1] - a[1]) * first);
      this.t2.setAttribute('x1', b[0]); this.t2.setAttribute('y1', b[1]); this.t2.setAttribute('x2', b[0] + (c[0] - b[0]) * second); this.t2.setAttribute('y2', b[1] + (c[1] - b[1]) * second);
      this.t2.style.display = second > 0 ? '' : 'none';
    }
  }
}

/** The words: a title, two lines, and a bar or a button. */
class TextLayer {
  constructor(parent, onTry) {
    this.root = div('st-tx', parent);
    this.t = div('st-t', this.root); this.a = div('st-a', this.root); this.b = div('st-a st-b', this.root);
    this.track = div('st-track', this.root); this.fill = document.createElement('i'); this.track.appendChild(this.fill);
    this.btn = document.createElement('button'); this.btn.type = 'button'; this.btn.className = 'st-try'; this.btn.textContent = 'Try now'; this.btn.addEventListener('click', (e) => { e.stopPropagation(); onTry(); });
    this.root.appendChild(this.btn);
  }
  set(face, info, tone_, w, dp, now) {
    const r = this.root.style;
    if (!face || w < 0.001) { r.display = 'none'; return; }
    r.display = 'flex'; r.opacity = w; r.width = `${(BODY - 24) * dp}px`; r.color = rgb(tone_.ink); r.gap = `${3 * dp}px`;
    const x = bigText(face, info);
    for (const [el, text] of [[this.t, x.t], [this.a, x.a], [this.b, x.b]]) if (el.textContent !== text) el.textContent = text;
    this.t.style.fontSize = `${22 * dp}px`; this.t.style.lineHeight = `${26 * dp}px`;
    for (const el of [this.a, this.b]) { el.style.fontSize = `${13.5 * dp}px`; el.style.lineHeight = `${19 * dp}px`; }
    const hasBar = x.bar !== null && x.bar !== undefined;
    this.track.style.display = hasBar ? 'block' : 'none'; this.track.style.height = `${5 * dp}px`; this.track.style.marginTop = `${5 * dp}px`; this.track.style.background = rgb(tone_.ink, 0.16);
    this.fill.style.background = rgb(tone_.ink, 0.78);
    if (hasBar) { const q = (now % 1500) / 1500, wd = 0.34; this.fill.style.width = `${wd * 100}%`; this.fill.style.marginLeft = `${(-wd + q * (1 + wd)) * 100}%`; }
    this.btn.style.display = x.btn ? 'block' : 'none'; this.btn.textContent = x.btn || '';
    this.btn.style.fontSize = `${13 * dp}px`; this.btn.style.height = `${26 * dp}px`; this.btn.style.padding = `0 ${14 * dp}px`; this.btn.style.marginTop = `${4 * dp}px`;
    this.btn.style.border = `${dp}px solid ${rgb(tone_.ink, 0.45)}`; this.btn.style.color = rgb(tone_.ink);
  }
}

/** The banner itself: StatusPill's animation (circle, widen, cloud, crossfade) at the large size. */
class Banner {
  constructor(host, onTry) {
    this.el = div('st-pv', host);
    this.cv = document.createElement('canvas'); this.el.appendChild(this.cv); this.c2 = this.cv.getContext('2d');
    this.slot = div('st-slot', this.el);
    this.icons = [new IconLayer(this.slot), new IconLayer(this.slot)];
    this.lbl = div('st-lbl', this.el);
    this.texts = [new TextLayer(this.lbl, onTry), new TextLayer(this.lbl, onTry)];
    this.ring = div('st-ring', this.el);
    this.a = { alpha: new Anim(0), scale: new Anim(0.4), slide: new Anim(-1), labelW: new Anim(0), labelA: new Anim(0), markP: new Anim(0), cloudA: new Anim(0.95) };
    this.visible = false; this.settled = false; this.settleAt = 0; this.shown = 'connecting'; this.prev = null; this.changeT = -1e9; this.tickT = 0;
  }
  /** Whether it can be left alone: gone, and not on its way in or out. */
  get asleep() { return !this.visible && this.a.alpha.get(1e12) < 0.01; }
  frame(now, face, pull, info, dp, still) {
    const A = this.a, vis = face !== 'hidden', th = themeNow();
    if (vis && face !== this.shown) {
      this.prev = A.alpha.get(now) > 0.02 && this.visible ? this.shown : null; if (this.prev) this.changeT = now;
      this.shown = face; if (face === 'done') this.tickT = now;
    }
    if (vis !== this.visible) {
      this.visible = vis;
      if (vis) {
        if (A.alpha.get(now) < 0.01) { A.alpha.jump(0); A.scale.jump(0.4); A.slide.jump(-1); }
        A.alpha.set(1, { dur: 200 }, now); A.scale.set(1, { dur: 300 }, now); A.slide.set(0, { spring: 20 }, now);
        this.settled = false; this.settleAt = now + 380; if (face === 'done') this.tickT = now;
      } else { A.alpha.set(0, { dur: 200 }, now); A.scale.set(0.6, { dur: 250 }, now); A.slide.set(-1, { spring: 20 }, now); this.settled = false; }
    }
    if (this.visible && !this.settled && now >= this.settleAt) this.settled = true;
    const expanded = this.visible && this.settled && this.shown !== 'pull';
    const t = th[(FACES[this.shown] || FACES.connecting).tone];
    A.labelW.set(expanded ? BODY : 0, { dur: expanded ? 400 : 280 }, now);
    A.labelA.set(expanded ? 1 : 0, expanded ? { dur: 320, delay: 80 } : { dur: 120 }, now);
    A.markP.set(expanded ? 1 : 0, { dur: 300 }, now);
    A.cloudA.set(expanded ? 0.5 : 0.95, { dur: 300 }, now);

    const wDp = SLOT + A.labelW.get(now), px = (v) => `${v * dp}px`, shift = pull > 0 ? (Math.min(pull, 1.3) - 1) * 36 * K : 0;
    const el = this.el.style;
    el.width = px(wDp); el.height = px(H); el.background = rgb(t.bg); el.opacity = A.alpha.get(now);
    el.boxShadow = `0 ${3 * dp}px ${7 * dp}px rgba(0,0,0,${th.dark ? 0.5 : 0.22}), 0 0 ${2 * dp}px rgba(0,0,0,${th.dark ? 0.4 : 0.16})`;
    el.transform = `translate(-50%, ${(A.slide.get(now) * H + shift) * dp}px) scale(${A.scale.get(now)})`;
    el.pointerEvents = A.alpha.get(now) > 0.5 ? 'auto' : 'none';
    this.ring.style.border = `${dp}px solid ${rgb(t.line)}`;
    const dpr = globalThis.devicePixelRatio || 1, cw = Math.max(1, Math.round(wDp * dp * dpr)), ch = Math.round(H * dp * dpr);
    if (this.cv.width !== cw || this.cv.height !== ch) { this.cv.width = cw; this.cv.height = ch; }
    drawCloud(this.c2, still ? 0 : now, t.cloud, dp * dpr, wDp, Math.max(1, wDp / H * 1.25));
    this.cv.style.opacity = A.cloudA.get(now);

    this.slot.style.width = this.slot.style.height = px(SLOT);
    const mk = mix(WHITE, t.ink, A.markP.get(now)), ctx = { pull, tickT: this.tickT, still };
    const inT = FOSE(clamp((now - this.changeT) / 180, 0, 1)), outT = 1 - FOSE(clamp((now - this.changeT) / 120, 0, 1));
    [[this.shown, inT], [this.prev, this.prev ? outT : 0]].forEach(([fc, w], n) => {
      const ic = this.icons[n];
      ic.root.style.opacity = fc ? w : 0; ic.root.style.color = rgb(mk);
      if (fc && w > 0.001) ic.draw(fc, now, dp, ctx); else ic.root.style.display = 'none';
      this.texts[n].set(fc, info, th[(FACES[fc || this.shown] || FACES.connecting).tone], w, dp, now);
    });
    this.lbl.style.width = px(A.labelW.get(now)); this.lbl.style.height = px(H); this.lbl.style.opacity = A.labelA.get(now);
  }
}

// ---- The status: the line's state, the pull, and the loop that draws them ----

/**
 * Mounts the banner on the page and returns the controls the app calls as the line changes.
 * opts: host() the house's name, page() the current page's title, onTry() called by Try now.
 */
export function createStatus(opts) {
  const root = document.createElement('div');
  root.id = 'status'; root.setAttribute('role', 'status'); root.setAttribute('aria-live', 'polite');
  document.body.appendChild(root);
  const banner = new Banner(root, () => opts.onTry && opts.onTry());
  const reduced = globalThis.matchMedia ? globalThis.matchMedia('(prefers-reduced-motion: reduce)') : { matches: false };
  const link = { connection: 'Connected', statesLoaded: true, refreshing: false, restarting: false };
  const world = { attempt: 0, retryAt: 0, lostAt: 0, restartedAt: 0, updatedAt: 0, version: '', latency: 0, haState: '' };
  let since = 0, phase = 0, phaseT = 0, finger = null, pullA = new Anim(0), raf = 0, restartTimer = 0;
  const clock = () => performance.now();
  const dp = () => clamp((innerWidth - 24) / BANNER_WIDTH, 0.55, 1.2);

  function face(now) {
    const offline = link.connection === 'Connected' ? 0 : Math.floor((now - since) / 1000) * 1000;
    const pull = finger !== null ? finger : pullA.get(now);
    const f = chooseFace(link, offline, phase, pull);
    return { face: f, pull: f === 'pull' || f === 'release' ? pull : 0, offline, rawPull: pull };
  }
  function info(now) {
    return { now: Date.now(), host: opts.host(), page: opts.page(), attempt: world.attempt, retryIn: world.retryAt ? Math.max(0, world.retryAt - Date.now()) : 0, lostAt: world.lostAt, restartedAt: world.restartedAt,
      updatedAt: world.updatedAt, version: world.version, latency: world.latency, haState: world.haState, connected: link.connection === 'Connected' };
  }
  function step(now) {
    raf = 0;
    // LaunchedEffect(phase): refreshing lasts at least 0.9 s, then until the house has answered; "Up to date" stays 1.3 s.
    if (phase === 1 && now - phaseT >= 900) {
      const waiting = link.refreshing || link.connection !== 'Connected' || !link.statesLoaded;
      if (!waiting || now - phaseT >= 12900) { phase = link.connection === 'Connected' ? 2 : 0; phaseT = now; }
    } else if (phase === 2 && now - phaseT >= 1300) { phase = 0; phaseT = now; }
    if (phase !== 1 && finger === null && pullA.to !== 0) pullA.set(0, { spring: Math.sqrt(1500) }, now);
    const f = face(now);
    banner.frame(now, f.face, f.pull, info(now), dp(), reduced.matches);
    if (!banner.asleep || f.face !== 'hidden' || pullA.get(now) > 0.0005 || phase !== 0) kick();
  }
  function kick() { if (!raf) raf = requestAnimationFrame(step); }
  addEventListener('resize', kick);

  function set(patch) {
    const wasUp = link.connection === 'Connected';
    Object.assign(link, patch);
    if (wasUp !== (link.connection === 'Connected')) since = clock();
    kick();
  }

  return {
    /** Trying to reach the house: [attempt] counts from 1, [retryIn] is how long until the next try if this one fails (ms). */
    connecting(attempt, retryIn = 0) { world.attempt = attempt; world.retryAt = retryIn ? Date.now() + retryIn : 0; set({ connection: 'Connecting' }); },
    /** The next try is [retryIn] ms away: the line is down in between. */
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
    /** A refresh the person asked for, by pulling down: the app reloads the page, then calls refreshed(). */
    refreshing() { phase = 1; phaseT = clock(); set({ refreshing: true }); },
    refreshed() { world.updatedAt = Date.now(); set({ refreshing: false }); },
    /** Sign-in screens and the like: nothing to say. */
    hide() { clearTimeout(restartTimer); phase = 0; finger = null; pullA.jump(0); Object.assign(link, { connection: 'Connected', statesLoaded: true, refreshing: false, restarting: false }); kick(); },
    /** Dragging down: [fraction] is the pull as a fraction of the threshold (see pullFraction). */
    pull(fraction) { finger = fraction; pullA.jump(fraction); kick(); },
    /** Let go. True when the pull went far enough to refresh. */
    release() {
      const frac = finger; finger = null; const go = frac !== null && frac > 1 && phase === 0;
      pullA.jump(frac || 0); pullA.set(go ? 1 : 0, { spring: Math.sqrt(1500) }, clock()); kick(); return go;
    },
    /** What the banner shows now (for tests and the page). */
    face() { return face(clock()).face; },
  };
}

/** Pull down on a touch screen, when the page is at the top: calls onRefresh after a long enough pull. Replaces the browser's own pull-to-reload. */
export function attachPull(status, onRefresh) {
  let y0 = null;
  addEventListener('touchstart', (e) => { y0 = scrollY <= 0 && e.touches.length === 1 ? e.touches[0].clientY : null; }, { passive: true });
  addEventListener('touchmove', (e) => { if (y0 !== null) { const d = e.touches[0].clientY - y0; if (d > 0 && scrollY <= 0) status.pull(pullFraction(d)); else if (d <= 0) status.pull(0); } }, { passive: true });
  const end = () => { if (y0 === null) return; y0 = null; if (status.release()) onRefresh(); };
  addEventListener('touchend', end); addEventListener('touchcancel', end);
}
