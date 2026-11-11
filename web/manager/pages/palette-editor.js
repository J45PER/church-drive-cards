// Editing a palette on the site: change its name and five colours with a live preview, then send it for checking (nothing changes in the house until
// Clive applies it, unless it is marked urgent). The house runs the same checks again.
import { HEX, PALETTE_PARTS, paletteWarnings, slugOk } from './logic.js';

const esc = (t) => String(t ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

/** Fills [host] with the editor for a palette. [key] null means a new palette. [onDone] runs after a proposal was sent. */
export function openPaletteEditor(host, ctx, { key, palette, onDone }) {
  const isNew = key == null;
  const colours = (palette?.colours || ['#5B54C9', '#2C2A6B', '#AEB6FF', '#E3E5FF', '#1B1F4A']).slice();
  host.innerHTML = `<form class="editor" novalidate>
    ${isNew ? '<label class="sub">Key (lower-case letters, digits and _)<input class="field" name="key" autocomplete="off" placeholder="my_palette"></label>' : ''}
    <label class="sub">Name<input class="field" name="name" value="${esc(palette?.name || '')}" maxlength="60"></label>
    <div class="parts">${PALETTE_PARTS.map((n, i) => `<label class="part">${n}<input type="color" name="c${i}" value="${esc(colours[i])}"><code>${esc(colours[i])}</code></label>`).join('')}</div>
    <div class="preview" aria-hidden="true"></div>
    <p class="warn" role="status"></p>
    <label class="sub">Note for Clive (optional)<input class="field" name="note" maxlength="400" placeholder="Why this change?"></label>
    <label class="sub"><input type="checkbox" name="urgent"> Urgent: apply straight away instead of waiting to be checked</label>
    <div class="chips"><button class="btn" type="submit">Send</button><button class="btn quiet" type="button" data-cancel>Cancel</button></div>
    <p class="note" data-msg role="status"></p></form>`;
  const form = host.querySelector('form');
  const read = () => PALETTE_PARTS.map((_, i) => form[`c${i}`].value);
  const paint = () => {
    const c = read();
    form.querySelectorAll('.part code').forEach((code, i) => { code.textContent = c[i]; });
    const w = HEX.test(c[0]) ? paletteWarnings(c) : [];
    form.querySelector('.warn').textContent = w.join(' ');
    form.querySelector('.preview').style.cssText = `background:${c[1]};color:#fff;--ga:${c[0]};--gb:${c[2]}`;
    form.querySelector('.preview').innerHTML = `<b>Sample card</b><span style="color:${c[3]}">Light text</span><i style="background:${c[0]}"></i><i style="background:${c[2]}"></i><i style="background:${c[4]}"></i>`;
  };
  form.addEventListener('input', paint); paint();
  host.querySelector('[data-cancel]').addEventListener('click', () => { host.innerHTML = ''; });
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const msg = form.querySelector('[data-msg]'); const c = read();
    const newKey = isNew ? form.key.value.trim() : key;
    if (isNew && !slugOk(newKey)) { msg.textContent = 'The key needs lower-case letters, digits and _, starting with a letter.'; return; }
    const item = { colours: c.map((x) => x.toUpperCase()) };
    if (form.name.value.trim()) item.name = form.name.value.trim();
    form.querySelector('button[type=submit]').disabled = true; msg.textContent = 'Sending…';
    try {
      const sent = await ctx.ha.call('church_drive/manager/propose', { kind: 'palette', change: { mode: 'set', palettes: { [newKey]: item } }, note: form.note.value.trim() || null, urgent: form.urgent.checked });
      msg.textContent = sent.status === 'applied' ? 'Applied.' : `Sent for checking${sent.checks?.length ? ` (the check noted: ${sent.checks.join(' ')})` : ''}. You can follow it on the Edits page.`;
      onDone?.(sent);
    } catch (err) {
      form.querySelector('button[type=submit]').disabled = false;
      msg.textContent = err.code === 'unknown_command' ? 'Home Assistant is running an older Church Drive without proposals. Update Church Drive in HACS, then restart Home Assistant.' : err.message;
    }
  });
}
