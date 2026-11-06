---
name: sungen-harness-audit
description: 'How to read `sungen audit` output and repair test-design findings. Auto-loaded by the design orchestrator.'
user-invocable: false
---

## What `sungen audit` measures

`sungen audit --screen <name>` runs deterministic sensors over `features/<name>.feature` + `requirements/test-viewpoint.md` and writes `.sungen/reports/<name>-audit.json`. It is the **gate** the orchestrator repairs against. Run with `--json` to parse it.

### Report shape (key fields)
```jsonc
{
  "score": { "overall": 3.9, "coverage": 0.4, "businessDepth": 0.18, "balance": 0.5, "traceability": 0.7 },
  "gateStatus": "PASS" | "FAIL",
  "gate": { "pageType": "ecommerce-list", "themesCovered": 2, "themesTotal": 5,
            "gaps": [ { "theme": "cart-correctness", "keywords": [...] } ] },
  "depth": { "businessCriticalShallow": 9, "businessCriticalTotal": 11,
             "shallowBusinessCritical": [ { "name": "...", "category": "PRODUCT" } ] },
  "balance": { "imbalanced": true, "coreCount": 11, "secondaryCount": 22, "byBucket": {...} },
  "duplicates": { "clusters": [ { "sameDataLikely": false, "scenarios": [...] } ] },
  "trace": { "mappedRatio": 0.4, "note": "..." },
  "findings": [ "GATE: ...", "DEPTH: ...", "BALANCE: ...", "TRACE: ..." ]
}
```
- **`overall` score is business-weighted** (coverage 0.4 + businessDepth 0.3 + balance 0.15 + traceability 0.15). It is intentionally strict on business value — a high count with shallow business coverage scores low. Don't optimize the count; optimize coverage + depth.
- Exit code **2** when `gateStatus == FAIL` (usable in CI / loop).

## Finding → repair mapping

| Finding prefix | Meaning | Repair action |
|---|---|---|
| **GATE** | a critical theme for the page-type has no covering scenario | Generate scenarios for that theme. **If cross-screen** (cart-correctness, product-detail-consistency, filter-result-correctness, multi-item cart) → do NOT fake it on a single screen; plan a **flow** (`/sungen:add-flow`) and record the deferral. |
| **DEPTH** | business-critical scenarios assert only visibility/navigation | Replace `Then User see [X] page/section` with **observable data assertions**: `Then User see [X] with {{value}}`, `Then User see [T] table match data:`. Capture real expected values into `test-data.yaml`. |
| **BALANCE** | secondary viewpoints (UI/validation/security) outweigh business-core | **Stop expanding** secondary viewpoints; generate the missing business-core scenarios first. Do not add more subscription/UI variants while core is thin. |
| **TRACE** | scenarios use ad-hoc `VP-<CAT>-NNN` codes not linked to the viewpoint-overview | Make each scenario map to a viewpoint-overview id (align category codes, or add a mapping comment). |
| **ORACLE-URL-ONLY** | every `Then` in the scenario is a page/URL assertion — it proves a path was pushed, not that the page rendered; on a permission/guard scenario a late 403 redirect turns it falsely green | Add `anchor:` to the page selector (one line per screen; every `see [X] page` on it becomes a content oracle) or a content assertion after the page step. Never delete the page step to silence it. |
| **DEFECT-REF-MISSING** | a `@known-defect:DEF-NNN` tag names no file in `qa/defects/` | Draft the record (`sungen defect draft --screen <name> --scenario "<title>"`) or correct the id. Never drop the tag to silence it — the scenario still asserts a live defect. |
| **DEFECT-ORPHAN** (info) | an open defect record names a scenario this unit no longer has | Update the record's `scenarios:` to the renamed title, or close the record if the scenario was removed on purpose. Nothing in the suite to repair. |
| **UNIVERSAL** | a universal theme (error/empty-state, accessibility) is absent | Low priority — add if in scope; otherwise note as out-of-scope with reason. |

## P5 steps for deep cross-screen / list coverage

Use these when repairing GATE/DEPTH findings for the hard viewpoints (cart/detail/filter correctness). They need **runtime data mode** (the default).

- **Capture a value to compare across screens** (product-detail-consistency, cart-correctness):
  ```gherkin
  When User remember [Product Name] text as {{selected_product_name}}
  And User remember [Product Price] text as {{selected_product_price}}
  And User click [View Product] link
  Then User see [Detail Product Name] header with {{selected_product_name}}
  And User see [Detail Product Price] text with {{selected_product_price}}
  ```
  `remember` stores the element's text/value at runtime; later `{{var}}` resolves to it. This proves the detail/cart shows the SAME product, not a random one.

