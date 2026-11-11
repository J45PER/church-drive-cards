# Clive Admin: changelog

## 0.135.0

- The "A newer version of Clive Admin is ready" banner (with its Update button) now shows on every page, as in the other apps; it was missing on the Health tab.
- Clive Admin has the same status pill as the Church Drive app at the top of the page: "Connecting", "Updating", "No connection" (tap to try again) and "Home Assistant restarting". Pull down on any page to refresh the connection. The pill at the top of the page shows it: "Pull to refresh", "Release to refresh", "Updating" and then "Up to date". 

## 0.134.0

- No change (this release adds the cards, icons and layouts editors to the Manager website and Home Assistant).

## 0.133.0

- The Energy page is the same as the Church Drive app's: the header lines (cheap or peak now, live use, today so far), then Octopus Electricity, Octopus Gas, Last 7 days, Car charging and Octoplus in that order, each with its icon, colour and summary, read from the house's dashboard.
- The Octopus Energy logo shows beside Octopus Electricity and Octopus Gas.

## 0.132.0

- The bottom bar is now Home, Health, Energy and Activity, so Device health and Energy are one tap away; Home keeps a card for each.
- Colour palettes moved to the side panel (swipe left, or the person's button), under Open Church Drive.
- Manager, in the side panel: the Manager's sections as the house's Manager dashboard lists them. Notifications, Locations, Camera links, Icons, the To-do settings and the house jobs are in Clive Admin now; People, Who's home, Device health list, Alarm lights, Watch and the automatic to-dos say they are still in the Church Drive app and open it. Nothing is removed from the Church Drive app yet.

## 0.131.0

- The launcher icon's shield is smaller and centred, so it no longer looks oversized next to the other Clive apps.

## 0.130.0

- Activity log: what people and the assistant did, and who came and went, as in the phone app (it is in both apps).
- Device health: batteries, filters and devices that stopped answering, from a card on Home.
- Energy: electricity and the cheap times, the last seven days, gas and Octoplus, from a card on Home.
- Home now has a card for each; the bottom bar is Home, Activity and Colours.

## 0.129.0

- The Google Play version uses the Church Drive app's sign-in when it is on the phone and signed in (the phone app now trusts this app's Play signing certificate), so it no longer asks to sign in.
- Its own logo and splash: the house's shield with Clive's mark cut out of its middle. On the splash Clive's two squares turn into the shield and its cut-out, then the second square pops in. The launcher icon, the sign-in screen and About use it too.

## 0.128.0

- The first version of Clive Admin, the app for the house's administrators.
- Signs in with the Home Assistant login, or uses the Church Drive app's sign-in if it is on the phone and signed in. Anyone who is not an administrator is told so.
- Laid out like the other Church Drive apps: a title bar with the person's button, Home and Colours along the bottom, tinted cards with the Clive glow, and the account panel sliding in from the right (from the person's button or a swipe left) with Open Church Drive, About and Sign out.
- Colour palettes, view only: each palette is a card in its own colours with its five colours named under it, and the ones changed in Home Assistant are marked. Palettes are changed on the website.
- About (in the account panel): the version and build, where updates come from, and this app's signing certificate (SHA-256) with a Copy button, so it can be added to the trusted list for the Church Drive app's shared sign-in.
- Placeholder logo and splash until Clive Admin's own are chosen.
- Manager, Energy and Device Health are still in the Church Drive phone app; they move here one at a time once this app is proven.
