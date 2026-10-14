import { test } from 'node:test';
import assert from 'node:assert/strict';
import { alarmWho, ALARM_ACTOR } from '../src/alarm-actor.js';

const rec = (state, alarm, alarmState, at) => ({ [ALARM_ACTOR]: { state, attributes: { alarm, alarm_state: alarmState, at } } });
const ID = 'alarm_control_panel.church_drive_alarm';

test("the person Home Assistant recorded wins over Ring's account", () => {
  const s = rec('Jamie', ID, 'disarmed', '2026-10-13T14:00:05+00:00');
  assert.deepEqual(alarmWho(s, ID, 'disarmed', 'Hayley Smith', '2026-10-13T14:00:02Z'), { by: 'Jamie', at: '2026-10-13T14:00:05+00:00' });
});

test("Ring's name is used when nothing was recorded, or it was not a login", () => {
  assert.deepEqual(alarmWho({}, ID, 'disarmed', 'Hayley', '2026-10-13T14:00:02Z'), { by: 'Hayley', at: '2026-10-13T14:00:02Z' });
  const s = rec('unknown', ID, 'disarmed', '2026-10-13T14:00:05+00:00');
  assert.equal(alarmWho(s, ID, 'disarmed', 'Hayley', '2026-10-13T14:00:02Z').by, 'Hayley');
});

test('a record for another state or another alarm is not used', () => {
  assert.equal(alarmWho(rec('Jamie', ID, 'armed_away', '2026-10-13T14:00:05+00:00'), ID, 'disarmed', 'Hayley', '2026-10-13T14:00:02Z').by, 'Hayley');
  assert.equal(alarmWho(rec('Jamie', 'alarm_control_panel.other', 'disarmed', '2026-10-13T14:00:05+00:00'), ID, 'disarmed', 'Hayley', '2026-10-13T14:00:02Z').by, 'Hayley');
});

test("a later change by Ring (keypad) is Ring's, not the older recorded one", () => {
  const s = rec('Jamie', ID, 'disarmed', '2026-10-13T14:00:05+00:00');
  assert.equal(alarmWho(s, ID, 'disarmed', 'Diane', '2026-10-13T14:30:00Z').by, 'Diane');
});

test('with neither there is no line', () => {
  assert.equal(alarmWho({}, ID, 'disarmed', undefined, undefined), null);
});
