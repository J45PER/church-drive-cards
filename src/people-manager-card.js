// People Manager Card (Manager › People): everything about each person in one
// place. A list of people (chips on a phone), and the chosen person's page:
// their picture (upload or remove), where they are, their places (what they call
// each zone), which phones get alerts, which cars are theirs, which alerts they
// get, their to-dos and house jobs, who's told when they get home, and the
// devices only some people use (everything else is everyone's; cards hide a
// device from people who can't use it).
//
// Kept by the Church Drive integration (church_drive/people/…; only
// administrators can change anything); pictures are Home Assistant's own person
// pictures (image upload, then person/update).

import { createFormEditor } from './form-editor.js';
import { iconHtml, hydrateIcons } from './icons.js';
import { SUFFIX, LABEL } from './suffix.js';
import { kitEsc, kitNavigate } from './card-kit.js';
import { placeText } from './places-card.js';
import { carDevices, carEntities } from './car-card.js';

const PEOPLE_SENSOR = 'sensor.church_drive_people';
const SUGGEST = ['Work', 'School', 'College', 'Gym', 'Family', 'Friends', 'Shops'];
const TEAL = '#26a69a';
const PURPLE = '#7e57c2';
const COLOURS = ['#26a69a', '#7e57c2', '#ef6c00', '#5c6bc0', '#d81b60', '#00897b', '#6d4c41'];
const TODO_GROUPS = ['To-dos', 'House jobs'];
// Devices worth limiting: ones with something to control or watch.
const DEVICE_DOMAINS = ['light', 'switch', 'cover', 'fan', 'climate', 'media_player', 'lock', 'camera', 'vacuum', 'humidifier', 'water_heater', 'remote', 'siren', 'valve', 'lawn_mower'];

