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
        ${extraCss}
      </style>
      <div style="display:flex; align-items:baseline; gap:8px;">
        <div class="ck-title" style="flex:1; min-width:0; font-size:1.5rem; font-weight:500; line-height:1.2; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; transition:color .6s;"></div>
        <div class="ck-word" style="flex:none; font-size:0.85rem; color:var(--secondary-text-color);"></div>
      </div>
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

// Demo history: `values` spread evenly over the last `hours`.
export function kitDemoSeries(values, hours = 24) {
  const now = Date.now();
  return values.map((v, i) => [now - (hours * 3600e3 * (values.length - 1 - i)) / (values.length - 1), v]);
}

// An SVG graph of one or two series over the last `hours`, each on its own
// scale: [{ pts, color, fill, pad }]. The first is drawn filled if `fill`.
export function kitGraph(series, { hours = 24, height = 48, label = '' } = {}) {
  const W = 300, H = height, now = Date.now(), from = now - hours * 3600e3;
  const x = (t) => ((Math.max(from, t) - from) / (now - from)) * W;
  let under = '', over = '';
  series.forEach((s) => {
    const pts = (s.pts || []).filter((p) => p[1] != null && !isNaN(p[1]));
    if (s.current != null && !isNaN(s.current)) pts.push([now, Number(s.current)]);
    if (pts.length < 2) return;
    const vals = pts.map((p) => p[1]);
    const lo = Math.min(...vals) - (s.pad || 0.3), hi = Math.max(...vals) + (s.pad || 0.3);
    const y = (v) => H - 3 - ((v - lo) / (hi - lo || 1)) * (H - 6);
    const d = pts.map((p, i) => `${i ? 'L' : 'M'}${x(p[0]).toFixed(1)},${y(p[1]).toFixed(1)}`).join(' ');
    if (s.fill) under += `<path d="${d} L${W},${H} L0,${H} Z" fill="${s.color}" fill-opacity="0.16"></path>`;
    over += `<path d="${d}" fill="none" stroke="${s.color}" stroke-width="${s.width || 2}" vector-effect="non-scaling-stroke"></path>`;
  });
  if (!under && !over) return '';
  return `<svg viewBox="0 0 ${W} ${H}" preserveAspectRatio="none" style="display:block; width:100%; height:${H}px;" role="img" aria-label="${kitEsc(label)}">${under}${over}</svg>`;
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
  constructor(owner, ids, hours) {
    this.owner = owner;
    this.ids = ids;
    this.hours = hours;
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
      this.data = await kitHistory(hass, this.ids.filter(Boolean), this.hours);
    } catch (err) {
      this.data = this.data || {};
    }
    this.at = Date.now();
    this.loading = false;
    this.owner._render();
  }
}
