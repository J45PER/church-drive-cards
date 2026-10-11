// Camera events viewer, and the video stage both camera pop-ups use.
//
// openCameraEvents(card, hass, 'front_door', { title }) opens the pop-up:
// every saved doorbell press and motion for that camera from the last few
// days, as pictures by day. Tapping one opens a player above the list with
// its clip (or its picture if Ring didn't record one). Live video is the
// camera card's own pop-up. The events are saved by the Church Drive integration
// (events.py) and fetched over `church_drive/camera/events`, with signed
// links to each picture and clip.
//
// stageMedia(box, el, aspect) puts a video (or picture) in a 16:9 box at its
// own shape, covering the box: drag to look around, pinch, scroll or
// double-tap to zoom. The doorbell's head-to-toe video is square.

import { iconHtml, hydrateIcons } from './icons.js';
import { kitEsc } from './card-kit.js';
import { openPopup } from './popup.js';

export const KINDS = {
  ding: ['Doorbell', '#29b6f6', '#012'],
  motion: ['Motion', '#5c6bc0', '#fff'],
  linked: ['Linked', '#26a69a', '#fff'],
  live: ['Live view', '#78909c', '#fff'],
  interval: ['Snapshot', 'rgba(255,255,255,0.22)', '#fff'],
  'on-demand': ['Snapshot', 'rgba(255,255,255,0.22)', '#fff'],
};

export const STAGE_CSS = `
  .cc-pop-media { position:relative; border-radius:14px; overflow:hidden; background:#000; aspect-ratio:var(--cc-aspect, 16/9); max-height:var(--cc-max, 56dvh); width:100%; margin:0 auto; touch-action:none; cursor:grab; user-select:none; }
  .cc-pop-media.cc-big { --cc-max: 78dvh; }
  .cc-stage { position:absolute; left:0; top:0; transform-origin:0 0; will-change:transform; }
  .cc-stage > * { display:block; width:100%; height:100%; pointer-events:none; }
  .cc-stage img, .cc-stage video { object-fit:contain; }
  .cc-ib { width:30px; height:30px; border-radius:50%; background:rgba(0,0,0,0.5); color:#fff; display:flex; align-items:center; justify-content:center; border:none; padding:0; }
  .cc-ib.pill { width:auto; min-width:30px; border-radius:999px; padding:0 8px; gap:4px; font-size:0.72rem; font-weight:700; }
  button.cc-ib { cursor:pointer; }
  /* One bar for everything over the video, so nothing can sit on top of anything else. */
  .cc-top { position:absolute; left:0; right:0; top:0; z-index:2; display:flex; align-items:flex-start; justify-content:space-between; gap:8px; padding:8px; pointer-events:none;
    background:linear-gradient(rgba(0,0,0,0.45), transparent); }
  .cc-top-l { display:flex; gap:6px; align-items:center; min-width:0; flex:1 1 auto; flex-wrap:wrap; }
  .cc-top-r { display:flex; gap:6px; align-items:center; flex:none; }
  .cc-top button { pointer-events:auto; }
  .cc-tag { font-size:0.7rem; font-weight:800; border-radius:6px; padding:3px 7px; color:#fff; max-width:100%; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
  .cc-pop-media.cc-fs { position:fixed; inset:0; z-index:2147483000; width:100vw; height:100dvh; max-height:none; aspect-ratio:auto; border-radius:0; }
  .cc-pop-media.cc-fs.cc-rot { inset:auto; left:100vw; top:0; width:100dvh; height:100vw; transform-origin:0 0; transform:rotate(90deg); }
  .cc-pop-media:fullscreen { aspect-ratio:auto; max-height:none; border-radius:0; width:100vw; height:100vh; }
  .cc-btns { display:flex; gap:10px; margin:14px 0 6px; }
  .cc-btn { flex:1 1 0; min-width:0; border:none; border-radius:14px; padding:10px 4px; cursor:pointer; font:inherit; font-size:0.78rem; font-weight:700;
    background:rgba(127,127,127,0.16); color:var(--primary-text-color); display:flex; flex-direction:column; align-items:center; gap:4px; }
  .cc-btn.main { background:#5c6bc0; color:#fff; }
  .cc-btn.talk { background:#43a047; color:#fff; }
  .cc-btn.talk.on { background:#e53935; }
  .cc-btn.answer { background:#43a047; color:#fff; animation:cc-ring 1s ease-in-out infinite; }
  .cc-btn[disabled] { opacity:.5; cursor:default; }
  @keyframes cc-ring { 50% { box-shadow:0 0 0 6px rgba(67,160,71,0.35); } }
  @media (prefers-reduced-motion: reduce) { .cc-btn.answer { animation:none; } }
`;

