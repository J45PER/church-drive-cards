// The status capsule: at the right of the navigation bar the Manager icon (the house shield, not Clive's own logo) and the person's icon sit in one
// capsule. Press the Manager icon to refresh and the capsule widens to show two lines (a title and the detail) with a thin progress line while it
// works; it folds back by itself. A ring turns round the Manager icon while it works, a badge says done, no connection or restarting, and the
// capsule turns red or amber for a problem. Behind it Clive's colour cloud drifts, the brand's three colours.
// The rules and the words are in status.js; this file only draws. (Design reference: design/clive/link-pill-site.html.)

import { Anim, ColorAnim, FOSE, TAU, clamp, hex, mix, rgb, triangle } from './motion.js';
import { fillIcons, icon } from './icons.js';
import { LOGO_PATH, LOGO_VIEWBOX } from './logo.js';

const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

/** The capsule's markup with the person's button and menu in it. The Manager icon and the words are added in front of them by [CapsuleView]. */
export function capsuleHtml(name) {
  const who = esc(name || 'Signed in');
  return `<div class="capslot"><div class="cap">
      <div class="menu-wrap"><button class="person" type="button" id="who" aria-haspopup="true" aria-expanded="false" aria-label="Account: ${who}">${esc((name || '?').trim().slice(0, 1).toUpperCase())}</button>
        <div class="menu" id="menu" hidden role="menu"><p class="menu-name">${who}</p><p class="sub">Administrator</p>
          <button class="btn quiet" type="button" id="signout" role="menuitem">${icon('cd:sign-out', 20)} Sign out</button></div></div>
    </div></div>`;
}

// ---- Colours (the house's palettes, as the apps use them) ----

const RED = { colour: '#F25962', dark: '#491D20', accent: '#EF61AC', light: '#F7BABE', deep: '#6C2327' };     // CliveColours.Bad
const AMBER = { colour: '#F19655', dark: '#492F1D', accent: '#EF6168', light: '#F7D4BA', deep: '#6C4123' };   // CliveColours.Warn
const CLOUD = [hex('#5C6BC0'), hex('#AB47BC'), hex('#4FD08A')];                                            // the brand's three colours
const WHITE = [255, 255, 255];
/** A card's container and text colour in the house's tones: a soft tint of Light with Deep text, or its Dark ground with white text. */
const tone = (set, dark) => (dark ? { bg: hex(set.dark), ink: hex('#EEF1F5') } : { bg: mix(WHITE, hex(set.light), 0.38), ink: hex(set.deep) });
const BADGE = { noconn: ['cd:wifi-off', '#D64550'], restart: ['cd:update-arrow', '#D9822B'], done: ['cd:check', '#2E9A55'] };
const GREEN = hex('#4FD08A');

