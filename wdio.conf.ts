/**
 * WebdriverIO runner config for sungen mobile (Appium) tests.
 * Mobile equivalent of playwright.config.ts. Run with: npm run test:mobile
 *
 * Capabilities default to the app configured at `sungen init --mobile`, but every value can be
 * overridden at runtime via env vars — no need to edit this file to target another app/device:
 *   APP_PACKAGE=com.other.app ANDROID_UDID=emulator-5556 npm run test:mobile
 *   (the launch activity is auto-detected from APP_PACKAGE; set APP_ACTIVITY only to pin a non-standard one)
 *   APP_APK=/path/to/app.apk npm run test:mobile     # install + run from an .apk (Appium derives pkg/activity)
 *
 * iOS Simulator (opt in with MOBILE_PLATFORM=ios; needs Xcode + the xcuitest driver):
 *   MOBILE_PLATFORM=ios IOS_BUNDLE_ID=com.example.app npm run test:mobile          # app already installed on the sim
 *   MOBILE_PLATFORM=ios IOS_APP=/path/MyApp.app npm run test:mobile                # Appium installs a Simulator build
 *   (+ optional IOS_DEVICE='iPhone 16' IOS_VERSION='18.0' IOS_UDID=<sim-udid>)
 *
 * The @wdio/appium-service auto-starts a local Appium server (auto-detecting the uiautomator2 / xcuitest driver).
 */
/// <reference types="node" />
/// <reference types="@wdio/globals/types" />
// Load .env.appium (if present) into process.env FIRST — so every override below (ANDROID_HOME / JAVA_HOME /
// ANDROID_UDID / APP_PACKAGE / IOS_BUNDLE_ID / MOBILE_PLATFORM / SUNGEN_ENV …) can come from a dedicated
// .env.appium file instead of shell exports. A separate file (not the generic .env) keeps Appium config from
// clashing with other .env vars (e.g. FIGMA_PAT). dotenv is a dependency; a missing .env.appium is a no-op.
import dotenv from 'dotenv';
dotenv.config({ path: '.env.appium' });
import * as fs from 'node:fs';
import * as path from 'node:path';
import { execSync } from 'node:child_process';
import PwShapeReporter from './specs/reporters/pw-shape-reporter';

/**
 * Failure-evidence output. `test-results/` is already gitignored — screenshots and page sources
 * of a real app routinely contain live data (a signed-in email, a token rendered on screen), so
 * this directory must never become committable.
 */
const EVIDENCE_ROOT = path.join('test-results', 'mobile');
/** A full logcat is unbounded; only the tail is ever diagnostic. */
const EVIDENCE_LOG_TAIL_LINES = 500;
/** ~2 MB of accessibility tree is already far more than anyone reads. */
const EVIDENCE_PAGE_SOURCE_MAX = 2_000_000;

/** Filesystem-safe path segment from a feature/test title. */
function evidenceSlug(s: string): string {
  return String(s)
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 80) || 'unnamed';
}

/**
 * True only in the launcher process. `@wdio/runner` re-parses this very config inside EVERY worker
 * it spawns, so anything logged at module scope prints once per spec unless it is gated. WDIO's own
 * "am I a worker" test is this env var (`@wdio/local-runner` sets it when spawning; `@wdio/utils`
 * reads it) — reuse it rather than inventing a second signal.
 */
const IS_LAUNCHER = !process.env.WDIO_WORKER_ID;

/**
 * Auto-select MOBILE specs and classify each by its target OS (dual-target routing).
 *
 * sungen emits one spec per feature; the test runner differs by platform:
 *   - mobile/appium specs import from '@wdio/globals'  ← run these with wdio
 *   - web/playwright specs import from '@playwright/test' ← run those with `playwright test`
 * The '@wdio/globals' import is the machine-readable "platform = mobile" marker, so we keep
 * only those files and never feed Playwright specs into the (incompatible) mocha runner.
 *
 * Each mobile spec also carries a `// sungen:platform=<mobile|android|ios>` marker (emitted from the
 * feature's @platform:* tag). It routes the spec per OS:
 *   - mobile  → cross-platform: runs on BOTH the Android and iOS capability sets
 *   - android → Android cap only
 *   - ios     → iOS cap only
 * A spec with no marker (older codegen) defaults to `mobile` (runs on both) so nothing is dropped.
 *
 * Override with SUNGEN_SPECS=<glob-or-path> for a single spec (still classified by its marker).
 */
