---
name: sungen-capture
description: 'Acquire visual/design context for test generation from one of four sources (modes): figma-mcp, figma-pat, live, local. Auto-loaded by create-test/add-screen when a visual source is needed, or when --figma flag / spec_figma.md is present. Router skill — read only the mode file you need.'
user-invocable: false
---

## Purpose

Bring **visual + design context** into test generation so `sungen-tc-generation` can author Gherkin + test-data grounded in the real UI. This is a **router**: pick exactly **one mode** for the run, then read only that mode's file. Do **not** read all four.

This skill never generates Gherkin or `selectors.yaml` — it only acquires context and reports back to the calling command.

## Pick the mode

| Mode | Read | Use when | Needs |
|---|---|---|---|
| **figma-mcp** | `mode-figma-mcp.md` | Pre-launch / Figma is source of truth, **Figma Dev Mode MCP** connected | Figma MCP + frame URL |
| **figma-pat** | `mode-figma-pat.md` | `--figma` flag was used, or `requirements/spec_figma.md` exists (synthesize narrative from cached raw node JSON) | `sungen figma auth` PAT |
| **live** | `mode-live.md` | App is running (dev/staging/prod read-only) and you want the actual rendered UI | Playwright MCP + reachable URL |
| **local** | `mode-local.md` | Images already dropped in `requirements/ui/` (any design tool, screenshots, mockups) — baseline fallback, no network | nothing |

### How the mode is chosen (when the caller didn't specify)

1. `requirements/spec_figma.md` exists → **figma-pat** (PAT flow already ran during `add-screen`).
2. `requirements/ui/` has images → **local**.
3. Neither → ask the user which source (figma-mcp / live / local), then load that one mode file.

Modes are **mutually exclusive per run**, but the user can run `create-test` again with a different mode to layer context. All modes write to `requirements/ui/` and report back.

## What this skill (any mode) does NOT do

- Does not generate Gherkin — that's `sungen-tc-generation`.
- Does not write `selectors.yaml` — that's `/sungen:run-test`.
- Does not inject auth/cookies — the user logs in manually (see `live`).
- Does not crawl or generate images.
