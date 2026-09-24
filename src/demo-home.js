// A pretend Hue home for the light card's demo mode (used on the Design
// Presets dashboard so testing never touches real lights). It builds a
// hass-shaped object (states, entity/device/area registries, services) and
// simulates the services the card calls, entirely in the browser. Nothing is
// sent to Home Assistant.

import { scenePalette } from './scene-style.js';

const COLOUR = ['color_temp', 'xy'];

// Scene looks: white scenes use a colour temperature, the rest the palette
// from scene-style.js. Brightness is 0-255.
const WHITE = {
  nightlight: [2000, 26],
  rest: [2200, 89],
  relax: [2700, 143],
  read: [4300, 255],
  concentrate: [5500, 255],
  energise: [6300, 255],
  bright: [4000, 255],
};

export const DEMO_ROOMS = {
  living_room: {
    area: 'demo_living_room',
    areaName: 'Living Room',
    lights: [
      { id: 'demo_ceiling', name: 'Ceiling Light', modes: COLOUR },
      { id: 'demo_tv_lightstrip', name: 'TV Lightstrip', modes: COLOUR },
      { id: 'demo_table_lamp', name: 'Table Lamp', modes: ['color_temp'] },
      { id: 'demo_shelf_lamp', name: 'Shelf Lamp', modes: ['onoff'] },
    ],
    groups: [
      {
        id: 'demo_living_room',
        name: 'Living Room',
        area: true,
        members: ['demo_ceiling', 'demo_tv_lightstrip', 'demo_table_lamp', 'demo_shelf_lamp'],
        scenes: [['Nightlight'], ['Rest'], ['Bright']],
      },
      {
        // A Hue zone with no area, like the real "Living Room Ambience".
        id: 'demo_living_room_ambience',
        name: 'Living Room Ambience',
        area: false,
        members: ['demo_ceiling', 'demo_tv_lightstrip'],
        scenes: [['Concentrate'], ['Soho', true], ['Lake Placid', true], ['Relax'], ['Toil and trouble', true], ['Read']],
      },
    ],
  },
  bedroom: {
    area: 'demo_bedroom',
    areaName: 'Bedroom',
    lights: [
      { id: 'demo_bedside_left', name: 'Bedside Left', modes: COLOUR },
      { id: 'demo_bedside_right', name: 'Bedside Right', modes: COLOUR },
      { id: 'demo_big_light', name: 'The Big Light', modes: ['color_temp'] },
      { id: 'demo_bedroom_lightstrip', name: 'Bedroom Lightstrip', modes: COLOUR },
      // A settings entity the card must skip, like a purifier's display light.
      { id: 'demo_purifier_backlight', name: 'Purifier Display Backlight', modes: ['brightness'], category: 'config' },
    ],
    groups: [
      {
        id: 'demo_bedroom',
        name: 'Bedroom',
        area: true,
        members: ['demo_bedside_left', 'demo_bedside_right', 'demo_big_light', 'demo_bedroom_lightstrip'],
        scenes: [['Nightlight'], ['Read'], ['Relax']],
      },
      {
        id: 'demo_bedroom_ambiance',
        name: 'Bedroom Ambiance',
        area: true,
        members: ['demo_bedside_left', 'demo_bedside_right', 'demo_bedroom_lightstrip'],
        scenes: [['Arise', true], ['Spellbound', true], ['Storybook', true], ['Unwind', true]],
      },
    ],
  },
};