const EV_CSS = `
  .ce-big { position:absolute; left:50%; top:50%; transform:translate(-50%,-50%); z-index:1; width:64px; height:64px; border:none; border-radius:50%; cursor:pointer;
    background:rgba(0,0,0,0.5); color:#fff; display:flex; align-items:center; justify-content:center; }
  .ce-bar { display:flex; align-items:center; gap:8px; margin-top:8px; }
  .ce-ctl { flex:none; width:38px; height:38px; border:none; border-radius:50%; cursor:pointer; background:rgba(127,127,127,0.16); color:var(--primary-text-color);
    display:flex; align-items:center; justify-content:center; padding:0; }
  .ce-time { flex:none; font-size:0.75rem; font-variant-numeric:tabular-nums; color:var(--secondary-text-color); min-width:30px; text-align:center; }
  .ce-seek { flex:1; min-width:0; height:28px; margin:0; background:transparent; -webkit-appearance:none; appearance:none; cursor:pointer; --p:0%; }
  .ce-seek::-webkit-slider-runnable-track { height:6px; border-radius:3px; background:linear-gradient(90deg, #5c6bc0 var(--p), rgba(127,127,127,0.3) var(--p)); }
  .ce-seek::-moz-range-track { height:6px; border-radius:3px; background:linear-gradient(90deg, #5c6bc0 var(--p), rgba(127,127,127,0.3) var(--p)); }
  .ce-seek::-webkit-slider-thumb { -webkit-appearance:none; width:18px; height:18px; margin-top:-6px; border-radius:50%; background:#fff; border:none; box-shadow:0 1px 3px rgba(0,0,0,0.5); }
  .ce-seek::-moz-range-thumb { width:18px; height:18px; border-radius:50%; background:#fff; border:none; }
  .ce-chips { display:flex; gap:6px; flex-wrap:wrap; margin:12px 0 2px; }
  .ce-chip { border:none; cursor:pointer; font:inherit; font-size:0.75rem; font-weight:700; border-radius:999px; padding:5px 11px; background:rgba(127,127,127,0.18); color:var(--primary-text-color); }
  .ce-chip.on { background:var(--primary-text-color, #e6e8ee); color:var(--card-background-color, #1f2128); }
  .ce-day { font-size:0.78rem; font-weight:700; color:var(--secondary-text-color); margin:14px 0 6px; }
  .ce-grid { display:grid; grid-template-columns:repeat(auto-fill, minmax(104px, 1fr)); gap:6px; }
  .ce-ev { position:relative; border:none; padding:0; cursor:pointer; border-radius:9px; overflow:hidden; aspect-ratio:16/9; background:linear-gradient(160deg,#55606f,#2f3946); color:#fff; font:inherit; }
  .ce-ev img { position:absolute; inset:0; width:100%; height:100%; object-fit:cover; }
  .ce-ev.sel { outline:2px solid var(--primary-text-color, #e6e8ee); outline-offset:1px; }
  .ce-ev .t { position:absolute; left:5px; bottom:3px; font-size:0.68rem; font-weight:700; text-shadow:0 1px 2px #000; }
  .ce-ev .k { position:absolute; right:4px; top:4px; font-size:0.6rem; font-weight:800; border-radius:999px; padding:1px 6px; }
  .ce-ev .p { position:absolute; left:3px; top:3px; display:flex; color:#fff; filter:drop-shadow(0 1px 2px rgba(0,0,0,0.7)); }
  .ce-empty { color:var(--secondary-text-color); font-size:0.85rem; padding:14px 2px; }
`;

