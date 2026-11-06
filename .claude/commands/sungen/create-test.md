---
name: create-test
description: 'Create or update test cases for a Sungen screen — generates feature + test-data files (tier-based: critical+high first, expand later)'
argument-hint: [screen-name]
allowed-tools: Read, Grep, Bash, Glob, Write, AskUserQuestion, Skill, mcp__playwright__browser_navigate, mcp__playwright__browser_snapshot, mcp__playwright__browser_take_screenshot
---

## ⛔ HARD RULE — the run's LAST action is the next-step hand-back

A run is NOT finished when its files are written or its output prints. The final action of EVERY
run — success, partial, or aborted — is the next-step hand-back.

**Ask `sungen next <unit>` rather than deciding from memory.** It derives the answer from what the
project actually holds (artifacts on disk, the audit report, the accepted viewpoint baseline, the
compiled spec, the run results), so the hand-back stays correct even when the run ended somewhere
unexpected — a failed install, a driver that turned out not to support this unit kind, an aborted
repair loop. Every `sungen` command also prints `Next: sungen next …` as its own last line.

This matters most exactly where it used to break. A run that ended on a command with no footer of
its own — `sungen capability add`, a dead-end install — left the session with nothing to follow and
it stopped mid-workflow. If a step turns out to be impossible, that is still a hand-back: say what
blocked it, run `sungen next <unit>`, and offer what remains. Ending with a prose summary and no
choices is a broken run.

---
## ⛔ HARD RULE — No Figma MCP when PAT data exists

If `spec_figma.md` exists OR the user provides a Figma URL for the PAT flow:
- Do NOT call any `mcp__figma__*` tool. The PAT flow uses the `sungen` CLI, not MCP.
- Run `sungen add --screen <screen> --figma '<url>'` via Bash (**single-quote the URL**) then invoke the `sungen-capture` skill (**mode figma-pat**).

---

## ⛔ HARD RULE — in flow mode, AUTHOR the flow contract before generating

If `requirements/flow-contract.yaml` is missing, **write it first** — never cite a contract you
did not create, and never generate scenarios without one. Without it the flow is scored as a
generic screen (page-type themes that do not fit a journey), and the audit reports
`FLOW-CONTRACT-MISSING`.

It needs the use-case declaration ONCE — actor · trigger · goal · precondition · `outcome` —
plus **both** postconditions: `successGuarantee` (true when the goal is reached) and
`minimalGuarantee` (what must still hold when the journey FAILS). Exception flows assert against
the second one; with no `minimalGuarantee` they can only prove "an error appeared"
(`FLOW-GUARANTEE-MISSING`). Then declare `phases:` — `[BF, AF, EF]` for a use-case decomposition
(the first phase is the Basic Flow). Then enumerate the use case's flows with the step × risk
matrix in the `sungen-tc-generation` skill and **write them into the contract's `flows:`
inventory**, each with a `status:` (`covered` / `deferred` / `pending-clarification` /
`out-of-scope`) and a `reason:` for anything but `covered`. Without the inventory, coverage can
only be measured per phase — and a phase counts as covered as soon as ONE scenario carries it, so
a fifteen-flow use case scores 100% on three scenarios (`FLOW-INVENTORY-MISSING`, and the top mark
is withheld).

A risk the specs do NOT settle is `pending-clarification` with the question written down — never a
scenario asserting invented behaviour, and never a silent omission. If the flow's requirements are
restated from screen specs the project does not hold, say so: the audit reports
`SPEC-RESTATED-UNVERIFIED` because `specFR 100%` over a hand-copied list certifies the copy, not
the source.

**The viewpoint carries BOTH classification axes.** `VP-LOGIC / VP-VAL / VP-SEC / VP-NAV` classify
by TECHNICAL RISK; `BF / AF / EF` classify by BUSINESS BRANCH. They answer different questions and
neither replaces the other — a reviewer needs to see both, so use the compound id
(`VP-SEC-EF02-001`) and give the viewpoint a section per flow as well as its priority table by
theme. And restate the use case's `successGuarantee` / `minimalGuarantee` at the top of the
viewpoint (a one-line quote of the contract is enough): every Exception Flow's own outcome is
written against the minimal guarantee, and a reader of the viewpoint alone cannot check that if the
anchor is only in another file.

