// Scene builder: make your own universal scenes. A scene is white (colour
// temperature + brightness) or colours (up to nine, dealt round the lights;
// gradient strips show several), optionally animated like Hue's dynamic
// scenes. Saved scenes join the library, so every light card can use them in
// any room or zone. "Try in" plays the unsaved scene on a real room.
//
// Scenes are stored by the Church Drive integration (websocket
// church_drive/scene/save|delete|preview); saving needs an admin user.

import { createFormEditor } from './form-editor.js';
import { SUFFIX, LABEL } from './suffix.js';
import { loadUniversalScenes } from './universal-scenes.js';
import { iconHtml, hydrateIcons } from './icons.js';

const BLANK = { name: '', kind: 'colour', kelvin: 2700, brightness: 80, colors: ['#ff7b39', '#7b2cbf'], dynamic: true, speed: 0.5, icon: '' };

function kelvinHex(k) {
  // Rough colour temperature to sRGB, for the preview swatch.
  const t = k / 100;
  const r = t <= 66 ? 255 : 329.7 * (t - 60) ** -0.1332;
  const g = t <= 66 ? 99.47 * Math.log(t) - 161.12 : 288.12 * (t - 60) ** -0.0755;
  const b = t >= 66 ? 255 : t <= 19 ? 0 : 138.52 * Math.log(t - 10) - 305.04;
  return '#' + [r, g, b].map((v) => Math.round(Math.max(0, Math.min(255, v))).toString(16).padStart(2, '0')).join('');
}

function swatch(scene) {
  const colours = scene.kind === 'colour' ? scene.colors : [kelvinHex(scene.kelvin), kelvinHex(scene.kelvin)];
  const list = colours.length === 1 ? [colours[0], colours[0]] : colours;
  return `linear-gradient(135deg, ${list.join(', ')})`;
}

export const SceneBuilderCardEditor = createFormEditor({
  schema: () => [{ name: 'title', selector: { text: {} } }],
  labels: { title: 'Title' },
});

export class SceneBuilderCard extends HTMLElement {
  setConfig(config) {
    this.config = config || {};
    this._draft = this._draft || null;
    if (this._hass) this._draw();
  }

  set hass(hass) {
    const first = !this._hass;
    this._hass = hass;
    if (first) this._load();
  }

  async _load() {
    try {
      const res = await this._hass.callWS({ type: 'church_drive/library' });
      this._custom = res.custom || [];
      this._error = null;
    } catch (err) {
      this._custom = [];
      this._error = 'The Church Drive integration isn\'t available.';
    }
    this._draw();
  }

  _rooms() {
    return Object.values(this._hass.states)
      .filter((st) => st.entity_id.startsWith('light.') && st.attributes.hue_type)
      .map((st) => ({ id: st.entity_id, name: st.attributes.friendly_name || st.entity_id, kind: st.attributes.hue_type }))
      .sort((a, b) => (a.kind === b.kind ? a.name.localeCompare(b.name) : a.kind === 'room' ? -1 : 1));
  }

  _payload() {
    const d = this._draft;
    const scene = { name: d.name.trim(), kind: d.kind, brightness: Number(d.brightness) };
    if (d.key) scene.key = d.key;
    if (d.icon) scene.icon = d.icon.trim();
    if (d.kind === 'colour') Object.assign(scene, { colors: d.colors, dynamic: !!d.dynamic, speed: Number(d.speed) });
    else scene.kelvin = Number(d.kelvin);
    return scene;
  }

  async _call(message, done) {
    this._busy = true;
    this._status = '';
    this._draw();
    try {
      await this._hass.callWS(message);
      this._status = done;
    } catch (err) {
      this._status = `Couldn't do that: ${(err && err.message) || err}`;
    }
    this._busy = false;
  }

  async _save() {
    const scene = this._payload();
    if (!scene.name) {
      this._status = 'Give the scene a name.';
      this._draw();
      return;
    }
    await this._call({ type: 'church_drive/scene/save', scene }, `Saved "${scene.name}". It's now in every light card's scene list.`);
    if (!this._status.startsWith("Couldn't")) this._draft = null;
    loadUniversalScenes(this._hass, true);
    await this._load();
  }

