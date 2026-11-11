// The Manager on the site: the sections of the house's Manager dashboard (People, Notifications, Camera links...), built on the integration's
// `church_drive/*` commands the apps use. Home Assistant checks every change is by an administrator.
import { alarmLights, watchSettings } from './alarm-watch.js';
import { canTick, kindGoesTo, kindGroups, linksOf, modeLabel, name, presenceWord, presetLabel, triggerChoices, validIconName, zoneRows } from './manager-logic.js';

import { chips, h, soft } from './dom.js';
export { chips, h };

export const BUILT = new Set(['custom:alarm-lights-card', 'custom:watch-settings-card', 'custom:notifications-card', 'custom:people-manager-card', 'custom:people-card', 'custom:zone-map-card', 'custom:camera-links-card',
  'custom:icon-styles-card', 'entities', 'heading', 'markdown', 'custom:device-health-card']);

/** The sections of the Manager dashboard: every Section Panel, with its cards (a stack's cards taken out of it). */
export function managerSections(config) {
  const found = [];
  const walk = (n) => { if (Array.isArray(n)) n.forEach(walk); else if (n && typeof n === 'object') { if (n.type === 'custom:section-panel-card') found.push(n); else Object.values(n).forEach(walk); } };
  walk(config?.views);
  const cards = (list) => (Array.isArray(list) ? list : []).flatMap((c) => (['vertical-stack', 'horizontal-stack', 'grid'].includes(c?.type) ? cards(c.cards) : c?.type ? [c] : []));
  return found.map((p) => ({ title: p.title || '', icon: p.icon || null, color: p.color || null, cards: cards(p.cards) })).filter((s) => s.title);
}
export const builtHere = (s) => s.cards.every((c) => BUILT.has(c.type));

const TONE = { people: '#1E4A6B', alarm: '#6B1E24', ok: '#1F5C3A', warn: '#6A4B12', tasks: '#2C2A6B', neutral: '#2B3340' };

async function loadSections(ctx) {
  const config = await ctx.ha.call('lovelace/config', { url_path: 'dashboard-manager' });
  return managerSections(config);
}
async function states(ctx) { return Object.fromEntries((await ctx.ha.call('get_states')).map((e) => [e.entity_id, e])); }

/** The Manager's front page: a card per section. */
export async function renderManager(el, ctx, helpers) {
  el.replaceChildren(h('p', { class: 'note' }, 'Reading the Manager from the house…'));
  let sections;
  try { sections = await loadSections(ctx); } catch (e) { el.replaceChildren(h('p', { class: 'err', role: 'alert' }, `Could not read the Manager: ${e.message}`)); return; }
  el.replaceChildren(h('div', { class: 'stack' },
    h('p', { class: 'note' }, 'The same sections as the Manager in the Church Drive app and in Clive Admin. Changes here are made straight away, as they are there.'),
    h('div', { class: 'grid' }, sections.map((s) => h('button', { type: 'button', class: 'card', style: helpers.glowStyle(TONE[s.color] || TONE.neutral), onclick: () => helpers.go(`manager/${encodeURIComponent(s.title)}`) },
      h('p', { class: 'title' }, s.title), h('p', { class: 'sub' }, builtHere(s) ? 'Open' : 'Not on the site yet: use the Church Drive app'))))));
}