const PM_CSS = `
  .pm { display:flex; flex-wrap:wrap; gap:16px; align-items:flex-start; container-type:inline-size; }
  .pm-list { flex:1 1 220px; max-width:280px; display:flex; flex-direction:column; gap:8px; }
  .pm-main { flex:999 1 520px; min-width:0; display:flex; flex-direction:column; gap:14px; }
  @container (max-width: 700px) {
    .pm-list { flex-basis:100%; max-width:none; flex-direction:row; overflow-x:auto; gap:8px; padding-bottom:2px; }
    .pm-pick { min-height:44px !important; padding:6px 12px 6px 6px !important; flex:none; width:auto !important; }
    .pm-pick .pm-where, .pm-pick .pm-admin { display:none; }
  }
  .pm-pick { display:flex; align-items:center; gap:12px; width:100%; min-height:60px; border:none; border-radius:16px; padding:10px 14px; cursor:pointer; font:inherit;
    background:var(--cd-card-bg, var(--card-background-color)); color:var(--primary-text-color); box-shadow:0 2px 8px rgba(0,0,0,.3); text-align:left; }
  .pm-pick.on { outline:2px solid var(--pm-c); }
  .pm-pick b { font-size:0.95rem; font-weight:600; display:block; }
  .pm-where { font-size:0.78rem; color:var(--secondary-text-color); display:block; }
  .pm-admin, .pm-badge { margin-left:auto; font-size:0.68rem; font-weight:700; padding:2px 8px; border-radius:999px; background:color-mix(in srgb, ${PURPLE} 25%, transparent); color:#c8b4f0; white-space:nowrap; }
  .pm-av { flex:none; width:36px; height:36px; border-radius:50%; display:flex; align-items:center; justify-content:center; font-weight:700; color:#fff; background-size:cover; background-position:center; }
  .pm-head { display:flex; flex-wrap:wrap; align-items:center; gap:14px; border-radius:18px; padding:16px 18px; background:var(--cd-card-bg, var(--card-background-color)); box-shadow:0 3px 10px rgba(0,0,0,.45); }
  .pm-head .pm-av { width:64px; height:64px; font-size:1.6rem; }
  .pm-head h2 { margin:0; font-size:1.4rem; font-weight:600; }
  .pm-pic { display:flex; gap:6px; }
  .pm-grid { display:grid; grid-template-columns:repeat(auto-fit, minmax(min(100%, 300px), 1fr)); gap:14px; }
  .pm-sec { border-radius:18px; padding:16px 18px; background:var(--cd-card-bg, var(--card-background-color)); box-shadow:0 3px 10px rgba(0,0,0,.45); display:flex; flex-direction:column; gap:10px; min-width:0; }
  .pm-sec h3 { margin:0; font-size:1.05rem; font-weight:600; display:flex; align-items:center; gap:8px; }
  .pm-sec h3 small { margin-left:auto; font-size:0.78rem; font-weight:400; color:var(--secondary-text-color); text-align:right; }
  .pm-row { display:flex; align-items:center; gap:10px; min-height:36px; font-size:0.9rem; }
  .pm-row > span:first-child { flex:1; min-width:0; }
  .pm-row small { display:block; font-size:0.76rem; color:var(--secondary-text-color); }
  .pm-note { font-size:0.76rem; color:var(--secondary-text-color); line-height:1.45; }
  .pm-sw { flex:none; position:relative; width:44px; height:26px; border:none; border-radius:13px; padding:0; cursor:pointer; background:rgba(127,127,127,.35); transition:background .2s; }
  .pm-sw::after { content:''; position:absolute; top:3px; left:3px; width:20px; height:20px; border-radius:50%; background:#fff; transition:left .2s; }
  .pm-sw.on { background:${TEAL}; }
  .pm-sw.on::after { left:21px; }
  .pm-sw:disabled { opacity:.45; cursor:default; }
  .pm-btn { border:none; cursor:pointer; font:inherit; font-size:0.82rem; font-weight:600; border-radius:999px; padding:7px 14px; display:inline-flex; align-items:center; gap:6px;
    background:rgba(127,127,127,0.18); color:var(--primary-text-color); }
  .pm-save { background:${TEAL}; color:#fff; }
  .pm-chips { display:flex; flex-wrap:wrap; gap:8px; }
  .pm-chip { border:none; cursor:pointer; font:inherit; font-size:0.82rem; font-weight:600; border-radius:999px; padding:7px 13px; background:rgba(127,127,127,.18); color:var(--secondary-text-color); }
  .pm-chip.on { background:color-mix(in srgb, var(--pm-c) 28%, transparent); color:var(--primary-text-color); }
  .pm-group { border-radius:12px; background:rgba(127,127,127,.08); }
  .pm-group > button { width:100%; display:flex; align-items:center; gap:8px; border:none; background:none; color:var(--primary-text-color); font:inherit; font-size:0.9rem; font-weight:600; padding:10px 12px; cursor:pointer; min-height:44px; }
  .pm-group > button small { margin-left:auto; font-weight:400; color:var(--secondary-text-color); }
  .pm-kinds { padding:0 12px 8px; }
  .pm-bar { height:6px; border-radius:3px; background:rgba(127,127,127,.2); overflow:hidden; margin-top:5px; }
  .pm-bar i { display:block; height:100%; border-radius:3px; background:#00b8d4; }
  .pm-edit select, .pm-edit input { min-width:0; box-sizing:border-box; height:36px; border-radius:10px; border:1px solid var(--divider-color, rgba(127,127,127,0.3));
    background:var(--secondary-background-color, rgba(127,127,127,0.12)); color:var(--primary-text-color); font:inherit; font-size:0.85rem; padding:0 8px; }
  .pm-edit select { flex:1.3 1 0; } .pm-edit input { flex:1 1 0; }
  .pm-x { flex:none; width:32px; height:32px; border:none; border-radius:50%; background:transparent; color:var(--secondary-text-color); cursor:pointer; display:flex; align-items:center; justify-content:center; }
  .pm-acts { display:flex; flex-wrap:wrap; justify-content:flex-end; gap:8px; }
  .pm-err { font-size:0.8rem; color:#ffab91; }
  .pm-dev { border-radius:12px; background:rgba(127,127,127,.08); padding:10px 12px; display:flex; flex-direction:column; gap:8px; }
  .pm-dev .pm-chip { padding:6px 11px; }
  .pm-add select { flex:1; min-width:0; height:40px; border-radius:10px; border:1px solid var(--divider-color, rgba(127,127,127,0.3));
    background:var(--secondary-background-color, rgba(127,127,127,0.12)); color:var(--primary-text-color); font:inherit; font-size:0.85rem; padding:0 8px; }
`;

export const PeopleManagerCardEditor = createFormEditor({
  schema: () => [{ name: 'demo', selector: { boolean: {} } }],
  labels: { demo: 'Show pretend people (for Design Presets)' },
  helpers: { demo: "Everyone's places, phones, cars, alerts, to-dos and arrivals in one place. Only administrators can change things." },
});

export class PeopleManagerCard extends HTMLElement {
  setConfig(config) {
    this.config = config || {};
    this._built = false;
    this._data = null; // { people, kinds }
    this._picked = null;
    this._open = {}; // alert groups opened
    this._draft = null; // places being edited for the picked person
    this._err = '';
  }

