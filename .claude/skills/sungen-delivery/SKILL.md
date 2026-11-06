---
name: sungen-delivery
description: 'Export the Test Case & Coverage Matrix (or the legacy per-scenario CSV) from Gherkin + Playwright results. Auto-loaded by delivery command.'
user-invocable: false
---

## Purpose

Export test cases from Sungen units to the QA deliverable. Two modes:

- **Matrix (default)** — the **Test Case & Coverage Matrix**: one row per *delivery item* (one
  target + one test intent + one oracle family); scenarios / `@cases` rows stay as collapsible
  *coverage variant* sub-rows. Built for review, manual execution, customer hand-off, and
  reporting — no numbered step dumps.
- **Legacy (`--legacy`/`--full`)** — the classic per-scenario BM-2-901-13 workbook (documented
  in the second half of this skill; unchanged).

**This skill delegates all heavy work to the `sungen delivery` CLI.** The CLI is the single source
of truth for parsing + gating. The AI's only semantic contribution is **proposing the Delivery
Map** (grouping) — everything else is: invoke the CLI, show output verbatim, help on failures.

---

## Matrix mode — the Delivery Map

`qa/<screens|flows|api>/<unit>/delivery/<unit>.map.yaml` — committed, AI-proposed
(`review: proposed`), gate-validated, QA-approved (`sungen delivery --approve` stamps scenario
fingerprints). Schema + grouping rules live in the delivery command instructions; the normative
spec is `docs/spec/delivery-coverage-matrix-spec.md`.

**Gates** (CLI `--check`): A source (VP-ids unique, oracle present, Background setup-only) ·
B mapping (every scenario in exactly one group XOR one disposition) · C aggregation (test layer
recomputed and equal within a group — **execution mode and priority are coverage dimensions, not
splits**: mixed items show `Auto n · Manual m` and take the highest variant priority; heuristic
oracle-shape/precondition mismatches are review-level, silenced once approved and unchanged) ·
D executability (precondition · condition+
data · trigger · oracle all renderable; every `{{var}}` resolves; **no template token may survive
into a rendered cell** — test-data cross-references are resolved for display) · E drift
(scenario fingerprint mismatch → back to review; the map's OWN reviewed
wording/grouping is fingerprinted as `__map__` too, so post-approval edits re-open review) · G review state (proposed groups block the official
render; `--preview` renders a DRAFT watermark) · K category class (a `VP-SEC-*` variant outside
`category: security`, or `VP-NFR-*` outside `nfr` → warning: the Coverage grid's column would render
empty and report a false gap) · R requirement coverage (spec FR/TR/NFR ids with no trace and no
`requirements:` status → warning; **and a `status: covered` override that no variant traces to** →
warning: tag the proving scenario `@spec:<id>` instead of asserting it in prose) · W wording lint (map intent/oracle containing
tokens, `[Selector]` refs, DSL phrasing, or generator labels → warning).

**Wording normalization (deterministic, after semantic normalization):** DSL steps render as
controlled manual-test English without changing meaning — actions in the imperative (`User fill
[Email] field with X` → `Enter X in the Email field.`), expected results as observable assertions
(`User see [Jobs] page` → `The Jobs page is displayed.`), preconditions as states (`The user is
signed out.`). Manual `# Tester verifies:` labels become structured fields: `Setup:` →
Precondition, `Action:` → Action, `Observable:` → Expected Result, `Oracle:` → a separate
`Verification method:` line. Sequence-sensitive flows keep event order: actions numbered with
mid-flow assertions inline as `Verify: …`; only the final Then block is the Expected Result.
Empty test values render as `(empty)`.

**Output format**: **XLSX only by default** (one artifact). `--format csv` writes the flat CSV
instead; `--format both` writes both.

**Scope decides strictness**: a **named** unit that is not ready aborts with exit 1 (pass
`--continue-on-missing` to tolerate it); a **no-argument sweep** exports every ready unit, lists
every unit it did not export with the reason, and exits 0 as long as it wrote something — a
scaffolded unit never blocks the ready ones.

**Workbook** (matrix mode): four sheets — `Overview` (live roll-up over the two below), `Auto`,
`Manual`, `Coverage`. Auto and Manual split **per variant**, so a **mixed item's parent row
appears on both**, each side carrying only its own variants and rolling up only what is on that
sheet — expect the same ID twice in the workbook, with two different numbers. Both detail sheets
always exist even when one is empty. Layout is BM-2-901-13: band on rows 6/7/8, `*: Mandatory`
legend on row 12, the 16 headers `A..P` on row 13, data from row 14, frozen at `A14`.
`Testcase type` (`K`) is variant-level (`Auto` · `Auto · Mocked` · `Manual (M6)`) and blank on a
parent row. Full detail: `docs/spec/delivery-coverage-matrix-spec.md` §8.

