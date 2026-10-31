# Zappi + Intelligent Octopus Go

Since 27 Oct 2026 the house is on Intelligent Octopus Go (`E-1R-IOG-SMB-FIX-12M-26-10-27-B`). The live automations in Home Assistant are the source of truth; the YAML copies in this folder (`zappi-smart-charge.yaml`, `zappi-charge-later.yaml`, `zappi-catches-up.yaml`) predate this change and are not restore points for it.

## The tariff, in short
- The house pays the cheap rate (7.9p) from **23:30 to 05:30** every day (it was 00:30 to 05:30 on Go). Anything else is 36.8p.
- Octopus controls the ID.5 through VW's cloud (provider `VOLKSWAGEN`) and schedules up to 6 hours a day. A slot outside the window also makes the whole house cheap for that half-hour (`is_intelligent_adjusted` in the rates). Only the ID.5 is on the Octopus app.
- Octopus entities used: `binary_sensor.octopus_energy_electricity_24l3478610_1160001231179_off_peak` (the 23:30 to 05:30 window) and `binary_sensor.octopus_energy_00000000_0002_4000_8020_00000012e6f1_intelligent_dispatching` (a scheduled slot). The ID.5 also has a charge target, ready-by time, bump charge and smart charge switch (all on the Octopus device).

## What the Zappi does now
Charging always uses **Fast**; Eco and Eco+ are not used by smart charge. The gate is open when:

| Car (`input_select.zappi_car_role`) | Open when |
|---|---|
| Primary (the car on the Octopus app, today the ID.5) | off-peak window **or** Octopus is dispatching |
| Additional (any other car, today the Astra) | off-peak window only |
| Unknown (not answered) | off-peak window only, and nothing starts until the car is confirmed |

"Charge now" (card, app, notification) is still the manual override: it takes over for that plug-in until the car is unplugged.

## Changes made in Home Assistant (31 Oct 2026)
- New helper `input_select.zappi_car_role` (Unknown, Primary, Additional). Cleared when the car is unplugged.
- `Church Drive: car alerts`: the plug-in question is "Which car is plugged in?" with Primary / Additional / No (`CD_car_primary`, `CD_car_additional`, `CD_car_no`).
- `Church Drive: Zappi car confirmation`: handles the new buttons, clears the role on unplug, asks again hourly with the new wording. The old `CD_car_yes` button still counts as Primary.
- `script.zappi_car_yes` takes an optional `role` (Primary by default), records it, confirms the car.
- `Church Drive: Zappi smart charge`: "cheap" is now the gate above instead of comparing rates; also re-checks when the off-peak or dispatching sensors or the car role change. `Zappi smart charge ready by` and `max price` are no longer used (left in place).
- `Church Drive: Zappi safety net` (entity id still ends `_00_40`): runs at 23:40 and 10 minutes into a dispatch for the Primary car.

## App and cards (0.55.0)
- The phone app has the same choice as the dashboard: the Home page "Which car is plugged in?" card (Primary / Additional / No) and the notification buttons, and the charger card shows Primary / Additional under Smart charge plus the Octopus rate now. In the app it is two tiles, not a drop-down, to follow the app's look-and-feel rules (`companion/android/UI-STANDARDS.md`).
- The Car card (dashboard and app) lists the ID.5 from the Octopus Intelligent device: battery %, "Octopus charging until ..." or "Next Octopus charge ... to ...". Octopus gives no plug, range or place, so it shows as plugged in and charging when the Zappi is and the role is Primary. Range and location need a VW integration (the card already supports `vag_connect`, `volkswagencarnet` and `volkswagen_we_connect_id` if one is added).

## Still to do
- The compact Charging card does not show the car choice yet; the full card does.
- Watch whether Octopus really holds the ID.5 off outside its slots before considering "always on" for the Primary car.
- The Zappi reverted Fast to Stopped within seconds on 31 Oct 2026 while locked; see the session notes. The cause is on the charger or myenergi side.
