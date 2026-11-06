---
name: sungen-mobile-gestures
description: 'Mobile gesture patterns (swipe, long-press, scroll-to, pinch, pull-to-refresh) — Gherkin syntax + Appium MCP mapping. Auto-loaded for @platform:mobile/android/ios screens.'
user-invocable: false
---

## Purpose

Document the **mobile-only interactions** that have no web equivalent, so the AI can (a) drive them
during exploration via Appium MCP and (b) write Gherkin steps for them. These are gestures the web
patterns (`click`, `hover`, `fill`) don't cover.

> Codegen status: the Appium adapter **compiles** all of the following (verified against the shipped
> `.hbs` under `adapters/appium/templates/steps/{gestures,actions}/`):
> - **`tap` / `taps`** — synonym for `click` (→ `.click()`); **`double-tap`** → double-click.
> - **`scroll to [X]`** → `.scrollIntoView()` (shared `scroll-action` template).
> - **`swipe <dir> on [X]`** → `mobile: swipeGesture`.
> - **`long-press [X] [for N seconds]`** → `mobile: longClickGesture`.
> - **`rotate to landscape|portrait`** → `driver.setOrientation(...)`.
> - **`go back`** (also `goes back` · `press the back button` · `hardware|system|device back` ·
>   `back gesture` · `swipe back`) → Android `mobile: pressKey` with keycode 4 (KEYCODE_BACK — the
>   real hardware key); iOS `driver.back()`, which is WebDriverAgent's **navigation** back and *not*
>   the user's edge swipe-back gesture — iOS has no hardware back key. **This is NOT `tap [Back]`:**
>   Android's system back does not run the screen's own handler unless the app wires it
>   (`onBackPressed` / `BackHandler`), so a defect on the OS path is invisible to an in-app tap —
>   which is why the viewpoint ledger refuses the in-app button as an answer to its "OS/hardware
>   back gesture" theme. The step takes **no `[Ref]`**; that absence is what selects it. A phrasing
>   that names a target (`go back to [List] screen`) is refused rather than reinterpreted, so pick
>   one form: `go back`, or a tap.
>   **`swipe back` is only a spelling of this step, not a swipe.** It compiles to the same
>   `KEYCODE_BACK` / `driver.back()` as every other phrasing above, so on iOS — where the edge
>   swipe-back *is* what a real user does — a scenario named for the gesture never performs it. If
>   the edge swipe itself is under test, that needs a left-edge `mobile: swipe`, which no step
>   currently emits: tag it `@manual` rather than letting the wording imply coverage it does not have.
> - **`pull-to-refresh on [X]`** → fast `mobile: swipeGesture` (direction down).
> - **`pinch-zoom in|out on [X]`** → `mobile: pinchOpenGesture` / `pinchCloseGesture`.
> - **`send app to background for N seconds`** → `driver.background(N)`.
> - **`open notification panel`** → `driver.openNotifications()`. **Android-only** — XCUITest has no
>   notification-shade automation; on iOS the generated step throws loud. Keep such scenarios `@platform:android`.
> - **`tap top of [X]`** / **`tap [X] at top`** → tap the element's **visible top edge** (`mobile: clickGesture`
>   at top-centre from the element bounds) instead of its centre — use when the centre is occluded by a
>   floating bottom bar so a normal centre tap would hit the bar.
> - **`dismiss [X]`** (any non-alert element) → best-effort tap-if-shown-within-2.5s, never fails the
>   step (`dismiss-action`) — for launch interstitials / promo overlays, not the native alert dialog
>   (that's the existing `accept/dismiss [OK] alert` form).
> - **`hide the keyboard`** → `driver.hideKeyboard()`. **Android-only** — XCUITest cannot dismiss the
>   keyboard generically (WDA throws + retries, ~12s wasted); on iOS the step is a silent best-effort
>   no-op (never fails, just does nothing).
> - **`grant [X] permission`** → Android `mobile: changePermissions`; iOS (Simulator only) `mobile:
>   setPermission` — needs the `applesimutils` binary on iOS or the driver fails loud with the install hint.
> - **`set the clipboard to {{v}}`** → `driver.setClipboard(...)`. **Write-only**: the appium adapter
>   ships a `clipboard-text-assertion` template that reads the clipboard back, but no Gherkin pattern
>   requests it yet — there is currently no phrasing that compiles to an actual clipboard-read
>   assertion. Don't author "see clipboard contains X" expecting it to compile; tag it `@manual` instead.
> - **`set location to {{lat}}, {{lng}}`** → `driver.setGeoLocation(...)`.
> - **`restart the app`** → kill → relaunch → content settle (same oracle as the per-scenario
>   relaunch). THE step for persistence oracles: pinned card, dismissal survival, show-once tooltips.
> - **`reinstall the app`** → remove → install from the session's build file → launch. **Requires
>   the session to have installed via `APP_APK` / `IOS_APP`** (the `appium:app` cap) — attaching by
>   package only makes the step fail loud with that exact fix in the message.
>
> **These two are IN-scenario steps. Reset BETWEEN scenarios is a tag, not a step** — `@reset:` on the
> Feature (`none` · `soft` = default · `data` · `full`), which runs in the `beforeEach` hook. Reach for
> the tag when the whole suite needs a cleaner starting state, and for these steps when one scenario's
> ORACLE is "does this survive a restart / a reinstall". Writing a step to establish app state does not
> work on mobile: `Given the user is logged out` compiles to a comment (auth lives in the keychain, not
> in cookies) and reports **SG-W023** — declare `@reset:data` instead. Full tier table + trade-offs:
> `sungen-gherkin-syntax` → "Reset tiers".
> - **`turn airplane mode|wifi|mobile data on|off`** → `mobile: setConnectivity`. **Android-only** —
>   XCUITest cannot toggle connectivity; on iOS the step throws loud. Keep such scenarios
>   `@platform:android`. Do NOT author cleanup steps to restore connectivity "in case the test
>   dies": the generated `wdio.conf.ts` restores the baseline (airplane off, wifi/data/location on)
>   via adb in `onPrepare`/`onComplete`, healing even a run whose Appium session died mid-toggle (#615).
> - **`turn device location on|off`** → `mobile: isGpsEnabled` + `mobile: toggleGps` (the execute
>   aliases — WDIO v9 dropped the legacy wrappers), idempotent. **Android-only**, throws loud on iOS.
> - **`see [X] disappear within N seconds`** → reverse `waitForDisplayed` with the step's deadline
>   (+2s churn grace) — the auto-dismiss / expiry oracle (a 5s tooltip, a 60s barcode).
> - **`see [X] still visible after N seconds`** → wait out the window, then the platform-aware
>   visibility oracle — the "must NOT auto-dismiss / must stay valid" counterpart.
> - **`open deep link {{url}}`** → `mobile: deepLink` (VIEW intent / XCUITest deepLink with the
>   feature's app id) — THE entry for push-param / universal-link routing scenarios.
> - **`see device log contains {{v}}`** → logcat (Android) / syslog (iOS) poll-accumulate ≤10s —
>   makes SILENT failure modes and analytics events observable (they leave only a log line).
> - **`pass|fail biometric authentication`** (face wording → iOS faceId) → **emulator/Simulator
>   only**: Android `mobile: fingerprint` (finger 1 must be ENROLLED; fail = unenrolled id), iOS
>   `mobile: sendBiometricMatch`. Real devices reject the command — the step fails loud.
>
> 📜 **`scroll to [X]` — two failure modes seen, with the real cause (measured):**
> 1. *"Default scrollable element '//android.widget.ScrollView' not found"* — wdio's mobile scroll runs
>    `$$('//android.widget.ScrollView')` and throws if 0 match. This is **state-dependent**: that node
>    exists on a list/Home screen but NOT on every screen (tool/tab screens may lack it). If scroll runs
>    while the app is on a screen without one → this error. Not "the screen can't scroll".
> 2. **`.scrollIntoView()` is a NO-OP when the target is already inside the scroll viewport** — even if it's
>    visually **occluded by a floating bottom nav**. scrollIntoView only scrolls things that are *off-screen*;
>    it has no concept of z-order occlusion (verified: element bounds identical before/after, centre tap
>    still hit the nav). So scroll **cannot** fix an occluded-by-overlay element → use **`tap top of [X]`**
>    (visible-point tap), not scroll.
>
> `scroll to [X]` is still useful for genuinely **off-screen** items: it uses Android `UiScrollable`
> (accessibility-id targets) or a `mobile: scrollGesture` fallback.
>
> ⚠️ **Flutter note:** `.scrollIntoView()` looks for a native `android.widget.ScrollView`. Flutter
> (and some RN) scrollables don't expose one, and a screen whose content fits the viewport isn't
> scrollable at all — there `scroll to` fails or no-ops. For an element merely **occluded by a floating
> bottom nav** on a non-scrollable screen, neither scroll nor `element.click()` (taps the occluded
> centre) works; that needs a visible-point/coordinate tap (not yet supported) → tag such a scenario
> `@manual` for now.

---

## Gesture catalog

All map to the `appium_gesture` MCP tool (action + params). Element-relative gestures pass `elementUUID`
from `appium_find_element`; screen gestures pass `direction` or coordinates.

| Intent | Proposed Gherkin | `appium_gesture` |
|---|---|---|
| Tap | `User tap [Settings]` | `action=tap, elementUUID` |
| Double-tap | `User double-tap [Photo]` | `action=double_tap, elementUUID` |
| Long-press | `User long-press [Item] for 2 seconds` | `action=long_press, elementUUID, duration=2000` |
| Swipe (dismiss/switch/carousel) | `User swipe left on [Card]` | `action=swipe, elementUUID, direction=left` |
| Pull-to-refresh | `User pull-to-refresh on [Feed]` | `action=swipe, direction=down, speed=fast` |
| Scroll a list | `User scroll down on [Feed]` | `action=scroll, direction=down` |
| Scroll until visible | `User scroll to [Footer]` | `action=scroll_to_element, strategy, selector, direction` |
| Pinch zoom in/out | `User pinch-zoom in on [Map]` | `action=pinch_zoom, elementUUID, scale` (>1 in, <1 out) |
| System back | `User go back` | `action=back` — **compiles** (see codegen status; per-OS) |
| Drag & drop | `User drag [A] onto [B]` | `appium_drag_and_drop` (separate tool) |

Other device-level actions (separate MCP tools for exploration; Gherkin now compiles — see codegen
status above):
- Rotate: `appium_orientation` — `User rotate to landscape`
- Permission dialog: `appium_mobile_permissions` / `appium_alert` — `User grant [Location] permission`
- Background/foreground: `appium_app_lifecycle` — `User send app to background for 5 seconds`
- Clipboard write: `appium_mobile_clipboard` — `User set the clipboard to {{value}}` (read-back has
  no Gherkin pattern yet — see codegen status above)
- Notifications: open panel via `appium_mobile_device_control` — `User open notification panel`

---

## Authoring guidance

- **Prefer `scroll_to_element` over blind scrolling.** When a target may be off-screen, use
  `appium_gesture action=scroll_to_element` (same strategy+selector as the find) rather than repeated
  `appium_find_element` — it stops when the element appears or the page stops scrolling.
- **Direction semantics**: `swipe` = dismiss / switch screen / carousel / pull-to-refresh (use
  `speed=fast`); `scroll` = browse content in a list/feed. Choose by intent, not interchangeably.
- **Long-press duration**: default 2000ms; pass `duration` for context menus that need a longer hold.
- **Element vs screen**: pass `elementUUID` to gesture relative to an element; omit it (+ `direction`)
  to gesture on the whole screen.
- **Gestures in the navigation recipe**: a `Background:` step like `When User scroll to [X]` or
  `And User swipe up on [Feed]` is a valid nav hop — `sungen-capture-mobile` replays it to reach the
  target screen before scanning (see "Background = the navigation recipe" in `sungen-gherkin-syntax`).

---

## Selectors for gestures

Same rules as `sungen-selector-fix-mobile`: `accessibility-id` first, then `id`, platform-native, xpath.
For `scroll_to_element`, the `strategy`+`selector` you pass are the target you're scrolling toward — keep
them stable (accessibility-id) so the scroll terminates reliably.

---

## What this skill does NOT do

- Does not replace `sungen-gherkin-syntax` — it supplements it with the mobile-only step vocabulary.
- Does not cover tap/set-value/assertions (those are the shared Tier-1 patterns already supported).
- Does not yet support a clipboard-READ assertion via Gherkin (see "Write-only" note above) — the
  template exists but no pattern requests it.
