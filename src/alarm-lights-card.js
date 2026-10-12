// Alarm Lights Card (Manager): what the lights do when an alarm goes off.
//
// Two kinds of alarm, two answers (the rules and the playing are in the Church
// Drive integration, alarm_lights.py; this page sets them and tests them):
//   - Fire, smoke, heat or carbon monoxide: the strobe lights pulse red and amber;
//     at night the constant lights come on bright as well. Green afterwards.
//   - The house alarm: set to home, red and blue strobes and the constant lights;
//     set to away, red strobes only. Blue when it's turned off.
// Each light is a Strobe light, a Constant light, or neither. Day or night is read
// from the chosen light sensors for the whole house. The test buttons play any
// of it on the lights alone: no alarm, no notification, then the lights go back.
// Only administrators can change anything.

import { createFormEditor } from './form-editor.js';
import { iconHtml, hydrateIcons } from './icons.js';
import { SUFFIX, LABEL } from './suffix.js';
import { kitShell, kitHead, kitEsc } from './card-kit.js';

const WS = 'church_drive/alarm_lights';
const EVENT = 'church_drive_alarm_lights_changed';
const COLOUR = { red: '#ff1744', amber: '#ff9100', blue: '#2979ff', green: '#00e676' };

const RULES = [
  { value: 'any', label: 'Any one sensor' },
  { value: 'half', label: 'At least half of them' },
  { value: 'all', label: 'All of them' },
];

const TESTS = [
  { key: 'fire', label: 'Fire', icon: 'mdi:fire', colours: ['red', 'amber'] },
  { key: 'security_home', label: 'Alarm, home', icon: 'mdi:shield-home', colours: ['red', 'blue'] },
  { key: 'security_away', label: 'Alarm, away', icon: 'mdi:shield-lock', colours: ['red'] },
  { key: 'fire_ended', label: 'Fire over', icon: 'mdi:check-circle', colours: ['green'] },
  { key: 'disarmed', label: 'Alarm off', icon: 'mdi:shield-off', colours: ['blue'] },
];

const WHAT = [
  { name: 'Fire, smoke, heat or CO', day: 'Strobe lights pulse red and amber', night: 'Constant lights bright white, strobe lights pulse red and amber', after: 'Strobe lights green' },
  { name: 'House alarm, set to home', day: 'Strobe lights pulse red and blue, constant lights bright white', night: 'The same', after: 'Strobe lights blue' },
  { name: 'House alarm, set to away', day: 'Strobe lights pulse red', night: 'The same', after: 'Strobe lights blue' },
];

