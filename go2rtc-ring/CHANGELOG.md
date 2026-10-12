# Changelog

## 1.9.14-ring.3

- The Ring offer is now H264 only. go2rtc offered H264 and H265 on every WebRTC connection, and
  Ring answered the battery cameras (Battery Video Doorbell Plus, Outdoor Cam) with H265. go2rtc then
  made an empty H264 track for the browser player and a separate H265 track holding all the data,
  so the player sat on "loading" while picture arrived. Ring's own client offers H264 only and gets
  H264. This is go2rtc issue #1961. Other sources are unchanged (they still offer H265).

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
