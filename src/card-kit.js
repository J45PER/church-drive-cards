// Shared pieces for the device cards (fan, air purifier, carbon monoxide,
// blind, floor climate), so they look like the climate and alarm cards:
// a title in the status colour with a status word, an 84px arc gauge,
// grey 48px tiles where the selected one fills with its colour, and small
// 24-hour graphs read from Home Assistant's history.

import { iconHtml } from './icons.js';

export const KIT_COLOR = {
  off: '#8b919c',
  good: '#4caf50',
  fair: '#ffa726',
  poor: '#ff7043',
  bad: '#e53935',
  fan: '#26c6da',
  sleep: '#7e6fd6',
  humidity: '#b388ff',
  blind: '#a1887f',
  cold: '#42a5f5',
  cool: '#26c6da',
  comfy: '#66bb6a',
  warm: '#ffa726',
  hot: '#ef5350',
};

export const kitEsc = (text) =>
  String(text == null ? '' : text).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]);
export const kitCap = (text) => String(text || '').replace(/_/g, ' ').replace(/^./, (c) => c.toUpperCase());
export const kitNum = (st) => (st && st.state !== '' && !isNaN(Number(st.state)) ? Number(st.state) : null);

export const KIT_HEALTH_CSS = `.ck-stale .ck-row, .ck-stale .ck-dim { opacity:.55; }
.ck-health { display:none; align-items:center; gap:10px; padding:9px 11px; border-radius:12px; background:color-mix(in srgb, #ffa726 18%, transparent); color:#ffd08a; font-size:0.85rem; }
.ck-health small { display:block; color:var(--secondary-text-color); font-size:0.74rem; }
.ck-health button { flex:none; border:none; border-radius:10px; padding:7px 10px; font:inherit; font-size:0.8rem; font-weight:600; background:#ffa726; color:#2a1700; cursor:pointer; }`;

// The card shell: ha-card, shared styles, the title row, then `body`.
export function kitShell(body, extraCss = '') {
  return `
    <ha-card class="ck-card" style="border:none; box-shadow:0 3px 10px rgba(0,0,0,0.45); border-radius:16px; padding:16px; background:var(--card-background-color); transition:background-color .6s ease; display:flex; flex-direction:column; gap:12px;">
      <style>
        .ck-row { display:flex; gap:6px; }
        .ck-q { position:relative; overflow:hidden; container-type:inline-size; flex:1 1 0; min-width:0; height:48px; border:none; border-radius:12px; padding:0 6px; cursor:pointer;
          background:rgba(127,127,127,0.14); color:var(--primary-text-color); font:inherit; font-size:13px; font-weight:600;
          display:flex; align-items:center; justify-content:center; gap:6px; transition:background-color .2s, color .2s; }
        .ck-q.ck-col { flex-direction:column; gap:2px; height:56px; font-size:11px; }
        .ck-q.ck-on { color:#fff; }
        .ck-q:disabled { opacity:.4; cursor:default; }
        .ck-q span { white-space:nowrap; overflow:hidden; text-overflow:ellipsis; max-width:100%; }
        @container (max-width: 56px) { .ck-q:not(.ck-col) span.ck-hide { display:none; } }
        .ck-q::after { content:''; position:absolute; inset:0; background:#fff; opacity:0; transition:opacity .15s; pointer-events:none; }
        .ck-q:not(:disabled):hover::after { opacity:.08; }
        .ck-q:focus-visible, .ck-tap:focus-visible { outline:2px solid var(--primary-color); outline-offset:2px; }
        .ck-hold { position:absolute; left:0; top:0; bottom:0; width:0; background:rgba(255,255,255,.22); pointer-events:none; }
        .ck-sub { font-size:0.8rem; color:var(--secondary-text-color); }
        .ck-info { flex:1; min-width:0; display:flex; flex-direction:column; gap:4px; font-size:0.85rem; color:var(--secondary-text-color); }
        .ck-info > span { display:flex; align-items:center; gap:5px; }
        .ck-chip { display:inline-flex; align-items:center; gap:4px; padding:1px 8px; border-radius:999px; font-size:0.72rem; font-weight:600; }
        .ck-bar { height:8px; border-radius:99px; background:rgba(127,127,127,.2); overflow:hidden; }
        .ck-bar > i { display:block; height:100%; border-radius:inherit; transition:width .4s; }
        .ck-tap { cursor:pointer; }
        ${KIT_HEALTH_CSS}
        ${extraCss}
      </style>
      <div style="display:flex; align-items:baseline; gap:8px;">
        <div class="ck-title" style="flex:1; min-width:0; font-size:1.5rem; font-weight:500; line-height:1.2; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; transition:color .6s;"></div>
        <div class="ck-word" style="flex:none; font-size:0.85rem; color:var(--secondary-text-color);"></div>
      </div>
      <div class="ck-health" role="status"></div>
      ${body}
    </ha-card>`;
}

