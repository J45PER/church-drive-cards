// Alarm Panel Card — status header, live entry/exit countdown, icon-only
// arm/disarm buttons, whole-card colour wash on hover only.

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

export class AlarmPanelCard extends HTMLElement {
  setConfig(config) {
    if (!config.entity && !config.demo) throw new Error('entity required (or set demo: true)');
    this.config = config;
    this._built = false;
    this._countdownTimer = null;
    this._remaining = 0;
  }

  disconnectedCallback() {
    if (this._countdownTimer) clearInterval(this._countdownTimer);
  }

  set hass(hass) {
    this._hass = hass;
    let st;
    if (this.config.demo) {
      // The editor's datetime picker gives "YYYY-MM-DD HH:MM:SS"; Safari/iOS
      // can't parse the space form, so normalise it to ISO.
      const demoTime = this.config.demo_time
        ? String(this.config.demo_time).replace(' ', 'T')
        : new Date().toISOString();
      st = {
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
    } else {
      st = hass.states[this.config.entity];
    }
    if (!st) return;

    if (!this._built) {
      this.innerHTML = `
        <ha-card style="position:relative; border:none; box-shadow:0 3px 10px rgba(0,0,0,0.45); border-radius:16px; overflow:hidden; padding:20px 20px 18px 20px;">
          <style>
            ha-card::before {
              content:''; position:absolute; inset:0; background: var(--card-tint, transparent);
              opacity:0; transition: opacity .2s ease; pointer-events:none;
            }
            ha-card:hover::before { opacity:0.16; }
            .apc-btn { position:relative; overflow:hidden; }
            .apc-btn::after {
              content:''; position:absolute; inset:0; background: var(--btn-tint, #fff);
              opacity:0; transition: opacity .15s ease; pointer-events:none; border-radius:inherit;
            }
            .apc-btn:hover::after { opacity:0.18; }
          </style>
          <div class="apc-header" style="position:relative; display:flex; align-items:center; gap:16px;">
            <ha-icon class="apc-icon" style="--mdc-icon-size:48px; flex-shrink:0;"></ha-icon>
            <div style="flex:1; min-width:0;">
              <div class="apc-title" style="font-size:1.5rem; font-weight:600;"></div>
              <div class="apc-sub" style="font-size:0.9rem; color:var(--secondary-text-color);"></div>
            </div>
          </div>
          <div class="apc-countdown" style="position:relative; display:none; text-align:center; font-size:2.75rem; font-weight:700; margin:14px 0 4px 0;"></div>
          <div class="apc-countdown-label" style="position:relative; display:none; text-align:center; font-size:0.85rem; color:var(--secondary-text-color); margin-bottom:8px;"></div>
          <div class="apc-buttons" style="position:relative; display:flex; gap:10px; margin-top:14px;"></div>
        </ha-card>`;
      this._card = this.querySelector('ha-card');
      this._icon = this.querySelector('.apc-icon');
      this._title = this.querySelector('.apc-title');
      this._sub = this.querySelector('.apc-sub');
      this._countdown = this.querySelector('.apc-countdown');
      this._countdownLabel = this.querySelector('.apc-countdown-label');
      this._buttons = this.querySelector('.apc-buttons');
      this._built = true;
    }

    // Colour scheme: Disarmed = green, Armed Home = blue, Armed Away = red, Armed Night = purple.
    // Exit delay (arming, about to arm) = amber. Entry delay (pending, someone's come in while
    // armed — the urgent one) = deep orange, distinct from the calmer exit-delay amber.
    const stateInfo = {
      disarmed: { label: 'Disarmed', icon: 'mdi:shield-off-outline', color: 'var(--success-color, #43a047)' },
      armed_home: { label: 'Armed Home', icon: 'mdi:shield-home', color: '#2196f3' },
      armed_away: { label: 'Armed Away', icon: 'mdi:shield-lock', color: 'var(--error-color, #db4437)' },
      armed_night: { label: 'Armed Night', icon: 'mdi:shield-moon', color: '#7e57c2' },
      arming: { label: 'Arming', icon: 'mdi:shield-sync', color: '#ff9800' },
      pending: { label: 'Entry Delay', icon: 'mdi:shield-sync', color: '#ff5722' },
      triggered: { label: 'Triggered!', icon: 'mdi:shield-alert', color: 'var(--error-color, #db4437)' },
    };
    const info = stateInfo[st.state] || { label: st.state, icon: 'mdi:shield-question', color: '#9e9e9e' };

    // Whole-card tint is hover-only (see ha-card::before above) — resting state stays the
    // normal card background, no colour wash unless you're actually hovering the card.
    this._card.style.setProperty('--card-tint', info.color);

    this._icon.setAttribute('icon', info.icon);
    this._icon.style.color = info.color;
    this._title.textContent = info.label + (this.config.demo ? ' (demo)' : '');
    this._title.style.color = info.color;

    let sub = '';
    const fmt = (iso) => new Date(iso).toLocaleString('en-GB', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' });
    if (st.state === 'disarmed' && st.attributes.lastDisarmedBy && st.attributes.lastDisarmedTime) {
      sub = `Disarmed by ${st.attributes.lastDisarmedBy}, ${fmt(st.attributes.lastDisarmedTime)}`;
    } else if (st.attributes.lastArmedBy && st.attributes.lastArmedTime) {
      sub = `Armed by ${st.attributes.lastArmedBy}, ${fmt(st.attributes.lastArmedTime)}`;
    }
    this._sub.textContent = sub;

    const entrySecs = st.attributes.entrySecondsLeft || 0;
    const exitSecs = st.attributes.exitSecondsLeft || 0;
    const secsLeft = st.state === 'pending' ? entrySecs : (st.state === 'arming' ? exitSecs : 0);
    const label = st.state === 'pending' ? 'until alarm triggers' : (st.state === 'arming' ? 'until armed' : '');
    this._syncCountdown(secsLeft, st.state, label, info.color);

    // Which button should highlight: during a delay, use the mode being armed/currently armed to
    // (targetState), not the literal 'arming'/'pending' state string, so the right shield lights up.
    const activeKey = (st.state === 'arming' || st.state === 'pending') ? st.attributes.targetState : st.state;
    const activeColor = info.color;

    this._buttons.innerHTML = '';
    const feats = st.attributes.supported_features || 0;
    const actions = [
      { key: 'disarmed', icon: 'mdi:shield-off-outline', title: 'Disarm', service: 'alarm_disarm', show: true, color: stateInfo.disarmed.color },
      { key: 'armed_home', icon: 'mdi:shield-home', title: 'Arm Home', service: 'alarm_arm_home', show: (feats & 1) !== 0, color: stateInfo.armed_home.color },
      { key: 'armed_away', icon: 'mdi:shield-lock', title: 'Arm Away', service: 'alarm_arm_away', show: (feats & 2) !== 0, color: stateInfo.armed_away.color },
      { key: 'armed_night', icon: 'mdi:shield-moon', title: 'Arm Night', service: 'alarm_arm_night', show: (feats & 4) !== 0, color: stateInfo.armed_night.color },
    ];
    actions.filter((a) => a.show).forEach((a) => {
      const active = activeKey === a.key;
      const btn = document.createElement('button');
      btn.className = 'apc-btn';
      btn.title = a.title;
      btn.setAttribute('aria-label', a.title);
      btn.style.cssText = `--btn-tint:${a.color}; flex:1; display:flex; align-items:center; justify-content:center; padding:12px 8px; border-radius:10px; border:none; cursor:pointer; background:${active ? activeColor : 'rgba(255,255,255,0.08)'};`;
      const ic = document.createElement('ha-icon');
      ic.setAttribute('icon', a.icon);
      ic.style.cssText = `color:${active ? '#fff' : 'var(--primary-text-color)'}; --mdc-icon-size:24px; position:relative; z-index:1;`;
      btn.appendChild(ic);
      if (!this.config.demo) {
        btn.addEventListener('click', () => {
          this._hass.callService('alarm_control_panel', a.service, {}, { entity_id: this.config.entity });
        });
      } else {
        btn.style.opacity = '0.6';
        btn.style.cursor = 'default';
      }
      this._buttons.appendChild(btn);
    });
  }

  _syncCountdown(secsLeft, state, label, color) {
    const active = (state === 'pending' || state === 'arming') && secsLeft > 0;
    this._countdownShown = active;
    if (!active) {
      this._countdown.style.display = 'none';
      this._countdownLabel.style.display = 'none';
      if (this._countdownTimer) {
        clearInterval(this._countdownTimer);
        this._countdownTimer = null;
      }
      return;
    }
    this._countdown.style.display = 'block';
    this._countdown.style.color = color;
    this._countdownLabel.style.display = 'block';
    this._countdownLabel.textContent = label;
    this._remaining = secsLeft;
    this._renderCountdown();
    if (this._countdownTimer) clearInterval(this._countdownTimer);
    if (!this.config.demo) {
      this._countdownTimer = setInterval(() => {
        this._remaining = Math.max(0, this._remaining - 1);
        this._renderCountdown();
        if (this._remaining <= 0) {
          clearInterval(this._countdownTimer);
          this._countdownTimer = null;
        }
      }, 1000);
    }
  }

  _renderCountdown() {
    const m = Math.floor(this._remaining / 60);
    const s = this._remaining % 60;
    this._countdown.textContent = `${m}:${s.toString().padStart(2, '0')}`;
  }

  // Header + buttons ~3 units; the countdown adds ~2 while it's showing.
  getCardSize() {
    return this._countdownShown ? 5 : 3;
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
