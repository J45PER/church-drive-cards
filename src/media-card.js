// Media Card: the house's TVs and speakers, a row each (style B from the
// mock-ups). Whatever is playing goes first. Each row has play/pause (or
// power when it's off); tapping a row opens our own pop-up remote: artwork,
// play/pause and skip, volume, source, a direction pad and power.
//
// The direction pad works out how to talk to each TV by itself: a remote
// entity on the same device (e.g. the Google TV Streamer's remote.*, sent
// DPAD_UP etc.), or an LG webOS TV's own buttons (webostv.button). With
// neither, the pad isn't shown.

import { createFormEditor } from './form-editor.js';
import { iconHtml, hydrateIcons } from './icons.js';
import { SUFFIX, LABEL } from './suffix.js';
import { kitShell, kitHead, kitEsc } from './card-kit.js';
import { openPopup } from './popup.js';

const PLAYING = ['playing', 'paused', 'buffering'];
const ON = ['on', 'idle', 'standby', ...PLAYING];
const F = { PAUSE: 1, VOLUME_SET: 4, PREVIOUS: 16, NEXT: 32, TURN_ON: 128, TURN_OFF: 256, SELECT_SOURCE: 2048, PLAY: 16384 };
const has = (st, f) => !!(Number(st.attributes.supported_features || 0) & f);

const MC_CSS = `
  .mc-row { display:flex; align-items:center; gap:10px; padding:8px 2px; }
  .mc-row + .mc-row { border-top:1px solid var(--divider-color, rgba(127,127,127,0.18)); }
  .mc-ico { flex:none; width:38px; height:38px; border-radius:11px; display:flex; align-items:center; justify-content:center; background:rgba(127,127,127,0.16); }
  .mc-body { flex:1; min-width:0; cursor:pointer; border-radius:8px; }
  .mc-name { font-weight:600; font-size:0.95rem; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
  .mc-sub { font-size:0.78rem; color:var(--secondary-text-color); white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
  .mc-btn { flex:none; width:38px; height:38px; border:none; border-radius:50%; background:rgba(127,127,127,0.16); color:var(--primary-text-color); cursor:pointer; display:flex; align-items:center; justify-content:center; }
  .mc-btn:focus-visible, .mc-body:focus-visible, .mcp button:focus-visible { outline:2px solid var(--primary-color); outline-offset:2px; }
  .mcp { display:flex; flex-direction:column; gap:16px; }
  .mcp-now { display:flex; align-items:center; gap:12px; }
  .mcp-art { flex:none; width:64px; height:64px; border-radius:12px; background:rgba(127,127,127,0.16) center/cover no-repeat; display:flex; align-items:center; justify-content:center; }
  .mcp-t1 { font-weight:600; font-size:1rem; }
  .mcp-t2 { font-size:0.82rem; color:var(--secondary-text-color); }
  .mcp-row { display:flex; align-items:center; justify-content:center; gap:14px; }
  .mcp button { border:none; cursor:pointer; color:var(--primary-text-color); background:rgba(127,127,127,0.16); font:inherit; }
  .mcp .mcp-round { width:48px; height:48px; border-radius:50%; display:flex; align-items:center; justify-content:center; }
  .mcp .mcp-main { width:60px; height:60px; color:#fff; }
  .mcp-vol { display:flex; align-items:center; gap:10px; }
  .mcp-vol input { flex:1; accent-color:var(--mc-colour); }
  .mcp-pad { display:grid; grid-template-columns:repeat(3, 56px); grid-template-rows:repeat(3, 56px); gap:6px; justify-content:center; }
  .mcp-pad button { border-radius:14px; display:flex; align-items:center; justify-content:center; }
  .mcp-pad .mcp-ok { border-radius:50%; font-weight:700; }
  .mcp-wide { display:flex; flex-wrap:wrap; gap:8px; }
  .mcp-wide button, .mcp select { flex:1 1 30%; min-width:0; border-radius:12px; padding:10px 12px; display:flex; align-items:center; justify-content:center; gap:6px; font-size:0.85rem; font-weight:600; }
  .mcp select { border:1px solid var(--divider-color, rgba(127,127,127,0.3)); background:rgba(127,127,127,0.1); color:var(--primary-text-color); color-scheme:dark light; }
  .mcp-l { font-size:0.75rem; color:var(--secondary-text-color); text-transform:uppercase; letter-spacing:0.06em; font-weight:700; }
`;

