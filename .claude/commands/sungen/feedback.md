---
name: feedback
description: 'Record QA feedback locally (test-design knowledge or product telemetry). Auto-attaches context and stores to .sungen/feedback/.'
argument-hint: [message]
allowed-tools: Read, Grep, Bash, Glob
---

## Role

You capture QA feedback and store it **locally** (no server) via `sungen qa-feedback record`. The value is closing the learning loop inside this project — the harness can later reuse it (don't regenerate rejected viewpoints; include added ones) and it feeds the spec-change/reuse plan.

## Steps

1. Read the message from `$ARGUMENTS` (ask if empty).
2. **Classify the type** (do not over-ask — infer, confirm only if ambiguous):
   - `test-design` — about a viewpoint/scenario being wrong, missing, duplicate, low-value, or a new viewpoint to add.
   - `product` — about Sungen itself behaving wrong (a command failed, generated bad output structurally, a bug).
   - `other` — anything else.
3. **Auto-attach context** (do not make the user repeat it):
   - `--screen <name>`: the screen/flow currently in focus (from the conversation or cwd `qa/screens|flows/`).
   - `--target <ref>`: if the message references a viewpoint id (`VP-...`), a scenario title, a command, or an artifact, pass it.
   - `--decision <accept|reject|edit|add|none>`: if the feedback is a decision on an AI suggestion (e.g. "this viewpoint is wrong" → `reject`; "also test X" → `add`).
   - `--reason <text>`: the rationale, if distinct from the message.
4. Run, e.g.:
   ```bash
   sungen qa-feedback record --type test-design --screen <name> \
     --target "VP-DATA-CONSISTENCY" --decision add \
     --message "<message>" --reason "<why>"
   ```
5. Confirm what was recorded and where (`.sungen/feedback/feedback.jsonl`). Note it is **local now**; cross-project sync is opt-in and added later.
6. If the feedback implies a concrete next action (e.g. a missing critical viewpoint), offer it: `/sungen:design <name>` to regenerate with the gate, or `/sungen:add-flow` for a cross-screen gap.

## Notes
- **Never send anywhere** — this only writes a local file.
- Keep `product` feedback separate from `test-design` so it can route to telemetry vs the viewpoint knowledge later.
- View history: `sungen qa-feedback list [--screen <name>] [--type <t>]`.