// Title, status word and card tint (percent of the colour mixed in).
export function kitHead(root, title, word, color, tint = 0) {
  const t = root.querySelector('.ck-title');
  const w = root.querySelector('.ck-word');
  const card = root.querySelector('.ck-card');
  t.textContent = title;
  t.style.color = color;
  w.textContent = word;
  card.style.backgroundColor = tint ? `color-mix(in srgb, ${color} ${tint}%, var(--card-background-color))` : 'var(--card-background-color)';
}

// A 270° arc filled to `p` (0..1) with a label in the middle.
export function kitGauge(p, color, label, sub, size = 84) {
  const r = size / 2 - 7, cx = size / 2, len = 1.5 * Math.PI * r;
  const fill = Math.max(0, Math.min(1, p || 0));
  const arc = (extra) => `<circle cx="${cx}" cy="${cx}" r="${r}" fill="none" stroke-width="6" stroke-linecap="round" transform="rotate(135 ${cx} ${cx})" ${extra}></circle>`;
  return `<div style="position:relative; width:${size}px; height:${size}px; flex:none;">
      <svg viewBox="0 0 ${size} ${size}" width="${size}" height="${size}" aria-hidden="true" style="display:block;">
        ${arc(`stroke="rgba(127,127,127,0.28)" stroke-dasharray="${len} 9999"`)}
        ${fill > 0 ? arc(`stroke="${color}" stroke-dasharray="${Math.max(0.01, len * fill)} 9999"`) : ''}
      </svg>
      <div style="position:absolute; inset:0; display:flex; flex-direction:column; align-items:center; justify-content:center; text-align:center;">
        <b style="font-size:1.1rem; font-weight:700; font-variant-numeric:tabular-nums; line-height:1.1;">${kitEsc(label)}</b>
        <span style="font-size:0.66rem; color:var(--secondary-text-color); line-height:1.2;">${kitEsc(sub)}</span>
      </div>
    </div>`;
}

// A row of tiles. `list` = [{ key, name, icon, color, on, disabled, hold }].
// Redraws only when something changed. `hold` tiles need a 1.5s press.
export function kitTiles(box, list, onTap, { column = false, hideNames = false } = {}) {
  const sig = JSON.stringify(list.map((t) => [t.key, t.name, t.icon, t.color, !!t.on, !!t.disabled, !!t.hold]));
  if (box._ckSig === sig) return;
  box._ckSig = sig;
  box.innerHTML = '';
  box.style.display = list.length ? 'flex' : 'none';
  list.forEach((t) => {
    const b = document.createElement('button');
    b.className = `ck-q${column ? ' ck-col' : ''}${t.on ? ' ck-on' : ''}`;
    b.disabled = !!t.disabled;
    b.title = t.name;
    b.setAttribute('aria-label', b.title);
    b.setAttribute('aria-pressed', String(!!t.on));
    if (t.on) b.style.background = t.color;
    b.innerHTML = `${t.hold ? '<i class="ck-hold"></i>' : ''}${iconHtml(t.icon, { size: '20px', style: 'flex-shrink:0; position:relative;' })}<span class="${hideNames ? 'ck-hide' : ''}" style="position:relative;"></span>`;
    b.querySelector('span').textContent = t.name;
    if (t.hold) kitHold(b, () => onTap(t));
    else b.addEventListener('click', () => onTap(t));
    box.appendChild(b);
  });
}

// Press and hold (1.5s) before `done` runs; the tile fills while held.
function kitHold(button, done) {
  const bar = button.querySelector('.ck-hold');
  let timer = null;
  const stop = () => {
    clearTimeout(timer);
    timer = null;
    bar.style.transition = 'width .2s';
    bar.style.width = '0';
  };
  const start = (ev) => {
    if (ev.button > 0) return;
    stop();
    bar.style.transition = 'width 1.5s linear';
    requestAnimationFrame(() => (bar.style.width = '100%'));
    timer = setTimeout(() => {
      stop();
      done();
    }, 1500);
  };
  button.addEventListener('pointerdown', start);
  ['pointerup', 'pointerleave', 'pointercancel'].forEach((e) => button.addEventListener(e, stop));
  button.addEventListener('keydown', (ev) => {
    if ((ev.key === 'Enter' || ev.key === ' ') && !timer) start(ev);
  });
  button.addEventListener('keyup', stop);
}