`Auto` / `Manual` — parent rows + outline-level-1 variant sub-rows for **every**
item (single-variant included: the sub-row carries the source VP-id, resolved data, and the
result/evidence entry). Sub-rows are **delta-only**: knowledge common to the whole item
(precondition, trigger) is written ONCE on the parent; a child repeats only what distinguishes it
(condition/data, precondition delta, trigger when it differs) plus its own precise oracle and
execution fields. The parent never carries placeholder text — when triggers differ the cell is
simply empty and the sub-rows carry them.

Parent preconditions are the **intersection** of the variants' and the parent's Action holds the
**shared leading steps**; each variant renders only its remaining steps, numbered to continue the
parent's list — so a shared prefix is written once, not repeated on every child. The parent's
Coverage cell is the map's short `dimensions:` digest (or the variant refs when none is declared),
never a concatenation of variant titles.

Variant Result cells hold exactly one of `Passed`/`Failed`/`Blocked`/`Pending`/`N/A` (dropdown) —
that vocabulary is the contract: the parent Result and the summary band are **live Excel formulas**
counting those words, so a parent label is always a composed `n/m Passed …` string (`All N/A` when
every variant is N/A) and never a bare state word. **`N/A` leaves the denominator.** Note, Defect ID
and actual result share one column `P`; the header row is frozen; ISO dates; landscape print with
repeated header rows.

**Re-running delivery keeps the tester's Manual results** (#756, since 3.2.33). Before overwriting,
the CLI reads the existing workbook's **Manual** sheet and carries each variant's `L`–`P` cells into
the new one by `VP-…` ref, verbatim. A hidden `_sungen_manual` sheet stamps each case's fingerprint,
so a result recorded against an older procedure is reset to `Pending` with the previous result
moved into Note. Relay the CLI's `Manual results:` lines to the user: `reset to Pending` means those
cases must be run again by hand; `not carried` names results whose case left the feature. The
**Auto** sheet is not carried (the run report owns its Result), and rows a tester added by hand
have no ref and are not carried. Never tell the user to copy or rename the file to protect manual
results. A second `not carried` line names a duplicated row (only the first copy is kept) — tell
the user to merge it by hand. If the CLI prints `not written: cannot read …` (locked or damaged) or
`not written: … has no "Manual" sheet` (renamed), the unit was deliberately NOT written to protect
the results: ask the user to close the file or rename the sheet back to `Manual`, or move the file
aside. Never delete, rename or move it yourself.

`Coverage` sheet — requirement coverage table (every FR/TR/NFR id with an explicit status),
target × category grid with explicit `—` gaps, dispositions, manifest. CSV mirrors the same model
flat with a `Level` column (`item`/`variant`) + a requirement-coverage appendix.
`delivery_item_count` ≠ progress — variants are the execution metric.

**Id schemes**: the `VP-<CAT>-<NNN>` convention is a suggestion, not a requirement — a project's own
scheme (`SEC-123`, `PER-345`, `MS-HP-001`) is addressed the same way, and the category segment
(`SEC`, `PER`) still drives Gate K. Requirement ids come from the spec's own bolded declarations
(`- **REQ-12**: …`), so a project that does not use FR/TR/NFR still gets its coverage table.

**Runtime-captured values**: a scenario may produce its own values — `User remember [X] text as
{{v}}` (compared later) or a capability binding (`@query:`/`@api:`/`@dataFactory:`). These never
exist in test-data, so Gate D does not ask for them: the capture step renders as an instruction
(`Note the X text for later (as "v")`) and later references read `the captured v`.

**Dynamic vars**: `{{$timestamp}}`, `{{$uuid}}`, `{{$random:min:max}}`, `{{$date}}`, `{{$datetime}}`
are resolved by TestDataLoader at execution time — including when nested inside a test-data value
(`seed: "Summary intro {{$timestamp}}"`). They have no static value to print, so the report names
them instead: `<unique timestamp>`, `<unique id>`, `<random 1-100>`, `<today's date>`,
`<current date-time>`, and `{{$date:+1d}}` reads `<today's date + 1 day>`. An unknown `{{$foo}}` (or an
argument the runtime does not support, e.g. `{{$date:tz=…}}`) is left raw on purpose — it is a typo and Gate D says so.

**Data fidelity**: invisible test data is made visible, never normalized — `''` → `(empty)`,
whitespace-only → `(5 spaces)`, padded → `" value "` quoted verbatim. A trim/collapse here would
silently break the whitespace tests it describes.

**Authoring guidance the matrix rewards** (create-test side): payload/provider matrices (SQLi
payload lists, OAuth provider sets) belong in `@cases` datasets so each case is an atomic,
independently-reportable variant; keep dataset `case:` labels short and stable (`CHK-EMAIL-I1`),
with descriptions in other columns — the label is part of the variant's identity.

