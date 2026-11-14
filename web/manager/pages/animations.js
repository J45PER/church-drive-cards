// Clive's animations, view only: his mark in every pose as the apps draw it (AssistantMark.kt, docs/CLIVE-STYLE.md), on a phone button, a watch face and a satellite,
// and the mark turning into a card's symbol. The motion numbers are the apps' own; this page only plays them.
import { ic, circ, palette, toneVars } from '../ui.js';

const rr = (x, y, w, h, r) => { r = Math.max(0, Math.min(r, w / 2, h / 2)); return `M${x + r} ${y}H${x + w - r}A${r} ${r} 0 0 1 ${x + w} ${y + r}V${y + h - r}A${r} ${r} 0 0 1 ${x + w - r} ${y + h}H${x + r}A${r} ${r} 0 0 1 ${x} ${y + h - r}V${y + r}A${r} ${r} 0 0 1 ${x + r} ${y}Z`; };
/** A rounded square scaled about its own centre, then moved by (dx, dy). */
const sq = (x, y, w, h, r, k = 1, dx = 0, dy = 0) => { const cx = x + w / 2 + dx, cy = y + h / 2 + dy, w2 = w * k, h2 = h * k; return rr(cx - w2 / 2, cy - h2 / 2, w2, h2, r * k); };
const wave = (p) => 0.5 - 0.5 * Math.cos(p * 2 * Math.PI);
const SHIFT = 11.3;

/** The mark's outline at time [t] (ms) in a pose, in a 200 unit box. Idle and Thinking are two squircles with the overlap cut out (fill-rule evenodd). */
export function markPath(pose, t) {
  if (pose === 'idle') { const g = 1 - 0.55 * (1 - wave((t % 4000) / 4000)); return sq(62, 62, 76, 76, 30, 1, -SHIFT * g, SHIFT * g) + sq(62, 62, 76, 76, 30, 1, SHIFT * g, -SHIFT * g); }
  if (pose === 'thinking') { const tr = Math.sin(((t % 1200) / 1200) * 2 * Math.PI); return sq(62, 62, 76, 76, 30, 1.025 + 0.175 * tr, -SHIFT, SHIFT) + sq(62, 62, 76, 76, 30, 1.025 - 0.175 * tr, SHIFT, -SHIFT); }
  if (pose === 'ready') { const b = 1 + 0.05 * (1 - wave((t % 3000) / 3000)); return sq(80, 40, 40, 72, 20, b) + sq(66, 130, 68, 16, 8, b); }
  if (pose === 'listening') return sq(70, 70, 60, 60, 18, 1 + 0.07 * wave((t % 1400) / 1400));
  const tr = Math.sin(((t % 700) / 700) * 2 * Math.PI);   // speaking: two equaliser bars trading height
  return rr(64, 100 - 48 * (1 + 0.25 * tr), 30, 96 * (1 + 0.25 * tr), 15) + rr(106, 100 - 48 * (1 - 0.25 * tr), 30, 96 * (1 - 0.25 * tr), 15);
}
/** The answer card's change: the mark fades and shrinks to 35% turning 25 degrees while the symbol grows in (450 ms), shown, then back. 0 is the mark, 1 the symbol. */
export function morphAt(t) { const ph = t % 3600, a = Math.min(1, Math.max(0, (ph - 1200) / 450)), b = ph > 2700 ? Math.min(1, Math.max(0, (ph - 2700) / 450)) : 0; return a * (1 - b); }

export const POSES = [
  ['idle', 'Idle', 'Two squircles', 'Waiting. The two squircles slide together and apart along the diagonal, 4 seconds a cycle, up to 55% closer.'],
  ['ready', 'Ready', 'Microphone', 'On the phone, waiting for the mic. A microphone that breathes 5%, 3 seconds a cycle.'],
  ['listening', 'Listening', 'Stop button', 'A stop button: one rounded square that pulses 7% every 1.4 seconds and swells with the voice.'],
  ['thinking', 'Thinking', 'Two squircles', 'The two squircles trade size, one growing 17.5% as the other shrinks, 1.2 seconds a cycle.'],
  ['speaking', 'Speaking', 'Equaliser bars', 'Two equaliser bars whose heights trade 25% either way, 0.7 seconds a cycle.'],
];

const frame = (kind, pose) => `<div class="cframe ${kind}"><svg viewBox="0 0 200 200" aria-hidden="true"><path data-pose="${pose}" fill="#fff" fill-rule="evenodd"/></svg></div>`;

export function animationsHtml(ctx) {
  const tone = toneVars(ctx, 'tasks');
  const card = ([pose, title, tag, text]) => `<article class="tcard glow" style="${tone}"><div class="wc-h"><h2>${title}</h2><span class="wc-p">${tag}</span></div><p class="sub">${text}</p>
    <div class="cdevs"><figure>${frame('phone', pose)}<figcaption>Phone</figcaption></figure><figure>${frame('watch', pose)}<figcaption>Watch</figcaption></figure><figure>${frame('sat', pose)}<figcaption>Satellite</figcaption></figure></div></article>`;
  const morph = `<article class="tcard glow" style="${tone}"><div class="wc-h"><h2>Answer card</h2><span class="wc-p">Mark to symbol</span></div><p class="sub">When he shows a card the mark fades and shrinks to 35% while turning 25 degrees, and the card's symbol grows in over 450 milliseconds. Shown here with the energy card.</p>
    <div class="cdevs"><figure><div class="cframe phone" data-morph="1"><svg viewBox="0 0 200 200" aria-hidden="true" class="m1"><path data-pose="idle" fill="#fff" fill-rule="evenodd"/></svg><span class="m2">${ic('bolt', 24)}</span></div><figcaption>Phone</figcaption></figure></div></article>`;
  const pill = (icon, text, key) => `<div class="hl"><span class="pi" style="background:${circ(ctx, key)}">${ic(icon, 18)}</span><span>${text}</span></div>`;
  return `<div class="stack"><div class="hls">${pill('animation-play', `<b>${POSES.length + 1}</b> animations`, 'tasks')}${pill('watch', 'Phone, watch and satellite', 'media')}${pill('eye', 'View only', 'ok')}</div>
    <div class="hdg anigrid">${POSES.map(card).join('')}${morph}</div></div>`;
}

/** Draws the page and plays the animations until the page is left (a cleanup is registered), still when the person asks the device for less motion. */
export function renderAnimations(el, ctx) {
  el.innerHTML = animationsHtml(ctx);
  const still = matchMedia('(prefers-reduced-motion: reduce)').matches;
  let live = true, raf = 0;
  const tick = (now) => {
    if (!live) return;
    const t = still ? 0 : now;
    el.querySelectorAll('[data-pose]').forEach((p) => p.setAttribute('d', markPath(p.dataset.pose, t)));
    el.querySelectorAll('[data-morph]').forEach((m) => { const k = morphAt(t), a = m.querySelector('.m1'), b = m.querySelector('.m2'); a.style.opacity = 1 - k; a.style.transform = `scale(${1 - 0.65 * k}) rotate(${25 * k}deg)`; b.style.opacity = k; b.style.transform = `scale(${0.35 + 0.65 * k}) rotate(${-25 * (1 - k)}deg)`; });
    if (!still) raf = requestAnimationFrame(tick);
  };
  raf = requestAnimationFrame(tick);
  (ctx.cleanups ||= []).push(() => { live = false; cancelAnimationFrame(raf); });
}
