// The editors for cards, icons and layouts. Each sends a proposal (nothing changes until Clive has checked it): a card's palette, a dashboard
// panel's icon, or the order of a dashboard view's panels. Each proposal carries what it replaces, so it can be put back from the Edits page.
import { CARDS, CARD_GROUPS } from './cards-data.js';
import { chips, h } from './manager.js';
import { icon, iconNames, loadIcons } from '../icons.js';
import { circ, ic, palette, pill, readableOn, toneVars } from '../ui.js';

const card_ = (...kids) => h('article', { class: 'card plain' }, ...kids);
const note = (t) => h('p', { class: 'sub' }, t);

async function propose(ctx, kind, change, noteText) {
  return ctx.ha.call('church_drive/manager/propose', { kind, change, note: noteText || null });
}
const sentNote = (sent) => `Sent for checking${sent.checks?.length ? ` (the check noted: ${sent.checks.join(' ')})` : ''}. Follow it under Edits.`;
const oldHouse = (e) => (e.code === 'unknown_command' ? 'Home Assistant is running an older Church Drive without these editors. Update Church Drive in HACS, then restart Home Assistant.' : e.message);

// ---- Look: which palette each card uses (a tone card per card, in its own colours) ----

const HEX = /^#[0-9a-f]{6}$/i;
const pillsFor = (ctx, items) => items.map(([icon, html, key]) => pill(ctx, icon, html, key ? circ(ctx, key) : null)).join('');

