// Builds every registered card editor with a stub config and a fake hass,
// and reports any that throw (HA would then fall back to YAML for that card).
// Run after `npm run build`:
//   node tools/editors-smoke.mjs
// Uses Playwright's Chromium. Set PLAYWRIGHT_MODULE if Playwright isn't
// installed in this project (e.g. /opt/node22/lib/node_modules/playwright/index.mjs).
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const { chromium } = await import(process.env.PLAYWRIGHT_MODULE || 'playwright');
const page = path.join(path.dirname(fileURLToPath(import.meta.url)), 'editors-smoke.html');
const browser = await chromium.launch({ args: ['--allow-file-access-from-files'] });
const p = await browser.newPage();
p.on('pageerror', (e) => console.log('pageerror', e.message));
await p.goto(`file://${page}`);
await p.waitForFunction(() => window.__out, null, { timeout: 20000 });
const out = await p.evaluate(() => window.__out);
const errs = await p.evaluate(() => window.__errs);
console.log(out.join('\n'));
const bad = out.filter((l) => !l.endsWith(': ok')).length + errs.length;
console.log(bad ? `FAILED: ${bad}` : `All ${out.length} editors ok`);
await browser.close();
process.exit(bad ? 1 : 0);
