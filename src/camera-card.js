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
import { kitEsc } from './card-kit.js';
import { openPopup } from './popup.js';
import { KINDS, STAGE_CSS, stageMedia, stageTag, stageBar, liveElement, openCameraEvents, cameraBase, findVideo, toggleFullscreen, loadCameraEvents, playEvent, hm, dayName } from './camera-events.js';
import { signalBars, signalIcon, signalColour, batteryIcon, batteryColour, ringing, tileAspect, loadView, saveView, loadAspect, saveAspect } from './camera-status.js';

const OLD = ['#ffa726', '#221'];

// When each camera last asked for a snapshot from this browser, so two cards
// for one camera don't both ask.
const asked = new Map();

const CAM_CSS = `${STAGE_CSS}
  .cc-tile { position:relative; display:block; width:100%; aspect-ratio:var(--cc-tile, 16/9); border-radius:var(--ha-card-border-radius, 14px); overflow:hidden; cursor:pointer;
    background:linear-gradient(160deg, #5d6b7d, #2f3946 60%, #46503c); box-shadow:0 3px 10px rgba(0,0,0,0.45); }
  .cc-tile img { position:absolute; inset:0; width:100%; height:100%; object-fit:cover; transition:opacity .4s ease; }
  /* Top row: why/when on the left, status on the right. Bottom row: the name, and Answer when it rings. They never share a row. */
  .cc-top-row { position:absolute; left:0; right:0; top:0; padding:8px 8px 22px; display:flex; align-items:flex-start; justify-content:space-between; gap:6px; background:linear-gradient(rgba(0,0,0,0.5), transparent); pointer-events:none; }
  .cc-ov { position:absolute; left:0; right:0; bottom:0; padding:22px 12px 10px; background:linear-gradient(transparent, rgba(0,0,0,0.72)); display:flex; align-items:flex-end; gap:8px; color:#fff; }
  .cc-name { flex:1 1 auto; font-weight:700; font-size:0.98rem; min-width:0; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; text-shadow:0 1px 3px rgba(0,0,0,0.6); }
  .cc-chip { flex:0 1 auto; min-width:0; overflow:hidden; text-overflow:ellipsis; font-size:0.7rem; font-weight:700; border-radius:999px; padding:2px 8px; white-space:nowrap; }
  .cc-tr { flex:none; display:flex; gap:6px; }
  .cc-answer { flex:none; border:none; cursor:pointer; font:inherit; font-size:0.8rem; font-weight:800; border-radius:999px; padding:6px 12px; background:#43a047; color:#fff; display:flex; align-items:center; gap:6px; animation:cc-ring 1s ease-in-out infinite; }
  .cc-tile.ringing { outline:2px solid #43a047; outline-offset:-2px; }
  .cc-pop-sub { font-size:0.8rem; color:var(--secondary-text-color); margin:10px 0 0; }
  .cc-status { display:flex; gap:6px; }
  .cc-tl { display:flex; gap:8px; overflow-x:auto; margin:14px -18px 4px; padding:0 18px 6px; scroll-snap-type:x proximity; scroll-padding:0 18px; scrollbar-width:thin; }
  .cc-tl:empty { display:none; }
  .cc-tl-item { flex:none; position:relative; width:104px; aspect-ratio:16/9; border:none; padding:0; border-radius:9px; overflow:hidden; cursor:pointer; scroll-snap-align:start;
    background:linear-gradient(160deg,#55606f,#2f3946); color:#fff; font:inherit; }
  .cc-tl-item img { position:absolute; inset:0; width:100%; height:100%; object-fit:cover; }
  .cc-tl-item.sel { outline:2px solid var(--primary-text-color, #e6e8ee); outline-offset:1px; }
  .cc-tl-item .t { position:absolute; left:5px; bottom:3px; font-size:0.68rem; font-weight:700; text-shadow:0 1px 2px #000; }
  .cc-tl-item .k { position:absolute; right:4px; top:4px; font-size:0.6rem; font-weight:800; border-radius:999px; padding:1px 6px; }
  .cc-tl-item .p { position:absolute; left:3px; top:3px; display:flex; filter:drop-shadow(0 1px 2px rgba(0,0,0,0.7)); }
  .cc-tl-item.live { background:#e53935; display:flex; align-items:center; justify-content:center; flex-direction:column; gap:2px; font-size:0.8rem; font-weight:800; }
  .cc-tl-day { flex:none; align-self:center; font-size:0.7rem; font-weight:700; color:var(--secondary-text-color); writing-mode:vertical-rl; transform:rotate(180deg); }
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
    signal: has(`sensor.${base}_wireless`) || has(`sensor.${base}_wifi_signal_strength`) || has(`sensor.${base}_wifi_signal_category`),
    ringSensor: has(`binary_sensor.${base}_ding`),
    base,
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
    { name: 'ring_popup', selector: { boolean: {} } },
    { name: 'demo', selector: { boolean: {} } },
  ],
  labels: {
    entity: 'Camera',
    name: 'Name (optional)',
    light: 'Light by the camera (optional)',
    refresh_after: 'Ask for a new snapshot when the picture is older than',
    talk_stream: 'Two-way audio stream (optional, go2rtc stream name)',
    ring_popup: 'Open the live view by itself when the doorbell rings',
    demo: 'Show a pretend camera (for Design Presets)',
  },
  helpers: {
    entity: "Its snapshot, Take Snapshot button, battery and doorbell/motion events are found from the camera's name.",
    light: 'Empty: the one light in the same area, if there is exactly one.',
    refresh_after: 'Default 60. Only while someone is looking, and at most once per this time for everyone, so battery cameras last.',
    talk_stream: 'A go2rtc stream with two-way audio (e.g. front_door). Adds a Talk button to the pop-up, and lets Answer talk.',
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

  // Is the doorbell ringing now (and not yet answered from here)?
  _ringing() {
    const f = this._f;
    const h = this._hass;
    if (!f || !h || (!f.ringSensor && !f.ding)) return false;
    return ringing(f.ringSensor && h.states[f.ringSensor].state, f.ding && h.states[f.ding].state, Date.now(), this._answered || 0);
  }

  _render(timer = false) {
    const c = this.config;
    if (!this._hass && !c.demo) return;
    if (!this._built) {
      this.innerHTML = `<style>${CAM_CSS}</style><div class="cc-tile" role="button" tabindex="0"><img alt="" style="opacity:0"><div class="cc-top-row"><span class="cc-chip"></span><div class="cc-tr"></div></div><div class="cc-ov"><span class="cc-name"></span><button class="cc-answer" style="display:none;"></button></div></div>`;
      this._tile = this.querySelector('.cc-tile');
      this._img = this.querySelector('img');
      // The tile is the picture's own shape (kept between 3:4 and 16:9), remembered so it is the right height before the picture loads.
      const shape = (a) => this._tile.style.setProperty('--cc-tile', String(a));
      shape(loadAspect(c.entity || 'demo') || 16 / 9);
      this._img.addEventListener('load', () => {
        this._img.style.opacity = '1';
        if (this._img.naturalWidth) {
          const a = tileAspect(this._img.naturalWidth, this._img.naturalHeight);
          shape(a);
          saveAspect(c.entity || 'demo', a);
        }
      });
      this._tile.addEventListener('click', (ev) => {
        const ans = ev.target.closest('.cc-answer');
        this._open(!!ans);
      });
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

    // Top right: only what needs attention (low battery, weak signal).
    const bat = f.battery && Number(this._hass.states[f.battery].state);
    const bars = f.signal ? signalBars(this._hass.states[f.signal].state, f.signal.endsWith('_category') ? this._hass.states[f.signal].state : '') : -1;
    const low = !isNaN(bat) && bat < 25;
    const weak = bars >= 0 && bars <= 1;
    const sig = `${low ? 'b' : ''}${weak ? 's' : ''}`;
    const tr = this.querySelector('.cc-tr');
    if (tr.dataset.sig !== sig) {
      tr.dataset.sig = sig;
      tr.innerHTML =
        (low ? `<div class="cc-ib" title="Battery ${bat}%" style="color:#ffa726;">${iconHtml(batteryIcon(bat), { size: '18px' })}</div>` : '') +
        (weak ? `<div class="cc-ib" title="Weak signal" style="color:#ffa726;">${iconHtml(signalIcon(bars), { size: '18px' })}</div>` : '');
      hydrateIcons(tr);
    }

    // Ringing: a green outline and an Answer button, until answered or it stops.
    const ring = this._ringing();
    const ans = this.querySelector('.cc-answer');
    this._tile.classList.toggle('ringing', ring);
    if (ring !== (ans.style.display !== 'none')) {
      ans.style.display = ring ? '' : 'none';
      ans.innerHTML = ring ? `${iconHtml('mdi:phone', { size: '18px' })}Answer` : '';
      hydrateIcons(ans);
    }
    if (ring && !this._wasRinging && !this._pop && c.ring_popup) this._open(false);
    this._wasRinging = ring;
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
  // answer: opened from the Answer button, so talk starts and the sound is on.
  _open(answer = false) {
    if (this._pop) return;
    const c = this.config;
    const content = document.createElement('div');
    content.innerHTML = `<div class="cc-pop-media${loadView(c.entity || 'demo') === 'expanded' ? ' cc-big' : ''}"></div><div class="ce-bar" style="display:none;"></div><div class="cc-btns"></div><div class="cc-tl" role="list" aria-label="Activity"></div><div class="cc-pop-sub"></div>`;
    this._popEl = content;
    this._expanded = loadView(c.entity || 'demo') === 'expanded';
    this._full = false;
    this._talking = !!(answer && c.talk_stream);
    this._listen = !!answer; // sound on from the start (the tap that opened this allows it)
    this._sel = null; // the timeline event being played; null is live
    this._events = [];
    if (answer) this._answered = Date.now();
    this._pop = openPopup(this, {
      title: this._name(),
      icon: this._f && this._f.ding ? 'mdi:doorbell-video' : 'mdi:cctv',
      color: '#78909c',
      content,
      onClose: () => {
        // Stop the live video (and the microphone) as soon as it's closed.
        const m = content.querySelector('.cc-pop-media');
        if (m) {
          if (document.fullscreenElement === m) document.exitFullscreen().catch(() => {});
          m.innerHTML = '';
        }
        try {
          screen.orientation.unlock();
        } catch (_) {}
        clearTimeout(this._evTimer);
        this._pop = null;
        this._popEl = null;
      },
    });
    const box = content.querySelector('.cc-pop-media');
    box.addEventListener('cc-fullscreen', (ev) => {
      this._full = !!ev.detail;
      this._decorate();
    });
    content.querySelector('.cc-btns').addEventListener('click', (ev) => {
      const b = ev.target.closest('[data-act]');
      if (!b || b.disabled) return;
      const act = b.dataset.act;
      if (act === 'talk' || act === 'answer') {
        this._answered = Date.now();
        this._sel = null;
        this._talking = act === 'answer' ? true : !this._talking;
        if (act === 'answer') this._listen = true;
        this._media();
        this._renderPop();
      } else if (act === 'events') {
        const base = cameraBase(this._hass, this.config.entity);
        const aspect = this._aspect();
        this._pop.close().then(() => base && openCameraEvents(this, this._hass, base, { title: this._name(), aspect }));
      } else if (act === 'snap') {
        this._maybeRefresh(true);
      } else if (act === 'light' && this._f.light) {
        this._hass.callService('light', 'toggle', { entity_id: this._f.light });
      }
    });
    content.querySelector('.cc-tl').addEventListener('click', (ev) => {
      const b = ev.target.closest('[data-id]');
      if (!b) return;
      if (b.dataset.id === 'live') {
        this._sel = null;
        this._media();
        this._renderTimeline();
        return;
      }
      const e = this._events.find((x) => x.id === b.dataset.id);
      if (e) this._playEvent(e);
    });
    if (answer) this._answered = Date.now();
    this._media();
    this._renderPop();
    this._loadEvents();
  }

  // The picture's shape (width / height): the doorbell's is square.
  _aspect() {
    const img = this._img;
    return img && img.naturalWidth && img.naturalHeight ? img.naturalWidth / img.naturalHeight : loadAspect(this.config.entity || 'demo') || 16 / 9;
  }

  // Live video: the go2rtc stream (with the microphone while talking) when
  // the camera has one, else Home Assistant's own live view, else the picture.
  async _media() {
    const box = this._popEl && this._popEl.querySelector('.cc-pop-media');
    if (!box) return;
    const c = this.config;
    const bar = this._popEl.querySelector('.ce-bar');
    bar.style.display = 'none';
    bar.innerHTML = '';
    box.querySelectorAll('.ce-big').forEach((n) => n.remove());
    const mic = this._talking && !(navigator.mediaDevices && navigator.mediaDevices.getUserMedia);
    let el;
    if (c.demo) {
      el = document.createElement('div');
      el.style.background = 'linear-gradient(160deg,#5d6b7d,#2f3946 60%,#46503c)';
    } else if (c.talk_stream && customElements.get('webrtc-camera')) {
      el = document.createElement('webrtc-camera');
      el.setConfig({ url: c.talk_stream, media: this._talking && !mic ? 'video,audio,microphone' : 'video,audio', muted: !this._talking && !this._listen, ui: false });
      el.hass = this._hass;
    } else {
      try {
        el = await liveElement(this._hass, this._f && this._f.live);
        if (this._listen) el.muted = false;
      } catch (err) {
        el = document.createElement('img');
        if (this._img && this._img.src) el.src = this._img.src;
      }
    }
    if (!this._popEl || this._sel) return;
    box.onaspect = (a) => saveAspect(c.entity || 'demo', Math.max(0.5, Math.min(2.4, a)));
    stageMedia(box, el, this._aspect());
    this._decorate();
    if (c.demo) return;
    // "Connecting…" until real video is playing (players show a still first).
    const tag = stageTag(box, mic ? '● No microphone (needs https)' : '● Connecting…', mic ? '#c62828' : '#616875');
    const live = this._talking ? '● LIVE · talking' : '● LIVE';
    let tries = 0;
    const check = () => {
      if (!tag.isConnected || this._sel) return;
      const v = findVideo(el.shadowRoot || el);
      if (v && v.localName === 'video' && !v.paused && v.readyState >= 2) {
        if (!mic) tag.textContent = live;
        tag.style.background = mic ? '#c62828' : '#e53935';
      } else if (tries++ < 120) setTimeout(check, 500);
      else tag.textContent = '● Still picture';
    };
    check();
  }

  // The buttons and status icons that sit in the video's top bar. Expanded or
  // full screen shows the battery and signal; the view buttons are always there.
  _decorate() {
    const box = this._popEl && this._popEl.querySelector('.cc-pop-media');
    if (!box) return;
    const { right } = stageBar(box);
    right.querySelectorAll('.cc-status, .cc-view').forEach((n) => n.remove());
    const status = document.createElement('div');
    status.className = 'cc-status';
    right.insertBefore(status, right.firstChild);
    const view = document.createElement('div');
    view.className = 'cc-status cc-view';
    view.innerHTML =
      `<button class="cc-ib" data-v="expand" aria-label="${this._expanded ? 'Smaller' : 'Bigger'}">${iconHtml(this._expanded ? 'mdi:arrow-collapse-vertical' : 'mdi:arrow-expand-vertical', { size: '18px' })}</button>` +
      `<button class="cc-ib" data-v="full" aria-label="${this._full ? 'Leave full screen' : 'Full screen'}">${iconHtml(this._full ? 'mdi:fullscreen-exit' : 'mdi:fullscreen', { size: '18px' })}</button>`;
    right.appendChild(view);
    hydrateIcons(view);
    view.onclick = (ev) => {
      const b = ev.target.closest('[data-v]');
      if (!b) return;
      ev.stopPropagation();
      if (b.dataset.v === 'full') toggleFullscreen(box);
      else {
        this._expanded = !this._expanded;
        saveView(this.config.entity || 'demo', this._expanded ? 'expanded' : 'normal');
        box.classList.toggle('cc-big', this._expanded);
        this._decorate();
      }
    };
    this._status = status;
    this._renderStatus();
  }

  _renderStatus() {
    const st = this._status;
    const h = this._hass;
    const f = this._f || {};
    if (!st || !st.isConnected) return;
    const show = this._expanded || this._full;
    const bat = f.battery && h ? Number(h.states[f.battery].state) : NaN;
    const bars = f.signal && h ? signalBars(h.states[f.signal].state, f.signal.endsWith('_category') ? h.states[f.signal].state : '') : -1;
    const html = show
      ? (!isNaN(bat) ? `<div class="cc-ib pill" title="Battery ${Math.round(bat)}%" style="color:${batteryColour(bat)};">${iconHtml(batteryIcon(bat), { size: '18px' })}${Math.round(bat)}%</div>` : '') +
        (f.signal ? `<div class="cc-ib" title="Signal" style="color:${signalColour(bars)};">${iconHtml(signalIcon(bars), { size: '18px' })}</div>` : '')
      : '';
    if (st.dataset.sig !== html) {
      st.dataset.sig = html;
      st.innerHTML = html;
      hydrateIcons(st);
    }
  }

  // ---- Activity timeline under the video (like Ring's): a strip of saved
  // events, newest first, after a Live tile. Tap one to play it in the video.
  async _loadEvents() {
    const base = cameraBase(this._hass, this.config.entity);
    if (!base || this.config.demo) return;
    const res = await loadCameraEvents(this._hass, base);
    if (!this._popEl || !res) return;
    this._events = res.events.slice(0, 40);
    this._renderTimeline();
  }

  _renderTimeline() {
    const tl = this._popEl && this._popEl.querySelector('.cc-tl');
    if (!tl) return;
    if (!this._events.length) {
      tl.innerHTML = '';
      return;
    }
    let html = `<button class="cc-tl-item live${this._sel ? '' : ' sel'}" data-id="live" role="listitem">${iconHtml('mdi:video', { size: '20px' })}LIVE</button>`;
    let day = '';
    for (const e of this._events) {
      const d = dayName(e.ts * 1000);
      if (d !== day) {
        html += `<span class="cc-tl-day">${kitEsc(d)}</span>`;
        day = d;
      }
      const [label, bg, fg] = KINDS[e.kind] || KINDS.motion;
      html += `<button class="cc-tl-item${this._sel === e.id ? ' sel' : ''}" data-id="${kitEsc(e.id)}" role="listitem" aria-label="${label} ${hm(e.ts * 1000)}">
        ${e.picture ? `<img src="${kitEsc(e.picture)}" alt="" loading="lazy">` : ''}${e.clip ? `<span class="p">${iconHtml('mdi:play', { size: '16px' })}</span>` : ''}<span class="k" style="background:${bg}; color:${fg};">${label}</span><span class="t">${hm(e.ts * 1000)}</span></button>`;
    }
    const keep = tl.scrollLeft;
    tl.innerHTML = html;
    tl.scrollLeft = keep;
    hydrateIcons(tl);
  }

  _playEvent(e) {
    const box = this._popEl.querySelector('.cc-pop-media');
    this._sel = e.id;
    this._talking = false;
    const bar = this._popEl.querySelector('.ce-bar');
    playEvent(box, bar, e, { aspect: this._aspect(), name: this._name() });
    this._decorate();
    this._renderTimeline();
    this._renderPop();
  }

  _renderPop() {
    const p = this._popEl;
    if (!p) return;
    const c = this.config;
    const h = this._hass;
    const f = this._f || {};
    const bat = f.battery && h ? Number(h.states[f.battery].state) : NaN;
    // Expanded and full screen show the battery on the video, so the line only adds it when they don't.
    const sub = [!isNaN(bat) && !this._expanded && !this._full ? `Battery ${Math.round(bat)}%` : '', this._pic && this._pic.ms ? `Picture ${camAge(this._pic.ms)}${this._pic.ms && Date.now() - this._pic.ms >= 60000 ? ' old' : ''}` : ''].filter(Boolean).join(' · ');
    p.querySelector('.cc-pop-sub').textContent = sub;
    this._renderStatus();
    // A new event saved by the integration: refresh the timeline a few seconds later (the picture is saved after the event).
    const act = h && h.states[`sensor.${f.base}_last_activity`];
    const mark = act ? act.state : '';
    if (this._mark !== undefined && mark !== this._mark) {
      clearTimeout(this._evTimer);
      this._evTimer = setTimeout(() => this._loadEvents(), 8000);
    }
    this._mark = mark;
    const lightOn = f.light && h && h.states[f.light].state === 'on';
    const lightName = f.light && h ? String(h.states[f.light].attributes.friendly_name || 'Light') : '';
    const btns = [];
    const ring = this._ringing();
    if (ring) btns.push(`<button class="cc-btn answer" data-act="answer">${iconHtml('mdi:phone', { size: '22px' })}Answer</button>`);
    if (!c.demo && cameraBase(h, c.entity)) btns.push(`<button class="cc-btn main" data-act="events">${iconHtml('mdi:history', { size: '22px' })}Events</button>`);
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