const AL_CSS = `
  .al-boxes { columns:330px; column-gap:12px; }
  .al-box { break-inside:avoid; margin-bottom:12px; padding:10px 12px 12px; border-radius:14px; background:rgba(127,127,127,0.08); display:flex; flex-direction:column; gap:8px; }
  .al-gh { font-size:0.7rem; letter-spacing:0.07em; text-transform:uppercase; font-weight:700; color:var(--secondary-text-color); }
  .al-state { display:flex; align-items:center; gap:10px; flex-wrap:wrap; }
  .al-pill { display:inline-flex; align-items:center; gap:5px; padding:3px 10px; border-radius:999px; font-size:0.78rem; font-weight:700; background:rgba(127,127,127,0.18); }
  .al-dots { display:inline-flex; gap:3px; vertical-align:middle; margin-right:5px; }
  .al-dots i { width:10px; height:10px; border-radius:50%; display:inline-block; }
  .al-what { font-size:0.8rem; line-height:1.35; }
  .al-what b { display:block; font-size:0.85rem; margin-top:6px; }
  .al-what > span { display:block; color:var(--secondary-text-color); }
  .al-row { display:flex; align-items:center; gap:8px; padding:4px 0; border-top:1px solid var(--divider-color, rgba(127,127,127,0.18)); }
  .al-row:first-child { border-top:none; }
  .al-name { flex:1; min-width:0; font-size:0.85rem; font-weight:500; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
  .al-name small { color:var(--secondary-text-color); font-weight:400; margin-left:4px; }
  .al-seg { flex:none; display:inline-flex; border-radius:9px; overflow:hidden; background:rgba(127,127,127,0.18); }
  .al-seg button { border:none; padding:5px 9px; font:inherit; font-size:0.75rem; font-weight:600; cursor:pointer; background:transparent; color:var(--primary-text-color); }
  .al-seg button[aria-pressed="true"] { color:#fff; }
  .al-seg button:disabled { cursor:default; opacity:.6; }
  .al-list { max-height:340px; overflow-y:auto; }
  .al-in { font:inherit; font-size:0.85rem; padding:6px 9px; border-radius:9px; border:1px solid var(--divider-color, rgba(127,127,127,0.3)); background:transparent; color:var(--primary-text-color); min-width:0; }
  .al-num { width:72px; }
  .al-field { display:flex; align-items:center; gap:8px; flex-wrap:wrap; font-size:0.85rem; }
  .al-field > span { flex:1 1 150px; }
  .al-chips { display:flex; flex-wrap:wrap; gap:6px; }
  .al-chip { border:none; border-radius:999px; padding:5px 11px; font:inherit; font-size:0.78rem; font-weight:600; cursor:pointer; background:rgba(127,127,127,0.18); color:var(--primary-text-color); }
  .al-chip[aria-pressed="true"] { color:#fff; }
  .al-chip:disabled { cursor:default; }
  .al-btns { display:grid; grid-template-columns:repeat(auto-fill, minmax(130px, 1fr)); gap:6px; }
  .al-btn { border:none; border-radius:12px; padding:9px 8px; font:inherit; font-size:0.82rem; font-weight:600; cursor:pointer; background:rgba(127,127,127,0.18); color:var(--primary-text-color); display:flex; align-items:center; justify-content:center; gap:6px; }
  .al-btn:disabled { opacity:.45; cursor:default; }
  .al-stop { background:#e53935; color:#fff; }
  .al-btn:focus-visible, .al-chip:focus-visible, .al-seg button:focus-visible { outline:2px solid var(--primary-color); outline-offset:2px; }
  .al-msg { font-size:0.8rem; color:#ffa726; }
  .al-on { background:#e53935; color:#fff; padding:8px 10px; border-radius:10px; font-weight:700; font-size:0.85rem; }
`;

function alDemo() {
  return {
    config: { enabled: true, strobe: ['light.living_room_ambience'], constant: ['light.kitchen', 'light.hall'], sensors: ['sensor.garden_illuminance', 'sensor.entrance_illuminance'], lux: 50, rule: 'half', green_seconds: 60, blue_seconds: 10 },
    state: 'idle',
    test: null,
    mode: null,
    day: false,
    sensors: [
      { entity_id: 'sensor.garden_illuminance', name: 'Garden Illuminance', lux: 0 },
      { entity_id: 'sensor.entrance_illuminance', name: 'Entrance Illuminance', lux: 24 },
    ],
    missing: [],
  };
}

const dots = (colours) => `<span class="al-dots">${colours.map((c) => `<i style="background:${COLOUR[c]}"></i>`).join('')}</span>`;

export const AlarmLightsCardEditor = createFormEditor({
  schema: () => [
    { name: 'title', selector: { text: {} } },
    { name: 'color', selector: { ui_color: {} } },
    { name: 'demo', selector: { boolean: {} } },
  ],
  labels: {
    title: 'Title (optional)',
    color: 'Colour',
    demo: 'Show pretend lights (for Design Presets; changes are switched off)',
  },
  helpers: {
    color: 'Default red (#e53935).',
  },
});

export class AlarmLightsCard extends HTMLElement {
  setConfig(config) {
    this.config = config || {};
    this._built = false;
    this._data = this.config.demo ? alDemo() : this._data || null;
    this._sig = null;
    this._when = this._when || 'auto';
    this._seconds = this._seconds || 20;
    this._all = !!this._all;
    this._filter = this._filter || '';
  }

