#!/usr/bin/env node
// Clive Admin's logo: the house's own shield (cd:shield in design/clive/house-icons.json) with Clive's mark cut out of its middle, as chosen on
// satellite/design/admin-logo.html (idea D). From the same two sources it writes:
//   - the splash rings in companion/android/core/.../ui/LogoRings.kt (the shield, the first cut-out the assistant's second square turns into, and
//     the second cut-out that pops in), between the "admin:start" and "admin:end" markers;
//   - the launcher icon, companion/android/admin/src/main/res/drawable/ic_launcher_foreground.xml and ic_launcher_monochrome.xml, and the
//     one colour list icon companion/android/core/src/main/res/drawable/cd_logo_admin.xml.
// Run it again after the shield or the assistant's rings change:  node tools/gen-admin-logo.mjs
import { readFileSync, writeFileSync } from 'node:fs';

const RINGS = 'companion/android/core/src/main/java/com/churchdrive/app/ui/LogoRings.kt';
const RES = 'companion/android/admin/src/main/res/drawable/';
const N = 160;
const MIDDLE = [100, 93];   // the middle of the shield's wide part, where Clive sits
const K = 0.6;              // how big Clive is in the shield, against his own splash size

const src = readFileSync(RINGS, 'utf8');
const ring = (name) => {
  const m = new RegExp(`private const val ${name} = "([^"]*)"`).exec(src);
  if (!m) throw new Error(`${name} not found in LogoRings.kt`);
  const v = m[1].split(',').map(Number), a = [];
  for (let i = 0; i < v.length; i += 2) a.push([v[i], v[i + 1]]);
  if (a.length !== N) throw new Error(`${name} has ${a.length} points, not ${N}`);
  return a;
};
const assistantA = ring('ASSISTANT_A'), assistantB = ring('ASSISTANT_B');

const area = (a) => a.reduce((s, p, i) => { const q = a[(i + 1) % a.length]; return s + p[0] * q[1] - q[0] * p[1]; }, 0);
// n points evenly by length round a closed polygon, from its first point.
function resample(poly, n) {
  const pts = poly.concat([poly[0]]), cum = [0];
  for (let i = 1; i < pts.length; i++) cum.push(cum[i - 1] + Math.hypot(pts[i][0] - pts[i - 1][0], pts[i][1] - pts[i - 1][1]));
  const L = cum[cum.length - 1], out = [];
  let seg = 1;
  for (let i = 0; i < n; i++) {
    const d = L * i / n;
    while (seg < cum.length - 1 && cum[seg] < d) seg++;
    const t = (d - cum[seg - 1]) / (cum[seg] - cum[seg - 1] || 1);
    out.push([pts[seg - 1][0] + (pts[seg][0] - pts[seg - 1][0]) * t, pts[seg - 1][1] + (pts[seg][1] - pts[seg - 1][1]) * t]);
  }
  return out;
}
// The same way round as the assistant's squares, starting at the top left, so one can turn into the other without a twist.
function orient(a) {
  if ((area(a) > 0) !== (area(assistantA) > 0)) a = a.slice().reverse();
  let k = 0, best = Infinity;
  a.forEach((p, i) => { const v = p[0] * 0.5 + p[1]; if (v < best) { best = v; k = i; } });
  return a.slice(k).concat(a.slice(0, k));
}

const icons = JSON.parse(readFileSync('design/clive/house-icons.json', 'utf8')).icons;
const d = /d='([^']*)'/.exec(icons['cd:shield'].p)[1];
const vertices = [...d.matchAll(/(-?\d+\.?\d*),(-?\d+\.?\d*)/g)].map((m) => [Number(m[1]), Number(m[2])]);
const shield = orient(resample(vertices, N));
// Clive's two squares, small, in the middle of the shield: the second one is the cut-out the assistant's second square turns into, the first pops in.
const small = (a) => a.map(([x, y]) => [MIDDLE[0] + (x - 100) * K, MIDDLE[1] + (y - 100) * K]);
const cutB = small(assistantB), cutA = small(assistantA);

