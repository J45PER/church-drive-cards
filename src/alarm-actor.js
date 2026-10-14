// Who changed the alarm. Ring only knows its own signed-in account, so the alarm entity says "Disarmed by Hayley" even
// when Jamie did it from the app. The integration records the real person per change in sensor.church_drive_alarm_actor
// (state: first name or "unknown"; attributes alarm, alarm_state, at). This picks between that and Ring's own name:
// the person Home Assistant recorded wins when it is for the alarm's current state and Ring's own time is not much newer
// (a later change from the keypad or Ring app makes Ring's the latest, and Ring's name is right then).

export const ALARM_ACTOR = 'sensor.church_drive_alarm_actor';
const WINDOW_MS = 120000;

// {by, at} for the line "Disarmed by <by>" / "Armed by <by>", or null when there is neither.
export function alarmWho(states, alarmId, alarmState, ringBy, ringAt) {
  const rec = states && states[ALARM_ACTOR];
  const a = rec && rec.attributes;
  if (rec && a && a.alarm === alarmId && a.alarm_state === alarmState && rec.state && !['unknown', 'unavailable'].includes(rec.state)) {
    const ring = ringAt ? Date.parse(ringAt) : NaN;
    const mine = Date.parse(a.at);
    if (!Number.isNaN(mine) && (Number.isNaN(ring) || ring <= mine + WINDOW_MS)) return { by: rec.state, at: a.at };
  }
  return ringBy && ringAt ? { by: ringBy, at: ringAt } : null;
}
