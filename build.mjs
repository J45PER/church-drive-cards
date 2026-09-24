// Builds both bundles:
//   church-drive-cards.js       the release (installed via HACS)
//   church-drive-cards-beta.js  the same code with `-beta` card names, for
//                               testing on the Design Presets "Beta" tab
// `node build.mjs --watch` rebuilds both on change.
import * as esbuild from 'esbuild';

const builds = [
  { outfile: 'church-drive-cards.js', define: {} },
  { outfile: 'church-drive-cards-beta.js', define: { __CARD_SUFFIX__: '"-beta"' } },
];

const watch = process.argv.includes('--watch');
for (const { outfile, define } of builds) {
  const options = { entryPoints: ['src/index.js'], bundle: true, format: 'iife', outfile, define, logLevel: 'info' };
  if (watch) await (await esbuild.context(options)).watch();
  else await esbuild.build(options);
}