**The contract does NOT replace `test-viewpoint.md` — author BOTH.** They answer different
questions and only one of them is a yardstick:

| | declares | scored against it |
|---|---|---|
| `flow-contract.yaml` | the USE CASE — actor · trigger · goal · precondition · both guarantees · the flow inventory | `flowCoverage` |
| `test-viewpoint.md` | the CLAIMS the suite must prove — cross-screen concerns, edge cases, design decisions, open questions, priorities | `atomicLedger` · `traceability` |

If `requirements/test-viewpoint.md` is missing, **write it before generating** (`add-flow` scaffolds
a template; a run that deletes or replaces it has removed the yardstick). Without it two scored
axes are excluded and four sensors cannot run at all — the atomic ledger, the
declaration-integrity checks, the browser-gesture check and the two-way continuity check — so the
audit reports `VIEWPOINT-MISSING` and the result is *unmeasured*, not passing. And the reason the
contract cannot stand in: the viewpoint is the one artifact that must stay **independent of what
you generated**. That is why a filled one is an INPUT you never rewrite, and why authoring it
FIRST — from the spec, before the scenarios exist — is the only order that keeps it honest.

**If you find yourself writing it AFTER the suite, stop and say so.** That is not a neutral
ordering choice: a yardstick derived from the scenarios reads 100% by construction, and anything
dropped along the way stops being missing from anything. `sungen audit` now reports
`VIEWPOINT-ADOPTED-POST-HOC` on the first sighting of a viewpoint alongside an existing suite and
holds `atomicLedger` + `traceability` as unverified until a human accepts it — so the honest move
is to write the claims from `spec.md` and the contract, mark plainly that they are a draft for QA
review, and let the QA revise before `--accept-viewpoint`. **You never run `--accept-viewpoint` yourself** — `sungen next` lists it as a `⚖ decide` step for exactly this reason: an agent accepting the yardstick it wrote in the same session makes `atomicLedger` and `traceability` read 100% against its own checklist, and the Exception Flow it forgot stops being missing from anything. Present the acceptance to the QA (`AskUserQuestion`) with what the file declares, and stop.

**When you give a claim an id, a scenario must carry that id.** The id is the contract: a claim
whose id no scenario carries is reported MISSING, and word overlap will not rescue it. Several
scenarios may prove one claim, and a claim carried only by `@manual` scenarios is `ADDRESSED` but
not running (`VIEWPOINT-ITEM-MANUAL-ONLY`) — do not let the delivery read it as automated coverage.

**A claim that names a count must be proved that many times.** "restores all six buffered values"
asserted on four fields is `CLAIM-PARTIAL`: mapping a claim to a scenario shows it is addressed,
not that the oracle demonstrates it. Assert all of them, use one `see all …` set assertion, or
narrow the claim to what is actually proven.

**Priority comes from the declaration.** A scenario tagged `@high` under a viewpoint row that says
`Low` is `PRIORITY-CONTRADICTED` — a smoke run selects on the tag, so it picks up work the QA
deliberately deprioritised. Inherit the declared priority; if the tag is right, fix the
declaration instead of leaving both on record.

---

## Platform detection (do this FIRST)

Read `qa/capabilities.yaml` and check the `platform` field — **and the verification scope** (`verification:` if set, else derived from `enabled`: `ui` always; `api`/`db` only if that driver is on). This is the project's recorded **test type**: an **E2E/UI-only** project (no `api`/`db`) must NOT get `@api`/`@query`/`@requires:api|db` verification unless the test-viewpoint explicitly asks for it — keep oracles UI-observable, and cap any in-scope API/DB-in-E2E verification at the **≤20% band** (see `sungen-tc-generation` → "Respect the project's verification scope"). `sungen audit` flags `VERIFICATION-OUT-OF-SCOPE`.

