---
name: run-test
description: 'Generate selectors + auth state (Playwright MCP for web / Appium MCP for mobile), compile, and run tests — auto-fixes selectors on failure'
argument-hint: "[screen-name] [--ids <ID,…>] [--env <locale>] [--ios|--android|--both]"
allowed-tools: Read, Grep, Bash, Glob, Edit, Write, AskUserQuestion, mcp__playwright__browser_navigate, mcp__playwright__browser_snapshot, mcp__playwright__browser_take_screenshot, mcp__playwright__browser_wait_for, mcp__playwright__browser_evaluate, mcp__playwright__browser_run_code, mcp__playwright__browser_storage_state, mcp__playwright__browser_set_storage_state, mcp__appium-mcp__select_device, mcp__appium-mcp__appium_session_management, mcp__appium-mcp__generate_locators, mcp__appium-mcp__appium_get_page_source, mcp__appium-mcp__appium_find_element, mcp__appium-mcp__appium_gesture, mcp__appium-mcp__appium_screenshot, mcp__appium-mcp__appium_set_value
---

## ⛔ HARD RULE — the transition INTO this run is a tool call, never prose

A QA field report: the user picked "Run test" from create-test's hand-back, and the session
answered with a BRIEFING — missing selector, expected reds, "run under Node 22" — written as
"Before you do…", then stopped. Correct facts, wrong role: those are conditions YOU handle
inside the run, not reasons to stop and hand the work back.

- The first thing this command produces is a **tool call** (platform detection, preflight,
  compile — whatever comes first). Never open with a plan and end the turn.
- A **missing selector** goes to the selector-generation/fix step — that is what this command
  is FOR.
- **Expected-red scenarios** (@known-defect asserting a live defect) stay red; note them in
  the results summary, never "fix" them and never stop for them.
- **Runtime selection is yours**: check `node -v` first. If the major version is ≥ 23 and
  Playwright browser runs are known-broken on it, select Node 22 yourself when available —
  `export PATH="$HOME/.nvm/versions/node/$(ls $HOME/.nvm/versions/node | grep '^v22' | tail -1)/bin:$PATH"`
  — and say so in one line. Only if NO compatible Node exists do you stop, with the exact
  install command as the hand-back.
- Ending this run follows the same law as create-test: the LAST action is the next-step
  hand-back (the AskUserQuestion in "After showing results"), no matter how the run went.
  **Ask `sungen next <unit>` rather than deciding from memory** — it derives the answer from what
  the project now holds, so the hand-back stays correct even when the run ended somewhere
  unexpected (a failed install, a driver that does not support this unit kind, an aborted repair
  loop). Every `sungen` command also prints `Next: sungen next …` as its own last line. A step
  that turns out to be impossible is still a hand-back: say what blocked it, run
  `sungen next <unit>`, and offer what remains.

---

## Role

You are a **Senior Developer**.
- **Web screens/flows** → use `sungen-selector-fix`, `sungen-selector-keys`, and `sungen-error-mapping` skills (Playwright MCP + `npx playwright test`).
- **Mobile screens** (`platform: mobile` in `qa/capabilities.yaml`) → use `sungen-selector-fix-mobile`, `sungen-capture-mobile`, `sungen-mobile-gestures`, `sungen-selector-keys` (Appium MCP + `npm run test:mobile`).

## Platform detection (do this FIRST)

Read `qa/capabilities.yaml` and check the `platform` field.

- If `platform: mobile` → switch to the **mobile path** (below).
- If `platform: web` or field absent → follow the **web path** (all steps below, unchanged).

### Mobile path

When `platform: mobile`:

- **Selectors**: use **Appium MCP** (`select_device` → `appium_session_management(create)` → `generate_locators`), per the `sungen-selector-fix-mobile` skill. Also load `sungen-capture-mobile` and `sungen-mobile-gestures`.
- **Auth**: web Phase 0.5 (`storageState`) does not exist on mobile. When the feature has `@auth:<role>`, check `specs/.auth-mobile/<role>.json`; if missing, run the guided auth-contract builder (Phase 0.5 in `sungen-selector-fix-mobile`) BEFORE Phase 1.
- **Compile**: `[ -x ./bin/sungen.js ] && ./bin/sungen.js generate --screen <name> --framework appium || npx sungen generate --screen <name> --framework appium`
- **OS selection**: parse the target OS from `$ARGUMENTS` — `--ios`, `--android`, or `--both`
  (alias: `--platform <android|ios|both>`) → set `MOBILE_PLATFORM=<value>`. Default to `android` when no flag is given.
