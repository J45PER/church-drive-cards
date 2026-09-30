// Repeating tasks, kept as plain words in a to-do item's description:
//
//   "<repeat> · <who> · <notes>"
//
// <repeat> is one of
//   Once                                  (no repeat; the due date is the item's own)
//   Every day 08:00 / Every 3 days 08:00
//   Mon 09:00, Thu 18:30                  (weekly, a time per day)
//   Every 2 weeks: Mon 09:00, Thu 18:30   (fortnightly and so on)
//   Monthly on the 15th 10:00 / Every 3 months on the last day 10:00
//   Yearly on 12 Mar 09:00
//   Every 30 days after done 09:00 / Every week after done 09:00 / Every 2 months after done 09:00
// <who> is "for everyone", "for Jamie, Hayley" or "no reminders".
// Anything else is kept as notes. A description that doesn't start with a
// repeat is all notes (an ordinary to-do).
//
// "Church Drive: repeating tasks" (an automation) reads the same words and
// works out the next due time with the same rules as nextOccurrence() below:
// keep the two in step (scratchpad repeat-cross.mjs checks them against each
// other).

export const DAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
export const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
const pad = (n) => String(n).padStart(2, '0');
const T = '(\\d{1,2}):(\\d{2})';
const hhmm = (h, m) => `${pad(h)}:${m}`;
const ordinal = (n) => `${n}${n % 100 >= 11 && n % 100 <= 13 ? 'th' : ['th', 'st', 'nd', 'rd'][n % 10] || 'th'}`;

function parseSlots(text) {
  const out = [];
  for (const part of text.split(',').map((x) => x.trim())) {
    const m = part.match(new RegExp(`^(mon|tue|wed|thu|fri|sat|sun)[a-z]* ${T}$`, 'i'));
    if (!m) return null;
    out.push([DAYS.findIndex((d) => d.toLowerCase() === m[1].toLowerCase()), hhmm(m[2], m[3])]);
  }
  return out.length ? out : null;
}

// The repeat part alone, or null.
export function parseRepeat(text) {
  const s = String(text || '').trim();
  let m;
  if (/^once$/i.test(s)) return { type: 'once' };
  if ((m = s.match(new RegExp(`^every day ${T}$`, 'i')))) return { type: 'days', n: 1, time: hhmm(m[1], m[2]) };
  if ((m = s.match(new RegExp(`^every (\\d+) days ${T}$`, 'i')))) return { type: 'days', n: Math.max(1, Number(m[1])), time: hhmm(m[2], m[3]) };
  if ((m = s.match(/^every (\d+) weeks: (.+)$/i))) {
    const slots = parseSlots(m[2]);
    return slots ? { type: 'weekly', n: Math.max(1, Number(m[1])), slots } : null;
  }
  const slots = parseSlots(s);
  if (slots) return { type: 'weekly', n: 1, slots };
  if ((m = s.match(new RegExp(`^(?:monthly|every (\\d+) months) on the (?:(\\d{1,2})(?:st|nd|rd|th)|(last) day) ${T}$`, 'i'))))
    return { type: 'monthly', n: Math.max(1, Number(m[1] || 1)), day: m[3] ? 'last' : Math.min(31, Math.max(1, Number(m[2]))), time: hhmm(m[4], m[5]) };
  if ((m = s.match(new RegExp(`^yearly on (\\d{1,2}) (jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]* ${T}$`, 'i'))))
    return { type: 'yearly', month: MONTHS.findIndex((x) => x.toLowerCase() === m[2].toLowerCase()), day: Number(m[1]), time: hhmm(m[3], m[4]) };
  if ((m = s.match(new RegExp(`^every (?:(\\d+) )?(day|week|month)s? after done ${T}$`, 'i'))))
    return { type: 'after', n: Math.max(1, Number(m[1] || 1)), unit: m[2].toLowerCase(), time: hhmm(m[3], m[4]) };
  return null;
}

