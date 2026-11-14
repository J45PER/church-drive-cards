# Church Drive: working rules for every chat

Read this first. The longer notes are in `HANDOFF.md` (the house and the apps), `docs/MANAGER.md` (the Manager website and Clive Admin) and `docs/STYLE.md` (how everything looks).

## Asking Jamie about features

**Ask every question about a feature one at a time, as multiple choice.** Use the question tool (`AskUserQuestion`) with two to four distinct options, put the one you recommend first and mark it "(Recommended)", and give each option a one-line consequence. Never send a list of open questions in one message, and never ask a feature question in plain prose when a choice will do. Wait for the answer before asking the next one. Jamie can always pick "Other" to answer in their own words.

This applies to: what a page or feature should do, where it lives (website, Clive Admin, phone app, Home Assistant), what to keep or remove, privacy and recording choices, and wording or layout choices. It does not apply to routine steps already agreed (bumping a version, running tests, following the release process).

## Designing

- Design proposals are built as a tweakable example page first (a published Artifact, tweak panel hidden by default), page by page, and ported to the live site only once signed off. Keep the example site 1:1 with the live site.
- The apps are the style reference (`docs/STYLE.md`). The website adapts layout only. No outlines or borders unless the app has them. No figure repeated on a page. A missing figure reads "Data not available".
- Read-only access to the house is available for checking real entities; check a page against the real entity names, not only a mock.

## Where things live (decided 2026-11-14)

- **Clive Admin app:** windows into the house for administrators (Energy, Devices, Device health, Activity, and a read-only view of Manager): things hidden or hard to reach in the main app.
- **Website:** the control panel for how the apps look and behave, and for Clive himself: Design (colours, icons, layout), Clive (animations, chats, activity, his devices), Settings, Edits. Activity stays on both. Energy, Devices and Device health are not on the website.
- **Home Assistant:** keeps its own dashboards. The website does not copy them.
