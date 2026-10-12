# go2rtc (Church Drive, Ring fix)

A drop-in replacement for the **go2rtc-hevc-fix** add-on (and the official go2rtc add-on). It is
go2rtc **1.9.14**, built from source on your device, with two patches:

1. **HEVC aggregation packets** (upstream go2rtc PR #2296): the same patch as go2rtc-hevc-fix.
2. **Ring signalling fix** (`go2rtc-ring.patch`), for the Ring *battery* cameras that go2rtc's Ring
   source never starts (it waits for Ring forever, with nothing in the log).

## What the Ring patch changes

All in go2rtc's Ring source (`pkg/ring`). Nothing else about go2rtc changes.

- **Logs the signalling.** With `log: ring: debug` in the go2rtc config, every message sent to and
  received from Ring is logged (method and field names; ICE candidates and notification text are
  shown; the login ticket, tokens and the SDP are not). `trace` also logs Ring's answer SDP.
- **Accepts Ring's answer whichever message carries the session id.** go2rtc only learnt the
  session id from `session_created` and silently dropped everything else until then, so an answer
  that came first (or without it) was lost and go2rtc waited forever.
- **Answers Ring's pings with a pong,** as Ring's own client library does.
- **Gives up with a reason.** After 30 seconds (`&connect_timeout=N` on the ring URL changes it)
  it stops, closes the Ring session and logs where it stuck (answer received or not, ICE and
  connection state) instead of leaving a battery camera streaming.

## Installing

1. **Settings, Add-ons, Add-on Store, three dots, Repositories**, add
   `https://github.com/J45PER/church-drive-cards`.
2. Install **go2rtc (Church Drive, Ring fix)**. It is **built on your device** the first time
   (a few minutes). The build fails, and says why, if either patch does not apply or its tests fail.
3. **Stop** the old go2rtc add-on (go2rtc-hevc-fix) first: both use ports 1984, 8554 and 8555.
   Then **start** this one. Turn on *Start on boot* and *Watchdog*.
4. Check its **Config** tab shows your streams. This add-on maps the same config folder as
   go2rtc-hevc-fix; if it is empty, paste your config in.

To go back: stop this add-on and start the old one again. Nothing else is changed.

## Trying the battery cameras

Add the camera back under `streams:` (the Ring wizard in the go2rtc UI makes the line), turn on
`log: ring: debug`, restart, open the stream's **webrtc** player, then read the add-on's **Log** tab.

```
log:
  ring: debug
```
