// Camera Card: one camera's freshest picture, with its name and how old the
// picture is and why it was taken ("Doorbell · 4 min"). Tap it for a pop-up
// with live video, a new snapshot, the light by the camera, Talk (when the
// camera has a two-way audio stream) and recent events.
//
// Ring cameras have two pictures: the Ring integration's (a frame from the
// last recording, often hours old) and ring-mqtt's snapshot (taken on motion,
// a ring, or when asked). The card shows whichever is newer. When a picture
// is older than `refresh_after` minutes and someone is looking, it presses
// the camera's "Take Snapshot" button, but only once per `refresh_after` for
// everyone: the button remembers when it was last pressed, so battery
// cameras aren't woken over and over.
//
// The other entities are found from the camera's name: camera.front_door_live_view
// → camera.front_door_snapshot, button.front_door_take_snapshot,
// sensor.front_door_battery, event.front_door_ding / _motion.

import { createFormEditor } from './form-editor.js';
import { iconHtml, hydrateIcons } from './icons.js';
import { SUFFIX, LABEL } from './suffix.js';
import { kitEsc, kitStateHistory } from './card-kit.js';
import { openPopup } from './popup.js';

const KINDS = {
  ding: ['Doorbell', '#29b6f6', '#012'],
  motion: ['Motion', '#5c6bc0', '#fff'],
  interval: ['Snapshot', 'rgba(255,255,255,0.22)', '#fff'],
  'on-demand': ['Snapshot', 'rgba(255,255,255,0.22)', '#fff'],
};
const OLD = ['#ffa726', '#221'];

// When each camera last asked for a snapshot from this browser, so two cards
// for one camera don't both ask.
const asked = new Map();

const CAM_CSS = `
  .cc-tile { position:relative; display:block; width:100%; aspect-ratio:16/9; border-radius:var(--ha-card-border-radius, 14px); overflow:hidden; cursor:pointer;
    background:linear-gradient(160deg, #5d6b7d, #2f3946 60%, #46503c); box-shadow:0 3px 10px rgba(0,0,0,0.45); }
  .cc-tile img { position:absolute; inset:0; width:100%; height:100%; object-fit:cover; transition:opacity .4s ease; }
  .cc-ov { position:absolute; left:0; right:0; bottom:0; padding:22px 12px 10px; background:linear-gradient(transparent, rgba(0,0,0,0.72)); display:flex; align-items:flex-end; gap:8px; color:#fff; }
  .cc-name { font-weight:700; font-size:0.98rem; min-width:0; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; text-shadow:0 1px 3px rgba(0,0,0,0.6); }
  .cc-chip { flex:none; font-size:0.7rem; font-weight:700; border-radius:999px; padding:2px 8px; white-space:nowrap; }
  .cc-tr { position:absolute; top:8px; right:8px; display:flex; gap:6px; }
  .cc-ib { width:28px; height:28px; border-radius:50%; background:rgba(0,0,0,0.45); color:#fff; display:flex; align-items:center; justify-content:center; }
  .cc-pop-media { position:relative; border-radius:14px; overflow:hidden; background:#000; aspect-ratio:16/9; touch-action:none; cursor:grab; user-select:none; }
  .cc-stage { position:absolute; left:0; top:0; transform-origin:0 0; will-change:transform; }
  .cc-stage > * { display:block; width:100%; height:100%; pointer-events:none; }
  .cc-stage img { object-fit:cover; }
  .cc-mute { position:absolute; top:8px; right:8px; border:none; cursor:pointer; z-index:1; }
  .cc-pop-sub { font-size:0.8rem; color:var(--secondary-text-color); margin:-2px 0 10px; }
  .cc-btns { display:flex; gap:10px; margin:14px 0 6px; }
  .cc-btn { flex:1 1 0; min-width:0; border:none; border-radius:14px; padding:10px 4px; cursor:pointer; font:inherit; font-size:0.78rem; font-weight:700;
    background:rgba(127,127,127,0.16); color:var(--primary-text-color); display:flex; flex-direction:column; align-items:center; gap:4px; }
  .cc-btn.talk { background:#43a047; color:#fff; }
  .cc-btn.talk.on { background:#e53935; }
  .cc-btn[disabled] { opacity:.5; cursor:default; }
  .cc-ev { display:flex; align-items:center; gap:10px; padding:8px 2px; font-size:0.86rem; }
  .cc-ev + .cc-ev { border-top:1px solid var(--divider-color, rgba(127,127,127,0.18)); }
  .cc-ev-ico { flex:none; width:32px; height:32px; border-radius:10px; display:flex; align-items:center; justify-content:center; }
  .cc-ev-when { color:var(--secondary-text-color); font-size:0.78rem; }
`;