function readPlatformMarker(file: string): 'mobile' | 'android' | 'ios' {
  try {
    const m = /sungen:platform=(mobile|android|ios)/.exec(fs.readFileSync(file, 'utf-8'));
    return (m?.[1] as 'mobile' | 'android' | 'ios') || 'mobile';
  } catch {
    return 'mobile';
  }
}
function classifyMobileSpecs(root: string): Array<{ file: string; platform: string }> {
  if (!fs.existsSync(root)) return [];
  return (fs.readdirSync(root, { recursive: true }) as string[])
    .filter((rel) => rel.endsWith('.spec.ts'))
    .map((rel) => path.join(root, rel))
    .filter((full) => {
      try {
        return fs.readFileSync(full, 'utf-8').includes('@wdio/globals');
      } catch (e) {
        // A spec listed by readdir that cannot be read cannot be classified, so it is excluded.
        // Excluding it SILENTLY is the fault: WDIO only fails the run when the spec list is
        // EMPTY ("No specs found to run"), so dropping one spec out of ten leaves a green suite
        // containing a test that never executed. Name it, and let the count below show the shortfall.
        if (IS_LAUNCHER) console.warn(`[sungen] could not read ${full} — EXCLUDED from this run: ${(e as Error).message}`);
        return false;
      }
    })
    .map((file) => ({ file, platform: readPlatformMarker(file) }));
}

const specEntries = process.env.SUNGEN_SPECS
  ? [process.env.SUNGEN_SPECS].map((file) => ({ file, platform: readPlatformMarker(file) }))
  : classifyMobileSpecs(path.join(process.cwd(), 'specs', 'generated'));

// Android cap runs android + mobile (everything except ios-only); iOS cap runs ios + mobile.
const androidSpecs = specEntries.filter((e) => e.platform !== 'ios').map((e) => e.file);
const iosSpecs = specEntries.filter((e) => e.platform !== 'android').map((e) => e.file);
const mobileSpecs = specEntries.map((e) => e.file); // union — top-level fallback

// What DISCOVERY found — a mobile suite is only trustworthy if you can see how many specs it
// resolved: WDIO fails loudly on an EMPTY list but says nothing when the list is merely short,
// which is exactly what a dropped spec looks like.
//
// Two things this number is not. It is not an error on 0: WDIO already exits 1 there
// ("No specs found to run"), and a `--shard` that legitimately receives none must still return 0.
// And under `--shard` it is the PRE-SHARD total, because ConfigParser slices `specs` down to the
// shard's portion only after this file has finished evaluating.
if (IS_LAUNCHER) console.log(`[sungen] specs: ${specEntries.length} mobile spec(s) — android ${androidSpecs.length}, ios ${iosSpecs.length}`);

// App under test — env overrides the init-configured defaults.
const APP_PACKAGE = process.env.APP_PACKAGE || 'com.speakeasy.app';
const APP_ACTIVITY = process.env.APP_ACTIVITY || 'com.speakeasy.app.MainActivity';
const APP_APK = process.env.APP_APK; // optional: install + launch from an .apk file
// appActivity is OPTIONAL: UiAutomator2 auto-resolves the package's launcher activity when it's
// omitted, and the per-scenario relaunch uses activateApp(package) (no activity). So Android needs
// only APP_PACKAGE — set APP_ACTIVITY only to pin a non-standard launcher. iOS has no activity
// concept at all (bundleId only). Only pass the cap when APP_ACTIVITY is a real value (not the
// unfilled scaffold placeholder / empty), so a package-only setup launches cleanly.
const HAS_REAL_APP_ACTIVITY = !!APP_ACTIVITY && APP_ACTIVITY !== '__APP_ACTIVITY__';