export function kitMoreInfo(el, entityId) {
  if (!entityId) return;
  el.dispatchEvent(new CustomEvent('hass-more-info', { detail: { entityId }, bubbles: true, composed: true }));
}

// Numeric history of several entities' states: { id: [[ms, value], ...] }.
export async function kitHistory(hass, ids, hours = 24) {
  const out = {};
  if (!hass || !hass.callWS || !ids.length) return out;
  const res = await hass.callWS({
    type: 'history/history_during_period',
    start_time: new Date(Date.now() - hours * 3600e3).toISOString(),
    entity_ids: ids,
    minimal_response: true,
    no_attributes: true,
    significant_changes_only: false,
  });
  ids.forEach((id) => {
    out[id] = (res[id] || [])
      .map((p) => [(p.lu || p.lc || 0) * 1000, Number(p.s)])
      .filter((p) => p[0] && !isNaN(p[1]) && p[1] !== null);
  });
  return out;
}

// Raw state history (strings, e.g. on/off or an event's timestamp):
// { id: [[ms, state], ...] }, oldest first.
export async function kitStateHistory(hass, ids, hours = 24) {
  const out = {};
  if (!hass || !hass.callWS || !ids.length) return out;
  const res = await hass.callWS({
    type: 'history/history_during_period',
    start_time: new Date(Date.now() - hours * 3600e3).toISOString(),
    entity_ids: ids,
    minimal_response: true,
    no_attributes: true,
    significant_changes_only: false,
  });
  ids.forEach((id) => {
    out[id] = (res[id] || []).map((p) => [(p.lc || p.lu || 0) * 1000, p.s]).filter((p) => p[0]);
  });
  return out;
}

// Demo history: `values` spread evenly over the last `hours`.
export function kitDemoSeries(values, hours = 24) {
  const now = Date.now();
  return values.map((v, i) => [now - (hours * 3600e3 * (values.length - 1 - i)) / (values.length - 1), v]);
}

// Smoothing for jumpy sensors (0.5° steps, whole percents): the readings are
// averaged over 15-minute slots (each reading counts for as long as it held),
// then softened with a moving average over about 1¾ hours. The line is then drawn as a curve.
export function kitSmooth(pts, from, now, slots = 96) {
  const sorted = (pts || []).filter((p) => p[1] != null && !isNaN(p[1])).sort((a, b) => a[0] - b[0]);
  if (sorted.length < 3) return sorted;
  const step = (now - from) / slots;
  let j = 0, v = null;
  while (j < sorted.length && sorted[j][0] <= from) v = sorted[j++][1];
  const avg = [];
  for (let k = 0; k < slots; k++) {
    const a = from + k * step, b = a + step;
    let sum = 0, dur = 0, t = a;
    while (j < sorted.length && sorted[j][0] < b) {
      const tp = sorted[j][0];
      if (v != null) { sum += v * (tp - t); dur += tp - t; }
      t = tp;
      v = sorted[j++][1];
    }
    if (v != null) { sum += v * (b - t); dur += b - t; }
    if (dur > 0) avg.push([a + step / 2, sum / dur]);
  }
  const w = [1, 2, 3, 4, 3, 2, 1];
  const out = avg.map((p, i) => {
    let s = 0, n = 0;
    w.forEach((wt, k) => {
      const q = avg[i + k - 3];
      if (q) { s += q[1] * wt; n += wt; }
    });
    return [p[0], s / n];
  });
  if (out.length) out.push([now, out[out.length - 1][1]]);
  return out;
}

// SVG path through [x, y] points: a smooth curve when `curve`, else straight.
export function kitPath(xy, curve = true) {
  const f = (p) => `${p[0].toFixed(1)},${p[1].toFixed(1)}`;
  if (!curve || xy.length < 3) return xy.map((p, i) => `${i ? 'L' : 'M'}${f(p)}`).join(' ');
  let d = `M${f(xy[0])}`;
  for (let i = 0; i < xy.length - 1; i++) {
    const p0 = xy[i - 1] || xy[i], p1 = xy[i], p2 = xy[i + 1], p3 = xy[i + 2] || p2;
    const c1 = [p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6];
    const c2 = [p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6];
    d += ` C${f(c1)} ${f(c2)} ${f(p2)}`;
  }
  return d;
}