  set hass(hass) {
    this._hass = hass;
    if (this.config.demo) {
      if (!this._data) this._data = this._demo();
    } else {
      const st = hass.states[PEOPLE_SENSOR];
      const rev = st ? `${st.attributes.rev}|${st.last_updated}` : 'none';
      if (rev !== this._rev) {
        this._rev = rev;
        this._load();
      }
    }
    this._render();
  }

  _demo() {
    const kinds = [
      ['smoke', 'Safety', 'Smoke alarm'], ['doorbell', 'Security', 'Doorbell'], ['alarm_mode', 'Security', 'Alarm set or turned off'],
      ['damp', 'House', 'Damp risk'], ['cheap_rate', 'Energy', 'Cheap electricity'], ['car_charged', 'Car', 'Car charged'],
      ['internet', 'System', 'Internet down'], ['todo_summary', 'To-dos', 'To-do summary'], ['low_batteries', 'House jobs', 'Low batteries'],
      ['arrivals:person.jamie', 'People', 'Jamie gets home or leaves'], ['arrivals:person.hayley', 'People', 'Hayley gets home or leaves'],
    ].map(([key, group, name]) => ({ key, group, name, available: true, all: false, people: ['person.jamie', 'person.hayley'] }));
    return {
      kinds,
      access: { 'demo-blind': ['person.jamie', 'person.hayley'] },
      people: [
        { entity_id: 'person.jamie', name: 'Jamie', first: 'Jamie', admin: true, home: 'home', place: 'Home', zone: '', places: [{ zone: 'zone.work', name: 'Work' }], cars: [], phones: [{ service: 'a', name: 'iPhone', on: true }], list: 'todo.x' },
        { entity_id: 'person.hayley', name: 'Hayley', first: 'Hayley', admin: true, home: 'Frasers Group', place: 'Work', zone: 'Frasers Group', places: [], cars: ['demo-car'], phones: [{ service: 'b', name: 'Pixel 9', on: true }], list: 'todo.y' },
      ],
    };
  }

  async _load() {
    try {
      this._data = await this._hass.connection.sendMessagePromise({ type: 'church_drive/people' });
      this._sig = null;
      this._render();
    } catch (err) {
      this._data = this._data || { people: [], kinds: [] };
    }
  }

  _admin() {
    return !!(this.config.demo || (this._hass && this._hass.user && this._hass.user.is_admin));
  }

  _me() {
    const u = this._hass && this._hass.user;
    const people = (this._data && this._data.people) || [];
    return (u && people.find((p) => p.user_id === u.id)) || null;
  }

  // Admins see everyone; anyone else only their own page.
  _people() {
    const all = (this._data && this._data.people) || [];
    if (this._admin()) return all;
    const me = this._me();
    return me ? [me] : [];
  }

  _person() {
    const people = this._people();
    let p = people.find((x) => x.entity_id === this._picked);
    if (!p) p = (this._me() && people.find((x) => x.entity_id === this._me().entity_id)) || people[0];
    if (p) this._picked = p.entity_id;
    return p || null;
  }

  _colour(p) {
    const all = (this._data && this._data.people) || [];
    return COLOURS[Math.max(0, all.findIndex((x) => x.entity_id === p.entity_id)) % COLOURS.length];
  }

  _picture(p) {
    const st = this._hass && this._hass.states[p.entity_id];
    return (st && st.attributes.entity_picture) || p.picture || '';
  }

  _avatar(p) {
    const pic = this._picture(p);
    return `<span class="pm-av" style="background-color:${this._colour(p)};${pic ? ` background-image:url('${kitEsc(pic)}');` : ''}">${pic ? '' : kitEsc((p.first || p.name || '?')[0])}</span>`;
  }

  _zones() {
    if (this.config.demo) return [['zone.work', 'Ashfield School'], ['zone.gym', 'PureGym']];
    const s = this._hass.states;
    return Object.keys(s)
      .filter((id) => id.startsWith('zone.') && id !== 'zone.home')
      .map((id) => [id, String(s[id].attributes.friendly_name || id)])
      .sort((a, b) => a[1].localeCompare(b[1]));
  }

  _cars() {
    if (this.config.demo) return [{ device: 'demo-car', name: 'Electric car', battery: 64, range: '142 mi' }];
    return carDevices(this._hass).map((c) => {
      const e = carEntities(this._hass, c.ids);
      const b = this._hass.states[e.battery];
      const r = this._hass.states[e.range];
      return {
        device: c.device,
        name: c.name,
        battery: b && !isNaN(Number(b.state)) ? Math.round(Number(b.state)) : null,
        range: r && !isNaN(Number(r.state)) ? `${Math.round(Number(r.state))} ${r.attributes.unit_of_measurement || ''}`.trim() : '',
      };
    });
  }