- **Run**: WebdriverIO, not Playwright. Scope one spec with `SUNGEN_SPECS`; prefix the parsed `MOBILE_PLATFORM`:
  ```bash
  # Android (default — no flag, or --android)
  SUNGEN_SPECS='./specs/generated/<name>/<name>.spec.ts' npm run test:mobile
  # iOS only (--ios)
  SUNGEN_SPECS='./specs/generated/<name>/<name>.spec.ts' MOBILE_PLATFORM=ios npm run test:mobile
  # both OSes (--both)
  SUNGEN_SPECS='./specs/generated/<name>/<name>.spec.ts' MOBILE_PLATFORM=both npm run test:mobile
  ```
  `wdio.conf.ts` auto-selects `@wdio/globals` specs and auto-starts Appium. iOS needs a booted Simulator and `IOS_BUNDLE_ID`/`IOS_UDID` env vars.
- **Results**: the wdio reporter writes `<name>-test-result.<android|ios>[.<env>].json` — one file per capability, so a both-OS run keeps both outcomes. Then run `npx sungen results sanitize` — wdio error stacks quote absolute machine paths, and the result file is committed next to the spec.
- **Skip** the "Playwright command guidelines" section below — it applies to web only.
- The phased waves (smoke → priority → full → regression), attempt budgets, and next-steps mirror the web flow — follow the `sungen-selector-fix-mobile` skill for the mobile equivalents.

Stop here for mobile — do not continue to the web path.

> ⛔ **Source of truth — the live page/device is NOT the oracle; `.feature`/`test-data`/`spec.md` are.** Auto-fix is for **selector-resolution** failures (wrong locator → fix `selectors.yaml`). An **assertion-value** failure where the app contradicts the spec is a **CANDIDATE BUG → report it, let it FAIL** — never loosen the rule, weaken the assertion, edit the expected value/`.feature`, or hand-edit the generated `.spec.ts` to make it pass. See `sungen-error-mapping` § "Source of truth".

> ⛔ **The `.feature` Gherkin is QA-approved and FROZEN.** During auto-fix you may write
> **only** `selectors.yaml`, `test-data.yaml` (and other `*.yaml` data), plus the **one narrow
> `.feature` edit** allowed: the **element-type token** on a `[Reference]` (e.g. `field`→`textbox`,
> `button`→`link` — selector resolution, not test logic). A second, equally narrow exception:
> adding **`@known-defect:DEF-NNN`** to a scenario's tag line once its defect record exists (step 8)
> — that tag only, the tag line only. You must **NOT** change the Gherkin's
> content or logic: no rewriting/adding/removing steps, no `wait for` steps, no changed expected
> values, no weakened assertions. A failure that would need any of those is a **CANDIDATE BUG /
> QA finding → report it, let it FAIL** (it may be a real bug). The live page is NOT the oracle;
> never hand-edit the generated `.spec.ts` either. See `sungen-error-mapping` § "HARD RULE".
> (A `password > 8` test that fails on 6 chars is a bug to report, not a `>= 6` edit.)
>
> **Blocked by missing seed data / capability** (the run cannot create the state the scenario
> needs)? Keep every Given/When/Then; add only a tag — `@requires:<cap>` when a driver exists,
> else `@manual:M1` with a `# MANUAL (<date>, run-test): <reason>` comment inside the scenario —
> and list each downgrade in the run summary for QA. Never replace the assertion with a presence
> placeholder and park the oracle in a comment (`SG-W008`). See `sungen-error-mapping` § "Fails
> because the environment lacks the DATA or CAPABILITY".

## Parameters

Parse from `$ARGUMENTS`:
- **name** — screen or flow name. If missing, ask the user.
- **`--env <locale>`** — optional. Sets `SUNGEN_ENV=<locale>` for the test run so the runtime test-data resolver merges `<name>.<locale>.yaml` over the base, and `playwright.config.ts` writes results to `<name>-test-result.<locale>.json`. Accept `--locale <locale>` as an alias. The value can also be a plain ENVIRONMENT name (`dev`, `uat`, `stg`): the same overlay applies to test-data AND `.env.qa.<env>` overlays `.env.qa` (secrets, API/DB datasource URLs); when `SUNGEN_BASE_URL` is declared there, the web baseURL switches too — no config edit, no recompile.
- **`--change-only`** — re-run only the scenarios a selectors / test-data / feature edit obliges, then merge
  the result over the previous run's file instead of replacing it. Passing it also **skips the scope gate**:
  the flag IS the answer, so asking again would be noise. Without the flag the gate asks — but only when the
  selection is narrower than the whole unit (see the table). Either way a subset run must follow the merge
  flow below — web writes the subset to a temp report, mobile backs the fixed-name file up BEFORE the run.
