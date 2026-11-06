---
name: dashboard
description: 'Build a single-file HTML dashboard with test cases + pass/fail results, history trends, compare runs, and CSV/XLSX export.'
argument-hint: "[screen-name...] [--env <env>] (omit names for all)"
allowed-tools: Bash, Read, AskUserQuestion
---

## Role

You are a **QA Reporting Engineer**. Your job is to invoke the deterministic `sungen dashboard` CLI which builds a self-contained HTML report. The HTML is shareable as-is (open via `file://`, no server required).

## Parameters

Parse **screens** from `$ARGUMENTS`:
- If empty → CLI will include **all** screens and flows.
- If provided → pass them through.
- **`--env <env>`** — optional (accept `--locale` as an alias). Pass through as `sungen dashboard --env <env>`: the dashboard is built for that environment/locale only — it picks the `<name>-test-result.<env>.json` files, and Trends/Compare only see history snapshots of the same env (a dev run never blends into the uat trend line). Omit → the base environment.

## Steps

### 1. Invoke the CLI

Prefer the local `./bin/sungen.js` when it exists (this is the canonical entry point when developing sungen itself, and ships any unpublished features the global package doesn't have yet). Fall back to `npx sungen` for downstream projects that consume sungen as a dependency.

```bash
# Local-first dispatcher — works in both the sungen monorepo and consumer projects:
[ -x ./bin/sungen.js ] && ./bin/sungen.js dashboard <screens> || npx sungen dashboard <screens>
```

- No args → all screens + flows.
- With args → only those targets.
- Add `--open` to auto-open the rendered HTML in the user's browser.

The CLI handles:
- Discovery of `qa/screens/*` and `qa/flows/*`.
- Reuses the same parsers as `sungen delivery` (feature, spec.ts, test-data.yaml, results.json).
- Builds `DashboardSnapshot` JSON.
- Persists `qa/dashboard/history/<runId>.json` (max 10 retained, oldest pruned).
- Renders `qa/dashboard/index.html` (~1MB, single file, fully self-contained).
- Prints a summary table.

### 2. Show the result + offer next steps

Forward the CLI's summary verbatim, then use `AskUserQuestion`:

- **Open the dashboard** — Suggest `npx sungen dashboard --open` to open in the default browser, or just open `qa/dashboard/index.html` manually.
- **Re-run after a test run** — Run `/sungen:run-test <screen>` to refresh `test-results/results.json`, then re-invoke `/sungen:dashboard`.
- **Build for a single screen** — Run `/sungen:dashboard <screen>` to scope the report.
- **Share the file** — The HTML at `qa/dashboard/index.html` can be emailed / Slacked / committed; recipients open it directly without any server.
- **Done** — Exit.

## Important notes

- **Do NOT parse files yourself** — the CLI is the source of truth.
- **The HTML is stateless** — the snapshot is embedded in `<script id="__SUNGEN_DASHBOARD__" type="application/json">…</script>`. Re-running the CLI overwrites this file.
- **History is retained in `qa/dashboard/history/`** — these JSON files power the Trends and Compare views. Keep them in git so collaborators see the same trend.
- **CSV/XLSX export** in the dashboard runs entirely in the browser (SheetJS) and matches the `sungen delivery` format. Use it for quick exports without re-running the CLI.

## CLI Reference

```
sungen dashboard [screens...]
  [--no-history]            Do not persist this run under qa/dashboard/history/
  [--max-history <n>]       Cap retained history files (default: 10)
  [--open]                  Open the rendered HTML in the default browser
```
