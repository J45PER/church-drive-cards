// Light Control Card — mode-aware light/group/room control with a real
// visual editor, colour-tinted rows (never a literal white fill), scene
// chips, and Home Assistant's own more-info pop-up for the full picker.

import { createFormEditor } from './form-editor.js';
import { SUFFIX, LABEL } from './suffix.js';
import { sceneBackground, sceneIcon, scenePalette, loadSceneStyles, onSceneStylesChanged } from './scene-style.js';
import { DemoHome } from './demo-home.js';
import { iconHtml, hydrateIcons } from './icons.js';
import {
  UNIVERSAL_PREFIX,
  loadUniversalScenes,
  onUniversalScenesChanged,
  universalRef,
  universalScene,
  universalSceneActive,
  universalTarget,
  universalScenes,
  universalTurnOnData,
  universalDealColours,
  universalScenePlaying,
} from './universal-scenes.js';

const LCC_DEFAULT_MAX_SCENES = 8;

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

// White lights get a clearly warm or cool colour (pale near-whites blend into
// a dull grey on the dark card and look "off").
function lccKelvinColor(k) {
  if (k <= 2600) return '#ff9a3c';
  if (k <= 3400) return '#ffb45c';
  if (k <= 4800) return '#ffd08a';
  return '#8fc3ff';
}


