// The editors for cards, icons and layouts. Each sends a proposal (nothing changes until Clive has checked it): a card's palette, a dashboard
// panel's icon, or the order of a dashboard view's panels. Each proposal carries what it replaces, so it can be put back from the Edits page.
import { CARDS, CARD_GROUPS } from './cards-data.js';
import { chips, h } from './manager.js';
import { icon, iconNames, loadIcons } from '../icons.js';

const card_ = (...kids) => h('article', { class: 'card plain' }, ...kids);
const note = (t) => h('p', { class: 'sub' }, t);

async function propose(ctx, kind, change, noteText) {
  return ctx.ha.call('church_drive/manager/propose', { kind, change, note: noteText || null });
}
const sentNote = (sent) => `Sent for checking${sent.checks?.length ? ` (the check noted: ${sent.checks.join(' ')})` : ''}. Follow it under Edits.`;
const oldHouse = (e) => (e.code === 'unknown_command' ? 'Home Assistant is running an older Church Drive without these editors. Update Church Drive in HACS, then restart Home Assistant.' : e.message);

// ---- Cards: which palette each card uses ----

export async function renderCards(el, ctx) {
  el.replaceChildren(h('p', { class: 'note' }, 'Reading the palettes and picks from the house…'));
  let palettes, design;
  try { [palettes, design] = await Promise.all([ctx.ha.call('church_drive/colours'), ctx.ha.call('church_drive/manager/design')]); }
  catch (e) { el.replaceChildren(h('p', { class: 'err', role: 'alert' }, `Could not read the picks: ${oldHouse(e)}`)); return; }
  const keys = Object.entries(palettes.palettes || {}).sort((a, b) => a[1].name.localeCompare(b[1].name));
  const picks = design.cards || {}; const edits = {}; let filter = ''; const openGroups = new Set([CARD_GROUPS[0]]);
  const list = h('div', { class: 'stack' }); const msg = h('p', { class: 'note', role: 'status' }); const send = h('button', { class: 'btn', type: 'button' }, 'Send changes');
  const count = () => { const n = Object.keys(edits).length; send.disabled = !n; send.textContent = n ? `Send ${n} change${n === 1 ? '' : 's'}` : 'Send changes'; };
  const draw = () => {
    list.replaceChildren(...CARD_GROUPS.map((g) => {
      const cards = CARDS.filter((c) => c.group === g && (!filter || `${c.name} ${c.pages} ${c.ha} ${c.app}`.toLowerCase().includes(filter)));
      if (!cards.length) return null;
      return h('details', { class: 'fold', open: !!filter || openGroups.has(g), ontoggle: (e) => { e.target.open ? openGroups.add(g) : openGroups.delete(g); } }, h('summary', { class: 'head' }, h('h2', {}, g), h('span', { class: 'count' }, `${cards.length} cards`)),
        h('div', { class: 'grid4 fold-body' }, cards.map((c) => {
          const have = picks[c.id] || {}; const now = edits[c.id] || {};
          const palette = now.palette ?? have.palette ?? ''; const comment = now.comment ?? have.comment ?? '';
          const sel = h('select', { class: 'field', 'aria-label': `Palette for ${c.name}`, onchange: (e) => { setEdit(c.id, 'palette', e.target.value, have.palette); } },
            h('option', { value: '' }, `Not set${c.suggested ? ` (suggested: ${c.suggested})` : ''}`), keys.map(([k, p]) => h('option', { value: k, selected: k === palette }, `${p.name}${k === c.suggested ? ' · suggested' : ''}`)));
          const inp = h('input', { class: 'field', value: comment, placeholder: 'A note for Clive (optional)', 'aria-label': `Note on ${c.name}`, hidden: !comment, oninput: (e) => { setEdit(c.id, 'comment', e.target.value, have.comment || ''); } });
          const addNote = comment ? null : h('button', { class: 'btn quiet', type: 'button', onclick: (e) => { inp.hidden = false; e.currentTarget.remove(); inp.focus(); } }, 'Add a note');
          const sw = (keys.find(([k]) => k === palette)?.[1].colours || []).map((col) => h('i', { style: `background:${col}`, class: 'dot5' }));
          return h('article', { class: 'card plain compact' }, h('div', { class: 'row' }, h('h3', { class: 'title', style: 'margin:0;flex:1' }, c.name), c.status === 'available' ? h('span', { class: 'tag' }, 'Available') : null),
            note(c.pages), h('div', { class: 'dots' }, sw), sel, addNote, inp);
        })));
    }).filter(Boolean));
  };
  function setEdit(id, field, value, was) {
    const e = (edits[id] ||= {}); const base = (picks[id] || {})[field] || '';
    if (value === base || (field === 'palette' && value === '' && !base)) delete e[field]; else e[field] = value;
    if (field === 'palette' && value === '' && base) delete e[field]; // clearing a pick is not sent: a pick is changed to another, not removed
    if (!Object.keys(e).length) delete edits[id];
    count();
  }
  send.addEventListener('click', async () => {
    send.disabled = true; msg.textContent = 'Sending…';
    const cards = Object.fromEntries(Object.entries(edits).map(([id, e]) => [id, { ...(e.palette ? { palette: e.palette } : {}), ...(e.comment?.trim() ? { comment: e.comment.trim() } : {}) }]).filter(([, v]) => Object.keys(v).length));
    if (!Object.keys(cards).length) { msg.textContent = 'Nothing to send.'; count(); return; }
    try { const sent = await propose(ctx, 'cards', { cards }); Object.keys(edits).forEach((k) => delete edits[k]); msg.textContent = sentNote(sent); count(); }
    catch (e) { msg.textContent = oldHouse(e); count(); }
  });
  const find = h('input', { class: 'field', type: 'search', placeholder: 'Find a card, page or file', 'aria-label': 'Find a card', oninput: (e) => { filter = e.target.value.trim().toLowerCase(); draw(); } });
  el.replaceChildren(h('div', { class: 'stack' }, h('p', { class: 'note' }, 'Each card on the dashboards and in the apps has a colour theme. Pick a different one here and send it.'),
    h('div', { class: 'chips' }, find, send), msg, list));
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
  return h('div', { class: 'chips' }, dsel, vsel);
}

