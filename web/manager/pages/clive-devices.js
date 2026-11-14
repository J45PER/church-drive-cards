// His devices: the phones with the Church Drive app and the voice satellites he answers on, with their version, when each was last heard and whether it is answering.
// Read from the house (church_drive/clive/devices); no pairing from the site.
import { ago } from './logic.js';
import { h } from './dom.js';
import { circ, ic, pill, toneVars } from '../ui.js';

const problem = (e) => (e?.code === 'unknown_command' ? 'Home Assistant is running an older Church Drive without this list. Update Church Drive in HACS, then restart Home Assistant.' : e?.message || 'Could not read the devices');

/** One device as a row of a tone card: green when it is answering, red when it is not. */
export function deviceRow(d, now = Date.now()) {
  const seen = d.seen ? Date.parse(d.seen) : d.changed ? Date.parse(d.changed) : null;
  const where = [d.kind === 'phone' ? d.who : d.area, d.model].filter(Boolean).join(' · ');
  const detail = [d.version ? `Version ${d.version}` : null, d.kind === 'satellite' ? d.state : null, seen && Number.isFinite(seen) ? (d.kind === 'phone' ? `heard ${ago(seen, now)}` : `since ${ago(seen, now)}`) : (d.kind === 'phone' ? 'not heard' : null)].filter(Boolean).join(' · ');
  return { title: d.name || d.entity_id || 'Device', where, detail, answering: d.answering, icon: d.kind === 'phone' ? 'device-phone' : 'mic', tag: d.answering ? 'Answering' : 'Not answering' };
}

export async function renderCliveDevices(el, ctx) {
  el.innerHTML = '<p class="note">Reading his devices from the house…</p>';
  let data;
  try { data = await ctx.ha.call('church_drive/clive/devices'); } catch (e) { el.replaceChildren(h('p', { class: 'err', role: 'alert' }, `Could not read his devices: ${problem(e)}`)); return; }
  const phones = data.phones || [], sats = data.satellites || [], all = [...phones, ...sats], off = all.filter((d) => !d.answering).length;
  const group = (title, icon, list, empty) => {
    const rows = list.map((d) => deviceRow(d));
    const tone = rows.some((r) => !r.answering) ? 'warn' : 'ok';
    return `<article class="tcard glow wc" style="${toneVars(ctx, tone)}"><div class="wc-h"><span class="wc-i">${ic(icon, 26)}</span><h2>${title}</h2><span class="wc-p">${list.length}</span></div>
      ${rows.length ? rows.map((r) => `<div class="mr"><span class="mi">${ic(r.icon, 22)}</span><div class="mt"><b></b><small></small></div><span class="wc-p"></span></div>`).join('') : `<p class="sub"><span class="na">${empty}</span></p>`}</article>`;
  };
  const box = h('div', { class: 'stack' });
  box.innerHTML = `<div class="hls">${pill(ctx, 'watch', `<b>${all.length}</b> devices`, circ(ctx, 'media'))}${pill(ctx, off ? 'alert' : 'check', off ? `<b>${off}</b> not answering` : 'All answering', circ(ctx, off ? 'warn' : 'ok'))}${pill(ctx, 'mic', data.wake_word ? `Wake word <b></b>` : 'Wake word not available', circ(ctx, 'tasks'))}</div>
    <div class="hdg devgrid">${group('Phones', 'device-phone', phones, 'No phone with the Church Drive app is known to the house.')}${group('Satellites', 'mic', sats, 'No voice satellite found.')}</div>
    <p class="sub">Watches report through their phone. Pairing is done in the Satellites app, not here.</p>`;
  // The names and figures are put in as text, never as HTML.
  const fill = (sel, list) => box.querySelectorAll(sel).forEach((row, i) => { const r = deviceRow(list[i]); row.querySelector('b').textContent = r.title; row.querySelector('small').textContent = [r.where, r.detail].filter(Boolean).join(' · '); row.querySelector('.wc-p').textContent = r.tag; row.style.opacity = r.answering ? '' : '.8'; });
  const cards = box.querySelectorAll('.hdg > article');
  if (cards[0]) fill('.hdg > article:nth-child(1) .mr', phones);
  if (cards[1]) fill('.hdg > article:nth-child(2) .mr', sats);
  const wake = box.querySelector('.hls .hl:last-child b'); if (wake && data.wake_word) wake.textContent = String(data.wake_word).replace(/_/g, ' ');
  el.replaceChildren(box);
}
