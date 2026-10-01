// Auto Layout's arithmetic: sharing panels between columns, and choosing
// where automatically wide panels go. No page code, so it can be tested.

// Height of a set of columns. With `spanH`, panels that tall sit under
// every column but the tallest, so the block beside the tallest column is
// its tallest other column plus those panels.
function columnsCost(totals, gap, spanH) {
  const top = Math.max(...totals);
  if (spanH == null) return top;
  const at = totals.indexOf(top);
  const others = Math.max(0, ...totals.filter((t, j) => j !== at));
  return Math.max(top, others + (others > 0 ? gap : 0) + spanH);
}

// Share items between k columns so the tallest column is as short as
// possible. Each column keeps list order; column 1 starts with the first
// item. Tries every sharing for small pages (ties go to the one closest to
// list order), and falls back to "next item into the shortest column".
// With `spanH`, panels that tall go under the columns beside the tallest
// one, and the sharing makes room for them there.
// Returns an array of columns, each a list of item indexes.
export function balance(heights, k, gap, keep, spanH = null) {
  const n = heights.length;
  k = Math.max(1, Math.min(k, n));
  const totalsOf = (cols) => cols.map((c) => c.reduce((s, i) => s + heights[i], 0) + gap * Math.max(0, c.length - 1));
  const cost = (cols) => columnsCost(totalsOf(cols), gap, spanH);
  let best = null;
  let bestCost = Infinity;
  if (n <= 11) {
    // Restricted-growth labelling: each item joins a used column or opens
    // the next one, so each sharing is tried once.
    const lab = new Array(n).fill(0);
    const sums = new Array(k).fill(0);
    const counts = new Array(k).fill(0);
    const walk = (i, used) => {
      if (i === n) {
        if (used !== k) return;
        const c = columnsCost(sums.map((s, j) => s + gap * Math.max(0, counts[j] - 1)), gap, spanH);
        if (c < bestCost - 4) {
          bestCost = c;
          best = lab.slice();
        }
        return;
      }
      if (n - i < k - used) return;
      for (let j = 0; j <= Math.min(used, k - 1); j += 1) {
        sums[j] += heights[i];
        counts[j] += 1;
        lab[i] = j;
        const partial = sums[j] + gap * (counts[j] - 1);
        if (partial < bestCost - 4) walk(i + 1, Math.max(used, j + 1));
        sums[j] -= heights[i];
        counts[j] -= 1;
      }
    };
    walk(0, 0);
  }
  let cols;
  if (best) {
    cols = Array.from({ length: k }, () => []);
    best.forEach((j, i) => cols[j].push(i));
  } else {
    cols = Array.from({ length: k }, () => []);
    const sums = new Array(k).fill(0);
    heights.forEach((h, i) => {
      const j = sums.indexOf(Math.min(...sums));
      cols[j].push(i);
      sums[j] += h + gap;
    });
  }
  // Keep the current arrangement unless the new one is clearly better, so
  // small height changes don't shuffle the page.
  if (keep && keep.length === k && keep.flat().length === n && cost(keep) <= cost(cols) + 24) return keep;
  return cols;
}

// Lays out a page. Each item is { h, wide, noCol, full, hSpan, hFull,
// eCol, eSpan, eFull }: its height in one column, across every column but
// one, and across the page, and the blank space inside it at each width
// (e.g. a zone alone on its last row), as page height.
// A panel set to full width goes across the page below the columns. A panel
// that's automatically wide (zones, cameras) can go across the page too, or
// under the shorter columns beside the tallest one, or into a column. The page takes
// whichever is shortest with the least blank space. Being in a column costs
// a little extra, so wide panels stay wide when it's close; a panel set to
// wide (noCol) never goes into a column.
// Returns { cols, side, spans, full }: the columns (item indexes), which
// column the spanning panels sit beside, those panels, and the full-width
// ones below, in order. Keeps `prev` unless the
// new one is better by more than `margin`.
export function arrange(items, k, gap, prev, margin = 24) {
  const idx = items.map((it, i) => i);
  if (k <= 1) return { cols: [idx], side: null, spans: [], full: [] };
  const wide = idx.filter((i) => items[i].wide && !items[i].full);
  const cand = wide.slice(0, 4);
  const modesOf = (i) => {
    const m = k >= 3 ? ['full', 'col', 'span'] : ['full', 'col'];
    return items[i].noCol ? m.filter((x) => x !== 'col') : m;
  };
  const e = (i, key) => items[i][key] || 0;
  const acrossH = (list) => list.reduce((s, i) => s + gap + items[i].hFull + e(i, 'eFull'), 0);
  const spanHOf = (list) => (list.length ? list.reduce((s, i) => s + items[i].hSpan, 0) + gap * (list.length - 1) : null);
  const costOf = (a) => {
    const totals = a.cols.map((c) => c.reduce((s, i) => s + items[i].h, 0) + gap * Math.max(0, c.length - 1));
    const inCols = cand.filter((i) => a.cols.some((c) => c.includes(i))).length;
    const blank = a.cols.flat().reduce((s, i) => s + e(i, 'eCol'), 0) + a.spans.reduce((s, i) => s + e(i, 'eSpan'), 0);
    // Gaps under short columns that stretching (up to 160px) can't fill,
    // spread over the page's width.
    let left = 0;
    if (a.cols.length) {
      const band = columnsCost(totals, gap, spanHOf(a.spans));
      const block = a.spans.length ? Math.max(0, ...totals.filter((t, j) => j !== a.side)) : band;
      totals.forEach((t, j) => (left += Math.max(0, (a.spans.length && j !== a.side ? block : band) - t - 160)));
    }
    return (a.cols.length ? columnsCost(totals, gap, spanHOf(a.spans)) : -gap) + acrossH(a.full) + blank + left / k + 40 * inCols;
  };
  let best = null;
  const picks = [{}];
  cand.forEach((i) => {
    const next = [];
    picks.forEach((pk) => modesOf(i).forEach((m) => next.push({ ...pk, [i]: m })));
    picks.splice(0, picks.length, ...next);
  });
  for (const pick of picks) {
    const full = idx.filter((i) => items[i].full || pick[i] === 'full');
    const spans = idx.filter((i) => pick[i] === 'span');
    const rest = idx.filter((i) => !full.includes(i) && !spans.includes(i));
    if (spans.length && rest.length < k) continue;
    if (!rest.length) {
      const a = { cols: [], side: null, spans: [], full };
      const c = costOf(a);
      if (!best || c < best.cost - 4) best = { ...a, cost: c };
      continue;
    }
    const split = balance(rest.map((i) => items[i].h), k, gap, null, spanHOf(spans)).map((c) => c.map((j) => rest[j]));
    let side = null;
    if (spans.length) {
      const totals = split.map((c) => c.reduce((s, i) => s + items[i].h, 0) + gap * Math.max(0, c.length - 1));
      side = totals.indexOf(Math.max(...totals));
    }
    const a = { cols: split, side, spans, full };
    const c = costOf(a);
    if (!best || c < best.cost - 4) best = { ...a, cost: c };
  }
  const { cost, ...out } = best;
  const all = (a) => [...a.cols.flat(), ...(a.spans || []), ...(a.full || [])];
  const ok = (a) =>
    a &&
    Array.isArray(a.cols) &&
    all(a).sort((p, q) => p - q).join() === idx.join() &&
    (!a.cols.length || a.cols.length === Math.min(k, a.cols.flat().length)) &&
    items.every((it, i) => !it.full || a.full.includes(i));
  if (ok(prev) && costOf(prev) <= cost + margin) return prev;
  return out;
}