function mcDemo() {
  const st = (id, name, state, attrs = {}) => ({ entity_id: id, state, attributes: { friendly_name: name, supported_features: 0xffff, ...attrs } });
  return {
    'media_player.living_room_tv': st('media_player.living_room_tv', 'Living Room TV', 'playing', { app_name: 'Netflix', media_title: 'The Great British Bake Off', volume_level: 0.3, source: 'Netflix', source_list: ['Live TV', 'Netflix', 'YouTube', 'BBC iPlayer'], device_class: 'tv' }),
    'media_player.bedroom_tv': st('media_player.bedroom_tv', 'Master bedroom TV', 'off', { device_class: 'tv' }),
    'media_player.kitchen': st('media_player.kitchen', 'Kitchen speaker', 'unavailable', { device_class: 'speaker' }),
  };
}

export const MediaCardEditor = createFormEditor({
  schema: () => [
    { name: 'title', selector: { text: {} } },
    { name: 'entities', selector: { entity: { domain: 'media_player', multiple: true } } },
    { name: 'color', selector: { ui_color: {} } },
    { name: 'demo', selector: { boolean: {} } },
  ],
  labels: {
    title: 'Title (optional)',
    entities: 'TVs and speakers (empty for all of them)',
    color: 'Colour',
    demo: 'Show pretend players (for Design Presets; buttons are switched off)',
  },
  helpers: {
    entities: 'Tap a player for its remote. The direction pad appears by itself for TVs with a remote (e.g. Google TV) or an LG TV.',
    color: 'Default pink (#f050f8).',
  },
});

export class MediaCard extends HTMLElement {
  setConfig(config) {
    this.config = config || {};
    this._built = false;
    this._sig = null;
    if (this._pop) this._pop.close();
  }

  set hass(hass) {
    this._hass = hass;
    this._render();
    if (this._pop && this._popEntity) this._drawPop();
  }

  disconnectedCallback() {
    if (this._pop) this._pop.close();
  }

  _colour() {
    return this.config.color || '#f050f8';
  }

  _states() {
    if (this.config.demo) return this._demo || (this._demo = mcDemo());
    return (this._hass && this._hass.states) || {};
  }

  _players() {
    const s = this._states();
    const ids = this.config.demo ? Object.keys(s) : (this.config.entities && this.config.entities.length ? this.config.entities : Object.keys(s).filter((id) => id.startsWith('media_player.')));
    const rank = (st) => (!st ? 9 : st.state === 'playing' ? 0 : PLAYING.includes(st.state) ? 1 : ON.includes(st.state) ? 2 : st.state === 'off' ? 3 : 4);
    return ids
      .map((id) => s[id])
      .filter(Boolean)
      .sort((a, b) => rank(a) - rank(b) || String(a.attributes.friendly_name).localeCompare(String(b.attributes.friendly_name)));
  }

  _sub(st) {
    const a = st.attributes;
    if (st.state === 'unavailable') return 'Not responding';
    if (st.state === 'off') return 'Off';
    const what = [a.app_name, a.media_title || a.media_series_title, a.media_artist].filter(Boolean);
    if (PLAYING.includes(st.state)) return (st.state === 'paused' ? 'Paused · ' : '') + (what.join(' · ') || a.source || 'Playing');
    return a.source || a.app_name || 'On';
  }

  _icon(st) {
    const dc = st.attributes.device_class;
    const id = st.entity_id;
    if (dc === 'tv' || /tv|streamer|chromecast/i.test(id)) return 'mdi:television';
    if (/radio|_rb$/i.test(id)) return 'mdi:radio';
    return 'mdi:speaker';
  }

