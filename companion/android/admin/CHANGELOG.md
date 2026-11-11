# Clive Admin: changelog

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
