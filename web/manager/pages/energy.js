// The Energy page, as signed off on the example site: three neutral header pills (the dashboard's header lines), then the cards of the house's own Energy
// view in three columns of equal height (Electricity and Gas, Last 7 days, Car charging) and a thin Octoplus banner. No figure appears twice on the page,
// and a missing figure says "Data not available" so a card never changes size. The charger's buttons are the app's (EnergyKit.kt: GridButton).
import { OCTOPUS, allRates, currentRate, cheapUntil, energySnapshot } from './logic.js';
import { CHARGER_MODES, ZAPPI, buildWeek, chargerStatus, findCars, planSlots, weekIds, weekStatIds } from './energy-more.js';
import { NA, esc, fct, gbp, hhmm, ic, kwhf, palette, pf, pill, circ, readableOn, states as readStates, tagp, toneVars, wcard } from '../ui.js';
import { fillIcons } from '../icons.js';
import { cleanup } from '../kit.js';

let weekPick = null;
const find = (st, re) => Object.values(st).find((e) => re.test(e.entity_id));
const num = (e) => { const n = parseFloat(e?.state); return Number.isFinite(n) ? n : null; };
const dayName = (d, long) => d.toLocaleDateString('en-GB', long ? { weekday: 'short', day: 'numeric', month: 'short' } : { weekday: 'short' });

/** Everything the page shows, from the house's states (and the week's history, read separately). */
export function energyModel(st, weekResult, now = Date.now()) {
  const s = energySnapshot(st, now), rates = s.rates;
  const pounds = (re) => num(find(st, re));
  const low = rates.length ? Math.min(...rates.map((r) => r.pence)) : null, high = rates.length ? Math.max(...rates.map((r) => r.pence)) : null;
  const todays = rates.filter((r) => new Date(r.start).toDateString() === new Date(now).toDateString());
  const avg = todays.length ? todays.reduce((a, r) => a + r.pence, 0) / todays.length : null;
  const spins = (k) => num(find(st, new RegExp(`^sensor\\.octopus_energy_a_.*_wheel_of_fortune_spins_${k}$`)));
  const zs = (id) => st[id], mode = zs(ZAPPI.mode), role = zs(ZAPPI.role), smartOn = zs('input_boolean.zappi_smart_charge'), smartPhase = zs('input_select.zappi_smart_charge_state');
  const power = num(zs(ZAPPI.power)) || 0, plug = zs(ZAPPI.plug)?.state || '';
  return {
    s, rates, now, min: low, max: high, avg, cheap: s.cheapUntil != null || (low != null && s.rate != null && s.rate <= low + 0.01),
    stand: pounds(/^sensor\.octopus_energy_electricity_.*_current_standing_charge$/), gstand: pounds(/^sensor\.octopus_energy_gas_.*_current_standing_charge$/),
    week: weekResult ? buildWeek(weekResult, weekIds(st)) : null,
    cars: findCars(st), plan: planSlots(st),
    plus: s.points != null ? { pts: s.points, hh: num(find(st, /^sensor\.octopus_energy_a_.*_octoplus_weekend_happy_hours$/)), spins: (spins('electricity') ?? 0) + (spins('gas') ?? 0), hasSpins: spins('electricity') != null || spins('gas') != null } : null,
    zappi: mode ? { status: chargerStatus(st), session: num(zs(ZAPPI.session)), plugged: power > 0 || (plug && !/disconnected/i.test(plug)), charging: power > 100, mode: mode.state, options: mode.attributes?.options || [],
      role: role?.state ?? null, roles: (role?.attributes?.options || []).filter((o) => o !== 'Unknown'), smart: smartOn ? { on: smartOn.state === 'on', phase: smartPhase?.state || null } : null } : null,
    st,
  };
}

