# Home hardware plan

Where each service runs in the house, what is existing, what is new, and what is still open. This is a plan, not a record of what is live. When something is built and checked on the real house, update its status here and in [HANDOFF.md](../HANDOFF.md).

Status: **Existing** (running now), **New** (to buy or build), **Moves** (running now, to go elsewhere), **Retire** (remove after a replacement works), **Paused**, **Open** (decision not yet made).

## The three boxes

All three are on the LAN, all three are on a UPS.

| Box | Job | Keep it separate because |
|---|---|---|
| **Home Assistant Green** | Home Assistant only: lights, locks, alarm, Zigbee and Z-Wave radios, Matter, the apps' backend | Lights, locks and the alarm must not slow down or fail because of video, AI or storage work |
| **DXP2800 (UGREEN NAS, Intel N100)** | Storage for the house: HA backups, recordings, the NAS file share, Frigate, go2rtc | Drives are the only place recordings and backups should live, and the NAS has the disk space |
| **Beelink S13 (N150, 16 GB, one SO-DIMM slot, Windows 11 Home to be replaced with Linux)** | Clive's agent service, the small local fallback model, camera event analysis, other light smart home services | AI and extras run here so neither the Green nor the NAS gets busy |

Rules that follow from this:

- Nothing that arms, disarms or sounds the alarm runs on the Beelink or the NAS. It must still work when those are down.
- The Green stays free of extras. Add-ons that aren't needed for the house to work go to the Beelink or NAS.
- If the NAS is down, backups and recordings stop, and Frigate stops. Home Assistant keeps running. The recorder database stays on the Green unless the Green runs short of disk.
- The Beelink holding AI models uses its own SSD. Models are not loaded over the network.

## Services by box

### Home Assistant Green

| Item | Status | Notes |
|---|---|---|
| Home Assistant Core and HA OS | Existing | Core 2026.10.4, HA OS 18.4 |
| Zigbee coordinator (TI CC2652, "Home Assistant Zigbee Mesh") | Existing | Stays. Zigbee radio must be local. |
| Matter Server | Existing | Stays. Devices depend on it on the LAN. |
| Mosquitto broker | Existing | Stays. Ring-MQTT and others use it. |
| Z-Wave JS with Sonoff Z-Wave 800 Dongle Plus (Dongle-PZG23) | New | For localising the Ring Alarm. Stays on the Green so the alarm does not depend on another box. Set region to Europe (868 MHz) once, before pairing. Update the stick's firmware before pairing sensors. |
| HACS, File editor, Custom deps deployment, Duck DNS | Existing | Stay. Small. |
| Samba share | Existing | **Stays**, for editing `/config` (YAML, custom components, dashboards, `go2rtc.yaml`). The NAS gets its own share for general uploads. |
| Ring-MQTT with Video Streaming | Existing, **Retire** | Heaviest add-on, only for the Ring cameras. Remove after the Reolink and Tapo cameras work. |
| go2rtc (Church Drive, Ring fix) | Existing, **Retire** | Ring-only. Replaced by go2rtc on the NAS. Remove after the Reolink and Tapo cameras work. |
| openWakeWord | Existing, **Stays for now** | Moving it to the Beelink only moves the processing. Audio still goes through HA on the Green, and the Beelink becomes a dependency for voice. Revisit when the ESP32-P4 satellite can run its own wake word (microWakeWord), which takes the load off the Green. |
| HA backups | Existing, **Moves** to the NAS | Point HA's backup location at the NAS (network storage). Check the current backup size first. |
| Recorder database (SQLite, about 1.1 GB) | Existing | **Stays** on the Green. Optional move to MariaDB on the NAS, only if the Green's disk runs short. |
| Z-Wave JS | Existing (add-on installed, no controller) | See the New row above. |

Disk note: the Green has a 28 GB disk with 16.6 GB used at the last check. Moving backups off it is the first fix.

### DXP2800 (UGREEN NAS, Intel N100)

| Item | Status | Notes |
|---|---|---|
| Storage pool | New | Two bays. Recordings do not need redundancy in the first version, but drive failure will lose them. Decide before buying drives. |
| HA backups | Moves from the Green | Network storage target for Home Assistant. |
| NAS file share | New | For documents and files people upload. Separate from the Green's Samba. |
| Frigate (detection and recording) | New | Runs in Docker on UGOS. Check the Intel GPU is passed to the container for OpenVINO. Recording mode is **Open** (see below). Frigate's database and config stay on the NAS system volume, not on a network share. |
| go2rtc | New | Streams for the Reolink and Tapo cameras, and two-way talk. Port 8555 forward moves here from the Green. Frigate bundles go2rtc, so one instance can serve both. |
| MariaDB for the recorder | Open | Only if the Green runs short of disk. |
| UPS monitoring | New | Connect the UPS to the NAS (USB or network) so it shuts down cleanly when the battery is low. Expose the UPS status to HA with the NUT integration. |

