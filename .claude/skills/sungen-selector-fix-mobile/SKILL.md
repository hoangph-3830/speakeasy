---
name: sungen-selector-fix-mobile
description: 'Mobile selector fixing — Appium MCP exploration, accessibility-id-first strategy, run via wdio. Auto-loaded by run-test for @platform:mobile/android/ios screens.'
user-invocable: false
---

## Strategy: Phased Execution (mobile)

Mobile analogue of `sungen-selector-fix`. Same philosophy — pre-generate selectors, smoke-check, fix
in priority waves — but using **Appium MCP** for exploration and **WebdriverIO** (`npm run test:mobile`)
to run, instead of Playwright MCP / `playwright test`.

**Never run blindly.** Pre-generate selectors → smoke check → widen.

---

## Phase 0: Pre-run Selector Generation (Appium MCP)

Populate `selectors.yaml` from the live app before compiling so tests don't fail on missing keys.

### When to run
- `selectors.yaml` missing/empty, or `[Reference]` keys lack entries and aren't auto-inferable.
- User re-scans after a UI change.

### Steps
1. **Confirm** via `AskUserQuestion`: *"Generate selectors from the live app via Appium MCP now?"* —
   **Yes, scan device** / **Skip (use existing)** / **Cancel**.
2. **Collect references**: parse the `.feature` for every `[Reference]` + type. Deduplicate.
3. **Launch the app** (delegate to `sungen-capture-mobile` or inline):
   `select_device` → `appium_session_management(create)` with appPackage/appActivity from the feature `Path:`.
4. **⚠️ Wait for render** before capturing — a Flutter/RN splash exposes zero elements. Screenshot first;
   if blank, wait and re-check.
5. **Capture**: `generate_locators` (priority-ranked locators) for the current screen. Navigate
   (`appium_gesture tap` / `back`) to reach each screen referenced in the feature, capturing per screen.
6. **Generate YAML entries**:
   - Keys: follow `sungen-selector-keys` (lowercase, Unicode preserved, `--N` suffix for duplicates).
   - **Selector priority (mobile)** — see Step 3 table: `accessibility-id` > `id` > platform-native > `xpath`.
   - Copy `content-desc`/`accessibility id` **character-for-character** from `generate_locators`. Never
     infer from the Gherkin label.
   - **i18n / Flutter**: `content-desc` often equals the visible text and is locale-dependent. Use
     `{{variable}}` in `value` and add `lbl_*` keys to test-data + locale overlays (see `sungen-locale`).
7. **Duplicate check**: if a `content-desc` appears more than once in the tree, add `nth`.
8. **Merge, don't overwrite**: keep user-authored entries; add only missing keys.
9. **Confirm + write**, then **compile**: `sungen generate --screen <screen> --framework appium`.
10. **End session** (`action=delete`) when done exploring.

---

## Phase 0.5: Mobile auth — the auth contract (guided builder)

Mobile has **no `storageState`** like web. Instead, `@auth:<role>` features authenticate at runtime
through an **auth contract** at `specs/.auth-mobile/<role>.json` (gitignored): the generated spec's
`before(all)` hook (`__ensureAuth`) reaches the logged-in state — **idempotent** (skips login when the
authed marker is already present thanks to `noReset:true`) and **fail-loud** (throws when the contract
is missing or login fails, so @auth scenarios are blocked instead of silently running logged-out).

**Pre-flight (BEFORE Phase 1):** when the feature carries `@auth:<role>`, check the contract exists:

```bash
ls specs/.auth-mobile/<role>.json 2>/dev/null || echo MISSING
```

Present → continue to Phase 1 (the hook handles everything). Missing → run the **guided builder**:

### Guided contract builder

1. `AskUserQuestion`:
   - **Build the contract now (Recommended)** — AI drives the app via Appium MCP and writes the file
   - **I'll write it by hand** — print the schema below and stop
   - **Skip** — @auth scenarios will fail loud with instructions
2. **Logged-out baseline**: Appium session (`select_device` → `appium_session_management(create)`,
   `noReset:true`) → activate the app → `appium_get_page_source` → keep the element list.
3. **Reach the login form**: ask the user how to navigate there (typical: account/profile tab → login
   entry). Record EVERY tap on the way as a `loginSteps` entry, in order.
4. **Credentials**: never echo passwords into chat. Reference them as `{{email}}` / `{{password}}` in
   `loginSteps` and have the user fill the `credentials` map in the file themselves (it is gitignored).
5. **Log in once** — either AI drives (`appium_gesture(tap)` + `appium_set_value`) with values the user
   provides, or the user logs in by hand on the device. Both are fine; what matters is the end state.
