// Chats: what people asked Clive and what he answered, newest first, for administrators. Each chat can be marked good or bad with a note; the ones
// marked bad or noted are sent (through Edits) for Clive to check, and become changes to his prompt and rules. The house records them for 30 days.
import { h } from './dom.js';
import { circ, ic, pill, toneVars } from '../ui.js';
import { clock, dayLabel } from './logic.js';

export const MARKS = [['all', 'All'], ['unmarked', 'To review'], ['good', 'Good'], ['bad', 'Bad'], ['noted', 'With a note']];
const problem = (e) => (e?.code === 'unknown_command' ? 'Home Assistant is running an older Church Drive without chat recording. Update Church Drive in HACS, then restart Home Assistant.' : e?.message || 'Could not read the chats');

/** The chats grouped under their day, newest first. */
export function byDay(entries, now = new Date()) {
  const days = [];
  for (const e of entries) { const d = dayLabel(e.ts * 1000, now); const last = days[days.length - 1]; if (last && last[0] === d) last[1].push(e); else days.push([d, [e]]); }
  return days;
}
/** "Jamie · Kitchen satellite · 10:02 · 3.1 s": what is known about who, where, when and how long. */
export function chatLine(e) {
  return [e.who || 'Someone', e.device, clock(e.ts * 1000), e.seconds != null ? `${e.seconds.toFixed(1)} s` : null].filter(Boolean).join(' · ');
}

export async function renderChats(el, ctx) {
  const state = { mark: 'all' }; let data = null; let shown = 50; const msg = h('p', { class: 'note', role: 'status' });
  const load = async () => { data = await ctx.ha.call('church_drive/chats', { mark: state.mark === 'all' ? undefined : state.mark, limit: shown }); };
  const rate = async (chat, rating, note) => {
    try { Object.assign(chat, await ctx.ha.call('church_drive/chats/rate', { id: chat.id, rating: rating ?? null, ...(note != null ? { note } : {}) })); msg.textContent = ''; }
    catch (e) { msg.textContent = problem(e); }
  };
  const sendNotes = async () => {
    msg.textContent = 'Sending…';
    try {
      const due = (await ctx.ha.call('church_drive/chats', { mark: 'tosend', limit: 60 })).entries;
      if (!due.length) { msg.textContent = 'Nothing new to send.'; return; }
      const sent = await ctx.ha.call('church_drive/manager/propose', { kind: 'chats', change: { notes: due.map((c) => ({ id: c.id, rating: c.rating, note: c.note, asked: c.asked, answer: c.answer })) }, note: `${due.length} chat note${due.length === 1 ? '' : 's'}` });
      await ctx.ha.call('church_drive/chats/sent', { ids: due.map((c) => c.id) });
      msg.textContent = `Sent for checking${sent.checks?.length ? ` (the check noted: ${sent.checks.join(' ')})` : ''}. Follow it under Edits.`; await load(); draw();
    } catch (e) { msg.textContent = problem(e); }
  };
  const card = (c) => {
    const note = h('input', { class: 'field', value: c.note || '', placeholder: 'What should he do differently? (optional)', 'aria-label': 'Note on this chat', onchange: async (e) => { await rate(c, c.rating, e.target.value); draw(); } });
    const mark = (r, label) => h('button', { type: 'button', class: 'chip', 'aria-pressed': String(c.rating === r), onclick: async () => { await rate(c, c.rating === r ? null : r); draw(); } }, label);
    return h('article', { class: 'tcard glow chat', style: toneVars(ctx, c.rating === 'good' ? 'ok' : c.rating === 'bad' ? 'bad' : 'neutral') },
      h('p', { class: 'sub' }, chatLine(c)), h('p', { class: 'chat-q' }, c.asked), h('p', { class: 'chat-a' }, c.answer || 'No answer recorded'),
      h('div', { class: 'tagrow' }, (c.abilities || []).map((a) => h('span', { class: 'wc-p' }, a)), c.card ? h('span', { class: 'wc-p' }, `Card: ${c.card}`) : null),
      h('div', { class: 'chips' }, mark('good', 'Good'), mark('bad', 'Bad')), note);
  };
  function draw() {
    const counts = data.counts || {}, pills = [['mic-message', `<b>${counts.all || 0}</b> chat${counts.all === 1 ? '' : 's'} kept`, 'tasks'], ['eye', counts.unmarked ? `<b>${counts.unmarked}</b> to review` : 'All reviewed', counts.unmarked ? 'warn' : 'ok'], ['pencil', counts.to_send ? `<b>${counts.to_send}</b> note${counts.to_send === 1 ? '' : 's'} to send` : 'No notes to send', counts.to_send ? 'warn' : 'ok']];
    const top = h('div', { class: 'hls' }); top.innerHTML = pills.map(([i, t, k]) => pill(ctx, i, t, circ(ctx, k))).join('');
    const filters = h('div', { class: 'chips' }, MARKS.map(([k, label]) => h('button', { type: 'button', class: 'chip', 'aria-pressed': String(state.mark === k), onclick: async () => { state.mark = k; shown = 50; await load(); draw(); } }, `${label}${k !== 'all' && counts[k === 'unmarked' ? 'unmarked' : k] != null ? ` (${counts[k]})` : ''}`)),
      h('button', { type: 'button', class: 'btn', disabled: !counts.to_send, onclick: sendNotes }, counts.to_send ? `Send ${counts.to_send} note${counts.to_send === 1 ? '' : 's'} to Clive` : 'Send notes to Clive'));
    const body = !data.available ? h('article', { class: 'tcard glow', style: toneVars(ctx, 'warn') }, h('div', { class: 'wc-h' }, h('h2', {}, 'Chats are not being recorded'), h('span', { class: 'wc-p' }, 'Off')), h('p', { class: 'sub' }, `${data.reason || 'Home Assistant has no chat log to listen to.'} Nothing else is affected.`))
      : data.entries.length ? byDay(data.entries).map(([day, list]) => h('section', { class: 'stack', style: 'gap:8px' }, h('h2', { class: 'sh dayh2' }, day), list.map(card))) : h('p', { class: 'sub' }, state.mark === 'all' ? 'No chats recorded yet. They appear here as people talk to Clive.' : 'Nothing here.');
    const more = data.entries.length < data.total ? h('div', { class: 'chips' }, h('button', { type: 'button', class: 'chip', onclick: async () => { shown += 50; await load(); draw(); } }, `Show older (${data.entries.length} of ${data.total})`)) : null;
    el.replaceChildren(h('div', { class: 'stack' }, top, h('p', { class: 'sub' }, `What people said to Clive and what he answered, kept ${data.retention_days || 30} days for administrators. Mark a chat good or bad, add a note, then send the notes: Clive checks them and changes his prompt and rules through Edits.`), filters, msg, body, more));
    fillBody();
  }
  const fillBody = () => {};
  el.innerHTML = '<p class="note">Reading the chats from the house…</p>';
  try { await load(); } catch (e) { el.innerHTML = ''; el.append(h('p', { class: 'err', role: 'alert' }, `Could not read the chats: ${problem(e)}`)); return; }
  draw();
}