  set hass(hass) {
    this._hass = hass;
    if (!this.config.demo) {
      if (!this._watching && hass.connection && hass.connection.subscribeEvents) {
        this._watching = true;
        Promise.resolve(hass.connection.subscribeEvents(() => this._load(), EVENT))
          .then((unsub) => {
            this._unsub = unsub;
          })
          .catch(() => {
            this._watching = false;
          });
      }
      // The sensors' readings are part of the picture: look again when one changes.
      const sig = ((this._data && this._data.config.sensors) || []).map((id) => (hass.states[id] || {}).state).join('|');
      if (!this._loaded || sig !== this._readings) {
        this._readings = sig;
        this._loaded = true;
        this._load();
      }
    }
    this._render();
  }

  disconnectedCallback() {
    if (this._unsub) this._unsub();
    this._unsub = null;
    this._watching = false;
  }

  async _load() {
    try {
      this._data = await this._hass.connection.sendMessagePromise({ type: WS });
      this._error = '';
    } catch (err) {
      this._error = "Church Drive's alarm lights aren't running. Update Church Drive and restart Home Assistant.";
    }
    this._sig = null;
    this._render();
  }

  _colour() {
    return this.config.color || '#e53935';
  }

  _canEdit() {
    return !!(this.config.demo || (this._hass && this._hass.user && this._hass.user.is_admin));
  }

  async _call(message, demo) {
    if (this.config.demo) {
      demo(this._data);
    } else {
      try {
        this._data = await this._hass.connection.sendMessagePromise(message);
        this._msg = '';
      } catch (err) {
        this._msg = (err && err.message) || String(err);
      }
    }
    this._sig = null;
    this._render();
  }

  _set(change) {
    return this._call({ type: `${WS}/set`, ...change }, (d) => Object.assign(d.config, change));
  }

  _role(entity, role) {
    const c = this._data.config;
    const strobe = c.strobe.filter((x) => x !== entity);
    const constant = c.constant.filter((x) => x !== entity);
    if (role === 'strobe') strobe.push(entity);
    if (role === 'constant') constant.push(entity);
    return this._set({ strobe, constant });
  }

  _test(test) {
    return this._call({ type: `${WS}/test`, test, when: this._when, seconds: this._seconds }, (d) => {
      d.state = 'test';
      d.test = { test, when: this._when === 'auto' ? 'night' : this._when, seconds: this._seconds };
    });
  }

  _stop() {
    return this._call({ type: `${WS}/stop` }, (d) => {
      d.state = 'idle';
      d.test = null;
    });
  }

  _lightList() {
    const s = (this._hass && this._hass.states) || {};
    const lights = Object.keys(s).filter((id) => id.startsWith('light.'));
    if (this.config.demo) {
      for (const id of ['light.living_room_ambience', 'light.kitchen', 'light.hall', 'light.garden']) if (!lights.includes(id)) lights.push(id);
    }
    return lights.map((id) => {
      const st = s[id];
      const attrs = (st && st.attributes) || {};
      return { id, name: attrs.friendly_name || id.replace('light.', '').replace(/_/g, ' '), group: Array.isArray(attrs.entity_id) };
    });
  }

  _sensorList() {
    const s = (this._hass && this._hass.states) || {};
    const found = Object.keys(s).filter((id) => id.startsWith('sensor.') && (s[id].attributes || {}).device_class === 'illuminance');
    for (const id of (this._data && this._data.config.sensors) || []) if (!found.includes(id)) found.push(id);
    return found.map((id) => ({ id, name: ((s[id] || {}).attributes || {}).friendly_name || id }));
  }

