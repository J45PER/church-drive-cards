// Light Control Card — mode-aware light/group/room control with a real
// visual editor, colour-tinted rows (never a literal white fill), scene
// chips, and Home Assistant's own more-info pop-up for the full picker.

import { createFormEditor } from './form-editor.js';
import { sceneBackground, sceneIcon } from './scene-style.js';

const LCC_DEFAULT_MAX_SCENES = 6;

function lccHsToRgb(h, s) {
  const c = (s / 100);
  const x = c * (1 - Math.abs(((h / 60) % 2) - 1));
  let r = 0, g = 0, b = 0;
  if (h < 60) { r = c; g = x; } else if (h < 120) { r = x; g = c; }
  else if (h < 180) { g = c; b = x; } else if (h < 240) { g = x; b = c; }
  else if (h < 300) { r = x; b = c; } else { r = c; b = x; }
  const m = 1 - c;
  return `rgb(${Math.round((r + m) * 255)},${Math.round((g + m) * 255)},${Math.round((b + m) * 255)})`;
}

function lccKelvinColor(k) {
  if (k <= 3000) return '#ffb37d';
  if (k <= 4500) return '#ffe9c7';
  return '#cfe8ff';
}

function lccLightColor(st) {
  if (!st || st.state !== 'on') return '#ffc107';
  const a = st.attributes || {};
  if (a.hs_color) return lccHsToRgb(a.hs_color[0], a.hs_color[1]);
  if (a.rgb_color) return `rgb(${a.rgb_color[0]},${a.rgb_color[1]},${a.rgb_color[2]})`;
  if (a.color_temp_kelvin) return lccKelvinColor(a.color_temp_kelvin);
  return '#ffc107';
}

// Icon priority: the entity registry icon set in HA's UI (e.g. custom Hue
// icons like phu:ceiling-infuse — these are NOT copied into state
// attributes), then a state-attribute icon, then a bulb/group default.
function lccLightIcon(hass, st, isGroupLike) {
  const on = st && st.state === 'on';
  const entry = st && hass.entities && hass.entities[st.entity_id];
  if (entry && entry.icon) return entry.icon;
  if (st && st.attributes && st.attributes.icon) return st.attributes.icon;
  if (isGroupLike) return on ? 'mdi:lightbulb-group' : 'mdi:lightbulb-group-outline';
  return on ? 'mdi:lightbulb' : 'mdi:lightbulb-outline';
}

function lccMoreInfo(el, entityId) {
  el.dispatchEvent(new CustomEvent('hass-more-info', { detail: { entityId }, bubbles: true, composed: true }));
}

// Resolve an entity's area: its own area_id first, then its device's area_id.
// Philips Hue (and several other integrations) often only set an area on the
// *device*, or only on a room/zone group entity — never assume entity.area_id
// alone is authoritative.
function lccAreaOf(hass, entry) {
  if (!entry) return null;
  if (entry.area_id) return entry.area_id;
  if (entry.device_id && hass.devices && hass.devices[entry.device_id]) {
    return hass.devices[entry.device_id].area_id || null;
  }
  return null;
}

function lccIsGroupLike(st) {
  return !!(st && st.attributes && Array.isArray(st.attributes.entity_id));
}

// Member light IDs of a group-like entity (Hue room/zone, light group).
function lccMembersOf(hass, entityId) {
  const st = hass.states[entityId];
  if (!lccIsGroupLike(st)) return [];
  return st.attributes.entity_id.filter((id) => id.startsWith('light.') && hass.states[id]);
}

// `scenes` config items may be plain entity IDs (older YAML) or objects
// { entity, name?, icon?, image? } from the editor.
function lccNormalizeScenes(scenes) {
  return (scenes || []).map((s) => (typeof s === 'string' ? { entity: s } : s)).filter((s) => s && s.entity);
}

// The Hue room/zone group light a scene belongs to: Hue puts a group's scenes
// on the same device as the group's light entity.
function lccSceneGroup(hass, sceneId) {
  const entry = hass.entities && hass.entities[sceneId];
  if (!entry || !entry.device_id) return null;
  const group = Object.values(hass.entities).find(
    (e) => e.device_id === entry.device_id && e.entity_id.startsWith('light.') && lccIsGroupLike(hass.states[e.entity_id])
  );
  return group ? group.entity_id : null;
}

