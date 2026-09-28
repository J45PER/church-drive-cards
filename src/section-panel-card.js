// Section Panel Card: a group of cards on a faint panel in the section's
// colour, headed by a Section Title (large title, coloured icon, live
// summary). Several can sit in one dashboard section, so panels of different
// heights stack without the gaps HA's row-aligned section backgrounds leave.

import { createFormEditor } from './form-editor.js';
import { SUFFIX, LABEL } from './suffix.js';
import {
  stcColor,
  stcRender,
  STC_COLOR_TEMPLATE_FIELD,
  STC_COLOR_TEMPLATE_LABEL,
  STC_COLOR_TEMPLATE_HELPER,
} from './section-title-card.js';

let helpersPromise;
function cardHelpers() {
  if (!helpersPromise) helpersPromise = window.loadCardHelpers ? window.loadCardHelpers() : Promise.reject(new Error('no card helpers'));
  return helpersPromise;
}

const PanelFields = createFormEditor({
  schema: () => [
    { name: 'title', selector: { text: {} } },
    { name: 'icon', selector: { icon: {} } },
    { name: 'color', selector: { ui_color: {} } },
    STC_COLOR_TEMPLATE_FIELD,
    { name: 'summary', selector: { template: {} } },
    { name: 'link', selector: { navigation: {} } },
    {
      type: 'expandable',
      name: '',
      title: 'Open or compact',
      flatten: true,
      schema: [
        { name: 'phone_start', selector: { select: { mode: 'dropdown', options: [{ value: 'compact', label: 'Compact (one row per card)' }, { value: 'open', label: 'Open' }] } } },
        { name: 'tablet_start', selector: { select: { mode: 'dropdown', options: [{ value: 'open', label: 'Open' }, { value: 'compact', label: 'Compact (one row per card)' }] } } },
        { name: 'open_when', selector: { template: {} } },
        { name: 'collapsible', selector: { boolean: {} }, default: true },
      ],
    },
    {
      type: 'expandable',
      name: '',
      title: 'Layout on wider screens',
      flatten: true,
      schema: [
        { name: 'card_width', selector: { number: { min: 0, max: 800, step: 10, mode: 'box', unit_of_measurement: 'px' } } },
        { name: 'match_height', selector: { boolean: {} }, default: true },
      ],
    },
  ],
  labels: {
    title: 'Title',
    icon: 'Icon (optional)',
    link: 'Tapping the title opens (optional page)',
    phone_start: 'On phones, starts',
    tablet_start: 'On tablets and computers, starts',
    open_when: 'Opens by itself when (optional template)',
    collapsible: 'Show the ⌄ to switch between open and compact',
    card_width: 'Cards side by side when each can be at least (0 = always one per row)',
    match_height: "Line up this panel's bottom with the panels beside it",
    color: 'Colour (icon and panel)',
    color_template: STC_COLOR_TEMPLATE_LABEL,
    summary: 'Summary on the right (optional template)',
  },
  helpers: {
    phone_start: 'Each phone or tablet remembers what you last chose with the ⌄; this is where it starts. Phones are screens under 600px wide.',
    open_when: "E.g. {{ is_state('binary_sensor.back_door', 'on') }}. The panel opens while it's true, then goes back to how you left it.",
    card_width: 'Default 300px. Cards fill the panel width: e.g. cameras 2 or 3 across on a tablet, one per row on a phone.',
    match_height: "When sections sit side by side, the last panel in a shorter section grows so its bottom lines up with its neighbours'.",
    color_template: STC_COLOR_TEMPLATE_HELPER,
    summary: `A Home Assistant template, e.g. {{ states('vacuum.gregg') | title }}`,
  },
});