  _gets(kind, person) {
    return !!(kind.all || (kind.people || []).includes(person));
  }

  async _send(msg) {
    if (this.config.demo) return null;
    this._err = '';
    try {
      const r = await this._hass.connection.sendMessagePromise(msg);
      if (r && r.people) this._data.people = r.people;
      if (r && r.kinds) this._data.kinds = r.kinds;
      return r;
    } catch (err) {
      this._err = (err && err.message) || "Couldn't save that.";
      this._load();
      return null;
    } finally {
      this._sig = null;
      this._render();
    }
  }

  _render() {
    if (!this._hass && !this.config.demo) return;
    if (!this._built) {
      this.innerHTML = `<style>${PM_CSS}</style><div class="pm"></div><input type="file" accept="image/*" hidden>`;
      this._root = this.querySelector('.pm');
      this._file = this.querySelector('input[type=file]');
      this._file.addEventListener('change', () => this._upload());
      this._root.addEventListener('click', (ev) => this._click(ev));
      const keep = (ev) => {
        const i = Number(ev.target.dataset.i);
        if (!this._draft || !this._draft[i]) return;
        if (ev.target.matches('select')) this._draft[i].zone = ev.target.value;
        else if (ev.target.matches('input')) this._draft[i].name = ev.target.value;
      };
      this._root.addEventListener('change', keep);
      this._root.addEventListener('input', keep);
      this._built = true;
    }
    const people = this._people();
    const p = this._person();
    const admin = this._admin();
    const cars = this._cars();
    const zones = this._zones();
    const todo = p && this._hass && this._hass.states[p.list];
    const sig = JSON.stringify([this._data, this._picked, this._open, this._draft, this._err, admin, cars, zones, p && this._picture(p), todo && todo.state, this._busy]);
    if (sig === this._sig) return;
    this._sig = sig;
    if (!this._data) {
      this._root.innerHTML = '<div class="pm-note">Loading people…</div>';
      return;
    }
    if (!p) {
      this._root.innerHTML = '<div class="pm-note">No people yet. Add people in Settings › People, and they appear here by themselves.</div>';
      return;
    }
    const c = this._colour(p);
    this.style.setProperty('--pm-c', c);
    const list = people
      .map(
        (x) => `<button type="button" class="pm-pick${x.entity_id === p.entity_id ? ' on' : ''}" data-act="pick" data-person="${kitEsc(x.entity_id)}" style="--pm-c:${this._colour(x)};">
        ${this._avatar(x)}<span style="min-width:0;"><b>${kitEsc(x.first || x.name)}</b><span class="pm-where">${kitEsc(this._where(x))}</span></span>${x.admin ? '<span class="pm-admin">Admin</span>' : ''}</button>`,
      )
      .join('');
    this._root.innerHTML = `${people.length > 1 ? `<div class="pm-list">${list}</div>` : ''}
      <div class="pm-main">
        <div class="pm-head">${this._avatar(p)}
          <div style="display:flex; flex-direction:column; gap:3px; min-width:0;"><h2>${kitEsc(p.name)}</h2><span class="pm-where" style="font-size:0.88rem;">${kitEsc(this._where(p))}</span></div>
          <div style="margin-left:auto; display:flex; flex-wrap:wrap; gap:8px; align-items:center;">${p.admin ? '<span class="pm-badge" style="margin:0;">Admin</span>' : ''}
            ${admin ? `<span class="pm-pic"><button type="button" class="pm-btn" data-act="pic">${iconHtml('mdi:camera-outline', { size: '16px' })}${this._busy ? 'Uploading…' : this._picture(p) ? 'Change picture' : 'Add picture'}</button>${this._picture(p) ? `<button type="button" class="pm-btn" data-act="nopic" aria-label="Remove picture">${iconHtml('mdi:close', { size: '16px' })}</button>` : ''}</span>` : ''}</div>
        </div>
        ${this._err ? `<div class="pm-err">${kitEsc(this._err)}</div>` : ''}
        <div class="pm-grid">
          ${this._places(p, zones, admin)}
          ${this._phones(p, admin)}
          ${this._carsSec(p, cars, admin)}
          ${this._alerts(p, admin)}
          ${this._todos(p, admin, todo)}
          ${this._arrivals(p, admin)}
          ${this._devices(p, admin)}
        </div>
      </div>`;
    hydrateIcons(this);
  }

  _where(p) {
    if (!p.home || p.home === 'unknown' || p.home === 'unavailable') return 'Location not shared';
    return placeText(p.place, p.zone);
  }

