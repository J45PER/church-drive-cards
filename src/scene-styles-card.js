// Scene Styles card: the central place to set scene tile looks. Its config
// holds one entry per scene *name* (icon, up to three background colours, or
// a picture). Placed on the Design Presets "Scene styles" tab, light cards on
// every dashboard read these styles (see loadSceneStyles in scene-style.js).
// The card previews every scene name in the house with its current look.

import { createFormEditor } from './form-editor.js';
import { SUFFIX, LABEL } from './suffix.js';
import { builtInSceneNames, sceneBackground, sceneIcon, sceneKey, setSceneStyles } from './scene-style.js';
import { iconHtml, hydrateIcons } from './icons.js';

function titleCase(key) {
  return key.replace(/\b\w/g, (c) => c.toUpperCase());
}

// Every scene name worth styling: Hue scenes in this home (their own
// spelling), the built-in defaults, and anything already styled.
function sceneNames(hass, styles) {
  const names = new Map();
  Object.values((hass && hass.states) || {}).forEach((st) => {
    if (st.entity_id.startsWith('scene.') && st.attributes.name) {
      const key = sceneKey(st.attributes.name);
      if (!names.has(key)) names.set(key, { name: st.attributes.name.replace(/\s+\d+$/, ''), dynamic: st.attributes.is_dynamic === true, inHome: true });
    }
  });
  builtInSceneNames().forEach((key) => {
    if (!names.has(key)) names.set(key, { name: titleCase(key), dynamic: false, inHome: false });
  });
  (styles || []).forEach((s) => {
    const key = s && s.scene && sceneKey(s.scene);
    if (key && !names.has(key)) names.set(key, { name: s.scene, dynamic: false, inHome: false });
  });
  return [...names.entries()].map(([key, v]) => ({ key, ...v })).sort((a, b) => a.name.localeCompare(b.name));
}

export const SceneStylesCardEditor = createFormEditor({
  schema: (config, hass) => [
    { name: 'title', selector: { text: {} } },
    { name: 'only_home', selector: { boolean: {} } },
    {
      name: 'styles',
      selector: {
        object: {
          multiple: true,
          label_field: 'scene',
          description_field: 'icon',
          fields: {
            scene: {
              label: 'Scene name (applies in every room)',
              required: true,
              selector: {
                select: {
                  mode: 'dropdown',
                  custom_value: true,
                  options: sceneNames(hass, config.styles).map((n) => n.name),
                },
              },
            },
            icon: { label: 'Icon', selector: { icon: {} } },
            colour_1: { label: 'Background colour 1', selector: { color_rgb: {} } },
            colour_2: { label: 'Background colour 2 (optional)', selector: { color_rgb: {} } },
            colour_3: { label: 'Background colour 3 (optional)', selector: { color_rgb: {} } },
            image: { label: 'Picture (replaces the colours)', selector: { image: {} } },
          },
        },
      },
    },
  ],
  labels: {
    title: 'Title',
    only_home: 'Only preview scenes that exist in this home',
    styles: 'Custom scene styles',
  },
  helpers: {
    styles: 'Each entry restyles that scene name on every light card, on every dashboard. Leave a field empty to keep the default.',
  },
});

export class SceneStylesCard extends HTMLElement {
  setConfig(config) {
    this.config = config || {};
    // Editing this card restyles light cards on the page straight away.
    setSceneStyles(this.config.styles);
    if (this._hass) this._draw();
  }

  set hass(hass) {
    const first = !this._hass;
    this._hass = hass;
    if (first) this._draw();
  }

