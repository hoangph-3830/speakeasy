---
name: sungen-capture-mobile
description: 'Capture a live mobile app screen via Appium MCP — locator tree + screenshot for visual context. Auto-loaded by create-test when the screen is @platform:mobile/android/ios.'
user-invocable: false
---

## Purpose

Launch a mobile app on a device/emulator, capture **one locator tree** (`generate_locators`) and **one screenshot**, and save them as visual context for test generation. The mobile analogue of `sungen-capture` mode `live` — same job, Appium MCP instead of Playwright MCP.

Use when the target is a native/Flutter/React-Native app (`@platform:android` or `@platform:ios`) running on a connected device or emulator.

---

## Prerequisites

- `appium-mcp` connected (see `/mcp`). It runs Appium **embedded** — no separate server needed.
- A device/emulator booted: `adb devices` shows one for Android; a Simulator for iOS.
- The app **installed** on the device (`adb install -r <app>.apk` for Android).
- The screen's `Path:` (in `<screen>/features/*.feature` or `spec.md`) gives the app entry as
  `<appPackage>/<appActivity>` (Android) or bundleId (iOS), e.g. `com.kngroup.media.converter/.MainActivity`.

---

## Steps

### 1. Resolve target app + entry

Resolve in this order:
1. `Path:` line in the feature / `App ID` in `spec.md` → split into `appPackage` + `appActivity`.
   A dual-id Path (`<pkg>/<activity> | <iosBundleId>`) → take the part for the OS you are exploring
   (left of `|` = Android pkg/activity, right = iOS bundle id).
2. If missing → `AskUserQuestion`: *"What is the appPackage/appActivity (Android) or bundleId (iOS)?"*
3. **Navigation recipe** — read the feature's `Background:` (the in-app web-path analog). Line 1
   `Given User is on [Home] screen` is the launcher/landing screen the app opens on; each following
   `When/And User tap [...]` (or gesture) is one hop toward the target screen. You will **replay**
   these in step 4.5 so you scan the screen this feature targets — not the launcher. An anchor-only
   Background (no nav steps) ⇒ the target IS the launcher screen ⇒ no navigation needed.

### 2. Select device

`select_device` with `platform: android` (or `ios`). If exactly one device, it auto-selects.
If several, list them and ask the user which `deviceUdid`.

### 3. Create the session

`appium_session_management` `action=create`, `platform=android`, capabilities (JSON string):
```
appium:appPackage, appium:appActivity, appium:udid,
appium:noReset=true, appium:autoGrantPermissions=true, appium:newCommandTimeout=300
```
For iOS, call `prepare_ios_simulator` first, then create with `appium:bundleId`.

### 4. ⚠️ Wait for the screen to actually render

**This is the #1 gotcha for Flutter / RN apps.** Right after launch the app often shows a blank
splash — and the locator tree will contain **zero usable elements** (just an empty `FrameLayout`).
Do **not** capture yet. Take a quick `appium_screenshot`; if it's blank/splash, wait and re-check
until real content is drawn. Only then capture. (A Flutter app on a loading screen looks "invisible"
to Appium; the same app on a rendered screen exposes its full Semantics tree.)

### 4.5. ⚠️ Navigate to the target screen (replay the navigation recipe)

**This is what makes capture land on the RIGHT screen** — the mobile equivalent of `page.goto(url)`
on web. After the launcher screen has rendered, replay the `Background:` nav steps so Appium ends up
on the screen this feature targets, *then* capture there.

For each nav step **after** the `Given User is on [Home] screen` anchor, in order:
1. `generate_locators` (or `appium_get_page_source`) on the CURRENT screen.
2. Resolve the step's `[Label]` against the live tree — prefer `accessibility id`, then visible
   `text`/`content-desc`, then an `xpath` on a distinctive substring. Gestures (`scroll to [X]`,
   `swipe …`) follow `sungen-mobile-gestures`.
3. Perform the tap/gesture, then **wait for the next screen to render** (same blank-splash guard as
   step 4) before the next step.

After the LAST step you are on the target screen — capture HERE (steps 5–6).

- **Anchor-only Background** (no nav steps) ⇒ the target IS the launcher screen ⇒ skip this step.
- **A step won't resolve** (its `[Label]` matches no element) ⇒ **STOP. Do not capture the wrong
  screen.** Screenshot where you are and report: *"recipe step N: [Label] not found — fix the
  Background nav path."* Fail loud (run-test would fail at the same step anyway).
- **No recipe yet** (guided-stub Background, but the target is NOT the launcher) ⇒ drive to the
  screen interactively off the live tree (if unsure which control leads there, `AskUserQuestion`),
  then **report the exact nav steps you took** so create-test/add-screen can write them into the
  Background. Do **not** edit the `.feature` yourself — Gherkin authorship stays with those commands.

### 5. Capture the locator tree (primary AI context)

Call **`generate_locators`** — this is the mobile equivalent of the web accessibility snapshot. It
returns priority-ranked locators per interactable element: `accessibility id`, `id`, platform-native
(`-android uiautomator` / `-ios predicate string`), and `xpath`, plus `content-desc`/`text`,
`clickable`, `enabled`. This is what `sungen-tc-generation` and `sungen-selector-keys` consume.