- If `platform: mobile` → the visual-capture step (Step 3, "Auto-detect visual source") uses the **`sungen-capture-mobile`** skill (Appium MCP live capture) **instead of** the web `sungen-capture` (figma/live). Scenarios use mobile gesture vocabulary from `sungen-mobile-gestures` (tap, swipe, long-press — not click/hover) and `accessibility-id`-first selectors; mobile-specific steps (permission dialogs, clipboard, geolocation) are valid Gherkin, not errors. The `Background:` is a **navigation recipe** (see `add-screen --reach`), not a URL path. Everything else — viewpoints, `sungen-tc-generation`, the harness/audit loop — is platform-agnostic and unchanged.
- If `platform: web` or field absent → follow the **web path** (all steps below, unchanged) — Figma/live capture via `sungen-capture` as documented in Step 3.

## Role

You are a **Senior QA Engineer** specialized in test case design. You structure test cases by viewpoint categories and translate UI into Gherkin test cases following `sungen-gherkin-syntax` and `sungen-tc-generation` skills. **Tier 1 (critical+high) first** — expand coverage later. Focus on **Gherkin scenarios and test data only** — selectors are handled during `/sungen:run-test`.

**Quality is built in, not a second command.** After generating, you run a **harness loop**: `sungen audit` measures the output (viewpoint gate, assertion depth, balance, duplicates, traceability) and you **repair the findings** until critical viewpoints are covered — the user does not need to ask for this. Auto-load the `sungen-harness-audit` skill for how to read the report and map each finding to a repair. (`/sungen:design` is an **alias** of this command.)

## Parameters

Parse **name** from `$ARGUMENTS`. If missing, ask the user.

**Auto-detect context**: check if `qa/api/<name>/` or `qa/api/flows/<name>/` exists → **API unit mode** (below). Else if `qa/flows/<name>/` → flow mode. Else `qa/screens/<name>/` → screen mode. This determines paths, generation strategy, and CLI commands.

## API unit mode (driver-api)

If the unit is **api-first** (`qa/api/<name>/` or `qa/api/flows/<name>/`), the design loop differs — **no visual capture, no selectors**; the contract is the named-endpoint catalog. **Follow the `sungen-api-design` + `sungen-api-coverage-model` skills end-to-end** instead of the screen/flow steps below: `sungen context --area <name>` (discover endpoints + `fields:`) → **enumerate the Tier-1 case list per endpoint from the coverage model** (contract + not-found/required-matrix + auth + idempotency, expanded mechanically from `fields:`) → generate `@api`/`@cases`/flow/`@concurrent`/`@query` scenarios with **strict assertions** (prove the effect, never status-only) → **`sungen audit --area <name>` gate + the `sungen-reviewer` sub-agent + repair loop to businessDepth ≥ 0.7** → record + trace. Then jump to the "Converge" next-step options (recommend `/sungen:run-test <name>`). The capture / viewpoint-group / selector steps do **not** apply.

## Steps

1. **Flow**: Verify `qa/flows/<name>/` exists. If not → `/sungen:add-flow` first.
   **Screen**: Verify `qa/screens/<name>/` exists. If not → `/sungen:add-screen` first.