  async _delete(scene) {
    if (!window.confirm(`Delete "${scene.name}"? Light cards using it will stop showing it.`)) return;
    await this._call({ type: 'church_drive/scene/delete', key: scene.key }, `Deleted "${scene.name}".`);
    loadUniversalScenes(this._hass, true);
    await this._load();
  }

  async _try() {
    const target = this.querySelector('.sbc-room').value;
    if (!target) return;
    const scene = { ...this._payload(), name: this._payload().name || 'Preview' };
    delete scene.key;
    await this._call({ type: 'church_drive/scene/preview', entity_id: [target], scene }, 'Playing on the lights now.');
    this._draw();
  }

  _draw() {
    if (!this._hass) return;
    const cfg = this.config || {};
    const d = this._draft;
    this.innerHTML = `
      <ha-card style="border:none; box-shadow:0 3px 10px rgba(0,0,0,0.45); border-radius:16px; overflow:hidden; background:var(--card-background-color); padding:16px; color:var(--primary-text-color);">
        <style>
          .sbc-row { display:flex; align-items:center; gap:12px; padding:8px; border-radius:12px; background:rgba(127,127,127,0.08); margin-top:8px; }
          .sbc-sw { width:44px; height:44px; border-radius:10px; flex:none; display:flex; align-items:center; justify-content:center; color:#fff; }
          .sbc-name { flex:1; min-width:0; font-weight:500; }
          .sbc-sub { color:var(--secondary-text-color); font-size:0.85rem; }
          .sbc-btn { border:none; border-radius:10px; padding:8px 12px; background:rgba(127,127,127,0.18); color:var(--primary-text-color); font:inherit; cursor:pointer; }
          .sbc-btn:hover { background:rgba(127,127,127,0.28); }
          .sbc-primary { background:var(--primary-color); color:var(--text-primary-color, #fff); }
          .sbc-form label { display:block; margin-top:12px; font-size:0.9rem; color:var(--secondary-text-color); }
          .sbc-form input[type=text], .sbc-form select { width:100%; box-sizing:border-box; padding:8px; border-radius:8px; border:1px solid var(--divider-color); background:var(--card-background-color); color:var(--primary-text-color); font:inherit; }
          .sbc-form input[type=range] { width:100%; }
          .sbc-colours { display:flex; flex-wrap:wrap; gap:8px; margin-top:6px; }
          .sbc-colours input[type=color] { width:44px; height:44px; border:none; border-radius:10px; padding:0; background:none; cursor:pointer; }
          .sbc-seg { display:flex; gap:6px; margin-top:6px; }
          .sbc-seg .sbc-btn[aria-pressed=true] { background:var(--primary-color); color:var(--text-primary-color, #fff); }
          .sbc-actions { display:flex; flex-wrap:wrap; gap:8px; margin-top:16px; }
          .sbc-status { margin-top:10px; font-size:0.9rem; color:var(--secondary-text-color); }
        </style>
        <div style="font-size:1.5rem; font-weight:500;"></div>
        <div class="sbc-sub" style="margin-top:4px;">Your own scenes, usable in any room or zone. Built-in ones (Bright, Soho…) are always there.</div>
        <div class="sbc-list"></div>
        <div class="sbc-body"></div>
        <div class="sbc-status"></div>
      </ha-card>`;
    this.querySelector('div').textContent = cfg.title || 'Scene builder';
    this.querySelector('.sbc-status').textContent = this._error || this._status || '';
    const list = this.querySelector('.sbc-list');
    (this._custom || []).forEach((scene) => {
      const row = document.createElement('div');
      row.className = 'sbc-row';
      row.innerHTML = `<div class="sbc-sw">${scene.icon ? iconHtml(scene.icon, { size: '24px' }) : ''}</div>
        <div class="sbc-name"><div></div><div class="sbc-sub"></div></div>
        <button class="sbc-btn" data-act="edit">Edit</button><button class="sbc-btn" data-act="delete">Delete</button>`;
      row.querySelector('.sbc-sw').style.background = swatch(scene);
      row.querySelector('.sbc-name div').textContent = scene.name;
      row.querySelector('.sbc-name .sbc-sub').textContent =
        scene.kind === 'colour' ? `${scene.colors.length} colours${scene.dynamic ? ', animated' : ''} · ${scene.brightness}%` : `${scene.kelvin}K · ${scene.brightness}%`;
      row.querySelector('[data-act=edit]').addEventListener('click', () => {
        this._draft = { ...BLANK, ...scene, colors: [...(scene.colors || BLANK.colors)] };
        this._status = '';
        this._draw();
      });
      row.querySelector('[data-act=delete]').addEventListener('click', () => this._delete(scene));
      list.appendChild(row);
    });
    hydrateIcons(list);
    const body = this.querySelector('.sbc-body');
    if (!d) {
      body.innerHTML = `<div class="sbc-actions"><button class="sbc-btn sbc-primary" data-act="new">New scene</button></div>`;
      body.querySelector('[data-act=new]').addEventListener('click', () => {
        this._draft = { ...BLANK, colors: [...BLANK.colors] };
        this._status = '';
        this._draw();
      });
      return;
    }
    body.innerHTML = `<div class="sbc-form">
      <div class="sbc-row" style="margin-top:16px;"><div class="sbc-sw sbc-preview"></div><div class="sbc-name sbc-title"></div></div>
      <label>Name<input type="text" class="sbc-f-name" maxlength="32" placeholder="e.g. Film night"></label>
      <label>Type<div class="sbc-seg"><button class="sbc-btn" data-kind="white">White</button><button class="sbc-btn" data-kind="colour">Colours</button></div></label>
      <div class="sbc-white"><label>Colour temperature: <span class="sbc-k"></span>K<input type="range" class="sbc-f-kelvin" min="2000" max="6500" step="50"></label></div>
      <div class="sbc-colour"><label>Colours (dealt round the lights; gradient strips show several)</label><div class="sbc-colours"></div>
        <label><input type="checkbox" class="sbc-f-dynamic"> Animated (colours drift between the lights, like Hue's dynamic scenes)</label>
        <label class="sbc-speed">Speed<input type="range" class="sbc-f-speed" min="0" max="1" step="0.05"></label></div>
      <label>Brightness: <span class="sbc-b"></span>%<input type="range" class="sbc-f-brightness" min="1" max="100"></label>
      <label>Icon (optional, e.g. mdi:movie-open)<input type="text" class="sbc-f-icon" placeholder="mdi:…"></label>
      <label>Try it in<select class="sbc-room"><option value="">Choose a room or zone…</option></select></label>
      <div class="sbc-actions"><button class="sbc-btn" data-act="try">Try</button><button class="sbc-btn sbc-primary" data-act="save">Save</button><button class="sbc-btn" data-act="cancel">Cancel</button></div>
    </div>`;
    const $ = (sel) => body.querySelector(sel);
    const refresh = () => {
      $('.sbc-preview').style.background = swatch(d);
      $('.sbc-title').textContent = d.name || 'New scene';
      $('.sbc-k').textContent = d.kelvin;
      $('.sbc-b').textContent = d.brightness;
      body.querySelectorAll('[data-kind]').forEach((b) => b.setAttribute('aria-pressed', String(b.dataset.kind === d.kind)));
      $('.sbc-white').style.display = d.kind === 'white' ? '' : 'none';
      $('.sbc-colour').style.display = d.kind === 'colour' ? '' : 'none';
      $('.sbc-speed').style.display = d.dynamic ? '' : 'none';
    };
    const drawColours = () => {
      const box = $('.sbc-colours');
      box.innerHTML = '';
      d.colors.forEach((c, i) => {
        const input = document.createElement('input');
        input.type = 'color';
        input.value = c;
        input.title = 'Change colour (right-click or long-press to remove)';
        input.addEventListener('input', () => {
          d.colors[i] = input.value;
          refresh();
        });
        input.addEventListener('contextmenu', (ev) => {
          ev.preventDefault();
          if (d.colors.length > 1) {
            d.colors.splice(i, 1);
            drawColours();
            refresh();
          }
        });
        box.appendChild(input);
      });
      if (d.colors.length < 9) {
        const add = document.createElement('button');
        add.className = 'sbc-btn';
        add.textContent = '+ Colour';
        add.addEventListener('click', () => {
          d.colors.push(d.colors[d.colors.length - 1] || '#ffffff');
          drawColours();
          refresh();
        });
        box.appendChild(add);
      }
      if (d.colors.length > 1) {
        const remove = document.createElement('button');
        remove.className = 'sbc-btn';
        remove.textContent = '− Colour';
        remove.addEventListener('click', () => {
          d.colors.pop();
          drawColours();
          refresh();
        });
        box.appendChild(remove);
      }
    };
    $('.sbc-f-name').value = d.name;
    $('.sbc-f-name').addEventListener('input', (ev) => {
      d.name = ev.target.value;
      refresh();
    });
    body.querySelectorAll('[data-kind]').forEach((b) =>
      b.addEventListener('click', () => {
        d.kind = b.dataset.kind;
        refresh();
      })
    );
    $('.sbc-f-kelvin').value = d.kelvin;
    $('.sbc-f-kelvin').addEventListener('input', (ev) => {
      d.kelvin = Number(ev.target.value);
      refresh();
    });
    $('.sbc-f-brightness').value = d.brightness;
    $('.sbc-f-brightness').addEventListener('input', (ev) => {
      d.brightness = Number(ev.target.value);
      refresh();
    });
    $('.sbc-f-dynamic').checked = !!d.dynamic;
    $('.sbc-f-dynamic').addEventListener('change', (ev) => {
      d.dynamic = ev.target.checked;
      refresh();
    });
    $('.sbc-f-speed').value = d.speed;
    $('.sbc-f-speed').addEventListener('input', (ev) => {
      d.speed = Number(ev.target.value);
    });
    $('.sbc-f-icon').value = d.icon || '';
    $('.sbc-f-icon').addEventListener('input', (ev) => {
      d.icon = ev.target.value;
    });
    const rooms = $('.sbc-room');
    this._rooms().forEach((r) => {
      const opt = document.createElement('option');
      opt.value = r.id;
      opt.textContent = `${r.name} (${r.kind})`;
      rooms.appendChild(opt);
    });
    if (this._tryTarget) rooms.value = this._tryTarget;
    rooms.addEventListener('change', () => {
      this._tryTarget = rooms.value;
    });
    $('[data-act=try]').addEventListener('click', () => this._try());
    $('[data-act=save]').addEventListener('click', () => this._save());
    $('[data-act=cancel]').addEventListener('click', () => {
      this._draft = null;
      this._status = '';
      this._draw();
    });
    body.querySelectorAll('button').forEach((b) => (b.disabled = !!this._busy));
    drawColours();
    refresh();
  }

  getCardSize() {
    return 6;
  }

  getGridOptions() {
    return { columns: 12, min_columns: 6, rows: 'auto' };
  }

  static getConfigElement() {
    return document.createElement(`scene-builder-card-editor${SUFFIX}`);
  }

  static getStubConfig() {
    return { title: 'Scene builder' };
  }
}

export function registerSceneBuilderCard() {
  if (!customElements.get(`scene-builder-card-editor${SUFFIX}`)) {
    customElements.define(`scene-builder-card-editor${SUFFIX}`, SceneBuilderCardEditor);
  }
  if (!customElements.get(`scene-builder-card${SUFFIX}`)) {
    customElements.define(`scene-builder-card${SUFFIX}`, SceneBuilderCard);
  }
  window.customCards = window.customCards || [];
  window.customCards.push({
    type: `scene-builder-card${SUFFIX}`,
    name: `Scene Builder Card${LABEL}`,
    description: 'Make your own universal scenes (white or colours, optionally animated) for every Light Control card',
    preview: false,
    documentationURL: 'https://github.com/J45PER/church-drive-cards#readme',
  });
}