// ── iOS (Simulator) — opt in with MOBILE_PLATFORM=ios; every value is env-overridable ──────────
//   MOBILE_PLATFORM=ios IOS_BUNDLE_ID=com.example.app npm run test:mobile
//   - IOS_APP=/path/MyApp.app  → Appium installs a SIMULATOR build (.app or zipped .app) first.
//                                (A device .ipa won't run on a Simulator — wrong binary/platform.)
//   - IOS_BUNDLE_ID=com.x      → attach to an app already installed on the sim (simctl install first).
//   - IOS_DEVICE / IOS_VERSION → pick the simulator; IOS_UDID targets a specific one.
//
// MOBILE_PLATFORM selects which capability set(s) run:
//   android (default) → Android cap only        (runs @platform:android + @platform:mobile specs)
//   ios               → iOS cap only            (runs @platform:ios + @platform:mobile specs)
//   both              → BOTH caps in one run     (a @platform:mobile spec executes on each — write once, run both)
const MOBILE_PLATFORM = (process.env.MOBILE_PLATFORM || 'android').toLowerCase();
const RUN_ANDROID = MOBILE_PLATFORM === 'android' || MOBILE_PLATFORM === 'both';
const RUN_IOS = MOBILE_PLATFORM === 'ios' || MOBILE_PLATFORM === 'both';
const IOS_APP = process.env.IOS_APP;
const IOS_BUNDLE_ID = process.env.IOS_BUNDLE_ID || 'com.example.app';
const IOS_UDID = process.env.IOS_UDID;
// When a specific Simulator is targeted by IOS_UDID, XCUITest uses THAT device and IGNORES the
// deviceName/platformVersion caps — so a stale IOS_DEVICE/IOS_VERSION would MISLABEL the deliverable's
// "Test Environment". Resolve the real device name + iOS runtime from the UDID via simctl so the caps
// (and the reporter's label) reflect reality. Explicit IOS_DEVICE/IOS_VERSION still win; on a real
// device / non-mac, simctl fails → fall back to the provided/scaffold values.
function resolveSimDevice(udid: string): { name?: string; version?: string } {
  try {
    const data = JSON.parse(execSync('xcrun simctl list devices --json', { encoding: 'utf-8' })) as {
      devices?: Record<string, Array<{ udid: string; name: string }>>;
    };
    for (const [runtime, devs] of Object.entries(data.devices || {})) {
      const hit = devs.find((d) => d.udid === udid);
      if (hit) {
        const vm = /iOS-(\d+)-(\d+)/.exec(runtime); // com.apple.CoreSimulator.SimRuntime.iOS-26-3
        return { name: hit.name, version: vm ? `${vm[1]}.${vm[2]}` : undefined };
      }
    }
  } catch { /* simctl unavailable (real device / non-mac) → keep provided values */ }
  return {};
}
const IOS_SIM = IOS_UDID ? resolveSimDevice(IOS_UDID) : {};
const IOS_DEVICE = process.env.IOS_DEVICE || IOS_SIM.name || 'iPhone 16';
const IOS_VERSION = process.env.IOS_VERSION || IOS_SIM.version || '18.0';
// platformVersion is OPTIONAL on a real device: with IOS_UDID set, XCUITest reads the iOS version off
// the connected device. Only pass the cap when IOS_VERSION is a real value (not the unfilled scaffold
// placeholder). On a Simulator set it (or IOS_DEVICE) so Appium picks a matching runtime.
const HAS_REAL_IOS_VERSION = !!IOS_VERSION && IOS_VERSION !== '__IOS_VERSION__';
// Real iPhone (device) — WebDriverAgent must be code-signed onto it. Set IOS_TEAM_ID (your Apple
// Team ID) to switch the iOS cap into real-device mode; the Simulator ignores these (they're only
// emitted when IOS_TEAM_ID is present, so Simulator runs are unchanged). For a real device also set
// IOS_UDID (the device udid, NOT a sim) and the REAL IOS_BUNDLE_ID (Android package ≠ iOS bundle id).
const IOS_TEAM_ID = process.env.IOS_TEAM_ID;                       // → appium:xcodeOrgId
const IOS_SIGNING_ID = process.env.IOS_SIGNING_ID || 'Apple Development'; // → appium:xcodeSigningId
const IOS_WDA_BUNDLE_ID = process.env.IOS_WDA_BUNDLE_ID || 'com.sungen.wda'; // → appium:updatedWDABundleId; default gives WDA a unique id (required for free teams) so you needn't set it

