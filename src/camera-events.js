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
  .cc-pop-media { position:relative; border-radius:14px; overflow:hidden; background:#000; aspect-ratio:16/9; touch-action:none; cursor:grab; user-select:none; }
  .cc-stage { position:absolute; left:0; top:0; transform-origin:0 0; will-change:transform; }
  .cc-stage > * { display:block; width:100%; height:100%; pointer-events:none; }
  .cc-stage img, .cc-stage video { object-fit:cover; }
  .cc-ib { width:28px; height:28px; border-radius:50%; background:rgba(0,0,0,0.45); color:#fff; display:flex; align-items:center; justify-content:center; }
  .cc-mute { position:absolute; top:8px; right:8px; border:none; cursor:pointer; z-index:1; }
  .cc-tag { position:absolute; top:8px; left:8px; z-index:1; font-size:0.7rem; font-weight:800; border-radius:6px; padding:2px 7px; color:#fff; pointer-events:none; }
  .cc-btns { display:flex; gap:10px; margin:14px 0 6px; }
  .cc-btn { flex:1 1 0; min-width:0; border:none; border-radius:14px; padding:10px 4px; cursor:pointer; font:inherit; font-size:0.78rem; font-weight:700;
    background:rgba(127,127,127,0.16); color:var(--primary-text-color); display:flex; flex-direction:column; align-items:center; gap:4px; }
  .cc-btn.main { background:#5c6bc0; color:#fff; }
  .cc-btn.talk { background:#43a047; color:#fff; }
  .cc-btn.talk.on { background:#e53935; }
  .cc-btn[disabled] { opacity:.5; cursor:default; }
`;

const EV_CSS = `
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
  .ce-ev .p { position:absolute; left:4px; top:3px; text-shadow:0 1px 2px #000; }
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
    el.muted = false;
    return el;
  }
  const helpers = await window.loadCardHelpers();
  const el = helpers.createCardElement({ type: 'picture-entity', entity: entityId, camera_view: 'live', show_name: false, show_state: false });
  el.hass = hass;
  return el;
}

// Put `el` in `box` at its own shape (aspect = width / height, corrected from
// the playing video), covering the box, with drag, pinch, scroll and
// double-tap zoom, and a sound button. Returns the stage element.
export function stageMedia(box, el, aspect = 16 / 9) {
  box.innerHTML = '';
  const stage = document.createElement('div');
  stage.className = 'cc-stage';
  stage.appendChild(el);
  box.appendChild(stage);
  if ('muted' in el) {
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
    box.appendChild(mute);
    setMute();
  }
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
  // The box keeps its listeners across pictures: wire them once.
  box._cdStage = { fit, clamp, zoomAt, v };
  if (!box._cdWired) {
    box._cdWired = true;
    const s = () => box._cdStage;
    const pts = new Map();
    let pinch = null;
    box.addEventListener('pointerdown', (ev) => {
      if (ev.target.closest('.cc-mute')) return;
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
        s().zoomAt(s().v.z * (ev.deltaY < 0 ? 1.15 : 1 / 1.15), ev.clientX, ev.clientY);
      },
      { passive: false },
    );
    box.addEventListener('dblclick', (ev) => s().zoomAt(s().v.z > 1.4 ? 1 : 2, ev.clientX, ev.clientY));
    if (window.ResizeObserver) new ResizeObserver(() => s().fit()).observe(box);
  }
  requestAnimationFrame(fit);
  // Once the video or picture has loaded, use its own shape (a live player
  // keeps its <video> inside shadow roots).
  const findVideo = (root, depth = 0) => {
    if (!root || depth > 6) return null;
    if (root.localName === 'video' || root.localName === 'img') return root;
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
    if (!box.isConnected || box._cdStage.v !== v || tries++ > 30) return;
    const m = findVideo(el.shadowRoot || el);
    const w = m && (m.videoWidth || m.naturalWidth);
    const h = m && (m.videoHeight || m.naturalHeight);
    if (w && h) {
      const a = w / h;
      if (Math.abs(a - aspect) > 0.02) {
        aspect = a;
        v.w = 0;
        fit();
      }
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
  box.appendChild(t);
  return t;
}

// The events viewer pop-up for one camera ('front_door'): the list first; a
// tapped event plays above it.
export function openCameraEvents(host, hass, base, { title = '', aspect = 16 / 9 } = {}) {
  const live = `camera.${base}_live_view`;
  const name = title || String((hass.states[live] && hass.states[live].attributes.friendly_name) || base.replace(/_/g, ' ')).replace(/ Live view$/i, '');
  const content = document.createElement('div');
  content.innerHTML = `<style>${STAGE_CSS}${EV_CSS}</style><div class="cc-pop-media" style="display:none;"></div><div class="ce-chips"></div><div class="ce-list"><div class="ce-empty">Loading…</div></div>`;
  const box = content.querySelector('.cc-pop-media');
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
    },
  });

  // A tapped event opens the player above the list (the pop-up grows to fit).
  const play = (e) => {
    state.sel = e.id;
    renderList();
    box.style.display = '';
    if (pop.body) pop.body.scrollTo({ top: 0, behavior: 'smooth' });
    let el;
    if (e.clip) {
      el = document.createElement('video');
      el.src = e.clip;
      el.autoplay = true;
      el.playsInline = true;
      el.loop = false;
      el.muted = false;
      el.setAttribute('playsinline', '');
      // Tap the video (not a drag) to pause or play.
      el.addEventListener('loadeddata', () => el.play().catch(() => {}));
    } else {
      el = document.createElement('img');
      el.src = e.picture || '';
      el.alt = '';
    }
    stageMedia(box, el, aspect);
    const [label, colour] = KINDS[e.kind] || KINDS.motion;
    const from = e.kind === 'linked' && e.source ? ` from ${e.source}` : '';
    stageTag(box, `${e.clip ? '▶ ' : ''}${label}${from} · ${hm(e.ts * 1000)} ${dayName(e.ts * 1000).toLowerCase()}`, colour);
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
        ${e.clip ? '<span class="p">▶</span>' : ''}<span class="k" style="background:${bg}; color:${fg};">${label}</span><span class="t">${hm(e.ts * 1000)}</span></button>`;
    });
    list.innerHTML = `${html}</div>`;
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