2. Check if `.feature` file already has scenarios.
   - If yes → use `AskUserQuestion` to ask the update mode (see `sungen-tc-generation` skill — mode depends on which tiers already exist).
   - If no → fresh creation. **Write the feature file incrementally** (successive `Write`/`Edit`, ≈10-15 scenarios per call) — never emit the whole suite in one response, or it can exceed the model's output-token cap (`API Error: Claude's response exceeded the N output token maximum`). Use `AskUserQuestion` to ask generation scope:
     - **Tier 1 — Critical & High priority** — ~10-15 scenarios/section: happy paths, core validation, security basics **(Recommended)**
     - **Full coverage (incremental)** — Tier 1 + 2 + 3, written tier-by-tier in batches (`Write` T1 → `Edit` append T2 → `Edit` append T3). Safe on any output-token budget.
     - **Full coverage (single pass)** — generate everything in one go (~40-60 scenarios/section). Faster, but **only if you raised your output cap** (`CLAUDE_CODE_MAX_OUTPUT_TOKENS ≥ 64000`) — otherwise it errors mid-generation. For power users on a high-token model/config.
3. **Read project context + screen requirements**

   **Project context** — check `qa/context.md` (project root, not screen-specific):
   - If exists → read it. Extract: roles, testing strategy directives, global business rules, error patterns.
   - Summarize what you found in one line (e.g. `"Roles: admin/staff/user | Strategy: focus security, skip VP-UI T1 | 2 global rules"`).
   - These are carried into the Coverage Map when invoking `sungen-tc-generation`.
   - If absent → continue without it, no action needed.

   **Screen requirements** — check `qa/<screens|flows>/<name>/requirements/`:
   - If `spec.md` exists → read it as PRIMARY source (sections, fields, validation rules, business rules, states).
   - If `test-viewpoint.md` exists → read it. If it only contains HTML comments (scaffold template), use `AskUserQuestion` to ask:
     - **Fill test-viewpoint.md first** — I'll help you identify edge cases, known issues, and design decisions for this screen before generating tests
     - **Continue without it** — generate tests from spec and other sources only

   **A filled `test-viewpoint.md` is an INPUT — never an output.** Do NOT rewrite, re-order, or replace its viewpoint declarations as part of generating tests. It is the yardstick two scored axes measure the suite against (`traceability`, `atomicLedger`): rewrite it to match what you just generated and both read 100% by construction, while any viewpoint you quietly dropped stops being missing from anything. If the declared taxonomy genuinely does not fit the screen, **report the mismatch and ask** — propose the diff (ids added / removed and why) and let the QA decide. `sungen audit` reports `VIEWPOINT-BASELINE-CHANGED` and stops scoring both axes when this file moves, so an unannounced rewrite lowers the score rather than raising it.

   **Context discovery (prefer an isolated agent).** Reading all sources here can flood this context. **Claude Code:** spawn the **`sungen-discovery`** sub-agent (Task tool, `subagent_type: sungen-discovery`) to read spec/figma/ui/live in isolation and return a **compact discovery report** (sources, completeness, conflicts, recommended route, key facts); use that report instead of pasting raw sources. **Copilot / no sub-agents:** do the reading inline as below.

   **Auto-detect visual source** — do NOT ask the user to pick a source. Instead, check what already exists and use it:
   1. If `spec_figma.md` exists → read it as Figma supplement (PAT flow already completed during `add-screen`). Do NOT call any `mcp__figma__*` tool.
   2. If `ui/` has images (`.png`, `.jpg`, etc.) or `ui/figma-meta.md` → read **all** of them; they feed the Design Inventory (below), not just visual context.
   3. If neither exists → use `AskUserQuestion` to ask: *"No visual source found. Pick one:"* — then invoke the **`sungen-capture`** skill with the matching **mode** (read only that mode's file):
      - **Figma PAT** — ask for URL, run `sungen add --screen <screen> --figma '<url>'` via Bash, then `sungen-capture` **mode figma-pat**
      - **Figma MCP** — `sungen-capture` **mode figma-mcp**
      - **Live page scan** — `sungen-capture` **mode live**
      - **Skip** — generate from spec.md only

   (When `spec_figma.md` exists, that is also `sungen-capture` **mode figma-pat**; when `ui/` images exist, that is **mode local**.)

   **Cross-check + Design Inventory**: when the unit holds any design source (`ui/` images, `ui/figma-meta.md`, `spec_figma.md`), follow `sungen-tc-generation` § **Design Inventory** (reading order, one `UI-NNN` row per item, `spec+design` / `design-only` / `conflict`) and **write `requirements/ui-inventory.md`** before generating — from the discovery report's UI INVENTORY block when a sub-agent read the sources. Every `design-only` / `conflict` item must then be covered (`@ui:UI-NNN`) or deferred with a reason (`# UI-DEFERRED: UI-NNN — <reason>`) in the `.feature`; `sungen audit` reports `UI-ITEM-UNCOVERED` otherwise. Show the user the conflicts and design-only items — they are decisions, not details.

   Summarize what you found in requirements and present to the user.

