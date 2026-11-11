// The rest of the Energy page, ported from the apps (EnergyWeek.kt, EnergyKit.kt): the Last 7 days card, the car charger, the cars and Octopus' charging plan.
// The rules are plain functions (tested in test/manager-web.test.mjs); the drawing is at the bottom.
import { chips, h, soft } from './dom.js';
import { fact, toneCard } from '../kit.js';

// ---- The week ----
const RATE = (kind) => new RegExp(`^sensor\\.octopus_energy_${kind}_.*_current_rate$`);
const find = (states, re) => Object.keys(states).find((id) => re.test(id));
const DAY = 86400000;

/** The statistic ids for the meters (their account numbers differ), from the two `current_rate` sensors. */
export function weekIds(states) {
  const stat = (entity, tail) => (entity ? `octopus_energy:${entity.replace(/^sensor\.octopus_energy_/, '').replace(/current_rate$/, '')}${tail}` : null);
  const e = find(states, RATE('electricity')), g = find(states, RATE('gas'));
  return { elecCost: stat(e, 'previous_accumulative_cost'), elecKwh: stat(e, 'previous_accumulative_consumption'), offPeak: stat(e, 'previous_accumulative_consumption_off_peak'),
    peak: stat(e, 'previous_accumulative_consumption_peak'), standard: stat(e, 'previous_accumulative_consumption_standard'), gasCost: stat(g, 'previous_accumulative_cost'), gasKwh: stat(g, 'previous_accumulative_consumption_kwh') };
}
export const weekStatIds = (ids) => Object.values(ids).filter(Boolean);
const dayKey = (ms) => new Date(ms).toLocaleDateString('en-CA'); // yyyy-mm-dd in the browser's own zone

/** [result] is recorder/statistics_during_period for a day period: { id: [{start (ms), change}] }. Gives the seven days up to yesterday, oldest first. */
export function buildWeek(result, ids, now = Date.now(), n = 7) {
  const series = (id) => { const m = new Map(); for (const r of (id && result?.[id]) || []) if (r.change != null && Number.isFinite(r.change) && r.start != null) m.set(dayKey(r.start), r.change); return m; };
  const s = { ec: series(ids.elecCost), ek: series(ids.elecKwh), gc: series(ids.gasCost), gk: series(ids.gasKwh), off: series(ids.offPeak), pk: series(ids.peak), st: series(ids.standard) };
  const days = [];
  for (let i = n; i >= 1; i--) {
    const d = now - i * DAY, k = dayKey(d);
    days.push({ key: k, date: new Date(d), elecCost: s.ec.get(k) ?? null, elecKwh: s.ek.get(k) ?? null, gasCost: s.gc.get(k) ?? null, gasKwh: s.gk.get(k) ?? null, offPeak: s.off.get(k) ?? null, peak: s.pk.get(k) ?? null, standard: s.st.get(k) ?? null });
  }
  for (const d of days) { d.total = (d.elecCost ?? 0) + (d.gasCost ?? 0); d.hasData = d.elecCost != null || d.gasCost != null; }
  const withData = days.filter((d) => d.hasData), sum = (f) => days.reduce((a, d) => a + (f(d) ?? 0), 0);
  const cost = withData.reduce((a, d) => a + d.total, 0);
  const elecCost = sum((d) => d.elecCost), gasCost = sum((d) => d.gasCost);
  const total = elecCost + gasCost, elecPct = total > 0 ? Math.round((elecCost / total) * 100) : null;
  return { days, cost, average: withData.length ? cost / withData.length : null, elecCost, gasCost, elecKwh: sum((d) => d.elecKwh), gasKwh: sum((d) => d.gasKwh),
    balance: elecPct == null ? null : { elec: elecPct, gas: 100 - elecPct }, latest: withData.length ? withData[withData.length - 1] : null };
}
export const pounds = (v) => (v == null ? '–' : `£${v.toFixed(2)}`);
export const kwh = (v) => (v == null ? '' : `${v.toFixed(1)} kWh`);
const dayName = (d, long) => d.toLocaleDateString('en-GB', long ? { weekday: 'short', day: 'numeric', month: 'short' } : { weekday: 'short' });

// ---- The charger, the plan and the cars ----
export const ZAPPI = { mode: 'select.zappi_charge_mode', power: 'sensor.zappi_charging_power', session: 'sensor.zappi_charge_added_session', plug: 'sensor.zappi_plug_status', status: 'sensor.zappi_status', role: 'input_select.zappi_car_role' };
export const CHARGER_MODES = [['Stopped', 'Stop'], ['Eco', 'Eco'], ['Eco+', 'Eco+'], ['Fast', 'Fast']];

