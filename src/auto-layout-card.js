// Auto Layout Card: holds a page's panels in one list and arranges them
// itself. It uses as many columns as fit, splits the panels into columns of
// about equal height (keeping their order: down the first column, then the
// next), and stretches each column's last open panel so the columns end
// level. A panel marked "full width" sits across the page, with the panels
// before and after it balanced above and below. Adding a panel, opening or
// compacting one, or turning the tablet just rebalances the page.

import { createFormEditor } from './form-editor.js';
import { SUFFIX, LABEL } from './suffix.js';

let helpersPromise;
function cardHelpers() {
  if (!helpersPromise) helpersPromise = window.loadCardHelpers ? window.loadCardHelpers() : Promise.reject(new Error('no card helpers'));
  return helpersPromise;
}

const PANEL = /section-panel-card/;
const GAP = 'var(--ha-view-sections-column-gap, 32px)';

const LayoutFields = createFormEditor({
  schema: () => [
    { name: 'column_width', selector: { number: { min: 200, max: 800, step: 10, mode: 'box', unit_of_measurement: 'px' } } },
    { name: 'max_columns', selector: { number: { min: 1, max: 6, step: 1, mode: 'box' } } },
  ],
  labels: {
    column_width: 'Columns at least this wide',
    max_columns: 'At most this many columns',
  },
  helpers: {
    column_width: 'Default 340px. Phones (under 600px) always get one column in list order.',
    max_columns: 'Default 3. Mark a panel "Full width across an Auto Layout" to have it span the page.',
  },
});

// Split heights (in order) into k runs, making the tallest run as short as
// possible. Returns the index where each run starts.
export function balance(heights, k, gap) {
  const n = heights.length;
  k = Math.max(1, Math.min(k, n));
  const pre = [0];
  heights.forEach((h, i) => pre.push(pre[i] + h));
  const run = (i, j) => pre[j] - pre[i] + gap * (j - i - 1);
  const best = Array.from({ length: k + 1 }, () => Array(n + 1).fill(Infinity));
  const cut = Array.from({ length: k + 1 }, () => Array(n + 1).fill(0));
  best[0][0] = 0;
  for (let c = 1; c <= k; c += 1) {
    for (let j = c; j <= n; j += 1) {
      for (let i = c - 1; i < j; i += 1) {
        const v = Math.max(best[c - 1][i], run(i, j));
        // Ties go to the later cut, so earlier columns hold more.
        if (v <= best[c][j] + 0.5) {
          best[c][j] = Math.min(v, best[c][j]);
          cut[c][j] = i;
        }
      }
    }
  }
  const starts = [];
  let j = n;
  for (let c = k; c > 0; c -= 1) {
    starts.unshift(cut[c][j]);
    j = cut[c][j];
  }
  return starts;
}

export class AutoLayoutCardEditor extends HTMLElement {
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
      this._fields = document.createElement(`auto-layout-fields${SUFFIX}`);
      this._fields.addEventListener('config-changed', (ev) => {
        ev.stopPropagation();
        const { cards, ...fields } = ev.detail.config;
        this._emit({ ...this._config, ...fields, cards: this._config.cards || [] });
      });
      const label = document.createElement('div');
      label.textContent = 'Panels, in order (phones show them top to bottom in this order)';
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
        note.textContent = 'The card list editor could not load; use the code editor to change the panels.';
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

export class AutoLayoutCard extends HTMLElement {
  setConfig(config) {
    if (!Array.isArray(config.cards)) throw new Error('cards required');
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
    this.style.display = 'block';
    this.innerHTML = '';
    this._root = document.createElement('div');
    this._root.style.cssText = `display:flex; flex-direction:column; gap:${GAP};`;
    this.appendChild(this._root);
    this._items = [];
    this._plan = '';
    const token = (this._token = {});
    cardHelpers()
      .then((helpers) => {
        if (token !== this._token) return;
        this._items = this.config.cards.map((conf) => this._make(helpers, conf));
        this._layout(true);
      })
      .catch(() => {});
  }