  _sec(icon, colour, title, note, body) {
    return `<div class="pm-sec"><h3>${iconHtml(icon, { size: '22px', style: `color:${colour};` })}${title}<small>${note}</small></h3>${body}</div>`;
  }

  _sw(on, data, label, disabled = false) {
    return `<button type="button" class="pm-sw${on ? ' on' : ''}" role="switch" aria-checked="${on}" aria-label="${kitEsc(label)}" ${data}${disabled ? ' disabled' : ''}></button>`;
  }

  _places(p, zones, admin) {
    const zoneName = (id) => (zones.find((z) => z[0] === id) || [id, id.replace(/^zone\./, '').replace(/_/g, ' ')])[1];
    let body = `<div class="pm-row"><span>Church Drive<small>Automatic</small></span><span>Home</span></div>`;
    if (this._draft) {
      body += `<datalist id="pm-names${SUFFIX}">${SUGGEST.map((n) => `<option value="${n}">`).join('')}</datalist>`;
      body += this._draft
        .map(
          (pl, i) => `<div class="pm-row pm-edit"><select data-i="${i}" aria-label="Zone">${zones.map(([id, n]) => `<option value="${kitEsc(id)}"${id === pl.zone ? ' selected' : ''}>${kitEsc(n)}</option>`).join('')}</select>
          <input data-i="${i}" list="pm-names${SUFFIX}" value="${kitEsc(pl.name)}" placeholder="Called" aria-label="What ${kitEsc(p.first)} calls it">
          <button type="button" class="pm-x" data-act="pl-del" data-i="${i}" aria-label="Delete this place">${iconHtml('mdi:close', { size: '18px' })}</button></div>`,
        )
        .join('');
      body += `<div class="pm-acts"><button type="button" class="pm-btn" data-act="pl-add">${iconHtml('mdi:plus', { size: '16px' })}Add a place</button><span style="flex:1;"></span>
        <button type="button" class="pm-btn" data-act="pl-cancel">Cancel</button><button type="button" class="pm-btn pm-save" data-act="pl-save">${iconHtml('mdi:check', { size: '16px' })}Save</button></div>`;
    } else {
      body += (p.places || []).map((pl) => `<div class="pm-row"><span>${kitEsc(zoneName(pl.zone))}</span><span>${kitEsc(pl.name || zoneName(pl.zone))}</span></div>`).join('');
      if (admin) body += `<div class="pm-acts"><a class="pm-note" style="margin-right:auto; cursor:pointer;" data-act="zones">Make or rename zones</a><button type="button" class="pm-btn" data-act="pl-edit">${iconHtml('mdi:pencil', { size: '16px' })}Edit</button></div>`;
    }
    return this._sec('mdi:map-marker-outline', TEAL, 'Places', 'What "where" says for them', body);
  }

  _phones(p, admin) {
    const rows = (p.phones || []).map((ph) => `<div class="pm-row"><span>${kitEsc(ph.name)}${ph.model ? `<small>${kitEsc(ph.model)}</small>` : ''}</span>${this._sw(ph.on, `data-act="phone" data-service="${kitEsc(ph.service)}"`, `Alerts to ${ph.name}`, !admin)}</div>`).join('');
    return this._sec(
      'mdi:cellphone',
      TEAL,
      'Phones',
      'Alerts go to switched-on phones',
      (rows || '<div class="pm-note">No phones yet.</div>') + `<div class="pm-note">Phones join by themselves when the Home Assistant app signs in as ${kitEsc(p.first)}.</div>`,
    );
  }

  _carsSec(p, cars, admin) {
    const mine = p.cars || [];
    const rows = cars
      .map(
        (car) => `<div class="pm-row"><span>${kitEsc(car.name)}${car.battery != null ? `<small>${car.battery}%${car.range ? ` · ${kitEsc(car.range)}` : ''}</small><div class="pm-bar"><i style="width:${car.battery}%;"></i></div>` : ''}</span>
        ${this._sw(mine.includes(car.device), `data-act="car" data-device="${kitEsc(car.device)}"`, `${car.name} is ${p.first}'s`, !admin)}</div>`,
      )
      .join('');
    return this._sec(
      'mdi:car-outline',
      '#00b8d4',
      'Cars',
      'Their Car card and car alerts',
      (rows || '<div class="pm-note">No cars yet. Add a car with its own integration (Stellantis Vehicles, VW Group Connect) and it appears here.</div>') +
        (rows ? '<div class="pm-note">A car can belong to several people. A car nobody has shows to everyone.</div>' : ''),
    );
  }