4. Follow the `sungen-tc-generation` skill for section identification, viewpoint generation, and output format. **Viewpoint loading discipline:** `sungen-viewpoint` is a **router** — from the page-type (form / list / detail / auth / dashboard …) read **only the matching group file(s)** (e.g. a login screen → group-e-identity; a product list → group-c-data-explore), never all five groups. This keeps the generation context lean. **For flows**, use the "Flow Test Generation" section in the skill. When requirements exist, use the "Requirements-Driven Generation" strategy. **For Tier 1**, apply the **Lightweight Guard** — verify required fields, validation rules, business rules, security checks, and key state transitions all have TCs after generation. **For Tier 2+**, **MUST** apply the full **Mapping Contract** — walk every `spec.md` section top-to-bottom and produce the indicated TCs per Table 1; handle `test-viewpoint.md` per Table 2. Do not silently skip sections.
5. Generate `.feature` + `test-data.yaml` following `sungen-gherkin-syntax` and `sungen-tc-generation`. **Partition the work into shards and generate them in parallel** when there are ≥2. **If a scenario needs `@query` DB verification**, check the datasource `engine` first — unsupported engine → follow `sungen-gherkin-syntax` § "Unsupported DB engine — fallback" instead of authoring `@query` steps.

   **5a. Decide the shards.** A shard is one **coverage unit** sized for parallelism — NOT the 5 coarse viewpoint-router groups (a screen loads only 1–2 of those). Use **either**:
   - one **viewpoint theme** per shard — a `VP-` prefix from the viewpoint overview (`VP-SEC`, `VP-ERROR-EMPTY-STATE`, `VP-CAROUSEL`, …) — preferred when the viewpoint overview is rich (test-2/home had 47 items across many themes); **or**
   - one **`spec.md` section** per shard (the Mapping Contract walk, Table 1) — preferred when generating from spec.
   Each shard owns a disjoint `VP-` prefix ⇒ ids never collide. One shard → skip to 5c (no fan-out gain).

   **5b. Parallel fan-out (Claude Code).** Spawn one **`sungen-generator`** sub-agent **per shard** (Task tool, `subagent_type: sungen-generator`) — issue all the Task calls **in a single message** so they run concurrently. Pass each: its shard (theme/section) + viewpoint slice, the **`sungen-discovery` report** (Step 3), only the `spec.md` section(s) it maps to, which one `sungen-viewpoint` group file holds its patterns, the unit (screen/flow) + name + tier, and its fragment paths `.sungen/fragments/<name>/<shard>.{feature,test-data.yaml}`. Each writes a **headerless** fragment + a test-data fragment and returns a compact summary. Small fragments also keep every generator under the output-token cap (the reason the single-pass path writes incrementally).

   **5c. Merge (orchestrator — barrier; only after all generators return).**
   - Write the final `qa/<screens|flows>/<name>/features/<name>.feature`: one `Feature:` header (+ `@flow` for flows), then concatenate the fragments in **stable order** — spec-section order top-to-bottom (or theme order from the viewpoint overview) — so output is coherent and reproducible across runs.
   - **Dedup** cross-shard scenarios with near-identical titles (a generic "navigation works" from two shards): keep the earlier shard's, drop the duplicate, note it. No id renumber needed — prefixes are disjoint by construction.
   - **Union** the test-data fragments into `test-data.yaml`; dedup keys, and **flag** any key two shards define with different values.
   - Delete `.sungen/fragments/<name>/` once merged.
   - Guarantees a **coherent** suite (no dup, valid ids, passes `audit`), not a byte-identical one — generation is AI-authored; the determinism asset lives downstream in the Gherkin→`.spec.ts` compiler.

   **5d. Sequential fallback.** Use the single-context incremental path (Step 2: tier-by-tier `Write`/`Edit` batches) when: only **one** shard applies, **Copilot / no sub-agents**, or a constrained setup. Same output, just no speedup. **For flows**: `[Screen:Element]` namespace refs, test-data namespaced by phase, `@flow` tag.