const pad = (n) => String(n).padStart(2, '0');
const hm = (ms) => {
  const d = new Date(ms);
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`;
};
function dayName(ms, now = new Date()) {
  const d = new Date(ms);
  const day = new Date(d.getFullYear(), d.getMonth(), d.getDate());
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const diff = Math.round((today - day) / 86400000);
  if (diff === 0) return 'Today';
  if (diff === 1) return 'Yesterday';
  return d.toLocaleDateString('en-GB', { weekday: 'short', day: 'numeric', month: 'short' });
}

// Home Assistant's live view of a camera (with sound), or a live picture card.
export async function liveElement(hass, entityId) {
  const st = hass && hass.states[entityId];
  if (st && customElements.get('ha-camera-stream')) {
    const el = document.createElement('ha-camera-stream');
    el.hass = hass;
    el.stateObj = st;
    el.controls = false;
    // Muted to start: iPhones won't start a live stream with sound unless a tap
    // starts it, and show only a still until then. The sound button unmutes.
    el.muted = true;
    return el;
  }
  const helpers = await window.loadCardHelpers();
  const el = helpers.createCardElement({ type: 'picture-entity', entity: entityId, camera_view: 'live', show_name: false, show_state: false });
  el.hass = hass;
  return el;
}

// The <video> (or picture) inside a player; live players keep it in shadow roots.
export function findVideo(root, depth = 0) {
  if (!root || depth > 6) return null;
  if (root.localName === 'video' || root.localName === 'img') return root;
  const vid = root.querySelector && root.querySelector('video');
  if (vid) return vid;
  for (const n of root.querySelectorAll ? root.querySelectorAll('*') : []) {
    const f = n.shadowRoot && findVideo(n.shadowRoot, depth + 1);
    if (f) return f;
  }
  return null;
}

// The bar over the top of the video: a label on the left, buttons and status
// icons on the right. Everything that sits over the video goes in it.
export function stageBar(box) {
  let top = box.querySelector(':scope > .cc-top');
  if (!top) {
    top = document.createElement('div');
    top.className = 'cc-top';
    top.innerHTML = '<div class="cc-top-l"></div><div class="cc-top-r"></div>';
    box.appendChild(top);
  }
  return { left: top.firstChild, right: top.lastChild };
}

// Put `el` in `box` at its own shape (aspect = width / height, corrected from
// the playing video): the box takes that shape, so nothing is cropped. Drag,
// pinch, scroll or double-tap to zoom in, and a sound button. Returns the stage.
export function stageMedia(box, el, aspect = 16 / 9) {
  box.querySelectorAll(':scope > .cc-stage').forEach((n) => n.remove());
  box.querySelectorAll(':scope > .cc-top').forEach((n) => n.remove());
  const stage = document.createElement('div');
  stage.className = 'cc-stage';
  stage.appendChild(el);
  box.insertBefore(stage, box.firstChild);
  box.style.setProperty('--cc-aspect', String(aspect));
  if ('muted' in el) {
    const mute = document.createElement('button');
    mute.className = 'cc-ib cc-mute';
    mute.setAttribute('aria-label', 'Sound');
    const setMute = () => {
      mute.innerHTML = iconHtml(el.muted ? 'mdi:volume-off' : 'mdi:volume-high', { size: '18px' });
      hydrateIcons(mute);
    };
    mute.addEventListener('click', (ev) => {
      ev.stopPropagation();
      el.muted = !el.muted;
      const vid = findVideo(el.shadowRoot || el);
      if (vid && vid !== el) vid.muted = el.muted;
      if (vid && vid.paused) vid.play().catch(() => {});
      setMute();
    });
    el.addEventListener('volumechange', setMute);
    stageBar(box).right.appendChild(mute);
    setMute();
  }
  const v = { x: 0, y: 0, z: 1, w: 0, h: 0 };
  // clientWidth/Height, not the on-screen rectangle: the pop-up opens with
  // a little zoom, which would make the first measurement too small.
  const size = () => ({ width: box.clientWidth, height: box.clientHeight });
  const clamp = () => {
    const r = size();
    const w = v.w * v.z;
    const h = v.h * v.z;
    // Smaller than the box: centred. Larger: can't be dragged off the edge.
    v.x = w <= r.width ? (r.width - w) / 2 : Math.min(0, Math.max(r.width - w, v.x));
    v.y = h <= r.height ? (r.height - h) / 2 : Math.min(0, Math.max(r.height - h, v.y));
    stage.style.width = `${w}px`;
    stage.style.height = `${h}px`;
    stage.style.transform = `translate(${v.x}px, ${v.y}px)`;
  };
  const fit = () => {
    const r = size();
    if (!r.width || !r.height) return;
    // The whole picture inside the box: as wide as it for a wide picture, as tall for a tall one.
    if (aspect > r.width / r.height) {
      v.w = r.width;
      v.h = r.width / aspect;
    } else {
      v.h = r.height;
      v.w = r.height * aspect;
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
  // The box keeps its listeners across pictures: wire them once.
  box._cdStage = { fit, clamp, zoomAt, v };
  if (!box._cdWired) {
    box._cdWired = true;
    const s = () => box._cdStage;
    const pts = new Map();
    let pinch = null;
    // Turned sideways by the fallback full screen: screen movements don't match the picture's.
    const turned = () => box.classList.contains('cc-rot');
    box.addEventListener('pointerdown', (ev) => {
      if (ev.target.closest('.cc-top button')) return;
      box.setPointerCapture(ev.pointerId);
      pts.set(ev.pointerId, [ev.clientX, ev.clientY]);
      if (pts.size === 2) {
        const [a, b] = [...pts.values()];
        pinch = { d: Math.hypot(a[0] - b[0], a[1] - b[1]), z: s().v.z };
      }
    });
    box.addEventListener('pointermove', (ev) => {
      const prev = pts.get(ev.pointerId);
      if (!prev) return;
      pts.set(ev.pointerId, [ev.clientX, ev.clientY]);
      if (turned()) return;
      if (pts.size === 2 && pinch) {
        const [a, b] = [...pts.values()];
        s().zoomAt((pinch.z * Math.hypot(a[0] - b[0], a[1] - b[1])) / (pinch.d || 1), (a[0] + b[0]) / 2, (a[1] + b[1]) / 2);
      } else if (pts.size === 1) {
        s().v.x += ev.clientX - prev[0];
        s().v.y += ev.clientY - prev[1];
        s().clamp();
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
        if (!turned()) s().zoomAt(s().v.z * (ev.deltaY < 0 ? 1.15 : 1 / 1.15), ev.clientX, ev.clientY);
      },
      { passive: false },
    );
    box.addEventListener('dblclick', (ev) => !turned() && s().zoomAt(s().v.z > 1.4 ? 1 : 2, ev.clientX, ev.clientY));
    if (window.ResizeObserver) new ResizeObserver(() => s().fit()).observe(box);
  }
  requestAnimationFrame(fit);
  // Once the video or picture has loaded, use its own shape.
  let tries = 0;
  const look = () => {
    if (!box.isConnected || box._cdStage.v !== v || tries++ > 30) return;
    const m = findVideo(el.shadowRoot || el);
    const w = m && (m.videoWidth || m.naturalWidth);
    const h = m && (m.videoHeight || m.naturalHeight);
    if (w && h) {
      const a = w / h;
      if (Math.abs(a - aspect) > 0.02) {
        aspect = a;
        box.style.setProperty('--cc-aspect', String(a));
        if (box.onaspect) box.onaspect(a);
      }
      requestAnimationFrame(fit);
      return;
    }
    setTimeout(look, 400);
  };
  setTimeout(look, 300);
  return stage;
}

// A label on the video's top-left corner: "LIVE", or the event being played.
export function stageTag(box, text, colour) {
  const t = document.createElement('div');
  t.className = 'cc-tag';
  t.style.background = colour;
  t.textContent = text;
  stageBar(box).left.appendChild(t);
  return t;
}

// Full screen: the video box fills the screen and, on a phone, turns to
// landscape. Uses the browser's full screen where there is one (and locks the
// screen to landscape), else (iPhones, which only do it for a bare <video>)
// fills the screen itself, turned sideways when the phone is upright.
// Returns whether it is now full screen.
export function toggleFullscreen(box) {
  const on = !!(document.fullscreenElement === box || box.classList.contains('cc-fs'));
  const done = (state) => box.dispatchEvent(new CustomEvent('cc-fullscreen', { detail: state }));
  const turn = () => box.classList.toggle('cc-rot', box.classList.contains('cc-fs') && window.innerHeight > window.innerWidth);
  if (on) {
    if (document.fullscreenElement === box) document.exitFullscreen().catch(() => {});
    box.classList.remove('cc-fs', 'cc-rot');
    try {
      screen.orientation.unlock();
    } catch (_) {}
    done(false);
    return false;
  }
  const fake = () => {
    box.classList.add('cc-fs');
    turn();
    if (!box._cdFsWatch) {
      box._cdFsWatch = true;
      window.addEventListener('resize', turn);
    }
    done(true);
  };
  if (box.requestFullscreen) {
    box
      .requestFullscreen()
      .then(() => {
        try {
          screen.orientation.lock('landscape').catch(() => {});
        } catch (_) {}
        done(true);
      })
      .catch(fake);
  } else fake();
  if (!box._cdFsEvent) {
    box._cdFsEvent = true;
    // The browser's own exit (Escape, swipe): tell the buttons.
    document.addEventListener('fullscreenchange', () => {
      if (!document.fullscreenElement && box.isConnected && !box.classList.contains('cc-fs')) {
        try {
          screen.orientation.unlock();
        } catch (_) {}
        done(false);
      }
    });
  }
  return true;
}

// Saved events for a camera ('front_door'): { events, keep } or null.
export async function loadCameraEvents(hass, base) {
  try {
    const res = await hass.callWS({ type: 'church_drive/camera/events', camera: base });
    return { events: res.events || [], keep: (res.settings && res.settings.keep_days) || 5 };
  } catch (_) {
    return null;
  }
}

export { hm, dayName };

// Play one saved event in `box` with its controls in `bar`: the clip (or the
// picture if Ring didn't record one). Returns { tag, el }.
export function playEvent(box, bar, e, { aspect = 16 / 9, name = '' } = {}) {
  bar.style.display = 'none';
  bar.innerHTML = '';
  let el;
  if (e.clip) {
    el = document.createElement('video');
    el.src = e.clip;
    // The event's picture until the clip draws (not the phone's grey placeholder).
    if (e.picture) el.poster = e.picture;
    el.autoplay = true;
    el.playsInline = true;
    el.loop = false;
    el.muted = false;
    el.preload = 'auto';
    el.setAttribute('playsinline', '');
  } else {
    el = document.createElement('img');
    el.src = e.picture || '';
    el.alt = '';
  }
  stageMedia(box, el, aspect);
  const [label, colour] = KINDS[e.kind] || KINDS.motion;
  const from = e.kind === 'linked' && e.source ? ` from ${e.source}` : '';
  const tag = stageTag(box, `${label}${from} · ${hm(e.ts * 1000)} ${dayName(e.ts * 1000).toLowerCase()}`, colour);
  if (e.clip) {
    // Start it here, inside the tap: iPhones only start a video (with sound)
    // from a tap, and don't even load it otherwise. If sound isn't allowed,
    // play it muted (the sound button turns it on).
    const big = document.createElement('button');
    big.className = 'ce-big';
    big.setAttribute('aria-label', 'Play');
    big.innerHTML = iconHtml('mdi:play', { size: '34px' });
    hydrateIcons(big);
    big.addEventListener('click', (ev) => {
      ev.stopPropagation();
      el.play().catch(() => {});
    });
    box.appendChild(big);
    const show = () => (big.style.display = el.paused ? '' : 'none');
    ['play', 'playing', 'pause', 'ended'].forEach((n) => el.addEventListener(n, show));
    el.play().catch(() => {
      el.muted = true;
      el.play().catch(show);
    });
    show();
    clipControls(box, bar, el, e, name);
    el.addEventListener('error', () => {
      tag.textContent = `Couldn't play this clip${el.error ? ` (${['', 'stopped', 'network', 'decode', 'format not supported'][el.error.code] || el.error.code})` : ''}`;
      tag.style.background = '#c62828';
    });
  }
  return { tag, el };
}

