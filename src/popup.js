// Our own pop-up, for any card that needs one instead of Home Assistant's.
// Size aware: on a phone (up to 600px wide) it's a sheet that slides up from
// the bottom with a grab bar; on a tablet or PC it's a window in the middle.
//
//   const pop = openPopup(card, { title: 'New task', icon: 'mdi:plus', color: '#7e57c2', content: el, onClose });
//   pop.body       the scrolling area `content` was put in
//   pop.setTitle() change the heading
//   pop.close()    close it (onClose runs once, however it was closed); a promise
//                  that settles when it's fully gone, to open another straight after
//
// It's a native <dialog> shown with showModal(), so it sits above everything
// on the page (sidebar, nav bar, other cards' stacking) without being moved
// out of the card: the card's own CSS and the theme's variables still apply
// to what's inside. It closes with its ✕, a tap on the dimmed page, Escape,
// or the phone's Back button (it adds a history step while open).

import { iconHtml, hydrateIcons } from './icons.js';

const PHONE = '(max-width: 600px)';

const POP_CSS = `
  dialog.cd-pop { box-sizing:border-box; border:none; padding:0; margin:auto; width:min(520px, calc(100vw - 32px)); max-width:none; max-height:min(86dvh, 820px);
    border-radius:24px; overflow:hidden; display:flex; flex-direction:column;
    background:var(--ha-dialog-surface-background, var(--mdc-theme-surface, var(--card-background-color, #1f2128)));
    color:var(--primary-text-color); box-shadow:0 18px 50px rgba(0,0,0,0.45); font-family:var(--ha-font-family-body, inherit);
    animation:cd-pop-in 0.18s ease-out; }
  dialog.cd-pop:not([open]) { display:none; }
  dialog.cd-pop::backdrop { background:rgba(0,0,0,0.55); animation:cd-pop-fade 0.18s ease-out; }
  .cd-pop-grab { display:none; flex:none; width:40px; height:4px; border-radius:2px; background:rgba(127,127,127,0.45); margin:10px auto 0; }
  .cd-pop-drag { flex:none; touch-action:none; }
  @media (max-width: 600px) { .cd-pop-drag { cursor:grab; } }
  .cd-pop-head { flex:none; display:flex; align-items:center; gap:12px; padding:16px 12px 8px 18px; }
  .cd-pop-icon { flex:none; width:36px; height:36px; border-radius:50%; display:flex; align-items:center; justify-content:center; }
  .cd-pop-title { flex:1; min-width:0; font-size:1.1rem; font-weight:600; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
  .cd-pop-x { flex:none; width:40px; height:40px; border:none; border-radius:50%; background:transparent; color:var(--secondary-text-color); cursor:pointer; display:flex; align-items:center; justify-content:center; }
  .cd-pop-x:hover { background:rgba(127,127,127,0.15); }
  .cd-pop-x:focus-visible { outline:2px solid var(--primary-color); outline-offset:2px; }
  /* flex-basis auto: with 0 (flex:1), iPhones size the pop-up as if the body were empty. */
  .cd-pop-body { flex:1 1 auto; min-height:0; overflow:auto; overscroll-behavior:contain; padding:6px 18px 20px; }
  @media ${PHONE} {
    /* Pinned to the bottom (not pushed there by an auto margin): iPhones work
       the margin out once, so a pop-up that fills in after opening (cameras)
       grew off the bottom of the screen. */
    dialog.cd-pop { position:fixed; inset:auto 0 0 0; width:100vw; max-height:90dvh; margin:0; border-radius:24px 24px 0 0; animation:cd-sheet-in 0.22s ease-out; }
    .cd-pop-grab { display:block; }
    .cd-pop-head { padding-top:10px; }
    .cd-pop-body { padding-bottom:calc(20px + env(safe-area-inset-bottom, 0px)); }
  }
  @keyframes cd-pop-in { from { opacity:0; transform:scale(0.96); } }
  @keyframes cd-sheet-in { from { transform:translateY(100%); } }
  @keyframes cd-pop-fade { from { opacity:0; } }
  @media (prefers-reduced-motion: reduce) { dialog.cd-pop, dialog.cd-pop::backdrop { animation:none; } }
`;