  _build() {
    this.innerHTML = kitShell(
      `<div class="al-body"><div class="al-boxes">
        <div class="al-box al-status"></div>
        <div class="al-box al-whatbox"></div>
        <div class="al-box"><div class="al-gh">Lights</div><div class="ck-sub">Strobe lights pulse. Constant lights just come on bright. Pick rooms and zones; a light in neither is left alone.</div>
          <div class="al-field"><input class="al-in al-find" type="search" placeholder="Find a light" aria-label="Find a light" style="flex:1 1 140px;"><button type="button" class="al-chip al-showall"></button></div>
          <div class="al-list"></div></div>
        <div class="al-box al-day"></div>
        <div class="al-box al-after"></div>
        <div class="al-box al-test"></div>
      </div><div class="al-foot"></div></div>`,
      AL_CSS
    );
    const q = (sel) => this.querySelector(sel);
    this._els = { status: q('.al-status'), what: q('.al-whatbox'), list: q('.al-list'), day: q('.al-day'), after: q('.al-after'), test: q('.al-test'), foot: q('.al-foot'), find: q('.al-find'), showall: q('.al-showall') };
    this._els.find.value = this._filter;
    this._els.find.addEventListener('input', () => {
      this._filter = this._els.find.value;
      this._renderLights();
    });
    this._els.showall.addEventListener('click', () => {
      this._all = !this._all;
      this._renderLights();
    });
    const body = q('.al-body');
    body.addEventListener('click', (ev) => {
      const role = ev.target.closest('[data-role]');
      if (role && !role.disabled) return this._role(role.dataset.light, role.dataset.role);
      const sensor = ev.target.closest('[data-sensor]');
      if (sensor && !sensor.disabled) {
        const c = this._data.config.sensors;
        const id = sensor.dataset.sensor;
        return this._set({ sensors: c.includes(id) ? c.filter((x) => x !== id) : [...c, id] });
      }
      const test = ev.target.closest('[data-test]');
      if (test && !test.disabled) return this._test(test.dataset.test);
      const when = ev.target.closest('[data-when]');
      if (when) {
        this._when = when.dataset.when;
        this._sig = null;
        return this._render();
      }
      if (ev.target.closest('[data-stop]')) return this._stop();
      if (ev.target.closest('[data-enabled]')) return this._set({ enabled: !this._data.config.enabled });
      return undefined;
    });
    body.addEventListener('change', (ev) => {
      const el = ev.target;
      if (el.dataset.num) this._set({ [el.dataset.num]: Number(el.value) });
      else if (el.dataset.rule !== undefined) this._set({ rule: el.value });
      else if (el.dataset.seconds !== undefined) {
        this._seconds = Math.max(5, Math.min(120, Number(el.value) || 20));
        el.value = this._seconds;
      }
    });
    this._built = true;
  }