// The panel's own fields, then HA's stack editor for the cards inside it.
export class SectionPanelCardEditor extends HTMLElement {
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
    this.dispatchEvent(new CustomEvent('config-changed', { detail: { config }, bubbles: true, composed: true }));
  }

  async _render() {
    if (!this._config || !this._hass) return;
    if (!this._fields) {
      this._fields = document.createElement(`section-panel-fields${SUFFIX}`);
      this._fields.addEventListener('config-changed', (ev) => {
        ev.stopPropagation();
        const { cards, ...fields } = ev.detail.config;
        this._emit({ ...this._config, ...fields, cards: this._config.cards || [] });
      });
      const label = document.createElement('div');
      label.textContent = 'Cards in this panel';
      label.style.cssText = 'margin:20px 0 8px; font-weight:500;';
      this.append(this._fields, label);
    }
    const { cards, ...fields } = this._config;
    this._fields.hass = this._hass;
    this._fields.setConfig(fields);
    if (!this._stack && !this._stackLoading) {
      this._stackLoading = true;
      try {
        const helpers = await cardHelpers();
        helpers.createCardElement({ type: 'vertical-stack', cards: [] });
        await customElements.whenDefined('hui-vertical-stack-card');
        this._stack = await customElements.get('hui-vertical-stack-card').getConfigElement();
        this._stack.addEventListener('config-changed', (ev) => {
          ev.stopPropagation();
          this._emit({ ...this._config, cards: ev.detail.config.cards || [] });
        });
        this.appendChild(this._stack);
      } catch (err) {
        const note = document.createElement('p');
        note.textContent = 'The card list editor could not load; use the code editor to change the cards.';
        this.appendChild(note);
      }
      this._stackLoading = false;
    }
    if (this._stack) {
      this._stack.hass = this._hass;
      if (this._lovelace) this._stack.lovelace = this._lovelace;
      this._stack.setConfig({ type: 'vertical-stack', cards: this._config.cards || [] });
    }
  }
}

export class SectionPanelCard extends HTMLElement {
  setConfig(config) {
    if (!config.title) throw new Error('title required');
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
      this._watchOpenWhen();
      this._apply(); // now that we know who's signed in
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
    return window.innerWidth < 600 ? 'phone' : 'tablet';
  }

  _key() {
    // Per signed-in person too, so people sharing a tablet each keep their own.
    const user = (this._hass && this._hass.user && this._hass.user.id) || 'anyone';
    return `cd-panel:${user}:${location.pathname}:${this.config.title}:${this._device()}`;
  }

  _chosen() {
    try {
      const v = localStorage.getItem(this._key());
      if (v === 'open' || v === 'compact') return v;
    } catch (err) {
      /* storage blocked: use the default */
    }
    const start = this._device() === 'phone' ? this.config.phone_start || 'compact' : this.config.tablet_start || 'open';
    return start === 'compact' ? 'compact' : 'open';
  }

  _choose(mode) {
    try {
      localStorage.setItem(this._key(), mode);
    } catch (err) {
      /* storage blocked: still switch for now */
      this._fallback = mode;
    }
    this._apply();
  }

  _mode() {
    if (!this._collapsible || this._alert || this._editing()) return 'open';
    return this._fallback || this._chosen();
  }

  _editing() {
    return !!(this.editMode || this.preview);
  }

  _apply() {
    const compact = this._mode() === 'compact';
    if (this._title && this._title.setOpen) this._title.setOpen(!compact);
    (this._cards || []).forEach((card) => {
      if (card.supportsCompact) {
        card.style.display = '';
        card.compact = compact;
      } else {
        // Cards without a one-row version wait until the panel opens.
        card.style.display = compact ? 'none' : '';
      }
    });
    if (this._panelEl) this._panelEl.style.gap = compact ? '8px' : '12px';
    this._layoutGrid(compact);
    // Let the panels beside this one line up again too.
    window.dispatchEvent(new CustomEvent('cd-panels-changed'));
  }