- **`--ids <ID,…>`** — run exactly these test-case IDs (`VP-UI-001,VP-UI-002`) and merge the result
  over the previous run's file — see the `--ids` section below. Skips the scope gate, like `--change-only`.
- **`--ios`**, **`--android`**, **`--both`** (mobile only) — optional. Sets `MOBILE_PLATFORM` for the test run. Default: `android` if not specified. Parsed into `MOBILE_PLATFORM=<value>` and used in WebdriverIO test runs.

If `--env` is passed but no value follows, ask the user which locale to use.

**`--env <locale>` pre-flight**: when `--env` is passed AND the matching overlay file doesn't exist yet (`test-data/<feature>.<locale>.yaml` missing), tests will silently fall back to base locale values — the run will execute but won't actually exercise the locale. Before kicking off Phase 0.5, check:

```bash
ls qa/<screens|flows>/<name>/test-data/*.<locale>.yaml 2>/dev/null | wc -l
```

If the count is 0 → use `AskUserQuestion` to offer:
- **Run `/sungen:locale <name> <locale>` first** (Recommended) — bootstrap the overlay before running tests
- **Continue anyway** — run with empty overlay (tests will assert base-locale text, will likely fail on a real locale page)
- **Cancel**

Skip this pre-flight when `--env` matches the base locale (no overlay needed in that case).

**Auto-detect context**: check if `qa/api/<name>/` or `qa/api/flows/<name>/` exists → **API unit mode** (below). Else if `qa/flows/<name>/` → flow mode (base path: `qa/flows/<name>/`). Else `qa/screens/<name>/` → screen mode (base path: `qa/screens/<name>/`).

## API unit mode (driver-api) — no selectors

If the unit is **api-first**, skip every selector/capture phase (an API test has no DOM). Instead:

1. **Resolve the datasource** — ensure the `kind: api` datasource's `base_url` + auth are wired in `qa/datasources.yaml` + `.env.qa` (the `${X_URL}` key from `sungen api init`). A `production` datasource is refused unless `SUNGEN_ALLOW_PROD=1`.
2. **Compile**: `[ -x ./bin/sungen.js ] && ./bin/sungen.js generate --area <name> || npx sungen generate --area <name>` → `specs/generated/api/<name>/`.
3. **Run**: `npx playwright test specs/generated/api/<name>/<name>.spec.ts` (per-spec JSON results, as below).
4. **Auto-fix** (no selectors — the failure classes differ): use `sungen-error-mapping`.
   - **401/403** → wire `@hybrid` + `@auth:<role>` (reuse the UI session) or the catalog `Bearer :token` header; suggest `sungen makeauth <role>`.
   - **datasource/base_url unresolved** → set the `${X_URL}` key in `.env.qa`.
   - **missing/empty bound param** → trace `{{var}}` to test-data or a prior `@api` response; fill it.
   - **`expect.status` mismatch** → reconcile against `apis.yaml`/spec (the catalog is the oracle); **never hand-edit the generated spec** (re-`generate --area` instead).
   - **400 "parameter missing" / body ignored** → the endpoint wants a form body, not JSON → set `encoding: form` (or `multipart`) on the catalog entry, re-`generate --area`. Don't mark the scenario `@manual`.
   - **flaky** → enforce self-cleaning flows, per-row isolation (`@cases`), `@concurrent` caps.
5. **Integrity + trace** — `sungen script-check --area <name>` (verify the spec is a 1:1 of the Gherkin; on DRIFT re-`generate --area`, never hand-edit) and `sungen trace --area <name>` (process map + HUMAN-LOOP FOCUS). Then report + offer next steps.

## Scope gate — ask BEFORE running, only when there is a real choice

Run this immediately after platform detection, before Pre-run. It decides WHAT to run; skipping it
is how a QA ends up re-running 100 cases to re-check 3, or worse, re-running 3 and losing the 97.

```bash
[ -x ./bin/sungen.js ] && ./bin/sungen.js changed --screen <name> --json || npx sungen changed --screen <name> --json
```

Read `summary.selected`, `summary.total`, `all`, and `reasons`, then follow this table exactly:

