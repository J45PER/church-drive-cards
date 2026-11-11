// When the Manager site reads a page again by itself, so a page left open does not go stale. The same idea as the apps (Freshness.kt): back to the tab
// after a minute or more away, and every five minutes while it is open. Only the pages that just show things are refreshed (the editors are never
// redrawn under someone), and never while a field has focus, a dialog is open, or the page was touched in the last ten seconds.

/** Back to the tab after this long away reads the page again. */
export const AWAY_MS = 60_000;
/** An open page is read again this often. */
export const LIVE_EVERY_MS = 300_000;
/** Nothing is redrawn until the page has been left alone this long. */
export const IDLE_MS = 10_000;
/** How often the clock is looked at. */
export const TICK_MS = 30_000;

export const dueOnReturn = (now, hiddenAt) => hiddenAt > 0 && now - hiddenAt >= AWAY_MS;
export const duePeriodic = (now, loadedAt) => loadedAt > 0 && now - loadedAt >= LIVE_EVERY_MS;
export const idle = (now, lastTouched) => now - lastTouched >= IDLE_MS;

/** Whether something is being typed into or answered (a field with focus, or a dialog), which a redraw would wipe. [doc] is the document. */
export function editing(doc = globalThis.document) {
  const a = doc.activeElement;
  return !!((a && /^(INPUT|TEXTAREA|SELECT)$/.test(a.tagName)) || doc.querySelector('dialog[open], [role="dialog"]'));
}