// Auto-detected scenes: those of the groups shown on the card plus any Hue
// zone made up only of this card's lights (e.g. "Living Room Ambience", whose
// device has no area). Each group's scenes follow the Hue app order
// (`hue_scenes`); duplicate names keep the first (room before zone).
function lccAutoScenes(hass, groupIds, lightIds) {
  const lights = new Set(lightIds);
  const groups = [...groupIds];
  if (lights.size) {
    Object.values(hass.states).forEach((st) => {
      const members = lccIsGroupLike(st) && st.entity_id.startsWith('light.') ? st.attributes.entity_id : null;
      if (members && members.length && members.every((m) => lights.has(m)) && !groups.includes(st.entity_id)) {
        groups.push(st.entity_id);
      }
    });
  }
  const seen = new Set();
  const out = [];
  groups.forEach((groupId) => {
    const device = hass.entities && hass.entities[groupId] && hass.entities[groupId].device_id;
    if (!device) return;
    const order = (hass.states[groupId] && hass.states[groupId].attributes.hue_scenes) || [];
    const rank = (id) => {
      const i = order.indexOf(hass.states[id].attributes.name);
      return i === -1 ? order.length : i;
    };
    Object.values(hass.entities)
      .filter((e) => e.entity_id.startsWith('scene.') && e.device_id === device && !e.hidden && hass.states[e.entity_id])
      .map((e) => e.entity_id)
      .sort((a, b) => rank(a) - rank(b) || a.localeCompare(b))
      .forEach((id) => {
        const key = String(hass.states[id].attributes.name || id).toLowerCase();
        if (seen.has(key)) return;
        seen.add(key);
        out.push({ entity: id });
      });
  });
  return out;
}

export const LightControlCardEditor = createFormEditor({
  schema: (config) => {
    const mode = config.mode || 'light';
    return [
      {
        name: 'mode',
        selector: {
          select: {
            mode: 'dropdown',
            options: [
              { value: 'light', label: 'Single Light' },
              { value: 'group', label: 'Light Group' },
              { value: 'room', label: 'Room' },
            ],
          },
        },
      },
      mode === 'room'
        ? { name: 'area', selector: { area: {} } }
        : { name: 'entity', selector: { entity: { domain: 'light' } } },
      { name: 'name', selector: { text: {} } },
      { name: 'max_scenes', selector: { number: { mode: 'box', min: 0, max: 24 } } },
      {
        name: 'scenes',
        selector: {
          object: {
            multiple: true,
            label_field: 'name',
            description_field: 'entity',
            fields: {
              entity: { label: 'Scene', required: true, selector: { entity: { domain: 'scene' } } },
              name: { label: 'Name override', selector: { text: {} } },
              icon: { label: 'Icon override', selector: { icon: {} } },
              image: { label: 'Picture (replaces the colour background)', selector: { image: {} } },
            },
          },
        },
      },
    ];
  },
  normalize: (config) => (config.scenes ? { ...config, scenes: lccNormalizeScenes(config.scenes) } : config),
  labels: {
    mode: 'Card type',
    area: 'Room',
    entity: 'Light entity',
    name: 'Title (optional)',
    max_scenes: 'Max scenes',
    scenes: 'Scenes (leave empty to pick them automatically)',
  },
  helpers: {
    max_scenes: 'Default 6. Set 0 to hide scenes.',
  },
});

export class LightControlCard extends HTMLElement {
  setConfig(config) {
    if (!config.entity && !config.area) throw new Error('entity or area required');
    this.config = config;
    this._built = false;
    this._lastIds = null;
  }

  static getConfigElement() {
    return document.createElement('light-control-card-editor');
  }

  // Pre-fill the card picker with a real light so the preview isn't an error.
  static getStubConfig(hass) {
    const first = hass && Object.keys(hass.states).find((id) => id.startsWith('light.'));
    return { mode: 'light', entity: first || '' };
  }

  _toggle(entityId) {
    this._hass.callService('light', 'toggle', {}, { entity_id: entityId });
  }

