// Mirror Card: shows another page's card here, so a card that's on two pages
// (the Kitchen lights on Home and on Lighting) is set up once. Edit the original
// and every mirror of it follows: its rows, scenes, names, everything.
//
// Pick it in the visual editor: the dashboard, then the page, then the card.
// The card is found again by what it's about (its area, entity or name; see
// mirror.js), so moving cards around doesn't break a mirror. A mirror reloads
// when the dashboard is saved. It shrinks with its Section Panel exactly as the
// original card would.
//
// The Android app follows mirrors too (DashboardLights.kt).

import { createFormEditor } from './form-editor.js';
import { options, resolve, findView } from './mirror.js';
import { SUFFIX, LABEL } from './suffix.js';

// "This dashboard" is whichever one the card is on; the default (Overview) dashboard has no path.
const THIS = 'this';
const currentDashboard = () => {
  const p = location.pathname.split('/')[1];
  return !p || p === 'lovelace' ? null : p;
};
const pathOf = (config) => (!config.dashboard || config.dashboard === THIS ? currentDashboard() : config.dashboard === 'lovelace' ? null : config.dashboard);

// ---- Dashboard configs: loaded once, reloaded when HA says one was saved.
const configs = new Map(); // url_path ('' = default) -> { value, promise }
const listeners = new Set();
let watching = null;

const cached = (urlPath) => (configs.get(urlPath || '') || {}).value || null;

function loadConfig(hass, urlPath, force = false) {
  const key = urlPath || '';
  const hit = configs.get(key);
  if (hit && !force) return hit.promise;
  const entry = { value: hit ? hit.value : null };
  entry.promise = hass
    .callWS({ type: 'lovelace/config', url_path: urlPath, force })
    .then((value) => {
      entry.value = value;
      return value;
    })
    .catch(() => entry.value);
  configs.set(key, entry);
  return entry.promise;
}

function watch(hass) {
  if (!hass.connection || watching === hass.connection) return;
  watching = hass.connection;
  hass.connection.subscribeEvents(async (ev) => {
    const path = (ev.data && ev.data.url_path) || null;
    if (configs.has(path || '')) await loadConfig(hass, path, true);
    listeners.forEach((fn) => fn(path));
  }, 'lovelace_updated');
}

// The other dashboards, for the editor's first list.
let dashboardList = null;
let dashboardsLoading = null;
function loadDashboards(hass) {
  if (!dashboardsLoading) {
    dashboardsLoading = hass
      .callWS({ type: 'lovelace/dashboards/list' })
      .catch(() => [])
      .then((list) => {
        dashboardList = Array.isArray(list) ? list : [];
        return dashboardList;
      });
  }
  return dashboardsLoading;
}

// ---- The card
class MirrorCard extends HTMLElement {
  setConfig(config) {
    this.config = config || {};
    this._sig = null;
    this._update();
  }

  set hass(hass) {
    this._hass = hass;
    watch(hass);
    if (this._child) this._child.hass = hass;
    this._update();
  }

  connectedCallback() {
    this._listen = (path) => {
      if (path === pathOf(this.config || {})) this._update();
    };
    listeners.add(this._listen);
  }

  disconnectedCallback() {
    listeners.delete(this._listen);
  }

  // The Section Panel shrinks cards that say they can; this one passes that on.
  get supportsCompact() {
    return true;
  }

  get compact() {
    return !!this._compact;
  }

  set compact(v) {
    this._compact = !!v;
    this._applyCompact();
  }

  _applyCompact() {
    const child = this._child;
    if (!child) return;
    if (child.supportsCompact) {
      this.style.display = '';
      child.style.display = '';
      child.compact = !!this._compact;
    } else {
      // A card without a one-row version waits until the panel opens, as in the panel itself.
      this.style.display = this._compact ? 'none' : '';
    }
  }

  async _update() {
    const config = this.config;
    if (!config || !this._hass) return;
    if (!config.view || !config.source) return this._note('Pick a card to mirror in the editor.');
    const path = pathOf(config);
    const dashboard = await loadConfig(this._hass, path);
    if (this.config !== config) return;
    const card = dashboard && resolve(dashboard, config);
    if (!card) {
      this._sig = null;
      this._child = null;
      return this._note(dashboard ? 'The card this mirrors can\'t be found. It may have been moved or renamed: pick it again in the editor.' : 'Can\'t read the dashboard to mirror a card from it.');
    }
    const sig = JSON.stringify(card);
    if (sig === this._sig) return;
    this._sig = sig;
    const helpers = await window.loadCardHelpers();
    if (this._sig !== sig) return;
    const el = helpers.createCardElement(card);
    el.hass = this._hass;
    this.replaceChildren(el);
    this._child = el;
    this._applyCompact();
    window.dispatchEvent(new CustomEvent('cd-panels-changed'));
  }