  // How to send direction-pad presses: a remote.* on the same device, or an LG's buttons.
  _pad(id) {
    if (this.config.demo) return { kind: 'demo' };
    const reg = (this._hass && this._hass.entities) || {};
    const entry = reg[id];
    if (entry && entry.platform === 'webostv') return { kind: 'webos' };
    const dev = entry && entry.device_id;
    const remote = dev && Object.keys(reg).find((x) => x.startsWith('remote.') && reg[x].device_id === dev);
    return remote ? { kind: 'remote', entity: remote } : null;
  }

  _call(domain, service, data) {
    if (this.config.demo) return Promise.resolve();
    return this._hass.callService(domain, service, data).catch(() => {});
  }

  _press(id, key) {
    const pad = this._pad(id);
    if (!pad || pad.kind === 'demo') return;
    if (pad.kind === 'webos') {
      const map = { up: 'UP', down: 'DOWN', left: 'LEFT', right: 'RIGHT', ok: 'ENTER', back: 'BACK', home: 'HOME' };
      this._call('webostv', 'button', { entity_id: id, button: map[key] });
    } else {
      const map = { up: 'DPAD_UP', down: 'DPAD_DOWN', left: 'DPAD_LEFT', right: 'DPAD_RIGHT', ok: 'DPAD_CENTER', back: 'BACK', home: 'HOME' };
      this._call('remote', 'send_command', { entity_id: pad.entity, command: map[key] });
    }
  }

  _toggle(st) {
    if (this.config.demo) {
      st.state = st.state === 'playing' ? 'paused' : st.state === 'paused' ? 'playing' : st.state === 'off' ? 'on' : 'off';
      this._sig = null;
      this._render();
      if (this._pop) this._drawPop();
      return;
    }
    if (PLAYING.includes(st.state)) this._call('media_player', 'media_play_pause', { entity_id: st.entity_id });
    else this._call('media_player', st.state === 'off' ? 'turn_on' : 'turn_off', { entity_id: st.entity_id });
  }

  _render() {
    if (!this._hass && !this.config.demo) return;
    const c = this.config;
    const colour = this._colour();
    if (!this._built) {
      this.innerHTML = kitShell(`<div class="mc-list"></div>`, MC_CSS);
      this._list = this.querySelector('.mc-list');
      this._list.addEventListener('click', (ev) => {
        const btn = ev.target.closest('[data-act]');
        const id = (btn || ev.target.closest('[data-id]') || {}).dataset?.id;
        const st = id && this._states()[id];
        if (!st) return;
        if (btn) this._toggle(st);
        else this._open(id);
      });
      this._list.addEventListener('keydown', (ev) => {
        const body = ev.target.closest('.mc-body');
        if (body && (ev.key === 'Enter' || ev.key === ' ')) {
          ev.preventDefault();
          this._open(body.dataset.id);
        }
      });
      this._built = true;
    }
    this.querySelector('.ck-headrow').style.display = c.title ? '' : 'none';
    const players = this._players();
    const playing = players.filter((p) => p.state === 'playing').length;
    if (c.title) kitHead(this, c.title, playing ? `${playing} playing` : '', colour);
    const sig = JSON.stringify([players.map((p) => [p.entity_id, p.state, this._sub(p)]), colour]);
    if (sig === this._sig) return;
    this._sig = sig;
    this._list.innerHTML = players.length
      ? players
          .map((st) => {
            const live = PLAYING.includes(st.state);
            const on = ON.includes(st.state);
            const name = st.attributes.friendly_name || st.entity_id;
            const act = live
              ? `<button type="button" class="mc-btn" data-act="1" data-id="${kitEsc(st.entity_id)}" aria-label="${st.state === 'playing' ? 'Pause' : 'Play'} ${kitEsc(name)}">${iconHtml(st.state === 'playing' ? 'mdi:pause' : 'mdi:play', { size: '20px' })}</button>`
              : st.state === 'unavailable'
                ? ''
                : `<button type="button" class="mc-btn" data-act="1" data-id="${kitEsc(st.entity_id)}" aria-label="Turn ${on ? 'off' : 'on'} ${kitEsc(name)}" style="${on ? `color:${colour};` : ''}">${iconHtml('mdi:power', { size: '20px' })}</button>`;
            return `<div class="mc-row">
              <div class="mc-ico" style="${live ? `background:color-mix(in srgb, ${colour} 25%, transparent); color:${colour};` : st.state === 'unavailable' ? 'opacity:.5;' : ''}">${iconHtml(this._icon(st), { size: '20px' })}</div>
              <div class="mc-body" data-id="${kitEsc(st.entity_id)}" role="button" tabindex="0" aria-label="Remote for ${kitEsc(name)}">
                <div class="mc-name">${kitEsc(name)}</div><div class="mc-sub">${kitEsc(this._sub(st))}</div>
              </div>${act}</div>`;
          })
          .join('')
      : `<div class="ck-sub">No TVs or speakers found.</div>`;
    hydrateIcons(this);
  }