/** "Charging · 7.2 kW", or what the plug says. */
export function chargerStatus(states) {
  const mode = states[ZAPPI.mode], power = Number(states[ZAPPI.power]?.state) || 0;
  if (!mode) return 'Loading…';
  if (power > 0) return `Charging · ${(power / 1000).toFixed(1)} kW`;
  return states[ZAPPI.plug]?.state || states[ZAPPI.status]?.state || 'Idle';
}

/** Octopus' planned charging slots still to come, soonest first. */
export function planSlots(states, now = Date.now()) {
  const e = Object.values(states).find((s) => s.state !== 'unavailable' && /^binary_sensor\.octopus_energy_.*_intelligent_dispatching$/.test(s.entity_id));
  if (!e) return null;
  const slots = (e.attributes?.planned_dispatches || []).map((o) => ({ start: Date.parse(o.start), end: Date.parse(o.end), kwh: Math.abs(Number(o.charge_in_kwh) || 0), source: String(o.source || '').toUpperCase() }))
    .filter((s) => Number.isFinite(s.start) && Number.isFinite(s.end) && s.end > now).sort((a, b) => a.start - b.start);
  return slots;
}
const hhmm = (ms) => new Date(ms).toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' });
export const planLine = (s, now = Date.now()) => `${s.start <= now ? 'Now: ' : ''}${hhmm(s.start)} to ${hhmm(s.end)}${s.kwh > 0 ? ` · ${s.kwh.toFixed(1)} kWh` : ''} · ${s.source === 'BOOST' ? 'Boost' : 'Smart'}`;

/** Cars: a battery sensor (percent) with a range sensor of the same make, leaving out phones, 12 V and service batteries. */
export function findCars(states) {
  const out = [];
  for (const [id, e] of Object.entries(states)) {
    if (!id.startsWith('sensor.') || e.attributes?.device_class !== 'battery' || e.attributes?.unit_of_measurement !== '%' || /_(service|12v|aux|soh|key)/i.test(id)) continue;
    const base = id.replace(/^sensor\./, '').replace(/_(battery|battery_level|state_of_charge)$/, '');
    const range = Object.keys(states).find((r) => r.startsWith(`sensor.${base}_`) && /(electric_|battery_)?range$/.test(r) && !/fuel|combustion|total/.test(r));
    if (!range) continue;
    const plug = Object.keys(states).find((r) => r.startsWith(`binary_sensor.${base}_`) && /plug/.test(r) && !/lock/.test(r));
    out.push({ name: (e.attributes?.friendly_name || id).replace(/ battery( level)?$/i, ''), battery: Number(e.state), range: Number(states[range].state), unit: states[range].attributes?.unit_of_measurement || 'mi', plugged: plug ? states[plug].state === 'on' : null });
  }
  return out;
}

