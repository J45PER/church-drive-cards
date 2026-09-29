// Nav Bar Card: a floating capsule pinned to the bottom of the screen that
// takes you to any page of a dashboard in one tap. Each page has an icon in
// its colour (both fixed, or live from templates, e.g. following the alarm);
// the current page is a filled capsule with its name, and a small dot marks a
// page with something that needs you (from a template). Put the same card on
// every page of the dashboard; it takes no room in the layout.

import { createFormEditor } from './form-editor.js';
import { iconHtml, hydrateIcons } from './icons.js';
import { SUFFIX, LABEL } from './suffix.js';
import { kitNavigate, kitEsc, kitScrollParent, kitScrollTop, kitGlide } from './card-kit.js';
import { stcColor, stcRender, STC_COLOR_TEMPLATE_HELPER } from './section-title-card.js';

// Hiding the dashboard's own tabs: a style put into Home Assistant's
// dashboard header (hui-root). Every pinned bar on the page holds it; the
// last one to go takes it away (after a moment, so moving between pages,
// where one bar goes and the next arrives, doesn't flash the tabs).
const NB_HIDE_CSS = `.toolbar ha-tab-group, .toolbar sl-tab-group, .toolbar paper-tabs, .toolbar ha-tabs, .toolbar .tabs, ha-tab-group.tabs { display: none !important; }`;
let nbHolders = 0;
let nbHideTimer = null;

function nbFind(root, name, depth = 0) {
  if (!root || depth > 8) return null;
  const hit = root.querySelector(name);
  if (hit) return hit;
  for (const el of root.querySelectorAll('*')) {
    if (el.shadowRoot) {
      const found = nbFind(el.shadowRoot, name, depth + 1);
      if (found) return found;
    }
  }
  return null;
}

export function nbHuiRoot() {
  const ha = document.querySelector('home-assistant');
  return ha && ha.shadowRoot ? nbFind(ha.shadowRoot, 'hui-root') : null;
}

