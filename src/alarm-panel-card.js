// Alarm Panel Card: the status as the card title, a countdown ring and timer
// during entry/exit delays, and arm/disarm buttons.

import { createFormEditor } from './form-editor.js';
import { SUFFIX, LABEL } from './suffix.js';

const APC_STATE_OPTIONS = [
  { value: 'disarmed', label: 'Disarmed' },
  { value: 'armed_home', label: 'Armed Home' },
  { value: 'armed_away', label: 'Armed Away' },
  { value: 'armed_night', label: 'Armed Night' },
  { value: 'arming', label: 'Arming (exit delay)' },
  { value: 'pending', label: 'Entry delay' },
  { value: 'triggered', label: 'Triggered' },
];

export const AlarmPanelCardEditor = createFormEditor({
  schema: () => [
    { name: 'entity', selector: { entity: { domain: 'alarm_control_panel' } } },
    {
      type: 'expandable',
      name: '',
      title: 'Demo mode (fake data, buttons do nothing)',
      flatten: true,
      schema: [
        { name: 'demo', selector: { boolean: {} } },
        { name: 'demo_state', selector: { select: { mode: 'dropdown', options: APC_STATE_OPTIONS } } },
        { name: 'demo_target_state', selector: { select: { mode: 'dropdown', options: APC_STATE_OPTIONS.slice(1, 4) } } },
        { name: 'demo_countdown', selector: { number: { mode: 'box', min: 0, unit_of_measurement: 's' } } },
        { name: 'demo_by', selector: { text: {} } },
        { name: 'demo_time', selector: { datetime: {} } },
        { name: 'demo_supported_features', selector: { number: { mode: 'box', min: 0 } } },
      ],
    },
  ],
  labels: {
    entity: 'Alarm entity',
    demo: 'Use demo data instead of the entity',
    demo_state: 'Demo state',
    demo_target_state: 'Mode being armed to (during a delay)',
    demo_countdown: 'Countdown',
    demo_by: 'Armed/disarmed by',
    demo_time: 'Armed/disarmed at',
    demo_supported_features: 'Supported features',
  },
  helpers: {
    demo_supported_features: 'Bitmask: 1 = Arm Home, 2 = Arm Away, 4 = Arm Night (default 3)',
  },
});

// Status colours: Disarmed green, Armed Home blue, Armed Away red, Armed Night
// purple. Exit delay (arming) amber; entry delay (pending, someone's come in
// while armed, the urgent one) deep orange.
const APC_STATES = {
  disarmed: { label: 'Disarmed', icon: 'mdi:shield-off-outline', color: 'var(--success-color, #43a047)' },
  armed_home: { label: 'Armed Home', icon: 'mdi:shield-home', color: '#2196f3' },
  armed_away: { label: 'Armed Away', icon: 'mdi:shield-lock', color: 'var(--error-color, #db4437)' },
  armed_night: { label: 'Armed Night', icon: 'mdi:shield-moon', color: '#7e57c2' },
  arming: { label: 'Arming', icon: 'mdi:shield-sync', color: '#ff9800' },
  pending: { label: 'Entry Delay', icon: 'mdi:shield-sync', color: '#ff5722' },
  triggered: { label: 'Triggered!', icon: 'mdi:shield-alert', color: 'var(--error-color, #db4437)' },
};
const APC_MODE_NAMES = { armed_home: 'Home', armed_away: 'Away', armed_night: 'Night' };
const APC_RING_R = 32;
const APC_RING_LEN = 2 * Math.PI * APC_RING_R;

// Layout (the same in every state, so the card never changes size):
//   the status as the card title, in its colour;
//   a ring round the shield (it empties during a delay), what's happening,
//   and the timer on the right;
//   four 56px mode buttons, icon over label.
// During a delay the card fades from its normal background towards the state
// colour as time runs out; when triggered it's fully tinted.
export class AlarmPanelCard extends HTMLElement {
  setConfig(config) {
    if (!config.entity && !config.demo) throw new Error('entity required (or set demo: true)');
    this.config = config;
    this._built = false;
    this._countdownTimer = null;
    this._remaining = 0;
    this._total = 0;
  }