  _kindRows(p, kinds, admin) {
    return kinds
      .map(
        (k) => `<div class="pm-row"><span>${kitEsc(k.name)}${!k.available ? '<small>Waiting for its devices</small>' : k.note ? `<small>${kitEsc(k.note)}</small>` : ''}</span>
        ${this._sw(this._gets(k, p.entity_id), `data-act="kind" data-kind="${kitEsc(k.key)}"`, k.name, !admin || !k.available || (k.admin_only && !p.admin))}</div>`,
      )
      .join('');
  }

  _alerts(p, admin) {
    const kinds = ((this._data && this._data.kinds) || []).filter((k) => k.group !== 'People' || !k.key.startsWith('arrivals:'));
    const shown = kinds.filter((k) => !TODO_GROUPS.includes(k.group));
    const groups = [...new Set(shown.map((k) => k.group))];
    const on = shown.filter((k) => this._gets(k, p.entity_id)).length;
    const body = groups
      .map((g) => {
        const ks = shown.filter((k) => k.group === g);
        const n = ks.filter((k) => this._gets(k, p.entity_id)).length;
        const open = this._open[g];
        return `<div class="pm-group"><button type="button" data-act="group" data-group="${kitEsc(g)}" aria-expanded="${!!open}">${iconHtml(open ? 'mdi:chevron-down' : 'mdi:chevron-right', { size: '18px' })}${kitEsc(g)}<small>${n} of ${ks.length}</small></button>
          ${open ? `<div class="pm-kinds">${this._kindRows(p, ks, admin)}</div>` : ''}</div>`;
      })
      .join('');
    return this._sec('mdi:bell-outline', PURPLE, 'Alerts', `${on} of ${shown.length} on`, body + '<div class="pm-note">The whole-house table is still on the Notifications panel.</div>');
  }

  _todos(p, admin, todo) {
    const kinds = ((this._data && this._data.kinds) || []).filter((k) => TODO_GROUPS.includes(k.group));
    const open = todo && !isNaN(Number(todo.state)) ? Number(todo.state) : null;
    return this._sec(
      'mdi:format-list-checks',
      PURPLE,
      'To-dos',
      `${kitEsc(todo ? todo.attributes.friendly_name || 'Their list' : 'Their list')}${open != null ? ` · ${open} open` : ''}`,
      this._kindRows(p, kinds, admin) + '<div class="pm-note">House jobs are tasks the house adds by itself (a battery to change, a device not responding).</div>',
    );
  }

  _arrivals(p, admin) {
    const kind = ((this._data && this._data.kinds) || []).find((k) => k.key === `arrivals:${p.entity_id}`);
    const others = ((this._data && this._data.people) || []).filter((x) => x.entity_id !== p.entity_id);
    const chips = kind
      ? others
          .map((x) => `<button type="button" class="pm-chip${this._gets(kind, x.entity_id) ? ' on' : ''}" style="--pm-c:${this._colour(x)};" data-act="arrive" data-who="${kitEsc(x.entity_id)}"${admin ? '' : ' disabled'} aria-pressed="${this._gets(kind, x.entity_id)}">${kitEsc(x.first || x.name)}</button>`)
          .join('')
      : '';
    return this._sec(
      'mdi:home-account',
      PURPLE,
      'Arrivals',
      `Told when ${kitEsc(p.first)} gets home or leaves`,
      chips ? `<div class="pm-chips">${chips}</div>` : '<div class="pm-note">Nobody else to tell yet.</div>',
    );
  }

  // Every device worth limiting: [{ id, name, area }].
  _deviceList() {
    if (this.config.demo) return [{ id: 'demo-blind', name: "Hayley's Bedroom Blind", area: "Hayley's Bedroom" }, { id: 'demo-tv', name: 'Living Room TV', area: 'Living Room' }];
    const h = this._hass;
    const devs = h.devices || {};
    const used = new Set();
    Object.values(h.entities || {}).forEach((e) => {
      if (e.device_id && !e.hidden && !e.entity_category && DEVICE_DOMAINS.includes(e.entity_id.split('.')[0])) used.add(e.device_id);
    });
    return [...used]
      .filter((id) => devs[id] && devs[id].entry_type !== 'service')
      .map((id) => {
        const d = devs[id];
        const area = d.area_id && h.areas && h.areas[d.area_id];
        return { id, name: d.name_by_user || d.name || id, area: (area && area.name) || '' };
      })
      .sort((a, b) => (a.area || '~').localeCompare(b.area || '~') || a.name.localeCompare(b.name));
  }

