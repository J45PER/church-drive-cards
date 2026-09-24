(() => {
  // src/form-editor.js
  function createFormEditor({ schema, labels = {}, helpers = {}, normalize = (c) => c }) {
    return class extends HTMLElement {
      setConfig(config) {
        this._config = normalize(config || {});
        this._render();
      }
      set hass(hass) {
        this._hass = hass;
        this._render();
      }
      _render() {
        if (!this._hass || !this._config) return;
        if (!this._form) {
          this._form = document.createElement("ha-form");
          this._form.addEventListener("value-changed", (ev) => {
            this._config = ev.detail.value;
            this.dispatchEvent(
              new CustomEvent("config-changed", { detail: { config: this._config }, bubbles: true, composed: true })
            );
            this._render();
          });
          this.appendChild(this._form);
        }
        this._form.hass = this._hass;
        this._form.data = this._config;
        this._form.schema = schema(this._config);
        this._form.computeLabel = (s) => labels[s.name] || s.title || s.name;
        this._form.computeHelper = (s) => helpers[s.name];
      }
    };
  }

  // src/gauge-zone-card.js
  var GaugeZoneCardEditor = createFormEditor({
    schema: (config) => [
      { name: "title", selector: { text: {} } },
      {
        type: "expandable",
        name: "",
        title: "Colours, units and icons",
        flatten: true,
        schema: [
          {
            name: "direction",
            selector: {
              select: {
                mode: "dropdown",
                options: [
                  { value: "low", label: "Low value is bad (battery, signal)" },
                  { value: "high", label: "High value is bad (storage, CPU)" }
                ]
              }
            }
          },
          {
            type: "grid",
            name: "",
            schema: [
              { name: "alert_at", selector: { number: { mode: "box" } } },
              { name: "warn_at", selector: { number: { mode: "box" } } },
              { name: "unit", selector: { text: {} } },
              { name: "max", selector: { number: { mode: "box", min: 0 } } }
            ]
          },
          {
            name: "icon_mode",
            selector: {
              select: {
                mode: "dropdown",
                options: [
                  { value: "battery", label: "Battery (steps with the value)" },
                  { value: "gauge", label: "Gauge" },
                  { value: "entity", label: "Each entity's own icon" },
                  { value: "custom", label: "Custom icon (pick below)" }
                ]
              }
            }
          },
          ...config.icon_mode === "custom" ? [{ name: "icon", selector: { icon: {} } }] : []
        ]
      },
      {
        name: "entities",
        selector: {
          object: {
            multiple: true,
            label_field: "name",
            description_field: "entity",
            fields: {
              entity: { label: "Entity", selector: { entity: {} } },
              name: { label: "Name", required: true, selector: { text: {} } },
              word: {
                label: "Battery wording (shows the Battery Notes date)",
                selector: {
                  select: {
                    mode: "dropdown",
                    custom_value: true,
                    options: ["replaced", "charged", "swapped"]
                  }
                }
              },
              secondary: { label: "Secondary text (instead of wording)", selector: { text: {} } },
              icon: { label: "Icon override", selector: { icon: {} } },
              value: { label: "Fixed value (instead of an entity)", selector: { number: { mode: "box" } } },
              date: { label: "Replaced/charged date (fixed-value rows only)", selector: { date: {} } },
              unit: { label: "Unit override", selector: { text: {} } },
              max: { label: "Max override", selector: { number: { mode: "box", min: 0 } } }
            }
          }
        }
      }
    ],
    labels: {
      title: "Title",
      direction: "Which end is bad",
      alert_at: "Red at",
      warn_at: "Orange at",
      unit: "Unit",
      max: "Full bar value",
      icon_mode: "Icons",
      icon: "Icon for every row",
      entities: "Rows"
    },
    helpers: {
      alert_at: "Defaults: 20 (low is bad) / 90 (high is bad)",
      warn_at: "Defaults: 50 (low is bad) / 75 (high is bad)",
      unit: "Default %",
      max: "Default 100"
    }
  });
  function lczFormatDate(raw) {
    const d = /^\d{4}-\d{2}-\d{2}/.test(raw) ? new Date(raw) : null;
    return d && !isNaN(d) ? d.toLocaleDateString("en-GB", { day: "2-digit", month: "short", year: "numeric" }) : raw;
  }
  var GaugeZoneCard = class _GaugeZoneCard extends HTMLElement {
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
          const raw = e.date || e.demo_date;
          dateStr = raw ? lczFormatDate(raw) : "unknown";
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
        let useStateIcon = false;
        if (e.icon) {
          icon = e.icon;
        } else if (iconMode === "battery") {
          icon = this._batteryIcon(e.available ? e.val : 0);
        } else if (iconMode === "custom" && cfg.icon) {
          icon = cfg.icon;
        } else if (iconMode === "entity" && e.st) {
          const entry = hass.entities && hass.entities[e.entity];
          icon = entry && entry.icon || e.st.attributes.icon;
          useStateIcon = !icon && !!customElements.get("ha-state-icon");
          icon = icon || "mdi:gauge";
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
          <div style="font-weight:500; color:#ffffff; overflow:hidden; text-overflow:ellipsis; white-space:nowrap;">${e.name || e.st && e.st.attributes.friendly_name || e.entity || ""}</div>
          ${secondaryText ? `<div style="font-size:0.85rem; color:rgba(255,255,255,0.65);">${secondaryText}</div>` : ""}
        </div>
        <div style="font-weight:600; color:#ffffff; margin-left:8px; flex-shrink:0;">${e.available ? Math.round(e.val) + unit : "n/a"}</div>
      `;
        if (useStateIcon) {
          const placeholder = row.querySelector("ha-icon");
          const stateIcon = document.createElement("ha-state-icon");
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
      return { columns: 12, min_columns: 6, rows: "auto" };
    }
    static getConfigElement() {
      return document.createElement("gauge-zone-card-editor");
    }
    // What a new card starts with in the card picker (and its preview).
    // battery-zone-card: up to three real battery sensors, lowest first.
    // gauge-zone-card: a single fixed example row to edit.
    static getStubConfig(hass) {
      if (this === _GaugeZoneCard) {
        const batteries = Object.values(hass && hass.states || {}).filter((st) => st.attributes.device_class === "battery" && st.attributes.unit_of_measurement === "%" && !isNaN(parseFloat(st.state))).sort((a, b) => parseFloat(a.state) - parseFloat(b.state)).slice(0, 3).map((st) => ({ entity: st.entity_id, name: st.attributes.friendly_name || st.entity_id }));
        return { title: "Batteries", entities: batteries };
      }
      return { title: "Gauge", direction: "high", entities: [{ name: "Example", value: 42 }] };
    }
  };
  function registerGaugeZoneCard() {
    if (!customElements.get("gauge-zone-card-editor")) {
      customElements.define("gauge-zone-card-editor", GaugeZoneCardEditor);
    }
    if (!customElements.get("battery-zone-card")) {
      customElements.define("battery-zone-card", GaugeZoneCard);
    }
    if (!customElements.get("gauge-zone-card")) {
      customElements.define("gauge-zone-card", class extends GaugeZoneCard {
      });
    }
    window.customCards = window.customCards || [];
    window.customCards.push({
      type: "battery-zone-card",
      name: "Battery Zone Card",
      description: "Zone battery status with gradient rows",
      preview: true,
      documentationURL: "https://github.com/J45PER/church-drive-cards#readme"
    });
    window.customCards.push({
      type: "gauge-zone-card",
      name: "Gauge Zone Card",
      description: "Generic % / value gauge rows with gradient fill \u2014 storage, signal, humidity, CPU, anything measurable",
      preview: true,
      documentationURL: "https://github.com/J45PER/church-drive-cards#readme"
    });
  }

  // src/alarm-panel-card.js
  var APC_STATE_OPTIONS = [
    { value: "disarmed", label: "Disarmed" },
    { value: "armed_home", label: "Armed Home" },
    { value: "armed_away", label: "Armed Away" },
    { value: "armed_night", label: "Armed Night" },
    { value: "arming", label: "Arming (exit delay)" },
    { value: "pending", label: "Entry delay" },
    { value: "triggered", label: "Triggered" }
  ];
  var AlarmPanelCardEditor = createFormEditor({
    schema: () => [
      { name: "entity", selector: { entity: { domain: "alarm_control_panel" } } },
      {
        type: "expandable",
        name: "",
        title: "Demo mode (fake data, buttons do nothing)",
        flatten: true,
        schema: [
          { name: "demo", selector: { boolean: {} } },
          { name: "demo_state", selector: { select: { mode: "dropdown", options: APC_STATE_OPTIONS } } },
          { name: "demo_target_state", selector: { select: { mode: "dropdown", options: APC_STATE_OPTIONS.slice(1, 4) } } },
          { name: "demo_countdown", selector: { number: { mode: "box", min: 0, unit_of_measurement: "s" } } },
          { name: "demo_by", selector: { text: {} } },
          { name: "demo_time", selector: { datetime: {} } },
          { name: "demo_supported_features", selector: { number: { mode: "box", min: 0 } } }
        ]
      }
    ],
    labels: {
      entity: "Alarm entity",
      demo: "Use demo data instead of the entity",
      demo_state: "Demo state",
      demo_target_state: "Mode being armed to (during a delay)",
      demo_countdown: "Countdown",
      demo_by: "Armed/disarmed by",
      demo_time: "Armed/disarmed at",
      demo_supported_features: "Supported features"
    },
    helpers: {
      demo_supported_features: "Bitmask: 1 = Arm Home, 2 = Arm Away, 4 = Arm Night (default 3)"
    }
  });
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
        const demoTime = this.config.demo_time ? String(this.config.demo_time).replace(" ", "T") : (/* @__PURE__ */ new Date()).toISOString();
        st = {
          state: this.config.demo_state || "armed_away",
          attributes: {
            supported_features: this.config.demo_supported_features !== void 0 ? this.config.demo_supported_features : 3,
            targetState: this.config.demo_target_state || "armed_away",
            lastArmedBy: this.config.demo_by || "Demo User",
            lastArmedTime: demoTime,
            lastDisarmedBy: this.config.demo_by || "Demo User",
            lastDisarmedTime: demoTime,
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
      this._countdownShown = active;
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
    // Header + buttons ~3 units; the countdown adds ~2 while it's showing.
    getCardSize() {
      return this._countdownShown ? 5 : 3;
    }
    // Sections-view defaults; the editor's Layout tab can override them.
    getGridOptions() {
      return { columns: 12, min_columns: 6, rows: "auto" };
    }
    static getConfigElement() {
      return document.createElement("alarm-panel-card-editor");
    }
    // Pre-fill the card picker with the first real alarm entity.
    static getStubConfig(hass) {
      const first = hass && Object.keys(hass.states).find((id) => id.startsWith("alarm_control_panel."));
      return first ? { entity: first } : { demo: true };
    }
  };
  function registerAlarmPanelCard() {
    if (!customElements.get("alarm-panel-card-editor")) {
      customElements.define("alarm-panel-card-editor", AlarmPanelCardEditor);
    }
    if (!customElements.get("alarm-panel-card")) {
      customElements.define("alarm-panel-card", AlarmPanelCard);
    }
    window.customCards = window.customCards || [];
    window.customCards.push({
      type: "alarm-panel-card",
      name: "Alarm Panel Card",
      description: "Alarm status, entry/exit countdown, and arm/disarm controls",
      preview: true,
      documentationURL: "https://github.com/J45PER/church-drive-cards#readme"
    });
  }

  // src/scene-style.js
  var PALETTES = {
    bright: ["#fff6e0", "#ffd98a"],
    dimmed: ["#8a6630", "#3b2a14"],
    nightlight: ["#ff8a2a", "#3a1a05"],
    relax: ["#ffb35c", "#e0702a"],
    rest: ["#ff9f4a", "#8f4416"],
    read: ["#fff1d6", "#ffc978"],
    concentrate: ["#f2f6ff", "#a9c7ff"],
    energise: ["#d9ecff", "#5d9eff"],
    "natural light": ["#ffe0a0", "#9fd0ff"],
    soho: ["#ff4f8b", "#ffb347", "#7b2ff7"],
    "lake placid": ["#0f5e9c", "#35baf6", "#9fe2bf"],
    "toil and trouble": ["#6a0dad", "#2e8b57", "#ff7f00"],
    "bright & blue": ["#e8f4ff", "#3d7dff"],
    arise: ["#ff7b39", "#ffd27f"],
    spellbound: ["#3a0ca3", "#f72585", "#4cc9f0"],
    storybook: ["#ffadad", "#ffd6a5", "#9bf6ff"],
    unwind: ["#ff9966", "#ff5e62"],
    "pumpkin patch": ["#ff7518", "#8b4513", "#ffb347"],
    shine: ["#fffbd6", "#ffd23f"],
    phantom: ["#2d0a4e", "#6c2bd9", "#0f0f2e"],
    "city blue": ["#0b1d51", "#2f6fd6", "#89c2ff"]
  };
  var ICONS = [
    [/night/, "mdi:weather-night"],
    [/read/, "mdi:book-open-variant"],
    [/concentrat/, "mdi:head-lightbulb-outline"],
    [/energi/, "mdi:lightning-bolt"],
    [/relax|unwind/, "mdi:sofa-outline"],
    [/rest/, "mdi:bed-outline"],
    [/dim/, "mdi:brightness-5"],
    [/bright|shine|arise/, "mdi:white-balance-sunny"],
    [/natural/, "mdi:weather-sunset"]
  ];
  function baseName(name) {
    return String(name || "").toLowerCase().replace(/\s+\d+$/, "").trim();
  }
  function scenePalette(name) {
    const key = baseName(name);
    if (PALETTES[key]) return PALETTES[key];
    let h = 0;
    for (const ch of key) h = (h * 31 + ch.charCodeAt(0)) % 360;
    return [`hsl(${h} 70% 55%)`, `hsl(${(h + 50) % 360} 70% 35%)`];
  }
  function sceneBackground(name) {
    const colours = scenePalette(name);
    return `linear-gradient(135deg, ${colours.join(", ")})`;
  }
  function sceneIcon(name, isDynamic) {
    const key = baseName(name);
    for (const [re, icon] of ICONS) if (re.test(key)) return icon;
    return isDynamic ? "mdi:animation-play-outline" : "mdi:palette-outline";
  }

  // src/light-control-card.js
  var LCC_DEFAULT_MAX_SCENES = 6;
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
  function lccLightIcon(hass, st, isGroupLike) {
    const on = st && st.state === "on";
    const entry = st && hass.entities && hass.entities[st.entity_id];
    if (entry && entry.icon) return entry.icon;
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
  function lccNormalizeScenes(scenes) {
    return (scenes || []).map((s) => typeof s === "string" ? { entity: s } : s).filter((s) => s && s.entity);
  }
  function lccSceneGroup(hass, sceneId) {
    const entry = hass.entities && hass.entities[sceneId];
    if (!entry || !entry.device_id) return null;
    const group = Object.values(hass.entities).find(
      (e) => e.device_id === entry.device_id && e.entity_id.startsWith("light.") && lccIsGroupLike(hass.states[e.entity_id])
    );
    return group ? group.entity_id : null;
  }
  function lccAutoScenes(hass, groupIds, lightIds) {
    const lights = new Set(lightIds);
    const groups = [...groupIds];
    if (lights.size) {
      Object.values(hass.states).forEach((st) => {
        const members = lccIsGroupLike(st) && st.entity_id.startsWith("light.") ? st.attributes.entity_id : null;
        if (members && members.length && members.every((m) => lights.has(m)) && !groups.includes(st.entity_id)) {
          groups.push(st.entity_id);
        }
      });
    }
    const seen = /* @__PURE__ */ new Set();
    const out = [];
    groups.forEach((groupId) => {
      const device = hass.entities && hass.entities[groupId] && hass.entities[groupId].device_id;
      if (!device) return;
      const order = hass.states[groupId] && hass.states[groupId].attributes.hue_scenes || [];
      const rank = (id) => {
        const i = order.indexOf(hass.states[id].attributes.name);
        return i === -1 ? order.length : i;
      };
      Object.values(hass.entities).filter((e) => e.entity_id.startsWith("scene.") && e.device_id === device && !e.hidden && hass.states[e.entity_id]).map((e) => e.entity_id).sort((a, b) => rank(a) - rank(b) || a.localeCompare(b)).forEach((id) => {
        const key = String(hass.states[id].attributes.name || id).toLowerCase();
        if (seen.has(key)) return;
        seen.add(key);
        out.push({ entity: id });
      });
    });
    return out;
  }
  var LightControlCardEditor = createFormEditor({
    schema: (config) => {
      const mode = config.mode || "light";
      return [
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
        },
        mode === "room" ? { name: "area", selector: { area: {} } } : { name: "entity", selector: { entity: { domain: "light" } } },
        { name: "name", selector: { text: {} } },
        { name: "max_scenes", selector: { number: { mode: "box", min: 0, max: 24 } } },
        {
          name: "scenes",
          selector: {
            object: {
              multiple: true,
              label_field: "name",
              description_field: "entity",
              fields: {
                entity: { label: "Scene", required: true, selector: { entity: { domain: "scene" } } },
                name: { label: "Name override", selector: { text: {} } },
                icon: { label: "Icon override", selector: { icon: {} } },
                image: { label: "Picture (replaces the colour background)", selector: { image: {} } }
              }
            }
          }
        }
      ];
    },
    normalize: (config) => config.scenes ? { ...config, scenes: lccNormalizeScenes(config.scenes) } : config,
    labels: {
      mode: "Card type",
      area: "Room",
      entity: "Light entity",
      name: "Title (optional)",
      max_scenes: "Max scenes",
      scenes: "Scenes (leave empty to pick them automatically)"
    },
    helpers: {
      max_scenes: "Default 6. Set 0 to hide scenes."
    }
  });
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
    // Pre-fill the card picker with a real light so the preview isn't an error.
    static getStubConfig(hass) {
      const first = hass && Object.keys(hass.states).find((id) => id.startsWith("light."));
      return { mode: "light", entity: first || "" };
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
    // Animated (dynamic) Hue scenes are started with hue.activate_scene so
    // they actually play; everything else is a plain scene.turn_on.
    _activateScene(entityId, isDynamic) {
      const hue = this._hass.services && this._hass.services.hue;
      if (isDynamic && hue && hue.activate_scene) {
        this._hass.callService("hue", "activate_scene", { dynamic: true }, { entity_id: entityId });
      } else {
        this._hass.callService("scene", "turn_on", {}, { entity_id: entityId });
      }
    }
    // Pause a playing animated scene. Re-applying the scene with dynamic: false
    // doesn't work: Hue restarts the animation for scenes set to animate
    // automatically. Any explicit colour command does stop it, so send each lit
    // bulb of the scene's group the colour and brightness it's showing now.
    _freezeScene(scene) {
      const hass = this._hass;
      const ids = scene.group ? lccMembersOf(hass, scene.group) : this._cardLightIds || [];
      ids.map((id) => hass.states[id]).filter((st) => st && st.state === "on" && !lccIsGroupLike(st)).forEach((st) => {
        const a = st.attributes;
        const data = {};
        if (a.color_mode === "color_temp" && a.color_temp_kelvin) data.color_temp_kelvin = a.color_temp_kelvin;
        else if (a.xy_color) data.xy_color = a.xy_color;
        else if (a.color_temp_kelvin) data.color_temp_kelvin = a.color_temp_kelvin;
        if (a.brightness) data.brightness = a.brightness;
        hass.callService("light", "turn_on", data, { entity_id: st.entity_id });
      });
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
      const icon = lccLightIcon(this._hass, st, isGroupLike);
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
        ["pointerdown", "pointerup", "pointercancel"].forEach(
          (type) => moreBtn.addEventListener(type, (ev) => ev.stopPropagation())
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
        return Math.min(100, Math.max(0, (ev.clientX - rect.left) / rect.width * 100));
      };
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
      row.addEventListener("pointerdown", (ev) => {
        pressed = true;
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
        if (!pressed) return;
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
    // Square scene tiles: picture (or palette gradient) background, icon, name.
    // The selected scene glows in its own colour and the others are dimmed; an
    // animated scene shows a pulsing play badge while running, pause when not.
    _buildScenes(scenes) {
      if (!scenes.length) return null;
      const anyActive = scenes.some((s) => s.active);
      const wrap = document.createElement("div");
      wrap.style.cssText = "display:grid; grid-template-columns:repeat(auto-fill, minmax(84px, 1fr)); gap:8px; margin-top:12px; padding:4px 4px 8px;";
      scenes.forEach((s) => {
        const tile = document.createElement("button");
        tile.className = s.active || !anyActive ? "lcc-scene" : "lcc-scene lcc-dim";
        const glow = `color-mix(in srgb, ${scenePalette(s.name)[0]} 85%, transparent)`;
        tile.title = s.playing ? `${s.name} (playing, tap to stop)` : s.paused ? `${s.name} (paused, tap to play)` : s.name;
        const bg = s.image ? `center / cover no-repeat url("${s.image}")` : sceneBackground(s.name);
        tile.style.cssText = `position:relative; container-type:inline-size; aspect-ratio:1 / 1; border:none; border-radius:14px; padding:0; overflow:hidden; cursor:pointer; background:${bg};${s.active ? ` box-shadow:0 0 16px 3px ${glow}; transform:scale(1.04); z-index:1;` : ""}`;
        tile.innerHTML = `
        <div style="position:absolute; inset:0; background:linear-gradient(to top, rgba(0,0,0,0.6), rgba(0,0,0,0) 65%);"></div>
        <ha-icon icon="${s.icon}" style="position:absolute; left:50%; top:44%; transform:translate(-50%, -50%); --mdc-icon-size:40cqw; color:#fff; filter:drop-shadow(0 1px 3px rgba(0,0,0,0.55));"></ha-icon>
        ${s.paused ? '<ha-icon class="lcc-paused" icon="mdi:pause" title="Paused" style="position:absolute; top:6px; right:6px; --mdc-icon-size:20px; color:#fff; filter:drop-shadow(0 1px 2px rgba(0,0,0,0.7));"></ha-icon>' : ""}
        ${s.playing ? '<ha-icon class="lcc-playing" icon="mdi:play" title="Playing" style="position:absolute; top:6px; right:6px; --mdc-icon-size:20px; color:#fff; filter:drop-shadow(0 1px 2px rgba(0,0,0,0.7));"></ha-icon>' : ""}
        <div class="lcc-scene-name" style="position:absolute; left:8px; right:8px; bottom:7px; text-align:center; color:#fff; font-size:0.8rem; font-weight:600; line-height:1.15; text-shadow:0 1px 2px rgba(0,0,0,0.6); display:-webkit-box; -webkit-line-clamp:2; -webkit-box-orient:vertical; overflow:hidden;"></div>`;
        tile.querySelector(".lcc-scene-name").textContent = s.name;
        this._bindSceneTile(tile, s);
        wrap.appendChild(tile);
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
        tile.style.opacity = "";
      };
      tile.style.webkitTouchCallout = "none";
      tile.style.userSelect = "none";
      tile.addEventListener("contextmenu", (ev) => ev.preventDefault());
      tile.addEventListener("pointerdown", (ev) => {
        held = false;
        moved = false;
        start = { x: ev.clientX, y: ev.clientY };
        timer = setTimeout(() => {
          held = true;
          timer = null;
          tile.style.opacity = "0.6";
          if (navigator.vibrate) navigator.vibrate(30);
          this._turnOffCardLights();
        }, 500);
      });
      tile.addEventListener("pointermove", (ev) => {
        if (start && Math.hypot(ev.clientX - start.x, ev.clientY - start.y) > 10) {
          moved = true;
          cancel();
        }
      });
      ["pointerup", "pointercancel", "pointerleave"].forEach((type) => tile.addEventListener(type, cancel));
      tile.addEventListener("click", (ev) => {
        if (held || moved) {
          ev.preventDefault();
          held = false;
          moved = false;
          return;
        }
        if (scene.playing) this._freezeScene(scene);
        else this._activateScene(scene.entity, scene.isDynamic);
      });
    }
    _turnOffCardLights() {
      const ids = this._cardLightIds || [];
      if (ids.length) this._hass.callService("light", "turn_off", {}, { entity_id: ids });
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
        if (mode === "light" && !groups.length) {
          const area = lccAreaOf(hass, hass.entities && hass.entities[cfg.entity]);
          const areaLights = Object.values(hass.entities || {}).filter((e) => e.entity_id.startsWith("light.") && hass.states[e.entity_id] && area && lccAreaOf(hass, e) === area).map((e) => e.entity_id);
          groups = areaLights.filter((id) => lccIsGroupLike(hass.states[id]));
          lights = areaLights.filter((id) => !lccIsGroupLike(hass.states[id]));
        }
        items = lccAutoScenes(hass, groups, lights);
      }
      const scenes = items.filter((s) => hass.states[s.entity]).slice(0, max).map((s) => {
        const st = hass.states[s.entity];
        const isDynamic = st.attributes.is_dynamic === true;
        const name = s.name || st.attributes.name || st.attributes.friendly_name || s.entity;
        return {
          ...s,
          name,
          isDynamic,
          icon: s.icon || sceneIcon(name, isDynamic),
          group: lccSceneGroup(hass, s.entity),
          activated: Date.parse(st.state) || 0
        };
      });
      const latest = scenes.reduce((a, b) => b.activated > (a ? a.activated : 0) ? b : a, null);
      if (latest) {
        const groupSt = latest.group && hass.states[latest.group];
        const lightsOn = groupSt ? groupSt.state === "on" : [...headIds, ...memberIds].some((id) => hass.states[id] && hass.states[id].state === "on");
        latest.active = lightsOn;
        if (lightsOn && latest.isDynamic && groupSt) {
          latest.playing = lccMembersOf(hass, latest.group).some(
            (id) => hass.states[id].state === "on" && hass.states[id].attributes.dynamics === "dynamic_palette"
          );
          latest.paused = !latest.playing;
        }
      }
      return scenes;
    }
    set hass(hass) {
      this._hass = hass;
      const cfg = this.config;
      const mode = cfg.mode || (cfg.area ? "room" : "light");
      if (!this._built) {
        this.innerHTML = `
        <ha-card style="border:none; box-shadow: 0 3px 10px rgba(0,0,0,0.45); border-radius:16px; overflow:hidden; background: var(--card-background-color); padding:16px 16px 14px 16px;">
          <style>
            @keyframes lcc-pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.5; } }
            .lcc-playing { animation: lcc-pulse 1.6s ease-in-out infinite; }
            .lcc-scene { transition: opacity 0.2s, filter 0.2s, transform 0.2s, box-shadow 0.2s; }
            .lcc-scene.lcc-dim { opacity: 0.4; filter: saturate(0.4); }
            .lcc-scene.lcc-dim:hover { opacity: 0.8; filter: none; }
          </style>
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
          (e) => e.entity_id.startsWith("light.") && !e.hidden && e.entity_category == null && hass.states[e.entity_id] && lccAreaOf(hass, e) === cfg.area
        ).map((e) => e.entity_id);
        headIds = areaLights.filter((id) => lccIsGroupLike(hass.states[id]));
        memberIds = areaLights.filter((id) => !lccIsGroupLike(hass.states[id]));
      } else {
        headIds = [cfg.entity];
        if (mode === "group") memberIds = lccMembersOf(hass, cfg.entity);
      }
      const relevantEntityIds = [...headIds, ...memberIds];
      this._cardLightIds = relevantEntityIds;
      const scenes = this._resolveScenes(hass, mode, headIds, memberIds);
      const watchIds = [
        ...relevantEntityIds,
        ...scenes.map((s) => s.entity),
        ...scenes.map((s) => s.group).filter(Boolean),
        ...scenes.filter((s) => s.group).flatMap((s) => lccMembersOf(hass, s.group))
      ];
      const snapshot = watchIds.map((id) => hass.states[id]);
      if (this._lastIds && this._lastIds.join() === watchIds.join() && this._lastSnapshot.every((st, i) => st === snapshot[i])) {
        return;
      }
      if (this._interacting) {
        this._pendingHass = hass;
        return;
      }
      this._lastIds = watchIds;
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
      const scenesGrid = this._buildScenes(scenes);
      if (scenesGrid) this._scenesEl.appendChild(scenesGrid);
      this._size = 1 + (mode === "room" ? 1 : 0) + Math.max(relevantEntityIds.length, 1) + Math.ceil(scenes.length / 4) * 2;
    }
    getCardSize() {
      return this._size || 3;
    }
    // Sections-view defaults; the editor's Layout tab can override them.
    getGridOptions() {
      return { columns: 12, min_columns: 6, rows: "auto" };
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
      description: "Light/group/room control with icon, toggle, brightness, scenes, and full more-info pop-up (visual editor supported)",
      preview: true,
      documentationURL: "https://github.com/J45PER/church-drive-cards#readme"
    });
  }

  // src/index.js
  registerGaugeZoneCard();
  registerAlarmPanelCard();
  registerLightControlCard();
  console.info("%c CHURCH-DRIVE-CARDS %c loaded ", "color: white; background: #2196f3; font-weight: 700;", "color: #2196f3; background: transparent;");
})();
