// Camera Card (experimental): the camera card with the new live view, "layout C".
// It extends the stable camera card (same tile, same snapshot refresh) and replaces
// what the pop-up looks like:
//
//   Normal    the video in a 16:9 box (a tall picture such as the doorbell's is
//             cropped to it: drag up and down to look around). Only two small icons
//             sit on it, bottom right: bigger and full screen. Under it: a timer
//             and Hang up / Talk / Sound, then Light, Snapshot and Events.
//   Expanded  the picture fills the pop-up (remembered per camera). Tap the
//             picture to show or hide the buttons; they hide by themselves after 3 s.
//             Battery and signal stay on the picture, top right.
//   Full      the video turns landscape. Same tap to show or hide: Hang up, Talk and
//             Sound bottom left, + (Light, Snapshot, Events) bottom right, exit top
//             right, battery and signal top right beside it.
//
// No activity timeline in the live view: Events leaves it and opens the saved events.
// When the doorbell rings the tile has an Answer button, and the live view opens with a
// big Answer button; answering turns on the sound and (with talk_stream) the microphone.

import { CameraCard } from './camera-card.js';
import { iconHtml, hydrateIcons } from './icons.js';
import { SUFFIX, LABEL } from './suffix.js';
import { kitEsc } from './card-kit.js';
import { openPopup } from './popup.js';
import { createFormEditor } from './form-editor.js';
import { STAGE_CSS, stageMedia, stageTag, liveElement, openCameraEvents, cameraBase, findVideo, toggleFullscreen } from './camera-events.js';
import { signalBars, signalIcon, signalColour, batteryIcon, batteryColour, ringing, loadView, saveView } from './camera-status.js';

const HIDE_AFTER = 3000;

const EXP_CSS = `${STAGE_CSS}
  .cc-top-row { position:absolute; left:0; right:0; top:0; padding:8px 8px 22px; display:flex; align-items:flex-start; justify-content:space-between; gap:6px; background:linear-gradient(rgba(0,0,0,0.5), transparent); pointer-events:none; z-index:2; }
  .cc-top-row .cc-chip { flex:0 1 auto; min-width:0; overflow:hidden; text-overflow:ellipsis; }
  .cc-tr { flex:none; display:flex; gap:6px; }
  .cc-ov .cc-name { flex:1 1 auto; }
  .cc-answer { flex:none; border:none; cursor:pointer; font:inherit; font-size:0.8rem; font-weight:800; border-radius:999px; padding:6px 12px; background:#2e9d4f; color:#fff; display:flex; align-items:center; gap:6px; animation:cc-ring 1s ease-in-out infinite; }
  .cc-tile.ringing { outline:2px solid #2e9d4f; outline-offset:-2px; }
  @keyframes cc-ring { 50% { box-shadow:0 0 0 6px rgba(46,157,79,0.35); } }
  @media (prefers-reduced-motion: reduce) { .cc-answer { animation:none; } }

  /* The live view. Everything over the picture lives in the box (so it goes full screen with it) and is a .cc-ui, so touching it never pans the picture. */
  .cc-pop-media { --cc-pad:8px; }
  .cc-pop-media[data-mode="expanded"] { aspect-ratio:auto; width:100%; height:min(66dvh, 150vw); }
  .cc-pop-media[data-mode="full"] { aspect-ratio:auto; width:100%; }
  .cc-pop-media .cc-tag { z-index:3; }
  .cc-ui { position:absolute; z-index:3; display:flex; gap:6px; align-items:center; transition:opacity .2s, visibility .2s; pointer-events:none; }
  .cc-ui button, .cc-ui .cc-pill, .cc-ui .cc-menu, .cc-ui .cc-circles { pointer-events:auto; }
  .cc-ov-tr { top:var(--cc-pad); right:var(--cc-pad); }
  .cc-ov-tr:empty { display:none; }
  .cc-ov-corner { right:var(--cc-pad); bottom:var(--cc-pad); }
  .cc-ov-call { left:0; right:0; bottom:48px; justify-content:center; }
  .cc-pop-media[data-mode="full"] .cc-ov-call { left:var(--cc-pad); right:auto; bottom:var(--cc-pad); justify-content:flex-start; }
  .cc-ov-plus { right:var(--cc-pad); bottom:var(--cc-pad); flex-direction:column-reverse; align-items:flex-end; }
  .cc-pop-media[data-mode="expanded"].cc-hidec .hideable, .cc-pop-media[data-mode="full"].cc-hidec .hideable { opacity:0; visibility:hidden; }
  .cc-pill { display:flex; align-items:center; gap:5px; background:rgba(0,0,0,0.55); color:#fff; border-radius:999px; padding:4px 9px; font-size:0.72rem; font-weight:700; height:30px; box-sizing:border-box; }
  .cc-circle { width:44px; height:44px; border-radius:50%; border:none; padding:0; display:flex; align-items:center; justify-content:center; cursor:pointer; background:rgba(60,66,72,0.92); color:#fff; }
  .cc-circle.red { background:#d93025; }
  .cc-circle.on { background:#fff; color:#111; }
  .cc-circle.sm { width:32px; height:32px; background:rgba(0,0,0,0.55); color:#fff; }
  .cc-circle[disabled] { opacity:.5; cursor:default; }
  .cc-circles { display:flex; justify-content:center; gap:20px; }
  .cc-circles .lab { display:flex; flex-direction:column; align-items:center; gap:4px; font-size:0.7rem; font-weight:600; color:var(--secondary-text-color); }
  .cc-pop-media .cc-circles .lab { color:#fff; }
  .cc-pop-media .cc-circles { gap:12px; }
  .cc-timer { text-align:center; font-size:0.85rem; font-variant-numeric:tabular-nums; color:var(--secondary-text-color); padding:10px 0 8px; }
  .cc-answer-big { width:100%; border:none; border-radius:999px; padding:12px; background:#2e9d4f; color:#fff; font:inherit; font-size:1rem; font-weight:800; cursor:pointer; display:flex; align-items:center; justify-content:center; gap:8px; animation:cc-ring 1s ease-in-out infinite; }
  .cc-menu { display:flex; flex-direction:column; gap:2px; background:rgba(20,22,24,0.94); border-radius:12px; padding:6px; }
  .cc-menu button { display:flex; gap:8px; align-items:center; background:none; border:none; color:#fff; font:inherit; font-size:0.8rem; padding:8px 12px; cursor:pointer; }
  .cc-menu[hidden] { display:none; }
  .cc-note { font-size:0.78rem; color:var(--secondary-text-color); margin:8px 0 0; }
`;