  _setBrightnessPct(entityId, pct) {
    if (pct <= 2) {
      this._hass.callService('light', 'turn_off', {}, { entity_id: entityId });
    } else {
      this._hass.callService('light', 'turn_on', { brightness: Math.round((pct / 100) * 255) }, { entity_id: entityId });
    }
  }

  // Animated (dynamic) Hue scenes are started with hue.activate_scene so
  // they actually play; everything else is a plain scene.turn_on.
  _activateScene(entityId, isDynamic) {
    const hue = this._hass.services && this._hass.services.hue;
    if (isDynamic && hue && hue.activate_scene) {
      this._hass.callService('hue', 'activate_scene', { dynamic: true }, { entity_id: entityId });
    } else {
      this._hass.callService('scene', 'turn_on', {}, { entity_id: entityId });
    }
  }

  // A single full-width row that IS the control: background is a low-opacity
  // TINT of the light's colour blended into the card background (never a
  // literal paint of the colour — a white/bright light would otherwise wash
  // the row to solid white and make the text unreadable), sized to the
  // brightness. Tap toggles; drag horizontally sets brightness on dimmable
  // lights.
  _buildRow(entityId, { withMoreInfo, member = false }) {
    const st = this._hass.states[entityId];
    const name = (st && st.attributes.friendly_name) || entityId;
    const isGroupLike = lccIsGroupLike(st);
    const on = st && st.state === 'on';
    const dimmable =
      st && st.attributes.supported_color_modes && st.attributes.supported_color_modes.some((m) => m !== 'onoff');
    const color = lccLightColor(st);
    const icon = lccLightIcon(this._hass, st, isGroupLike);
    const brightnessPct = st && st.attributes.brightness ? Math.round((st.attributes.brightness / 255) * 100) : 0;
    const fillPct = on ? (dimmable ? Math.max(brightnessPct, 4) : 100) : 0;

    // Tint the fill: blend the light's colour into the card's own background at
    // ~30% strength rather than a literal fill, so white/bright lights never
    // wash the row to solid white. color-mix keeps this working for any hue.
    const tint = `color-mix(in srgb, ${color} 30%, var(--card-background-color, #1c1c1c))`;
    const track = 'rgba(255,255,255,0.06)';

    const row = document.createElement('div');
    row.className = 'lcc-row';
    const pad = member ? '9px 14px 9px 14px' : '12px 14px';
    const indent = member ? 'margin-left:16px;' : '';
    row.style.cssText = `position:relative; display:flex; align-items:center; gap:12px; padding:${pad}; ${indent} border-radius:12px; margin-top:6px; overflow:hidden; cursor:pointer; user-select:none; touch-action:pan-y; background: linear-gradient(to right, ${tint} 0%, ${tint} ${fillPct}%, ${track} ${fillPct}%, ${track} 100%);`;
    row.innerHTML = `
      <ha-icon icon="${icon}" style="color:${on ? color : 'var(--secondary-text-color)'}; --mdc-icon-size:24px; flex-shrink:0; pointer-events:none;"></ha-icon>
      <div class="lcc-name" style="flex:1; min-width:0; font-weight:500; color:var(--primary-text-color); overflow:hidden; text-overflow:ellipsis; white-space:nowrap; pointer-events:none;">${name}</div>
      ${withMoreInfo ? `<ha-icon class="lcc-more" icon="mdi:tune-variant" style="color:var(--secondary-text-color); --mdc-icon-size:20px; cursor:pointer; flex-shrink:0;"></ha-icon>` : ''}
    `;

    if (withMoreInfo) {
      const moreBtn = row.querySelector('.lcc-more');
      moreBtn.addEventListener('click', (ev) => {
        ev.stopPropagation();
        lccMoreInfo(this, entityId);
      });
      // Swallow the whole press on the tune icon so the row never sees it —
      // stopping only pointerdown still let the row's pointerup toggle.
      ['pointerdown', 'pointerup', 'pointercancel'].forEach((type) =>
        moreBtn.addEventListener(type, (ev) => ev.stopPropagation())
      );
    }

    let pressed = false;
    let dragging = false;
    let moved = false;
    let startX = 0;
    const setFillVisual = (pct) => {
      row.style.background = `linear-gradient(to right, ${tint} 0%, ${tint} ${pct}%, ${track} ${pct}%, ${track} 100%)`;
    };
    const pctFromEvent = (ev) => {
      const rect = row.getBoundingClientRect();
      return Math.min(100, Math.max(0, ((ev.clientX - rect.left) / rect.width) * 100));
    };
    // While a drag is in progress the card must not rebuild its rows (any
    // state change in the house triggers a hass update, which would replace
    // this element mid-gesture), so _interacting pauses re-rendering.
    const endInteraction = () => {
      pressed = false;
      dragging = false;
      moved = false;
      this._interacting = false;
      if (this._pendingHass) {
        const h = this._pendingHass;
        this._pendingHass = null;
        this.hass = h;
      }
    };
    row.addEventListener('pointerdown', (ev) => {
      pressed = true;
      if (!dimmable) return;
      dragging = true;
      moved = false;
      startX = ev.clientX;
      this._interacting = true;
      row.setPointerCapture(ev.pointerId);
    });
    row.addEventListener('pointermove', (ev) => {
      if (!dragging) return;
      if (Math.abs(ev.clientX - startX) > 4) moved = true;
      if (moved) setFillVisual(pctFromEvent(ev));
    });
    row.addEventListener('pointerup', (ev) => {
      // Only act on a press that started on this row.
      if (!pressed) return;
      if (dimmable && dragging && moved) {
        this._setBrightnessPct(entityId, pctFromEvent(ev));
      } else {
        this._toggle(entityId);
      }
      endInteraction();
    });
    row.addEventListener('pointercancel', () => {
      setFillVisual(fillPct);
      endInteraction();
    });

    return row;
  }