// An SVG graph of one or two series over the last `hours`, each on its own
// scale: [{ pts, color, fill, pad }]. The first is drawn filled if `fill`.
export function kitGraph(series, { hours = 24, height = 48, label = '', meta = null, smooth = true } = {}) {
  const W = 300, H = height, now = Date.now(), from = now - hours * 3600e3;
  const x = (t) => ((Math.max(from, t) - from) / (now - from)) * W;
  let under = '', over = '';
  const scrub = [];
  series.forEach((s) => {
    const raw = (s.pts || []).filter((p) => p[1] != null && !isNaN(p[1]));
    if (s.current != null && !isNaN(s.current)) raw.push([now, Number(s.current)]);
    const pts = smooth ? kitSmooth(raw, from, now) : raw;
    if (pts.length < 2) return;
    const vals = pts.map((p) => p[1]);
    const lo = Math.min(...vals) - (s.pad || 0.3), hi = Math.max(...vals) + (s.pad || 0.3);
    const y = (v) => H - 3 - ((v - lo) / (hi - lo || 1)) * (H - 6);
    const d = kitPath(pts.map((p) => [x(p[0]), y(p[1])]), smooth);
    if (s.fill) under += `<path d="${d} L${W},${H} L0,${H} Z" fill="${s.color}" fill-opacity="0.16"></path>`;
    over += `<path d="${d}" fill="none" stroke="${s.color}" stroke-width="${s.width || 2}" vector-effect="non-scaling-stroke"></path>`;
    scrub.push({ pts, raw, lo, hi, color: s.color, format: s.format, linear: smooth });
  });
  if (!under && !over) return '';
  if (meta) Object.assign(meta, { from, now, height: H, series: scrub });
  return `<svg viewBox="0 0 ${W} ${H}" preserveAspectRatio="none" style="display:block; width:100%; height:${H}px;" role="img" aria-label="${kitEsc(label)}">${under}${over}</svg>`;
}

