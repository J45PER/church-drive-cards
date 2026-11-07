// The plain rules behind the Purifier schedule card (no browser in here, so they can be tested alone).
// They match purifier_schedule_logic.py in the integration, which is what actually runs the schedules.
// Days are 0 = Monday to 6 = Sunday; times are "HH:MM".

export const DAY_NAMES = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
export const DAY_LETTERS = ['M', 'T', 'W', 'T', 'F', 'S', 'S'];
export const MAX_SCHEDULES = 12;

// "Every day", "Mon-Fri", "Sat & Sun", "Mon, Thu & Sat": runs of three or more days are ranges.
export function daysText(days) {
  const ds = [...new Set((days || []).filter((d) => Number.isInteger(d) && d >= 0 && d <= 6))].sort((a, b) => a - b);
  if (!ds.length) return 'No days';
  if (ds.length === 7) return 'Every day';
  const runs = [];
  for (const d of ds) {
    if (runs.length && d === runs[runs.length - 1][runs[runs.length - 1].length - 1] + 1) runs[runs.length - 1].push(d);
    else runs.push([d]);
  }
  const parts = [];
  for (const run of runs) {
    if (run.length >= 3) parts.push(`${DAY_NAMES[run[0]]}-${DAY_NAMES[run[run.length - 1]]}`);
    else for (const d of run) parts.push(DAY_NAMES[d]);
  }
  return parts.length === 1 ? parts[0] : `${parts.slice(0, -1).join(', ')} & ${parts[parts.length - 1]}`;
}

// "Mon-Fri Auto: 07:00-23:30".
export const summary = (s) => `${daysText(s.days)} Auto: ${s.auto}-${s.sleep}`;

export const validTime = (t) => /^([01]\d|2[0-3]):[0-5]\d$/.test(String(t || ''));

// A day on or off in a schedule (the other days are untouched).
export function toggleDay(schedule, day) {
  const has = schedule.days.includes(day);
  const days = has ? schedule.days.filter((d) => d !== day) : [...schedule.days, day].sort((a, b) => a - b);
  return { ...schedule, days };
}

// A new schedule for the days not covered yet (weekdays first, then the weekend), 07:00 to 22:30.
export function newSchedule(existing) {
  const covered = new Set((existing || []).flatMap((s) => s.days));
  const free = [0, 1, 2, 3, 4, 5, 6].filter((d) => !covered.has(d));
  const weekdays = free.filter((d) => d < 5);
  const days = weekdays.length ? weekdays : free.length ? free : [0, 1, 2, 3, 4];
  let n = 1;
  const ids = new Set((existing || []).map((s) => s.id));
  while (ids.has(`s${n}`)) n++;
  return { id: `s${n}`, days, auto: '07:00', sleep: '22:30' };
}

// What stops a draft from being saved, as a sentence; '' when it can be.
export function problem(schedules) {
  if (schedules.length > MAX_SCHEDULES) return `At most ${MAX_SCHEDULES} schedules.`;
  for (const s of schedules) {
    if (!s.days.length) return 'Pick at least one day for each schedule.';
    if (!validTime(s.auto) || !validTime(s.sleep)) return 'Set both times for each schedule.';
  }
  return '';
}

// Days that more than one schedule covers, as "Mon, Tue", so the card can warn about overlaps.
export function overlaps(schedules) {
  const count = new Array(7).fill(0);
  for (const s of schedules) for (const d of new Set(s.days)) count[d]++;
  const days = count.map((n, d) => (n > 1 ? d : -1)).filter((d) => d >= 0);
  return days.length ? days.map((d) => DAY_NAMES[d]).join(', ') : '';
}