function lccLightColor(st) {
  if (!st || st.state !== 'on') return '#ffc107';
  const a = st.attributes || {};
  // White light (colour-temperature mode, or a colour so pale it reads as
  // white): use a clear warm/cool colour for its temperature, not near-white.
  const paleColour = a.hs_color && a.hs_color[1] < 15;
  if ((a.color_mode === 'color_temp' || paleColour) && a.color_temp_kelvin) return lccKelvinColor(a.color_temp_kelvin);
  if (paleColour) return '#ffd08a';
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
// { entity, name?, icon?, image? } from the editor. A universal scene is
// `entity: universal:<key>` (or `scene: <name>` in YAML).
function lccNormalizeScenes(scenes) {
  return (scenes || [])
    .map((s) => (typeof s === 'string' ? { entity: s } : s))
    .map((s) => (s && !s.entity && s.scene ? { ...s, entity: `${UNIVERSAL_PREFIX}${String(s.scene).toLowerCase()}` } : s))
    .filter((s) => s && s.entity);
}

function lccIsUniversal(ref) {
  return String(ref || '').startsWith(UNIVERSAL_PREFIX);
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

// The Hue groups whose scenes belong on a card: the given groups plus any
// Hue zone made up only of this card's lights (e.g. "Living Room Ambience",
// whose device has no area).
function lccSceneGroups(hass, groupIds, lightIds) {
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
  return groups;
}

// A Hue group's scenes (they share the group light's device), in the Hue app
// order (`hue_scenes`).
function lccGroupScenes(hass, groupId) {
  const device = hass.entities && hass.entities[groupId] && hass.entities[groupId].device_id;
  if (!device) return [];
  const order = (hass.states[groupId] && hass.states[groupId].attributes.hue_scenes) || [];
  const rank = (id) => {
    const i = order.indexOf(hass.states[id].attributes.name);
    return i === -1 ? order.length : i;
  };
  return Object.values(hass.entities)
    .filter((e) => e.entity_id.startsWith('scene.') && e.device_id === device && !e.hidden && hass.states[e.entity_id])
    .map((e) => e.entity_id)
    .sort((a, b) => rank(a) - rank(b) || a.localeCompare(b));
}

// Scenes every card shows until its own are picked.
const LCC_DEFAULT_SCENES = ['bright', 'dimmed', 'relax', 'nightlight'];

// The card's groups and lights, worked out from its config the way the card
// does (a single bulb uses its room's).
function lccCardLights(hass, config) {
  const mode = config.mode || 'light';
  let area = config.area;
  if (mode === 'group') {
    return { groups: config.entity ? [config.entity] : [], lights: config.entity ? lccMembersOf(hass, config.entity) : [] };
  }
  if (mode === 'light') area = lccAreaOf(hass, hass.entities && hass.entities[config.entity]);
  const ids = lccCandidates(hass, 'room', area);
  return {
    groups: ids.filter((id) => lccIsGroupLike(hass.states[id])),
    lights: ids.filter((id) => !lccIsGroupLike(hass.states[id])),
  };
}

// Scene choices for the editor: only this card's room/zone scenes, labelled
// by scene name. A name that exists in more than one of its groups (e.g. the
// room and a zone both have "Bright") gets the group name added.
function lccSceneChoices(hass, config) {
  if (!hass || !hass.states) return [];
  loadUniversalScenes(hass);
  const { groups, lights } = lccCardLights(hass, config);
  // The room first, then its zones.
  const isRoom = (id) => hass.states[id].attributes.hue_type === 'room';
  const sceneGroups = lccSceneGroups(hass, groups, lights).sort(
    (a, b) => (isRoom(b) ? 1 : 0) - (isRoom(a) ? 1 : 0) || lccGroupName(hass, a).localeCompare(lccGroupName(hass, b))
  );
  const ids = sceneGroups.flatMap((g) => lccGroupScenes(hass, g));
  const nameOf = (id) => hass.states[id].attributes.name || hass.states[id].attributes.friendly_name || id;
  // Universal scenes first, one per scene per place: the room and each of
  // its zones. A card with no Hue group gets them for its lights as a whole.
  const options = [];
  universalScenes().forEach((u) => {
    if (!sceneGroups.length) options.push({ value: universalRef(u.key), label: u.name });
    sceneGroups.forEach((g) => options.push({ value: universalRef(u.key, g), label: `${u.name} · ${lccGroupName(hass, g)}` }));
  });
  // Then the Hue scenes, each labelled with the room or zone it belongs to;
  // one named like a universal scene is left out (it would do the same).
  const universalNames = new Set(universalScenes().map((u) => u.name.toLowerCase()));
  ids.filter((id) => !universalNames.has(nameOf(id).toLowerCase())).forEach((id) => {
    const group = hass.states[id].attributes.group_name;
    options.push({ value: id, label: group ? `${nameOf(id)} · ${group}` : nameOf(id) });
  });
  // Keep scenes already on the card visible even if they're from elsewhere.
  lccNormalizeScenes(config.scenes).forEach((s) => {
    if (options.some((o) => o.value === s.entity)) return;
    if (lccIsUniversal(s.entity)) {
      const u = universalScene(s.entity);
      const target = universalTarget(s.entity);
      if (u) options.push({ value: s.entity, label: target ? `${u.name} · ${lccGroupName(hass, target)}` : u.name });
      return;
    }
    const st = hass.states[s.entity];
    options.push({ value: s.entity, label: `${st ? nameOf(s.entity) : s.entity} (other room)` });
  });
  return options;
}

function lccGroupName(hass, id) {
  const st = hass.states[id];
  return (st && st.attributes.friendly_name) || id;
}

// `entities` config items may be plain IDs or { entity, name? } objects.
function lccNormalizeList(list) {
  return (list || []).map((s) => (typeof s === 'string' ? { entity: s } : s)).filter((s) => s && s.entity);
}

// Everything a room/zone card could show, for the editor's "Show" list:
// room mode offers the area's groups, any Hue zone made only of the area's
// bulbs (zones often have no area), then the bulbs; zone/group mode offers
// the group's members.
function lccCandidates(hass, mode, area, entity) {
  if (!hass || !hass.states) return [];
  if (mode === 'group') return entity ? lccMembersOf(hass, entity) : [];
  if (mode !== 'room' || !area) return [];
  const areaLights = Object.values(hass.entities || {})
    .filter(
      (e) =>
        e.entity_id.startsWith('light.') &&
        !e.hidden &&
        e.entity_category == null &&
        hass.states[e.entity_id] &&
        lccAreaOf(hass, e) === area
    )
    .map((e) => e.entity_id);
  const bulbs = areaLights.filter((id) => !lccIsGroupLike(hass.states[id]));
  const bulbSet = new Set(bulbs);
  const groups = areaLights.filter((id) => lccIsGroupLike(hass.states[id]));
  Object.values(hass.states).forEach((st) => {
    const members = st.entity_id.startsWith('light.') && lccIsGroupLike(st) ? st.attributes.entity_id : null;
    if (members && members.length && members.every((m) => bulbSet.has(m)) && !groups.includes(st.entity_id)) {
      groups.push(st.entity_id);
    }
  });
  return [...groups, ...bulbs];
}

// Default indentation for a set of rows: a Hue room at the top, other groups
// (zones) one level under it when a room is shown, and lights one level
// under the deepest group. A Show-list entry's own `level` (0-2) wins.
function lccLevels(hass, ids, explicit) {
  const isRoom = (id) => lccIsGroupLike(hass.states[id]) && hass.states[id].attributes.hue_type === 'room';
  const groups = ids.filter((id) => lccIsGroupLike(hass.states[id]));
  const hasRoom = groups.some(isRoom);
  const groupLevel = (id) => (isRoom(id) || !hasRoom ? 0 : 1);
  const lightLevel = groups.length ? Math.max(...groups.map(groupLevel)) + 1 : 0;
  const out = {};
  ids.forEach((id) => {
    const set = explicit[id];
    const level = set !== undefined && set !== null && set !== '' ? parseInt(set, 10) : NaN;
    out[id] = Number.isNaN(level)
      ? lccIsGroupLike(hass.states[id]) ? groupLevel(id) : lightLevel
      : Math.max(0, Math.min(2, level));
  });
  return out;
}

// The editor can't see the pretend home, so build one to list its lights.
const lccDemoCache = {};
function lccDemoFor(room) {
  const key = room || 'living_room';
  if (!lccDemoCache[key]) lccDemoCache[key] = new DemoHome(key, () => {});
  return lccDemoCache[key];
}

// The editor's scene choices, from the real home or the pretend one.
function lccEditorSceneOptions(config, hass) {
  // In demo mode the scene choices come from the pretend home.
  const sceneDemo = config.demo ? lccDemoFor(config.demo_room) : null;
  return sceneDemo
    ? lccSceneChoices(sceneDemo.hass(), {
        ...config,
        mode: config.mode || 'room',
        area: sceneDemo.area,
        entity: config.mode === 'group' ? sceneDemo.roomGroup : sceneDemo.firstLight,
      })
    : lccSceneChoices(hass, config);
}

// A new card's scene list starts with the four defaults, aimed at its room
// (the first place in the choices), so they show in the editor. If the card
// is moved to another room before they're changed, they follow it.
function lccFillDefaultScenes(config, hass) {
  if (!universalScenes().length) return config;
  const options = lccEditorSceneOptions({ ...config, scenes: undefined }, hass);
  const defaults = LCC_DEFAULT_SCENES.map((key) => {
    const option = options.find((o) => o.value === universalRef(key) || o.value.startsWith(`${universalRef(key)}@`));
    return option ? { entity: option.value } : null;
  }).filter(Boolean);
  if (!defaults.length) return config;
  // The "Reset scenes" button puts the defaults back.
  if (config.scenes === 'reset') return { ...config, scenes: defaults };
  if (config.scenes == null) return { ...config, scenes: defaults };
  const current = lccNormalizeScenes(config.scenes);
  const untouched =
    current.length === LCC_DEFAULT_SCENES.length &&
    current.every((sc, i) => Object.keys(sc).length === 1 && String(sc.entity).split('@')[0] === universalRef(LCC_DEFAULT_SCENES[i]));
  const elsewhere = current.some((sc) => !options.some((o) => o.value === sc.entity));
  return untouched && elsewhere ? { ...config, scenes: defaults } : config;
}

// Each listed scene is labelled by its name and place ("Bright · Kitchen")
// instead of its reference. HA's list shows a field's raw value, so the label
// is a form-only field (an invisible constant), added here and dropped on save.
function lccLabelScenes(config, hass) {
  if (!config.scenes) return config;
  const options = lccEditorSceneOptions(config, hass);
  const labelOf = (s) => (options.find((o) => o.value === s.entity) || {}).label || s.entity;
  return { ...config, scenes: lccNormalizeScenes(config.scenes).map((s) => ({ ...s, label: labelOf(s) })) };
}

export const LightControlCardEditor = createFormEditor({
  fill: lccFillDefaultScenes,
  buttons: [{ label: 'Reset scenes to Bright, Dimmed, Relax and Nightlight', apply: (config) => ({ ...config, scenes: 'reset' }) }],
  display: lccLabelScenes,
  store: (config) =>
    config.scenes ? { ...config, scenes: config.scenes.map(({ label: _label, ...s }) => s) } : config,
  schema: (config, hass) => {
    const mode = config.mode || 'light';
    loadUniversalScenes(hass);
    const sceneOptions = lccEditorSceneOptions(config, hass);
    // Choices for the "Show" list, from the real home or the pretend one.
    let showField = [];
    if (mode === 'room' || mode === 'group') {
      const demo = config.demo ? lccDemoFor(config.demo_room) : null;
      const h = demo ? demo.hass() : hass;
      const ids = demo
        ? lccCandidates(h, mode, demo.area, demo.roomGroup)
        : lccCandidates(h, mode, config.area, config.entity);
      const options = ids.map((id) => {
        const st = h.states[id];
        const kind = lccIsGroupLike(st) ? (st.attributes.hue_type === 'room' ? 'room' : 'zone') : 'light';
        return { value: id, label: `${(st && st.attributes.friendly_name) || id} (${kind})` };
      });
      showField = [
        {
          name: 'entities',
          title: mode === 'room' ? 'Show these lights and zones, in this order (empty = all)' : 'Show these lights, in this order (empty = all)',
          selector: {
            object: {
              multiple: true,
              label_field: 'entity',
              description_field: 'name',
              fields: {
                entity: { label: mode === 'room' ? 'Light or zone' : 'Light', required: true, selector: { select: { mode: 'dropdown', options } } },
                name: { label: 'Name override', selector: { text: {} } },
                icon: { label: 'Icon override', selector: { icon: {} } },
                level: {
                  label: 'Level (indentation)',
                  selector: {
                    select: {
                      mode: 'dropdown',
                      options: [
                        { value: '0', label: 'Top' },
                        { value: '1', label: 'Child (indented once)' },
                        { value: '2', label: 'Grandchild (indented twice)' },
                      ],
                    },
                  },
                },
              },
            },
          },
        },
      ];
    }
    const demoFields = [
      {
        type: 'expandable',
        name: '',
        title: 'Demo mode (pretend lights, for Design Presets)',
        flatten: true,
        schema: [
          { name: 'demo', selector: { boolean: {} } },
          {
            name: 'demo_room',
            selector: {
              select: {
                mode: 'dropdown',
                options: [
                  { value: 'living_room', label: 'Living Room (room + animated-scene zone)' },
                  { value: 'bedroom', label: 'Bedroom (two groups + a hidden settings light)' },
                ],
              },
            },
          },
        ],
      },
    ];
    return [
      {
        name: 'mode',
        selector: {
          select: {
            mode: 'dropdown',
            options: [
              { value: 'light', label: 'Single Light' },
              { value: 'group', label: 'Zone or light group' },
              { value: 'room', label: 'Room' },
            ],
          },
        },
      },
      // In demo mode the pretend home supplies the room/light, so hide these.
      ...(config.demo
        ? []
        : [
            mode === 'room'
              ? { name: 'area', selector: { area: {} } }
              : { name: 'entity', selector: { entity: { domain: 'light' } } },
          ]),
      ...showField,
      { name: 'name', selector: { text: {} } },
      ...(mode === 'room' ? [] : [{ name: 'icon', selector: { icon: {} } }]),
      { name: 'max_scenes', selector: { number: { mode: 'box', min: 0, max: 24 } } },
      {
        name: 'scene_names',
        selector: {
          select: {
            mode: 'dropdown',
            options: [
              { value: 'auto', label: 'Auto (hide on small tiles)' },
              { value: 'always', label: 'Always show' },
              { value: 'never', label: 'Never show (icons only)' },
            ],
          },
        },
      },
      {
        name: 'scenes',
        selector: {
          object: {
            multiple: true,
            label_field: 'label',
            description_field: 'name',
            fields: {
              entity: { label: 'Scene', required: true, selector: { select: { mode: 'dropdown', options: sceneOptions } } },
              name: { label: 'Name override', selector: { text: {} } },
              icon: { label: 'Icon override', selector: { icon: {} } },
              image: { label: 'Picture (replaces the colour background)', selector: { image: {} } },
              label: { selector: { constant: { value: '', label: '' } } },
            },
          },
        },
      },
      ...demoFields,
    ];
  },
  normalize: (config) => ({
    ...config,
    ...(config.scenes ? { scenes: lccNormalizeScenes(config.scenes) } : {}),
    ...(config.entities ? { entities: lccNormalizeList(config.entities) } : {}),
  }),
  labels: {
    mode: 'Card type',
    area: 'Room',
    entity: 'Light, zone or group',
    name: 'Title (optional)',
    icon: 'Icon override (optional)',
    max_scenes: 'Max scenes',
    scene_names: 'Scene names',
    scenes: 'Scenes',
    demo: 'Use pretend lights instead of real ones',
    demo_room: 'Pretend room',
  },
  helpers: {
    max_scenes: 'Default 8 (two rows). Set 0 to hide scenes.',
    demo: 'Nothing is sent to Home Assistant; taps only change the pretend lights on this card.',
  },
});

export class LightControlCard extends HTMLElement {
  setConfig(config) {
    if (!config.entity && !config.area && !config.demo) throw new Error('entity or area required');
    this.config = config;
    this._built = false;
    this._lastIds = null;
    if (this._demo) this._demo.stop();
    this._demo = null;
  }

  // Central scene styles (Design Presets "Scene styles" tab) can change after
  // this card has drawn; redraw when they do.
  connectedCallback() {
    if (!this._unsubUniversal) {
      this._unsubUniversal = onUniversalScenesChanged(() => {
        this._lastIds = null;
        if (this._lastInput) this.hass = this._lastInput;
      });
    }
    if (!this._unsubStyles) {
      this._unsubStyles = onSceneStylesChanged(() => {
        this._lastIds = null;
        if (this._lastInput) this.hass = this._lastInput;
      });
    }
  }

  disconnectedCallback() {
    if (this._demo) this._demo.stop();
    if (this._unsubStyles) this._unsubStyles();
    this._unsubStyles = null;
    if (this._unsubUniversal) this._unsubUniversal();
    this._unsubUniversal = null;
  }

  // Demo mode: a pretend home (demo-home.js) stands in for Home Assistant, so
  // taps only change the pretend lights and nothing reaches real devices.
  // Its room/group/first light replace any configured area or entity.
  _demoHass(realHass) {
    if (!this._demo) {
      this._demo = new DemoHome(this.config.demo_room, () => this._render(this._demo.hass(this._realHass)));
    }
    this._realHass = realHass || this._realHass;
    return this._demo.hass(this._realHass);
  }

  _effectiveConfig() {
    const cfg = this.config;
    if (!cfg.demo || !this._demo) return cfg;
    const mode = cfg.mode || 'room';
    return {
      ...cfg,
      mode,
      area: this._demo.area,
      entity: mode === 'group' ? this._demo.roomGroup : this._demo.firstLight,
      // Only universal scenes and scenes from the pretend home apply (real
      // ones don't exist there); with none left, the defaults are used.
      scenes: cfg.scenes == null ? undefined : lccNormalizeScenes(cfg.scenes).filter((sc) => lccIsUniversal(sc.entity) || this._demo.states[sc.entity]),
    };
  }

  static getConfigElement() {
    return document.createElement(`light-control-card-editor${SUFFIX}`);
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

  // Pause a playing animated scene. Re-applying the scene with dynamic: false
  // doesn't work: Hue restarts the animation for scenes set to animate
  // automatically. Any explicit colour command does stop it, so send each lit
  // bulb of the scene's group the colour and brightness it's showing now.
  // Real home: the integration applies it (colour scenes go through the Hue
  // bridge so they can animate). Pretend home: set the lights directly.
  _applyUniversal(scene) {
    const lib = scene.universal;
    if (!this.config.demo) {
      this._hass.callService('church_drive', 'apply_scene', { entity_id: scene.targets, scene: lib.key });
    } else if (lib.kind === 'colour' && this._demo) {
      this._demo.playPalette(scene.targetLights, lib.colors, lib.brightness, !!lib.dynamic);
    } else if (lib.kind === 'colour') {
      universalDealColours(lib, scene.targetLights).forEach(({ entity_id, ...data }) =>
        this._hass.callService('light', 'turn_on', data, { entity_id })
      );
    } else {
      this._hass.callService('light', 'turn_on', universalTurnOnData(lib), { entity_id: scene.targets });
    }
  }

  _freezeScene(scene) {
    const hass = this._hass;
    const ids = scene.group ? lccMembersOf(hass, scene.group) : scene.targetLights || this._cardLightIds || [];
    ids
      .map((id) => hass.states[id])
      .filter((st) => st && st.state === 'on' && !lccIsGroupLike(st))
      .forEach((st) => {
        const a = st.attributes;
        const data = {};
        if (a.color_mode === 'color_temp' && a.color_temp_kelvin) data.color_temp_kelvin = a.color_temp_kelvin;
        else if (a.xy_color) data.xy_color = a.xy_color;
        else if (a.color_temp_kelvin) data.color_temp_kelvin = a.color_temp_kelvin;
        if (a.brightness) data.brightness = a.brightness;
        hass.callService('light', 'turn_on', data, { entity_id: st.entity_id });
      });
  }

  // A single full-width row that IS the control: background is a low-opacity
  // TINT of the light's colour blended into the card background (never a
  // literal paint of the colour — a white/bright light would otherwise wash
  // the row to solid white and make the text unreadable), sized to the
  // brightness. Tap toggles; drag horizontally sets brightness on dimmable
  // lights.
  _buildRow(entityId, { withMoreInfo, level = 0, icon: iconOverride }) {
    const st = this._hass.states[entityId];
    const name = (st && st.attributes.friendly_name) || entityId;
    const isGroupLike = lccIsGroupLike(st);
    const on = st && st.state === 'on';
    const dimmable =
      st && st.attributes.supported_color_modes && st.attributes.supported_color_modes.some((m) => m !== 'onoff');
    const color = lccLightColor(st);
    const icon = iconOverride || lccLightIcon(this._hass, st, isGroupLike);
    // State shown in words on the right, so on/off is never a guess.
    const stateText = !st || st.state === 'unavailable' ? 'Unavailable' : !on ? 'Off' : dimmable && st.attributes.brightness ? `${Math.round((st.attributes.brightness / 255) * 100)}%` : 'On';
    const brightnessPct = st && st.attributes.brightness ? Math.round((st.attributes.brightness / 255) * 100) : 0;
    const fillPct = on ? (dimmable ? Math.max(brightnessPct, 4) : 100) : 0;

    // Tint the fill: blend the light's colour into the card's own background at
    // ~30% strength rather than a literal fill, so white/bright lights never
    // wash the row to solid white. color-mix keeps this working for any hue.
    const tint = `color-mix(in srgb, ${color} 40%, var(--card-background-color, #1c1c1c))`;
    const track = 'rgba(255,255,255,0.06)';

    const row = document.createElement('div');
    row.className = 'lcc-row';
    // Indentation levels: 0 top, 1 child, 2 grandchild (16px each).
    const pad = level > 0 ? '9px 14px' : '12px 14px';
    const indent = level > 0 ? `margin-left:${16 * level}px;` : '';
    row.style.cssText = `position:relative; display:flex; align-items:center; gap:12px; padding:${pad}; ${indent} border-radius:12px; margin-top:6px; overflow:hidden; cursor:pointer; user-select:none; touch-action:pan-y; background: linear-gradient(to right, ${tint} 0%, ${tint} ${fillPct}%, ${track} ${fillPct}%, ${track} 100%);`;
    row.innerHTML = `
      ${iconHtml(icon, { size: '24px', cls: 'lcc-row-icon', style: `color:${on ? color : 'var(--secondary-text-color)'}; opacity:${on ? 1 : 0.6}; flex-shrink:0; pointer-events:none;` })}
      <div class="lcc-name" style="flex:1; min-width:0; font-weight:${on ? 600 : 400}; color:${on ? 'var(--primary-text-color)' : 'var(--secondary-text-color)'}; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; pointer-events:none;">${name}</div>
      <div class="lcc-state" style="flex-shrink:0; font-size:0.85rem; font-variant-numeric:tabular-nums; color:${on ? 'var(--primary-text-color)' : 'var(--secondary-text-color)'}; opacity:${on ? 0.9 : 0.7}; pointer-events:none;">${stateText}</div>
      ${withMoreInfo && !this.config.demo ? `<ha-icon class="lcc-more" icon="mdi:tune-variant" style="color:var(--secondary-text-color); --mdc-icon-size:20px; cursor:pointer; flex-shrink:0;"></ha-icon>` : ''}
    `;

    if (withMoreInfo && !this.config.demo) {
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
      // While dragging, show the brightness being set.
      if (dragging && moved) row.querySelector('.lcc-state').textContent = pct <= 2 ? 'Off' : `${Math.round(pct)}%`;
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
        this._render(h);
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

  // Scene tiles, the same height as a light row: picture (or palette
  // gradient) background with the icon and name side by side. At most four
  // per row in as few rows as possible, shared out evenly with any fuller
  // row last (6 = 3 + 3, 5 = 2 + 3, 7 = 3 + 4); each row fills the width.
  // The selected scene glows in its own colour and the others are dimmed; an
  // animated scene shows a pulsing play badge while running, pause when not.
  _buildScenes(scenes) {
    if (!scenes.length) return null;
    const anyActive = scenes.some((s) => s.active);
    const names = ['always', 'never'].includes(this.config.scene_names) ? this.config.scene_names : 'auto';
    const wrap = document.createElement('div');
    wrap.className = `lcc-names-${names}`;
    wrap.style.cssText = 'display:flex; flex-direction:column; gap:6px; margin-top:10px; padding:2px 0 4px;';
    const rowCount = Math.ceil(scenes.length / 4);
    const base = Math.floor(scenes.length / rowCount);
    const extra = scenes.length % rowCount;
    const rows = Array.from({ length: rowCount }, (_, i) => {
      const row = document.createElement('div');
      row.style.cssText = 'display:flex; gap:6px;';
      row.dataset.size = base + (i >= rowCount - extra ? 1 : 0);
      wrap.appendChild(row);
      return row;
    });
    let rowIndex = 0;
    scenes.forEach((s) => {
      while (rows[rowIndex].children.length >= Number(rows[rowIndex].dataset.size)) rowIndex += 1;
      const tile = document.createElement('button');
      tile.className = s.active || !anyActive ? 'lcc-scene' : 'lcc-scene lcc-dim';
      const glow = `color-mix(in srgb, ${scenePalette(s.name, s.colours)[0]} 85%, transparent)`;
      tile.title = s.playing ? `${s.name} (playing, tap to stop)` : s.paused ? `${s.name} (paused, tap to play)` : s.name;
      const bg = sceneBackground(s.name, s.image, s.colours);
      tile.style.cssText = `position:relative; container-type:inline-size; flex:1 1 0; min-width:0; height:48px; border:none; border-radius:12px; padding:0; overflow:hidden; cursor:pointer; background:${bg};${
        s.active ? ` box-shadow:0 0 12px 2px ${glow}; transform:scale(1.03); z-index:1;` : ''
      }`;
      const badge = (cls, icon, title) =>
        `<ha-icon class="${cls}" icon="${icon}" title="${title}" style="position:absolute; top:50%; right:5px; transform:translateY(-50%); --mdc-icon-size:16px; color:#fff; filter:drop-shadow(0 1px 2px rgba(0,0,0,0.7));"></ha-icon>`;
      tile.innerHTML = `
        <div style="position:absolute; inset:0; background:rgba(0,0,0,0.18);"></div>
        <div class="lcc-scene-body" style="position:relative; display:flex; align-items:center; justify-content:center; gap:6px; height:100%; padding:0 8px; color:#fff;">
          ${iconHtml(s.icon, { size: '22px', cls: 'lcc-scene-icon', style: 'flex-shrink:0; filter:drop-shadow(0 1px 3px rgba(0,0,0,0.55));' })}
          <span class="lcc-scene-name" style="min-width:0; font-weight:600; line-height:1.15; text-shadow:0 1px 2px rgba(0,0,0,0.6); white-space:nowrap; overflow:hidden; text-overflow:ellipsis;"></span>
        </div>
        ${s.paused ? badge('lcc-paused', 'mdi:pause', 'Paused') : ''}
        ${s.playing ? badge('lcc-playing', 'mdi:play', 'Playing') : ''}`;
      tile.querySelector('.lcc-scene-name').textContent = s.name;
      this._bindSceneTile(tile, s);
      rows[rowIndex].appendChild(tile);
    });
    return wrap;
  }

  // Tap: activate (or stop a playing animation). Press and hold (~0.5s):
  // turn off all of this card's lights. Moving the finger (a scroll) cancels
  // the hold, and the browser's long-press menu is suppressed.
  _bindSceneTile(tile, scene) {
    let timer = null;
    let held = false;
    let moved = false;
    let start = null;
    const cancel = () => {
      clearTimeout(timer);
      timer = null;
      tile.style.opacity = '';
    };
    tile.style.webkitTouchCallout = 'none';
    tile.style.userSelect = 'none';
    tile.addEventListener('contextmenu', (ev) => ev.preventDefault());
    tile.addEventListener('pointerdown', (ev) => {
      held = false;
      moved = false;
      start = { x: ev.clientX, y: ev.clientY };
      timer = setTimeout(() => {
        held = true;
        timer = null;
        tile.style.opacity = '0.6';
        if (navigator.vibrate) navigator.vibrate(30);
        this._turnOffCardLights();
      }, 500);
    });
    tile.addEventListener('pointermove', (ev) => {
      if (start && Math.hypot(ev.clientX - start.x, ev.clientY - start.y) > 10) {
        moved = true;
        cancel();
      }
    });
    ['pointerup', 'pointercancel', 'pointerleave'].forEach((type) => tile.addEventListener(type, cancel));
    tile.addEventListener('click', (ev) => {
      if (held || moved) {
        // A hold already turned the lights off, and a drag isn't a tap.
        ev.preventDefault();
        held = false;
        moved = false;
        return;
      }
      if (scene.playing) this._freezeScene(scene);
      else if (scene.universal) this._applyUniversal(scene);
      else this._activateScene(scene.entity, scene.isDynamic);
    });
  }

  _turnOffCardLights() {
    const ids = this._cardLightIds || [];
    if (ids.length) this._hass.callService('light', 'turn_off', {}, { entity_id: ids });
  }

  // Resolve the scenes to show (configured list or the defaults), capped at
  // max_scenes, with display name/icon/picture and selected/playing status.
  _resolveScenes(hass, mode, headIds, memberIds) {
    const cfg = this._effectiveConfig();
    const max = cfg.max_scenes != null ? cfg.max_scenes : LCC_DEFAULT_MAX_SCENES;
    if (max <= 0) return [];
    // No scenes set at all: the defaults. An emptied list shows none.
    const items = cfg.scenes == null ? LCC_DEFAULT_SCENES.map((key) => ({ entity: universalRef(key) })) : lccNormalizeScenes(cfg.scenes);
    // Universal scenes apply to the card's room (or its zones/groups, or its
    // lights when it shows no group) with one light.turn_on.
    const headGroups = headIds.filter((id) => lccIsGroupLike(hass.states[id]));
    const rooms = headGroups.filter((id) => hass.states[id].attributes.hue_type === 'room');
    const targets = rooms.length ? rooms : headGroups.length ? headGroups : [...headIds, ...memberIds];
    const targetLights = [...new Set(targets.flatMap((id) => (lccIsGroupLike(hass.states[id]) ? lccMembersOf(hass, id) : [id])))];
    const scenes = items
      .filter((s) => (lccIsUniversal(s.entity) ? universalScene(s.entity) : hass.states[s.entity]))
      .slice(0, max)
      .map((s) => {
        if (lccIsUniversal(s.entity)) {
          const lib = universalScene(s.entity);
          // Aimed at one zone/group: just its lights, and the tile says where
          // (the zone name without the room's, e.g. "Bright · Spotlights").
          const target = universalTarget(s.entity);
          const own = target && hass.states[target] ? [target] : null;
          let name = s.name || lib.name;
          if (!s.name && own && hass.states[target].attributes.hue_type !== 'room') {
            const area = hass.areas && cfg.area && hass.areas[cfg.area];
            const room = rooms[0] ? lccGroupName(hass, rooms[0]) : (area && area.name) || '';
            const zone = lccGroupName(hass, target);
            const short = room && zone.toLowerCase().startsWith(`${room.toLowerCase()} `) ? zone.slice(room.length + 1) : zone;
            name = `${lib.name} · ${short}`;
          }
          const tTargets = own || targets;
          const tLights = own
            ? [...new Set(own.flatMap((id) => (lccIsGroupLike(hass.states[id]) ? lccMembersOf(hass, id) : [id])))]
            : targetLights;
          const isDynamic = lib.kind === 'colour' && !!lib.dynamic;
          return {
            ...s,
            name,
            isDynamic,
            icon: s.icon || lib.icon || sceneIcon(lib.name, isDynamic),
            colours: lib.hex,
            universal: lib,
            targets: tTargets,
            targetLights: tLights,
            activated: 0,
          };
        }
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

    // A universal scene is selected while its room/zone shows it: the
    // integration's scene select says so for a Hue room/zone, otherwise the
    // lights have to match it. Animated ones show playing or paused.
    const selectFor = (target) =>
      Object.values(hass.states).find((st) => st.entity_id.startsWith('select.') && st.attributes.target === target);
    // Watch those selects so the card redraws when they change.
    this._sceneSelects = scenes
      .filter((sc) => sc.universal && sc.targets.length === 1)
      .map((sc) => selectFor(sc.targets[0]))
      .filter(Boolean)
      .map((st) => st.entity_id);
    const matching = scenes.find((sc) => {
      if (!sc.universal) return false;
      const sel = sc.targets.length === 1 ? selectFor(sc.targets[0]) : null;
      if (sel) return sel.attributes.scene_key === sc.universal.key;
      return universalSceneActive(hass, sc.universal, sc.targetLights);
    });
    if (matching) {
      matching.active = true;
      if (matching.isDynamic) {
        matching.playing = universalScenePlaying(hass, matching.targetLights);
        matching.paused = !matching.playing;
      }
      return scenes;
    }

    // Selected = the most recently activated of these scenes, while its
    // group's lights are still on. Playing = that scene is animated and Hue
    // reports any of the group's lit bulbs as running a dynamic palette.
    const latest = scenes.filter((sc) => !sc.universal).reduce((a, b) => (b.activated > (a ? a.activated : 0) ? b : a), null);
    if (latest) {
      const groupSt = latest.group && hass.states[latest.group];
      const lightsOn = groupSt
        ? groupSt.state === 'on'
        : [...headIds, ...memberIds].some((id) => hass.states[id] && hass.states[id].state === 'on');
      latest.active = lightsOn;
      if (lightsOn && latest.isDynamic && groupSt) {
        // Only lit bulbs count: Hue leaves `dynamics` set on bulbs (and so on
        // the group) after they're switched off.
        latest.playing = lccMembersOf(hass, latest.group).some(
          (id) => hass.states[id].state === 'on' && hass.states[id].attributes.dynamics === 'dynamic_palette'
        );
        // Selected animated scene that isn't animating (e.g. stopped by a tap).
        latest.paused = !latest.playing;
      }
    }
    return scenes;
  }

  set hass(hass) {
    this._lastInput = hass;
    loadSceneStyles(hass);
    loadUniversalScenes(hass);
    this._render(this.config.demo ? this._demoHass(hass) : hass);
  }

  _render(hass) {
    this._hass = hass;
    const cfg = this._effectiveConfig();
    const mode = cfg.mode || (cfg.area ? 'room' : 'light');

    if (!this._built) {
      this.innerHTML = `
        <ha-card style="border:none; box-shadow: 0 3px 10px rgba(0,0,0,0.45); border-radius:16px; overflow:hidden; background: var(--card-background-color); padding:16px 16px 14px 16px;">
          <style>
            @keyframes lcc-pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.5; } }
            .lcc-playing { animation: lcc-pulse 1.6s ease-in-out infinite; }
            .lcc-scene { transition: opacity 0.2s, filter 0.2s, transform 0.2s, box-shadow 0.2s; }
            .lcc-scene.lcc-dim { opacity: 0.4; filter: saturate(0.4); }
            .lcc-scene.lcc-dim:hover { opacity: 0.8; filter: none; }
            /* Up to four tiles per row, each as tall as a light row. Names
               sit beside the icon on one line (… if too long); auto hides
               them on tiles under 100px wide, never always does. */
            .lcc-scene-name { font-size: 13px; }
            @container (max-width: 99px) {
              .lcc-names-auto .lcc-scene-name { display: none; }
            }
            .lcc-names-never .lcc-scene-name { display: none; }
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
    // `rows` is the display order; `names` holds per-row name overrides.
    // A configured `entities` list picks and orders what's shown (only IDs
    // that exist; if none do, fall back to showing everything).
    let headIds = [];
    let memberIds = [];
    let rows = [];
    const names = {};
    const levels = {};
    const icons = {};
    const chosen = lccNormalizeList(cfg.entities).filter((s) => hass.states[s.entity]);
    chosen.forEach((s) => {
      if (s.name) names[s.entity] = s.name;
      if (s.level !== undefined) levels[s.entity] = s.level;
      if (s.icon) icons[s.entity] = s.icon;
    });
    if (mode === 'room') {
      if (chosen.length) {
        const ids = chosen.map((s) => s.entity);
        headIds = ids.filter((id) => lccIsGroupLike(hass.states[id]));
        memberIds = ids.filter((id) => !lccIsGroupLike(hass.states[id]));
        const lv = lccLevels(hass, ids, levels);
        rows = ids.map((id) => ({ id, level: lv[id] }));
      } else {
        const all = lccCandidates(hass, 'room', cfg.area).filter((id) => {
          // Auto mode keeps the original behaviour: only groups that have
          // this area themselves, not zones found by their members.
          if (!lccIsGroupLike(hass.states[id])) return true;
          return lccAreaOf(hass, hass.entities && hass.entities[id]) === cfg.area;
        });
        headIds = all.filter((id) => lccIsGroupLike(hass.states[id]));
        memberIds = all.filter((id) => !lccIsGroupLike(hass.states[id]));
        const ordered = [...headIds, ...memberIds];
        const lv = lccLevels(hass, ordered, {});
        rows = ordered.map((id) => ({ id, level: lv[id] }));
      }
    } else {
      headIds = [cfg.entity];
      if (mode === 'group') {
        const members = lccMembersOf(hass, cfg.entity);
        const picked = chosen.map((s) => s.entity).filter((id) => members.includes(id));
        memberIds = picked.length ? picked : members;
      }
      if (cfg.name) names[cfg.entity] = cfg.name;
      if (cfg.icon) icons[cfg.entity] = cfg.icon;
      const level = (id, fallback) => {
        const n = parseInt(levels[id], 10);
        return Number.isNaN(n) ? fallback : Math.max(0, Math.min(2, n));
      };
      rows = [{ id: cfg.entity, level: 0 }, ...memberIds.map((id) => ({ id, level: level(id, 1) }))];
    }
    const relevantEntityIds = rows.map((r) => r.id);
    this._cardLightIds = relevantEntityIds;
    const scenes = this._resolveScenes(hass, mode, headIds, memberIds);
    const watchIds = [
      ...relevantEntityIds,
      ...scenes.filter((s) => !s.universal).map((s) => s.entity),
      ...scenes.flatMap((s) => s.targetLights || []),
      ...(this._sceneSelects || []),
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
      if (rows.length === 0) {
        this._main.textContent = 'No lights found in this area.';
        this._main.style.cssText = 'color:var(--secondary-text-color); padding:8px 4px;';
      }
    } else {
      this._titleEl.style.display = 'none';
    }
    // Rows in display order; bulbs are indented under any group row.
    rows.forEach(({ id, level }) => {
      const row = this._buildRow(id, { withMoreInfo: true, level, icon: icons[id] });
      const nameEl = row.querySelector('.lcc-name');
      if (names[id] && nameEl) nameEl.textContent = names[id];
      this._main.appendChild(row);
    });

    this._scenesEl.innerHTML = '';
    const scenesGrid = this._buildScenes(scenes);
    if (scenesGrid) this._scenesEl.appendChild(scenesGrid);
    hydrateIcons(this);

    // ~1 masonry unit (50px) per row, plus card padding and room title; scene
    // tiles are ~2 units per row of about four.
    this._size =
      1 + (mode === 'room' ? 1 : 0) + Math.max(relevantEntityIds.length, 1) + Math.ceil(scenes.length / 4);
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
  if (!customElements.get(`light-control-card-editor${SUFFIX}`)) {
    customElements.define(`light-control-card-editor${SUFFIX}`, LightControlCardEditor);
  }
  if (!customElements.get(`light-control-card${SUFFIX}`)) {
    customElements.define(`light-control-card${SUFFIX}`, LightControlCard);
  }
  window.customCards = window.customCards || [];
  window.customCards.push({
    type: `light-control-card${SUFFIX}`,
    name: `Light Control Card${LABEL}`,
    description: 'Light/group/room control with icon, toggle, brightness, scenes, and full more-info pop-up (visual editor supported)',
    preview: true,
    documentationURL: 'https://github.com/J45PER/church-drive-cards#readme',
  });
}