  _draw() {
    const cfg = this.config;
    const styled = new Set((cfg.styles || []).map((s) => s && s.scene && sceneKey(s.scene)).filter(Boolean));
    const names = sceneNames(this._hass, cfg.styles).filter((n) => !cfg.only_home || n.inHome || styled.has(n.key));
    this.innerHTML = `
      <ha-card style="border:none; box-shadow:0 3px 10px rgba(0,0,0,0.45); border-radius:16px; overflow:hidden; background:var(--card-background-color); padding:16px;">
        <style>
          .ssc-grid { display:grid; grid-template-columns:repeat(4, minmax(0, 1fr)); gap:8px; margin-top:12px; }
          .ssc-tile { position:relative; container-type:inline-size; aspect-ratio:1 / 1; border-radius:14px; overflow:hidden; }
          .ssc-name { position:absolute; left:6px; right:6px; bottom:6px; text-align:center; color:#fff; font-weight:600; line-height:1.15; font-size:clamp(9px, 12.5cqw, 13px); text-shadow:0 1px 2px rgba(0,0,0,0.6); white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
          @container (max-width: 99px) { .ssc-name { display:none; } .ssc-icon { top:50% !important; } }
        </style>
        <div style="font-size:1.5rem; font-weight:500; color:var(--primary-text-color);"></div>
        <div class="ssc-sub" style="margin-top:4px; color:var(--secondary-text-color); font-size:0.9rem;"></div>
        <div class="ssc-grid"></div>
      </ha-card>`;
    this.querySelector('div').textContent = cfg.title || 'Scene styles';
    this.querySelector('.ssc-sub').textContent = `${names.length} scenes · ${styled.size} custom · edit this card to change them`;
    const grid = this.querySelector('.ssc-grid');
    names.forEach((n) => {
      const tile = document.createElement('div');
      tile.className = 'ssc-tile';
      tile.title = styled.has(n.key) ? `${n.name} (custom style)` : n.name;
      tile.style.background = sceneBackground(n.name);
      tile.innerHTML = `
        <div style="position:absolute; inset:0; background:linear-gradient(to top, rgba(0,0,0,0.6), rgba(0,0,0,0) 65%);"></div>
        ${iconHtml(sceneIcon(n.name, n.dynamic), { size: '40cqw', cls: 'ssc-icon', style: 'position:absolute; left:50%; top:44%; transform:translate(-50%, -50%); color:#fff; filter:drop-shadow(0 1px 3px rgba(0,0,0,0.55));' })}
        ${styled.has(n.key) ? '<ha-icon icon="mdi:pencil" title="Custom style" style="position:absolute; top:6px; right:6px; --mdc-icon-size:clamp(12px, 18cqw, 18px); color:#fff; filter:drop-shadow(0 1px 2px rgba(0,0,0,0.7));"></ha-icon>' : ''}
        <div class="ssc-name"></div>`;
      tile.querySelector('.ssc-name').textContent = n.name;
      grid.appendChild(tile);
      hydrateIcons(tile);
    });
    this._rows = Math.ceil(names.length / 4);
  }

  getCardSize() {
    return 2 + (this._rows || 4) * 2;
  }

  getGridOptions() {
    return { columns: 12, min_columns: 6, rows: 'auto' };
  }

  static getConfigElement() {
    return document.createElement(`scene-styles-card-editor${SUFFIX}`);
  }

  static getStubConfig() {
    return { title: 'Scene styles', only_home: true, styles: [] };
  }
}

export function registerSceneStylesCard() {
  if (!customElements.get(`scene-styles-card-editor${SUFFIX}`)) {
    customElements.define(`scene-styles-card-editor${SUFFIX}`, SceneStylesCardEditor);
  }
  if (!customElements.get(`scene-styles-card${SUFFIX}`)) {
    customElements.define(`scene-styles-card${SUFFIX}`, SceneStylesCard);
  }
  window.customCards = window.customCards || [];
  window.customCards.push({
    type: `scene-styles-card${SUFFIX}`,
    name: `Scene Styles Card${LABEL}`,
    description: 'Central scene tile icons, colours and pictures for every Light Control card (put it on the Design Presets "Scene styles" tab)',
    preview: true,
    documentationURL: 'https://github.com/J45PER/church-drive-cards#readme',
  });
}
