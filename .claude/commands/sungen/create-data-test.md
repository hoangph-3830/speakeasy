---
name: create-data-test
description: 'Generate standardized test-data for a screen/flow from the Data Factory catalog — valid/boundary/invalid values with CHK-* traceability (no free-form guessing)'
argument-hint: [screen-or-flow-name]
allowed-tools: Read, Grep, Bash, Glob, Write, Edit, AskUserQuestion, Skill
---

**Input**: A screen or flow name (e.g., `/sungen:create-data-test login`), OR **no name** to do every unit (with a confirmation — see ALL mode). Load the `sungen-data-factory` skill for the full method.

> **When to use.** `/sungen:create-test` already standardizes field test-data via Data Factory **inline** (its step 5.3), so you normally do **not** need this command as a follow-up. Use it **on demand** to *re-standardize*: after you edit a field-map or the `.overwrite`, for a unit authored before Data Factory existed, to refresh error-code mappings, or to (re)generate across many units at once (ALL mode). It MERGES into existing test-data (never clobbers).

## Role

You are a **Senior QA Engineer** producing **test data to a standard**, not by guessing. You do NOT invent values field-by-field. You follow the 4-source method (spec rules → field-type catalog → design technique → DB mapping), driven by the **Data Factory** capability driver. Values come from the catalog (`common/` + the project's `.overwrite`), each tagged with a `CHK-*` id that traces back to the spec. Where the catalog cannot cover a project-specific field, you **ask the QA** — you never fabricate domain data.

## Preconditions

1. **Availability** — Data Factory is **bundled with core** (no `capability add` needed); its CLI (`sungen data …`) is available out of the box. Just continue.
2. Determine mode from `$ARGUMENTS`:
   - A **name** given → single-unit mode (that screen/flow only).
   - **No name** → **ALL mode** (see below — always confirm first).

## ALL mode (no name) — confirm before doing everything

If no name was given, the user wants **all** screens/flows — but this is wide, and a missing name is often a typo (they meant `create-data-test home`). So:

1. List every unit under `qa/screens/*` and `qa/flows/*`, and classify each as **new** (no `test-data/<name>.yaml` yet) vs **would-overwrite**.
2. Show the list and use `AskUserQuestion` to confirm: *"Generate test-data for ALL N units? (X new · Y would overwrite)"* — default to **No**.
3. Only proceed when the user confirms. Then run the per-unit flow below for each (the CLI `sungen data gen --all` also enforces this confirm; `--yes` after the user agrees).

## Per-unit flow (single unit, or each unit in ALL mode)

Auto-detect: `qa/flows/<name>/` → flow; else `qa/screens/<name>/` → screen. (API units carry their contract in `apis.yaml` — data there is `@cases` in create-test, not this command.)

### 1. Read the sources (Nguồn 1 + 4)

Read `qa/<screens|flows>/<name>/requirements/spec.md` and `test-viewpoint.md`. For **each input field / parameter**, extract:
- data **type** (map to a catalog field-type: email · password · phone · string · number-integer · number-float · currency · postcode · katakana …),
- `required`, format, **min/max** length or value,
- the real **error/message code** the spec shows when a rule is violated,
- any **DB mapping** (unique, cross-entity dependency) noted in the spec.

Viewpoint tells you which fields are high-priority → give them the fullest data.

**No input fields → nothing to generate.** If the unit is a pure navigation / listing / capture-compare flow (e.g. a home-shopping browse flow) with **no user-input fields or parameters**, Data Factory has nothing to standardize. Report *"`<name>`: no input fields → nothing to generate"* and stop for this unit — do **not** write an empty `qa/data-factory/<name>.fields.yaml`. (Its scenarios are covered by create-test's data, not by field-level test data.)

### 2. Write the field-map — `qa/data-factory/<name>.fields.yaml`

This is the input the deterministic generator expands. One entry per field:

```yaml
screen: <name>            # or: flow: <name>
fields:
  - name: email
    type: email
    required: true
    unique: true                                   # fresh-per-run (register email won't collide)
    constraints: { maxLocal: 64, maxTotal: 254 }   # project's real limits
    errorMap: { M13: E1042, M02: E1001 }            # catalog placeholder → project's real code
  - name: password
    type: password
    constraints: { minLength: 8, maxLength: 32 }
```

**`unique: true`** — set it for a field that must be **different every run** (register email/username that can't clash with a record a previous run created). The valid value then gets a runtime `{{$timestamp}}` (email → `user+{{$timestamp}}@…`), so each run is fresh — no cross-run cache, no order-dependent flakiness. Boundary/invalid values stay literal (they test rules, not record creation).

Map every placeholder error code you can (`errorMap`) to the spec's real code. Leave a code unmapped only if the spec truly has none — the lint will flag it as an open point to confirm with BA/Dev.

**Complex forms — nested & dependent fields.** For a grouped/object field, give it **`fields:`** (sub-fields, expanded recursively; no `type` on the group). For a field whose rule depends on another, add **`dependsOn: { field: <other>, value: <v> }`** — it's recorded as a note so the scenario/`@cases` honor it (the generator doesn't resolve the runtime condition):
```yaml
fields:
  - name: address                       # group → nested object
    fields:
      - { name: city, type: string, constraints: { maxLength: 50 } }
      - { name: postcode, type: postcode, dependsOn: { field: country, value: JP } }
```

### 3. Blind spots — ask, do not invent

If a field's type is **not** in the catalog (a business enum, a cross-entity constraint, a project data model), **stop and ask the QA** with `AskUserQuestion` for: (1) the valid set, (2) invalid / non-existent values, (3) the error code when wrong, (4) dependencies on other fields. Record the answer in `qa/data-factory/project/<entity>.yaml` for reuse. Never fabricate domain values.

### 4. Lint the coverage

Run (local-first): `[ -x ./bin/sungen.js ] && ./bin/sungen.js data lint --screen <name> || npx sungen data lint --screen <name>`. Fix what it reports: add missing boundary values, resolve `UNRESOLVED` items (supply the constraint), map `OPEN_ERRORREF` codes. The bar is the phuong-phap §6 checklist — every required field has valid + boundary + invalid, one invalid per rule.

### 5. Generate the test-data

Run (local-first): `[ -x ./bin/sungen.js ] && ./bin/sungen.js data gen --screen <name> || npx sungen data gen --screen <name>`. If `test-data/<name>.yaml` already exists it **merges** the standardized field data in — it **replaces only the field keys it owns and preserves every other key** (create-test's data, flow-namespaced keys, `@cases` datasets, overrides). It asks before merging (in ALL mode after they confirmed, add `--yes`; `--force` skips the prompt). The output is standardized `valid/boundary/invalid` values with `CHK-*` traceability.

### 5.5 Validate semantic correctness

Run (local-first): `[ -x ./bin/sungen.js ] && ./bin/sungen.js data validate --screen <name> || npx sungen data validate --screen <name>`. This is stronger than lint (coverage): it checks every generated value is **semantically** correct — each `valid`/`boundary` value actually satisfies the constraints, and each synthesized `invalid` value actually violates them (a value in the `valid` set that breaks `maxLength`, or a "below min" case that landed inside the range, is a `VALUE_NOT_VALID` error). Fix any `error`; `INVALID_LOOKS_VALID` warns are a prompt to confirm the value is invalid for a non-length reason (charset/format). Each generated value also carries an `expected: valid|invalid` field in the test-data for traceability.

### 5.6 Domain / stateful preconditions (spec + viewpoint only — no DB/API needed)

If the spec describes a **stateful precondition** — an entity with relationships or a business state the test needs *before* it runs (e.g. "a paid order of user A", "a valid unused reset token ≤24h", "the session-storage buffer holds the entered values") — **auto-draft it: run `… data state --scaffold <name>`** to generate a recipe skeleton from the spec's precondition sentences (one resource per signal, provenance guessed, entity as `TODO`). Then **fill each `entity`/`desiredState`/`bindings` and confirm the domain specifics with the QA** (`AskUserQuestion`) — never invent domain values. (You can also hand-author the `resources:` graph in `qa/data-factory/recipes.yaml`.) Provenance is **not only api/db** — pick `prior-flow` / `fixture` / `client-storage` / `server-context` / `manual` (use `api`/`db` only when a datasource exists). Then run `data state --recipe <name>`: it validates the graph and renders a standardized **manual precondition** block (`@manual:data-setup` when it can't be auto-provisioned) to weave into the scenario's `Background`. This is the domain/stateful improvement that works with **only spec + viewpoint** — the testcase states the required state precisely instead of a vague note; it auto-provisions later if an api/db datasource is added (no rewrite). For a **journey where the same data flows across screens** (setup → confirmation → complete), declare a `shared: { entity, screens: [...] }` so one dataset is used across all of them (the Cross-artifact Gate then asserts they match).

### 5.7 Cross-artifact check (testcase ↔ data agree)

Run (local-first): `… data crosscheck --screen <name>`. It verifies the testcase and its test-data agree: no dangling `{{var}}` (every referenced var has a value), any cross-screen **shared** dataset is identical across the journey, and a declared state-precondition is surfaced in the feature. Fix any `error`. This is the `data-only` harness profile — `data validate` (values correct) + `data crosscheck` (values match the test); together they are the deterministic Data Gate + Cross-artifact Gate.

### 6. Weave into scenarios (if create-test already ran)

If the `.feature` exists, turn the generated invalid/boundary sets into data-driven `Scenario Outline` + `Examples` (`@cases`) referencing the values — following `sungen-gherkin-syntax`. Weave any `data state` precondition block into the `Background`. Otherwise leave the standardized `test-data/<name>.yaml` for `/sungen:run-test` to consume.

## After running

Use `AskUserQuestion` to offer the next step: review the generated data, run `/sungen:create-test <name>` (if scenarios don't exist yet), or `/sungen:run-test <name>`.
