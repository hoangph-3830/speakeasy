---
name: sungen-dashboard
description: 'Build a single-file HTML dashboard summarising test cases & Playwright results. Auto-loaded by /sungen:dashboard.'
user-invocable: false
---

## Purpose

Generate `qa/dashboard/index.html` — a single-file, share-ready test report covering all (or selected) screens and flows. The HTML embeds the snapshot data inline, so it can be emailed, Slacked, or committed and opened by anyone via `file://` without any server.

**This skill delegates all heavy work to the `sungen dashboard` CLI.** The CLI is the source of truth for parsing logic — do NOT re-parse files in AI. Your role is only to:

1. Invoke the CLI.
2. Show its output verbatim.
3. Help the user decide next actions.

---

## Architecture

```
User → /sungen:dashboard [screen...]
       │
       ▼
  sungen dashboard CLI  (deterministic — no AI tokens)
  ├─ Discovery: qa/screens/* + qa/flows/*
  ├─ Reuse delivery parsers (.feature, .spec.ts, test-data.yaml, results.json)
  ├─ Build DashboardSnapshot JSON
  ├─ Write qa/dashboard/history/<runId>.json (max 20 retained, oldest pruned)
  ├─ Inject payload into pre-built HTML template
  └─ Write qa/dashboard/index.html (~1MB, fully self-contained)
```

Source modules: `src/dashboard/*.ts` + `src/exporters/json-exporter.ts`.
UI source: `dashboard/` (built once, ships in npm package as `dist/dashboard/templates/index.html`).

---

## Required sources (CLI tolerates missing files)

| # | Source | Path | Required? |
|---|--------|------|-----------|
| 1 | Feature file | `qa/screens/<name>/features/<name>.feature` (or `qa/flows/...`) | Yes — screens without a feature are skipped |
| 2 | Test data | `qa/screens/<name>/test-data/<name>.yaml` | Optional — `{{vars}}` fall back to literal |
| 3 | Compiled spec | `specs/generated/<name>/<name>.spec.ts` | Optional — flagged as "Not compiled" if missing |
| 4 | Test results | `specs/generated/<name>/<name>-test-result.json` (or `test-results/results.json`) | Optional — TCs show as "Pending" if missing |

Unlike `sungen delivery`, the dashboard CLI is **forgiving** — it always renders whatever data is available. Pre-flight is implicit, not blocking.

---

## Output structure

```
qa/dashboard/
├── index.html                      # share-ready single-file report
└── history/
    ├── 2026-04-26T...Z.json        # past snapshots, oldest → newest
    ├── 2026-04-27T...Z.json
    └── …                           # max 20, older pruned automatically
```

Both should be committed to git so collaborators see the same trend lines.

---

## Dashboard views

| View | What it shows |
|------|---------------|
| **Overview** | Stat cards (total/passed/failed/pending), pass-rate donut, priority bar chart, trend mini-chart, per-screen progress bars |
| **Suites** | Tree: screen → scenarios; filter by search/status/priority; click → detail modal with steps, expected, error, trace |
| **Trends** | Pass-rate line chart + stacked status bars across all retained runs (requires ≥2 runs) |
| **Compare** | Pick base/head run; per-screen Δ table; lists of newly passing/failing/added/removed/changed tests |
| **Export** | Browser-side CSV / XLSX download. One-button generation, no server. Same column layout as `sungen delivery` |

Trends + Compare are disabled in the sidebar until at least 2 runs exist in history.

---

## CLI command reference

```bash
# All screens + flows
sungen dashboard

# Specific targets
sungen dashboard kudos awards flow/checkout

# Skip history persistence (one-off / CI ephemeral)
sungen dashboard --no-history

# Cap retained history (default: 20)
sungen dashboard --max-history 50

# Auto-open in default browser
sungen dashboard --open
```

---

## Skill responsibilities (when invoked from /sungen:dashboard)

1. **Run the CLI** with whatever arguments came from `$ARGUMENTS`.
2. **Show the CLI output verbatim** — do not summarize, paraphrase, or omit warnings.
3. **Offer next-step options via `AskUserQuestion`** based on what just happened:
   - On success → open dashboard, share file, run more tests, build for another screen, done.
   - On error → diagnose from the error message; common causes are missing `qa/` directories or no targets.
4. **Do NOT** parse `.feature`, `.spec.ts`, or results files yourself — that is the CLI's job.
5. **Do NOT** touch files under `qa/dashboard/` directly — only the CLI writes there.

---

## Sharing the dashboard

The HTML is self-contained:
- No CDN or external font — system font stack only.
- All JS, CSS, charts, and the SheetJS library are inlined.
- The snapshot payload sits in `<script id="__SUNGEN_DASHBOARD__" type="application/json">…</script>`.

Acceptable sharing: email, Slack upload, S3 bucket, committed in git. The recipient opens it directly, no install required. ~1MB on disk, ~300KB gzipped over the wire.