| Situation | What to do |
|---|---|
| `--change-only` was passed | **Do NOT ask.** Run the changed set, print the list + reasons for the record, follow the merge flow below. |
| `--ids` was passed | **Do NOT ask, and do not run `sungen changed` at all.** The user named the cases — follow the `--ids` section below. |
| `all: true` | **Do NOT ask** — there is nothing to choose. Print the reason (`no previous run recorded`, `the Background changed`, `project config changed`, `git reports …`) and run the FULL suite. |
| `selected == 0` | **Do NOT ask.** Say plainly that nothing changed since the last recorded run, and use `AskUserQuestion` to offer: re-run the full suite anyway / stop here. |
| `0 < selected < total` | **ASK** — this is the only case with two honest answers. |

**Close EVERY accepted run by recording the baseline** — full runs included, not just subsets:

```bash
[ -x ./bin/sungen.js ] && ./bin/sungen.js changed --screen <name> --record || npx sungen changed --screen <name> --record
```

Without this the first run leaves no baseline, so the next invocation reports `no previous run
recorded` and runs everything again — the narrowing never starts. Record it after the run whose
results you accept (a full run, or a subset once merged), and NOT after a run you are about to
redo: leaving the baseline where it is keeps the same scenarios selected on the next attempt.

**Never record after an `--ids` run.** The user's IDs need not cover what an edit changed;
recording would mark every changed scenario as re-run, and the next `--change-only` would skip the
ones this run never touched.

When you ask, first PRINT the selection so the choice is informed — one line per scenario with its
reason, exactly as `sungen changed` renders it — then `AskUserQuestion` with two options:

- **`Chạy N case đã đổi` (Recommended)** — description: names the N scenarios (truncate past 3 with
  "…và K case khác") and states that the previous results are preserved by the merge, so the report
  stays complete.
- **`Chạy full M case`** — description: slower, and states WHEN it is the right call — before a
  delivery/hand-off, or when the APP under test changed.

That second reason matters and must be said: the selection fingerprints **the QA's files only**
(feature, selectors, test-data, project config). It cannot see the app under test. If the build
changed, nothing in `qa/` moved, `changed` reports "nothing changed", and only a full run tells you
anything. Never present the changed set as proof the rest still passes — it is proof only that the
rest was not re-examined.

Do not add a third option. "Show me more detail" is not a choice — the detail is printed above the
question already.

## Pre-run (phased — per `sungen-selector-fix` skill)

1. Verify `<base>/<name>/` has `.feature` + `test-data.yaml`. **If the feature has `@query` steps**, check the resolved datasource `engine` in `qa/datasources.yaml` — unsupported engine → follow the `sungen-gherkin-syntax` skill § "Unsupported DB engine — fallback" before compiling (do not attempt a direct connect).
1.5. **Data Factory pre-check (before running)** — Data Factory is bundled with core, so whenever a field-map exists (`qa/data-factory/<name>.fields.yaml`), confirm the test-data is standardized and covers each field before you run: `[ -x ./bin/sungen.js ] && ./bin/sungen.js data lint --screen <name> || npx sungen data lint --screen <name>`. If it reports coverage gaps (missing boundary/invalid, unresolved values, unmapped error codes) or the field-map is newer than `test-data/<name>.yaml`, refresh via `/sungen:create-data-test <name>` (or `sungen data gen --screen <name>`) — then compile. Blind spots (a `type` not in the catalog) are for the QA to answer, not to invent. No field-map / driver disabled → skip this step.
2. **Phase 0 — Selector Pre-gen**: if `selectors.yaml` is missing/empty or doesn't cover the feature file's `[Reference]`s, apply the following decision tree before running Phase 0 from `sungen-selector-fix`:

   ```
   Phase 0 — Selector Generation decision tree

   Live page reachable? (URL provided and loads without error)
     YES → existing flow: browser_navigate → wait for page to fully load (no spinner/skeleton/empty table) →
            one browser_snapshot → cross-verify every [Reference] label vs snapshot name →
            generate selectors.yaml (verified entries; explicit YAML for any label≠DOM-name mismatch)
     NO  → spec_figma.md exists in requirements/?
             YES → provisional flow (sungen-capture mode figma-pat + sungen-selector-fix skills):
                   1. Read filtered Figma node data from spec_figma.md (## Components + ## Text Inventory)
                   2. Apply selector priority from sungen-selector-fix § Step 3 (testid > role+name > label > placeholder > text > locator CSS last)
                   3. Write selectors.yaml — every provisional entry gets this comment on the line above:
                          # @needs-live-verify source=figma node_id=<id>
                   4. Compile: Screen: sungen generate --screen <name>. Flow: sungen generate --flow <name> — must succeed
                   5. Phase 1 smoke check runs; tests using unverified selectors may fail
                      → auto-fix triggers on next run-test invocation when a live page is available
             NO  → hard stop: print the following message and stop:
                   "Cannot generate selectors: no live page URL and no spec_figma.md found.
                    Options:
                    • Provide the live URL so Playwright MCP can snapshot the page, OR
                    • Run: sungen add --screen <name> --figma <figma-url>  to generate spec_figma.md first"
   ```

   **Auto-fix on subsequent runs**: when `run-test` is invoked again with a reachable live page, Phase 0 compares the DOM snapshot against existing `selectors.yaml` entries. Entries tagged `# @needs-live-verify` are treated as candidates — if the actual selector differs, the entry is replaced and the comment removed (entry becomes verified). Entries that already match are also promoted to verified (comment removed).

   **`@needs-live-verify` comment format** (one comment line, directly above the YAML key):
   ```yaml
   # @needs-live-verify source=figma node_id=<figma-node-id>
   submit-button:
     type: role
     value: button
     name: "Submit"
   ```