// ---- Icons: each panel's icon ----

export async function renderIcons(el, ctx) {
  await loadIcons(ctx.house);
  const body = h('div', { class: 'stack' }); const msg = h('p', { class: 'note', role: 'status' });
  const draw = (d, view) => {
    const panels = panelsOf(view);
    body.replaceChildren(...(panels.length ? panels.map((p) => {
      const slot = h('div', {});
      const row = h('article', { class: 'card plain compact' }, h('div', { class: 'row' }, h('span', { class: 'ico-box', html: '' }), h('div', { style: 'flex:1' }, h('p', { class: 'row-title' }, p.title), h('p', { class: 'sub' }, p.icon || 'no icon')),
        h('button', { class: 'btn quiet', type: 'button', onclick: () => picker(slot, p, d, view) }, 'Change')), slot);
      row.querySelector('.ico-box').innerHTML = icon(p.icon.startsWith('cd:') ? p.icon : 'cd:circle', 28);
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
  el.replaceChildren(h('div', { class: 'stack' }, h('p', { class: 'note' }, 'Every section of a dashboard has a small icon. Choose a dashboard page, then Change on a section to pick another from the house icon set.'), picked, msg, body));
}

// ---- Layouts: the order and visibility of a view's panels ----

export async function renderLayouts(el, ctx) {
  const body = h('div', { class: 'stack' }); const msg = h('p', { class: 'note', role: 'status' });
  const draw = (d, view) => {
    const before = panelsOf(view).map((p) => p.title); let order = before.slice(); let hidden = new Set();
    const redraw = () => {
      const changed = order.join('|') !== before.join('|') || hidden.size > 0;
      body.replaceChildren(...order.map((t, i) => h('article', { class: 'card plain compact' }, h('div', { class: 'row' },
        h('span', { style: `flex:1;${hidden.has(t) ? 'opacity:.5;text-decoration:line-through' : ''}` }, t),
        h('button', { class: 'btn quiet', type: 'button', 'aria-label': `Move ${t} up`, disabled: i === 0, onclick: () => { [order[i - 1], order[i]] = [order[i], order[i - 1]]; redraw(); } }, '↑'),
        h('button', { class: 'btn quiet', type: 'button', 'aria-label': `Move ${t} down`, disabled: i === order.length - 1, onclick: () => { [order[i + 1], order[i]] = [order[i], order[i + 1]]; redraw(); } }, '↓'),
        h('button', { class: 'chip', type: 'button', 'aria-pressed': String(hidden.has(t)), onclick: () => { hidden.has(t) ? hidden.delete(t) : hidden.add(t); redraw(); } }, 'Hide')))),
        h('div', { class: 'chips' }, h('button', { class: 'btn', type: 'button', disabled: !changed, onclick: async () => {
          msg.textContent = 'Sending…';
          try { const sent = await propose(ctx, 'layouts', { dashboard: d.path === 'lovelace' ? 'lovelace' : d.url, view: view.path, order, hidden: [...hidden], before: { order: before, hidden: [] } }); msg.textContent = sentNote(sent); }
          catch (e) { msg.textContent = oldHouse(e); }
        } }, 'Send this layout')));
    };
    if (!order.length) body.replaceChildren(h('p', { class: 'note' }, 'This view has no panels with titles.')); else redraw();
  };
  const picked = await pickView(ctx, body, draw);
  el.replaceChildren(h('div', { class: 'stack' }, h('p', { class: 'note' }, 'Choose a dashboard page, then move its sections up or down, or hide ones you do not want. The apps follow the same order.'), picked, msg, body));
}