  _open(id) {
    const st = this._states()[id];
    if (!st) return;
    if (this._pop) this._pop.close();
    this._popEntity = id;
    this._popBody = document.createElement('div');
    this._popBody.className = 'mcp';
    this._popBody.style.setProperty('--mc-colour', this._colour());
    this._popBody.addEventListener('click', (ev) => this._popClick(ev));
    this._popBody.addEventListener('change', (ev) => this._popChange(ev));
    this._popSig = null;
    this._pop = openPopup(this, {
      title: st.attributes.friendly_name || id,
      icon: this._icon(st),
      color: this._colour(),
      content: this._popBody,
      onClose: () => {
        this._pop = null;
        this._popEntity = null;
      },
    });
    this._drawPop();
  }

  _drawPop() {
    const id = this._popEntity;
    const st = this._states()[id];
    if (!st || !this._popBody) return;
    const a = st.attributes;
    const colour = this._colour();
    const sig = JSON.stringify([st.state, a.media_title, a.app_name, a.source, a.entity_picture, a.source_list, Math.round((a.volume_level || 0) * 100)]);
    if (sig === this._popSig || (this._dragging && this._popSig)) return;
    this._popSig = sig;
    const live = PLAYING.includes(st.state);
    const on = ON.includes(st.state);
    const pad = this._pad(id);
    const art = a.entity_picture ? `style="background-image:url('${kitEsc(a.entity_picture)}')"` : '';
    const btn = (act, icon, label, extra = '', cls = 'mcp-round') => `<button type="button" class="${cls}" data-p="${act}" aria-label="${label}" ${extra}>${iconHtml(icon, { size: '22px' })}</button>`;
    let html = `<div class="mcp-now"><div class="mcp-art" ${art}>${a.entity_picture ? '' : iconHtml(this._icon(st), { size: '28px' })}</div>
      <div style="min-width:0;"><div class="mcp-t1">${kitEsc(a.media_title || (on ? a.app_name || a.source || 'On' : st.state === 'off' ? 'Off' : 'Not responding'))}</div><div class="mcp-t2">${kitEsc(this._sub(st))}</div></div></div>`;
    if (on) {
      html += `<div class="mcp-row">
        ${has(st, F.PREVIOUS) || this.config.demo ? btn('prev', 'mdi:skip-previous', 'Previous') : ''}
        ${btn('playpause', live && st.state === 'playing' ? 'mdi:pause' : 'mdi:play', live && st.state === 'playing' ? 'Pause' : 'Play', `style="background:${colour};"`, 'mcp-round mcp-main')}
        ${has(st, F.NEXT) || this.config.demo ? btn('next', 'mdi:skip-next', 'Next') : ''}
      </div>`;
      if (has(st, F.VOLUME_SET) || this.config.demo)
        html += `<div class="mcp-vol">${iconHtml('mdi:volume-low', { size: '20px' })}<input type="range" min="0" max="100" step="1" value="${Math.round((a.volume_level || 0) * 100)}" aria-label="Volume">${iconHtml('mdi:volume-high', { size: '20px' })}</div>`;
      if ((has(st, F.SELECT_SOURCE) || this.config.demo) && Array.isArray(a.source_list) && a.source_list.length)
        html += `<div class="mcp-l">Source</div><select class="mcp-src" aria-label="Source">${a.source_list.map((s) => `<option ${s === a.source ? 'selected' : ''}>${kitEsc(s)}</option>`).join('')}</select>`;
      if (pad)
        html += `<div class="mcp-l">Remote</div><div class="mcp-pad">
          <span></span>${btn('up', 'mdi:chevron-up', 'Up', '', '')}<span></span>
          ${btn('left', 'mdi:chevron-left', 'Left', '', '')}<button type="button" class="mcp-ok" data-p="ok" aria-label="OK">OK</button>${btn('right', 'mdi:chevron-right', 'Right', '', '')}
          <span></span>${btn('down', 'mdi:chevron-down', 'Down', '', '')}<span></span></div>
          <div class="mcp-wide"><button type="button" data-p="back">${iconHtml('mdi:arrow-left', { size: '18px' })}Back</button><button type="button" data-p="home">${iconHtml('mdi:home', { size: '18px' })}Home</button></div>`;
    }
    if (st.state !== 'unavailable')
      html += `<div class="mcp-wide"><button type="button" data-p="power" style="${on ? '' : `background:${colour}; color:#fff;`}">${iconHtml('mdi:power', { size: '18px' })}${on ? 'Turn off' : 'Turn on'}</button></div>`;
    this._popBody.innerHTML = html;
    const vol = this._popBody.querySelector('input[type=range]');
    if (vol) {
      vol.addEventListener('pointerdown', () => (this._dragging = true));
      vol.addEventListener('pointerup', () => (this._dragging = false));
    }
    hydrateIcons(this._popBody);
  }

