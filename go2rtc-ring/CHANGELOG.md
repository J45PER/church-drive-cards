# Changelog

## 1.9.14-ring.2

- Asks Ring for a keyframe every 2 seconds (Ring only), as go2rtc already does for other sources
  and as Ring's own client does. Ring battery cameras connected fine but gave the player no picture.
- With `log: ring: debug`, logs for the first minute after connecting whether video and audio
  packets are really arriving (packets, bytes, loss), so "connected but no picture" can be told
  apart from "no media".

## 1.9.14-ring.1

- First version: go2rtc 1.9.14 with the HEVC aggregation-packet fix (PR #2296) and the Ring
  signalling fix: logging (`log: ring: debug`), accepting the answer before `session_created`,
  replying to Ring's pings, and a 30 second connect timeout that says where it stuck.
