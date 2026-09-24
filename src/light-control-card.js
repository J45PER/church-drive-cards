// Light Control Card — mode-aware light/group/room control with a real
// visual editor, colour-tinted rows (never a literal white fill), scene
// chips, and Home Assistant's own more-info pop-up for the full picker.

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

function lccSceneChips(hass, entityIds) {
  const areas = new Set(
    entityIds
      .map((id) => hass.entities && hass.entities[id] && lccAreaOf(hass, hass.entities[id]))
      .filter(Boolean)
  );
  if (areas.size === 0) return [];
  return Object.values(hass.entities || {}).filter(
    (e) => e.entity_id.startsWith('scene.') && areas.has(lccAreaOf(hass, e))
  );
}

export class LightControlCardEditor extends HTMLElement {
  setConfig(config) {
    this._config = config || {};
    this._render();
  }

  set hass(hass) {
    this._hass = hass;
    this._render();
  }

  _render() {
    if (!this._hass) return;
    if (!this._form) {
      this._form = document.createElement('ha-form');
      this._form.addEventListener('value-changed', (ev) => {
        this._config = ev.detail.value;
        this.dispatchEvent(new CustomEvent('config-changed', { detail: { config: this._config }, bubbles: true, composed: true }));
        this._render();
      });
      this.appendChild(this._form);
    }
    const mode = this._config.mode || 'light';
    const schema = [
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
    ];
    if (mode === 'room') {
      schema.push({ name: 'area', selector: { area: {} } });
    } else {
      schema.push({ name: 'entity', selector: { entity: { domain: 'light' } } });
    }
    schema.push({ name: 'name', selector: { text: {} } });
    schema.push({ name: 'scenes', selector: { entity: { domain: 'scene', multiple: true } } });
    this._form.hass = this._hass;
    this._form.data = this._config;
    this._form.schema = schema;
    this._form.computeLabel = (s) =>
      ({
        mode: 'Card type',
        area: 'Room',
        entity: 'Light entity',
        name: 'Title (optional)',
        scenes: 'Scenes (optional — auto-detected by area if left blank)',
      }[s.name] || s.name);
  }
}

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

  static getStubConfig() {
    return { mode: 'light', entity: '' };
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

  _activateScene(entityId) {
    this._hass.callService('scene', 'turn_on', {}, { entity_id: entityId });
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

  _buildScenes(sceneEntities) {
    if (!sceneEntities.length) return null;
    const wrap = document.createElement('div');
    wrap.style.cssText = 'display:flex; gap:8px; flex-wrap:wrap; margin-top:10px;';
    sceneEntities.forEach((s) => {
      const chip = document.createElement('button');
      const name = (this._hass.states[s.entity_id] && this._hass.states[s.entity_id].attributes.friendly_name) || s.name || s.entity_id;
      chip.textContent = name.replace(/^living\s?room\s*/i, '');
      chip.style.cssText =
        'padding:8px 14px; border-radius:20px; border:none; background:rgba(255,255,255,0.08); color:var(--primary-text-color); font-size:0.85rem; cursor:pointer;';
      chip.addEventListener('click', () => this._activateScene(s.entity_id));
      wrap.appendChild(chip);
    });
    return wrap;
  }

  set hass(hass) {
    this._hass = hass;
    const cfg = this.config;
    const mode = cfg.mode || (cfg.area ? 'room' : 'light');

    if (!this._built) {
      this.innerHTML = `
        <ha-card style="border:none; box-shadow: 0 3px 10px rgba(0,0,0,0.45); border-radius:16px; overflow:hidden; background: var(--card-background-color); padding:16px 16px 14px 16px;">
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

    // Skip the rebuild when none of this card's lights changed (hass is
    // re-set on every state change anywhere in the house), and never rebuild
    // mid-drag — replay the latest hass once the gesture ends instead.
    const snapshot = relevantEntityIds.map((id) => hass.states[id]);
    if (
      this._lastIds &&
      this._lastIds.join() === relevantEntityIds.join() &&
      this._lastSnapshot.every((st, i) => st === snapshot[i])
    ) {
      return;
    }
    if (this._interacting) {
      this._pendingHass = hass;
      return;
    }
    this._lastIds = relevantEntityIds;
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
    let sceneEntities;
    if (cfg.scenes && cfg.scenes.length) {
      sceneEntities = cfg.scenes.map((id) => ({ entity_id: id }));
    } else {
      sceneEntities = lccSceneChips(hass, relevantEntityIds);
    }
    const scenesRow = this._buildScenes(sceneEntities);
    if (scenesRow) this._scenesEl.appendChild(scenesRow);
  }

  getCardSize() {
    return 3;
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
  });
}