3. **Phase 0.5 — Auth Persistence**: if the feature has `@auth:<role>` tags and `specs/.auth/<role>.json` is missing/expired, run Phase 0.5 from `sungen-selector-fix` — user logs in manually in MCP browser → `browser_storage_state` → `specs/.auth/<role>.json`. Offer `sungen makeauth <role>` as CLI fallback only if `browser_storage_state` isn't available in this MCP version.
4. Compile via local-first dispatcher so the sungen monorepo's unpublished selector-resolver features (i18n `{{var}}` interpolation, namespaced selector lookup) are picked up:
   - **Screen**: `[ -x ./bin/sungen.js ] && ./bin/sungen.js generate --screen <name> || npx sungen generate --screen <name>`
   - **Flow**: `[ -x ./bin/sungen.js ] && ./bin/sungen.js generate --flow <name> || npx sungen generate --flow <name>`

   Default: runtime data loading from YAML. Use `--inline-data` only if user requests compile-time hardcoded values.

## Run & Fix (phased — per `sungen-selector-fix` skill)

5. **Phase 1 — Smoke Check**: Run first 5 `@high` scenarios only. If failures → diagnose, fix fundamentals (page selector, auth, base @steps), re-run. Max 2 attempts. If still broken → ask user.
6. **Phase 2 — Priority Wave**: Run all `@high` scenarios. Fix only failures from this wave. Max 2 attempts. Shared selectors fixed here cascade to later phases.
7. **Phase 3 — Full Run**: Run all tests. Fix only **new** failures (elements unique to `@normal`/`@low`). Max 1 attempt. Don't loop on low-priority failures.
8. **Phase 4 — Regression**: One final full run. No more fix loops. Then run `sungen triage --screen <name>` and render its table as the results summary: every red or flaky scenario already sits in one class (`product-candidate`, `unclassified`, `test-defect`, `environment`, `blocked-capability`, `known-defect`, `flaky`) with the rule and one evidence line. You may move an `unclassified` row into a class only with a written evidence line of your own. You may never turn a `product-candidate` into a `test-defect` by editing the `.feature` or the expected value — the Gherkin is frozen; report it as a candidate bug. For each `product-candidate` you judge to be a product bug, run `sungen defect draft --screen <name> --scenario "<title>"`, fill the record's Summary, check its Expected against `spec.md`, then add `@known-defect:DEF-NNN` to that scenario's tag line (the tag-only exception above). The severity in the draft is `proposed` — confirm it with QA (`AskUserQuestion`) before the hand-back and set `severity_source: qa`. A row you judge is not a product bug stays in the summary with your reason, and gets no record. A `bare-timeout` row (`unclassified`) is never re-run inside this command — Phase 4 was the final run; offer the re-run in Next steps.
9. **Phase 8 — Serial-cascade risk check** (#414): If any scenario has many steps, a skipped test may cause downstream steps to be incorrectly skipped due to the serial-cascade sensor detecting false "not reached" conditions. When you see "test skipped" messages during Phase 3–4 runs, check if the skipped scenario is a **blockers-exist** dependency in the test ledger (`sungen ledger query --screen <name>`). If the scenario had high step count and cascade fired, offer: *"Scenarios with deep steps can trigger serial-cascade skipping. Consider parallelizing this spec with `@concurrent` tags or breaking into smaller scenarios."* Let the user decide whether to refactor (do not auto-apply parallelization).
9. **Integrity check & trace (always run after the final run).**
   - `sungen script-check --screen <name>` — verify the generated spec is a **1:1** of the Gherkin (every non-@manual scenario ↔ one `test()`, no drift). If it reports **DRIFT** (spec hand-edited or stale), re-run `sungen generate --screen <name>` so the spec matches the feature, then re-run — **never hand-edit the generated spec** (auto-fix must edit `selectors.yaml`, not the `.spec.ts`).
   - `sungen ledger record --screen <name> --step run --ms <elapsed>` (record this run), then `sungen trace --screen <name>` — show the process map + bottlenecks + **HUMAN-LOOP FOCUS** (the @manual scenarios the QA must verify) to the user.
10. **Capability-pending offer (consent-gated).** If `sungen audit --screen <name>` reports `AUTOMATION-READY-PENDING` (or the run shows `@requires:<cap>` tests skipped "requires …"), these are **automation-ready** scenarios waiting on an opt-in driver. Use `AskUserQuestion` to offer: *"N scenario(s) are automation-ready — enable `<cap>` to run them? (`sungen capability add <cap>`)"*. **Only on the user's yes** run `sungen capability add <cap>` then re-run those specs; on no, leave them skipped (they are NOT failures and NOT manual). **Never auto-install.**

## Playwright command guidelines

**Multi-feature screens** — `sungen generate --screen <name>` produces one `<basename>.spec.ts` per `.feature` file (e.g. `home.spec.ts` + `home-modal.spec.ts`). You must **invoke playwright once per spec file** so each gets its own JSON result that `sungen delivery` can pick up. Do NOT run a single command with the directory as the test argument — that bundles everything into one results file that delivery can't disambiguate.

**Per-spec JSON results** — each invocation writes its JSON report to a path matching the spec basename. When `--env <locale>` was parsed from `$ARGUMENTS`, prepend `SUNGEN_ENV=<locale>` — `playwright.config.ts` auto-inserts `.<locale>` before `.json` in the output path:

```bash
# ✅ Screen with 1 feature
PLAYWRIGHT_JSON_OUTPUT_NAME=specs/generated/<name>/<name>-test-result.json \
  npx playwright test specs/generated/<name>/<name>.spec.ts

# ✅ Screen with multiple features — loop in shell:
for spec in specs/generated/<name>/*.spec.ts; do
  base=$(basename "$spec" .spec.ts)
  PLAYWRIGHT_JSON_OUTPUT_NAME="specs/generated/<name>/${base}-test-result.json" \
    npx playwright test "$spec"
done

# ✅ Locale 'vi' — same loop, just prepend SUNGEN_ENV=vi
for spec in specs/generated/<name>/*.spec.ts; do
  base=$(basename "$spec" .spec.ts)
  SUNGEN_ENV=vi \
  PLAYWRIGHT_JSON_OUTPUT_NAME="specs/generated/<name>/${base}-test-result.json" \
    npx playwright test "$spec"
done
# → writes <basename>-test-result.vi.json for each feature

# ✅ Flow
PLAYWRIGHT_JSON_OUTPUT_NAME=specs/generated/flows/<name>/<name>-test-result.json \
  npx playwright test specs/generated/flows/<name>/<name>.spec.ts
```

**DO NOT** pass `--reporter=...` flag — it overrides the reporters from `playwright.config.ts` and disables the JSON reporter that `sungen delivery` depends on.

**Sanitize the result file paths right after the run** — the JSON report records the operator's absolute machine paths (`/Users/<name>/…`, `C:\Users\…`) in `config`, error locations, attachments and stack traces, and the file is committed next to the spec:

```bash
npx sungen results sanitize          # every *-test-result*.json under specs/generated + .sungen/reports/*.json → project-relative paths
```

Projects created with a recent sungen (or refreshed by `sungen update`) run the same rewrite automatically through the `report-path-sanitize-reporter` in `playwright.config.ts`; this command is the safety net for every other project and is idempotent — an already-clean file is left untouched.

**Re-running a SUBSET (fix-and-retry, or a partially edited spec) when the per-target result file already exists:** the JSON reporter would OVERWRITE it and lose every result you didn't re-run. Write the subset's report to a temp file and MERGE it instead — same title wins, everything else is kept:

```bash
# re-run just the failed cases (or -g "<scenario title>") into a temp report…
PLAYWRIGHT_JSON_OUTPUT_NAME=specs/generated/<name>/subset-tmp.json \
  npx playwright test specs/generated/<name>/<name>.spec.ts --last-failed

# …then fold it into the existing per-target file (delivery reads the merged result)
sungen results merge --screen <name> specs/generated/<name>/subset-tmp.json
rm specs/generated/<name>/subset-tmp.json
```

`--screen` takes the FEATURE basename — for a multi-feature screen (`home.spec.ts` + `home-modal.spec.ts`), each feature has its own result file: merge `home-modal`'s subset with `--screen home-modal`. If the previous run had zero failures, `--last-failed` finds no tests: Playwright prints "No tests found", exits 1, and still writes a zero-test report — don't merge it; use `-g "<scenario title>"` to pick the cases instead. `--last-failed` reads the failures of the *previous run only*, so any run in between replaces the list.

For a locale run (`SUNGEN_ENV=vi`), the config appends `.vi` before `.json` to WHATEVER `PLAYWRIGHT_JSON_OUTPUT_NAME` says — the temp report lands at `subset-tmp.vi.json`; merge that file into the matching `<name>-test-result.vi.json` explicitly: `sungen results merge specs/generated/<name>/<name>-test-result.vi.json specs/generated/<name>/subset-tmp.vi.json`. A scenario RENAMED by a spec edit leaves its old-title result in the file — the merge warns and keeps it (delivery builds rows from the feature, so it never renders).

```bash
# ❌ Wrong — --reporter flag disables the config's JSON reporter
npx playwright test specs/generated/<screen>/<screen>.spec.ts --reporter=list

# ❌ Wrong — no env var → writes to default test-results/results.json
# (overwritten on every screen run, loses per-screen tracking)
npx playwright test specs/generated/<screen>/<screen>.spec.ts
```

If you want to filter scenarios, use `-g "<pattern>"` instead of a reporter override.

`sungen delivery` reads per-feature `<basename>-test-result[.env].json` files (one per feature in the screen) and writes one CSV/XLSX per feature (e.g. `home-testcases.csv` + `home-modal-testcases.csv`). When `--env <locale>` was used here, run delivery with the same locale (`/sungen:delivery <name> --env <locale>`) so it picks the matching `*-test-result.<locale>.json` files and produces `*-testcases.<locale>.csv` / `.xlsx`.

## `--change-only` — re-run just what an edit obliges, without losing the rest

A per-target result file holds the whole unit, so a subset run must never replace it: re-running 3
cases of a 100-case screen used to throw away the 97 that still passed. The subset is MERGED in
instead — on web via a temp report, on mobile via a backup taken BEFORE the run (the WDIO reporter
always writes the fixed-name file, so by the time the run finishes the old one is gone):

```bash
# 1. what does the edit oblige?  (empty output = nothing to run, do NOT fall through to a full run)
npx sungen changed --screen <name>            # human-readable, with the reason per scenario
GREP=$(npx sungen changed --screen <name> --grep)

# MOBILE — the WDIO reporter always writes the canonical per-OS file, so back it up first:
# 2. keep the previous results BEFORE the runner overwrites them
cp specs/generated/<name>/<name>-test-result.<os>.json /tmp/<name>.prev.json
# 3. run only the selected scenarios
SUNGEN_SPECS='./specs/generated/<name>/<name>.spec.ts' npm run test:mobile -- --mochaOpts.grep "$GREP"
# 4. merge the subset back over the kept results (same title → new wins, others kept)
npx sungen results merge /tmp/<name>.prev.json specs/generated/<name>/<name>-test-result.<os>.json \
  -o specs/generated/<name>/<name>-test-result.<os>.json

# WEB — Playwright writes its report to PLAYWRIGHT_JSON_OUTPUT_NAME (else test-results/results.json),
# never to the per-target file by itself: write the subset to a temp report and merge THAT in (#837).
# With --env <x>: prefix BOTH commands with SUNGEN_ENV=<x>; the config then writes subset-tmp.<x>.json.
PLAYWRIGHT_JSON_OUTPUT_NAME=specs/generated/<name>/subset-tmp.json \
  npx playwright test specs/generated/<name>/<name>.spec.ts -g "$GREP"
npx sungen results merge --screen <name> specs/generated/<name>/subset-tmp.json   # --env x → subset-tmp.x.json
rm specs/generated/<name>/subset-tmp*.json

# 5. record the new baseline — ONLY after a run you accept
npx sungen changed --screen <name> --record
```

**Selection rules** (deterministic, computed by `sungen changed`): a scenario re-runs when its own
steps/tags changed, when a selectors.yaml key it references changed, or when a test-data key it
binds changed. A `Background` edit re-runs everything, because the Background runs before every
scenario. An edit that cannot be attributed — an unparseable YAML, a file that is not a key
mapping, or no baseline yet — selects **every** scenario and says so. That bias is deliberate:
over-running costs minutes, under-running reports a scenario green that never re-ran after the
change that broke it.

**Without `--change-only`** the full run still writes the file wholesale, which is correct — a full
run IS the complete picture. Merging only matters for subsets.

## `--ids` — run a chosen list of test-case IDs

The user names the cases (`--ids VP-UI-001,VP-UI-002`). `sungen select` resolves them —
deterministic, exact ID match, grouped per feature — and refuses anything it cannot run:

```bash
npx sungen select --screen <name> --ids <ID,…>          # human-readable
npx sungen select --screen <name> --ids <ID,…> --json   # what you act on
```

**Exit 1 = do not run.** Report every entry of `unknown` (with its `didYouMean`), `notRunnable`
(manual / owned by a flow — with the reason) and `notCompiled` (→ `sungen generate` first), then
use `AskUserQuestion` to offer: run the valid IDs only / correct the list. Never fall back to a
full run, and never run with an empty grep.

Then, **for each entry of `features`** (a multi-feature screen has one spec and one result file per
feature), with `<spec>` = its `spec`, `<f>` = its `feature`, `<dir>` = the spec's directory and
`GREP` = its `grep`:

```bash
# WEB — write the subset to a temp report, then merge it into <f>'s per-target file.
# With --env <x>: prefix BOTH commands with SUNGEN_ENV=<x>; the config then writes subset-tmp.<x>.json.
PLAYWRIGHT_JSON_OUTPUT_NAME=<dir>/subset-tmp.json npx playwright test <spec> -g "$GREP"
npx sungen results merge --screen <f> <dir>/subset-tmp.json      # --env x → <dir>/subset-tmp.x.json
rm <dir>/subset-tmp*.json

# MOBILE — the reporter always writes <dir>/<f>-test-result.<android|ios>[.<env>].json: back it up first
# (with --env <x> every <f>-test-result.<os>.json below is <f>-test-result.<os>.<x>.json)
cp <dir>/<f>-test-result.<os>.json /tmp/<f>.prev.json
SUNGEN_SPECS='./<spec>' npm run test:mobile -- --mochaOpts.grep "$GREP"
npx sungen results merge /tmp/<f>.prev.json <dir>/<f>-test-result.<os>.json -o <dir>/<f>-test-result.<os>.json
```

With no previous result file (first run of the unit, or of this `--env`) there is nothing to merge
into — `results merge --screen` says so and exits 1: on web run with
`PLAYWRIGHT_JSON_OUTPUT_NAME=<dir>/<f>-test-result.json`, on mobile skip the backup and the merge.
With `--both`, back up and merge each OS's file. **Do NOT run `sungen changed --record`** (see the
scope gate). Report the per-ID outcome, plus the IDs that were refused.

## Next steps

After showing results, use `AskUserQuestion` to offer next steps:

If all tests **passed**:
- **`/sungen:create-test <screen>`** — Add more test cases (Recommended)
- **Done** — All tests passed, I'm finished

If tests **failed** (after retries):
- **`/sungen:run-test <screen>`** — Re-run after manual fixes
- **`/sungen:create-test <screen>`** — Revise test cases
- **Done for now** — I'll fix manually later

If the triage table has `bare-timeout` rows, put this option FIRST and mark it Recommended: **Re-run the N timed-out scenario(s) once** — it tells a slow environment from a hanging page (name them). Ask; never re-run them on your own: a scenario may create data or spend an OTP/rate limit on a shared environment. On a yes, re-run only those scenarios with the subset-merge flow (§ Playwright command guidelines — never overwrite the result file), then `sungen triage` again:
- green now → move the row to `flaky` (or `environment`) with that as the evidence line;
- the same timeout again → read the trace's last action: stuck after a submit or a navigation → candidate bug, handled as in Phase 4 step 8 (`sungen defect draft`, QA confirms the severity); stuck in a wait the test itself defines → `test-defect`.