export function formatRepeat(r) {
  switch (r && r.type) {
    case 'once':
      return 'Once';
    case 'days':
      return r.n > 1 ? `Every ${r.n} days ${r.time}` : `Every day ${r.time}`;
    case 'weekly': {
      const slots = [...r.slots].sort((a, b) => a[0] - b[0] || a[1].localeCompare(b[1])).map(([d, t]) => `${DAYS[d]} ${t}`).join(', ');
      return r.n > 1 ? `Every ${r.n} weeks: ${slots}` : slots;
    }
    case 'monthly': {
      const on = r.day === 'last' ? 'the last day' : `the ${ordinal(r.day)}`;
      return r.n > 1 ? `Every ${r.n} months on ${on} ${r.time}` : `Monthly on ${on} ${r.time}`;
    }
    case 'yearly':
      return `Yearly on ${r.day} ${MONTHS[r.month]} ${r.time}`;
    case 'after':
      return r.n > 1 ? `Every ${r.n} ${r.unit}s after done ${r.time}` : `Every ${r.unit} after done ${r.time}`;
    default:
      return '';
  }
}

// "<repeat> · <who> · <notes>" -> { repeat, who, notes }. who is 'everyone',
// 'none' or a list of first names.
export function parseTask(description) {
  const parts = String(description || '').split(' · ');
  const repeat = parseRepeat(parts[0]);
  if (!repeat) return { repeat: null, who: 'none', notes: String(description || '') };
  let who = 'none';
  let rest = parts.slice(1);
  const w = (rest[0] || '').trim();
  if (/^for everyone$/i.test(w)) who = 'everyone';
  else if (/^for /i.test(w)) who = w.slice(4).split(',').map((x) => x.trim()).filter(Boolean);
  if (/^(for |no reminders$)/i.test(w)) rest = rest.slice(1);
  return { repeat, who, notes: rest.join(' · ') };
}

export function formatTask(repeat, who, notes) {
  const n = String(notes || '').trim();
  if (!repeat) return n;
  const whom = who === 'everyone' ? 'for everyone' : Array.isArray(who) && who.length ? `for ${who.join(', ')}` : 'no reminders';
  return [formatRepeat(repeat), whom, n].filter(Boolean).join(' · ');
}

// Local date helpers (a "date" here is a Date at local midnight).
const dayOf = (d) => new Date(d.getFullYear(), d.getMonth(), d.getDate());
const at = (date, time) => {
  const [h, m] = time.split(':').map(Number);
  return new Date(date.getFullYear(), date.getMonth(), date.getDate(), h, m);
};
const addDays = (date, n) => new Date(date.getFullYear(), date.getMonth(), date.getDate() + n);
const lastDay = (y, m) => new Date(y, m + 1, 0).getDate();
const daysBetween = (a, b) => Math.round((dayOf(b) - dayOf(a)) / 86400000);

