# Changelog

## 1.9.14-ring.1

- First version: go2rtc 1.9.14 with the HEVC aggregation-packet fix (PR #2296) and the Ring
  signalling fix: logging (`log: ring: debug`), accepting the answer before `session_created`,
  replying to Ring's pings, and a 30 second connect timeout that says where it stuck.
