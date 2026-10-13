// A phone running the Church Drive app is registered with Home Assistant on its own, next to the Home Assistant app on the
// same phone. In the Phones list they are one phone: the Church Drive app's registration is folded into the Home Assistant
// app's row (same model, not already paired), and its Alerts switch follows that row's.
export function groupPhones(phones) {
  const list = phones || [];
  const taken = new Set();
  const rows = [];
  for (const ph of list) {
    if (ph.own_app) continue;
    const mate = list.find((o) => o.own_app && !taken.has(o.service + o.tracker) && o.model && o.model === ph.model && !ph.apple);
    if (mate) taken.add(mate.service + mate.tracker);
    rows.push({ main: ph, also: mate || null });
  }
  // A Church Drive app phone with no Home Assistant app beside it is listed as it is.
  for (const ph of list) if (ph.own_app && !taken.has(ph.service + ph.tracker)) rows.push({ main: ph, also: null });
  return rows;
}