function cheapWindow(m) {
  if (!m.rates.length) return null;
  const low = Math.min(...m.rates.map((r) => r.pence)), cheap = (r) => r.pence <= low + 0.01, cur = currentRate(m.rates, m.now);
  if (cur && cheap(cur)) return { now: true, until: cheapUntil(m.rates, m.now) };
  const n = m.rates.find((r) => r.start >= m.now && cheap(r)); return n ? { now: false, from: n.start } : null;
}
function smartText(m) {
  const sm = m.zappi.smart; if (!sm) return ''; if (!sm.on) return 'Off: it only charges when you start it';
  const w = cheapWindow(m);
  if (sm.phase === 'Charging') return w?.now ? `Charging at the cheap rate until ${hhmm(w.until)}` : 'Charging at the cheap rate';
  if (sm.phase === 'Waiting') return w && !w.now ? `Waiting for the cheap rate, ${hhmm(w.from)}` : 'Starting at the cheap rate';
  if (sm.phase === 'Done') return 'Finished for this plug-in'; if (sm.phase === 'Manual') return "You're in control for this plug-in";
  return 'Charges at the cheap rate when a car is plugged in';
}
function headline(m) {
  const cur = currentRate(m.rates, m.now), low = m.min;
  if (!cur) return { cheap: m.cheap, text: m.s.rate == null ? 'No rates yet' : `<b>${m.cheap ? 'Cheap now' : 'Peak'} ${pf(m.s.rate, m.cheap ? 2 : 1)}</b>` };
  if (cur.pence <= low + 0.0001) return { cheap: true, text: `<b>Cheap now ${pf(cur.pence, 2)}</b> · until ${hhmm(cheapUntil(m.rates, m.now))}` };
  const nxt = m.rates.find((r) => r.start >= m.now && r.pence <= low + 0.0001), sec = nxt ? Math.round((nxt.start - m.now) / 1000) : 0;
  return { cheap: false, text: `<b>Peak ${pf(cur.pence)}</b>${nxt ? ` · cheap from ${hhmm(nxt.start)} (in ${sec >= 3600 ? `${Math.floor(sec / 3600)} h ` : ''}${Math.floor((sec % 3600) / 60)} m)` : ''}` };
}