/** One section's cards. */
export async function renderManagerSection(el, ctx, helpers, title) {
  el.replaceChildren(h('p', { class: 'note' }, 'Reading this section from the house…'));
  let section;
  try { section = (await loadSections(ctx)).find((s) => s.title === title); } catch (e) { el.replaceChildren(h('p', { class: 'err', role: 'alert' }, `Could not read the Manager: ${e.message}`)); return; }
  if (!section) { el.replaceChildren(h('p', { class: 'note' }, 'That section is not in the Manager any more.'), h('button', { class: 'btn quiet', type: 'button', onclick: () => helpers.go('manager') }, 'Back to the Manager')); return; }
  const out = h('div', { class: 'stack' }, h('button', { class: 'btn quiet back', type: 'button', onclick: () => helpers.go('manager') }, '‹ Manager'));
  el.replaceChildren(out);
  for (const card of section.cards) {
    const slot = h('div', { class: 'stack', style: 'gap:12px' }); out.append(slot);
    const draw = CARDS[card.type];
    if (!draw) { slot.append(card_(h('p', { class: 'title' }, 'Not on the site yet'), soft('This part is in the Church Drive app and Clive Admin for now.'))); continue; }
    try { await draw(slot, ctx, card, helpers); } catch (e) { slot.replaceChildren(h('p', { class: 'err', role: 'alert' }, `${presetLabel(card.type.split(':').pop())} could not load: ${e.message}`)); }
  }
}

const card_ = (...kids) => h('article', { class: 'card plain' }, ...kids);
const group = (titleText, ...kids) => card_(h('h3', { class: 'title' }, titleText), ...kids);

/** Draws a card from a command's answer, and again after each change it sends. */
async function live(slot, ctx, command, build, extra = {}) {
  const load = async () => build(await ctx.ha.call(command, extra), send, load);
  const send = async (type, params) => { try { await ctx.ha.call(type, params); } catch (e) { slot.prepend(h('p', { class: 'err', role: 'alert' }, e.message)); return; } slot.replaceChildren(...await load()); };
  slot.replaceChildren(...await load());
}

// ---- Notifications, People