  _render() {
    if (!this._hass && !this.config.demo) return;
    if (!this._built) this._build();
    const colour = this._colour();
    this.querySelector('.ck-headrow').style.display = this.config.title ? '' : 'none';
    if (this.config.title) kitHead(this, this.config.title, '', colour);
    const sig = JSON.stringify([this._data, this._error, this._msg, colour, this._when, this._seconds, this._canEdit()]);
    if (sig === this._sig) return;
    this._sig = sig;
    const e = this._els;
    if (this._error || !this._data) {
      e.status.innerHTML = `<div class="ck-sub" style="line-height:1.5;">${kitEsc(this._error || 'Loading…')}</div>`;
      for (const k of ['what', 'day', 'after', 'test']) e[k].style.display = 'none';
      return;
    }
    for (const k of ['what', 'day', 'after', 'test']) e[k].style.display = '';
    const d = this._data;
    const c = d.config;
    const edit = this._canEdit();
    const dis = edit ? '' : 'disabled';

    // Status and the on/off switch.
    const testing = d.state === 'test';
    const running = d.state === 'fire' || d.state === 'security' || d.state === 'ending';
    const now = { idle: 'Waiting for an alarm', fire: 'FIRE ALARM: lights running', security: `HOUSE ALARM (${d.mode === 'away' ? 'set to away' : 'set to home'}): lights running`, ending: 'All clear: putting the lights back', test: 'Testing the lights' }[d.state] || '';
    e.status.innerHTML = `<div class="al-gh">Alarm lights</div>
      <div class="al-state">
        <button type="button" class="al-chip" data-enabled aria-pressed="${!!c.enabled}" ${dis} style="${c.enabled ? `background:${colour};` : ''}">${c.enabled ? 'On' : 'Off'}</button>
        <span class="al-pill">${iconHtml(d.day ? 'mdi:weather-sunny' : 'mdi:weather-night', { size: '15px' })}${d.day ? 'Day' : 'Night'} in the house</span>
      </div>
      ${running ? `<div class="al-on" role="status">${kitEsc(now)}</div>` : `<div class="ck-sub" role="status">${kitEsc(now)}${testing && d.test ? ` (${kitEsc((TESTS.find((t) => t.key === d.test.test) || {}).label || '')}, ${kitEsc(d.test.when)})` : ''}</div>`}
      ${c.enabled ? '' : '<div class="ck-sub">Switched off: the lights are left alone in an alarm.</div>'}`;

    // What happens.
    e.what.innerHTML = `<div class="al-gh">What happens</div><div class="al-what">${WHAT.map(
      (w, i) => `<b>${dots(TESTS[i].colours)}${kitEsc(w.name)}</b><span>Day: ${kitEsc(w.day)}</span><span>Night: ${kitEsc(w.night)}</span><span>After: ${kitEsc(w.after)}</span>`
    ).join('')}<span style="margin-top:6px;">Fire wins if both are going. When it's all over the lights go back as they were.</span></div>`;

    this._renderLights();

    // Day or night.
    const sensors = this._sensorList();
    const readings = Object.fromEntries((d.sensors || []).map((r) => [r.entity_id, r.lux]));
    e.day.innerHTML = `<div class="al-gh">Day or night</div>
      <div class="ck-sub">Read once, when an alarm starts, from the sensors you tick, for the whole house. With no readings it counts as night, which lights more.</div>
      <div class="al-chips">${
        sensors.length
          ? sensors
              .map((s) => {
                const on = c.sensors.includes(s.id);
                const lux = readings[s.id];
                return `<button type="button" class="al-chip" data-sensor="${kitEsc(s.id)}" aria-pressed="${on}" ${dis} style="${on ? `background:${colour};` : ''}">${kitEsc(s.name.replace(/ illuminance$/i, ''))}${on && lux != null ? ` · ${Math.round(lux)} lx` : ''}</button>`;
              })
              .join('')
          : '<span class="ck-sub">No light sensors found.</span>'
      }</div>
      <label class="al-field"><span>It's day when a sensor reads at least</span><input class="al-in al-num" type="number" min="1" max="100000" value="${kitEsc(c.lux)}" data-num="lux" ${dis}> lx</label>
      <label class="al-field"><span>and this many sensors say day</span><select class="al-in" data-rule ${dis}>${RULES.map((r) => `<option value="${r.value}"${r.value === c.rule ? ' selected' : ''}>${kitEsc(r.label)}</option>`).join('')}</select></label>`;

    // After.
    e.after.innerHTML = `<div class="al-gh">When it's over</div>
      <label class="al-field"><span>${dots(['green'])}After a fire, strobe lights green for</span><input class="al-in al-num" type="number" min="0" max="600" value="${kitEsc(c.green_seconds)}" data-num="green_seconds" ${dis}> seconds</label>
      <label class="al-field"><span>${dots(['blue'])}When the alarm is turned off, blue for</span><input class="al-in al-num" type="number" min="0" max="600" value="${kitEsc(c.blue_seconds)}" data-num="blue_seconds" ${dis}> seconds</label>`;

    // Test.
    const noLights = !c.strobe.length && !c.constant.length;
    e.test.innerHTML = `<div class="al-gh">Test (lights only)</div>
      <div class="ck-sub">Plays it on the lights above and puts them back after. No alarm, no notification. Not while a real alarm is going.</div>
      <div class="al-field"><span>Pretend it is</span><span class="al-seg">${['auto', 'day', 'night']
        .map((w) => `<button type="button" data-when="${w}" aria-pressed="${this._when === w}" style="${this._when === w ? `background:${colour};` : ''}">${w === 'auto' ? `Now (${d.day ? 'day' : 'night'})` : w === 'day' ? 'Day' : 'Night'}</button>`)
        .join('')}</span></div>
      <label class="al-field"><span>For</span><input class="al-in al-num" type="number" min="5" max="120" value="${kitEsc(this._seconds)}" data-seconds ${dis}> seconds</label>
      <div class="al-btns">${TESTS.map((t) => `<button type="button" class="al-btn" data-test="${t.key}" ${edit && !running && !noLights ? '' : 'disabled'}>${iconHtml(t.icon, { size: '17px' })}${dots(t.colours)}${kitEsc(t.label)}</button>`).join('')}
        ${testing ? `<button type="button" class="al-btn al-stop" data-stop ${dis}>${iconHtml('mdi:stop', { size: '17px' })}Stop</button>` : ''}</div>
      ${noLights ? '<div class="ck-sub">Pick some lights first.</div>' : ''}`;

    const foot = [];
    if (!edit) foot.push('<div class="ck-sub">Only administrators can change these.</div>');
    if ((d.missing || []).length) foot.push(`<div class="al-msg">Lights that no longer exist: ${kitEsc(d.missing.join(', '))}</div>`);
    if (this._msg) foot.push(`<div class="al-msg" role="status">${kitEsc(this._msg)}</div>`);
    e.foot.innerHTML = foot.join('');
    hydrateIcons(this);
  }

