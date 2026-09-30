(() => {
  // src/form-editor.js
  var same = (c) => c;
  function findSelector(root, name, depth = 0) {
    if (!root || depth > 6) return null;
    for (const el of root.querySelectorAll("*")) {
      if (el.localName === "ha-selector" && el.name === name) return el;
      const found = el.shadowRoot && findSelector(el.shadowRoot, name, depth + 1);
      if (found) return found;
    }
    return null;
  }
  function placeInList(form, field, button) {
    const selector = findSelector(form.shadowRoot, field);
    const list = selector && selector.shadowRoot && selector.shadowRoot.querySelector("ha-selector-object");
    const container = list && list.shadowRoot && list.shadowRoot.querySelector(".items-container");
    if (!container) return false;
    if (button.parentNode === container) return true;
    if (!list.shadowRoot.querySelector("style.cd-row")) {
      const style = document.createElement("style");
      style.className = "cd-row";
      style.textContent = ".items-container{display:flex;flex-wrap:wrap;align-items:center;justify-content:space-between;row-gap:8px}.items-container>ha-sortable{flex-basis:100%}.items-container>.cd-button{order:1}.items-container>ha-button:not(.cd-button){order:2}.items-container>.cd-alert{order:-1;flex-basis:100%}";
      list.shadowRoot.appendChild(style);
    }
    button.style.marginTop = "";
    container.appendChild(button);
    return true;
  }
  function createFormEditor({ schema, labels = {}, helpers = {}, normalize = same, fill = same, display = same, store = same, buttons = [], alerts = [] }) {
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
          this._form.addEventListener("value-changed", (ev) => this._changed(store(ev.detail.value)));
          this.appendChild(this._form);
          this._buttons = buttons.map(({ label, apply, field, variant }) => {
            const button = document.createElement("ha-button");
            button.className = "cd-button";
            button.textContent = label;
            button.setAttribute("appearance", "filled");
            if (variant) button.setAttribute("variant", variant);
            button.addEventListener("click", () => this._changed(fill(apply(this._config), this._hass)));
            return { button, field };
          });
          this._alerts = alerts.map(({ field, text }) => {
            const alert = document.createElement("ha-alert");
            alert.className = "cd-alert";
            alert.setAttribute("alert-type", "warning");
            return { button: alert, field, text };
          });
        }
        const filled = fill(this._config, this._hass);
        if (filled !== this._config) {
          this._changed(filled);
          return;
        }
        this._form.hass = this._hass;
        this._form.data = display(this._config, this._hass);
        this._form.schema = schema(this._config, this._hass);
        this._form.computeLabel = (s) => labels[s.name] || s.title || s.name;
        this._form.computeHelper = (s) => helpers[s.name];
        (this._alerts || []).forEach((a) => {
          const message = a.text(this._config) || "";
          a.button.textContent = message;
          a.button.style.display = message ? "" : "none";
        });
        this._placeButtons();
      }
      // HA renders the form's insides asynchronously: try for a couple of
      // seconds to reach the list's Add row, else put the button under the form.
      _placeButtons(tries = 0) {
        clearTimeout(this._placeTimer);
        const waiting = [...this._buttons || [], ...this._alerts || []].filter(({ button, field }) => !(field && placeInList(this._form, field, button)));
        if (!waiting.length) return;
        if (tries < 20) {
          this._placeTimer = setTimeout(() => this._placeButtons(tries + 1), 100);
          return;
        }
        waiting.forEach(({ button }) => {
          if (button.parentNode === this) return;
          button.style.marginTop = "16px";
          this.appendChild(button);
        });
      }
      _changed(config) {
        this._config = config;
        this.dispatchEvent(new CustomEvent("config-changed", { detail: { config }, bubbles: true, composed: true }));
        this._render();
      }
    };
  }

  // src/suffix.js
  var SUFFIX = true ? "-beta" : "";
  var LABEL = SUFFIX ? " (beta)" : "";

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
      const iconMode = cfg.icon_mode || (this.tagName.toLowerCase() === `battery-zone-card${SUFFIX}` ? "battery" : "gauge");
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
        const row3 = document.createElement("div");
        row3.style.cssText = `display:flex; align-items:center; box-sizing:border-box; width:100%; padding:10px 16px; margin:${isFirst ? "0" : "4px"} 0 0 0; border:none; border-radius:${radius}; ${maskCss} background: linear-gradient(to right, ${color} 0%, transparent ${widthPct}%);`;
        row3.innerHTML = `
        <ha-icon icon="${icon}" style="color:${iconColor}; margin-right:14px; flex-shrink:0; --mdc-icon-size:26px;"></ha-icon>
        <div style="flex:1; min-width:0;">
          <div style="font-weight:500; color:#ffffff; overflow:hidden; text-overflow:ellipsis; white-space:nowrap;">${e.name || e.st && e.st.attributes.friendly_name || e.entity || ""}</div>
          ${secondaryText ? `<div style="font-size:0.85rem; color:rgba(255,255,255,0.65);">${secondaryText}</div>` : ""}
        </div>
        <div style="font-weight:600; color:#ffffff; margin-left:8px; flex-shrink:0;">${e.available ? Math.round(e.val) + unit : "n/a"}</div>
      `;
        if (useStateIcon) {
          const placeholder = row3.querySelector("ha-icon");
          const stateIcon = document.createElement("ha-state-icon");
          stateIcon.hass = hass;
          stateIcon.stateObj = e.st;
          stateIcon.style.cssText = placeholder.style.cssText;
          placeholder.replaceWith(stateIcon);
        }
        this._rows.appendChild(row3);
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
      return document.createElement(`gauge-zone-card-editor${SUFFIX}`);
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
    if (!customElements.get(`gauge-zone-card-editor${SUFFIX}`)) {
      customElements.define(`gauge-zone-card-editor${SUFFIX}`, GaugeZoneCardEditor);
    }
    if (!customElements.get(`battery-zone-card${SUFFIX}`)) {
      customElements.define(`battery-zone-card${SUFFIX}`, GaugeZoneCard);
    }
    if (!customElements.get(`gauge-zone-card${SUFFIX}`)) {
      customElements.define(`gauge-zone-card${SUFFIX}`, class extends GaugeZoneCard {
      });
    }
    window.customCards = window.customCards || [];
    window.customCards.push({
      type: `battery-zone-card${SUFFIX}`,
      name: `Battery Zone Card${LABEL}`,
      description: "Zone battery status with gradient rows",
      preview: true,
      documentationURL: "https://github.com/J45PER/church-drive-cards#readme"
    });
    window.customCards.push({
      type: `gauge-zone-card${SUFFIX}`,
      name: `Gauge Zone Card${LABEL}`,
      description: "Generic % / value gauge rows with gradient fill \u2014 storage, signal, humidity, CPU, anything measurable",
      preview: true,
      documentationURL: "https://github.com/J45PER/church-drive-cards#readme"
    });
  }

  // src/icons.js
  var cache = /* @__PURE__ */ new Map();
  var pending = /* @__PURE__ */ new Map();
  function packFor(icon) {
    const [prefix, name] = String(icon || "").split(":");
    if (!name || prefix === "mdi" || prefix === "hass") return null;
    const set = window.customIcons && window.customIcons[prefix];
    if (set && typeof set.getIcon === "function") return { get: () => set.getIcon(name) };
    const legacy = window.customIconsets && window.customIconsets[prefix];
    if (typeof legacy === "function") return { get: () => legacy(name) };
    return { waiting: true };
  }
  function isCustom(icon) {
    const [prefix, name] = String(icon || "").split(":");
    return !!name && prefix !== "mdi" && prefix !== "hass";
  }
  function svg(def, size) {
    const d = (def.path || "").replace(/"/g, "&quot;");
    return `<svg viewBox="${def.viewBox || "0 0 24 24"}" width="${size}" height="${size}" style="display:block; fill:currentColor;"><path d="${d}"></path></svg>`;
  }
  function escapeAttr(text) {
    return String(text).replace(/&/g, "&amp;").replace(/"/g, "&quot;");
  }
  function iconHtml(icon, { size = "24px", style = "", cls = "" } = {}) {
    if (!isCustom(icon)) {
      return `<ha-icon class="${cls}" icon="${escapeAttr(icon)}" style="--mdc-icon-size:${size}; ${style}"></ha-icon>`;
    }
    const def = cache.get(icon);
    const inner = def ? svg(def, "100%") : "";
    return `<span class="${cls} cdc-icon" data-icon="${escapeAttr(icon)}" style="display:inline-flex; width:${size}; height:${size}; ${style}">${inner}</span>`;
  }
  function resolve(icon) {
    if (cache.has(icon)) return Promise.resolve(cache.get(icon));
    if (pending.has(icon)) return pending.get(icon);
    const promise = new Promise((done) => {
      let tries = 0;
      const attempt = () => {
        const pack = packFor(icon);
        if (pack && pack.get) {
          Promise.resolve(pack.get()).then((def) => {
            const ok = def && def.path ? def : null;
            cache.set(icon, ok);
            done(ok);
          }).catch(() => {
            cache.set(icon, null);
            done(null);
          });
          return;
        }
        tries += 1;
        if (tries > 80) {
          pending.delete(icon);
          done(null);
          return;
        }
        setTimeout(attempt, 250);
      };
      attempt();
    });
    pending.set(icon, promise);
    return promise;
  }
  function hydrateIcons(root) {
    root.querySelectorAll("span.cdc-icon[data-icon]").forEach((el) => {
      if (el.firstChild) return;
      const icon = el.getAttribute("data-icon");
      resolve(icon).then((def) => {
        if (!el.isConnected && !el.parentNode) return;
        if (def) el.innerHTML = svg(def, "100%");
        else if (!el.firstChild) el.innerHTML = `<ha-icon icon="mdi:help-circle-outline" style="--mdc-icon-size:100%; width:100%; height:100%;"></ha-icon>`;
      });
    });
  }

  // src/card-kit.js
  var KIT_GRAIN = `url("data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='160' height='160'><filter id='n'><feTurbulence type='fractalNoise' baseFrequency='.9' numOctaves='3' stitchTiles='stitch'/><feColorMatrix values='0 0 0 0 1  0 0 0 0 1  0 0 0 0 1  0 0 0 1 0'/></filter><rect width='100%' height='100%' filter='url(%23n)'/></svg>")`;
  var KIT_ACRYLIC_FILTER = "blur(42px) saturate(115%)";
  var kitAcrylicCss = (sel) => `
  ${sel} { position:relative; isolation:isolate; border:none;
    background:linear-gradient(rgba(255,255,255,.04), rgba(255,255,255,0)), color-mix(in srgb, var(--card-background-color, #1f2128) 76%, transparent);
    -webkit-backdrop-filter:${KIT_ACRYLIC_FILTER}; backdrop-filter:${KIT_ACRYLIC_FILTER}; box-shadow:0 10px 30px rgba(0,0,0,.45); }
  ${sel}::after { content:''; position:absolute; inset:0; border-radius:inherit; pointer-events:none; z-index:-1; background-image:${KIT_GRAIN}; opacity:.08; }`;
  var KIT_CARD_BG = "var(--cd-card-bg, var(--card-background-color))";
  var KIT_COLOR = {
    off: "#8b919c",
    good: "#4caf50",
    fair: "#ffa726",
    poor: "#ff7043",
    bad: "#e53935",
    fan: "#26c6da",
    sleep: "#7e6fd6",
    humidity: "#b388ff",
    blind: "#a1887f",
    cold: "#42a5f5",
    cool: "#26c6da",
    comfy: "#66bb6a",
    warm: "#ffa726",
    hot: "#ef5350"
  };
  var kitEsc = (text) => String(text == null ? "" : text).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[c]);
  var kitCap = (text) => String(text || "").replace(/_/g, " ").replace(/^./, (c) => c.toUpperCase());
  var kitNum = (st) => st && st.state !== "" && !isNaN(Number(st.state)) ? Number(st.state) : null;
  var KIT_HEALTH_CSS = `.ck-stale .ck-row, .ck-stale .ck-dim { opacity:.55; }
.ck-health { display:none; align-items:center; gap:10px; padding:9px 11px; border-radius:12px; background:color-mix(in srgb, #ffa726 18%, transparent); color:#ffd08a; font-size:0.85rem; }
.ck-health small { display:block; color:var(--secondary-text-color); font-size:0.74rem; }
.ck-health button { flex:none; border:none; border-radius:10px; padding:7px 10px; font:inherit; font-size:0.8rem; font-weight:600; background:#ffa726; color:#2a1700; cursor:pointer; }`;
  function kitShell(body, extraCss = "") {
    return `
    <ha-card class="ck-card" style="border:none; box-shadow:0 3px 10px rgba(0,0,0,0.45); border-radius:16px; padding:16px; background:${KIT_CARD_BG}; -webkit-backdrop-filter:var(--cd-card-filter, none); backdrop-filter:var(--cd-card-filter, none); transition:background-color .6s ease; display:flex; flex-direction:column; gap:12px;">
      <style>
        /* Narrow cards (e.g. five side by side): a smaller title, and the
           status word drops to its own line instead of cutting the title. */
        .ck-card { container-type:inline-size; }
        @container (max-width: 260px) { .ck-title { font-size:1.25rem !important; } .ck-word { text-align:left !important; } }
        .ck-row { display:flex; gap:6px; }
        .ck-q { position:relative; overflow:hidden; container-type:inline-size; flex:1 1 0; min-width:0; height:48px; border:none; border-radius:12px; padding:0 6px; cursor:pointer;
          background:rgba(127,127,127,0.14); color:var(--primary-text-color); font:inherit; font-size:13px; font-weight:600;
          display:flex; align-items:center; justify-content:center; gap:6px; transition:background-color .2s, color .2s; }
        .ck-q.ck-col { flex-direction:column; gap:2px; height:56px; font-size:11px; }
        .ck-q.ck-on { color:#fff; }
        .ck-q:disabled { opacity:.4; cursor:default; }
        .ck-q span { white-space:nowrap; overflow:hidden; text-overflow:ellipsis; max-width:100%; }
        @container (max-width: 56px) { .ck-q:not(.ck-col) span.ck-hide { display:none; } }
        .ck-q::after { content:''; position:absolute; inset:0; background:#fff; opacity:0; transition:opacity .15s; pointer-events:none; }
        .ck-q:not(:disabled):hover::after { opacity:.08; }
        .ck-q:focus-visible, .ck-tap:focus-visible { outline:2px solid var(--primary-color); outline-offset:2px; }
        .ck-hold { position:absolute; left:0; top:0; bottom:0; width:0; background:rgba(255,255,255,.22); pointer-events:none; }
        .ck-sub { font-size:0.8rem; color:var(--secondary-text-color); }
        .ck-info { flex:1; min-width:0; display:flex; flex-direction:column; gap:4px; font-size:0.85rem; color:var(--secondary-text-color); }
        .ck-info > span { display:flex; align-items:center; gap:5px; }
        .ck-chip { display:inline-flex; align-items:center; gap:4px; padding:1px 8px; border-radius:999px; font-size:0.72rem; font-weight:600; }
        .ck-bar { height:8px; border-radius:99px; background:rgba(127,127,127,.2); overflow:hidden; }
        .ck-bar > i { display:block; height:100%; border-radius:inherit; transition:width .4s; }
        .ck-tap { cursor:pointer; }
        ${KIT_HEALTH_CSS}
        ${extraCss}
      </style>
      <div class="ck-headrow" style="display:flex; flex-wrap:wrap; align-items:baseline; column-gap:8px; row-gap:2px;">
        <div class="ck-title" style="flex:0 1 auto; max-width:100%; min-width:0; font-size:1.5rem; font-weight:500; line-height:1.2; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; transition:color .6s;"></div>
        <div class="ck-word" style="flex:1 0 auto; max-width:100%; font-size:0.85rem; color:var(--secondary-text-color); white-space:nowrap; overflow:hidden; text-overflow:ellipsis; text-align:right;"></div>
      </div>
      <div class="ck-health" role="status"></div>
      ${body}
    </ha-card>`;
  }
  function kitHead(root, title, word, color, tint = 0) {
    const t = root.querySelector(".ck-title");
    const w = root.querySelector(".ck-word");
    const card = root.querySelector(".ck-card");
    t.textContent = title;
    t.style.color = color;
    w.textContent = word;
    card.style.backgroundColor = tint ? `color-mix(in srgb, ${color} ${tint}%, ${KIT_CARD_BG})` : KIT_CARD_BG;
  }
  function kitGauge(p, color, label, sub, size = 84) {
    const r = size / 2 - 7, cx = size / 2, len = 1.5 * Math.PI * r;
    const fill = Math.max(0, Math.min(1, p || 0));
    const arc = (extra) => `<circle cx="${cx}" cy="${cx}" r="${r}" fill="none" stroke-width="6" stroke-linecap="round" transform="rotate(135 ${cx} ${cx})" ${extra}></circle>`;
    return `<div style="position:relative; width:${size}px; height:${size}px; flex:none;">
      <svg viewBox="0 0 ${size} ${size}" width="${size}" height="${size}" aria-hidden="true" style="display:block;">
        ${arc(`stroke="rgba(127,127,127,0.28)" stroke-dasharray="${len} 9999"`)}
        ${fill > 0 ? arc(`stroke="${color}" stroke-dasharray="${Math.max(0.01, len * fill)} 9999"`) : ""}
      </svg>
      <div style="position:absolute; inset:0; display:flex; flex-direction:column; align-items:center; justify-content:center; text-align:center;">
        <b style="font-size:1.1rem; font-weight:700; font-variant-numeric:tabular-nums; line-height:1.1;">${kitEsc(label)}</b>
        <span style="font-size:0.66rem; color:var(--secondary-text-color); line-height:1.2;">${kitEsc(sub)}</span>
      </div>
    </div>`;
  }
  function kitTiles(box, list, onTap, { column = false, hideNames = false } = {}) {
    const sig = JSON.stringify(list.map((t) => [t.key, t.name, t.icon, t.color, !!t.on, !!t.disabled, !!t.hold]));
    if (box._ckSig === sig) return;
    box._ckSig = sig;
    box.innerHTML = "";
    box.style.display = list.length ? "flex" : "none";
    list.forEach((t) => {
      const b = document.createElement("button");
      b.className = `ck-q${column ? " ck-col" : ""}${t.on ? " ck-on" : ""}`;
      b.disabled = !!t.disabled;
      b.title = t.name;
      b.setAttribute("aria-label", b.title);
      b.setAttribute("aria-pressed", String(!!t.on));
      if (t.on) b.style.background = t.color;
      b.innerHTML = `${t.hold ? '<i class="ck-hold"></i>' : ""}${iconHtml(t.icon, { size: "20px", style: "flex-shrink:0; position:relative;" })}<span class="${hideNames ? "ck-hide" : ""}" style="position:relative;"></span>`;
      b.querySelector("span").textContent = t.name;
      if (t.hold) kitHold(b, () => onTap(t));
      else b.addEventListener("click", () => onTap(t));
      box.appendChild(b);
    });
  }
  function kitHold(button, done) {
    const bar = button.querySelector(".ck-hold");
    let timer = null;
    const stop = () => {
      clearTimeout(timer);
      timer = null;
      bar.style.transition = "width .2s";
      bar.style.width = "0";
    };
    const start = (ev) => {
      if (ev.button > 0) return;
      stop();
      bar.style.transition = "width 1.5s linear";
      requestAnimationFrame(() => bar.style.width = "100%");
      timer = setTimeout(() => {
        stop();
        done();
      }, 1500);
    };
    button.addEventListener("pointerdown", start);
    ["pointerup", "pointerleave", "pointercancel"].forEach((e) => button.addEventListener(e, stop));
    button.addEventListener("keydown", (ev) => {
      if ((ev.key === "Enter" || ev.key === " ") && !timer) start(ev);
    });
    button.addEventListener("keyup", stop);
  }
  function kitNavigate(path, replace = false) {
    if (!path) return;
    if (/^https?:/.test(path)) {
      window.open(path, "_blank", "noopener");
      return;
    }
    history[replace ? "replaceState" : "pushState"](null, "", path);
    window.dispatchEvent(new CustomEvent("location-changed", { detail: { replace } }));
  }
  function kitScrollParent(el) {
    let n = el;
    for (let i = 0; n && i < 40; i += 1) {
      n = n.parentNode || (n.host !== void 0 ? n.host : null);
      if (n && n.nodeType === 1) {
        const oy = getComputedStyle(n).overflowY;
        if ((oy === "auto" || oy === "scroll") && n.scrollHeight > n.clientHeight + 1) return n;
      }
    }
    return document.scrollingElement || document.documentElement;
  }
  function kitScrollTop(sc) {
    return sc === document.scrollingElement || sc === document.documentElement ? window.scrollY : sc.scrollTop;
  }
  var kitGlideFrame = 0;
  function kitGlide(sc, remaining) {
    cancelAnimationFrame(kitGlideFrame);
    const stop = () => {
      cancelAnimationFrame(kitGlideFrame);
      window.removeEventListener("touchstart", stop, true);
      window.removeEventListener("wheel", stop, true);
    };
    window.addEventListener("touchstart", stop, { capture: true, passive: true, once: true });
    window.addEventListener("wheel", stop, { capture: true, passive: true, once: true });
    const t0 = performance.now();
    const step = () => {
      const rem = remaining();
      const late = performance.now() - t0 > 1400;
      if (Math.abs(rem) < 1 || late) {
        if (late && Math.abs(rem) >= 1) sc.scrollBy(0, rem);
        stop();
        return;
      }
      const move = rem * 0.16;
      sc.scrollBy(0, Math.abs(move) < 1 ? Math.sign(rem) : move);
      kitGlideFrame = requestAnimationFrame(step);
    };
    kitGlideFrame = requestAnimationFrame(step);
  }
  function kitMoreInfo(el, entityId) {
    if (!entityId) return;
    el.dispatchEvent(new CustomEvent("hass-more-info", { detail: { entityId }, bubbles: true, composed: true }));
  }
  async function kitHistory(hass, ids, hours = 24) {
    const out = {};
    if (!hass || !hass.callWS || !ids.length) return out;
    const res = await hass.callWS({
      type: "history/history_during_period",
      start_time: new Date(Date.now() - hours * 36e5).toISOString(),
      entity_ids: ids,
      minimal_response: true,
      no_attributes: true,
      significant_changes_only: false
    });
    ids.forEach((id) => {
      out[id] = (res[id] || []).map((p) => [(p.lu || p.lc || 0) * 1e3, Number(p.s)]).filter((p) => p[0] && !isNaN(p[1]) && p[1] !== null);
    });
    return out;
  }
  async function kitStateHistory(hass, ids, hours = 24) {
    const out = {};
    if (!hass || !hass.callWS || !ids.length) return out;
    const res = await hass.callWS({
      type: "history/history_during_period",
      start_time: new Date(Date.now() - hours * 36e5).toISOString(),
      entity_ids: ids,
      minimal_response: true,
      no_attributes: true,
      significant_changes_only: false
    });
    ids.forEach((id) => {
      out[id] = (res[id] || []).map((p) => [(p.lc || p.lu || 0) * 1e3, p.s]).filter((p) => p[0]);
    });
    return out;
  }
  function kitDemoSeries(values, hours = 24) {
    const now = Date.now();
    return values.map((v, i) => [now - hours * 36e5 * (values.length - 1 - i) / (values.length - 1), v]);
  }
  function kitSmooth(pts, from, now, slots = 96) {
    const sorted = (pts || []).filter((p) => p[1] != null && !isNaN(p[1])).sort((a, b) => a[0] - b[0]);
    if (sorted.length < 3) return sorted;
    const step = (now - from) / slots;
    let j = 0, v = null;
    while (j < sorted.length && sorted[j][0] <= from) v = sorted[j++][1];
    const avg2 = [];
    for (let k = 0; k < slots; k++) {
      const a = from + k * step, b = a + step;
      let sum = 0, dur = 0, t = a;
      while (j < sorted.length && sorted[j][0] < b) {
        const tp = sorted[j][0];
        if (v != null) {
          sum += v * (tp - t);
          dur += tp - t;
        }
        t = tp;
        v = sorted[j++][1];
      }
      if (v != null) {
        sum += v * (b - t);
        dur += b - t;
      }
      if (dur > 0) avg2.push([a + step / 2, sum / dur]);
    }
    const w = [1, 2, 3, 4, 3, 2, 1];
    const out = avg2.map((p, i) => {
      let s = 0, n = 0;
      w.forEach((wt, k) => {
        const q = avg2[i + k - 3];
        if (q) {
          s += q[1] * wt;
          n += wt;
        }
      });
      return [p[0], s / n];
    });
    if (out.length) out.push([now, out[out.length - 1][1]]);
    return out;
  }
  function kitPath(xy, curve = true) {
    const f = (p) => `${p[0].toFixed(1)},${p[1].toFixed(1)}`;
    if (!curve || xy.length < 3) return xy.map((p, i) => `${i ? "L" : "M"}${f(p)}`).join(" ");
    let d = `M${f(xy[0])}`;
    for (let i = 0; i < xy.length - 1; i++) {
      const p0 = xy[i - 1] || xy[i], p1 = xy[i], p2 = xy[i + 1], p3 = xy[i + 2] || p2;
      const c1 = [p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6];
      const c2 = [p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6];
      d += ` C${f(c1)} ${f(c2)} ${f(p2)}`;
    }
    return d;
  }
  function kitGraph(series, { hours = 24, height = 48, label = "", meta = null, smooth = true } = {}) {
    const W = 300, H = height, now = Date.now(), from = now - hours * 36e5;
    const x = (t) => (Math.max(from, t) - from) / (now - from) * W;
    let under = "", over = "";
    const scrub = [];
    series.forEach((s) => {
      const raw = (s.pts || []).filter((p) => p[1] != null && !isNaN(p[1]));
      if (s.current != null && !isNaN(s.current)) raw.push([now, Number(s.current)]);
      const pts = smooth ? kitSmooth(raw, from, now) : raw;
      if (pts.length < 2) return;
      const vals = pts.map((p) => p[1]);
      const lo = Math.min(...vals) - (s.pad || 0.3), hi = Math.max(...vals) + (s.pad || 0.3);
      const y = (v) => H - 3 - (v - lo) / (hi - lo || 1) * (H - 6);
      const d = kitPath(pts.map((p) => [x(p[0]), y(p[1])]), smooth);
      if (s.fill) under += `<path d="${d} L${W},${H} L0,${H} Z" fill="${s.color}" fill-opacity="0.16"></path>`;
      over += `<path d="${d}" fill="none" stroke="${s.color}" stroke-width="${s.width || 2}" vector-effect="non-scaling-stroke"></path>`;
      scrub.push({ pts, raw, lo, hi, color: s.color, format: s.format, linear: smooth });
    });
    if (!under && !over) return "";
    if (meta) Object.assign(meta, { from, now, height: H, series: scrub });
    return `<svg viewBox="0 0 ${W} ${H}" preserveAspectRatio="none" style="display:block; width:100%; height:${H}px;" role="img" aria-label="${kitEsc(label)}">${under}${over}</svg>`;
  }
  function kitScrub(svg2, spec) {
    if (!svg2 || !spec || !spec.series || !spec.series.length || svg2.parentNode._ckScrub) return;
    const H = spec.height;
    const wrap = document.createElement("div");
    wrap._ckScrub = true;
    wrap.style.cssText = "position:relative; touch-action:pan-y; user-select:none; -webkit-user-select:none; -webkit-touch-callout:none;";
    svg2.parentNode.insertBefore(wrap, svg2);
    wrap.appendChild(svg2);
    const line = document.createElement("div");
    line.style.cssText = `position:absolute; top:0; height:${H}px; width:1px; background:var(--primary-text-color); opacity:.6; pointer-events:none; display:none;`;
    const tip = document.createElement("div");
    tip.style.cssText = "position:absolute; bottom:calc(100% + 6px); z-index:3; padding:6px 9px; border-radius:10px; background:var(--card-background-color); box-shadow:0 3px 10px rgba(0,0,0,.45); font-size:0.78rem; line-height:1.4; white-space:nowrap; pointer-events:none; display:none; font-variant-numeric:tabular-nums;";
    const dots = spec.series.map((s) => {
      const d = document.createElement("div");
      d.style.cssText = `position:absolute; width:9px; height:9px; margin:-4.5px 0 0 -4.5px; border-radius:50%; background:${s.color}; box-shadow:0 0 0 2px var(--card-background-color); pointer-events:none; display:none;`;
      return d;
    });
    wrap.append(line, ...dots, tip);
    const lerp = (pts, t) => {
      if (!pts.length) return null;
      if (t <= pts[0][0]) return pts[0][1];
      for (let k = 1; k < pts.length; k++) {
        if (t <= pts[k][0]) {
          const [a, va] = pts[k - 1], [b, vb] = pts[k];
          return va + (vb - va) * (t - a) / (b - a || 1);
        }
      }
      return pts[pts.length - 1][1];
    };
    const valueAt = (pts, t) => {
      let v = null;
      for (const p of pts) {
        if (p[0] <= t) v = p[1];
        else break;
      }
      return v == null && pts.length ? pts[0][1] : v;
    };
    const when2 = (t) => {
      const d = new Date(t), today = /* @__PURE__ */ new Date();
      const time = d.toLocaleTimeString(void 0, { hour: "2-digit", minute: "2-digit" });
      return d.toDateString() === today.toDateString() ? time : `${d.toLocaleDateString(void 0, { weekday: "short" })} ${time}`;
    };
    const show = (clientX) => {
      const rect = wrap.getBoundingClientRect();
      const f = Math.max(0, Math.min(1, (clientX - rect.left) / (rect.width || 1)));
      const t = spec.from + f * (spec.now - spec.from);
      const left = f * rect.width;
      line.style.left = `${left}px`;
      line.style.display = "block";
      const rows = [];
      spec.series.forEach((s, i) => {
        const v = s.raw ? valueAt(s.raw, t) : valueAt(s.pts, t);
        const yv = s.linear ? lerp(s.pts, t) : v;
        const dot = dots[i];
        if (v == null || yv == null) {
          dot.style.display = "none";
          return;
        }
        const colour = s.colourOf ? s.colourOf(v) : s.color;
        dot.style.background = colour;
        dot.style.left = `${left}px`;
        dot.style.top = `${H - 3 - (yv - s.lo) / (s.hi - s.lo || 1) * (H - 6)}px`;
        dot.style.display = "block";
        rows.push(`<div style="color:${colour};">\u25CF ${kitEsc(s.format ? s.format(v) : Number(v).toFixed(1))}</div>`);
      });
      tip.innerHTML = `<div style="color:var(--secondary-text-color);">${when2(t)}</div>${rows.join("")}`;
      tip.style.display = "block";
      const w = tip.offsetWidth;
      tip.style.left = `${Math.max(0, Math.min(rect.width - w, left - w / 2))}px`;
    };
    const hide = () => {
      [line, tip, ...dots].forEach((el) => el.style.display = "none");
    };
    let active = false, used = false, timer = null, sx = 0, sy = 0;
    wrap.addEventListener("pointerenter", (ev) => ev.pointerType === "mouse" && show(ev.clientX));
    wrap.addEventListener("pointermove", (ev) => {
      if (ev.pointerType === "mouse") return show(ev.clientX);
      if (active) return show(ev.clientX);
      if (timer && (Math.abs(ev.clientX - sx) > 10 || Math.abs(ev.clientY - sy) > 10)) {
        clearTimeout(timer);
        timer = null;
      }
    });
    wrap.addEventListener("pointerleave", (ev) => ev.pointerType === "mouse" && hide());
    wrap.addEventListener("pointerdown", (ev) => {
      if (ev.pointerType === "mouse") return;
      sx = ev.clientX;
      sy = ev.clientY;
      clearTimeout(timer);
      timer = setTimeout(() => {
        timer = null;
        active = true;
        used = true;
        show(sx);
      }, 300);
    });
    const end = () => {
      clearTimeout(timer);
      timer = null;
      if (active) {
        active = false;
        hide();
      }
    };
    ["pointerup", "pointercancel"].forEach((e) => wrap.addEventListener(e, end));
    wrap.addEventListener("touchmove", (ev) => {
      if (active && ev.cancelable) ev.preventDefault();
      if (active && ev.touches[0]) show(ev.touches[0].clientX);
    }, { passive: false });
    wrap.addEventListener("touchend", end);
    wrap.addEventListener("contextmenu", (ev) => (active || used) && ev.preventDefault());
    wrap.addEventListener("click", (ev) => {
      if (used) {
        used = false;
        ev.stopPropagation();
        ev.preventDefault();
      }
    }, true);
  }
  function kitRange(pts, current, digits, unit) {
    const vals = (pts || []).map((p) => p[1]).filter((v) => v != null && !isNaN(v));
    if (current != null && !isNaN(current)) vals.push(Number(current));
    if (!vals.length) return "";
    const f = (v) => Number(v).toFixed(digits);
    return `${f(Math.min(...vals))}\u2013${f(Math.max(...vals))}${unit}`;
  }
  var KitHistory = class {
    constructor(owner, ids, hours, loader = kitHistory) {
      this.owner = owner;
      this.ids = ids;
      this.hours = hours;
      this.loader = loader;
      this.data = null;
      this.at = 0;
      this.loading = false;
    }
    due() {
      return !this.loading && Date.now() - this.at > 10 * 6e4;
    }
    async load(hass) {
      if (!this.due()) return;
      this.loading = true;
      try {
        this.data = await this.loader(hass, this.ids.filter(Boolean), this.hours);
      } catch (err) {
        this.data = this.data || {};
      }
      this.at = Date.now();
      this.loading = false;
      this.owner._render();
    }
  };
  var KitPending = class {
    constructor(owner) {
      this.owner = owner;
      this.want = null;
    }
    set(want) {
      this.want = want;
      clearTimeout(this.timer);
      this.timer = setTimeout(() => {
        this.want = null;
        this.owner._render();
      }, 8e3);
    }
    apply(st) {
      const w = this.want;
      if (!w || !st) return st;
      const a = st.attributes || {};
      const same2 = (x, y) => typeof x === "number" && typeof y === "number" ? Math.abs(x - y) <= 2 : x === y;
      const attrs = w.attrs || {};
      if (st.state === w.state && Object.keys(attrs).every((k) => same2(a[k], attrs[k]))) {
        this.want = null;
        clearTimeout(this.timer);
        return st;
      }
      return { ...st, state: w.state, attributes: { ...a, ...attrs } };
    }
  };
  var HEALTH_SENSOR = "sensor.church_drive_device_health";
  function kitHealthOf(hass, entityId) {
    const s = hass && entityId && hass.states[HEALTH_SENSOR];
    const d = s && s.attributes.devices && s.attributes.devices[entityId];
    return d && d.status !== "ok" ? d : null;
  }
  var kitClock = (iso) => iso ? new Date(iso).toLocaleTimeString(void 0, { hour: "2-digit", minute: "2-digit" }) : "";
  function kitRealText(real) {
    if (!real) return "";
    let what = kitCap(real.state);
    if (real.preset_mode) {
      const m = /^speed[ _-]?(\d+)$/i.exec(real.preset_mode);
      what = m ? `Speed ${m[1]}` : kitCap(real.preset_mode);
    } else if (real.temperature != null && real.state !== "off") {
      what = `${kitCap(real.state)} ${real.temperature}\xB0`;
    }
    return `${what} at ${kitClock(real.at)}`;
  }
  function kitHealthBanner(root, hass, entityId, demo) {
    const box = root.querySelector(".ck-health");
    const card = root.querySelector(".ck-card") || root.querySelector("ha-card");
    if (!box) return null;
    const d = demo ? null : kitHealthOf(hass, entityId);
    card.classList.toggle("ck-stale", !!d);
    const sig = d ? JSON.stringify([d.reason, d.since, d.last_real, d.fixes]) : "";
    if (box._sig === sig) return d;
    box._sig = sig;
    box.style.display = d ? "flex" : "none";
    if (!d) {
      box.innerHTML = "";
      return null;
    }
    const last = (d.fixes || []).slice(-1)[0];
    box.innerHTML = `${iconHtml("mdi:lan-disconnect", { size: "22px", style: "flex:none;" })}
    <div style="flex:1; min-width:0; line-height:1.35;">Not responding since ${kitClock(d.since)}
      <small></small></div>
    <button type="button">Fix now</button>`;
    box.querySelector("small").textContent = [d.reason, d.last_real ? `Last real: ${kitRealText(d.last_real)}` : "", last ? `Fixing: ${last.replace(/^\d\d:\d\d /, "")}` : ""].filter(Boolean).join(" \xB7 ");
    box.querySelector("button").addEventListener("click", () => hass.callService("church_drive", "health_fix", { entity_id: entityId, action: entityId.startsWith("fan.") ? "nudge" : "resync" }));
    return d;
  }
  var KIT_CPT_CSS = `
  .ck-cpt { display:flex; flex-direction:column; gap:6px; border:none; box-shadow:0 3px 10px rgba(0,0,0,.45); border-radius:14px; padding:8px 10px; background:${KIT_CARD_BG}; -webkit-backdrop-filter:var(--cd-card-filter, none); backdrop-filter:var(--cd-card-filter, none); }
  .ck-cpt-row { display:flex; align-items:center; gap:8px; min-height:32px; }
  .ck-cpt-name { font-weight:600; font-size:0.92rem; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; cursor:pointer; min-width:0; flex:0 1 auto; }
  .ck-cpt-val { font-weight:700; font-size:1.05rem; font-variant-numeric:tabular-nums; white-space:nowrap; flex:none; }
  .ck-cpt-st { flex:1 1 0; min-width:0; color:var(--secondary-text-color); font-size:0.76rem; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
  .ck-cpt-btns { display:flex; gap:4px; flex:none; }
  .ck-cpt-b { position:relative; min-width:32px; height:30px; padding:0 6px; border:none; border-radius:9px; background:rgba(127,127,127,.16); color:var(--primary-text-color); font:inherit; font-size:0.78rem; font-weight:700; display:flex; align-items:center; justify-content:center; gap:3px; cursor:pointer; }
  .ck-cpt-b.ck-on { color:#fff; }
  .ck-cpt-b:focus-visible, .ck-cpt-name:focus-visible { outline:2px solid var(--primary-color); outline-offset:2px; }
  .ck-cpt-chips { display:flex; flex-wrap:wrap; gap:5px; }
  .ck-cpt-chip { display:inline-flex; align-items:center; gap:5px; padding:3px 9px; border-radius:999px; background:rgba(127,127,127,.14); font-size:0.78rem; font-weight:600; font-variant-numeric:tabular-nums; }
  .ck-cpt-chip i { width:8px; height:8px; border-radius:50%; flex:none; }`;
  function kitCompact(root, spec) {
    const sig = JSON.stringify(spec, (k, v) => typeof v === "function" ? void 0 : v);
    if (root._cptSig === sig && root.querySelector(".ck-cpt")) return;
    root._cptSig = sig;
    root.innerHTML = `<style>${KIT_CPT_CSS}</style><ha-card class="ck-cpt"><div class="ck-cpt-row">
      <span class="ck-cpt-name" role="button" tabindex="0"></span>
      ${spec.value != null && spec.value !== "" ? '<b class="ck-cpt-val"></b>' : ""}
      <span class="ck-cpt-st"></span>
      <div class="ck-cpt-btns"></div>
    </div>${(spec.chips || []).length ? '<div class="ck-cpt-chips"></div>' : ""}</ha-card>`;
    const name = root.querySelector(".ck-cpt-name");
    name.textContent = spec.name || "";
    name.style.color = spec.color || "var(--primary-text-color)";
    const open = () => root.dispatchEvent(new CustomEvent("cd-expand", { bubbles: true, composed: true }));
    name.addEventListener("click", open);
    name.addEventListener("keydown", (ev) => (ev.key === "Enter" || ev.key === " ") && open());
    const val = root.querySelector(".ck-cpt-val");
    if (val) {
      val.textContent = spec.value;
      val.style.color = spec.valueColor || "var(--primary-text-color)";
    }
    root.querySelector(".ck-cpt-st").textContent = spec.status || "";
    const box = root.querySelector(".ck-cpt-btns");
    (spec.buttons || []).forEach((b) => {
      const el = document.createElement("button");
      el.type = "button";
      el.className = `ck-cpt-b${b.on ? " ck-on" : ""}`;
      el.title = b.title || b.label || b.key;
      el.setAttribute("aria-label", el.title);
      el.setAttribute("aria-pressed", String(!!b.on));
      if (b.on) el.style.background = b.color || "var(--primary-color)";
      el.innerHTML = b.icon ? iconHtml(b.icon, { size: "17px", style: "flex:none;" }) : "";
      if (b.label) el.append(document.createTextNode(b.label));
      el.addEventListener("click", (ev) => {
        ev.stopPropagation();
        if (spec.onButton) spec.onButton(b);
      });
      box.appendChild(el);
    });
    const chips = root.querySelector(".ck-cpt-chips");
    (spec.chips || []).forEach((c) => {
      const el = document.createElement("span");
      el.className = "ck-cpt-chip";
      el.innerHTML = `<i style="background:${c.color || "var(--secondary-text-color)"};"></i><span></span>`;
      el.querySelector("span").textContent = c.label;
      chips.appendChild(el);
    });
    hydrateIcons(root);
  }
  function kitCompactable(Cls, rebuild = (card) => {
    card._built = false;
  }) {
    Object.defineProperty(Cls.prototype, "compact", {
      configurable: true,
      get() {
        return !!this._compact;
      },
      set(v) {
        v = !!v;
        if (v === !!this._compact) return;
        this._compact = v;
        this._cptSig = null;
        rebuild(this);
        if (!v) this.innerHTML = "";
        const hass = this._lastInput || this._hass;
        if (hass) this.hass = hass;
      }
    });
    Cls.prototype.supportsCompact = true;
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
  var APC_STATES = {
    disarmed: { label: "Disarmed", icon: "mdi:shield-off-outline", color: "var(--success-color, #43a047)" },
    armed_home: { label: "Armed Home", icon: "mdi:shield-home", color: "#2196f3" },
    armed_away: { label: "Armed Away", icon: "mdi:shield-lock", color: "var(--error-color, #db4437)" },
    armed_night: { label: "Armed Night", icon: "mdi:shield-moon", color: "#7e57c2" },
    arming: { label: "Arming", icon: "mdi:shield-sync", color: "#ff9800" },
    pending: { label: "Entry Delay", icon: "mdi:shield-sync", color: "#ff5722" },
    triggered: { label: "Triggered!", icon: "mdi:shield-alert", color: "var(--error-color, #db4437)" }
  };
  var APC_MODE_NAMES = { armed_home: "Home", armed_away: "Away", armed_night: "Night" };
  var APC_RING_R = 38;
  var APC_RING_LEN = 2 * Math.PI * APC_RING_R;
  var AlarmPanelCard = class extends HTMLElement {
    setConfig(config) {
      if (!config.entity && !config.demo) throw new Error("entity required (or set demo: true)");
      this.config = config;
      this._built = false;
      this._countdownTimer = null;
      this._remaining = 0;
      this._total = 0;
    }
    disconnectedCallback() {
      if (this._countdownTimer) clearInterval(this._countdownTimer);
      this._countdownTimer = null;
    }
    _state(hass) {
      if (!this.config.demo) return hass.states[this.config.entity];
      const demoTime = this.config.demo_time ? String(this.config.demo_time).replace(" ", "T") : (/* @__PURE__ */ new Date()).toISOString();
      return {
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
    }
    _build() {
      this.innerHTML = `
      <ha-card class="apc-card" style="border:none; box-shadow:0 3px 10px rgba(0,0,0,0.45); border-radius:16px; overflow:hidden; padding:16px; background:var(--card-background-color); transition:background-color .8s ease;">
        <style>
          .apc-btn { position:relative; overflow:hidden; flex:1 1 0; min-width:0; height:56px; border:none; border-radius:12px; cursor:pointer;
            display:flex; flex-direction:column; align-items:center; justify-content:center; gap:2px; padding:0 4px;
            background:rgba(127,127,127,0.14); color:var(--primary-text-color); font:inherit; font-size:11px; font-weight:600; }
          .apc-btn.apc-on { color:#fff; }
          .apc-btn span { position:relative; z-index:1; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; max-width:100%; }
          .apc-btn ha-icon { position:relative; z-index:1; --mdc-icon-size:22px; }
          .apc-btn::after { content:''; position:absolute; inset:0; background:var(--btn-tint, #fff); opacity:0; transition:opacity .15s ease; pointer-events:none; }
          .apc-btn:hover::after { opacity:0.18; }
          .apc-btn:focus-visible { outline:2px solid var(--primary-color); outline-offset:2px; }
        </style>
        <div class="apc-title" style="font-size:1.5rem; font-weight:500; line-height:1.2; padding:0 0 10px 0;"></div>
        <div style="display:flex; align-items:center; gap:14px;">
          <div style="position:relative; width:84px; height:84px; flex:none;">
            <svg viewBox="0 0 84 84" style="width:84px; height:84px; transform:rotate(-90deg);" aria-hidden="true">
              <circle cx="42" cy="42" r="${APC_RING_R}" fill="none" stroke="rgba(127,127,127,0.25)" stroke-width="6"></circle>
              <circle class="apc-ring" cx="42" cy="42" r="${APC_RING_R}" fill="none" stroke-width="6" stroke-linecap="round"
                stroke-dasharray="${APC_RING_LEN}" stroke-dashoffset="0" style="transition:stroke-dashoffset 1s linear;"></circle>
            </svg>
            <ha-icon class="apc-icon" style="position:absolute; inset:0; margin:auto; width:38px; height:38px; --mdc-icon-size:38px;"></ha-icon>
          </div>
          <div style="flex:1; min-width:0;">
            <div class="apc-line1" style="font-size:0.95rem; color:var(--primary-text-color);"></div>
            <div class="apc-line2" style="font-size:0.85rem; color:var(--secondary-text-color);"></div>
          </div>
          <div class="apc-countdown" style="flex:none; font-size:2rem; font-weight:700; font-variant-numeric:tabular-nums; visibility:hidden;">0:00</div>
        </div>
        <div class="apc-buttons" style="display:flex; gap:8px; margin-top:12px;"></div>
      </ha-card>`;
      const q = (sel) => this.querySelector(sel);
      this._card = q(".apc-card");
      this._title = q(".apc-title");
      this._ring = q(".apc-ring");
      this._icon = q(".apc-icon");
      this._line1 = q(".apc-line1");
      this._line2 = q(".apc-line2");
      this._countdown = q(".apc-countdown");
      this._buttons = q(".apc-buttons");
      this._built = true;
    }
    set hass(hass) {
      this._hass = hass;
      const st = this._state(hass);
      if (!st) return;
      if (this._compact) return kitCompact(this, this._compactSpec(st));
      if (!this._built) this._build();
      const info = APC_STATES[st.state] || { label: st.state, icon: "mdi:shield-outline", color: "#9e9e9e" };
      const inDelay = st.state === "arming" || st.state === "pending";
      const target = APC_MODE_NAMES[st.attributes.targetState];
      this._color = info.color;
      this._stateName = st.state;
      const label = st.state === "arming" && target ? `Arming ${target}` : info.label;
      this._title.textContent = label + (this.config.demo ? " (demo)" : "");
      this._title.style.color = info.color;
      this._icon.setAttribute("icon", info.icon);
      this._icon.style.color = info.color;
      this._ring.style.stroke = info.color;
      this._countdown.style.color = info.color;
      const fmt = (iso) => new Date(iso).toLocaleString("en-GB", { day: "2-digit", month: "short", hour: "2-digit", minute: "2-digit" });
      const a = st.attributes;
      let line1 = "";
      let line2 = "";
      if (st.state === "arming") [line1, line2] = ["Leave now", "until armed"];
      else if (st.state === "pending") [line1, line2] = ["Disarm now", "until the alarm sounds"];
      else if (st.state === "triggered") [line1, line2] = ["Alarm sounding", "Disarm to stop it"];
      else if (st.state === "disarmed" && a.lastDisarmedBy && a.lastDisarmedTime) [line1, line2] = [`Disarmed by ${a.lastDisarmedBy}`, fmt(a.lastDisarmedTime)];
      else if (a.lastArmedBy && a.lastArmedTime) [line1, line2] = [`Armed by ${a.lastArmedBy}`, fmt(a.lastArmedTime)];
      this._line1.textContent = line1;
      this._line2.textContent = line2;
      const secsLeft = st.state === "pending" ? a.entrySecondsLeft || 0 : st.state === "arming" ? a.exitSecondsLeft || 0 : 0;
      this._syncCountdown(inDelay ? secsLeft : 0, st.state, st.last_changed);
      const activeKey = inDelay || st.state === "triggered" ? a.targetState : st.state;
      this._renderButtons(a.supported_features || 0, activeKey, info.color);
    }
    // One row: the state (with the seconds left in a delay) and the arm/disarm
    // buttons as icons.
    _compactSpec(st) {
      const a = st.attributes;
      const info = APC_STATES[st.state] || { label: st.state, icon: "mdi:shield-outline", color: "#9e9e9e" };
      const target = APC_MODE_NAMES[a.targetState];
      const inDelay = st.state === "arming" || st.state === "pending";
      const secs = st.state === "pending" ? a.entrySecondsLeft || 0 : st.state === "arming" ? a.exitSecondsLeft || 0 : 0;
      const feats = a.supported_features || 0;
      const activeKey = inDelay || st.state === "triggered" ? a.targetState : st.state;
      const actions = [
        { key: "disarmed", icon: "mdi:shield-off-outline", title: "Disarm", service: "alarm_disarm", show: true },
        { key: "armed_home", icon: "mdi:shield-home", title: "Arm Home", service: "alarm_arm_home", show: (feats & 1) !== 0 },
        { key: "armed_away", icon: "mdi:shield-lock", title: "Arm Away", service: "alarm_arm_away", show: (feats & 2) !== 0 },
        { key: "armed_night", icon: "mdi:shield-moon", title: "Arm Night", service: "alarm_arm_night", show: (feats & 4) !== 0 }
      ].filter((b) => b.show);
      return {
        name: (st.state === "arming" && target ? `Arming ${target}` : info.label) + (this.config.demo ? " (demo)" : ""),
        color: info.color,
        value: inDelay && secs ? `${Math.floor(secs / 60)}:${String(secs % 60).padStart(2, "0")}` : "",
        valueColor: info.color,
        status: st.state === "arming" ? "Leave now" : st.state === "pending" ? "Disarm now" : st.state === "triggered" ? "Alarm sounding" : "",
        buttons: actions.map((b) => ({ key: b.key, icon: b.icon, title: b.title, on: activeKey === b.key, color: info.color, service: b.service })),
        onButton: (b) => {
          if (!this.config.demo) this._hass.callService("alarm_control_panel", b.service, {}, { entity_id: this.config.entity });
        }
      };
    }
    _renderButtons(feats, activeKey, activeColor) {
      const actions = [
        { key: "disarmed", icon: "mdi:shield-off-outline", title: "Disarm", service: "alarm_disarm", show: true },
        { key: "armed_home", icon: "mdi:shield-home", title: "Home", service: "alarm_arm_home", show: (feats & 1) !== 0 },
        { key: "armed_away", icon: "mdi:shield-lock", title: "Away", service: "alarm_arm_away", show: (feats & 2) !== 0 },
        { key: "armed_night", icon: "mdi:shield-moon", title: "Night", service: "alarm_arm_night", show: (feats & 4) !== 0 }
      ].filter((b) => b.show);
      const sig = JSON.stringify([actions.map((b) => b.key), activeKey, activeColor, !!this.config.demo]);
      if (sig === this._buttonsSig) return;
      this._buttonsSig = sig;
      this._buttons.innerHTML = "";
      actions.forEach((b) => {
        const active = activeKey === b.key;
        const btn = document.createElement("button");
        btn.className = `apc-btn${active ? " apc-on" : ""}`;
        btn.title = b.key === "disarmed" ? "Disarm" : `Arm ${b.title}`;
        btn.setAttribute("aria-label", btn.title);
        btn.style.setProperty("--btn-tint", (APC_STATES[b.key] || {}).color || "#fff");
        if (active) btn.style.background = activeColor;
        btn.innerHTML = `<ha-icon icon="${b.icon}"></ha-icon><span>${b.title}</span>`;
        if (this.config.demo) {
          btn.style.opacity = "0.6";
          btn.style.cursor = "default";
        } else {
          btn.addEventListener(
            "click",
            () => this._hass.callService("alarm_control_panel", b.service, {}, { entity_id: this.config.entity })
          );
        }
        this._buttons.appendChild(btn);
      });
    }
    _syncCountdown(secsLeft, state, since) {
      const active = secsLeft > 0;
      if (!active) {
        if (this._countdownTimer) clearInterval(this._countdownTimer);
        this._countdownTimer = null;
        this._remaining = 0;
        this._total = 0;
        this._reported = null;
        this._delayState = null;
        this._paint();
        return;
      }
      if (state !== this._delayState) {
        this._total = 0;
        this._reported = null;
      }
      this._delayState = state;
      const began = since ? Date.parse(since) : NaN;
      const elapsed = isNaN(began) ? 0 : Math.max(0, (Date.now() - began) / 1e3);
      const fromStart = elapsed < 600 ? Math.round(secsLeft + elapsed) : 0;
      this._total = Math.max(this._total, secsLeft, fromStart);
      if (secsLeft !== this._reported) {
        this._reported = secsLeft;
        this._remaining = secsLeft;
      }
      this._paint();
      if (this._countdownTimer) return;
      this._countdownTimer = setInterval(() => {
        this._remaining = this.config.demo && this._remaining <= 1 ? this._total : Math.max(0, this._remaining - 1);
        this._paint();
        if (this._remaining <= 0) {
          clearInterval(this._countdownTimer);
          this._countdownTimer = null;
        }
      }, 1e3);
    }
    // Timer, ring and background for the time left.
    _paint() {
      const counting = this._remaining > 0 && this._total > 0;
      const frac = counting ? this._remaining / this._total : 1;
      const m = Math.floor(this._remaining / 60);
      const s = this._remaining % 60;
      this._countdown.textContent = `${m}:${String(s).padStart(2, "0")}`;
      this._countdown.style.visibility = counting ? "visible" : "hidden";
      this._ring.style.strokeDashoffset = String(APC_RING_LEN * (1 - frac));
      let tint = 0;
      if (this._stateName === "triggered") tint = 32;
      else if (counting) tint = Math.round(6 + 26 * (1 - frac));
      this._card.style.backgroundColor = tint ? `color-mix(in srgb, ${this._color} ${tint}%, var(--card-background-color))` : "var(--card-background-color)";
    }
    // Same size in every state: title, ring row and buttons.
    getCardSize() {
      return 4;
    }
    // Sections-view defaults; the editor's Layout tab can override them.
    getGridOptions() {
      return { columns: 12, min_columns: 6, rows: "auto" };
    }
    static getConfigElement() {
      return document.createElement(`alarm-panel-card-editor${SUFFIX}`);
    }
    // Pre-fill the card picker with the first real alarm entity.
    static getStubConfig(hass) {
      const first = hass && Object.keys(hass.states).find((id) => id.startsWith("alarm_control_panel."));
      return first ? { entity: first } : { demo: true };
    }
  };
  kitCompactable(AlarmPanelCard, (card) => {
    card._built = false;
    card._buttonsSig = null;
    if (card._countdownTimer) clearInterval(card._countdownTimer);
    card._countdownTimer = null;
    card._delayState = null;
    card._reported = null;
  });
  function registerAlarmPanelCard() {
    if (!customElements.get(`alarm-panel-card-editor${SUFFIX}`)) {
      customElements.define(`alarm-panel-card-editor${SUFFIX}`, AlarmPanelCardEditor);
    }
    if (!customElements.get(`alarm-panel-card${SUFFIX}`)) {
      customElements.define(`alarm-panel-card${SUFFIX}`, AlarmPanelCard);
    }
    window.customCards = window.customCards || [];
    window.customCards.push({
      type: `alarm-panel-card${SUFFIX}`,
      name: `Alarm Panel Card${LABEL}`,
      description: "Alarm status, entry/exit countdown, and arm/disarm controls",
      preview: true,
      documentationURL: "https://github.com/J45PER/church-drive-cards#readme"
    });
  }

  // src/scene-style.js
  var PALETTES = {
    bright: ["#fff6e0", "#ffd98a"],
    "cool bright": ["#f4f9ff", "#bcd8ff"],
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
    "city blue": ["#0b1d51", "#2f6fd6", "#89c2ff"],
    aqua: ["#00c9d6", "#0077b6", "#90e0ef"],
    "dreamy dusk": ["#6a4c93", "#f28482", "#ffb4a2"],
    "emerald isle": ["#1b7f6b", "#52b788", "#b7e4c7"],
    magneto: ["#3a0ca3", "#4361ee", "#f72585"],
    meriete: ["#ff9e7a", "#c86b98", "#5f4b8b"],
    motown: ["#7b2cbf", "#ff6d00", "#ffd60a"],
    "ruby glow": ["#9b111e", "#e0115f", "#ff6f61"],
    "witching hour": ["#240046", "#5a189a", "#ff7900"]
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
  function sceneKey(name) {
    return String(name || "").toLowerCase().replace(/\s+\d+$/, "").trim();
  }
  function builtInSceneNames() {
    return Object.keys(PALETTES);
  }
  function toHex(c) {
    if (Array.isArray(c)) return "#" + c.map((v) => Math.max(0, Math.min(255, v | 0)).toString(16).padStart(2, "0")).join("");
    return c;
  }
  function hslHex(h, s, l) {
    const a = s * Math.min(l, 1 - l);
    const f = (n) => {
      const k = (n + h / 30) % 12;
      return Math.round(255 * (l - a * Math.max(-1, Math.min(k - 3, 9 - k, 1))));
    };
    return toHex([f(0), f(8), f(4)]);
  }
  var central = {};
  var STYLES_EVENT = "church-drive-scene-styles";
  function setSceneStyles(list) {
    const next = {};
    (list || []).forEach((s) => {
      if (s && s.scene) next[sceneKey(s.scene)] = s;
    });
    if (JSON.stringify(next) === JSON.stringify(central)) return;
    central = next;
    window.dispatchEvent(new CustomEvent(STYLES_EVENT));
  }
  function centralSceneStyle(name) {
    return central[sceneKey(name)] || null;
  }
  function onSceneStylesChanged(callback) {
    window.addEventListener(STYLES_EVENT, callback);
    return () => window.removeEventListener(STYLES_EVENT, callback);
  }
  var STYLES_DASHBOARD = "design-presets";
  var loading = null;
  function findStyleCards(node, found) {
    if (Array.isArray(node)) node.forEach((n) => findStyleCards(n, found));
    else if (node && typeof node === "object") {
      if (typeof node.type === "string" && /^custom:scene-styles-card(-beta)?$/.test(node.type)) found.push(node);
      Object.values(node).forEach((v) => findStyleCards(v, found));
    }
    return found;
  }
  function loadSceneStyles(hass) {
    if (loading || !hass || !hass.callWS) return loading;
    loading = hass.callWS({ type: "lovelace/config", url_path: STYLES_DASHBOARD }).then((config) => {
      const cards = findStyleCards(config, []);
      const mine = cards.find((c) => c.type === `custom:scene-styles-card${SUFFIX}`) || cards[0];
      if (mine) setSceneStyles(mine.styles);
    }).catch(() => {
    });
    return loading;
  }
  function scenePalette(name, fallback) {
    const style = centralSceneStyle(name);
    const custom = style ? [style.colour_1, style.colour_2, style.colour_3].filter(Boolean).map(toHex) : [];
    if (custom.length) return custom.length === 1 ? [custom[0], custom[0]] : custom;
    if (fallback && fallback.length) return fallback.length === 1 ? [fallback[0], fallback[0]] : fallback;
    const key = sceneKey(name);
    if (PALETTES[key]) return PALETTES[key];
    let h = 0;
    for (const ch of key) h = (h * 31 + ch.charCodeAt(0)) % 360;
    return [hslHex(h, 0.7, 0.55), hslHex((h + 50) % 360, 0.7, 0.35)];
  }
  function sceneBackground(name, image, fallback) {
    const style = centralSceneStyle(name);
    const picture = image || style && style.image;
    if (picture) return `center / cover no-repeat url("${picture}")`;
    return `linear-gradient(135deg, ${scenePalette(name, fallback).join(", ")})`;
  }
  function sceneIcon(name, isDynamic) {
    const style = centralSceneStyle(name);
    if (style && style.icon) return style.icon;
    const key = sceneKey(name);
    for (const [re, icon] of ICONS) if (re.test(key)) return icon;
    return isDynamic ? "mdi:animation-play-outline" : "mdi:palette-outline";
  }

  // src/universal-scenes.js
  var EVENT = "church-drive-universal-scenes";
  var UNIVERSAL_PREFIX = "universal:";
  var library = [];
  var loading2 = null;
  function loadUniversalScenes(hass, force = false) {
    if (force) loading2 = null;
    if (loading2 || !hass || !hass.callWS) return loading2;
    loading2 = hass.callWS({ type: "church_drive/library" }).then((res) => {
      library = res && res.scenes || [];
      window.dispatchEvent(new CustomEvent(EVENT));
    }).catch(() => {
    });
    return loading2;
  }
  function universalScenes() {
    return library;
  }
  function universalRef(key, target) {
    return `${UNIVERSAL_PREFIX}${key}${target ? `@${target}` : ""}`;
  }
  function universalTarget(ref) {
    const at = String(ref || "").indexOf("@");
    return at === -1 ? null : ref.slice(at + 1);
  }
  function universalScene(ref) {
    let key = String(ref || "").startsWith(UNIVERSAL_PREFIX) ? ref.slice(UNIVERSAL_PREFIX.length) : String(ref || "");
    if (key.includes("@")) key = key.slice(0, key.indexOf("@"));
    const wanted = String(key || "").toLowerCase();
    return library.find((s) => s.key === wanted || s.name.toLowerCase() === wanted) || null;
  }
  function onUniversalScenesChanged(callback) {
    window.addEventListener(EVENT, callback);
    return () => window.removeEventListener(EVENT, callback);
  }
  function universalTurnOnData(scene) {
    const data = { brightness: scene.brightness };
    if (scene.color_temp_kelvin) data.color_temp_kelvin = scene.color_temp_kelvin;
    if (scene.xy_color) data.xy_color = scene.xy_color;
    return data;
  }
  var COLOUR_MODES = ["xy", "hs", "rgb", "rgbw", "rgbww"];
  function spreadColours(colors, count) {
    if (count <= colors.length) {
      const step = colors.length / Math.max(count, 1);
      return Array.from({ length: count }, (_, i) => colors[Math.floor(i * step)]);
    }
    return Array.from({ length: count }, (_, i) => {
      const pos = count > 1 ? i * (colors.length - 1) / (count - 1) : 0;
      const a = Math.min(Math.floor(pos), colors.length - 1);
      const b = Math.min(a + 1, colors.length - 1);
      const t = pos - a;
      return [0, 1].map((k) => Math.round((colors[a][k] + (colors[b][k] - colors[a][k]) * t) * 1e4) / 1e4);
    });
  }
  function universalDealColours(scene, lightIds) {
    const ids = [...lightIds].sort();
    const colours = spreadColours(scene.colors, ids.length);
    return ids.map((id, i) => ({
      entity_id: id,
      xy_color: colours[i],
      brightness: scene.brightness
    }));
  }
  function universalScenePlaying(hass, lightIds) {
    return lightIds.some((id) => hass.states[id] && hass.states[id].state === "on" && hass.states[id].attributes.dynamics === "dynamic_palette");
  }
  function universalSceneActive(hass, scene, lightIds) {
    const lit = lightIds.map((id) => hass.states[id]).filter((st) => st && st.state === "on");
    if (!lit.length) return false;
    return lit.every((st) => {
      const a = st.attributes;
      const modes = a.supported_color_modes || [];
      const animating = scene.kind === "colour" && a.dynamics === "dynamic_palette";
      if (!animating && a.brightness != null && Math.abs(a.brightness - scene.brightness) > 4) return false;
      if (scene.color_temp_kelvin && modes.includes("color_temp")) {
        if (a.color_mode !== "color_temp" || a.color_temp_kelvin == null) return false;
        return Math.abs(a.color_temp_kelvin - scene.color_temp_kelvin) <= scene.color_temp_kelvin * 0.03;
      }
      const colours = scene.kind === "colour" ? scene.colors : scene.xy_color ? [scene.xy_color] : null;
      if (colours && modes.some((m) => COLOUR_MODES.includes(m))) {
        if (scene.kind === "colour" && a.dynamics === "dynamic_palette") return true;
        const xy = a.xy_color;
        return !!xy && colours.some((c) => Math.abs(xy[0] - c[0]) < 0.06 && Math.abs(xy[1] - c[1]) < 0.06);
      }
      return true;
    });
  }

  // src/demo-home.js
  var COLOUR = ["color_temp", "xy"];
  var WHITE = {
    nightlight: [2e3, 26],
    rest: [2200, 89],
    relax: [2700, 143],
    read: [4300, 255],
    concentrate: [5500, 255],
    energise: [6300, 255],
    bright: [4e3, 255]
  };
  var DEMO_ROOMS = {
    living_room: {
      area: "demo_living_room",
      areaName: "Living Room",
      lights: [
        { id: "demo_ceiling", name: "Ceiling Light", modes: COLOUR },
        { id: "demo_tv_lightstrip", name: "TV Lightstrip", modes: COLOUR },
        { id: "demo_table_lamp", name: "Table Lamp", modes: ["color_temp"] },
        { id: "demo_shelf_lamp", name: "Shelf Lamp", modes: ["onoff"] }
      ],
      groups: [
        {
          id: "demo_living_room",
          name: "Living Room",
          area: true,
          members: ["demo_ceiling", "demo_tv_lightstrip", "demo_table_lamp", "demo_shelf_lamp"],
          scenes: [["Nightlight"], ["Rest"], ["Bright"]]
        },
        {
          // A Hue zone with no area, like the real "Living Room Ambience".
          id: "demo_living_room_ambience",
          name: "Living Room Ambience",
          area: false,
          members: ["demo_ceiling", "demo_tv_lightstrip"],
          scenes: [["Concentrate"], ["Soho", true], ["Lake Placid", true], ["Relax"], ["Toil and trouble", true], ["Read"]]
        }
      ]
    },
    bedroom: {
      area: "demo_bedroom",
      areaName: "Bedroom",
      lights: [
        { id: "demo_bedside_left", name: "Bedside Left", modes: COLOUR },
        { id: "demo_bedside_right", name: "Bedside Right", modes: COLOUR },
        { id: "demo_big_light", name: "The Big Light", modes: ["color_temp"] },
        { id: "demo_bedroom_lightstrip", name: "Bedroom Lightstrip", modes: COLOUR },
        // A settings entity the card must skip, like a purifier's display light.
        { id: "demo_purifier_backlight", name: "Purifier Display Backlight", modes: ["brightness"], category: "config" }
      ],
      groups: [
        {
          id: "demo_bedroom",
          name: "Bedroom",
          area: true,
          members: ["demo_bedside_left", "demo_bedside_right", "demo_big_light", "demo_bedroom_lightstrip"],
          scenes: [["Nightlight"], ["Read"], ["Relax"]]
        },
        {
          id: "demo_bedroom_ambiance",
          name: "Bedroom Ambiance",
          area: true,
          members: ["demo_bedside_left", "demo_bedside_right", "demo_bedroom_lightstrip"],
          scenes: [["Arise", true], ["Spellbound", true], ["Storybook", true], ["Unwind", true]]
        }
      ]
    }
  };
  function hexToRgb(hex2) {
    const m = /^#?([0-9a-f]{6})$/i.exec(hex2);
    if (!m) return [255, 180, 110];
    const n = parseInt(m[1], 16);
    return [n >> 16 & 255, n >> 8 & 255, n & 255];
  }
  function xyToRgb([x, y]) {
    const z = 1 - x - y;
    const X = x / y;
    const Z = z / y;
    let r = X * 1.656492 - 0.354851 - Z * 0.255038;
    let g = -X * 0.707196 + 1.655397 + Z * 0.036152;
    let b = X * 0.051713 - 0.121364 + Z * 1.01153;
    const m = Math.max(r, g, b, 1e-6);
    [r, g, b] = [r, g, b].map((v) => Math.max(0, v / m));
    return [r, g, b].map((v) => Math.round(255 * (v <= 31308e-7 ? 12.92 * v : 1.055 * v ** (1 / 2.4) - 0.055)));
  }
  function slug(text) {
    return text.toLowerCase().replace(/[^a-z0-9]+/g, "_").replace(/^_|_$/g, "");
  }
  var DemoHome = class {
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
          state: i < 3 && !l.category ? "on" : "off",
          attributes: { friendly_name: l.name, supported_color_modes: l.modes, dynamics: "none", brightness: null }
        };
      });
      this.room.groups.forEach((g, gi) => {
        const id = `light.${g.id}`;
        const dev = `dev_${g.id}`;
        this.devices[dev] = { id: dev, area_id: g.area ? area : null };
        this.entities[id] = { entity_id: id, device_id: dev, area_id: null };
        this.states[id] = {
          entity_id: id,
          state: "off",
          attributes: {
            friendly_name: g.name,
            entity_id: g.members.map((m) => `light.${m}`),
            is_hue_group: true,
            hue_type: gi === 0 ? "room" : "zone",
            hue_scenes: g.scenes.map(([name]) => name),
            supported_color_modes: COLOUR
          }
        };
        g.scenes.forEach(([name, dynamic]) => {
          const sid = `scene.${g.id}_${slug(name)}`;
          this.entities[sid] = { entity_id: sid, device_id: dev, area_id: null };
          this.states[sid] = {
            entity_id: sid,
            state: "unknown",
            attributes: { friendly_name: `${g.name} ${name}`, name, is_dynamic: !!dynamic, group_name: g.name }
          };
        });
      });
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
        ...realHass || {},
        states: { ...this.states },
        entities: this.entities,
        devices: this.devices,
        areas: { [this.room.area]: { area_id: this.room.area, name: this.room.areaName } },
        services: { light: { turn_on: {}, turn_off: {}, toggle: {} }, scene: { turn_on: {} }, hue: { activate_scene: {} } },
        callService: (domain, service, data, target) => this.callService(domain, service, data || {}, target || {})
      };
    }
    stop() {
      clearInterval(this.timer);
      this.timer = null;
    }
    // ---- simulated services -------------------------------------------------
    callService(domain, service, data, target) {
      const ids = [].concat(target.entity_id || data.entity_id || []);
      if (domain === "light") {
        ids.forEach((id) => this._lightService(service, id, data));
      } else if (domain === "scene" && service === "turn_on") {
        ids.forEach((id) => this._applyScene(id, false));
      } else if (domain === "hue" && service === "activate_scene") {
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
        const on = service === "toggle" ? st.state !== "on" : service === "turn_on";
        if (!on) {
          this._set(bulb, "off", { brightness: null, dynamics: "none" });
          return;
        }
        const modes = st.attributes.supported_color_modes;
        const attrs = { dynamics: "none", brightness: modes.includes("onoff") ? null : data.brightness || st.attributes.brightness || 200 };
        if (data.color_temp_kelvin) Object.assign(attrs, { color_mode: "color_temp", color_temp_kelvin: data.color_temp_kelvin, rgb_color: null, hs_color: null });
        if (data.xy_color && modes.includes("xy")) {
          Object.assign(attrs, { color_mode: "xy", xy_color: data.xy_color, rgb_color: xyToRgb(data.xy_color), hs_color: null, color_temp_kelvin: null });
        }
        this._set(bulb, "on", attrs);
      });
      if (!Object.values(this.states).some((s) => s.attributes.dynamics === "dynamic_palette")) this.stop();
    }
    _applyScene(sceneId, dynamic, quiet) {
      const scene = this.states[sceneId];
      if (!scene) return;
      const group = Object.keys(this.entities).find(
        (id) => id.startsWith("light.") && this.entities[id].device_id === this.entities[sceneId].device_id
      );
      const name = scene.attributes.name;
      const white = WHITE[name.toLowerCase()];
      const palette = scenePalette(name).map(hexToRgb);
      this._members(group).forEach((bulb, i) => {
        const modes = this.states[bulb].attributes.supported_color_modes;
        const attrs = { dynamics: dynamic && scene.attributes.is_dynamic ? "dynamic_palette" : "none" };
        if (modes.includes("onoff")) attrs.brightness = null;
        else attrs.brightness = white ? white[1] : 180;
        if (white || !modes.includes("xy")) {
          Object.assign(attrs, { color_mode: "color_temp", color_temp_kelvin: white ? white[0] : 2700, rgb_color: null, hs_color: null });
        } else {
          const rgb = palette[i % palette.length];
          Object.assign(attrs, { color_mode: "xy", rgb_color: rgb, xy_color: [0.4, 0.4], hs_color: null, color_temp_kelvin: null });
        }
        this._set(bulb, "on", attrs);
      });
      this._set(sceneId, (/* @__PURE__ */ new Date()).toISOString(), {});
      if (!quiet) this._refreshGroups();
      else this._refreshGroups();
      if (dynamic && scene.attributes.is_dynamic) this._animate(group, palette);
    }
    // A universal colour scene: colours dealt round the lights; animated ones
    // drift round them every couple of seconds, like a Hue dynamic scene.
    playPalette(lightIds, colors, brightness, dynamic) {
      this.stop();
      const lights = lightIds.filter((id) => this.states[id]).sort();
      const palette = spreadColours(colors, Math.max(lights.length, colors.length));
      const paint = (shift) => lights.forEach((id, i) => {
        const st = this.states[id];
        if (dynamic && shift && (st.state !== "on" || st.attributes.dynamics !== "dynamic_palette")) return;
        const modes = st.attributes.supported_color_modes;
        const xy = palette[(i + shift) % palette.length];
        const attrs = { dynamics: dynamic ? "dynamic_palette" : "none", brightness: modes.includes("onoff") ? null : brightness };
        if (modes.includes("xy")) Object.assign(attrs, { color_mode: "xy", xy_color: xy, rgb_color: xyToRgb(xy), hs_color: null, color_temp_kelvin: null });
        this._set(id, "on", attrs);
      });
      paint(0);
      this._refreshGroups();
      this.onChange();
      if (!dynamic) return;
      this.timer = setInterval(() => {
        this.tick += 1;
        if (!lights.some((id) => this.states[id].state === "on" && this.states[id].attributes.dynamics === "dynamic_palette")) {
          this.stop();
          return;
        }
        paint(this.tick);
        this._refreshGroups();
        this.onChange();
      }, 2e3);
    }
    // Cycle the palette round the group's colour bulbs every couple of seconds.
    _animate(group, palette) {
      this.stop();
      this.timer = setInterval(() => {
        this.tick += 1;
        let still = false;
        this._members(group).forEach((bulb, i) => {
          const st = this.states[bulb];
          if (st.state !== "on" || st.attributes.dynamics !== "dynamic_palette") return;
          still = true;
          if (st.attributes.supported_color_modes.includes("xy")) {
            this._set(bulb, null, { rgb_color: palette[(i + this.tick) % palette.length] });
          }
        });
        if (!still) {
          this.stop();
          return;
        }
        this._refreshGroups();
        this.onChange();
      }, 2e3);
    }
    // Groups are on if any member is on, at their lit members' average brightness.
    _refreshGroups() {
      this.room.groups.forEach((g) => {
        const id = `light.${g.id}`;
        const lit = this._members(id).map((m) => this.states[m]).filter((s) => s.state === "on");
        const dimmable = lit.filter((s) => s.attributes.brightness);
        const first = lit.find((s) => s.attributes.rgb_color) || lit[0];
        this._set(id, lit.length ? "on" : "off", {
          brightness: dimmable.length ? Math.round(dimmable.reduce((a, s) => a + s.attributes.brightness, 0) / dimmable.length) : null,
          rgb_color: first ? first.attributes.rgb_color : null,
          color_temp_kelvin: first ? first.attributes.color_temp_kelvin : null,
          dynamics: lit.some((s) => s.attributes.dynamics === "dynamic_palette")
        });
      });
    }
  };

  // src/light-control-card.js
  var LCC_DEFAULT_MAX_SCENES = 8;
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
    if (k <= 2600) return "#ff9a3c";
    if (k <= 3400) return "#ffb45c";
    if (k <= 4800) return "#ffd08a";
    return "#8fc3ff";
  }
  function lccLightColor(st) {
    if (!st || st.state !== "on") return "#ffc107";
    const a = st.attributes || {};
    const paleColour = a.hs_color && a.hs_color[1] < 15;
    if ((a.color_mode === "color_temp" || paleColour) && a.color_temp_kelvin) return lccKelvinColor(a.color_temp_kelvin);
    if (paleColour) return "#ffd08a";
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
    return (scenes || []).map((s) => typeof s === "string" ? { entity: s } : s).map((s) => s && !s.entity && s.scene ? { ...s, entity: `${UNIVERSAL_PREFIX}${String(s.scene).toLowerCase()}` } : s).filter((s) => s && s.entity);
  }
  function lccIsUniversal(ref) {
    return String(ref || "").startsWith(UNIVERSAL_PREFIX);
  }
  function lccSceneGroup(hass, sceneId) {
    const entry = hass.entities && hass.entities[sceneId];
    if (!entry || !entry.device_id) return null;
    const group = Object.values(hass.entities).find(
      (e) => e.device_id === entry.device_id && e.entity_id.startsWith("light.") && lccIsGroupLike(hass.states[e.entity_id])
    );
    return group ? group.entity_id : null;
  }
  function lccSceneGroups(hass, groupIds, lightIds) {
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
    return groups;
  }
  function lccGroupScenes(hass, groupId) {
    const device = hass.entities && hass.entities[groupId] && hass.entities[groupId].device_id;
    if (!device) return [];
    const order = hass.states[groupId] && hass.states[groupId].attributes.hue_scenes || [];
    const rank = (id) => {
      const i = order.indexOf(hass.states[id].attributes.name);
      return i === -1 ? order.length : i;
    };
    return Object.values(hass.entities).filter((e) => e.entity_id.startsWith("scene.") && e.device_id === device && !e.hidden && hass.states[e.entity_id]).map((e) => e.entity_id).sort((a, b) => rank(a) - rank(b) || a.localeCompare(b));
  }
  var LCC_DEFAULT_SCENES = ["bright", "dimmed", "relax", "nightlight"];
  function lccCardLights(hass, config) {
    const mode = config.mode || "light";
    let area = config.area;
    if (mode === "group") {
      return { groups: config.entity ? [config.entity] : [], lights: config.entity ? lccMembersOf(hass, config.entity) : [] };
    }
    if (mode === "light") area = lccAreaOf(hass, hass.entities && hass.entities[config.entity]);
    const ids = lccCandidates(hass, "room", area);
    return {
      groups: ids.filter((id) => lccIsGroupLike(hass.states[id])),
      lights: ids.filter((id) => !lccIsGroupLike(hass.states[id]))
    };
  }
  function lccSceneChoices(hass, config) {
    if (!hass || !hass.states) return [];
    loadUniversalScenes(hass);
    const { groups, lights } = lccCardLights(hass, config);
    const isRoom = (id) => hass.states[id].attributes.hue_type === "room";
    const sceneGroups = lccSceneGroups(hass, groups, lights).sort(
      (a, b) => (isRoom(b) ? 1 : 0) - (isRoom(a) ? 1 : 0) || lccGroupName(hass, a).localeCompare(lccGroupName(hass, b))
    );
    const ids = sceneGroups.flatMap((g) => lccGroupScenes(hass, g));
    const nameOf = (id) => hass.states[id].attributes.name || hass.states[id].attributes.friendly_name || id;
    const options = [];
    universalScenes().forEach((u) => {
      if (!sceneGroups.length) options.push({ value: universalRef(u.key), label: u.name });
      sceneGroups.forEach((g) => options.push({ value: universalRef(u.key, g), label: `${u.name} \xB7 ${lccGroupName(hass, g)}` }));
    });
    const universalNames = new Set(universalScenes().map((u) => u.name.toLowerCase()));
    ids.filter((id) => !universalNames.has(nameOf(id).toLowerCase())).forEach((id) => {
      const group = hass.states[id].attributes.group_name;
      options.push({ value: id, label: group ? `${nameOf(id)} \xB7 ${group}` : nameOf(id) });
    });
    lccNormalizeScenes(config.scenes).forEach((s) => {
      if (options.some((o) => o.value === s.entity)) return;
      if (lccIsUniversal(s.entity)) {
        const u = universalScene(s.entity);
        const target = universalTarget(s.entity);
        if (u) options.push({ value: s.entity, label: target ? `${u.name} \xB7 ${lccGroupName(hass, target)}` : u.name });
        return;
      }
      const st = hass.states[s.entity];
      options.push({ value: s.entity, label: `${st ? nameOf(s.entity) : s.entity} (other room)` });
    });
    return options;
  }
  function lccGroupName(hass, id) {
    const st = hass.states[id];
    return st && st.attributes.friendly_name || id;
  }
  function lccNormalizeList(list) {
    return (list || []).map((s) => typeof s === "string" ? { entity: s } : s).filter((s) => s && s.entity);
  }
  function lccCandidates(hass, mode, area, entity) {
    if (!hass || !hass.states) return [];
    if (mode === "group") return entity ? lccMembersOf(hass, entity) : [];
    if (mode !== "room" || !area) return [];
    const areaLights = Object.values(hass.entities || {}).filter(
      (e) => e.entity_id.startsWith("light.") && !e.hidden && e.entity_category == null && hass.states[e.entity_id] && lccAreaOf(hass, e) === area
    ).map((e) => e.entity_id);
    const bulbs = areaLights.filter((id) => !lccIsGroupLike(hass.states[id]));
    const bulbSet = new Set(bulbs);
    const groups = areaLights.filter((id) => lccIsGroupLike(hass.states[id]));
    Object.values(hass.states).forEach((st) => {
      const members = st.entity_id.startsWith("light.") && lccIsGroupLike(st) ? st.attributes.entity_id : null;
      if (members && members.length && members.every((m) => bulbSet.has(m)) && !groups.includes(st.entity_id)) {
        groups.push(st.entity_id);
      }
    });
    return [...groups, ...bulbs];
  }
  function lccLevels(hass, ids, explicit) {
    const isRoom = (id) => lccIsGroupLike(hass.states[id]) && hass.states[id].attributes.hue_type === "room";
    const groups = ids.filter((id) => lccIsGroupLike(hass.states[id]));
    const hasRoom = groups.some(isRoom);
    const groupLevel = (id) => isRoom(id) || !hasRoom ? 0 : 1;
    const lightLevel = groups.length ? Math.max(...groups.map(groupLevel)) + 1 : 0;
    const out = {};
    ids.forEach((id) => {
      const set = explicit[id];
      const level = set !== void 0 && set !== null && set !== "" ? parseInt(set, 10) : NaN;
      out[id] = Number.isNaN(level) ? lccIsGroupLike(hass.states[id]) ? groupLevel(id) : lightLevel : Math.max(0, Math.min(2, level));
    });
    return out;
  }
  var lccDemoCache = {};
  function lccDemoFor(room) {
    const key = room || "living_room";
    if (!lccDemoCache[key]) lccDemoCache[key] = new DemoHome(key, () => {
    });
    return lccDemoCache[key];
  }
  function lccEditorSceneOptions(config, hass) {
    const sceneDemo = config.demo ? lccDemoFor(config.demo_room) : null;
    return sceneDemo ? lccSceneChoices(sceneDemo.hass(), {
      ...config,
      mode: config.mode || "room",
      area: sceneDemo.area,
      entity: config.mode === "group" ? sceneDemo.roomGroup : sceneDemo.firstLight
    }) : lccSceneChoices(hass, config);
  }
  function lccFillDefaultScenes(config, hass) {
    if (!universalScenes().length) return config;
    const options = lccEditorSceneOptions({ ...config, scenes: void 0 }, hass);
    const defaults = LCC_DEFAULT_SCENES.map((key) => {
      const option = options.find((o) => o.value === universalRef(key) || o.value.startsWith(`${universalRef(key)}@`));
      return option ? { entity: option.value } : null;
    }).filter(Boolean);
    if (!defaults.length) return config;
    if (config.scenes === "reset") return { ...config, scenes: defaults };
    if (config.scenes == null) return { ...config, scenes: defaults };
    const current = lccNormalizeScenes(config.scenes);
    const untouched = current.length === LCC_DEFAULT_SCENES.length && current.every((sc, i) => Object.keys(sc).length === 1 && String(sc.entity).split("@")[0] === universalRef(LCC_DEFAULT_SCENES[i]));
    const elsewhere = current.some((sc) => !options.some((o) => o.value === sc.entity));
    return untouched && elsewhere ? { ...config, scenes: defaults } : config;
  }
  function lccLabelScenes(config, hass) {
    if (!config.scenes) return config;
    const options = lccEditorSceneOptions(config, hass);
    const labelOf = (s) => (options.find((o) => o.value === s.entity) || {}).label || s.entity;
    return { ...config, scenes: lccNormalizeScenes(config.scenes).map((s) => ({ ...s, label: labelOf(s) })) };
  }
  var LightControlCardEditor = createFormEditor({
    fill: lccFillDefaultScenes,
    buttons: [{ label: "Reset", field: "scenes", variant: "danger", apply: (config) => ({ ...config, scenes: "reset" }) }],
    // Scenes past Max scenes are left off the card: say so above the list.
    alerts: [
      {
        field: "scenes",
        text: (config) => {
          const count = Array.isArray(config.scenes) ? config.scenes.length : 0;
          const max = config.max_scenes != null && config.max_scenes !== "" ? Number(config.max_scenes) : LCC_DEFAULT_MAX_SCENES;
          if (count <= max) return "";
          return max <= 0 ? "Max scenes is 0, so no scenes show on the card." : `Only the first ${max} of these ${count} scenes show on the card. Raise Max scenes to show them all.`;
        }
      }
    ],
    display: lccLabelScenes,
    store: (config) => config.scenes ? { ...config, scenes: config.scenes.map(({ label: _label, ...s }) => s) } : config,
    schema: (config, hass) => {
      const mode = config.mode || "light";
      loadUniversalScenes(hass);
      const sceneOptions = lccEditorSceneOptions(config, hass);
      let showField = [];
      if (mode === "room" || mode === "group") {
        const demo = config.demo ? lccDemoFor(config.demo_room) : null;
        const h = demo ? demo.hass() : hass;
        const ids = demo ? lccCandidates(h, mode, demo.area, demo.roomGroup) : lccCandidates(h, mode, config.area, config.entity);
        const options = ids.map((id) => {
          const st = h.states[id];
          const kind = lccIsGroupLike(st) ? st.attributes.hue_type === "room" ? "room" : "zone" : "light";
          return { value: id, label: `${st && st.attributes.friendly_name || id} (${kind})` };
        });
        showField = [
          {
            name: "entities",
            title: mode === "room" ? "Show these lights and zones, in this order (empty = all)" : "Show these lights, in this order (empty = all)",
            selector: {
              object: {
                multiple: true,
                label_field: "entity",
                description_field: "name",
                fields: {
                  entity: { label: mode === "room" ? "Light or zone" : "Light", required: true, selector: { select: { mode: "dropdown", options } } },
                  name: { label: "Name override", selector: { text: {} } },
                  icon: { label: "Icon override", selector: { icon: {} } },
                  level: {
                    label: "Level (indentation)",
                    selector: {
                      select: {
                        mode: "dropdown",
                        options: [
                          { value: "0", label: "Top" },
                          { value: "1", label: "Child (indented once)" },
                          { value: "2", label: "Grandchild (indented twice)" }
                        ]
                      }
                    }
                  }
                }
              }
            }
          }
        ];
      }
      const demoFields = [
        {
          type: "expandable",
          name: "",
          title: "Demo mode (pretend lights, for Design Presets)",
          flatten: true,
          schema: [
            { name: "demo", selector: { boolean: {} } },
            {
              name: "demo_room",
              selector: {
                select: {
                  mode: "dropdown",
                  options: [
                    { value: "living_room", label: "Living Room (room + animated-scene zone)" },
                    { value: "bedroom", label: "Bedroom (two groups + a hidden settings light)" }
                  ]
                }
              }
            }
          ]
        }
      ];
      return [
        {
          name: "mode",
          selector: {
            select: {
              mode: "dropdown",
              options: [
                { value: "light", label: "Single Light" },
                { value: "group", label: "Zone or light group" },
                { value: "room", label: "Room" }
              ]
            }
          }
        },
        // In demo mode the pretend home supplies the room/light, so hide these.
        ...config.demo ? [] : [
          mode === "room" ? { name: "area", selector: { area: {} } } : { name: "entity", selector: { entity: { domain: "light" } } }
        ],
        ...showField,
        { name: "name", selector: { text: {} } },
        ...mode === "room" ? [] : [{ name: "icon", selector: { icon: {} } }],
        { name: "max_scenes", selector: { number: { mode: "box", min: 0, max: 24 } } },
        {
          name: "scene_names",
          selector: {
            select: {
              mode: "dropdown",
              options: [
                { value: "auto", label: "Auto (hide on small tiles)" },
                { value: "always", label: "Always show" },
                { value: "never", label: "Never show (icons only)" }
              ]
            }
          }
        },
        {
          name: "scenes",
          selector: {
            object: {
              multiple: true,
              label_field: "label",
              description_field: "name",
              fields: {
                entity: { label: "Scene", required: true, selector: { select: { mode: "dropdown", options: sceneOptions } } },
                name: { label: "Name override", selector: { text: {} } },
                icon: { label: "Icon override", selector: { icon: {} } },
                image: { label: "Picture (replaces the colour background)", selector: { image: {} } },
                label: { selector: { constant: { value: "", label: "" } } }
              }
            }
          }
        },
        ...demoFields
      ];
    },
    normalize: (config) => ({
      ...config,
      ...config.scenes ? { scenes: lccNormalizeScenes(config.scenes) } : {},
      ...config.entities ? { entities: lccNormalizeList(config.entities) } : {}
    }),
    labels: {
      mode: "Card type",
      area: "Room",
      entity: "Light, zone or group",
      name: "Title (optional)",
      icon: "Icon override (optional)",
      max_scenes: "Max scenes",
      scene_names: "Scene names",
      scenes: "Scenes",
      demo: "Use pretend lights instead of real ones",
      demo_room: "Pretend room"
    },
    helpers: {
      max_scenes: "Default 8 (two rows). Set 0 to hide scenes.",
      demo: "Nothing is sent to Home Assistant; taps only change the pretend lights on this card."
    }
  });
  var LightControlCard = class extends HTMLElement {
    setConfig(config) {
      if (!config.entity && !config.area && !config.demo) throw new Error("entity or area required");
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
      const mode = cfg.mode || "room";
      return {
        ...cfg,
        mode,
        area: this._demo.area,
        entity: mode === "group" ? this._demo.roomGroup : this._demo.firstLight,
        // Only universal scenes and scenes from the pretend home apply (real
        // ones don't exist there); with none left, the defaults are used.
        scenes: cfg.scenes == null ? void 0 : lccNormalizeScenes(cfg.scenes).filter((sc) => lccIsUniversal(sc.entity) || this._demo.states[sc.entity])
      };
    }
    static getConfigElement() {
      return document.createElement(`light-control-card-editor${SUFFIX}`);
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
    // Real home: the integration applies it (colour scenes go through the Hue
    // bridge so they can animate). Pretend home: set the lights directly.
    _applyUniversal(scene) {
      const lib = scene.universal;
      if (!this.config.demo) {
        this._hass.callService("church_drive", "apply_scene", { entity_id: scene.targets, scene: lib.key });
      } else if (lib.kind === "colour" && this._demo) {
        this._demo.playPalette(scene.targetLights, lib.colors, lib.brightness, !!lib.dynamic);
      } else if (lib.kind === "colour") {
        universalDealColours(lib, scene.targetLights).forEach(
          ({ entity_id, ...data }) => this._hass.callService("light", "turn_on", data, { entity_id })
        );
      } else {
        this._hass.callService("light", "turn_on", universalTurnOnData(lib), { entity_id: scene.targets });
      }
    }
    _freezeScene(scene) {
      const hass = this._hass;
      const ids = scene.group ? lccMembersOf(hass, scene.group) : scene.targetLights || this._cardLightIds || [];
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
    _buildRow(entityId, { withMoreInfo, level = 0, icon: iconOverride }) {
      const st = this._hass.states[entityId];
      const name = st && st.attributes.friendly_name || entityId;
      const isGroupLike = lccIsGroupLike(st);
      const on = st && st.state === "on";
      const dimmable = st && st.attributes.supported_color_modes && st.attributes.supported_color_modes.some((m) => m !== "onoff");
      const color = lccLightColor(st);
      const icon = iconOverride || lccLightIcon(this._hass, st, isGroupLike);
      const stateText = !st || st.state === "unavailable" ? "Unavailable" : !on ? "Off" : dimmable && st.attributes.brightness ? `${Math.round(st.attributes.brightness / 255 * 100)}%` : "On";
      const brightnessPct = st && st.attributes.brightness ? Math.round(st.attributes.brightness / 255 * 100) : 0;
      const fillPct = on ? dimmable ? Math.max(brightnessPct, 4) : 100 : 0;
      const tint = `color-mix(in srgb, ${color} 40%, var(--card-background-color, #1c1c1c))`;
      const track = "rgba(255,255,255,0.06)";
      const row3 = document.createElement("div");
      row3.className = "lcc-row";
      const pad = level > 0 ? "9px 14px" : "12px 14px";
      const indent = level > 0 ? `margin-left:${16 * level}px;` : "";
      row3.style.cssText = `position:relative; display:flex; align-items:center; gap:12px; padding:${pad}; ${indent} border-radius:12px; margin-top:6px; overflow:hidden; cursor:pointer; user-select:none; touch-action:pan-y; background: linear-gradient(to right, ${tint} 0%, ${tint} ${fillPct}%, ${track} ${fillPct}%, ${track} 100%);`;
      row3.innerHTML = `
      ${iconHtml(icon, { size: "24px", cls: "lcc-row-icon", style: `color:${on ? color : "var(--secondary-text-color)"}; opacity:${on ? 1 : 0.6}; flex-shrink:0; pointer-events:none;` })}
      <div class="lcc-name" style="flex:1; min-width:0; font-weight:${on ? 600 : 400}; color:${on ? "var(--primary-text-color)" : "var(--secondary-text-color)"}; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; pointer-events:none;">${name}</div>
      <div class="lcc-state" style="flex-shrink:0; font-size:0.85rem; font-variant-numeric:tabular-nums; color:${on ? "var(--primary-text-color)" : "var(--secondary-text-color)"}; opacity:${on ? 0.9 : 0.7}; pointer-events:none;">${stateText}</div>
      ${withMoreInfo && !this.config.demo ? `<ha-icon class="lcc-more" icon="mdi:tune-variant" style="color:var(--secondary-text-color); --mdc-icon-size:20px; cursor:pointer; flex-shrink:0;"></ha-icon>` : ""}
    `;
      if (withMoreInfo && !this.config.demo) {
        const moreBtn = row3.querySelector(".lcc-more");
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
        row3.style.background = `linear-gradient(to right, ${tint} 0%, ${tint} ${pct}%, ${track} ${pct}%, ${track} 100%)`;
        if (dragging && moved) row3.querySelector(".lcc-state").textContent = pct <= 2 ? "Off" : `${Math.round(pct)}%`;
      };
      const pctFromEvent = (ev) => {
        const rect = row3.getBoundingClientRect();
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
          this._render(h);
        }
      };
      row3.addEventListener("pointerdown", (ev) => {
        pressed = true;
        if (!dimmable) return;
        dragging = true;
        moved = false;
        startX = ev.clientX;
        this._interacting = true;
        row3.setPointerCapture(ev.pointerId);
      });
      row3.addEventListener("pointermove", (ev) => {
        if (!dragging) return;
        if (Math.abs(ev.clientX - startX) > 4) moved = true;
        if (moved) setFillVisual(pctFromEvent(ev));
      });
      row3.addEventListener("pointerup", (ev) => {
        if (!pressed) return;
        if (dimmable && dragging && moved) {
          this._setBrightnessPct(entityId, pctFromEvent(ev));
        } else {
          this._toggle(entityId);
        }
        endInteraction();
      });
      row3.addEventListener("pointercancel", () => {
        setFillVisual(fillPct);
        endInteraction();
      });
      return row3;
    }
    // Scene tiles, the same height as a light row: picture (or palette
    // gradient) background with the icon and name side by side. At most four
    // per row in as few rows as possible, shared out evenly with any fuller
    // row first (6 = 3 + 3, 5 = 3 + 2, 7 = 4 + 3), so the last row's tiles are wider;
    // each row fills the width.
    // The selected scene glows in its own colour and the others are dimmed; an
    // animated scene shows a pulsing play badge while running, pause when not.
    _buildScenes(scenes) {
      if (!scenes.length) return null;
      const anyActive = scenes.some((s) => s.active);
      const names = ["always", "never"].includes(this.config.scene_names) ? this.config.scene_names : "auto";
      const wrap = document.createElement("div");
      wrap.className = `lcc-names-${names}`;
      wrap.style.cssText = "display:flex; flex-direction:column; gap:6px; margin-top:10px; padding:2px 0 4px;";
      const rowCount = Math.ceil(scenes.length / 4);
      const base = Math.floor(scenes.length / rowCount);
      const extra = scenes.length % rowCount;
      const rows = Array.from({ length: rowCount }, (_, i) => {
        const row3 = document.createElement("div");
        row3.style.cssText = "display:flex; gap:6px;";
        row3.dataset.size = base + (i < extra ? 1 : 0);
        wrap.appendChild(row3);
        return row3;
      });
      let rowIndex = 0;
      scenes.forEach((s) => {
        while (rows[rowIndex].children.length >= Number(rows[rowIndex].dataset.size)) rowIndex += 1;
        const tile = document.createElement("button");
        tile.className = s.active || !anyActive ? "lcc-scene" : "lcc-scene lcc-dim";
        const look = s.styleName || s.name;
        const glow = `color-mix(in srgb, ${scenePalette(look, s.colours)[0]} 85%, transparent)`;
        tile.title = s.playing ? `${s.name} (playing, tap to stop)` : s.paused ? `${s.name} (paused, tap to play)` : s.name;
        const bg = sceneBackground(look, s.image, s.colours);
        tile.style.cssText = `position:relative; container-type:inline-size; flex:1 1 0; min-width:0; height:48px; border:none; border-radius:12px; padding:0; overflow:hidden; cursor:pointer; background:${bg};${s.active ? ` box-shadow:0 0 12px 2px ${glow}; transform:scale(1.03); z-index:1;` : ""}`;
        const badge = (cls, icon, title) => `<ha-icon class="${cls}" icon="${icon}" title="${title}" style="position:absolute; top:50%; right:5px; transform:translateY(-50%); --mdc-icon-size:16px; color:#fff; filter:drop-shadow(0 1px 2px rgba(0,0,0,0.7));"></ha-icon>`;
        tile.innerHTML = `
        <div style="position:absolute; inset:0; background:rgba(0,0,0,0.18);"></div>
        <div class="lcc-scene-body" style="position:relative; display:flex; align-items:center; justify-content:center; gap:6px; height:100%; padding:0 8px; color:#fff;">
          ${iconHtml(s.icon, { size: "22px", cls: "lcc-scene-icon", style: "flex-shrink:0; filter:drop-shadow(0 1px 3px rgba(0,0,0,0.55));" })}
          <span class="lcc-scene-name" style="min-width:0; font-weight:600; line-height:1.15; text-shadow:0 1px 2px rgba(0,0,0,0.6); white-space:nowrap; overflow:hidden; text-overflow:ellipsis;"></span>
        </div>
        ${s.paused ? badge("lcc-paused", "mdi:pause", "Paused") : ""}
        ${s.playing ? badge("lcc-playing", "mdi:play", "Playing") : ""}`;
        tile.querySelector(".lcc-scene-name").textContent = s.name;
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
        else if (scene.universal) this._applyUniversal(scene);
        else this._activateScene(scene.entity, scene.isDynamic);
      });
    }
    _turnOffCardLights() {
      const ids = this._cardLightIds || [];
      if (ids.length) this._hass.callService("light", "turn_off", {}, { entity_id: ids });
    }
    // Resolve the scenes to show (configured list or the defaults), capped at
    // max_scenes, with display name/icon/picture and selected/playing status.
    _resolveScenes(hass, mode, headIds, memberIds) {
      const cfg = this._effectiveConfig();
      const max = cfg.max_scenes != null ? cfg.max_scenes : LCC_DEFAULT_MAX_SCENES;
      if (max <= 0) return [];
      const items = cfg.scenes == null ? LCC_DEFAULT_SCENES.map((key) => ({ entity: universalRef(key) })) : lccNormalizeScenes(cfg.scenes);
      const headGroups = headIds.filter((id) => lccIsGroupLike(hass.states[id]));
      const rooms = headGroups.filter((id) => hass.states[id].attributes.hue_type === "room");
      const targets = rooms.length ? rooms : headGroups.length ? headGroups : [...headIds, ...memberIds];
      const targetLights = [...new Set(targets.flatMap((id) => lccIsGroupLike(hass.states[id]) ? lccMembersOf(hass, id) : [id]))];
      const scenes = items.filter((s) => lccIsUniversal(s.entity) ? universalScene(s.entity) : hass.states[s.entity]).slice(0, max).map((s) => {
        if (lccIsUniversal(s.entity)) {
          const lib = universalScene(s.entity);
          const target = universalTarget(s.entity);
          const own = target && hass.states[target] ? [target] : null;
          let name2 = s.name || lib.name;
          if (!s.name && own && hass.states[target].attributes.hue_type !== "room") {
            const area = hass.areas && cfg.area && hass.areas[cfg.area];
            const room = rooms[0] ? lccGroupName(hass, rooms[0]) : area && area.name || "";
            const zone = lccGroupName(hass, target);
            const short = room && zone.toLowerCase().startsWith(`${room.toLowerCase()} `) ? zone.slice(room.length + 1) : zone;
            name2 = `${lib.name} \xB7 ${short}`;
          }
          const tTargets = own || targets;
          const tLights = own ? [...new Set(own.flatMap((id) => lccIsGroupLike(hass.states[id]) ? lccMembersOf(hass, id) : [id]))] : targetLights;
          const isDynamic2 = lib.kind === "colour" && !!lib.dynamic;
          return {
            ...s,
            name: name2,
            styleName: s.name || lib.name,
            isDynamic: isDynamic2,
            icon: s.icon || lib.icon || sceneIcon(lib.name, isDynamic2),
            colours: lib.hex,
            universal: lib,
            targets: tTargets,
            targetLights: tLights,
            activated: 0
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
          activated: Date.parse(st.state) || 0
        };
      });
      const selectFor = (target) => Object.values(hass.states).find((st) => st.entity_id.startsWith("select.") && st.attributes.target === target);
      this._sceneSelects = scenes.filter((sc) => sc.universal && sc.targets.length === 1).map((sc) => selectFor(sc.targets[0])).filter(Boolean).map((st) => st.entity_id);
      const anyOn = (ids) => ids.some((id) => hass.states[id] && hass.states[id].state === "on");
      const matching = scenes.find((sc) => {
        if (!sc.universal || !anyOn(sc.targetLights)) return false;
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
      const latest = scenes.filter((sc) => !sc.universal).reduce((a, b) => b.activated > (a ? a.activated : 0) ? b : a, null);
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
      this._lastInput = hass;
      loadSceneStyles(hass);
      loadUniversalScenes(hass);
      this._render(this.config.demo ? this._demoHass(hass) : hass);
    }
    _render(hass) {
      this._hass = hass;
      const cfg = this._effectiveConfig();
      const mode = cfg.mode || (cfg.area ? "room" : "light");
      if (this._compact) {
        const rowsCfg = Array.isArray(cfg.entities) ? cfg.entities.map((e) => typeof e === "string" ? { entity: e } : e) : [];
        const headRow = rowsCfg.find((e) => e && e.entity && !Number(e.level || 0));
        const head = headRow && headRow.entity || cfg.entity;
        const st = head && hass.states[head];
        const area = cfg.area && hass.areas && hass.areas[cfg.area];
        const on = !!st && st.state === "on";
        const pct = on && st.attributes.brightness != null ? Math.round(st.attributes.brightness / 255 * 100) : null;
        const members = st && Array.isArray(st.attributes.entity_id) ? st.attributes.entity_id.filter((id) => hass.states[id] && hass.states[id].state === "on").length : 0;
        return kitCompact(this, {
          name: cfg.name || headRow && headRow.name || area && area.name || st && st.attributes.friendly_name || head || "Lights",
          color: !st || st.state === "unavailable" ? KIT_COLOR.off : on ? KIT_COLOR.warm : KIT_COLOR.off,
          value: pct != null ? `${pct}%` : "",
          status: !st ? "" : st.state === "unavailable" ? "Unavailable" : on ? members ? `${members} on` : "On" : "Off",
          buttons: st ? [{ key: "power", icon: "mdi:power", title: on ? "Turn off" : "Turn on", on, color: KIT_COLOR.warm }] : [],
          onButton: () => this._toggle(head)
        });
      }
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
               sit beside the icon on one line (\u2026 if too long); auto hides
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
        this._titleEl = this.querySelector(".lcc-title");
        this._main = this.querySelector(".lcc-main");
        this._members = this.querySelector(".lcc-members");
        this._scenesEl = this.querySelector(".lcc-scenes");
        this._built = true;
      }
      let headIds = [];
      let memberIds = [];
      let rows = [];
      const names = {};
      const levels = {};
      const icons = {};
      const chosen = lccNormalizeList(cfg.entities).filter((s) => hass.states[s.entity]);
      chosen.forEach((s) => {
        if (s.name) names[s.entity] = s.name;
        if (s.level !== void 0) levels[s.entity] = s.level;
        if (s.icon) icons[s.entity] = s.icon;
      });
      if (mode === "room") {
        if (chosen.length) {
          const ids = chosen.map((s) => s.entity);
          headIds = ids.filter((id) => lccIsGroupLike(hass.states[id]));
          memberIds = ids.filter((id) => !lccIsGroupLike(hass.states[id]));
          const lv = lccLevels(hass, ids, levels);
          rows = ids.map((id) => ({ id, level: lv[id] }));
        } else {
          const all = lccCandidates(hass, "room", cfg.area).filter((id) => {
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
        if (mode === "group") {
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
        ...this._sceneSelects || [],
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
        if (rows.length === 0) {
          this._main.textContent = "No lights found in this area.";
          this._main.style.cssText = "color:var(--secondary-text-color); padding:8px 4px;";
        }
      } else {
        this._titleEl.style.display = "none";
      }
      rows.forEach(({ id, level }) => {
        const row3 = this._buildRow(id, { withMoreInfo: true, level, icon: icons[id] });
        const nameEl = row3.querySelector(".lcc-name");
        if (names[id] && nameEl) nameEl.textContent = names[id];
        this._main.appendChild(row3);
      });
      this._scenesEl.innerHTML = "";
      const scenesGrid = this._buildScenes(scenes);
      if (scenesGrid) this._scenesEl.appendChild(scenesGrid);
      hydrateIcons(this);
      this._size = 1 + (mode === "room" ? 1 : 0) + Math.max(relevantEntityIds.length, 1) + Math.ceil(scenes.length / 4);
    }
    getCardSize() {
      return this._size || 3;
    }
    // Sections-view defaults; the editor's Layout tab can override them.
    getGridOptions() {
      return { columns: 12, min_columns: 6, rows: "auto" };
    }
  };
  kitCompactable(LightControlCard, (card) => {
    card._built = false;
    card._lastIds = null;
  });
  function registerLightControlCard() {
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
      description: "Light/group/room control with icon, toggle, brightness, scenes, and full more-info pop-up (visual editor supported)",
      preview: true,
      documentationURL: "https://github.com/J45PER/church-drive-cards#readme"
    });
  }

  // src/scene-styles-card.js
  function titleCase(key) {
    return key.replace(/\b\w/g, (c) => c.toUpperCase());
  }
  function sceneNames(hass, styles) {
    const names = /* @__PURE__ */ new Map();
    Object.values(hass && hass.states || {}).forEach((st) => {
      if (st.entity_id.startsWith("scene.") && st.attributes.name) {
        const key = sceneKey(st.attributes.name);
        if (!names.has(key)) names.set(key, { name: st.attributes.name.replace(/\s+\d+$/, ""), dynamic: st.attributes.is_dynamic === true, inHome: true });
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
  var SceneStylesCardEditor = createFormEditor({
    schema: (config, hass) => [
      { name: "title", selector: { text: {} } },
      { name: "only_home", selector: { boolean: {} } },
      {
        name: "styles",
        selector: {
          object: {
            multiple: true,
            label_field: "scene",
            description_field: "icon",
            fields: {
              scene: {
                label: "Scene name (applies in every room)",
                required: true,
                selector: {
                  select: {
                    mode: "dropdown",
                    custom_value: true,
                    options: sceneNames(hass, config.styles).map((n) => n.name)
                  }
                }
              },
              icon: { label: "Icon", selector: { icon: {} } },
              colour_1: { label: "Background colour 1", selector: { color_rgb: {} } },
              colour_2: { label: "Background colour 2 (optional)", selector: { color_rgb: {} } },
              colour_3: { label: "Background colour 3 (optional)", selector: { color_rgb: {} } },
              image: { label: "Picture (replaces the colours)", selector: { image: {} } }
            }
          }
        }
      }
    ],
    labels: {
      title: "Title",
      only_home: "Only preview scenes that exist in this home",
      styles: "Custom scene styles"
    },
    helpers: {
      styles: "Each entry restyles that scene name on every light card, on every dashboard. Leave a field empty to keep the default."
    }
  });
  var SceneStylesCard = class extends HTMLElement {
    setConfig(config) {
      this.config = config || {};
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
      this.querySelector("div").textContent = cfg.title || "Scene styles";
      this.querySelector(".ssc-sub").textContent = `${names.length} scenes \xB7 ${styled.size} custom \xB7 edit this card to change them`;
      const grid = this.querySelector(".ssc-grid");
      names.forEach((n) => {
        const tile = document.createElement("div");
        tile.className = "ssc-tile";
        tile.title = styled.has(n.key) ? `${n.name} (custom style)` : n.name;
        tile.style.background = sceneBackground(n.name);
        tile.innerHTML = `
        <div style="position:absolute; inset:0; background:linear-gradient(to top, rgba(0,0,0,0.6), rgba(0,0,0,0) 65%);"></div>
        ${iconHtml(sceneIcon(n.name, n.dynamic), { size: "40cqw", cls: "ssc-icon", style: "position:absolute; left:50%; top:44%; transform:translate(-50%, -50%); color:#fff; filter:drop-shadow(0 1px 3px rgba(0,0,0,0.55));" })}
        ${styled.has(n.key) ? '<ha-icon icon="mdi:pencil" title="Custom style" style="position:absolute; top:6px; right:6px; --mdc-icon-size:clamp(12px, 18cqw, 18px); color:#fff; filter:drop-shadow(0 1px 2px rgba(0,0,0,0.7));"></ha-icon>' : ""}
        <div class="ssc-name"></div>`;
        tile.querySelector(".ssc-name").textContent = n.name;
        grid.appendChild(tile);
        hydrateIcons(tile);
      });
      this._rows = Math.ceil(names.length / 4);
    }
    getCardSize() {
      return 2 + (this._rows || 4) * 2;
    }
    getGridOptions() {
      return { columns: 12, min_columns: 6, rows: "auto" };
    }
    static getConfigElement() {
      return document.createElement(`scene-styles-card-editor${SUFFIX}`);
    }
    static getStubConfig() {
      return { title: "Scene styles", only_home: true, styles: [] };
    }
  };
  function registerSceneStylesCard() {
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
      documentationURL: "https://github.com/J45PER/church-drive-cards#readme"
    });
  }

  // src/scene-builder-card.js
  var BLANK = { name: "", kind: "colour", kelvin: 2700, brightness: 80, colors: ["#ff7b39", "#7b2cbf"], dynamic: true, speed: 0.63, icon: "" };
  function kelvinHex(k) {
    const t = k / 100;
    const r = t <= 66 ? 255 : 329.7 * (t - 60) ** -0.1332;
    const g = t <= 66 ? 99.47 * Math.log(t) - 161.12 : 288.12 * (t - 60) ** -0.0755;
    const b = t >= 66 ? 255 : t <= 19 ? 0 : 138.52 * Math.log(t - 10) - 305.04;
    return "#" + [r, g, b].map((v) => Math.round(Math.max(0, Math.min(255, v))).toString(16).padStart(2, "0")).join("");
  }
  function swatch(scene) {
    const colours = scene.kind === "colour" ? scene.colors : [kelvinHex(scene.kelvin), kelvinHex(scene.kelvin)];
    const list = colours.length === 1 ? [colours[0], colours[0]] : colours;
    return `linear-gradient(135deg, ${list.join(", ")})`;
  }
  var SceneBuilderCardEditor = createFormEditor({
    schema: () => [{ name: "title", selector: { text: {} } }],
    labels: { title: "Title" }
  });
  var SceneBuilderCard = class extends HTMLElement {
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
        const res = await this._hass.callWS({ type: "church_drive/library" });
        this._custom = res.custom || [];
        this._error = null;
      } catch (err) {
        this._custom = [];
        this._error = "The Church Drive integration isn't available.";
      }
      this._draw();
    }
    _rooms() {
      return Object.values(this._hass.states).filter((st) => st.entity_id.startsWith("light.") && st.attributes.hue_type).map((st) => ({ id: st.entity_id, name: st.attributes.friendly_name || st.entity_id, kind: st.attributes.hue_type })).sort((a, b) => a.kind === b.kind ? a.name.localeCompare(b.name) : a.kind === "room" ? -1 : 1);
    }
    _payload() {
      const d = this._draft;
      const scene = { name: d.name.trim(), kind: d.kind, brightness: Number(d.brightness) };
      if (d.key) scene.key = d.key;
      if (d.icon) scene.icon = d.icon.trim();
      if (d.kind === "colour") Object.assign(scene, { colors: d.colors, dynamic: !!d.dynamic, speed: Number(d.speed) });
      else scene.kelvin = Number(d.kelvin);
      return scene;
    }
    async _call(message, done) {
      this._busy = true;
      this._status = "";
      this._draw();
      try {
        await this._hass.callWS(message);
        this._status = done;
      } catch (err) {
        this._status = `Couldn't do that: ${err && err.message || err}`;
      }
      this._busy = false;
    }
    async _save() {
      const scene = this._payload();
      if (!scene.name) {
        this._status = "Give the scene a name.";
        this._draw();
        return;
      }
      await this._call({ type: "church_drive/scene/save", scene }, `Saved "${scene.name}". It's now in every light card's scene list.`);
      if (!this._status.startsWith("Couldn't")) this._draft = null;
      loadUniversalScenes(this._hass, true);
      await this._load();
    }
    async _delete(scene) {
      if (!window.confirm(`Delete "${scene.name}"? Light cards using it will stop showing it.`)) return;
      await this._call({ type: "church_drive/scene/delete", key: scene.key }, `Deleted "${scene.name}".`);
      loadUniversalScenes(this._hass, true);
      await this._load();
    }
    async _try() {
      const target = this.querySelector(".sbc-room").value;
      if (!target) return;
      const scene = { ...this._payload(), name: this._payload().name || "Preview" };
      delete scene.key;
      await this._call({ type: "church_drive/scene/preview", entity_id: [target], scene }, "Playing on the lights now.");
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
        <div class="sbc-sub" style="margin-top:4px;">Your own scenes, usable in any room or zone. Built-in ones (Bright, Soho\u2026) are always there.</div>
        <div class="sbc-list"></div>
        <div class="sbc-body"></div>
        <div class="sbc-status"></div>
      </ha-card>`;
      this.querySelector("div").textContent = cfg.title || "Scene builder";
      this.querySelector(".sbc-status").textContent = this._error || this._status || "";
      const list = this.querySelector(".sbc-list");
      (this._custom || []).forEach((scene) => {
        const row3 = document.createElement("div");
        row3.className = "sbc-row";
        row3.innerHTML = `<div class="sbc-sw">${scene.icon ? iconHtml(scene.icon, { size: "24px" }) : ""}</div>
        <div class="sbc-name"><div></div><div class="sbc-sub"></div></div>
        <button class="sbc-btn" data-act="edit">Edit</button><button class="sbc-btn" data-act="delete">Delete</button>`;
        row3.querySelector(".sbc-sw").style.background = swatch(scene);
        row3.querySelector(".sbc-name div").textContent = scene.name;
        row3.querySelector(".sbc-name .sbc-sub").textContent = scene.kind === "colour" ? `${scene.colors.length} colours${scene.dynamic ? ", animated" : ""} \xB7 ${scene.brightness}%` : `${scene.kelvin}K \xB7 ${scene.brightness}%`;
        row3.querySelector("[data-act=edit]").addEventListener("click", () => {
          this._draft = { ...BLANK, ...scene, colors: [...scene.colors || BLANK.colors] };
          this._status = "";
          this._draw();
        });
        row3.querySelector("[data-act=delete]").addEventListener("click", () => this._delete(scene));
        list.appendChild(row3);
      });
      hydrateIcons(list);
      const body = this.querySelector(".sbc-body");
      if (!d) {
        body.innerHTML = `<div class="sbc-actions"><button class="sbc-btn sbc-primary" data-act="new">New scene</button></div>`;
        body.querySelector("[data-act=new]").addEventListener("click", () => {
          this._draft = { ...BLANK, colors: [...BLANK.colors] };
          this._status = "";
          this._draw();
        });
        return;
      }
      body.innerHTML = `<div class="sbc-form">
      <div class="sbc-row" style="margin-top:16px;"><div class="sbc-sw sbc-preview"></div><div class="sbc-name sbc-title"></div></div>
      <label>Name<input type="text" class="sbc-f-name" maxlength="32" placeholder="e.g. Film night"></label>
      <label>Type<div class="sbc-seg"><button class="sbc-btn" data-kind="white">White</button><button class="sbc-btn" data-kind="colour">Colours</button></div></label>
      <div class="sbc-white"><label>Colour temperature: <span class="sbc-k"></span>K<input type="range" class="sbc-f-kelvin" min="2000" max="6500" step="50"></label></div>
      <div class="sbc-colour"><label>Colours (spread round the lights, blended so no two match; gradient strips show several)</label><div class="sbc-colours"></div>
        <label><input type="checkbox" class="sbc-f-dynamic"> Animated (colours drift between the lights, like Hue's dynamic scenes)</label>
        <label class="sbc-speed">Speed<input type="range" class="sbc-f-speed" min="0" max="1" step="0.05"></label></div>
      <label>Brightness: <span class="sbc-b"></span>%<input type="range" class="sbc-f-brightness" min="1" max="100"></label>
      <label>Icon (optional, e.g. mdi:movie-open)<input type="text" class="sbc-f-icon" placeholder="mdi:\u2026"></label>
      <label>Try it in<select class="sbc-room"><option value="">Choose a room or zone\u2026</option></select></label>
      <div class="sbc-actions"><button class="sbc-btn" data-act="try">Try</button><button class="sbc-btn sbc-primary" data-act="save">Save</button><button class="sbc-btn" data-act="cancel">Cancel</button></div>
    </div>`;
      const $ = (sel) => body.querySelector(sel);
      const refresh = () => {
        $(".sbc-preview").style.background = swatch(d);
        $(".sbc-title").textContent = d.name || "New scene";
        $(".sbc-k").textContent = d.kelvin;
        $(".sbc-b").textContent = d.brightness;
        body.querySelectorAll("[data-kind]").forEach((b) => b.setAttribute("aria-pressed", String(b.dataset.kind === d.kind)));
        $(".sbc-white").style.display = d.kind === "white" ? "" : "none";
        $(".sbc-colour").style.display = d.kind === "colour" ? "" : "none";
        $(".sbc-speed").style.display = d.dynamic ? "" : "none";
      };
      const drawColours = () => {
        const box = $(".sbc-colours");
        box.innerHTML = "";
        d.colors.forEach((c, i) => {
          const input = document.createElement("input");
          input.type = "color";
          input.value = c;
          input.title = "Change colour (right-click or long-press to remove)";
          input.addEventListener("input", () => {
            d.colors[i] = input.value;
            refresh();
          });
          input.addEventListener("contextmenu", (ev) => {
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
          const add = document.createElement("button");
          add.className = "sbc-btn";
          add.textContent = "+ Colour";
          add.addEventListener("click", () => {
            d.colors.push(d.colors[d.colors.length - 1] || "#ffffff");
            drawColours();
            refresh();
          });
          box.appendChild(add);
        }
        if (d.colors.length > 1) {
          const remove = document.createElement("button");
          remove.className = "sbc-btn";
          remove.textContent = "\u2212 Colour";
          remove.addEventListener("click", () => {
            d.colors.pop();
            drawColours();
            refresh();
          });
          box.appendChild(remove);
        }
      };
      $(".sbc-f-name").value = d.name;
      $(".sbc-f-name").addEventListener("input", (ev) => {
        d.name = ev.target.value;
        refresh();
      });
      body.querySelectorAll("[data-kind]").forEach(
        (b) => b.addEventListener("click", () => {
          d.kind = b.dataset.kind;
          refresh();
        })
      );
      $(".sbc-f-kelvin").value = d.kelvin;
      $(".sbc-f-kelvin").addEventListener("input", (ev) => {
        d.kelvin = Number(ev.target.value);
        refresh();
      });
      $(".sbc-f-brightness").value = d.brightness;
      $(".sbc-f-brightness").addEventListener("input", (ev) => {
        d.brightness = Number(ev.target.value);
        refresh();
      });
      $(".sbc-f-dynamic").checked = !!d.dynamic;
      $(".sbc-f-dynamic").addEventListener("change", (ev) => {
        d.dynamic = ev.target.checked;
        refresh();
      });
      $(".sbc-f-speed").value = d.speed;
      $(".sbc-f-speed").addEventListener("input", (ev) => {
        d.speed = Number(ev.target.value);
      });
      $(".sbc-f-icon").value = d.icon || "";
      $(".sbc-f-icon").addEventListener("input", (ev) => {
        d.icon = ev.target.value;
      });
      const rooms = $(".sbc-room");
      this._rooms().forEach((r) => {
        const opt = document.createElement("option");
        opt.value = r.id;
        opt.textContent = `${r.name} (${r.kind})`;
        rooms.appendChild(opt);
      });
      if (this._tryTarget) rooms.value = this._tryTarget;
      rooms.addEventListener("change", () => {
        this._tryTarget = rooms.value;
      });
      $("[data-act=try]").addEventListener("click", () => this._try());
      $("[data-act=save]").addEventListener("click", () => this._save());
      $("[data-act=cancel]").addEventListener("click", () => {
        this._draft = null;
        this._status = "";
        this._draw();
      });
      body.querySelectorAll("button").forEach((b) => b.disabled = !!this._busy);
      drawColours();
      refresh();
    }
    getCardSize() {
      return 6;
    }
    getGridOptions() {
      return { columns: 12, min_columns: 6, rows: "auto" };
    }
    static getConfigElement() {
      return document.createElement(`scene-builder-card-editor${SUFFIX}`);
    }
    static getStubConfig() {
      return { title: "Scene builder" };
    }
  };
  function registerSceneBuilderCard() {
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
      description: "Make your own universal scenes (white or colours, optionally animated) for every Light Control card",
      preview: false,
      documentationURL: "https://github.com/J45PER/church-drive-cards#readme"
    });
  }

  // src/section-title-card.js
  var STC_FALLBACK = {
    red: "#f44336",
    pink: "#e91e63",
    purple: "#926bc7",
    "deep-purple": "#6e41ab",
    indigo: "#3f51b5",
    blue: "#2196f3",
    "light-blue": "#03a9f4",
    cyan: "#00bcd4",
    teal: "#009688",
    green: "#4caf50",
    "light-green": "#8bc34a",
    lime: "#cddc39",
    yellow: "#ffeb3b",
    amber: "#ffc107",
    orange: "#ff9800",
    "deep-orange": "#ff5722",
    brown: "#795548",
    grey: "#9e9e9e",
    "blue-grey": "#607d8b"
  };
  function stcColor(color) {
    if (!color) return "var(--primary-text-color)";
    if (/^(#|rgb|hsl|var\()/.test(color)) return color;
    return `var(--${color}-color, ${STC_FALLBACK[color] || color})`;
  }
  var TPL_KEY = "cd-tpl-cache";
  var tplCache = null;
  var tplSave = 0;
  function tplStore() {
    if (tplCache) return tplCache;
    tplCache = /* @__PURE__ */ new Map();
    try {
      const saved = JSON.parse(localStorage.getItem(TPL_KEY) || "[]");
      if (Array.isArray(saved)) saved.forEach(([k, v]) => tplCache.set(k, v));
    } catch (err) {
    }
    return tplCache;
  }
  function tplRemember(template, value) {
    const store = tplStore();
    if (store.get(template) === value) return;
    store.delete(template);
    store.set(template, value);
    while (store.size > 300) store.delete(store.keys().next().value);
    clearTimeout(tplSave);
    tplSave = setTimeout(() => {
      try {
        localStorage.setItem(TPL_KEY, JSON.stringify([...store]));
      } catch (err) {
      }
    }, 1e3);
  }
  function stcRender(hass, template, done) {
    if (!template || !hass || !hass.connection) return null;
    if (!/[{%]/.test(template)) {
      done(template);
      return null;
    }
    const store = tplStore();
    if (store.has(template)) done(store.get(template));
    return hass.connection.subscribeMessage(
      (msg) => {
        if (msg.result === void 0) return;
        const text = String(msg.result).trim();
        tplRemember(template, text);
        done(text);
      },
      { type: "render_template", template, strict: false, report_errors: false }
    ).catch(() => null);
  }
  function stcSetInstantly(el, prop, value, instant) {
    if (!instant) {
      el.style[prop] = value;
      return;
    }
    const t = el.style.transition;
    el.style.transition = "none";
    el.style[prop] = value;
    void el.offsetWidth;
    el.style.transition = t;
  }
  var STC_TOGGLE_CSS = `
  .stc-tog { position:relative; flex:none; width:30px; height:30px; margin:-4px -2px -4px 0; padding:0; border-radius:50%;
    border:2px solid color-mix(in srgb, var(--stc-c) 65%, transparent); background:transparent; cursor:pointer;
    transition:background-color .2s, border-color .6s; -webkit-tap-highlight-color:transparent; }
  .stc-tog:hover { background:color-mix(in srgb, var(--stc-c) 18%, transparent); }
  .stc-tog::before, .stc-tog::after { content:''; position:absolute; left:50%; top:50%; width:12px; height:2px; border-radius:2px;
    background:var(--primary-text-color); transform:translate(-50%, -50%); transition:transform .32s cubic-bezier(.2,.8,.2,1); }
  .stc-tog.stc-shut::after { transform:translate(-50%, -50%) rotate(90deg); }
`;
  var STC_COLOR_TEMPLATE_FIELD = { name: "color_template", selector: { template: {} } };
  var STC_COLOR_TEMPLATE_LABEL = "Colour from a template (optional; overrides the colour)";
  var STC_COLOR_TEMPLATE_HELPER = "Gives a colour name or code, e.g. {{ 'red' if is_state('alarm_control_panel.house', 'armed_away') else 'green' }}";
  var SectionTitleCardEditor = createFormEditor({
    schema: () => [
      { name: "title", selector: { text: {} } },
      { name: "icon", selector: { icon: {} } },
      { name: "color", selector: { ui_color: {} } },
      STC_COLOR_TEMPLATE_FIELD,
      { name: "summary", selector: { template: {} } },
      { name: "link", selector: { navigation: {} } }
    ],
    labels: {
      title: "Title",
      icon: "Icon (optional)",
      link: "Tapping the title opens (optional page)",
      color: "Colour (for the icon)",
      color_template: STC_COLOR_TEMPLATE_LABEL,
      summary: "Summary on the right (optional template)"
    },
    helpers: {
      color_template: STC_COLOR_TEMPLATE_HELPER,
      summary: `A Home Assistant template, e.g. {{ states('vacuum.gregg') | title }}`
    }
  });
  var SectionTitleCard = class extends HTMLElement {
    setConfig(config) {
      if (!config.title) throw new Error("title required");
      const changed = !this.config || this.config.summary !== config.summary || this.config.color_template !== config.color_template;
      this.config = config;
      this._build();
      if (changed) this._subscribe();
    }
    set hass(hass) {
      const first = !this._hass;
      this._hass = hass;
      if (first) this._subscribe();
    }
    connectedCallback() {
      if (this._hass && !this._unsub) this._subscribe();
    }
    disconnectedCallback() {
      this._unsubscribe();
    }
    _build() {
      const c = this.config;
      const color = stcColor(this._liveColor || c.color);
      this.innerHTML = `
      <div style="display:flex; align-items:center; gap:10px; padding:2px 4px 2px 4px; min-height:40px;">
        ${c.icon ? iconHtml(c.icon, { size: "26px", style: `color:${color}; flex:none; transition:color .6s ease;`, cls: "stc-icon" }) : ""}
        <div class="stc-title" style="flex:1; min-width:0; font-size:1.6rem; font-weight:500; line-height:1.2; color:var(--primary-text-color); white-space:nowrap; overflow:hidden; text-overflow:ellipsis;"></div>
        <div class="stc-summary" style="flex:none; max-width:55%; font-size:0.9rem; color:var(--secondary-text-color); white-space:nowrap; overflow:hidden; text-overflow:ellipsis; text-align:right;"></div>
        ${c.link && !c.collapsible ? iconHtml("mdi:chevron-right", { size: "22px", style: "flex:none; margin-left:-4px; color:var(--secondary-text-color);" }) : ""}
        ${c.collapsible ? `<button class="stc-tog" type="button" aria-label="Show less" aria-expanded="true" style="--stc-c:${color};"></button>` : ""}
      </div>`;
      if (c.collapsible) {
        const st = document.createElement("style");
        st.textContent = STC_TOGGLE_CSS;
        this.prepend(st);
      }
      const row3 = this.querySelector("div");
      const toggle = () => this.dispatchEvent(new CustomEvent("stc-toggle", { bubbles: true }));
      this._tog = this.querySelector(".stc-tog");
      if (this._tog) {
        this._tog.addEventListener("click", (ev) => {
          ev.stopPropagation();
          toggle();
        });
        this.setOpen(this._open !== false);
      }
      if (c.link && !c.collapsible) {
        row3.style.cursor = "pointer";
        row3.setAttribute("role", "link");
        row3.tabIndex = 0;
        const go = () => kitNavigate(c.link);
        row3.addEventListener("click", go);
        row3.addEventListener("keydown", (ev) => ev.target === row3 && (ev.key === "Enter" || ev.key === " ") && go());
      } else if (c.collapsible) {
        row3.style.cursor = "pointer";
        row3.addEventListener("click", toggle);
      }
      this.querySelector(".stc-title").textContent = c.title;
      this._summaryEl = this.querySelector(".stc-summary");
      this._iconEl = this.querySelector(".stc-icon");
      if (this._summary) this._summaryEl.textContent = this._summary;
      hydrateIcons(this);
    }
    // − while open, + while compact (the upright bar turns in).
    setOpen(open) {
      this._open = open;
      if (!this._tog) return;
      this._tog.setAttribute("aria-expanded", String(open));
      this._tog.setAttribute("aria-label", open ? "Show less" : "Show more");
      this._tog.classList.toggle("stc-shut", !open);
    }
    _unsubscribe() {
      [this._unsub, this._unsubColor].forEach((p) => p && p.then((unsub) => unsub && unsub()).catch(() => {
      }));
      this._unsub = null;
      this._unsubColor = null;
    }
    // The summary, and the colour when it comes from a template. A new colour is
    // also announced (stc-color) for a Section Panel to tint its background.
    _subscribe() {
      this._unsubscribe();
      this._summary = "";
      if (this._summaryEl) this._summaryEl.textContent = "";
      if (!this.config || !this._hass) return;
      this._unsub = stcRender(this._hass, this.config.summary, (text) => {
        this._summary = text;
        if (this._summaryEl) this._summaryEl.textContent = text;
      });
      this._liveColor = null;
      this._unsubColor = stcRender(this._hass, this.config.color_template, (color) => {
        this._liveColor = color || null;
        const css = stcColor(this._liveColor || this.config.color);
        if (this._iconEl) stcSetInstantly(this._iconEl, "color", css, !this._colorShown);
        if (this._tog) this._tog.style.setProperty("--stc-c", css);
        this._colorShown = true;
        this.dispatchEvent(new CustomEvent("stc-color", { detail: css, bubbles: true }));
      });
    }
    getCardSize() {
      return 1;
    }
    getGridOptions() {
      return { columns: "full", rows: "auto" };
    }
    static getConfigElement() {
      return document.createElement(`section-title-card-editor${SUFFIX}`);
    }
    static getStubConfig() {
      return { title: "Lights", icon: "mdi:lightbulb", color: "amber" };
    }
  };
  function registerSectionTitleCard() {
    if (!customElements.get(`section-title-card-editor${SUFFIX}`)) {
      customElements.define(`section-title-card-editor${SUFFIX}`, SectionTitleCardEditor);
    }
    if (!customElements.get(`section-title-card${SUFFIX}`)) {
      customElements.define(`section-title-card${SUFFIX}`, SectionTitleCard);
    }
    window.customCards = window.customCards || [];
    window.customCards.push({
      type: `section-title-card${SUFFIX}`,
      name: `Section Title Card${LABEL}`,
      description: "A large section title with a coloured icon and a live summary",
      preview: true,
      documentationURL: "https://github.com/J45PER/church-drive-cards#readme"
    });
  }

  // src/section-panel-card.js
  var knownUser;
  function lastUser() {
    if (knownUser === void 0) {
      try {
        knownUser = localStorage.getItem("cd-user") || null;
      } catch (err) {
        knownUser = null;
      }
    }
    return knownUser;
  }
  function rememberUser(hass) {
    const id = hass && hass.user && hass.user.id;
    if (!id || id === knownUser) return;
    knownUser = id;
    try {
      localStorage.setItem("cd-user", id);
    } catch (err) {
    }
  }
  var PANEL_TRANSITION = "min-height 320ms cubic-bezier(.2,.8,.2,1)";
  var helpersPromise;
  function cardHelpers() {
    if (!helpersPromise) helpersPromise = window.loadCardHelpers ? window.loadCardHelpers() : Promise.reject(new Error("no card helpers"));
    return helpersPromise;
  }
  var AUTO_WIDTH = { "security-zone-card": 200, "picture-entity": 220, "picture-glance": 220, picture: 220, "camera-card": 220, tile: 200 };
  var PanelFields = createFormEditor({
    schema: () => [
      { name: "title", selector: { text: {} } },
      { name: "icon", selector: { icon: {} } },
      { name: "color", selector: { ui_color: {} } },
      STC_COLOR_TEMPLATE_FIELD,
      { name: "summary", selector: { template: {} } },
      { name: "link", selector: { navigation: {} } },
      { name: "link_label", selector: { text: {} } },
      {
        type: "expandable",
        name: "",
        title: "Open or compact",
        flatten: true,
        schema: [
          { name: "phone_start", selector: { select: { mode: "dropdown", options: [{ value: "compact", label: "Compact (one row per card)" }, { value: "open", label: "Open" }] } } },
          { name: "tablet_start", selector: { select: { mode: "dropdown", options: [{ value: "open", label: "Open" }, { value: "compact", label: "Compact (one row per card)" }] } } },
          { name: "open_when", selector: { template: {} } },
          { name: "collapsible", selector: { boolean: {} }, default: true }
        ]
      },
      {
        type: "expandable",
        name: "",
        title: "Layout on wider screens",
        flatten: true,
        schema: [
          { name: "card_width", selector: { number: { min: 0, max: 800, step: 10, mode: "box", unit_of_measurement: "px" } } },
          { name: "match_height", selector: { boolean: {} }, default: true },
          { name: "frosted_cards", selector: { boolean: {} }, default: true },
          { name: "full_width", selector: { select: { mode: "dropdown", options: [{ value: "auto", label: "Automatic" }, { value: "yes", label: "Always full width" }, { value: "no", label: "Never" }] } } },
          { name: "priority", selector: { select: { mode: "dropdown", options: [{ value: "auto", label: "Work it out from the cards" }, { value: "controls", label: "Controls (goes higher)" }, { value: "info", label: "Information only" }] } } }
        ]
      }
    ],
    labels: {
      title: "Title",
      icon: "Icon (optional)",
      link: "Go-to page (optional)",
      link_label: 'Go-to button says "Go to \u2026" (optional; default the title)',
      phone_start: "On phones, starts",
      tablet_start: "On tablets and computers, starts",
      open_when: "Opens by itself when (optional template)",
      collapsible: "Show the \u2304 to switch between open and compact",
      card_width: "Cards side by side when each can be at least (empty = automatic, 0 = always one per row)",
      match_height: "Line up this panel's bottom with the panels beside it",
      frosted_cards: "Frosted cards",
      full_width: "Full width across an Auto Layout",
      priority: "In an Auto Layout, counts as",
      color: "Colour (icon and panel)",
      color_template: STC_COLOR_TEMPLATE_LABEL,
      summary: "Summary on the right (optional template)"
    },
    helpers: {
      phone_start: "Each phone or tablet remembers what you last chose with the \u2304; this is where it starts. Phones are screens under 600px wide.",
      open_when: "E.g. {{ is_state('binary_sensor.back_door', 'on') }}. The panel opens while it's true, then goes back to how you left it.",
      card_width: "Automatic: zones 200px, cameras 220px, everything else 300px. Cards fill the panel width: e.g. cameras 2 or 3 across on a tablet, one per row on a phone.",
      frosted_cards: "The cards inside are see-through with a heavy blur of what's behind them (acrylic). Turn off for solid cards.",
      match_height: "When sections sit side by side, the last panel in a shorter section grows so its bottom lines up with its neighbours'.",
      priority: "Auto Layout puts panels with buttons and sliders above ones that only show information. Auto: lights, alarm, thermostats, fan, purifier, blinds and tiles with controls count as controls.",
      full_width: "Only inside an Auto Layout Card: the panel spans every column, with the panels before and after it balanced above and below. Automatic: a panel of 3 or more small cards (zones, cameras, tiles) goes full width when they would not fit side by side in one column.",
      color_template: STC_COLOR_TEMPLATE_HELPER,
      summary: `A Home Assistant template, e.g. {{ states('vacuum.gregg') | title }}`
    }
  });
  var SectionPanelCardEditor = class extends HTMLElement {
    setConfig(config) {
      this._config = config;
      this._render();
    }
    set hass(hass) {
      this._hass = hass;
      this._render();
    }
    set lovelace(lovelace) {
      this._lovelace = lovelace;
      if (this._stack) this._stack.lovelace = lovelace;
    }
    _emit(config) {
      this._config = config;
      this.dispatchEvent(new CustomEvent("config-changed", { detail: { config }, bubbles: true, composed: true }));
    }
    async _render() {
      if (!this._config || !this._hass) return;
      if (!this._fields) {
        this._fields = document.createElement(`section-panel-fields${SUFFIX}`);
        this._fields.addEventListener("config-changed", (ev) => {
          ev.stopPropagation();
          const { cards: cards2, ...fields2 } = ev.detail.config;
          this._emit({ ...this._config, ...fields2, cards: this._config.cards || [] });
        });
        const label = document.createElement("div");
        label.textContent = "Cards in this panel";
        label.style.cssText = "margin:20px 0 8px; font-weight:500;";
        this.append(this._fields, label);
      }
      const { cards, ...fields } = this._config;
      this._fields.hass = this._hass;
      this._fields.setConfig(fields);
      if (!this._stack && !this._stackLoading) {
        this._stackLoading = true;
        try {
          const helpers = await cardHelpers();
          helpers.createCardElement({ type: "vertical-stack", cards: [] });
          await customElements.whenDefined("hui-vertical-stack-card");
          this._stack = await customElements.get("hui-vertical-stack-card").getConfigElement();
          this._stack.addEventListener("config-changed", (ev) => {
            ev.stopPropagation();
            this._emit({ ...this._config, cards: ev.detail.config.cards || [] });
          });
          this.appendChild(this._stack);
        } catch (err) {
          const note = document.createElement("p");
          note.textContent = "The card list editor could not load; use the code editor to change the cards.";
          this.appendChild(note);
        }
        this._stackLoading = false;
      }
      if (this._stack) {
        this._stack.hass = this._hass;
        if (this._lovelace) this._stack.lovelace = this._lovelace;
        this._stack.setConfig({ type: "vertical-stack", cards: this._config.cards || [] });
      }
    }
  };
  var SectionPanelCard = class extends HTMLElement {
    setConfig(config) {
      if (!config.title) throw new Error("title required");
      this.config = config;
      this._built = false;
      this._fallback = null;
      this._build();
      if (this._hass) this._watchOpenWhen();
    }
    set hass(hass) {
      const first = !this._hass;
      this._hass = hass;
      if (this._title) this._title.hass = hass;
      (this._cards || []).forEach((card) => {
        card.hass = hass;
      });
      if (first) {
        rememberUser(hass);
        this._watchOpenWhen();
        this._apply();
      }
    }
    // ---- Open or compact.
    // Phones (under 600px) and bigger screens each start as the config says,
    // then remember the last choice on that device. An "open when" template
    // opens the panel while it's true.
    get _collapsible() {
      return this.config.collapsible !== false;
    }
    _device() {
      return window.innerWidth < 600 ? "phone" : "tablet";
    }
    _key() {
      const user = this._hass && this._hass.user && this._hass.user.id || lastUser() || "anyone";
      return `cd-panel:${user}:${location.pathname}:${this.config.title}:${this._device()}`;
    }
    _chosen() {
      try {
        const v = localStorage.getItem(this._key());
        if (v === "open" || v === "compact") return v;
      } catch (err) {
      }
      const start = this._device() === "phone" ? this.config.phone_start || "compact" : this.config.tablet_start || "open";
      return start === "compact" ? "compact" : "open";
    }
    _choose(mode) {
      try {
        localStorage.setItem(this._key(), mode);
      } catch (err) {
        this._fallback = mode;
      }
      this._slide(() => this._apply());
    }
    // Slide between the old and new height instead of jumping. The panel is
    // held at its old height while the cards redraw (they settle a frame or
    // two later), then eases to the height they really need, with the cards
    // fading in. Layout work elsewhere waits (cd-anim) so nothing else moves
    // mid-slide.
    _slide(change) {
      const panel = this._panelEl;
      const reduced = window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
      if (!panel || reduced || !this.isConnected) return change();
      this._endSlide();
      const before = panel.getBoundingClientRect().height;
      const token = this._slideToken = {};
      window.dispatchEvent(new CustomEvent("cd-anim", { detail: 1 }));
      this._sliding = true;
      Object.assign(panel.style, { transition: "none", height: `${before}px`, minHeight: "0px", overflow: "hidden" });
      change();
      if (this._grid && this._grid.animate) this._grid.animate([{ opacity: 0 }, { opacity: 1 }], { duration: 260, easing: "ease-out" });
      const frame = (fn) => requestAnimationFrame(() => requestAnimationFrame(fn));
      frame(() => {
        if (token !== this._slideToken) return;
        panel.style.height = "auto";
        const after = panel.getBoundingClientRect().height;
        panel.style.height = `${before}px`;
        void panel.offsetHeight;
        panel.style.transition = "height 340ms cubic-bezier(.2,.8,.2,1)";
        panel.style.height = `${after}px`;
        const finish = () => token === this._slideToken && this._endSlide();
        panel.addEventListener("transitionend", finish, { once: true });
        this._slideTimer = setTimeout(finish, 450);
      });
      return void 0;
    }
    _endSlide() {
      clearTimeout(this._slideTimer);
      if (!this._sliding) return;
      this._sliding = false;
      this._slideToken = null;
      const panel = this._panelEl;
      Object.assign(panel.style, { transition: PANEL_TRANSITION, height: "", overflow: "" });
      window.dispatchEvent(new CustomEvent("cd-anim", { detail: -1 }));
      window.dispatchEvent(new CustomEvent("cd-panels-changed"));
    }
    _mode() {
      if (!this._collapsible || this._alert || this._editing()) return "open";
      return this._fallback || this._chosen();
    }
    _editing() {
      return !!(this.editMode || this.preview);
    }
    _apply() {
      const compact = this._mode() === "compact";
      if (this._title && this._title.setOpen) this._title.setOpen(!compact);
      (this._cards || []).forEach((card) => {
        if (card.supportsCompact) {
          card.style.display = "";
          card.compact = compact;
        } else {
          card.style.display = compact ? "none" : "";
        }
      });
      if (this._panelEl) this._panelEl.style.gap = compact ? "8px" : "12px";
      if (this._goEl) this._goEl.style.display = compact ? "none" : "flex";
      this._layoutGrid(compact);
      window.dispatchEvent(new CustomEvent("cd-panels-changed"));
    }
    // How wide each card should be at least: card_width, or worked out from
    // the cards (small ones like zones and cameras sit side by side).
    _cardWidth() {
      const set = this.config.card_width;
      if (set != null && set !== "" && set !== "auto") return Number(set) || 0;
      const types = (this.config.cards || []).map((c) => String(c && c.type || "").replace(/^custom:/, "").replace(/-beta$/, ""));
      if (!types.length) return 300;
      return Math.max(...types.map((t) => AUTO_WIDTH[t] || 300));
    }
    // Cards side by side when each can be at least card_width wide.
    _layoutGrid(compact) {
      const g = this._grid;
      if (!g) return;
      const w = this._cardWidth();
      g.style.display = "grid";
      g.style.gap = compact ? "8px" : "12px";
      g.style.alignItems = w > 0 ? "stretch" : "start";
      if (!this.querySelector("style.spc-fill")) {
        const st = document.createElement("style");
        st.className = "spc-fill";
        st.textContent = ".spc-cards > * { display:flex; flex-direction:column; min-width:0; } .spc-cards > * > ha-card { flex:1 1 auto; }";
        this.prepend(st);
      }
      g.style.gridTemplateColumns = w > 0 ? `repeat(auto-fill, minmax(min(100%, ${w}px), 1fr))` : "1fr";
    }
    // ---- Matching heights with the sections beside this one.
    // Sections that sit side by side (same top) are lined up item by item:
    // the 1st panels share a height, then the 2nd, and so on; the last panel
    // in each section takes up whatever is left so every section ends level.
    // Each panel works out the whole row the same way and applies its share.
    _queueMatch() {
      cancelAnimationFrame(this._matchFrame);
      this._matchFrame = requestAnimationFrame(() => this._match());
    }
    _match() {
      const panel = this._panelEl;
      if (!panel || !this.isConnected || this._managed) return;
      const clear = () => {
        if (panel.style.minHeight) panel.style.minHeight = "";
      };
      if (this.config.match_height === false || window.innerWidth < 600 || this._mode() === "compact") return clear();
      const up = (el) => el.parentNode || el.getRootNode && el.getRootNode().host || null;
      let section = this;
      for (let i = 0; section && i < 14 && section.localName !== "hui-section"; i += 1) section = up(section);
      if (!section) return clear();
      const PANEL2 = "section-panel-card, section-panel-card-beta";
      const deep = (root, sel, depth = 0, out = []) => {
        if (!root || depth > 5 || !root.querySelectorAll) return out;
        root.querySelectorAll(sel).forEach((el) => out.push(el));
        root.querySelectorAll("*").forEach((el) => el.shadowRoot && deep(el.shadowRoot, sel, depth + 1, out));
        return out;
      };
      const top = (el) => el.getBoundingClientRect().top;
      const myTop = top(section);
      const row3 = [...section.getRootNode().querySelectorAll("hui-section")].filter((el) => Math.abs(top(el) - myTop) < 4);
      if (row3.length < 2) return clear();
      const NAV = "nav-bar-card, nav-bar-card-beta";
      const isNav = (el) => !!(el.querySelector(NAV) || el.shadowRoot && el.shadowRoot.querySelector(NAV));
      const itemsOf = (sec) => deep(sec, "hui-card").map((el) => {
        const p = el.matches && el.matches(PANEL2) ? el : el.querySelector(PANEL2) || el.shadowRoot && el.shadowRoot.querySelector(PANEL2) || null;
        const r = el.getBoundingClientRect();
        const stretch = !!(p && p._naturalHeight && !(p._mode && p._mode() === "compact") && p.config && p.config.match_height !== false);
        return { el, panel: p, stretch, top: r.top, bottom: r.bottom, h: p && p._naturalHeight ? p._naturalHeight() : r.height };
      }).filter((it) => it.bottom > it.top && !isNav(it.el));
      const secs = row3.map((sec) => ({ sec, items: itemsOf(sec) })).filter((x) => x.items.length);
      if (secs.length < 2) return clear();
      const shared = [];
      const most = Math.max(...secs.map((x) => x.items.length));
      for (let k = 0; k < most - 1; k += 1) {
        shared[k] = Math.max(0, ...secs.filter((x) => k < x.items.length - 1 && x.items[k].stretch).map((x) => x.items[k].h));
      }
      secs.forEach((x) => {
        x.heights = x.items.map((it, k) => k < x.items.length - 1 && it.stretch ? Math.max(it.h, shared[k]) : it.h);
        const gaps = x.items.slice(1).reduce((sum, it, k) => sum + Math.max(0, it.top - x.items[k].bottom), 0);
        x.end = x.items[0].top + x.heights.reduce((a, b) => a + b, 0) + gaps;
      });
      const end = Math.max(...secs.map((x) => x.end));
      let target = null;
      secs.forEach((x) => {
        const last = x.items.length - 1;
        if (x.items[last].stretch) x.heights[last] += end - x.end;
        x.items.forEach((it, k) => {
          if (it.panel === this) target = x.heights[k];
        });
      });
      const natural = this._naturalHeight();
      if (target == null || target - natural < 1 || target - natural > 1500) return clear();
      const px = `${Math.round(target)}px`;
      if (panel.style.minHeight !== px) panel.style.minHeight = px;
    }
    // The panel's height without any stretch: its cards plus its padding.
    _naturalHeight() {
      const panel = this._panelEl;
      const last = this._goEl && this._goEl.style.display !== "none" ? this._goEl : this._grid;
      const g = last && last.getBoundingClientRect();
      return g && panel ? g.bottom + 12 - panel.getBoundingClientRect().top : this.getBoundingClientRect().height;
    }
    _watchOpenWhen() {
      if (this._unsubOpen) this._unsubOpen.then((u) => u && u()).catch(() => {
      });
      this._unsubOpen = null;
      this._alert = false;
      if (!this.config.open_when || !this._hass || !this.isConnected) return;
      this._unsubOpen = stcRender(this._hass, this.config.open_when, (text) => {
        const t = String(text || "").trim().toLowerCase();
        const alert = !!t && !["0", "false", "off", "no", "none", "unknown", "unavailable"].includes(t);
        if (alert === this._alert) return;
        this._alert = alert;
        this._apply();
      });
    }
    _build() {
      const c = this.config;
      const color = stcColor(c.color);
      const frost = c.frosted_cards !== false ? `--cd-card-bg:color-mix(in srgb, var(--card-background-color, #1f2128) 74%, transparent); --cd-card-filter:${KIT_ACRYLIC_FILTER}; --ha-card-background:var(--cd-card-bg); --ha-card-backdrop-filter:var(--cd-card-filter);` : "";
      this.innerHTML = `
      <div class="spc-panel" style="position:relative; box-sizing:border-box; border-radius:24px; padding:12px; display:flex; flex-direction:column; gap:12px; isolation:isolate; transition:${PANEL_TRANSITION}; ${frost}">
        <div class="spc-bg" style="position:absolute; inset:0; border-radius:inherit; background:${color}; opacity:0.1; z-index:-1; pointer-events:none; transition:background-color .6s ease;"></div>
      </div>`;
      const panel = this.querySelector(".spc-panel");
      this._panelEl = panel;
      panel.addEventListener("stc-toggle", (ev) => {
        ev.stopPropagation();
        const next = this._mode() === "compact" ? "open" : "compact";
        this._fallback = null;
        this._choose(next);
      });
      panel.addEventListener("cd-expand", (ev) => {
        ev.stopPropagation();
        this._fallback = null;
        this._choose("open");
      });
      const bg = this.querySelector(".spc-bg");
      panel.addEventListener("stc-color", (ev) => {
        ev.stopPropagation();
        stcSetInstantly(bg, "background", ev.detail, !this._bgShown);
        panel.style.setProperty("--spc-c", ev.detail);
        this._bgShown = true;
      });
      this._title = document.createElement(`section-title-card${SUFFIX}`);
      this._grid = document.createElement("div");
      this._grid.className = "spc-cards";
      this._title.setConfig({ title: c.title, icon: c.icon, color: c.color, color_template: c.color_template, summary: c.summary, link: c.link, collapsible: this._collapsible });
      if (this._hass) this._title.hass = this._hass;
      panel.appendChild(this._title);
      panel.appendChild(this._grid);
      panel.style.setProperty("--spc-c", color);
      this._goEl = null;
      if (c.link) {
        const go = document.createElement("button");
        go.type = "button";
        go.className = "spc-go";
        go.style.cssText = "display:flex; align-items:center; justify-content:center; gap:6px; width:100%; min-height:40px; margin-top:auto; padding:0 12px; border:none; border-radius:14px; cursor:pointer; font:inherit; font-size:0.88rem; font-weight:600; color:var(--primary-text-color); background:color-mix(in srgb, var(--spc-c) 20%, var(--card-background-color, #1f2128)); -webkit-tap-highlight-color:transparent;";
        const label = document.createElement("span");
        label.textContent = `Go to ${c.link_label || c.title}`;
        go.appendChild(label);
        go.insertAdjacentHTML("beforeend", iconHtml("mdi:arrow-right", { size: "18px", style: "color:var(--spc-c); flex:none;" }));
        go.addEventListener("click", (ev) => {
          ev.stopPropagation();
          kitNavigate(c.link);
        });
        hydrateIcons(go);
        panel.appendChild(go);
        this._goEl = go;
      }
      this._layoutGrid(false);
      const token = this._token = {};
      this._cards = [];
      cardHelpers().then((helpers) => {
        if (token !== this._token) return;
        (c.cards || []).forEach((conf) => {
          const el = helpers.createCardElement(conf);
          if (this._hass) el.hass = this._hass;
          el.addEventListener("ll-rebuild", (ev) => {
            ev.stopPropagation();
            const fresh = helpers.createCardElement(conf);
            if (this._hass) fresh.hass = this._hass;
            el.replaceWith(fresh);
            this._cards[this._cards.indexOf(el)] = fresh;
            this._apply();
          });
          this._cards.push(el);
          this._grid.appendChild(el);
        });
        this._apply();
      }).catch(() => {
      });
    }
    connectedCallback() {
      requestAnimationFrame(() => this._spaceFromAbove());
      this._onResize = () => {
        const d = this._device();
        if (d !== this._lastDevice) {
          this._lastDevice = d;
          this._apply();
        }
      };
      this._lastDevice = this._device();
      window.addEventListener("resize", this._onResize);
      this._onPanels = () => this._queueMatch();
      if (!this._managed) window.addEventListener("cd-panels-changed", this._onPanels);
      if (window.ResizeObserver && !this._ro && !this._managed) {
        this._ro = new ResizeObserver(() => this._queueMatch());
        this._ro.observe(document.body);
        this._matchTimer = setInterval(() => this._queueMatch(), 3e3);
      }
      if (this._hass) this._watchOpenWhen();
      this._apply();
    }
    disconnectedCallback() {
      this._endSlide();
      window.removeEventListener("resize", this._onResize);
      window.removeEventListener("cd-panels-changed", this._onPanels);
      if (this._ro) this._ro.disconnect();
      this._ro = null;
      clearInterval(this._matchTimer);
      if (this._unsubOpen) this._unsubOpen.then((u) => u && u()).catch(() => {
      });
      this._unsubOpen = null;
    }
    // A panel right under another panel in the same section gets the same gap
    // as between section columns (32px; the section's own gap between cards is
    // 8px), so stacked panels read as separate groups.
    _spaceFromAbove() {
      if (this._managed) return;
      const up = (el) => el.parentNode || el.getRootNode && el.getRootNode().host || null;
      let wrap = this;
      for (let i = 0; i < 6 && wrap && wrap.localName !== "hui-card"; i += 1) wrap = up(wrap);
      let prev = null;
      for (let i = 0; i < 3 && wrap && !prev; i += 1) {
        prev = wrap.previousElementSibling;
        wrap = up(wrap);
      }
      const prevCard = prev && (prev.localName === "hui-card" ? prev : prev.querySelector && prev.querySelector("hui-card"));
      const type = prevCard && prevCard.config && String(prevCard.config.type || "");
      const stacked = !!type && type.includes("section-panel-card");
      this.style.display = "block";
      this.style.marginTop = stacked ? "calc(var(--ha-view-sections-column-gap, 32px) - 8px)" : "";
    }
    getCardSize() {
      return 1 + (this._cards || []).reduce((n, card) => n + (card.getCardSize ? Number(card.getCardSize()) || 1 : 1), 0);
    }
    getGridOptions() {
      return { columns: "full", rows: "auto" };
    }
    static getConfigElement() {
      return document.createElement(`section-panel-card-editor${SUFFIX}`);
    }
    static getStubConfig() {
      return { title: "Lights", icon: "mdi:lightbulb", color: "amber", cards: [] };
    }
  };
  function registerSectionPanelCard() {
    if (!customElements.get(`section-panel-fields${SUFFIX}`)) {
      customElements.define(`section-panel-fields${SUFFIX}`, PanelFields);
    }
    if (!customElements.get(`section-panel-card-editor${SUFFIX}`)) {
      customElements.define(`section-panel-card-editor${SUFFIX}`, SectionPanelCardEditor);
    }
    if (!customElements.get(`section-panel-card${SUFFIX}`)) {
      customElements.define(`section-panel-card${SUFFIX}`, SectionPanelCard);
    }
    window.customCards = window.customCards || [];
    window.customCards.push({
      type: `section-panel-card${SUFFIX}`,
      name: `Section Panel Card${LABEL}`,
      description: "A group of cards on a coloured panel with a large title, icon and live summary",
      preview: false,
      documentationURL: "https://github.com/J45PER/church-drive-cards#readme"
    });
  }

  // src/climate-zone-card.js
  var CZ_TYPES = {
    living: { name: "Living room", low: 19, high: 22 },
    bedroom: { name: "Bedroom", low: 16, high: 20 },
    office: { name: "Office / study", low: 19, high: 22 },
    hall: { name: "Hall / landing", low: 16, high: 21 },
    bathroom: { name: "Bathroom", low: 20, high: 24 },
    kitchen: { name: "Kitchen", low: 17, high: 21 }
  };
  var CZ_HUMIDITY = { humidity_low: 40, humidity_high: 60, humidity_dry: 30 };
  var FREEZING = "#e3f2fd";
  function czGuessType(r) {
    const s = `${r.name || ""} ${r.icon || ""}`.toLowerCase();
    if (/bed/.test(s)) return "bedroom";
    if (/office|study|desk|chair/.test(s)) return "office";
    if (/landing|hall|entrance|stair|coat|porch|corridor/.test(s)) return "hall";
    if (/bath|en-?suite|shower|toilet|wc/.test(s)) return "bathroom";
    if (/kitchen|utility/.test(s)) return "kitchen";
    return "living";
  }
  function czRange(r) {
    const t = CZ_TYPES[r.type] || CZ_TYPES[czGuessType(r)];
    const low = r.low != null && r.low !== "" ? Number(r.low) : t.low;
    const high = r.high != null && r.high !== "" ? Number(r.high) : t.high;
    return { low, high: Math.max(high, low + 0.5), typeName: t.name };
  }
  function czStops(low, high) {
    const mid = (low + high) / 2;
    const list = [
      [0.01, "#1a3f9e"],
      [low - 6, "#1e5fd6"],
      [low - 3, "#42a5f5"],
      [low - 1, "#26c6da"],
      [low, "#26a69a"],
      [mid, "#66bb6a"],
      [high, "#c0ca33"],
      [high + 1, "#ffca28"],
      [high + 2.5, "#ffa726"],
      [high + 4, "#ef5350"],
      [high + 6, "#b71c1c"]
    ];
    return list.filter((s, i) => i === 0 || s[0] > list[i - 1][0]);
  }
  var hex = (h) => [1, 3, 5].map((i) => parseInt(h.slice(i, i + 2), 16));
  function czColour(t, low, high) {
    if (t == null || isNaN(t)) return KIT_COLOR.off;
    if (t <= 0) return FREEZING;
    const s = czStops(low, high);
    if (t <= s[0][0]) return s[0][1];
    for (let i = 0; i < s.length - 1; i++) {
      const [a, ca] = s[i], [b, cb] = s[i + 1];
      if (t <= b) {
        const f = (t - a) / (b - a), A = hex(ca), B = hex(cb);
        return `rgb(${A.map((v, k) => Math.round(v + (B[k] - v) * f)).join(",")})`;
      }
    }
    return s[s.length - 1][1];
  }
  function czWord(t, low, high) {
    if (t == null) return "";
    if (t <= 0) return "Freezing";
    if (t < low - 3) return "Cold";
    if (t < low) return "Cool";
    if (t <= high) return "Comfortable";
    if (t <= high + 2.5) return "Warm";
    return "Hot";
  }
  var czOff = (t, low, high) => t == null ? 0 : t < low ? low - t : t > high ? t - high : 0;
  function czHumColour(h, low, high, dry) {
    if (h == null) return KIT_COLOR.humidity;
    const mix = (a, b, f) => {
      const A = hex(a), B = hex(b);
      f = Math.max(0, Math.min(1, f));
      return `rgb(${A.map((v, k) => Math.round(v + (B[k] - v) * f)).join(",")})`;
    };
    if (h < low) return mix(KIT_COLOR.humidity, "#ede4ff", (low - h) / Math.max(1, low - dry));
    if (h > high) return mix(KIT_COLOR.humidity, "#6a1bff", (h - high) / 10);
    return KIT_COLOR.humidity;
  }
  var avg = (list) => list.length ? list.reduce((a, b) => a + b, 0) / list.length : null;
  var CZ_DEMO = {
    ground: {
      title: "Ground Floor",
      rooms: [
        { name: "Living Room", icon: "mdi:sofa", type: "living", t: 21.8, h: 61, ts: [19.1, 18.9, 18.8, 18.9, 19.6, 20.4, 20.9, 20.6, 21, 21.4, 21.6, 21.8], hs: [59, 60, 60, 61, 60, 58, 56, 57, 58, 60, 62, 61] },
        { name: "Entrance", icon: "mdi:coat-rack", type: "hall", t: 20.9, ts: [18.4, 18.2, 18.1, 18.3, 18.9, 19.6, 20.1, 20.3, 20.5, 20.7, 20.8, 20.9] }
      ]
    },
    top: {
      title: "Hayley's Floor",
      rooms: [
        { name: "Hayley's Bedroom", icon: "mdi:bed-king", type: "bedroom", t: 23, h: 48.5, ts: [20.9, 20.7, 20.6, 20.9, 21.4, 22, 22.4, 22.6, 22.8, 22.9, 23, 23], hs: [51, 52, 52, 51, 50, 48, 47, 47.5, 48, 49, 48.5, 48.5] },
        { name: "Hayley's Office", icon: "mdi:chair-rolling", type: "office", t: 22.2, h: 58, ts: [19.8, 19.6, 19.5, 19.7, 20.2, 20.8, 21.3, 21.5, 21.8, 22, 22.1, 22.2], hs: [59, 60, 60, 59, 58, 56, 55, 56, 57, 58, 58, 58] },
        { name: "Hayley's Landing", icon: "mdi:stairs", type: "hall", t: 17.6, ts: [16.4, 16.2, 16.1, 16.3, 16.8, 17.2, 17.5, 17.6, 17.4, 17.5, 17.6, 17.6] }
      ]
    },
    cold: {
      title: "Cold weather",
      rooms: [
        { name: "Garage", icon: "mdi:garage", type: "hall", t: -1.5, ts: [4, 3, 2, 1, 0.5, 0, -0.5, -1, -1.2, -1.4, -1.5, -1.5] },
        { name: "Spare Bedroom", icon: "mdi:bed", type: "bedroom", t: 13.2, h: 68, ts: [16, 15.5, 15, 14.6, 14.2, 14, 13.8, 13.6, 13.5, 13.3, 13.2, 13.2], hs: [62, 63, 64, 65, 66, 66, 67, 67, 68, 68, 68, 68] }
      ]
    }
  };
  var TYPE_OPTIONS = Object.entries(CZ_TYPES).map(([value, t]) => ({ value, label: `${t.name} (${t.low}\u2013${t.high}\xB0)` }));
  var ClimateZoneCardEditor = createFormEditor({
    schema: (config) => [
      { name: "title", selector: { text: {} } },
      ...config.demo ? [] : [
        {
          name: "rooms",
          selector: {
            object: {
              multiple: true,
              label_field: "name",
              fields: {
                name: { label: "Name", required: true, selector: { text: {} } },
                temperature: { label: "Temperature sensor", selector: { entity: { domain: "sensor", device_class: "temperature" } } },
                humidity: { label: "Humidity sensor (optional)", selector: { entity: { domain: "sensor", device_class: "humidity" } } },
                type: { label: "Room type (sets the comfortable range)", selector: { select: { mode: "dropdown", options: TYPE_OPTIONS } } },
                low: { label: "Comfortable from (optional, overrides the type)", selector: { number: { min: 0, max: 35, step: 0.5, mode: "box", unit_of_measurement: "\xB0" } } },
                high: { label: "Comfortable to (optional, overrides the type)", selector: { number: { min: 0, max: 40, step: 0.5, mode: "box", unit_of_measurement: "\xB0" } } },
                icon: { label: "Icon (optional)", selector: { icon: {} } },
                note: { label: "Small text under the name (optional)", selector: { text: {} } }
              }
            }
          }
        }
      ],
      {
        type: "expandable",
        name: "",
        title: "Graphs",
        flatten: true,
        schema: [
          { name: "show_graphs", selector: { boolean: {} }, default: true },
          { name: "show_limits", selector: { boolean: {} }, default: true },
          { name: "show_humidity_graph", selector: { boolean: {} }, default: true },
          { name: "smooth_graphs", selector: { boolean: {} }, default: true },
          { name: "hours", selector: { number: { min: 1, max: 168, mode: "box", unit_of_measurement: "hours" } } }
        ]
      },
      {
        type: "expandable",
        name: "",
        title: "Humidity limits (%)",
        flatten: true,
        schema: Object.keys(CZ_HUMIDITY).map((name) => ({ name, selector: { number: { min: 0, max: 100, mode: "box", unit_of_measurement: "%" } } }))
      },
      {
        type: "expandable",
        name: "",
        title: "Demo mode (pretend rooms, for Design Presets)",
        flatten: true,
        schema: [
          { name: "demo", selector: { boolean: {} } },
          {
            name: "demo_floor",
            selector: {
              select: {
                mode: "dropdown",
                options: [
                  { value: "ground", label: "Ground floor" },
                  { value: "top", label: "Top floor (warm bedroom, cool landing)" },
                  { value: "cold", label: "Cold weather (freezing garage)" }
                ]
              }
            }
          }
        ]
      }
    ],
    labels: {
      title: "Title (e.g. the floor)",
      rooms: "Rooms and sensors",
      show_graphs: "A graph for each room",
      show_limits: "Comfortable range and humidity limits on the graphs",
      show_humidity_graph: "Humidity line on the graphs",
      smooth_graphs: "Smooth the graphs (averages jumpy sensor readings)",
      hours: "Graph length",
      humidity_low: "Comfortable humidity from",
      humidity_high: "Comfortable humidity to (above gets deeper purple: mould risk)",
      humidity_dry: "Too dry below (palest purple)",
      demo: "Use pretend rooms instead of real sensors",
      demo_floor: "Pretend floor"
    },
    helpers: {
      rooms: "Colours follow each room\u2019s comfortable range: teal to lime inside it, blue below, orange to red above, and ice-white with a warning at 0\xB0 and below.",
      hours: "Default 24.",
      humidity_low: "Defaults: 40\u201360% comfortable. Humidity stays purple: paler as it gets drier, deeper above 60%."
    }
  });
  var ClimateZoneCard = class extends HTMLElement {
    setConfig(config) {
      if (!config.demo && !Array.isArray(config.rooms)) throw new Error("rooms required (or set demo: true)");
      this.config = config;
      this._built = false;
      const hours = Number(config.hours) || 24;
      const ids = (config.rooms || []).flatMap((r) => [r.temperature, r.humidity]).filter(Boolean);
      this._hist = config.demo ? null : new KitHistory(this, ids, hours);
      this._sig = null;
    }
    set hass(hass) {
      this._hass = hass;
      this._render();
    }
    _hum(k) {
      const v = this.config[k];
      return v != null && v !== "" ? Number(v) : CZ_HUMIDITY[k];
    }
    _rooms() {
      const c = this.config;
      if (c.demo) {
        const d = CZ_DEMO[c.demo_floor] || CZ_DEMO.ground;
        return d.rooms.map((r) => ({ ...r, ...czRange(r), tPts: kitDemoSeries(r.ts), hPts: r.hs ? kitDemoSeries(r.hs) : null, note: "", hasHumidity: !!r.hs }));
      }
      const s = (id) => id && this._hass && this._hass.states[id];
      const data = this._hist && this._hist.data || {};
      return (c.rooms || []).map((r) => {
        const ts = s(r.temperature), hs = s(r.humidity);
        return {
          name: r.name || ts && ts.attributes.friendly_name || r.temperature,
          icon: r.icon || ts && ts.attributes.icon || "mdi:thermometer",
          note: r.note || "",
          ...czRange(r),
          t: kitNum(ts),
          h: kitNum(hs),
          tPts: data[r.temperature] || null,
          hPts: r.humidity ? data[r.humidity] || null : null,
          entity: r.temperature,
          hasHumidity: !!r.humidity
        };
      });
    }
    // The room's graph: comfortable band, temperature coloured by the scale,
    // humidity with dotted limits. Each series on its own scale.
    _graph(r, i, hours, limits, showHum, smooth) {
      const W = 300, H = 56, now = Date.now(), from = now - hours * 36e5;
      const x = (t) => (Math.max(from, t) - from) / (now - from) * W;
      const scrub = [];
      const tRaw = (r.tPts || []).filter((p) => !isNaN(p[1]));
      if (r.t != null) tRaw.push([now, r.t]);
      const tp = smooth ? kitSmooth(tRaw, from, now) : tRaw;
      if (tp.length < 2) return "";
      const tv = tp.map((p) => p[1]);
      let lo = Math.min(...tv), hi = Math.max(...tv);
      if (limits) {
        lo = Math.min(lo, r.low - 1);
        hi = Math.max(hi, r.high + 1);
      }
      lo -= 0.3;
      hi += 0.3;
      const y = (v) => H - 3 - (v - lo) / (hi - lo || 1) * (H - 6);
      const id = `cz${i}${Math.random().toString(36).slice(2, 7)}`;
      let stops = "";
      for (let k = 0; k <= 20; k++) stops += `<stop offset="${k * 5}%" stop-color="${czColour(hi - (hi - lo) * k / 20, r.low, r.high)}"></stop>`;
      let svg2 = `<defs><linearGradient id="${id}" x1="0" y1="0" x2="0" y2="${H}" gradientUnits="userSpaceOnUse">${stops}</linearGradient></defs>`;
      if (limits) {
        svg2 += `<rect x="0" y="${y(r.high).toFixed(1)}" width="${W}" height="${(y(r.low) - y(r.high)).toFixed(1)}" fill="${KIT_COLOR.comfy}" fill-opacity="0.12"></rect>`;
        [r.low, r.high].forEach((v) => {
          svg2 += `<line x1="0" x2="${W}" y1="${y(v).toFixed(1)}" y2="${y(v).toFixed(1)}" stroke="${KIT_COLOR.comfy}" stroke-opacity="0.55" stroke-dasharray="4 3" vector-effect="non-scaling-stroke"></line>`;
        });
      }
      if (showHum && r.hPts) {
        const hRaw = r.hPts.filter((p) => !isNaN(p[1]));
        if (r.h != null) hRaw.push([now, r.h]);
        const hp = smooth ? kitSmooth(hRaw, from, now) : hRaw;
        if (hp.length >= 2) {
          const hv = hp.map((p) => p[1]);
          const hLow = this._hum("humidity_low"), hHigh = this._hum("humidity_high");
          const hl = Math.min(...hv, limits ? hLow - 5 : Infinity) - 3, hh = Math.max(...hv, limits ? hHigh + 5 : -Infinity) + 3;
          const yh = (v) => H - 3 - (v - hl) / (hh - hl || 1) * (H - 6);
          if (limits) {
            [hLow, hHigh].forEach((v) => {
              svg2 += `<line x1="0" x2="${W}" y1="${yh(v).toFixed(1)}" y2="${yh(v).toFixed(1)}" stroke="${KIT_COLOR.humidity}" stroke-opacity="0.6" stroke-dasharray="1.5 3" vector-effect="non-scaling-stroke"></line>`;
            });
          }
          const humOf = (v) => czHumColour(v, this._hum("humidity_low"), this._hum("humidity_high"), this._hum("humidity_dry"));
          scrub.push({ pts: hp, raw: hRaw, linear: smooth, lo: hl, hi: hh, color: humOf(r.h), colourOf: humOf, format: (v) => `${Math.round(v)}%` });
          svg2 += `<path d="${kitPath(hp.map((p) => [x(p[0]), yh(p[1])]), smooth)}" fill="none" stroke="${KIT_COLOR.humidity}" stroke-width="1.6" vector-effect="non-scaling-stroke"></path>`;
        }
      }
      scrub.unshift({ pts: tp, raw: tRaw, linear: smooth, lo, hi, color: czColour(r.t, r.low, r.high), colourOf: (v) => czColour(v, r.low, r.high), format: (v) => `${v.toFixed(1)}\xB0` });
      this._scrub[i] = { from, now, height: H, series: scrub };
      const d = kitPath(tp.map((p) => [x(p[0]), y(p[1])]), smooth);
      svg2 += `<path d="${d} L${W},${H} L0,${H} Z" fill="url(#${id})" fill-opacity="0.14"></path>`;
      svg2 += `<path d="${d}" fill="none" stroke="url(#${id})" stroke-width="2.2" vector-effect="non-scaling-stroke"></path>`;
      return `<svg viewBox="0 0 ${W} ${H}" preserveAspectRatio="none" style="display:block; width:100%; height:${H}px;" role="img" aria-label="${r.name}: last ${hours} hours">${svg2}</svg>`;
    }
    _render() {
      if (!this._hass || !this.config) return;
      const c = this.config;
      if (!this._built && !this._compact) {
        this.innerHTML = kitShell(`<div class="cz-rooms" style="display:flex; flex-direction:column; gap:10px;"></div>`);
        this._box = this.querySelector(".cz-rooms");
        this._built = true;
      }
      if (this._hist && this._hist.due() && !this._compact) this._hist.load(this._hass);
      const rooms = this._rooms();
      const showGraphs = c.show_graphs !== false;
      const showHum = c.show_humidity_graph !== false;
      const limits = c.show_limits !== false;
      const temps = rooms.map((r) => r.t).filter((v) => v != null);
      const hums = rooms.map((r) => r.h).filter((v) => v != null);
      const t = avg(temps), h = avg(hums);
      const worst = rooms.filter((r) => r.t != null).sort((a, b) => czOff(b.t, b.low, b.high) - czOff(a.t, a.low, a.high))[0];
      const titleColour = !worst ? KIT_COLOR.off : worst.t <= 0 ? FREEZING : czOff(worst.t, worst.low, worst.high) ? czColour(worst.t, worst.low, worst.high) : KIT_COLOR.comfy;
      const title = c.title || (c.demo ? (CZ_DEMO[c.demo_floor] || CZ_DEMO.ground).title : "Climate");
      if (this._compact) {
        return kitCompact(this, {
          name: title,
          color: titleColour,
          value: t != null ? `${t.toFixed(1)}\xB0` : "",
          status: h != null ? `${Math.round(h)}%` : "",
          chips: rooms.map((r) => ({ label: `${r.name} ${r.t != null ? `${r.t.toFixed(1)}\xB0` : "\u2013"}`, color: r.t != null && r.t <= 0 ? FREEZING : czColour(r.t, r.low, r.high) }))
        });
      }
      kitHead(this, title, [t != null ? `${t.toFixed(1)}\xB0` : "", h != null ? `${Math.round(h)}%` : ""].filter(Boolean).join(" \xB7 ") + (c.demo ? " \xB7 demo" : ""), titleColour);
      const sig = JSON.stringify([rooms.map((r) => [r.name, r.icon, r.note, r.t, r.h, r.low, r.high]), showGraphs, showHum, limits, this._hist && this._hist.at, c]);
      if (sig === this._sig) return;
      this._sig = sig;
      const hours = Number(c.hours) || 24;
      this._scrub = [];
      const hLow = this._hum("humidity_low"), hHigh = this._hum("humidity_high"), hDry = this._hum("humidity_dry");
      this._box.innerHTML = rooms.map((r, i) => {
        const colour = czColour(r.t, r.low, r.high);
        const freezing = r.t != null && r.t <= 0;
        const humWarn = r.h != null && (r.h > hHigh || r.h < hDry);
        const humColour = czHumColour(r.h, hLow, hHigh, hDry);
        const graph = showGraphs ? this._graph(r, i, hours, limits, showHum, c.smooth_graphs !== false) : "";
        const hRange = showGraphs && showHum && r.hPts ? kitRange(r.hPts, r.h, 0, "%") : "";
        return `<div class="cz-room" data-i="${i}" style="display:flex; flex-direction:column; gap:5px;${i ? " border-top:1px solid var(--divider-color, rgba(127,127,127,0.22)); padding-top:10px;" : ""}">
          ${freezing ? `<div style="display:flex; align-items:center; gap:8px; padding:7px 10px; border-radius:10px; background:${FREEZING}; color:#0b2233; font-size:0.85rem; font-weight:600;">${iconHtml("mdi:snowflake", { size: "20px" })}Freezing: pipes at risk</div>` : ""}
          <div style="display:flex; align-items:center; gap:10px;">
            ${iconHtml(freezing ? "mdi:snowflake" : r.icon, { size: "22px", style: `color:${colour}; flex:none;` })}
            <div style="flex:1; min-width:0;">
              <div class="cz-name" style="font-size:0.95rem; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;"></div>
              <div class="cz-note ck-sub" style="font-size:0.72rem; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;"></div>
            </div>
            ${r.h != null ? `<span style="font-size:0.85rem; color:${humColour}; font-weight:${humWarn ? 700 : 400}; font-variant-numeric:tabular-nums;" title="${humWarn ? r.h > hHigh ? "Humid: mould risk" : "Too dry" : "Humidity"}">${Math.round(r.h * 10) / 10}%</span>` : ""}
            <b style="min-width:54px; text-align:right; font-size:1.15rem; font-variant-numeric:tabular-nums; color:${colour};">${r.t != null ? `${r.t.toFixed(1)}\xB0` : "\u2013"}</b>
          </div>
          ${graph}
          ${graph ? `<div style="display:flex; justify-content:space-between; gap:8px; font-size:0.72rem; color:var(--secondary-text-color);">
              <span style="color:${KIT_COLOR.comfy};">${limits ? `\u25AD ${r.low}\u2013${r.high}\xB0` : kitRange(r.tPts, r.t, 1, "\xB0")}</span>
              ${hRange ? `<span style="color:${KIT_COLOR.humidity};">${limits ? `\u2504 ${hLow}\u2013${hHigh}%` : hRange}</span>` : ""}
              <span>last ${hours} h</span>
            </div>` : ""}
          ${showGraphs && !graph && !c.demo ? `<div class="ck-sub" style="font-size:0.72rem;">${this._hist && this._hist.data ? "No history yet" : "Loading history\u2026"}</div>` : ""}
        </div>`;
      }).join("");
      this._box.querySelectorAll(".cz-room").forEach((el) => {
        const r = rooms[Number(el.dataset.i)];
        el.querySelector(".cz-name").textContent = r.name;
        el.querySelector(".cz-note").textContent = [r.note || r.typeName, czWord(r.t, r.low, r.high)].filter(Boolean).join(" \xB7 ");
        const svg2 = el.querySelector("svg");
        if (svg2 && this._scrub[Number(el.dataset.i)]) kitScrub(svg2, this._scrub[Number(el.dataset.i)]);
      });
      hydrateIcons(this);
    }
    getCardSize() {
      const n = this.config.demo ? 2 : (this.config.rooms || []).length;
      return 1 + n * (this.config.show_graphs === false ? 1 : 2);
    }
    getGridOptions() {
      return { columns: 12, min_columns: 6, rows: "auto" };
    }
    static getConfigElement() {
      return document.createElement(`climate-zone-card-editor${SUFFIX}`);
    }
    static getStubConfig() {
      return { demo: true, title: "Ground Floor" };
    }
  };
  kitCompactable(ClimateZoneCard, (card) => {
    card._built = false;
    card._sig = null;
  });
  function registerClimateZoneCard() {
    if (!customElements.get(`climate-zone-card-editor${SUFFIX}`)) customElements.define(`climate-zone-card-editor${SUFFIX}`, ClimateZoneCardEditor);
    if (!customElements.get(`climate-zone-card${SUFFIX}`)) customElements.define(`climate-zone-card${SUFFIX}`, ClimateZoneCard);
    window.customCards = window.customCards || [];
    window.customCards.push({
      type: `climate-zone-card${SUFFIX}`,
      name: `Climate Zone Card${LABEL}`,
      description: "A floor or zone: temperature and humidity per room, coloured against each room\u2019s comfortable range, with 24-hour graphs",
      preview: true,
      documentationURL: "https://github.com/J45PER/church-drive-cards#readme"
    });
  }

  // src/climate-card.js
  var CC_MAX_QUICK = 5;
  var CC_RING = 84;
  var CC_HUMIDITY = "#b388ff";
  var CC_HISTORY_HOURS = 24;
  var CC_MODES = {
    off: { name: "Off", icon: "mdi:power", color: "#8b919c", colors: ["#5d636e", "#3a3f48"] },
    heat: { name: "Heat", icon: "mdi:fire", color: "#ff7a2f", colors: ["#ff9a4d", "#e2531d"] },
    cool: { name: "Cool", icon: "mdi:snowflake", color: "#3aa0ff", colors: ["#63b3ff", "#1f6fd1"] },
    heat_cool: { name: "Heat/Cool", icon: "mdi:sun-snowflake-variant", color: "#ffa94d", colors: ["#ffb36b", "#3f8fe0"] },
    auto: { name: "Auto", icon: "mdi:thermostat-auto", color: "#a594de", colors: ["#a594de", "#6a4fb3"] },
    dry: { name: "Dry", icon: "mdi:water-percent", color: "#d4a72c", colors: ["#e3c35a", "#a8841b"] },
    fan_only: { name: "Fan", icon: "mdi:fan", color: "#26c6da", colors: ["#4dd8e8", "#12879a"] }
  };
  var CC_PRESETS = {
    none: { name: "None", icon: "mdi:circle-off-outline", color: "#8b919c" },
    eco: { name: "Eco", icon: "mdi:leaf", color: "#4caf50", colors: ["#6fcf73", "#2e7d32"] },
    boost: { name: "Boost", icon: "mdi:rocket-launch", color: "#e53935", colors: ["#ff7b7b", "#c62828"] },
    away: { name: "Away", icon: "mdi:home-export-outline", color: "#90a4ae", colors: ["#90a4ae", "#546e7a"] },
    sleep: { name: "Sleep", icon: "mdi:power-sleep", color: "#7e6fd6", colors: ["#8a7de0", "#3b2f86"] },
    comfort: { name: "Comfort", icon: "mdi:sofa", color: "#ffa94d", colors: ["#ffb36b", "#d9731f"] },
    home: { name: "Home", icon: "mdi:home", color: "#64b5f6", colors: ["#7fb3d5", "#3b6e99"] }
  };
  var CC_COLOR = { heating: "#ff7a2f", cooling: "#3aa0ff", drying: "#d4a72c", fan: "#26c6da", eco: "#4caf50", off: "#8b919c" };
  var CC_ROWS = {
    show_controls: true,
    show_mode: true,
    show_preset: true,
    show_fan: true,
    show_swing: true,
    show_quick: true,
    show_temperature_history: false,
    show_humidity_history: false
  };
  var row = (config, key) => config[key] != null ? !!config[key] : CC_ROWS[key];
  var cap = (text) => String(text || "").replace(/_/g, " ").replace(/^./, (c) => c.toUpperCase());
  var noPreset = (p) => p == null || p === "" || p === "none";
  var deg = (n) => `${Number(n).toFixed(1)}\xB0`;
  var esc = (text) => String(text).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[c]);
  function ccDemoState(config) {
    const off = config.demo_mode === "off";
    return {
      entity_id: "climate.demo",
      state: off ? "off" : "heat",
      attributes: {
        friendly_name: "Downstairs",
        hvac_modes: ["off", "heat"],
        preset_modes: ["none", "eco"],
        preset_mode: "none",
        current_temperature: 20.2,
        temperature: off ? null : 21.5,
        current_humidity: 57,
        hvac_action: off ? "off" : "heating",
        min_temp: 10,
        max_temp: 32,
        target_temp_step: 0.5
      }
    };
  }
  function ccDemoHistory() {
    const now = Date.now();
    const temps = [19.1, 18.9, 18.8, 18.7, 18.9, 19.6, 20.4, 20.9, 20.6, 20.1, 19.9, 20.2, 20.6, 20.9, 21.1, 20.8, 20.3, 19.8, 19.5, 19.7, 20, 20.2];
    const hums = [59, 60, 60, 61, 61, 60, 58, 56, 55, 56, 57, 58, 59, 62, 66, 63, 60, 59, 58, 57, 57, 57];
    const at = (i) => now - CC_HISTORY_HOURS * 36e5 * (temps.length - 1 - i) / (temps.length - 1);
    return {
      temperature: temps.map((v, i) => [at(i), v]),
      humidity: hums.map((v, i) => [at(i), v]),
      target: temps.map((_, i) => [at(i), 21.5])
    };
  }
  function ccDefaultQuick(st, config = {}) {
    const a = st && st.attributes || {};
    const modes = (a.hvac_modes || []).filter((m) => CC_MODES[m]);
    const presets = (a.preset_modes || []).filter((p) => !noPreset(p));
    const main = modes.includes("cool") ? "cool" : modes.includes("heat") ? "heat" : null;
    if (row(config, "show_mode") && main) {
      const temps = main === "heat" ? [18, 20, 21] : [24, 22, 20];
      const names = main === "heat" ? ["Night", "Day", "Comfort"] : ["Sleep", "Cool", "Cold"];
      const icons = main === "heat" ? ["mdi:weather-night", "mdi:sofa", "mdi:fire"] : ["mdi:weather-night", "mdi:snowflake", "mdi:snowflake-alert"];
      const out2 = temps.map((t, i) => ({ name: `${names[i]} ${t}\xB0`, icon: icons[i], hvac_mode: main, temperature: t }));
      if (presets.includes("eco")) out2.push({ name: "Eco", preset_mode: "eco" });
      return out2.slice(0, CC_MAX_QUICK);
    }
    const ordered = [...modes.filter((m) => m === "off"), ...modes.filter((m) => m !== "off")];
    const out = ordered.map((m) => ({ name: CC_MODES[m].name, hvac_mode: m }));
    presets.forEach((p) => out.push({ name: (CC_PRESETS[p] || {}).name || cap(p), preset_mode: p }));
    return out.slice(0, CC_MAX_QUICK);
  }
  var ClimateCardEditor = createFormEditor({
    fill: (config, hass) => {
      if (config.quick_settings !== "reset" && config.quick_settings != null) return config;
      const st = config.demo ? ccDemoState(config) : hass && config.entity && hass.states[config.entity];
      if (!st) return config;
      return { ...config, quick_settings: ccDefaultQuick(st, config) };
    },
    buttons: [{ label: "Reset", field: "quick_settings", variant: "danger", apply: (config) => ({ ...config, quick_settings: "reset" }) }],
    alerts: [
      {
        field: "quick_settings",
        text: (config) => Array.isArray(config.quick_settings) && config.quick_settings.length > CC_MAX_QUICK ? `Only the first ${CC_MAX_QUICK} quick settings show (one row).` : ""
      }
    ],
    schema: (config, hass) => {
      const st = config.demo ? ccDemoState(config) : hass && config.entity && hass.states[config.entity];
      const a = st && st.attributes || {};
      const modeOptions = (a.hvac_modes && a.hvac_modes.length ? a.hvac_modes : Object.keys(CC_MODES)).map((m) => ({
        value: m,
        label: (CC_MODES[m] || {}).name || cap(m)
      }));
      const presetOptions = [{ value: "none", label: "None (clear the preset)" }].concat(
        (a.preset_modes || Object.keys(CC_PRESETS)).filter((p) => !noPreset(p)).map((p) => ({ value: p, label: (CC_PRESETS[p] || {}).name || cap(p) }))
      );
      const toggle = (name) => ({ name, selector: { boolean: {} }, default: CC_ROWS[name] });
      return [
        ...config.demo ? [] : [{ name: "entity", selector: { entity: { domain: "climate" } } }],
        { name: "name", selector: { text: {} } },
        {
          type: "expandable",
          name: "",
          title: "Rows to show",
          flatten: true,
          schema: [
            toggle("show_temperature_history"),
            toggle("show_humidity_history"),
            { name: "smooth_graphs", selector: { boolean: {} }, default: true },
            { name: "show_limits", selector: { boolean: {} }, default: true },
            { name: "room_type", selector: { select: { mode: "dropdown", options: Object.entries(CZ_TYPES).map(([value, t]) => ({ value, label: `${t.name} (${t.low}\u2013${t.high}\xB0)` })) } } },
            { name: "comfort_low", selector: { number: { min: 0, max: 35, step: 0.5, mode: "box", unit_of_measurement: "\xB0" } } },
            { name: "comfort_high", selector: { number: { min: 0, max: 40, step: 0.5, mode: "box", unit_of_measurement: "\xB0" } } },
            { name: "humidity_low", selector: { number: { min: 0, max: 100, mode: "box", unit_of_measurement: "%" } } },
            { name: "humidity_high", selector: { number: { min: 0, max: 100, mode: "box", unit_of_measurement: "%" } } },
            toggle("show_controls"),
            toggle("show_mode"),
            toggle("show_preset"),
            toggle("show_fan"),
            toggle("show_swing"),
            toggle("show_quick")
          ]
        },
        {
          type: "expandable",
          name: "",
          title: "Other sensors",
          flatten: true,
          schema: [
            { name: "outdoor_entity", selector: { entity: { domain: ["weather", "sensor"] } } },
            { name: "window_entity", selector: { entity: { domain: "binary_sensor" } } },
            { name: "humidity_entity", selector: { entity: { domain: "sensor" } } },
            { name: "entities", selector: { entity: { multiple: true } } }
          ]
        },
        {
          name: "quick_names",
          selector: {
            select: {
              mode: "dropdown",
              options: [
                { value: "auto", label: "Auto (hide on small tiles)" },
                { value: "always", label: "Always show" },
                { value: "never", label: "Never show (icons only)" }
              ]
            }
          }
        },
        {
          name: "quick_settings",
          selector: {
            object: {
              multiple: true,
              label_field: "name",
              fields: {
                name: { label: "Name", required: true, selector: { text: {} } },
                hvac_mode: { label: "Mode (optional)", selector: { select: { mode: "dropdown", options: modeOptions } } },
                preset_mode: { label: "Preset (optional)", selector: { select: { mode: "dropdown", options: presetOptions } } },
                temperature: {
                  label: "Temperature (optional)",
                  selector: { number: { mode: "box", min: a.min_temp || 5, max: a.max_temp || 35, step: a.target_temp_step || 0.5, unit_of_measurement: "\xB0" } }
                },
                icon: { label: "Icon (optional)", selector: { icon: {} } },
                color: { label: "Colour (optional)", selector: { ui_color: {} } }
              }
            }
          }
        },
        {
          type: "expandable",
          name: "",
          title: "Demo mode (a pretend thermostat, for Design Presets)",
          flatten: true,
          schema: [
            { name: "demo", selector: { boolean: {} } },
            { name: "demo_mode", selector: { select: { mode: "dropdown", options: [{ value: "heat", label: "Heating" }, { value: "off", label: "Off" }] } } }
          ]
        }
      ];
    },
    labels: {
      entity: "Thermostat, valve or air conditioner",
      name: "Title (optional)",
      show_temperature_history: "Temperature history (24 hours)",
      show_humidity_history: "Humidity history (24 hours)",
      smooth_graphs: "Smooth the history graph (averages jumpy readings)",
      show_limits: "Comfortable range and humidity limits on the graph",
      room_type: "Room type (sets the comfortable range)",
      comfort_low: "Comfortable from (optional, overrides the type)",
      comfort_high: "Comfortable to (optional, overrides the type)",
      humidity_low: "Comfortable humidity from (default 40%)",
      humidity_high: "Comfortable humidity to (default 60%)",
      show_controls: "\u2212 and + buttons",
      show_mode: "Mode dropdown",
      show_preset: "Preset dropdown",
      show_fan: "Fan speed dropdown (if the device has one)",
      show_swing: "Swing dropdown (if the device has one)",
      show_quick: "Quick settings",
      outdoor_entity: "Outdoor temperature (weather or sensor)",
      window_entity: "Window or door sensor (shows a warning when open)",
      humidity_entity: "Humidity sensor (if the thermostat has none)",
      entities: "Extra readings (shown as small chips)",
      quick_names: "Quick setting names",
      quick_settings: "Quick settings (one row, up to 5)",
      demo: "Use a pretend thermostat instead of a real one",
      demo_mode: "Pretend thermostat starts"
    },
    helpers: {
      show_temperature_history: "With humidity history on too, both share one graph.",
      quick_settings: "Each sets a mode, a preset and/or a temperature. The one that matches the thermostat glows."
    }
  });
  var ClimateCard = class extends HTMLElement {
    setConfig(config) {
      if (!config.entity && !config.demo) throw new Error("entity required (or set demo: true)");
      this.config = config;
      this._built = false;
      this._demo = config.demo ? ccDemoState(config) : null;
      this._pending = null;
      this._history = config.demo ? ccDemoHistory() : null;
      this._historyAt = 0;
      this._open = null;
      this._sig = null;
    }
    set hass(hass) {
      this._hass = hass;
      this._render();
    }
    disconnectedCallback() {
      this._closeMenu();
    }
    _state() {
      return this._demo || this._hass && this._hass.states[this.config.entity];
    }
    _build() {
      this.innerHTML = `
      <ha-card class="cc-card" style="border:none; box-shadow:0 3px 10px rgba(0,0,0,0.45); border-radius:16px; padding:16px; background:var(--card-background-color); transition:background-color .6s ease; display:flex; flex-direction:column; gap:12px;">
        <style>
          .cc-btn { position:relative; overflow:hidden; flex:1 1 0; min-width:0; height:48px; border:none; border-radius:12px; cursor:pointer;
            background:rgba(127,127,127,0.16); color:var(--primary-text-color); font:inherit; font-size:26px; line-height:1; }
          .cc-btn:disabled { opacity:.4; cursor:default; }
          .cc-btn::after, .cc-q::after, .cc-dd::after { content:''; position:absolute; inset:0; background:#fff; opacity:0; transition:opacity .15s; pointer-events:none; border-radius:inherit; }
          .cc-btn:not(:disabled):hover::after, .cc-q:hover::after, .cc-dd:hover::after { opacity:.08; }
          .cc-btn:focus-visible, .cc-q:focus-visible, .cc-dd:focus-visible, .cc-opt:focus-visible { outline:2px solid var(--primary-color); outline-offset:2px; }
          .cc-q { position:relative; container-type:inline-size; flex:1 1 0; min-width:0; height:48px; border:none; border-radius:12px; padding:0; overflow:hidden; cursor:pointer; color:var(--primary-text-color); background:rgba(127,127,127,0.14);
            transition:opacity .2s, filter .2s, transform .2s, box-shadow .2s; }
          .cc-q.cc-on { color:#fff; }
          .cc-q-name { font-size:13px; }
          @container (max-width: 89px) { .cc-names-auto .cc-q-name { display:none; } }
          .cc-names-never .cc-q-name { display:none; }
          .cc-dd { position:relative; width:100%; height:48px; border:none; border-radius:12px; cursor:pointer; background:rgba(127,127,127,0.16);
            color:var(--primary-text-color); font:inherit; display:flex; align-items:center; gap:10px; padding:0 12px; text-align:left; }
          .cc-dd-text { flex:1; min-width:0; display:flex; flex-direction:column; line-height:1.2; }
          .cc-dd-text small { font-size:0.7rem; color:var(--secondary-text-color); }
          .cc-dd-text b { font-size:0.95rem; font-weight:600; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
          .cc-menu { margin-top:4px; background:var(--card-background-color); border-radius:12px;
            box-shadow:0 3px 10px rgba(0,0,0,0.45); padding:6px; display:flex; flex-direction:column; gap:2px; max-height:280px; overflow:auto; }
          .cc-opt { border:none; background:none; color:var(--primary-text-color); font:inherit; font-size:0.95rem; text-align:left; padding:10px; border-radius:8px; cursor:pointer; display:flex; align-items:center; gap:10px; }
          .cc-opt:hover { background:rgba(127,127,127,0.14); }
          .cc-opt.cc-on { background:rgba(127,127,127,0.22); font-weight:600; }
          ${KIT_HEALTH_CSS}
          .cc-chip { display:inline-flex; align-items:center; gap:6px; padding:5px 10px; border-radius:999px; background:rgba(127,127,127,0.16); font-size:0.8rem; }
        </style>
        <div style="display:flex; align-items:baseline; gap:8px;">
          <div class="cc-title" style="flex:1; min-width:0; font-size:1.5rem; font-weight:500; line-height:1.2; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; transition:color .6s;"></div>
          <div class="cc-word" style="flex:none; font-size:0.85rem; color:var(--secondary-text-color);"></div>
        </div>
        <div style="display:flex; align-items:center; gap:14px;">
          <div class="cc-gauge-wrap" style="position:relative; width:${CC_RING}px; height:${CC_RING}px; flex:none; cursor:pointer;" title="More details">
            <svg class="cc-gauge" viewBox="0 0 ${CC_RING} ${CC_RING}" width="${CC_RING}" height="${CC_RING}" aria-hidden="true" style="display:block;"></svg>
            <div style="position:absolute; inset:0; display:flex; flex-direction:column; align-items:center; justify-content:center;">
              <b class="cc-target" style="font-size:1.15rem; font-weight:700; font-variant-numeric:tabular-nums;"></b>
              <span class="cc-target-label" style="font-size:0.7rem; color:var(--secondary-text-color);"></span>
            </div>
          </div>
          <div class="cc-info" style="flex:1; min-width:0; display:flex; flex-direction:column; gap:4px; font-size:0.85rem; color:var(--secondary-text-color);"></div>
          <div style="flex:none; text-align:right;">
            <div class="cc-room" style="font-size:2rem; font-weight:700; line-height:1; font-variant-numeric:tabular-nums;"></div>
            <div style="font-size:0.85rem; color:var(--secondary-text-color);">room</div>
          </div>
        </div>
        <div class="ck-health" role="status"></div>
        <div class="cc-window"></div>
        <div class="cc-history"></div>
        <div class="cc-controls ck-dim" style="display:flex; gap:6px;">
          <button class="cc-btn cc-down" aria-label="Lower the temperature">\u2212</button>
          <button class="cc-btn cc-up" aria-label="Raise the temperature">+</button>
        </div>
        <div class="cc-dropdowns" style="display:flex; flex-direction:column; gap:6px;"></div>
        <div class="cc-quick ck-dim" style="display:flex; gap:6px;"></div>
        <div class="cc-chips" style="display:flex; flex-wrap:wrap; gap:6px;"></div>
      </ha-card>`;
      const q = (sel) => this.querySelector(sel);
      this._els = {
        card: q(".cc-card"),
        title: q(".cc-title"),
        word: q(".cc-word"),
        gauge: q(".cc-gauge"),
        target: q(".cc-target"),
        targetLabel: q(".cc-target-label"),
        info: q(".cc-info"),
        room: q(".cc-room"),
        window: q(".cc-window"),
        history: q(".cc-history"),
        controls: q(".cc-controls"),
        down: q(".cc-down"),
        up: q(".cc-up"),
        dropdowns: q(".cc-dropdowns"),
        quick: q(".cc-quick"),
        chips: q(".cc-chips")
      };
      this._els.down.addEventListener("click", () => this._step(-1));
      this._els.up.addEventListener("click", () => this._step(1));
      q(".cc-gauge-wrap").addEventListener("click", () => this._moreInfo(this.config.entity));
      this._built = true;
    }
    _moreInfo(entityId) {
      if (!entityId || this._demo) return;
      this.dispatchEvent(new CustomEvent("hass-more-info", { detail: { entityId }, bubbles: true, composed: true }));
    }
    _view(st) {
      const a = st.attributes;
      const target = this._pending != null ? this._pending : a.temperature != null ? a.temperature : null;
      return { mode: st.state, a, target, action: a.hvac_action, preset: noPreset(a.preset_mode) ? null : a.preset_mode };
    }
    _status(v) {
      if (v.mode === "off" || v.mode === "unavailable") return { word: v.mode === "off" ? "Off" : "Unavailable", color: CC_COLOR.off, tint: 0 };
      const presetWord = v.preset ? (CC_PRESETS[v.preset] || {}).name || cap(v.preset) : null;
      if (v.preset === "eco") return { word: "Eco", color: CC_COLOR.eco, tint: v.action === "heating" || v.action === "cooling" ? 10 : 0 };
      if (v.action === "heating" || v.action === "preheating") return { word: presetWord || "Heating", color: CC_COLOR.heating, tint: 14 };
      if (v.action === "cooling") return { word: presetWord || "Cooling", color: CC_COLOR.cooling, tint: 14 };
      if (v.action === "drying") return { word: presetWord || "Drying", color: CC_COLOR.drying, tint: 8 };
      if (v.action === "fan") return { word: presetWord || "Fan", color: CC_COLOR.fan, tint: 8 };
      return { word: presetWord || "Idle", color: (CC_MODES[v.mode] || {}).color || "#a594de", tint: 0 };
    }
    // One row: name, room temperature, what it's doing, and the quick settings
    // (icons only).
    _compactSpec(st) {
      const cfg = this.config, v = this._view(st), s = this._status(v), a = v.a;
      const off = v.mode === "off" || v.mode === "unavailable";
      const active = this._quickList(st).findIndex((q) => this._matches(q, v));
      return {
        name: cfg.name || a.friendly_name || cfg.entity,
        color: s.color,
        value: a.current_temperature != null ? deg(a.current_temperature) : "\u2013",
        status: off || v.target == null ? s.word : `${s.word} \xB7 ${deg(v.target)}`,
        buttons: this._quickList(st).map((q, i) => {
          const look = q.preset_mode && !noPreset(q.preset_mode) && CC_PRESETS[q.preset_mode] || q.hvac_mode && CC_MODES[q.hvac_mode] || CC_MODES.heat;
          return { key: `q${i}`, icon: q.icon || look.icon, title: q.name, on: i === active, color: q.color ? stcColor(q.color) : look.color || CC_MODES.heat.color, q };
        }),
        onButton: (b) => this._applyQuick(b.q)
      };
    }
    _entityState(id) {
      return id && this._hass && this._hass.states[id];
    }
    _render() {
      const st = this._state();
      if (!st) return;
      if (this._compact) return kitCompact(this, this._compactSpec(st));
      if (!this._built) this._build();
      const e = this._els;
      const cfg = this.config;
      const v = this._view(st);
      const s = this._status(v);
      const a = v.a;
      this._color = s.color;
      e.card.style.backgroundColor = s.tint ? `color-mix(in srgb, ${s.color} ${s.tint}%, var(--card-background-color))` : "var(--card-background-color)";
      e.title.textContent = cfg.name || a.friendly_name || cfg.entity;
      e.title.style.color = s.color;
      e.word.textContent = s.word + (this._demo ? " \xB7 demo" : "");
      kitHealthBanner(this, this._hass, cfg.entity, !!this._demo);
      const off = v.mode === "off" || v.mode === "unavailable";
      const hasTarget = !off && v.target != null;
      e.target.textContent = off ? "Off" : hasTarget ? deg(v.target) : cap((CC_MODES[v.mode] || {}).name || v.mode);
      e.targetLabel.textContent = hasTarget ? "target" : "";
      e.room.textContent = a.current_temperature != null ? deg(a.current_temperature) : "\u2013";
      this._drawGauge(a, v, s, hasTarget);
      const humidity = this._humidity(a);
      const outdoor = this._entityState(cfg.outdoor_entity);
      const outTemp = outdoor && (outdoor.entity_id.startsWith("weather.") ? outdoor.attributes.temperature : outdoor.state);
      const infoSig = JSON.stringify([humidity, outTemp]);
      if (infoSig !== this._infoSig) {
        this._infoSig = infoSig;
        e.info.innerHTML = [
          humidity != null ? `<span style="display:flex; align-items:center; gap:4px;">${iconHtml("mdi:water-percent", { size: "18px", style: `color:${CC_HUMIDITY};` })}${Math.round(humidity)}%</span>` : "",
          outTemp != null && outTemp !== "" && !isNaN(Number(outTemp)) ? `<span style="display:flex; align-items:center; gap:4px;">${iconHtml("mdi:weather-partly-cloudy", { size: "18px" })}Outside ${Math.round(Number(outTemp))}\xB0</span>` : ""
        ].join("");
      }
      const win = this._entityState(cfg.window_entity);
      const open = win && win.state === "on";
      e.window.style.display = open ? "flex" : "none";
      if (open) {
        const verb = v.action === "cooling" ? "cooling" : "heating";
        e.window.innerHTML = `<span class="cc-chip" style="background:color-mix(in srgb, #ffb300 22%, transparent); color:#ffd54f;">${iconHtml("mdi:window-open-variant", { size: "18px" })}${esc(win.attributes.friendly_name || "Window")} open${off ? "" : ` \xB7 ${verb} wasted`}</span>`;
      }
      e.controls.style.display = row(cfg, "show_controls") ? "flex" : "none";
      e.down.disabled = !hasTarget;
      e.up.disabled = !hasTarget;
      this._drawHistory(a, v);
      this._drawDropdowns(st, v);
      this._drawQuick(st, v);
      this._drawChips();
      hydrateIcons(this);
    }
    _humidity(a) {
      if (a.current_humidity != null) return Number(a.current_humidity);
      const h = this._entityState(this.config.humidity_entity);
      return h && !isNaN(Number(h.state)) ? Number(h.state) : null;
    }
    // 270° arc from min to max: faint up to the target, solid between the room
    // and the target, a tick at the target and a white dot at the room.
    _drawGauge(a, v, s, hasTarget) {
      const size = CC_RING, cx = size / 2, r = size / 2 - 7, len = 1.5 * Math.PI * r, sw = 6;
      const min = a.min_temp != null ? a.min_temp : 7, max = a.max_temp != null ? a.max_temp : 35;
      const pos = (t) => Math.max(0, Math.min(1, (t - min) / (max - min || 1)));
      const pt = (p, rr) => {
        const ang = (135 + 270 * p) * Math.PI / 180;
        return [cx + rr * Math.cos(ang), cx + rr * Math.sin(ang)];
      };
      const arc = (extra) => `<circle cx="${cx}" cy="${cx}" r="${r}" fill="none" stroke-width="${sw}" transform="rotate(135 ${cx} ${cx})" ${extra}></circle>`;
      let svg2 = arc(`stroke="rgba(127,127,127,0.28)" stroke-linecap="round" stroke-dasharray="${len} 9999"`);
      const room = a.current_temperature;
      if (hasTarget) {
        const t = pos(v.target);
        svg2 += arc(`stroke="${s.color}" stroke-opacity="0.35" stroke-linecap="round" stroke-dasharray="${Math.max(0.01, len * t)} 9999"`);
        if (room != null) {
          const lo = pos(Math.min(room, v.target)), hi = pos(Math.max(room, v.target));
          svg2 += arc(`stroke="${s.color}" stroke-dasharray="${Math.max(0.01, len * (hi - lo))} 9999" stroke-dashoffset="${-len * lo}"`);
        }
        const [x1, y1] = pt(t, r - 9), [x2, y2] = pt(t, r + 5);
        svg2 += `<line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}" stroke="${s.color}" stroke-width="3" stroke-linecap="round"></line>`;
      }
      if (room != null) {
        const [dx, dy] = pt(pos(room), r);
        svg2 += `<circle cx="${dx}" cy="${dy}" r="5" fill="#fff" stroke="var(--card-background-color)" stroke-width="2"></circle>`;
      }
      this._els.gauge.innerHTML = svg2;
    }
    // ---- History: room temperature (state colour, target dashed) and humidity
    // (purple), each on its own scale, sharing one graph when both are on.
    async _loadHistory() {
      if (this._demo || this._historyLoading || !this._hass || !this._hass.callWS) return;
      this._historyLoading = true;
      try {
        const start = new Date(Date.now() - CC_HISTORY_HOURS * 36e5).toISOString();
        const ids = [this.config.entity];
        if (this.config.humidity_entity) ids.push(this.config.humidity_entity);
        const res = await this._hass.callWS({
          type: "history/history_during_period",
          start_time: start,
          entity_ids: ids,
          minimal_response: false,
          no_attributes: false,
          significant_changes_only: false
        });
        const temperature = [], humidity = [], target = [];
        let attrs = {};
        (res[this.config.entity] || []).forEach((p) => {
          attrs = p.a || attrs;
          const t = (p.lu || p.lc || 0) * 1e3;
          if (attrs.current_temperature != null) temperature.push([t, Number(attrs.current_temperature)]);
          if (attrs.current_humidity != null) humidity.push([t, Number(attrs.current_humidity)]);
          target.push([t, p.s === "off" || attrs.temperature == null ? null : Number(attrs.temperature)]);
        });
        if (this.config.humidity_entity && !humidity.length) {
          (res[this.config.humidity_entity] || []).forEach((p) => {
            if (!isNaN(Number(p.s))) humidity.push([(p.lu || p.lc || 0) * 1e3, Number(p.s)]);
          });
        }
        this._history = { temperature, humidity, target };
        this._historyAt = Date.now();
        this._sig = null;
        this._render();
      } catch (err) {
        this._historyAt = Date.now();
      }
      this._historyLoading = false;
    }
    _drawHistory(a, v) {
      const cfg = this.config;
      const showT = row(cfg, "show_temperature_history");
      const showH = row(cfg, "show_humidity_history");
      const box = this._els.history;
      if (!showT && !showH) {
        box.style.display = "none";
        return;
      }
      box.style.display = "block";
      if (!this._demo && Date.now() - this._historyAt > 10 * 6e4) this._loadHistory();
      const h = this._history;
      const sig = JSON.stringify([showT, showH, this._historyAt, this._color, !!h, cfg.smooth_graphs, cfg.show_limits, cfg.room_type, cfg.comfort_low, cfg.comfort_high, cfg.humidity_low, cfg.humidity_high]);
      if (sig === this._historySig) return;
      this._historySig = sig;
      if (!h) {
        box.innerHTML = `<div style="height:74px; display:flex; align-items:center; justify-content:center; font-size:0.8rem; color:var(--secondary-text-color);">Loading history\u2026</div>`;
        return;
      }
      const now = Date.now();
      const from = now - CC_HISTORY_HOURS * 36e5;
      const extend = (pts, current) => {
        const out = pts.filter((p) => p[1] != null && !isNaN(p[1]));
        if (current != null) out.push([now, Number(current)]);
        return out;
      };
      const smooth = cfg.smooth_graphs !== false;
      const tRaw = showT ? extend(h.temperature, a.current_temperature) : [];
      const hRaw = showH ? extend(h.humidity, this._humidity(a)) : [];
      const temps = smooth ? kitSmooth(tRaw, from, now) : tRaw;
      const hums = smooth ? kitSmooth(hRaw, from, now) : hRaw;
      const W = 300, H = 56;
      const x = (t) => (Math.max(from, t) - from) / (now - from) * W;
      const pathOf = (pts, lo, hi) => {
        const y = (val) => H - 3 - (val - lo) / (hi - lo || 1) * (H - 6);
        return { y, d: kitPath(pts.map((p) => [x(p[0]), y(p[1])]), smooth) };
      };
      let svg2 = "";
      const legend = [];
      const scrub = [];
      const limits = cfg.show_limits !== false;
      const type = CZ_TYPES[cfg.room_type] || CZ_TYPES.living;
      const num = (k, d) => cfg[k] != null && cfg[k] !== "" ? Number(cfg[k]) : d;
      const cLow = num("comfort_low", type.low), cHigh = Math.max(num("comfort_high", type.high), cLow + 0.5);
      const hLow = num("humidity_low", 40), hHigh = num("humidity_high", 60);
      let limitsSvg = "";
      if (hums.length > 1) {
        const vals = hums.map((p2) => p2[1]);
        const lo = Math.min(...vals, ...limits ? [hLow - 5] : []) - 3, hi = Math.max(...vals, ...limits ? [hHigh + 5] : []) + 3;
        const p = pathOf(hums, lo, hi);
        if (limits) {
          [hLow, hHigh].forEach((val) => {
            limitsSvg += `<line x1="0" x2="${W}" y1="${p.y(val).toFixed(1)}" y2="${p.y(val).toFixed(1)}" stroke="${CC_HUMIDITY}" stroke-opacity="0.6" stroke-dasharray="1.5 3" vector-effect="non-scaling-stroke"></line>`;
          });
        }
        scrub.push({ pts: hums, raw: hRaw, linear: smooth, lo, hi, color: CC_HUMIDITY, format: (val) => `${Math.round(val)}% humidity` });
        if (!temps.length) svg2 += `<path d="${p.d} L${W},${H} L0,${H} Z" fill="${CC_HUMIDITY}" fill-opacity="0.16"></path>`;
        svg2 += `<path d="${p.d}" fill="none" stroke="${CC_HUMIDITY}" stroke-width="2" vector-effect="non-scaling-stroke"></path>`;
        legend.push(limits ? `<span style="color:${CC_HUMIDITY}">\u2504 ${hLow}\u2013${hHigh}%</span>` : `<span style="color:${CC_HUMIDITY}">\u25CF Humidity ${Math.round(Math.min(...vals))}\u2013${Math.round(Math.max(...vals))}%</span>`);
      }
      if (temps.length > 1) {
        const vals = temps.map((p2) => p2[1]);
        const targets = (h.target || []).map((p2) => p2[1]).filter((t) => t != null);
        if (v.target != null) targets.push(Number(v.target));
        const band = limits ? [cLow - 1, cHigh + 1] : [];
        const lo = Math.min(...vals, ...targets, ...band) - 0.5, hi = Math.max(...vals, ...targets, ...band) + 0.5;
        const p = pathOf(temps, lo, hi);
        if (limits) {
          limitsSvg = `<rect x="0" y="${p.y(cHigh).toFixed(1)}" width="${W}" height="${(p.y(cLow) - p.y(cHigh)).toFixed(1)}" fill="#66bb6a" fill-opacity="0.12"></rect>` + [cLow, cHigh].map((val) => `<line x1="0" x2="${W}" y1="${p.y(val).toFixed(1)}" y2="${p.y(val).toFixed(1)}" stroke="#66bb6a" stroke-opacity="0.55" stroke-dasharray="4 3" vector-effect="non-scaling-stroke"></line>`).join("") + limitsSvg;
        }
        scrub.unshift({ pts: temps, raw: tRaw, linear: smooth, lo, hi, color: this._color, format: (val) => `${val.toFixed(1)}\xB0 room` });
        const tPts = (h.target || []).filter((q) => q[1] != null);
        if (tPts.length) scrub.splice(1, 0, { pts: tPts, lo, hi, color: this._color, format: (val) => `${Number(val).toFixed(1)}\xB0 target` });
        let tgt = "";
        if (v.target != null) {
          const ty = p.y(Number(v.target)).toFixed(1);
          tgt = `<line x1="0" x2="${W}" y1="${ty}" y2="${ty}" stroke="${this._color}" stroke-dasharray="4 4" stroke-width="1.5" vector-effect="non-scaling-stroke"></line>`;
        }
        svg2 = `<path d="${p.d} L${W},${H} L0,${H} Z" fill="${this._color}" fill-opacity="0.16"></path>` + svg2 + `<path d="${p.d}" fill="none" stroke="${this._color}" stroke-width="2" vector-effect="non-scaling-stroke"></path>${tgt}`;
        legend.unshift(limits ? `<span style="color:#66bb6a">\u25AD ${type.name} ${cLow}\u2013${cHigh}\xB0</span>` : `<span style="color:${this._color}">\u25CF Temperature ${Math.min(...vals).toFixed(1)}\u2013${Math.max(...vals).toFixed(1)}\xB0</span>`);
      }
      if (!legend.length) {
        box.innerHTML = `<div style="height:74px; display:flex; align-items:center; justify-content:center; font-size:0.8rem; color:var(--secondary-text-color);">No history yet</div>`;
        return;
      }
      if (legend.length === 1) legend.push("<span>last 24 h</span>");
      box.innerHTML = `<svg viewBox="0 0 ${W} ${H}" preserveAspectRatio="none" style="display:block; width:100%; height:${H}px;" role="img" aria-label="The last 24 hours">${limitsSvg}${svg2}</svg>
      <div style="display:flex; justify-content:space-between; gap:8px; flex-wrap:wrap; margin-top:2px; font-size:0.8rem; color:var(--secondary-text-color);">${legend.join("")}</div>`;
      kitScrub(box.querySelector("svg"), { from, now, height: H, series: scrub });
    }
    // ---- Full-width dropdowns: mode, preset, fan speed, swing.
    _dropdownDefs(st, v) {
      const a = st.attributes;
      const cfg = this.config;
      const defs = [];
      if (row(cfg, "show_mode") && (a.hvac_modes || []).length > 1) {
        defs.push({
          key: "mode",
          label: "Mode",
          value: v.mode,
          options: a.hvac_modes.map((m) => ({ value: m, name: (CC_MODES[m] || {}).name || cap(m), icon: (CC_MODES[m] || {}).icon || "mdi:thermostat", color: (CC_MODES[m] || {}).color })),
          set: (val) => this._set("set_hvac_mode", { hvac_mode: val }, () => {
            this._demo.state = val;
          })
        });
      }
      if (row(cfg, "show_preset") && (a.preset_modes || []).length) {
        const opts = a.preset_modes.includes("none") ? a.preset_modes : ["none", ...a.preset_modes];
        defs.push({
          key: "preset",
          label: "Preset",
          value: noPreset(a.preset_mode) ? "none" : a.preset_mode,
          options: opts.map((p) => ({ value: p, name: (CC_PRESETS[p] || {}).name || cap(p), icon: (CC_PRESETS[p] || {}).icon || "mdi:tune-variant", color: (CC_PRESETS[p] || {}).color })),
          set: (val) => this._set("set_preset_mode", { preset_mode: val }, () => {
            this._demo.attributes.preset_mode = val;
          })
        });
      }
      if (row(cfg, "show_fan") && (a.fan_modes || []).length) {
        defs.push({
          key: "fan",
          label: "Fan speed",
          value: a.fan_mode,
          options: a.fan_modes.map((m) => ({ value: m, name: cap(m), icon: "mdi:fan", color: CC_COLOR.fan })),
          set: (val) => this._set("set_fan_mode", { fan_mode: val })
        });
      }
      if (row(cfg, "show_swing") && (a.swing_modes || []).length) {
        defs.push({
          key: "swing",
          label: "Swing",
          value: a.swing_mode,
          options: a.swing_modes.map((m) => ({ value: m, name: cap(m), icon: "mdi:arrow-oscillating", color: CC_COLOR.fan })),
          set: (val) => this._set("set_swing_mode", { swing_mode: val })
        });
      }
      return defs;
    }
    _drawDropdowns(st, v) {
      const defs = this._dropdownDefs(st, v);
      const sig = JSON.stringify([defs.map((d) => [d.key, d.value, d.options.map((o) => o.value)]), this._open]);
      if (sig === this._ddSig) return;
      this._ddSig = sig;
      const box = this._els.dropdowns;
      box.style.display = defs.length ? "flex" : "none";
      box.innerHTML = "";
      defs.forEach((d) => {
        const cur = d.options.find((o) => o.value === d.value) || { name: cap(d.value || "\u2013"), icon: "mdi:help-circle-outline" };
        const wrap = document.createElement("div");
        wrap.style.position = "relative";
        const btn = document.createElement("button");
        btn.className = "cc-dd";
        btn.setAttribute("aria-haspopup", "listbox");
        btn.setAttribute("aria-expanded", String(this._open === d.key));
        btn.innerHTML = `${iconHtml(cur.icon, { size: "22px", style: `color:${cur.color || "var(--primary-text-color)"}; flex:none;` })}
        <span class="cc-dd-text"><small>${d.label}</small><b>${esc(cur.name)}</b></span>
        <ha-icon icon="mdi:menu-down" style="--mdc-icon-size:22px; color:var(--secondary-text-color); flex:none;"></ha-icon>`;
        btn.addEventListener("click", (ev) => {
          ev.stopPropagation();
          this._open = this._open === d.key ? null : d.key;
          this._ddSig = null;
          this._render();
          if (this._open) this._listenOutside();
        });
        wrap.appendChild(btn);
        if (this._open === d.key) {
          const menu = document.createElement("div");
          menu.className = "cc-menu";
          menu.setAttribute("role", "listbox");
          d.options.forEach((o) => {
            const opt = document.createElement("button");
            opt.className = `cc-opt${o.value === d.value ? " cc-on" : ""}`;
            opt.setAttribute("role", "option");
            opt.setAttribute("aria-selected", String(o.value === d.value));
            opt.innerHTML = `${iconHtml(o.icon, { size: "20px", style: `color:${o.color || "var(--primary-text-color)"}; flex:none;` })}<span></span>`;
            opt.querySelector("span").textContent = o.name;
            opt.addEventListener("click", (ev) => {
              ev.stopPropagation();
              this._closeMenu();
              if (o.value !== d.value) d.set(o.value);
            });
            menu.appendChild(opt);
          });
          wrap.appendChild(menu);
        }
        box.appendChild(wrap);
      });
    }
    _listenOutside() {
      if (this._outside) return;
      this._outside = () => this._closeMenu();
      setTimeout(() => document.addEventListener("click", this._outside), 0);
    }
    _closeMenu() {
      if (this._outside) document.removeEventListener("click", this._outside);
      this._outside = null;
      if (this._open) {
        this._open = null;
        this._ddSig = null;
        if (this._built) this._render();
      }
    }
    // ---- Quick settings: one row, up to five.
    _quickList(st) {
      if (!row(this.config, "show_quick")) return [];
      const list = Array.isArray(this.config.quick_settings) ? this.config.quick_settings : ccDefaultQuick(st, this.config);
      return list.filter((q) => q && q.name).slice(0, CC_MAX_QUICK);
    }
    _matches(q, v) {
      if (q.hvac_mode && q.hvac_mode !== v.mode) return false;
      if (!q.hvac_mode && v.mode === "off") return false;
      if (q.preset_mode && !noPreset(q.preset_mode)) {
        if (q.preset_mode !== v.preset) return false;
      } else if (v.preset && (q.hvac_mode || q.temperature != null)) return false;
      if (q.temperature != null && Number(q.temperature) !== Number(v.target)) return false;
      return true;
    }
    _drawQuick(st, v) {
      const list = this._quickList(st);
      const names = ["always", "never"].includes(this.config.quick_names) ? this.config.quick_names : "auto";
      const sig = JSON.stringify([list, names, v.mode, v.preset, v.target]);
      if (sig === this._quickSig) return;
      this._quickSig = sig;
      const box = this._els.quick;
      box.className = `cc-quick ck-dim cc-names-${names}`;
      box.style.display = list.length ? "flex" : "none";
      box.innerHTML = "";
      const active = list.findIndex((q) => this._matches(q, v));
      list.forEach((q, i) => {
        const look = q.preset_mode && !noPreset(q.preset_mode) && CC_PRESETS[q.preset_mode] || q.hvac_mode && CC_MODES[q.hvac_mode] || CC_MODES.heat;
        const color = q.color ? stcColor(q.color) : look.color || CC_MODES.heat.color;
        const icon = q.icon || look.icon;
        const on = i === active;
        const tile = document.createElement("button");
        tile.className = `cc-q${on ? " cc-on" : ""}`;
        tile.title = q.name;
        tile.setAttribute("aria-label", q.name);
        tile.setAttribute("aria-pressed", String(on));
        if (on) tile.style.background = color;
        tile.innerHTML = `<div style="position:relative; display:flex; align-items:center; justify-content:center; gap:6px; height:100%; padding:0 6px;">
          ${iconHtml(icon, { size: "22px", style: "flex-shrink:0;" })}
          <span class="cc-q-name" style="min-width:0; font-weight:600; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;"></span>
        </div>`;
        tile.querySelector(".cc-q-name").textContent = q.name;
        tile.addEventListener("click", () => this._applyQuick(q));
        box.appendChild(tile);
      });
    }
    // ---- Extra readings as chips; a tap opens the sensor.
    _drawChips() {
      const ids = Array.isArray(this.config.entities) ? this.config.entities : [];
      const states = ids.map((id) => this._entityState(id)).filter(Boolean);
      const sig = JSON.stringify(states.map((s) => [s.entity_id, s.state]));
      if (sig === this._chipSig) return;
      this._chipSig = sig;
      const box = this._els.chips;
      box.style.display = states.length ? "flex" : "none";
      box.innerHTML = "";
      states.forEach((s) => {
        const chip = document.createElement("button");
        chip.className = "cc-chip";
        chip.style.cssText = "border:none; color:var(--primary-text-color); font:inherit; font-size:0.8rem; cursor:pointer;";
        const text = this._hass.formatEntityState ? this._hass.formatEntityState(s) : `${s.state}${s.attributes.unit_of_measurement ? " " + s.attributes.unit_of_measurement : ""}`;
        const icon = s.attributes.icon || (s.attributes.device_class === "battery" ? "mdi:battery" : s.attributes.device_class === "temperature" ? "mdi:thermometer" : "mdi:information-outline");
        chip.innerHTML = `${iconHtml(icon, { size: "16px", style: "color:var(--secondary-text-color);" })}<span></span>`;
        chip.querySelector("span").textContent = `${s.attributes.friendly_name || s.entity_id} ${text}`;
        chip.addEventListener("click", () => this._moreInfo(s.entity_id));
        box.appendChild(chip);
      });
    }
    // ---- Changing things.
    _set(service, data, demo) {
      if (this._demo) {
        if (demo) demo();
        this._demoSettle();
        return;
      }
      this._hass.callService("climate", service, { entity_id: this.config.entity, ...data });
    }
    _demoSettle() {
      const a = this._demo.attributes;
      if (this._demo.state === "off") {
        a.hvac_action = "off";
      } else {
        if (a.temperature == null) a.temperature = 20;
        if (a.preset_mode === "eco") a.temperature = 16.5;
        a.hvac_action = a.current_temperature < a.temperature ? "heating" : "idle";
      }
      this._render();
    }
    _applyQuick(q) {
      const st = this._state();
      const a = st.attributes;
      if (this._demo) {
        if (q.hvac_mode) this._demo.state = q.hvac_mode;
        if (q.preset_mode) this._demo.attributes.preset_mode = q.preset_mode;
        else if (q.hvac_mode || q.temperature != null) this._demo.attributes.preset_mode = "none";
        if (q.temperature != null) this._demo.attributes.temperature = Number(q.temperature);
        this._demoSettle();
        return;
      }
      if (q.temperature != null) {
        this._set("set_temperature", { temperature: Number(q.temperature), ...q.hvac_mode ? { hvac_mode: q.hvac_mode } : {} });
      } else if (q.hvac_mode && q.hvac_mode !== st.state) {
        this._set("set_hvac_mode", { hvac_mode: q.hvac_mode });
      }
      if (q.preset_mode) {
        this._set("set_preset_mode", { preset_mode: q.preset_mode });
      } else if ((q.hvac_mode || q.temperature != null) && q.hvac_mode !== "off" && !noPreset(a.preset_mode) && (a.preset_modes || []).includes("none")) {
        this._set("set_preset_mode", { preset_mode: "none" });
      }
    }
    // − / + change the target locally and send it once tapping stops.
    _step(dir) {
      const st = this._state();
      if (!st) return;
      const a = st.attributes;
      const current = this._pending != null ? this._pending : a.temperature;
      if (current == null) return;
      const step = Number(a.target_temp_step) || 0.5;
      const min = a.min_temp != null ? a.min_temp : 7, max = a.max_temp != null ? a.max_temp : 35;
      const next = Math.round(Math.min(max, Math.max(min, Number(current) + dir * step)) * 10) / 10;
      if (this._demo) {
        this._demo.attributes.temperature = next;
        this._demoSettle();
        return;
      }
      this._pending = next;
      this._render();
      clearTimeout(this._sendTimer);
      clearTimeout(this._clearTimer);
      this._sendTimer = setTimeout(() => {
        this._set("set_temperature", { temperature: next });
        this._clearTimer = setTimeout(() => {
          this._pending = null;
          this._render();
        }, 3e3);
      }, 700);
    }
    getCardSize() {
      const cfg = this.config || {};
      let size = 3;
      if (row(cfg, "show_temperature_history") || row(cfg, "show_humidity_history")) size += 2;
      if (row(cfg, "show_controls")) size += 1;
      if (row(cfg, "show_mode")) size += 1;
      if (row(cfg, "show_preset")) size += 1;
      if (row(cfg, "show_quick")) size += 1;
      return size;
    }
    getGridOptions() {
      return { columns: 12, min_columns: 6, rows: "auto" };
    }
    static getConfigElement() {
      return document.createElement(`climate-card-editor${SUFFIX}`);
    }
    static getStubConfig(hass) {
      const first = hass && Object.keys(hass.states).find((id) => id.startsWith("climate."));
      return first ? { entity: first } : { demo: true };
    }
  };
  kitCompactable(ClimateCard);
  function registerClimateCard() {
    if (!customElements.get(`climate-card-editor${SUFFIX}`)) {
      customElements.define(`climate-card-editor${SUFFIX}`, ClimateCardEditor);
    }
    if (!customElements.get(`climate-card${SUFFIX}`)) {
      customElements.define(`climate-card${SUFFIX}`, ClimateCard);
    }
    window.customCards = window.customCards || [];
    window.customCards.push({
      type: `climate-card${SUFFIX}`,
      name: `Climate Card${LABEL}`,
      description: "A thermostat, radiator valve or air conditioner: gauge, history, \u2212 / +, dropdowns and quick settings",
      preview: true,
      documentationURL: "https://github.com/J45PER/church-drive-cards#readme"
    });
  }

  // src/fan-card.js
  var FAN_PRESET_ICONS = {
    natural: "mdi:weather-windy",
    nature: "mdi:weather-windy",
    breeze: "mdi:weather-windy",
    sleep: "mdi:power-sleep",
    auto: "mdi:fan-auto",
    smart: "mdi:fan-auto",
    turbo: "mdi:rocket-launch",
    boost: "mdi:rocket-launch",
    eco: "mdi:leaf"
  };
  var speedOf = (preset) => {
    const m = /^speed[ _-]?(\d+)$/i.exec(String(preset || ""));
    return m ? Number(m[1]) : null;
  };
  function fanDemo(config) {
    const on = config.demo_state === "on";
    return {
      entity_id: "fan.demo",
      state: on ? "on" : "off",
      attributes: {
        friendly_name: "Bedroom Fan",
        preset_modes: ["speed_1", "speed_2", "speed_3", "natural", "sleep"],
        preset_mode: on ? "speed_2" : null,
        percentage: on ? 67 : null,
        percentage_step: 33.333333333333336,
        oscillating: on,
        supported_features: 59,
        model_id: "CX3550/01"
      }
    };
  }
  function fanSpeeds(a) {
    const presets = (a.preset_modes || []).filter((p) => speedOf(p) != null).sort((x, y) => speedOf(x) - speedOf(y));
    if (presets.length) return presets.map((p) => ({ n: speedOf(p), preset: p }));
    const step = Number(a.percentage_step) || 0;
    const count = step ? Math.round(100 / step) : 0;
    if (count < 2 || count > 6) return [];
    return Array.from({ length: count }, (_, i) => ({ n: i + 1, percentage: Math.round(step * (i + 1)) }));
  }
  var FanCardEditor = createFormEditor({
    schema: (config) => [
      ...config.demo ? [] : [{ name: "entity", selector: { entity: { domain: "fan" } } }],
      { name: "name", selector: { text: {} } },
      { name: "temperature_entity", selector: { entity: { domain: "sensor", device_class: "temperature" } } },
      {
        type: "expandable",
        name: "",
        title: "Rows to show",
        flatten: true,
        schema: [
          { name: "show_gauge", selector: { boolean: {} }, default: true },
          { name: "show_speeds", selector: { boolean: {} }, default: true },
          { name: "show_presets", selector: { boolean: {} }, default: true },
          { name: "show_oscillate", selector: { boolean: {} }, default: true }
        ]
      },
      {
        type: "expandable",
        name: "",
        title: "Demo mode (a pretend fan, for Design Presets)",
        flatten: true,
        schema: [
          { name: "demo", selector: { boolean: {} } },
          { name: "demo_state", selector: { select: { mode: "dropdown", options: [{ value: "off", label: "Off" }, { value: "on", label: "On (speed 2)" }] } } }
        ]
      }
    ],
    labels: {
      entity: "Fan",
      name: "Title (optional)",
      temperature_entity: "Room temperature (optional, shown under the gauge)",
      show_gauge: "Speed gauge",
      show_speeds: "Off and speed buttons",
      show_presets: "Preset buttons (Natural, Sleep\u2026)",
      show_oscillate: "Oscillate button (if the fan can)",
      demo: "Use a pretend fan instead of a real one",
      demo_state: "Pretend fan starts"
    }
  });
  var FanCard = class extends HTMLElement {
    setConfig(config) {
      if (!config.entity && !config.demo) throw new Error("entity required (or set demo: true)");
      this.config = config;
      this._built = false;
      this._demo = config.demo ? fanDemo(config) : null;
      this._pending = new KitPending(this);
    }
    set hass(hass) {
      this._hass = hass;
      this._render();
    }
    _state() {
      return this._pending.apply(this._demo || this._hass && this._hass.states[this.config.entity]);
    }
    _render() {
      const st = this._state();
      if (!st || !this._hass) return;
      const c = this.config;
      if (this._compact) return kitCompact(this, this._compactSpec(st));
      if (!this._built) {
        this.innerHTML = kitShell(`
        <div class="fc-top" style="display:flex; align-items:center; gap:14px;">
          <div class="fc-gauge ck-tap"></div>
          <div class="ck-info fc-info"></div>
        </div>
        <div class="ck-row fc-speeds"></div>
        <div class="ck-row fc-presets"></div>`);
        this.querySelector(".fc-gauge").addEventListener("click", () => !this._demo && kitMoreInfo(this, c.entity));
        this._built = true;
      }
      const a = st.attributes;
      const on = st.state === "on";
      const speeds = fanSpeeds(a);
      const current = on ? speeds.find((s) => s.preset ? s.preset === a.preset_mode : Math.abs((a.percentage || 0) - s.percentage) < 5) : null;
      const preset = on && a.preset_mode && speedOf(a.preset_mode) == null ? a.preset_mode : null;
      const color = !on ? KIT_COLOR.off : preset === "sleep" ? KIT_COLOR.sleep : KIT_COLOR.fan;
      const word = st.state === "unavailable" ? "Unavailable" : !on ? "Off" : preset ? kitCap(preset) : current ? `Speed ${current.n}` : "On";
      kitHead(this, c.name || a.friendly_name || c.entity, word + (this._demo ? " \xB7 demo" : ""), color);
      kitHealthBanner(this, this._hass, c.entity, !!(this._demo || c.demo));
      const top = this.querySelector(".fc-top");
      top.style.display = c.show_gauge === false ? "none" : "flex";
      const level = !on ? 0 : current ? current.n / (speeds.length || 1) : (a.percentage || 100) / 100;
      this.querySelector(".fc-gauge").innerHTML = kitGauge(level, color, !on ? "Off" : current ? String(current.n) : preset ? kitCap(preset) : "On", !on ? "" : current ? "speed" : "");
      const temp = c.temperature_entity && this._hass.states[c.temperature_entity];
      const tv = kitNum(temp);
      const lines = [];
      if (a.model_id) lines.push(`<span>${a.model_id}</span>`);
      if (a.oscillating != null) lines.push(`<span>Oscillate ${a.oscillating ? "on" : "off"}</span>`);
      if (tv != null) lines.push(`<span>${tv.toFixed(1)}\xB0 in the room</span>`);
      this.querySelector(".fc-info").innerHTML = lines.join("");
      const speedTiles = c.show_speeds === false ? [] : [
        { key: "off", name: "Off", icon: "mdi:power", color: KIT_COLOR.off, on: !on },
        ...speeds.map((s, i) => ({
          key: `s${s.n}`,
          name: String(s.n),
          icon: ["mdi:speedometer-slow", "mdi:speedometer-medium", "mdi:speedometer"][Math.min(2, Math.round(i / Math.max(1, speeds.length - 1) * 2))],
          color: KIT_COLOR.fan,
          on: current === s,
          speed: s
        }))
      ];
      kitTiles(this.querySelector(".fc-speeds"), speedTiles, (t) => t.key === "off" ? this._call("turn_off", {}) : this._setSpeed(t.speed));
      const others = (a.preset_modes || []).filter((p) => speedOf(p) == null);
      const canOscillate = a.oscillating != null || ((a.supported_features || 0) & 2) === 2;
      const presetTiles = [
        ...c.show_presets === false ? [] : others.map((p) => ({ key: p, name: kitCap(p), icon: FAN_PRESET_ICONS[String(p).toLowerCase()] || "mdi:fan", color: p === "sleep" ? KIT_COLOR.sleep : KIT_COLOR.fan, on: preset === p })),
        ...c.show_oscillate === false || !canOscillate ? [] : [{ key: "__osc", name: "Oscillate", icon: "mdi:arrow-oscillating", color: KIT_COLOR.fan, on: on && !!a.oscillating }]
      ];
      kitTiles(this.querySelector(".fc-presets"), presetTiles, (t) => {
        if (t.key === "__osc") this._call("oscillate", { oscillating: !this._state().attributes.oscillating });
        else this._call("set_preset_mode", { preset_mode: t.key });
      });
      hydrateIcons(this);
    }
    // One row: name, room temperature, and Off / speed buttons.
    _compactSpec(st) {
      const c = this.config, a = st.attributes, on = st.state === "on";
      const speeds = fanSpeeds(a);
      const current = on ? speeds.find((s) => s.preset ? s.preset === a.preset_mode : Math.abs((a.percentage || 0) - s.percentage) < 5) : null;
      const preset = on && a.preset_mode && speedOf(a.preset_mode) == null ? a.preset_mode : null;
      const color = !on ? KIT_COLOR.off : preset === "sleep" ? KIT_COLOR.sleep : KIT_COLOR.fan;
      const tv = kitNum(c.temperature_entity && this._hass.states[c.temperature_entity]);
      const word = st.state === "unavailable" ? "Unavailable" : !on ? "Off" : preset ? kitCap(preset) : current ? `Speed ${current.n}` : "On";
      return {
        name: c.name || a.friendly_name || c.entity,
        color,
        status: [preset ? word : "", tv != null ? `${tv.toFixed(1)}\xB0` : ""].filter(Boolean).join(" \xB7 ") || word,
        buttons: [{ key: "off", icon: "mdi:power", title: "Off", on: !on, color: KIT_COLOR.off }, ...speeds.map((s) => ({ key: `s${s.n}`, label: String(s.n), title: `Speed ${s.n}`, on: current === s, color: KIT_COLOR.fan, speed: s }))],
        onButton: (b) => b.key === "off" ? this._call("turn_off", {}) : this._setSpeed(b.speed)
      };
    }
    _setSpeed(s) {
      if (s.preset) this._call("set_preset_mode", { preset_mode: s.preset });
      else this._call("set_percentage", { percentage: s.percentage });
    }
    _call(service, data) {
      const st = this._state();
      if (!this._demo && st) {
        if (service === "turn_off") this._pending.set({ state: "off" });
        else if (service === "oscillate") this._pending.set({ state: st.state, attrs: { oscillating: data.oscillating } });
        else if (service === "set_percentage") this._pending.set({ state: "on", attrs: { percentage: data.percentage } });
        else this._pending.set({ state: "on", attrs: { preset_mode: data.preset_mode } });
        this._render();
      }
      if (this._demo) {
        const d = this._demo, a = d.attributes;
        if (service === "turn_off") {
          d.state = "off";
          a.preset_mode = null;
        } else if (service === "oscillate") {
          a.oscillating = data.oscillating;
        } else {
          d.state = "on";
          if (data.preset_mode) a.preset_mode = data.preset_mode;
        }
        this._render();
        return;
      }
      this._hass.callService("fan", service, { entity_id: this.config.entity, ...data });
    }
    getCardSize() {
      return 4;
    }
    getGridOptions() {
      return { columns: 12, min_columns: 6, rows: "auto" };
    }
    static getConfigElement() {
      return document.createElement(`fan-card-editor${SUFFIX}`);
    }
    static getStubConfig(hass) {
      const first = hass && Object.keys(hass.states).find((id) => id.startsWith("fan."));
      return first ? { entity: first } : { demo: true };
    }
  };
  kitCompactable(FanCard);
  function registerFanCard() {
    if (!customElements.get(`fan-card-editor${SUFFIX}`)) customElements.define(`fan-card-editor${SUFFIX}`, FanCardEditor);
    if (!customElements.get(`fan-card${SUFFIX}`)) customElements.define(`fan-card${SUFFIX}`, FanCard);
    window.customCards = window.customCards || [];
    window.customCards.push({
      type: `fan-card${SUFFIX}`,
      name: `Fan Card${LABEL}`,
      description: "A fan: speed gauge, Off and speed buttons, presets and oscillate",
      preview: true,
      documentationURL: "https://github.com/J45PER/church-drive-cards#readme"
    });
  }

  // src/air-purifier-card.js
  var AP_BANDS = { good_max: 35, fair_max: 75, poor_max: 115 };
  var apBand = (c, k) => c && c[k] != null && c[k] !== "" ? Number(c[k]) : AP_BANDS[k];
  function apQuality(pm, c) {
    if (pm == null) return { color: KIT_COLOR.off, word: "" };
    if (pm <= apBand(c, "good_max")) return { color: KIT_COLOR.good, word: "Good" };
    if (pm <= apBand(c, "fair_max")) return { color: KIT_COLOR.fair, word: "Fair" };
    if (pm <= apBand(c, "poor_max")) return { color: KIT_COLOR.poor, word: "Poor" };
    return { color: KIT_COLOR.bad, word: "Very poor" };
  }
  function apAllergen(v) {
    if (v == null) return null;
    if (v <= 3) return { color: KIT_COLOR.good, word: "Low" };
    if (v <= 6) return { color: KIT_COLOR.fair, word: "Moderate" };
    if (v <= 9) return { color: KIT_COLOR.poor, word: "High" };
    return { color: KIT_COLOR.bad, word: "Very high" };
  }
  var AP_MODE_ICONS = {
    auto: "mdi:autorenew",
    "auto (general)": "mdi:autorenew",
    allergen: "mdi:flower",
    medium: "mdi:fan",
    turbo: "mdi:rocket-launch",
    sleep: "mdi:power-sleep",
    night: "mdi:power-sleep",
    low: "mdi:fan-speed-1",
    high: "mdi:fan-speed-3"
  };
  var AP_ROWS = { show_gauge: true, show_allergen: true, show_graph: true, show_modes: true, show_filters: true };
  var row2 = (c, k) => c[k] != null ? !!c[k] : AP_ROWS[k];
  function apDemo() {
    return {
      fan: {
        entity_id: "fan.demo_purifier",
        state: "on",
        attributes: { friendly_name: "Air Purifier", preset_modes: ["auto", "turbo", "medium", "sleep"], preset_mode: "auto", model_id: "AC0951/13" }
      },
      pm25: 30,
      allergen: 3,
      filters: [
        { name: "NanoProtect filter", value: 90, hours: 8594 },
        { name: "Pre-filter", value: 18, hours: 130 }
      ],
      pmPts: kitDemoSeries([8, 7, 9, 12, 18, 24, 31, 28, 22, 26, 33, 30])
    };
  }
  function apSiblings(hass, fanId) {
    const base = String(fanId || "").split(".")[1];
    if (!hass || !base) return {};
    const ids = Object.keys(hass.states).filter((id) => id.startsWith(`sensor.${base}_`));
    const find = (re) => ids.find((id) => re.test(id));
    return {
      pm25_entity: find(/pm2_?5/),
      allergen_entity: find(/allergen/),
      filters: ids.filter((id) => /filter/.test(id) && !isNaN(Number(hass.states[id].state))).map((entity) => ({ entity }))
    };
  }
  var filterName = (hass, f) => {
    if (f.name) return f.name;
    const st = hass && hass.states[f.entity];
    const name = st && st.attributes.friendly_name || f.entity;
    return name.replace(/^.*?(pre-?filter|nanoprotect filter|hepa filter|carbon filter|filter)/i, "$1").replace(/^./, (c) => c.toUpperCase());
  };
  var AirPurifierCardEditor = createFormEditor({
    fill: (config, hass) => {
      if (config.demo || !config.entity || config.pm25_entity !== void 0 || !hass) return config;
      const found = apSiblings(hass, config.entity);
      return { ...config, pm25_entity: found.pm25_entity || "", allergen_entity: found.allergen_entity || "", filters: found.filters || [] };
    },
    schema: (config) => [
      ...config.demo ? [] : [{ name: "entity", selector: { entity: { domain: "fan" } } }],
      { name: "name", selector: { text: {} } },
      {
        type: "expandable",
        name: "",
        title: "Rows to show",
        flatten: true,
        schema: [...Object.keys(AP_ROWS).map((name) => ({ name, selector: { boolean: {} }, default: AP_ROWS[name] })), { name: "smooth_graphs", selector: { boolean: {} }, default: true }]
      },
      ...config.demo ? [] : [
        {
          type: "expandable",
          name: "",
          title: "Sensors (found automatically)",
          flatten: true,
          schema: [
            { name: "pm25_entity", selector: { entity: { domain: "sensor" } } },
            { name: "allergen_entity", selector: { entity: { domain: "sensor" } } },
            {
              name: "filters",
              selector: {
                object: {
                  multiple: true,
                  label_field: "name",
                  fields: {
                    entity: { label: "Filter life sensor (%)", required: true, selector: { entity: { domain: "sensor" } } },
                    name: { label: "Name (optional)", selector: { text: {} } }
                  }
                }
              }
            }
          ]
        }
      ],
      {
        type: "expandable",
        name: "",
        title: "Air quality bands (PM2.5 \xB5g/m\xB3)",
        flatten: true,
        schema: Object.keys(AP_BANDS).map((name) => ({ name, selector: { number: { min: 1, max: 500, mode: "box", unit_of_measurement: "\xB5g/m\xB3" } } }))
      },
      {
        type: "expandable",
        name: "",
        title: "Demo mode (a pretend purifier, for Design Presets)",
        flatten: true,
        schema: [{ name: "demo", selector: { boolean: {} } }]
      }
    ],
    labels: {
      entity: "Air purifier (fan)",
      name: "Title (optional)",
      show_gauge: "PM2.5 gauge and air quality",
      show_allergen: "Allergen index",
      show_graph: "PM2.5 graph (24 hours)",
      show_modes: "Mode buttons",
      show_filters: "Filter life",
      smooth_graphs: "Smooth the graph (averages jumpy readings)",
      pm25_entity: "PM2.5 sensor",
      allergen_entity: "Allergen index sensor (optional)",
      filters: "Filters",
      good_max: "Good up to",
      fair_max: "Fair up to",
      poor_max: "Poor up to (above is very poor)",
      demo: "Use a pretend purifier instead of a real one"
    },
    helpers: {
      good_max: "Defaults 35 / 75 / 115, the Chinese standard Philips purifiers use, so the card matches the Philips app.",
      filters: 'Shown as bars; amber under 25% ("Clean soon" for a pre-filter, "Replace soon" otherwise), red under 10%.'
    }
  });
  var AirPurifierCard = class extends HTMLElement {
    setConfig(config) {
      if (!config.entity && !config.demo) throw new Error("entity required (or set demo: true)");
      this.config = config;
      this._built = false;
      this._demo = config.demo ? apDemo() : null;
      this._pending = new KitPending(this);
      this._hist = config.demo || !config.pm25_entity ? null : new KitHistory(this, [config.pm25_entity], 24);
    }
    set hass(hass) {
      this._hass = hass;
      this._render();
    }
    _render() {
      if (!this._hass) return;
      const c = this.config;
      const st = this._pending.apply(this._demo ? this._demo.fan : this._hass.states[c.entity]);
      if (!st) return;
      if (this._compact) return kitCompact(this, this._compactSpec(st));
      if (!this._built) {
        this.innerHTML = kitShell(`
        <div class="ap-top" style="display:flex; align-items:center; gap:14px;">
          <div class="ap-gauge"></div>
          <div class="ck-info ap-info"></div>
          <div class="ap-quality" style="flex:none; text-align:right;"></div>
        </div>
        <div class="ap-graph"></div>
        <div class="ck-row ap-modes"></div>
        <div class="ap-filters" style="display:flex; flex-direction:column; gap:10px;"></div>`);
        this._built = true;
      }
      if (this._hist && row2(c, "show_graph") && this._hist.due()) this._hist.load(this._hass);
      const a = st.attributes;
      const on = st.state === "on";
      const pm = this._demo ? this._demo.pm25 : kitNum(c.pm25_entity && this._hass.states[c.pm25_entity]);
      const allergen = this._demo ? this._demo.allergen : kitNum(c.allergen_entity && this._hass.states[c.allergen_entity]);
      const q = apQuality(pm, c);
      const mode = on ? a.preset_mode ? kitCap(a.preset_mode) : "On" : st.state === "unavailable" ? "Unavailable" : "Off";
      const color = on ? q.color : KIT_COLOR.off;
      kitHead(this, c.name || a.friendly_name || c.entity, [mode, q.word ? `${q.word} air` : ""].filter(Boolean).join(" \xB7 ") + (this._demo ? " \xB7 demo" : ""), color, on && pm > 35 ? 12 : 0);
      kitHealthBanner(this, this._hass, c.entity, !!(this._demo || c.demo));
      const top = this.querySelector(".ap-top");
      top.style.display = row2(c, "show_gauge") ? "flex" : "none";
      this.querySelector(".ap-gauge").innerHTML = kitGauge(pm == null ? 0 : pm / (apBand(c, "poor_max") * 1.3), q.color, pm == null ? "\u2013" : String(Math.round(pm)), "PM2.5 \xB5g/m\xB3");
      const al = row2(c, "show_allergen") ? apAllergen(allergen) : null;
      this.querySelector(".ap-info").innerHTML = [
        al ? `<span>${iconHtml("mdi:flower", { size: "18px", style: `color:${al.color};` })}Allergens ${allergen} \xB7 ${al.word}</span>` : "",
        a.model_id ? `<span>${iconHtml("mdi:air-purifier", { size: "18px" })}${a.model_id}</span>` : ""
      ].join("");
      this.querySelector(".ap-quality").innerHTML = q.word ? `<div style="font-size:1.35rem; font-weight:700; color:${q.color};">${q.word}</div><div class="ck-sub">air quality</div>` : "";
      const gBox = this.querySelector(".ap-graph");
      if (row2(c, "show_graph") && (this._demo || c.pm25_entity)) {
        const pts = this._demo ? this._demo.pmPts : this._hist && this._hist.data ? this._hist.data[c.pm25_entity] : null;
        const meta = {};
        const svg2 = kitGraph([{ pts, current: pm, color: q.color, fill: true, pad: 2, format: (v) => `PM2.5 ${Math.round(v)} \xB5g/m\xB3` }], { height: 48, label: "PM2.5, last 24 hours", meta, smooth: c.smooth_graphs !== false });
        gBox.style.display = "block";
        const gsig = JSON.stringify([pm, q.color, this._hist && this._hist.at, !!svg2, c.smooth_graphs]);
        if (gsig !== this._gsig) {
          this._gsig = gsig;
          gBox.innerHTML = svg2 ? `${svg2}<div style="display:flex; justify-content:space-between; font-size:0.78rem; color:var(--secondary-text-color); margin-top:2px;"><span style="color:${q.color};">\u25CF PM2.5 ${kitRange(pts, pm, 0, "")}</span><span>last 24 h</span></div>` : `<div class="ck-sub">${this._hist && this._hist.data ? "No PM2.5 history yet" : "Loading history\u2026"}</div>`;
          kitScrub(gBox.querySelector("svg"), meta);
        }
      } else gBox.style.display = "none";
      const presets = a.preset_modes || [];
      const modes = row2(c, "show_modes") ? [
        { key: "__off", name: "Off", icon: "mdi:power", color: KIT_COLOR.off, on: !on },
        ...presets.map((p) => ({
          key: p,
          name: kitCap(p),
          icon: AP_MODE_ICONS[String(p).toLowerCase()] || "mdi:fan",
          color: /sleep|night/i.test(p) ? KIT_COLOR.sleep : KIT_COLOR.good,
          on: on && a.preset_mode === p
        }))
      ] : [];
      kitTiles(this.querySelector(".ap-modes"), modes, (t) => this._mode(t.key), { column: true });
      const fBox = this.querySelector(".ap-filters");
      const filters = !row2(c, "show_filters") ? [] : this._demo ? this._demo.filters : (c.filters || []).map((f) => {
        const fs = this._hass.states[f.entity];
        return { name: filterName(this._hass, f), value: kitNum(fs), hours: fs ? Number(fs.attributes.time_remaining) : NaN, entity: f.entity };
      });
      fBox.style.display = filters.length ? "flex" : "none";
      fBox.innerHTML = filters.map((f, i) => {
        const v = f.value == null ? 0 : f.value;
        const fc = v < 10 ? KIT_COLOR.bad : v < 25 ? KIT_COLOR.fair : KIT_COLOR.good;
        const soon = v < 25 ? /pre/i.test(f.name) ? "Clean soon" : "Replace soon" : "";
        const left = !isNaN(f.hours) && f.hours >= 0 ? f.hours >= 48 ? `${Math.round(f.hours / 24)} days` : `${Math.round(f.hours)} hours` : "";
        return `<div class="ap-filter" data-i="${i}" style="display:grid; grid-template-columns:minmax(0,1fr) auto; gap:4px 10px; font-size:0.85rem;">
          <span style="display:flex; align-items:center; gap:6px; min-width:0;"><span class="ap-fname" style="white-space:nowrap; overflow:hidden; text-overflow:ellipsis;"></span>${soon ? `<span class="ck-chip" style="color:${fc}; background:color-mix(in srgb, ${fc} 18%, transparent);">${soon}</span>` : ""}</span>
          <span class="ck-sub" style="font-variant-numeric:tabular-nums;">${f.value == null ? "\u2013" : `${Math.round(v)}%`}${left ? ` \xB7 ${left}` : ""}</span>
          <div class="ck-bar" style="grid-column:1/-1;"><i style="width:${Math.max(0, Math.min(100, v))}%; background:${fc};"></i></div>
        </div>`;
      }).join("");
      fBox.querySelectorAll(".ap-filter").forEach((el) => {
        const f = filters[Number(el.dataset.i)];
        el.querySelector(".ap-fname").textContent = f.name;
      });
      hydrateIcons(this);
    }
    // One row: name, PM2.5 and quality, and Off / Auto / Sleep (plus the
    // current mode if it's another one).
    _compactSpec(st) {
      const c = this.config, a = st.attributes, on = st.state === "on";
      const pm = this._demo ? this._demo.pm25 : kitNum(c.pm25_entity && this._hass.states[c.pm25_entity]);
      const q = apQuality(pm, c);
      const presets = a.preset_modes || [];
      const pick = presets.filter((p) => /^(auto|sleep|night)$/i.test(p));
      if (on && a.preset_mode && !pick.includes(a.preset_mode)) pick.push(a.preset_mode);
      const mode = on ? a.preset_mode ? kitCap(a.preset_mode) : "On" : st.state === "unavailable" ? "Unavailable" : "Off";
      return {
        name: c.name || a.friendly_name || c.entity,
        color: on ? q.color : KIT_COLOR.off,
        value: pm == null ? "" : String(Math.round(pm)),
        valueColor: q.color,
        status: pm == null ? mode : `PM2.5 \xB7 ${q.word || mode}`,
        buttons: [
          { key: "__off", icon: "mdi:power", title: "Off", on: !on, color: KIT_COLOR.off },
          ...pick.map((p) => ({ key: p, icon: AP_MODE_ICONS[String(p).toLowerCase()] || "mdi:fan", title: kitCap(p), on: on && a.preset_mode === p, color: /sleep|night/i.test(p) ? KIT_COLOR.sleep : KIT_COLOR.good }))
        ],
        onButton: (b) => this._mode(b.key)
      };
    }
    _mode(key) {
      if (this._demo) {
        const f = this._demo.fan;
        if (key === "__off") f.state = "off";
        else {
          f.state = "on";
          f.attributes.preset_mode = key;
        }
        this._render();
        return;
      }
      this._pending.set(key === "__off" ? { state: "off" } : { state: "on", attrs: { preset_mode: key } });
      this._render();
      if (key === "__off") this._hass.callService("fan", "turn_off", { entity_id: this.config.entity });
      else this._hass.callService("fan", "set_preset_mode", { entity_id: this.config.entity, preset_mode: key });
    }
    getCardSize() {
      const c = this.config;
      return 2 + (row2(c, "show_gauge") ? 2 : 0) + (row2(c, "show_graph") ? 1 : 0) + (row2(c, "show_modes") ? 1 : 0) + (row2(c, "show_filters") ? 1 : 0);
    }
    getGridOptions() {
      return { columns: 12, min_columns: 6, rows: "auto" };
    }
    static getConfigElement() {
      return document.createElement(`air-purifier-card-editor${SUFFIX}`);
    }
    static getStubConfig() {
      return { demo: true };
    }
  };
  kitCompactable(AirPurifierCard);
  function registerAirPurifierCard() {
    if (!customElements.get(`air-purifier-card-editor${SUFFIX}`)) customElements.define(`air-purifier-card-editor${SUFFIX}`, AirPurifierCardEditor);
    if (!customElements.get(`air-purifier-card${SUFFIX}`)) customElements.define(`air-purifier-card${SUFFIX}`, AirPurifierCard);
    window.customCards = window.customCards || [];
    window.customCards.push({
      type: `air-purifier-card${SUFFIX}`,
      name: `Air Purifier Card${LABEL}`,
      description: "An air purifier: PM2.5 gauge, allergen index, 24-hour graph, modes and filter life",
      preview: true,
      documentationURL: "https://github.com/J45PER/church-drive-cards#readme"
    });
  }

  // src/co-alarm-card.js
  var CO_PARTS = {
    alarm_entity: (p) => [`binary_sensor.${p}alarm_status`, `binary_sensor.${p}carbon_monoxide`, `binary_sensor.${p}co`],
    status_entity: (p) => [`sensor.${p}device_status`, `sensor.${p}status`],
    battery_entity: (p) => [`sensor.${p}battery`, `sensor.${p}battery_level`],
    report_entity: (p) => [`sensor.${p}report_time`, `sensor.${p}last_report`],
    test_entity: (p) => [`button.${p}device_test`, `button.${p}test`, `button.${p}self_test`],
    mute_entity: (p) => [`button.${p}mute`, `button.${p}silence`]
  };
  function coFill(hass, readingId) {
    const obj = String(readingId || "").split(".")[1] || "";
    const prefix = obj.replace(/(co_reading|co_level|co|carbon_monoxide)$/, "");
    const out = {};
    Object.entries(CO_PARTS).forEach(([key, list]) => {
      out[key] = list(prefix).find((id) => hass.states[id]) || "";
    });
    return out;
  }
  function coDemo(config) {
    const alarm = config.demo_state === "alarm";
    return { ppm: alarm ? 86 : 0, alarm, status: alarm ? "alarm" : "normal", battery: 100, report: new Date(Date.now() - 3 * 36e5).toISOString() };
  }
  var CoAlarmCardEditor = createFormEditor({
    fill: (config, hass) => {
      if (config.demo || !config.entity || config.alarm_entity !== void 0 || !hass) return config;
      return { ...config, ...coFill(hass, config.entity) };
    },
    schema: (config) => [
      ...config.demo ? [] : [{ name: "entity", selector: { entity: { domain: "sensor" } } }],
      { name: "name", selector: { text: {} } },
      { name: "show_buttons", selector: { boolean: {} }, default: true },
      ...config.demo ? [] : [
        {
          type: "expandable",
          name: "",
          title: "Other alarm entities (found automatically)",
          flatten: true,
          schema: [
            { name: "alarm_entity", selector: { entity: { domain: "binary_sensor" } } },
            { name: "status_entity", selector: { entity: { domain: "sensor" } } },
            { name: "battery_entity", selector: { entity: { domain: "sensor" } } },
            { name: "report_entity", selector: { entity: { domain: "sensor" } } },
            { name: "test_entity", selector: { entity: { domain: "button" } } },
            { name: "mute_entity", selector: { entity: { domain: "button" } } }
          ]
        }
      ],
      {
        type: "expandable",
        name: "",
        title: "Demo mode (a pretend alarm, for Design Presets)",
        flatten: true,
        schema: [
          { name: "demo", selector: { boolean: {} } },
          { name: "demo_state", selector: { select: { mode: "dropdown", options: [{ value: "normal", label: "Normal" }, { value: "alarm", label: "CO detected" }] } } }
        ]
      }
    ],
    labels: {
      entity: "CO reading sensor (ppm)",
      name: "Title (optional)",
      show_buttons: "Test and Mute buttons",
      alarm_entity: "Alarm (on when CO is found)",
      status_entity: "Device status (optional)",
      battery_entity: "Battery (optional)",
      report_entity: "Last report time (optional)",
      test_entity: "Test button (optional)",
      mute_entity: "Mute button (optional)",
      demo: "Use a pretend alarm instead of a real one",
      demo_state: "Pretend alarm shows"
    },
    helpers: { entity: "Picking it fills in the rest of the alarm below." }
  });
  var CoAlarmCard = class extends HTMLElement {
    setConfig(config) {
      if (!config.entity && !config.demo) throw new Error("entity required (or set demo: true)");
      this.config = config;
      this._built = false;
      this._demo = config.demo ? coDemo(config) : null;
    }
    set hass(hass) {
      this._hass = hass;
      this._render();
    }
    _data() {
      if (this._demo) return this._demo;
      const c = this.config, s = (id) => id && this._hass.states[id];
      const alarm = s(c.alarm_entity);
      const report = s(c.report_entity);
      return {
        ppm: kitNum(s(c.entity)),
        alarm: !!alarm && alarm.state === "on",
        status: s(c.status_entity) ? s(c.status_entity).state : null,
        battery: kitNum(s(c.battery_entity)),
        report: report && report.state,
        unavailable: !s(c.entity) || s(c.entity).state === "unavailable"
      };
    }
    _render() {
      if (!this._hass) return;
      const c = this.config;
      if (this._compact) {
        const d2 = this._data();
        const high2 = d2.alarm || d2.ppm != null && d2.ppm >= 50;
        const color2 = d2.unavailable ? KIT_COLOR.off : high2 ? KIT_COLOR.bad : d2.ppm >= 10 ? KIT_COLOR.fair : KIT_COLOR.good;
        return kitCompact(this, {
          name: c.name || "Carbon Monoxide",
          color: color2,
          value: d2.ppm == null ? "\u2013" : String(Math.round(d2.ppm)),
          valueColor: color2,
          status: `ppm \xB7 ${d2.unavailable ? "Unavailable" : d2.alarm ? "CO detected" : d2.status ? kitCap(d2.status) : "Normal"}${d2.battery != null && d2.battery < 20 ? ` \xB7 battery ${Math.round(d2.battery)}%` : ""}`
        });
      }
      if (!this._built) {
        this.innerHTML = kitShell(`
        <div class="co-warn" style="display:none; align-items:center; gap:10px; padding:10px 12px; border-radius:12px; background:${KIT_COLOR.bad}; color:#fff; font-weight:600;"></div>
        <div style="display:flex; align-items:center; gap:14px;">
          <div class="co-gauge"></div>
          <div class="ck-info co-info"></div>
        </div>
        <div class="ck-row co-buttons"></div>`);
        this._built = true;
      }
      const d = this._data();
      const high = d.alarm || d.ppm != null && d.ppm >= 50;
      const color = d.unavailable ? KIT_COLOR.off : high ? KIT_COLOR.bad : d.ppm >= 10 ? KIT_COLOR.fair : KIT_COLOR.good;
      const word = d.unavailable ? "Unavailable" : d.alarm ? "CO detected" : d.status ? kitCap(d.status) : "Normal";
      kitHead(this, c.name || "Carbon Monoxide", word + (this._demo ? " \xB7 demo" : ""), color, high ? 30 : 0);
      kitHealthBanner(this, this._hass, c.entity, !!(this._demo || c.demo));
      const warn = this.querySelector(".co-warn");
      warn.style.display = high ? "flex" : "none";
      if (high) warn.innerHTML = `${iconHtml("mdi:alert", { size: "24px" })}<span>Carbon monoxide found. Get everyone outside and open doors and windows.</span>`;
      this.querySelector(".co-gauge").innerHTML = kitGauge(d.ppm == null ? 0 : Math.max(0.02, d.ppm / 100), color, d.ppm == null ? "\u2013" : String(Math.round(d.ppm)), "ppm CO");
      const when2 = d.report && !isNaN(Date.parse(d.report)) ? new Date(d.report) : null;
      const whenText = when2 ? when2.toLocaleString(void 0, { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" }) : "";
      this.querySelector(".co-info").innerHTML = [
        `<span>${iconHtml(high ? "mdi:alert-circle" : "mdi:shield-check", { size: "18px", style: `color:${color};` })}${high ? "CO detected" : "No CO detected"}</span>`,
        d.battery != null ? `<span>${iconHtml(d.battery < 20 ? "mdi:battery-alert" : "mdi:battery", { size: "18px", style: `color:${d.battery < 20 ? KIT_COLOR.bad : KIT_COLOR.good};` })}Battery ${Math.round(d.battery)}%</span>` : "",
        whenText ? `<span>${iconHtml("mdi:clock-outline", { size: "18px" })}Reported ${whenText}</span>` : ""
      ].join("");
      const buttons = c.show_buttons === false ? [] : [
        ...this._demo || c.test_entity ? [{ key: "test", name: "Hold to test", icon: "mdi:bell-ring", color: KIT_COLOR.good, hold: true }] : [],
        ...this._demo || c.mute_entity ? [{ key: "mute", name: "Mute", icon: "mdi:volume-off", color: KIT_COLOR.off }] : []
      ];
      kitTiles(this.querySelector(".co-buttons"), buttons, (t) => this._press(t.key));
      hydrateIcons(this);
    }
    _press(key) {
      if (this._demo) return;
      const id = key === "test" ? this.config.test_entity : this.config.mute_entity;
      if (id) this._hass.callService("button", "press", { entity_id: id });
    }
    getCardSize() {
      return 4;
    }
    getGridOptions() {
      return { columns: 12, min_columns: 6, rows: "auto" };
    }
    static getConfigElement() {
      return document.createElement(`co-alarm-card-editor${SUFFIX}`);
    }
    static getStubConfig() {
      return { demo: true };
    }
  };
  kitCompactable(CoAlarmCard);
  function registerCoAlarmCard() {
    if (!customElements.get(`co-alarm-card-editor${SUFFIX}`)) customElements.define(`co-alarm-card-editor${SUFFIX}`, CoAlarmCardEditor);
    if (!customElements.get(`co-alarm-card${SUFFIX}`)) customElements.define(`co-alarm-card${SUFFIX}`, CoAlarmCard);
    window.customCards = window.customCards || [];
    window.customCards.push({
      type: `co-alarm-card${SUFFIX}`,
      name: `Carbon Monoxide Card${LABEL}`,
      description: "A CO alarm: reading, status, battery, and hold-to-test",
      preview: true,
      documentationURL: "https://github.com/J45PER/church-drive-cards#readme"
    });
  }

  // src/cover-card.js
  var COVER_ICONS = {
    curtain: ["mdi:curtains", "mdi:curtains-closed"],
    shutter: ["mdi:window-shutter-open", "mdi:window-shutter"],
    garage: ["mdi:garage-open", "mdi:garage"],
    door: ["mdi:door-open", "mdi:door-closed"],
    window: ["mdi:window-open", "mdi:window-closed"],
    awning: ["mdi:awning-outline", "mdi:awning-outline"],
    blind: ["mdi:blinds-horizontal", "mdi:blinds-horizontal-closed"]
  };
  function coverDemo(config) {
    const kind = config.demo_state || "unknown";
    return {
      entity_id: "cover.demo",
      state: kind === "unknown" ? "unknown" : kind,
      attributes: {
        friendly_name: "Blind",
        device_class: "blind",
        supported_features: kind === "unknown" ? 11 : 15,
        assumed_state: kind === "unknown",
        ...kind === "open" ? { current_position: 60 } : kind === "closed" ? { current_position: 0 } : {}
      }
    };
  }
  var CoverCardEditor = createFormEditor({
    schema: (config) => [
      ...config.demo ? [] : [{ name: "entity", selector: { entity: { domain: "cover" } } }],
      { name: "name", selector: { text: {} } },
      { name: "subtitle", selector: { text: {} } },
      { name: "icon", selector: { icon: {} } },
      { name: "show_position", selector: { boolean: {} }, default: true },
      {
        type: "expandable",
        name: "",
        title: "Demo mode (a pretend blind, for Design Presets)",
        flatten: true,
        schema: [
          { name: "demo", selector: { boolean: {} } },
          {
            name: "demo_state",
            selector: {
              select: {
                mode: "dropdown",
                options: [
                  { value: "unknown", label: "Position unknown (like an RF blind)" },
                  { value: "open", label: "Open 60%" },
                  { value: "closed", label: "Closed" }
                ]
              }
            }
          }
        ]
      }
    ],
    labels: {
      entity: "Blind, curtain or other cover",
      name: "Title (optional)",
      subtitle: "Small text beside the icon (optional, e.g. the room)",
      icon: "Icon (optional)",
      show_position: "Position bar (if the cover reports one)",
      demo: "Use a pretend blind instead of a real one",
      demo_state: "Pretend blind starts"
    }
  });
  var CoverCard = class extends HTMLElement {
    setConfig(config) {
      if (!config.entity && !config.demo) throw new Error("entity required (or set demo: true)");
      this.config = config;
      this._built = false;
      this._demo = config.demo ? coverDemo(config) : null;
      this._last = null;
    }
    set hass(hass) {
      this._hass = hass;
      this._render();
    }
    _state() {
      return this._demo || this._hass && this._hass.states[this.config.entity];
    }
    _render() {
      const st = this._state();
      if (!st || !this._hass) return;
      const c = this.config;
      if (this._compact) {
        const a2 = st.attributes, f2 = a2.supported_features || 0, pos2 = a2.current_position;
        const known2 = ["open", "closed", "opening", "closing"].includes(st.state);
        const color2 = st.state === "unavailable" ? KIT_COLOR.off : KIT_COLOR.blind;
        const active2 = !known2 ? this._last : st.state === "open" || st.state === "opening" ? "open" : "close";
        const word2 = st.state === "unavailable" ? "Unavailable" : known2 ? st.state === "open" && pos2 != null && pos2 < 100 ? `Open ${pos2}%` : st.state.charAt(0).toUpperCase() + st.state.slice(1) : this._last ? `Last: ${this._last.charAt(0).toUpperCase() + this._last.slice(1)}` : "Position unknown";
        return kitCompact(this, {
          name: c.name || a2.friendly_name || c.entity,
          color: color2,
          status: word2,
          buttons: [
            ...f2 & 1 ? [{ key: "open", icon: "mdi:arrow-up", title: "Open", on: active2 === "open", color: color2 }] : [],
            ...f2 & 8 ? [{ key: "stop", icon: "mdi:stop", title: "Stop", on: !known2 && this._last === "stop", color: color2 }] : [],
            ...f2 & 2 ? [{ key: "close", icon: "mdi:arrow-down", title: "Close", on: active2 === "close", color: color2 }] : []
          ],
          onButton: (b) => this._press(b.key)
        });
      }
      if (!this._built) {
        this.innerHTML = kitShell(`
        <div style="display:flex; align-items:center; gap:14px;">
          <div class="cv-icon" style="flex:none;"></div>
          <div class="ck-info cv-info"></div>
        </div>
        <div class="cv-pos"></div>
        <div class="ck-row cv-buttons"></div>`);
        this.querySelector(".cv-icon").addEventListener("click", () => {
          const st2 = this._state();
          if (!this._demo && st2 && (st2.attributes.supported_features || 0) & 4) kitMoreInfo(this, c.entity);
        });
        this._built = true;
      }
      const a = st.attributes;
      const pos = a.current_position;
      const known = ["open", "closed", "opening", "closing"].includes(st.state);
      const word = st.state === "unavailable" ? "Unavailable" : !known ? "Position unknown" : st.state === "open" && pos != null && pos < 100 ? `Open ${pos}%` : st.state.charAt(0).toUpperCase() + st.state.slice(1);
      const closed = st.state === "closed" || !known && this._last === "close";
      const color = st.state === "unavailable" ? KIT_COLOR.off : KIT_COLOR.blind;
      kitHead(this, c.name || a.friendly_name || c.entity, word + (this._demo ? " \xB7 demo" : ""), color);
      kitHealthBanner(this, this._hass, c.entity, !!(this._demo || c.demo));
      const icons = COVER_ICONS[a.device_class] || COVER_ICONS.blind;
      this.querySelector(".cv-icon").style.cursor = !this._demo && (a.supported_features || 0) & 4 ? "pointer" : "default";
      this.querySelector(".cv-icon").innerHTML = iconHtml(c.icon || icons[closed ? 1 : 0], { size: "44px", style: `color:${color};` });
      const lines = [];
      if (c.subtitle) lines.push(`<span class="cv-sub"></span>`);
      if (!known && this._last) lines.push(`<span>Last command: ${this._last.charAt(0).toUpperCase() + this._last.slice(1)}</span>`);
      else if (!known && a.assumed_state) lines.push("<span>This blind doesn\u2019t report where it is</span>");
      this.querySelector(".cv-info").innerHTML = lines.join("");
      const sub = this.querySelector(".cv-sub");
      if (sub) sub.textContent = c.subtitle;
      const posBox = this.querySelector(".cv-pos");
      const showPos = c.show_position !== false && pos != null;
      posBox.style.display = showPos ? "block" : "none";
      if (showPos) posBox.innerHTML = `<div class="ck-bar"><i style="width:${pos}%; background:${color};"></i></div><div class="ck-sub" style="display:flex; justify-content:space-between; margin-top:3px;"><span>Closed</span><span>${pos}% open</span></div>`;
      const f = a.supported_features || 0;
      const active = !known ? this._last : st.state === "open" || st.state === "opening" ? "open" : "close";
      const tiles = [
        ...f & 1 ? [{ key: "open", name: "Open", icon: "mdi:arrow-up", color, on: active === "open" }] : [],
        ...f & 8 ? [{ key: "stop", name: "Stop", icon: "mdi:stop", color, on: !known && this._last === "stop" }] : [],
        ...f & 2 ? [{ key: "close", name: "Close", icon: "mdi:arrow-down", color, on: active === "close" }] : []
      ];
      kitTiles(this.querySelector(".cv-buttons"), tiles, (t) => this._press(t.key));
      hydrateIcons(this);
    }
    _press(key) {
      this._last = key;
      if (this._demo) {
        const d = this._demo;
        if (!d.attributes.assumed_state) {
          if (key === "open") {
            d.state = "open";
            d.attributes.current_position = 100;
          }
          if (key === "close") {
            d.state = "closed";
            d.attributes.current_position = 0;
          }
        }
        this._render();
        return;
      }
      this._hass.callService("cover", `${key}_cover`, { entity_id: this.config.entity });
      this._render();
    }
    getCardSize() {
      return 3;
    }
    getGridOptions() {
      return { columns: 12, min_columns: 6, rows: "auto" };
    }
    static getConfigElement() {
      return document.createElement(`cover-card-editor${SUFFIX}`);
    }
    static getStubConfig(hass) {
      const first = hass && Object.keys(hass.states).find((id) => id.startsWith("cover."));
      return first ? { entity: first } : { demo: true };
    }
  };
  kitCompactable(CoverCard);
  function registerCoverCard() {
    if (!customElements.get(`cover-card-editor${SUFFIX}`)) customElements.define(`cover-card-editor${SUFFIX}`, CoverCardEditor);
    if (!customElements.get(`cover-card${SUFFIX}`)) customElements.define(`cover-card${SUFFIX}`, CoverCard);
    window.customCards = window.customCards || [];
    window.customCards.push({
      type: `cover-card${SUFFIX}`,
      name: `Blind Card${LABEL}`,
      description: "A blind, curtain or other cover: Open, Stop and Close, with position when known",
      preview: true,
      documentationURL: "https://github.com/J45PER/church-drive-cards#readme"
    });
  }

  // src/device-health-card.js
  var DH_ICONS = {
    fan: "mdi:fan",
    climate: "mdi:thermostat",
    cover: "mdi:blinds-horizontal",
    light: "mdi:lightbulb",
    sensor: "mdi:gauge",
    binary_sensor: "mdi:checkbox-blank-circle-outline",
    alarm_control_panel: "mdi:shield-home"
  };
  function dhDemo() {
    const t = (mins) => new Date(Date.now() - mins * 6e4).toISOString();
    return {
      "fan.demo_fan": { name: "Bedroom Fan", status: "stale", reason: "Came back with old readings after a restart", since: t(34), last_heard: t(34), usual_gap: 194, last_real: { state: "on", preset_mode: "speed_1", at: t(53) }, fixes: ["19:32 Refreshed", "19:33 Re-synced to its 19:11 reading"] },
      "fan.demo_purifier": { name: "Air Purifier", status: "ok", last_heard: t(1), usual_gap: 194 },
      "climate.demo_downstairs": { name: "Downstairs", status: "ok", last_heard: t(4), usual_gap: 900 },
      "sensor.demo_co": { name: "Carbon Monoxide Alarm CO Reading", status: "ok", last_heard: t(180) }
    };
  }
  var ago = (iso) => {
    if (!iso) return "not heard yet";
    const m = Math.round((Date.now() - Date.parse(iso)) / 6e4);
    if (m < 1) return "just now";
    if (m < 60) return `${m} min ago`;
    const h = Math.round(m / 60);
    return h < 48 ? `${h} h ago` : `${Math.round(h / 24)} days ago`;
  };
  var every = (s) => !s ? "" : s < 90 ? `every ${Math.round(s)} s` : s < 5400 ? `every ${Math.round(s / 60)} min` : `every ${Math.round(s / 3600)} h`;
  var DeviceHealthCardEditor = createFormEditor({
    schema: () => [
      { name: "title", selector: { text: {} } },
      { name: "show_ok", selector: { boolean: {} }, default: true },
      { name: "demo", selector: { boolean: {} } }
    ],
    labels: {
      title: "Title (optional)",
      show_ok: "List devices that are fine too",
      demo: "Show pretend devices (for Design Presets)"
    },
    helpers: {
      title: "Choose the devices to watch in Settings \u2192 Devices & services \u2192 Church Drive \u2192 Configure \u2192 Device health."
    }
  });
  var DeviceHealthCard = class extends HTMLElement {
    setConfig(config) {
      this.config = config || {};
      this._built = false;
    }
    set hass(hass) {
      this._hass = hass;
      this._render();
    }
    _render() {
      if (!this._hass) return;
      const c = this.config;
      if (this._compact) {
        const sensor2 = this._hass.states[HEALTH_SENSOR];
        const devices2 = c.demo ? dhDemo() : sensor2 && sensor2.attributes.devices || {};
        const bad2 = Object.keys(devices2).filter((id) => devices2[id].status !== "ok");
        return kitCompact(this, {
          name: c.title || "Device Health",
          color: bad2.length ? KIT_COLOR.fair : KIT_COLOR.good,
          status: bad2.length ? bad2.map((id) => devices2[id].name || id).join(", ") : "All responding"
        });
      }
      if (!this._built) {
        this.innerHTML = kitShell(`<div class="dh-list" style="display:flex; flex-direction:column;"></div>`);
        this._list = this.querySelector(".dh-list");
        this._built = true;
      }
      const sensor = this._hass.states[HEALTH_SENSOR];
      const devices = c.demo ? dhDemo() : sensor && sensor.attributes.devices || {};
      const ids = Object.keys(devices);
      const bad = ids.filter((id) => devices[id].status !== "ok");
      const colour = !c.demo && !sensor ? KIT_COLOR.off : bad.length ? KIT_COLOR.fair : KIT_COLOR.good;
      const word = !c.demo && !sensor ? "Not set up" : bad.length ? `${bad.length} need${bad.length === 1 ? "s" : ""} attention` : ids.length ? "All responding" : "Nothing watched";
      kitHead(this, c.title || "Device Health", word + (c.demo ? " \xB7 demo" : ""), colour);
      const minute = Math.floor(Date.now() / 6e4);
      const sig = JSON.stringify([devices, minute, c.show_ok]);
      if (sig === this._sig) return;
      this._sig = sig;
      if (!c.demo && !sensor) {
        this._list.innerHTML = `<div class="ck-sub" style="line-height:1.5;">Device health isn't running. Update Church Drive, then choose devices to watch in Settings \u2192 Devices &amp; services \u2192 Church Drive \u2192 Configure \u2192 Device health.</div>`;
        return;
      }
      if (!ids.length) {
        this._list.innerHTML = `<div class="ck-sub" style="line-height:1.5;">No devices watched yet. Choose them in Settings \u2192 Devices &amp; services \u2192 Church Drive \u2192 Configure \u2192 Device health.</div>`;
        return;
      }
      const order = [...bad, ...ids.filter((id) => devices[id].status === "ok")].filter((id) => c.show_ok !== false || devices[id].status !== "ok");
      this._list.innerHTML = order.map((id, i) => {
        const d = devices[id];
        const ok = d.status === "ok";
        const col = ok ? KIT_COLOR.good : KIT_COLOR.fair;
        return `<div class="dh-row" data-id="${id}" style="display:flex; flex-direction:column; gap:6px; padding:9px 0;${i ? " border-top:1px solid var(--divider-color, rgba(127,127,127,0.22));" : ""}">
          <div style="display:flex; align-items:center; gap:10px;">
            ${iconHtml(DH_ICONS[id.split(".")[0]] || "mdi:devices", { size: "22px", style: `color:${col}; flex:none;` })}
            <div style="flex:1; min-width:0;">
              <div class="dh-name" style="font-size:0.95rem; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;"></div>
              <div class="dh-sub ck-sub" style="font-size:0.74rem; line-height:1.35;"></div>
            </div>
            <span class="ck-chip" style="color:${col}; background:color-mix(in srgb, ${col} 20%, transparent);">${ok ? "OK" : "Stale"}</span>
          </div>
          ${ok ? "" : `<div class="dh-fixes ck-sub" style="font-size:0.74rem; padding-left:32px;"></div>
          <div style="display:flex; gap:6px; padding-left:32px;">
            <button class="dh-fix" type="button" style="border:none; border-radius:10px; padding:7px 10px; font:inherit; font-size:0.8rem; font-weight:600; background:#ffa726; color:#2a1700; cursor:pointer;">Fix now</button>
          </div>`}
        </div>`;
      }).join("");
      this._list.querySelectorAll(".dh-row").forEach((el) => {
        const id = el.dataset.id;
        const d = devices[id];
        el.querySelector(".dh-name").textContent = d.name || id;
        el.querySelector(".dh-sub").textContent = d.status === "ok" ? [`Heard ${ago(d.last_heard)}`, d.usual_gap ? `usually ${every(d.usual_gap)}` : ""].filter(Boolean).join(" \xB7 ") : [d.reason, d.last_real ? `last real: ${kitRealText(d.last_real)}` : "", `heard ${ago(d.last_heard)}`].filter(Boolean).join(" \xB7 ");
        const fixes = el.querySelector(".dh-fixes");
        if (fixes) fixes.textContent = (d.fixes || []).length ? `Tried: ${d.fixes.join(" \xB7 ")}` : "Fixing automatically\u2026";
        const call = (action) => !c.demo && this._hass.callService("church_drive", "health_fix", { entity_id: id, action });
        const fix = el.querySelector(".dh-fix");
        if (fix) fix.addEventListener("click", () => call(id.startsWith("fan.") ? "nudge" : "resync"));
      });
      hydrateIcons(this);
    }
    getCardSize() {
      return 4;
    }
    getGridOptions() {
      return { columns: 12, min_columns: 6, rows: "auto" };
    }
    static getConfigElement() {
      return document.createElement(`device-health-card-editor${SUFFIX}`);
    }
    static getStubConfig() {
      return {};
    }
  };
  kitCompactable(DeviceHealthCard, (card) => {
    card._built = false;
    card._sig = null;
  });
  function registerDeviceHealthCard() {
    if (!customElements.get(`device-health-card-editor${SUFFIX}`)) customElements.define(`device-health-card-editor${SUFFIX}`, DeviceHealthCardEditor);
    if (!customElements.get(`device-health-card${SUFFIX}`)) customElements.define(`device-health-card${SUFFIX}`, DeviceHealthCard);
    window.customCards = window.customCards || [];
    window.customCards.push({
      type: `device-health-card${SUFFIX}`,
      name: `Device Health Card${LABEL}`,
      description: "Watched devices: responding or stale, last heard, and fixes",
      preview: true,
      documentationURL: "https://github.com/J45PER/church-drive-cards#readme"
    });
  }

  // src/security-zone-card.js
  var SZ_COLOR = {
    zone: "#5c6bc0",
    motion: "#7986cb",
    door: KIT_COLOR.fair,
    ring: "#f06292",
    tamper: KIT_COLOR.bad,
    light: "#ffd54f"
  };
  var SZ_WORD = { motion: "Motion", door: "Door open", ring: "Doorbell", tamper: "Tamper", light: "Light on" };
  var SZ_BATTERIES = [1, 2, 3, 4];
  var RECENT = 12e4;
  var LOW = 25;
  var isEvent = (id) => String(id || "").startsWith("event.");
  var clock = (ms) => new Date(ms).toLocaleTimeString(void 0, { hour: "2-digit", minute: "2-digit" });
  function when(ms) {
    const d = new Date(ms), today = /* @__PURE__ */ new Date();
    const yesterday = new Date(today.getTime() - 864e5);
    if (d.toDateString() === today.toDateString()) return clock(ms);
    if (d.toDateString() === yesterday.toDateString()) return `yesterday ${clock(ms)}`;
    return `${d.toLocaleDateString(void 0, { weekday: "short" })} ${clock(ms)}`;
  }
  function spansOf(id, series, from, now) {
    const out = [];
    if (isEvent(id)) {
      const seen = /* @__PURE__ */ new Set();
      series.forEach(([, s]) => {
        const t = Date.parse(s);
        if (!isNaN(t) && t >= from && t <= now && !seen.has(t)) {
          seen.add(t);
          out.push([t, t + 3e4]);
        }
      });
      return out.sort((a, b) => a[0] - b[0]);
    }
    let on = null;
    series.forEach(([t, s]) => {
      if (s === "on" && on === null) on = Math.max(t, from);
      else if (s !== "on" && on !== null) {
        if (t >= from) out.push([on, Math.max(t, on + 3e4)]);
        on = null;
      }
    });
    if (on !== null) out.push([on, now]);
    return out;
  }
  function szDemo(config) {
    const now = Date.now(), m = 6e4, kind = config.demo_state || "quiet";
    const ticks = (list) => list.map((ago2) => [now - ago2 * m, now - ago2 * m + 3e4]);
    return {
      tracks: [
        ["light", [[now - 400 * m, now - 385 * m], [now - 70 * m, now - 58 * m]]],
        ["motion", ticks([640, 610, 604, 420, 398, 395, 390, 260, 255, 130, 70, 66, 62, ...kind === "motion" ? [1] : []])],
        ["door", ticks([612, 396, 258, 64, ...kind === "open" ? [] : []]).concat(kind === "open" ? [[now - 4 * m, now]] : [])],
        ["ring", ticks([397])]
      ],
      word: { quiet: "Closed", motion: "Motion just now", open: "Open 4 min", tamper: "Tamper" }[kind],
      chip: { quiet: "Closed", motion: "Motion", open: "Open", tamper: "Tampered" }[kind],
      level: { quiet: "ok", motion: "motion", open: "open", tamper: "tamper" }[kind],
      warn: kind === "tamper" ? `Door sensor tampered with at ${clock(now - 3 * m)}` : "",
      last: kind === "open" ? `Open since ${clock(now - 4 * m)} \xB7 motion ${clock(now - 62 * m)}` : `Closed since ${clock(now - 64 * m)} \xB7 motion ${clock(now - (kind === "motion" ? 0 : 62) * m)} \xB7 rang ${clock(now - 397 * m)}`,
      bats: [["mdi:doorbell-video", "Doorbell", 58], ["mdi:motion-sensor", "Motion sensor", 100], ["mdi:door", "Door contact", 18]],
      light: config.demo_light === false ? null : { name: "Porch light", on: kind === "motion" }
    };
  }
  var SecurityZoneCardEditor = createFormEditor({
    schema: (config) => [
      { name: "name", selector: { text: {} } },
      ...config.demo ? [] : [
        { name: "door_entity", selector: { entity: { domain: "binary_sensor" } } },
        { name: "motion_entities", selector: { entity: { multiple: true, domain: ["binary_sensor", "event"] } } },
        { name: "doorbell_entity", selector: { entity: { domain: "event" } } },
        { name: "tamper_entities", selector: { entity: { multiple: true, domain: "binary_sensor" } } },
        {
          type: "expandable",
          name: "",
          title: "Light tile (optional)",
          flatten: true,
          schema: [
            { name: "light_entity", selector: { entity: { domain: ["light", "switch"] } } },
            { name: "light_name", selector: { text: {} } }
          ]
        },
        {
          type: "expandable",
          name: "",
          title: "Batteries (each on its own row)",
          flatten: true,
          schema: SZ_BATTERIES.flatMap((n) => [
            { name: `battery_${n}`, selector: { entity: { domain: "sensor" } } },
            { name: `battery_${n}_name`, selector: { text: {} } }
          ])
        },
        { name: "alarm_entity", selector: { entity: { domain: "alarm_control_panel" } } }
      ],
      {
        type: "expandable",
        name: "",
        title: "Activity strip",
        flatten: true,
        schema: [
          { name: "hours", selector: { select: { mode: "dropdown", options: [{ value: "6", label: "Last 6 hours" }, { value: "12", label: "Last 12 hours" }, { value: "24", label: "Last 24 hours" }] } } },
          { name: "strip", selector: { select: { mode: "dropdown", options: [{ value: "bars", label: "Bars (busy periods)" }, { value: "ticks", label: "Ticks (each event)" }] } } },
          { name: "show_last", selector: { boolean: {} }, default: true }
        ]
      },
      {
        type: "expandable",
        name: "",
        title: "Demo mode (a pretend zone, for Design Presets)",
        flatten: true,
        schema: [
          { name: "demo", selector: { boolean: {} } },
          {
            name: "demo_state",
            selector: {
              select: {
                mode: "dropdown",
                options: [
                  { value: "quiet", label: "All quiet" },
                  { value: "motion", label: "Motion just now" },
                  { value: "open", label: "Door open" },
                  { value: "tamper", label: "Tampered with" }
                ]
              }
            }
          }
        ]
      }
    ],
    labels: {
      name: "Zone name (e.g. Front Garden)",
      door_entity: "Door or window contact (optional)",
      motion_entities: "Motion sensors and camera motion (optional)",
      doorbell_entity: "Doorbell ring (optional)",
      tamper_entities: "Tamper sensors (optional)",
      light_entity: "Light",
      light_name: "Light tile name (optional)",
      ...Object.fromEntries(SZ_BATTERIES.flatMap((n) => [[`battery_${n}`, `Battery ${n}`], [`battery_${n}_name`, `Battery ${n} name (e.g. Doorbell)`]])),
      alarm_entity: "Alarm (optional: an open door turns red while it\u2019s set)",
      hours: "Time shown",
      strip: "Strip style",
      show_last: "Last events line",
      demo: "Use a pretend zone instead of real sensors",
      demo_state: "Pretend zone shows"
    },
    helpers: {
      motion_entities: "Motion sensors (on/off) and camera motion events both work.",
      battery_1: "Named rows, e.g. Doorbell 58% and Hue sensor 100%. Below 25% turns amber."
    }
  });
  var SecurityZoneCard = class extends HTMLElement {
    setConfig(config) {
      if (!config.demo && !config.door_entity && !(config.motion_entities || []).length && !config.doorbell_entity) {
        throw new Error("Choose a door, motion sensor or doorbell (or set demo: true)");
      }
      this.config = config;
      this._built = false;
      this._hours = Number(config.hours) || 12;
      this._demo = config.demo ? szDemo(config) : null;
      const c = config;
      this._ids = [c.door_entity, ...c.motion_entities || [], c.doorbell_entity, ...c.tamper_entities || [], c.light_entity].filter(Boolean);
      this._hist = this._demo ? null : new KitHistory(this, this._ids, this._hours, kitStateHistory);
      this._live = {};
    }
    set hass(hass) {
      this._hass = hass;
      this._render();
    }
    // History plus the changes seen since it loaded.
    _series(id) {
      const hist = this._hist && this._hist.data && this._hist.data[id] || [];
      const lastHist = hist.length ? hist[hist.length - 1][0] : 0;
      const live = (this._live[id] || []).filter((p) => p[0] > lastHist);
      return hist.concat(live);
    }
    _watch() {
      this._ids.forEach((id) => {
        const st = this._hass.states[id];
        if (!st) return;
        const t = Date.parse(st.last_changed);
        const list = this._live[id] = this._live[id] || [];
        const last = list[list.length - 1];
        if (!last || last[0] !== t || last[1] !== st.state) list.push([t, st.state]);
        if (list.length > 200) list.splice(0, list.length - 200);
      });
    }
    _data() {
      if (this._demo) return this._demo;
      const c = this.config, s = (id) => id ? this._hass.states[id] : null;
      const now = Date.now(), from = now - this._hours * 36e5;
      const tracks = [];
      if (c.light_entity) tracks.push(["light", spansOf(c.light_entity, this._series(c.light_entity), from, now)]);
      (c.motion_entities || []).forEach((id) => tracks.push(["motion", spansOf(id, this._series(id), from, now)]));
      if (c.door_entity) tracks.push(["door", spansOf(c.door_entity, this._series(c.door_entity), from, now)]);
      (c.tamper_entities || []).forEach((id) => tracks.push(["tamper", spansOf(id, this._series(id), from, now)]));
      if (c.doorbell_entity) tracks.push(["ring", spansOf(c.doorbell_entity, this._series(c.doorbell_entity), from, now)]);
      const door = s(c.door_entity);
      const open = !!door && door.state === "on";
      const tampered = (c.tamper_entities || []).map(s).filter((st) => st && st.state === "on");
      const alarm = s(c.alarm_entity);
      const armed = !!alarm && /^armed/.test(alarm.state);
      const lastOf = (id) => {
        const st = s(id);
        if (!st) return null;
        if (isEvent(id)) {
          const t = Date.parse(st.state);
          return isNaN(t) ? null : t;
        }
        if (st.state === "on") return now;
        const ons = spansOf(id, this._series(id), 0, now);
        return ons.length ? ons[ons.length - 1][0] : null;
      };
      const motionAt = Math.max(0, ...(c.motion_entities || []).map(lastOf).filter(Boolean));
      const ringAt = c.doorbell_entity ? lastOf(c.doorbell_entity) : null;
      const moving = motionAt && now - motionAt < RECENT;
      const ringing = ringAt && now - ringAt < RECENT;
      const ids = [c.door_entity, ...c.motion_entities || [], c.doorbell_entity].filter(Boolean);
      const unavailable = ids.length && ids.every((id) => !s(id) || s(id).state === "unavailable");
      let level = "ok", word = door ? "Closed" : "Quiet", chip = door ? "Closed" : "Quiet", warn = "";
      if (moving) [level, word, chip] = ["motion", "Motion just now", "Motion"];
      if (ringing) [level, word, chip] = ["motion", "Doorbell just now", "Doorbell"];
      if (open) {
        const mins = Math.max(0, Math.round((now - Date.parse(door.last_changed)) / 6e4));
        [level, word, chip] = [armed ? "tamper" : "open", armed ? "Open while armed" : mins < 1 ? "Just opened" : `Open ${mins} min`, "Open"];
      }
      if (tampered.length) {
        level = "tamper";
        word = "Tamper";
        chip = "Tampered";
        warn = `${tampered.map((st) => st.attributes.friendly_name || st.entity_id).join(", ")} tampered with at ${clock(Date.parse(tampered[0].last_changed))}`;
      }
      if (unavailable) [level, word, chip] = ["off", "Unavailable", "Unavailable"];
      const last = [];
      if (door && door.state !== "unavailable") last.push(`${open ? "Open" : "Closed"} since ${when(Date.parse(door.last_changed))}`);
      if (motionAt) last.push(`${last.length ? "motion" : "Motion"} ${now - motionAt < RECENT ? "just now" : when(motionAt)}`);
      if (ringAt) last.push(`${last.length ? "rang" : "Rang"} ${when(ringAt)}`);
      const bats = SZ_BATTERIES.filter((n) => c[`battery_${n}`]).map((n) => {
        const st = s(c[`battery_${n}`]);
        const name = c[`battery_${n}_name`] || st && String(st.attributes.friendly_name || "").replace(/\s*battery$/i, "") || c[`battery_${n}`];
        return [st && st.attributes.icon || "mdi:battery", name, kitNum(st)];
      });
      const light = c.light_entity && s(c.light_entity) ? { name: c.light_name || s(c.light_entity).attributes.friendly_name || c.light_entity, on: s(c.light_entity).state === "on" } : null;
      return { tracks, word, chip, level, warn, last: last.join(" \xB7 "), bats, light };
    }
    _render() {
      if (!this._hass) return;
      const c = this.config;
      if (this._compact) return kitCompact(this, this._compactSpec());
      if (!this._built) {
        this.innerHTML = kitShell(
          `
        <div class="sz-warn" style="display:none; align-items:center; gap:8px; padding:8px 10px; border-radius:10px; background:${KIT_COLOR.bad}; color:#fff; font-weight:600; font-size:0.85rem;"></div>
        <div style="display:flex; flex-direction:column; gap:4px;">
          <div style="display:flex; align-items:center; gap:8px;">
            <div class="sz-strip"></div>
            <span class="ck-chip sz-chip"></span>
          </div>
          <div class="sz-axis ck-sub" style="display:flex; justify-content:space-between; font-size:0.66rem; font-variant-numeric:tabular-nums; margin-right:var(--sz-chip-w, 0px);"></div>
        </div>
        <div class="sz-last ck-sub" style="font-size:0.76rem;"></div>
        <div class="sz-bats" style="display:flex; flex-direction:column; gap:3px;"></div>
        <div class="ck-row sz-light" style="margin-top:auto;"></div>`,
          `.sz-strip { position:relative; flex:1; min-width:0; height:20px; border-radius:6px; background:rgba(127,127,127,.12); overflow:hidden; touch-action:pan-y; user-select:none; -webkit-user-select:none; -webkit-touch-callout:none; }
        .sz-strip i { position:absolute; top:3px; bottom:3px; min-width:2px; border-radius:2px; }
        .sz-strip b { position:absolute; bottom:2px; border-radius:2px 2px 0 0; }
        .sz-strip .sz-grid { position:absolute; top:0; bottom:0; width:1px; background:rgba(127,127,127,.18); }
        .sz-strip .sz-cursor { position:absolute; top:0; bottom:0; width:2px; margin-left:-1px; background:var(--primary-text-color); opacity:.8; display:none; pointer-events:none; }
        .sz-brow { display:flex; align-items:center; gap:8px; font-size:0.8rem; color:var(--secondary-text-color); font-variant-numeric:tabular-nums; }
        .sz-brow .sz-nm { flex:1; min-width:0; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
        .sz-brow .sz-bar { width:56px; height:5px; border-radius:99px; background:rgba(127,127,127,.22); overflow:hidden; flex:none; }
        .sz-brow .sz-bar i { display:block; height:100%; border-radius:inherit; }
        .sz-brow b { width:38px; text-align:right; font-weight:600; color:var(--primary-text-color); }
        .sz-brow.sz-low, .sz-brow.sz-low b { color:${KIT_COLOR.fair}; }`
        );
        this._stripEl = this.querySelector(".sz-strip");
        this._scrub(this._stripEl);
        this._built = true;
      }
      if (!this._demo) {
        this._watch();
        if (this._hist.due()) this._hist.load(this._hass);
      }
      const d = this._data();
      const colour = { tamper: KIT_COLOR.bad, open: KIT_COLOR.fair, off: KIT_COLOR.off }[d.level] || SZ_COLOR.zone;
      const tint = { tamper: 18, open: 10 }[d.level] || 0;
      this._word = d.word + (this._demo ? " \xB7 demo" : "");
      if (!this._scrubbing) kitHead(this, c.name || "Zone", this._word, colour, tint);
      else kitHead(this, c.name || "Zone", this.querySelector(".ck-word").textContent, colour, tint);
      const warn = this.querySelector(".sz-warn");
      warn.style.display = d.warn ? "flex" : "none";
      if (d.warn) warn.innerHTML = `${iconHtml("mdi:alert", { size: "20px" })}<span>${kitEsc(d.warn)}</span>`;
      const chip = this.querySelector(".sz-chip");
      const chipCol = d.level === "motion" ? d.chip === "Doorbell" ? SZ_COLOR.ring : SZ_COLOR.zone : d.level === "ok" && d.chip === "Closed" ? KIT_COLOR.good : d.level === "ok" || d.level === "off" ? null : colour;
      chip.textContent = d.chip;
      chip.style.color = d.level === "motion" ? "#fff" : chipCol || "var(--secondary-text-color)";
      chip.style.background = d.level === "motion" ? chipCol : chipCol ? `color-mix(in srgb, ${chipCol} 22%, transparent)` : "rgba(127,127,127,.2)";
      this._tracks = d.tracks;
      this._drawStrip(d.tracks);
      const lastEl = this.querySelector(".sz-last");
      lastEl.style.display = c.show_last !== false && d.last ? "block" : "none";
      lastEl.textContent = d.last;
      const batsEl = this.querySelector(".sz-bats");
      const bsig = JSON.stringify(d.bats);
      if (batsEl._sig !== bsig) {
        batsEl._sig = bsig;
        batsEl.style.display = d.bats.length ? "flex" : "none";
        batsEl.innerHTML = d.bats.map(([icon, name, v]) => {
          const low = v != null && v < LOW;
          return `<div class="sz-brow${low ? " sz-low" : ""}">${iconHtml(v == null ? "mdi:battery-unknown" : low ? "mdi:battery-alert" : icon, { size: "17px", style: "flex:none;" })}<span class="sz-nm">${kitEsc(name)}</span><span class="sz-bar"><i style="width:${v == null ? 0 : Math.max(2, Math.min(100, v))}%; background:${low ? KIT_COLOR.fair : KIT_COLOR.good};"></i></span><b>${v == null ? "\u2013" : `${Math.round(v)}%`}</b></div>`;
        }).join("");
      }
      const tiles = d.light ? [{ key: "light", name: `${d.light.name} ${d.light.on ? "on" : "off"}`, icon: d.light.on ? "mdi:lightbulb-on" : "mdi:lightbulb-outline", color: KIT_COLOR.warm, on: d.light.on }] : [];
      kitTiles(this.querySelector(".sz-light"), tiles, () => this._toggleLight());
      hydrateIcons(this);
    }
    // One row: zone name and state, a low battery if any, and the light.
    _compactSpec() {
      if (!this._demo) this._watch();
      const d = this._data();
      const colour = { tamper: KIT_COLOR.bad, open: KIT_COLOR.fair, off: KIT_COLOR.off }[d.level] || SZ_COLOR.zone;
      const low = d.bats.filter(([, , v]) => v != null && v < LOW).map(([, name, v]) => `${name} ${Math.round(v)}%`);
      return {
        name: this.config.name || "Zone",
        color: colour,
        status: [d.word, ...low].join(" \xB7 "),
        buttons: d.light ? [{ key: "light", icon: d.light.on ? "mdi:lightbulb-on" : "mdi:lightbulb-outline", title: `${d.light.name} ${d.light.on ? "on" : "off"}`, on: d.light.on, color: KIT_COLOR.warm }] : [],
        onButton: () => this._toggleLight()
      };
    }
    _drawStrip(tracks) {
      const now = Date.now(), span = this._hours * 36e5, from = now - span;
      const style = this.config.strip === "ticks" ? "ticks" : "bars";
      const x = (t) => (t - from) / span * 100;
      const minute = Math.floor(now / 6e4);
      const sig = JSON.stringify([style, this._hours, minute, tracks.map(([k, s]) => [k, s.length, s.length ? s[s.length - 1] : 0])]);
      if (this._stripSig === sig) return;
      this._stripSig = sig;
      let html = [1, 2, 3].map((g) => `<span class="sz-grid" style="left:${g * 25}%"></span>`).join("");
      tracks.filter(([k]) => k === "light").forEach(([, spans]) => spans.forEach(([a, b]) => {
        html += `<i style="left:${Math.max(0, x(a))}%; width:${Math.max(0.4, x(b) - Math.max(0, x(a)))}%; top:0; bottom:0; border-radius:0; background:${SZ_COLOR.light}; opacity:.35;"></i>`;
      }));
      if (style === "ticks") {
        tracks.filter(([k]) => k !== "light").forEach(([kind, spans]) => spans.forEach(([a, b]) => {
          const l = Math.max(0, x(a));
          html += `<i style="left:${l}%; width:${Math.max(0, x(b) - l)}%; background:${SZ_COLOR[kind]};"></i>`;
        }));
      } else {
        const slots = 24, slot = span / slots;
        const counts = Array.from({ length: slots }, () => ({}));
        tracks.filter(([k]) => k !== "light").forEach(([kind, spans]) => spans.forEach(([a]) => {
          if (a < from) return;
          const k = Math.min(slots - 1, Math.floor((a - from) / slot));
          counts[k][kind] = (counts[k][kind] || 0) + 1;
        }));
        const totals = counts.map((c) => Object.values(c).reduce((p, q) => p + q, 0));
        const max = Math.max(1, ...totals);
        counts.forEach((c, k) => {
          if (!totals[k]) return;
          const kind = c.tamper ? "tamper" : c.ring ? "ring" : c.door ? "door" : "motion";
          html += `<b style="left:${k / slots * 100 + 0.4}%; width:${100 / slots - 0.8}%; height:${Math.max(20, totals[k] / max * 85)}%; background:${SZ_COLOR[kind]};"></b>`;
        });
      }
      this._stripEl.innerHTML = `${html}<span class="sz-cursor"></span>`;
      const chip = this.querySelector(".sz-chip");
      this.querySelector(".sz-axis").style.setProperty("--sz-chip-w", `${(chip.offsetWidth || 0) + 8}px`);
      this.querySelector(".sz-axis").innerHTML = [0, 0.25, 0.5, 0.75].map((f) => `<span>${clock(from + f * span)}</span>`).join("") + "<span>now</span>";
    }
    // What happened at a point on the strip, shown in the title line.
    _at(frac) {
      const span = this._hours * 36e5, now = Date.now(), t = now - span + frac * span, tol = span / 90;
      const found = /* @__PURE__ */ new Set();
      (this._tracks || []).forEach(([kind, spans]) => spans.forEach(([a, b]) => {
        if (t >= a - tol && t <= b + tol) found.add(SZ_WORD[kind]);
      }));
      return `${clock(t)} \xB7 ${found.size ? [...found].join(", ") : "nothing"}`;
    }
    // Hover with a mouse, or press and hold (0.3s) then drag with a finger.
    _scrub(el) {
      const word = () => this.querySelector(".ck-word");
      const show = (clientX) => {
        const r = el.getBoundingClientRect();
        const f = Math.max(0, Math.min(1, (clientX - r.left) / (r.width || 1)));
        const cur = el.querySelector(".sz-cursor");
        if (cur) {
          cur.style.left = `${f * 100}%`;
          cur.style.display = "block";
        }
        this._scrubbing = true;
        word().textContent = this._at(f);
      };
      const hide = () => {
        this._scrubbing = false;
        const cur = el.querySelector(".sz-cursor");
        if (cur) cur.style.display = "none";
        word().textContent = this._word || "";
      };
      let active = false, timer = null, sx = 0, sy = 0;
      el.addEventListener("pointermove", (ev) => {
        if (ev.pointerType === "mouse" || active) return show(ev.clientX);
        if (timer && (Math.abs(ev.clientX - sx) > 10 || Math.abs(ev.clientY - sy) > 10)) {
          clearTimeout(timer);
          timer = null;
        }
      });
      el.addEventListener("pointerleave", (ev) => ev.pointerType === "mouse" && hide());
      el.addEventListener("pointerdown", (ev) => {
        if (ev.pointerType === "mouse") return;
        sx = ev.clientX;
        sy = ev.clientY;
        clearTimeout(timer);
        timer = setTimeout(() => {
          timer = null;
          active = true;
          show(sx);
        }, 300);
      });
      const end = () => {
        clearTimeout(timer);
        timer = null;
        if (active) {
          active = false;
          hide();
        }
      };
      ["pointerup", "pointercancel"].forEach((e) => el.addEventListener(e, end));
      el.addEventListener("touchmove", (ev) => {
        if (active && ev.cancelable) ev.preventDefault();
        if (active && ev.touches[0]) show(ev.touches[0].clientX);
      }, { passive: false });
      el.addEventListener("touchend", end);
      el.addEventListener("contextmenu", (ev) => active && ev.preventDefault());
    }
    _toggleLight() {
      if (this._demo) {
        if (this._demo.light) this._demo.light.on = !this._demo.light.on;
        this._render();
        return;
      }
      const id = this.config.light_entity;
      if (id) this._hass.callService("homeassistant", "toggle", { entity_id: id });
    }
    getCardSize() {
      return 3;
    }
    getGridOptions() {
      return { columns: 12, min_columns: 6, rows: "auto" };
    }
    static getConfigElement() {
      return document.createElement(`security-zone-card-editor${SUFFIX}`);
    }
    static getStubConfig() {
      return { demo: true, name: "Front Garden", hours: "12", strip: "bars" };
    }
  };
  kitCompactable(SecurityZoneCard, (card) => {
    card._built = false;
    card._stripSig = null;
  });
  function registerSecurityZoneCard() {
    if (!customElements.get(`security-zone-card-editor${SUFFIX}`)) customElements.define(`security-zone-card-editor${SUFFIX}`, SecurityZoneCardEditor);
    if (!customElements.get(`security-zone-card${SUFFIX}`)) customElements.define(`security-zone-card${SUFFIX}`, SecurityZoneCard);
    window.customCards = window.customCards || [];
    window.customCards.push({
      type: `security-zone-card${SUFFIX}`,
      name: `Security Zone Card${LABEL}`,
      description: "One zone (garden, entrance, driveway\u2026): motion, doors, doorbell and light over the last hours, with named batteries",
      preview: true,
      documentationURL: "https://github.com/J45PER/church-drive-cards#readme"
    });
  }

  // src/nav-bar-card.js
  var NB_HIDE_CSS = `.toolbar ha-tab-group, .toolbar sl-tab-group, .toolbar paper-tabs, .toolbar ha-tabs, .toolbar .tabs, ha-tab-group.tabs { display: none !important; }`;
  var nbHolders = 0;
  var nbHideTimer = null;
  function nbFind(root, name, depth = 0) {
    if (!root || depth > 8) return null;
    const hit = root.querySelector(name);
    if (hit) return hit;
    for (const el of root.querySelectorAll("*")) {
      if (el.shadowRoot) {
        const found = nbFind(el.shadowRoot, name, depth + 1);
        if (found) return found;
      }
    }
    return null;
  }
  function nbHuiRoot() {
    const ha = document.querySelector("home-assistant");
    return ha && ha.shadowRoot ? nbFind(ha.shadowRoot, "hui-root") : null;
  }
  function nbHideTabs(hide) {
    clearTimeout(nbHideTimer);
    const apply = () => {
      const root = nbHuiRoot();
      const sr = root && root.shadowRoot;
      if (!sr) return false;
      const style = sr.querySelector("style.cd-nav-hide-tabs");
      if (nbHolders > 0 && !style) {
        const el = document.createElement("style");
        el.className = "cd-nav-hide-tabs";
        el.textContent = NB_HIDE_CSS;
        sr.appendChild(el);
      } else if (nbHolders <= 0 && style) style.remove();
      return true;
    };
    if (hide) {
      nbHolders += 1;
      let tries = 0;
      const attempt = () => {
        if (!apply() && tries++ < 10) nbHideTimer = setTimeout(attempt, 200);
      };
      attempt();
    } else {
      nbHolders = Math.max(0, nbHolders - 1);
      nbHideTimer = setTimeout(apply, 400);
    }
  }
  var NB_DEMO = [
    { name: "Home", icon: "mdi:home", path: "#home", color: "blue" },
    { name: "Lights", icon: "mdi:lightbulb", path: "#lights", color: "amber" },
    { name: "Security", icon: "mdi:shield-lock", path: "#security", color: "blue" },
    { name: "Climate", icon: "mdi:thermostat", path: "#climate", color: "amber", alert_template: "on" },
    { name: "Cleaning", icon: "mdi:robot-vacuum", path: "#cleaning", color: "blue" }
  ];
  var nbAlert = (text) => {
    const t = String(text || "").trim().toLowerCase();
    return !!t && !["0", "false", "off", "no", "none", "unknown", "unavailable"].includes(t);
  };
  var NavBarCardEditor = createFormEditor({
    schema: () => [
      {
        name: "pages",
        selector: {
          object: {
            multiple: true,
            label_field: "name",
            fields: {
              name: { label: "Name", required: true, selector: { text: {} } },
              icon: { label: "Icon", required: true, selector: { icon: {} } },
              path: { label: "Page", required: true, selector: { navigation: {} } },
              color: { label: "Colour", selector: { ui_color: {} } },
              color_template: { label: "Colour from a template (optional)", selector: { template: {} } },
              icon_template: { label: "Icon from a template (optional, e.g. mdi:shield-off when disarmed)", selector: { template: {} } },
              alert_template: { label: "Needs attention when (optional template)", selector: { template: {} } }
            }
          }
        }
      },
      { name: "hide_tabs", selector: { boolean: {} }, default: true },
      { name: "back_to_top", selector: { boolean: {} }, default: true },
      { name: "demo", selector: { boolean: {} } }
    ],
    labels: {
      pages: "Pages",
      hide_tabs: "Hide the dashboard's own tabs at the top",
      back_to_top: "Back-to-top button beside the bar (an arrow once you scroll down, a dash at the top)",
      demo: "Show pretend pages (for Design Presets; shown in place, not pinned)"
    },
    helpers: {
      pages: `Each page's colour can come from a template (${STC_COLOR_TEMPLATE_HELPER.replace(/^Gives a colour name or code, /, "")}). A page gets a dot while its "needs attention" template gives something other than 0, off or empty, e.g. {{ is_state('binary_sensor.back_door', 'on') }}.`
    }
  });
  var NavBarCard = class extends HTMLElement {
    setConfig(config) {
      if (!config.demo && !(config.pages || []).length) throw new Error("Add at least one page (or set demo: true)");
      this.config = config;
      this._pages = config.demo ? NB_DEMO : config.pages;
      this._live = {};
      this._built = false;
      this._resubscribe();
      this._render();
    }
    set hass(hass) {
      const first = !this._hass;
      this._hass = hass;
      if (first) this._resubscribe();
      this._render();
    }
    // Pinned bars live on document.body, outside the dashboard layout, so no
    // container styling can clip or move them. Editors and demos stay inline.
    get _inline() {
      return !!(this.config && this.config.demo) || !!this.editMode || !!this.preview;
    }
    connectedCallback() {
      this._onLocation = () => this._render();
      window.addEventListener("location-changed", this._onLocation);
      window.addEventListener("popstate", this._onLocation);
      this._sc = null;
      this._onScroll = () => {
        cancelAnimationFrame(this._scrollFrame);
        this._scrollFrame = requestAnimationFrame(() => this._syncTop());
      };
      window.addEventListener("scroll", this._onScroll, { capture: true, passive: true });
      this._onResize = () => this._fit();
      window.addEventListener("resize", this._onResize);
      if (this._hass && !this._subs) this._resubscribe();
      this._render();
      this._holdTabs();
    }
    // Hide the header tabs while a pinned bar is on screen.
    _holdTabs() {
      const want = !!this.config && !this._inline && this.config.hide_tabs !== false && this.isConnected;
      if (want === !!this._holding) return;
      this._holding = want;
      nbHideTabs(want);
    }
    disconnectedCallback() {
      if (this._holding) {
        this._holding = false;
        nbHideTabs(false);
      }
      if (this._host) this._host.remove();
      this._host = null;
      this._built = false;
      this._sig = null;
      window.removeEventListener("location-changed", this._onLocation);
      window.removeEventListener("popstate", this._onLocation);
      window.removeEventListener("scroll", this._onScroll, { capture: true });
      window.removeEventListener("resize", this._onResize);
      this._unsubscribe();
    }
    _unsubscribe() {
      (this._subs || []).forEach((p) => p && p.then((unsub) => unsub && unsub()).catch(() => {
      }));
      this._subs = null;
    }
    // Live colours and attention dots from each page's templates.
    _resubscribe() {
      this._unsubscribe();
      if (!this._hass || !this.config || this.config.demo || !this.isConnected) return;
      this._subs = [];
      this._pages.forEach((p, i) => {
        const live = this._live[i] = this._live[i] || {};
        this._subs.push(stcRender(this._hass, p.color_template, (c) => {
          live.color = c;
          this._render();
        }));
        this._subs.push(stcRender(this._hass, p.icon_template, (c) => {
          live.icon = String(c || "").trim();
          this._render();
        }));
        this._subs.push(stcRender(this._hass, p.alert_template, (a) => {
          live.alert = nbAlert(a);
          this._render();
        }));
      });
    }
    _active() {
      if (this.config.demo) return this._demoActive || 0;
      const here = location.pathname.replace(/\/$/, "");
      let best = -1, len = -1;
      this._pages.forEach((p, i) => {
        const path = String(p.path || "").split(/[?#]/)[0].replace(/\/$/, "");
        if (path && (here === path || here.startsWith(`${path}/`)) && path.length > len) [best, len] = [i, path.length];
      });
      return best;
    }
    _render() {
      if (!this.config || !this.isConnected) return;
      this._holdTabs();
      const demo = !!this.config.demo;
      const inline = this._inline;
      if (this._built && this._builtInline !== inline) {
        if (this._host) this._host.remove();
        this._host = null;
        this._built = false;
        this._sig = null;
      }
      if (!this._built) {
        this._builtInline = inline;
        const html = `
        <style>
          .nb { pointer-events:auto; flex:1 1 auto; min-width:0; max-width:440px; height:58px; border-radius:29px; display:flex; align-items:center; justify-content:space-between; gap:4px; padding:0 7px; box-sizing:border-box; }
          .nb-it { position:relative; flex:none; height:44px; min-width:44px; border:none; border-radius:22px; padding:0; background:transparent; cursor:pointer; font:inherit;
            display:flex; align-items:center; justify-content:center; gap:6px; color:var(--secondary-text-color); transition:background-color .25s, padding .25s; -webkit-tap-highlight-color:transparent; }
          .nb-it.nb-on { padding:0 14px 0 12px; color:#fff; font-weight:600; font-size:0.85rem; }
          .nb-it span.nb-name { white-space:nowrap; overflow:hidden; text-overflow:ellipsis; max-width:110px; }
          /* Too many pages for the width (e.g. six on a phone): tighter spacing,
             and the current page is its coloured pill without the label. */
          .nb.nb-tight { gap:2px; padding:0 5px; }
          .nb.nb-tight .nb-it { min-width:40px; }
          .nb.nb-tight .nb-it.nb-on { padding:0 12px; }
          .nb.nb-tight .nb-it span.nb-name { display:none; }
          .nb-it:focus-visible { outline:2px solid var(--primary-color); outline-offset:2px; }
          .nb-dot { position:absolute; left:28px; top:6px; width:8px; height:8px; border-radius:50%; background:#ff9800; box-shadow:0 0 0 2px var(--card-background-color, #1f2128); }
          .nb-it.nb-on .nb-dot { left:auto; right:6px; }
          .nb-top { pointer-events:auto; position:relative; flex:none; width:58px; height:58px; padding:0; border:none; border-radius:50%; cursor:pointer; -webkit-tap-highlight-color:transparent; }
          ${kitAcrylicCss(".nb")}
          ${kitAcrylicCss(".nb-top")}
          .nb-top:focus-visible { outline:2px solid var(--primary-color); outline-offset:2px; }
          .nb-top i { position:absolute; left:50%; top:50%; width:12px; height:2.5px; margin:-1.25px 0 0 -6px; border-radius:2px; background:#fff;
            transition:transform .38s cubic-bezier(.2,.8,.2,1); }
          .nb-top i.nb-a { transform:translateX(-5px); }
          .nb-top i.nb-b { transform:translateX(5px); }
          .nb-top i.nb-c { width:2.5px; height:14px; margin:-7px 0 0 -1.25px; transform:scaleY(0); }
          .nb-top.nb-up i.nb-a { transform:translate(-4.2px, -2.8px) rotate(-45deg); }
          .nb-top.nb-up i.nb-b { transform:translate(4.2px, -2.8px) rotate(45deg); }
          .nb-top.nb-up i.nb-c { transform:scaleY(1); }
        </style>
        <div class="nb-wrap" style="${inline ? "position:relative; display:flex; justify-content:center; padding:4px 0;" : "position:fixed; z-index:5; left:0; right:0; bottom:calc(14px + env(safe-area-inset-bottom, 0px)); display:flex; justify-content:center; pointer-events:none; padding:0 14px;"} gap:10px;"><nav class="nb" aria-label="Pages"></nav>${this.config.back_to_top === false ? "" : '<button class="nb-top" type="button" aria-label="Back to top" title="Back to top"><i class="nb-a"></i><i class="nb-b"></i><i class="nb-c"></i></button>'}</div>`;
        if (inline) {
          this.innerHTML = html;
          this._nav = this.querySelector(".nb");
        } else {
          this.innerHTML = '<div style="height:70px;"></div>';
          this._host = document.createElement("div");
          this._host.className = "church-drive-nav-bar";
          this._host.innerHTML = html;
          document.body.appendChild(this._host);
          this._nav = this._host.querySelector(".nb");
        }
        this._top = (this._host || this).querySelector(".nb-top");
        if (this._top) this._top.addEventListener("click", () => this._toTop());
        this._built = true;
        this._up = void 0;
        this._syncTop();
      }
      const active = this._active();
      const items = this._pages.map((p, i) => {
        const live = this._live[i] || {};
        const colour = stcColor(live.color || p.color || "primary");
        const alert = demo ? !!p.alert_template : !!live.alert;
        const icon = /^[a-z]+:[\w-]+$/.test(live.icon || "") ? live.icon : p.icon || "mdi:circle";
        return { i, p, icon, colour, alert, on: i === active };
      });
      const sig = JSON.stringify(items.map((t) => [t.p.name, t.icon, t.colour, t.alert, t.on]));
      if (sig === this._sig) return;
      this._sig = sig;
      this._nav.innerHTML = items.map(({ i, p, icon, colour, alert, on }) => `<button class="nb-it${on ? " nb-on" : ""}" type="button" data-i="${i}" title="${kitEsc(p.name)}" aria-label="${kitEsc(p.name)}${alert ? ", needs attention" : ""}"${on ? ' aria-current="page"' : ""} style="${on ? `background:${colour};` : ""}">
          ${iconHtml(icon, { size: "22px", style: `flex:none; color:${on ? "#fff" : colour};` })}
          ${on ? `<span class="nb-name">${kitEsc(p.name)}</span>` : ""}
          ${alert ? '<i class="nb-dot"></i>' : ""}
        </button>`).join("");
      this._nav.querySelectorAll(".nb-it").forEach(
        (b) => b.addEventListener("click", () => {
          const i = Number(b.dataset.i);
          if (demo) {
            this._demoActive = i;
            this._render();
            return;
          }
          if (i !== this._active()) kitNavigate(this._pages[i].path);
          else this._toTop();
        })
      );
      hydrateIcons(this._host || this);
      this._fit();
    }
    // Tight mode when the pages don't fit side by side at full size.
    _fit() {
      const nav = this._nav;
      if (!nav) return;
      nav.classList.remove("nb-tight");
      if (nav.scrollWidth > nav.clientWidth + 1) nav.classList.add("nb-tight");
    }
    // ---- Back to top: an arrow once the page is scrolled, a dash at the top.
    _scroller() {
      if (!this._sc || !this._sc.isConnected) this._sc = kitScrollParent(this);
      return this._sc;
    }
    _syncTop() {
      if (!this._top) return;
      const up = !this.config.demo && kitScrollTop(this._scroller()) > 40;
      if (up !== this._up) {
        this._up = up;
        this._top.classList.toggle("nb-up", up);
        this._top.setAttribute("aria-disabled", String(!up));
      }
    }
    _toTop() {
      window.dispatchEvent(new CustomEvent("cd-to-top"));
      if (this.config.demo) return;
      const sc = this._scroller();
      kitGlide(sc, () => -kitScrollTop(sc));
    }
    getCardSize() {
      return this.config && this.config.demo ? 1 : 0;
    }
    getGridOptions() {
      return { columns: "full", rows: "auto" };
    }
    static getConfigElement() {
      return document.createElement(`nav-bar-card-editor${SUFFIX}`);
    }
    static getStubConfig() {
      return { demo: true };
    }
  };
  function registerNavBarCard() {
    if (!customElements.get(`nav-bar-card-editor${SUFFIX}`)) customElements.define(`nav-bar-card-editor${SUFFIX}`, NavBarCardEditor);
    if (!customElements.get(`nav-bar-card${SUFFIX}`)) customElements.define(`nav-bar-card${SUFFIX}`, NavBarCard);
    window.customCards = window.customCards || [];
    window.customCards.push({
      type: `nav-bar-card${SUFFIX}`,
      name: `Nav Bar Card${LABEL}`,
      description: "A floating bar at the bottom of the screen: every page one tap away, in its live colour, with a dot when a page needs you",
      preview: true,
      documentationURL: "https://github.com/J45PER/church-drive-cards#readme"
    });
  }

  // src/house-tasks-card.js
  var HOUSE_TASKS_LIST = "todo.priorities_automatic";
  var HOUSE_TASKS_COLOR = "#ab47bc";
  function houseTask(item) {
    const parts = String(item && item.description || "").split(" \xB7 ");
    const last = parts[parts.length - 1] || "";
    const who = /^for /.test(last) ? last.slice(4).trim() : "Everyone";
    const rest = parts.slice(1, /^for /.test(last) ? -1 : void 0);
    const kind = rest[0] || "";
    const icon = /batter/i.test(kind) ? "mdi:battery-alert-variant-outline" : /filter/i.test(kind) ? "mdi:air-filter" : /vacuum/i.test(kind) ? "mdi:robot-vacuum" : /respond|device/i.test(kind) ? "mdi:heart-pulse" : /safety|smoke|alarm/i.test(kind) ? "mdi:smoke-detector-alert" : /update/i.test(kind) ? "mdi:update" : "mdi:home-alert-outline";
    return { kind, detail: rest.slice(1).join(" \xB7 "), who, icon };
  }
  function houseTaskFor(task, first) {
    const names = String(task.who || "").toLowerCase().split(",").map((x) => x.trim());
    return names.includes("everyone") || !!first && names.includes(String(first).toLowerCase());
  }
  function htDemo() {
    return [
      { uid: "d1", summary: "Replace battery: Ring Front Doorbell", status: "needs_action", description: "Automatic \xB7 Low batteries \xB7 17% \xB7 for Everyone" },
      { uid: "d2", summary: "Clean the air purifier pre-filter", status: "needs_action", description: "Automatic \xB7 Filters due \xB7 8% left \xB7 for Jamie" }
    ];
  }
  var HouseTasksCardEditor = createFormEditor({
    schema: () => [
      { name: "title", selector: { text: {} } },
      { name: "entity", selector: { entity: { domain: "todo" } } },
      { name: "show", selector: { select: { mode: "dropdown", options: [{ value: "mine", label: "The signed-in person\u2019s and everyone\u2019s" }, { value: "all", label: "Everyone\u2019s, with names" }] } } },
      { name: "color", selector: { text: {} } },
      { name: "demo", selector: { boolean: {} } }
    ],
    labels: {
      title: "Title (optional)",
      color: "Colour",
      entity: "Automatic to-do list",
      show: "Show",
      demo: "Show pretend tasks (for Design Presets)"
    },
    helpers: {
      color: `Default ${HOUSE_TASKS_COLOR} (purple). Any CSS colour.`,
      entity: `Defaults to ${HOUSE_TASKS_LIST}. Who gets each kind of task is set in Manager \u2192 Automatic to-dos.`
    }
  });
  var HouseTasksCard = class extends HTMLElement {
    setConfig(config) {
      this.config = config || {};
      this._built = false;
      this._sig = null;
      if (this._unsub) this._unwatch();
      if (this._hass) this._watch();
    }
    set hass(hass) {
      this._hass = hass;
      this._watch();
      this._render();
    }
    connectedCallback() {
      if (this._hass) this._watch();
    }
    disconnectedCallback() {
      this._unwatch();
    }
    _entity() {
      return this.config.entity || HOUSE_TASKS_LIST;
    }
    _watch() {
      if (this.config.demo || this._unsub || !this.isConnected || !this._hass || !this._hass.states[this._entity()]) return;
      const id = this._entity();
      this._unsub = this._hass.connection.subscribeMessage(
        (msg) => {
          this._items = msg && msg.items || [];
          this._render();
        },
        { type: "todo/item/subscribe", entity_id: id }
      ).catch(() => null);
    }
    _unwatch() {
      if (this._unsub) this._unsub.then((u) => u && u()).catch(() => {
      });
      this._unsub = null;
      this._items = null;
    }
    _render() {
      if (!this._hass) return;
      const c = this.config;
      if (!this._built) {
        this.innerHTML = kitShell(`<div class="ht-list" style="display:flex; flex-direction:column;"></div>`);
        this._list = this.querySelector(".ht-list");
        this.querySelector(".ck-headrow").style.display = c.title ? "" : "none";
        this._built = true;
      }
      const first = this._hass.user && this._hass.user.name ? String(this._hass.user.name).split(" ")[0] : "";
      const all = c.show === "all";
      const colour = c.color || HOUSE_TASKS_COLOR;
      const missing = !c.demo && !this._hass.states[this._entity()];
      const tasks = (c.demo ? htDemo() : this._items || []).filter((t) => t.status === "needs_action").map((t) => ({ t, h: houseTask(t) })).filter(({ h }) => all || houseTaskFor(h, first));
      if (c.title) kitHead(this, c.title, missing ? "Not set up" : tasks.length ? `${tasks.length} to sort` : "All sorted", tasks.length ? colour : KIT_COLOR.good);
      const sig = JSON.stringify([tasks, missing, all, first, colour]);
      if (sig === this._sig) return;
      this._sig = sig;
      if (missing) {
        this._list.innerHTML = `<div class="ck-sub" style="line-height:1.5;">There's no ${this._entity()} list yet. Add a Local To-do list named "Priorities Automatic".</div>`;
        return;
      }
      if (!tasks.length) {
        this._list.innerHTML = `<div style="display:flex; align-items:center; gap:10px; padding:4px 0;">${iconHtml("mdi:check-circle-outline", { size: "22px", style: `color:${KIT_COLOR.good}; flex:none;` })}<span class="ck-sub">Nothing needs doing. Jobs like a low battery or a filter due show here, and go by themselves once they're done.</span></div>`;
        hydrateIcons(this);
        return;
      }
      this._list.innerHTML = tasks.map(({ h }, i) => {
        const tag = all || h.who.toLowerCase() !== first.toLowerCase() ? `<span class="ck-chip ht-who" style="color:var(--secondary-text-color); background:rgba(127,127,127,0.16);"></span>` : "";
        return `<div class="ht-row" style="display:flex; align-items:center; gap:10px; padding:9px 0;${i ? " border-top:1px solid var(--divider-color, rgba(127,127,127,0.22));" : ""}">
            ${iconHtml(h.icon, { size: "22px", style: `color:${colour}; flex:none;` })}
            <div style="flex:1; min-width:0;">
              <div class="ht-name" style="font-size:0.95rem; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;"></div>
              <div class="ht-sub ck-sub" style="font-size:0.74rem; line-height:1.35;"></div>
            </div>${tag}
          </div>`;
      }).join("") + `<div class="ck-sub" style="font-size:0.72rem; padding-top:6px;">These go by themselves once the device reports they're done.</div>`;
      this._list.querySelectorAll(".ht-row").forEach((el, i) => {
        const { t, h } = tasks[i];
        el.querySelector(".ht-name").textContent = t.summary;
        el.querySelector(".ht-sub").textContent = [h.kind, h.detail].filter(Boolean).join(" \xB7 ");
        const who = el.querySelector(".ht-who");
        if (who) who.textContent = all ? h.who : h.who.split(", ").map((n) => first && n.toLowerCase() === first.toLowerCase() ? "You" : n).join(", ");
      });
      hydrateIcons(this);
    }
    getCardSize() {
      return 3;
    }
    getGridOptions() {
      return { columns: 12, min_columns: 6, rows: "auto" };
    }
    static getConfigElement() {
      return document.createElement(`house-tasks-card-editor${SUFFIX}`);
    }
    static getStubConfig() {
      return { title: "From the house" };
    }
  };
  function registerHouseTasksCard() {
    if (!customElements.get(`house-tasks-card-editor${SUFFIX}`)) customElements.define(`house-tasks-card-editor${SUFFIX}`, HouseTasksCardEditor);
    if (!customElements.get(`house-tasks-card${SUFFIX}`)) customElements.define(`house-tasks-card${SUFFIX}`, HouseTasksCard);
    window.customCards = window.customCards || [];
    window.customCards.push({
      type: `house-tasks-card${SUFFIX}`,
      name: `House Tasks Card${LABEL}`,
      description: "Jobs the house has spotted (batteries, filters, devices), which clear themselves once done",
      preview: true,
      documentationURL: "https://github.com/J45PER/church-drive-cards#readme"
    });
  }

  // src/auto-layout-card.js
  var helpersPromise2;
  function cardHelpers2() {
    if (!helpersPromise2) helpersPromise2 = window.loadCardHelpers ? window.loadCardHelpers() : Promise.reject(new Error("no card helpers"));
    return helpersPromise2;
  }
  var PANEL = /section-panel-card/;
  var CONTROL_TYPES = [
    "alarm-panel-card",
    "light-control-card",
    "climate-card",
    "fan-card",
    "air-purifier-card",
    "cover-card",
    "scene-styles-card",
    "scene-builder-card",
    "thermostat",
    "humidifier",
    "light",
    "button",
    "media-control",
    "alarm-panel",
    "area"
  ];
  function hasControls(conf) {
    if (!conf || typeof conf !== "object") return false;
    if (conf.priority === "controls") return true;
    if (conf.priority === "info") return false;
    const type = String(conf.type || "").replace(/^custom:/, "").replace(/-beta$/, "");
    if (CONTROL_TYPES.includes(type)) return true;
    if (type === "tile" && Array.isArray(conf.features) && conf.features.length) return true;
    const kids = [].concat(conf.cards || [], conf.card ? [conf.card] : []);
    return kids.some((k) => hasControls(k));
  }
  var GAP = "var(--ha-view-sections-column-gap, 32px)";
  var PLAN_KEY = "cd-layout-plans";
  var plans = null;
  function planStore() {
    if (plans) return plans;
    plans = {};
    try {
      plans = JSON.parse(localStorage.getItem(PLAN_KEY) || "{}") || {};
    } catch (err) {
    }
    return plans;
  }
  function planRemember(key, splits) {
    const store = planStore();
    const v = JSON.stringify(splits);
    if (JSON.stringify(store[key]) === v) return;
    store[key] = splits;
    const keys = Object.keys(store);
    if (keys.length > 60) delete store[keys[0]];
    try {
      localStorage.setItem(PLAN_KEY, JSON.stringify(store));
    } catch (err) {
    }
  }
  var LayoutFields = createFormEditor({
    schema: () => [
      { name: "title", selector: { text: {} } },
      { name: "priorities", selector: { boolean: {} }, default: false },
      { name: "priorities_page", selector: { navigation: {} } },
      { name: "widget_width", selector: { number: { min: 280, max: 1200, step: 10, mode: "box", unit_of_measurement: "px" } } },
      { name: "column_width", selector: { number: { min: 200, max: 800, step: 10, mode: "box", unit_of_measurement: "px" } } },
      { name: "max_columns", selector: { number: { min: 1, max: 6, step: 1, mode: "box" } } },
      { name: "controls_first", selector: { boolean: {} }, default: true },
      { name: "jump_chips", selector: { select: { mode: "dropdown", options: [{ value: "auto", label: "Automatic (phones)" }, { value: "always", label: "Always" }, { value: "never", label: "Never" }] } } }
    ],
    labels: {
      title: "Page title (optional; {user} is the signed-in person's first name)",
      priorities: "Show the signed-in person's top to-do in the header",
      priorities_page: 'Their to-do page (for "+N more")',
      widget_width: "Header widget at most this wide",
      column_width: "Columns at least this wide",
      max_columns: "At most this many columns",
      controls_first: "Panels with buttons and sliders go above ones that only show information",
      jump_chips: "Jump-to chips at the top"
    },
    helpers: {
      title: "Shown large at the top of the page, above the chips. Any panel that has opened by itself (its 'opens by itself when' is true) shows under it as an alert; tapping one goes to that panel.",
      priorities: `Reads the to-do list named "Priorities <first name>" (e.g. todo.priorities_jamie), plus the shared "Priorities Everyone" list if there is one, each item with a \u2713 (a shared item ticked off clears for everyone); overdue and due-soonest come first. Also shows this person's and everyone's jobs from "Priorities Automatic" (low batteries, filters and so on) with no \u2713: they go by themselves once the device reports they're done.`,
      widget_width: "Default 520px, centred under the title. Phones use the full width.",
      column_width: "Default 340px. Phones (under 600px) always get one column in list order.",
      max_columns: 'Default 3. Mark a panel "Full width across an Auto Layout" to have it span the page.',
      controls_first: 'Keeps list order otherwise, on phones too. Each panel can override what it counts as ("Counts as" in the panel).',
      jump_chips: "One chip per panel, in its colour; tapping one scrolls to that panel, and the chip for the panel you're looking at is filled in."
    }
  });
  var CHIPS_CSS = `
  .al-chips .al-cap { flex:1 1 auto; min-width:0; padding:6px; border-radius:999px; box-sizing:border-box; }
  ${kitAcrylicCss(".al-chips .al-cap")}
  .al-chips .al-strip { display:flex; gap:6px; overflow-x:auto; scrollbar-width:none; }
`;
  var boxHeight = (el) => el ? Math.round(el.getBoundingClientRect().height) : 0;
  function headerBottom() {
    const root = nbHuiRoot();
    const sr = root && root.shadowRoot;
    const bar = sr && (sr.querySelector(".header") || sr.querySelector("app-header") || sr.querySelector("app-toolbar"));
    const r = bar && bar.getBoundingClientRect();
    return r && r.height ? Math.max(0, r.bottom) : 56;
  }
  function balance(heights, k, gap, keep) {
    const n = heights.length;
    k = Math.max(1, Math.min(k, n));
    const cost = (cols2) => Math.max(...cols2.map((c) => c.reduce((s, i) => s + heights[i], 0) + gap * Math.max(0, c.length - 1)));
    let best = null;
    let bestCost = Infinity;
    if (n <= 11) {
      const lab = new Array(n).fill(0);
      const sums = new Array(k).fill(0);
      const counts = new Array(k).fill(0);
      const walk = (i, used) => {
        if (i === n) {
          if (used !== k) return;
          const c = Math.max(...sums.map((s, j) => s + gap * Math.max(0, counts[j] - 1)));
          if (c < bestCost - 4) {
            bestCost = c;
            best = lab.slice();
          }
          return;
        }
        if (n - i < k - used) return;
        for (let j = 0; j <= Math.min(used, k - 1); j += 1) {
          sums[j] += heights[i];
          counts[j] += 1;
          lab[i] = j;
          const partial = sums[j] + gap * (counts[j] - 1);
          if (partial < bestCost - 4) walk(i + 1, Math.max(used, j + 1));
          sums[j] -= heights[i];
          counts[j] -= 1;
        }
      };
      walk(0, 0);
    }
    let cols;
    if (best) {
      cols = Array.from({ length: k }, () => []);
      best.forEach((j, i) => cols[j].push(i));
    } else {
      cols = Array.from({ length: k }, () => []);
      const sums = new Array(k).fill(0);
      heights.forEach((h, i) => {
        const j = sums.indexOf(Math.min(...sums));
        cols[j].push(i);
        sums[j] += h + gap;
      });
    }
    if (keep && keep.length === k && keep.flat().length === n && cost(keep) <= cost(cols) + 24) return keep;
    return cols;
  }
  var AutoLayoutCardEditor = class extends HTMLElement {
    setConfig(config) {
      this._config = config;
      this._render();
    }
    set hass(hass) {
      const first = !this._hass;
      this._hass = hass;
      if (first) this._renderHead();
      if (this.config && this.config.priorities) this._watchTodo();
      this._render();
    }
    set lovelace(lovelace) {
      this._lovelace = lovelace;
      if (this._stack) this._stack.lovelace = lovelace;
    }
    _emit(config) {
      this._config = config;
      this.dispatchEvent(new CustomEvent("config-changed", { detail: { config }, bubbles: true, composed: true }));
    }
    async _render() {
      if (!this._config || !this._hass) return;
      if (!this._fields) {
        this._fields = document.createElement(`auto-layout-fields${SUFFIX}`);
        this._fields.addEventListener("config-changed", (ev) => {
          ev.stopPropagation();
          const { cards: cards2, ...fields2 } = ev.detail.config;
          this._emit({ ...this._config, ...fields2, cards: this._config.cards || [] });
        });
        const label = document.createElement("div");
        label.textContent = "Panels, in order (phones show them top to bottom in this order)";
        label.style.cssText = "margin:20px 0 8px; font-weight:500;";
        this.append(this._fields, label);
      }
      const { cards, ...fields } = this._config;
      this._fields.hass = this._hass;
      this._fields.setConfig(fields);
      if (!this._stack && !this._stackLoading) {
        this._stackLoading = true;
        try {
          const helpers = await cardHelpers2();
          helpers.createCardElement({ type: "vertical-stack", cards: [] });
          await customElements.whenDefined("hui-vertical-stack-card");
          this._stack = await customElements.get("hui-vertical-stack-card").getConfigElement();
          this._stack.addEventListener("config-changed", (ev) => {
            ev.stopPropagation();
            this._emit({ ...this._config, cards: ev.detail.config.cards || [] });
          });
          this.appendChild(this._stack);
        } catch (err) {
          const note = document.createElement("p");
          note.textContent = "The card list editor could not load; use the code editor to change the panels.";
          this.appendChild(note);
        }
        this._stackLoading = false;
      }
      if (this._stack) {
        this._stack.hass = this._hass;
        if (this._lovelace) this._stack.lovelace = this._lovelace;
        this._stack.setConfig({ type: "vertical-stack", cards: this._config.cards || [] });
      }
    }
  };
  var AutoLayoutCard = class extends HTMLElement {
    setConfig(config) {
      if (!Array.isArray(config.cards)) throw new Error("cards required");
      this.config = config;
      this._build();
    }
    set hass(hass) {
      this._hass = hass;
      (this._items || []).forEach((it) => {
        it.el.hass = hass;
      });
    }
    _build() {
      this.style.display = "block";
      this.innerHTML = "";
      if (this._chips) this._chips.remove();
      this._chips = document.createElement("div");
      this._chips.className = "al-chips";
      this._chips.style.cssText = "display:none; align-items:center; gap:8px; box-sizing:border-box; z-index:4; transition:opacity .2s;";
      this._chips.addEventListener("click", (ev) => {
        const chip = ev.target.closest && ev.target.closest("[data-i]");
        const it = chip && this._items[Number(chip.dataset.i)];
        if (!it) return;
        this._jumpTo(it);
        this._current = Number(chip.dataset.i);
        this._chipsSig = null;
        this._renderChips();
      });
      this._spacer = document.createElement("div");
      this._spacer.style.cssText = "display:none;";
      this._root = document.createElement("div");
      this._root.style.cssText = `display:flex; flex-direction:column; gap:${GAP};`;
      this._tail = document.createElement("div");
      this._tail.style.cssText = "height:0;";
      this._head = document.createElement("div");
      this._head.className = "al-head";
      this._head.style.cssText = "display:none; flex-direction:column; align-items:stretch; gap:8px; padding:4px 0 12px; box-sizing:border-box;";
      this._head.addEventListener("click", (ev) => {
        const tick = ev.target.closest && ev.target.closest("[data-done]");
        if (tick) {
          this._completeTop(tick);
          return;
        }
        if (ev.target.closest && ev.target.closest("[data-todo]")) {
          if (this.config.priorities_page) kitNavigate(this.config.priorities_page);
          return;
        }
        const pill = ev.target.closest && ev.target.closest("[data-alert]");
        const it = pill && this._items[Number(pill.dataset.alert)];
        if (it) this._jumpTo(it);
      });
      this.append(this._head, this._spacer, this._root, this._tail);
      this._items = [];
      this._plan = "";
      const token = this._token = {};
      cardHelpers2().then((helpers) => {
        if (token !== this._token) return;
        this._items = this.config.cards.map((conf) => this._make(helpers, conf));
        this._layout(true);
      }).catch(() => {
      });
    }
    _make(helpers, conf) {
      const el = helpers.createCardElement(conf);
      el._managed = true;
      if (this._hass) el.hass = this._hass;
      const it = { conf, el, controls: hasControls(conf) };
      el.addEventListener("ll-rebuild", (ev) => {
        ev.stopPropagation();
        const fresh = helpers.createCardElement(conf);
        fresh._managed = true;
        if (this._hass) fresh.hass = this._hass;
        el.replaceWith(fresh);
        it.el = fresh;
        this._queue();
      });
      return it;
    }
    _columns() {
      if (window.innerWidth < 600) return 1;
      const c = this.config;
      const min = Number(c.column_width) || 340;
      const max = Number(c.max_columns) || 3;
      const gap = this._gap();
      const w = this.getBoundingClientRect().width || window.innerWidth;
      return Math.max(1, Math.min(max, Math.floor((w + gap) / (min + gap))));
    }
    _gap() {
      const v = parseFloat(getComputedStyle(this).getPropertyValue("--ha-view-sections-column-gap"));
      return Number.isFinite(v) ? v : 32;
    }
    // Full width: set on the panel, or automatic for a panel of 3+ small cards
    // (zones, cameras, tiles) that won't fit side by side in one column.
    _full(it, colWidth) {
      const f = it.conf.full_width;
      if (f === true || f === "yes") return true;
      if (f === false || f === "no") return false;
      const cards = it.conf.cards || [];
      const w = it.el._cardWidth ? it.el._cardWidth() : 300;
      if (cards.length < 3 || !w || w > 240) return false;
      const across = Math.max(1, Math.floor((colWidth - 24 + 12) / (w + 12)));
      return cards.length > across;
    }
    _open(el) {
      return PANEL.test(el.localName) && !(el._mode && el._mode() === "compact") && !(el.config && el.config.match_height === false);
    }
    // Height of an item without any stretch.
    _height(el) {
      if (el._naturalHeight) return el._naturalHeight();
      return el.getBoundingClientRect().height;
    }
    _queue() {
      if (this._animating > 0) {
        this._pending = true;
        return;
      }
      cancelAnimationFrame(this._frame);
      this._frame = requestAnimationFrame(() => {
        this._layout(false);
        this._trimTail();
      });
    }
    // Work out bands (split at full-width items) and columns; only move cards
    // when the arrangement actually changes, so cameras etc. aren't reloaded.
    _layout(force) {
      if (!this.isConnected || !this._items.length) return;
      const cols = this._columns();
      const gap = this._gap();
      const colWidth = ((this.getBoundingClientRect().width || window.innerWidth) - gap * (cols - 1)) / cols;
      const wide = cols > 1 ? this._items.filter((it) => this._full(it, colWidth)) : [];
      const rest = this._items.filter((it) => !wide.includes(it));
      const bands = [];
      if (rest.length) bands.push({ items: rest });
      const first = this.config.controls_first !== false;
      [...wide.filter((it) => !first || it.controls), ...wide.filter((it) => first && !it.controls)].forEach((it) => bands.push({ items: [it], full: true }));
      if (this.config.controls_first !== false) {
        bands.forEach((b) => {
          b.items = [...b.items.filter((it) => it.controls), ...b.items.filter((it) => !it.controls)];
        });
      }
      const placed = this._items.every((it) => it.el.isConnected);
      const planKey = `${location.pathname}|${cols}|${bands.map((b) => b.items.map((it) => it.conf.title || it.conf.type).join(",")).join("/")}`;
      const saved = planStore()[planKey];
      bands.forEach((b, n) => {
        if (b.full || cols === 1) {
          b.split = [b.items.map((it, i) => i)];
          return;
        }
        if (!placed) {
          const s = saved && saved[n];
          const ok = Array.isArray(s) && s.flat().length === b.items.length && s.length <= cols;
          b.split = ok ? s : [b.items.map((it, i) => i)];
          return;
        }
        const key = b.items.map((it) => this._items.indexOf(it)).join(",");
        const prev = this._prevSplits && this._prevSplits[key];
        const settled = performance.now() > this._settleUntil && cols === this._cols;
        b.split = settled && prev && prev.length === Math.min(cols, b.items.length) ? prev : balance(b.items.map((it) => this._height(it.el)), cols, gap, prev);
      });
      this._prevSplits = {};
      bands.forEach((b) => {
        this._prevSplits[b.items.map((it) => this._items.indexOf(it)).join(",")] = b.split;
      });
      if (placed) planRemember(planKey, bands.map((b) => b.split));
      const plan = `${cols}|${bands.map((b) => `${b.full ? "F" : ""}${b.split.map((c) => c.join(".")).join(",")}`).join("/")}`;
      if (force || plan !== this._plan) {
        this._plan = plan;
        this._root.innerHTML = "";
        bands.forEach((b) => {
          const row3 = document.createElement("div");
          row3.style.cssText = `display:flex; gap:${GAP}; align-items:stretch;`;
          b.cols = b.split.map((idx) => {
            const col = document.createElement("div");
            col.style.cssText = `flex:1 1 0; min-width:0; display:flex; flex-direction:column; gap:${GAP};`;
            idx.forEach((i) => col.appendChild(b.items[i].el));
            row3.appendChild(col);
            return col;
          });
          this._root.appendChild(row3);
        });
        this._bands = bands;
        if (!placed) {
          this._queue();
          return;
        }
      }
      if (cols !== this._cols) this._settleUntil = performance.now() + 2500;
      this._stretch(cols, gap);
      this._cols = cols;
      this._renderHead();
      this._renderChips();
    }
    // Each column's last open panel grows so the columns in a band end level,
    // but only by a modest amount: a column that can't be evened out stays
    // short rather than ending in a big empty panel.
    _stretch(cols, gap) {
      const set = (el, px) => {
        const p = el._panelEl;
        if (!p) return;
        const v = px ? `${Math.round(px)}px` : "";
        if (p.style.minHeight === v) return;
        const settling = performance.now() < this._settleUntil;
        if (settling && !el._sliding) p.style.transition = "none";
        p.style.minHeight = v;
        if (settling && !el._sliding) {
          void p.offsetHeight;
          p.style.transition = PANEL_TRANSITION;
        }
      };
      (this._bands || []).forEach((b) => {
        const runs = b.cols.map((col) => [...col.children].map((el) => this._items.find((it) => it.el === el)).filter(Boolean));
        if (cols === 1 || runs.length < 2) {
          runs.flat().forEach((it) => set(it.el, 0));
          return;
        }
        const totals = runs.map((r) => r.reduce((s, it) => s + this._height(it.el), 0) + gap * Math.max(0, r.length - 1));
        const end = Math.max(...totals);
        runs.forEach((r, i) => {
          let grow = null;
          for (let k = r.length - 1; k >= 0 && !grow; k -= 1) if (this._open(r[k].el)) grow = r[k];
          r.forEach((it) => {
            const extra = end - totals[i];
            const h = this._height(it.el);
            const ok = extra > 1 && extra <= Math.max(160, h * 0.5);
            set(it.el, it === grow && ok ? h + extra : 0);
          });
        });
      });
    }
    // ---- Page header: the title and any alerts.
    _renderHead() {
      const head = this._head;
      if (!head) return;
      const esc2 = (t) => String(t).replace(/[&<>"]/g, (ch) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[ch]);
      const alerts = (this._items || []).map((it, i) => ({ it, i })).filter(({ it }) => it.el._alert && it.conf.title).map(({ it, i }) => {
        const bg = it.el.querySelector && it.el.querySelector(".spc-bg");
        const colour = bg && bg.style.background || stcColor(it.conf.color || "primary");
        const summary = it.el._title && it.el._title._summary;
        return { i, colour, icon: it.conf.icon, text: summary ? `${it.conf.title} \xB7 ${summary}` : it.conf.title };
      });
      const user = this._hass && this._hass.user && this._hass.user.name ? String(this._hass.user.name).split(" ")[0] : "";
      const title = String(this.config.title || "").replace(/\{user\}/g, user).trim();
      const todo = this._todoView();
      const sig = JSON.stringify([title, alerts, todo, this.config.widget_width]);
      if (sig === this._headSig) return;
      this._headSig = sig;
      if (!this.config.title) {
        head.style.display = "none";
        head.innerHTML = "";
        return;
      }
      head.style.display = "flex";
      const rows = [
        ...alerts.map((a) => ({ kind: "alert", ...a })),
        ...todo ? todo.items.map((t) => ({ kind: "todo", ...t })) : []
      ];
      const shown = rows.slice(0, 3);
      const extra = rows.length - shown.length;
      const row3 = (r) => {
        if (!r) return '<div style="height:26px;"></div>';
        const c = r.kind === "todo" ? r.overdue ? "#e53935" : r.auto ? HOUSE_TASKS_COLOR : "#7e57c2" : r.colour;
        const icon = r.icon;
        const tick = r.kind === "todo" && !r.auto ? `<button type="button" data-done="${esc2(r.uid)}" data-list="${esc2(r.list)}" aria-label="Done" title="Done" style="flex:none; width:22px; height:22px; padding:0; border:2px solid color-mix(in srgb, ${c} 70%, transparent); border-radius:50%; background:transparent; color:var(--primary-text-color); cursor:pointer; display:flex; align-items:center; justify-content:center;">${iconHtml("mdi:check", { size: "14px" })}</button>` : "";
        return `<div ${r.kind === "alert" ? `data-alert="${r.i}" role="button"` : ""} style="height:26px; display:flex; align-items:center; gap:8px; padding:0 4px 0 8px; border-radius:13px; cursor:${r.kind === "alert" ? "pointer" : "default"}; background:color-mix(in srgb, ${c} 16%, transparent);">${icon ? iconHtml(icon, { size: "16px", style: `color:${c}; flex:none;` }) : ""}<span style="flex:1; min-width:0; font-size:0.8rem; font-weight:600; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">${esc2(r.text)}</span>${tick}</div>`;
      };
      const side = todo && this.config.priorities_page ? `<button type="button" data-todo style="flex:none; width:58px; padding:0 4px; border:none; border-radius:12px; cursor:pointer; font:inherit; font-size:0.72rem; font-weight:700; line-height:1.2; color:var(--primary-text-color); background:color-mix(in srgb, #7e57c2 22%, transparent); display:flex; flex-direction:column; align-items:center; justify-content:center; gap:2px;">${iconHtml(
        "mdi:format-list-checks",
        { size: "18px", style: "color:#b39ddb;" }
      )}${extra > 0 ? `+${extra}` : "To-do"}<span style="font-size:0.9rem; line-height:1;">\u203A</span></button>` : "";
      head.innerHTML = `<div style="height:40px; font-size:2rem; font-weight:700; line-height:40px; text-align:center; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; color:var(--primary-text-color);">${esc2(title)}</div>
      <div class="al-widget" style="height:96px; width:100%; max-width:${Number(this.config.widget_width) || 520}px; margin:0 auto; box-sizing:border-box; display:flex; gap:6px; padding:6px; border-radius:18px; background:color-mix(in srgb, var(--card-background-color, #1f2128) 70%, transparent);">
        <div style="flex:1; min-width:0; display:flex; flex-direction:column; gap:3px;">${[0, 1, 2].map((n) => row3(shown[n])).join("")}</div>${side}
      </div>`;
      hydrateIcons(head);
    }
    // ---- Priorities: the signed-in person's to-do list ("Priorities Jamie"),
    // plus the shared one ("Priorities Everyone") when it exists. A shared item
    // is a single copy, so ticking it off clears it for everyone.
    _todoEntities() {
      if (!this.config.priorities || !this._hass || !this._hass.user) return [];
      const first = String(this._hass.user.name || "").split(" ")[0].toLowerCase().replace(/[^a-z0-9]+/g, "_");
      return [`todo.priorities_${first}`, "todo.priorities_everyone", HOUSE_TASKS_LIST].filter((id) => this._hass.states[id]);
    }
    _watchTodo() {
      const ids = this._todoEntities();
      const key = ids.join(",");
      if (key === this._todoKey) return;
      this._unwatchTodo();
      this._todoKey = key;
      this._todoId = ids[0] || null;
      if (!ids.length || !this.isConnected) return;
      this._todoUnsubs = ids.map(
        (id) => this._hass.connection.subscribeMessage(
          (msg) => {
            this._todoItems[id] = msg && msg.items || [];
            this._renderHead();
          },
          { type: "todo/item/subscribe", entity_id: id }
        ).catch(() => null)
      );
    }
    _unwatchTodo() {
      (this._todoUnsubs || []).forEach((u) => u.then((f) => f && f()).catch(() => {
      }));
      this._todoUnsubs = [];
      this._todoItems = {};
      this._todoKey = void 0;
      this._todoId = null;
    }
    // Open items, overdue and due-soonest first, then in list order (personal,
    // shared, then the house's). The house's automatic tasks (from
    // "Priorities Automatic", the ones for this person or everyone) are cleared
    // by the device itself, so they get no ✓ and an icon by kind.
    _todoView() {
      if (!this._todoKey) return null;
      const shared = "todo.priorities_everyone";
      const order = (id) => id === HOUSE_TASKS_LIST ? 2 : id === shared ? 1 : 0;
      const first = String(this._hass && this._hass.user && this._hass.user.name || "").split(" ")[0];
      const open = [];
      Object.keys(this._todoItems || {}).sort((x, y) => order(x) - order(y)).forEach(
        (list) => (this._todoItems[list] || []).forEach((t) => {
          if (t.status !== "needs_action") return;
          const auto = list === HOUSE_TASKS_LIST || /^Automatic/.test(t.description || "");
          const task = auto ? houseTask(t) : null;
          if (list === HOUSE_TASKS_LIST && !houseTaskFor(task, first)) return;
          open.push({ t, list, task });
        })
      );
      const rank = (t) => t.due ? new Date(t.due).getTime() : Infinity;
      const today = (/* @__PURE__ */ new Date()).toISOString().slice(0, 10);
      const items = open.map((o, n) => ({ ...o, n })).sort((a, b) => rank(a.t) - rank(b.t) || a.n - b.n).map(({ t, list, task }) => ({
        uid: t.uid,
        list,
        text: t.summary,
        overdue: !!t.due && String(t.due).slice(0, 10) < today,
        auto: !!task,
        icon: task ? task.icon : list === shared ? "mdi:account-group" : "mdi:flag"
      }));
      return { items };
    }
    _completeTop(tick) {
      const uid = tick.dataset.done;
      const list = tick.dataset.list;
      if (!uid || !list) return;
      tick.style.background = "#4caf50";
      tick.style.borderColor = "#4caf50";
      this._hass.callService("todo", "update_item", { item: uid, status: "completed" }, { entity_id: list }).catch(() => {
      });
    }
    // ---- Jump-to chips: one per panel, in page order, in the panel's colour.
    _chipsWanted() {
      const mode = this.config.jump_chips || "auto";
      if (mode === "never") return false;
      const panels = (this._items || []).filter((it) => it.conf.title);
      if (mode === "always") return panels.length > 1;
      return this._cols === 1 && panels.length >= 1;
    }
    // Panels in the order they appear on the page (top to bottom, left to right).
    _pageOrder() {
      return (this._items || []).map((it, i) => ({ it, i, r: it.el.getBoundingClientRect() })).filter((x) => x.it.conf.title && x.r.height > 0).sort((a, b) => a.r.top - b.r.top || a.r.left - b.r.left);
    }
    _chipsFloat() {
      return !(this.editMode || this.preview) && this.isConnected;
    }
    // Where the capsule goes. At the top of the page it sits in its own row
    // (inside the spacer, scrolling with the page, so it never covers what's
    // above it, like the welcome message); once that row reaches the header it
    // pins under the header (on document.body, since HA's card wrappers stop
    // position:sticky working).
    _placeChips() {
      const box = this._chips;
      const float = this._chipsFloat();
      const spacer = this._spacer;
      if (!float) {
        if (box.parentNode !== this) this.insertBefore(box, this._root);
        Object.assign(box.style, { position: "relative", top: "", left: "", width: "", marginBottom: "12px" });
        spacer.style.display = "none";
        return;
      }
      if (box.parentNode !== spacer && box.parentNode !== document.body) spacer.appendChild(box);
      const h = boxHeight(box) || 44;
      spacer.style.cssText = `display:block; position:relative; height:${h + 12}px;`;
      const r = this.getBoundingClientRect();
      const pinAt = headerBottom() + 8;
      const rowTop = spacer.getBoundingClientRect().top;
      const pinned = rowTop < pinAt;
      if (pinned) {
        if (box.parentNode !== document.body) document.body.appendChild(box);
        Object.assign(box.style, {
          position: "fixed",
          top: `${pinAt}px`,
          left: `${Math.round(r.left)}px`,
          width: `${Math.round(r.width)}px`,
          marginBottom: "",
          opacity: r.width ? "1" : "0",
          pointerEvents: r.width ? "auto" : "none"
        });
      } else {
        if (box.parentNode !== spacer) spacer.appendChild(box);
        Object.assign(box.style, { position: "absolute", top: "0", left: "0", width: "100%", marginBottom: "", opacity: "1", pointerEvents: "auto" });
      }
    }
    // Scroll a panel up to just under the chips, opening it if it's compact
    // (for this visit only; the saved open/compact choice doesn't change).
    // The panel a previous jump opened closes again, unless it's been touched.
    _jumpTo(it) {
      const el = it.el;
      const prev = this._closeJumped(el);
      if (el._mode && el._mode() === "compact" && el._slide && el._apply) {
        el._fallback = "open";
        el._slide(() => el._apply());
        this._jumpOpened = el;
      }
      const top = this._pinnedChipsBottom() + 10;
      const r = el.getBoundingClientRect();
      let below = this._root.getBoundingClientRect().bottom - r.top;
      if (prev && prev.getBoundingClientRect().top > r.top) below -= prev.getBoundingClientRect().height;
      const need = Math.max(0, Math.ceil(window.innerHeight - top - below), this._restTail());
      this._tail.style.height = `${need}px`;
      this._jump = { el, top, arrived: false, since: performance.now() };
      kitGlide(kitScrollParent(this), () => el.getBoundingClientRect().top - top);
    }
    // Close the panel the last chip jump opened, if it's still only open for
    // that jump. Returns it.
    _closeJumped(keep) {
      const el = this._jumpOpened;
      this._jumpOpened = null;
      if (!el || el === keep || el._fallback !== "open" || !el._slide) return null;
      el._fallback = null;
      el._slide(() => el._apply());
      return el;
    }
    // Room at the end of the page so the last panel can always be scrolled
    // up to just under the chips, where a chip jump puts it (phones, with the
    // chips showing). It follows the last panel's height as panels open/close.
    _restTail() {
      if (!this._tail || !this._chipsWanted() || !this._chipsFloat()) return 0;
      const order = this._pageOrder();
      const last = order[order.length - 1];
      if (!last) return 0;
      const sc = kitScrollParent(this);
      const doc = sc === document.scrollingElement || sc === document.documentElement;
      const scTop = doc ? 0 : sc.getBoundingClientRect().top;
      const view = doc ? window.innerHeight : sc.clientHeight;
      const tail = parseFloat(this._tail.style.height) || 0;
      const after = sc.scrollHeight - tail - (last.r.top - scTop + kitScrollTop(sc));
      return Math.max(0, Math.ceil(view - (this._pinnedChipsBottom() + 10 - scTop) - after));
    }
    // A chip jump may need more room than that (to bring a panel that isn't
    // last to the top); drop back to the resting room once the jump has
    // landed and you scroll back up away from that panel (or if it never lands).
    _trimTail() {
      if (!this._tail) return;
      const rest = this._restTail();
      const j = this._jump;
      if (j) {
        const at = j.el.getBoundingClientRect().top;
        if (Math.abs(at - j.top) < 8) j.arrived = true;
        const leftIt = j.arrived && at > j.top + 40;
        if (!leftIt && (j.arrived || performance.now() - j.since <= 3e3)) {
          if ((parseFloat(this._tail.style.height) || 0) < rest) this._tail.style.height = `${rest}px`;
          return;
        }
        this._jump = null;
      }
      this._tail.style.height = `${rest}px`;
    }
    _pinnedChipsBottom() {
      const box = this._chips;
      return box && box.style.display !== "none" && this._chipsFloat() ? headerBottom() + 8 + boxHeight(box) : this._chipsBottom();
    }
    _chipsBottom() {
      const box = this._chips;
      return box && box.style.display !== "none" ? box.getBoundingClientRect().bottom : headerBottom();
    }
    _renderChips() {
      const box = this._chips;
      if (!box) return;
      if (!this._chipsWanted()) {
        box.style.display = "none";
        if (this._spacer) this._spacer.style.display = "none";
        return;
      }
      box.style.display = "flex";
      this._placeChips();
      const order = this._pageOrder();
      if (this._current == null && order.length) this._current = order[0].i;
      const isOpen = (el) => !(el._mode && el._mode() === "compact");
      const chips = order.map(({ it, i }) => {
        const bg = it.el.querySelector && it.el.querySelector(".spc-bg");
        const colour = bg && bg.style.background || stcColor(it.conf.color || "primary");
        return { i, title: it.conf.title, icon: it.conf.icon, colour, on: i === this._current, open: isOpen(it.el) };
      });
      const sig = JSON.stringify(chips);
      if (sig === this._chipsSig) return;
      this._chipsSig = sig;
      const esc2 = (t) => String(t).replace(/[&<>"]/g, (ch) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[ch]);
      const chip = (c) => {
        const filled = c.on && c.open;
        c.on = filled;
        return `<button type="button" data-i="${c.i}" style="flex:1 0 auto; display:inline-flex; align-items:center; justify-content:center; gap:5px; padding:6px 11px; border:none; border-radius:999px; cursor:pointer; font:inherit; font-size:0.78rem; font-weight:600; transition:background-color .2s, box-shadow .2s;
        color:${filled ? "#fff" : "var(--primary-text-color)"}; background:${filled ? c.colour : `color-mix(in srgb, ${c.colour} 18%, var(--card-background-color, #22252e))`};">${c.icon ? iconHtml(c.icon, { size: "16px", style: `color:${filled ? "#fff" : c.colour};` }) : ""}${esc2(c.title)}</button>`;
      };
      box.innerHTML = `<div class="al-cap"><div class="al-strip">${chips.map(chip).join("")}</div></div>`;
      if (!box.querySelector("style.al-css")) {
        const st = document.createElement("style");
        st.className = "al-css";
        st.textContent = CHIPS_CSS;
        box.prepend(st);
      }
      hydrateIcons(box);
      const strip = box.querySelector(".al-strip");
      const on = box.querySelector(`[data-i="${this._current}"]`);
      if (on && strip) strip.scrollLeft = Math.max(0, on.offsetLeft - strip.offsetLeft - 24);
    }
    // The chip for the panel at the top of the screen is filled in.
    _onScroll() {
      this._trimTail();
      if (!this._chipsWanted()) return;
      const line = this._chipsBottom() + 24;
      let current = null;
      this._pageOrder().forEach(({ i, r }) => {
        if (r.top <= line) current = i;
      });
      if (current == null) current = (this._pageOrder()[0] || {}).i;
      this._current = current;
      this._renderChips();
    }
    connectedCallback() {
      this._settleUntil = performance.now() + 4e3;
      this._animating = 0;
      this._onAnim = (ev) => {
        this._animating = Math.max(0, this._animating + (Number(ev.detail) || 0));
        if (!this._animating && this._pending) {
          this._pending = false;
          this._queue();
        }
      };
      window.addEventListener("cd-anim", this._onAnim);
      this._onTop = () => this._closeJumped(null);
      window.addEventListener("cd-to-top", this._onTop);
      if (this._hass && this.config.priorities) this._watchTodo();
      this._onScrollBound = () => {
        cancelAnimationFrame(this._scrollFrame);
        this._scrollFrame = requestAnimationFrame(() => this._onScroll());
      };
      window.addEventListener("scroll", this._onScrollBound, { capture: true, passive: true });
      this._onChange = () => this._queue();
      this._onResize = () => {
        this._queue();
        if (this._chips && this._chips.style.display !== "none") this._placeChips();
      };
      window.addEventListener("resize", this._onResize);
      window.addEventListener("cd-panels-changed", this._onChange);
      if (window.ResizeObserver && !this._ro) {
        this._ro = new ResizeObserver(() => this._queue());
        this._ro.observe(this);
        this._timer = setInterval(() => this._queue(), 3e3);
      }
      this._queue();
    }
    disconnectedCallback() {
      window.removeEventListener("scroll", this._onScrollBound, { capture: true });
      window.removeEventListener("resize", this._onResize);
      window.removeEventListener("cd-anim", this._onAnim);
      window.removeEventListener("cd-to-top", this._onTop);
      if (this._chips && this._chips.parentNode === document.body) this._chips.remove();
      this._unwatchTodo();
      window.removeEventListener("cd-panels-changed", this._onChange);
      if (this._ro) this._ro.disconnect();
      this._ro = null;
      clearInterval(this._timer);
    }
    getCardSize() {
      return (this._items || []).reduce((n, it) => n + (it.el.getCardSize ? Number(it.el.getCardSize()) || 1 : 1), 0);
    }
    getGridOptions() {
      return { columns: "full", rows: "auto" };
    }
    static getConfigElement() {
      return document.createElement(`auto-layout-card-editor${SUFFIX}`);
    }
    static getStubConfig() {
      return { cards: [] };
    }
  };
  function registerAutoLayoutCard() {
    if (!customElements.get(`auto-layout-fields${SUFFIX}`)) customElements.define(`auto-layout-fields${SUFFIX}`, LayoutFields);
    if (!customElements.get(`auto-layout-card-editor${SUFFIX}`)) customElements.define(`auto-layout-card-editor${SUFFIX}`, AutoLayoutCardEditor);
    if (!customElements.get(`auto-layout-card${SUFFIX}`)) customElements.define(`auto-layout-card${SUFFIX}`, AutoLayoutCard);
    window.customCards = window.customCards || [];
    window.customCards.push({
      type: `auto-layout-card${SUFFIX}`,
      name: `Auto Layout Card${LABEL}`,
      description: "Arranges a page's panels into balanced columns by itself, with level bottoms",
      preview: false,
      documentationURL: "https://github.com/J45PER/church-drive-cards#readme"
    });
  }

  // src/index.js
  registerGaugeZoneCard();
  registerAlarmPanelCard();
  registerLightControlCard();
  registerSceneStylesCard();
  registerSceneBuilderCard();
  registerSectionTitleCard();
  registerSectionPanelCard();
  registerClimateCard();
  registerClimateZoneCard();
  registerFanCard();
  registerAirPurifierCard();
  registerCoAlarmCard();
  registerCoverCard();
  registerDeviceHealthCard();
  registerSecurityZoneCard();
  registerNavBarCard();
  registerAutoLayoutCard();
  registerHouseTasksCard();
  console.info(`%c CHURCH-DRIVE-CARDS${SUFFIX ? " BETA" : ""} %c loaded `, "color: white; background: #2196f3; font-weight: 700;", "color: #2196f3; background: transparent;");
})();