export function openPopup(host, { title = '', icon = '', color = 'var(--primary-color)', content = null, onClose = null } = {}) {
  const d = document.createElement('dialog');
  d.className = 'cd-pop';
  d.setAttribute('aria-label', title);
  d.innerHTML = `<style>${POP_CSS}</style>
    <div class="cd-pop-drag"><div class="cd-pop-grab" aria-hidden="true"></div>
    <div class="cd-pop-head">
      ${icon ? `<div class="cd-pop-icon" style="background:color-mix(in srgb, ${color} 22%, transparent); color:${color};">${iconHtml(icon, { size: '20px' })}</div>` : ''}
      <div class="cd-pop-title"></div>
      <button type="button" class="cd-pop-x" aria-label="Close">${iconHtml('mdi:close', { size: '22px' })}</button>
    </div>
    </div><div class="cd-pop-body"></div>`;
  const body = d.querySelector('.cd-pop-body');
  const heading = d.querySelector('.cd-pop-title');
  heading.textContent = title;
  if (content) body.appendChild(content);
  host.appendChild(d);
  hydrateIcons(d);

  let closed = false;
  let pushed = false;
  const finish = () => {
    if (closed) return;
    closed = true;
    window.removeEventListener('popstate', onBack);
    if (d.open) d.close();
    d.remove();
    if (onClose) onClose();
  };
  // Back closes it; closing it any other way takes the extra history step back off.
  const onBack = () => {
    pushed = false;
    finish();
  };
  // Returns a promise that settles once the history step is gone, so another
  // pop-up can open straight after without the Back closing it too.
  const close = () => {
    if (closed) return Promise.resolve();
    const wasPushed = pushed;
    pushed = false;
    finish();
    if (!(wasPushed && history.state && history.state.cdPopup)) return Promise.resolve();
    return new Promise((resolve) => {
      const done = () => {
        window.removeEventListener('popstate', done);
        resolve();
      };
      window.addEventListener('popstate', done);
      setTimeout(done, 500);
      history.back();
    });
  };

  d.querySelector('.cd-pop-x').addEventListener('click', close);
  // Escape: let us close it (so onClose and history are handled the same way).
  d.addEventListener('cancel', (ev) => {
    ev.preventDefault();
    close();
  });
  // A tap on the dimmed page lands on the <dialog> itself, outside its box.
  let downOutside = false;
  const outside = (ev) => {
    if (ev.target !== d) return false;
    const r = d.getBoundingClientRect();
    return ev.clientX < r.left || ev.clientX > r.right || ev.clientY < r.top || ev.clientY > r.bottom;
  };
  d.addEventListener('pointerdown', (ev) => (downOutside = outside(ev)));
  d.addEventListener('click', (ev) => {
    if (downOutside && outside(ev)) close();
    downOutside = false;
  });

  // Phone sheet: drag the grab bar (or the title) down to close it, or up to
  // open it to full height.
  const drag = d.querySelector('.cd-pop-drag');
  let start = null;
  drag.addEventListener('pointerdown', (ev) => {
    if (!window.matchMedia(PHONE).matches || ev.target.closest('.cd-pop-x')) return;
    start = { y: ev.clientY, t: Date.now() };
    drag.setPointerCapture(ev.pointerId);
    d.style.transition = 'none';
  });
  drag.addEventListener('pointermove', (ev) => {
    if (!start) return;
    const dy = ev.clientY - start.y;
    d.style.transform = dy > 0 ? `translateY(${dy}px)` : '';
  });
  const release = (ev) => {
    if (!start) return;
    const dy = ev.clientY - start.y;
    const fast = Math.abs(dy) / Math.max(1, Date.now() - start.t) > 0.6;
    start = null;
    d.style.transition = 'transform 0.18s ease-out, height 0.18s ease-out';
    if (dy > 120 || (fast && dy > 50)) {
      d.style.transform = 'translateY(100%)';
      setTimeout(close, 170);
      return;
    }
    d.style.transform = '';
    if (dy < -40) d.style.height = '90dvh';
  };
  drag.addEventListener('pointerup', release);
  drag.addEventListener('pointercancel', release);

  d.showModal();
  try {
    history.pushState({ ...(history.state || {}), cdPopup: true }, '');
    pushed = true;
    window.addEventListener('popstate', onBack);
  } catch (_) {
    // No history (an editor preview): the other ways to close still work.
  }

  return {
    dialog: d,
    body,
    close,
    setTitle: (text) => {
      heading.textContent = text;
      d.setAttribute('aria-label', text);
    },
    get open() {
      return !closed;
    },
  };
}
