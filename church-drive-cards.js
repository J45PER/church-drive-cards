(() => {
  // src/gauge-zone-card.js
  var GaugeZoneCard = class extends HTMLElement {
    setConfig(config) {
      if (!config.entities) throw new Error("entities required");
      this.config = config;
      this._built = false;
    }
    _batteryIcon(pct) {
      if (pct <= 5) return "mdi:battery-alert";
      if (pct >= 95) return "mdi:battery";
      const r = Math.round(pct / 10) * 10;
      return `mdi:battery-${r}`;
    }
    set hass(hass) {
      this._hass = hass;
      const cfg = this.config;
      const iconMode = cfg.icon_mode || (this.tagName.toLowerCase() === "battery-zone-card" ? "battery" : "gauge");
      const direction = cfg.direction || "low";
      const alertAt = cfg.alert_at !== void 0 ? cfg.alert_at : direction === "low" ? 20 : 90;
      const warnAt = cfg.warn_at !== void 0 ? cfg.warn_at : direction === "low" ? 50 : 75;
      const cardUnit = cfg.unit !== void 0 ? cfg.unit : "%";
      const cardMax = cfg.max || 100;
      if (!this._built) {
        this.innerHTML = `
        <ha-card style="border:none; box-shadow: 0 3px 10px rgba(0,0,0,0.45); border-radius:16px; overflow:hidden; background: var(--card-background-color);">
          <div class="bzc-title" style="padding:16px 16px 8px 16px; font-size:1.5rem; font-weight:500; color: var(--primary-text-color);">${cfg.title || ""}</div>
          <div class="bzc-rows" style="padding:0; margin:0;"></div>
        </ha-card>`;
        this._rows = this.querySelector(".bzc-rows");
        this._built = true;
      }
      this._rows.innerHTML = "";
      const entries = cfg.entities.map((e) => {
        const literal = e.value !== void 0 ? e.value : e.demo_pct;
        if (literal !== void 0) {
          const available2 = literal >= 0;
          return { ...e, val: available2 ? literal : -1, available: available2, demo: true };
        }
        const st = hass.states[e.entity];
        const raw = st ? parseFloat(st.state) : NaN;
        const available = st && !["unknown", "unavailable"].includes(st.state) && !isNaN(raw);
        return { ...e, st, val: available ? raw : -1, available };
      });
      entries.sort((a, b) => direction === "low" ? a.val - b.val : b.val - a.val);
      entries.forEach((e, i) => {
        const val = e.available ? e.val : 0;
        const max = e.max || cardMax;
        const widthPct = Math.min(Math.max(val / max * 100, 0), 100);
        const unit = e.unit !== void 0 ? e.unit : cardUnit;
        let colorState;
        if (direction === "low") {
          colorState = val <= alertAt ? "red" : val <= warnAt ? "orange" : "green";
        } else {
          colorState = val >= alertAt ? "red" : val >= warnAt ? "orange" : "green";
        }
        const color = { red: "var(--error-color, #db4437)", orange: "var(--warning-color, #ff9800)", green: "var(--success-color, #43a047)" }[colorState];
        const unavailableColor = "#9e9e9e";
        let iconColor;
        if (!e.available) {
          iconColor = unavailableColor;
        } else if (colorState === "red" && (direction === "low" ? val <= 0 : val >= max)) {
          iconColor = color;
        } else {
          iconColor = "#ffffff";
        }
        let dateStr = null;
        if (e.demo) {
          dateStr = e.demo_date || "unknown";
        } else {
          const replaced = e.st && e.st.attributes ? e.st.attributes.battery_last_replaced : null;
          if (replaced) {
            const d = new Date(replaced);
            dateStr = d.toLocaleDateString("en-GB", { day: "2-digit", month: "short", year: "numeric" });
          } else if (e.word) {
            dateStr = "unknown";
          }
        }
        let secondaryText = e.secondary;
        if (secondaryText === void 0) {
          secondaryText = e.word ? `${e.word} ${dateStr}` : "";
        }
        let icon;
        if (e.icon) {
          icon = e.icon;
        } else if (iconMode === "battery") {
          icon = this._batteryIcon(e.available ? e.val : 0);
        } else {
          icon = "mdi:gauge";
        }
        const isFirst = i === 0;
        const isLast = i === entries.length - 1;
        let mask = null;
        if (!isFirst && !isLast) {
          mask = "linear-gradient(to bottom, transparent 0%, black 2%, black 98%, transparent 100%)";
        } else if (!isFirst && isLast) {
          mask = "linear-gradient(to bottom, transparent 0%, black 2%, black 100%)";
        } else if (isFirst && !isLast) {
          mask = "linear-gradient(to bottom, black 0%, black 98%, transparent 100%)";
        }
        const radius = `${isFirst ? "16px 16px" : "0 0"} ${isLast ? "16px 16px" : "0 0"}`;
        const maskCss = mask ? `-webkit-mask-image:${mask}; mask-image:${mask};` : "";
        const row = document.createElement("div");
        row.style.cssText = `display:flex; align-items:center; box-sizing:border-box; width:100%; padding:10px 16px; margin:${isFirst ? "0" : "4px"} 0 0 0; border:none; border-radius:${radius}; ${maskCss} background: linear-gradient(to right, ${color} 0%, transparent ${widthPct}%);`;
        row.innerHTML = `
        <ha-icon icon="${icon}" style="color:${iconColor}; margin-right:14px; flex-shrink:0; --mdc-icon-size:26px;"></ha-icon>
        <div style="flex:1; min-width:0;">
          <div style="font-weight:500; color:#ffffff; overflow:hidden; text-overflow:ellipsis; white-space:nowrap;">${e.name}</div>
          ${secondaryText ? `<div style="font-size:0.85rem; color:rgba(255,255,255,0.65);">${secondaryText}</div>` : ""}
        </div>
        <div style="font-weight:600; color:#ffffff; margin-left:8px; flex-shrink:0;">${e.available ? Math.round(e.val) + unit : "n/a"}</div>
      `;
        this._rows.appendChild(row);
      });
    }
    getCardSize() {
      return (this.config.entities ? this.config.entities.length : 1) + 1;
    }
    static getStubConfig() {
      return { title: "Zone", entities: [] };
    }
  };
  function registerGaugeZoneCard() {
    if (!customElements.get("battery-zone-card")) {
      customElements.define("battery-zone-card", GaugeZoneCard);
    }
    if (!customElements.get("gauge-zone-card")) {
      customElements.define("gauge-zone-card", class extends GaugeZoneCard {
      });
    }
    window.customCards = window.customCards || [];
    window.customCards.push({ type: "battery-zone-card", name: "Battery Zone Card", description: "Zone battery status with gradient rows" });
    window.customCards.push({ type: "gauge-zone-card", name: "Gauge Zone Card", description: "Generic % / value gauge rows with gradient fill \u2014 storage, signal, humidity, CPU, anything measurable" });
  }

  // src/alarm-panel-card.js
  var AlarmPanelCard = class extends HTMLElement {
    setConfig(config) {
      if (!config.entity && !config.demo) throw new Error("entity required (or set demo: true)");
      this.config = config;
      this._built = false;
      this._countdownTimer = null;
      this._remaining = 0;
    }
    disconnectedCallback() {
      if (this._countdownTimer) clearInterval(this._countdownTimer);
    }
    set hass(hass) {
      this._hass = hass;
      let st;
      if (this.config.demo) {
        st = {
          state: this.config.demo_state || "armed_away",
          attributes: {
            supported_features: this.config.demo_supported_features !== void 0 ? this.config.demo_supported_features : 3,
            targetState: this.config.demo_target_state || "armed_away",
            lastArmedBy: this.config.demo_by || "Demo User",
            lastArmedTime: this.config.demo_time || (/* @__PURE__ */ new Date()).toISOString(),
            lastDisarmedBy: this.config.demo_by || "Demo User",
            lastDisarmedTime: this.config.demo_time || (/* @__PURE__ */ new Date()).toISOString(),
            entrySecondsLeft: this.config.demo_countdown || 0,
            exitSecondsLeft: this.config.demo_countdown || 0
          }
        };
      } else {
        st = hass.states[this.config.entity];
      }
      if (!st) return;
      if (!this._built) {
        this.innerHTML = `
        <ha-card style="position:relative; border:none; box-shadow:0 3px 10px rgba(0,0,0,0.45); border-radius:16px; overflow:hidden; padding:20px 20px 18px 20px;">
          <style>
            ha-card::before {
              content:''; position:absolute; inset:0; background: var(--card-tint, transparent);
              opacity:0; transition: opacity .2s ease; pointer-events:none;
            }
            ha-card:hover::before { opacity:0.16; }
            .apc-btn { position:relative; overflow:hidden; }
            .apc-btn::after {
              content:''; position:absolute; inset:0; background: var(--btn-tint, #fff);
              opacity:0; transition: opacity .15s ease; pointer-events:none; border-radius:inherit;
            }
            .apc-btn:hover::after { opacity:0.18; }
          </style>
          <div class="apc-header" style="position:relative; display:flex; align-items:center; gap:16px;">
            <ha-icon class="apc-icon" style="--mdc-icon-size:48px; flex-shrink:0;"></ha-icon>
            <div style="flex:1; min-width:0;">
              <div class="apc-title" style="font-size:1.5rem; font-weight:600;"></div>
              <div class="apc-sub" style="font-size:0.9rem; color:var(--secondary-text-color);"></div>
            </div>
          </div>
          <div class="apc-countdown" style="position:relative; display:none; text-align:center; font-size:2.75rem; font-weight:700; margin:14px 0 4px 0;"></div>
          <div class="apc-countdown-label" style="position:relative; display:none; text-align:center; font-size:0.85rem; color:var(--secondary-text-color); margin-bottom:8px;"></div>
          <div class="apc-buttons" style="position:relative; display:flex; gap:10px; margin-top:14px;"></div>
        </ha-card>`;
        this._card = this.querySelector("ha-card");
        this._icon = this.querySelector(".apc-icon");
        this._title = this.querySelector(".apc-title");
        this._sub = this.querySelector(".apc-sub");
        this._countdown = this.querySelector(".apc-countdown");
        this._countdownLabel = this.querySelector(".apc-countdown-label");
        this._buttons = this.querySelector(".apc-buttons");
        this._built = true;
      }
      const stateInfo = {
        disarmed: { label: "Disarmed", icon: "mdi:shield-off-outline", color: "var(--success-color, #43a047)" },
        armed_home: { label: "Armed Home", icon: "mdi:shield-home", color: "#2196f3" },
        armed_away: { label: "Armed Away", icon: "mdi:shield-lock", color: "var(--error-color, #db4437)" },
        armed_night: { label: "Armed Night", icon: "mdi:shield-moon", color: "#7e57c2" },
        arming: { label: "Arming", icon: "mdi:shield-sync", color: "#ff9800" },
        pending: { label: "Entry Delay", icon: "mdi:shield-sync", color: "#ff5722" },
        triggered: { label: "Triggered!", icon: "mdi:shield-alert", color: "var(--error-color, #db4437)" }
      };
      const info = stateInfo[st.state] || { label: st.state, icon: "mdi:shield-question", color: "#9e9e9e" };
      this._card.style.setProperty("--card-tint", info.color);
      this._icon.setAttribute("icon", info.icon);
      this._icon.style.color = info.color;
      this._title.textContent = info.label + (this.config.demo ? " (demo)" : "");
      this._title.style.color = info.color;
      let sub = "";
      const fmt = (iso) => new Date(iso).toLocaleString("en-GB", { day: "2-digit", month: "short", hour: "2-digit", minute: "2-digit" });
      if (st.state === "disarmed" && st.attributes.lastDisarmedBy && st.attributes.lastDisarmedTime) {
        sub = `Disarmed by ${st.attributes.lastDisarmedBy}, ${fmt(st.attributes.lastDisarmedTime)}`;
      } else if (st.attributes.lastArmedBy && st.attributes.lastArmedTime) {
        sub = `Armed by ${st.attributes.lastArmedBy}, ${fmt(st.attributes.lastArmedTime)}`;
      }
      this._sub.textContent = sub;
      const entrySecs = st.attributes.entrySecondsLeft || 0;
      const exitSecs = st.attributes.exitSecondsLeft || 0;
      const secsLeft = st.state === "pending" ? entrySecs : st.state === "arming" ? exitSecs : 0;
      const label = st.state === "pending" ? "until alarm triggers" : st.state === "arming" ? "until armed" : "";
      this._syncCountdown(secsLeft, st.state, label, info.color);
      const activeKey = st.state === "arming" || st.state === "pending" ? st.attributes.targetState : st.state;
      const activeColor = info.color;
      this._buttons.innerHTML = "";
      const feats = st.attributes.supported_features || 0;
      const actions = [
        { key: "disarmed", icon: "mdi:shield-off-outline", title: "Disarm", service: "alarm_disarm", show: true, color: stateInfo.disarmed.color },
        { key: "armed_home", icon: "mdi:shield-home", title: "Arm Home", service: "alarm_arm_home", show: (feats & 1) !== 0, color: stateInfo.armed_home.color },
        { key: "armed_away", icon: "mdi:shield-lock", title: "Arm Away", service: "alarm_arm_away", show: (feats & 2) !== 0, color: stateInfo.armed_away.color },
        { key: "armed_night", icon: "mdi:shield-moon", title: "Arm Night", service: "alarm_arm_night", show: (feats & 4) !== 0, color: stateInfo.armed_night.color }
      ];
      actions.filter((a) => a.show).forEach((a) => {
        const active = activeKey === a.key;
        const btn = document.createElement("button");
        btn.className = "apc-btn";
        btn.title = a.title;
        btn.setAttribute("aria-label", a.title);
        btn.style.cssText = `--btn-tint:${a.color}; flex:1; display:flex; align-items:center; justify-content:center; padding:12px 8px; border-radius:10px; border:none; cursor:pointer; background:${active ? activeColor : "rgba(255,255,255,0.08)"};`;
        const ic = document.createElement("ha-icon");
        ic.setAttribute("icon", a.icon);
        ic.style.cssText = `color:${active ? "#fff" : "var(--primary-text-color)"}; --mdc-icon-size:24px; position:relative; z-index:1;`;
        btn.appendChild(ic);
        if (!this.config.demo) {
          btn.addEventListener("click", () => {
            this._hass.callService("alarm_control_panel", a.service, {}, { entity_id: this.config.entity });
          });
        } else {
          btn.style.opacity = "0.6";
          btn.style.cursor = "default";
        }
        this._buttons.appendChild(btn);
      });
    }
    _syncCountdown(secsLeft, state, label, color) {
      const active = (state === "pending" || state === "arming") && secsLeft > 0;
      if (!active) {
        this._countdown.style.display = "none";
        this._countdownLabel.style.display = "none";
        if (this._countdownTimer) {
          clearInterval(this._countdownTimer);
          this._countdownTimer = null;
        }
        return;
      }
      this._countdown.style.display = "block";
      this._countdown.style.color = color;
      this._countdownLabel.style.display = "block";
      this._countdownLabel.textContent = label;
      this._remaining = secsLeft;
      this._renderCountdown();
      if (this._countdownTimer) clearInterval(this._countdownTimer);
      if (!this.config.demo) {
        this._countdownTimer = setInterval(() => {
          this._remaining = Math.max(0, this._remaining - 1);
          this._renderCountdown();
          if (this._remaining <= 0) {
            clearInterval(this._countdownTimer);
            this._countdownTimer = null;
          }
        }, 1e3);
      }
    }
    _renderCountdown() {
      const m = Math.floor(this._remaining / 60);
      const s = this._remaining % 60;
      this._countdown.textContent = `${m}:${s.toString().padStart(2, "0")}`;
    }
    getCardSize() {
      return 4;
    }
    static getStubConfig() {
      return { entity: "alarm_control_panel.alarm" };
    }
  };
  function registerAlarmPanelCard() {
    if (!customElements.get("alarm-panel-card")) {
      customElements.define("alarm-panel-card", AlarmPanelCard);
    }
    window.customCards = window.customCards || [];
    window.customCards.push({ type: "alarm-panel-card", name: "Alarm Panel Card", description: "Alarm status, entry/exit countdown, and arm/disarm controls" });
  }

  // src/light-control-card.js
  function lccHsToRgb(h, s) {
    const c = s / 100;
    const x = c * (1 - Math.abs(h / 60 % 2 - 1));
    let r = 0, g = 0, b = 0;
    if (h < 60) {
      r = c;
      g = x;
    } else if (h < 120) {
      r = x;
      g = c;
    } else if (h < 180) {
      g = c;
      b = x;
    } else if (h < 240) {
      g = x;
      b = c;
    } else if (h < 300) {
      r = x;
      b = c;
    } else {
      r = c;
      b = x;
    }
    const m = 1 - c;
    return `rgb(${Math.round((r + m) * 255)},${Math.round((g + m) * 255)},${Math.round((b + m) * 255)})`;
  }
  function lccKelvinColor(k) {
    if (k <= 3e3) return "#ffb37d";
    if (k <= 4500) return "#ffe9c7";
    return "#cfe8ff";
  }
  function lccLightColor(st) {
    if (!st || st.state !== "on") return "#ffc107";
    const a = st.attributes || {};
    if (a.hs_color) return lccHsToRgb(a.hs_color[0], a.hs_color[1]);
    if (a.rgb_color) return `rgb(${a.rgb_color[0]},${a.rgb_color[1]},${a.rgb_color[2]})`;
    if (a.color_temp_kelvin) return lccKelvinColor(a.color_temp_kelvin);
    return "#ffc107";
  }
  function lccLightIcon(st, isGroupLike) {
    const on = st && st.state === "on";
    if (st && st.attributes && st.attributes.icon) return st.attributes.icon;
    if (isGroupLike) return on ? "mdi:lightbulb-group" : "mdi:lightbulb-group-outline";
    return on ? "mdi:lightbulb" : "mdi:lightbulb-outline";
  }
  function lccMoreInfo(el, entityId) {
    el.dispatchEvent(new CustomEvent("hass-more-info", { detail: { entityId }, bubbles: true, composed: true }));
  }
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
  function lccMembersOf(hass, entityId) {
    const st = hass.states[entityId];
    if (!lccIsGroupLike(st)) return [];
    return st.attributes.entity_id.filter((id) => id.startsWith("light.") && hass.states[id]);
  }
  function lccSceneChips(hass, entityIds) {
    const areas = new Set(
      entityIds.map((id) => hass.entities && hass.entities[id] && lccAreaOf(hass, hass.entities[id])).filter(Boolean)
    );
    if (areas.size === 0) return [];
    return Object.values(hass.entities || {}).filter(
      (e) => e.entity_id.startsWith("scene.") && areas.has(lccAreaOf(hass, e))
    );
  }
  var LightControlCardEditor = class extends HTMLElement {
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
        this._form = document.createElement("ha-form");
        this._form.addEventListener("value-changed", (ev) => {
          this._config = ev.detail.value;
          this.dispatchEvent(new CustomEvent("config-changed", { detail: { config: this._config }, bubbles: true, composed: true }));
          this._render();
        });
        this.appendChild(this._form);
      }
      const mode = this._config.mode || "light";
      const schema = [
        {
          name: "mode",
          selector: {
            select: {
              mode: "dropdown",
              options: [
                { value: "light", label: "Single Light" },
                { value: "group", label: "Light Group" },
                { value: "room", label: "Room" }
              ]
            }
          }
        }
      ];
      if (mode === "room") {
        schema.push({ name: "area", selector: { area: {} } });
      } else {
        schema.push({ name: "entity", selector: { entity: { domain: "light" } } });
      }
      schema.push({ name: "name", selector: { text: {} } });
      schema.push({ name: "scenes", selector: { entity: { domain: "scene", multiple: true } } });
      this._form.hass = this._hass;
      this._form.data = this._config;
      this._form.schema = schema;
      this._form.computeLabel = (s) => ({
        mode: "Card type",
        area: "Room",
        entity: "Light entity",
        name: "Title (optional)",
        scenes: "Scenes (optional \u2014 auto-detected by area if left blank)"
      })[s.name] || s.name;
    }
  };
  var LightControlCard = class extends HTMLElement {
    setConfig(config) {
      if (!config.entity && !config.area) throw new Error("entity or area required");
      this.config = config;
      this._built = false;
      this._lastIds = null;
    }
    static getConfigElement() {
      return document.createElement("light-control-card-editor");
    }
    static getStubConfig() {
      return { mode: "light", entity: "" };
    }
    _toggle(entityId) {
      this._hass.callService("light", "toggle", {}, { entity_id: entityId });
    }
    _setBrightnessPct(entityId, pct) {
      if (pct <= 2) {
        this._hass.callService("light", "turn_off", {}, { entity_id: entityId });
      } else {
        this._hass.callService("light", "turn_on", { brightness: Math.round(pct / 100 * 255) }, { entity_id: entityId });
      }
    }
    _activateScene(entityId) {
      this._hass.callService("scene", "turn_on", {}, { entity_id: entityId });
    }
    // A single full-width row that IS the control: background is a low-opacity
    // TINT of the light's colour blended into the card background (never a
    // literal paint of the colour — a white/bright light would otherwise wash
    // the row to solid white and make the text unreadable), sized to the
    // brightness. Tap toggles; drag horizontally sets brightness on dimmable
    // lights.
    _buildRow(entityId, { withMoreInfo, member = false }) {
      const st = this._hass.states[entityId];
      const name = st && st.attributes.friendly_name || entityId;
      const isGroupLike = lccIsGroupLike(st);
      const on = st && st.state === "on";
      const dimmable = st && st.attributes.supported_color_modes && st.attributes.supported_color_modes.some((m) => m !== "onoff");
      const color = lccLightColor(st);
      const icon = lccLightIcon(st, isGroupLike);
      const brightnessPct = st && st.attributes.brightness ? Math.round(st.attributes.brightness / 255 * 100) : 0;
      const fillPct = on ? dimmable ? Math.max(brightnessPct, 4) : 100 : 0;
      const tint = `color-mix(in srgb, ${color} 30%, var(--card-background-color, #1c1c1c))`;
      const track = "rgba(255,255,255,0.06)";
      const row = document.createElement("div");
      row.className = "lcc-row";
      const pad = member ? "9px 14px 9px 14px" : "12px 14px";
      const indent = member ? "margin-left:16px;" : "";
      row.style.cssText = `position:relative; display:flex; align-items:center; gap:12px; padding:${pad}; ${indent} border-radius:12px; margin-top:6px; overflow:hidden; cursor:pointer; user-select:none; touch-action:pan-y; background: linear-gradient(to right, ${tint} 0%, ${tint} ${fillPct}%, ${track} ${fillPct}%, ${track} 100%);`;
      row.innerHTML = `
      <ha-icon icon="${icon}" style="color:${on ? color : "var(--secondary-text-color)"}; --mdc-icon-size:24px; flex-shrink:0; pointer-events:none;"></ha-icon>
      <div class="lcc-name" style="flex:1; min-width:0; font-weight:500; color:var(--primary-text-color); overflow:hidden; text-overflow:ellipsis; white-space:nowrap; pointer-events:none;">${name}</div>
      ${withMoreInfo ? `<ha-icon class="lcc-more" icon="mdi:tune-variant" style="color:var(--secondary-text-color); --mdc-icon-size:20px; cursor:pointer; flex-shrink:0;"></ha-icon>` : ""}
    `;
      if (withMoreInfo) {
        const moreBtn = row.querySelector(".lcc-more");
        moreBtn.addEventListener("click", (ev) => {
          ev.stopPropagation();
          lccMoreInfo(this, entityId);
        });
        moreBtn.addEventListener("pointerdown", (ev) => ev.stopPropagation());
      }
      let dragging = false;
      let moved = false;
      let startX = 0;
      const setFillVisual = (pct) => {
        row.style.background = `linear-gradient(to right, ${tint} 0%, ${tint} ${pct}%, ${track} ${pct}%, ${track} 100%)`;
      };
      const pctFromEvent = (ev) => {
        const rect = row.getBoundingClientRect();
        return Math.min(100, Math.max(0, (ev.clientX - rect.left) / rect.width * 100));
      };
      const endInteraction = () => {
        dragging = false;
        moved = false;
        this._interacting = false;
        if (this._pendingHass) {
          const h = this._pendingHass;
          this._pendingHass = null;
          this.hass = h;
        }
      };
      row.addEventListener("pointerdown", (ev) => {
        if (!dimmable) return;
        dragging = true;
        moved = false;
        startX = ev.clientX;
        this._interacting = true;
        row.setPointerCapture(ev.pointerId);
      });
      row.addEventListener("pointermove", (ev) => {
        if (!dragging) return;
        if (Math.abs(ev.clientX - startX) > 4) moved = true;
        if (moved) setFillVisual(pctFromEvent(ev));
      });
      row.addEventListener("pointerup", (ev) => {
        if (dimmable && dragging && moved) {
          this._setBrightnessPct(entityId, pctFromEvent(ev));
        } else {
          this._toggle(entityId);
        }
        endInteraction();
      });
      row.addEventListener("pointercancel", () => {
        setFillVisual(fillPct);
        endInteraction();
      });
      return row;
    }
    _buildScenes(sceneEntities) {
      if (!sceneEntities.length) return null;
      const wrap = document.createElement("div");
      wrap.style.cssText = "display:flex; gap:8px; flex-wrap:wrap; margin-top:10px;";
      sceneEntities.forEach((s) => {
        const chip = document.createElement("button");
        const name = this._hass.states[s.entity_id] && this._hass.states[s.entity_id].attributes.friendly_name || s.name || s.entity_id;
        chip.textContent = name.replace(/^living\s?room\s*/i, "");
        chip.style.cssText = "padding:8px 14px; border-radius:20px; border:none; background:rgba(255,255,255,0.08); color:var(--primary-text-color); font-size:0.85rem; cursor:pointer;";
        chip.addEventListener("click", () => this._activateScene(s.entity_id));
        wrap.appendChild(chip);
      });
      return wrap;
    }
    set hass(hass) {
      this._hass = hass;
      const cfg = this.config;
      const mode = cfg.mode || (cfg.area ? "room" : "light");
      if (!this._built) {
        this.innerHTML = `
        <ha-card style="border:none; box-shadow: 0 3px 10px rgba(0,0,0,0.45); border-radius:16px; overflow:hidden; background: var(--card-background-color); padding:16px 16px 14px 16px;">
          <div class="lcc-title" style="display:none; padding:0 0 10px 0; font-size:1.5rem; font-weight:500; color: var(--primary-text-color);"></div>
          <div class="lcc-main"></div>
          <div class="lcc-members"></div>
          <div class="lcc-scenes"></div>
        </ha-card>`;
        this._titleEl = this.querySelector(".lcc-title");
        this._main = this.querySelector(".lcc-main");
        this._members = this.querySelector(".lcc-members");
        this._scenesEl = this.querySelector(".lcc-scenes");
        this._built = true;
      }
      let headIds = [];
      let memberIds = [];
      if (mode === "room") {
        const areaLights = Object.values(hass.entities || {}).filter(
          (e) => e.entity_id.startsWith("light.") && !e.hidden && !e.entity_category && hass.states[e.entity_id] && lccAreaOf(hass, e) === cfg.area
        ).map((e) => e.entity_id);
        headIds = areaLights.filter((id) => lccIsGroupLike(hass.states[id]));
        memberIds = areaLights.filter((id) => !lccIsGroupLike(hass.states[id]));
      } else {
        headIds = [cfg.entity];
        if (mode === "group") memberIds = lccMembersOf(hass, cfg.entity);
      }
      const relevantEntityIds = [...headIds, ...memberIds];
      const snapshot = relevantEntityIds.map((id) => hass.states[id]);
      if (this._lastIds && this._lastIds.join() === relevantEntityIds.join() && this._lastSnapshot.every((st, i) => st === snapshot[i])) {
        return;
      }
      if (this._interacting) {
        this._pendingHass = hass;
        return;
      }
      this._lastIds = relevantEntityIds;
      this._lastSnapshot = snapshot;
      this._main.innerHTML = "";
      this._main.style.cssText = "";
      this._members.innerHTML = "";
      if (mode === "room") {
        const area = hass.areas && hass.areas[cfg.area];
        this._titleEl.textContent = cfg.name || (area ? area.name : cfg.area);
        this._titleEl.style.display = "block";
        if (relevantEntityIds.length === 0) {
          this._main.textContent = "No lights found in this area.";
          this._main.style.cssText = "color:var(--secondary-text-color); padding:8px 4px;";
        }
        headIds.forEach((id) => this._main.appendChild(this._buildRow(id, { withMoreInfo: true })));
        const member = headIds.length > 0;
        memberIds.forEach((id) => this._members.appendChild(this._buildRow(id, { withMoreInfo: true, member })));
      } else {
        this._titleEl.style.display = "none";
        const row = this._buildRow(cfg.entity, { withMoreInfo: true });
        if (cfg.name) {
          const nameEl = row.querySelector(".lcc-name");
          if (nameEl) nameEl.textContent = cfg.name;
        }
        this._main.appendChild(row);
        memberIds.forEach((id) => this._members.appendChild(this._buildRow(id, { withMoreInfo: true, member: true })));
      }
      this._scenesEl.innerHTML = "";
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
  };
  function registerLightControlCard() {
    if (!customElements.get("light-control-card-editor")) {
      customElements.define("light-control-card-editor", LightControlCardEditor);
    }
    if (!customElements.get("light-control-card")) {
      customElements.define("light-control-card", LightControlCard);
    }
    window.customCards = window.customCards || [];
    window.customCards.push({
      type: "light-control-card",
      name: "Light Control Card",
      description: "Light/group/room control with icon, toggle, brightness, scenes, and full more-info pop-up (visual editor supported)"
    });
  }

  // src/index.js
  registerGaugeZoneCard();
  registerAlarmPanelCard();
  registerLightControlCard();
  console.info("%c CHURCH-DRIVE-CARDS %c loaded ", "color: white; background: #2196f3; font-weight: 700;", "color: #2196f3; background: transparent;");
})();
