---
name: sungen-error-mapping
description: 'Error diagnosis and fix strategy — systematic debugging flow, error patterns, fix priorities. Auto-loaded during run-test.'
user-invocable: false
---

## Diagnosis Flow (before fixing anything)

**Step 1: Read the error** — extract: error type, element name, expected vs actual, file:line.

**Step 2: Read error context** — check `test-results/` for the snapshot at failure time. This shows the exact page state when the test failed — more reliable than re-navigating with MCP.

**Step 3: Compare** — match the failing selector against the snapshot. Ask:
- Does the element exist on the page?
- Does the accessible name match exactly?
- Are there multiple matches (strict mode)?
- Is the element inside an iframe or dialog?
- Is the page in the expected state (correct URL, loaded)?

Then choose the fix from the patterns below.

---

## ⛔⛔ HARD RULE — the `.feature` (Gherkin) is FROZEN during run-test

The Gherkin test cases have been **reviewed and approved by QA**. During the run-test auto-fix
loop you **MUST NOT change their content or logic**. Concretely, you may write only:

- ✅ `selectors.yaml` — locators (the normal auto-fix)
- ✅ `test-data.yaml` and other `*.yaml` data files
- ✅ **ONE narrow `.feature` edit:** the **element-type token** on a `[Reference]`
  (e.g. `[Email] field` → `[Email] textbox`, `button` → `link`). This is selector resolution,
  not test logic. NEVER touch the reference name, the step keyword, the expected value, the
  assertion, or the scenario structure.

