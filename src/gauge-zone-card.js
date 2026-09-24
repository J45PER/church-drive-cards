// Gauge Zone Card — generic %/value gauge rows with a gradient fill.
// battery-zone-card is this same class registered a second time with
// battery-friendly defaults (auto battery icon stepping, low-is-bad colouring).

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
        dateStr = e.demo_date || 'unknown';
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

      let icon;
      if (e.icon) {
        icon = e.icon;
      } else if (iconMode === 'battery') {
        icon = this._batteryIcon(e.available ? e.val : 0);
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
          <div style="font-weight:500; color:#ffffff; overflow:hidden; text-overflow:ellipsis; white-space:nowrap;">${e.name}</div>
          ${secondaryText ? `<div style="font-size:0.85rem; color:rgba(255,255,255,0.65);">${secondaryText}</div>` : ''}
        </div>
        <div style="font-weight:600; color:#ffffff; margin-left:8px; flex-shrink:0;">${e.available ? Math.round(e.val) + unit : 'n/a'}</div>
      `;
      this._rows.appendChild(row);
    });
  }

  getCardSize() {
    return (this.config.entities ? this.config.entities.length : 1) + 1;
  }

  static getStubConfig() {
    return { title: 'Zone', entities: [] };
  }
}

export function registerGaugeZoneCard() {
  if (!customElements.get('battery-zone-card')) {
    customElements.define('battery-zone-card', GaugeZoneCard);
  }
  if (!customElements.get('gauge-zone-card')) {
    customElements.define('gauge-zone-card', class extends GaugeZoneCard {});
  }
  window.customCards = window.customCards || [];
  window.customCards.push({ type: 'battery-zone-card', name: 'Battery Zone Card', description: 'Zone battery status with gradient rows' });
  window.customCards.push({ type: 'gauge-zone-card', name: 'Gauge Zone Card', description: 'Generic % / value gauge rows with gradient fill — storage, signal, humidity, CPU, anything measurable' });
}