---

## Legacy mode (--legacy / --full)

## Architecture

```
User → /sungen:delivery [name...]
       │
       ▼
  sungen delivery CLI  (deterministic — no AI tokens)
  ├─ Scope detection (no-arg = all screens + flows)
  ├─ Auto-detect: qa/flows/<name>/ → flow, qa/screens/<name>/ → screen
  ├─ Pre-flight source checks per target
  ├─ Parse .feature (metadata)
  ├─ Parse .spec.ts (resolved Playwright code — source of truth)
  ├─ Parse test-data.yaml (resolve {{vars}})
  ├─ Parse test-results/results.json (match test titles)
  ├─ Merge sources + generate CSV rows
  └─ Write qa/deliverables/<name>-testcases.csv
```

Source modules: `src/exporters/*.ts`

---

## Required sources (CLI pre-flight checks these)

| # | Source | Screen path | Flow path | Created by |
|---|--------|-------------|-----------|------------|
| 1 | Feature file | `qa/screens/<name>/features/<name>.feature` | `qa/flows/<name>/features/<name>.feature` | `add-screen`/`add-flow` + `create-test` |
| 2 | Test data | `qa/screens/<name>/test-data/<name>.yaml` | `qa/flows/<name>/test-data/<name>.yaml` | `create-test` |
| 3 | Selectors | `qa/screens/<name>/selectors/<name>.yaml` | `qa/flows/<name>/selectors/<name>.yaml` | `run-test` |
| 4 | Compiled spec | `specs/generated/<name>/<name>.spec.ts` | `specs/generated/flows/<name>/<name>.spec.ts` | `sungen generate` |
| 5 | Test results | `specs/generated/<name>/<name>-test-result.json` or `test-results/results.json` | `specs/generated/flows/<name>/<name>-test-result.json` or global fallback | `run-test` |

**Sources 1-2 are blocking** (they are what every export row is built from) — CLI aborts if either is missing.
**Sources 3-5 are optional** — when selectors/spec/results are absent, delivery still exports a **pre-run overview**: content columns (Steps · Expected results · Test Data · Precondition) are complete from the feature + test-data, and every executable row shows **Pending**. This is the intended path for QA to review the test-case list right after `create-test`, before the first `run-test`. Once `.spec.ts`/results exist they are picked up automatically — no flag needed.

The CLI reads the **per-target result file first** (co-located with `.spec.ts`), then falls back to the global `test-results/results.json`. Per-target is preferred because the global file gets OVERWRITTEN each time Playwright runs, losing results from earlier targets.

---

## Column mapping (handled by CLI)

| CSV Column | Source |
|------------|--------|
| TC ID | Generated, namespaced per screen/flow: `<SCREEN_UPPER>-<CAT>-<NNN>` (e.g. `VP-SEC-001` on screen `login` → `LOGIN-SEC-001`). The namespace makes it globally unique — the stable key the dashboard tracks each test case by. |
| Category 1 | Scenario name with VP prefix stripped |
| Category 2 | VP group: `VP-SEC`→Accessing, `VP-UI`→GUI, `VP-VAL`/`VP-LOGIC`→Function |
| Category 3 | Feature name (first line of `.feature`) |
| Category 4 | Screen name |
| Pre-condition | Auth tag → "Logged in as X" / "Not authenticated" + Given steps (natural language) |
| Test Data | `{{vars}}` from scenario resolved via test-data.yaml → `key: value; key2: value2`. SECRET refs are printed **verbatim** as `${QA_*}` — the exporter never resolves them, so credentials never enter CSV/XLSX/dashboard |
| Steps | `.spec.ts` code comments for interactions (numbered) |
| Expected results | `.spec.ts` `expect(...)` comments (numbered) |
| Priority | Tag: `@high`/`@normal`/`@low` (default: Normal) |
| Testcase type | `@manual` → Manual, else Auto. Not compiled → "Not compiled" |
| Test Result | results.json status: passed→Passed, failed/timedOut→Failed, skipped→N/A, else Pending |
| Executed Date | results.json startTime formatted as `dd/mm/yyyy` |
| Test Executor | `git config user.name` |
| Test Environment | `playwright.config.ts` baseURL + project name |
| Note | Error message + trace path (for failed tests) |

**Runtime-secret redaction (all cells, all unit kinds):** a failed test's error message carries the RESOLVED `${QA_*}` credential (runtime substituted it before asserting), and cell content can too. The exporter builds a per-unit denylist — env vars referenced as `${QA_*}` in the unit's test data whose NAME looks sensitive (`QA_ADMIN_PASSWORD` yes; `QA_BASE_URL`, `QA_ADMIN_EMAIL` no) — resolving values from shell/CI env > `.env.qa.<SUNGEN_ENV>` > `.env.qa`, and erases every occurrence (`***`) from Note, dashboard errorMessage, Steps, Expected results and Test Data. Value-based only: fake demo values (`password123`), error-message strings (`Password is required`) and element names (`[Password]`) stay readable. If a sensitive ref can't be resolved while the run has failures, delivery prints a "redaction coverage incomplete" warning — set the variable in the env before exporting. `sungen delivery` also runs the same secret scan as `generate` and warns if test-data holds a literal token-shaped value.

