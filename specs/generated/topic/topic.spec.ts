import { $, $$, expect, driver, browser } from '@wdio/globals';
import { TestDataLoader } from '../test-data';

// This file is auto-generated from Gherkin feature files
// DO NOT EDIT MANUALLY - changes will be overwritten
// To modify tests, edit the corresponding .feature file and regenerate

// sungen:platform=android

const testData = TestDataLoader.load('topic', 'topic');

// Auto scroll-into-view before EVERY element access — the mobile analogue of Playwright's implicit
// scrollIntoView (Appium/WDIO has none). Fixes two Flutter/native failure modes at once:
//   (A) lazy-render / zero-rect: an item below the fold is NOT usable (absent from the Android a11y
//       tree; present-but-0x0 on iOS), so find/tap/assert fail even though scrolling would reveal it;
//   (B) floating bottom-nav occlusion (Android): a row near the fold is "displayed" but its tap point
//       sits behind the nav bar, so a coordinate tap misses.
// Already-visible elements return immediately (no scroll, no cost). Returns $(selector) even when
// never found so the caller's own step surfaces the real failure. The `selector` is re-queried each
// iteration (post-scroll elements go stale). The readiness gate and gesture differ per OS — each
// runtime lies differently (see the per-branch notes below).
//
// Android gates on isDisplayed() (reliable on UiAutomator2) + rect clearing the 0.82·H nav band,
// and scrolls with mobile: scrollGesture — NOT UiScrollable, which no-ops on Flutter (no native
// ScrollView).
//
// iOS gates on GEOMETRY, and scrolls with small drags. XCUITest reports a below-fold Flutter
// element as rect 0x0 + displayed:false while keeping it in the tree, and isDisplayed() also
// false-negatives on genuinely visible elements (snapshot artifact) — so visibility booleans are
// unusable as a scroll gate on iOS. An element is "usable" iff its rect is REAL (width/height > 0)
// with the top edge on screen; the width/height check is essential — a 0x0 rect has y=0, which a
// naive `y >= 0` gate mistakes for "at the top of the screen" (that exact bug shipped once: it
// skipped the scroll AND returned a stale 0x0 handle whose click went nowhere). Scrolling uses
// `mobile: scroll {direction}` — native XCTest paged scroll, the only primitive that worked in a
// live probe matrix on Flutter: `mobile: scroll {toVisible}` throws (WDA needs a scrollable
// ancestor with visible children, but Flutter exposes content as SIBLINGS of the ScrollView),
// `mobile: swipe` flings with momentum past the target, and dragFromToForDuration / W3C pointer
// drags corrupt the a11y tree (targets vanish from it entirely).
async function __ensureVisible(selector: string): Promise<WebdriverIO.Element> {
  let winH = 2000;
  try { const s = await driver.getWindowSize(); winH = s.height; } catch { /* keep default */ }
  if (driver.isIOS) {
    // Phase 1 — wait for the element to ENTER the tree WITHOUT gesturing. Right after the
    // relaunch-per-scenario beforeEach nothing is rendered yet, and dragging a still-launching
    // app makes XCUITest block on quiescence per gesture — enough to blow the mocha hook
    // timeout (seen live). A below-fold Flutter element is IN the tree from the start (just
    // 0x0), so in the genuine scroll case this wait returns instantly.
    try { await (await $(selector)).waitForExist({ timeout: 15000 }); } catch { /* virtualized far item or missing → still try scrolling below */ }
    for (let __i = 0; __i < 8; __i++) {
      let existed = false;
      try {
        const el = await $(selector);
        if (await el.isExisting()) {
          existed = true;
          const r = await driver.getElementRect(el.elementId);
          if (r.width > 0 && r.height > 0 && r.y >= 0 && r.y < winH) return el; // real rect, on screen → usable
        }
      } catch { /* not in the tree yet → keep scrolling */ }
      // Render grace — do NOT scroll on the first two passes when the element is in the tree but
      // still 0x0: right after a relaunch Flutter briefly reports even TOP-OF-SCREEN elements as
      // 0x0 while it renders, and a paged scroll fired in that window scrolls them clean off the
      // top, where a down-only loop can never recover them (seen live as a random ~2-cases-per-run
      // flake: "APP PREFERENCES rect 0x0"). A genuinely below-fold element just costs ~1s extra.
      if (existed && __i < 2) { await driver.pause(500); continue; }
      // `mobile: scroll {direction}` = native XCTest paged scroll — the ONLY primitive that moves
      // this Flutter list deterministically (probed live): `mobile: swipe` flings past the target
      // (momentum), and dragFromToForDuration / W3C pointer drags corrupt the a11y tree (targets
      // vanish entirely). The page step is ~0.9 viewport with built-in overlap, so a below-fold
      // element can never be jumped over — it lands near the top of the next page.
      await driver.execute('mobile: scroll', { direction: 'down' })
        .catch(() => driver.execute('mobile: swipe', { direction: 'up' }));
      await driver.pause(400); // settle: let Flutter render the newly-exposed band
    }
    return $(selector);
  }
  const safeBottom = Math.round(winH * 0.82); // above the floating bottom-nav band
  let lastY: number | null = null;
  let lastEl: WebdriverIO.Element | null = null;
  for (let __i = 0; __i < 12; __i++) {
    let y: number | null = null;
    let existsUndisplayed = false;
    try {
      const el = await $(selector);
      if (await el.isDisplayed()) {
        const r = await driver.getElementRect(el.elementId);
        // Done when the element sits fully above the floating bottom-nav band — OR is simply too
        // tall to ever fit there (a scroll container / full-screen list): demanding "fully above"
        // from such an element makes the loop scroll a page that was already correct, dragging
        // list-hosted siblings (e.g. the top bar as first list item) out of the viewport.
        if (r.y + r.height <= safeBottom || r.height >= safeBottom) return el;
        y = r.y;
        lastEl = el;
      } else if (el.elementId) {
        existsUndisplayed = true; // in the tree, just not visible right now
      }
    } catch { /* off-screen / not in tree yet → keep scrolling */ }
    // Overlay grace (Android mirror of the iOS render grace above): the element is IN the tree but
    // reports not-displayed — typically a launch popup/overlay covering it that auto-dismisses
    // within seconds. Scrolling now would carry a list-hosted element (a Compose top bar is often
    // the first list item) clean out of the viewport, where a down-only loop can never recover it —
    // so wait the overlay out before the first scroll.
    if (existsUndisplayed && __i < 4) { await driver.pause(1200); continue; }
    // No-progress break: the element is on screen but a prior scroll didn't move it — it's a FIXED
    // element (e.g. a bottom-nav tab, intentionally in the nav band) or we've hit the end of the
    // scrollable. Stop rather than burn the whole budget; the caller acts on it where it is.
    // Return the RESOLVED element, never a fresh $() stub: on an animation-churned tree (a looping
    // banner is enough) a from-scratch re-find takes seconds and outlasts the caller's assertion
    // budget — the element we just measured is the one the caller should act on.
    if (y !== null && lastY !== null && Math.abs(y - lastY) < 2) return lastEl ?? $(selector);
    lastY = y;
    await driver.execute('mobile: scrollGesture', { left: 60, top: 420, width: 960, height: 1320, direction: 'down', percent: 0.5 });
    await driver.pause(300);
  }
  return $(selector);
}