// Under a clip: play/pause, time, a timeline to scrub, length, download
// and full screen.
function clipControls(box, bar, el, e, name) {
  const icon = (i) => iconHtml(i, { size: '22px' });
  bar.innerHTML = `<button class="ce-ctl" data-c="play" aria-label="Play or pause"></button>
    <span class="ce-time">0:00</span>
    <input class="ce-seek" type="range" min="0" max="1000" step="1" value="0" aria-label="Timeline">
    <span class="ce-time ce-len">0:00</span>
    <button class="ce-ctl" data-c="save" aria-label="Download">${icon('mdi:download')}</button>
    <button class="ce-ctl" data-c="full" aria-label="Full screen">${icon('mdi:fullscreen')}</button>`;
  bar.style.display = '';
  hydrateIcons(bar);
  const btn = bar.querySelector('[data-c="play"]');
  const seek = bar.querySelector('.ce-seek');
  const [now, len] = bar.querySelectorAll('.ce-time');
  const clock = (t) => (isFinite(t) ? `${Math.floor(t / 60)}:${String(Math.floor(t % 60)).padStart(2, '0')}` : '0:00');
  let dragging = false;
  const sync = () => {
    btn.innerHTML = icon(el.paused ? 'mdi:play' : 'mdi:pause');
    hydrateIcons(btn);
    now.textContent = clock(el.currentTime);
    len.textContent = clock(el.duration);
    if (!dragging && el.duration) seek.value = String(Math.round((el.currentTime / el.duration) * 1000));
    seek.style.setProperty('--p', `${(Number(seek.value) / 10).toFixed(1)}%`);
  };
  ['play', 'pause', 'ended', 'timeupdate', 'durationchange', 'loadedmetadata'].forEach((n) => el.addEventListener(n, sync));
  sync();
  seek.addEventListener('input', () => {
    dragging = true;
    if (el.duration) el.currentTime = (Number(seek.value) / 1000) * el.duration;
    sync();
  });
  seek.addEventListener('change', () => (dragging = false));
  // The file, fetched once it's playing, so Download can hand it over at once
  // (iPhones only open the share sheet straight from a tap).
  let file = null;
  const fname = `${name} ${new Date(e.ts * 1000).toISOString().slice(0, 10)} ${hm(e.ts * 1000).replace(':', '.')}.mp4`;
  el.addEventListener(
    'playing',
    () => {
      fetch(e.clip)
        .then((r) => (r.ok ? r.blob() : null))
        .then((b) => (file = b && new File([b], fname, { type: 'video/mp4' })))
        .catch(() => {});
    },
    { once: true },
  );
  bar.addEventListener('click', (ev) => {
    const c = ev.target.closest('[data-c]');
    if (!c) return;
    if (c.dataset.c === 'play') {
      if (el.paused) el.play().catch(() => {});
      else el.pause();
    } else if (c.dataset.c === 'full') {
      if (box.requestFullscreen || document.fullscreenEnabled === false) toggleFullscreen(box);
      else if (el.webkitEnterFullscreen) el.webkitEnterFullscreen(); // iPhone: its own player
      else toggleFullscreen(box);
    } else if (c.dataset.c === 'save') {
      if (file && navigator.canShare && navigator.canShare({ files: [file] })) {
        navigator.share({ files: [file], title: fname }).catch(() => {});
        return;
      }
      const a = document.createElement('a');
      a.href = file ? URL.createObjectURL(file) : e.clip;
      a.download = fname;
      document.body.appendChild(a);
      a.click();
      a.remove();
    }
  });
}

