# Church Drive Android app: working rules

Before you change or add anything under `app/src/main/java/com/churchdrive/app/ui/`, read `UI-STANDARDS.md` in this
folder and follow its checklist: look at the dashboard card first, use the kit (`TileRow`, `IconRow`, `OptionRow`,
`EntityCard`, `CentredText`, the sizes in `Ui`), never a raw Material button, route mode icons through `IconMap`, and
keep a row's tiles filling its width. `UiStandardsTest` enforces the mechanical rules; if it fails, fix the change,
don't weaken the test.

The project has four modules: `core` (Home Assistant connection, sign-in storage, to-do/Clive data: package names are still `com.churchdrive.app.*`), `app` (the phone app), `wear` (the Wear OS app: same application id as `app`, so it is the same Play listing) and `todo` (the to-do-only phone app). Code the watch or to-do app needs goes in `core`.

Build and test happen in CI (there is no Android SDK in the sandbox): push to `companion-apps`, wait for the
"Companion Android" run, and only report an APK once it is green. Keep commits to `companion/` only; never run
`git checkout origin/main -- .` on this branch.

Never name code identifiers after the assistant (no `Jarvis*` or `Clive*`): it has been renamed once already. Use `Assistant*` and take the displayed name from the house (`AssistModel.name`). Only the wake word id (`hey_jarvis`, a model name) and old Clive-log ids (never reused) may keep the old name.

When Clive (the assistant) gains a feature, changes how something works or has a fix, add an entry at the top of `ASSIST_FEATURES`
(the Clive log, `core/src/main/java/com/churchdrive/app/ui/AssistData.kt`: it lives in the shared `core` module because the watch app uses it too) with a new, never-reused `id`. Each person is told about each entry once; the full
introduction is only for the first time ever, so never rely on the app's version to bring it back.

Releases: the phone app (with the watch app) and Church Drive To-Do are versioned and released separately, but **always keep their version numbers equal** (bump both in every release, with a changelog line in each) so the Play bundles match. The phone app's version is `version.properties` and its notes `CHANGELOG.md` (releases `android-v<version>` and `companion-android-latest`, which the app's update check reads); To-Do's are `todo/version.properties` and `todo/CHANGELOG.md` (releases `todo-v<version>` and `todo-latest`). CI publishes each only when a push touches it; a change in `core` or the build files touches both, so bump both versions and note it in both changelogs.
