// Auto Layout Card: holds a page's panels in one list and arranges them
// itself. It uses as many columns as fit, shares the panels between columns
// so they're as even as possible (each column keeps list order), and
// stretches each column's last open panel a little so the columns end
// level. A panel marked "full width" sits across the page, with the panels
// before and after it balanced above and below. Adding a panel, opening or
// compacting one, or turning the tablet just rebalances the page.

import { createFormEditor } from './form-editor.js';
import { SUFFIX, LABEL } from './suffix.js';
import { iconHtml, hydrateIcons } from './icons.js';
import { stcColor } from './section-title-card.js';
import { nbHuiRoot } from './nav-bar-card.js';
import { kitScrollParent, kitGlide } from './card-kit.js';

let helpersPromise;
function cardHelpers() {
  if (!helpersPromise) helpersPromise = window.loadCardHelpers ? window.loadCardHelpers() : Promise.reject(new Error('no card helpers'));
  return helpersPromise;
}

const PANEL = /section-panel-card/;

// Cards whose main job is buttons or sliders; everything else (cameras, room
// temperatures, zone status, entity lists) counts as information.
const CONTROL_TYPES = [
  'alarm-panel-card', 'light-control-card', 'climate-card', 'fan-card', 'air-purifier-card', 'cover-card',
  'scene-styles-card', 'scene-builder-card', 'thermostat', 'humidifier', 'light', 'button', 'media-control', 'alarm-panel', 'area',
];

// Whether a card config (or anything inside it) has controls. A panel can
// say so itself with `priority: controls` or `priority: info`.
export function hasControls(conf) {
  if (!conf || typeof conf !== 'object') return false;
  if (conf.priority === 'controls') return true;
  if (conf.priority === 'info') return false;
  const type = String(conf.type || '').replace(/^custom:/, '').replace(/-beta$/, '');
  if (CONTROL_TYPES.includes(type)) return true;
  if (type === 'tile' && Array.isArray(conf.features) && conf.features.length) return true;
  const kids = [].concat(conf.cards || [], conf.card ? [conf.card] : []);
  return kids.some((k) => hasControls(k));
}
const GAP = 'var(--ha-view-sections-column-gap, 32px)';

// Each page's last arrangement, so coming back to a page (or the app waking
// up) shows it straight away instead of in list order and then rearranging.
const PLAN_KEY = 'cd-layout-plans';
let plans = null;
function planStore() {
  if (plans) return plans;
  plans = {};
  try {
    plans = JSON.parse(localStorage.getItem(PLAN_KEY) || '{}') || {};
  } catch (err) {
    /* storage blocked or bad data */
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
    /* storage full or blocked */
  }
}

const LayoutFields = createFormEditor({
  schema: () => [
    { name: 'title', selector: { text: {} } },
    { name: 'column_width', selector: { number: { min: 200, max: 800, step: 10, mode: 'box', unit_of_measurement: 'px' } } },
    { name: 'max_columns', selector: { number: { min: 1, max: 6, step: 1, mode: 'box' } } },
    { name: 'controls_first', selector: { boolean: {} }, default: true },
    { name: 'jump_chips', selector: { select: { mode: 'dropdown', options: [{ value: 'auto', label: 'Automatic (phones, 4+ panels)' }, { value: 'always', label: 'Always' }, { value: 'never', label: 'Never' }] } } },
  ],
  labels: {
    title: 'Page title (optional)',
    column_width: 'Columns at least this wide',
    max_columns: 'At most this many columns',
    controls_first: 'Panels with buttons and sliders go above ones that only show information',
    jump_chips: 'Jump-to chips at the top',
  },
  helpers: {
    title: "Shown large at the top of the page, above the chips. Any panel that has opened by itself (its 'opens by itself when' is true) shows under it as an alert; tapping one goes to that panel.",
    column_width: 'Default 340px. Phones (under 600px) always get one column in list order.',
    max_columns: 'Default 3. Mark a panel "Full width across an Auto Layout" to have it span the page.',
    controls_first: 'Keeps list order otherwise, on phones too. Each panel can override what it counts as ("Counts as" in the panel).',
    jump_chips: "One chip per panel, in its colour; tapping one scrolls to that panel, and the chip for the panel you're looking at is filled in.",
  },
});