export async function renderCards(el, ctx) {
  el.replaceChildren(h('p', { class: 'note' }, 'Reading the palettes and picks from the house…'));
  let palettes, design;
  try { [palettes, design] = await Promise.all([ctx.ha.call('church_drive/colours'), ctx.ha.call('church_drive/manager/design')]); }
  catch (e) { el.replaceChildren(h('p', { class: 'err', role: 'alert' }, `Could not read the picks: ${oldHouse(e)}`)); return; }
  const pals = palettes.palettes || {}, keys = Object.entries(pals).sort((a, b) => a[1].name.localeCompare(b[1].name));
  const picks = design.cards || {}; const edits = {}; let filter = ''; const openGroups = new Set([CARD_GROUPS[0]]);
  const msg = h('p', { class: 'note', role: 'status' }), send = h('button', { class: 'btn', type: 'button' }, 'Send changes'), hls = h('div', { class: 'hls' });
  const paletteOf = (c) => edits[c.id]?.palette ?? picks[c.id]?.palette ?? '';
  const count = () => {
    const n = Object.keys(edits).length, unset = CARDS.filter((c) => !pals[paletteOf(c)]).length;
    send.disabled = !n; send.textContent = n ? `Send ${n} change${n === 1 ? '' : 's'}` : 'Send changes';
    hls.innerHTML = pillsFor(ctx, [['palette', `<b>${CARDS.length}</b> cards in ${CARD_GROUPS.length} groups`, 'tasks'], ['exclamation', unset ? `<b>${unset}</b> without a colour theme` : 'Every card has a theme', unset ? 'warn' : 'ok'], ['pencil', n ? `<b>${n}</b> change${n === 1 ? '' : 's'} to send` : 'Nothing to send', n ? 'warn' : 'ok']]);
  };
  const build = (c) => {
    const have = picks[c.id] || {}, now = edits[c.id] || {}, palette = paletteOf(c), comment = now.comment ?? have.comment ?? '', pal = pals[palette];
    const ac = pal && HEX.test(pal.colours[0]) ? readableOn(pal.colours[0], pal.colours[3], pal.colours[1]) : '#9AA4B2';
    const sel = h('select', { class: 'sel2', 'aria-label': `Palette for ${c.name}`, onchange: (e) => { setEdit(c.id, 'palette', e.target.value); art.replaceWith(build(c)); count(); } },
      h('option', { value: '' }, `Not set${c.suggested ? ` (suggested: ${c.suggested})` : ''}`), keys.map(([k, p]) => h('option', { value: k, selected: k === palette }, `${p.name}${k === c.suggested ? ' · suggested' : ''}`)));
    const inp = h('input', { class: 'field', value: comment, placeholder: 'A note for Clive (optional)', 'aria-label': `Note on ${c.name}`, hidden: !comment, oninput: (e) => { setEdit(c.id, 'comment', e.target.value); count(); } });
    const addNote = comment ? null : h('button', { class: 'btn s q on-tone', type: 'button', onclick: (e) => { inp.hidden = false; e.currentTarget.remove(); inp.focus(); } }, 'Add a note');
    const art = h('article', { class: 'tcard glow lk', style: toneVars(ctx, pal ? palette : 'neutral') },
      h('div', { class: 'wc-h' }, h('h2', {}, c.name), pal ? h('span', { class: 'wc-p', style: `color:${ac};background:${ac}2e` }, 'Status') : h('span', { class: 'wc-p' }, 'No theme')),
      h('p', { class: 'sub' }, `${c.pages}${c.status === 'available' ? ' · Available' : ''}`),
      h('div', { class: 'use' }, h('span', { class: 'use-btn', style: `background:${ac};color:${pal ? pal.colours[1] : '#14181F'}` }, 'Selected'), h('span', { class: 'use-btn off' }, 'Not selected'), h('span', { class: 'use-bar' }, h('i', { style: `background:${ac}` }))),
      sel, addNote, inp);
    return art;
  };
  const list = h('div', { class: 'stack' });
  const draw = () => {
    list.replaceChildren(...CARD_GROUPS.map((g) => {
      const cards = CARDS.filter((c) => c.group === g && (!filter || `${c.name} ${c.pages} ${c.ha} ${c.app}`.toLowerCase().includes(filter)));
      if (!cards.length) return null;
      return h('details', { class: 'cfold lkg', open: !!filter || openGroups.has(g), ontoggle: (e) => { e.target.open ? openGroups.add(g) : openGroups.delete(g); } },
        h('summary', { class: 'ph', style: 'cursor:pointer' }, h('h2', { class: 'sh' }, g), h('span', { class: 'sub' }, `${cards.length} cards`)), h('div', { class: 'hdg lkgrid' }, cards.map(build)));
    }).filter(Boolean));
  };
  function setEdit(id, field, value) {
    const e = (edits[id] ||= {}), base = (picks[id] || {})[field] || '';
    if (value === base || (field === 'palette' && value === '')) delete e[field]; else e[field] = value;   // clearing a pick is not sent: a pick is changed to another, not removed
    if (!Object.keys(e).length) delete edits[id];
  }
  send.addEventListener('click', async () => {
    send.disabled = true; msg.textContent = 'Sending…';
    const cards = Object.fromEntries(Object.entries(edits).map(([id, e]) => [id, { ...(e.palette ? { palette: e.palette } : {}), ...(e.comment?.trim() ? { comment: e.comment.trim() } : {}) }]).filter(([, v]) => Object.keys(v).length));
    if (!Object.keys(cards).length) { msg.textContent = 'Nothing to send.'; count(); return; }
    try { const sent = await propose(ctx, 'cards', { cards }); Object.keys(edits).forEach((k) => delete edits[k]); msg.textContent = sentNote(sent); draw(); count(); }
    catch (e) { msg.textContent = oldHouse(e); count(); }
  });
  const find = h('input', { class: 'field', type: 'search', placeholder: 'Find a card, page or file', 'aria-label': 'Find a card', oninput: (e) => { filter = e.target.value.trim().toLowerCase(); draw(); } });
  el.replaceChildren(h('div', { class: 'stack' }, hls, h('p', { class: 'sub' }, 'Each card on the dashboards and in the apps has a colour theme. Pick a different one here and send it.'), h('div', { class: 'filters' }, find, send), msg, list));
  draw(); count();
}

// ---- Dashboards: read a view's panels ----