### Beelink S13 (N150, 16 GB, one SO-DIMM slot)

| Item | Status | Notes |
|---|---|---|
| Ubuntu Server LTS replacing Windows 11 Home | New | Decided. Docker on the host. One named admin account with passwordless sudo, password logins off, Remote Control for access (no inbound ports). Only power and Ethernet connected. Secrets in a root-only env file, not the repo. Unattended security updates on. |
| Clive's agent service | New | HA's Assist pipeline calls this instead of Claude directly. Calls Claude in the cloud for conversation. Uses HA's API for device control. |
| Small local fallback model (3–4B, quantised) | New | Answers simple commands when the internet or the API is down. One model loaded at a time. |
| Camera event analysis (snapshots only) | New | Describes what Frigate detected, using snapshots, not the real-time video path. Decided: a local vision model on the Server, so camera images never leave the house. Slower than a cloud model, so the CPU matters (see the S13 vs EQR6 choice). Frigate on the NAS still does the detection, recording and storage. |
| Other smart home services (light only) | Later | Examples: an energy history database with Grafana, an uptime monitor, DNS filtering. About 8 GB of RAM is the budget. |
| RAM upgrade to 32 GB | Later | Only after one week of real usage shows it's needed. Check the maximum supported RAM first. |

Rules for the Beelink:

- Clive has priority. His service runs in its own container with a memory limit.
- The Beelink is not a single point of failure for the house. If it goes down, Clive and the extras stop, and the Green keeps running everything else.
- Every service runs in its own container so one can be rebuilt without touching the others.

### Cameras

| Camera | Status | Notes |
|---|---|---|
| Ring cameras (front door, driveway, garden, entrance, living room) | Existing, **Retire** | Replace with Reolink. The Ring fix add-on and Ring-MQTT retire with them. |
| Tapo D260 doorbell | New | Replaces the Ring doorbell. Confirm RTSP is enabled and it is wired to the existing doorbell circuit before buying. |
| Reolink wired or PoE cameras | New | Replace the Ring cameras. Only wired or PoE models: battery models can't stream continuously, so Frigate can't use them. |

The camera PoE switch goes on the UPS.

### Network and power

| Item | Status | Notes |
|---|---|---|
| Eero mesh router | Existing | Stays for Wi-Fi. |
| Wired LAN for the three boxes | New | Keep all three on wired connections. The Beelink has dual 2.5G LAN, so one port can be used for the LAN. |
| UPS | New | Covers the Green, Beelink, NAS, router, the camera PoE switch, and the modem or ONT. Size for 15–30 minutes with the NAS, Green, router and cameras running. |
| 5G internet backup | **Paused** | Eero's Hotspot Backup and eero Signal both need an eero Plus subscription. The alternative is a failover router with a 5G SIM, placed in front of the Eero. Both need a check: the Talk-over-5G port forward will not work on 5G (CGNAT), and Nabu Casa remote access still will. Resume when ready. |

## Server monitoring (planned)

The Beelink is called **Server** in code and docs. On screen, in the Admin app and on the website, its label is built from the assistant-name setting (`assistantName`), so it reads **Clive Server** now and follows any rename without a code change. Code and identifiers never hard-code the name. Decided so far:

- **Clive Admin app, a Server page in the sidebar:** status and issues only. Whether the box is up, CPU, memory and disk use, containers that have stopped, and recent problems.
- **Website, a Server detail page:** more history and logs than the app shows.
- **No live shell in the browser.** Anyone who got into the website could run commands on the box, including with the Anthropic key and the HA token. Commands stay in Remote Control, where you approve each session.

Open:
- The website isn't on the list of website pages in CLAUDE.md for this kind of thing. Confirm the detail page belongs on the website, or move it into the Admin app.
- Which metrics and how far back the history goes.

## Footage stays local (rule)