6. **Derive the `authedMarker`**: fresh `appium_get_page_source` → DIFF against the step-2 baseline.
   Candidates = elements present ONLY after login (member-only menu item, avatar, logout). Prefer
   accessibility-id; verify with `appium_find_element`. Never pick an element guests also have.
7. **Write the contract** (schema below) and **validate**: terminate + relaunch the app → the marker is
   still present (session persisted). Full loop: run one @auth scenario via
   `SUNGEN_SPECS=<spec> npm run test:mobile`.

### Contract schema — `specs/.auth-mobile/<role>.json` (gitignored)

```jsonc
{
  "authedMarker": { "type": "accessibility-id", "value": "<element only present when logged in>" },
  "credentials": { "email": "...", "password": "..." },          // keys are free-form per auth method
  "loginSteps": [
    { "action": "tap",  "selector": { "type": "accessibility-id", "value": "<account tab>" } },
    { "action": "tap",  "selector": { "type": "accessibility-id", "value": "<login entry>" } },
    { "action": "fill", "selector": { "type": "accessibility-id", "value": "<email field>" }, "value": "{{email}}" },
    { "action": "fill", "selector": { "type": "accessibility-id", "value": "<password field>" }, "value": "{{password}}" },
    { "action": "tap",  "selector": { "type": "accessibility-id", "value": "<submit>" } }
    // app shows a launch interstitial/ad? add its dismiss tap as the FIRST step
  ]
}
```

- `selector.type`: `accessibility-id` | `xpath` | `android-uiautomator` | `ios-predicate` | `id` | `text`/`label`/`placeholder`
- `action`: `tap` | `fill` (robust fill: type → read back → retry) | `wait`
- Session model: `noReset:true` keeps the login alive across scenarios. i18n runs flip `noReset:false`
  (device-locale re-detect) which wipes the session — re-login in the contract or keep @auth suites on
  the base locale.
- The non-automatable parts (OTP, captcha, biometrics) stay manual: have the user complete them once in
  step 5; the persisted session + `authedMarker` check carries every later run.
---

## Phases 1–4: Run waves via WebdriverIO

Mobile runs go through the wdio runner (auto-selects `@wdio/globals` specs — the mobile ones):

```bash
# Smoke: a single scenario / spec
SUNGEN_SPECS='./specs/generated/<screen>/<feature>.spec.ts' npm run test:mobile

# Full mobile suite
npm run test:mobile
```

- **Phase 1 (smoke)**: run the one screen's spec. Max 2 fix attempts.
- **Phase 2 (priority)**: run the screen/feature suite. Max 2 fix attempts.
- **Phase 3 (full)**: `npm run test:mobile`. Max 1 fix attempt.
- **Phase 4 (regression)**: one final `npm run test:mobile`. No fix loop.

> wdio/mocha tag-grep differs from Playwright. For a subset, scope with `SUNGEN_SPECS` or
> `--mochaOpts.grep '<scenario name>'`.

---

## Diagnosis & Fix (mobile)

### Step 1: Parse failures — and distinguish selector vs. state

| Error pattern | Likely root cause | Fix target |
|---|---|---|
| `element wasn't found` / `can't call click on "~X"` | **selector mismatch** *or* **app on the wrong screen** | selectors.yaml **or** scenario state (see ⚠️ below) |
| `waitForDisplayed` timeout | wrong locator, or element not yet rendered | selectors.yaml / add wait |
| `toHaveText` mismatch | wrong expected data (or locale) | test-data.yaml |
| `session not created` / `app not installed` | capability / install issue | wdio.conf capabilities / `adb install` |