const CARDS = {
  'custom:notifications-card': (slot, ctx) => live(slot, ctx, 'church_drive/people', (d, send) => {
    const people = d.people || [];
    const groups = kindGroups(d.kinds).map(([g, kinds]) => group(g, kinds.map((kind) => {
      const everyone = !!kind.all;
      return h('div', { class: 'kind' }, h('p', { class: 'row-title' }, kind.name + (kind.critical ? ' ⚠' : '')), kind.note && soft(kind.note),
        kind.available === false ? soft('Waiting for its devices to be set up')
          : [chips([['Everyone', everyone, () => send('church_drive/people/assign', { kind: kind.key, on: !everyone })]]),
            chips(people.filter((p) => canTick(kind, p)).map((p) => [p.first || p.name, kindGoesTo(kind, p.entity_id), () => send('church_drive/people/assign', { kind: kind.key, person: p.entity_id, on: !kindGoesTo(kind, p.entity_id) }), everyone]))]);
    })));
    const phones = group('Phones', soft('Notifications go to the phones that are switched on.'), people.map((p) => h('div', { class: 'kind' },
      h('p', { class: 'row-title' }, p.name + (p.admin ? ' · Admin' : '')), (p.phones || []).length ? chips(p.phones.map((ph) => [ph.name, ph.on, () => send('church_drive/people/phone', { person: p.entity_id, service: ph.service, on: !ph.on })])) : soft('No phones yet'))));
    return [...groups, phones];
  }),

  'custom:people-manager-card': async (slot, ctx) => {
    const st = await states(ctx);
    const zones = Object.values(st).filter((e) => e.entity_id.startsWith('zone.') && e.entity_id !== 'zone.home').sort((a, b) => name(a).localeCompare(name(b)));
    const editing = {};
    await live(slot, ctx, 'church_drive/people', (d, send, reload) => {
      const people = d.people || []; const kinds = d.kinds || [];
      const out = people.map((p) => {
        const id = p.entity_id; const places = (p.places || []).map((x) => [x.zone, x.name]);
        const kindChips = (list) => chips(list.filter((k) => k.available !== false && canTick(k, p)).map((k) => [k.name, kindGoesTo(k, id), () => send('church_drive/people/assign', { kind: k.key, person: id, on: !kindGoesTo(k, id) })]));
        const todoGroups = new Set(['To-dos', 'House jobs']);
        const alerts = kinds.filter((k) => !(k.group === 'People' && String(k.key).startsWith('arrivals:')) && !todoGroups.has(k.group));
        const arrivals = kinds.find((k) => k.key === `arrivals:${id}`); const others = people.filter((o) => o.entity_id !== id);
        const draft = editing[id];
        return card_(
          h('div', { class: 'row' }, h('h3', { class: 'title', style: 'margin:0;flex:1' }, p.name), h('span', { class: 'tag' }, presenceWord(p.home, p.place, p.zone))),
          h('p', { class: 'label' }, 'Phones'),
          (p.phones || []).length ? chips(p.phones.map((ph) => [ph.name, ph.on, () => send('church_drive/people/phone', { person: id, service: ph.service, on: !ph.on })])) : soft(`No phones yet. They join by themselves when the Home Assistant app signs in as ${p.first}.`),
          h('p', { class: 'label' }, 'Places'),
          !draft ? [places.length ? places.map(([z, n]) => h('p', {}, `${name(st[z]) || z} · ${n}`)) : soft(`Home is automatic. Add the places ${p.first} goes to.`),
            chips([['Edit places', false, () => { editing[id] = places; reload().then((n) => slot.replaceChildren(...n)); }]])]
            : [draft.map(([z, n], i) => h('div', { class: 'row' }, h('label', { style: 'flex:1' }, name(st[z]) || z,
              h('input', { class: 'field', value: n, 'aria-label': `${name(st[z]) || z}, called`, oninput: (e) => { draft[i][1] = e.target.value; } })),
              h('button', { class: 'btn quiet', type: 'button', 'aria-label': 'Take this place out', onclick: () => { draft.splice(i, 1); reload().then((x) => slot.replaceChildren(...x)); } }, '−'))),
              zones.filter((z) => !draft.some(([zz]) => zz === z.entity_id)).length ? [h('p', { class: 'label' }, 'Add a place'), chips(zones.filter((z) => !draft.some(([zz]) => zz === z.entity_id)).map((z) => [name(z), false, () => { draft.push([z.entity_id, name(z)]); reload().then((x) => slot.replaceChildren(...x)); }]))] : null,
              chips([['Save', true, () => { const list = draft.map(([zone, n]) => ({ zone, name: n.trim() })); delete editing[id]; send('church_drive/people/places', { person: id, places: list }); }],
                ['Cancel', false, () => { delete editing[id]; reload().then((x) => slot.replaceChildren(...x)); }]])],
          kindGroups(alerts).map(([g, list]) => [h('p', { class: 'label' }, `Alerts · ${g}`), kindChips(list)]),
          kinds.some((k) => todoGroups.has(k.group)) ? [h('p', { class: 'label' }, 'To-dos'), kindChips(kinds.filter((k) => todoGroups.has(k.group)))] : null,
          arrivals && others.length ? [h('p', { class: 'label' }, `Told when ${p.first} gets home or leaves`),
            chips(others.map((o) => [o.first || o.name, kindGoesTo(arrivals, o.entity_id), () => send('church_drive/people/assign', { kind: `arrivals:${id}`, person: o.entity_id, on: !kindGoesTo(arrivals, o.entity_id) })]))] : null,
          h('p', { class: 'sub' }, 'Cars and pictures are changed in the Church Drive app and in Home Assistant for now.'));
      });
      return out;
    });
  },

  'custom:people-card': async (slot, ctx) => {
    const st = await states(ctx);
    const people = Object.values(st).filter((e) => e.entity_id.startsWith('person.')).sort((a, b) => name(a).localeCompare(name(b)));
    slot.replaceChildren(card_(people.length ? people.map((p) => h('div', { class: 'row' }, h('span', { style: 'flex:1' }, name(p)), h('span', { class: 'tag' }, presenceWord(p.state)))) : soft('No people yet.')));
  },

  'custom:zone-map-card': async (slot, ctx) => {
    const rows = zoneRows(await states(ctx));
    slot.replaceChildren(card_(rows.length ? rows.map((z) => h('div', { class: 'kind' }, h('p', { class: 'row-title' }, z.name),
      soft([z.radius != null ? `${z.radius} m across` : null, z.people.length ? z.people.join(', ') : null].filter(Boolean).join(' · ') || ' '))) : soft('No zones yet.'),
    h('a', { class: 'btn quiet', href: `${ctx.house}/config/zone`, target: '_blank', rel: 'noopener' }, 'Edit zones on the map in Home Assistant')));
  },

  // ---- Camera links

  'custom:camera-links-card': async (slot, ctx) => {
    const st = await states(ctx); let mode = null;
    await live(slot, ctx, 'church_drive/camera/links', (d, send, reload) => {
      const cameras = (d.cameras || []).map((c) => [c.base, c.name]); const modes = d.modes || ['disarmed', 'home', 'away'];
      mode = modes.includes(mode) ? mode : (modes.includes(d.mode) ? d.mode : modes[0]);
      const set = (trigger, cams, secs) => send('church_drive/camera/links/set', cams ? { mode, trigger, cams, secs } : { mode, trigger });
      const links = linksOf(d.links, mode); const free = triggerChoices(st, new Set(links.map((l) => l.trigger)));
      const cooldown = d.cooldown ?? 120;
      return [
        group('Alarm mode', chips(modes.map((m) => [modeLabel(m) + (m === d.mode ? ' (now)' : ''), m === mode, () => { mode = m; reload().then((x) => slot.replaceChildren(...x)); }])), soft(`Choose what records in ${modeLabel(mode)}.`)),
        ...links.map((l) => group(name(st[l.trigger]) || l.trigger,
          chips(cameras.map(([base, n]) => [n, l.cams.includes(base), () => set(l.trigger, l.cams.includes(base) ? l.cams.filter((c) => c !== base) : [...l.cams, base], l.secs)])),
          h('div', { class: 'row' }, h('span', { style: 'flex:1' }, 'Records for'), h('button', { class: 'btn quiet', type: 'button', 'aria-label': 'Shorter', onclick: () => set(l.trigger, l.cams, Math.max(5, l.secs - 5)) }, '−'),
            h('span', {}, `${l.secs} s`), h('button', { class: 'btn quiet', type: 'button', 'aria-label': 'Longer', onclick: () => set(l.trigger, l.cams, Math.min(120, l.secs + 5)) }, '+')),
          chips([[`Take out of ${modeLabel(mode)}`, false, () => set(l.trigger)]]))),
        free.length && cameras.length ? group('Add a trigger', chips(free.slice(0, 40).map((e) => [name(e), false, () => set(e.entity_id, [cameras[0][0]], 30)]))) : null,
        group('Wait between recordings', soft("A camera won't record again for this long"), h('div', { class: 'row' }, h('span', { style: 'flex:1' }, `${cooldown} s`),
          h('button', { class: 'btn quiet', type: 'button', 'aria-label': 'Shorter', onclick: () => send('church_drive/camera/settings', { cooldown: Math.max(30, cooldown - 30) }) }, '−'),
          h('button', { class: 'btn quiet', type: 'button', 'aria-label': 'Longer', onclick: () => send('church_drive/camera/settings', { cooldown: Math.min(3600, cooldown + 30) }) }, '+'))),
      ].filter(Boolean);
    });
  },

  // ---- Icons

  'custom:icon-styles-card': async (slot, ctx) => {
    let open = null, text = '';
    await live(slot, ctx, 'church_drive/icons', (d, send, reload) => {
      const icons = d.icons || {}; const defaults = d.defaults || {}; const names = d.groups || {};
      const redraw = () => reload().then((x) => slot.replaceChildren(...x));
      return Object.entries(icons).map(([g, slots]) => group(names[g] || presetLabel(g), Object.entries(slots).map(([key, icon]) => {
        const isOpen = open && open[0] === g && open[1] === key;
        return h('div', { class: 'kind' }, h('div', { class: 'row' }, h('div', { style: 'flex:1' }, h('p', { class: 'row-title' }, presetLabel(key)), h('p', { class: 'sub' }, icon)),
          h('button', { class: 'btn quiet', type: 'button', onclick: () => { open = isOpen ? null : [g, key]; text = icon; redraw(); } }, isOpen ? 'Close' : 'Change')),
          isOpen ? [h('input', { class: 'field', value: text, placeholder: 'Icon, like mdi:fan', 'aria-label': 'Icon, like mdi:fan', oninput: (e) => { text = e.target.value; e.target.setAttribute('aria-invalid', String(!validIconName(text))); } }),
            chips([['Save', true, () => { if (validIconName(text)) { open = null; send('church_drive/icons/set', { group: g, key, icon: text.trim() }); } }],
              ['Built-in', false, () => { open = null; send('church_drive/icons/set', { group: g, key }); }]]), soft(`Built-in is ${defaults[g]?.[key] ?? ''}`)] : null);
      })));
    });
  },

  // ---- Settings, headings, text

  entities: async (slot, ctx, card) => {
    const st = await states(ctx);
    const rows = (card.entities || []).map((x) => (typeof x === 'string' ? { entity: x } : x));
    const call = async (domain, service, entity_id, data) => { try { await ctx.ha.call('call_service', { domain, service, service_data: data, target: { entity_id } }); } catch (e) { slot.prepend(h('p', { class: 'err', role: 'alert' }, e.message)); } };
    slot.replaceChildren(card_(rows.map((r) => {
      const e = st[r.entity]; if (!e) return null; const label = r.name || name(e); const id = e.entity_id;
      let control;
      if (id.startsWith('input_select.') || id.startsWith('select.')) control = h('select', { class: 'field', 'aria-label': label, onchange: (ev) => call(id.split('.')[0], 'select_option', id, { option: ev.target.value }) }, (e.attributes.options || []).map((o) => h('option', { selected: o === e.state }, o)));
      else if (id.startsWith('input_number.')) control = h('input', { class: 'field', type: 'number', min: e.attributes.min, max: e.attributes.max, step: e.attributes.step || 1, value: e.state, 'aria-label': label, onchange: (ev) => call('input_number', 'set_value', id, { value: Number(ev.target.value) }) });
      else if (id.startsWith('input_datetime.') && e.attributes.has_time && !e.attributes.has_date) control = h('input', { class: 'field', type: 'time', value: String(e.state).slice(0, 5), 'aria-label': label, onchange: (ev) => call('input_datetime', 'set_datetime', id, { time: `${ev.target.value}:00` }) });
      else if (id.startsWith('input_boolean.') || id.startsWith('switch.')) control = chips([[e.state === 'on' ? 'On' : 'Off', e.state === 'on', () => call(id.split('.')[0], 'toggle', id).then(() => { e.state = e.state === 'on' ? 'off' : 'on'; CARDS.entities(slot, ctx, card); })]]);
      else control = h('span', {}, `${e.state}${e.attributes.unit_of_measurement ? ` ${e.attributes.unit_of_measurement}` : ''}`);
      return h('div', { class: 'row setting' }, h('span', { style: 'flex:1' }, label), control);
    })));
  },

  'custom:alarm-lights-card': alarmLights,
  'custom:watch-settings-card': watchSettings,

  heading: async (slot, ctx, card) => { slot.replaceChildren(h('h3', { class: 'head-sub' }, card.heading || card.title || '')); },
  markdown: async (slot, ctx, card) => { slot.replaceChildren(h('p', { class: 'note' }, String(card.content || '').replace(/\*\*|__|`/g, ''))); },

  'custom:device-health-card': async (slot, ctx, card, helpers) => {
    slot.replaceChildren(card_(h('p', { class: 'title' }, 'Watched devices'), soft('Batteries, filters and devices that stopped answering.'),
      h('button', { class: 'btn quiet', type: 'button', onclick: () => helpers.go('health') }, 'Open Device health')));
  },
};