// ── i18n (device locale) ────────────────────────────────────────────────────
// SUNGEN_ENV doubles as the locale selector (the same var that swaps the test-data overlay).
// When its value looks like a locale code ("vi", "ja", "vi-VN"), drive the DEVICE locale via
// appium:language/appium:locale so an app that localizes from device locale on first launch picks
// it up, and force a data reset (noReset:false) so that first-launch path actually re-runs (clears
// any persisted in-app language override). Non-locale envs ("staging", "production") are left
// untouched, so existing suites are unaffected. SUNGEN_LOCALE overrides explicitly when the device
// locale must differ from the overlay name. Country defaults are used only when the code omits a region.
const LOCALE_COUNTRY: Record<string, string> = {
  vi: 'VN',
  ja: 'JP',
  en: 'US',
  ko: 'KR',
  zh: 'CN',
  th: 'TH',
  id: 'ID',
  fr: 'FR',
  de: 'DE',
  es: 'ES',
  pt: 'BR',
  ru: 'RU',
};
function resolveLocaleCaps(): Record<string, unknown> {
  const raw = process.env.SUNGEN_LOCALE || process.env.SUNGEN_ENV;
  if (!raw) return {};
  const m = /^([a-z]{2})(?:[-_]([A-Za-z]{2}))?$/.exec(raw.trim());
  if (!m) return {}; // not a locale code (e.g. "staging") → don't touch the device locale
  const language = m[1];
  const country = (m[2] || LOCALE_COUNTRY[language] || language).toUpperCase();
  return {
    'appium:language': language,
    'appium:locale': country,
    'appium:noReset': false,
  };
}
const localeCaps = resolveLocaleCaps();

// ── Connectivity self-heal (#615) ───────────────────────────────────────────
// The connectivity/location steps mutate DEVICE state (airplane, wifi, data, GPS). A run that
// aborts mid-toggle — e.g. an Appium session timeout during an airplane round-trip — leaves the
// device offline as PERSISTENT state, so every later launch fails with the app's network error
// and the next run dies at cold start. Restore the baseline around the run via host-side adb:
// it works even when no Appium session survives (a spec-level after() cannot — the session is
// exactly what died). Android-only (the mutating steps are Android-only), idempotent (on/on/on
// is the baseline every suite assumes; offline scenarios set their own state mid-test), and
// best-effort per command (a device lacking one command just skips that line).
function restoreConnectivity(hook: string): void {
  if (!RUN_ANDROID) return;
  // Appium finds adb via ANDROID_HOME — PATH may not carry it (fresh CI runner), which would
  // make every heal command fail silently. Resolve the same way Appium does, fall back to PATH.
  const adbBin = process.env.ANDROID_HOME ? path.join(process.env.ANDROID_HOME, 'platform-tools', 'adb') : 'adb';
  const adb = `"${adbBin}" -s ${process.env.ANDROID_UDID || 'emulator-5554'} shell`;
  // stderr ignored: with a detached device the reads would otherwise spray raw adb errors on every run.
  const sh = (cmd: string) => execSync(`${adb} ${cmd}`, { timeout: 10000, stdio: ['ignore', 'pipe', 'ignore'] }).toString().trim();
  // Detect-first: read each state and only touch what is actually wedged, so a healthy run
  // costs four quick reads, mutates nothing, and logs nothing. Each fix lists every command
  // variant (emulator `svc`, real-device `cmd`, API<30 fallbacks) — all best-effort, same
  // direction, idempotent, so running the inapplicable ones is harmless.
  let touched = false;
  const heal = (read: string, wedged: string, fixes: string[]) => {
    try {
      if (sh(read) !== wedged) return;
      for (const fix of fixes) { try { sh(fix); } catch { /* variant not on this API — fine */ } }
      touched = true;
    } catch { /* unreadable state (device gone / key absent) — leave untouched */ }
  };
  heal('settings get global airplane_mode_on', '1', [
    'cmd connectivity airplane-mode disable',                       // API 30+
    'settings put global airplane_mode_on 0',                       // API<30 wedge (settings+broadcast)
    'am broadcast -a android.intent.action.AIRPLANE_MODE --ez state false',
  ]);
  heal('settings get global wifi_on', '0', [
    'svc wifi enable',                                              // emulator flavor
    'cmd -w wifi set-wifi-enabled enabled',                         // real-device flavor (svc can no-op silently)
  ]);
  heal('settings get global mobile_data', '0', [
    'svc data enable',
    'cmd phone data enable',
  ]);
  heal('cmd location is-location-enabled', 'false', ['cmd location set-location-enabled true']);
  if (!touched) return;
  console.log(`[sungen] connectivity self-heal (${hook}): device was offline — a previous run likely aborted mid-toggle. Baseline restored (airplane off, wifi/data/location on).`);
  // Radios re-associate asynchronously (emulator wifi can take 10-30s) — wait for REAL IP
  // connectivity before the session starts, or the app's first cold start still lands on its
  // network-error dialog and the auth/first assert fails despite the heal.
  for (let i = 0; i < 10; i++) {
    try { execSync(`${adb} ping -c 1 -W 2 8.8.8.8`, { stdio: 'ignore', timeout: 8000 }); return; } catch { /* not yet */ }
    try { execSync(`${adb} sleep 2`, { stdio: 'ignore', timeout: 8000 }); } catch { /* device gone — stop waiting */ return; }
  }
  console.log('[sungen] connectivity self-heal: no ICMP reply within ~30s — continuing (fine if this network blocks ping; otherwise the device may need a cold boot).');
}

