// Overnight heating: the house-wide setting that decides what the heating does between two times at night (default midnight to 6am).
//   Smart: Clive keeps looking after the heating, as in the day.
//   Eco:   the heating goes on Eco for the night and Clive stops adjusting it.
//   Off:   the heating is turned off for the night and Clive stops adjusting it.
// The mode and the two times are Home Assistant helpers; the scripts that act on them are in ha/ (clive-climate-zone.yaml). This file is
// the shared wording and arithmetic for the card (the phone app has the same in OvernightHeating.kt). Tested in test/overnight-logic.test.mjs.

export const OVERNIGHT_MODE_ID = 'input_select.clive_overnight_mode';
export const OVERNIGHT_START_ID = 'input_datetime.clive_overnight_start';
export const OVERNIGHT_END_ID = 'input_datetime.clive_overnight_end';

export const OVERNIGHT_MODES = [
  { id: 'Off', label: 'Off', icon: 'mdi:power', what: 'turns the heating off for the night' },
  { id: 'Eco', label: 'Eco', icon: 'mdi:leaf', what: 'puts the heating on Eco for the night' },
  { id: 'Smart', label: 'Smart', icon: 'mdi:thermostat-auto', what: 'leaves it to Clive, as in the day' },
];

// "00:00:00" or "6:5" as "00:00" / "06:05"; null when it isn't a time.
export function hm(value) {
  const m = /^(\d{1,2}):(\d{1,2})/.exec(String(value == null ? '' : value));
  if (!m) return null;
  const h = Number(m[1]), n = Number(m[2]);
  if (h > 23 || n > 59) return null;
  return `${String(h).padStart(2, '0')}:${String(n).padStart(2, '0')}`;
}

// Is [nowHm] inside the window from [start] to [end] (which may run past midnight)? Equal times mean no window.
export function inWindow(start, end, nowHm) {
  const s = hm(start), e = hm(end), n = hm(nowHm);
  if (!s || !e || !n || s === e) return false;
  return s < e ? s <= n && n < e : n >= s || n < e;
}

// Move a time by [direction] steps of [step] minutes, wrapping round the clock.
export function stepTime(value, direction, step = 30) {
  const t = hm(value);
  if (!t) return null;
  const [h, m] = t.split(':').map(Number);
  const total = (((h * 60 + m + direction * step) % 1440) + 1440) % 1440;
  return `${String(Math.floor(total / 60)).padStart(2, '0')}:${String(total % 60).padStart(2, '0')}`;
}

// The sentence under the buttons for the chosen mode.
export function describeOvernight(mode, start, end, name = 'Clive') {
  const s = hm(start) || '00:00', e = hm(end) || '06:00';
  if (mode === 'Eco') return `From ${s} to ${e} the heating is put on Eco and ${name} does not adjust it. In the morning ${name} puts it back.`;
  if (mode === 'Off') return `From ${s} to ${e} the heating is turned off and ${name} does not adjust it. In the morning ${name} turns it back on.`;
  return `${name} looks after the heating all night, as in the day.`;
}

// The mode as the helper reports it, or Smart when it is missing (so nothing changes until it is set).
export function modeOf(state) {
  return OVERNIGHT_MODES.some((m) => m.id === state) ? state : 'Smart';
}
