// Gauge Zone Card — generic %/value gauge rows with a gradient fill.
// battery-zone-card is this same class registered a second time with
// battery-friendly defaults (auto battery icon stepping, low-is-bad colouring).

import { createFormEditor } from './form-editor.js';

export const GaugeZoneCardEditor = createFormEditor({
  schema: (config) => [
    { name: 'title', selector: { text: {} } },
    {
      type: 'expandable',
      name: '',
      title: 'Colours, units and icons',
      flatten: true,
      schema: [
        {
          name: 'direction',
          selector: {
            select: {
              mode: 'dropdown',
              options: [
                { value: 'low', label: 'Low value is bad (battery, signal)' },
                { value: 'high', label: 'High value is bad (storage, CPU)' },
              ],
            },
          },
        },
        {
          type: 'grid',
          name: '',
          schema: [
            { name: 'alert_at', selector: { number: { mode: 'box' } } },
            { name: 'warn_at', selector: { number: { mode: 'box' } } },
            { name: 'unit', selector: { text: {} } },
            { name: 'max', selector: { number: { mode: 'box', min: 0 } } },
          ],
        },
        {
          name: 'icon_mode',
          selector: {
            select: {
              mode: 'dropdown',
              options: [
                { value: 'battery', label: 'Battery (steps with the value)' },
                { value: 'gauge', label: 'Gauge' },
                { value: 'entity', label: "Each entity's own icon" },
                { value: 'custom', label: 'Custom icon (pick below)' },
              ],
            },
          },
        },
        ...(config.icon_mode === 'custom' ? [{ name: 'icon', selector: { icon: {} } }] : []),
      ],
    },
    {
      name: 'entities',
      selector: {
        object: {
          multiple: true,
          label_field: 'name',
          description_field: 'entity',
          fields: {
            entity: { label: 'Entity', selector: { entity: {} } },
            name: { label: 'Name', required: true, selector: { text: {} } },
            word: {
              label: 'Battery wording (shows the Battery Notes date)',
              selector: {
                select: {
                  mode: 'dropdown',
                  custom_value: true,
                  options: ['replaced', 'charged', 'swapped'],
                },
              },
            },
            secondary: { label: 'Secondary text (instead of wording)', selector: { text: {} } },
            icon: { label: 'Icon override', selector: { icon: {} } },
            value: { label: 'Fixed value (instead of an entity)', selector: { number: { mode: 'box' } } },
            date: { label: 'Replaced/charged date (fixed-value rows only)', selector: { date: {} } },
            unit: { label: 'Unit override', selector: { text: {} } },
            max: { label: 'Max override', selector: { number: { mode: 'box', min: 0 } } },
          },
        },
      },
    },
  ],
  labels: {
    title: 'Title',
    direction: 'Which end is bad',
    alert_at: 'Red at',
    warn_at: 'Orange at',
    unit: 'Unit',
    max: 'Full bar value',
    icon_mode: 'Icons',
    icon: 'Icon for every row',
    entities: 'Rows',
  },
  helpers: {
    alert_at: 'Defaults: 20 (low is bad) / 90 (high is bad)',
    warn_at: 'Defaults: 50 (low is bad) / 75 (high is bad)',
    unit: 'Default %',
    max: 'Default 100',
  },
});

// 2026-01-01 -> "01 Jan 2026"; anything that isn't a date is shown as-is.
function lczFormatDate(raw) {
  const d = /^\d{4}-\d{2}-\d{2}/.test(raw) ? new Date(raw) : null;
  return d && !isNaN(d) ? d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' }) : raw;
}

export class GaugeZoneCard extends HTMLElement {
  setConfig(config) {
    if (!config.entities) throw new Error('entities required');
    this.config = config;
    this._built = false;
  }

  _batteryIcon(pct) {
    if (pct <= 5) return 'mdi:battery-alert';
    if (pct >= 95) return 'mdi:battery';
    const r = Math.round(pct / 10) * 10;
    return `mdi:battery-${r}`;
  }

