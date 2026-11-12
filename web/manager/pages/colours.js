// Colour palettes, as signed off on the example site: three header pills, every palette as a tone card (its five colours with their roles, and a sample of how the
// apps use it), and the editor as a floating frosted-glass window you can drag, showing the original and the new colours side by side with a way back.
import { HEX, PALETTE_PARTS, contrast, paletteWarnings, slugOk } from './logic.js';
import { circ, dragWindow, esc, ic, pill, readableOn, slug, toneVars } from '../ui.js';
import { fillIcons } from '../icons.js';
import { cleanup } from '../kit.js';
import { houseAddress } from '../ha.js';

const win = { x: null, y: 96 };
const edit = {};   // key -> the working colours, kept while the page is open

export async function renderColours(el, ctx) {
  cleanup(ctx);
  el.innerHTML = '<p class="note">Reading the palettes from the house…</p>';
  let data, shipped = {}, mine = {}, openKey = null, find = '', isNew = false, newKey = '', newName = '';
  try { data = await ctx.ha.call('church_drive/colours'); } catch (e) { el.innerHTML = `<p class="err" role="alert">Could not read the palettes: ${esc(e.message)}</p>`; return; }
  try { mine = (await ctx.ha.call('church_drive/manager/design')).groups || {}; } catch { /* groupings are optional: the palettes' own groups are used */ }
  for (const url of ['clive/cards.json', `${houseAddress()}/church_drive/clive/cards.json`]) { try { const r = await fetch(url); if (r.ok) { shipped = (await r.json()).entity_colours?.types || {}; break; } } catch { /* try the next place */ } }
  const palettes = () => Object.entries(data.palettes || {});
  const groupOf = ([key, p]) => mine[key] || p.group || 'Other';
  const changedFrom = (key, p) => !p.custom && shipped[key]?.colours && shipped[key].colours.join().toLowerCase() !== p.colours.join().toLowerCase();
  const original = (key) => (shipped[key]?.colours || data.palettes[key]?.colours || ['#5B54C9', '#2C2A6B', '#AEB6FF', '#E3E5FF', '#1B1F4A']).map((c) => c.toUpperCase());

  const sample = (p) => { const ac = readableOn(p[0], p[3], p[1]); return `<div class="use"><span class="use-pill" style="color:${ac};background:${ac}2e">Status</span><span class="use-btn" style="background:${ac};color:${p[1]}">Selected</span><span class="use-btn off">Not selected</span><span class="use-bar"><i style="background:${ac}"></i></span></div>`; };
  const card = ([key, p], groups) => {
    const c = p.colours;
    return `<article class="tcard glow pc" style="--d:${c[1]};--a:${c[0]};--b:${c[2]};--ac:${readableOn(c[0], c[3], c[1])}" id="pal-${esc(key)}"><div class="wc-h"><h2>${esc(p.name)}</h2>${changedFrom(key, p) ? '<span class="wc-p">Changed</span>' : ''}${p.custom ? '<span class="wc-p">Yours</span>' : ''}</div><code class="pk">${esc(key)}</code>
      <div class="swatches">${c.map((x, i) => `<div class="sw" title="${PALETTE_PARTS[i]} ${esc(x)}"><i style="background:${esc(x)}"></i><span>${PALETTE_PARTS[i]}</span><code>${esc(x)}</code></div>`).join('')}</div>${sample(c)}
      <div class="pcf"><select class="sel2" data-grp="${esc(key)}" aria-label="Group of ${esc(p.name)}">${groups.map((g) => `<option${g === groupOf([key, p]) ? ' selected' : ''}>${esc(g)}</option>`).join('')}<option value="__new">New group…</option></select><button class="btn s q on-tone" type="button" data-edit="${esc(key)}">${openKey === key ? 'Editing' : 'Edit'}</button></div></article>`;
  };
  const mini = (colours, label, state) => { const ac = readableOn(colours[0], colours[3], colours[1]); return `<article class="tcard glow wprev" style="--d:${colours[1]};--a:${colours[0]};--b:${colours[2]};--ac:${ac}"><div class="wc-h"><h2>${label}</h2>${state ? `<span class="wc-p">${state}</span>` : ''}</div><div class="use"><span class="use-pill" style="color:${ac};background:${ac}2e">Pill</span><span class="use-btn" style="background:${ac};color:${colours[1]}">Selected</span></div><div class="use-bar wide"><i style="background:${ac}"></i></div></article>`; };
  const previewBlock = (p, base) => (p.every((v) => HEX.test(v)) ? `<div class="wpair">${mini(base, 'Original', 'Shipped')}${mini(p, 'New', p.join() === base.join() ? 'Same' : 'Changed')}</div>` : '<p class="sub">A colour is not a full hex code yet (like #F2C464), so there is no preview.</p>');
  const winHtml = () => {
    if (!openKey && !isNew) return '';
    const key = isNew ? '' : openKey, base = isNew ? ['#5B54C9', '#2C2A6B', '#AEB6FF', '#E3E5FF', '#1B1F4A'] : original(key), p = (edit[key || '__new'] ||= [...(isNew ? base : data.palettes[key].colours.map((c) => c.toUpperCase()))]);
    const dirty = isNew || p.join() !== data.palettes[key].colours.map((c) => c.toUpperCase()).join(), valid = p.every((v) => HEX.test(v)), left = win.x == null ? Math.max(16, innerWidth - 510) : win.x;
    const c = valid ? contrast(p[0], p[1]) : null, name = isNew ? 'New palette' : data.palettes[key].name;
    return `<div class="win" id="win" role="dialog" aria-label="Edit ${esc(name)}" style="left:${left}px;top:${win.y}px"><div class="win-bar" id="win-bar"><span class="win-t"><b>${esc(name)}</b><code>${esc(key || 'a new palette')}</code></span><button type="button" class="win-x" data-wclose aria-label="Close">${ic('close', 18)}</button></div>
      <div class="win-body">${isNew ? `<label class="wfield">Key<input class="whex wtext" data-nk value="${esc(newKey)}" placeholder="my_palette" autocomplete="off" aria-label="Key"></label><label class="wfield">Name<input class="whex wtext" data-nn value="${esc(newName)}" maxlength="60" aria-label="Name"></label>` : ''}
      <div class="whead"><span></span><span>Original</span><span>New</span><span></span></div>${p.map((v, i) => { const o = base[i]; return `<div class="wrow"><span>${PALETTE_PARTS[i]}</span><span class="wold" title="Original ${o}"><i style="background:${o}"></i><code>${o}</code></span><span class="wnew"><input type="color" value="${HEX.test(v) ? v : '#888888'}" data-wc="${i}" aria-label="${PALETTE_PARTS[i]} colour"><input type="text" class="whex" value="${esc(v)}" data-wh="${i}" maxlength="7" spellcheck="false" aria-label="${PALETTE_PARTS[i]} hex"></span><button type="button" class="wr" data-wr="${i}" aria-label="Put ${PALETTE_PARTS[i]} back to ${o}" title="Put back to ${o}"${v.toUpperCase() === o ? ' disabled' : ''}>${ic('restore', 18)}</button></div>`; }).join('')}
      <div id="win-prev">${previewBlock(p, base)}</div>
      <p class="pw" id="win-warn">${valid ? esc(paletteWarnings(p).join(' ')) : ''}</p><input class="whex wtext" data-note placeholder="Note for Clive (optional)" maxlength="400" aria-label="Note for Clive"><label class="chk"><input type="checkbox" data-urgent> Urgent: apply straight away instead of waiting to be checked</label>
      <p class="note" data-msg role="status"></p><div class="btnrow"><button class="btn s q" type="button" data-wreset ${p.join() === base.join() ? 'disabled' : ''}>Put all back to the originals</button><button class="btn s" type="button" data-wsend ${valid && dirty ? '' : 'disabled'}>Send for checking</button></div></div></div>`;
  };
  const save = async (next) => { try { await ctx.ha.call('church_drive/manager/groups/set', { groups: next }); mine = next; draw(); } catch (e) { const m = el.querySelector('#group-msg'); if (m) m.textContent = `Could not save the groups: ${e.message}`; } };

  function draw() {
    const groups = new Map(); palettes().forEach((e) => { const g = groupOf(e); if (!groups.has(g)) groups.set(g, []); groups.get(g).push(e); });
    const names = [...groups.keys()], f = find.toLowerCase(), nChanged = palettes().filter(([k, p]) => changedFrom(k, p)).length;
    const pills = pill(ctx, 'palette', `<b>${palettes().length}</b> ${palettes().length === 1 ? 'palette' : 'palettes'}`, circ(ctx, 'tasks')) + pill(ctx, 'update', data.revision != null ? `Revision <b>${esc(data.revision)}</b>` : 'Revision not available', data.revision != null ? circ(ctx, 'ok') : circ(ctx, 'warn'))
      + pill(ctx, 'pencil', nChanged ? `<b>${nChanged}</b> changed from shipped` : 'None changed', nChanged ? circ(ctx, 'warn') : circ(ctx, 'ok'));
    const body = [...groups].map(([g, list]) => { const l = list.filter(([k, p]) => !f || `${p.name} ${k}`.toLowerCase().includes(f)); return l.length ? `<section class="stack" style="gap:8px" id="grp-${slug(g)}"><div class="ph"><h2 class="sh">${esc(g)}</h2><span class="sub">${l.length}</span><button class="btn s q" type="button" data-rename="${esc(g)}">Rename group</button></div><div class="hdg pgrid">${l.map((e) => card(e, names)).join('')}</div></section>` : ''; }).join('');
    el.innerHTML = `<div class="stack"><div class="hls">${pills}</div><div class="filters"><input class="field" id="pfind" type="search" placeholder="Find a palette" value="${esc(find)}" aria-label="Find a palette"><button class="btn" type="button" data-new>New palette</button></div><p class="err" id="group-msg" role="status"></p>${body || '<p class="sub">Nothing matches.</p>'}</div>${winHtml()}`;
    ctx.setSub?.(`<nav class="subdock" aria-label="Palette groups">${names.map((g) => `<button type="button" class="si2" data-gjump="${slug(g)}">${esc(g)}</button>`).join('')}</nav>`);
    fillIcons(el); bind();
  }
  function bind() {
    const pf = el.querySelector('#pfind'); pf.addEventListener('input', () => { find = pf.value.trim(); const at = pf.selectionStart; draw(); const n = el.querySelector('#pfind'); n.focus(); n.setSelectionRange(at, at); });
    el.querySelectorAll('[data-edit]').forEach((b) => b.addEventListener('click', () => { isNew = false; openKey = openKey === b.dataset.edit ? null : b.dataset.edit; draw(); }));
    el.querySelector('[data-new]').addEventListener('click', () => { isNew = true; openKey = null; delete edit.__new; newKey = ''; newName = ''; draw(); });
    el.querySelectorAll('[data-grp]').forEach((s) => s.addEventListener('change', () => {
      let g = s.value; if (g === '__new') { g = (prompt('Name of the new group') || '').trim(); if (!g) { draw(); return; } }
      save({ ...mine, [s.dataset.grp]: g });
    }));
    el.querySelectorAll('[data-rename]').forEach((b) => b.addEventListener('click', () => {
      const to = (prompt('New name for the group', b.dataset.rename) || '').trim(); if (!to || to === b.dataset.rename) return;
      const next = { ...mine }; palettes().filter((e) => groupOf(e) === b.dataset.rename).forEach(([key]) => { next[key] = to; }); save(next);
    }));
    const w = el.querySelector('#win'); if (!w) return;
    const k = isNew ? '__new' : openKey, key = isNew ? null : openKey, p = edit[k];
    const orig = original(key || ''), same = (i) => String(p[i]).toUpperCase() === orig[i];
    const upd = (from) => {   // update the window in place, so a colour picker being dragged and a field being typed in are not rebuilt
      w.querySelectorAll('[data-wc]').forEach((i) => { if (HEX.test(p[+i.dataset.wc]) && i !== from) i.value = p[+i.dataset.wc]; });
      w.querySelectorAll('[data-wh]').forEach((i) => { if (i !== from) i.value = p[+i.dataset.wh]; i.classList.toggle('bad', !HEX.test(p[+i.dataset.wh])); });
      w.querySelectorAll('[data-wr]').forEach((b) => { b.disabled = same(+b.dataset.wr); });
      const valid = p.every((v) => HEX.test(v)), dirty = isNew || p.join() !== data.palettes[key].colours.map((c) => c.toUpperCase()).join();
      w.querySelector('#win-prev').innerHTML = previewBlock(p, orig); fillIcons(w);
      w.querySelector('#win-warn').textContent = valid ? paletteWarnings(p).join(' ') : '';
      w.querySelector('[data-wsend]').disabled = !(valid && dirty); w.querySelector('[data-wreset]').disabled = p.join() === orig.join();
    };
    w.querySelectorAll('[data-wc]').forEach((i) => i.addEventListener('input', () => { p[+i.dataset.wc] = i.value.toUpperCase(); upd(i); }));
    w.querySelectorAll('[data-wh]').forEach((i) => i.addEventListener('input', () => { let v = i.value.trim(); if (v && v[0] !== '#') v = `#${v}`; p[+i.dataset.wh] = v.toUpperCase(); upd(i); }));
    w.querySelectorAll('[data-wr]').forEach((b) => b.addEventListener('click', () => { p[+b.dataset.wr] = original(key || '')[+b.dataset.wr]; draw(); }));
    w.querySelector('[data-wreset]').addEventListener('click', () => { edit[k] = [...original(key || '')]; draw(); });
    w.querySelector('[data-wclose]').addEventListener('click', () => { openKey = null; isNew = false; draw(); });
    w.addEventListener('keydown', (e) => { if (e.key === 'Escape') { openKey = null; isNew = false; draw(); } });
    w.querySelector('[data-nk]')?.addEventListener('input', (e) => { newKey = e.target.value.trim(); });
    w.querySelector('[data-nn]')?.addEventListener('input', (e) => { newName = e.target.value; });
    dragWindow(w, w.querySelector('#win-bar'), win);
    w.querySelector('[data-wsend]').addEventListener('click', async (e) => {
      const msg = w.querySelector('[data-msg]'), useKey = isNew ? newKey : key;
      if (isNew && !slugOk(useKey)) { msg.textContent = 'The key needs lower-case letters, digits and _, starting with a letter.'; return; }
      e.currentTarget.disabled = true; msg.textContent = 'Sending…';
      const item = { colours: p.map((x) => x.toUpperCase()) }; if (isNew && newName.trim()) item.name = newName.trim();
      try {
        const sent = await ctx.ha.call('church_drive/manager/propose', { kind: 'palette', change: { mode: 'set', palettes: { [useKey]: item } }, note: w.querySelector('[data-note]').value.trim() || null, urgent: w.querySelector('[data-urgent]').checked });
        msg.textContent = sent.status === 'applied' ? 'Applied.' : `Sent for checking${sent.checks?.length ? ` (the check noted: ${sent.checks.join(' ')})` : ''}. You can follow it on the Edits page.`;
      } catch (err) { e.currentTarget.disabled = false; msg.textContent = err.code === 'unknown_command' ? 'Home Assistant is running an older Church Drive without proposals. Update Church Drive in HACS, then restart Home Assistant.' : err.message; }
    });
  }
  /** Redraws the page and puts the cursor back where it was in the field being typed in. */
  function redrawKeepFocus(sel) { const at = document.activeElement?.selectionStart; draw(); const n = el.querySelector(sel); if (n && n.type !== 'color') { n.focus(); if (at != null) n.setSelectionRange(at, at); } }
  draw();
}
