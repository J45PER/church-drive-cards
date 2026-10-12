// Steps inside a to-do task, kept as tick lines at the end of the task's notes (which are the last part of its
// description, see repeat.js):
//
//   "Check the Hub is in modem mode\n- [x] Power-cycle the Hub\n- [ ] Power-cycle the eero"
//
// The words before the first line starting "- [ ]" or "- [x]" are the notes; each such line is a step. The
// Android app (TodoSteps.kt) reads and writes the same lines: keep the two in step.

const STEP = /^\s*[-*] \[([ xX])\] ?(.*)$/;
// A step marker at the start of the description, a line, or after " · " (when the notes are only steps).
const MARK = /(^|\n|· )([-*] \[)([ xX])(\])/g;

const clean = (s) => String(s || '').replace(/\s*\n\s*/g, ' ').replace(/ · /g, ' - ').trim();

// Notes -> { text, steps: [{ text, done }] }.
export function splitSteps(notes) {
  const text = [];
  const steps = [];
  for (const line of String(notes || '').split('\n')) {
    const m = line.match(STEP);
    if (m) steps.push({ text: m[2].trim(), done: m[1] !== ' ' });
    else text.push(line);
  }
  return { text: text.join('\n').trim(), steps };
}

// { text, steps } -> notes. Blank steps are dropped.
export function joinSteps(text, steps) {
  const lines = (steps || []).map((s) => ({ text: clean(s.text), done: !!s.done })).filter((s) => s.text).map((s) => `- [${s.done ? 'x' : ' '}] ${s.text}`);
  return [String(text || '').trim(), ...lines].filter(Boolean).join('\n');
}

// "2 of 5", or '' for a task without steps.
export function stepsCount(steps) {
  return steps && steps.length ? `${steps.filter((s) => s.done).length} of ${steps.length}` : '';
}

// The whole description with step number `index` ticked or unticked, or null if there's no such step. Nothing
// else in the description is touched.
export function flipStep(description, index) {
  let n = -1;
  let found = false;
  const out = String(description || '').replace(MARK, (all, pre, open, mark, close) => {
    n += 1;
    if (n !== index) return all;
    found = true;
    return `${pre}${open}${mark === ' ' ? 'x' : ' '}${close}`;
  });
  return found ? out : null;
}

// The description with every step unticked (a repeating task starting again).
export function resetSteps(description) {
  return String(description || '').replace(MARK, (all, pre, open) => `${pre}${open} ]`);
}