  // Cards side by side when each can be at least card_width wide.
  _layoutGrid(compact) {
    const g = this._grid;
    if (!g) return;
    const w = this.config.card_width == null || this.config.card_width === '' ? 300 : Number(this.config.card_width);
    g.style.display = 'grid';
    g.style.gap = compact ? '8px' : '12px';
    // Cards on the same row share a height; each card's background fills it.
    g.style.alignItems = w > 0 ? 'stretch' : 'start';
    if (!this.querySelector('style.spc-fill')) {
      const st = document.createElement('style');
      st.className = 'spc-fill';
      st.textContent = '.spc-cards > * { display:flex; flex-direction:column; min-width:0; } .spc-cards > * > ha-card { flex:1 1 auto; }';
      this.prepend(st);
    }
    g.style.gridTemplateColumns = w > 0 ? `repeat(auto-fill, minmax(min(100%, ${w}px), 1fr))` : '1fr';
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
    if (!panel || !this.isConnected) return;
    const clear = () => {
      if (panel.style.minHeight) panel.style.minHeight = '';
    };
    // A compact panel keeps its own (short) height and isn't lined up.
    if (this.config.match_height === false || window.innerWidth < 600 || this._mode() === 'compact') return clear();
    const up = (el) => el.parentNode || (el.getRootNode && el.getRootNode().host) || null;
    let section = this;
    for (let i = 0; section && i < 14 && section.localName !== 'hui-section'; i += 1) section = up(section);
    if (!section) return clear();
    const PANEL = 'section-panel-card, section-panel-card-beta';
    const deep = (root, sel, depth = 0, out = []) => {
      if (!root || depth > 5 || !root.querySelectorAll) return out;
      root.querySelectorAll(sel).forEach((el) => out.push(el));
      root.querySelectorAll('*').forEach((el) => el.shadowRoot && deep(el.shadowRoot, sel, depth + 1, out));
      return out;
    };
    // Every section of the page lives in the same shadow root.
    const top = (el) => el.getBoundingClientRect().top;
    const myTop = top(section);
    const row = [...section.getRootNode().querySelectorAll('hui-section')].filter((el) => Math.abs(top(el) - myTop) < 4);
    if (row.length < 2) return clear();
    const itemsOf = (sec) =>
      deep(sec, 'hui-card').map((el) => {
        const p = el.matches && el.matches(PANEL) ? el : el.querySelector(PANEL) || (el.shadowRoot && el.shadowRoot.querySelector(PANEL)) || null;
        const r = el.getBoundingClientRect();
        const stretch = !!(p && p._naturalHeight && !(p._mode && p._mode() === 'compact') && p.config && p.config.match_height !== false);
        return { el, panel: p, stretch, top: r.top, bottom: r.bottom, h: p && p._naturalHeight ? p._naturalHeight() : r.height };
      }).filter((it) => it.bottom > it.top);
    const secs = row.map((sec) => ({ sec, items: itemsOf(sec) })).filter((x) => x.items.length);
    if (secs.length < 2) return clear();
    const m = Math.min(...secs.map((x) => x.items.length));
    const shared = [];
    for (let k = 0; k < m - 1; k += 1) shared[k] = Math.max(0, ...secs.map((x) => x.items[k]).filter((it) => it.stretch).map((it) => it.h));
    secs.forEach((x) => {
      x.heights = x.items.map((it, k) => (k < m - 1 && it.stretch ? Math.max(it.h, shared[k]) : it.h));
      const gaps = x.items.slice(1).reduce((sum, it, k) => sum + Math.max(0, it.top - x.items[k].bottom), 0);
      x.end = x.items[0].top + x.heights.reduce((a, b) => a + b, 0) + gaps;
    });
    const end = Math.max(...secs.map((x) => x.end));
    let target = null;
    secs.forEach((x) => {
      // Only an open last panel fills to the common bottom; a section ending
      // in a compact panel just ends where it ends.
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
    const g = this._grid && this._grid.getBoundingClientRect();
    return g && panel ? g.bottom + 12 - panel.getBoundingClientRect().top : this.getBoundingClientRect().height;
  }

  _watchOpenWhen() {
    if (this._unsubOpen) this._unsubOpen.then((u) => u && u()).catch(() => {});
    this._unsubOpen = null;
    this._alert = false;
    if (!this.config.open_when || !this._hass || !this.isConnected) return;
    this._unsubOpen = stcRender(this._hass, this.config.open_when, (text) => {
      const t = String(text || '').trim().toLowerCase();
      const alert = !!t && !['0', 'false', 'off', 'no', 'none', 'unknown', 'unavailable'].includes(t);
      if (alert === this._alert) return;
      this._alert = alert;
      this._apply();
    });
  }

  _build() {
    const c = this.config;
    const color = stcColor(c.color);
    this.innerHTML = `
      <div class="spc-panel" style="position:relative; box-sizing:border-box; border-radius:24px; padding:12px; display:flex; flex-direction:column; gap:12px; isolation:isolate;">
        <div class="spc-bg" style="position:absolute; inset:0; border-radius:inherit; background:${color}; opacity:0.1; z-index:-1; pointer-events:none; transition:background-color .6s ease;"></div>
      </div>`;
    const panel = this.querySelector('.spc-panel');
    this._panelEl = panel;
    panel.addEventListener('stc-toggle', (ev) => {
      ev.stopPropagation();
      this._fallback = null;
      this._choose(this._mode() === 'compact' ? 'open' : 'compact');
    });
    // Tapping a compact card's name opens the panel.
    panel.addEventListener('cd-expand', (ev) => {
      ev.stopPropagation();
      this._fallback = null;
      this._choose('open');
    });
    // A colour template on the title recolours the panel as it changes.
    const bg = this.querySelector('.spc-bg');
    panel.addEventListener('stc-color', (ev) => {
      ev.stopPropagation();
      bg.style.background = ev.detail;
    });
    this._title = document.createElement(`section-title-card${SUFFIX}`);
    this._grid = document.createElement('div');
    this._grid.className = 'spc-cards';
    this._title.setConfig({ title: c.title, icon: c.icon, color: c.color, color_template: c.color_template, summary: c.summary, link: c.link, collapsible: this._collapsible });
    if (this._hass) this._title.hass = this._hass;
    panel.appendChild(this._title);
    panel.appendChild(this._grid);
    this._layoutGrid(false);
    const token = (this._token = {});
    this._cards = [];
    cardHelpers()
      .then((helpers) => {
        if (token !== this._token) return;
        (c.cards || []).forEach((conf) => {
          const el = helpers.createCardElement(conf);
          if (this._hass) el.hass = this._hass;
          // A card that fails to load (e.g. a custom card still downloading)
          // asks to be rebuilt.
          el.addEventListener('ll-rebuild', (ev) => {
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
      })
      .catch(() => {});
  }

  connectedCallback() {
    // Wait for HA to finish placing the card before looking at its neighbours.
    requestAnimationFrame(() => this._spaceFromAbove());
    this._onResize = () => {
      const d = this._device();
      if (d !== this._lastDevice) {
        this._lastDevice = d;
        this._apply();
      }
    };
    this._lastDevice = this._device();
    window.addEventListener('resize', this._onResize);
    this._onPanels = () => this._queueMatch();
    window.addEventListener('cd-panels-changed', this._onPanels);
    // Heights change as cards load, open, go compact or update.
    if (window.ResizeObserver && !this._ro) {
      this._ro = new ResizeObserver(() => this._queueMatch());
      this._ro.observe(document.body);
      this._matchTimer = setInterval(() => this._queueMatch(), 3000);
    }
    if (this._hass) this._watchOpenWhen();
    this._apply();
  }

  disconnectedCallback() {
    window.removeEventListener('resize', this._onResize);
    window.removeEventListener('cd-panels-changed', this._onPanels);
    if (this._ro) this._ro.disconnect();
    this._ro = null;
    clearInterval(this._matchTimer);
    if (this._unsubOpen) this._unsubOpen.then((u) => u && u()).catch(() => {});
    this._unsubOpen = null;
  }

  // A panel right under another panel in the same section gets the same gap
  // as between section columns (32px; the section's own gap between cards is
  // 8px), so stacked panels read as separate groups.
  _spaceFromAbove() {
    const up = (el) => el.parentNode || (el.getRootNode && el.getRootNode().host) || null;
    let wrap = this;
    for (let i = 0; i < 6 && wrap && wrap.localName !== 'hui-card'; i += 1) wrap = up(wrap);
    let prev = null;
    for (let i = 0; i < 3 && wrap && !prev; i += 1) {
      prev = wrap.previousElementSibling;
      wrap = up(wrap);
    }
    const prevCard = prev && (prev.localName === 'hui-card' ? prev : prev.querySelector && prev.querySelector('hui-card'));
    const type = prevCard && prevCard.config && String(prevCard.config.type || '');
    const stacked = !!type && type.includes('section-panel-card');
    this.style.display = 'block';
    this.style.marginTop = stacked ? 'calc(var(--ha-view-sections-column-gap, 32px) - 8px)' : '';
  }

  getCardSize() {
    return 1 + (this._cards || []).reduce((n, card) => n + (card.getCardSize ? Number(card.getCardSize()) || 1 : 1), 0);
  }

  getGridOptions() {
    return { columns: 'full', rows: 'auto' };
  }

  static getConfigElement() {
    return document.createElement(`section-panel-card-editor${SUFFIX}`);
  }

  static getStubConfig() {
    return { title: 'Lights', icon: 'mdi:lightbulb', color: 'amber', cards: [] };
  }
}

export function registerSectionPanelCard() {
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
    description: 'A group of cards on a coloured panel with a large title, icon and live summary',
    preview: false,
    documentationURL: 'https://github.com/J45PER/church-drive-cards#readme',
  });
}