// ---- Drawing ----
export function weekCard(ctx, tone, states) {
  const ids = weekIds(states), box = toneCard(ctx, tone, soft('Reading the Octopus history…'));
  box.style.setProperty('--elec', ctx.style.colours('energy')[0]); box.style.setProperty('--gas', ctx.style.colours('gas')[0]);
  if (!weekStatIds(ids).length) { box.replaceChildren(soft('No Octopus meter found.')); return box; }
  const draw = (week, pick) => {
    if (!week.days.some((d) => d.hasData)) { box.replaceChildren(soft("Octopus hasn't sent any of the last seven days yet.")); return; }
    const max = Math.max(0.01, ...week.days.map((d) => d.total)), chosen = pick || week.latest;
    const bars = h('div', { class: 'week' }, week.days.map((d) => h('button', { type: 'button', class: `wk${d === chosen ? ' on' : ''}`, onclick: () => draw(week, d), 'aria-label': `${dayName(d.date, true)}, ${pounds(d.total)}` },
      h('span', { class: 'wk-v' }, d.hasData ? pounds(d.total) : '–'),
      h('span', { class: 'wk-bar' }, d.hasData ? [h('i', { class: 'gas', style: `height:${(((d.gasCost ?? 0) / max) * 100).toFixed(1)}%` }), h('i', { class: 'elec', style: `height:${(((d.elecCost ?? 0) / max) * 100).toFixed(1)}%` })] : h('i', { class: 'none' })),
      h('span', { class: 'wk-d' }, dayName(d.date)))));
    const b = week.balance;
    const share = chosen.offPeak > 0 && (chosen.offPeak + (chosen.peak ?? 0) + (chosen.standard ?? 0)) > 0 ? Math.round((chosen.offPeak / (chosen.offPeak + (chosen.peak ?? 0) + (chosen.standard ?? 0))) * 100) : null;
    box.replaceChildren(fact('Last 7 days', `${pounds(week.cost)} · ${pounds(week.average)} a day`, true), bars,
      b ? h('div', { class: 'balance' }, h('i', { class: 'elec', style: `width:${b.elec}%` }), h('i', { class: 'gas', style: `width:${b.gas}%` })) : null,
      b ? soft(`Electricity ${pounds(week.elecCost)} · ${b.elec}% · ${kwh(week.elecKwh)}`) : null, b ? soft(`Gas ${pounds(week.gasCost)} · ${b.gas}% · ${kwh(week.gasKwh)}`) : null,
      h('p', { class: 'sub' }, dayName(chosen.date, true)), fact('Electricity', pounds(chosen.elecCost) + (chosen.elecKwh != null ? ` · ${kwh(chosen.elecKwh)}` : '')), fact('Gas', pounds(chosen.gasCost) + (chosen.gasKwh != null ? ` · ${kwh(chosen.gasKwh)}` : '')),
      share != null ? soft(`${share}% of that day's electricity was used at the cheap rate.`) : null);
  };
  const load = async () => {
    try { const start = new Date(Date.now() - 10 * DAY); start.setHours(0, 0, 0, 0);
      draw(buildWeek(await ctx.ha.call('recorder/statistics_during_period', { start_time: start.toISOString(), statistic_ids: weekStatIds(ids), period: 'day', types: ['change'] }), ids)); }
    catch { box.replaceChildren(soft("Couldn't read the Octopus history from the house.")); }
  };
  load();
  return box;
}

export function chargerCard(ctx, tone, states) {
  if (!states[ZAPPI.mode]) return null;
  const box = toneCard(ctx, tone), msg = h('p', { class: 'err', role: 'status' });
  const draw = (st) => {
    const session = Number(st[ZAPPI.session]?.state), mode = st[ZAPPI.mode], role = st[ZAPPI.role];
    const act = async (domain, service, data, entity) => { try { await ctx.ha.call('call_service', { domain, service, service_data: data, target: { entity_id: entity } }); setTimeout(refresh, 800); } catch (e) { msg.textContent = e.message; } };
    box.replaceChildren(h('p', { class: 'title', style: 'margin:0' }, chargerStatus(st)), Number.isFinite(session) ? soft(`${session.toFixed(1)} kWh this session`) : null,
      role?.attributes?.options?.length ? chips(role.attributes.options.filter((o) => o !== 'Unknown').map((o) => [o === 'Primary' ? 'VW ID5' : o === 'Additional' ? 'Astra GSe' : o, role.state === o, () => act('input_select', 'select_option', { option: o }, ZAPPI.role)])) : null,
      chips(CHARGER_MODES.filter(([k]) => !mode.attributes?.options?.length || mode.attributes.options.includes(k)).map(([k, label]) => [label, mode.state === k, () => act('select', 'select_option', { option: k }, ZAPPI.mode)])), msg);
  };
  const refresh = async () => { try { draw(Object.fromEntries((await ctx.ha.call('get_states')).map((e) => [e.entity_id, e]))); } catch (e) { msg.textContent = e.message; } };
  draw(states);
  return box;
}

export function planCard(ctx, tone, states) {
  const slots = planSlots(states); if (slots == null) return null; const now = Date.now();
  return toneCard(ctx, tone, h('p', { class: 'sub' }, 'Octopus charging plan'), ...(slots.length ? slots.slice(0, 6).map((s) => h('p', { class: s.start <= now ? 'title' : 'sub', style: 'margin:4px 0' }, planLine(s, now))) : [soft('Nothing planned yet. Octopus plans once the car is plugged in.')]));
}

export function carsCard(ctx, tone, states) {
  const cars = findCars(states);
  if (!cars.length) return null;
  return toneCard(ctx, tone, ...cars.map((c) => h('div', { class: 'fact' }, h('span', {}, c.name), h('b', {}, `${Math.round(c.battery)}%${Number.isFinite(c.range) ? ` · ${Math.round(c.range)} ${c.unit}` : ''}${c.plugged ? ' · plugged in' : ''}`))));
}
