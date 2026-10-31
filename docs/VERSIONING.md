# One version number

The Home Assistant integration and its cards bundle, the phone app (and the watch app that ships with it), Church Drive To-Do and Church Drive Satellites all carry the **same x.y.z**, bumped together. It started at **0.80.0** (2026-10-31).

## Changing it

```bash
node tools/versions.mjs            # shows every version and fails if they differ (CI runs this)
node tools/versions.mjs set 0.81.0 # sets them all, then rebuilds the bundles so the manifest and cards bundle follow
```

Then add a line to `CHANGELOG.md` and a `## 0.81.0` section to each Android changelog (`companion/android/CHANGELOG.md`, `todo/CHANGELOG.md`, `satellites/CHANGELOG.md`). The release notes of each app come from its own section, and fall back to "Version 0.81.0." if it is missing.

## Where each lives

| What | File |
|---|---|
| Home Assistant integration and cards | `package.json` (`build.mjs` copies it into `custom_components/church_drive/manifest.json` and into both bundles) |
| Phone app and watch app | `companion/android/version.properties` |
| To-Do | `companion/android/todo/version.properties` |
| Satellites | `companion/android/satellites/version.properties` |

## One release per version

Everything for a version is on **one GitHub release, `v<version>`**: the Home Assistant integration (HACS reads it, and `church-drive-cards.js` is attached), and every app's APK and Google Play bundle, named `church-drive-<app>-<version>-b<build>.apk` / `.aab` (the phone app is `church-drive-manager`, the watch bundle `church-drive-manager-wear`, then `church-drive-todo` and `church-drive-satellites`).

`release.yml` makes the release for the integration; the Companion Android workflow adds each app's files to it with `tools/publish-release-assets.sh`, creating the release itself if it gets there first. Each app's files are added once, the first time a build has that version, and that is also when its bundle goes to Google Play's internal testing. A later build of the same version is not added. The manual "Upload to Google Play" workflow reads the bundles from the same release.

Separate from these, three rolling **test-build** prereleases are replaced on every push that touches the app: `companion-android-latest`, `todo-latest` and `satellites-latest`. The apps' update check reads the first two to offer GitHub test builds, so they stay.

Releases before 0.80.0 and the 0.80.0 ones already made are the old per-app releases (`android-v...`, `todo-v...`); they are left as they are.

## Build numbers are separate

Every CI build has GitHub's run number. Android and Google Play compare that (the `versionCode`), not the version, so a new build never needs a version bump, and the version going from 0.54.4 to 0.80.0 changes nothing for Play. A version is bumped when there is something to say in a changelog.



## Not covered

The satellite firmware (`satellite/`) is not versioned here: it is flashed by hand or over the air from ESPHome. It would take this version once it is released to a real device.