- **No cloud processing of footage.** Video, snapshots, clips and event images are processed and kept on the house's own hardware. Nothing from the cameras goes to a cloud AI service, including Claude. Clive's cloud model only ever gets text.
- **To check before going live:**
  - The D260's on-camera detection and face recognition keep their data on the camera, and don't sync images to Tapo's servers.
  - Remote viewing through Nabu Casa doesn't route video through their relay. If it does, either make remote viewing local-only or record the exception.
  - Talk through go2rtc connects to the camera over the LAN, which it does per the README's examples.
  - Notifications contain no footage, or only what you accept leaves the house.

## Camera streams and talk (decided)

- **Frigate runs on the NAS**, with recordings on its own drives. Not on the Beelink.
- **Detection streams:** Frigate uses a low-resolution substream for detection, and the main stream for recording and live view. Hardware decoding on the NAS's N100.
- **Cameras are wired** (PoE or hardwired doorbell). The D260 needs hardwired power for 24/7 recording, so it's not Wi-Fi-only.
- **Two-way talk:** go2rtc's `tapo://` route takes the **Tapo cloud password** (go2rtc's README says so; not a local camera account). The Tapo account itself is free. Decided: the password is stored in go2rtc's config on the NAS, readable only by the service, and go2rtc's API is not exposed outside the house. Anyone who can read that file gets into the Tapo account, so the NAS must be locked down. The Tapo cloud account can reach every Tapo device on it, so use a separate account for the house if one is set up. Newer firmware may need the SHA256 form of the password; test which one the D260 accepts on the bench.
- **Test before fitting:** confirm talk works from the bench with the password route, and confirm the D260 streams over RTSP.
- **Talk in the dashboard needs HTTPS** (the browser only allows the microphone over HTTPS). Inside the house, use HA's HTTPS address.

## Camera recognition (planned)

Decided:
- **What the Server does with snapshots:** name known people, flag packages and deliveries, and give a short label for everything else (for example "car on the drive"). Alerts are sent for people and packages, not every label.
- **Who is recognised:** the household, plus named friends and family who have each agreed to be enrolled.
- **Where it runs:** on the house's own hardware. Face data is never sent to a cloud service.
- **What people are doing:** a short written description for each event, like Ring's, such as "a person carrying a box walking up the driveway". Runs locally, so it's less polished than Ring's cloud descriptions, and it can be wrong. Treat it as a summary, not a record.
- **Descriptions are kept as long as their clip,** and deleted when the clip is.

Required before going live (not yet done):
- A consent record for each enrolled friend or family member.
- A stated retention period for face data, and a rule that a person's data is deleted when they're removed.
- Notices for visitors and neighbours who may be on camera.
- A short check on the UK data protection rules by someone who knows them. This is a planning note, not legal advice.

## Open decisions

| Decision | Options | Notes |
|---|---|---|
| Frigate recording mode | Events only (recommended), continuous with short retention, continuous with long retention | Not answered yet. Affects drive wear and storage size. |
| Storage pool layout on the NAS | To decide before buying drives | Recordings do not need redundancy, but a drive failure loses them. |
| Ring Alarm sensors | Test one sensor with the Sonoff stick first | Confirm the sensor can pair with a third-party controller, and remove it from Ring's app only after the test passes. |
| Clive's model | Decided: Sonnet 5.5 (see below) | Sonnet 5.5 is the balance for Clive: it handles the answers Haiku 4.5 drifted on, at a higher per-answer cost. Haiku 5.5 is the cheap option for simple jobs. Opus 5.5 is too slow and dear for voice. Still to measure: speech-to-answer timings in the app's Settings. |
| 5G backup | Paused | See above. |

## Order of work

Each step should be checked on the real house before the next one starts.

1. Check the backup size on the Green, then point HA's backups at the NAS.
2. Buy and install the NAS storage, and set up its file share and UPS monitoring.
3. Plug in the Sonoff Z-Wave stick, set the region to Europe, update its firmware, and test one Ring Alarm sensor.
4. Install the Beelink with Linux and Docker, and set up Clive's agent service in a container with a memory limit.
5. Install Frigate and go2rtc on the NAS, then add the Tapo D260 and the first Reolink camera.
6. Move the port 8555 forward to the NAS, and test Talk on the house network and from outside with Nabu Casa.
7. Once every camera works on Frigate, retire the Ring cameras, Ring-MQTT and the go2rtc Ring fix add-on.
8. Decide whether openWakeWord moves, once the ESP32-P4 satellite runs its own wake word.
9. Only then, check whether the 5G backup is still needed and resume it if so.