/** The capsule's colours from the page's own tokens, so it follows the site's light or dark theme (and the toggle). */
function themeNow() {
  const cs = getComputedStyle(document.documentElement);
  const read = (name, fallback) => { const v = cs.getPropertyValue(name).trim(); return /^#[0-9a-f]{6}$/i.test(v) ? hex(v) : fallback; };
  const bg = read('--bg', hex('#F3F5F9')), dark = (bg[0] * 0.2126 + bg[1] * 0.7152 + bg[2] * 0.0722) < 128;
  const line = read('--line', hex('#DDE1E8'));
  return {
    dark, line, primary: read('--primary', hex('#5B54C9')),
    normal: { bg: read('--card', hex('#FFFFFF')), ink: read('--ink', hex('#14181F')), cloud: CLOUD },
    noconn: { ...tone(RED, dark), cloud: [hex(RED.colour), hex(RED.accent), hex(RED.colour)] },
    restart: { ...tone(AMBER, dark), cloud: [hex(AMBER.colour), hex(AMBER.accent), hex(AMBER.colour)] },
  };
}

/** AssistOrb, idle: three soft clouds on a 9 s drift and a 4.5 s breathe, stretched over the capsule. Drawn on [ctx] in CSS pixels times [px]. */
function drawCloud(ctx, now, colours, px, w, h, sx, orb) {
  const phase = ((now % 9000) / 9000) * TAU, breathe = -1 + 2 * FOSE(triangle(now, 4500));
  const small = orb < 80, u = orb / 120 * (1 + 0.05 * breathe), drift = 11 * u, spread = small ? 1.5 : 1;
  ctx.setTransform(1, 0, 0, 1, 0, 0); ctx.clearRect(0, 0, ctx.canvas.width, ctx.canvas.height);
  ctx.setTransform(px * sx, 0, 0, px, w * px / 2, h * px / 2);
  ctx.globalCompositeOperation = 'screen';
  const cloud = (col, dx, dy, disc, a, b) => {
    const ax = dx * u * spread + Math.cos(phase * a) * drift, ay = dy * u * spread + Math.sin(phase * b) * drift;
    let radius = disc * u; const outer = radius + 26 * u; if (small) radius *= 0.9;
    const g = ctx.createRadialGradient(ax, ay, 0, ax, ay, outer);
    g.addColorStop(0, rgb(col, 1));
    g.addColorStop(clamp(Math.max(radius - 26 * u, 0) / outer, 0, 1), rgb(col, 1));
    g.addColorStop(clamp(radius / outer, 0, 1), rgb(col, 0.5));
    g.addColorStop(1, rgb(col, 0));
    ctx.fillStyle = g; ctx.beginPath(); ctx.arc(ax, ay, outer, 0, TAU); ctx.fill();
  };
  cloud(colours[0], -13, -1, 42, 1, 2); cloud(colours[1], 15.5, -13.5, 39.5, 2, 1); cloud(colours[2], -3, 15, 37, 1, 1);
}

function arc(c, r, startDeg, sweepDeg) {
  const a0 = startDeg * Math.PI / 180, a1 = (startDeg + sweepDeg) * Math.PI / 180;
  return `M${c + r * Math.cos(a0)} ${c + r * Math.sin(a0)}A${r} ${r} 0 ${sweepDeg > 180 ? 1 : 0} 1 ${c + r * Math.cos(a1)} ${c + r * Math.sin(a1)}`;
}

let uid = 0;
const textCtx = document.createElement('canvas').getContext('2d');
const measure = (text, font) => { textCtx.font = font; return textCtx.measureText(text).width; };

export class CapsuleView {
  /** [cap] is the `.cap` element from [capsuleHtml]; [onPress] is called when the Manager icon is pressed. */
  constructor(cap, onPress) {
    const id = 'cap' + (uid++);
    this.el = cap;
    this.cv = document.createElement('canvas'); this.cv.className = 'capcloud'; this.cv.setAttribute('aria-hidden', 'true'); this.c2 = this.cv.getContext('2d');
    this.mgr = document.createElement('button'); this.mgr.type = 'button'; this.mgr.className = 'mgr'; this.mgr.setAttribute('aria-label', 'Refresh this page');
    this.mgr.title = 'Refresh';
    const base = (c) => `<path class="sh-base" fill="${c}" fill-rule="evenodd" d="${LOGO_PATH}"/>`;
    this.mgr.innerHTML = `<span class="shield"><svg class="sh-col" viewBox="${LOGO_VIEWBOX}" aria-hidden="true"><defs>
        <radialGradient id="${id}p" gradientUnits="userSpaceOnUse" cx="150" cy="70" r="88"><stop offset="0" stop-color="#AB47BC"/><stop offset="1" stop-color="#AB47BC" stop-opacity="0"/></radialGradient>
        <radialGradient id="${id}g" gradientUnits="userSpaceOnUse" cx="70" cy="170" r="110"><stop offset="0" stop-color="#4FD08A"/><stop offset="1" stop-color="#4FD08A" stop-opacity="0"/></radialGradient>
        <radialGradient id="${id}i" gradientUnits="userSpaceOnUse" cx="40" cy="40" r="130"><stop offset="0" stop-color="#5C6BC0"/><stop offset="1" stop-color="#5C6BC0" stop-opacity="0"/></radialGradient></defs>
        ${base('#6A5BC2')}${[`url(#${id}p)`, `url(#${id}g)`, `url(#${id}i)`].map((f) => `<path fill="${f}" fill-rule="evenodd" d="${LOGO_PATH}"/>`).join('')}</svg>
        <svg class="sh-mono" viewBox="${LOGO_VIEWBOX}" aria-hidden="true"><path fill="currentColor" fill-rule="evenodd" d="${LOGO_PATH}"/></svg></span>
      <svg class="ring" viewBox="0 0 40 40" aria-hidden="true"><path fill="none" stroke-width="2.5" stroke-linecap="round"/></svg><span class="badge"></span>`;
    this.info = document.createElement('div'); this.info.className = 'info'; this.info.setAttribute('aria-live', 'polite');
    this.info.innerHTML = '<div class="in"><div class="t"></div><div class="d"></div></div><div class="pbar"><i></i></div>';
    const wrap = cap.querySelector('.menu-wrap');
    cap.insertBefore(this.cv, cap.firstChild); cap.insertBefore(this.mgr, wrap); cap.insertBefore(this.info, wrap);
    this.mgr.addEventListener('click', () => onPress && onPress());
    this.A = { infoW: new Anim(0), infoA: new Anim(0), cloudA: new Anim(0.2), txt: new Anim(1), ringA: new Anim(0), badgeA: new Anim(0) };
    this.bg = new ColorAnim([255, 255, 255]); this.face = 'rest'; this.shown = 'updating'; this.tickT = 0; this.quiet = false; this.textW = 0;
    const q = (s) => this.mgr.querySelector(s);
    this.ringPath = q('.ring path'); this.badge = q('.badge'); this.shieldCol = q('.sh-col'); this.shieldMono = q('.sh-mono'); this.shieldBase = q('.sh-base');
    this.t = this.info.querySelector('.t'); this.d = this.info.querySelector('.d'); this.in = this.info.querySelector('.in'); this.pbar = this.info.querySelector('.pbar');
  }

  /** How wide the words may be: what the bar has left once everything else in it has its room (a little for the wordmark and the navigation). */
  room() {
    const bar = this.el.closest('.site-in');
    if (!bar) return Infinity;
    const cs = getComputedStyle(bar), kids = [...bar.children].filter((k) => getComputedStyle(k).display !== 'none');
    const gap = parseFloat(cs.columnGap) || 0, pad = parseFloat(cs.paddingLeft) + parseFloat(cs.paddingRight), slot = this.el.parentNode;
    const others = kids.filter((k) => k !== slot && !k.classList.contains('brand') && !k.classList.contains('topnav')).reduce((s, k) => s + k.getBoundingClientRect().width, 0);
    const narrow = !!globalThis.matchMedia && globalThis.matchMedia('(max-width: 899px)').matches;
    // The capsule's own icons, padding and border take 92; the wordmark keeps a little on a phone and the brand and navigation keep 220 on a desktop.
    return Math.max(0, bar.clientWidth - pad - others - gap * (kids.length - 1) - 92 - (narrow ? 32 : 220));
  }

  /** One frame. [face] is rest, connecting, updating, done, noconn or restart; [lines] the title and the detail. */
  frame(now, face, lines) {
    const A = this.A, th = themeNow(), exp = face !== 'rest';
    if (face !== this.face) {
      this.face = face;
      if (exp) {
        this.shown = face; A.txt.jump(0); A.txt.set(1, { dur: 180 }, now);
        if (BADGE[face]) { this.badge.innerHTML = icon(BADGE[face][0], 11); fillIcons(this.badge); this.badge.style.background = BADGE[face][1]; A.badgeA.jump(0); }
      }
      if (face === 'done') this.tickT = now;
    }
    // At rest the words are kept as they were while the capsule folds away (and nothing changes in the live region).
    if (exp) {
      const [title, detail] = lines;
      if (this.t.textContent !== title) this.t.textContent = title;
      if (this.d.textContent !== detail) this.d.textContent = detail;
      const family = getComputedStyle(this.el).fontFamily;
      // plus the letter spacing the stylesheet adds, and a little to spare, so the words are never cut by a pixel or two
      this.textW = Math.max(measure(title, `600 14px ${family}`) + title.length * 0.1, measure(detail, `400 12px ${family}`)) + 3;
    }
    A.infoW.set(exp ? Math.min(Math.ceil(this.textW) + 24, this.room()) : 0, { dur: 320 }, now);
    A.infoA.set(exp ? 1 : 0, exp ? { dur: 320, delay: 80 } : { dur: 120 }, now);
    const problem = exp && (this.shown === 'noconn' || this.shown === 'restart');
    const t = problem ? th[this.shown] : th.normal;
    this.bg.set(problem ? t.bg : th.normal.bg, now);
    A.cloudA.set(exp ? 0.5 : 0.2, { dur: 300 }, now);
    const iw = A.infoW.get(now);
    this.info.style.width = iw + 'px'; this.in.style.opacity = String(A.infoA.get(now) * A.txt.get(now));
    const ink = problem ? t.ink : th.normal.ink, cs = this.el.style;
    cs.color = rgb(ink); cs.background = rgb(this.bg.get(now)); cs.borderColor = rgb(th.line);
    // The Manager icon: single colour, in the capsule's ink (the colour logo is the one beside the wordmark).
    this.shieldBase.setAttribute('fill', th.dark ? '#B3A9F2' : '#6A5BC2');
    const mono = 1;   // the capsule's icon is the single-colour shield: the capsule's ink (black on light, white on dark)
    this.shieldMono.style.opacity = String(mono); this.shieldCol.style.opacity = String(1 - mono);
    this.mgr.style.opacity = String(this.shown === 'noconn' && exp ? 0.9 : 1);
    // A ring turns round it while it works and fills when done; a badge says done or what is wrong.
    const working = exp && (face === 'connecting' || face === 'updating'), done = exp && face === 'done';
    A.ringA.set(working || done ? 1 : 0, { dur: 200 }, now); A.badgeA.set(exp && BADGE[face] ? 1 : 0, { dur: 260 }, now);
    const ringA = A.ringA.get(now), bA = A.badgeA.get(now), p = done ? FOSE(clamp((now - this.tickT) / 380, 0, 1)) : 0;
    let d = '';
    if (ringA > 0.01) {
      if (done) d = arc(20, 17, -90, Math.min(359.9, 360 * p));
      else { const sw = FOSE(triangle(now, 1400)), start = ((now % 1100) / 1100 * 360) * 0.8 - 90 + 95 * sw; d = arc(20, 17, start, 90 + 190 * sw); }
    }
    this.ringPath.setAttribute('d', d); this.ringPath.setAttribute('stroke', rgb(mix(th.primary, GREEN, p))); this.ringPath.style.opacity = String(ringA);
    this.badge.style.opacity = String(bA); this.badge.style.transform = `scale(${0.4 + 0.6 * FOSE(bA)})`;
    const bi = this.badge.firstChild;
    if (bi) { bi.style.transform = this.shown === 'restart' ? `rotate(${(now % 1100) / 1100 * 360}deg)` : ''; bi.style.opacity = this.shown === 'noconn' ? String(0.55 + 0.45 * FOSE(triangle(now, 900))) : '1'; }
    this.shieldCol.parentNode.style.transform = working ? `scale(${1 + 0.05 * FOSE(triangle(now, 1000))})` : '';
    // a thin progress line along the bottom while it works
    const showBar = exp && (face === 'updating' || face === 'connecting' || face === 'restart');
    this.pbar.style.display = showBar ? 'block' : 'none';
    if (showBar) { const qq = (now % 1500) / 1500, i = this.pbar.firstChild; i.style.width = '34%'; i.style.marginLeft = (-0.34 + qq * 1.34) * 100 + '%'; i.style.background = rgb(ink, 0.7); }
    // the cloud behind the capsule
    const w = this.el.getBoundingClientRect().width || 90, h = 48, dpr = globalThis.devicePixelRatio || 1;
    const cw = Math.max(1, Math.round(w * dpr)), ch = Math.round(h * dpr);
    if (this.cv.width !== cw || this.cv.height !== ch) { this.cv.width = cw; this.cv.height = ch; }
    drawCloud(this.c2, now, problem ? t.cloud : th.normal.cloud, dpr, w, h, Math.max(1, w / h * 1.25), 38);
    this.cv.style.opacity = String(A.cloudA.get(now));
    this.quiet = !exp && iw < 0.5 && bA < 0.01 && ringA < 0.01;
  }
}