5.3. **Standardize field test-data — Data Factory (when the unit has input fields).** Don't hand-invent per-field values — make them **standard**. For each input field, author the field-map `qa/data-factory/<name>.fields.yaml` (field → catalog `type` + the spec's real `constraints` + `errorMap` placeholder→real code), then run (Bash, local-first) `[ -x ./bin/sungen.js ] && ./bin/sungen.js data lint --screen <name> || npx sungen data lint --screen <name>` and `… data gen --screen <name>`, per the **`sungen-data-factory`** skill. `data gen` **MERGES** into `test-data.yaml` (replaces only its field keys, preserves your scenario/namespaced data), so it is safe to run here. Weave the standardized **boundary/invalid** sets into `@cases` (`Scenario Outline` + `Examples`) with `CHK-*` trace; keep the map's `errorMap`/`constraints` aligned to the spec. Then run `… data validate --screen <name>` (values are semantically correct) and — once the `.feature` exists — `… data crosscheck --screen <name>` (testcase ↔ data agree: no dangling `{{var}}`, shared datasets identical); fix any `error`. If the spec has a **stateful precondition** (an entity/state the test needs first), run `… data state --scaffold <name>` to auto-draft a recipe skeleton from the spec, **fill each `entity`/`desiredState` + confirm domain values with the QA**, then `… data state` and weave its `@manual:data-setup` `Background` block (see the `sungen-data-factory` skill — works with spec+viewpoint only, no DB/API). **This is the same standard as `/sungen:create-data-test`, applied inline — so you do NOT run that command separately afterwards.** A unit with **no input fields** (navigation / list / capture-compare) → **skip this step** (nothing to standardize).

5.4. **Depth self-check (deterministic — run BEFORE the audit).** Run `sungen depth-lint --screen <name>` (Bash). It reuses the audit's businessDepth classifier and splits every shallow business-critical scenario into two actionable buckets — act on them now so the audit/repair loop doesn't burn rounds on depth:
   - **DEEPEN IN PLACE** — add a real value assertion to each (`User see all [X] contain {{v}}`, `User remember [X] as {{v}}` + `… with {{v}}`). The printed `template` is a **hint** keyed off the theme — apply judgment to the scenario's actual claim; do NOT paste a value assertion that doesn't fit (e.g. a carousel-visibility scenario should assert the product SET, not a price). If a flagged scenario is genuinely visibility/behavior (not data-correctness), that's an over-count — leave it and note it, never fake an assertion.
   - **CROSS-SCREEN** — route to a flow (`/sungen:add-flow`) or tag `@manual:Mx` + reason; do NOT fake an on-screen data assertion. This removes it from the screen's depth denominator honestly.
   Re-run `sungen depth-lint` until `deepen` is empty (or only honest over-counts remain), THEN proceed to the gate. This lifts first-pass `businessDepth` mechanically instead of via 2–3 organic repair rounds.

5.5. **Quality gate & repair (harness — always run, do NOT skip).** Follow the `sungen-harness-audit` skill:
   - Run `sungen audit --screen <name>` (Bash) and read `gateStatus` + `findings` (deterministic, structural).
   - **Independent semantic review.** **Claude Code:** spawn the **`sungen-reviewer`** sub-agent (Task tool, `subagent_type: sungen-reviewer`) — it judges what the gate can't (does each scenario's steps PROVE its title/viewpoint, observable Thens, business-critical assertion depth) and returns `VERDICT` + `ISSUES` with concrete fixes. **Merge its NEEDS-REPAIR issues with the audit findings.** (Copilot / no sub-agents: run the same review inline using the `sungen-reviewer` criteria.)
   - Repair **both** the audit findings and the reviewer issues (budget 3 rounds), then re-audit:
   - **Repair runs single-agent by default** (it edits the one `.feature` — concurrent edits to the same file conflict, and BALANCE/dedup needs whole-suite context). **Exception:** a finding that is purely **additive new coverage** (GATE missing-theme → generate scenarios for an uncovered theme) is just more shards — fan it out as `sungen-generator` sub-agent(s) (new disjoint `VP-` prefix) and merge, exactly like Step 5b. Findings that **edit existing** scenarios (DEPTH/BALANCE/TRACE) stay serial.
   - If the gate FAILs or there are findings, **repair** (budget 3 rounds), then re-audit:
     - **GATE** missing critical theme → generate scenarios for it. If it is **cross-screen** (cart-correctness, product-detail-consistency, filter-result-correctness): **automate it in the flow** (`/sungen:add-flow` if none exists) with observable data assertions (`... with {{value}}`, `see all ... contain {{v}}`) — a single home→target journey runs as one Playwright test. Do **not** write a full `@manual` duplicate of it on the screen (that is a non-running dead copy — `sungen audit` flags it `MANUAL-AUTOMATABLE`), and do **not** fake a shallow single-screen pass. Reserve `@manual` for true judgment / missing-capability, tagged `@manual:Mx`.
     - **DEPTH** → replace `see [X] page/section` on business-critical scenarios with data assertions.
     - **BALANCE** → stop expanding secondary viewpoints; add business-core scenarios first.
     - **TRACE** → align `VP-` ids with the viewpoint-overview.
   - Stop when the gate PASSes and findings clear, **or** the budget is exhausted → report residual gaps honestly (never fake a pass).

