# appium-environment/ — one command to a working Appium machine

Emitted by `sungen init --mobile` (or `sungen capability add mobile`), which also runs it once.
It provisions everything **below** the Appium layer — the part QA used to do by hand — and ends with
`appium driver doctor`. Every step is **check → install → verify**: a satisfied step is skipped, so the
bundle is safe to re-run any time (a new machine, a teammate's clone, after an OS update).

```bash
sungen setup-mobile-env                       # any OS — emits this folder if missing, then runs it
./appium-environment/setup.sh                 # macOS, directly
powershell -NoProfile -ExecutionPolicy Bypass -File .\appium-environment\setup.ps1   # Windows, directly
```

| Flag (macOS) | Flag (Windows) | Effect |
|---|---|---|
| `--check` | `-Check` | report what is missing, install nothing (side-effect free) |
| `--skip-android` | `-SkipAndroid` | skip JDK / Android SDK / uiautomator2 |
| `--skip-ios` | — | skip Xcode CLT / applesimutils / xcuitest (iOS needs a Mac) |
| `--no-profile` | `-NoProfile` | do not write env vars to your shell profile / User environment |

Exit code: `0` ready (or only warnings) · `1` something failed · `2` unsupported OS / bad flag.
`sungen init` treats a non-zero exit as a warning — the project scaffold is already complete.

## What it installs (and what it only checks)

| Step | macOS (`setup.sh`) | Windows (`setup.ps1`) |
|---|---|---|
| Node.js ≥ 18 | check only (sungen needs it already) | check only |
| Package manager | Homebrew — installed if missing | winget — check only (App Installer, Microsoft Store) |
| JDK ≥ 17 | `brew install openjdk@21` (an existing JDK ≥ 17 is kept) | `winget install Microsoft.OpenJDK.21` (existing JDK ≥ 17 kept) |
| Android SDK root | existing `ANDROID_HOME` / Android Studio SDK / `brew install --cask android-commandlinetools` | existing / `%LOCALAPPDATA%\Android\Sdk` / Google's command-line tools zip |
| `platform-tools` (adb) + `emulator` | `sdkmanager` | `sdkmanager` |
| Env vars | marked block in `~/.zshrc` (or `~/.bash_profile`) | User environment (`ANDROID_HOME`, `ANDROID_SDK_ROOT`, `JAVA_HOME`, `Path`) |
| Android Studio | **check + hint only** (optional — AVD manager / on-screen emulator) | same |
| Xcode Command Line Tools | `xcode-select --install` (Apple's dialog — finish it, re-run) | — |
| Xcode.app | **check + hint only** (App Store; required for iOS) | — |
| Xcode license | `sudo xcodebuild -license accept` | — |
| `applesimutils` | `brew tap wix/brew && brew install applesimutils` | — |
| Appium | **project-local** (`node_modules/.bin/appium`, from `@sungen/driver-mobile`) — never global | same |
| Appium drivers | `uiautomator2` + `xcuitest` into `~/.appium` | `uiautomator2` |
| Health | `adb`, env vars, devices, Simulators, `appium driver doctor` | `adb`, env vars, devices, `appium driver doctor` |

Nothing is installed globally with npm. GUI apps (Xcode.app, Android Studio) are never installed for you —
the step tells you exactly where to get them and re-running the script picks up from there.

## The env-var block

macOS: the lines live between `# >>> sungen mobile env >>>` and `# <<< sungen mobile env <<<` in your
shell profile; re-running rewrites only that block, and it is skipped entirely when your shell already
exports matching values. To undo, delete the block. Windows: plain User-scope variables — undo in
*System Properties → Environment Variables*.

**Open a new terminal after the first run** so `PATH` / `JAVA_HOME` / `ANDROID_HOME` apply.

## Known limitations

- **Linux** is not provisioned (the entry point prints a notice and exits 0). Android on Linux: install a
  JDK ≥ 17 and the Android command-line tools with your package manager, export the same variables, then
  `sungen capability add mobile` for the project-local Appium + `uiautomator2`.
- **`sungen update` does not refresh this folder.** To pick up a newer bundle, delete `appium-environment/`
  and run `sungen capability add mobile` again (all other files are skipped because they already exist).
- Windows downloads Google's command-line tools by a **pinned build number** (`$CmdlineToolsBuild` at the
  top of `scripts/windows/install-android.ps1`). If the download 404s, bump it from
  https://developer.android.com/studio#command-line-tools-only.
- No AVD / Simulator is created and no phone is paired — plug in a device (USB debugging on) or start an
  emulator before `/sungen:run-test`.

## Manual fallback (the steps this bundle automates)

- Android: https://sungen.sun-asterisk.vn/docs/mobile/appium-setup-android
- iOS: https://sungen.sun-asterisk.vn/docs/mobile/appium-setup-ios
- Overview + troubleshooting: https://sungen.sun-asterisk.vn/docs/mobile/appium-setup ·
  https://sungen.sun-asterisk.vn/docs/mobile/mobile-troubleshooting

## Layout

```
appium-environment/
├── setup.sh · setup.ps1          entry points (OS check, flags, step order, summary)
├── scripts/common/               lib.{sh,ps1} step runner · verify-node · install-appium · verify-appium
├── scripts/macos/                install-android · verify-android · install-ios · verify-ios
└── scripts/windows/              install-android · verify-android
```
