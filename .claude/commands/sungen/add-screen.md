---
name: add-screen
description: 'Add a new Sungen screen or a sub-feature to an existing one — scaffolds directories, helps fill spec.md, and can capture visuals via Figma (pre-launch) or Playwright MCP (web) / Appium MCP (mobile)'
argument-hint: "[screen-name] [url-path] [--feature <name>] [--figma <url>] [--reach <steps>] [--platform android|ios]"
allowed-tools: Read, Grep, Bash, Glob, Edit, Write, AskUserQuestion, mcp__playwright__browser_navigate, mcp__playwright__browser_take_screenshot, mcp__playwright__browser_snapshot, mcp__figma__get_design_context, mcp__figma__get_variable_defs, mcp__figma__get_screenshot
---

You are adding a new Sungen screen for test generation, or extending an existing screen with an additional `.feature` file (sub-feature).

## Parameters

Parse from `$ARGUMENTS`:
- **screen** — screen name (e.g., `login`, `dashboard`, `settings`)
- **path** — URL path (e.g., `/login`, `/dashboard`, `/settings`) - optional when `--figma` is provided or when adding a sub-feature to an existing screen
- **`--feature <name>`** — sub-feature name to add to an existing screen (e.g., `modal`, `filter`, `create-flow`). Creates `<screen>-<feature>.feature` + matching `selectors/<screen>-<feature>.yaml` + `test-data/<screen>-<feature>.yaml` without touching the screen's other files. Use this when one screen has multiple `.feature` files (e.g. main listing + modal + filter panel).
- **`--figma <url>`** — Figma share URL (optional)
- **`--refresh`** — bypass Figma cache and re-fetch (optional, use with `--figma`)
- **`--scale <n>`** — PNG export scale factor, default 2 (optional)
- **`--hi-res`** — export at 4× scale, shorthand for `--scale 4` (optional)

If **screen** is missing, ask: "What is the screen name? (e.g., `login`, `dashboard`)"
If **path** is missing and **`--feature`** is NOT used and `--figma` was NOT provided, ask: "What is the URL path? (e.g., `/login`, `/dashboard`)"

**Mode detection** — before invoking the CLI, check whether `qa/screens/<screen>/` already exists:
- Screen does NOT exist → fresh screen creation (need `--path` unless `--figma` provided)
- Screen exists AND `--feature <name>` provided → sub-feature mode (skip spec.md, only generate the 3 new files)
- Screen exists AND no `--feature` → ask the user whether they meant to add a sub-feature (offer `--feature <suggested-name>`) or refresh visuals only (with `--figma`)

## Platform Detection

Read `qa/capabilities.yaml` and check the `platform` field.

- If `platform: mobile` → follow the **mobile path** (see "Mobile capture" step below, uses Appium MCP)
- If `platform: web` or field absent → follow the **web path** (standard, uses Playwright MCP)

For **mobile** projects:
- **Live capture**: use **Appium MCP** (`select_device` → `appium_session_management(create)` → `appium_screenshot`)
- **Navigation**: use `--reach "<steps>"` to describe UI navigation (e.g., `"tap [My Page] tab > tap [Settings] row"`)
- **Device platform**: Optional `--platform android|ios` (defaults to Android if omitted)

## Steps

### 1. Scaffold the screen (or sub-feature)

**Standard path (no `--figma`, no `--feature`):**
```bash
sungen add --screen <screen> --path <path>
```

**Sub-feature mode (`--feature <name>` provided):**
```bash
sungen add --screen <screen> --feature <name> [--path <path>]
# Generates qa/screens/<screen>/{features,selectors,test-data}/<screen>-<name>.{feature,yaml,yaml}
# Existing screen files (awards.feature, spec.md, …) are untouched.
# --path is optional — defaults to /<screen> when omitted. Pass --path only if the sub-feature lives at a different URL.
```

**Figma branch (when `--figma <url>` is in `$ARGUMENTS`):**

Invoke the `sungen-capture` skill (mode figma-pat) by running:
```bash
sungen add --screen <screen> --figma '<url>' [--path <path>] [--feature <name>] [--refresh] [--scale <n>]
# Single-quote the URL — Figma links contain `&` which bash otherwise treats as a background operator.
# --feature can be combined with --figma when the new sub-feature has its own Figma frame.
```

This CLI command automatically:
1. Parses the Figma URL and fetches the design node.
2. Downloads frame + variant PNGs to `qa/screens/<screen>/requirements/ui/`.
3. Writes `qa/screens/<screen>/requirements/spec_figma.md` (auto-generated, always overwritten on re-run).
4. Creates `qa/screens/<screen>/requirements/spec.md` stub **only if the file does not already exist** — never overwrites existing human content.

