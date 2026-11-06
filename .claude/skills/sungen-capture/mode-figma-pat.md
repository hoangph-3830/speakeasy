# Capture mode: figma-pat

Figma URL → `spec_figma.md` envelope + LLM-synthesized narrative from cached raw node JSON. This mode is the active one when any of these is true: a sungen command was invoked with `--figma`, `requirements/spec_figma.md` exists, or the user says "generate from Figma".

## Prerequisites

- [ ] `sungen figma auth check` succeeds — PAT is stored and valid
- [ ] Figma file URL is available (shared file or frame link)
- [ ] If auth missing → run `sungen figma auth set` and follow the walkthrough

**Never paste the PAT into any transcript, spec file, or commit.**

## Two-Layer Architecture

`spec_figma.md` has two layers separated by the `<!-- SYNTHESIS-BELOW -->` marker:

| Layer | Producer | Overwrite Rule |
|---|---|---|
| **Envelope** (above marker) | sungen CLI | Regenerated each `sungen figma` run — deterministic |
| **Narrative** (below marker) | This mode (LLM) | Replaced on re-synthesis — everything from marker to EOF |

The envelope contains: YAML frontmatter, Frame metadata, Screenshots. The narrative is synthesized by YOU from the cached raw Figma node JSON.

## Inputs You Read

The scaffolder persists a raw (unfiltered) Figma node tree to:
```
.sungen/figma-cache/<fileKey>/<versionId>/<nodeId>-raw.json
```
Read this file + the envelope frontmatter of `requirements/spec_figma.md` + any PNGs under `requirements/ui/`. You MUST NOT call the Figma REST API directly — the PAT is not available to you.

## Synthesis Task

Append 7 narrative sections below `<!-- SYNTHESIS-BELOW -->`. Each is inferred from the raw node tree (names, types, `characters`, layout bounds, auto-layout direction, componentProperties):

### 1. Purpose
One paragraph. What screen is this? Primary user goal? Infer from frame name + top-level text + dominant CTA.

### 2. ASCII Layout
Rough spatial sketch using box characters. Reflect top-bottom / left-right ordering from absoluteBoundingBox. Keep under ~20 lines:
```
┌──────────────────────────────────────┐
│ [Logo]                     [Sign In] │
├──────────────────────────────────────┤
│  Welcome back                        │
│  ┌────────────────────────────────┐  │
│  │ email@example.com              │  │
│  └────────────────────────────────┘  │
│  [ Continue ]                        │
└──────────────────────────────────────┘
```

### 3. Regions
Bulleted list of major layout regions (header, sidebar, main, footer, modal…) with a one-line purpose each. Use auto-layout frames as region hints.

### 4. Actions
Every interactive element the user can trigger (button, link, icon-button, menu-item, toggle…):
```
- **<Action name>** — <what it does> (source: <node name>)
```

### 5. Form Fields
Every input. Include label, type (text/email/password/select/checkbox/radio/textarea/date), required hint if inferable, placeholder:
```
| Label | Type | Required | Placeholder |
|---|---|---|---|
```
Omit (write `_none_`) if no inputs exist.

### 6. Data Columns
If the screen shows a table/list/card grid — enumerate the columns/fields per row. Otherwise `_none_`.

### 7. Navigation
Outgoing links, tab bars, breadcrumbs, back buttons — anything that moves to another screen. Include explicit nav components and implicit CTAs that navigate.

## Synthesis Workflow

1. Read `requirements/spec_figma.md` — note `file_key`, `node_id`, `figma_version_id` from frontmatter
2. Read `.sungen/figma-cache/<file_key>/<figma_version_id>/<safe_node_id>-raw.json` (colons in node_id become underscores)
3. Traverse the tree. Collect names, types, `characters`, `componentProperties`, `absoluteBoundingBox`
4. Produce the 7 sections above
5. **Locate the insertion point** in `spec_figma.md`:
   - **If `<!-- SYNTHESIS-BELOW -->` is present** → replace everything from the marker (inclusive) to EOF with: the marker line, a blank line, then the 7 sections.
   - **If the marker is NOT present** (older file or hand-edited) → locate the last non-empty line of the envelope (usually the end of `## Screenshots`), append a blank line, then the marker, another blank line, then the 7 sections. Do NOT delete any envelope content.
   - **If the file is missing entirely** → advise the user to re-run `sungen add --screen <screen> --figma <url> --refresh` to regenerate the envelope first. Do not fabricate one.
6. Preserve the envelope (frontmatter + Frame + Screenshots) byte-for-byte. Never touch content above the marker.

## Re-synthesis

- If the envelope's `figma_version_id` changed → envelope is fresh; re-run synthesis
- If only the narrative is stale → truncate from marker to EOF and regenerate
- Never edit content ABOVE the marker — that is the scaffolder's territory

## Selector Heuristics (for downstream `run-test`)

During `run-test` Phase 0, provisional selectors can be seeded from the raw JSON:

| Figma Signal | Provisional YAML Entry |
|---|---|
| Node name ends `Button`, has text | `role: button` + `name: "<text>"` |
| Node name ends `Input`/`Field` | `placeholder: "<placeholder text>"` |
| Node name ends `Link`, has text | `role: link` + `name: "<text>"` |
| componentProperties has `data-testid` | `testid: <value>` |
| Plain text leaf (outside interactive) | `text: "<content>"` |
| Node name ends `Icon` | `role: img` + `name: "<accessible name>"` |

Every provisional entry MUST carry:
```
# @needs-live-verify source=figma node_id=<id>
```
Provisional selectors feed `selectors.yaml` as candidates. `run-test` Phase 0 verifies them against the live page and overwrites incorrect entries.

## Security

- Never include the PAT in `spec_figma.md`, selectors, test data, or any committed file
- Never log or echo the PAT in terminal output
- Read only from `.sungen/figma-cache/` and screen directories — never from `.env`
