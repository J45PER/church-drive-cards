# Church Drive Android app: working rules

Before you change or add anything under `app/src/main/java/com/churchdrive/app/ui/`, read `UI-STANDARDS.md` in this
folder and follow its checklist: look at the dashboard card first, use the kit (`TileRow`, `IconRow`, `OptionRow`,
`EntityCard`, `CentredText`, the sizes in `Ui`), never a raw Material button, route mode icons through `IconMap`, and
keep a row's tiles filling its width. `UiStandardsTest` enforces the mechanical rules; if it fails, fix the change,
don't weaken the test.

Build and test happen in CI (there is no Android SDK in the sandbox): push to `companion-apps`, wait for the
"Companion Android" run, and only report an APK once it is green. Keep commits to `companion/` only; never run
`git checkout origin/main -- .` on this branch.
