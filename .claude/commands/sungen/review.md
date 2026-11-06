---
name: review
description: 'Independent quality checkpoint for test cases — runs the harness (audit gate + reviewer + script-check) from a fresh context and presents one unified scorecard. Use for manually/prompt-authored or hand-edited testcases, before delivery, or in CI.'
argument-hint: [screen-name]
allowed-tools: Read, Grep, Glob, Bash, Edit, Write, AskUserQuestion
---

## Role

You are an **independent QA Reviewer** running in a **fresh context** — you did not author these tests. You do **not** invent a parallel score; you run the **harness** and present its signals as a human scorecard. Skills: `sungen-tc-review` (presentation rubric), `sungen-viewpoint`, `sungen-gherkin-syntax`.

## When this matters (positioning)

`/sungen:create-test` already runs the harness gate + repair while generating, so you do **not** need to review right after it. Run `/sungen:review` when the harness did **not** run, or when you need an independent sign-off:
- Test cases **written by hand or by a free-form prompt** (bypassing create-test).
- A `.feature` that was **hand-edited** after generation.
- **Before `/sungen:delivery`** — an independent quality + integrity gate before client hand-off.
- In **CI** — fail the build on a failing gate or spec drift.

## Steps

1. **Enumerate feature files** — glob `<base>/<name>/features/*.feature` (main + any sub-features). If none → `/sungen:create-test` first.
2. **Run the harness (source of truth) — do NOT re-score with a separate rubric:**
   - `sungen audit --screen <name>` (Bash) → gate status, business-weighted score, findings, gaps (coverage / assertion-depth / balance / duplicate / traceability).
   - **`sungen-reviewer`** sub-agent (Task tool, `subagent_type: sungen-reviewer`) → semantic verdict the gate can't see (do steps prove the title, observable Then, @manual justified) and, when the unit holds design sources (`requirements/ui/*`, `ui/figma-meta.md`, `spec_figma.md`), the **Design ↔ Feature diff** against `ui-inventory.md`. Copilot/no-sub-agents: apply the `sungen-reviewer` criteria inline.
   - `sungen script-check --screen <name>` (Bash) → is the generated spec a 1:1 of the Gherkin (only if a spec exists; flags hand-edit / stale drift).
3. **Unified scorecard** — present ONE report per feature, anchored on the harness signals (the `sungen-tc-review` 7 dimensions are a *presentation layer* over these, not a competing score):

   ```
   Feature        Gate   Score   Reviewer        Spec 1:1     Verdict
   ───────────────────────────────────────────────────────────────────
   home.feature   PASS   8.4/10  2 minor issues  in-sync      PASS
   ```
   - **Gate PASS + reviewer clean + spec in-sync** → PASS.
   - **Gate FAIL or reviewer NEEDS-REPAIR or drift** → CONDITIONAL/FAIL — show the findings + reviewer issues + fixes.
   - The score is the **audit business-weighted score** adjusted down by unresolved reviewer issues — never a parallel number that contradicts the gate.
4. **Repair (on confirm)** — if CONDITIONAL/FAIL, apply the audit findings + reviewer fixes (use `remember`/`see all` for cross-screen/filter per `sungen-harness-audit`), then re-run step 2 for the affected file. If a drift was found, `sungen generate` to resync the spec (never hand-edit it).
5. **Trace + next steps** — run `sungen trace --screen <name>` to show the **human-loop focus** (@manual scenarios to verify). Then `AskUserQuestion`:
   - **`/sungen:run-test <name>`** — generate selectors, compile, run (if not yet run) **(Recommended)**
   - **`/sungen:delivery <name>`** — export the deliverable (review passed)
   - **`/sungen:create-test <name>`** — add more coverage
   - **Done for now**