// Press and hold a graph (or hover with a mouse) to read a point in time, as
// on Home Assistant's own history graphs: a line, a dot on each series and a
// label with the time and values. Drag to move; let go to hide.
// `spec` = { from, now, height, series: [{ pts, lo, hi, color, format(v) }] },
// the same scales the graph was drawn with.
export function kitScrub(svg, spec) {
  if (!svg || !spec || !spec.series || !spec.series.length || svg.parentNode._ckScrub) return;
  const H = spec.height;
  const wrap = document.createElement('div');
  wrap._ckScrub = true;
  wrap.style.cssText = 'position:relative; touch-action:pan-y; user-select:none; -webkit-user-select:none; -webkit-touch-callout:none;';
  svg.parentNode.insertBefore(wrap, svg);
  wrap.appendChild(svg);
  const line = document.createElement('div');
  line.style.cssText = `position:absolute; top:0; height:${H}px; width:1px; background:var(--primary-text-color); opacity:.6; pointer-events:none; display:none;`;
  const tip = document.createElement('div');
  tip.style.cssText = 'position:absolute; bottom:calc(100% + 6px); z-index:3; padding:6px 9px; border-radius:10px; background:var(--card-background-color); box-shadow:0 3px 10px rgba(0,0,0,.45); font-size:0.78rem; line-height:1.4; white-space:nowrap; pointer-events:none; display:none; font-variant-numeric:tabular-nums;';
  const dots = spec.series.map((s) => {
    const d = document.createElement('div');
    d.style.cssText = `position:absolute; width:9px; height:9px; margin:-4.5px 0 0 -4.5px; border-radius:50%; background:${s.color}; box-shadow:0 0 0 2px var(--card-background-color); pointer-events:none; display:none;`;
    return d;
  });
  wrap.append(line, ...dots, tip);
  const lerp = (pts, t) => {
    if (!pts.length) return null;
    if (t <= pts[0][0]) return pts[0][1];
    for (let k = 1; k < pts.length; k++) {
      if (t <= pts[k][0]) {
        const [a, va] = pts[k - 1], [b, vb] = pts[k];
        return va + ((vb - va) * (t - a)) / (b - a || 1);
      }
    }
    return pts[pts.length - 1][1];
  };
  const valueAt = (pts, t) => {
    let v = null;
    for (const p of pts) {
      if (p[0] <= t) v = p[1];
      else break;
    }
    return v == null && pts.length ? pts[0][1] : v;
  };
  const when = (t) => {
    const d = new Date(t), today = new Date();
    const time = d.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
    return d.toDateString() === today.toDateString() ? time : `${d.toLocaleDateString(undefined, { weekday: 'short' })} ${time}`;
  };
  const show = (clientX) => {
    const rect = wrap.getBoundingClientRect();
    const f = Math.max(0, Math.min(1, (clientX - rect.left) / (rect.width || 1)));
    const t = spec.from + f * (spec.now - spec.from);
    const left = f * rect.width;
    line.style.left = `${left}px`;
    line.style.display = 'block';
    const rows = [];
    spec.series.forEach((s, i) => {
      const v = s.raw ? valueAt(s.raw, t) : valueAt(s.pts, t);
      const yv = s.linear ? lerp(s.pts, t) : v;
      const dot = dots[i];
      if (v == null || yv == null) {
        dot.style.display = 'none';
        return;
      }
      const colour = s.colourOf ? s.colourOf(v) : s.color;
      dot.style.background = colour;
      dot.style.left = `${left}px`;
      dot.style.top = `${H - 3 - ((yv - s.lo) / (s.hi - s.lo || 1)) * (H - 6)}px`;
      dot.style.display = 'block';
      rows.push(`<div style="color:${colour};">● ${kitEsc(s.format ? s.format(v) : Number(v).toFixed(1))}</div>`);
    });
    tip.innerHTML = `<div style="color:var(--secondary-text-color);">${when(t)}</div>${rows.join('')}`;
    tip.style.display = 'block';
    const w = tip.offsetWidth;
    tip.style.left = `${Math.max(0, Math.min(rect.width - w, left - w / 2))}px`;
  };
  const hide = () => {
    [line, tip, ...dots].forEach((el) => (el.style.display = 'none'));
  };
  let active = false, used = false, timer = null, sx = 0, sy = 0;
  wrap.addEventListener('pointerenter', (ev) => ev.pointerType === 'mouse' && show(ev.clientX));
  wrap.addEventListener('pointermove', (ev) => {
    if (ev.pointerType === 'mouse') return show(ev.clientX);
    if (active) return show(ev.clientX);
    if (timer && (Math.abs(ev.clientX - sx) > 10 || Math.abs(ev.clientY - sy) > 10)) {
      clearTimeout(timer);
      timer = null;
    }
  });
  wrap.addEventListener('pointerleave', (ev) => ev.pointerType === 'mouse' && hide());
  wrap.addEventListener('pointerdown', (ev) => {
    if (ev.pointerType === 'mouse') return;
    sx = ev.clientX;
    sy = ev.clientY;
    clearTimeout(timer);
    timer = setTimeout(() => {
      timer = null;
      active = true;
      used = true;
      show(sx);
    }, 300);
  });
  const end = () => {
    clearTimeout(timer);
    timer = null;
    if (active) {
      active = false;
      hide();
    }
  };
  ['pointerup', 'pointercancel'].forEach((e) => wrap.addEventListener(e, end));
  wrap.addEventListener('touchmove', (ev) => {
    if (active && ev.cancelable) ev.preventDefault();
    if (active && ev.touches[0]) show(ev.touches[0].clientX);
  }, { passive: false });
  wrap.addEventListener('touchend', end);
  wrap.addEventListener('contextmenu', (ev) => (active || used) && ev.preventDefault());
  // A hold isn't a tap: don't let it open the card's pop-up.
  wrap.addEventListener('click', (ev) => {
    if (used) {
      used = false;
      ev.stopPropagation();
      ev.preventDefault();
    }
  }, true);
}

// Range text for a series, e.g. "18.5–21.4°".
export function kitRange(pts, current, digits, unit) {
  const vals = (pts || []).map((p) => p[1]).filter((v) => v != null && !isNaN(v));
  if (current != null && !isNaN(current)) vals.push(Number(current));
  if (!vals.length) return '';
  const f = (v) => Number(v).toFixed(digits);
  return `${f(Math.min(...vals))}–${f(Math.max(...vals))}${unit}`;
}