- **Assert every item in a result matches** (category/brand-filter-correctness):
  ```gherkin
  When User click [Women] link
  And User click [Dress] link
  Then User see all [Result Product Name] contain {{selected_category}}
  ```
  `see all [X] contain {{v}}` asserts EVERY matching element contains the value → "all displayed products belong to the selected category/brand", not just one.

> Cross-screen flows (home → detail/cart): **automate the journey as a flow** (`/sungen:add-flow`) — it runs as one test, so it is automatable. Do **not** keep a full `@manual` duplicate of it on the screen (a non-running dead copy that `sungen audit` flags as `MANUAL-AUTOMATABLE` and that inflates nothing — deferred business-critical is reported as `DEPTH-DEFERRED`). The screen keeps its screen-contract; the flow owns the cross-screen depth. `@manual` is for genuine judgment / missing-capability only, tagged `@manual:Mx`.

## Repair loop rules

1. **Budget = 3 rounds.** Re-run `sungen audit` after each repair; track score delta.
2. **Stop when** `gateStatus == PASS` AND `findings` empty — or budget exhausted.
3. **Never fake a pass.** A shallow `see [Cart] page` does not satisfy `cart-correctness`. If a gap is genuinely cross-screen or needs capabilities the DSL lacks (e.g. capture an element value to compare elsewhere), **report it as a residual gap / flow item** instead of forcing a green gate.
4. **EP/data families are OK.** A `duplicates` cluster with `sameDataLikely=false` is an intentional equivalence-partition family (e.g. many invalid-email cases) — keep it; only collapse `sameDataLikely=true` exact duplicates.
5. **Advisory findings — surface, don't gate.** `MANUAL-REASON-MISMATCH` → fix the scenario's `@manual:Mx` code (so the planner recommends the right driver) during repair. `CAPABILITY-SUGGESTION` → **present it to the user as a next-step option** (e.g. "N @manual could be automated — `sungen capability add api db`?"), **recommend-only — never auto-install**. `VERIFICATION-OUT-OF-SCOPE (api|db)` → the unit uses `@api`/`@query`/`@requires:api|db` verification the project's recorded **test type** never opted into (E2E/UI-only per `capabilities.yaml`, and the viewpoint doesn't ask for it). During repair: **rewrite the oracle to be UI-observable** (or downgrade the deep check to an `(optional deep check: …)` note); only keep the API/DB verification if the project genuinely tests that layer — then record it (`verification: [ui, api]` / `sungen capability add`). Keep API/DB-in-E2E to the ≤20% band. Neither of these fails the gate.

6. **A gate that contradicts the spec's intent is a question, not a repair.** When the only way to
   satisfy a finding is to change what the spec deliberately asked for — widening an entry variant the
   spec keeps light so `FLOW-SCOPE-CREEP` stops firing, renaming a title around a trigger word so a
   claim rule stops firing, adding an assertion the spec never demands — **stop and put the conflict
   to the user** (`AskUserQuestion`: the finding, the spec passage it collides with, the two ways out).
   A score reached by editing toward the gate is not a measurement of the suite; it is a measurement
   of how well the gate was gamed, and the human reviewer will read it as over-testing or a false
   claim. Where the harness provides a declaration route — `flows:` with a per-flow `outcome:`, a
   `status: deferred` with a reason, `@manual` naming the gesture — that route is the fix, never the
   scenario.
7. **`--accept-viewpoint` is a QA decision. Never run it.** `atomicLedger` and `traceability` are
   evidence only while `test-viewpoint.md` is independent of what was generated. `sungen next` lists
   the acceptance as a `⚖ decide` step: present it (the diff of declared ids, what was added, what was
   dropped) and let the QA run the command. An agent that accepts the yardstick it just wrote makes
   both axes read 100% by construction — and the spec's Exception Flow it forgot stops being missing
   from anything.

## Discovery / fallback tree (when input is limited)

```
spec.md good enough?       → YES: Spec-first
  │ NO
source code available?     → YES: Source-first (mine behavior from code)
  │ NO
similar legacy testcases?  → YES: History-first
  │ NO
risky domain + defects?    → YES: Defect-first
  │ NO
→ ask QA; if QA hasn't responded → OUTPUT with an explicit ASSUMPTION LIST (do not stall)
```

See `docs/orchestration-spec.md` for the full flow and `docs/spec/sungen_refactor_spec.md` for the design rationale.