const pad = (n) => String(n).padStart(2, '0');

// "just now", "4 min", "3 h", or "Mon 21:40".
export function camAge(ms, now = Date.now()) {
  const s = Math.max(0, (now - ms) / 1000);
  if (s < 60) return 'just now';
  if (s < 3600) return `${Math.floor(s / 60)} min`;
  if (s < 86400) return `${Math.floor(s / 3600)} h`;
  const d = new Date(ms);
  return `${d.toLocaleDateString('en-GB', { weekday: 'short' })} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function when(ms, now = new Date()) {
  const d = new Date(ms);
  const day = new Date(d.getFullYear(), d.getMonth(), d.getDate());
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const diff = Math.round((today - day) / 86400000);
  const t = `${pad(d.getHours())}:${pad(d.getMinutes())}`;
  if (diff === 0) return `${t} today`;
  if (diff === 1) return `Yesterday ${t}`;
  return `${d.toLocaleDateString('en-GB', { weekday: 'short', day: 'numeric', month: 'short' })} ${t}`;
}

// The entities that go with a camera.
export function camFind(hass, entity, c = {}) {
  const s = (hass && hass.states) || {};
  const has = (id) => (id && s[id] ? id : null);
  const base = String(entity || '').replace(/^camera\./, '').replace(/_(live_view|snapshot|last_recording)$/, '');
  let light = has(c.light);
  if (!light && c.light !== 'none' && hass && hass.entities && hass.devices) {
    // The one light in the camera's area, if there's exactly one.
    const e = hass.entities[entity];
    const area = e && (e.area_id || (hass.devices[e.device_id] || {}).area_id);
    const lights = area ? Object.keys(s).filter((id) => id.startsWith('light.') && hass.entities[id] && (hass.entities[id].area_id || (hass.devices[hass.entities[id].device_id] || {}).area_id) === area) : [];
    if (lights.length === 1) light = lights[0];
  }
  return {
    live: has(entity),
    snap: has(c.snapshot) || has(`camera.${base}_snapshot`),
    button: has(c.snapshot_button) || has(`button.${base}_take_snapshot`),
    battery: has(`sensor.${base}_battery`),
    ding: has(`event.${base}_ding`),
    motion: has(`event.${base}_motion`),
    light,
  };
}

// The newest picture: { id, url, ms, kind }.
export function camPicture(hass, f) {
  const s = hass.states;
  const out = [];
  if (f.snap) {
    const a = s[f.snap].attributes;
    const ms = Number(a.timestamp) * 1000 || Date.parse(s[f.snap].last_changed);
    out.push({ id: f.snap, url: a.entity_picture, ms, kind: a.type || 'interval' });
  }
  if (f.live) {
    const base = f.live.replace(/^camera\./, '').replace(/_live_view$/, '');
    const act = s[`sensor.${base}_last_activity`];
    const ms = act ? Date.parse(act.state) : NaN;
    const kind = act && act.attributes.category === 'ding' ? 'ding' : 'motion';
    out.push({ id: f.live, url: s[f.live].attributes.entity_picture, ms: isNaN(ms) ? 0 : ms, kind });
  }
  out.sort((a, b) => b.ms - a.ms);
  return out[0] || null;
}

export const CameraCardEditor = createFormEditor({
  schema: () => [
    { name: 'entity', selector: { entity: { domain: 'camera' } } },
    { name: 'name', selector: { text: {} } },
    { name: 'light', selector: { entity: { domain: 'light' } } },
    { name: 'refresh_after', selector: { number: { min: 10, max: 1440, step: 5, mode: 'box', unit_of_measurement: 'min' } } },
    { name: 'talk_stream', selector: { text: {} } },
    { name: 'demo', selector: { boolean: {} } },
  ],
  labels: {
    entity: 'Camera',
    name: 'Name (optional)',
    light: 'Light by the camera (optional)',
    refresh_after: 'Ask for a new snapshot when the picture is older than',
    talk_stream: 'Two-way audio stream (optional, go2rtc stream name)',
    demo: 'Show a pretend camera (for Design Presets)',
  },
  helpers: {
    entity: "Its snapshot, Take Snapshot button, battery and doorbell/motion events are found from the camera's name.",
    light: 'Empty: the one light in the same area, if there is exactly one.',
    refresh_after: 'Default 60. Only while someone is looking, and at most once per this time for everyone, so battery cameras last.',
    talk_stream: 'A go2rtc stream with two-way audio (e.g. front_door). Adds a Talk button to the pop-up.',
  },
});

export class CameraCard extends HTMLElement {
  setConfig(config) {
    if (!config || (!config.entity && !config.demo)) throw new Error('Choose a camera');
    this.config = config;
    this._built = false;
    this._url = null;
  }

  set hass(hass) {
    this._hass = hass;
    this._render();
    if (this._pop) this._renderPop();
  }

  connectedCallback() {
    if (!this._io && window.IntersectionObserver) {
      this._io = new IntersectionObserver((list) => {
        this._seen = list.some((e) => e.isIntersecting);
        this._maybeRefresh();
      });
      this._io.observe(this);
    }
    clearInterval(this._tick);
    this._tick = setInterval(() => this._render(true), 30000);
  }

  disconnectedCallback() {
    clearInterval(this._tick);
  }

  _name() {
    const c = this.config;
    if (c.name) return c.name;
    const st = this._hass && this._hass.states[c.entity];
    return String((st && st.attributes.friendly_name) || c.entity || 'Camera').replace(/ (Live view|Snapshot)$/i, '');
  }

  _render(timer = false) {
    const c = this.config;
    if (!this._hass && !c.demo) return;
    if (!this._built) {
      this.innerHTML = `<style>${CAM_CSS}</style><div class="cc-tile" role="button" tabindex="0"><img alt="" style="opacity:0"><div class="cc-tr"></div><div class="cc-ov"><span class="cc-name"></span><span class="cc-chip"></span></div></div>`;
      this._tile = this.querySelector('.cc-tile');
      this._img = this.querySelector('img');
      this._img.addEventListener('load', () => (this._img.style.opacity = '1'));
      this._tile.addEventListener('click', () => this._open());
      this._tile.addEventListener('keydown', (ev) => (ev.key === 'Enter' || ev.key === ' ') && (ev.preventDefault(), this._open()));
      this._built = true;
    }
    this.querySelector('.cc-name').textContent = this._name();
    const chip = this.querySelector('.cc-chip');
    if (c.demo) {
      chip.textContent = 'Doorbell · 4 min';
      chip.style.cssText = `background:${KINDS.ding[1]}; color:${KINDS.ding[2]};`;
      return;
    }
    const f = camFind(this._hass, c.entity, c);
    this._f = f;
    const pic = camPicture(this._hass, f);
    this._pic = pic;
    if (pic && pic.url) {
      const url = `${pic.url}${pic.url.includes('?') ? '&' : '?'}t=${pic.ms}`;
      if (url !== this._url) {
        // Load the new picture first, then swap, so the tile never goes blank.
        this._url = url;
        const next = new Image();
        next.onload = () => {
          if (this._url === url) this._img.src = url;
        };
        next.src = url;
      }
    }
    const refresh = (Number(c.refresh_after) || 60) * 60000;
    const old = !pic || !pic.ms || Date.now() - pic.ms > refresh;
    const waiting = asked.get(c.entity) && Date.now() - asked.get(c.entity) < 120000 && (!pic || pic.ms < asked.get(c.entity));
    const [label, bg, fg] = KINDS[pic && pic.kind] || KINDS.interval;
    chip.textContent = !pic || !pic.ms ? 'No picture yet' : waiting ? `${camAge(pic.ms)} · updating…` : `${label} · ${camAge(pic.ms)}`;
    chip.style.cssText = old || waiting ? `background:${OLD[0]}; color:${OLD[1]};` : `background:${bg}; color:${fg};`;
    const bat = f.battery && Number(this._hass.states[f.battery].state);
    const tr = this.querySelector('.cc-tr');
    const low = !isNaN(bat) && bat < 25;
    const sig = low ? 'low' : '';
    if (tr.dataset.sig !== sig) {
      tr.dataset.sig = sig;
      tr.innerHTML = low ? `<div class="cc-ib" title="Battery ${bat}%" style="color:#ffa726;">${iconHtml('mdi:battery-low', { size: '17px' })}</div>` : '';
      hydrateIcons(tr);
    }
    if (!timer) this._maybeRefresh();
  }

  // A new snapshot when the picture is old and someone is looking, at most
  // once per refresh_after for everyone (the button's state is when it was
  // last pressed, from any device).
  _maybeRefresh(force = false) {
    const c = this.config;
    const h = this._hass;
    if (!h || c.demo || !this._f || !this._f.button) return;
    if (!force && (!this._seen || document.visibilityState === 'hidden')) return;
    const refresh = (Number(c.refresh_after) || 60) * 60000;
    const now = Date.now();
    const pic = this._pic;
    if (!force && pic && pic.ms && now - pic.ms < refresh) return;
    const last = Math.max(Date.parse(h.states[this._f.button].state) || 0, asked.get(c.entity) || 0);
    if (!force && now - last < refresh) return;
    if (force && now - last < 20000) return;
    asked.set(c.entity, now);
    h.callService('button', 'press', { entity_id: this._f.button }).catch(() => {});
    this._render(true);
  }

  // ---- Pop-up
  _open() {
    if (this._pop) return;
    const c = this.config;
    const content = document.createElement('div');
    content.innerHTML = `<div class="cc-pop-sub"></div><div class="cc-pop-media"></div><div class="cc-btns"></div><div class="cc-evs"></div>`;
    this._popEl = content;
    this._talking = false;
    this._pop = openPopup(this, {
      title: this._name(),
      icon: this._f && this._f.ding ? 'mdi:doorbell-video' : 'mdi:cctv',
      color: '#78909c',
      content,
      onClose: () => {
        // Stop the live video (and the microphone) as soon as it's closed.
        const m = content.querySelector('.cc-pop-media');
        if (m) m.innerHTML = '';
        this._pop = null;
        this._popEl = null;
      },
    });
    content.querySelector('.cc-btns').addEventListener('click', (ev) => {
      const b = ev.target.closest('[data-act]');
      if (!b || b.disabled) return;
      const act = b.dataset.act;
      if (act === 'talk') {
        this._talking = !this._talking;
        this._media();
        this._renderPop();
      } else if (act === 'snap') {
        this._maybeRefresh(true);
      } else if (act === 'light' && this._f.light) {
        this._hass.callService('light', 'toggle', { entity_id: this._f.light });
      }
    });
    this._media();
    this._renderPop();
    this._events();
  }

  // Live video: the go2rtc stream (with the microphone while talking) when
  // the camera has one, else Home Assistant's own live view, else the picture.
  async _media() {
    const box = this._popEl && this._popEl.querySelector('.cc-pop-media');
    if (!box) return;
    const c = this.config;
    box.innerHTML = '';
    let el;
    if (c.demo) {
      el = document.createElement('div');
      el.style.background = 'linear-gradient(160deg,#5d6b7d,#2f3946 60%,#46503c)';
    } else if (c.talk_stream && customElements.get('webrtc-camera')) {
      el = document.createElement('webrtc-camera');
      el.setConfig({ url: c.talk_stream, media: this._talking ? 'video,audio,microphone' : 'video,audio', muted: false, ui: false });
      el.hass = this._hass;
    } else {
      const st = this._f && this._f.live && this._hass.states[this._f.live];
      if (st && customElements.get('ha-camera-stream')) {
        el = document.createElement('ha-camera-stream');
        el.hass = this._hass;
        el.stateObj = st;
        el.controls = false;
        el.muted = false;
      } else {
        try {
          const helpers = await window.loadCardHelpers();
          el = helpers.createCardElement({ type: 'picture-entity', entity: this._f.live, camera_view: 'live', show_name: false, show_state: false });
          el.hass = this._hass;
        } catch (err) {
          el = document.createElement('img');
          if (this._img && this._img.src) el.src = this._img.src;
        }
      }
    }
    if (!this._popEl) return;
    this._stage(box, el);
  }

  // The video at its own shape (the doorbell's is square, head to toe),
  // filling the 16:9 box: drag to look around, pinch, scroll or double-tap
  // to zoom.
  _stage(box, el) {
    const stage = document.createElement('div');
    stage.className = 'cc-stage';
    stage.appendChild(el);
    const mute = document.createElement('button');
    mute.className = 'cc-ib cc-mute';
    mute.setAttribute('aria-label', 'Sound');
    const setMute = () => {
      mute.innerHTML = iconHtml(el.muted ? 'mdi:volume-off' : 'mdi:volume-high', { size: '17px' });
      hydrateIcons(mute);
    };
    mute.addEventListener('click', (ev) => {
      ev.stopPropagation();
      el.muted = !el.muted;
      setMute();
    });
    box.append(stage, mute);
    setMute();
    const img = this._img;
    let aspect = img && img.naturalWidth && img.naturalHeight ? img.naturalWidth / img.naturalHeight : 16 / 9;
    const v = { x: 0, y: 0, z: 1, w: 0, h: 0 };
    // clientWidth/Height, not the on-screen rectangle: the pop-up opens with
    // a little zoom, which would make the first measurement too small.
    const size = () => ({ width: box.clientWidth, height: box.clientHeight });
    const clamp = () => {
      const r = size();
      v.x = Math.min(0, Math.max(r.width - v.w * v.z, v.x));
      v.y = Math.min(0, Math.max(r.height - v.h * v.z, v.y));
      stage.style.width = `${v.w * v.z}px`;
      stage.style.height = `${v.h * v.z}px`;
      stage.style.transform = `translate(${v.x}px, ${v.y}px)`;
    };
    const fit = () => {
      const r = size();
      if (!r.width) return;
      const first = !v.w;
      // Cover the box: as wide as it for a tall picture, as tall for a wide one.
      if (aspect < r.width / r.height) {
        v.w = r.width;
        v.h = r.width / aspect;
      } else {
        v.h = r.height;
        v.w = r.height * aspect;
      }
      if (first) {
        v.x = (r.width - v.w) / 2;
        v.y = (r.height - v.h) / 2;
      }
      clamp();
    };
    const zoomAt = (z, cx, cy) => {
      const r = box.getBoundingClientRect();
      const px = cx - r.left;
      const py = cy - r.top;
      const nz = Math.min(4, Math.max(1, z));
      v.x = px - ((px - v.x) * nz) / v.z;
      v.y = py - ((py - v.y) * nz) / v.z;
      v.z = nz;
      clamp();
    };
    const pts = new Map();
    let pinch = null;
    box.addEventListener('pointerdown', (ev) => {
      if (ev.target.closest('.cc-mute')) return;
      box.setPointerCapture(ev.pointerId);
      pts.set(ev.pointerId, [ev.clientX, ev.clientY]);
      if (pts.size === 2) {
        const [a, b] = [...pts.values()];
        pinch = { d: Math.hypot(a[0] - b[0], a[1] - b[1]), z: v.z };
      }
    });
    box.addEventListener('pointermove', (ev) => {
      const prev = pts.get(ev.pointerId);
      if (!prev) return;
      pts.set(ev.pointerId, [ev.clientX, ev.clientY]);
      if (pts.size === 2 && pinch) {
        const [a, b] = [...pts.values()];
        zoomAt((pinch.z * Math.hypot(a[0] - b[0], a[1] - b[1])) / (pinch.d || 1), (a[0] + b[0]) / 2, (a[1] + b[1]) / 2);
      } else if (pts.size === 1) {
        v.x += ev.clientX - prev[0];
        v.y += ev.clientY - prev[1];
        clamp();
      }
    });
    const up = (ev) => {
      pts.delete(ev.pointerId);
      if (pts.size < 2) pinch = null;
    };
    box.addEventListener('pointerup', up);
    box.addEventListener('pointercancel', up);
    box.addEventListener(
      'wheel',
      (ev) => {
        ev.preventDefault();
        zoomAt(v.z * (ev.deltaY < 0 ? 1.15 : 1 / 1.15), ev.clientX, ev.clientY);
      },
      { passive: false },
    );
    box.addEventListener('dblclick', (ev) => zoomAt(v.z > 1.4 ? 1 : 2, ev.clientX, ev.clientY));
    if (window.ResizeObserver) new ResizeObserver(fit).observe(box);
    requestAnimationFrame(fit);
    // Once the video is playing, use its own shape (it's inside the
    // player's shadow roots).
    const findVideo = (root, depth = 0) => {
      if (!root || depth > 6) return null;
      const vid = root.querySelector && root.querySelector('video');
      if (vid) return vid;
      for (const n of root.querySelectorAll ? root.querySelectorAll('*') : []) {
        const f = n.shadowRoot && findVideo(n.shadowRoot, depth + 1);
        if (f) return f;
      }
      return null;
    };
    let tries = 0;
    const look = () => {
      if (!box.isConnected || tries++ > 30) return;
      const vid = findVideo(el.shadowRoot || el);
      if (vid && vid.videoWidth && vid.videoHeight) {
        const a = vid.videoWidth / vid.videoHeight;
        if (Math.abs(a - aspect) > 0.02) {
          aspect = a;
          v.w = 0;
          fit();
        }
        return;
      }
      setTimeout(look, 500);
    };
    setTimeout(look, 500);
  }

  _renderPop() {
    const p = this._popEl;
    if (!p) return;
    const c = this.config;
    const h = this._hass;
    const f = this._f || {};
    const bat = f.battery && h ? Number(h.states[f.battery].state) : NaN;
    const sub = [!isNaN(bat) ? `Battery ${Math.round(bat)}%` : '', this._pic && this._pic.ms ? `Picture ${camAge(this._pic.ms)}${this._pic.ms && Date.now() - this._pic.ms >= 60000 ? ' old' : ''}` : ''].filter(Boolean).join(' · ');
    p.querySelector('.cc-pop-sub').textContent = sub;
    const lightOn = f.light && h && h.states[f.light].state === 'on';
    const lightName = f.light && h ? String(h.states[f.light].attributes.friendly_name || 'Light') : '';
    const btns = [];
    if (c.talk_stream) btns.push(`<button class="cc-btn talk${this._talking ? ' on' : ''}" data-act="talk">${iconHtml(this._talking ? 'mdi:microphone' : 'mdi:microphone-outline', { size: '22px' })}${this._talking ? 'Talking… tap to stop' : 'Talk'}</button>`);
    if (f.button || c.demo) btns.push(`<button class="cc-btn" data-act="snap">${iconHtml('mdi:camera', { size: '22px' })}Snapshot</button>`);
    if (f.light) btns.push(`<button class="cc-btn" data-act="light" style="${lightOn ? 'background:color-mix(in srgb, #ffb300 30%, transparent);' : ''}">${iconHtml(lightOn ? 'mdi:lightbulb-on' : 'mdi:lightbulb-outline', { size: '22px' })}${kitEsc(lightName)} ${lightOn ? 'on' : 'off'}</button>`);
    const html = btns.join('');
    const box = p.querySelector('.cc-btns');
    if (box.dataset.sig !== html) {
      box.dataset.sig = html;
      box.innerHTML = html;
      box.style.display = html ? '' : 'none';
      hydrateIcons(box);
    }
  }

  // Recent doorbell presses and motion, from the event entities' history.
  async _events() {
    const p = this._popEl;
    const f = this._f || {};
    const box = p && p.querySelector('.cc-evs');
    if (!box) return;
    let list = [];
    if (this.config.demo) {
      const now = Date.now();
      list = [
        ['ding', now - 300000],
        ['motion', now - 2 * 3600e3],
        ['motion', now - 15 * 3600e3],
      ];
    } else {
      const ids = [f.ding, f.motion].filter(Boolean);
      if (!ids.length) return;
      try {
        const hist = await kitStateHistory(this._hass, ids, 48);
        ids.forEach((id) =>
          (hist[id] || []).forEach(([, s]) => {
            const ms = Date.parse(s);
            if (!isNaN(ms)) list.push([id === f.ding ? 'ding' : 'motion', ms]);
          }),
        );
      } catch (err) {
        return;
      }
    }
    list = [...new Map(list.map((e) => [`${e[0]}${e[1]}`, e])).values()].sort((a, b) => b[1] - a[1]).slice(0, 8);
    if (!this._popEl) return;
    box.innerHTML = list.length
      ? list
          .map(([k, ms]) => {
            const [label, col] = KINDS[k];
            return `<div class="cc-ev"><div class="cc-ev-ico" style="background:color-mix(in srgb, ${col} 25%, transparent); color:${col};">${iconHtml(k === 'ding' ? 'mdi:doorbell' : 'mdi:motion-sensor', { size: '18px' })}</div><div><b>${label}</b><div class="cc-ev-when">${kitEsc(when(ms))}</div></div></div>`;
          })
          .join('')
      : '<div class="cc-ev-when">Nothing in the last two days.</div>';
    hydrateIcons(box);
  }

  getCardSize() {
    return 3;
  }

  getGridOptions() {
    return { columns: 6, rows: 'auto' };
  }

  static getConfigElement() {
    return document.createElement(`camera-card-editor${SUFFIX}`);
  }

  static getStubConfig(hass) {
    const id = Object.keys((hass && hass.states) || {}).find((e) => /^camera\..*_live_view$/.test(e)) || Object.keys((hass && hass.states) || {}).find((e) => e.startsWith('camera.'));
    return id ? { entity: id } : { demo: true };
  }
}

export function registerCameraCard() {
  if (!customElements.get(`camera-card-editor${SUFFIX}`)) customElements.define(`camera-card-editor${SUFFIX}`, CameraCardEditor);
  if (!customElements.get(`camera-card${SUFFIX}`)) customElements.define(`camera-card${SUFFIX}`, CameraCard);
  window.customCards = window.customCards || [];
  window.customCards.push({
    type: `camera-card${SUFFIX}`,
    name: `Camera Card${LABEL}`,
    description: "A camera's freshest picture with how old it is; tap for live video, a new snapshot, the light and Talk",
    preview: true,
    documentationURL: 'https://github.com/J45PER/church-drive-cards#readme',
  });
}