function nbHideTabs(hide) {
  clearTimeout(nbHideTimer);
  const apply = () => {
    const root = nbHuiRoot();
    const sr = root && root.shadowRoot;
    if (!sr) return false;
    const style = sr.querySelector('style.cd-nav-hide-tabs');
    if (nbHolders > 0 && !style) {
      const el = document.createElement('style');
      el.className = 'cd-nav-hide-tabs';
      el.textContent = NB_HIDE_CSS;
      sr.appendChild(el);
    } else if (nbHolders <= 0 && style) style.remove();
    return true;
  };
  if (hide) {
    nbHolders += 1;
    // The header may still be drawing: try again a few times.
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

const NB_DEMO = [
  { name: 'Home', icon: 'mdi:home', path: '#home', color: 'blue' },
  { name: 'Lights', icon: 'mdi:lightbulb', path: '#lights', color: 'amber' },
  { name: 'Security', icon: 'mdi:shield-lock', path: '#security', color: 'blue' },
  { name: 'Climate', icon: 'mdi:thermostat', path: '#climate', color: 'amber', alert_template: 'on' },
  { name: 'Cleaning', icon: 'mdi:robot-vacuum', path: '#cleaning', color: 'blue' },
];

// "on", "true", a number above 0 or any other text counts as needing you.
const nbAlert = (text) => {
  const t = String(text || '').trim().toLowerCase();
  return !!t && !['0', 'false', 'off', 'no', 'none', 'unknown', 'unavailable'].includes(t);
};

export const NavBarCardEditor = createFormEditor({
  schema: () => [
    {
      name: 'pages',
      selector: {
        object: {
          multiple: true,
          label_field: 'name',
          fields: {
            name: { label: 'Name', required: true, selector: { text: {} } },
            icon: { label: 'Icon', required: true, selector: { icon: {} } },
            path: { label: 'Page', required: true, selector: { navigation: {} } },
            color: { label: 'Colour', selector: { ui_color: {} } },
            color_template: { label: 'Colour from a template (optional)', selector: { template: {} } },
            icon_template: { label: 'Icon from a template (optional, e.g. mdi:shield-off when disarmed)', selector: { template: {} } },
            alert_template: { label: 'Needs attention when (optional template)', selector: { template: {} } },
          },
        },
      },
    },
    { name: 'hide_tabs', selector: { boolean: {} }, default: true },
    { name: 'back_to_top', selector: { boolean: {} }, default: true },
    { name: 'demo', selector: { boolean: {} } },
  ],
  labels: {
    pages: 'Pages',
    hide_tabs: "Hide the dashboard's own tabs at the top",
    back_to_top: 'Back-to-top button beside the bar (an arrow once you scroll down, a dash at the top)',
    demo: 'Show pretend pages (for Design Presets; shown in place, not pinned)',
  },
  helpers: {
    pages: `Each page's colour can come from a template (${STC_COLOR_TEMPLATE_HELPER.replace(/^Gives a colour name or code, /, '')}). A page gets a dot while its "needs attention" template gives something other than 0, off or empty, e.g. {{ is_state('binary_sensor.back_door', 'on') }}.`,
  },
});

export class NavBarCard extends HTMLElement {
  setConfig(config) {
    if (!config.demo && !(config.pages || []).length) throw new Error('Add at least one page (or set demo: true)');
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
    window.addEventListener('location-changed', this._onLocation);
    window.addEventListener('popstate', this._onLocation);
    this._sc = null;
    this._onScroll = () => {
      cancelAnimationFrame(this._scrollFrame);
      this._scrollFrame = requestAnimationFrame(() => this._syncTop());
    };
    window.addEventListener('scroll', this._onScroll, { capture: true, passive: true });
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
    window.removeEventListener('location-changed', this._onLocation);
    window.removeEventListener('popstate', this._onLocation);
    window.removeEventListener('scroll', this._onScroll, { capture: true });
    this._unsubscribe();
  }

  _unsubscribe() {
    (this._subs || []).forEach((p) => p && p.then((unsub) => unsub && unsub()).catch(() => {}));
    this._subs = null;
  }

  // Live colours and attention dots from each page's templates.
  _resubscribe() {
    this._unsubscribe();
    if (!this._hass || !this.config || this.config.demo || !this.isConnected) return;
    this._subs = [];
    this._pages.forEach((p, i) => {
      const live = (this._live[i] = this._live[i] || {});
      this._subs.push(stcRender(this._hass, p.color_template, (c) => { live.color = c; this._render(); }));
      this._subs.push(stcRender(this._hass, p.icon_template, (c) => { live.icon = String(c || '').trim(); this._render(); }));
      this._subs.push(stcRender(this._hass, p.alert_template, (a) => { live.alert = nbAlert(a); this._render(); }));
    });
  }

  _active() {
    if (this.config.demo) return this._demoActive || 0;
    const here = location.pathname.replace(/\/$/, '');
    // Longest matching path wins, so /mobile/climate beats /mobile.
    let best = -1, len = -1;
    this._pages.forEach((p, i) => {
      const path = String(p.path || '').split(/[?#]/)[0].replace(/\/$/, '');
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
          .nb { pointer-events:auto; flex:1 1 auto; min-width:0; max-width:440px; height:58px; border-radius:29px; display:flex; align-items:center; justify-content:space-between; gap:4px; padding:0 7px; box-sizing:border-box;
            background:color-mix(in srgb, var(--card-background-color, #1f2128) 92%, #fff 4%); box-shadow:0 8px 24px rgba(0,0,0,.5), inset 0 0 0 1px rgba(255,255,255,.06);
            -webkit-backdrop-filter:blur(12px); backdrop-filter:blur(12px); }
          .nb-it { position:relative; flex:none; height:44px; min-width:44px; border:none; border-radius:22px; padding:0; background:transparent; cursor:pointer; font:inherit;
            display:flex; align-items:center; justify-content:center; gap:6px; color:var(--secondary-text-color); transition:background-color .25s, padding .25s; -webkit-tap-highlight-color:transparent; }
          .nb-it.nb-on { padding:0 14px 0 12px; color:#fff; font-weight:600; font-size:0.85rem; }
          .nb-it span.nb-name { white-space:nowrap; overflow:hidden; text-overflow:ellipsis; max-width:110px; }
          .nb-it:focus-visible { outline:2px solid var(--primary-color); outline-offset:2px; }
          .nb-dot { position:absolute; left:28px; top:6px; width:8px; height:8px; border-radius:50%; background:#ff9800; box-shadow:0 0 0 2px var(--card-background-color, #1f2128); }
          .nb-it.nb-on .nb-dot { left:auto; right:6px; }
          .nb-top { pointer-events:auto; position:relative; flex:none; width:58px; height:58px; padding:0; border:none; border-radius:50%; cursor:pointer;
            background:color-mix(in srgb, var(--card-background-color, #1f2128) 92%, #fff 4%); box-shadow:0 8px 24px rgba(0,0,0,.5), inset 0 0 0 1px rgba(255,255,255,.06);
            -webkit-backdrop-filter:blur(12px); backdrop-filter:blur(12px); -webkit-tap-highlight-color:transparent; }
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
        <div class="nb-wrap" style="${inline ? 'position:relative; display:flex; justify-content:center; padding:4px 0;' : 'position:fixed; z-index:5; left:0; right:0; bottom:calc(14px + env(safe-area-inset-bottom, 0px)); display:flex; justify-content:center; pointer-events:none; padding:0 14px;'} gap:10px;"><nav class="nb" aria-label="Pages"></nav>${
          this.config.back_to_top === false ? '' : '<button class="nb-top" type="button" aria-label="Back to top" title="Back to top"><i class="nb-a"></i><i class="nb-b"></i><i class="nb-c"></i></button>'
        }</div>`;
      if (inline) {
        this.innerHTML = html;
        this._nav = this.querySelector('.nb');
      } else {
        // Room at the end of the page so the bar never covers the last card.
        this.innerHTML = '<div style="height:70px;"></div>';
        this._host = document.createElement('div');
        this._host.className = 'church-drive-nav-bar';
        this._host.innerHTML = html;
        document.body.appendChild(this._host);
        this._nav = this._host.querySelector('.nb');
      }
      this._top = (this._host || this).querySelector('.nb-top');
      if (this._top) this._top.addEventListener('click', () => this._toTop());
      this._built = true;
      this._up = undefined;
      this._syncTop();
    }
    const active = this._active();
    const items = this._pages.map((p, i) => {
      const live = this._live[i] || {};
      const colour = stcColor(live.color || p.color || 'primary');
      const alert = demo ? !!p.alert_template : !!live.alert;
      const icon = /^[a-z]+:[\w-]+$/.test(live.icon || '') ? live.icon : p.icon || 'mdi:circle';
      return { i, p, icon, colour, alert, on: i === active };
    });
    const sig = JSON.stringify(items.map((t) => [t.p.name, t.icon, t.colour, t.alert, t.on]));
    if (sig === this._sig) return;
    this._sig = sig;
    this._nav.innerHTML = items
      .map(({ i, p, icon, colour, alert, on }) => `<button class="nb-it${on ? ' nb-on' : ''}" type="button" data-i="${i}" title="${kitEsc(p.name)}" aria-label="${kitEsc(p.name)}${alert ? ', needs attention' : ''}"${on ? ' aria-current="page"' : ''} style="${on ? `background:${colour};` : ''}">
          ${iconHtml(icon, { size: '22px', style: `flex:none; color:${on ? '#fff' : colour};` })}
          ${on ? `<span class="nb-name">${kitEsc(p.name)}</span>` : ''}
          ${alert ? '<i class="nb-dot"></i>' : ''}
        </button>`)
      .join('');
    this._nav.querySelectorAll('.nb-it').forEach((b) =>
      b.addEventListener('click', () => {
        const i = Number(b.dataset.i);
        if (demo) {
          this._demoActive = i;
          this._render();
          return;
        }
        if (i !== this._active()) kitNavigate(this._pages[i].path);
        else this._toTop();
      }),
    );
    hydrateIcons(this._host || this);
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
      this._top.classList.toggle('nb-up', up);
      this._top.setAttribute('aria-disabled', String(!up));
    }
  }

  _toTop() {
    window.dispatchEvent(new CustomEvent('cd-to-top'));
    if (this.config.demo) return;
    const sc = this._scroller();
    kitGlide(sc, () => -kitScrollTop(sc));
  }

  getCardSize() {
    return this.config && this.config.demo ? 1 : 0;
  }

  getGridOptions() {
    return { columns: 'full', rows: 'auto' };
  }

  static getConfigElement() {
    return document.createElement(`nav-bar-card-editor${SUFFIX}`);
  }

  static getStubConfig() {
    return { demo: true };
  }
}

export function registerNavBarCard() {
  if (!customElements.get(`nav-bar-card-editor${SUFFIX}`)) customElements.define(`nav-bar-card-editor${SUFFIX}`, NavBarCardEditor);
  if (!customElements.get(`nav-bar-card${SUFFIX}`)) customElements.define(`nav-bar-card${SUFFIX}`, NavBarCard);
  window.customCards = window.customCards || [];
  window.customCards.push({
    type: `nav-bar-card${SUFFIX}`,
    name: `Nav Bar Card${LABEL}`,
    description: 'A floating bar at the bottom of the screen: every page one tap away, in its live colour, with a dot when a page needs you',
    preview: true,
    documentationURL: 'https://github.com/J45PER/church-drive-cards#readme',
  });
}