// Read a live element ARRAY after giving the tree a chance to render.
//
// `await $$(sel)` is a ONE-SHOT read — unlike a Playwright Locator it does not re-query, so an
// assertion that loops over the result silently asserts NOTHING when the list has not painted yet:
// zero matches, zero iterations, green. #672 fixed that class on the Playwright side and left the
// appium adapter untouched, so every collection assertion here still raced the first render.
//
// `mustExist` says which verdict emptiness is. A positive assertion ("all of them contain X",
// "every match is disabled") is meaningless over an empty set, so it waits the full budget and
// then fails naming the reference. A negated one ("none of them is displayed") is legitimately
// satisfied by absence, so it only waits long enough to outlast a render race and then proceeds
// with whatever it found — waiting the full budget there would add 15s to every correct `is
// hidden` step, which is a worse trade than the race it would close.
async function __settleElements(
  selector: string, mustExist: boolean, ref: string,
): Promise<WebdriverIO.Element[]> {
  try {
    await (await $(selector)).waitForExist({ timeout: mustExist ? 15000 : 3000 });
  } catch {
    if (mustExist) {
      throw new Error(`[sungen] Expected at least one ${ref ? `[${ref}]` : `element matching ${selector}`} — none appeared within 15s`);
    }
  }
  return $$(selector);
}

// Visibility oracle for simple-case `see [X]` assertions. Android: WDIO's toBeDisplayed()
// (reliable on UiAutomator2). iOS: XCUITest's `visible` attribute false-negatives on real,
// on-screen elements (snapshot artifact — seen live even on a screen title every prior run
// passed), so assert GEOMETRY instead: after __ensureVisible has scrolled the target toward
// view, the element must exist with a real rect (w,h > 0) whose top edge is on screen. On
// Flutter an off-screen element keeps a 0x0 rect, so this is exactly the "user can see it"
// boundary. Re-queries fresh + retries briefly to absorb render lag.
async function __assertVisible(selector: string): Promise<void> {
  const el = await __ensureVisible(selector);
  if (!driver.isIOS) { await expect(el).toBeDisplayed(); return; }
  let winH = 2000;
  try { const s = await driver.getWindowSize(); winH = s.height; } catch { /* keep default */ }
  let r = { x: 0, y: 0, width: 0, height: 0 };
  for (let __i = 0; __i < 3; __i++) {
    try {
      const e2 = await $(selector);
      if (await e2.isExisting()) r = await driver.getElementRect(e2.elementId);
    } catch { /* not in the tree */ }
    if (r.width > 0 && r.height > 0 && r.y >= 0 && r.y < winH) return;
    await driver.pause(500);
  }
  throw new Error(`Expect ${JSON.stringify(selector)} to be displayed — iOS geometry oracle: rect=${JSON.stringify(r)} is not on screen (0x0 = not rendered / scrolled out)`);
}

// Checkbox / toggle / radio state oracle. There is NO single authoritative source for it on
// mobile, and the standard one lies in a way that cannot be detected from the read itself:
// UiAutomator2 puts `checked` on EVERY Android node and defaults it to "false" when the widget
// publishes no checked semantics. So a custom-rendered control — a Flutter widget without
// `Semantics(checked: …)`, an RN `Pressable` without `accessibilityState={ checked: … }` — is not
// ABSENT from the tree, it reports a confident false while sitting visibly ON, and a strict
// `toHaveAttribute('checked','true')` fails with no way to see why.
// Hence: probe every signal both runtimes expose, and let the DIRECTION of the assertion decide
// how to combine them (__assertCheckedState below).
//   Android: `checked`, `selected`, isSelected()   iOS: `value` ("1"/"0"), `selected`, isSelected()
// Descendants are probed too, bounded: on a composite row (label + control merged into one a11y
// node) the state routinely sits on an inner node while the queried outer node reports the default.
// A child xpath must start with `.//` — a leading `//` is ABSOLUTE in Appium even when chained.
async function __probeCheckedSignals(el: WebdriverIO.Element): Promise<{ source: string; value: boolean }[]> {
  const out: { source: string; value: boolean }[] = [];
  const read = async (node: WebdriverIO.Element, prefix: string): Promise<void> => {
    for (const attr of driver.isIOS ? ['value', 'selected'] : ['checked', 'selected']) {
      let raw: string | null = null;
      try { raw = await node.getAttribute(attr); } catch { continue; } // attribute unsupported on this node
      const v = String(raw ?? '').trim().toLowerCase();
      if (v === 'true' || v === '1') out.push({ source: `${prefix}${attr}="${raw}"`, value: true });
      else if (v === 'false' || v === '0') out.push({ source: `${prefix}${attr}="${raw}"`, value: false });
      // anything else (empty, a real iOS `value` string like "Off") carries no parseable state → skip
    }
    try { out.push({ source: `${prefix}isSelected()`, value: await node.isSelected() }); } catch { /* not supported here */ }
  };
  await read(el, '');
  // Only descend when the node itself yielded nothing positive — otherwise we already have the
  // answer and a subtree walk would turn one assertion into dozens of round-trips for nothing.
  if (!out.some(s => s.value)) {
    let kids: WebdriverIO.Element[] = [];
    try { kids = await el.$$('.//*'); } catch { /* no child query on this node */ }
    for (const k of kids.slice(0, 8)) await read(k, 'descendant.');
  }
  return out;
}