// The first time the repeat comes round after `after`, counting its cycles
// from `anchor` (the previous due time, or the start date for a new task).
// Weekly "every 2 weeks" keeps the anchor's fortnight; monthly "every 3
// months" keeps the anchor's months. "After done" is `anchor` plus the gap.
export function nextOccurrence(r, anchor, after) {
  if (!r || r.type === 'once') return null;
  const a = dayOf(anchor);
  if (r.type === 'after') {
    const base = r.unit === 'month' ? new Date(a.getFullYear(), a.getMonth() + r.n, Math.min(a.getDate(), lastDay(a.getFullYear(), a.getMonth() + r.n))) : addDays(a, r.n * (r.unit === 'week' ? 7 : 1));
    return at(base, r.time);
  }
  if (r.type === 'days') {
    let k = Math.max(0, Math.floor(daysBetween(a, after) / r.n) - 1);
    for (let i = 0; i < 5; i += 1, k += 1) {
      const o = at(addDays(a, k * r.n), r.time);
      if (o > after) return o;
    }
    return null;
  }
  if (r.type === 'weekly') {
    const monday = addDays(a, -((a.getDay() + 6) % 7));
    const slots = [...r.slots].sort((x, y) => x[0] - y[0] || x[1].localeCompare(y[1]));
    let k = Math.max(0, Math.floor(daysBetween(monday, after) / (7 * r.n)) - 1);
    for (let i = 0; i < 5; i += 1, k += 1) {
      const week = addDays(monday, k * 7 * r.n);
      for (const [d, t] of slots) {
        const o = at(addDays(week, d), t);
        if (o > after && o >= a) return o;
      }
    }
    return null;
  }
  if (r.type === 'monthly') {
    const months = (after.getFullYear() - a.getFullYear()) * 12 + after.getMonth() - a.getMonth();
    let k = Math.max(0, Math.floor(months / r.n) - 1);
    for (let i = 0; i < 5; i += 1, k += 1) {
      const y = a.getFullYear() + Math.floor((a.getMonth() + k * r.n) / 12);
      const m = (a.getMonth() + k * r.n) % 12;
      const d = r.day === 'last' ? lastDay(y, m) : Math.min(r.day, lastDay(y, m));
      const o = at(new Date(y, m, d), r.time);
      if (o > after && o >= a) return o;
    }
    return null;
  }
  if (r.type === 'yearly') {
    for (let y = Math.max(a.getFullYear(), after.getFullYear() - 1); y <= after.getFullYear() + 2; y += 1) {
      const o = at(new Date(y, r.month, Math.min(r.day, lastDay(y, r.month))), r.time);
      if (o > after && o >= a) return o;
    }
    return null;
  }
  return null;
}

// The first due time for a new (or changed) task that starts on `start`.
export function firstDue(r, start, now = new Date()) {
  if (!r || r.type === 'once') return null;
  const s = dayOf(start);
  if (r.type === 'after') {
    const o = at(s, r.time);
    return o > now ? o : nextOccurrence(r, now, now);
  }
  return nextOccurrence(r, s, new Date(Math.max(now.getTime(), s.getTime() - 1)));
}

// When a repeating task is ticked off: the next due time.
export function afterDone(r, prevDue, now = new Date()) {
  if (!r || r.type === 'once') return null;
  if (r.type === 'after') return nextOccurrence(r, now, now);
  const anchor = prevDue || now;
  return nextOccurrence(r, anchor, new Date(Math.max(now.getTime(), anchor.getTime())));
}

// "Every Mon, Thu 09:00", for lists.
export function describeRepeat(r) {
  if (!r) return '';
  switch (r.type) {
    case 'once':
      return 'Once';
    case 'days':
      return r.n > 1 ? `Every ${r.n} days, ${r.time}` : `Daily, ${r.time}`;
    case 'weekly': {
      const byTime = {};
      r.slots.forEach(([d, t]) => (byTime[t] = byTime[t] || []).push(d));
      const parts = Object.keys(byTime)
        .sort()
        .map((t) => {
          const ds = byTime[t].sort((x, y) => x - y);
          return `${ds.length === 7 ? 'every day' : ds.map((d) => DAYS[d]).join(', ')} ${t}`;
        });
      return `${r.n === 2 ? 'Fortnightly' : r.n > 2 ? `Every ${r.n} weeks` : 'Weekly'}: ${parts.join(' · ')}`;
    }
    case 'monthly':
      return `${r.n > 1 ? `Every ${r.n} months` : 'Monthly'} on the ${r.day === 'last' ? 'last day' : ordinal(r.day)}, ${r.time}`;
    case 'yearly':
      return `Yearly on ${r.day} ${MONTHS[r.month]}, ${r.time}`;
    case 'after':
      return `${r.n > 1 ? `${r.n} ${r.unit}s` : `A ${r.unit}`} after it's done`;
    default:
      return '';
  }
}
