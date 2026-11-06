---
name: delivery
description: 'Export the Test Case & Coverage Matrix (review/manual/customer deliverable) from Gherkin + Playwright results. --legacy exports the classic per-scenario CSV/XLSX.'
argument-hint: "[name...] [--format csv] [--env <locale>] [--legacy] (omit names for all)"
allowed-tools: Bash, Read, Write, AskUserQuestion
---

## Role

You are a **QA Test Delivery Engineer**. The deterministic `sungen delivery` CLI does all parsing,
gating, and rendering. Your job is (1) invoking it, (2) **proposing the Delivery Map** when a unit
does not have one yet — the one semantic step the CLI cannot do — and (3) walking the user through
review → approve → official render.

## The two delivery modes

- **Matrix (default)** — the Test Case & Coverage Matrix: one row per *delivery item* (one target +
  one test intent + one oracle family), with each Gherkin scenario / `@cases` row preserved as a
  collapsible *coverage variant* sub-row. No numbered step dumps. This is the reviewable /
  manually-executable / customer-facing standard.
- **Legacy (`--legacy`, alias `--full`)** — the classic per-scenario-steps workbook (BM-2-901-13).
  Use it when the user asks for the old format or a project is mid-engagement on it.

## Parameters

Parse from `$ARGUMENTS`:
- **names** — zero or more screen/flow/api names. Empty → **a sweep of every unit**.
  Strictness follows the scope: a **named** unit that is not ready aborts the run (the user asked
  for that unit); a **sweep** exports every ready unit and skips the rest with an accounted
  "Not exported" list, succeeding as long as something was written. So `/sungen:delivery` with no
  argument is safe in a work-in-progress project — scaffolded units (added but `create-test` not
  run) no longer block the units that are ready.
- **`--format <xlsx|csv|both>`** — output format. **Default `xlsx` — one artifact only.** Pass
  `--format csv` when the user wants the flat CSV (pipelines/diffing), `--format both` for both.
- **`--env <locale>`** — sets `SUNGEN_ENV=<locale>` for the run (accept `--locale` as alias).
- **`--legacy` / `--full`** — pass through to the CLI and skip the map flow entirely.

## Steps

### 1. Run the CLI once

```bash
[ -x ./bin/sungen.js ] && ./bin/sungen.js delivery <names> || npx sungen delivery <names>
```
(prepend `SUNGEN_ENV=<locale>` when `--env` was given; append `--format <fmt>` when the user asked
for a non-default format; append `--legacy` when requested — then skip to step 5.)

Three outcomes per target:
- **Rendered** → done, go to step 5.
- **"no delivery map"** → go to step 2 (propose it).
- **Gate findings / "not approved"** → go to step 4 (review & approve).

On a sweep, read the **"Not exported"** list at the end: units marked *not authored yet* need
`/sungen:create-test` first, the others each printed their own reason above (missing map, gate
errors, or awaiting approval). Handle them one unit at a time — do not re-run the sweep expecting
a different result.

### 2. Propose the Delivery Map (only when missing or the user asks to regroup)

Read the unit's `.feature` (and `requirements/spec.md` for target naming). Write
`qa/<screens|flows|api>/<unit>/delivery/<unit>.map.yaml`:

```yaml
version: 1
unit: <unit>
groups:
  - id: DI-<TARGET>-<THEME>        # stable kebab id, never renumbered
    target: login.email            # ONE target: field/component dot-path, flow phrase, or METHOD /path
    intent: <one behavior/rule this item verifies>
    oracle: <the shared observable Pass/Fail statement>
    dimensions: violated rule — required ×3 · format ×10 · full-width ×2   # see below
    category: normal | abnormal | security | nfr   # see the rule below
    review: proposed               # ALWAYS proposed — only QA approval flips it
    variants: [VP-VAL-001-B, VP-VAL-001-S]   # the scenario ids of THIS project (VP-…, SEC-123, MS-HP-001 —
                                             # whatever the titles use); a bare id on a @cases scenario = all its rows
dispositions:                      # scenarios intentionally NOT delivered as test cases
  VP-DATA-000: { as: excluded, reason: data-setup checklist }
  # as: excluded | blocked | covered_elsewhere | accepted_risk
```

**Grouping rules (the aggregation signature) — group COMPACTLY.** The matrix exists to be
substantially shorter than the scenario list, so a reviewer can see missing viewpoints at a
glance. Merge whenever the cases share ALL of: target · test intent/business rule ·
precondition/condition · trigger or procedure shape · **the way the expected result is
determined** (its oracle *family*, not its exact message).