  _note(text) {
    this._child = null;
    this.innerHTML = '<ha-card style="padding:16px;color:var(--secondary-text-color);font-size:.9rem;"></ha-card>';
    this.firstChild.textContent = text;
  }

  getCardSize() {
    const size = this._child && this._child.getCardSize && this._child.getCardSize();
    return typeof size === 'number' ? size : 3;
  }

  getGridOptions() {
    return (this._child && this._child.getGridOptions && this._child.getGridOptions()) || { columns: 'full', rows: 'auto' };
  }

  static getConfigElement() {
    return document.createElement(`mirror-card-editor${SUFFIX}`);
  }

  static getStubConfig() {
    return { dashboard: THIS };
  }
}

// ---- The editor: dashboard, then page, then card.
const names = (hass) => ({
  areaName: (a) => (hass.areas && hass.areas[a] && hass.areas[a].name) || '',
  entityName: (e) => (hass.states && hass.states[e] && hass.states[e].attributes.friendly_name) || '',
});

const MirrorEditorBase = createFormEditor({
  schema: (config, hass) => {
    const list = dashboardList || [];
    const dashboardOptions = [
      { value: THIS, label: 'This dashboard' },
      ...list.map((d) => ({ value: d.url_path, label: d.title || d.url_path })),
    ];
    const dashboard = cached(pathOf(config));
    const views = (dashboard && Array.isArray(dashboard.views) && dashboard.views) || [];
    const viewOptions = views.map((v, i) => ({ value: v.path || String(i), label: v.title || v.path || `Page ${i + 1}` }));
    const view = dashboard && findView(dashboard, config.view);
    const cardOptions = view ? options(view, names(hass)) : [];
    return [
      { name: 'dashboard', selector: { select: { mode: 'dropdown', options: dashboardOptions } } },
      { name: 'view', selector: { select: { mode: 'dropdown', options: viewOptions } } },
      { name: 'source', selector: { select: { mode: 'dropdown', options: cardOptions } } },
    ];
  },
  labels: { dashboard: 'Dashboard', view: 'Page', source: 'Card to mirror' },
  helpers: {
    dashboard: 'Where the card is. "This dashboard" is the one this card is on.',
    view: 'The page the original card is on.',
    source: 'Listed by panel and name. Change the original card and every mirror of it follows.',
  },
  fill: (config) => (config.dashboard ? config : { ...config, dashboard: THIS }),
});

class MirrorCardEditor extends MirrorEditorBase {
  setConfig(config) {
    super.setConfig(config);
    this._load();
  }

  set hass(hass) {
    super.hass = hass;
    this._load();
  }

  // The dashboards and the chosen dashboard's pages arrive asynchronously; the form fills in when they do.
  _load() {
    if (!this._hass || !this._config) return;
    const hass = this._hass;
    if (!dashboardList) loadDashboards(hass).then(() => this._render());
    const path = pathOf(this._config);
    if (!cached(path)) loadConfig(hass, path).then(() => this._render());
  }

  // Changing the dashboard clears the page and card; changing the page clears the card.
  _changed(config) {
    const before = this._config || {};
    const next = { ...config };
    if (next.dashboard !== before.dashboard) {
      delete next.view;
      delete next.source;
    } else if (next.view !== before.view) {
      delete next.source;
    }
    super._changed(next);
  }
}

export function registerMirrorCard() {
  if (!customElements.get(`mirror-card-editor${SUFFIX}`)) {
    customElements.define(`mirror-card-editor${SUFFIX}`, MirrorCardEditor);
  }
  if (!customElements.get(`mirror-card${SUFFIX}`)) {
    customElements.define(`mirror-card${SUFFIX}`, MirrorCard);
  }
  window.customCards = window.customCards || [];
  window.customCards.push({
    type: `mirror-card${SUFFIX}`,
    name: `Mirror Card${LABEL}`,
    description: 'Shows another page\'s card here, set up once',
    preview: false,
    documentationURL: 'https://github.com/J45PER/church-drive-cards#readme',
  });
}
