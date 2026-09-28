// Nav Bar Card: a floating capsule pinned to the bottom of the screen that
// takes you to any page of a dashboard in one tap. Each page's icon is in
// its colour (fixed, or live from a template, e.g. the alarm colour); the
// current page is a filled capsule with its name, and a small dot marks a
// page with something that needs you (from a template). Put the same card on
// every page of the dashboard; it takes no room in the layout.

import { createFormEditor } from './form-editor.js';
import { iconHtml, hydrateIcons } from './icons.js';
import { SUFFIX, LABEL } from './suffix.js';
import { kitNavigate, kitEsc } from './card-kit.js';
import { stcColor, stcRender, STC_COLOR_TEMPLATE_HELPER } from './section-title-card.js';

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
            alert_template: { label: 'Needs attention when (optional template)', selector: { template: {} } },
          },
        },
      },
    },
    { name: 'demo', selector: { boolean: {} } },
  ],
  labels: {
    pages: 'Pages',
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
    if (this._hass && !this._subs) this._resubscribe();
    this._render();
  }

  disconnectedCallback() {
    if (this._host) this._host.remove();
    this._host = null;
    this._built = false;
    this._sig = null;
    window.removeEventListener('location-changed', this._onLocation);
    window.removeEventListener('popstate', this._onLocation);
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
          .nb { pointer-events:auto; width:100%; max-width:440px; height:58px; border-radius:29px; display:flex; align-items:center; justify-content:space-between; gap:4px; padding:0 7px; box-sizing:border-box;
            background:color-mix(in srgb, var(--card-background-color, #1f2128) 92%, #fff 4%); box-shadow:0 8px 24px rgba(0,0,0,.5), inset 0 0 0 1px rgba(255,255,255,.06);
            -webkit-backdrop-filter:blur(12px); backdrop-filter:blur(12px); }
          .nb-it { position:relative; flex:none; height:44px; min-width:44px; border:none; border-radius:22px; padding:0; background:transparent; cursor:pointer; font:inherit;
            display:flex; align-items:center; justify-content:center; gap:6px; color:var(--secondary-text-color); transition:background-color .25s, padding .25s; -webkit-tap-highlight-color:transparent; }
          .nb-it.nb-on { padding:0 14px 0 12px; color:#fff; font-weight:600; font-size:0.85rem; }
          .nb-it span.nb-name { white-space:nowrap; overflow:hidden; text-overflow:ellipsis; max-width:110px; }
          .nb-it:focus-visible { outline:2px solid var(--primary-color); outline-offset:2px; }
          .nb-dot { position:absolute; left:28px; top:6px; width:8px; height:8px; border-radius:50%; background:#ff9800; box-shadow:0 0 0 2px var(--card-background-color, #1f2128); }
          .nb-it.nb-on .nb-dot { left:auto; right:6px; }
        </style>
        <div class="nb-wrap" style="${inline ? 'position:relative; display:flex; justify-content:center; padding:4px 0;' : 'position:fixed; z-index:5; left:0; right:0; bottom:calc(14px + env(safe-area-inset-bottom, 0px)); display:flex; justify-content:center; pointer-events:none; padding:0 14px;'}"><nav class="nb" aria-label="Pages"></nav></div>`;
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
      this._built = true;
    }
    const active = this._active();
    const items = this._pages.map((p, i) => {
      const live = this._live[i] || {};
      const colour = stcColor(live.color || p.color || 'primary');
      const alert = demo ? !!p.alert_template : !!live.alert;
      return { i, p, colour, alert, on: i === active };
    });
    const sig = JSON.stringify(items.map((t) => [t.p.name, t.p.icon, t.colour, t.alert, t.on]));
    if (sig === this._sig) return;
    this._sig = sig;
    this._nav.innerHTML = items
      .map(({ i, p, colour, alert, on }) => `<button class="nb-it${on ? ' nb-on' : ''}" type="button" data-i="${i}" title="${kitEsc(p.name)}" aria-label="${kitEsc(p.name)}${alert ? ', needs attention' : ''}"${on ? ' aria-current="page"' : ''} style="${on ? `background:${colour};` : ''}">
          ${iconHtml(p.icon || 'mdi:circle', { size: '22px', style: `flex:none; color:${on ? '#fff' : colour};` })}
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
        else window.scrollTo({ top: 0, behavior: 'smooth' });
      }),
    );
    hydrateIcons(this._host || this);
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
