---
name: sungen-data-factory
description: 'Use when create-data-test (or create-test) needs standardized TEST DATA — valid/boundary/invalid values for input fields with CHK-* traceability. Drives the Data Factory catalog + the 4-source method. Invoke after reading spec.md, when authoring the field-map or filling test-data.'
user-invocable: false
---

## What this skill does

Turn a screen/flow's fields into **standardized test data** — not guessed values. You author a
**field-map**; the deterministic `sungen data gen` expands it from the Data Factory **catalog**
(field-type + security + technique library) into valid / boundary / invalid values, each carrying a
`CHK-*` id that traces to the spec. Your judgment goes into *reading the spec correctly* and
*answering blind spots* — never into inventing values the catalog or the QA should own.

Data Factory is **bundled with core** (runtime-free) — no `capability add` needed; the `sungen data …`
CLI is available out of the box.

**Auto-detection (act on these, don't wait to be asked).** `sungen generate`/`sungen audit` now NOTICE
the work a unit needs, even before any field-map exists:
- **`DATA-FACTORY-UNSTANDARDIZED`** — the unit has input fields but no `qa/data-factory/<name>.fields.yaml`.
  → author the field-map + `data gen` (this skill's main job).
- **`DATA-FACTORY-PRECONDITION-UNDECLARED`** — the spec describes a stateful precondition (a required
  entity/state) but the test declares none. → **auto-draft a state-recipe** from the spec sentence, run
  `sungen data state`, and weave its `@manual:data-setup` `Background`; confirm the drafted values with
  the QA (`AskUserQuestion`) — never invent domain values.
When you see either finding, do the work rather than leaving it — that is what makes Sungen self-driving
instead of a toolbox the QA must operate.

## The 4-source method (phuong-phap-tao-test-data.md)

Never invent values from memory. Cross-reference four sources — each covers the previous one's gap:

1. **Spec validate rules** — required, format, min/max, business rules, and the **error/message code**
   per rule. Defines the legal/illegal boundary. (Says the *law*, not every way to *break* it.)
2. **Field-type catalog** — the accumulated "how this type breaks" knowledge (email/password/phone/
   number/…). This is the Data Factory `common/` — it fills the "spec doesn't list every break" gap.
3. **Design technique** — Boundary Value Analysis / Equivalence Partitioning / Decision Table / State
   Transition — reduces the rule + candidates to a *minimal-but-sufficient* representative set.
4. **DB mapping** — unique constraints, cross-entity dependency, real backend limits → precondition
   data, not just a single input value.

Then classify every value into **Valid / Boundary / Invalid** (each invalid tagged with its error
code) and give it a `CHK-*` id for traceability.

## The field-map — `qa/data-factory/<name>.fields.yaml`

Your main artifact. One entry per input field; the generator expands it:

```yaml
screen: login                 # or  flow: <name>
fields:
  - name: email
    type: email               # a catalog field-type (see list below)
    required: true
    constraints: { maxLocal: 64, maxTotal: 254 }   # the project's REAL limits from spec
    errorMap: { M13: E1042, M02: E1001 }            # catalog placeholder code → project's real code
  - name: password
    type: password
    constraints: { minLength: 8, maxLength: 32 }
```

**Catalog field-types** (P1): `email` · `password` · `phone` · `string` · `number-integer` ·
`number-float` · `currency` · `postcode` · `katakana` (JP). Security payload banks auto-append to a
field's invalid group via the type's `uses_security` (`sqli`, `xss`, `idor`, `broken-auth`, `upload`,
`input-validation`).

Rules:
- Pick the closest `type`; put the project's real numbers in `constraints` (they override the
  catalog defaults).
- Map every `errorRef` placeholder (`M13`, `M39`, …) to the spec's real code via `errorMap`. Leave
  one unmapped **only** if the spec has none — the lint flags it as an open point to confirm.
- Some catalog constraints are only placeholders (string `minLength: 1` / `maxLength: 255`, phone
  `8..15`, …). A field that does not set its own value is warned **`PLACEHOLDER_CONSTRAINT`** — the
  boundary cases are built from that number, so take it from the spec.
- An **empty value** is judged by `required`, not by `minLength`: `required: false` → `""` is valid
  (no "below minimum" case for it); `required: true` **or unset** → `""` is invalid. `minLength: 0`
  has no min-1 case. So an optional field must say `required: false`.
- To drop **one** generated case for **one** field, list its id under the field's **`disable:`**
  (e.g. `disable: [CHK-STR-I2]` drops min-1 and keeps max+1 `CHK-STR-I2a`). It is reported as
  not applicable. The overwrite's `disable:` drops a case for every field of the type.
- **Complex forms:** a **group** field takes nested **`fields:`** (recursive; no `type` on the group → renders a nested object like `address.city`). A field whose rule depends on another takes **`dependsOn: { field, value }`** — recorded as a `_depends_on` note (the generator doesn't resolve the runtime condition; weave it into the scenario/`@cases`).
- Set **`unique: true`** on a field that must be fresh every run (register email/username, anything
  that would clash with a record a previous run left behind). Its valid value then carries a runtime
  `{{$timestamp}}` (email → `user+{{$timestamp}}@…`) so each run is unique — no cross-run cache
  (which would make tests order-dependent/flaky). Prefer this over caching prior-run values. For
  reusing a value produced earlier **within the same run** (register → then log in with it), bind it
  via the scenario (`@dataFactory` recipe / `testData.bind`), not a cross-run cache.

## No input fields → nothing to generate

Data Factory standardizes **field-level input data**. If a unit has no user-input fields or
parameters — a pure navigation, listing, or capture-compare flow — there is nothing to generate.
Report *"no input fields → nothing to generate"* and skip it; do **not** write an empty field-map.
Those scenarios are covered by the data create-test already produced, not by field-level test data.
(This is a correct outcome, not a gap — don't force a field-map to make the unit "processed".)

## Blind spots — ask, never fabricate

If a field's type is **not** in the catalog — a business enum, a cross-entity constraint, a
project-specific data model — do **not** guess. Ask the QA (via `AskUserQuestion`) for: (1) the valid
set, (2) invalid / non-existent values, (3) the error code when wrong, (4) dependencies on other
fields. Record it in `qa/data-factory/project/<entity>.yaml` so it becomes reusable project catalog.
This is the core discipline: *common is never enough; when blind, think and ask — don't invent.*

## Customizing the catalog — `.overwrite` (survives `sungen update`)

The shipped `common/` is a floor. Project rules go in `qa/data-factory/common.overwrite.yaml`,
deep-merged over the common (**later-wins**, keyed by `type` + data-point `id`) via three explicit
operations: **EXTEND** (a new `id` appends), **REPLACE** (same `id` wins; `constraints` merge
key-by-key), **DISABLE** (list ids under `disable:` to drop shipped cases that don't apply — a stale
target id warns). You can also override `security:` banks and `techniques:` the same way. `sungen
update` refreshes `common/` but never touches your overlay; a stale entry **warns you to prune it** —
it never edits your file. Nothing is silently overwritten: **`sungen data provenance`** (and a
`⟲ .overwrite →` line at `data gen`) shows exactly what your overlay changed, and each overlaid value
is tagged `via: .overwrite` in the test-data. Prefer a targeted `errorMap`/`constraints` in the
field-map for per-screen tweaks; use `.overwrite` only for rules that apply project-wide.

## Bulk-import fixtures (CSV)

For CSV/Excel import or bulk-upload testing, `sungen data fixture --screen <name> [--rows N] [--invalid]`
turns the field-map into a fixture FILE under `qa/fixtures/`: `<name>.csv` (N valid rows; nested groups
→ dotted headers; `unique` fields row-indexed) that should import cleanly, and — with `--invalid` —
`<name>.invalid.csv` (one seeded-bad row per field, **same columns as the valid file so a strict
importer fails on the data, not an extra column**) plus a **sidecar** `<name>.invalid.expected.yaml`
that says which 0-based row must be rejected and why (`field`, `chk`, `reason`, `expected_error`).
Reference the CSV from an `@api` upload (`files:`) or a UI file input, and assert row rejection against
the sidecar.

## Verify before done — the phuong-phap §6 checklist

Run `sungen data lint` and clear it: every required field has **≥1 valid**, **boundary present**
(exact min/max, not only over-boundary) for bounded types, **≥1 invalid per rule** (not one generic
"wrong" value for many rules), no `UNRESOLVED` values, error codes mapped or explicitly left open.
Then `sungen data gen` writes the standardized `test-data/<name>.yaml`. Do not hand-edit the
generated values — change the field-map or `.overwrite` and re-run (determinism: same input → same
data).

Then run **`sungen data validate`** — the semantic check beyond coverage. It verifies each generated
value is *correct*, not just *present*: every `valid`/`boundary` value actually satisfies the
constraints and every synthesized `invalid` value actually violates them. A boundary is expanded to
**one case per point** (`min / min+1` → two cases; `< min` → min-1, below the minimum), each stamped
`expected: valid|invalid` in the output. Any `VALUE_NOT_VALID` error means a value labeled valid breaks
a constraint — fix the field-map/constraints and re-run. This is what stops the "structurally right but
semantically wrong" data (e.g. a "below minimum length" case that is actually long enough to pass).

**Applicability & profiles.** The generator only emits cases that apply, and reports the rest (never
silent). Set **`required: true|false`** correctly — an optional field drops the "empty (required)" case;
a numeric field with `min ≥ 0` drops the negative example. Security/injection payloads land in their own
**`adversarial:`** bucket, separate from `invalid:`. Dropped cases show up as `_not_applicable` in the
output + a `NOT_APPLICABLE` lint info. A `--profile` (`regression` default · `functional` · `smoke` ·
`security-min`) and `--locale`/`--channel`/`--sink` on `data gen|validate|lint` select a reduced set;
selection is deterministic. Prefer setting `required` in the field-map over disabling the empty case.

## Domain / stateful preconditions (spec + viewpoint only — no DB/API)

Field values are not enough when a test needs a **business state first** — "a paid order of user A",
"a valid unused reset token ≤24h", "the session-storage buffer holds the entered values". Declare a
**state-recipe** in `qa/data-factory/recipes.yaml` under `resources:` — each resource has an `entity`,
a **`provenance`** (`prior-flow` | `fixture` | `client-storage` | `server-context` | `manual` |
`api` | `db`), optional `dependsOn`, `desiredState`, `lifecycle` (`reusable`/`consumable`/`expiring`/
`leased`/`mutable-state`/`unique-per-run`), and `with:` values — plus `bindings:` (`orderId: ${order.id}`).
**Auto-draft it — don't make the QA hand-write it.** When the spec describes a precondition (or you saw
a `PRECONDITION-UNDECLARED` finding), run **`sungen data state --scaffold <unit>`**: it drafts a recipe
skeleton from the spec's precondition sentences (one resource per signal, provenance guessed, entity
left as `TODO`). Then **fill each `entity` + `desiredState`/`bindings` from the spec and confirm the
domain specifics with the QA (`AskUserQuestion`)** — never invent domain values.

Run **`sungen data state`**: it validates the graph (cycles, dangling deps, bindings) and renders a
standardized **manual precondition** block — tagged **`@manual:data-setup`** when it can't be
auto-provisioned — to weave into the scenario's `Background`. **This works with only spec + viewpoint:
provenance is usually `prior-flow`/`fixture`/`manual`, NOT DB/API.** If a datasource is later added and
a resource uses `api`/`db` provenance (+ a `provider:` endpoint), the *same* recipe auto-provisions — no
rewrite. For a **journey where the same data flows across screens** (setup → confirmation → complete),
add `shared: { entity, screens: [...] }` so one dataset is used across all of them.

## One QA-facing door: `/sungen:create-test`

QA never needs to remember which `sungen data …` sub-command to run. **`/sungen:create-test` is the
single entry** — it standardizes fields, validates, cross-checks, and drafts any stateful precondition
inline. The `sungen data gen|lint|validate|crosscheck|state|provenance|fixture` commands are the
**deterministic plumbing** the create-test loop (and CI) drives; run them directly only for a targeted
re-standardize. `/sungen:create-data-test` is the same pipeline on demand (re-standardize after editing
a field-map/`.overwrite`, or a unit authored before Data Factory) — not a step you must invoke after
create-test.

## Cross-artifact check — testcase ↔ data agree

`data validate` checks the data alone; **`sungen data crosscheck`** checks the testcase and its data
AGREE **and are COMPLETE**: no dangling `{{var}}`; a cross-screen `shared` dataset is identical across
the journey; a declared state-precondition is surfaced; **and — even when you author `@cases` by hand
instead of merging `data gen` — the authored values COVER the BVA the field-map's constraints require
and the login experience checklist**:
- **`BVA_UNCOVERED`** — a bounded dimension is missing a boundary value. Classic miss: `email
  minLength: 5` → you must have an email of length **4 (invalid), 5, 6**; a URL/code of length 5 does
  NOT count (matched by type). Add the missing values (the exact BVA is in `data gen`'s output) or waive.
- **`EXPERIENCE_UNCOVERED`** — a login/auth unit is missing credential-state scenarios: **soft-deleted**,
  locked, unverified, password-expired, **valid-with-surrounding-whitespace** (trim → accept). Add each
  (a seeded account in that state) or waive per spec.

Treat these as MUST-ADDRESS in the create-test loop — they are exactly the gaps a QA reviewer catches.
Together `data validate` + `data crosscheck` are the **Data Gate + Cross-artifact + Coverage Gate** (the
`data-only` profile). Every `data gen` also stamps a `_fingerprint` (catalog+generator+context hash).

## Boundary completeness + trim

Boundary Value Analysis is **complete by construction**: for every bounded dimension in the field-map's
`constraints` — including ones the field-type's catalog didn't model (e.g. an **email `minLength`**) —
the generator auto-emits the full set **min-1 (invalid) · min · min+1 · max-1 · max · max+1 (invalid)**.
So a spec "email min 5" yields length 4 (invalid), 5, 6 — don't hand-add them. Text fields also carry a
**leading/trailing-whitespace** case (trim behaviour: the server must trim then accept, or reject per
spec — confirm). If `data validate`/the QA still finds a missing boundary, it means the constraint was
absent from the field-map — add it there.

## Login / authentication screens — credential-state cases (experience)

A login screen is not just field-format validation. The catalog carries an **experience checklist**
(`auth-account-states`) of credential/account STATES a login must cover — each a scenario, most needing
a domain-state precondition (a seeded account in that state; declare it with a state-recipe →
`@manual:data-setup` Background):
- **active** (happy path) · **wrong-password** · **non-existent** (same generic error — anti-enumeration)
- **soft-deleted** (must NOT authenticate — real-world bug precedent) · **locked/suspended** ·
  **unverified-email** · **password-expired** · **valid-with-surrounding-whitespace** (trim → accept).
When the unit is a login/auth screen, generate these as scenarios (confirm the exact outcome/message
code with the spec). Do not assume the outcome — ask the QA for states the spec doesn't pin.

## Weaving into scenarios

When a `.feature` exists, express invalid/boundary sets as data-driven `Scenario Outline` + `Examples`
(`@cases`) referencing the values — see `sungen-gherkin-syntax`. This keeps one logic + many values
instead of copy-pasted scenarios, and each row carries its `CHK-*` id for traceability.