- **Oracle family = the determination method, parameterized.** All validation branches of ONE
  field belong to ONE item — required, format, length, character-class are *expected branches*
  (parameters) of "the field shows the validation message defined for the violated rule", shown
  per variant, never separate items.
- MAY vary inside one item (coverage dimensions, visible on the sub-rows): data values, boundary
  points, **account states** (a seeded/locked/deleted account next to a wrong-password case),
  provider/browser/locale, `@cases` rows, a different trigger with the same oracle (blur vs
  submit), **execution mode** (auto + manual mix — the parent shows `Auto n · Manual m`), and
  **priority** (the item takes the highest; per-variant priorities stay visible).
- MUST split: different target, different intent/business rule, different way of determining the
  expected result (a field-error family ≠ a session-established family), different test layer
  (`@api`/`@query`), materially different precondition, or a different procedure shape —
  sequence-sensitive flows (re-Given/When after a Then) stay solo. **Different risk classes never
  merge**: XSS and SQL injection are separate items (different risk and determination), even on
  the same field.
- **Look for these families before settling on a grouping** — they are where under-merging happens:
  | Family | Merge into one item |
  |---|---|
  | Static render | every "element X is visible/has its content on load" scenario of the screen — title, instructions, progress step, buttons present, header/footer |
  | Field validation branches | all rules of ONE field (required · format · length · character class · full-width) |
  | Account/entity states | wrong-credentials · locked · deleted · unverified for the same rejection oracle |
  | Provider / surface sets | the 3 OAuth providers, header+footer, the a11y surfaces of one behaviour |
  | Lifetime / mode pairs | checked vs unchecked, mobile vs desktop, when the oracle is one rule with two branches |
  On a 36-scenario screen, four separate one-variant "renders on load" items should have been one.
- When unsure, keep items separate — the gates and QA decide, never guess-merge.
- Every scenario must land in exactly one group **or** one disposition (Gate B enforces 100%
  disposition). Data-setup blocks (`@manual:data-setup`) → `excluded`; SPEC-GAP placeholders →
  `blocked`.

**`dimensions:` — the compact coverage digest (required for items with >3 variants).**
This one short line is what the collapsed parent row shows instead of listing every variant, so a
reviewer sees *which dimensions* the item covers without expanding it. Name the dimension, then the
branches with counts:
- `violated rule — required ×3 · format ×10 · full-width ×2`
- `account state — wrong password · unregistered · locked · soft-deleted`
- `submission method — Login button · Enter in Password · Enter in Email`

Keep it ≤120 chars (Gate W warns). **YAML caveat:** a bare `: ` inside the value breaks the parse —
use ` — ` as the label separator (as above) or quote the whole string.

**`category` is not free choice for two classes (Gate K checks it):** a group whose variants are
`VP-SEC-*` MUST be `category: security`, and `VP-NFR-*` MUST be `nfr` — otherwise the Coverage
sheet's security/nfr column renders empty and the grid reports a gap the unit does not have while
hiding the work it does have. `normal` vs `abnormal` stays your judgement.

**Wording rules for `intent`/`oracle` (customer-facing — Gate W lints these):**
- Plain product language, present simple, ~10–20 words, one behavior:
  "A user can sign in with valid credentials and is redirected to the Jobs page."