  set hass(hass) {
    this._hass = hass;
    const cfg = this.config;
    const iconMode = cfg.icon_mode || (this.tagName.toLowerCase() === 'battery-zone-card' ? 'battery' : 'gauge');
    const direction = cfg.direction || 'low'; // 'low' = low value is bad (battery); 'high' = high value is bad (storage/cpu)
    const alertAt = cfg.alert_at !== undefined ? cfg.alert_at : (direction === 'low' ? 20 : 90);
    const warnAt = cfg.warn_at !== undefined ? cfg.warn_at : (direction === 'low' ? 50 : 75);
    const cardUnit = cfg.unit !== undefined ? cfg.unit : '%';
    const cardMax = cfg.max || 100;

    if (!this._built) {
      this.innerHTML = `
        <ha-card style="border:none; box-shadow: 0 3px 10px rgba(0,0,0,0.45); border-radius:16px; overflow:hidden; background: var(--card-background-color);">
          <div class="bzc-title" style="padding:16px 16px 8px 16px; font-size:1.5rem; font-weight:500; color: var(--primary-text-color);">${cfg.title || ''}</div>
          <div class="bzc-rows" style="padding:0; margin:0;"></div>
        </ha-card>`;
      this._rows = this.querySelector('.bzc-rows');
      this._built = true;
    }
    this._rows.innerHTML = '';

    // Each entity entry is EITHER a real entity ({entity, name, ...}) OR a literal value
    // ({value, name, ...} / legacy {demo_pct, name, ...}) that skips hass lookup entirely.
    const entries = cfg.entities.map((e) => {
      const literal = e.value !== undefined ? e.value : e.demo_pct;
      if (literal !== undefined) {
        const available = literal >= 0;
        return { ...e, val: available ? literal : -1, available, demo: true };
      }
      const st = hass.states[e.entity];
      const raw = st ? parseFloat(st.state) : NaN;
      const available = st && !['unknown', 'unavailable'].includes(st.state) && !isNaN(raw);
      return { ...e, st, val: available ? raw : -1, available };
    });
    entries.sort((a, b) => (direction === 'low' ? a.val - b.val : b.val - a.val));

    entries.forEach((e, i) => {
      const val = e.available ? e.val : 0;
      const max = e.max || cardMax;
      const widthPct = Math.min(Math.max((val / max) * 100, 0), 100);
      const unit = e.unit !== undefined ? e.unit : cardUnit;

      let colorState; // 'red' | 'orange' | 'green'
      if (direction === 'low') {
        colorState = val <= alertAt ? 'red' : val <= warnAt ? 'orange' : 'green';
      } else {
        colorState = val >= alertAt ? 'red' : val >= warnAt ? 'orange' : 'green';
      }
      const color = { red: 'var(--error-color, #db4437)', orange: 'var(--warning-color, #ff9800)', green: 'var(--success-color, #43a047)' }[colorState];
      const unavailableColor = '#9e9e9e';

      let iconColor;
      if (!e.available) {
        iconColor = unavailableColor;
      } else if (colorState === 'red' && (direction === 'low' ? val <= 0 : val >= max)) {
        iconColor = color;
      } else {
        iconColor = '#ffffff';
      }

      let dateStr = null;
      if (e.demo) {
        // `date` (from the editor's date picker, YYYY-MM-DD) or the older
        // free-text `demo_date` YAML field.
        const raw = e.date || e.demo_date;
        dateStr = raw ? lczFormatDate(raw) : 'unknown';
      } else {
        const replaced = e.st && e.st.attributes ? e.st.attributes.battery_last_replaced : null;
        if (replaced) {
          const d = new Date(replaced);
          dateStr = d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
        } else if (e.word) {
          dateStr = 'unknown';
        }
      }
      let secondaryText = e.secondary;
      if (secondaryText === undefined) {
        secondaryText = e.word ? `${e.word} ${dateStr}` : '';
      }

      // A row's own `icon` always wins; otherwise the card's icon mode decides.
      // 'entity' uses the icon HA shows for that entity: the registry icon, a
      // state-attribute icon, or (via <ha-state-icon> below) its device-class
      // default, e.g. a thermometer for temperature.
      let icon;
      let useStateIcon = false;
      if (e.icon) {
        icon = e.icon;
      } else if (iconMode === 'battery') {
        icon = this._batteryIcon(e.available ? e.val : 0);
      } else if (iconMode === 'custom' && cfg.icon) {
        icon = cfg.icon;
      } else if (iconMode === 'entity' && e.st) {
        const entry = hass.entities && hass.entities[e.entity];
        icon = (entry && entry.icon) || e.st.attributes.icon;
        useStateIcon = !icon && !!customElements.get('ha-state-icon');
        icon = icon || 'mdi:gauge';
      } else {
        icon = 'mdi:gauge';
      }

      const isFirst = i === 0;
      const isLast = i === entries.length - 1;

      let mask = null;
      if (!isFirst && !isLast) {
        mask = 'linear-gradient(to bottom, transparent 0%, black 2%, black 98%, transparent 100%)';
      } else if (!isFirst && isLast) {
        mask = 'linear-gradient(to bottom, transparent 0%, black 2%, black 100%)';
      } else if (isFirst && !isLast) {
        mask = 'linear-gradient(to bottom, black 0%, black 98%, transparent 100%)';
      }

      const radius = `${isFirst ? '16px 16px' : '0 0'} ${isLast ? '16px 16px' : '0 0'}`;
      const maskCss = mask ? `-webkit-mask-image:${mask}; mask-image:${mask};` : '';

      const row = document.createElement('div');
      row.style.cssText = `display:flex; align-items:center; box-sizing:border-box; width:100%; padding:10px 16px; margin:${isFirst ? '0' : '4px'} 0 0 0; border:none; border-radius:${radius}; ${maskCss} background: linear-gradient(to right, ${color} 0%, transparent ${widthPct}%);`;
      row.innerHTML = `
        <ha-icon icon="${icon}" style="color:${iconColor}; margin-right:14px; flex-shrink:0; --mdc-icon-size:26px;"></ha-icon>
        <div style="flex:1; min-width:0;">
          <div style="font-weight:500; color:#ffffff; overflow:hidden; text-overflow:ellipsis; white-space:nowrap;">${e.name || (e.st && e.st.attributes.friendly_name) || e.entity || ''}</div>
          ${secondaryText ? `<div style="font-size:0.85rem; color:rgba(255,255,255,0.65);">${secondaryText}</div>` : ''}
        </div>
        <div style="font-weight:600; color:#ffffff; margin-left:8px; flex-shrink:0;">${e.available ? Math.round(e.val) + unit : 'n/a'}</div>
      `;
      if (useStateIcon) {
        const placeholder = row.querySelector('ha-icon');
        const stateIcon = document.createElement('ha-state-icon');
        stateIcon.hass = hass;
        stateIcon.stateObj = e.st;
        stateIcon.style.cssText = placeholder.style.cssText;
        placeholder.replaceWith(stateIcon);
      }
      this._rows.appendChild(row);
    });
  }