  _make(helpers, conf) {
    const el = helpers.createCardElement(conf);
    // Panels inside leave lining up and spacing to this card.
    el._managed = true;
    if (this._hass) el.hass = this._hass;
    const it = { conf, el, full: !!conf.full_width };
    el.addEventListener('ll-rebuild', (ev) => {
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
    const v = parseFloat(getComputedStyle(this).getPropertyValue('--ha-view-sections-column-gap'));
    return Number.isFinite(v) ? v : 32;
  }

  _open(el) {
    return PANEL.test(el.localName) && !(el._mode && el._mode() === 'compact') && !(el.config && el.config.match_height === false);
  }

  // Height of an item without any stretch.
  _height(el) {
    if (el._naturalHeight) return el._naturalHeight();
    return el.getBoundingClientRect().height;
  }

  _queue() {
    cancelAnimationFrame(this._frame);
    this._frame = requestAnimationFrame(() => this._layout(false));
  }

  // Work out bands (split at full-width items) and columns; only move cards
  // when the arrangement actually changes, so cameras etc. aren't reloaded.
  _layout(force) {
    if (!this.isConnected || !this._items.length) return;
    const cols = this._columns();
    const gap = this._gap();
    const bands = [];
    let cur = [];
    const flush = () => {
      if (cur.length) bands.push({ items: cur });
      cur = [];
    };
    this._items.forEach((it) => {
      if (it.full && cols > 1) {
        flush();
        bands.push({ items: [it], full: true });
      } else cur.push(it);
    });
    flush();
    // Unplaced cards have no height yet: place them in order first.
    const placed = this._items.every((it) => it.el.isConnected);
    bands.forEach((b) => {
      if (b.full || cols === 1 || !placed) {
        b.starts = [0];
        return;
      }
      b.starts = balance(b.items.map((it) => this._height(it.el)), cols, gap);
    });
    const plan = `${cols}|${bands.map((b) => `${b.full ? 'F' : ''}${b.items.length}:${b.starts.join(',')}`).join('/')}`;
    if (force || plan !== this._plan) {
      this._plan = plan;
      this._root.innerHTML = '';
      bands.forEach((b) => {
        const row = document.createElement('div');
        row.style.cssText = `display:flex; gap:${GAP}; align-items:stretch;`;
        b.cols = b.starts.map((s, i) => {
          const col = document.createElement('div');
          col.style.cssText = `flex:1 1 0; min-width:0; display:flex; flex-direction:column; gap:${GAP};`;
          b.items.slice(s, b.starts[i + 1] == null ? b.items.length : b.starts[i + 1]).forEach((it) => col.appendChild(it.el));
          row.appendChild(col);
          return col;
        });
        this._root.appendChild(row);
      });
      this._bands = bands;
      if (!placed) {
        this._queue();
        return;
      }
    }
    this._stretch(cols, gap);
  }

  // Each column's last open panel grows so the columns in a band end level.
  _stretch(cols, gap) {
    const set = (el, px) => {
      const p = el._panelEl;
      if (!p) return;
      const v = px ? `${Math.round(px)}px` : '';
      if (p.style.minHeight !== v) p.style.minHeight = v;
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
          set(it.el, it === grow && extra > 1 ? this._height(it.el) + extra : 0);
        });
      });
    });
  }

  connectedCallback() {
    this._onChange = () => this._queue();
    window.addEventListener('resize', this._onChange);
    window.addEventListener('cd-panels-changed', this._onChange);
    if (window.ResizeObserver && !this._ro) {
      this._ro = new ResizeObserver(() => this._queue());
      this._ro.observe(this);
      this._timer = setInterval(() => this._queue(), 3000);
    }
    this._queue();
  }

  disconnectedCallback() {
    window.removeEventListener('resize', this._onChange);
    window.removeEventListener('cd-panels-changed', this._onChange);
    if (this._ro) this._ro.disconnect();
    this._ro = null;
    clearInterval(this._timer);
  }

  getCardSize() {
    return (this._items || []).reduce((n, it) => n + (it.el.getCardSize ? Number(it.el.getCardSize()) || 1 : 1), 0);
  }

  getGridOptions() {
    return { columns: 'full', rows: 'auto' };
  }

  static getConfigElement() {
    return document.createElement(`auto-layout-card-editor${SUFFIX}`);
  }

  static getStubConfig() {
    return { cards: [] };
  }
}

export function registerAutoLayoutCard() {
  if (!customElements.get(`auto-layout-fields${SUFFIX}`)) customElements.define(`auto-layout-fields${SUFFIX}`, LayoutFields);
  if (!customElements.get(`auto-layout-card-editor${SUFFIX}`)) customElements.define(`auto-layout-card-editor${SUFFIX}`, AutoLayoutCardEditor);
  if (!customElements.get(`auto-layout-card${SUFFIX}`)) customElements.define(`auto-layout-card${SUFFIX}`, AutoLayoutCard);
  window.customCards = window.customCards || [];
  window.customCards.push({
    type: `auto-layout-card${SUFFIX}`,
    name: `Auto Layout Card${LABEL}`,
    description: "Arranges a page's panels into balanced columns by itself, with level bottoms",
    preview: false,
    documentationURL: 'https://github.com/J45PER/church-drive-cards#readme',
  });
}