If the tree is huge, also `appium_get_page_source` (saved to a file) and grep it — never dump the
full XML inline.

### 6. Screenshot

`appium_screenshot` → save to:
```
qa/screens/<screen>/requirements/ui/mobile-<timestamp>.png
```
`<timestamp>` = `YYYYMMDD-HHMM` local (e.g. `mobile-20260605-1645.png`).

### 7. Detect discrepancies vs spec

If `spec.md` exists, cross-check `generate_locators` labels against spec sections (fields in spec but
not on screen, and vice-versa). Report; do **not** auto-edit `spec.md`.

### 8. End the session

`appium_session_management` `action=delete` to free the device. Always clean up.

### 9. Report back

> Captured mobile screen `<appPackage>`:
> - Reached via: <N nav steps replayed from the Background recipe, or "launcher screen — no nav">
> - Locators: <N> interactable elements (M with accessibility-id)
> - Screenshot: `requirements/ui/mobile-<timestamp>.png`
> - Discrepancies vs spec: <count, or "none">
> - Discovered nav steps (only if the recipe was empty): <the taps you took, for create-test/add-screen to write into the Background>

Hand back to the calling command.

---

## Cross-platform discovery (`@platform:mobile` — explore one OS, layer the rest)

When the screen is **`@platform:mobile`** (write-once-run-both), discover the **shared** cases first,
then peel off only what is genuinely OS-specific. You do **not** need to explore both OSes up front —
Flutter/RN expose the same Semantics on each, so one exploration covers most of it.

1. **Explore whichever OS has a device up** (Android or iOS — symmetric for Flutter). Capture the tree.
2. **Classify each component** into THREE buckets (not two):
   - **Shared-exact** → a stable `accessibility id` equal on both OSes (Flutter Semantics → same value on
     Android `content-desc` AND iOS `name`) → flat `type: accessibility-id` key in `<screen>.feature`.
   - **Shared-variant** → SAME step/assertion on both OSes but the LOCATOR differs (composite
     `content-desc` vs `-ios predicate`; partial/dynamic text via `descriptionContains` vs predicate
     `CONTAINS`; a different attribute) → STILL `<screen>.feature`, with `android:`/`ios:` variant keys in
     selectors.yaml. **A native locator alone is NOT a reason to split** — default for composite/partial text.
   - **Sub-feature** → ONLY for genuine SCENARIO divergence: an element on ONE OS only, divergent
     steps/flow, an OS-exclusive gesture/permission/dialog flow, or a case needing a per-platform
     `nth`/`scope` (a variant can't diverge those). → `<screen>-android.feature` / `<screen>-ios.feature`.
3. **Report the classification** so create-test/run-test scaffolds the right thing:
   - `<screen>.feature`         `@platform:mobile`  ← shared-exact AND shared-variant (the vast majority)
   - `<screen>-android.feature` `@platform:android` ← ONLY scenario-divergent Android cases
   - `<screen>-ios.feature`     `@platform:ios`     ← ONLY scenario-divergent iOS cases
4. **Verifying the other OS needs no second exploration** — just running the shared spec there
   (`MOBILE_PLATFORM=both`) IS the check. Re-capture on the 2nd OS only for components that fail, and
   move those into that OS's sub-feature. (Asking devs to add a Semantics label to an unlabeled element
   converts an OS-specific case back into a shared one — prefer that when feasible.)

## Selector-quality notes (carry into selectors.yaml)

- **Prefer `accessibility id`** (Android `content-desc` / iOS `accessibilityIdentifier`) — most stable.
- For **Flutter**, the Semantics label surfaces as `content-desc` ⇒ it usually equals the **visible
  text**, so it is **locale-dependent** ("Good afternoon!", "English"). Keep such values in test-data
  and use `{{variable}}` + locale overlays (see `sungen-locale`).
- **Composite nodes**: cards may concatenate child text into one `content-desc` with `\n` → prefer an
  xpath `contains(@content-desc,'…')` on a distinctive substring.
- **Duplicate labels** → use `nth`. **Unlabelled controls** (search fields, icon buttons) → fall back
  to `xpath` / class.

---

## What this skill does NOT do

- Does not run tests or generate `selectors.yaml` (that's `/sungen:run-test` + `sungen-selector-fix-mobile`)
- Does not generate Gherkin (that's `sungen-tc-generation`) — it may *report* discovered nav steps,
  but writing them into the Background is create-test/add-screen's job
- Does not handle login/auth state (mobile auth is a separate concern — deep link / test build)
- Captures **the feature's target screen** per invocation — reached by replaying the Background
  navigation recipe (step 4.5), not just the launcher. For a *different* screen, give it its own
  feature + recipe and re-invoke.

---

## Relationship to other capture skills

- `sungen-capture` (modes `figma-mcp` / `figma-pat` / `local`) — design/image sources (platform-agnostic)
- `sungen-capture` (mode `live`) — web (Playwright MCP)
- `sungen-capture-mobile` — this skill, mobile (Appium MCP)

All write to `requirements/ui/` and report back to the caller.
