#!/usr/bin/env node
// One version number for the whole house: the Home Assistant integration and its cards bundle, the phone app (and the watch app that
// ships with it), Church Drive To-Do and Church Drive Satellites all carry the same x.y.z, bumped together.
//
//   node tools/versions.mjs                check that every one of them agrees (CI runs this); exits 1 if not
//   node tools/versions.mjs set 0.80.1     set them all, then rebuild the bundles (npm run build) so the manifest and bundles follow
//
// Where each lives: package.json (the integration; build.mjs copies it into custom_components/church_drive/manifest.json and
// into both bundles) and the four version.properties files below. Build numbers are separate: every CI build has GitHub's run number,
// which is what Android and Play compare (versionCode), so a version does not have to be bumped for a new build.
import { readFileSync, writeFileSync } from 'node:fs';
import { execSync } from 'node:child_process';

const PROPS = [
  ['phone and watch app', 'companion/android/version.properties'],
  ['To-Do app', 'companion/android/todo/version.properties'],
  ['Satellites app', 'companion/android/satellites/version.properties'],
];
const MANIFEST = 'custom_components/church_drive/manifest.json';
const SEMVER = /^\d+\.\d+\.\d+$/;

const propVersion = (file) => (readFileSync(file, 'utf8').match(/^version=(.*)$/m) || [])[1]?.trim();
const found = () => [
  ['Home Assistant integration (package.json)', JSON.parse(readFileSync('package.json', 'utf8')).version],
  ['Integration manifest', JSON.parse(readFileSync(MANIFEST, 'utf8')).version],
  ...PROPS.map(([name, file]) => [name, propVersion(file)]),
];

const [cmd, arg] = process.argv.slice(2);
if (cmd === 'set') {
  if (!SEMVER.test(arg || '')) {
    console.error('Usage: node tools/versions.mjs set <x.y.z>');
    process.exit(2);
  }
  const pkg = JSON.parse(readFileSync('package.json', 'utf8'));
  pkg.version = arg;
  writeFileSync('package.json', JSON.stringify(pkg, null, 2) + '\n');
  for (const [, file] of PROPS) {
    writeFileSync(file, readFileSync(file, 'utf8').replace(/^version=.*$/m, `version=${arg}`));
  }
  execSync('npm run build', { stdio: 'inherit' });
  console.log(`\nAll set to ${arg}. Add a line to CHANGELOG.md and a "## ${arg}" section to each Android CHANGELOG.md (companion/android, todo, satellites).`);
} else {
  const rows = found();
  const versions = new Set(rows.map(([, v]) => v));
  for (const [name, v] of rows) console.log(`${(v || '(missing)').padEnd(10)} ${name}`);
  if (versions.size !== 1 || versions.has(undefined) || ![...versions].every((v) => SEMVER.test(v))) {
    console.error('\nThe versions do not agree. Run: node tools/versions.mjs set <x.y.z>');
    process.exit(1);
  }
  console.log(`\nAll ${rows.length} agree: ${[...versions][0]}`);
}