---

## XLSX sheets — Overview + Auto / Manual split

The `.xlsx` leads with an **`Overview`** roll-up sheet, then splits the test cases into detail sheets so QA manages the sets separately:
- **`Overview`** — first sheet. Same masthead (Sun\* logo, `<SCREEN> TESTCASE` title, No/Version/Issue-Date, ISO band) as the others, then a summary table: one row per following sheet with `# · Sheet name · Passed · Failed · Pending · N/A · Remain · Total`, plus a grand-total row. `Remain = Total − Passed − N/A` (i.e. Failed + Pending), reconciling with each detail sheet's own summary band.
- **`Auto`** — automatable test cases (`Auto` + `Not compiled`).
- **`Manual`** — `@manual` test cases (always present, header-only when there are none).

Multi-locale: `Overview` + one **`<LOCALE> Auto`** sheet per locale + a single shared **`Manual`** sheet (manual TCs are locale-invariant). The **CSV stays one file with every row** — the `Testcase type` column distinguishes Auto vs Manual. The Overview lists every detail sheet, so each locale's counts appear on its own row.

**Which result files become locale sheets — locales are DECLARED, never guessed.** A result-file suffix (`<name>-test-result.<X>.json`) is a locale ONLY when `X` is listed in `qa/capabilities.yaml` `locales:` (written by `/sungen:locale`), or — when delivering with `--env <E>` / `SUNGEN_ENV=<E>` — when `X = <E>-<locale>`. Every delivery mixes results of ONE environment:
- `sungen delivery login` → base results + bare declared locales (`ja`); `dev`/`stg` suffixes are IGNORED with a hint (`→ sungen delivery login --env dev`)
- `sungen delivery login --env dev` → `dev` results as base + `dev-ja` sheets; everything else ignored
- `--env ja` (SUNGEN_ENV=<locale> convention) → that locale's run IS the base, labeled JA
The base sheet label comes from `base_locale:` (default **EN**). Undeclared env suffixes NEVER fabricate locale sheets — a project without `locales:` always gets one clean `Auto` sheet. Mobile per-OS suffixes (`android`, `ios.ja`, …) are exempt and keep the per-OS files; their Test Data column merges the after-OS tail overlay (`ios.ja` → `<name>.ja.yaml`), matching what the runtime merged.

---

## API delivery — extra worksheet

For **api-kind units** (`qa/api/<area>/`), the `.xlsx` gains a final worksheet **`API detail`** (appended after Overview/Auto/Manual). The main BM-2-901-13 testcase-sheet layout is unchanged. The CSV is unchanged (16-column, no extra sheet).

### Required sources (API detail sheet only)

| Source | Path | Created by |
|--------|------|------------|
| Endpoint catalog | `qa/api/<area>/api/apis.yaml` | `sungen add --api` or `sungen api import` |
| Scenario annotations | `qa/api/<area>/features/<feature>.feature` | `create-test` |

### API detail column mapping

| Column | Source |
|--------|--------|
| Endpoint | `path` from `apis.yaml` catalog entry |
| Method | `method` from catalog entry (uppercased) |
| Auth / Datasource | catalog `datasource` + any `@auth:<role>` tag from scenarios calling this endpoint |
| Request shape | catalog `body` + `params` fields composed as `body: {…}; params: [a, b]` |
| Expected-status matrix | `@cases:<dataset>` label for data-driven scenarios; catalog `expect.status` as fallback |
| Flow steps | Ordered `@api:<name>` call chain from multi-call scenarios (e.g. `register → count_users`) |
| Concurrency invariant | `@concurrent:<N>` + `@query:<oracle>` from concurrent scenarios (e.g. `ok_count=2; @query user_count`) |

**Sources are catalog + annotations only** — Field Metadata (FM) is not required for this sheet.

---

## Excluded from CSV

- `@steps:<name>` **base** scenarios — these are setup-only, inlined into `@extend:...` scenarios at compile time
- Default scaffold `Sample scenario for <screen>` — not a real test

---

## CLI command reference

```bash
# Export all screens and flows
sungen delivery

# Export specific targets (auto-detects screen vs flow)
sungen delivery kudos awards nomination-flow

# Skip pre-flight (CI only)
sungen delivery --skip-preflight

# Skip targets with blocking misses
sungen delivery --continue-on-missing
```

Output: `qa/deliverables/<name>-testcases.csv` (UTF-8 with BOM)