**⚠️ State, not selector.** The most common mobile false alarm: a selector "not found" because the app
is **on a different screen than the scenario assumed** (e.g. a prior scenario left it deep in a sub-page;
`appium:noReset=true` keeps state across the whole run). Before "fixing" a selector, **verify the app's
current screen** with `appium_screenshot`. If the app is simply on the wrong screen, the fix is in the
**scenario/navigation** (ensure each scenario starts from a known state — e.g. an explicit "navigate to
Home" step, `back`, or relaunch), not in `selectors.yaml`.

### Step 2: Targeted MCP exploration
1. `select_device` + `appium_session_management(create)` (or reuse an open session).
2. Navigate to the failing screen (`appium_gesture`), **wait for render**.
3. `generate_locators` (or `appium_find_element` to test one candidate). Fix all broken selectors from
   this snapshot. Use `appium_ai` (vision) only as a last resort for low-accessibility screens.

### Step 3: Fix broken selectors — mobile priority

| Priority | type | When |
|---|---|---|
| 1 | `accessibility-id` | element has `content-desc` / accessibilityIdentifier (most stable) |
| 2 | `id` | Android resource-id present |
| 3 | `android-uiautomator` / `ios-predicate` | platform-native, when 1–2 unavailable |
| 4 | `xpath` | last resort — slow & brittle; prefer `contains(@content-desc,'…')` |

Common fixes:
- Name mismatch → copy exact `content-desc` from `generate_locators`.
- Multiple matches → add `nth`.
- Composite `\n` content-desc → `xpath` with `contains` on a unique substring.
- No label (icon button, search field) → `xpath` / class, or ask devs to add a Semantics label / `Key`.
- Locale-dependent text → `{{variable}}` + locale overlay (don't hardcode "English"/"Good afternoon!").

### Step 4: Recompile after fix
```bash
sungen generate --screen <screen> --framework appium
```
Then re-run only the current phase.

---

## Cross-platform selectors (`@platform:mobile` — write once, run both)

A `@platform:mobile` feature runs on **both** the Android and iOS capability sets in one
`MOBILE_PLATFORM=both` run, so **every selector must resolve on both OSes**. Only `accessibility-id`
is truly cross-platform: a Flutter `Semantics(label/identifier)` surfaces as Android `content-desc`
**and** iOS `name`/`accessibilityIdentifier`, so a single `~Label` matches on each.

**Rule for `@platform:mobile`: use `type: accessibility-id` for everything.** Avoid the platform-only
strategies — a spec that uses them passes on the OS you tested and silently fails on the other:

| Strategy | OK on | NOT on | Why it breaks cross-platform |
|---|---|---|---|
| `accessibility-id` (`~Label`) | Android + iOS | — | ✅ the one to use |
| `//*[contains(@content-desc,'…')]` / `@text` xpath | Android | iOS | iOS has no `content-desc`/`text` attribute |
| `android-uiautomator` (UiScrollable, UiSelector) | Android | iOS | UiAutomator2 only |
| `id=` (resource-id) | Android | iOS | Android resource-id only |
| `-ios predicate string:` / `-ios class chain` | iOS | Android | XCUITest only |

When scanning a `@platform:mobile` screen with Appium MCP, **verify on both a booted Android device
and an iOS simulator** and keep only selectors whose value matches on each (for Flutter the label is
normally identical). If a required element is labeled on one OS but not the other (e.g. an icon button
with no Semantics label on Android), either ask devs to add a matching `Semantics`/`accessibilityIdentifier`,
or **split the feature** into `@platform:android` + `@platform:ios` files (each may then use its native
strategy). Do NOT smuggle an Android-only xpath into a `@platform:mobile` selector set.

> The app id may differ per OS (e.g. a Flutter `.dev` flavor on iOS). You don't handle that in
> selectors — the generated spec resolves it at runtime (`driver.isIOS` → iOS bundle id from the
> session caps, else the Android package from `Path:`). Selectors only need to be label-stable.

For `@platform:android`-only or `@platform:ios`-only features, the **full** Step 3 priority table
applies — platform-native strategies are fine because the spec runs on that one OS.

---

## Per-platform selector variants (`android:` / `ios:` in selectors.yaml)

When a logical element can't be one cross-platform selector — **composite** content-desc, **partial/dynamic**
text (greeting, substrings), or **per-OS-divergent** labels — give the key `android:` and `ios:` sub-selectors
instead of a flat `type`/`value`. For a `@platform:mobile` feature the generated spec renders a runtime
`driver.isIOS ? '<ios>' : '<android>'` per key (so ONE shared scenario resolves the right native selector on
each OS); a `@platform:android` / `@platform:ios` feature picks that variant directly.

```yaml
# composite on Android (contains) / predicate on iOS — one shared scenario, both OSes
"settings--title":
  android: { type: xpath, value: "//*[contains(@content-desc,'{{settings_subtitle}}')]" }
  ios:     { type: ios-predicate, value: "name CONTAINS '{{settings_subtitle}}' OR label CONTAINS '{{settings_subtitle}}'" }
```

- Define **both** variants for a `@platform:mobile` key (a missing side falls back to the bare entry).
- `{{var}}` interpolation works inside each variant. **A shared `nth`/`scope`/`name` (at the key's top
  level) applies to both OSes, but `nth`/`scope`/`name` CANNOT diverge per-platform** — the codegen ternary
  swaps only strategy+value (`appium-selector-expr.hbs`). If Android and iOS need a DIFFERENT `nth`/`scope`,
  that's a sub-feature, not a variant.
- **Prefer a single exact `accessibility-id`** when the label is genuinely exact on both OSes (e.g. a clean
  Flutter Semantics label) — only reach for variants when exact can't work. Verified example: "Output Format"
  is exact → one `accessibility-id` passes both; "Select Image"/"Select Video" are composite → need variants.
- This is the in-place alternative to splitting into per-OS sub-features: variants keep the scenario shared
  and only the SELECTOR differs per OS (no scenario duplication). Use sub-features when the *scenario itself*
  (steps/flow) differs per OS, variants when only the *locator* differs.

> Below-fold on iOS: `scroll to` IS cross-platform (Android UiScrollable / iOS swipe-loop), and
> `tap top of` on iOS taps the TOP-LEFT of the element — on short iOS viewports a bottom-row card may
> peek only a few pt above a floating bottom nav whose raised centre FAB occludes the top-CENTRE
> (verified live on the converter; top-left tap opens it). If a list genuinely cannot scroll further
> and the element stays fully occluded, only then split that case to `@platform:android`.

## Layered workflow & per-OS sub-features

A screen's mobile coverage is **layered**. The shared file is the DEFAULT — exact accessibility-id OR
`android:`/`ios:` selector variants (when only the locator differs). Push a case to a per-OS sub-feature
ONLY on genuine SCENARIO divergence (different steps/flow, OS-only element, per-platform `nth`/`scope`):

| File | Tag | Runs on | Selectors |
|---|---|---|---|
| `<screen>.feature` | `@platform:mobile` | Android **and** iOS | `accessibility-id` only |
| `<screen>-android.feature` | `@platform:android` | Android only | full Android arsenal (`id`, `-android uiautomator`, `@content-desc` xpath, …) |
| `<screen>-ios.feature` | `@platform:ios` | iOS only | full iOS arsenal (`-ios predicate`, …) |

**Discovery order (explore one OS, run the other):**
1. Explore the available OS (`sungen-capture-mobile`), write the **shared** cases into `<screen>.feature`
   with accessibility-id selectors.
2. Run them on the other OS: `MOBILE_PLATFORM=both npm run test:mobile`. Green ⇒ done, no second
   exploration needed.
3. For any element that **fails** on the 2nd OS, first add an `android:`/`ios:` selector VARIANT to its key
   (the usual fix — composite content-desc / predicate / partial text) and re-run; the scenario stays
   shared. Move the case into `<screen>-<os>.feature` ONLY if the FLOW itself diverges or a per-platform
   `nth`/`scope` is required (a variant can't diverge those).

**Running the layers** (each `.feature` compiles to its own spec, routed by its `// sungen:platform=` marker):
```bash
MOBILE_PLATFORM=both    npm run test:mobile   # shared specs on BOTH + each OS's own sub-feature specs
MOBILE_PLATFORM=android npm run test:mobile   # shared + @platform:android only
MOBILE_PLATFORM=ios     npm run test:mobile   # shared + @platform:ios only
```
(`wdio.conf` gives the Android cap the android+mobile specs and the iOS cap the ios+mobile specs, so a
shared spec runs on each and a sub-feature spec runs only on its OS.)

## Scrolling to off-screen elements (Flutter caveat)

`scroll to [X]` compiles to `.scrollIntoView()`, which needs a native `android.widget.ScrollView`.
Flutter/RN scrollables usually don't expose one → it errors ("Default scrollable element not found").
For a **Flutter scrollable list**, prefer making the *target selector* auto-scroll via an
`android-uiautomator` UiScrollable instead of a separate scroll step:

```yaml
"hindi option":
  type: android-uiautomator
  value: 'new UiScrollable(new UiSelector().scrollable(true)).scrollIntoView(new UiSelector().descriptionContains("हिन्दी"))'
```

When `$()` resolves that selector, UiAutomator2 scrolls the list until the element is found — no
separate `scroll to` needed. If the screen **isn't scrollable** (content fits the viewport) and an
element is merely occluded by a floating bottom bar, neither scroll nor `element.click()` works → tag
that scenario `@manual` until visible-point tapping lands.

## Flutter / React-Native note

These render to a canvas, but expose a **Semantics tree** to Appium as `content-desc` once a real screen
is drawn — so UiAutomator2 + `accessibility-id` usually works (no flutter-driver needed) **provided** the
app sets semantic labels. If `generate_locators` returns almost nothing on a populated screen, the app
likely lacks semantics → escalate: ask devs to add `Semantics`/`Key`, or consider `appium-flutter-driver`
(needs a debug/profile build). A blank result on a *loading* screen just means "wait for render".

---

## Attempt Budget Summary

| Phase | What runs | Max fix attempts |
|---|---|---|
| 0. Pre-gen | Appium MCP `generate_locators` → selectors.yaml | 1 scan |
| 1. Smoke | one screen spec via wdio | 2 |
| 2. Priority | screen/feature suite | 2 |
| 3. Full | `npm run test:mobile` | 1 |
| 4. Regression | final `npm run test:mobile` | 0 |
