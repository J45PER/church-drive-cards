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
- `Church Drive: Zappi safety net` (entity id still ends `_00_40`): follows the car's role. For the Primary car it runs only 10 minutes into an Octopus dispatch (Octopus decides when that car charges), and its alert says to check the car and its VW connection first. For an Additional or unanswered car it runs only 10 minutes after the cheap-rate sensor (`binary_sensor.octopus_energy_electricity_..._off_peak`) turns on, so it follows the window if Octopus moves it.

## App and cards (0.55.0)
- The phone app has the same choice as the dashboard: the Home page "Which car is plugged in?" card (Primary / Additional / No) and the notification buttons, and the charger card shows Primary / Additional under Smart charge plus the Octopus rate now. In the app it is two tiles, not a drop-down, to follow the app's look-and-feel rules (`companion/android/UI-STANDARDS.md`).
- The Car card (dashboard and app) lists the ID.5 from the Octopus Intelligent device: battery %, "Octopus charging until ..." or "Next Octopus charge ... to ...". Octopus gives no plug, range or place, so it shows as plugged in and charging when the Zappi is and the role is Primary. Range and location need a VW integration (the card already supports `vag_connect`, `volkswagencarnet` and `volkswagen_we_connect_id` if one is added).

- **Octopus charging plan** (dashboard and app): the Octopus charging plan card (`octopus-plan-card`, in the Car charging panel on the Home and Energy pages, and in the app) lists the unfinished entries of the dispatching sensor's `planned_dispatches` (Smart, or Boost) with their kWh. Before 0.88.0 it sat inside the charger card.
- **Boost is not on the cards.** Octopus bills a Boost charge at the standard rate even at night ("the car rate flips back to this rate (even if it happens at 2:00 AM ...)"), and Charge Cap stops the ID.5 before that rate, so the switch (`switch.octopus_energy_..._intelligent_bump_charge`) was removed from the charger card in 0.88.0. Octopus swaps its smart plan for a BOOST dispatch while it is on; whether charging another car (the Astra) stops later smart charging is untested.

## What we found on 31 Oct to 1 Nov 2026
- **Octopus decides when the ID.5 draws power.** With the Zappi left on Fast, the car charged only in Octopus's dispatches (02:02 to 03:21 and 04:02 to 04:12 on 1 Nov, 63% to 80%) and stopped between them; the Zappi then shows "Completed / Waiting for EV", which is the car not asking for power, not a full car. The short 23:00 to 23:30 dispatches are usually ignored by the car.
- **Octopus's API fails for the ID.5 now and then.** The Octopus integration logged `KT-CT-4340 "Unable to fetch planned dispatches"` many times on 31 Oct (12:13, 13:10, 21:30, 22:03, 00:02); Home Assistant then keeps showing its last saved plan until the next successful fetch.
- **Remote mode changes on the Zappi were ignored.** Home Assistant and the myenergi app both sent Fast/Eco/Stopped; myenergi's cloud answered 200 but the hub kept the mode it had and the select reverted about 6 to 10 seconds later (`pymyenergi` writes the new mode into its own data at once and never reads the reply). Only setting it at the unit worked. The first revert was on 31 Oct at 14:51, 26 minutes after Octopus began controlling the ID.5. Cause not found; to try: power-cycle the Zappi, check the myenergi app's boost timers and any Octopus link, then raise it with myenergi (hub raw data had `cmt` stuck at 2 to 5 and `bst: 1` while boosting).
- **Unlock** is sent as `/cgi-jlock-<serial>-00000010` (no `Z` prefix, unlike the mode commands); it gets a 200 reply but the lock sensor stays on, so it proves nothing.
- Debug logging for the myenergi integration (and `logger.set_level` for `pymyenergi`) shows these requests in the Home Assistant log; both reset on a restart.

## Still to do
- The compact Charging card does not show the car choice yet; the full card does.
- Watch whether Octopus really holds the ID.5 off outside its slots before considering "always on" for the Primary car.
- The Zappi reverted Fast to Stopped within seconds on 31 Oct 2026 while locked; see the session notes. The cause is on the charger or myenergi side.