function hexToRgb(hex) {
  const m = /^#?([0-9a-f]{6})$/i.exec(hex);
  if (!m) return [255, 180, 110];
  const n = parseInt(m[1], 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
}

function slug(text) {
  return text.toLowerCase().replace(/[^a-z0-9]+/g, '_').replace(/^_|_$/g, '');
}

export class DemoHome {
  // `onChange` is called after every simulated change so the card re-renders.
  constructor(roomKey, onChange) {
    this.room = DEMO_ROOMS[roomKey] || DEMO_ROOMS.living_room;
    this.onChange = onChange;
    this.timer = null;
    this.tick = 0;
    this.states = {};
    this.entities = {};
    this.devices = {};
    const area = this.room.area;

    this.room.lights.forEach((l, i) => {
      const id = `light.${l.id}`;
      this.devices[`dev_${l.id}`] = { id: `dev_${l.id}`, area_id: area };
      this.entities[id] = { entity_id: id, device_id: `dev_${l.id}`, area_id: null, entity_category: l.category || null };
      this.states[id] = {
        entity_id: id,
        state: i < 3 && !l.category ? 'on' : 'off',
        attributes: { friendly_name: l.name, supported_color_modes: l.modes, dynamics: 'none', brightness: null },
      };
    });

    this.room.groups.forEach((g, gi) => {
      const id = `light.${g.id}`;
      const dev = `dev_${g.id}`;
      this.devices[dev] = { id: dev, area_id: g.area ? area : null };
      this.entities[id] = { entity_id: id, device_id: dev, area_id: null };
      this.states[id] = {
        entity_id: id,
        state: 'off',
        attributes: {
          friendly_name: g.name,
          entity_id: g.members.map((m) => `light.${m}`),
          is_hue_group: true,
          hue_type: gi === 0 ? 'room' : 'zone',
          hue_scenes: g.scenes.map(([name]) => name),
          supported_color_modes: COLOUR,
        },
      };
      g.scenes.forEach(([name, dynamic]) => {
        const sid = `scene.${g.id}_${slug(name)}`;
        this.entities[sid] = { entity_id: sid, device_id: dev, area_id: null };
        this.states[sid] = {
          entity_id: sid,
          state: 'unknown',
          attributes: { friendly_name: `${g.name} ${name}`, name, is_dynamic: !!dynamic, group_name: g.name },
        };
      });
    });

    // Start with the first static room scene applied, so the card looks lived-in.
    const firstRoom = this.room.groups[0];
    this._applyScene(`scene.${firstRoom.id}_${slug(firstRoom.scenes[1][0])}`, false, true);
  }

  // The "default" entities the card uses in demo mode.
  get area() {
    return this.room.area;
  }
  get roomGroup() {
    return `light.${this.room.groups[0].id}`;
  }
  get firstLight() {
    return `light.${this.room.lights[0].id}`;
  }

  hass(realHass) {
    return {
      ...(realHass || {}),
      states: { ...this.states },
      entities: this.entities,
      devices: this.devices,
      areas: { [this.room.area]: { area_id: this.room.area, name: this.room.areaName } },
      services: { light: { turn_on: {}, turn_off: {}, toggle: {} }, scene: { turn_on: {} }, hue: { activate_scene: {} } },
      callService: (domain, service, data, target) => this.callService(domain, service, data || {}, target || {}),
    };
  }

  stop() {
    clearInterval(this.timer);
    this.timer = null;
  }

  // ---- simulated services -------------------------------------------------

  callService(domain, service, data, target) {
    const ids = [].concat(target.entity_id || data.entity_id || []);
    if (domain === 'light') {
      ids.forEach((id) => this._lightService(service, id, data));
    } else if (domain === 'scene' && service === 'turn_on') {
      ids.forEach((id) => this._applyScene(id, false));
    } else if (domain === 'hue' && service === 'activate_scene') {
      ids.forEach((id) => this._applyScene(id, data.dynamic !== false));
    }
    this._refreshGroups();
    this.onChange();
    return Promise.resolve();
  }

  _set(id, state, attrs) {
    const old = this.states[id];
    this.states[id] = { ...old, state: state || old.state, attributes: { ...old.attributes, ...attrs } };
  }

  _members(id) {
    const st = this.states[id];
    return st && Array.isArray(st.attributes.entity_id) ? st.attributes.entity_id : [id];
  }

  _lightService(service, id, data) {
    this._members(id).forEach((bulb) => {
      const st = this.states[bulb];
      if (!st) return;
      const on = service === 'toggle' ? st.state !== 'on' : service === 'turn_on';
      if (!on) {
        this._set(bulb, 'off', { brightness: null, dynamics: 'none' });
        return;
      }
      const modes = st.attributes.supported_color_modes;
      const attrs = { dynamics: 'none', brightness: modes.includes('onoff') ? null : data.brightness || st.attributes.brightness || 200 };
      // An explicit colour or colour temperature stops an animation, as on Hue.
      if (data.color_temp_kelvin) Object.assign(attrs, { color_mode: 'color_temp', color_temp_kelvin: data.color_temp_kelvin, rgb_color: null, hs_color: null });
      this._set(bulb, 'on', attrs);
    });
    if (!Object.values(this.states).some((s) => s.attributes.dynamics === 'dynamic_palette')) this.stop();
  }

  _applyScene(sceneId, dynamic, quiet) {
    const scene = this.states[sceneId];
    if (!scene) return;
    const group = Object.keys(this.entities).find(
      (id) => id.startsWith('light.') && this.entities[id].device_id === this.entities[sceneId].device_id
    );
    const name = scene.attributes.name;
    const white = WHITE[name.toLowerCase()];
    const palette = scenePalette(name).map(hexToRgb);
    this._members(group).forEach((bulb, i) => {
      const modes = this.states[bulb].attributes.supported_color_modes;
      const attrs = { dynamics: dynamic && scene.attributes.is_dynamic ? 'dynamic_palette' : 'none' };
      if (modes.includes('onoff')) attrs.brightness = null;
      else attrs.brightness = white ? white[1] : 180;
      if (white || !modes.includes('xy')) {
        Object.assign(attrs, { color_mode: 'color_temp', color_temp_kelvin: white ? white[0] : 2700, rgb_color: null, hs_color: null });
      } else {
        const rgb = palette[i % palette.length];
        Object.assign(attrs, { color_mode: 'xy', rgb_color: rgb, xy_color: [0.4, 0.4], hs_color: null, color_temp_kelvin: null });
      }
      this._set(bulb, 'on', attrs);
    });
    this._set(sceneId, new Date().toISOString(), {});
    if (!quiet) this._refreshGroups();
    else this._refreshGroups();
    if (dynamic && scene.attributes.is_dynamic) this._animate(group, palette);
  }

  // Cycle the palette round the group's colour bulbs every couple of seconds.
  _animate(group, palette) {
    this.stop();
    this.timer = setInterval(() => {
      this.tick += 1;
      let still = false;
      this._members(group).forEach((bulb, i) => {
        const st = this.states[bulb];
        if (st.state !== 'on' || st.attributes.dynamics !== 'dynamic_palette') return;
        still = true;
        if (st.attributes.supported_color_modes.includes('xy')) {
          this._set(bulb, null, { rgb_color: palette[(i + this.tick) % palette.length] });
        }
      });
      if (!still) {
        this.stop();
        return;
      }
      this._refreshGroups();
      this.onChange();
    }, 2000);
  }

  // Groups are on if any member is on, at their lit members' average brightness.
  _refreshGroups() {
    this.room.groups.forEach((g) => {
      const id = `light.${g.id}`;
      const lit = this._members(id).map((m) => this.states[m]).filter((s) => s.state === 'on');
      const dimmable = lit.filter((s) => s.attributes.brightness);
      const first = lit.find((s) => s.attributes.rgb_color) || lit[0];
      this._set(id, lit.length ? 'on' : 'off', {
        brightness: dimmable.length ? Math.round(dimmable.reduce((a, s) => a + s.attributes.brightness, 0) / dimmable.length) : null,
        rgb_color: first ? first.attributes.rgb_color : null,
        color_temp_kelvin: first ? first.attributes.color_temp_kelvin : null,
        dynamics: lit.some((s) => s.attributes.dynamics === 'dynamic_palette'),
      });
    });
  }
}
