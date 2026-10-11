// A page keeps the card code it loaded when it was opened. After Church Drive is updated, an open page (the phone app's
// especially) can carry on with the old cards until it is reloaded, which looks like the update didn't work. Each card
// calls checkStale: if Home Assistant says a newer Church Drive is installed than the one this page loaded, a small
// banner offers to reload.

/* global __CD_VERSION__ */
export const BUILT_VERSION = typeof __CD_VERSION__ !== 'undefined' ? __CD_VERSION__ : '';

const clean = (v) => String(v || '').trim().replace(/^v/i, '');
const isVersion = (v) => /^\d+\.\d+\.\d+$/.test(v);

// True when [installed] (what HACS says is installed, "v0.43.1") is a different release from [built] (this page's code).
export function isStale(installed, built) {
  const a = clean(installed);
  const b = clean(built);
  return isVersion(a) && isVersion(b) && a !== b;
}

let shown = false;

export function checkStale(hass) {
  if (shown || !BUILT_VERSION || !hass || !hass.states) return;
  const update = hass.states['update.church_drive_cards_update'];
  const installed = update && update.attributes && update.attributes.installed_version;
  if (!isStale(installed, BUILT_VERSION)) return;
  shown = true;
  const bar = document.createElement('div');
  bar.setAttribute('role', 'status');
  bar.style.cssText =
    'position:fixed; left:12px; right:12px; bottom:calc(env(safe-area-inset-bottom, 0px) + 96px); z-index:9998; display:flex; align-items:center; gap:10px; padding:10px 14px; border-radius:14px; background:#3949ab; color:#fff; font:600 0.85rem/1.3 sans-serif; box-shadow:0 6px 20px rgba(0,0,0,.5);';
  bar.innerHTML = `<span style="flex:1;">Church Drive was updated to ${clean(installed)}. This page is still on ${clean(BUILT_VERSION)}.</span><button type="button" style="border:none; border-radius:10px; padding:8px 12px; font:inherit; cursor:pointer;">Reload</button>`;
  bar.querySelector('button').addEventListener('click', () => location.reload());
  document.body.appendChild(bar);
}