  _popClick(ev) {
    const b = ev.target.closest('[data-p]');
    if (!b) return;
    const id = this._popEntity;
    const st = this._states()[id];
    if (!st) return;
    const p = b.dataset.p;
    if (p === 'playpause' || p === 'power') {
      if (p === 'power' || !PLAYING.includes(st.state)) {
        if (this.config.demo) st.state = ON.includes(st.state) ? 'off' : 'on';
        else this._call('media_player', ON.includes(st.state) ? 'turn_off' : 'turn_on', { entity_id: id });
        if (p === 'playpause' && !this.config.demo) this._call('media_player', 'media_play', { entity_id: id });
      } else this._toggle(st);
    } else if (p === 'prev') this._call('media_player', 'media_previous_track', { entity_id: id });
    else if (p === 'next') this._call('media_player', 'media_next_track', { entity_id: id });
    else this._press(id, p);
    if (this.config.demo) {
      this._sig = null;
      this._render();
      this._popSig = null;
      this._drawPop();
    }
  }

  _popChange(ev) {
    const id = this._popEntity;
    if (ev.target.matches('input[type=range]')) {
      this._dragging = false;
      this._call('media_player', 'volume_set', { entity_id: id, volume_level: Number(ev.target.value) / 100 });
    } else if (ev.target.matches('.mcp-src')) this._call('media_player', 'select_source', { entity_id: id, source: ev.target.value });
  }

  getCardSize() {
    return 3;
  }

  getGridOptions() {
    return { columns: 'full', rows: 'auto' };
  }

  static getConfigElement() {
    return document.createElement(`media-card-editor${SUFFIX}`);
  }

  static getStubConfig() {
    return { title: 'TVs & speakers' };
  }
}

export function registerMediaCard() {
  if (!customElements.get(`media-card-editor${SUFFIX}`)) customElements.define(`media-card-editor${SUFFIX}`, MediaCardEditor);
  if (!customElements.get(`media-card${SUFFIX}`)) customElements.define(`media-card${SUFFIX}`, MediaCard);
  window.customCards = window.customCards || [];
  window.customCards.push({
    type: `media-card${SUFFIX}`,
    name: `Media Card${LABEL}`,
    description: 'TVs and speakers, a row each, with a pop-up remote (play, volume, source, direction pad, power)',
    preview: true,
    documentationURL: 'https://github.com/J45PER/church-drive-cards#readme',
  });
}