const fmt = (a) => a.map(([x, y]) => `${x.toFixed(1)},${y.toFixed(1)}`).join(',');
const block = `    // admin:start (written by tools/gen-admin-logo.mjs)
    private const val ADMIN_A = "${fmt(shield)}"
    private const val ADMIN_B = "${fmt(cutB)}"
    private const val ADMIN_C = "${fmt(cutA)}"
    // admin:end`;
const valsBlock = `    // admin:vals-start
    val adminA = parse(ADMIN_A)
    val adminB = parse(ADMIN_B)
    val adminC = parse(ADMIN_C)
    // admin:vals-end`;
let out = src;
if (out.includes('// admin:start')) out = out.replace(/ {4}\/\/ admin:start[\s\S]*?\/\/ admin:end/, block);
else out = out.replace(/\n {4}fun parse\(/, `\n${block}\n\n    fun parse(`);
if (out.includes('// admin:vals-start')) out = out.replace(/ {4}\/\/ admin:vals-start[\s\S]*?\/\/ admin:vals-end/, valsBlock);
else out = out.replace(/(\n {4}val satB = parse\(SAT_B\)\n)/, `$1${valsBlock}\n`);
writeFileSync(RINGS, out);

// The launcher icon: the final shapes (shield, then the two small squares, even-odd) in the 200 unit frame, scaled into the 108dp adaptive icon.
const path = (a) => 'M' + a.map(([x, y]) => `${x.toFixed(1)},${y.toFixed(1)}`).join(' L') + 'Z';
const pathData = [shield, cutB, cutA].map(path).join(' ');
const S = 0.405, T = 54 - 100 * S;
const group = (inner) => `    <group android:scaleX="${S}" android:scaleY="${S}" android:translateX="${T.toFixed(3)}" android:translateY="${T.toFixed(3)}">\n${inner}    </group>\n`;
const glow = (cx, cy, r, c0, c1) => `            <aapt:attr name="android:fillColor">
                <gradient android:type="radial" android:centerX="${cx}" android:centerY="${cy}" android:gradientRadius="${r}"><item android:offset="0" android:color="${c0}" /><item android:offset="1" android:color="${c1}" /></gradient>
            </aapt:attr>
`;
const layer = (extra) => `        <path android:fillType="evenOdd" ${extra ? '' : 'android:fillColor="#6A5BC2" '}android:pathData="${pathData}"${extra ? '>\n' + extra + '        </path>' : ' />'}\n`;
const fg = `<?xml version="1.0" encoding="utf-8"?>
<!-- Clive Admin's launcher icon: the house's own shield with Clive's mark cut out of its middle. Generated by tools/gen-admin-logo.mjs; do not redraw. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
` + group(
  layer('') +
  layer(glow(150, 70, 88, '#FFAB47BC', '#00AB47BC')) +
  layer(glow(70, 170, 110, '#FF4FD08A', '#004FD08A')) +
  layer(glow(40, 40, 130, '#FF5C6BC0', '#005C6BC0')) +
  layer(glow(100, 100, 60, '#59FFFFFF', '#00FFFFFF'))
) + `</vector>
`;
const mono = `<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
` + group(`        <path android:fillType="evenOdd" android:fillColor="#000000" android:pathData="${pathData}" />\n`) + `</vector>
`;
// The same shapes as a one colour icon for the app's lists (like cd_logo_todo.xml), tinted by whatever shows it.
const CORE_RES = 'companion/android/core/src/main/res/drawable/';
const logo = `<?xml version="1.0" encoding="utf-8"?>
<!-- Clive Admin's logo as a one colour icon. Generated by tools/gen-admin-logo.mjs; do not redraw. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="20dp" android:height="24dp" android:viewportWidth="124" android:viewportHeight="148.4">
    <group android:translateX="-38" android:translateY="-26">
    <path android:fillColor="#FF000000" android:fillType="evenOdd" android:pathData="${pathData}" />
    </group>
</vector>
`;
writeFileSync(CORE_RES + 'cd_logo_admin.xml', logo);
writeFileSync(RES + 'ic_launcher_foreground.xml', fg);
writeFileSync(RES + 'ic_launcher_monochrome.xml', mono);
console.log(`shield ${shield.length} points; cut-outs ${cutB.length} + ${cutA.length}; launcher icon written`);