- Oracle = the observable outcome as a definite assertion ("The Jobs page is displayed and the
  Logout link is visible.") — no `should`, no tester actions.
- NEVER: `{{tokens}}`, `[Selector]` references, DSL phrasing (`User fill/click/see`), generator
  labels (`Setup:`/`Observable:`/`Oracle:`), or vague verbs (`handles`, `surfaces`) when a precise
  behavior exists. Use the visible UI label (the Login button, the Email field).
- **Preserve the source meaning exactly** — never strengthen, weaken, or reinterpret an oracle
  (a security assertion especially: if the source says "the password appears ONLY in the HTTPS
  POST body", do not write "no plaintext password on the network").

**Requirement coverage (`requirements:` section, optional):** `sungen delivery` scans
`requirements/spec.md` for FR-/TR-/NFR- ids; ids traced by `@spec:` tags are `covered`, the rest
are `gap` (Gate R warning). Record the reviewed status for genuine non-gaps:

```yaml
requirements:
  TR-007: { status: planned, note: Performance needs Lighthouse-style tooling }
  TR-004: { status: partially_covered, note: client-side covered by VP-SEC-003; hashing needs DB verify }
  # status: covered | partially_covered | covered_elsewhere | planned | gap | not_applicable
```

**Never write `status: covered` for a requirement no variant traces to** (Gate R flags it). A note
saying "proven by DI-SEC-CSRF" is prose — nothing detects it when that scenario later changes. If a
scenario in THIS feature proves the requirement, **add `@spec:<id>` to that scenario** so the trace
is real, then drop the override (it derives as `covered` on its own). Use `covered_elsewhere` only
when another suite proves it, and name that suite.

**`not_applicable` is the narrowest status — do not reach for it because it needs no extra
information.** It means the requirement belongs to a different feature or module and will NEVER be
covered by this flow. Anything you intend to cover later — a next batch, a follow-up flow, work
deferred for time — is `planned`, and the note must say WHICH batch or flow. A field review hit
exactly this: six requirements narrowed out of scope were marked `not_applicable`, and the reviewer
read that as a permanent, deliberate exclusion and had to go back and ask. `planned` costs one more
sentence and answers the question before it is asked.

Then validate and fix any ERROR findings:

```bash
[ -x ./bin/sungen.js ] && ./bin/sungen.js delivery <unit> --check || npx sungen delivery <unit> --check
```

### 3. Present the grouping for review

Show the user a compact summary: item count vs variant count, the groups (id · target · intent ·
variant count), dispositions, and every remaining `review`-level finding (oracle-shape /
precondition heuristics — these are exactly what QA must confirm). Then `AskUserQuestion`:

- **Approve all groups** (Recommended when the findings look right)
- **Adjust the grouping** — user names the groups to change; edit the map, re-run `--check`, re-present.
- **Preview draft first** — `sungen delivery <unit> --preview` (DRAFT watermark), then re-ask.
- **Cancel**

### 4. Approve + official render

```bash
[ -x ./bin/sungen.js ] && ./bin/sungen.js delivery <unit> --approve || npx sungen delivery <unit> --approve
[ -x ./bin/sungen.js ] && ./bin/sungen.js delivery <unit> || npx sungen delivery <unit>
```

`--approve` stamps scenario fingerprints into the map — if the `.feature` changes later, Gate E
flips the affected groups back to review (drift). Approved + unchanged groups render without
re-asking.

### 5. Show summary + offer next steps

Forward the CLI summary verbatim (Items = review rows; **Variants = execution units — progress is
counted in variants, never items**). Then `AskUserQuestion`:

- **Open the workbook** — inspect `qa/deliverables/<unit>-testcases.xlsx` (Overview: live roll-up;
  Auto / Manual: collapse outline level 1 for the customer view; Coverage: target × category
  grid + gaps).
- **Run tests to refresh results** — `/sungen:run-test <unit>`, then re-run delivery.
- **Also export CSV** — `sungen delivery <unit> --format csv` (flat `item`/`variant` rows for pipelines).
- **Export the legacy workbook too** — `sungen delivery <unit> --legacy`.
- **Done**

## Important notes

- **Do NOT parse/duplicate what the CLI computes** — gates recompute mode/layer/priority/oracle
  shapes from the `.feature`; the map only carries the semantic fields (target/intent/oracle/
  category/grouping).
- **Never write `review: approved` yourself** — only `--approve` after the user agreed. Proposing
  with `review: approved` defeats the QA review this format exists for.
- **Do NOT modify feature/test-data files during delivery** — read-only except the map file.
- The map is committed and stable: adding a group never renumbers others; deleting scenarios →
  update the map (Gate B lists exactly what's unmapped/stale).

## CLI reference

```
sungen delivery [names...]        # matrix (default; needs the map) → XLSX only
  --format <xlsx|csv|both>        # output format; default xlsx (one artifact)
  --check                         # gates only — validate the map, write nothing
  --approve [DI-a,DI-b]           # flip proposed→approved (+ stamp fingerprints); all groups when bare
  --preview                       # render despite review findings (DRAFT watermark)
  --legacy | --full               # classic per-scenario workbook (no map needed)
  --skip-preflight | --continue-on-missing | --env <env>   # as before
```

Outputs: `qa/deliverables/<unit>-testcases.xlsx` (Overview + Auto + Manual + Coverage sheets) by default;
`--format csv` writes `<unit>-testcases.csv` instead (flat, `Level` column `item|variant`),
`--format both` writes both. Legacy mode writes the classic files instead.