// Assert checked/unchecked from the probed signals. The two directions are deliberately NOT
// symmetric, because the evidence isn't:
//   checked   → ANY credible positive signal passes. One runtime exposing the state is enough;
//               demanding the standard attribute is what breaks custom-rendered controls.
//   unchecked → NO signal anywhere may be positive. Strictly stronger than reading one attribute,
//               so a visibly-ON custom toggle can't slip through as a false PASS.
// Polls like WDIO's own matchers do — a tap-then-assert reads the tree mid-animation.
// KNOWN LIMIT, stated rather than hidden: on Android a UiAutomator2-defaulted `checked="false"` is
// indistinguishable from a genuine false — that information is not in the tree. So `unchecked`
// passes on "no positive signal anywhere", which is the strongest available evidence, not proof.
async function __assertCheckedState(el: WebdriverIO.Element, expected: boolean, label: string): Promise<void> {
  // Fail loud with the REAL cause when the element never resolved (mirrors check-action, #430).
  if (!el.elementId) throw new Error((el as any).error?.message || `Element not found: ${el.selector}`);
  let signals: { source: string; value: boolean }[] = [];
  for (let __i = 0; __i < 10; __i++) {
    signals = await __probeCheckedSignals(el);
    if (signals.some(s => s.value) === expected) return;
    await driver.pause(500);
  }
  const want = expected ? 'checked' : 'unchecked';
  if (signals.length === 0) {
    throw new Error(
      `Expect ${label} to be ${want} — the control publishes NO checked/selected state, on itself or any child. ` +
      `A custom-rendered control has to publish it for any tool to read it: Flutter → wrap it in Semantics(checked: …); ` +
      `React Native → accessibilityState={ checked: … }. Until then assert a visible consequence instead ` +
      `(a label/text change), or tag the scenario @manual.`,
    );
  }
  throw new Error(`Expect ${label} to be ${want} — read [${signals.map(s => `${s.source} → ${s.value}`).join(', ')}]`);
}

// Entering a "frame" on a native driver means switching into the WebView CONTEXT — there is no
// iframe to locate. That context does not exist until the WebView runtime has created it, and the
// tap that kicks off the navigation returns long before that, so a ONE-SHOT getContexts() reads a
// list that holds only NATIVE_APP. Skipping the switch on that reading is the worst possible
// answer: every later step then searches the native tree, where a hybrid WebView is a SINGLE
// opaque leaf with no children — the target can never appear there, so no downstream wait can
// recover it and the run dies with a misleading "element still not displayed" (#705). Poll for the
// context instead, and throw when it never arrives: a step that reads "switch to X frame" must
// never quietly not switch.
async function __enterWebContext(label: string): Promise<void> {
  const budget = Number((driver as any)?.options?.waitforTimeout) || 30000;
  const deadline = Date.now() + budget;
  let seen: any[] = [];
  for (;;) {
    seen = await driver.getContexts();
    const web = seen.find(c => /WEBVIEW|CHROMIUM/i.test(typeof c === 'string' ? c : c.id));
    if (web) { await driver.switchContext(typeof web === 'string' ? web : web.id); return; }
    if (Date.now() >= deadline) break;
    await driver.pause(500);
  }
  throw new Error(
    `[sungen] "switch to ${label} frame" — no WEBVIEW/CHROMIUM context appeared within ${budget}ms ` +
    `(contexts seen: ${JSON.stringify(seen)}). The driver is still in NATIVE_APP, where a hybrid WebView ` +
    `is one opaque node with no children, so every later step would fail as "element not displayed" ` +
    `while the real cause is this switch. If the transition is genuinely slower than ${budget}ms, raise ` +
    `waitforTimeout in wdio.conf.ts; if the screen has no WebView, drop the frame step and assert the ` +
    `native markers instead.`,
  );
}

/**
 * Feature: Topic lesson
 *   Màn chủ đề (Meeting new people) và 4 bước học: cụm từ, hội thoại, luyện nói, game; luyện nhại
  Path: com.speakeasy.app/.MainActivity
 */
