/**
 * WebdriverIO reporter that writes a **Playwright-JSON-shaped** result file per spec, so the
 * existing sungen delivery/dashboard parser (playwright-report-parser.ts) consumes mobile runs
 * unchanged. Output: `<genDir>/<basename>-test-result.<platform>[.<SUNGEN_ENV>].json`.
 *
 * The `<platform>` segment (android|ios, from the session capabilities) is REQUIRED for dual-target:
 * with MOBILE_PLATFORM=both one @platform:mobile spec runs on BOTH capabilities — two workers, two
 * reporter instances — and without a platform component they'd overwrite the same file (the last
 * platform silently erasing the first). Delivery/dashboard discover per-platform variants.
 *
 * Every field emitted here is one the parser ALREADY reads — nothing is invented:
 *   · `duration`    (parser:158) feeds the perf axis. Without it `perf.ts` sees zero samples and
 *                   returns null, so performance budgets die silently rather than loudly.
 *   · `startTime`   (parser:159) is the "Executed date". It is stamped when the test STARTS, not
 *                   when its record is written, or the column reports reporting time instead.
 *   · `attachments` (parser:148) carry the failure evidence written by wdio.conf's afterTest hook.
 *   · `results[]`   one entry PER ATTEMPT. The parser reads `results[results.length - 1]`, so a
 *                   retry belongs on the SAME spec entry. Pushing each attempt as its own spec
 *                   entry with a duplicate title only worked because the parser's Map overwrote
 *                   by title — right answer, accidental mechanism.
 *
 * Wired in wdio.conf.ts: reporters: ['spec', [PwShapeReporter, {}]].
 */
import WDIOReporter from '@wdio/reporter';
import * as fs from 'node:fs';
import * as path from 'node:path';
import { fileURLToPath } from 'node:url';

interface Attachment { name?: string; path?: string; contentType?: string }

interface Attempt {
  status: string;
  error?: string;
  startTime: string;
  /** Wall-clock ms for this attempt. Internal `startMs` never reaches the JSON. */
  startMs: number;
  duration: number;
}

interface Rec { title: string; attempts: Attempt[] }

/**
 * Failure evidence, keyed by test, queued in attempt order.
 *
 * wdio.conf.ts's `afterTest` hook writes the screenshot / page source / device log and calls
 * `attachEvidence`. Hook and reporter live in the same worker process, but WDIO does not promise
 * whether `afterTest` runs before or after the reporter's `test:fail` event — so evidence is
 * merged at `onRunnerEnd`, once, when ordering can no longer matter.
 *
 * Attempts run strictly in sequence and only a FAILING attempt produces evidence, so the i-th
 * queued batch belongs to the i-th attempt. That keeps the hook free of retry bookkeeping.
 */
const evidenceQueue = new Map<string, Attachment[][]>();

const evidenceKey = (feature: string, title: string): string => `${feature}\0${title}`;

/** Called from wdio.conf.ts's afterTest hook. One call per attempt that produced evidence. */
export function attachEvidence(feature: string, title: string, files: Attachment[]): void {
  if (!files?.length) return;
  const key = evidenceKey(feature || 'Tests', title || '');
  const batches = evidenceQueue.get(key) || [];
  batches.push(files);
  evidenceQueue.set(key, batches);
}

/** Test-only: drop queued evidence so one process can drive several fake runs. */
export function resetEvidence(): void {
  evidenceQueue.clear();
}

/**
 * Publish the collector on globalThis so wdio.conf.ts's afterTest hook can reach it WITHOUT
 * importing this module.
 *
 * Both files are scaffolded skip-if-exists and independently, so a project can end up with a new
 * wdio.conf.ts beside an old reporter (or the reverse). A static named import would then resolve
 * to undefined and the hook would throw while a test was already failing — turning a missing
 * screenshot into a crash. Through a global, a version mismatch degrades to "evidence files are
 * written but not linked in the JSON", which is the honest failure mode.
 */
(globalThis as Record<string, unknown>).__sungenAttachEvidence = attachEvidence;

type TestLike = { title?: string; parent?: string; error?: { message?: string } };

export default class PwShapeReporter extends WDIOReporter {
  private byFeature: Record<string, Rec[]> = {};

