// The Activity log card's rules: the day and time-of-day choices, the question sent to the house, and how lines are grouped and worded.
// The house (activity.py) does the filtering; this only decides what to ask for. Days are the browser's own local days.

export const DAYS = [
  { id: 'today', label: 'Today' },
  { id: 'yesterday', label: 'Yesterday' },
  { id: 'week', label: '7 days' },
  { id: 'month', label: '30 days' },
  { id: 'custom', label: 'Pick dates' },
];

export const TIMES = [
  { id: 'all', label: 'All day', start: '', end: '' },
  { id: 'morning', label: 'Morning', sub: '06:00 to 12:00', start: '06:00', end: '12:00' },
  { id: 'afternoon', label: 'Afternoon', sub: '12:00 to 18:00', start: '12:00', end: '18:00' },
  { id: 'evening', label: 'Evening', sub: '18:00 to 22:00', start: '18:00', end: '22:00' },
  { id: 'night', label: 'Night', sub: '22:00 to 06:00', start: '22:00', end: '06:00' },
  { id: 'custom', label: 'Pick times' },
];

// `clive` is what he did because someone asked him; `clive_auto` is what he did because an automation ran his script.
export const KINDS = [
  { id: 'user', label: 'People', colour: '#42a5f5', icon: 'mdi:account' },
  { id: 'clive', label: 'asked', colour: '#ab47bc', icon: 'mdi:creation', assistant: true },
  { id: 'clive_auto', label: 'automatic', colour: '#7e57c2', icon: 'mdi:robot', assistant: true },
  { id: 'presence', label: 'Coming and going', colour: '#26a69a', icon: 'mdi:map-marker-account' },
];

// The chip's words: the assistant's kinds are named after him ("Clive asked", "Clive automatic").
export const kindLabel = (kind, assistant = 'Clive') => (kind.assistant ? `${assistant} ${kind.label}` : kind.label);

export const defaultFilters = () => ({ day: 'today', from: '', to: '', time: 'all', start: '', end: '', kinds: [], who: '' });

const startOfDay = (d) => new Date(d.getFullYear(), d.getMonth(), d.getDate());
const addDays = (d, n) => new Date(d.getFullYear(), d.getMonth(), d.getDate() + n);
const secs = (d) => d.getTime() / 1000;

// "2026-10-24" as a local date, or null.
export function parseDay(text) {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(String(text || ''));
  if (!m) return null;
  const d = new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3]));
  return d.getMonth() === Number(m[2]) - 1 ? d : null;
}

export function dayText(d) {
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

// The times (seconds) a day choice covers: from the start of its first day up to (not including) the end of its last. A custom range
// takes either date alone ("from" only = from then on, "to" only = up to then); reversed dates are put right.
export function rangeFor(filters, now = new Date()) {
  const today = startOfDay(now);
  switch (filters.day) {
    case 'yesterday':
      return { since: secs(addDays(today, -1)), until: secs(today) };
    case 'week':
      return { since: secs(addDays(today, -6)), until: secs(addDays(today, 1)) };
    case 'month':
      return { since: secs(addDays(today, -29)), until: secs(addDays(today, 1)) };
    case 'custom': {
      let a = parseDay(filters.from);
      let b = parseDay(filters.to);
      if (a && b && b < a) [a, b] = [b, a];
      const out = {};
      if (a) out.since = secs(a);
      if (b) out.until = secs(addDays(b, 1));
      return out;
    }
    default:
      return { since: secs(today), until: secs(addDays(today, 1)) };
  }
}

// The time-of-day window chosen, as { start, end } ("" = open).
export function windowFor(filters) {
  if (filters.time === 'custom') return { start: validTime(filters.start), end: validTime(filters.end) };
  const t = TIMES.find((x) => x.id === filters.time);
  return { start: t ? t.start : '', end: t ? t.end : '' };
}

const validTime = (text) => (/^([01]\d|2[0-3]):[0-5]\d$/.test(String(text || '')) ? text : '');

// The message for the house.
export function buildQuery(filters, now = new Date(), limit = 100, offset = 0) {
  const msg = { type: 'church_drive/activity', ...rangeFor(filters, now), limit };
  const { start, end } = windowFor(filters);
  if (start) msg.start = start;
  if (end) msg.end = end;
  if (filters.kinds && filters.kinds.length) msg.kinds = filters.kinds.slice();
  if (filters.who) msg.who = filters.who;
  if (offset) msg.offset = offset;
  return msg;
}

export function toggleKind(filters, kind) {
  const has = filters.kinds.includes(kind);
  const kinds = has ? filters.kinds.filter((k) => k !== kind) : [...filters.kinds, kind];
  return { ...filters, kinds };
}

// "Today", "Yesterday", "Fri 24 Oct" (with the year when it isn't this year).
export function dayHeading(ts, now = new Date()) {
  const d = startOfDay(new Date(ts * 1000));
  const today = startOfDay(now);
  if (+d === +today) return 'Today';
  if (+d === +addDays(today, -1)) return 'Yesterday';
  const text = d.toLocaleDateString('en-GB', { weekday: 'short', day: 'numeric', month: 'short', ...(d.getFullYear() === today.getFullYear() ? {} : { year: 'numeric' }) });
  return text.replace(',', '');
}

export function timeText(ts) {
  return new Date(ts * 1000).toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit', hour12: false });
}

// Lines (newest first) in runs under their day, keeping the order.
export function groupByDay(entries, now = new Date()) {
  const groups = [];
  for (const e of entries) {
    const heading = dayHeading(e.ts, now);
    const last = groups[groups.length - 1];
    if (last && last.heading === heading) last.entries.push(e);
    else groups.push({ heading, entries: [e] });
  }
  return groups;
}

// Who did it and what, for a line: the assistant is named by the house, and the note says whether someone asked him (and who) or an
// automation ran his script (and which).
export function lineParts(entry, assistant = 'Clive') {
  if (entry.kind === 'clive') return { who: assistant, text: entry.text, note: entry.by ? `asked by ${entry.by}` : 'asked' };
  if (entry.kind === 'clive_auto') return { who: assistant, text: entry.text, note: entry.rule ? `automatic: ${entry.rule}` : 'automatic' };
  return { who: entry.who || 'Someone', text: entry.text, note: '' };
}

// A short line saying what is being shown.
export function summary(filters, total) {
  const n = `${total} ${total === 1 ? 'line' : 'lines'}`;
  return filters.kinds.length || filters.who || filters.time !== 'all' ? `${n} match` : n;
}