// A card that reads history keeps it fresh every 10 minutes.
export class KitHistory {
  constructor(owner, ids, hours, loader = kitHistory) {
    this.owner = owner;
    this.ids = ids;
    this.hours = hours;
    this.loader = loader;
    this.data = null;
    this.at = 0;
    this.loading = false;
  }

  due() {
    return !this.loading && Date.now() - this.at > 10 * 60e3;
  }

  async load(hass) {
    if (!this.due()) return;
    this.loading = true;
    try {
      this.data = await this.loader(hass, this.ids.filter(Boolean), this.hours);
    } catch (err) {
      this.data = this.data || {};
    }
    this.at = Date.now();
    this.loading = false;
    this.owner._render();
  }
}

// What the user just asked for, shown straight away and held until the device
// reports it (or 8 seconds pass). Some devices report in steps, e.g. the
// Philips fan says "on" before its speed, which would flicker otherwise.
// `want` = { state, attrs }; numbers match within 2 (percentages round).
export class KitPending {
  constructor(owner) {
    this.owner = owner;
    this.want = null;
  }

  set(want) {
    this.want = want;
    clearTimeout(this.timer);
    this.timer = setTimeout(() => {
      this.want = null;
      this.owner._render();
    }, 8000);
  }

  apply(st) {
    const w = this.want;
    if (!w || !st) return st;
    const a = st.attributes || {};
    const same = (x, y) => (typeof x === 'number' && typeof y === 'number' ? Math.abs(x - y) <= 2 : x === y);
    const attrs = w.attrs || {};
    if (st.state === w.state && Object.keys(attrs).every((k) => same(a[k], attrs[k]))) {
      this.want = null;
      clearTimeout(this.timer);
      return st;
    }
    return { ...st, state: w.state, attributes: { ...a, ...attrs } };
  }
}

// ---- Device health (see the integration's health.py): a banner on a card
// whose device looks stale, from sensor.church_drive_device_health.
export const HEALTH_SENSOR = 'sensor.church_drive_device_health';

export function kitHealthOf(hass, entityId) {
  const s = hass && entityId && hass.states[HEALTH_SENSOR];
  const d = s && s.attributes.devices && s.attributes.devices[entityId];
  return d && d.status !== 'ok' ? d : null;
}

const kitClock = (iso) => (iso ? new Date(iso).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' }) : '');

// "Speed 1 at 19:11", "Heat 21° at 07:30", "Off at 22:05".
export function kitRealText(real) {
  if (!real) return '';
  let what = kitCap(real.state);
  if (real.preset_mode) {
    const m = /^speed[ _-]?(\d+)$/i.exec(real.preset_mode);
    what = m ? `Speed ${m[1]}` : kitCap(real.preset_mode);
  } else if (real.temperature != null && real.state !== 'off') {
    what = `${kitCap(real.state)} ${real.temperature}°`;
  }
  return `${what} at ${kitClock(real.at)}`;
}

export function kitHealthBanner(root, hass, entityId, demo) {
  const box = root.querySelector('.ck-health');
  const card = root.querySelector('.ck-card') || root.querySelector('ha-card');
  if (!box) return null;
  const d = demo ? null : kitHealthOf(hass, entityId);
  card.classList.toggle('ck-stale', !!d);
  const sig = d ? JSON.stringify([d.reason, d.since, d.last_real, d.fixes]) : '';
  if (box._sig === sig) return d;
  box._sig = sig;
  box.style.display = d ? 'flex' : 'none';
  if (!d) {
    box.innerHTML = '';
    return null;
  }
  const last = (d.fixes || []).slice(-1)[0];
  box.innerHTML = `${iconHtml('mdi:lan-disconnect', { size: '22px', style: 'flex:none;' })}
    <div style="flex:1; min-width:0; line-height:1.35;">Not responding since ${kitClock(d.since)}
      <small></small></div>
    <button type="button">Fix now</button>`;
  box.querySelector('small').textContent = [d.reason, d.last_real ? `Last real: ${kitRealText(d.last_real)}` : '', last ? `Fixing: ${last.replace(/^\d\d:\d\d /, '')}` : ''].filter(Boolean).join(' · ');
  box.querySelector('button').addEventListener('click', () => hass.callService('church_drive', 'health_fix', { entity_id: entityId, action: entityId.startsWith('fan.') ? 'nudge' : 'resync' }));
  return d;
}