  // Square scene tiles: picture (or palette gradient) background, icon, name.
  // The selected scene is outlined; an animated scene that's running shows a
  // pulsing play badge.
  _buildScenes(scenes) {
    if (!scenes.length) return null;
    const wrap = document.createElement('div');
    wrap.style.cssText =
      'display:grid; grid-template-columns:repeat(auto-fill, minmax(84px, 1fr)); gap:8px; margin-top:12px;';
    scenes.forEach((s) => {
      const tile = document.createElement('button');
      tile.className = 'lcc-scene';
      tile.title = s.name;
      const bg = s.image ? `center / cover no-repeat url("${s.image}")` : sceneBackground(s.name);
      tile.style.cssText = `position:relative; aspect-ratio:1 / 1; border:none; border-radius:14px; padding:0; overflow:hidden; cursor:pointer; background:${bg};${
        s.active ? ' outline:3px solid var(--primary-color); outline-offset:2px;' : ''
      }`;
      tile.innerHTML = `
        <div style="position:absolute; inset:0; background:linear-gradient(to top, rgba(0,0,0,0.6), rgba(0,0,0,0) 65%);"></div>
        <ha-icon icon="${s.icon}" style="position:absolute; top:8px; left:8px; --mdc-icon-size:20px; color:#fff; background:rgba(0,0,0,0.28); border-radius:50%; padding:4px;"></ha-icon>
        ${s.playing ? '<ha-icon class="lcc-playing" icon="mdi:play" title="Playing" style="position:absolute; top:8px; right:8px; --mdc-icon-size:18px; color:#fff; background:var(--primary-color); border-radius:50%; padding:4px;"></ha-icon>' : ''}
        <div class="lcc-scene-name" style="position:absolute; left:8px; right:8px; bottom:7px; text-align:left; color:#fff; font-size:0.8rem; font-weight:600; line-height:1.15; text-shadow:0 1px 2px rgba(0,0,0,0.6); display:-webkit-box; -webkit-line-clamp:2; -webkit-box-orient:vertical; overflow:hidden;"></div>`;
      tile.querySelector('.lcc-scene-name').textContent = s.name;
      tile.addEventListener('click', () => this._activateScene(s.entity, s.isDynamic));
      wrap.appendChild(tile);
    });
    return wrap;
  }

