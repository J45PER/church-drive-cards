// Builds both bundles:
//   custom_components/church_drive/frontend/church-drive-cards.js
//       the release, shipped inside the Church Drive integration (HACS)
//   church-drive-cards-beta.js
//       the same code with `-beta` card names, for testing on the Design
//       Presets "Beta" tab (loaded from jsDelivr at a pinned commit)
// It also copies the package.json version into the integration's manifest.
// `node build.mjs --watch` rebuilds both on change.
import * as esbuild from 'esbuild';
import { readFileSync, writeFileSync } from 'node:fs';

const MANIFEST = 'custom_components/church_drive/manifest.json';
const { version } = JSON.parse(readFileSync('package.json', 'utf8'));
const manifest = JSON.parse(readFileSync(MANIFEST, 'utf8'));
if (manifest.version !== version) {
  manifest.version = version;
  writeFileSync(MANIFEST, JSON.stringify(manifest, null, 2) + '\n');
}

const builds = [
  { outfile: 'custom_components/church_drive/frontend/church-drive-cards.js', define: {} },
  { outfile: 'church-drive-cards-beta.js', define: { __CARD_SUFFIX__: '"-beta"' } },
];

const watch = process.argv.includes('--watch');
for (const { outfile, define } of builds) {
  const options = { entryPoints: ['src/index.js'], bundle: true, format: 'iife', outfile, define, logLevel: 'info' };
  if (watch) await (await esbuild.context(options)).watch();
  else await esbuild.build(options);
}
