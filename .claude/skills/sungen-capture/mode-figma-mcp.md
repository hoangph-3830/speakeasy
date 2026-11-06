# Capture mode: figma-mcp

Pull **structured design data** (layout, typography, colors, component tree, design tokens) and a **PNG screenshot** from a Figma frame URL via the **Figma Dev Mode MCP**, so `sungen-tc-generation` can author Gherkin + test-data before a live domain exists. Use when the project is pre-launch or Figma is the source of truth and the live build lags the design.

## Prerequisites

- **Figma MCP server** (`https://mcp.figma.com/mcp`, HTTP transport) connected in `.mcp.json` — `sungen init` scaffolds this. On first use, Claude Code opens a browser for Figma OAuth. Official tools: `get_design_context`, `get_variable_defs`, `get_screenshot`.
- Figma account signed in with access to the file. **Dev/Full seats** get per-minute rate limits; **Starter/View seats** get monthly tool-call limits.
- A Figma URL with both **fileKey** and **nodeId**.

If the MCP is not connected, **do not fail silently** — tell the user:
> "Figma MCP not detected. Run `sungen init` to scaffold the config, or manually add `figma` with `url: https://mcp.figma.com/mcp` to `.mcp.json`. Then sign in when Claude Code prompts."

Then stop.

## Steps

### 1. Resolve Figma URL

Prefer in this order:
1. `Figma URL` field in `requirements/spec.md` (Overview section)
2. If empty/missing → `AskUserQuestion`: *"Paste the Figma frame URL"* (free text)

Accept any of these shapes:
```
https://www.figma.com/file/<fileKey>/<title>?node-id=<nodeId>
https://www.figma.com/design/<fileKey>/<title>?node-id=<nodeId>
https://www.figma.com/proto/<fileKey>/<title>?node-id=<nodeId>
```
Parse: `fileKey` = segment after `/file/`, `/design/`, or `/proto/`; `nodeId` = the `node-id` query param (pass `-` or `:` through as-is). If `node-id` is missing, ask the user to select a frame in Figma and copy the **frame URL** (not the file root).

### 2. Fetch design context

Call **both** in parallel:
```
get_design_context({ fileKey, nodeId })
get_variable_defs({ fileKey, nodeId })
```
`get_design_context` → layout, typography, colors, component structure, spacing. `get_variable_defs` → named design tokens.

### 3. Fetch screenshot

```
get_screenshot({ fileKey, nodeId })
```
Save the PNG to `requirements/ui/figma-<sanitized-nodeId>.png` (replace `:` and `-` with `_`, e.g. `42-15` → `figma-42_15.png`).

### 4. Write metadata dump

Combine design context + variables into `requirements/ui/figma-meta.md`:
```markdown
# Figma Capture — <nodeId>
**Source:** <full Figma URL>
**Captured:** <ISO date>
## Components
<component names + variants>
## Typography
<font families, sizes, weights, line heights>
## Colors
<color tokens + raw hex>
## Spacing & Layout
<spacing tokens, auto-layout specs>
## Text Content
<visible text strings — used by tc-generation to populate test-data>
```
Consumed by `sungen-tc-generation` as a secondary source alongside `spec.md`.

### 5. Report back

> Captured Figma frame `<nodeId>`: Components N · Text strings M · Design tokens K · Screenshot `requirements/ui/figma-<nodeId>.png` · Metadata `requirements/ui/figma-meta.md`

Then hand back to the calling command.

## Error handling

| Error | Action |
|---|---|
| MCP tool not available | Print setup instructions, stop, do not fall back silently |
| `fileKey` missing from URL | Ask user to paste a valid frame URL |
| `nodeId` missing from URL | Ask user to right-click a frame in Figma → *Copy link to selection* |
| `get_design_context` 403 | Ask user to check Dev Mode seat on that file |
| `get_screenshot` returns no image | Continue with metadata only; warn no PNG was captured |
