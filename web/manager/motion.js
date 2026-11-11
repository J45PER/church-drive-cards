// Motion and colour helpers shared by the status capsule: Compose's tween and spring (as the apps use them) and small colour functions.

export const TAU = Math.PI * 2;
export const clamp = (v, a, b) => Math.min(b, Math.max(a, v));
export const lerp = (a, b, t) => a + (b - a) * t;

const bez = (x1, y1, x2, y2) => (t) => {
  if (t <= 0) return 0; if (t >= 1) return 1;
  let lo = 0, hi = 1, x;
  for (let i = 0; i < 28; i++) { x = (lo + hi) / 2; const cx = 3 * x1 * (1 - x) * (1 - x) * x + 3 * x2 * (1 - x) * x * x + x * x * x; if (cx < t) lo = x; else hi = x; }
  const s = (lo + hi) / 2; return 3 * y1 * (1 - s) * (1 - s) * s + 3 * y2 * (1 - s) * s * s + s * s * s;
};
/** Material's FastOutSlowIn, the default easing of tween(). */
export const FOSE = bez(0.4, 0, 0.2, 1);
/** 0 to 1 and back again, once per two [period]s (a reversing repeat). */
export const triangle = (t, period) => { const q = (t / period) % 2; return q < 1 ? q : 2 - q; };

/** One animated value: set(target, spec) starts from wherever it is now. spec: {dur, delay?} (a tween) or {spring: omega}. */
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

export const hex = (h) => { const n = parseInt(h.slice(1), 16); return [n >> 16 & 255, n >> 8 & 255, n & 255]; };
export const rgb = (c, a = 1) => `rgba(${c[0] | 0},${c[1] | 0},${c[2] | 0},${a})`;
export const mix = (a, b, t) => a.map((v, i) => v + (b[i] - v) * t);

/** A colour that moves to a new colour over 300 ms. */
export class ColorAnim {
  constructor(c) { this.from = c; this.to = c; this.t0 = 0; }
  set(c, now) { if (c.join() === this.to.join()) return; this.from = this.get(now); this.to = c; this.t0 = now; }
  get(now) { return mix(this.from, this.to, FOSE(clamp((now - this.t0) / 300, 0, 1))); }
}