describe('Topic lesson', () => {
  // Multi-app: this feature's app (from the `Path:` line). Reset to its launch screen before each
  // scenario so one `wdio run` can host screens from different apps — each spec launches its own.
  // The `Path:` holds the ANDROID package (or, for an @platform:ios feature, the iOS bundle id).
  // EMPTY when the feature declared no usable `Path:` (#669) — the session capability then supplies
  // the id (see __resolveAppId). Emitted unconditionally so that a feature without a `Path:` still
  // gets per-scenario reset + Background instead of silently dropping to a once-per-file before().
  const __APP_PKG_DEFAULT__ = 'com.speakeasy.app';
  // Platform-aware app id: an @platform:mobile spec runs on BOTH OSes. On iOS prefer the feature's own
  // bundle id (dual-id Path), then the session cap (IOS_BUNDLE_ID in wdio.conf), then the Path value;
  // on Android (or any non-iOS session) prefer the Path package, then the session cap.
  // The session-capability fallback (#669) is what lets a feature with no app-package `Path:` keep a
  // real per-scenario reset: wdio.conf sets `appium:appPackage` for the run, so the id the compiler
  // could not read off the feature file is almost always present here. Throwing when NOTHING supplies
  // it is deliberate — the alternative was the silent once-per-file before() this issue is about,
  // where scenarios leak tab/scroll/login state into each other and the suite lies about why it
  // failed. SG-W029 already named this at compile time, so this throw is never the first warning.
  function __resolveAppId(): string {
    const caps = (typeof driver !== 'undefined' ? (driver.requestedCapabilities || {}) : {}) as Record<string, string>;
    const resolved = (typeof driver !== 'undefined' && driver.isIOS)
      ? (caps['appium:bundleId'] || caps['bundleId'] || __APP_PKG_DEFAULT__ || caps['appium:appPackage'] || caps['appPackage'])
      : (__APP_PKG_DEFAULT__ || caps['appium:appPackage'] || caps['appPackage'] || caps['appium:bundleId'] || caps['bundleId']);
    if (!resolved) {
      throw new Error(
        `[sungen] cannot resolve this app's id, so no per-scenario reset is possible (SG-W029). ` +
        `Add an app-package \`Path:\` to the feature — \`Path: com.example.app/.MainActivity\` — ` +
        `or set APP_PACKAGE (Android) / IOS_BUNDLE_ID (iOS) so wdio.conf puts it on the session.`,
      );
    }
    return resolved;
  }
  // ── Reset tier ────────────────────────────────────────────────────────────────────────────────
  // How much state each scenario starts from (`@reset:` on the Feature; default `soft`):
  //   none — nothing reset; state carries over BY DESIGN (a cart accumulating across scenarios)
  //   soft — terminate + activate: in-memory state and the nav stack
  //   data — soft + wipe the app's data layer (local DB, cache, prefs, keychain)
  //   full — remove + reinstall: also permissions and install-time state
  // Unlike web, no cheap "reload the URL" exists here, and a cold start is 5–30s — hence a ladder
  // rather than one all-or-nothing switch.
  const __DECLARED_TIER = 'data';
  const __TIER_ORDER = ['none', 'soft', 'data', 'full'];
  // SUNGEN_RESET_TIER RAISES the floor for a whole run — CI at `data`, local dev at `soft` — without
  // regenerating a single spec (same runtime-resolution model as the test-data overlays). It never
  // LOWERS a declared tier: a feature that asked for `full` needs it. Resolved once, so an
  // unrecognized value warns once rather than per scenario — a silently ignored typo here would
  // reintroduce exactly the false-confidence this tier system exists to remove.
  const __RUN_TIER: string = (() => {
    // Defensive only — the compiler validates the tag — but fall back to `soft`, NOT to index 0
    // (`none`). A safety net that silently drops to "reset nothing" points the wrong way.
    const declaredIdx = __TIER_ORDER.indexOf(__DECLARED_TIER);
    const declared = declaredIdx < 0 ? __TIER_ORDER.indexOf('soft') : declaredIdx;
    const raw = (process.env.SUNGEN_RESET_TIER || '').trim().toLowerCase();
    if (!raw) return __TIER_ORDER[declared];
    const floor = __TIER_ORDER.indexOf(raw);
    if (floor < 0) {
      console.warn(`  ! SUNGEN_RESET_TIER='${raw}' is not a tier (${__TIER_ORDER.join(', ')}) — ignored`);
      return __TIER_ORDER[declared];
    }
    return __TIER_ORDER[Math.max(declared, floor)];
  })();

  // A scenario that FAILED left unknown state behind — a half-written local DB, an open modal — that a
  // plain process relaunch does not undo. So the NEXT scenario resets one rung harder than it asked
  // for: this is the "A fails mid-flow, B fails for A's reasons" case that no amount of relaunching
  // fixes. One rung, one scenario, never sticky — otherwise a single flake would silently promote the
  // whole run to `full` and put 30s on every remaining scenario.
  // `none` is a FIXED POINT by design: it is an explicit state-carryover contract (a cart built up
  // across scenarios), and escalating would silently break the very thing it was asked for. The chain
  // is dead once one link fails anyway, so a relaunch would change only how the rest fail.
  // Mirrors escalateResetTier() in the compiler; a test asserts the two ladders agree.
  const __ESCALATE: Record<string, string> = { none: 'none', soft: 'data', data: 'full', full: 'full' };
  // Holds the TITLE of the scenario that failed, not a boolean — because escalation must reach the next
  // DIFFERENT scenario and must NOT reach a RETRY of the failed one.
  //
  // `mochaOpts.retries` re-runs a failed scenario, and the retry is also "the next run". Escalating it
  // would make attempt 2 reset harder than attempt 1, which breaks the guarantee this config is built
  // on: race victims recover on attempt 2 while REAL failures fail both attempts. A dirty-state failure
  // is a real failure — if the retry silently got a wiped data layer it would pass, the reporter takes
  // the last attempt, and a scenario that only works after a wipe would be delivered as a clean
  // `Passed` under a feature file still reading its weaker declared tier. That false pass is worse than
  // the flakiness escalation was added to fix, so the retry keeps attempt 1's tier and fails honestly.
  //
  // Comparing titles rather than reading mocha's retry counter keeps this independent of the runner's
  // API, and it fails in the SAFE direction: if a title is unavailable both sides compare equal, so no
  // escalation happens and the invariant above still holds. (Two scenarios sharing one title in a file
  // lose the escalation between them — SG-W024 already reports that shape when tiers are involved.)
  let __escalateAfter: string | null = null;

  // `@known-defect` marks a scenario that is EXPECTED to be red — it asserts a live defect. The
  // scaffolded /sungen:run-test command tells authors exactly that ("expected-red scenarios stay red;
  // never 'fix' them"), so the ladder honours it here: such a failure is the suite working as
  // designed, not evidence of leftover state. Arming escalation off it would make the NEXT scenario
  // pay a harder reset for nothing — on every run, forever, because the defect is known.
  //
  // Read off the TITLE because `@known-defect` is a pass-through tag: appium appends it there as the
  // `[@a @b]` suffix (which is what keeps it greppable via `--grep`), and that is how the field
  // report shows it. Anchored to that trailing group, so a scenario merely NAMED `… known-defect …`
  // cannot exempt itself — the same reason __tierFor refuses to strip a trailing `[Selector]` ref.
  //
  // Matched as a whole TAG TOKEN, never as a substring. `@known-defect-list` is a DIFFERENT tag, and
  // reading it as this one would silently stop the next scenario's real dirty-state failure from
  // getting the escalated reset it needs — with no log line at all, because "never armed" is not the
  // "capped" path and prints nothing. The `:<ref>` parameter form is accepted because that is what
  // the rest of the vocabulary does (`@manual:M10`, `@reset:data`).
  //
  // Caveat, inherited rather than introduced: a @cases scenario builds its title at runtime as
  // `name — <row label>`, so a row LABEL that itself ends in `[@…]` would be read as the tag group.
  // Same trailing-bracket ambiguity __tierFor accepts for a `[Selector]` ref.
  function __isExpectedFail(title: string): boolean {
    const tagGroup = title.match(/ \[@[^\]]*\]$/);
    if (!tagGroup) return false;
    return tagGroup[0].slice(2, -1).split(/\s+/)
      .some((t) => t === '@known-defect' || t.startsWith('@known-defect:'));
  }

  // Registered BEFORE any user @afterEach block so a throwing user hook cannot skip the bookkeeping.
  afterEach(async function () {
    __escalateAfter = this.currentTest?.state === 'failed' && !__isExpectedFail(this.currentTest?.title ?? '') ? (this.currentTest?.title ?? '') : null;
  });

  // Settle after a cold (re)launch: a Flutter app first renders a BLANK accessibility skeleton
  // (nested FrameLayout/View, no content-desc) for several seconds before the real tree appears.
  // So require rendered content — at least one non-empty content-desc/text — on two consecutive
  // reads before proceeding. A generous timeout covers slow cold renders on a stressed emulator.
  // Best-effort: a timeout never fails the scenario, it just falls through to the step's own
  // auto-wait.
  // "Settled" = rendered content present on TWO consecutive reads. Deliberately NOT full source
  // equality: a UI with any looping animation (a rotating promo carousel is enough) never yields
  // two identical snapshots, so an equality oracle starves and burns the whole timeout on every
  // scenario. Two content-bearing reads still reject the blank first-frame skeleton (no
  // content-desc/text at all) that a single read could false-positive on.
  async function __settleUi(timeout: number): Promise<void> {
    let __prevHadContent = false;
    await driver
      .waitUntil(
        async () => {
          const src = await driver.getPageSource();
          const hasContent = /content-desc="[^"]+"|\btext="[^"]+"/.test(src);
          const settled = hasContent && __prevHadContent;
          __prevHadContent = hasContent;
          return settled;
        },
        { timeout, interval: 400, timeoutMsg: 'UI did not settle (no rendered content)' },
      )
      .catch(() => { /* best-effort — fall through to per-step auto-wait */ });
  }

  // Wipe the app's data layer. `mobile: clearApp` is a UiAutomator2 (Android) command — it maps to
  // `pm clear`, which drops the local DB, cache, SharedPreferences and keychain entries in one call
  // for ~2–5s. XCUITest has NO equivalent, so iOS degrades to a reinstall: slower, but the only way
  // to reach the same layers. That needs a build to install from, hence the named throw.
  async function __clearAppData(pkg: string): Promise<void> {
    if (driver.isIOS) {
      await __reinstallApp(pkg);
      return;
    }
    await driver.execute('mobile: clearApp', { appId: pkg });
  }

  /** The app's build file, when the session was given one. Absent when the app was attached BY PACKAGE. */
  function __appBuildPath(): string | undefined {
    const caps = (driver.requestedCapabilities || {}) as Record<string, string>;
    return caps['appium:app'] || caps['app'];
  }

  /**
   * The strongest tier THIS SESSION can actually carry out.
   *
   * Reinstalling needs the app's build file, and `data` on iOS IS a reinstall (no `mobile: clearApp`
   * outside UiAutomator2). Attaching the app by package — the common local setup — leaves no build
   * file, so those tiers cannot run at all.
   *
   * This exists so ESCALATION can cap itself. Escalation is a recovery the runner CHOSE; nothing
   * asked for it. Escalating into a tier that throws turns one failed scenario into a dead spec file,
   * because the throw lands in `beforeEach` and mocha then skips every remaining test in the file.
   * A DECLARED tier is never capped — that is an unmet contract and must still throw by name.
   */
  function __performableTier(): string {
    if (__appBuildPath()) return 'full';
    return driver.isIOS ? 'soft' : 'data';
  }

  async function __reinstallApp(pkg: string): Promise<void> {
    const appPath = __appBuildPath();
    if (!appPath) {
      throw new Error(
        `reset tier needs the app's build file to reinstall — set APP_APK (Android) / IOS_APP (iOS) so the appium:app capability is present, or drop to @reset:soft.`,
      );
    }
    try { await driver.removeApp(pkg); } catch { /* not installed */ }
    await driver.installApp(appPath);
  }

  // One reset, at the given tier. `none` still restores the NATIVE context and portrait: both leak at
  // OS/session level rather than app level, so skipping them alongside the relaunch would make `none`
  // outright buggy instead of merely stateful. It skips the settle wait, though — nothing relaunched,
  // so there is no blank first frame to wait through, and burning up to 30s per scenario would defeat
  // the whole point of asking for `none`.
  async function __resetApp(tier: string): Promise<void> {
    const __APP_PKG__ = __resolveAppId();
    if (tier !== 'none') {
      if (tier === 'full') {
        try { await driver.terminateApp(__APP_PKG__); } catch { /* app may not be running yet */ }
        await __reinstallApp(__APP_PKG__);
      } else {
        try { await driver.terminateApp(__APP_PKG__); } catch { /* app may not be running yet */ }
        if (tier === 'data') await __clearAppData(__APP_PKG__);
      }
      // Let the termination fully land before relaunching — activateApp racing a still-dying
      // process can RESUME the old instance (prior scenario's scroll position and all) instead
      // of cold-starting, which strands top-of-screen elements above the viewport.
      await driver.pause(500);
      await driver.activateApp(__APP_PKG__);
    }
    // Restore the NATIVE context — a prior scenario that entered a WebView (`switch to [X] frame`)
    // without switching back would otherwise poison every following native find.
    try { await driver.switchContext('NATIVE_APP'); } catch { /* single-context session */ }
    // Restore portrait so a prior scenario's `rotate to landscape` can't leak into this one — the
    // app relaunch above resets in-app state, but device orientation is OS-level and persists.
    try { await driver.setOrientation('PORTRAIT'); } catch { /* orientation control not supported */ }
    // A fresh install paints its first frame slower than a plain relaunch — same oracle, more rope.
    if (tier !== 'none') await __settleUi(tier === 'full' || tier === 'data' ? 45000 : 30000);
  }
  beforeEach(async function () {
    const __base = __RUN_TIER;
    // Same title ⟹ this is a retry of the scenario that just failed: keep its declared tier so a real
    // failure fails both attempts (see __escalateAfter above).
    const __escalate = __escalateAfter !== null && __escalateAfter !== (this.currentTest?.title ?? '');
    // Escalation caps at what this session can perform, with the DECLARED tier as the FLOOR.
    // The cap stops a tier NOBODY ASKED FOR from throwing in this hook and taking the rest of the
    // spec file with it. The floor stops the cap from silently WEAKENING what the feature declared:
    // a declared tier this session cannot perform still reaches __resetApp and still throws by name,
    // because that one IS an unmet contract.
    const __want = __escalate ? __ESCALATE[__base] : __base;
    const __tier = __escalate
      ? __TIER_ORDER[Math.max(
          __TIER_ORDER.indexOf(__base),
          Math.min(__TIER_ORDER.indexOf(__want), __TIER_ORDER.indexOf(__performableTier())),
        )]
      : __want;
    if (__tier !== __base) {
      console.log(`  \u2191 reset escalated ${__base} \u2192 ${__tier} (previous scenario failed: "${__escalateAfter}")`);
    } else if (__want !== __base) {
      // Said per occurrence, not once per run: this names the scenario that did NOT get the stronger
      // reset it was owed, which is a fact about THIS scenario, not just about the session.
      console.warn(`  ! reset could not escalate ${__base} \u2192 ${__want} — this session has no app build file to reinstall from (the app was attached by package). Staying at ${__base}; set APP_APK (Android) / IOS_APP (iOS) to enable the stronger reset.`);
    }
    await __resetApp(__tier);
    // Background runs here (inside beforeEach) — the app resets to its launch screen every scenario,
    // so Background setup (nav, dismiss launch promos…) must re-run after each relaunch, not once.
    // Navigate to Home
    // App launched via wdio capabilities — entry: com.speakeasy.app/.MainActivity
    
    // Tap Later Button
    await (await __ensureVisible('android=new UiSelector().resourceId("btn_name_skip")')).click();
    
    // Tap Continue Learning
    await (await __ensureVisible('android=new UiSelector().resourceId("btn_continue_learning")')).click();
    
  });

  it('VP-UI-001 Topic detail screen shows the title, counts and learning steps [@high @spec:FR-TP-01]', async () => {
    // Assert Topic Screen is visible
    await __assertVisible('android=new UiSelector().resourceId("screen_topic_detail")');
    // Assert Topic Title contains "topic_title"
    {
      const __el = await (await __ensureVisible('android=new UiSelector().text("Meeting new people")'));
      await __el.waitForDisplayed();
      const __expected = String(testData.get('topic_title')).trim();
      const __sources = driver.isIOS
        ? [await __el.getAttribute('label'), await __el.getAttribute('value')]
        : [await __el.getText(), await __el.getAttribute('content-desc')];
      const __actual = String(__sources.find(s => s && String(s).trim() !== '') ?? '').trim();
      if (!__actual.includes(__expected)) {
        throw new Error(`[sungen] Expected text containing "${__expected}" but element reads "${__actual}"`);
      }
    }
    
    // Assert Topic Counts contains "topic_counts"
    {
      const __el = await (await __ensureVisible('android=new UiSelector().textContains("mẫu câu ·")'));
      await __el.waitForDisplayed();
      const __expected = String(testData.get('topic_counts')).trim();
      const __sources = driver.isIOS
        ? [await __el.getAttribute('label'), await __el.getAttribute('value')]
        : [await __el.getText(), await __el.getAttribute('content-desc')];
      const __actual = String(__sources.find(s => s && String(s).trim() !== '') ?? '').trim();
      if (!__actual.includes(__expected)) {
        throw new Error(`[sungen] Expected text containing "${__expected}" but element reads "${__actual}"`);
      }
    }
    
    // Assert Step Phrases is visible
    await __assertVisible('android=new UiSelector().text("Cụm từ & từ vựng")');
    // Assert Step Dialogue is visible
    await __assertVisible('android=new UiSelector().text("Hội thoại nhập vai")');
    // Assert Step Speak is visible
    await __assertVisible('android=new UiSelector().text("Luyện nói")');
    // Assert Step Game is visible
    await __assertVisible('android=new UiSelector().text("Chơi mà học")');
  });

  it('VP-NAV-001 The phrases step opens with the topic\'s first phrase [@high @spec:FR-TP-02]', async () => {
    // Tap Step Phrases
    await (await __ensureVisible('android=new UiSelector().text("Cụm từ & từ vựng")')).click();
    
    // Assert Phrases Screen is visible
    await __assertVisible('android=new UiSelector().resourceId("screen_topic_phrases")');
    // Assert First Phrase contains "first_phrase"
    {
      const __el = await (await __ensureVisible('android=new UiSelector().textContains("I don\'t think we\'ve met")'));
      await __el.waitForDisplayed();
      const __expected = String(testData.get('first_phrase')).trim();
      const __sources = driver.isIOS
        ? [await __el.getAttribute('label'), await __el.getAttribute('value')]
        : [await __el.getText(), await __el.getAttribute('content-desc')];
      const __actual = String(__sources.find(s => s && String(s).trim() !== '') ?? '').trim();
      if (!__actual.includes(__expected)) {
        throw new Error(`[sungen] Expected text containing "${__expected}" but element reads "${__actual}"`);
      }
    }
    
    // Assert Speak Button is visible
    await __assertVisible('android=new UiSelector().resourceId("btn_speak")');
  });

  it('VP-NAV-002 The dialogue step opens with Alex\'s first line [@high @spec:FR-TP-03]', async () => {
    // Tap Step Dialogue
    await (await __ensureVisible('android=new UiSelector().text("Hội thoại nhập vai")')).click();
    
    // Assert Dialogue Screen is visible
    await __assertVisible('android=new UiSelector().resourceId("screen_topic_dialogue")');
    // Assert Alex Line contains "alex_line"
    {
      const __el = await (await __ensureVisible('android=new UiSelector().textContains("I\'m Alex")'));
      await __el.waitForDisplayed();
      const __expected = String(testData.get('alex_line')).trim();
      const __sources = driver.isIOS
        ? [await __el.getAttribute('label'), await __el.getAttribute('value')]
        : [await __el.getText(), await __el.getAttribute('content-desc')];
      const __actual = String(__sources.find(s => s && String(s).trim() !== '') ?? '').trim();
      if (!__actual.includes(__expected)) {
        throw new Error(`[sungen] Expected text containing "${__expected}" but element reads "${__actual}"`);
      }
    }
    
  });

  it('VP-NAV-003 The speaking step opens practice with the microphone [@high @spec:FR-TP-04]', async () => {
    // Tap Step Speak
    await (await __ensureVisible('android=new UiSelector().text("Luyện nói")')).click();
    
    // Assert Speak Screen is visible
    await __assertVisible('android=new UiSelector().resourceId("screen_topic_speak")');
    // Assert Mic Button is visible
    await __assertVisible('android=new UiSelector().resourceId("btn_mic")');
  });

  it('VP-NAV-004 The game step opens the topic game at its first round [@high @spec:FR-TP-05]', async () => {
    // Tap Step Game
    await (await __ensureVisible('android=new UiSelector().text("Chơi mà học")')).click();
    
    // Assert Game Screen is visible
    await __assertVisible('android=new UiSelector().resourceId("screen_game_detail")');
    // Assert Round Counter contains "first_round"
    {
      const __el = await (await __ensureVisible('android=new UiSelector().textContains("Vòng ")'));
      await __el.waitForDisplayed();
      const __expected = String(testData.get('first_round')).trim();
      const __sources = driver.isIOS
        ? [await __el.getAttribute('label'), await __el.getAttribute('value')]
        : [await __el.getText(), await __el.getAttribute('content-desc')];
      const __actual = String(__sources.find(s => s && String(s).trim() !== '') ?? '').trim();
      if (!__actual.includes(__expected)) {
        throw new Error(`[sungen] Expected text containing "${__expected}" but element reads "${__actual}"`);
      }
    }
    
  });

  it('VP-NAV-005 Shadowing card opens shadowing practice [@normal @spec:FR-TP-06]', async () => {
    // Tap Shadowing Card
    await (await __ensureVisible('android=new UiSelector().textContains("Luyện nhại")')).click();
    
    // Assert Shadow Screen is visible
    await __assertVisible('android=new UiSelector().resourceId("screen_topic_shadow")');
  });

  it('VP-LOGIC-001 Finishing the vocabulary cards completes the phrases step and adds 10 XP [@high @spec:FR-TP-07]', async () => {
    // Tap Step Phrases
    await (await __ensureVisible('android=new UiSelector().text("Cụm từ & từ vựng")')).click();
    
    // Tap Vocab Cards Tab
    await (await __ensureVisible('android=new UiSelector().clickable(true).textContains("Từ vựng")')).click();
    
    // Tap Next Card Button
    await (await __ensureVisible('android=new UiSelector().text("Tiếp →")')).click();
    
    // Tap Next Card Button
    await (await __ensureVisible('android=new UiSelector().text("Tiếp →")')).click();
    
    // Tap Next Card Button
    await (await __ensureVisible('android=new UiSelector().text("Tiếp →")')).click();
    
    // Tap Next Card Button
    await (await __ensureVisible('android=new UiSelector().text("Tiếp →")')).click();
    
    // Tap Next Card Button
    await (await __ensureVisible('android=new UiSelector().text("Tiếp →")')).click();
    
    // Tap Finish Button
    await (await __ensureVisible('android=new UiSelector().text("Hoàn thành")')).click();
    
    // Assert Complete Dialog is visible
    await __assertVisible('android=new UiSelector().resourceId("dialog_complete")');
    // Assert Complete Title contains "complete_title"
    {
      const __el = await (await __ensureVisible('android=new UiSelector().textContains("Hoàn thành bài học")'));
      await __el.waitForDisplayed();
      const __expected = String(testData.get('complete_title')).trim();
      const __sources = driver.isIOS
        ? [await __el.getAttribute('label'), await __el.getAttribute('value')]
        : [await __el.getText(), await __el.getAttribute('content-desc')];
      const __actual = String(__sources.find(s => s && String(s).trim() !== '') ?? '').trim();
      if (!__actual.includes(__expected)) {
        throw new Error(`[sungen] Expected text containing "${__expected}" but element reads "${__actual}"`);
      }
    }
    
    // Tap Dialog Continue Button
    await (await __ensureVisible('android=new UiSelector().resourceId("btn_dialog_continue")')).click();
    
    // Restart the app (kill → relaunch → settle)
    // Restart the app: kill → relaunch → wait for rendered content (same settle oracle as the
    // per-scenario relaunch hook — a cold start first paints a contentless skeleton).
    await (async () => {
      const __pkg = __resolveAppId();
      try { await driver.terminateApp(__pkg); } catch { /* not running */ }
      await driver.pause(500);
      await driver.activateApp(__pkg);
      let __prevHad = false;
      await driver
        .waitUntil(async () => {
          const src = await driver.getPageSource();
          const has = /content-desc="[^"]+"|\btext="[^"]+"/.test(src);
          const ok = has && __prevHad; __prevHad = has; return ok;
        }, { timeout: 30000, interval: 400 })
        .catch(() => { /* best-effort — the next step has its own wait */ });
    })();
    
    // Tap Later Button
    await (await __ensureVisible('android=new UiSelector().resourceId("btn_name_skip")')).click();
    
    // Assert Daily Goal contains "goal_after_phrases"
    {
      const __el = await (await __ensureVisible('android=new UiSelector().resourceId("text_daily_goal")'));
      await __el.waitForDisplayed();
      const __expected = String(testData.get('goal_after_phrases')).trim();
      const __sources = driver.isIOS
        ? [await __el.getAttribute('label'), await __el.getAttribute('value')]
        : [await __el.getText(), await __el.getAttribute('content-desc')];
      const __actual = String(__sources.find(s => s && String(s).trim() !== '') ?? '').trim();
      if (!__actual.includes(__expected)) {
        throw new Error(`[sungen] Expected text containing "${__expected}" but element reads "${__actual}"`);
      }
    }
    
  });

  it('VP-LOGIC-002 Completing the phrases step fills the review deck [@high @spec:FR-TP-08]', async () => {
    // Tap Step Phrases
    await (await __ensureVisible('android=new UiSelector().text("Cụm từ & từ vựng")')).click();
    
    // Tap Vocab Cards Tab
    await (await __ensureVisible('android=new UiSelector().clickable(true).textContains("Từ vựng")')).click();
    
    // Tap Next Card Button
    await (await __ensureVisible('android=new UiSelector().text("Tiếp →")')).click();
    
    // Tap Next Card Button
    await (await __ensureVisible('android=new UiSelector().text("Tiếp →")')).click();
    
    // Tap Next Card Button
    await (await __ensureVisible('android=new UiSelector().text("Tiếp →")')).click();
    
    // Tap Next Card Button
    await (await __ensureVisible('android=new UiSelector().text("Tiếp →")')).click();
    
    // Tap Next Card Button
    await (await __ensureVisible('android=new UiSelector().text("Tiếp →")')).click();
    
    // Tap Finish Button
    await (await __ensureVisible('android=new UiSelector().text("Hoàn thành")')).click();
    
    // Tap Dialog Continue Button
    await (await __ensureVisible('android=new UiSelector().resourceId("btn_dialog_continue")')).click();
    
    // Restart the app (kill → relaunch → settle)
    // Restart the app: kill → relaunch → wait for rendered content (same settle oracle as the
    // per-scenario relaunch hook — a cold start first paints a contentless skeleton).
    await (async () => {
      const __pkg = __resolveAppId();
      try { await driver.terminateApp(__pkg); } catch { /* not running */ }
      await driver.pause(500);
      await driver.activateApp(__pkg);
      let __prevHad = false;
      await driver
        .waitUntil(async () => {
          const src = await driver.getPageSource();
          const has = /content-desc="[^"]+"|\btext="[^"]+"/.test(src);
          const ok = has && __prevHad; __prevHad = has; return ok;
        }, { timeout: 30000, interval: 400 })
        .catch(() => { /* best-effort — the next step has its own wait */ });
    })();
    
    // Tap Later Button
    await (await __ensureVisible('android=new UiSelector().resourceId("btn_name_skip")')).click();
    
    // Tap Review Shortcut
    await (await __ensureVisible('android=new UiSelector().resourceId("shortcut_review")')).click();
    
    // Assert Review Counter contains "review_counter_prefix"
    {
      const __el = await (await __ensureVisible('android=new UiSelector().textContains("thẻ · đã ôn")'));
      await __el.waitForDisplayed();
      const __expected = String(testData.get('review_counter_prefix')).trim();
      const __sources = driver.isIOS
        ? [await __el.getAttribute('label'), await __el.getAttribute('value')]
        : [await __el.getText(), await __el.getAttribute('content-desc')];
      const __actual = String(__sources.find(s => s && String(s).trim() !== '') ?? '').trim();
      if (!__actual.includes(__expected)) {
        throw new Error(`[sungen] Expected text containing "${__expected}" but element reads "${__actual}"`);
      }
    }
    
    // Assert Review Empty Title is not visible
    await expect($('android=new UiSelector().text("Chưa có thẻ nào cần ôn")')).not.toBeDisplayed();
    
  });

  it('VP-LOGIC-003 Topic detail shows the same title as the continue card on Home (consistency) [@high @spec:FR-TP-01]', async () => {
    // Tap Back Button
    await (await __ensureVisible('android=new UiSelector().resourceId("btn_back")')).click();
    
    // Dismiss Later Button if present
    // Best-effort dismiss (launch interstitial / promo / ad): tap if it shows within a short grace,
    // otherwise skip — never fails the step.
    try { const __d = await (await __ensureVisible('android=new UiSelector().resourceId("btn_name_skip")')); if (await __d.waitForDisplayed({ timeout: 2500 }).then(() => true).catch(() => false)) await __d.click(); } catch { /* not present — skip */ }
    
    // Remember Home Topic Title text as card_topic
    {
      const __el = await (await __ensureVisible('android=new UiSelector().resourceId("screen_home").childSelector(new UiSelector().text("Meeting new people"))'));
      const __sources = driver.isIOS
        ? [await __el.getAttribute('label'), await __el.getAttribute('value')]
        : [await __el.getText(), await __el.getAttribute('content-desc')];
      let __captured = String(__sources.find(s => s && String(s).trim() !== '') ?? '').trim();
      testData.set('card_topic', __captured);
    }
    
    // Tap Continue Learning
    await (await __ensureVisible('android=new UiSelector().resourceId("btn_continue_learning")')).click();
    
    // Assert Topic Title contains "card_topic"
    {
      const __el = await (await __ensureVisible('android=new UiSelector().text("Meeting new people")'));
      await __el.waitForDisplayed();
      const __expected = String(testData.get('card_topic')).trim();
      const __sources = driver.isIOS
        ? [await __el.getAttribute('label'), await __el.getAttribute('value')]
        : [await __el.getText(), await __el.getAttribute('content-desc')];
      const __actual = String(__sources.find(s => s && String(s).trim() !== '') ?? '').trim();
      if (!__actual.includes(__expected)) {
        throw new Error(`[sungen] Expected text containing "${__expected}" but element reads "${__actual}"`);
      }
    }
    
  });

  it('VP-NAV-006 Back from the topic returns to Home [@normal @spec:FR-TP-01]', async () => {
    // Tap Back Button
    await (await __ensureVisible('android=new UiSelector().resourceId("btn_back")')).click();
    
    // Dismiss Later Button if present
    // Best-effort dismiss (launch interstitial / promo / ad): tap if it shows within a short grace,
    // otherwise skip — never fails the step.
    try { const __d = await (await __ensureVisible('android=new UiSelector().resourceId("btn_name_skip")')); if (await __d.waitForDisplayed({ timeout: 2500 }).then(() => true).catch(() => false)) await __d.click(); } catch { /* not present — skip */ }
    
    // Assert Home Screen is visible
    await __assertVisible('android=new UiSelector().resourceId("screen_home")');
  });

});
