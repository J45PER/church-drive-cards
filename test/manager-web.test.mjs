import test from 'node:test';
import assert from 'node:assert/strict';
import { authorizeUrl, cleanAddress, parseTokens, siteId, socketUrl, tokenForm } from '../web/manager/ha.js';
import { glowColour, glowStyle, hexToRgb, inkOn } from '../web/manager/glow.js';

test('the site is its own origin with a trailing slash, which is the client id and the way back', () => {
  assert.equal(siteId({ origin: 'https://church-drive-smart-home.web.app' }), 'https://church-drive-smart-home.web.app/');
  const url = new URL(authorizeUrl('https://ha.example', 'https://site.example/', 'abc'));
  assert.equal(url.origin + url.pathname, 'https://ha.example/auth/authorize');
  assert.equal(url.searchParams.get('client_id'), 'https://site.example/');
  assert.equal(url.searchParams.get('redirect_uri'), 'https://site.example/');
  assert.equal(url.searchParams.get('state'), 'abc');
});

test('an address is tidied: https added, no trailing slash', () => {
  assert.equal(cleanAddress('  ha.example.com/ '), 'https://ha.example.com');
  assert.equal(cleanAddress('http://192.168.0.136:8123//'), 'http://192.168.0.136:8123');
  assert.equal(cleanAddress(''), '');
});

test('the token form is for a code the first time and a refresh token after', () => {
  assert.equal(tokenForm('https://s/', { code: 'c' }).get('grant_type'), 'authorization_code');
  assert.equal(tokenForm('https://s/', { code: 'c' }).get('code'), 'c');
  const r = tokenForm('https://s/', { refresh: 'r' });
  assert.equal(r.get('grant_type'), 'refresh_token');
  assert.equal(r.get('refresh_token'), 'r');
  assert.equal(r.get('client_id'), 'https://s/');
});

test('the socket address follows the house address', () => {
  assert.equal(socketUrl('https://ha.example'), 'wss://ha.example/api/websocket');
  assert.equal(socketUrl('http://192.168.0.1:8123'), 'ws://192.168.0.1:8123/api/websocket');
});

test('tokens: none without an access token, and a lifetime of at least a minute', () => {
  assert.equal(parseTokens({ error: 'invalid_grant' }), null);
  assert.equal(parseTokens(null), null);
  const t = parseTokens({ access_token: 'a', refresh_token: 'r', expires_in: 1800 }, 1_000);
  assert.deepEqual(t, { access: 'a', refresh: 'r', expires: 1_000 + 1_800_000 });
  assert.equal(parseTokens({ access_token: 'a', expires_in: 1 }, 0).expires, 60_000);
});

test('the glow is the ground\'s own hue, bright, and a second one turned 28 degrees; a grey ground has none', () => {
  assert.equal(hexToRgb('#493A1D').join(), '73,58,29');
  const a = glowColour('#493A1D', 0), b = glowColour('#493A1D', 28);
  assert.match(a, /^#[0-9a-f]{6}$/);
  assert.notEqual(a, b);
  assert.equal(glowColour('#808080', 0), null);
  assert.equal(glowColour('not a colour', 0), null);
  assert.match(glowStyle('#493A1D'), /--ground:#493A1D;--ga:#[0-9a-f]{6};--gb:#[0-9a-f]{6};/);
  assert.equal(glowStyle('#808080'), '--ground:#808080;');
});

test('text is white on a dark ground and dark on a pale one', () => {
  assert.equal(inkOn('#1D2D49'), '#ffffff');
  assert.equal(inkOn('#F7E3BA'), '#14181f');
});