export const CameraExperimentalEditor = createFormEditor({
  schema: () => [
    { name: 'entity', selector: { entity: { domain: 'camera' } } },
    { name: 'name', selector: { text: {} } },
    { name: 'light', selector: { entity: { domain: 'light' } } },
    { name: 'refresh_after', selector: { number: { min: 10, max: 1440, step: 5, mode: 'box', unit_of_measurement: 'min' } } },
    { name: 'talk_stream', selector: { text: {} } },
    { name: 'ring_popup', selector: { boolean: {} } },
  ],
  labels: {
    entity: 'Camera',
    name: 'Name (optional)',
    light: 'Light by the camera (optional)',
    refresh_after: 'Ask for a new snapshot when the picture is older than',
    talk_stream: 'Two-way audio stream (optional, go2rtc stream name)',
    ring_popup: 'Open the live view by itself when the doorbell rings',
  },
  helpers: {
    talk_stream: 'A go2rtc stream with two-way audio (e.g. front_door). Adds Talk, and lets Answer talk.',
  },
});

export class CameraExperimentalCard extends CameraCard {
  // ---- the tile: the stable tile, with why/when on top, and an Answer button when it rings
  _render(timer = false) {
    super._render(timer);
    if (!this._built || !this._tile) return;
    if (!this.querySelector('style.cc-exp')) this.insertAdjacentHTML('afterbegin', `<style class="cc-exp">${EXP_CSS}</style>`);
    if (!this._topRow) {
      const row = document.createElement('div');
      row.className = 'cc-top-row';
      row.append(this.querySelector('.cc-chip'), this.querySelector('.cc-tr'));
      this._tile.insertBefore(row, this._tile.querySelector('.cc-ov'));
      this._topRow = row;
      const ans = document.createElement('button');
      ans.className = 'cc-answer';
      ans.style.display = 'none';
      ans.innerHTML = `${iconHtml('mdi:phone', { size: '18px' })}Answer`;
      hydrateIcons(ans);
      this.querySelector('.cc-ov').appendChild(ans);
      this._tile.addEventListener(
        'click',
        (ev) => {
          if (ev.target.closest('.cc-answer')) {
            ev.stopPropagation();
            this._open(true);
          }
        },
        true,
      );
    }
    const ring = this._config_ringing();
    this._tile.classList.toggle('ringing', ring);
    this.querySelector('.cc-answer').style.display = ring ? '' : 'none';
    if (ring && !this._wasRinging && !this._pop && this.config.ring_popup) this._open(false);
    this._wasRinging = ring;
  }

