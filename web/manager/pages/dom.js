// Small DOM helpers for the Manager pages: text from the house is never put into the page as HTML.

/** A small element builder, so text from the house is never put into the page as HTML. */
export function h(tag, attrs = {}, ...kids) {
  const e = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs || {})) {
    if (v == null || v === false) continue;
    if (k === 'class') e.className = v; else if (k.startsWith('on')) e.addEventListener(k.slice(2), v); else if (k === 'style') e.setAttribute('style', v); else e.setAttribute(k, v === true ? '' : v);
  }
  for (const kid of kids.flat(Infinity)) if (kid != null && kid !== false) e.append(kid.nodeType ? kid : document.createTextNode(String(kid)));
  return e;
}

/** A row of choices: [label, on, onClick, disabled]. */
export const chips = (items, cls = '') => h('div', { class: `chips ${cls}` }, items.map(([label, on, click, off]) =>
  h('button', { type: 'button', class: 'chip', 'aria-pressed': String(!!on), disabled: off, onclick: click }, label)));
export const soft = (t) => h('p', { class: 'sub' }, t);