  // Resolve the scenes to show (configured list or auto-detected), capped at
  // max_scenes, with display name/icon/picture and selected/playing status.
  _resolveScenes(hass, mode, headIds, memberIds) {
    const cfg = this.config;
    const max = cfg.max_scenes != null ? cfg.max_scenes : LCC_DEFAULT_MAX_SCENES;
    if (max <= 0) return [];
    let items = lccNormalizeScenes(cfg.scenes);
    if (!items.length) {
      let groups = headIds.filter((id) => lccIsGroupLike(hass.states[id]));
      let lights = [...headIds, ...memberIds].filter((id) => !lccIsGroupLike(hass.states[id]));
      if (mode === 'light' && !groups.length) {
        // A single bulb has no scenes of its own: use its room's.
        const area = lccAreaOf(hass, hass.entities && hass.entities[cfg.entity]);
        const areaLights = Object.values(hass.entities || {})
          .filter((e) => e.entity_id.startsWith('light.') && hass.states[e.entity_id] && area && lccAreaOf(hass, e) === area)
          .map((e) => e.entity_id);
        groups = areaLights.filter((id) => lccIsGroupLike(hass.states[id]));
        lights = areaLights.filter((id) => !lccIsGroupLike(hass.states[id]));
      }
      items = lccAutoScenes(hass, groups, lights);
    }
    const scenes = items
      .filter((s) => hass.states[s.entity])
      .slice(0, max)
      .map((s) => {
        const st = hass.states[s.entity];
        const isDynamic = st.attributes.is_dynamic === true;
        const name = s.name || st.attributes.name || st.attributes.friendly_name || s.entity;
        return {
          ...s,
          name,
          isDynamic,
          icon: s.icon || sceneIcon(name, isDynamic),
          group: lccSceneGroup(hass, s.entity),
          activated: Date.parse(st.state) || 0,
        };
      });

    // Selected = the most recently activated of these scenes, while its
    // group's lights are still on. Playing = that scene is animated and Hue
    // reports its group (or any of the group's bulbs) as running dynamics.
    const latest = scenes.reduce((a, b) => (b.activated > (a ? a.activated : 0) ? b : a), null);
    if (latest) {
      const groupSt = latest.group && hass.states[latest.group];
      const lightsOn = groupSt
        ? groupSt.state === 'on'
        : [...headIds, ...memberIds].some((id) => hass.states[id] && hass.states[id].state === 'on');
      latest.active = lightsOn;
      if (lightsOn && latest.isDynamic && groupSt) {
        latest.playing =
          groupSt.attributes.dynamics === true ||
          lccMembersOf(hass, latest.group).some((id) => hass.states[id].attributes.dynamics === 'dynamic_palette');
      }
    }
    return scenes;
  }