  constructor(options: Record<string, unknown>) {
    super({ ...options, stdout: false });
  }

  private rec(test: TestLike): Rec {
    const feature = test.parent || 'Tests';
    const title = test.title || '';
    const recs = (this.byFeature[feature] ||= []);
    // Same title ⇒ same test being retried, so append an attempt rather than a second entry.
    let found = recs.find((r) => r.title === title);
    if (!found) {
      found = { title, attempts: [] };
      recs.push(found);
    }
    return found;
  }

  onTestStart(test: TestLike): void {
    this.rec(test).attempts.push({
      status: 'running',
      startTime: new Date().toISOString(),
      startMs: Date.now(),
      duration: 0,
    });
  }

  private close(test: TestLike, status: string): void {
    const rec = this.rec(test);
    let attempt = rec.attempts[rec.attempts.length - 1];
    // A skipped/pending test never emits test:start, and no framework is obliged to. Synthesize
    // the attempt so a missing start event can never drop a test from the report.
    if (!attempt || attempt.status !== 'running') {
      attempt = { status, startTime: new Date().toISOString(), startMs: Date.now(), duration: 0 };
      rec.attempts.push(attempt);
    }
    attempt.status = status;
    attempt.duration = Math.max(1, Date.now() - attempt.startMs);
    const message = test.error?.message;
    if (message) attempt.error = message;
  }

  onTestPass(test: TestLike): void { this.close(test, 'passed'); }
  onTestFail(test: TestLike): void { this.close(test, 'failed'); }
  onTestSkip(test: TestLike): void { this.close(test, 'skipped'); }

  onRunnerEnd(runner: any): void {
    const specUrl: string | undefined = runner?.specs?.[0];
    if (!specUrl) return;
    const specPath = specUrl.startsWith('file:') ? fileURLToPath(specUrl) : specUrl;
    const dir = path.dirname(specPath);
    const base = path.basename(specPath).replace(/\.spec\.(ts|js)$/, '');
    const env = process.env.SUNGEN_ENV;

    // Build the runner label shown as "Test Environment": platform + OS version + the
    // actual device the test ran on (e.g. "Android 16 (Appium) — emulator-5554"). The
    // device — not the app activity — is what identifies the test environment.
    const caps = (runner?.capabilities || {}) as Record<string, unknown>;
    const cap = (k: string): string | undefined => {
      const v = caps[k] ?? caps[`appium:${k}`];
      return typeof v === 'string' && v ? v : typeof v === 'number' ? String(v) : undefined;
    };
    const platformName = cap('platformName') || 'Android';
    // Per-platform filename — each capability's worker writes its OWN file so a both-OS run keeps
    // both results: <base>-test-result.<android|ios>[.<env>].json
    const platformSlug = platformName.toLowerCase() === 'ios' ? 'ios' : 'android';
    const outName = env
      ? `${base}-test-result.${platformSlug}.${env}.json`
      : `${base}-test-result.${platformSlug}.json`;
    const version = cap('platformVersion') || cap('deviceApiLevel');
    const device = cap('deviceModel') || cap('deviceName') || cap('udid') || cap('deviceUDID');
    const runnerLabel = `${platformName}${version ? ` ${version}` : ''} (Appium)`;
    const platform = device ? `${runnerLabel} — ${device}` : runnerLabel;
    const report = {
      config: { projects: [{ name: platform }] },
      suites: Object.entries(this.byFeature).map(([feature, recs]) => ({
        title: feature,
        specs: recs.map((r) => {
          const batches = evidenceQueue.get(evidenceKey(feature, r.title)) || [];
          return {
            title: r.title,
            tests: [
              {
                projectName: platform,
                // One result per attempt, in order — Playwright's own retry shape, and what
                // the parser's `results[results.length - 1]` is written against.
                results: r.attempts.map((a, i) => ({
                  status: a.status,
                  startTime: a.startTime,
                  duration: a.duration,
                  retry: i,
                  attachments: batches[i] || [],
                  ...(a.error ? { error: { message: a.error } } : {}),
                })),
              },
            ],
          };
        }),
      })),
    };

    fs.writeFileSync(path.join(dir, outName), JSON.stringify(report, null, 2));
  }
}