  disconnectedCallback() {
    if (this._countdownTimer) clearInterval(this._countdownTimer);
    this._countdownTimer = null;
  }

  _state(hass) {
    if (!this.config.demo) return hass.states[this.config.entity];
    // The editor's datetime picker gives "YYYY-MM-DD HH:MM:SS"; Safari/iOS
    // can't parse the space form, so normalise it to ISO.
    const demoTime = this.config.demo_time ? String(this.config.demo_time).replace(' ', 'T') : new Date().toISOString();
    return {
      state: this.config.demo_state || 'armed_away',
      attributes: {
        supported_features: this.config.demo_supported_features !== undefined ? this.config.demo_supported_features : 3,
        targetState: this.config.demo_target_state || 'armed_away',
        lastArmedBy: this.config.demo_by || 'Demo User',
        lastArmedTime: demoTime,
        lastDisarmedBy: this.config.demo_by || 'Demo User',
        lastDisarmedTime: demoTime,
        entrySecondsLeft: this.config.demo_countdown || 0,
        exitSecondsLeft: this.config.demo_countdown || 0,
      },
    };
  }

  _build() {
    this.innerHTML = `
      <ha-card class="apc-card" style="border:none; box-shadow:0 3px 10px rgba(0,0,0,0.45); border-radius:16px; overflow:hidden; padding:16px; background:var(--card-background-color); transition:background-color .8s ease;">
        <style>
          .apc-btn { position:relative; overflow:hidden; flex:1 1 0; min-width:0; height:56px; border:none; border-radius:12px; cursor:pointer;
            display:flex; flex-direction:column; align-items:center; justify-content:center; gap:2px; padding:0 4px;
            background:rgba(127,127,127,0.14); color:var(--primary-text-color); font:inherit; font-size:11px; font-weight:600; }
          .apc-btn.apc-on { color:#fff; }
          .apc-btn span { position:relative; z-index:1; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; max-width:100%; }
          .apc-btn ha-icon { position:relative; z-index:1; --mdc-icon-size:22px; }
          .apc-btn::after { content:''; position:absolute; inset:0; background:var(--btn-tint, #fff); opacity:0; transition:opacity .15s ease; pointer-events:none; }
          .apc-btn:hover::after { opacity:0.18; }
          .apc-btn:focus-visible { outline:2px solid var(--primary-color); outline-offset:2px; }
        </style>
        <div class="apc-title" style="font-size:1.5rem; font-weight:500; line-height:1.2; padding:0 0 10px 0;"></div>
        <div style="display:flex; align-items:center; gap:14px;">
          <div style="position:relative; width:72px; height:72px; flex:none;">
            <svg viewBox="0 0 72 72" style="width:72px; height:72px; transform:rotate(-90deg);" aria-hidden="true">
              <circle cx="36" cy="36" r="${APC_RING_R}" fill="none" stroke="rgba(127,127,127,0.25)" stroke-width="5"></circle>
              <circle class="apc-ring" cx="36" cy="36" r="${APC_RING_R}" fill="none" stroke-width="5" stroke-linecap="round"
                stroke-dasharray="${APC_RING_LEN}" stroke-dashoffset="0" style="transition:stroke-dashoffset 1s linear;"></circle>
            </svg>
            <ha-icon class="apc-icon" style="position:absolute; inset:0; margin:auto; width:32px; height:32px; --mdc-icon-size:32px;"></ha-icon>
          </div>
          <div style="flex:1; min-width:0;">
            <div class="apc-line1" style="font-size:0.95rem; color:var(--primary-text-color);"></div>
            <div class="apc-line2" style="font-size:0.85rem; color:var(--secondary-text-color);"></div>
          </div>
          <div class="apc-countdown" style="flex:none; font-size:2rem; font-weight:700; font-variant-numeric:tabular-nums; visibility:hidden;">0:00</div>
        </div>
        <div class="apc-buttons" style="display:flex; gap:8px; margin-top:12px;"></div>
      </ha-card>`;
    const q = (sel) => this.querySelector(sel);
    this._card = q('.apc-card');
    this._title = q('.apc-title');
    this._ring = q('.apc-ring');
    this._icon = q('.apc-icon');
    this._line1 = q('.apc-line1');
    this._line2 = q('.apc-line2');
    this._countdown = q('.apc-countdown');
    this._buttons = q('.apc-buttons');
    this._built = true;
  }