  set hass(hass) {
    this._hass = hass;
    const cfg = this.config;
    const mode = cfg.mode || (cfg.area ? 'room' : 'light');

    if (!this._built) {
      this.innerHTML = `
        <ha-card style="border:none; box-shadow: 0 3px 10px rgba(0,0,0,0.45); border-radius:16px; overflow:hidden; background: var(--card-background-color); padding:16px 16px 14px 16px;">
          <style>
            @keyframes lcc-pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.5; } }
            .lcc-playing { animation: lcc-pulse 1.6s ease-in-out infinite; }
            .lcc-scene:active { transform: scale(0.97); }
          </style>
          <div class="lcc-title" style="display:none; padding:0 0 10px 0; font-size:1.5rem; font-weight:500; color: var(--primary-text-color);"></div>
          <div class="lcc-main"></div>
          <div class="lcc-members"></div>
          <div class="lcc-scenes"></div>
        </ha-card>`;
      this._titleEl = this.querySelector('.lcc-title');
      this._main = this.querySelector('.lcc-main');
      this._members = this.querySelector('.lcc-members');
      this._scenesEl = this.querySelector('.lcc-scenes');
      this._built = true;
    }

    // Work out which lights to show: `headIds` are full-size rows (the light,
    // the group, or a room's Hue room/zone groups), `memberIds` are the
    // indented individual lights underneath.
    let headIds = [];
    let memberIds = [];
    if (mode === 'room') {
      const areaLights = Object.values(hass.entities || {})
        .filter(
          (e) =>
            e.entity_id.startsWith('light.') &&
            !e.hidden &&
            e.entity_category == null &&
            hass.states[e.entity_id] &&
            lccAreaOf(hass, e) === cfg.area
        )
        .map((e) => e.entity_id);
      headIds = areaLights.filter((id) => lccIsGroupLike(hass.states[id]));
      memberIds = areaLights.filter((id) => !lccIsGroupLike(hass.states[id]));
    } else {
      headIds = [cfg.entity];
      if (mode === 'group') memberIds = lccMembersOf(hass, cfg.entity);
    }
    const relevantEntityIds = [...headIds, ...memberIds];
    const scenes = this._resolveScenes(hass, mode, headIds, memberIds);
    const watchIds = [
      ...relevantEntityIds,
      ...scenes.map((s) => s.entity),
      ...scenes.map((s) => s.group).filter(Boolean),
      ...scenes.filter((s) => s.group).flatMap((s) => lccMembersOf(hass, s.group)),
    ];

    // Skip the rebuild when none of this card's lights changed (hass is
    // re-set on every state change anywhere in the house), and never rebuild
    // mid-drag — replay the latest hass once the gesture ends instead.
    const snapshot = watchIds.map((id) => hass.states[id]);
    if (
      this._lastIds &&
      this._lastIds.join() === watchIds.join() &&
      this._lastSnapshot.every((st, i) => st === snapshot[i])
    ) {
      return;
    }
    if (this._interacting) {
      this._pendingHass = hass;
      return;
    }
    this._lastIds = watchIds;
    this._lastSnapshot = snapshot;

    this._main.innerHTML = '';
    this._main.style.cssText = '';
    this._members.innerHTML = '';

    if (mode === 'room') {
      const area = hass.areas && hass.areas[cfg.area];
      this._titleEl.textContent = cfg.name || (area ? area.name : cfg.area);
      this._titleEl.style.display = 'block';
      if (relevantEntityIds.length === 0) {
        this._main.textContent = 'No lights found in this area.';
        this._main.style.cssText = 'color:var(--secondary-text-color); padding:8px 4px;';
      }
      headIds.forEach((id) => this._main.appendChild(this._buildRow(id, { withMoreInfo: true })));
      // Only indent members when there's a room group above them.
      const member = headIds.length > 0;
      memberIds.forEach((id) => this._members.appendChild(this._buildRow(id, { withMoreInfo: true, member })));
    } else {
      this._titleEl.style.display = 'none';
      const row = this._buildRow(cfg.entity, { withMoreInfo: true });
      if (cfg.name) {
        const nameEl = row.querySelector('.lcc-name');
        if (nameEl) nameEl.textContent = cfg.name;
      }
      this._main.appendChild(row);
      memberIds.forEach((id) => this._members.appendChild(this._buildRow(id, { withMoreInfo: true, member: true })));
    }

    this._scenesEl.innerHTML = '';
    const scenesGrid = this._buildScenes(scenes);
    if (scenesGrid) this._scenesEl.appendChild(scenesGrid);

    // ~1 masonry unit (50px) per row, plus card padding and room title; scene
    // tiles are ~2 units per row of about four.
    this._size =
      1 + (mode === 'room' ? 1 : 0) + Math.max(relevantEntityIds.length, 1) + Math.ceil(scenes.length / 4) * 2;
  }

  getCardSize() {
    return this._size || 3;
  }

  // Sections-view defaults; the editor's Layout tab can override them.
  getGridOptions() {
    return { columns: 12, min_columns: 6, rows: 'auto' };
  }
}

export function registerLightControlCard() {
  if (!customElements.get('light-control-card-editor')) {
    customElements.define('light-control-card-editor', LightControlCardEditor);
  }
  if (!customElements.get('light-control-card')) {
    customElements.define('light-control-card', LightControlCard);
  }
  window.customCards = window.customCards || [];
  window.customCards.push({
    type: 'light-control-card',
    name: 'Light Control Card',
    description: 'Light/group/room control with icon, toggle, brightness, scenes, and full more-info pop-up (visual editor supported)',
    preview: true,
    documentationURL: 'https://github.com/J45PER/church-drive-cards#readme',
  });
}