// Evidence readability: render a dot at every injected tap so screen recordings show WHERE the
// test touched. Android-only (iOS has no supported equivalent). The pre-run value is remembered
// and restored on onComplete, so a developer's own device setting is not permanently mutated.
let showTouchesBefore: string | null = null;
function setShowTouches(hook: 'onPrepare' | 'onComplete'): void {
  if (!RUN_ANDROID) return;
  const adbBin = process.env.ANDROID_HOME ? path.join(process.env.ANDROID_HOME, 'platform-tools', 'adb') : 'adb';
  const adb = `"${adbBin}" -s ${process.env.ANDROID_UDID || 'emulator-5554'} shell`;
  const sh = (cmd: string) => execSync(`${adb} ${cmd}`, { timeout: 10000, stdio: ['ignore', 'pipe', 'ignore'] }).toString().trim();
  try {
    if (hook === 'onPrepare') {
      showTouchesBefore = sh('settings get system show_touches');
      if (showTouchesBefore !== '1') sh('settings put system show_touches 1');
    } else if (showTouchesBefore !== null && showTouchesBefore !== '1') {
      sh(`settings put system show_touches ${showTouchesBefore === 'null' ? '0' : showTouchesBefore}`);
    }
  } catch { /* device gone / key absent — cosmetic feature, never block the run */ }
}