  _devices(p, admin) {
    const access = (this._data && this._data.access) || {};
    const all = this._deviceList();
    const people = (this._data && this._data.people) || [];
    const limited = all.filter((d) => access[d.id]);
    const rows = limited
      .map((d) => {
        const who = access[d.id] || [];
        const chips = people
          .map((x) => {
            const on = who.includes(x.entity_id);
            return `<button type="button" class="pm-chip${on ? ' on' : ''}" style="--pm-c:${this._colour(x)};${x.entity_id === p.entity_id ? ' outline:1px solid var(--pm-c);' : ''}" data-act="dev-who" data-dev="${kitEsc(d.id)}" data-who="${kitEsc(x.entity_id)}" aria-pressed="${on}"${admin ? '' : ' disabled'}>${kitEsc(x.first || x.name)}</button>`;
          })
          .join('');
        return `<div class="pm-dev"><div class="pm-row" style="min-height:0;"><span>${kitEsc(d.name)}${d.area ? `<small>${kitEsc(d.area)}</small>` : ''}</span>
          ${admin ? `<button type="button" class="pm-x" data-act="dev-free" data-dev="${kitEsc(d.id)}" aria-label="Make ${kitEsc(d.name)} everyone's again">${iconHtml('mdi:close', { size: '18px' })}</button>` : ''}</div>
          <div class="pm-chips">${chips}</div></div>`;
      })
      .join('');
    const free = all.filter((d) => !access[d.id]);
    const add =
      admin && free.length
        ? `<div class="pm-row pm-add"><select aria-label="Device to limit" data-dev-pick><option value="">Limit a device to some people…</option>${free
            .map((d) => `<option value="${kitEsc(d.id)}">${kitEsc(d.area ? `${d.area} · ${d.name}` : d.name)}</option>`)
            .join('')}</select><button type="button" class="pm-btn" data-act="dev-add">${iconHtml('mdi:plus', { size: '16px' })}Add</button></div>`
        : '';
    const mine = limited.filter((d) => (access[d.id] || []).includes(p.entity_id)).length;
    return this._sec(
      'mdi:devices',
      PURPLE,
      'Devices',
      limited.length ? `${kitEsc(p.first)} can use ${mine} of ${limited.length} limited` : 'Everyone can use everything',
      (rows || "<div class=\"pm-note\">Every device is everyone's. Limit one (e.g. a bedroom blind) to the people who use it, and it disappears from everyone else's screens.</div>") +
        add +
        "<div class=\"pm-note\">Tap names to choose who can use each one; ✕ makes it everyone's again. This tidies the screens; it isn't a lock.</div>",
    );
  }

  async _click(ev) {
    const b = ev.target.closest('[data-act]');
    if (!b) return;
    const act = b.dataset.act;
    const p = this._person();
    if (act === 'pick') {
      this._picked = b.dataset.person;
      this._draft = null;
      this._err = '';
      this._sig = null;
      return this._render();
    }
    if (act === 'zones') return kitNavigate('/config/zone');
    if (act === 'group') {
      this._open[b.dataset.group] = !this._open[b.dataset.group];
      this._sig = null;
      return this._render();
    }
    if (!this._admin() || !p) return;
    if (act === 'pic') return this._file.click();
    if (act === 'nopic') return this._setPicture(p, null);
    if (act === 'pl-edit') this._draft = (p.places || []).map((x) => ({ ...x }));
    else if (act === 'pl-cancel') this._draft = null;
    else if (act === 'pl-add') {
      const used = new Set(this._draft.map((x) => x.zone));
      const free = this._zones().find(([id]) => !used.has(id));
      if (!free) return kitNavigate('/config/zone');
      this._draft.push({ zone: free[0], name: 'Work' });
    } else if (act === 'pl-del') this._draft.splice(Number(b.dataset.i), 1);
    else if (act === 'dev-add' || act === 'dev-who' || act === 'dev-free') {
      const access = (this._data.access = this._data.access || {});
      let key;
      let who;
      if (act === 'dev-add') {
        const pick = this._root.querySelector('[data-dev-pick]');
        key = pick && pick.value;
        if (!key) return;
        who = [p.entity_id];
      } else {
        key = b.dataset.dev;
        const now = access[key] || [];
        who = act === 'dev-free' ? [] : now.includes(b.dataset.who) ? now.filter((x) => x !== b.dataset.who) : [...now, b.dataset.who];
        // Nobody left would make it everyone's again: that's what ✕ is for.
        if (act === 'dev-who' && !who.length) {
          this._err = "A limited device needs someone. To make it everyone's again, use ✕.";
          this._sig = null;
          return this._render();
        }
      }
      if (who.length) access[key] = who;
      else delete access[key];
      const r = await this._send({ type: 'church_drive/people/access', key, people: who });
      if (r && r.access) this._data.access = r.access;
      this._sig = null;
      return this._render();
    }
    else if (act === 'pl-save') {
      const places = this._draft.filter((x) => x.zone).map((x) => ({ zone: x.zone, name: (x.name || '').trim() }));
      p.places = places;
      this._draft = null;
      return this._send({ type: 'church_drive/people/places', person: p.entity_id, places });
    } else if (act === 'phone') {
      const ph = (p.phones || []).find((x) => x.service === b.dataset.service);
      if (ph) ph.on = !ph.on;
      return this._send({ type: 'church_drive/people/phone', person: p.entity_id, service: b.dataset.service, on: !!(ph && ph.on) });
    } else if (act === 'car') {
      const set = new Set(p.cars || []);
      if (set.has(b.dataset.device)) set.delete(b.dataset.device);
      else set.add(b.dataset.device);
      p.cars = [...set];
      return this._send({ type: 'church_drive/people/cars', person: p.entity_id, cars: p.cars });
    } else if (act === 'kind' || act === 'arrive') {
      const key = act === 'kind' ? b.dataset.kind : `arrivals:${p.entity_id}`;
      const who = act === 'kind' ? p.entity_id : b.dataset.who;
      const k = this._data.kinds.find((x) => x.key === key);
      if (!k) return;
      const on = !this._gets(k, who);
      // Show it straight away (the integration's answer replaces this).
      if (k.all && !on) {
        k.all = false;
        k.people = this._data.people.map((x) => x.entity_id).filter((x) => x !== who);
      } else k.people = on ? [...(k.people || []), who] : (k.people || []).filter((x) => x !== who);
      return this._send({ type: 'church_drive/people/assign', kind: key, person: who, on });
    }
    this._sig = null;
    this._render();
  }