  // Rows with a secondary line are ~60px, so count them as 1.2 units.
  getCardSize() {
    const rows = this.config.entities || [];
    const tall = rows.filter((e) => e.secondary || e.word).length;
    return 1 + Math.ceil(rows.length + tall * 0.2);
  }

  // Sections-view defaults; the editor's Layout tab can override them.
  getGridOptions() {
    return { columns: 12, min_columns: 6, rows: 'auto' };
  }

  static getConfigElement() {
    return document.createElement('gauge-zone-card-editor');
  }

  // What a new card starts with in the card picker (and its preview).
  // battery-zone-card: up to three real battery sensors, lowest first.
  // gauge-zone-card: a single fixed example row to edit.
  static getStubConfig(hass) {
    if (this === GaugeZoneCard) {
      const batteries = Object.values((hass && hass.states) || {})
        .filter((st) => st.attributes.device_class === 'battery' && st.attributes.unit_of_measurement === '%' && !isNaN(parseFloat(st.state)))
        .sort((a, b) => parseFloat(a.state) - parseFloat(b.state))
        .slice(0, 3)
        .map((st) => ({ entity: st.entity_id, name: st.attributes.friendly_name || st.entity_id }));
      return { title: 'Batteries', entities: batteries };
    }
    return { title: 'Gauge', direction: 'high', entities: [{ name: 'Example', value: 42 }] };
  }
}

export function registerGaugeZoneCard() {
  if (!customElements.get('gauge-zone-card-editor')) {
    customElements.define('gauge-zone-card-editor', GaugeZoneCardEditor);
  }
  if (!customElements.get('battery-zone-card')) {
    customElements.define('battery-zone-card', GaugeZoneCard);
  }
  if (!customElements.get('gauge-zone-card')) {
    customElements.define('gauge-zone-card', class extends GaugeZoneCard {});
  }
  window.customCards = window.customCards || [];
  window.customCards.push({
    type: 'battery-zone-card',
    name: 'Battery Zone Card',
    description: 'Zone battery status with gradient rows',
    preview: true,
    documentationURL: 'https://github.com/J45PER/church-drive-cards#readme',
  });
  window.customCards.push({
    type: 'gauge-zone-card',
    name: 'Gauge Zone Card',
    description: 'Generic % / value gauge rows with gradient fill — storage, signal, humidity, CPU, anything measurable',
    preview: true,
    documentationURL: 'https://github.com/J45PER/church-drive-cards#readme',
  });
}