export function energyHtml(ctx, m) {
  const hl = headline(m), s = m.s, demand = s.demand;
  const pills = pill(ctx, hl.cheap ? 'leaf' : 'bolt', hl.text, hl.cheap ? circ(ctx, 'ok') : null)
    + pill(ctx, 'speedometer', demand == null ? 'Live use not available' : `<b>Using ${demand < 1000 ? `${Math.round(demand)} W` : `${(demand / 1000).toFixed(1)} kW`}</b> now`, demand > 2000 && !hl.cheap ? circ(ctx, 'warn') : null)
    + pill(ctx, 'pound', `Today so far <b>${gbp((s.today || 0) + (s.gasToday || 0))}</b> · ${s.todayKwh == null ? '–' : s.todayKwh.toFixed(1)} kWh + gas`);
  const cost = (c, k) => (c == null && k == null ? NA : `${c == null ? '–' : gbp(c)}${k != null ? ` · ${kwhf(k)}` : ''}`);
  const mx = Math.max(1, ...m.rates.map((r) => r.pence));
  const bars = m.rates.length ? `<div class="bars" role="img" aria-label="Electricity rates, half hour by half hour">${m.rates.map((r) => `<i class="${r.start <= m.now && m.now < r.end ? 'n' : r.pence <= m.min + 0.01 ? 'c' : ''}" style="height:${Math.max(8, (r.pence / mx) * 100)}%"></i>`).join('')}</div>` : '<div class="bars none"><span class="na">Rates not available</span></div>';
  const trio = `<div class="trio"><span><small>Cheapest</small><b>${m.min != null ? pf(m.min) : NA}</b></span><span><small>Average</small><b>${m.avg != null ? pf(m.avg) : NA}</b></span><span><small>Highest</small><b>${m.max != null ? pf(m.max) : NA}</b></span></div>`;
  const elec = wcard(ctx, 'energy', 'logo:octopusenergy', 'Octopus Electricity', '', `${bars}${trio}${fct('Today', cost(s.today, s.todayKwh))}${fct('Yesterday', cost(s.yesterday, s.yesterdayKwh))}${fct('Standing charge', m.stand != null ? `${gbp(m.stand)} a day` : NA)}`);
  const gas = wcard(ctx, 'gas', 'logo:octopusenergy', 'Octopus Gas', s.gasRate != null ? `${pf(s.gasRate, 2)} per kWh` : '', `${fct('Today', s.gasToday != null ? gbp(s.gasToday) : NA)}${fct('Yesterday', s.gasYesterday != null ? gbp(s.gasYesterday) : NA)}${fct('Standing charge', m.gstand != null ? `${gbp(m.gstand)} a day` : NA)}`);
  // Last 7 days: the same rows whatever the day has
  let wkBody; const w = m.week, has = w && w.days.length;
  if (has) {
    const max = Math.max(0.01, ...w.days.map((d) => d.total)), def = w.latest || w.days[w.days.length - 1], ch = weekPick != null && w.days[weekPick] ? w.days[weekPick] : def, b = w.balance;
    const tot = (ch.offPeak || 0) + (ch.peak || 0), share = ch.offPeak != null && tot > 0 ? Math.round((ch.offPeak / tot) * 100) : null;
    const ec = palette(ctx, 'energy')[0], gc = palette(ctx, 'gas')[0];
    wkBody = `<div class="wk7">${w.days.map((d, i) => `<button type="button" class="wkb${d === ch ? ' on' : ''}" data-wk="${i}" aria-label="${dayName(d.date, true)}, ${d.hasData ? gbp(d.total) : 'no data'}"><span class="wkv">${d.hasData ? gbp(d.total) : '–'}</span><span class="wkbar">${d.hasData ? `<i style="height:${(((d.gasCost ?? 0) / max) * 100).toFixed(1)}%;background:${gc}"></i><i style="height:${(((d.elecCost ?? 0) / max) * 100).toFixed(1)}%;background:${ec}"></i>` : '<i class="none"></i>'}</span><span class="wkd">${dayName(d.date)}</span></button>`).join('')}</div>
      ${b ? `<div class="bal"><i style="width:${b.elec}%;background:${ec}"></i><i style="width:${b.gas}%;background:${gc}"></i></div><p class="sub">Electricity ${gbp(w.elecCost)} · ${b.elec}% · ${kwhf(w.elecKwh)} &nbsp; Gas ${gbp(w.gasCost)} · ${b.gas}% · ${kwhf(w.gasKwh)}</p>` : `<div class="bal"></div><p class="sub">${NA}</p>`}
      <div class="sect"><p class="sub">${dayName(ch.date, true)}</p>${fct('Electricity', cost(ch.elecCost, ch.elecKwh))}${fct('Gas', cost(ch.gasCost, ch.gasKwh))}${fct('Cheap-rate share', share != null ? `${share}% of the electricity` : NA)}</div>`;
  } else wkBody = `<p class="sub">${NA}</p>`;
  const week = wcard(ctx, 'energy', 'calendar', 'Last 7 days', w && w.average != null ? `${gbp(w.cost)} · ${gbp(w.average)} a day` : '', wkBody);
  // Car charging: one card, the cars, the charger as the app draws it, then Octopus's plan
  const z = m.zappi, roleName = { Primary: 'VW ID5', Additional: 'Astra GSe' }, onCharger = z ? roleName[z.role] : null;
  const cars = m.cars.map((c) => {
    const on = z?.plugged && onCharger && (c.name === onCharger || (onCharger === 'Astra GSe' && /astra/i.test(c.name)));
    return `<div class="car"><div class="car-t"><b>${esc(c.name)}</b>${on ? '<span class="tag">Plugged in</span>' : ''}<span class="car-v">${Math.round(c.battery)}%${Number.isFinite(c.range) ? ` · ${Math.round(c.range)} ${esc(c.unit)}` : ''}</span></div><div class="batt"><i style="width:${Math.max(2, Math.min(100, c.battery))}%;background:${batteryFade((c.battery || 0) / 100)}"></i></div></div>`;
  }).join('');
  let charger = '';
  if (z) {
    const act = !z.plugged ? `<button type="button" class="evb none" disabled>${ic('ev-plug', 18)}No car connected</button>` : z.charging ? `<button type="button" class="evb live" disabled>${ic('bolt', 18)}Charging</button>` : '';
    const smart = z.smart ? `<button type="button" class="evb smart" data-smart role="switch" aria-checked="${z.smart.on}" aria-label="Smart charge">${ic('clock-bolt', 20)}<span class="est"><b>Smart charge</b><small>${esc(smartText(m))}</small></span><span class="esw${z.smart.on ? ' on' : ''}"></span></button>` : '';
    const desc = z.role === 'Primary' ? 'VW ID5: also charges when Octopus schedules it' : z.role === 'Additional' ? 'Astra GSe: charges 23:30 to 05:30 only' : 'Not answered yet, so it will not charge';
    const which = z.plugged && z.roles.length ? `<label class="evb car">${ic('car-ev', 20)}<span class="est"><b>Car plugged in</b><small>${esc(desc)}</small></span><select class="esel" data-role aria-label="Car plugged in">${z.role === 'Unknown' ? '<option value="Unknown">Which car?</option>' : ''}${z.roles.map((r) => `<option value="${esc(r)}"${r === z.role ? ' selected' : ''}>${esc(roleName[r] || r)}</option>`).join('')}</select></label>` : '';
    const ICONS = { Stopped: 'stop', Eco: 'leaf', 'Eco+': 'solar-power', Fast: 'bolt' }, modes = CHARGER_MODES.filter(([k]) => !z.options.length || z.options.includes(k));
    charger = `<div class="sect"><p class="sub">Charger · ${esc(z.status)}${z.session != null ? ` · ${z.session.toFixed(1)} kWh this session` : ''}</p><div class="evacts">${act}${smart}${which}</div><div class="evrow">${modes.map(([k, l]) => `<button type="button" class="evq${z.mode === k ? ' on' : ''}" data-mode="${esc(k)}" aria-pressed="${z.mode === k}" aria-label="${esc(l)}">${ic(ICONS[k] || 'bolt', 20)}<span>${esc(l)}</span></button>`).join('')}</div></div>`;
  }
  const plan = m.plan == null ? '' : `<div class="sect"><p class="sub">Octopus charging plan</p>${m.plan.length ? m.plan.slice(0, 6).map((p) => `<p class="${p.start <= m.now ? 'title' : 'sub'}">${p.start <= m.now ? 'Now: ' : ''}${hhmm(p.start)} to ${hhmm(p.end)}${p.kwh > 0 ? ` · ${p.kwh.toFixed(1)} kWh` : ''} · ${p.source === 'BOOST' ? 'Boost' : 'Smart'}</p>`).join('') : '<p class="sub">Nothing planned yet. Octopus plans once the car is plugged in.</p>'}</div>`;
  const car = cars || charger || plan ? wcard(ctx, 'car', 'ev-solid', 'Car charging', z ? esc(z.mode) : '', cars + charger + plan) : '';
  const pl = m.plus, plus = pl ? `<article class="tcard glow wc banner" style="${toneVars(ctx, 'energy')}"><span class="wc-i">${ic('gift', 24)}</span><h2>Octoplus</h2><span class="wc-p"><b>${esc(pl.pts)}</b> points</span>${pl.hh != null ? `<span class="wc-p"><b>${esc(pl.hh)}</b> weekend happy hours</span>` : ''}${pl.hasSpins ? `<span class="wc-p"><b>${pl.spins}</b> Wheel of Fortune spins</span>` : ''}</article>` : '';
  return `<div class="stack"><div class="hls">${pills}</div><div class="egrid"><div class="ecol">${elec}${gas}</div><div class="ecol">${week}</div><div class="ecol">${car}</div></div>${plus}</div>`;
}