  // Upload the chosen picture (Home Assistant's image upload), then set it on the person.
  async _upload() {
    const file = this._file.files && this._file.files[0];
    this._file.value = '';
    const p = this._person();
    if (!file || !p || this.config.demo) return;
    this._busy = true;
    this._sig = null;
    this._render();
    try {
      const form = new FormData();
      form.append('file', file);
      const res = await this._hass.fetchWithAuth('/api/image/upload', { method: 'POST', body: form });
      if (!res.ok) throw new Error(res.status === 413 ? 'That picture is too big.' : "Couldn't upload that picture.");
      const img = await res.json();
      await this._setPicture(p, `/api/image/serve/${img.id}/512x512`);
    } catch (err) {
      this._err = (err && err.message) || "Couldn't upload that picture.";
    } finally {
      this._busy = false;
      this._sig = null;
      this._render();
    }
  }

  async _setPicture(p, picture) {
    if (this.config.demo) return;
    this._err = '';
    try {
      const list = await this._hass.connection.sendMessagePromise({ type: 'person/list' });
      const st = this._hass.states[p.entity_id];
      const items = (list && list.storage) || [];
      const item = items.find((x) => p.user_id && x.user_id === p.user_id) || items.find((x) => x.name === ((st && st.attributes.friendly_name) || p.name));
      if (!item) throw new Error(`${p.name} is set up in YAML, so their picture can't be changed here.`);
      await this._hass.connection.sendMessagePromise({
        type: 'person/update',
        person_id: item.id,
        name: item.name,
        user_id: item.user_id || null,
        device_trackers: item.device_trackers || [],
        picture,
      });
    } catch (err) {
      this._err = (err && err.message) || "Couldn't change the picture.";
    }
    this._sig = null;
    this._render();
  }

  getCardSize() {
    return 10;
  }

  getGridOptions() {
    return { columns: 'full', rows: 'auto' };
  }

  static getConfigElement() {
    return document.createElement(`people-manager-card-editor${SUFFIX}`);
  }

  static getStubConfig() {
    return {};
  }
}

export function registerPeopleManagerCard() {
  if (!customElements.get(`people-manager-card-editor${SUFFIX}`)) customElements.define(`people-manager-card-editor${SUFFIX}`, PeopleManagerCardEditor);
  if (!customElements.get(`people-manager-card${SUFFIX}`)) customElements.define(`people-manager-card${SUFFIX}`, PeopleManagerCard);
  window.customCards = window.customCards || [];
  window.customCards.push({
    type: `people-manager-card${SUFFIX}`,
    name: `People Manager Card${LABEL}`,
    description: "Each person's picture, places, phones, cars, alerts, to-dos and arrivals in one place",
    preview: true,
    documentationURL: 'https://github.com/J45PER/church-drive-cards#readme',
  });
}