  _renderLights() {
    if (!this._data || !this._els) return;
    const c = this._data.config;
    const edit = this._canEdit();
    const colour = this._colour();
    const chosen = new Set([...c.strobe, ...c.constant]);
    const find = this._filter.trim().toLowerCase();
    const lights = this._lightList()
      .filter((l) => chosen.has(l.id) || ((this._all || l.group) && (!find || l.name.toLowerCase().includes(find) || l.id.includes(find))))
      .sort((a, b) => Number(chosen.has(b.id)) - Number(chosen.has(a.id)) || Number(b.group) - Number(a.group) || a.name.localeCompare(b.name));
    this._els.showall.textContent = this._all ? 'Rooms and zones, and single lights' : 'Rooms and zones only';
    this._els.showall.setAttribute('aria-pressed', String(this._all));
    this._els.showall.style.background = this._all ? colour : '';
    const seg = (l, role, label) => {
      const on = (role === 'strobe' && c.strobe.includes(l.id)) || (role === 'constant' && c.constant.includes(l.id)) || (role === 'none' && !chosen.has(l.id));
      const bg = role === 'strobe' ? '#e53935' : role === 'constant' ? '#fbc02d' : '#5d6170';
      return `<button type="button" data-light="${kitEsc(l.id)}" data-role="${role}" aria-pressed="${on}" ${edit ? '' : 'disabled'} style="${on ? `background:${bg};${role === 'constant' ? 'color:#2a1700;' : ''}` : ''}">${label}</button>`;
    };
    this._els.list.innerHTML =
      lights
        .map((l) => `<div class="al-row"><div class="al-name">${kitEsc(l.name)}${l.group ? '<small>room or zone</small>' : ''}</div><span class="al-seg">${seg(l, 'none', 'Off')}${seg(l, 'strobe', 'Strobe')}${seg(l, 'constant', 'Constant')}</span></div>`)
        .join('') || '<div class="ck-sub">No lights match.</div>';
  }

  getCardSize() {
    return 10;
  }

  getGridOptions() {
    return { columns: 'full', rows: 'auto' };
  }

  static getConfigElement() {
    return document.createElement(`alarm-lights-card-editor${SUFFIX}`);
  }

  static getStubConfig() {
    return { title: 'Alarm lights' };
  }
}

export function registerAlarmLightsCard() {
  if (!customElements.get(`alarm-lights-card-editor${SUFFIX}`)) customElements.define(`alarm-lights-card-editor${SUFFIX}`, AlarmLightsCardEditor);
  if (!customElements.get(`alarm-lights-card${SUFFIX}`)) customElements.define(`alarm-lights-card${SUFFIX}`, AlarmLightsCard);
  window.customCards = window.customCards || [];
  window.customCards.push({
    type: `alarm-lights-card${SUFFIX}`,
    name: `Alarm Lights Card${LABEL}`,
    description: 'What the lights do when an alarm goes off: fire, smoke and CO, or the house alarm (home or away), day or night, with a lights-only test',
    preview: true,
    documentationURL: 'https://github.com/J45PER/church-drive-cards#readme',
  });
}