export const config: WebdriverIO.Config = {
  runner: 'local',
  tsConfigPath: './tsconfig.json',

  specs: mobileSpecs,
  // SUNGEN_MAX_INSTANCES=2 lets a MOBILE_PLATFORM=both run execute the Android and iOS capability
  // in PARALLEL (separate devices; uiautomator2/XCUITest do not share ports; the reporter writes
  // per-platform files so results cannot collide). Default stays 1 — serial is the safe choice on
  // a laptop driving an emulator + a simulator at once.
  maxInstances: Number(process.env.SUNGEN_MAX_INSTANCES || 1),

  // Global auto-wait for $/$$ matchers (toBeDisplayed, click, …). Default is 3s, too short for a
  // heavy app's cold (re)launch — each scenario terminates+activates its app, so the first
  // find/click must wait out the splash/first-frame. 20s keeps slow native/Flutter apps green.
  // 30s (not the classic 20s): on a Compose app with looping in-app animations the accessibility
  // manager can starve for 10s+ per query ("Timed out waiting for the root AccessibilityNodeInfo",
  // observed live) — a 20s budget turns that latency into flaky not-found failures.
  waitforTimeout: 30000,

  // Capability set(s) selected by MOBILE_PLATFORM (android | ios | both). Each cap carries its OWN
  // `specs` (per-OS routing): the Android cap runs android + mobile specs, the iOS cap runs ios +
  // mobile specs, so MOBILE_PLATFORM=both executes a @platform:mobile spec on each in a single run.
  // i18n localeCaps (appium:language/locale + noReset:false) apply to both when SUNGEN_ENV is a locale.
  capabilities: [
    ...(RUN_ANDROID
      ? [
          {
            platformName: 'Android',
            'appium:automationName': 'UiAutomator2',
            'appium:udid': process.env.ANDROID_UDID || 'emulator-5554',
            // APP_APK installs from a file (Appium reads pkg/activity from the manifest);
            // otherwise target an already-installed app by package + activity.
            ...(APP_APK ? { 'appium:app': APP_APK } : {}),
            // Explicit APP_PACKAGE/APP_ACTIVITY are passed even alongside APP_APK: the driver's
            // APK-manifest parse can flake (empty activity → bare MAIN/LAUNCHER intent, which
            // Android rejects as "matches multiple activities"), and explicit caps override it.
            ...(process.env.APP_PACKAGE || !APP_APK ? { 'appium:appPackage': APP_PACKAGE } : {}),
            // Omit when unset → UiAutomator2 launches the package's default launcher activity.
            ...(HAS_REAL_APP_ACTIVITY ? { 'appium:appActivity': APP_ACTIVITY } : {}),
            'appium:noReset': true,
            'appium:autoGrantPermissions': true,
            'appium:newCommandTimeout': 300,
            // Compose/animated UIs (a looping promo animation is enough) never report accessibility-idle,
            // so every find first waits out UiAutomator2's full idle timeout — 10000ms by default. 2s keeps
            // interactions responsive while still absorbing screen transitions. NOTE: milliseconds — the
            // same-named XCUITest cap below is in seconds; the two drivers define the knob differently.
            'appium:settings[waitForIdleTimeout]': 2000,
            // Disable SYSTEM animation scales for the session — trims accessibility-tree churn so
            // queries stall less on animation-heavy apps (in-app Compose animations still run).
            'appium:disableWindowAnimation': true,
            // i18n: when SUNGEN_ENV/SUNGEN_LOCALE is a locale code, this overrides noReset→false and
            // adds appium:language/appium:locale so the app boots in that locale. Empty otherwise.
            ...localeCaps,
            // Per-capability spec routing (supported by WDIO at runtime; not in the TS cap type → cast).
            specs: androidSpecs,
          } as any,
        ]
      : []),
    ...(RUN_IOS
      ? [
          {
            platformName: 'iOS',
            'appium:automationName': 'XCUITest',
            // Flutter apps NEVER report "idle" to XCUITest, so WDA always waits the FULL
            // waitForIdleTimeout before EVERY interaction. The default (~10s) makes gesture
            // loops blow the mocha timeout; 0 removes the wait entirely but then taps fire
            // mid-transition/mid-launch and randomly miss (measured: ~2 flaky scenarios per
            // run). 2s is the working balance — a universal settle that covers Flutter
            // transitions while keeping bounded scroll/error paths well inside the budget.
            'appium:waitForIdleTimeout': 2,
            'appium:deviceName': IOS_DEVICE,
            // Omit when unknown (real device, no IOS_VERSION) → XCUITest reads it from the device by udid.
            ...(HAS_REAL_IOS_VERSION ? { 'appium:platformVersion': IOS_VERSION } : {}),
            // target a specific simulator if given (else Appium uses the matching device/version)
            ...(IOS_UDID ? { 'appium:udid': IOS_UDID } : {}),
            // IOS_APP installs a Simulator build (.app/.zip); otherwise attach to an installed bundleId.
            ...(IOS_APP
              ? { 'appium:app': IOS_APP }
              : IOS_BUNDLE_ID
                ? { 'appium:bundleId': IOS_BUNDLE_ID }
                : {}),
            // Real-device WDA signing — emitted only when IOS_TEAM_ID is set (Simulator: omitted → unchanged).
            ...(IOS_TEAM_ID
              ? {
                  'appium:xcodeOrgId': IOS_TEAM_ID,
                  'appium:xcodeSigningId': IOS_SIGNING_ID,
                  // Pass -allowProvisioningUpdates -allowProvisioningDeviceRegistration to xcodebuild so it
                  // auto-creates/refreshes the WDA profile + registers the device (needed for automatic
                  // signing on a real device; without it WDA build fails fast with "xcodebuild code 65").
                  'appium:allowProvisioningDeviceRegistration': true,
                  ...(IOS_WDA_BUNDLE_ID ? { 'appium:updatedWDABundleId': IOS_WDA_BUNDLE_ID } : {}),
                }
              : {}),
            'appium:noReset': true,
            'appium:newCommandTimeout': 300,
            ...localeCaps,
            specs: iosSpecs,
          } as any,
        ]
      : []),
  ],

  logLevel: 'warn',
  framework: 'mocha',
  // 'spec' for console + PwShapeReporter for a Playwright-shaped JSON the delivery/dashboard reuse.
  reporters: ['spec', [PwShapeReporter, {}]],

  // Auto-start a local Appium server on :4723 (base path '/')
  // Appium server options — security-gated features, each opt-in via .env.appium:
  //   APPIUM_ALLOW_ADB_SHELL=1            → --allow-insecure uiautomator2:adb_shell
  //     (raw shell access — device-level oracles like device-sqlite's run-as fallback)
  //   APPIUM_CHROMEDRIVER_AUTODOWNLOAD=1  → --allow-insecure uiautomator2:chromedriver_autodownload
  //     (hybrid WebView: downloads the chromedriver matching the device's WebView at session start —
  //      without it, `switch to [X] frame` fails with "No Chromedriver found that can automate Chrome N")
  // Both stay OFF by default (see docs/spec/security-and-telemetry-roadmap.md).
  services: [
    ['appium', (() => {
      const feats: string[] = [];
      if (process.env.APPIUM_ALLOW_ADB_SHELL) feats.push('uiautomator2:adb_shell');
      if (process.env.APPIUM_CHROMEDRIVER_AUTODOWNLOAD) feats.push('uiautomator2:chromedriver_autodownload');
      // NOTE: pass a comma-joined STRING — @wdio/appium-service JSON.stringifies arrays, which the
      // appium server then reads as a JSON literal and the features silently never register.
      return feats.length ? { args: { allowInsecure: feats.join(',') } } : {};
    })()],
  ],
  port: 4723,
  path: '/',

  mochaOpts: {
    ui: 'bdd',
    timeout: 120000,
    // One retry per test: the relaunch-per-scenario pattern on a Flutter sim has a residual
    // launch race (~10%/scenario — the first interaction can fire before the app routes
    // touches), with a RANDOM victim each run. A retry re-runs the full beforeEach (fresh
    // relaunch), so race victims recover on attempt 2 while real failures (wrong expected
    // value, missing element) fail both attempts and still report Failed. The reporter and
    // delivery already take the LAST attempt.
    retries: 1,
  },

  // expect-webdriverio's default assertion budget (~2s wait / 100ms interval) is tuned for
  // sub-second finds. On an accessibility tree churned by looping in-app animations (a rotating
  // promo carousel is enough) a single server-side find can take 5-15s, so every toBeDisplayed()
  // would expire before its first query even returns — align the budget with waitforTimeout.
  before: async () => {
    const { setOptions } = await import('expect-webdriverio');
    setOptions({ wait: 20000, interval: 500 });
  },

  // Connectivity self-heal (#615): onPrepare heals a device wedged by a previous KILLED run
  // (its onComplete never fired) before the session starts; onComplete restores after this run,
  // pass or fail. Both run in the launcher process, so they fire even when the worker/session died.
  onPrepare: () => { restoreConnectivity('onPrepare'); setShowTouches('onPrepare'); fs.mkdirSync(EVIDENCE_ROOT, { recursive: true }); },
  onComplete: () => { restoreConnectivity('onComplete'); setShowTouches('onComplete'); },

  /**
   * Failure evidence. A mobile failure used to reach the report as a single error string — no
   * image, no tree, no device log — which makes triaging a native UI almost impossible.
   *
   * Only runs on failure, so a passing suite pays nothing. Each capture is wrapped SEPARATELY:
   * one unavailable command (a Flutter page source that blows the buffer, a driver with no log
   * endpoint) must not cost us the other two, and nothing in here may convert an already-failing
   * test into a different error.
   */
  afterTest: async (
    test: { title?: string; parent?: string },
    _context: unknown,
    result: { passed?: boolean },
  ): Promise<void> => {
    if (result?.passed) return;

    // Opt-out for constrained runners (a device farm billing per artifact, a CI box with no disk).
    // A feature tagged @screenshot:on-failure declares its evidence essential and overrides it.
    const forced = (globalThis as Record<string, unknown>).__sungenForceEvidence === true;
    if (!forced && process.env.SUNGEN_MOBILE_EVIDENCE === 'off') return;

    const feature = test?.parent || 'Tests';
    const title = test?.title || 'test';
    const dir = path.join(EVIDENCE_ROOT, evidenceSlug(feature), evidenceSlug(title));
    const files: Array<{ name: string; path: string; contentType: string }> = [];

    try {
      fs.mkdirSync(dir, { recursive: true });
    } catch (e) {
      console.warn(`[sungen] could not create evidence dir ${dir}: ${(e as Error).message}`);
      return;
    }

    // 1. Screenshot — the single most useful artifact for a UI failure.
    try {
      const shot = path.join(dir, 'screenshot.png');
      await driver.saveScreenshot(shot);
      files.push({ name: 'screenshot', path: shot, contentType: 'image/png' });
    } catch (e) {
      console.warn(`[sungen] screenshot capture failed: ${(e as Error).message}`);
    }

    // 2. Page source — the accessibility tree, i.e. WHY a selector did not match. Capped: a
    //    Flutter/RecyclerView tree can run to tens of MB and there is no value in writing that.
    try {
      const src = await driver.getPageSource();
      const capped = src.length > EVIDENCE_PAGE_SOURCE_MAX
        ? `${src.slice(0, EVIDENCE_PAGE_SOURCE_MAX)}\n<!-- [sungen] truncated at ${EVIDENCE_PAGE_SOURCE_MAX} of ${src.length} chars -->`
        : src;
      if (src.length > EVIDENCE_PAGE_SOURCE_MAX) {
        console.warn(`[sungen] page source ${src.length} chars — truncated to ${EVIDENCE_PAGE_SOURCE_MAX}`);
      }
      const out = path.join(dir, 'page-source.xml');
      fs.writeFileSync(out, capped, 'utf-8');
      files.push({ name: 'page-source', path: out, contentType: 'application/xml' });
    } catch (e) {
      console.warn(`[sungen] page source capture failed: ${(e as Error).message}`);
    }

    // 3. Device log — logcat on Android, syslog on iOS. Tail only: a full logcat is unbounded.
    try {
      const logType = driver.isIOS ? 'syslog' : 'logcat';
      const entries = (await driver.getLogs(logType)) as Array<{ message?: string } | string>;
      const lines = entries.map((l) => (typeof l === 'string' ? l : l?.message || '')).filter(Boolean);
      const tail = lines.slice(-EVIDENCE_LOG_TAIL_LINES).join('\n');
      const out = path.join(dir, 'device.log');
      fs.writeFileSync(out, tail, 'utf-8');
      files.push({ name: 'device-log', path: out, contentType: 'text/plain' });
    } catch (e) {
      console.warn(`[sungen] device log capture failed: ${(e as Error).message}`);
    }

    // Hand the paths to the reporter so they land in the result's `attachments`. Resolved off
    // globalThis, not imported — see the note in specs/reporters/pw-shape-reporter.ts.
    try {
      const attach = (globalThis as Record<string, unknown>).__sungenAttachEvidence;
      if (typeof attach === 'function') {
        (attach as (f: string, t: string, files: typeof files) => void)(feature, title, files);
      }
    } catch (e) {
      console.warn(`[sungen] could not register evidence with the reporter: ${(e as Error).message}`);
    }
  },

  // iOS + locale: XCUITest's `noReset:false` does NOT actually clear the app's data container
  // (verified live 2026-06-11) — a Flutter app that persisted its language on a previous launch keeps
  // it and ignores the new device locale, so the i18n run asserts the wrong language. Wipe the app
  // data via simctl BEFORE the session so first-launch locale detection re-runs. Android needs no
  // hook (its noReset:false reset works natively).
  beforeSession: async (_config, capabilities, specs) => {
    if (Object.keys(localeCaps).length === 0) return; // not a locale run
    const caps = capabilities as Record<string, unknown>;
    if (String(caps['platformName'] || '').toLowerCase() !== 'ios') return;
    const { execSync } = await import('node:child_process');
    const fsm = await import('node:fs');
    const udid = (caps['appium:udid'] as string) || 'booted';
    // Wipe the session-default app AND every per-feature dual-id bundle the worker's specs declare
    // (__IOS_BUNDLE__ markers) — a multi-app iOS locale run must reset each app, not just the first.
    const bundleIds = new Set<string>();
    if (typeof caps['appium:bundleId'] === 'string') bundleIds.add(caps['appium:bundleId'] as string);
    for (const spec of specs || []) {
      try {
        const src = fsm.readFileSync(String(spec).replace('file://', ''), 'utf-8');
        const m = /__IOS_BUNDLE__ = '([^']+)'/.exec(src);
        if (m) bundleIds.add(m[1]);
      } catch { /* unreadable spec — skip */ }
    }
    for (const bundleId of bundleIds) {
      try { execSync(`xcrun simctl terminate ${udid} ${bundleId}`, { stdio: 'ignore' }); } catch { /* not running */ }
      try {
        const dataDir = execSync(`xcrun simctl get_app_container ${udid} ${bundleId} data`).toString().trim();
        if (dataDir) execSync(`rm -rf "${dataDir}/Library" "${dataDir}/Documents"`);
        console.log(`[sungen] iOS locale run: wiped app data for ${bundleId} (first-launch locale re-detect)`);
      } catch { /* app not installed yet */ }
    }
  },

  // Per-scenario app reset lives in each generated spec's `beforeEach` (terminate+activate the app
  // from that feature's `Path:`). One run can therefore host screens from different apps — each spec
  // launches its own — so there's no single-app reset hook here. The capabilities above only bootstrap
  // the session (default app / device); the real app per scenario comes from the spec.
};