const boxHeight = (el) => (el ? Math.round(el.getBoundingClientRect().height) : 0);

// Bottom of Home Assistant's top bar, so floating chips sit just under it.
function headerBottom() {
  const root = nbHuiRoot();
  const sr = root && root.shadowRoot;
  const bar = sr && (sr.querySelector('.header') || sr.querySelector('app-header') || sr.querySelector('app-toolbar'));
  const r = bar && bar.getBoundingClientRect();
  return r && r.height ? Math.max(0, r.bottom) : 56;
}

// Share items between k columns so the tallest column is as short as
// possible. Each column keeps list order; column 1 starts with the first
// item. Tries every sharing for small pages (ties go to the one closest to
// list order), and falls back to "next item into the shortest column".
// Returns an array of columns, each a list of item indexes.
export function balance(heights, k, gap, keep) {
  const n = heights.length;
  k = Math.max(1, Math.min(k, n));
  const cost = (cols) => Math.max(...cols.map((c) => c.reduce((s, i) => s + heights[i], 0) + gap * Math.max(0, c.length - 1)));
  let best = null;
  let bestCost = Infinity;
  if (n <= 11) {
    // Restricted-growth labelling: each item joins a used column or opens
    // the next one, so each sharing is tried once.
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
  // Keep the current arrangement unless the new one is clearly better, so
  // small height changes don't shuffle the page.
  if (keep && keep.length === k && keep.flat().length === n && cost(keep) <= cost(cols) + 24) return keep;
  return cols;
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
    // Jump-to chips float under the header (a capsule on document.body, like
    // the nav bar, since HA's card wrappers stop position:sticky working);
    // the spacer keeps room for them at the top of the page.
    if (this._chips) this._chips.remove();
    this._chips = document.createElement('div');
    this._chips.className = 'al-chips';
    this._chips.style.cssText =
      'display:none; gap:6px; overflow-x:auto; padding:6px; border-radius:999px; scrollbar-width:none; box-sizing:border-box; z-index:4;' +
      'background:color-mix(in srgb, var(--card-background-color, #1f2128) 88%, transparent); box-shadow:0 6px 18px rgba(0,0,0,.45), inset 0 0 0 1px rgba(255,255,255,.06);' +
      '-webkit-backdrop-filter:blur(12px); backdrop-filter:blur(12px); transition:opacity .2s;';
    this._chips.addEventListener('click', (ev) => {
      const chip = ev.target.closest && ev.target.closest('[data-i]');
      const it = chip && this._items[Number(chip.dataset.i)];
      if (!it) return;
      this._jumpTo(it);
      this._current = Number(chip.dataset.i);
      this._chipsSig = null;
      this._renderChips();
    });
    this._spacer = document.createElement('div');
    this._spacer.style.cssText = 'display:none;';
    this._root = document.createElement('div');
    this._root.style.cssText = `display:flex; flex-direction:column; gap:${GAP};`;
    // Room at the end of the page, only while a chip jump needs it, so even
    // the last panels can come up under the chips.
    this._tail = document.createElement('div');
    this._tail.style.cssText = 'height:0;';
    // The page header: title, then alerts from panels that opened by themselves.
    this._head = document.createElement('div');
    this._head.className = 'al-head';
    this._head.style.cssText = 'display:none; flex-direction:column; align-items:center; gap:10px; padding:4px 4px 16px;';
    this._head.addEventListener('click', (ev) => {
      const pill = ev.target.closest && ev.target.closest('[data-alert]');
      const it = pill && this._items[Number(pill.dataset.alert)];
      if (it) this._jumpTo(it);
    });
    this.append(this._head, this._spacer, this._root, this._tail);
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
    const it = { conf, el, controls: hasControls(conf) };
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

  // Full width: set on the panel, or automatic for a panel of 3+ small cards
  // (zones, cameras, tiles) that won't fit side by side in one column.
  _full(it, colWidth) {
    const f = it.conf.full_width;
    if (f === true || f === 'yes') return true;
    if (f === false || f === 'no') return false;
    const cards = it.conf.cards || [];
    const w = it.el._cardWidth ? it.el._cardWidth() : 300;
    if (cards.length < 3 || !w || w > 240) return false;
    const across = Math.max(1, Math.floor((colWidth - 24 + 12) / (w + 12)));
    return cards.length > across;
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
    // Wait while a panel slides open or shut; lay out once when it's done.
    if (this._animating > 0) {
      this._pending = true;
      return;
    }
    cancelAnimationFrame(this._frame);
    this._frame = requestAnimationFrame(() => this._layout(false));
  }

  // Work out bands (split at full-width items) and columns; only move cards
  // when the arrangement actually changes, so cameras etc. aren't reloaded.
  _layout(force) {
    if (!this.isConnected || !this._items.length) return;
    const cols = this._columns();
    const gap = this._gap();
    // Balanced columns first, then full-width panels across the page below
    // them (in list order), so a full-width panel never strands one panel on
    // its own row.
    const colWidth = ((this.getBoundingClientRect().width || window.innerWidth) - gap * (cols - 1)) / cols;
    const wide = cols > 1 ? this._items.filter((it) => this._full(it, colWidth)) : [];
    const rest = this._items.filter((it) => !wide.includes(it));
    const bands = [];
    if (rest.length) bands.push({ items: rest });
    const first = this.config.controls_first !== false;
    [...wide.filter((it) => !first || it.controls), ...wide.filter((it) => first && !it.controls)].forEach((it) => bands.push({ items: [it], full: true }));
    // Panels with controls first within each band (stable, so list order
    // holds otherwise).
    if (this.config.controls_first !== false) {
      bands.forEach((b) => {
        b.items = [...b.items.filter((it) => it.controls), ...b.items.filter((it) => !it.controls)];
      });
    }
    // Unplaced cards have no height yet: use this page's last arrangement,
    // or list order, until they can be measured.
    const placed = this._items.every((it) => it.el.isConnected);
    const planKey = `${location.pathname}|${cols}|${bands.map((b) => b.items.map((it) => it.conf.title || it.conf.type).join(',')).join('/')}`;
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
      const key = b.items.map((it) => this._items.indexOf(it)).join(',');
      const prev = this._prevSplits && this._prevSplits[key];
      // Once the page has settled, panels keep their columns: opening or
      // closing one only re-levels the bottoms, it never moves panels about.
      const settled = performance.now() > this._settleUntil && cols === this._cols;
      b.split = settled && prev && prev.length === Math.min(cols, b.items.length) ? prev : balance(b.items.map((it) => this._height(it.el)), cols, gap, prev);
    });
    this._prevSplits = {};
    bands.forEach((b) => {
      this._prevSplits[b.items.map((it) => this._items.indexOf(it)).join(',')] = b.split;
    });
    if (placed) planRemember(planKey, bands.map((b) => b.split));
    const plan = `${cols}|${bands.map((b) => `${b.full ? 'F' : ''}${b.split.map((c) => c.join('.')).join(',')}`).join('/')}`;
    if (force || plan !== this._plan) {
      this._plan = plan;
      this._root.innerHTML = '';
      bands.forEach((b) => {
        const row = document.createElement('div');
        row.style.cssText = `display:flex; gap:${GAP}; align-items:stretch;`;
        b.cols = b.split.map((idx) => {
          const col = document.createElement('div');
          col.style.cssText = `flex:1 1 0; min-width:0; display:flex; flex-direction:column; gap:${GAP};`;
          idx.forEach((i) => col.appendChild(b.items[i].el));
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
    const esc = (t) => String(t).replace(/[&<>"]/g, (ch) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[ch]);
    const alerts = (this._items || [])
      .map((it, i) => ({ it, i }))
      .filter(({ it }) => it.el._alert && it.conf.title)
      .map(({ it, i }) => {
        const bg = it.el.querySelector && it.el.querySelector('.spc-bg');
        const colour = (bg && bg.style.background) || stcColor(it.conf.color || 'primary');
        const summary = it.el._title && it.el._title._summary;
        return { i, colour, icon: it.conf.icon, text: summary ? `${it.conf.title} · ${summary}` : it.conf.title };
      });
    const title = this.config.title || '';
    const sig = JSON.stringify([title, alerts]);
    if (sig === this._headSig) return;
    this._headSig = sig;
    if (!title && !alerts.length) {
      head.style.display = 'none';
      head.innerHTML = '';
      return;
    }
    head.style.display = 'flex';
    head.innerHTML = `${title ? `<div style="font-size:2rem; font-weight:700; line-height:1.2; text-align:center; color:var(--primary-text-color);">${esc(title)}</div>` : ''}${
      alerts.length
        ? `<div style="display:flex; flex-wrap:wrap; justify-content:center; gap:6px;">${alerts
            .map(
              (a) => `<button type="button" data-alert="${a.i}" style="display:inline-flex; align-items:center; gap:6px; max-width:100%; padding:7px 12px; border:none; border-radius:999px; cursor:pointer; font:inherit; font-size:0.82rem; font-weight:600; color:var(--primary-text-color); background:color-mix(in srgb, ${a.colour} 26%, var(--card-background-color, #22252e)); box-shadow:inset 0 0 0 1px color-mix(in srgb, ${a.colour} 55%, transparent);">${
                a.icon ? iconHtml(a.icon, { size: '18px', style: `color:${a.colour}; flex:none;` }) : ''
              }<span style="white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">${esc(a.text)}</span></button>`
            )
            .join('')}</div>`
        : ''
    }`;
    hydrateIcons(head);
  }

  // ---- Jump-to chips: one per panel, in page order, in the panel's colour.
  _chipsWanted() {
    const mode = this.config.jump_chips || 'auto';
    if (mode === 'never') return false;
    const panels = (this._items || []).filter((it) => it.conf.title);
    if (mode === 'always') return panels.length > 1;
    return this._cols === 1 && panels.length >= 4;
  }

  // Panels in the order they appear on the page (top to bottom, left to right).
  _pageOrder() {
    return (this._items || [])
      .map((it, i) => ({ it, i, r: it.el.getBoundingClientRect() }))
      .filter((x) => x.it.conf.title && x.r.height > 0)
      .sort((a, b) => a.r.top - b.r.top || a.r.left - b.r.left);
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
      Object.assign(box.style, { position: 'relative', top: '', left: '', width: '', marginBottom: '12px' });
      spacer.style.display = 'none';
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
        position: 'fixed',
        top: `${pinAt}px`,
        left: `${Math.round(r.left)}px`,
        width: `${Math.round(r.width)}px`,
        marginBottom: '',
        opacity: r.width ? '1' : '0',
        pointerEvents: r.width ? 'auto' : 'none',
      });
    } else {
      if (box.parentNode !== spacer) spacer.appendChild(box);
      Object.assign(box.style, { position: 'absolute', top: '0', left: '0', width: '100%', marginBottom: '', opacity: '1', pointerEvents: 'auto' });
    }
  }

  // Scroll a panel up to just under the chips, opening it if it's compact
  // (for this visit only; the saved open/compact choice doesn't change).
  // The panel a previous jump opened closes again, unless it's been touched.
  _jumpTo(it) {
    const el = it.el;
    const prev = this._closeJumped(el);
    if (el._mode && el._mode() === 'compact' && el._slide && el._apply) {
      el._fallback = 'open';
      el._slide(() => el._apply());
      this._jumpOpened = el;
    }
    // Aim for just under the chips where they'll be once pinned.
    const top = this._pinnedChipsBottom() + 10;
    const r = el.getBoundingClientRect();
    let below = this._root.getBoundingClientRect().bottom - r.top;
    // A panel closing further down makes the page shorter: allow for it.
    if (prev && prev.getBoundingClientRect().top > r.top) below -= prev.getBoundingClientRect().height;
    const need = Math.max(0, Math.ceil(window.innerHeight - top - below));
    this._tail.style.height = `${need}px`;
    this._jump = { el, top, arrived: false, since: performance.now() };
    kitGlide(kitScrollParent(this), () => el.getBoundingClientRect().top - top);
  }

  // Close the panel the last chip jump opened, if it's still only open for
  // that jump. Returns it.
  _closeJumped(keep) {
    const el = this._jumpOpened;
    this._jumpOpened = null;
    if (!el || el === keep || el._fallback !== 'open' || !el._slide) return null;
    el._fallback = null;
    el._slide(() => el._apply());
    return el;
  }

  // Drop the extra room once the jump has landed and you scroll back up
  // away from that panel (or if the jump never lands).
  _trimTail() {
    const j = this._jump;
    if (!j) return;
    const at = j.el.getBoundingClientRect().top;
    if (Math.abs(at - j.top) < 8) j.arrived = true;
    const leftIt = j.arrived && at > j.top + 40;
    if (leftIt || (!j.arrived && performance.now() - j.since > 3000)) {
      this._tail.style.height = '0px';
      this._jump = null;
    }
  }

  _pinnedChipsBottom() {
    const box = this._chips;
    return box && box.style.display !== 'none' && this._chipsFloat() ? headerBottom() + 8 + boxHeight(box) : this._chipsBottom();
  }

  _chipsBottom() {
    const box = this._chips;
    return box && box.style.display !== 'none' ? box.getBoundingClientRect().bottom : headerBottom();
  }

  _renderChips() {
    const box = this._chips;
    if (!box) return;
    if (!this._chipsWanted()) {
      box.style.display = 'none';
      if (this._spacer) this._spacer.style.display = 'none';
      return;
    }
    box.style.display = 'flex';
    this._placeChips();
    const order = this._pageOrder();
    if (this._current == null && order.length) this._current = order[0].i;
    const chips = order.map(({ it, i }) => {
      const bg = it.el.querySelector && it.el.querySelector('.spc-bg');
      const colour = (bg && bg.style.background) || stcColor(it.conf.color || 'primary');
      return { i, title: it.conf.title, icon: it.conf.icon, colour, on: i === this._current };
    });
    const sig = JSON.stringify(chips);
    if (sig === this._chipsSig) return;
    this._chipsSig = sig;
    box.innerHTML = chips
      .map(
        (c) => `<button type="button" data-i="${c.i}" style="flex:none; display:inline-flex; align-items:center; gap:5px; padding:6px 11px; border:none; border-radius:999px; cursor:pointer; font:inherit; font-size:0.78rem; font-weight:600; color:${c.on ? '#fff' : 'var(--primary-text-color)'}; background:${c.on ? c.colour : `color-mix(in srgb, ${c.colour} 18%, var(--card-background-color, #22252e))`};">${
          c.icon ? iconHtml(c.icon, { size: '16px', style: `color:${c.on ? '#fff' : c.colour};` }) : ''
        }${String(c.title).replace(/[&<>"]/g, (ch) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[ch])}</button>`
      )
      .join('');
    hydrateIcons(box);
    const on = box.querySelector(`[data-i="${this._current}"]`);
    if (on && on.scrollIntoView) box.scrollLeft = Math.max(0, on.offsetLeft - 24);
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
    // Cheap when nothing changed: the chips only redraw when their look does.
    this._renderChips();
  }

  connectedCallback() {
    this._settleUntil = performance.now() + 4000;
    this._animating = 0;
    this._onAnim = (ev) => {
      this._animating = Math.max(0, this._animating + (Number(ev.detail) || 0));
      if (!this._animating && this._pending) {
        this._pending = false;
        this._queue();
      }
    };
    window.addEventListener('cd-anim', this._onAnim);
    // The nav bar's back-to-top also closes the panel a chip jump opened.
    this._onTop = () => this._closeJumped(null);
    window.addEventListener('cd-to-top', this._onTop);
    this._onScrollBound = () => {
      cancelAnimationFrame(this._scrollFrame);
      this._scrollFrame = requestAnimationFrame(() => this._onScroll());
    };
    window.addEventListener('scroll', this._onScrollBound, { capture: true, passive: true });
    this._onChange = () => this._queue();
    this._onResize = () => {
      this._queue();
      if (this._chips && this._chips.style.display !== 'none') this._placeChips();
    };
    window.addEventListener('resize', this._onResize);
    window.addEventListener('cd-panels-changed', this._onChange);
    if (window.ResizeObserver && !this._ro) {
      this._ro = new ResizeObserver(() => this._queue());
      this._ro.observe(this);
      this._timer = setInterval(() => this._queue(), 3000);
    }
    this._queue();
  }

  disconnectedCallback() {
    window.removeEventListener('scroll', this._onScrollBound, { capture: true });
    window.removeEventListener('resize', this._onResize);
    window.removeEventListener('cd-anim', this._onAnim);
    window.removeEventListener('cd-to-top', this._onTop);
    if (this._chips && this._chips.parentNode === document.body) this._chips.remove();
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