  set hass(hass) {
    this._hass = hass;
    const st = this._state(hass);
    if (!st) return;
    if (!this._built) this._build();

    const info = APC_STATES[st.state] || { label: st.state, icon: 'mdi:shield-outline', color: '#9e9e9e' };
    const inDelay = st.state === 'arming' || st.state === 'pending';
    const target = APC_MODE_NAMES[st.attributes.targetState];
    this._color = info.color;
    this._stateName = st.state;

    const label = st.state === 'arming' && target ? `Arming ${target}` : info.label;
    this._title.textContent = label + (this.config.demo ? ' (demo)' : '');
    this._title.style.color = info.color;
    this._icon.setAttribute('icon', info.icon);
    this._icon.style.color = info.color;
    this._ring.style.stroke = info.color;
    this._countdown.style.color = info.color;

    // What's happening: what to do during a delay, otherwise who and when.
    const fmt = (iso) => new Date(iso).toLocaleString('en-GB', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' });
    const a = st.attributes;
    let line1 = '';
    let line2 = '';
    if (st.state === 'arming') [line1, line2] = ['Leave now', 'until armed'];
    else if (st.state === 'pending') [line1, line2] = ['Disarm now', 'until the alarm sounds'];
    else if (st.state === 'triggered') [line1, line2] = ['Alarm sounding', 'Disarm to stop it'];
    else if (st.state === 'disarmed' && a.lastDisarmedBy && a.lastDisarmedTime) [line1, line2] = [`Disarmed by ${a.lastDisarmedBy}`, fmt(a.lastDisarmedTime)];
    else if (a.lastArmedBy && a.lastArmedTime) [line1, line2] = [`Armed by ${a.lastArmedBy}`, fmt(a.lastArmedTime)];
    this._line1.textContent = line1;
    this._line2.textContent = line2;

    const secsLeft = st.state === 'pending' ? a.entrySecondsLeft || 0 : st.state === 'arming' ? a.exitSecondsLeft || 0 : 0;
    this._syncCountdown(inDelay ? secsLeft : 0, st.state);

    // During a delay, light the button of the mode being armed to (targetState).
    const activeKey = inDelay || st.state === 'triggered' ? a.targetState : st.state;
    this._renderButtons(a.supported_features || 0, activeKey, info.color);
  }

  _renderButtons(feats, activeKey, activeColor) {
    const actions = [
      { key: 'disarmed', icon: 'mdi:shield-off-outline', title: 'Disarm', service: 'alarm_disarm', show: true },
      { key: 'armed_home', icon: 'mdi:shield-home', title: 'Home', service: 'alarm_arm_home', show: (feats & 1) !== 0 },
      { key: 'armed_away', icon: 'mdi:shield-lock', title: 'Away', service: 'alarm_arm_away', show: (feats & 2) !== 0 },
      { key: 'armed_night', icon: 'mdi:shield-moon', title: 'Night', service: 'alarm_arm_night', show: (feats & 4) !== 0 },
    ].filter((b) => b.show);
    const sig = JSON.stringify([actions.map((b) => b.key), activeKey, activeColor, !!this.config.demo]);
    if (sig === this._buttonsSig) return;
    this._buttonsSig = sig;
    this._buttons.innerHTML = '';
    actions.forEach((b) => {
      const active = activeKey === b.key;
      const btn = document.createElement('button');
      btn.className = `apc-btn${active ? ' apc-on' : ''}`;
      btn.title = b.key === 'disarmed' ? 'Disarm' : `Arm ${b.title}`;
      btn.setAttribute('aria-label', btn.title);
      btn.style.setProperty('--btn-tint', (APC_STATES[b.key] || {}).color || '#fff');
      if (active) btn.style.background = activeColor;
      btn.innerHTML = `<ha-icon icon="${b.icon}"></ha-icon><span>${b.title}</span>`;
      if (this.config.demo) {
        btn.style.opacity = '0.6';
        btn.style.cursor = 'default';
      } else {
        btn.addEventListener('click', () =>
          this._hass.callService('alarm_control_panel', b.service, {}, { entity_id: this.config.entity })
        );
      }
      this._buttons.appendChild(btn);
    });
  }

  _syncCountdown(secsLeft, state) {
    const active = secsLeft > 0;
    if (!active) {
      if (this._countdownTimer) clearInterval(this._countdownTimer);
      this._countdownTimer = null;
      this._remaining = 0;
      this._total = 0;
      this._reported = null;
      this._delayState = null;
      this._paint();
      return;
    }
    // The alarm only reports the seconds left, so the delay's full length is
    // the most seen since it started (a page opened mid-delay starts full).
    if (state !== this._delayState) {
      this._total = 0;
      this._reported = null;
    }
    this._delayState = state;
    this._total = Math.max(this._total, secsLeft);
    // hass is re-set on every change in the house: only take the alarm's
    // figure when it changes, otherwise keep counting down locally.
    if (secsLeft !== this._reported) {
      this._reported = secsLeft;
      this._remaining = secsLeft;
    }
    this._paint();
    if (this._countdownTimer) return;
    this._countdownTimer = setInterval(() => {
      // Demo mode loops the countdown so the ring and tint can be seen.
      this._remaining = this.config.demo && this._remaining <= 1 ? this._total : Math.max(0, this._remaining - 1);
      this._paint();
      if (this._remaining <= 0) {
        clearInterval(this._countdownTimer);
        this._countdownTimer = null;
      }
    }, 1000);
  }

  // Timer, ring and background for the time left.
  _paint() {
    const counting = this._remaining > 0 && this._total > 0;
    const frac = counting ? this._remaining / this._total : 1;
    const m = Math.floor(this._remaining / 60);
    const s = this._remaining % 60;
    this._countdown.textContent = `${m}:${String(s).padStart(2, '0')}`;
    this._countdown.style.visibility = counting ? 'visible' : 'hidden';
    this._ring.style.strokeDashoffset = String(APC_RING_LEN * (1 - frac));
    // Grey to the state colour as a delay runs out; fully tinted when triggered.
    let tint = 0;
    if (this._stateName === 'triggered') tint = 32;
    else if (counting) tint = Math.round(6 + 26 * (1 - frac));
    this._card.style.backgroundColor = tint
      ? `color-mix(in srgb, ${this._color} ${tint}%, var(--card-background-color))`
      : 'var(--card-background-color)';
  }

  // Same size in every state: title, ring row and buttons.
  getCardSize() {
    return 4;
  }

  // Sections-view defaults; the editor's Layout tab can override them.
  getGridOptions() {
    return { columns: 12, min_columns: 6, rows: 'auto' };
  }

  static getConfigElement() {
    return document.createElement(`alarm-panel-card-editor${SUFFIX}`);
  }

  // Pre-fill the card picker with the first real alarm entity.
  static getStubConfig(hass) {
    const first = hass && Object.keys(hass.states).find((id) => id.startsWith('alarm_control_panel.'));
    return first ? { entity: first } : { demo: true };
  }
}

export function registerAlarmPanelCard() {
  if (!customElements.get(`alarm-panel-card-editor${SUFFIX}`)) {
    customElements.define(`alarm-panel-card-editor${SUFFIX}`, AlarmPanelCardEditor);
  }
  if (!customElements.get(`alarm-panel-card${SUFFIX}`)) {
    customElements.define(`alarm-panel-card${SUFFIX}`, AlarmPanelCard);
  }
  window.customCards = window.customCards || [];
  window.customCards.push({
    type: `alarm-panel-card${SUFFIX}`,
    name: `Alarm Panel Card${LABEL}`,
    description: 'Alarm status, entry/exit countdown, and arm/disarm controls',
    preview: true,
    documentationURL: 'https://github.com/J45PER/church-drive-cards#readme',
  });
}
