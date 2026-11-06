---
name: design
description: 'Alias of create-test. Generates test cases AND runs the quality harness (gate + repair). Kept for discoverability; create-test now does this by default.'
argument-hint: [screen-name]
allowed-tools: Read, Grep, Bash, Glob, Write, AskUserQuestion, Skill, mcp__playwright__browser_navigate, mcp__playwright__browser_snapshot, mcp__playwright__browser_take_screenshot
---

## `/sungen:design` is an alias of `/sungen:create-test`

As of v3.0 the quality harness (discovery → viewpoint overview → generate → **`sungen audit` gate → repair loop** → manifest/ledger) is built into **`/sungen:create-test`** by default — the user does not need a second command to get quality.

**Do exactly what `/sungen:create-test <name>` does.** Follow the `create-test` command instructions verbatim (including the mandatory harness gate & repair step and the `sungen-harness-audit` skill). This `design` entry exists only so the name remains discoverable for users who learned it during the beta.