// The events viewer pop-up for one camera ('front_door'): the list first; a
// tapped event plays above it.
export function openCameraEvents(host, hass, base, { title = '', aspect = 16 / 9 } = {}) {
  const live = `camera.${base}_live_view`;
  const name = title || String((hass.states[live] && hass.states[live].attributes.friendly_name) || base.replace(/_/g, ' ')).replace(/ Live view$/i, '');
  const content = document.createElement('div');
  content.innerHTML = `<style>${STAGE_CSS}${EV_CSS}</style><div class="cc-pop-media" style="display:none;"></div><div class="ce-bar" style="display:none;"></div><div class="ce-chips"></div><div class="ce-list"><div class="ce-empty">Loading…</div></div>`;
  const box = content.querySelector('.cc-pop-media');
  const bar = content.querySelector('.ce-bar');
  const chips = content.querySelector('.ce-chips');
  const list = content.querySelector('.ce-list');
  const state = { filter: 'all', events: [], sel: null, keep: 5 };

  const pop = openPopup(host, {
    title: `${name} · events`,
    icon: 'mdi:history',
    color: '#5c6bc0',
    content,
    onClose: () => {
      box.innerHTML = ''; // stop any video
      if (document.fullscreenElement) document.exitFullscreen().catch(() => {});
    },
  });

  // A tapped event opens the player above the list (the pop-up grows to fit).
  const play = (e) => {
    state.sel = e.id;
    renderList();
    box.style.display = '';
    if (pop.body) pop.body.scrollTo({ top: 0, behavior: 'smooth' });
    playEvent(box, bar, e, { aspect, name });
    bar.style.display = e.clip ? '' : 'none';
  };

  const renderChips = () => {
    const opts = [['all', 'All'], ['ding', 'Doorbell'], ['motion', 'Motion'], ['linked', 'Linked'], ['live', 'Live view']].filter(
      ([k]) => k === 'all' || state.events.some((e) => e.kind === k),
    );
    chips.innerHTML = opts.map(([k, t]) => `<button class="ce-chip${state.filter === k ? ' on' : ''}" data-f="${k}">${t}</button>`).join('');
  };

  const renderList = () => {
    renderChips();
    const shown = state.events.filter((e) => state.filter === 'all' || e.kind === state.filter);
    if (!shown.length) {
      list.innerHTML = `<div class="ce-empty">${state.events.length ? 'Nothing of that kind' : 'No events saved yet'} in the last ${state.keep} days.</div>`;
      return;
    }
    let html = '';
    let day = '';
    shown.forEach((e) => {
      const d = dayName(e.ts * 1000);
      if (d !== day) {
        html += `${day ? '</div>' : ''}<div class="ce-day">${kitEsc(d)}</div><div class="ce-grid">`;
        day = d;
      }
      const [label, bg, fg] = KINDS[e.kind] || KINDS.motion;
      html += `<button class="ce-ev${state.sel === e.id ? ' sel' : ''}" data-id="${kitEsc(e.id)}" aria-label="${label} ${hm(e.ts * 1000)}">
        ${e.picture ? `<img src="${kitEsc(e.picture)}" alt="" loading="lazy">` : ''}
        ${e.clip ? `<span class="p">${iconHtml('mdi:play', { size: '18px' })}</span>` : ''}<span class="k" style="background:${bg}; color:${fg};">${label}</span><span class="t">${hm(e.ts * 1000)}</span></button>`;
    });
    list.innerHTML = `${html}</div>`;
    hydrateIcons(list);
  };

  chips.addEventListener('click', (ev) => {
    const b = ev.target.closest('[data-f]');
    if (!b) return;
    state.filter = b.dataset.f;
    renderList();
  });
  list.addEventListener('click', (ev) => {
    const b = ev.target.closest('[data-id]');
    const e = b && state.events.find((x) => x.id === b.dataset.id);
    if (e) play(e);
  });

  hass
    .callWS({ type: 'church_drive/camera/events', camera: base })
    .then((res) => {
      state.events = res.events || [];
      state.keep = (res.settings && res.settings.keep_days) || 5;
      renderList();
    })
    .catch(() => {
      list.innerHTML = '<div class="ce-empty">Saved events need the latest Church Drive integration (and a restart).</div>';
    });
  return pop;
}

// The camera a card or zone is about, by its base name ('front_door'): from
// a camera entity, or an event.<x>_ding / _motion, or the zone's name.
export function cameraBase(hass, ...hints) {
  const s = (hass && hass.states) || {};
  for (const h of hints.flat()) {
    if (!h) continue;
    const m = String(h).match(/^(?:camera|event)\.([a-z0-9_]+?)(?:_live_view|_snapshot|_last_recording|_ding|_motion)?$/);
    const base = m ? m[1] : String(h).toLowerCase().trim().replace(/[^a-z0-9]+/g, '_').replace(/^_|_$/g, '');
    if (base && s[`camera.${base}_live_view`]) return base;
  }
  return null;
}
