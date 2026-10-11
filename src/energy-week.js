// The plain rules behind the Octopus "Last 7 days" card (no page in here, so they can be tested alone).
//
// The Octopus Energy integration keeps every finished day as long-term statistics in Home Assistant (the ones the
// Energy dashboard uses), and fills in days Octopus delivers late. The card reads those: for each day, the cost and
// the kWh for electricity and gas, and the kWh used at the cheap (off-peak) and the other rates.

const DAY = 86400000;

// The statistic ids for the meters found by octoFind: { elec: 'octopus_energy_electricity_<mpan>_<serial>_', gas: … }.
export function weekStatIds(m) {
  const stat = (prefix, tail) => (prefix ? `octopus_energy:${String(prefix).replace(/^octopus_energy_/, '')}${tail}` : null);
  const ids = {
    elecCost: stat(m.elec, 'previous_accumulative_cost'),
    elecKwh: stat(m.elec, 'previous_accumulative_consumption'),
    offPeak: stat(m.elec, 'previous_accumulative_consumption_off_peak'),
    peak: stat(m.elec, 'previous_accumulative_consumption_peak'),
    standard: stat(m.elec, 'previous_accumulative_consumption_standard'),
    gasCost: stat(m.gas, 'previous_accumulative_cost'),
    gasKwh: stat(m.gas, 'previous_accumulative_consumption_kwh'),
  };
  return Object.fromEntries(Object.entries(ids).filter(([, v]) => v));
}

// A calendar day as YYYY-MM-DD in Home Assistant's time zone [tz] (the statistics are cut at its midnight), else the browser's.
export function dayKey(ms, tz) {
  return new Intl.DateTimeFormat('en-CA', { timeZone: tz || undefined, year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date(ms));
}

const addDays = (key, n) => {
  const [y, m, d] = key.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d + n, 12)).toISOString().slice(0, 10);
};

// The [n] days up to and including yesterday (today isn't finished), oldest first, as dates.
export function lastDays(now, n = 7, tz) {
  const today = dayKey(now, tz);
  const out = [];
  for (let i = n; i >= 1; i--) out.push(addDays(today, -i));
  return out;
}

// result: what recorder/statistics_during_period returns, { <statistic id>: [{ start, change }, …] } with a day period.
// Returns { days: [{ date, elecCost, elecKwh, gasCost, gasKwh, offPeak, peak, standard }], … } with null for a day
// Octopus hasn't delivered, plus the totals, the cheap share and how far each fuel is behind.
export function buildWeek(result, ids, now = Date.now(), n = 7, tz) {
  const byDay = {};
  for (const [name, id] of Object.entries(ids)) {
    for (const row of (result && result[id]) || []) {
      if (row == null || row.change == null || !Number.isFinite(Number(row.change))) continue;
      const key = dayKey(Number(row.start), tz);
      (byDay[key] = byDay[key] || {})[name] = Number(row.change);
    }
  }
  const dates = lastDays(now, n, tz);
  const days = dates.map((date) => {
    const d = byDay[date] || {};
    const has = (k) => (d[k] == null ? null : d[k]);
    return { date, elecCost: has('elecCost'), elecKwh: has('elecKwh'), gasCost: has('gasCost'), gasKwh: has('gasKwh'), offPeak: has('offPeak'), peak: has('peak'), standard: has('standard') };
  });
  const sum = (key) => days.reduce((s, d) => s + (d[key] == null ? 0 : d[key]), 0);
  const count = (key) => days.filter((d) => d[key] != null).length;
  const total = (d) => (d.elecCost || 0) + (d.gasCost || 0);
  const withData = days.filter((d) => d.elecCost != null || d.gasCost != null);
  const latest = (key) => {
    const hit = [...days].reverse().find((d) => d[key] != null);
    return hit ? hit.date : null;
  };
  const yesterday = dates[dates.length - 1];
  const behind = (date) => (date ? Math.round((Date.parse(`${yesterday}T12:00:00Z`) - Date.parse(`${date}T12:00:00Z`)) / DAY) : null);
  const cheapKwh = sum('offPeak');
  const otherKwh = sum('peak') + sum('standard');
  return {
    days,
    hasElec: !!ids.elecCost,
    hasGas: !!ids.gasCost,
    elecCost: sum('elecCost'),
    elecKwh: sum('elecKwh'),
    gasCost: sum('gasCost'),
    gasKwh: sum('gasKwh'),
    cost: withData.reduce((s, d) => s + total(d), 0),
    average: withData.length ? withData.reduce((s, d) => s + total(d), 0) / withData.length : null,
    // Share of electricity used at the cheap rate, when the tariff has cheap and other rates.
    cheapShare: cheapKwh + otherKwh > 0 && cheapKwh > 0 ? Math.round((cheapKwh / (cheapKwh + otherKwh)) * 100) : null,
    elecLatest: latest('elecCost'),
    gasLatest: latest('gasCost'),
    elecBehind: behind(latest('elecCost')),
    gasBehind: behind(latest('gasCost')),
    elecDays: count('elecCost'),
    gasDays: count('gasCost'),
    latestWithData: withData.length ? withData[withData.length - 1].date : null,
  };
}

// One day's cheap share, or null.
export function dayCheapShare(d) {
  const cheap = d.offPeak || 0;
  const other = (d.peak || 0) + (d.standard || 0);
  return cheap > 0 && cheap + other > 0 ? Math.round((cheap / (cheap + other)) * 100) : null;
}

// A note when a fuel is behind: "Electricity is 3 days behind (latest Wed 7 Oct)". Empty when all is current.
// [label] turns a YYYY-MM-DD into the words shown.
export function lagNote(week, label) {
  const one = (name, latest, behind) =>
    latest == null ? `${name}: nothing from Octopus in these days` : behind > 0 ? `${name} is ${behind} day${behind === 1 ? '' : 's'} behind (latest ${label(latest)})` : '';
  return [week.hasElec ? one('Electricity', week.elecLatest, week.elecBehind) : '', week.hasGas ? one('Gas', week.gasLatest, week.gasBehind) : ''].filter(Boolean).join(' · ');
}

// How the week's spending splits between electricity and gas, as whole percentages that add up to 100 (null if nothing was spent).
export function balance(week) {
  const total = week.elecCost + week.gasCost;
  if (!(total > 0)) return null;
  const elec = Math.round((week.elecCost / total) * 100);
  return { elec, gas: 100 - elec };
}