  // The names that go with the camera, beyond what the stable card finds.
  _ex() {
    const s = (this._hass && this._hass.states) || {};
    const base = String(this.config.entity || '').replace(/^camera\./, '').replace(/_(live_view|snapshot|last_recording)$/, '');
    const signal = [`sensor.${base}_wireless`, `sensor.${base}_wifi_signal_strength`, `sensor.${base}_wifi_signal_category`].find((id) => s[id]);
    return { base, signal, ringSensor: s[`binary_sensor.${base}_ding`] ? `binary_sensor.${base}_ding` : null };
  }

  _config_ringing() {
    const h = this._hass;
    const f = this._f;
    if (!h || !f) return false;
    const x = this._ex();
    if (!x.ringSensor && !f.ding) return false;
    return ringing(x.ringSensor && h.states[x.ringSensor].state, f.ding && h.states[f.ding].state, Date.now(), this._answered || 0);
  }

  // ---- the live view
  // answer: opened from Answer, so the sound is on and (with talk_stream) so is the microphone.
  _open(answer = false) {
    if (this._pop) return;
    const c = this.config;
    const key = c.entity || 'demo';
    const content = document.createElement('div');
    content.innerHTML = `<div class="cc-pop-media"></div><div class="cc-slot-below"></div><div class="cc-btns"></div><div class="cc-note" hidden></div>`;
    this._popEl = content;
    this._expanded = loadView(key) === 'expanded';
    this._full = false;
    this._talking = !!(answer && c.talk_stream);
    this._sound = !!answer;
    this._started = Date.now();
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
        clearTimeout(this._hideT);
        clearInterval(this._clock);
        this._pop = null;
        this._popEl = null;
      },
    });
    const box = content.querySelector('.cc-pop-media');
    this._box = box;
    this._build(box);
    box.addEventListener('cc-fullscreen', (ev) => {
      this._full = !!ev.detail;
      this._mode();
    });
    this._wireBox(box);
    content.querySelector('.cc-btns').addEventListener('click', (ev) => this._act(ev));
    this._clock = setInterval(() => this._tickClock(), 1000);
    this._mode();
    this._media();
    this._renderPop();
  }

  // The buttons over the picture and under it, made once; _mode() moves the call row to where it belongs.
  _build(box) {
    const circ = (cls, icon, label, act, size = 22) => `<div class="lab"><button class="cc-circle ${cls}" data-act="${act}" aria-label="${label}">${iconHtml(icon, { size: `${size}px` })}</button><span class="t">${label}</span></div>`;
    this._circ = circ;
    box.innerHTML = `<div class="cc-ui cc-ov-tr"></div>
      <div class="cc-ui cc-ov-call hideable"></div>
      <div class="cc-ui cc-ov-corner hideable"><button class="cc-circle sm" data-act="expand" aria-label="Bigger">${iconHtml('mdi:arrow-expand-vertical', { size: '16px' })}</button><button class="cc-circle sm" data-act="full" aria-label="Full screen">${iconHtml('mdi:fullscreen', { size: '16px' })}</button></div>
      <div class="cc-ui cc-ov-plus hideable"><div class="cc-menu" hidden></div><button class="cc-circle" data-act="more" aria-label="More">${iconHtml('mdi:plus', { size: '22px' })}</button></div>`;
    hydrateIcons(box);
    this._callEl = document.createElement('div');
    this._callEl.className = 'cc-circles';
    // Over the picture the box's own listener handles it; under it, this one.
    this._callEl.addEventListener('click', (ev) => !box.contains(this._callEl) && this._act(ev));
    box.addEventListener('click', (ev) => this._act(ev));
  }

  // One tap on the picture (not a drag) shows or hides the buttons; they hide after 3 s.
  _wireBox(box) {
    let down = null;
    box.addEventListener('pointerdown', (ev) => (down = [ev.clientX, ev.clientY]));
    box.addEventListener('click', (ev) => {
      if (ev.target.closest('.cc-ui')) return this._showUi();
      if (down && Math.hypot(ev.clientX - down[0], ev.clientY - down[1]) > 8) return; // a drag
      if (this._modeName() === 'normal') return;
      box.classList.contains('cc-hidec') ? this._showUi() : this._hideUi();
    });
  }

  _showUi() {
    const box = this._box;
    if (!box) return;
    box.classList.remove('cc-hidec');
    clearTimeout(this._hideT);
    if (this._modeName() !== 'normal') this._hideT = setTimeout(() => this._hideUi(), HIDE_AFTER);
  }

  _hideUi() {
    if (this._box && this._modeName() !== 'normal') {
      const menu = this._box.querySelector('.cc-menu');
      if (menu) menu.hidden = true;
      this._box.classList.add('cc-hidec');
    }
  }

  _modeName() {
    return this._full ? 'full' : this._expanded ? 'expanded' : 'normal';
  }

  // Put the call row where this mode wants it, and show or hide what only some modes have.
  _mode() {
    const box = this._box;
    if (!box) return;
    const mode = this._modeName();
    box.dataset.mode = mode;
    const call = box.querySelector('.cc-ov-call');
    const below = this._popEl.querySelector('.cc-slot-below');
    const over = mode !== 'normal';
    // Normal: Hang up / Talk / Sound under the picture. Expanded and full: over it.
    if (over) call.appendChild(this._callEl);
    else below.appendChild(this._callEl);
    box.querySelector('.cc-ov-corner').style.display = mode === 'full' ? 'none' : '';
    box.querySelector('.cc-ov-plus').style.display = mode === 'full' ? '' : 'none';
    below.style.display = over ? 'none' : '';
    this._popEl.querySelector('.cc-btns').style.display = over && mode === 'full' ? 'none' : '';
    const corner = box.querySelector('[data-act="expand"]');
    corner.innerHTML = iconHtml(this._expanded ? 'mdi:arrow-collapse-vertical' : 'mdi:arrow-expand-vertical', { size: '16px' });
    corner.setAttribute('aria-label', this._expanded ? 'Smaller' : 'Bigger');
    hydrateIcons(corner);
    // In normal the buttons on the picture stay; elsewhere they come and go.
    box.classList.remove('cc-hidec');
    this._showUi();
    this._renderPop();
  }

  _act(ev) {
    const b = ev.target.closest('[data-act]');
    if (!b || b.disabled) return;
    const act = b.dataset.act;
    const key = this.config.entity || 'demo';
    const h = this._hass;
    if (act === 'hang') this._pop.close();
    else if (act === 'sound') {
      this._sound = !this._sound;
      this._applySound();
      this._renderPop();
    } else if (act === 'talk') {
      this._talking = !this._talking;
      if (this._talking) this._sound = true;
      this._answered = Date.now();
      this._media();
      this._renderPop();
    } else if (act === 'answer') {
      this._answered = Date.now();
      this._sound = true;
      if (this.config.talk_stream) this._talking = true;
      this._media();
      this._renderPop();
    } else if (act === 'expand') {
      this._expanded = !this._expanded;
      saveView(key, this._expanded ? 'expanded' : 'normal');
      this._mode();
    } else if (act === 'full') {
      toggleFullscreen(this._box);
    } else if (act === 'more') {
      const menu = this._box.querySelector('.cc-menu');
      menu.hidden = !menu.hidden;
      clearTimeout(this._hideT);
      if (menu.hidden) this._showUi();
    } else if (act === 'events') {
      const base = cameraBase(h, this.config.entity);
      this._pop.close().then(() => base && openCameraEvents(this, h, base, { title: this._name(), aspect: this._aspect() }));
    } else if (act === 'snap') this._maybeRefresh(true);
    else if (act === 'light' && this._f.light) h.callService('light', 'toggle', { entity_id: this._f.light });
  }

  _tickClock() {
    const t = this._popEl && this._popEl.querySelector('.cc-timer');
    if (!t) return;
    const s = Math.floor((Date.now() - this._started) / 1000);
    t.textContent = `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
  }

  // The sound button: the playing video's own muted flag.
  _applySound() {
    const el = this._el;
    if (!el) return;
    const vid = findVideo(el.shadowRoot || el);
    if ('muted' in el) el.muted = !this._sound;
    if (vid && vid !== el) vid.muted = !this._sound;
    if (vid && this._sound && vid.paused) vid.play().catch(() => {});
  }

  // Live video: the go2rtc stream (with the microphone while talking) when the camera has one, else Home Assistant's own live view, else the picture.
  async _media() {
    const box = this._box;
    if (!box || !this._popEl) return;
    const c = this.config;
    box.querySelectorAll(':scope > .cc-tag').forEach((n) => n.remove());
    const noMic = this._talking && !(navigator.mediaDevices && navigator.mediaDevices.getUserMedia);
    let el;
    const viaGo2rtc = c.talk_stream && !this._go2rtcBad && customElements.get('webrtc-camera');
    if (viaGo2rtc) {
      el = document.createElement('webrtc-camera');
      el.setConfig({ url: c.talk_stream, media: this._talking && !noMic ? 'video,audio,microphone' : 'video,audio', muted: !this._sound, ui: false });
      el.hass = this._hass;
    } else {
      try {
        el = await liveElement(this._hass, this._f && this._f.live);
        el.muted = !this._sound;
      } catch (err) {
        el = document.createElement('img');
        if (this._img && this._img.src) el.src = this._img.src;
      }
    }
    if (!this._popEl) return;
    this._el = el;
    // A 16:9 box, covering it: a tall picture is cropped and can be dragged up and down.
    stageMedia(box, el, this._aspect(), { mute: false });
    const tag = stageTag(box, noMic ? '● No microphone (needs https)' : '● Connecting…', noMic ? '#c62828' : '#616875');
    const live = this._talking ? '● LIVE · talking' : '● LIVE';
    let tries = 0;
    const check = () => {
      if (!tag.isConnected) return;
      const v = findVideo(el.shadowRoot || el);
      if (v && v.localName === 'video' && !v.paused && v.readyState >= 2) {
        if (!noMic) tag.textContent = live;
        tag.style.background = noMic ? '#c62828' : '#e53935';
        this._applySound();
      } else if (viaGo2rtc && tries++ > 24) {
        // The go2rtc stream isn't there (not set up, or not reachable): carry on with Home Assistant's own live view, listen-only.
        this._go2rtcBad = true;
        this._talking = false;
        this._media();
        this._renderPop();
      } else if (tries++ < 120) setTimeout(check, 500);
      else tag.textContent = '● Still picture';
    };
    check();
  }

  _renderPop() {
    const p = this._popEl;
    if (!p) return;
    const c = this.config;
    const h = this._hass;
    const f = this._f || {};
    const x = this._ex();
    const mode = this._modeName();

    // Battery and signal: on the picture, only when it is expanded or full screen.
    const tr = p.querySelector('.cc-ov-tr');
    const bat = f.battery && h ? Number(h.states[f.battery].state) : NaN;
    const sg = x.signal && h ? h.states[x.signal] : null;
    const bars = sg ? signalBars(sg.state, x.signal.endsWith('_category') ? sg.state : '') : -1;
    const trHtml =
      mode === 'normal'
        ? ''
        : `${!isNaN(bat) ? `<div class="cc-pill" title="Battery ${Math.round(bat)}%" style="color:${batteryColour(bat)};">${iconHtml(batteryIcon(bat), { size: '18px' })}${Math.round(bat)}%</div>` : ''}${sg ? `<div class="cc-pill" title="Signal" style="color:${signalColour(bars)};">${iconHtml(signalIcon(bars), { size: '18px' })}</div>` : ''}${mode === 'full' ? `<button class="cc-circle sm hideable cc-ui-btn" data-act="full" aria-label="Leave full screen">${iconHtml('mdi:fullscreen-exit', { size: '18px' })}</button>` : ''}`;
    if (tr.dataset.sig !== trHtml) {
      tr.dataset.sig = trHtml;
      tr.innerHTML = trHtml;
      hydrateIcons(tr);
    }

    // The call row: Answer while it rings, else Hang up / Talk / Sound.
    const ring = this._config_ringing();
    const lab = mode === 'normal';
    const callHtml = ring
      ? `<button class="cc-answer-big" data-act="answer">${iconHtml('mdi:phone', { size: '22px' })}Answer</button>`
      : `${this._circ('red', 'mdi:phone-hangup', 'Hang up', 'hang')}${c.talk_stream && !this._go2rtcBad ? this._circ(this._talking ? 'on' : '', this._talking ? 'mdi:microphone' : 'mdi:microphone-off', this._talking ? 'Talking' : 'Talk', 'talk', 20) : ''}${this._circ(this._sound ? 'on' : '', this._sound ? 'mdi:volume-high' : 'mdi:volume-off', 'Sound', 'sound', 20)}`;
    const sig = `${callHtml}|${lab}`;
    if (this._callEl.dataset.sig !== sig) {
      this._callEl.dataset.sig = sig;
      this._callEl.innerHTML = callHtml;
      this._callEl.classList.toggle('nolab', !lab);
      hydrateIcons(this._callEl);
    }
    this._callEl.querySelectorAll('.t').forEach((t) => (t.style.display = lab ? '' : 'none'));
    let timer = p.querySelector('.cc-timer');
    if (!timer) {
      timer = document.createElement('div');
      timer.className = 'cc-timer';
      p.querySelector('.cc-slot-below').prepend(timer);
      this._tickClock();
    }
    timer.style.display = lab && !ring ? '' : 'none';

    // The More menu (full screen) and the bar under the picture: Light, Snapshot, Events.
    const lightOn = f.light && h && h.states[f.light].state === 'on';
    const lightName = f.light && h ? String(h.states[f.light].attributes.friendly_name || 'Light') : '';
    const items = [];
    if (f.light) items.push([lightOn ? 'mdi:lightbulb-on' : 'mdi:lightbulb-outline', `${lightName} ${lightOn ? 'on' : 'off'}`, 'light', lightOn]);
    if (f.button) items.push(['mdi:camera', 'Snapshot', 'snap']);
    if (cameraBase(h, c.entity)) items.push(['mdi:history', 'Events', 'events']);
    const bar = items.map(([i, t, a, on]) => `<button class="cc-btn${a === 'events' ? ' main' : ''}" data-act="${a}" style="${on ? 'background:color-mix(in srgb, #ffb300 30%, transparent);' : ''}">${iconHtml(i, { size: '22px' })}${kitEsc(t)}</button>`).join('');
    const barBox = p.querySelector('.cc-btns');
    if (barBox.dataset.sig !== bar) {
      barBox.dataset.sig = bar;
      barBox.innerHTML = bar;
      hydrateIcons(barBox);
    }
    const menu = this._box.querySelector('.cc-menu');
    const menuHtml = items.map(([i, t, a]) => `<button data-act="${a}">${iconHtml(i, { size: '18px' })}${kitEsc(t)}</button>`).join('');
    if (menu.dataset.sig !== menuHtml) {
      menu.dataset.sig = menuHtml;
      menu.innerHTML = menuHtml;
      hydrateIcons(menu);
    }
    const note = p.querySelector('.cc-note');
    note.hidden = !(this._pic && this._pic.ms && Date.now() - this._pic.ms >= 60000 && mode === 'normal');
    note.textContent = this._pic && this._pic.ms ? `Picture ${this._pic.ms ? Math.round((Date.now() - this._pic.ms) / 60000) : 0} min old` : '';
  }

  static getConfigElement() {
    return document.createElement(`camera-experimental-card-editor${SUFFIX}`);
  }
}

export function registerCameraExperimentalCard() {
  if (!customElements.get(`camera-experimental-card-editor${SUFFIX}`)) customElements.define(`camera-experimental-card-editor${SUFFIX}`, CameraExperimentalEditor);
  if (!customElements.get(`camera-experimental-card${SUFFIX}`)) customElements.define(`camera-experimental-card${SUFFIX}`, CameraExperimentalCard);
  window.customCards = window.customCards || [];
  window.customCards.push({
    type: `camera-experimental-card${SUFFIX}`,
    name: `Camera Card (experimental)${LABEL}`,
    description: 'The camera card with the new live view: 16:9 video, tap-to-show buttons, bigger and landscape full screen, Answer and Talk',
    preview: true,
    documentationURL: 'https://github.com/J45PER/church-drive-cards#readme',
  });
}