async function dashboards(ctx) {
  const list = await ctx.ha.call('lovelace/dashboards/list').catch(() => []);
  return [{ url_path: null, id: 'lovelace', title: 'Overview' }, ...list.filter((d) => d.url_path)].map((d) => ({ path: d.url_path || 'lovelace', url: d.url_path, title: d.title || d.url_path }));
}
export function panelsOf(view) {
  const found = [];
  const walk = (n) => { if (Array.isArray(n)) n.forEach(walk); else if (n && typeof n === 'object') { if (n.type === 'custom:section-panel-card') found.push({ title: n.title || '', icon: n.icon || '' }); else Object.values(n).forEach(walk); } };
  walk(view?.sections ?? view?.cards ?? view);
  return found.filter((p) => p.title);
}
async function pickView(ctx, box, onView) {
  const ds = await dashboards(ctx);
  const dsel = h('select', { class: 'field', 'aria-label': 'Dashboard' }, ds.map((d) => h('option', { value: d.path }, d.title)));
  const vsel = h('select', { class: 'field', 'aria-label': 'View' });
  const load = async () => {
    const d = ds.find((x) => x.path === dsel.value);
    let config; try { config = await ctx.ha.call('lovelace/config', d.url ? { url_path: d.url } : {}); } catch (e) { box.replaceChildren(h('p', { class: 'err', role: 'alert' }, `Could not read that dashboard: ${e.message}`)); return; }
    const views = (config.views || []).filter((v) => v.path);
    vsel.replaceChildren(...views.map((v) => h('option', { value: v.path }, v.title || v.path)));
    vsel.onchange = () => onView(d, views.find((v) => v.path === vsel.value));
    if (views[0]) onView(d, views[0]); else box.replaceChildren(h('p', { class: 'note' }, 'That dashboard has no named views.'));
  };
  dsel.addEventListener('change', load);
  load();
  return h('div', { class: 'filters' }, dsel, vsel);
}

// ---- Icons: each panel's icon, a row per section drawn as a tone card ----

export async function renderIcons(el, ctx) {
  await loadIcons(ctx.house);
  const body = h('div', { class: 'stack' }), msg = h('p', { class: 'note', role: 'status' });
  const draw = (d, view) => {
    const panels = panelsOf(view);
    body.replaceChildren(...(panels.length ? panels.map((p) => {
      const slot = h('div', { style: 'flex:1 1 100%' });
      const shown = (p.icon || '').startsWith('cd:') ? p.icon : 'cd:circle';
      const row = h('article', { class: 'tcard glow irowc', style: toneVars(ctx, 'tasks') }, h('span', { class: 'wc-i', style: 'width:34px;height:34px' }), h('div', { class: 'rt2c' }, h('b', {}, p.title), h('small', {}, p.icon || 'No icon set')),
        h('div', { class: 'btnrow' }, h('button', { class: 'btn s q on-tone', type: 'button', onclick: () => picker(slot, p, d, view) }, 'Change')), slot);
      row.querySelector('.wc-i').innerHTML = icon(shown, 26);
      return row;
    }) : [h('p', { class: 'note' }, 'This view has no panels with titles.')]));
  };
  const picker = (slot, p, d, view) => {
    const names = iconNames(); let q = '';
    const grid = h('div', { class: 'icon-grid' });
    const fill = () => grid.replaceChildren(...names.filter((n) => n.includes(q)).slice(0, 120).map((n) => {
      const b = h('button', { type: 'button', class: 'icon-pick', title: n, 'aria-label': n, onclick: async () => {
        msg.textContent = 'Sending…';
        try { const sent = await propose(ctx, 'icons', { dashboard: d.path === 'lovelace' ? 'lovelace' : d.url, view: view.path, panel: p.title, icon: n, before: p.icon || undefined }); msg.textContent = `${p.title}: ${sentNote(sent)}`; slot.replaceChildren(); }
        catch (e) { msg.textContent = oldHouse(e); }
      } }); b.innerHTML = icon(n, 28); return b;
    }));
    slot.replaceChildren(h('input', { class: 'field', type: 'search', placeholder: names.length ? 'Find an icon' : 'The icon set is still loading', 'aria-label': 'Find an icon', oninput: (e) => { q = e.target.value.trim().toLowerCase(); fill(); } }), grid);
    fill();
  };
  const picked = await pickView(ctx, body, draw);
  el.replaceChildren(h('div', { class: 'stack' }, h('p', { class: 'sub' }, 'Every section of a dashboard has a small icon. Choose a dashboard page, then Change on a section to pick another from the house icon set.'), picked, msg, body));
}