**Important coexistence rules:**
- `--figma` and `--path` can be passed together: `--path` provides the live URL for `run-test`; `--figma` provides design context.
- `spec.md` is the human-authored source of truth. Never modify it automatically.
- `spec_figma.md` is auto-generated — always overwritten; tell the user to copy useful sections into `spec.md`.
- `--capture` (Playwright screenshot) and `--figma` can be used simultaneously; they produce different outputs.

**If Figma auth is missing**, the command will fail with a message like:
> Figma PAT is not configured. Run: sungen figma auth set

In that case, guide the user to run `sungen figma auth set` and retry.

### 1a. Synthesize narrative sections (Figma branch only)

After `sungen add --figma` succeeds, the envelope of `spec_figma.md` is deterministic but the narrative below the `<!-- SYNTHESIS-BELOW -->` marker is empty. Invoke the `sungen-capture` skill (mode figma-pat) to fill it:

1. Read `qa/screens/<screen>/requirements/spec_figma.md` — note `file_key`, `node_id`, `figma_version_id` from frontmatter.
2. Read the cached raw node JSON at `.sungen/figma-cache/<file_key>/<figma_version_id>/<safe_node_id>-raw.json` (colons in node_id become underscores).
3. Follow the skill's 7-section template (Purpose / ASCII Layout / Regions / Actions / Form Fields / Data Columns / Navigation) and **replace** everything from the marker to EOF.
4. Preserve the envelope above the marker byte-for-byte.

**Review gate.** Before moving on, show the user the synthesized narrative and use `AskUserQuestion`:

- **Approve** — narrative looks right, continue to Step 2
- **Edit** — user will tweak `spec_figma.md` now; wait for confirmation before continuing
- **Cancel** — abort; advise the user that `spec.md` was NOT modified and they can re-run `sungen add --figma --refresh` later

### 2. Capture visual source

**If Figma branch (Step 1) already downloaded PNGs** → visuals already exist. Use `AskUserQuestion` to offer:
- **Continue** — Figma visuals are enough (Recommended)
- **Also capture live page** — supplement Figma with real page scan (invoke `sungen-capture` skill (mode live))

**If standard path (no `--figma`)** → go straight to source selection. Use `AskUserQuestion`: *"Pick a visual source for this screen:"*
- **Figma design** (Recommended for pre-launch) — invoke `sungen-capture` skill (mode figma-mcp)
- **Live page scan** (dev/staging is up) — invoke `sungen-capture` skill (mode live)
- **Skip** — user will drop images manually into `requirements/ui/` later

Each capture skill writes outputs into `qa/screens/<screen>/requirements/ui/` and reports back a summary. Do not inline capture logic here — always delegate to the skill so behavior stays consistent with `/sungen:create-test`.

### 3. Fill spec.md

**Skip this step when `--feature` was used** — the screen's existing `spec.md` is reused for all sub-features; only suggest the user append a new section for the sub-feature if useful (e.g. `## Modal — Confirm Withdrawal`).

For fresh screen creation, use `AskUserQuestion`: *"Fill `spec.md` now? (You can reference the captured visuals)"* — offer **Yes, fill now (Recommended)** / **Skip, fill later**.

If yes → open `qa/screens/<screen>/requirements/spec.md` and help the user fill sections, fields, validation rules, business rules, and states. Reference the captured visuals from Step 2 to suggest field names, form elements, and UI states. Especially prompt for the optional **Figma URL** and **Live URL** fields in Overview — those unlock auto-capture without re-asking next run.

### 4. Next steps

Tell the user what was created. When `--feature` was used, list the 3 new files explicitly (`features/<screen>-<name>.feature`, `selectors/<screen>-<name>.yaml`, `test-data/<screen>-<name>.yaml`) and remind that `/sungen:create-test` currently operates at the screen level — it will see both the parent feature and the new sub-feature and ask which one to update.

Then use `AskUserQuestion` to offer next steps:

- **`/sungen:create-test <screen>`** — Create test cases from requirements/designs (Recommended)
- **Done for now** — I'll come back later

If user picks `/sungen:create-test`, **you MUST use the Skill tool** to invoke it. Do NOT generate test cases yourself — the skill auto-loads `sungen-gherkin-syntax` and `sungen-tc-generation`.

## CLI Reference

```
sungen add --screen <name>
  [-p, --path <path>]            URL route (e.g., /awards). Required for capture; defaults to /<screen> when omitted.
  [-f, --feature <name>]         Add a sub-feature .feature file to an existing screen.
                                 Output filename = <screen>-<feature>.{feature,yaml,yaml}.
  [-c, --capture]                Auto-capture a live screenshot to requirements/ui/ (needs --path).
  [-d, --description <text>]     Screen description (used in spec.md stub).
  [--figma <url>]                Figma share URL — generates spec_figma.md + ui/*.png.
  [--refresh]                    Bypass Figma cache (use with --figma).
  [--scale <n>]                  PNG export scale (default 2).
  [--hi-res]                     Shorthand for --scale 4.
```
