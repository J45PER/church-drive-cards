// The Versions card on the site's Home page: this website's version, the Church Drive integration installed in Home Assistant, the newest release,
// and the newest test build of each app, so any difference between them shows at once. (What each phone has installed is not known here: Google Play
// and the phones keep that; the test builds and the release are what was published.)

const REPO = 'J45PER/church-drive-cards';
const SEMVER = /(\d+)\.(\d+)\.(\d+)/;

/** [major, minor, patch] from any text holding x.y.z, or null. */
export function parseVersion(text) { const m = SEMVER.exec(String(text || '')); return m ? [Number(m[1]), Number(m[2]), Number(m[3])] : null; }
export function compareVersions(a, b) {
  const x = parseVersion(a), y = parseVersion(b); if (!x || !y) return null;
  for (let i = 0; i < 3; i++) if (x[i] !== y[i]) return x[i] < y[i] ? -1 : 1;
  return 0;
}

/** The rolling test builds in GitHub's release list: `Clive Admin 0.133.0 (test build 880)` and the like, by app. */
export function testBuilds(releases) {
  const apps = { 'companion-android-latest': 'Phone and watch app', 'todo-latest': 'To-Do app', 'satellites-latest': 'Satellites app', 'admin-latest': 'Clive Admin app' };
  const out = [];
  for (const [tag, name] of Object.entries(apps)) {
    const r = (releases || []).find((x) => x.tag_name === tag); if (!r) { out.push({ name, version: null, build: null }); continue; }
    const m = /(\d+\.\d+\.\d+).*?build (\d+)/i.exec(r.name || '');
    out.push({ name, version: m ? m[1] : (parseVersion(r.name) || []).join('.') || null, build: m ? m[2] : null });
  }
  return out;
}

/** Rows to show: each with the version it has and whether it matches [expected] (the website's own version). */
export function versionRows({ site, integration, release, builds }) {
  const rows = [
    { name: 'This website', version: site, note: 'built from the repository' },
    { name: 'Church Drive in Home Assistant', version: integration, note: 'installed through HACS' },
    { name: 'Latest release on GitHub', version: release, note: 'what HACS offers' },
    ...builds.map((b) => ({ name: b.name, version: b.version, note: b.build ? `latest test build ${b.build}` : 'no test build found' })),
  ];
  return rows.map((r) => {
    const c = compareVersions(r.version, site);
    return { ...r, status: r.version == null ? 'unknown' : c === 0 ? 'same' : c < 0 ? 'behind' : 'ahead' };
  });
}

export function summary(rows) {
  const off = rows.filter((r) => r.status === 'behind' || r.status === 'ahead');
  const unknown = rows.filter((r) => r.status === 'unknown');
  if (!off.length && !unknown.length) return { tone: 'ok', text: `Everything is on ${rows[0].version}.` };
  const parts = [];
  if (off.length) parts.push(`${off.map((r) => `${r.name} is ${r.status === 'behind' ? 'behind' : 'ahead'} (${r.version})`).join('; ')}`);
  if (unknown.length) parts.push(`could not read: ${unknown.map((r) => r.name).join(', ')}`);
  return { tone: 'warn', text: `${parts.join('. ')}.` };
}

async function json(url, init) { const r = await fetch(url, init); if (!r.ok) throw new Error(String(r.status)); return r.json(); }

/** Reads every source (any that fails is simply unknown) and gives the rows. */
export async function readVersions(ctx) {
  const [site, integration, releases, latest] = await Promise.all([
    json('version.json', { cache: 'no-store' }).then((v) => v.version).catch(() => null),
    ctx.ha.call('manifest/get', { integration: 'church_drive' }).then((m) => m.version).catch(() => null),
    json(`https://api.github.com/repos/${REPO}/releases?per_page=30`).catch(() => null),
    json(`https://api.github.com/repos/${REPO}/releases/latest`).then((r) => r.tag_name).catch(() => null),
  ]);
  return versionRows({ site, integration, release: parseVersion(latest) ? parseVersion(latest).join('.') : null, builds: testBuilds(releases) });
}

/** Who is on what: each person's Church Drive app installs (from the phones the house knows) against the newest version there is.
 *  [people] is the `church_drive/people` answer; [newest] the house's version. Behind first, then by person. */
export function peopleVersions(people, newest) {
  const rows = [];
  for (const p of people?.people || []) {
    for (const ph of p.phones || []) {
      if (!ph.own_app) continue;
      const c = compareVersions(ph.version, newest);
      rows.push({ person: p.first || p.name, phone: ph.name, model: ph.model || '', version: parseVersion(ph.version) ? parseVersion(ph.version).join('.') : null, seen: ph.seen || null,
        status: !parseVersion(ph.version) ? 'unknown' : c == null ? 'unknown' : c === 0 ? 'same' : c < 0 ? 'behind' : 'ahead' });
    }
  }
  const order = { behind: 0, unknown: 1, ahead: 2, same: 3 };
  return rows.sort((a, b) => order[a.status] - order[b.status] || a.person.localeCompare(b.person) || a.phone.localeCompare(b.phone));
}
export async function readPeopleVersions(ctx, newest) { return peopleVersions(await ctx.ha.call('church_drive/people'), newest); }