// ---- Layout: drag a view's panels into order, or hide them ----

export async function renderLayouts(el, ctx) {
  const body = h('div', { class: 'stack' }), msg = h('p', { class: 'note', role: 'status' });
  const draw = (d, view) => {
    const before = panelsOf(view).map((p) => p.title); const hidden = new Set(); let order = before.slice();
    if (!before.length) { body.replaceChildren(h('p', { class: 'note' }, 'This view has no panels with titles.')); return; }
    const send = h('button', { class: 'btn', type: 'button', onclick: async () => {
      msg.textContent = 'Sending…';
      try { const sent = await propose(ctx, 'layouts', { dashboard: d.path === 'lovelace' ? 'lovelace' : d.url, view: view.path, order, hidden: [...hidden], before: { order: before, hidden: [] } }); msg.textContent = sentNote(sent); }
      catch (e) { msg.textContent = oldHouse(e); }
    } }, 'Send this layout');
    const list = h('div', { class: 'stack', style: 'gap:6px' });
    const changed = () => { send.disabled = order.join('|') === before.join('|') && !hidden.size; };
    const rows = () => order.map((t) => {
      const grip = h('span', { class: 'grip', 'aria-hidden': 'true', title: 'Drag to move' }, '⋮⋮');
      const eye = h('button', { class: 'mv', type: 'button', 'aria-label': `${hidden.has(t) ? 'Show' : 'Hide'} ${t}`, onclick: () => { hidden.has(t) ? hidden.delete(t) : hidden.add(t); fill(); } });
      eye.innerHTML = ic(hidden.has(t) ? 'eye' : 'eye-off', 18);
      const row = h('div', { class: `rowc dr${hidden.has(t) ? ' hid' : ''}`, 'data-t': t, style: `--tone:${palette(ctx, 'people')[0]}` }, grip, h('div', { class: 'rt' }, h('b', {}, t)), h('span', { class: `tagp${hidden.has(t) ? '' : ' ok'}` }, hidden.has(t) ? 'Hidden' : 'Shown'), eye);
      // Dragging by the grip works with a finger as well as a mouse: the row follows the pointer and is moved in the list as it passes the others.
      grip.addEventListener('pointerdown', (e) => {
        e.preventDefault(); grip.setPointerCapture(e.pointerId); row.classList.add('drag');
        const move = (m) => {
          const others = [...list.children].filter((r) => r !== row);
          const next = others.find((r) => { const b = r.getBoundingClientRect(); return m.clientY < b.top + b.height / 2; });
          if (next !== row.nextElementSibling) list.insertBefore(row, next || null);
        };
        const stop = () => { grip.removeEventListener('pointermove', move); grip.removeEventListener('pointerup', stop); grip.removeEventListener('pointercancel', stop); row.classList.remove('drag'); order = [...list.children].map((r) => r.dataset.t); changed(); };
        grip.addEventListener('pointermove', move); grip.addEventListener('pointerup', stop); grip.addEventListener('pointercancel', stop);
      });
      return row;
    });
    const fill = () => { list.replaceChildren(...rows()); changed(); };
    body.replaceChildren(list, h('p', { class: 'sub' }, 'Drag a row by its handle to move it.'), h('div', { class: 'btnrow' }, send));
    fill();
  };
  const picked = await pickView(ctx, body, draw);
  el.replaceChildren(h('div', { class: 'stack' }, h('p', { class: 'sub' }, 'Choose a dashboard page, then drag its sections into order, or hide ones you do not want. The apps follow the same order.'), picked, msg, body));
}