**Forbidden in `.feature` (all of these are content/logic — report, don't edit):**
adding/removing/rewriting steps, adding `wait for` steps, changing expected values or rules,
weakening assertions, "fixing" an unknown step pattern, adding table-scope steps. If a failure
needs any of these, it is a **finding for QA** — surface it in the run summary and let the test
**FAIL**. A failing test may be a **real bug**; do not edit the QA-approved Gherkin to make it pass.

### Fails because the environment lacks the DATA or CAPABILITY (not the app, not the locator)

Sometimes the scenario is right and the app may be right, but the run cannot set up the state —
the dev seed has no tie-break pair, no locked account, no second tenant. That is **neither** a
selector fix **nor** a reason to rewrite. The only allowed `.feature` edit is a **tag** (plus a
reason comment); the Given/When/Then stay **exactly** as QA approved them:

1. The capability exists as a driver (`db`, `api`, data-factory) → tag `@requires:<cap>` (e.g.
   `@requires:db`) — it compiles to a skip stub (`AUTOMATION-READY-PENDING`) and runs, unchanged,
   once `sungen capability add <cap>` is done.
2. Otherwise → tag `@manual:M1` and put the reason as a comment **inside** the scenario (below the
   `Scenario:` line — a comment above the tags is attached to the previous scenario):
   `# MANUAL (<date>, run-test): no seeded pair with equal plan + distinct last-updated`.
3. **Never** swap the real assertion (`table match data:`, `with {{v}}`, `count is …`) for a
   presence placeholder (`see [X] table`) and move the oracle into a `# Tester verifies:` comment —
   that hybrid exports an Expected cell that says nothing about what is checked, and `sungen
   generate` flags it `SG-W008`.
4. List **every** downgrade of the run (scenario, tag added, reason) in the run summary so QA
   reviews it — a downgrade is a coverage decision, not an auto-fix.

## ⛔ Source of truth — classify EVERY failure before you "fix" it

`.feature` + `test-data.yaml` + `spec.md` are the **oracle**. The **live page is NOT** — it may be the thing that's broken. A failing test is not automatically a test to "make pass". Classify first:

- **Selector-resolution failure** (element not found / wrong locator / strict-mode / wrong element type) → the test looked in the wrong place. **Fix the locator in `selectors.yaml`** (re-snapshot, copy the exact accessible name). Legit auto-fix.
- **Assertion-value failure** (element FOUND, but observed value ≠ expected) → STOP and ask the decisive question: ***does `spec.md` PIN this exact value?***
  - **Spec PINS the value and live contradicts it** (spec mandates X, app shows Y) → **CANDIDATE BUG**. **Report it** (let the test FAIL). **NEVER** change the expected, loosen the rule, weaken the assertion (`toHaveText`→`toContainText`), shrink a boundary (`>8`→`>=6`), or edit `.feature`/`.spec.ts` to pass.
  - **Spec does NOT pin the exact string, and your expected was your own transcription/guess** (whitespace, casing, punctuation, or a label the spec only *describes* narratively) while the live value is the legitimate, spec-consistent truth → **accuracy fix (allowed):** set `test-data.yaml`/`.feature` to the **real** value — *not* weakening, the claim is unchanged. (e.g. hero `<h1>` renders `AutomationExercise`; spec says "the Automation Exercise logo" but pins no string → fix `hero_title` to `"AutomationExercise"`, don't leave it failing.)
  - Expected is wrong **relative to a value `spec.md` DOES state** (typo vs the spec) → fix to the **spec** value, never the live page.

> **Accuracy fix vs weakening — the line:** an *accuracy fix* matches a spec-consistent reality the spec didn't pin (cosmetic, meaning preserved). *Weakening* changes the **oracle** to dodge a real discrepancy. **If the spec pins it, or you're unsure → treat as a bug and leave it failing.**

> **Cardinal sin (do NOT do this):** a `password > 8 chars` rule fails on a 6-char input → "fix" it to `>= 6` so the test passes. The logic is now meaningless. A failing assertion is a **finding**, not a chore.

**`sungen triage` makes the first pass for you.** Its classes are this section's split: `test-defect` = selector-resolution / test-data failure (auto-fix scope), `product-candidate` = assertion-value failure with an Expected and a Received (apply the spec question above), `environment` = the run itself (fix the setup, not the test), `unclassified` = yours to judge — an assertion on an element that never appeared lands here, because a wrong selector and a missing message look the same.

**Auto-fix loop scope:** the run-test auto-fix loop engages ONLY on **selector-resolution** failures. On an assertion-value failure where the app contradicts the spec → **HALT and report**, do not loop it into passing.

**Never hand-edit the generated `.spec.ts`** (e.g. inserting `page.evaluate`/`fetch` to bypass a broken control). `sungen script-check` regenerates the spec from `.feature` and flags any edit as DRIFT — regenerate, don't patch.

---

## Fix Priority (try in order)

1. **Auth issue** — page redirected to login? Fix auth first, everything else is noise
2. **Element not found** — wrong name/type/value in selectors.yaml. Re-snapshot, copy exact name
3. **Multiple matches** — add `nth`, `exact: true`, or `scope` to narrow down
4. **Wrong assertion** — `toHaveText` vs `toHaveValue` mismatch, wrong expected data
5. **Timing** — SPA not loaded, async content. Prefer tuning waits/caching in `specs/base.ts` (see Performance section). Do NOT add a `wait for` step to `.feature` — if the test genuinely needs a wait step, **report it** as a Gherkin gap for QA; the auto-fix loop must not edit the approved Gherkin.

---

## Playwright Errors

### Selector errors → fix in `selectors.yaml`

| Error | Diagnosis | Fix |
|---|---|---|
| strict mode violation | Multiple elements match | Add `nth: 0`, `exact: true`, or more specific `name` |
| strict mode violation on `see`/`hover [X] item\|option\|cell with {{v}}` (dynamic list) | Older builds didn't filter the locator by the value | Since 3.2.13 `see`/`hover ... with {{v}}` on list-member roles filters by `hasText` like `click` (existence/hover the matching item) — keep the step data-driven, don't add per-item selectors. Update sungen if still unfiltered |
| strict mode violation on a filtered list step whose value is a PREFIX of sibling texts (e.g. `1:00` also inside `11:00`) | The `hasText` filter matches by SUBSTRING by default (case-insensitive, like Playwright), so several members match | Add `exact: true` to the selector YAML entry — it anchors the filter to the full text and is honored for `type: locator` entries too |
| Timeout / not found | Element doesn't exist or name wrong | Re-snapshot → copy exact accessible name. Check iframe/dialog scope |
| Element is not an input | Wrong element type targeted | Change `type` or `value` to match actual element |
| not a select | Custom dropdown, not native `<select>` | Set the widget variant: `variant: radix-select`/`antd-select` (built-in recipes), a custom recipe name from `qa/app.yaml`, or project-wide `widgets.select` in `qa/app.yaml` |
| Frame not found | iframe selector wrong or doesn't exist | Fix `frame` value, verify iframe in snapshot |
| Timeout / not found on a step AFTER a dialog closed, and the element is plainly there in the snapshot | **Stale dialog scope** — the scenario entered dialog scope (`Given User is on [X] dialog` or `User use dialog`) and never left. Scope is sticky: it survives the click that closed the dialog, and `wait for [X] dialog is hidden` does NOT end it. Every following locator is still prefixed with the dead dialog root | **Not a selectors.yaml fix — the locator is correct.** The `.feature` needs a `close`/`dismiss`/`exit dialog` step after the dialog closes, and the `.feature` is FROZEN during run-test → **report it as a Gherkin gap for QA** and let the test fail |

### Compile diagnostics (`sungen generate` output) → fix BEFORE running

| Code | Meaning | Fix |
|---|---|---|
| `SG-W001` | Step matched no pattern — sungen has no step form for the sentence. Compiles to `throw new Error("[sungen] …")` naming the step (it emitted a no-op `// TODO` before the unsupported-step gate, which let the scenario PASS asserting nothing) | Rephrase to a documented pattern (`sungen-gherkin-syntax`) or tag `@manual` with a reason. There is no silent fallback — the test goes red until one of those two is done |
| `SG-W002` | A step's `{{value}}` was parsed but never used in the generated code | The step form isn't the one you think — check the pattern's exact syntax |
| `SG-W003` | `{ name }` filter defaulted from the Gherkin reference on a name-from-content role (toast/alert/status) | Add explicit `name:` or opt out with `name: null` |
| `SG-W004` | A selectors.yaml entry declares a **field no consumer reads**, so writing it changes nothing in the generated test. Reserved for this in the original diagnostics spec (`exact: true` on an entry with no construct it applies to) and implemented for the wider class by #712, where `pagination:` and `contenteditable:` were public, documented and plumbed — yet read by nothing, discoverable only by grepping. The field partition lives in `utils/selector-entry-fields.ts`, each entry naming WHO reads it. A warning, not an error: an unknown key is inert, so refusing to compile would break a project over a stray comment-turned-key while teaching nothing | Delete the field, or fix its spelling — the message names the nearest valid key. If the field genuinely should do something, that is a compiler gap: report it rather than leaving the declaration in place believing it works |
| `SG-W005` | `qa/app.yaml` unknown key / unknown preset / unparseable | Fix the key, or `extends: native\|radix\|antd` |
| `SG-W006` | `qa/app.<env>.yaml` found — unsupported by design | Describe the app once in `qa/app.yaml`; env differences belong in test-data overlays |
| `SG-W007` | Browser-alert handler step written AFTER an action — listener registers too late, the dialog auto-dismisses silently | Use the compound form `click [X] button and accept [OK] alert` (preferred), or move the alert step before the trigger |
| `SG-W008` | `@manual` scenario incomplete in either direction: (a) HAS Gherkin steps but NO `Then` — a dangling `When` is invalid Gherkin, comments can NOT substitute; (b) documentation-style (NO steps) but its comments lack numbered action lines and/or a labeled oracle line — prose paragraphs / rationale-only comments export EMPTY Steps + Expected cells | Two valid shapes only: complete the Gherkin with a `Then`, or write the FULL procedure in the `# Tester verifies:` block as NUMBERED lines — action steps first (`1. <action>`), then labeled `Observable:`/`Oracle:`/`Expected:` lines (the number is what fills Steps, the label is what fills Expected results) |
| `SG-W009` | test-data references `${QA_<NAME>}` (a SECRET env ref) but the name is declared in neither `.env.qa` nor `.env.qa.example` — the test throws "environment variable not set" the moment the key is used. (Only the `${QA_*}` shape is checked; any other `${...}` is literal data and never flagged.) | Add `QA_<NAME>=<value>` to `.env.qa` (gitignored) and list the NAME in `.env.qa.example` (the committed checklist). See `qa/TEST-DATA-GUIDE.md` |
| `SG-W010` | A row step's `[Ref]` looks POSITIONAL (`First…`/`Last…`/`Top…`/`nth`) but has no selectors.yaml entry — the label is display-only, so the step checks existence ANYWHERE in the table; an ordering/sort-stability rule would pass even when the row moved | Declare the row in selectors.yaml (a `type: locator` value RELATIVE to the table, e.g. `first contact row:` → `value: 'tbody tr:first-child'`) — the step then asserts `toContainText` on that exact row. This is a selectors.yaml-only fix — allowed inside the run-test auto-fix loop |
| `SG-W011` | A storage assertion written as `expect … in local/session storage` — `expect` steps only read `{{response}}` refs, so this matches no pattern. Compiles to a `throw` (was a silent no-op) | Rewrite using the supported grammar: `User see [KEY] in local storage exists/…` |
| `SG-W012` | A mock-install step written AFTER a navigation step in the same block — `page.route()` registered after `goto()` misses every request fired during page load | Move the mock-install step before the navigation, or into `Background` |
| `SG-W013` | A page step (`see [X] page` / `is on [X] page` / `wait for [X] page`) whose `[Ref]` has no `type: page` selector entry (or collides with a non-page entry) — the step falls back to the feature's own path (or `/<ref>/`) instead of `X`'s real URL, so the anchored match can never reach it: the assertion always fails, the wait can only time out | Declare a `type: page` entry for `[Ref]` with its real URL; if the key collides with another type, disambiguate with a `--type` suffix (`sungen-selector-keys` § Collision rule) |
| `SG-W014` | `[X] page with {{v}}` where `{{v}}`'s base test-data value carries no query and no fragment — the step checks the PATH only, asserting less than it reads as | Informational — pass a value like `?q=…` if you meant to assert a query, or drop `with {{v}}` for a bare page |
| `SG-W015` | `see [X] card/item with {{n}}` where `{{n}}`'s test-data value is a **bare number** — this compiles to a TEXT assertion (`toHaveText('3')`), NOT a count. An author reaching for "3 cards are shown" gets an assertion that can only pass if the card's TEXT is literally the digit: a wrong test with no error. Scoped to container-ish element types (`card`/`item`/`option` + plurals); a text element asserting a numeric readout (`[Row Quantity] text with 4`) stays silent. In a `@cases` scenario the value is judged on every dataset row; it also fires on `see [X] row with {{n}}` whose rows MIX numbers and text (a row count needs every row numeric, so the step stays a text assertion) | For mixed `@cases` rows: put the counting rows in their own `@cases` scenario. For a COUNT use a form that has count semantics: `see [Ref] row with {{n}}` / `see [T] table with {{n}}`, or a list `should have count`. If the number really is the element's text, assert it on a **text/label** element instead |
| `SG-W016` | A table step on a table located by CSS/testid whose value does not mention `table` and that declares no `rows:` — the table templates find rows by `getByRole('row')`; on a list built from `<div>`s that is 0 rows, so `is hidden`/`is empty` PASS without checking anything and counts/clicks time out (issue #688). A heuristic — the compiler cannot see the DOM — so it warns once per table | Declare the row shape on the TABLE entry (`rows: 'div.job-row'`, relative to the table) — it drives all seven table steps — or locate the table by `role: table` if it really is one. Do not put the shape on a row label's entry: that key positions ONE row |
| `SG-W017` | A step needs a **selector-entry field the entry does not declare**, so there is nothing honest to compile — today: a table paginate step whose table entry declares no `pagination.next`/`.prev` (#712); a web `fill [X] date-picker` whose entry locates a `<button>` (a calendar trigger) and names no fill recipe (#907). The mirror of `SG-W004`: that one reports a field written that nothing reads, this one a field a consumer needs that nobody wrote. The step compiles to a loud runtime `throw` rather than guessing a locator, which is the #690 failure mode (a locator no page has, failing far from its cause) | Add the missing field to the entry in selectors.yaml — for pagination: `pagination:` with `next:`/`prev:` locators; for a button date-picker: `variant: <recipe>` naming a `qa/app.yaml` recipe with a `fill` intent (and `assertValue` to read it back). This is a selectors.yaml-only fix, allowed inside the run-test auto-fix loop |
| `SG-W018` | A selectors.yaml entry declares an **`nth:` outside the contract** — `nth: -2`, `nth: 'last'`, `nth: 1.5`. `nth:` was the one entry field whose VALUE nothing checked (issue #720), so the web adapter silently DROPPED the filter (identical to deleting the line — the step then hits `strict mode violation: resolved to N elements`, which names the DOM and never the YAML line) while the mobile adapter emitted `$$('~x')[-1]` / `[last]` — `undefined` indexing and a bare identifier. The value is now ignored and reported once per entry | Write an integer `>= 0` for a zero-based index, or `-1` for the last match (`.last()` on web, `.at(-1)` on mobile). There is no keyword form: `'last'` is a string, not an index. This is a selectors.yaml-only fix — allowed inside the run-test auto-fix loop |
| `SG-E020` | A step matched a pattern, but the **active adapter ships no template** for it (e.g. a web-only step compiled under `platform: mobile`). The feature file still generates — that one step compiles to `throw new Error("[sungen] …")` naming the step, feature, pattern, template and adapter, so the failure is loud and traceable rather than a crashed build | Rephrase to a step the target adapter actually ships (see `sungen-gherkin-syntax` Platform Support section / `sungen-mobile-gestures`), or tag the scenario `@manual` with the platform reason |
| `SG-E021` | The step carries a trailing **state clause** (`… is hidden`) that the pattern which matched it does not assert — the clause would be SILENTLY DROPPED. Worst shape in this table: it does not weaken an assertion, it can INVERT one. `see [X] page is hidden` matched `page-assertion` (priority 13) ahead of `is-hidden` (11), so the step compiled to `toHaveURL(...)` — asserting the page IS displayed, and passing. Also fires on `wait for [X] is checked` (a wait honours only `hidden`/`visible`/`disabled`/`enabled`; everything else silently fell back to `visible`) and on `row`/`column in [T] table … is <state>` for any state but `hidden` | Move the state onto the element that has it: `Then User see [X] checkbox is checked`. For a page, assert the page you expect (`Then User see [Other] page`) or a marker element (`Then User see [Banner] is hidden`). If sungen cannot express the check, tag the scenario `@manual`. The diagnostic names the pattern that swallowed the clause — that name tells you which form you actually hit |
| `SG-W020` | The matched pattern **declares `platforms`** and the active platform isn't among them. Caught before template lookup, so the diagnostic names the alternative for the platform you are on. This is now the MAIN off-platform channel, not a corner case: the whole web-only set (`table-*`, `storage-*`, `route-assertion`, `tab-order`, `sorted`, `upload`, `alert-fill`) and the whole mobile-only set (gestures, device state, app lifecycle, device oracles) declare it, so the refusal arrives at compile time with a concrete substitute instead of at run time from inside a template. It still compiles to a `throw` — moving the message earlier never turns a refusal into a silent pass | Do what the message says: it carries the pattern's OWN `platformAlternative`, e.g. a table assertion on mobile → per-row accessibility-id assertions; `local storage` on mobile → assert the state via a UI element or `@api`/`@query`; a swipe on web → drive the control the swipe reveals. `@mock` on a mobile unit is the same shape (there is no Mock Driver on Appium) |
| `SG-W021` | `use dialog` / `User is on [X] dialog` scope under the **mobile** adapter — the scope is recorded but no Appium template reads `inDialog`, so every following step resolves against the whole screen, not just the dialog | Don't rely on `scope: dialog`/`use dialog` for disambiguation on mobile — give the element inside the dialog its own unique accessibility-id/testid instead |
| `SG-W022` | `Then User see [X] page` under the **mobile** adapter — a native app has no URL, so the step compiles to a bare COMMENT: it reads as an assertion, checks nothing, and the scenario passes whatever is on screen. Worse than a hard failure, because nothing ever goes red. (Its `is on [X] page` twin throws via `route-assertion`; `Given User is on [X] page` is the app-LAUNCH directive and correctly emits nothing) | Assert something actually on the screen — `Then User see [Some Header] text` / a screen-marker accessibility-id — instead of a page/URL check, or tag the scenario `@manual` |
| `SG-W023` | A mobile **setup** step that emitted NO executable code, so a declared PRECONDITION is never established. `Given the user is logged out` matches `user-not-logged-in` → the `clear-auth` template, which on Appium is a bare COMMENT: mobile auth lives in the keychain/prefs, not in cookies, so no per-step action can clear it. The scenario then runs against whatever the PREVIOUS scenario left signed in and passes regardless — the same false-pass class as `SG-W022`, arriving through setup instead of assertion. Deliberately NOT a `throw` (an unestablished precondition weakens setup, it does not INVERT an assertion) | Declare a feature-level reset tier — `@reset:data` on the Feature — so each scenario starts from a wiped data layer; app state is reset in the `beforeEach` hook, not mid-scenario by a step. For BACKEND state (`database is empty`) the remedy is different: reset through a test API/script, or bind a `@query:` precondition, the same way the web adapter has to |
| `SG-W024` | Two or more scenarios in one feature **share a name** and at least ONE carries a `@reset:` tag. The generated per-scenario tier lookup is keyed on the scenario NAME (a `@cases` title is built at runtime, so no compile-time key can match the rendered title), so every same-named scenario resolves to the SAME tier — including one that declared none, which then silently runs at its namesake's tier: a `@reset:none` chain broken, or a reinstall nobody asked for. Counted over EVERY scenario rather than the tagged subset, because the untagged twin is the one that gets the wrong tier and has no tag to be counted by | **Rename** the scenarios so each title is unique. Reported rather than resolved by precedence on purpose — two tests genuinely sharing one title cannot be told apart by a name-keyed lookup, so a rename is the only honest fix (silently picking a winner would be the same silent-discard shape as `SG-W021`) |
| `SG-W026` | A mobile **active-navigation** step that emitted NO executable code, so a promised screen change never happens. `When User open [Settings] page` compiles to the one-line `navigation` template — a COMMENT noting the app was already launched by the wdio capabilities — so the step reads as "go to Settings" and goes nowhere; pair it with a `page` assertion (itself a no-op, `SG-W022`) and the scenario passes without ever touching the app. Scoped to the ACTIVE forms only: `Given User is on [X] page|screen` is the app-LAUNCH anchor and correctly emits nothing, so it is never reported. Deliberately NOT a `throw` — an unfulfilled navigation weakens the scenario rather than INVERTING an assertion, and throwing would break every mobile suite already carrying one | Change screens the way a user does — `When User tap [Settings] button` — then assert a marker on the new screen (`Then User see [Settings Header] text`). Keep `is on [X] screen` only as the feature's opening anchor. Note `generate --strict` fails on any diagnostic, so this code is blocking there from the day it lands |
| `SG-W028` | A trailing **scope clause was DROPPED**, so the step runs against more of the page than its sentence names. Two causes, one code: (1) the word after `in [X]` is not a container type — `in [Job Card] widget`, or a leaf element used as a parent — so the clause matched nothing and the first-`[ref]`/first-`{{data}}` extractors took only the head of the sentence; (2) the by-content form `in [X] <type> with {{v}}` on the **mobile** adapter, where a parent is chained with `$()` and has no content filter. Before this code, both compiled clean and silently widened the assertion to the WHOLE screen — in a list of look-alike cards the test then passes for the wrong reason, findable only by diffing the generated `.spec.ts` against the Gherkin (#667) | For (1) use a real container type: `table` `list` `list-item` `section` `dialog` `modal` `drawer` `overlay` `form` `card` `item` `row` `group` `panel` `region` `container` `tab` `menu`. For (2) give the container an accessibility-id that identifies it and scope by that, or assert on the element the value belongs to |
| `SG-W029` | A **mobile feature with no usable app-package `Path:`** — the line is missing, or it holds a web-style route (`Path: /applied-jobs`). The compiler then cannot name the app to reset between scenarios, and before this code that was SILENT: the per-scenario `beforeEach` was not emitted at all, so Background fell to Mocha's `before()` — once per FILE. Every scenario shared one continuous app session, so a tab switch or a scroll in one stranded elements off-screen for every later one, and the `@reset:` tier ladder was dropped with it, leaving the tag inert (#669). The spec now always emits the reset machinery and resolves the app id from the session capability instead, throwing a named error at run time if nothing supplies one | Add the package to the feature: `Path: com.example.app/.MainActivity`, or `Path: <androidPkg>/<activity> | <iosBundleId>` for a dual-id feature. A `Path:` starting with `/` is a web route and will not be read as a package. Failing that, set `APP_PACKAGE` (Android) / `IOS_BUNDLE_ID` (iOS) so `wdio.conf` puts the id on the session |
| `SG-E022` | The **generated spec file itself** is not parseable JS/TS, so NOTHING in it runs — Playwright reports one `SyntaxError` and every scenario in the unit is lost. A compiler self-check (`@babel/parser`) on the emitted code, added after a field report where the runtime-data marker post-processor mis-paired quote delimiters across two literals and the user only found out at `npx playwright test`. Reported, never thrown — the file is still written so you keep the artifact to inspect. **Unlike every other code in this table this one blames the COMPILER, not your `.feature`** | Nothing to fix in the Gherkin — this should be impossible. **Report it** with the `.feature` (and the step) the file was generated from, attaching the unparseable `.spec.ts`. Check first that you are running the project-local compiler (`npx sungen`), not an older global one |
| `SG-E023` | The step carries an **assertion clause** (`matches "…"`, `contains`, `has text`) but the generated code is a BARE VISIBILITY check — the clause was swallowed by a catch-all and the test would pass whatever the element says. The sibling of `SG-E021`, for the clauses that are not states: `SG-E021` closed the state case declaratively, but a dropped `matches`/`contains`/`has text` still slid through (reproduced live: a `matches "<format>"` step compiled by an older sungen lost the assertion entirely, emitted no warning, and left a `toBeVisible()` that passed whatever the label said). Four separate defects arrived this way, so the step now compiles to a runtime `throw`. Deliberately narrow — fires only on the bare-visibility shape, so a pattern that genuinely consumed the clause (`is-hidden`, `contains`, `matches-format`, the state family, or a `filter({ hasText })` on a list member) is untouched. Also fires on a trailing `is <word>` / `is not <word>` that is no state (`is on`, `is open`, `is active`, `is not visible`) — only `is visible`/`displayed`/`shown`/`present` and `is not hidden` are what a visibility check asserts | For an `is <word>` ending: use a state from the `sungen-gherkin-syntax` States list (`is expanded`/`is collapsed` for a disclosure), or `@manual`. Otherwise, either the phrasing is not the supported one for that clause — check the exact form in `sungen-gherkin-syntax` — or this sungen predates the step: verify you are running the project-local compiler (`npx sungen`), not an older global one |
| `SG-E024` | A mobile **app-lifecycle** step (`User restart the app` / `User reinstall the app`) in a feature that declares no `Path:` app id. The lifecycle templates resolve the target app through `__resolveAppId()`, a helper the appium test-file template emits ONLY when the id is present, so the spec used to compile clean and then die at runtime with `ReferenceError: __resolveAppId is not defined` — taking the WHOLE spec down, not the one step, because the failure lands in a before hook. (Field case: a mobile unit scaffolded with the nav recipe in `--path` instead of `--reach`, so `Path` never carried an app id.) Now refused at compile time as a `throw` naming the missing line | Add the app id under the `Feature` line: `Path: com.example.app/.MainActivity \| com.example.app`. For a mobile unit `--path` carries the **APP ID** and `--reach` carries the navigation recipe — if the nav recipe ended up in `--path`, that is the mistake |
| `SG-E025` | A feature tagged `@platform:ios` uses a step the Appium adapter does **not** serve on iOS — the template either throws deep inside a device run (`set-connectivity`, `location-services`, `open-notifications`) or silently does nothing (`hide-keyboard`, a guarded `if (!driver.isIOS …)`). Derived from the template body via the per-OS axis of the step-support matrix, so it cannot disagree with the adapter. Only an explicit `@platform:ios` is gated (`@platform:mobile` picks its OS at run time). Compiles to a loud `throw` | Use a step the matrix marks ✅ for iOS (see `docs/spec/step-support-matrix.md`), tag the feature `@platform:mobile` if it should run on either OS, or tag the scenario `@manual` |
| `SG-E026` | A **click step names a position** (`at position "top-left"`, `at offset x=…, y=…`) that would not be honoured: the keyword is misspelt or unquoted, a value is outside the grammar (not a number, negative, above 100%, a missing axis, an unknown preset), text follows the position, or another click form claimed the step first (`try to click`, a `with {{v}}` text filter, `… and accept [OK] alert`, `double click`). Each of those would compile to a click on the element's CENTRE and pass — for a backdrop-dismiss overlay that is the one outcome that proves nothing — so the step compiles to a loud `throw` | Write `click [X] <type> at position "top-left\|top-right\|bottom-left\|bottom-right"` or `… at offset x=<n>px\|%, y=<n>px\|%` as the LAST thing in the step, on a plain `click` (no `try to`, no `with {{v}}`, no alert clause). Web only — on mobile tap the control the position targets |
| `SG-E027` | A selectors.yaml entry declares a **`type:` that is not a selector type** — `type: 'testId'` (capital I), `'xpth'`, `'rol'`, `'test-id'`. YAML is untyped at run time, so this used to compile clean and resolve through the strategy switch's `default:` as `getByPlaceholder('<the value>')`: a locator that cannot match, surfacing much later as a timeout naming placeholder text nobody wrote. Reported once per entry, naming the entry key and the nearest valid spelling; every step using that entry now compiles to a `throw` | Fix the spelling in selectors.yaml. Valid types: `placeholder`, `role`, `testid`, `label`, `text`, `locator`, `id`, `upload`, `page`, `column`, `frame`, `table`, and the mobile strategies `accessibility-id`, `xpath`, `android-uiautomator`, `ios-predicate`, `css`. This is a selectors.yaml-only fix — allowed inside the run-test auto-fix loop |
| `SG-W030` | A selectors.yaml **`expanded.class`/`expanded.attribute`** value carries a character a class or attribute name cannot — whitespace, a quote, a backslash, `$`, `{`, `}`. The value is pasted into the generated spec (string literal, regex, `classList` argument) and selectors.yaml is often generated from the page under test, so it is refused and the field dropped: the step falls back to driving and asserting `aria-expanded`. Reported once per entry and field. The checked/selected signal is refused outright instead of warned about, because dropping it leaves the step with no state to read at all | Use the plain class or attribute name (Tailwind arbitrary values such as `bg-[#eff6ff]` or `!mt-0` are accepted); never paste a selector fragment or quoted text into the field |
| `SG-E031` | A selectors.yaml **`checked:`/`selected:` declaration the compiler refuses**: the two spellings disagree (`checked.class` and `selected.class` with different values), a class token or attribute name carries whitespace / a quote / a backslash, or one entry declares both a `class` and an `attribute`. Every one of them is silent at run time — the generated matcher tests a single token, so the step would simply time out blaming your component — and an entry declaring two signals lets an action drive one while the assertion after it reads the other. Refused, not dropped: with no signal the step is back on the *"has no on/off state to set"* failure the declaration exists to remove | Declare ONE spelling and ONE of `class` / `attribute`, as a single plain token: `checked: { class: 'is-checked' }`, `selected: { class: 'border-link' }`, or `checked: { attribute: 'data-checked' }`. This is a selectors.yaml-only fix, allowed inside the run-test auto-fix loop |
| `SG-W032` | A step carries the row filter **`in [T] table with {{v}}`** but the pattern that compiled it dropped the clause, so the generated code runs against the WHOLE table. `Then User see [Status] text in [Job Table] table with {{job_title}}` asserts on every `.status` in the table and passes on a row the sentence never named. The `table-*` family (positioned rows included) resolves its own container and is not flagged, nor is the two-`with` form, which scopes the row correctly, nor a step that WRITES — a write needs a value, so its `with {{v}}` IS that value and `fill [Search] field in [T] table with {{v}}` is an ordinary container-scoped write | Rewrite with the two-`with` form: `User see [Status] text with <expected> in [Job Row] row with {{job_title}}`. Point the second `[…]` at a ROW entry in selectors.yaml, not at the table entry — a filter applied to the table narrows nothing |
| `SG-W034` | A `see [X] column with {{v}}` step runs inside a table whose rows are declared with a **`rows:` shape**, so the row is not an ARIA row and holds no ARIA cells — but the assertion's only other way to reach a cell is `getByRole('cell')`, which matches nothing there. `columns.<col>.index` cannot rescue it: it indexes that same empty set. Before this the step failed as a timeout blaming the app. Not reported on a real `<table>`, where the role fallback is correct | Name the cell on the column: `columns: { title: { index: 0, header: 'Title', elements: { cell: { type: locator, value: '.job-title' } } } }`. This is a selectors.yaml-only fix — allowed inside the run-test auto-fix loop |
| `SG-E035` | **`table match data:` on a table declared with a `rows:` shape** and no matching `columns:` entries. The assertion reads its header row from `thead tr` and addresses cells as `td, th`; a <div> list has neither — forever, not as a render race — so the step could only wait out every timeout and fail on an empty header row. Refused at compile time, naming the columns it needs | Declare each DataTable column on the table entry with the selector that matches its cell inside a row: `columns: { title: { index: 0, header: 'Title', elements: { cell: { type: locator, value: '.job-title' } } } }`. Declaring columns also removes the runtime header read on a real `<table>`. This is a selectors.yaml-only fix — allowed inside the run-test auto-fix loop |
| `SG-W036` | A **`remember [X] … as {{v}}` capture through a `locator`/`css` selector with no `nth:`**. A capture reads ONE element and the step takes the FIRST match — the right default, and what the documented `nth: 0` workaround asked for after Playwright's strict mode turned a list-shaped selector into a hard failure naming the count. But the first of many is a decision: if the selector describes every item in a list, the captured value is the first row's. Not reported for testid/label/placeholder/role+name/text entries, which name a particular thing | Declare the index (`nth: 0`) to say the first one is meant, or narrow the selector to the single element. This is a selectors.yaml-only fix — allowed inside the run-test auto-fix loop |
| `SG-W037` | A **`type: page` entry saying something about the QUERY that the compiler cannot use**. Two causes: the entry's `value` carries a `?`/`#`, which is DROPPED (a page value is the path only — the pattern is matched against `u.pathname`, which never holds a query), or `query:` declares something other than `any`, which is ignored rather than read as permissive so a typo cannot quietly switch off the bare-page assertion. Both used to be silent, and the first is the trap: an author who hit "a bare `see [X] page` requires NO query" reaches for the query in the value, gets byte-identical output, and no hint why | To assert the query, declare it on the STEP (`see [X] page with {{v}}`). If the query is generated at run time — `/admins/detail?id=<the row just clicked>`, which no literal can express — add `query: any` to the entry: the query is then not part of that page's identity. selectors.yaml-only fix — allowed inside the run-test auto-fix loop |
| `SG-E038` | A **`{{var}}` the compiler cannot resolve** — the feature has no test-data file of its own, the key is absent, or it holds a list/map where a value was needed. This used to emit the PLACEHOLDER TEXT as the value: `fill [Partner Customer ID] field with {{valid_partner_id}}` compiled to `fill('${valid_partner_id}')`, typing those 20 literal characters into the app with `generate` exiting 0. A field with no format validation accepts the string, the server sees an unknown user, and the scenario fails at the assertion it was written to fail at — a suite that looks correct while feeding the system garbage. Every run also shares the SAME literal, so a scenario needing a fresh user silently reuses one. The step now compiles to a `throw` naming the ref and the file | Add the key to the file the message names. Note a feature resolves `{{var}}` against a data file named after **itself** — `qa/screens/<screen>/test-data/<feature>.yaml` — so the second `.feature` in a screen directory needs its own data file, not the screen-named one. `sungen data crosscheck` reports the same gap before you compile |

A diagnostic never changes generation's own exit code — `sungen generate --strict` turns any collected diagnostic into a non-zero exit for CI gates. They are not all inert, though: **`SG-W001`, `SG-W011`, `SG-E020`, `SG-E021`, `SG-W020`, `SG-E023`, `SG-E024`, `SG-E025`, `SG-E026`, `SG-E027`, `SG-E035` and `SG-E038` additionally compile the offending step to a runtime `throw`, and `SG-E031` refuses the whole file** — a step that reads as a check and silently checks nothing is worse than one that fails loudly. Generation always COMPLETES: the rest of the scenario and the rest of the file still compile. `SG-E022` is the odd one out — it blames the compiler, not your `.feature`, and throws nothing (the file is written so you can inspect it).

### Runtime error → `Test data "<key>" references ${QA_*} but the environment variable is not set`

The scenario uses a SECRET test-data key whose `QA_*` variable is missing in this environment. Fix: copy `.env.qa.example` → `.env.qa` and fill the value (or export it in the shell/CI). Never "fix" it by writing the real secret into test-data.yaml.

### Assertion errors → apply the Source-of-truth gate above FIRST

> The "Fix" column below applies **only when the expected value was wrong relative to `spec.md`** (a test defect). If the app's value contradicts the spec, the row is a **candidate bug → report it, do not edit the expected to match live**. Never weaken `toHaveText`→`toContainText` just to pass.

| Error | Diagnosis | Fix (only if the TEST was wrong per spec) |
|---|---|---|
| toHaveText mismatch | Expected text differs from actual | If the test's expected was wrong per spec → fix value in **test-data** (not `.feature`). If element is an input → change only the **element-type token** in `.feature` (e.g. `field`/`textarea`, the allowed selector-type edit). If the app value contradicts spec → **report as bug, let it FAIL**. |
| toHaveValue mismatch | Expected value differs from actual | Fix value in test-data |
| toContainText mismatch | Partial text not found | Fix expected partial text in test-data |
| toBeVisible timeout | Element exists but hidden, or name wrong | Check: is element conditionally visible? Wrong name? Inside dialog? |
| toHaveCount mismatch | Row count differs | Fix expected count in test-data. Verify: is table loaded? Filtered? If `Received: 0` while a dialog is open → aria-hidden trap, see **Table-Specific Errors** |

### Assertion type rule

Sungen picks assertion based on element type:
- **Input** (`field`, `textarea`, `search`, `slider`) → `toHaveValue()`
- **Dropdown / date-picker** (`dropdown`, `select`, `combobox`, `date-picker`) → the `assertValue` recipe when the entry's `variant:` (or `widgets.select` / `widgets.datepicker`) names one; otherwise a runtime tag probe: `toHaveValue()` on a native `<select>`/input, `toHaveText()` on a `<button role="combobox">` trigger (Radix/shadcn, MUI) or a `<button>` that opens a calendar popover. Pin one branch with `variant: native` or an `assertValue` recipe in `qa/app.yaml`. Any other element with a `variant:` uses its recipe's `assertValue` too.
- **`SG-W017` on a date-picker fill** (the entry is provably a button: `role: button`, or a css/locator ending in a `button` tag) and, at run time, **`[sungen] [X] is not a fillable element`** (a trigger the compiler could not prove is a button) → the fill target is a calendar / popup trigger. Declare a recipe with `fill` (and `assertValue`) intents in `qa/app.yaml` and set `variant: <recipe>` on the entry, or `widgets.datepicker` for every date-picker. Do not change the step to a click.
- **Text** (everything else: `message`, `heading`, `label`, `row`) → `toHaveText()`
- **Partial** (`contains` keyword) → `toContainText()`

If `toHaveText` fails on an input → the Gherkin step has the wrong target type. Fix: change **only the element-type token** in `.feature` (e.g. `[Email] message` → `[Email] field`). This is the one permitted `.feature` edit — do not change the reference name, value, or anything else.

---

## Table-Specific Errors

| Error | Diagnosis | Fix |
|---|---|---|
| `tableRow is not defined` | Column assertion without preceding row scope step | The Gherkin is missing a row-scope step — this is a **QA authoring gap → report it, let it FAIL**. Do NOT add the step during run-test (the Gherkin is frozen). |
| `toHaveText` on cell fails (with columns) | Wrong column index in `columns` config | Re-count columns in snapshot (0-indexed). Fix `index` in selectors.yaml |
| `toBeVisible` on cell fails (no columns) | `filter({ hasText })` didn't match | Check exact cell text in snapshot. Fix value in test-data |
| Row/cell count reads 0 while a dialog is open, rows visibly present behind it | **aria-hidden trap**: the modal (Radix/MUI/HeadlessUI…) sets `aria-hidden="true"` on the background; plain `getByRole('row'/'cell')` reads the a11y tree, which is empty behind a modal. Twin symptom: count-0 assertions (`table is empty`, `row not exists`) false-PASS under the same condition | Re-run `sungen generate` — table locators from sungen ≥3.2.8 emit `getByRole(…, { includeHidden: true }).filter({ visible: true })` and are immune. Do NOT edit the expected count or weaken the assertion — the rows exist; the old locator measured the wrong tree |
| Row filter matches 0 rows | Filter text doesn't match any row content | Re-snapshot → find actual row text. Fix filter value in test-data |
| Row filter matches multiple rows | Filter text is too generic (matches multiple rows) | Use more specific filter text (unique identifier like email, ID) |
| Table not found | Wrong table name or table not rendered | Re-snapshot → copy exact table accessible name |

---

## Auth Errors

| Symptom | Fix |
|---|---|
| Redirect to login page | Auth expired. Ask user to log in manually via MCP browser |
| `storageState` file not found | Ask user to log in manually via MCP browser, then save storage state |
| Most tests timeout on first step | Auth expired — ask user to re-authenticate via MCP browser |
| Page shows home instead of target | SPA + expired auth. Re-authenticate via MCP browser. (If a wait is genuinely needed, tune `specs/base.ts` — do NOT add a `wait for` step to the frozen `.feature`.) |

**Never use `sungen makeauth`.** Always let the user log in manually via the MCP browser.

---

## Sungen Compile Errors

| Error | Fix |
|---|---|
| Unknown step pattern | The Gherkin uses a step the compiler doesn't recognize — a **QA authoring issue → report it, let it FAIL**. Do NOT rewrite the step during run-test (the Gherkin is frozen; fix belongs in create-test/review). |
| Missing selector | Add key to `selectors.yaml` |
| Missing variable | Add key to `test-data.yaml` |
| Invalid selector type | Use: role/testid/placeholder/label/text/locator/page/upload/frame |

---

## Flow-Specific Errors

| Error | Diagnosis | Fix |
|---|---|---|
| Navigation timeout between screens | Cross-screen transition takes too long or URL mismatch | Verify the target URL path; tune navigation waits in `specs/base.ts`. Do NOT add a `wait for page` step to `.feature` — if the flow genuinely needs one, **report it** as a Gherkin gap for QA. |
| Selector `"screen:element"` not found | Namespace key missing or wrong format | Ensure colon-namespaced key in `selectors.yaml` is **quoted**: `"login:submit":`. Check screen prefix matches `[Screen:Element]` ref in Gherkin |
| Test data `screen.key` undefined | Phase namespace mismatch | Verify `test-data.yaml` uses dot-namespaced keys: `login.email`, `submission.nominee`. Keys must match `{{screen.key}}` refs in `.feature` |
| State lost between screens | Auth/session expired during multi-screen flow | Ensure all screens in the flow share the same `@auth:role` tag. Check if the app invalidates sessions on navigation |
| Duplicate selector key across screens | Two screens use same element name without namespace | Always use `[Screen:Element]` format in flow `.feature`. Selectors must use `"screen:element":` quoted keys |

---

## API Request Errors (`@api` / catalog)

| Error | Diagnosis | Fix |
|---|---|---|
| `400 "Unexpected field"` on a file upload | Endpoint accepts **multiple files under one field**, but the catalog declared a single file (one part sent) | In `apis.yaml`, make that `files:` field an **array** — one `:param` per file: `files: { gift_image: [":gift_image_1", ":gift_image_2"] }`. Add matching `test-data` paths. This is a real automatable case — do NOT defer to `@manual`. |
| `415 Unsupported Media Type` on upload | Sent multipart where the endpoint wants a raw binary body (or vice-versa) | Use `bodyFile:` (raw binary body) instead of `files:` (multipart part), or the reverse — they are mutually exclusive. |
| `fixture "…" not found` | The referenced file isn't on disk | Drop the sample file under `qa/fixtures/` and reference it by name from `test-data`. |

---

## Mobile Runtime Errors (Appium / WebdriverIO)

**The fixes are NOT written here.** They live once, as data, in `driver-mobile/src/mobile-repair.ts`,
and `sungen repair --screen <unit>` prints the matching one for the failure you actually got:

```bash
sungen audit --screen <unit>     # findings
npx playwright test              # (or the wdio run) — leaves the result JSON
sungen repair --screen <unit>    # → the concrete fix for each finding + each failure
```

This table is the INDEX into that catalog — symptom → rule id — so you can recognise a failure class
by eye and know a rule already covers it. Two copies of a fix drift; one copy plus an index does not.
`tests/ai-skills/error-mapping-repair-parity.run.ts` fails if this table and the catalog disagree in
either direction.

| What you see | Rule id | Class |
|---|---|---|
| `Element not found: ~key` / `no such element` | `mobile-element-absent` | selector |
| `iOS geometry oracle: rect={…0x0…}` | `mobile-ios-offscreen` | selector |
| `element click intercepted` / `is not clickable at point` | `mobile-tap-intercepted` | selector |
| `Default scrollable element '//android.widget.ScrollView' not found` | `mobile-scroll-no-scrollable` | selector |
| `A new session could not be created` | `mobile-session-not-created` | session |
| `Could not proxy command to the remote server` / `socket hang up` | `mobile-driver-proxy-lost` | session |
| `The instrumentation process cannot be initialized` | `mobile-instrumentation-failed` | session |
| `No Chromedriver found that can automate Chrome '…'` | `mobile-chromedriver-mismatch` | session |
| `[sungen] "switch to X frame" — no WEBVIEW/CHROMIUM context appeared within …ms` | `mobile-webview-context-absent` | session |
| `@auth:<role> login did not complete` / `could not decide the login state` | `mobile-auth-session` | session |
| `device log (logcat) did not contain "…"` | `mobile-device-log-silent` | oracle |
| `[sungen] … web-only` / `not supported on the appium adapter` | `mobile-web-only-step` | compile (SG-W020/E020) |
| `[sungen] step "…" matched no pattern` | `mobile-step-unmatched` | compile (SG-W001) |
| `… needs the app id: the mobile lifecycle steps resolve …` | `mobile-lifecycle-no-app-id` | compile (SG-E024) |
| `… This feature declares @platform:ios` | `mobile-ios-unsupported-step` | compile (SG-E025) |
| `UNIVERSAL: missing theme(s): …` | `mobile-universal-theme` | audit |
| `GATE: critical theme "…" has NO covering scenario` | `mobile-gate-theme-uncovered` | audit |
| `GATE: critical theme "…" covered only by SHALLOW` | `mobile-gate-theme-shallow` | audit |
| `VIEWPOINT-GESTURE-SUBSTITUTED: …` | `mobile-gesture-substituted` | audit |
| `MANUAL-CODE-MISSING: …` | `mobile-manual-uncoded` | audit |
| `PAGE-TYPE-UNDETERMINED: …` | `mobile-page-type-undetermined` | audit |

A failure with no rule id is reported by `sungen repair` under **"No known rule (review manually)"**.
That list is deliberately not empty-by-construction: a rule broad enough to match everything would
hide the findings nobody has looked at yet.

---

## Performance & Infrastructure Errors → Fix in `specs/base.ts`

All generated `.spec.ts` import from `specs/base.ts` — shared context caching, navigation, overlay cleanup. AI **can and should** tune `base.ts` to match the project.

| Symptom | Root cause | Fix |
|---|---|---|
| Server 429 (rate limited) | Too many browser contexts | Fix `contextCache` to reuse sessions per `storageState` |
| Tests slow with `--workers=1` | Redundant navigation | Fix goto patch: skip if `currentPath === url` |
| Previous test's modal blocks next | Overlay not cleaned up | Add/improve Escape + backdrop click in cleanup hook |
| All tests fail on first navigation | `page.url()` is `about:blank` | Add try/catch in goto patch |
| Flaky timeouts on SPA pages | Default timeout too short for app | Increase `actionTimeout` / `navigationTimeout` |
| Tests pass individually but fail in batch | Shared state leaking between tests | Isolate context per test or reset state in `beforeEach` |

### What AI CAN fix in `base.ts`

- Timeout tuning (increase for slow APIs, decrease for fast apps)
- Custom overlay/modal dismiss logic (project has unique close patterns)
- Navigation wait strategy (`networkidle` vs `domcontentloaded` vs custom)
- Context caching strategy (per-role, per-session)
- Cookie/localStorage cleanup between tests

### What AI should NOT move into `base.ts`

- Individual selector fixes → belong in `selectors.yaml`
- Test data values → belong in `test-data.yaml`
- Single-test workarounds → belong in the `.spec.ts` directly

---

## Smart Fix Strategy

### When multiple tests fail on the same element
Fix the **root cause once** — don't fix each test individually. Group by selector key, fix selectors.yaml, recompile, re-run batch.

### When you're unsure about the fix
**Re-snapshot** — `browser_navigate` to the failing page, take `browser_snapshot`, compare the live state against what the test expects. This is faster than guessing.

### When the fix might break other tests
After fixing selectors.yaml, run ALL tests (not just the failing one) to catch regressions from renamed/changed selectors.

### When nothing works after 3 attempts
Ask the user. The issue might be: dynamic content, race condition, environment-specific state, or a real application bug.
