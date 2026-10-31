# One version number

The Home Assistant integration and its cards bundle, the phone app (and the watch app that ships with it), Church Drive To-Do and Church Drive Satellites all carry the **same x.y.z**, bumped together. It started at **0.80.0** (2026-10-31).

## Changing it

```bash
node tools/versions.mjs            # shows every version and fails if they differ (CI runs this)
node tools/versions.mjs set 0.81.0 # sets them all, then rebuilds the bundles so the manifest and cards bundle follow
```

Then add a line to `CHANGELOG.md` and a `## 0.81.0` section to each Android changelog (`companion/android/CHANGELOG.md`, `todo/CHANGELOG.md`, `satellites/CHANGELOG.md`). The release notes of each app come from its own section, and fall back to "Version 0.81.0." if it is missing.

## Where each lives

| What | File | Released as |
|---|---|---|
| Home Assistant integration and cards | `package.json` (`build.mjs` copies it into `custom_components/church_drive/manifest.json` and into both bundles) | `v<version>` (HACS) |
| Phone app and watch app | `companion/android/version.properties` | `android-v<version>`, `companion-android-latest` |
| To-Do | `companion/android/todo/version.properties` | `todo-v<version>`, `todo-latest` |
| Satellites | `companion/android/satellites/version.properties` | `satellites-v<version>`, `satellites-latest` |

## Build numbers are separate

Every CI build has GitHub's run number. Android and Google Play compare that (the `versionCode`), not the version, so a new build never needs a version bump, and the version going from 0.54.4 to 0.80.0 changes nothing for Play. A version is bumped when there is something to say in a changelog.

Each version is released once, the first time a build carries it. The apps' bundles are named `church-drive-<app>-<version>-b<build>.aab`, so the file says which app, version and build it is.

## Not covered

The satellite firmware (`satellite/`) is not versioned here: it is flashed by hand or over the air from ESPHome. It would take this version once it is released to a real device.