/** The app's battery colours (EnergyKit.kt batteryFade/levelFade): green from 75% up, then yellow, amber and red as it empties. */
export function batteryFade(f) {
  if (f >= 0.75) return '#4cc38a';
  const stops = [[0, [229, 83, 75]], [0.25, [232, 163, 59]], [0.5, [230, 201, 58]], [0.75, [76, 195, 138]]], x = Math.max(0, f);
  for (let i = 0; i < stops.length - 1; i++) { const [f0, c0] = stops[i], [f1, c1] = stops[i + 1]; if (x <= f1) { const k = (x - f0) / (f1 - f0); return `#${c0.map((v, j) => Math.round(v + (c1[j] - v) * k).toString(16).padStart(2, '0')).join('')}`; } }
  return '#4cc38a';
}

async function readWeek(ctx, st) {
  const ids = weekStatIds(weekIds(st)); if (!ids.length) return null;
  const start = new Date(Date.now() - 10 * 86400000); start.setHours(0, 0, 0, 0);
  try { return await ctx.ha.call('recorder/statistics_during_period', { start_time: start.toISOString(), statistic_ids: ids, period: 'day', types: ['change'] }); } catch { return null; }
}

export async function renderEnergyPage(el, ctx) {
  cleanup(ctx);
  el.innerHTML = '<p class="note">Reading the energy page from the house…</p>';
  let st, week;
  try { st = await readStates(ctx); week = await readWeek(ctx, st); }
  catch (e) { el.innerHTML = `<p class="err" role="alert">Could not read the energy page: ${esc(e.message)}</p>`; return; }
  const draw = () => { el.innerHTML = energyHtml(ctx, energyModel(st, week)); fillIcons(el); bind(); };
  const refresh = async () => { try { st = await readStates(ctx); draw(); } catch { /* the page stays as it is */ } };
  const call = async (domain, service, data, entity) => { try { await ctx.ha.call('call_service', { domain, service, service_data: data, target: { entity_id: entity } }); setTimeout(refresh, 800); } catch (e) { el.querySelector('.hls')?.insertAdjacentHTML('afterend', `<p class="err" role="alert">${esc(e.message)}</p>`); } };
  function bind() {
    el.querySelectorAll('[data-wk]').forEach((b) => b.addEventListener('click', () => { weekPick = Number(b.dataset.wk); draw(); }));
    el.querySelectorAll('[data-mode]').forEach((b) => b.addEventListener('click', () => call('select', 'select_option', { option: b.dataset.mode }, ZAPPI.mode)));
    el.querySelector('[data-smart]')?.addEventListener('click', (e) => call('input_boolean', e.currentTarget.getAttribute('aria-checked') === 'true' ? 'turn_off' : 'turn_on', {}, 'input_boolean.zappi_smart_charge'));
    el.querySelector('[data-role]')?.addEventListener('change', (e) => call('input_select', 'select_option', { option: e.target.value }, ZAPPI.role));
  }
  draw();
}