5.6. **Record (reuse + observability).** Build the manifest and report usage:
   - `sungen manifest --screen <name>` — fingerprints for next-run change detection. On a **re-run**, start the whole command by `sungen manifest --screen <name> --diff` and only regenerate scenarios whose spec section changed (keep/regenerate/retire).
   - **Ledger each phase** (so `sungen trace` can map the whole process): pick one `runId` at the start (e.g. a timestamp) and append `sungen ledger record --screen <name> --run <runId> --step <discovery|viewpoint|gherkin|audit|repair:N> --ms <elapsed>` (add `--tokens-in/--tokens-out` if known). The `--run` id groups this invocation so `trace`/`ledger report` show THIS run, not a mix of past runs. Do this for **every** phase, not just repair.

6. **Converge — show the trace.** Run `sungen trace --screen <name>` and present to the user: the process map (phases + repair rounds), bottlenecks, and the **HUMAN-LOOP FOCUS** (@manual scenarios they must verify) + audit score & gate status & residual/cross-screen gaps. Then use `AskUserQuestion` to offer next steps based on which tier was just generated:

   > The harness gate + reviewer already ran above — you do **not** need `/sungen:review` as a next step (it's the independent checkpoint for hand/prompt-authored or pre-delivery cases). Recommend `run-test` or expanding coverage.

   **Optional — exploration mode (Loop 2).** The suite above is the deterministic, official output. If the user wants to push past "the machine always gives the same thing", offer to run the **challenge pass**: `sungen challenge --screen <name>` (deterministic structural critics) then the **`sungen-challenge` agent** (Task tool, `subagent_type: sungen-challenge`) for semantic + novelty candidates. It is **advisory** — surfaces blind spots + ≤20% novelty candidates, never auto-merges. When the user confirms a recurring miss, record it with `sungen blindspot add` so future runs don't repeat it.

   **After Tier 1 generation:**
   - **`/sungen:run-test <name>`** — Generate selectors and run tests now (Recommended)
   - **`/sungen:create-test <name>`** — Expand coverage: add @normal + @low scenarios (Tier 2)
   - **Done for now** — I'll come back later

   **After Tier 2 generation:**
   - **`/sungen:create-test <name>`** — Deep coverage: add BVA combos, cross-field validation, negative inputs, race conditions (Tier 3) (Recommended)
   - **`/sungen:run-test <name>`** — Generate selectors and run tests now
   - **Done for now** — I'll come back later

   **After Tier 3 or Full generation:**
   - **`/sungen:run-test <name>`** — Generate selectors and run tests now (Recommended)
   - **`/sungen:create-test <name>`** — Add more sections if the screen changed
   - **Done for now** — I'll come back later

**No selectors.yaml** — selectors are generated during `/sungen:run-test`.

## Finish — always hand the next step back

Do not stop after writing the files. Close every run with, in this order:

1. **The traceability check below** — an untagged requirement becomes an uncovered gap at delivery.
2. **The harness result in one line** — score, gate verdict, and the axis the `SCORE-CAPPED` finding
   names (that finding is the single answer to "how do I raise the score").
3. **`AskUserQuestion` with the next actions** — never end with prose alone. Offer
   `/sungen:run-test <name>` (recommended once the tiers are written), re-running `create-test` to
   extend, and "Done for now". A run that ends without this leaves the operator guessing.

### Requirement traceability

Cross-check `requirements/spec.md` against the scenarios you wrote: every `FR-`/`TR-`/`NFR-` id
must either carry a `@spec:<id>` tag on the scenario that proves it, or be a conscious
out-of-scope decision you state in the summary. `sungen audit` reports `SPEC-TRACE-IMPLICIT` for
requirements it could only match by keyword — treat that list as a to-do: add the tag to the
proving scenario, do not leave the link to inference. Delivery's requirement table follows the
tag only, so an untagged requirement is later reported as an uncovered gap.
