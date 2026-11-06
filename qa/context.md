# Project Context

> Read by the AI before generating test cases for any screen in this project.
> Fill in what applies — leave sections empty if not relevant.
> **The more specific you are, the more accurate the generated test cases.**

---

## Project Overview

**Application:**
<!-- One sentence: what does this app do? -->
<!-- Example: B2B award nomination platform for enterprise HR teams. -->

**Target users:**
<!-- Who uses this app and in what context? -->
<!-- Example: HR managers submit nominations; employees view results. -->

**Domain notes:**
<!-- Key terminology, conventions, or constraints the AI should know. -->
<!-- Example: "Nomination = an award record. Once submitted, status cannot revert to Draft." -->
<!-- Example: "All monetary values are in JPY. No decimal places." -->

---

## Auth Roles

> The AI maps these directly to `@auth:X` tags and generates permission-boundary test scenarios.
> Leave the table empty (or delete it) if the app has no auth system.

| Role | Can do | Cannot do |
|------|--------|-----------|
| | | |

<!--
Example:
| Role    | Can do                                      | Cannot do                            |
|---------|---------------------------------------------|--------------------------------------|
| admin   | All CRUD, manage users, configure settings  | Nothing blocked                      |
| manager | Create/edit records, view reports           | Delete records, manage users         |
| staff   | View and submit own records only            | Edit others' records, view reports   |
-->

---

## Testing Strategy

Machine-readable intent — `sungen audit` reads these keys (Intent Profile). Values here are
live even when the surrounding text changes; an invalid value silently falls back to the default.

focus: functional
<!-- focus: functional | e-commerce | security | smoke — drives the audit's depth threshold -->

risk_tier: normal
<!-- risk_tier: high | normal | low -->

To silence driver suggestions in audit findings, add a line: capability_suggestions with value off.

**Focus areas** — what to cover thoroughly:
<!-- Prose for humans; the parseable value is the `focus:` key above.
     Response-time budgets are NOT a focus value — declare them in qa/perf.yaml
     (percentile + scenario_ms budgets; reported by `sungen delivery`). -->
<!-- Example: functional, security -->

**Mandatory coverage:**
<!-- Rules that override the AI's default tier decisions for every screen. -->
<!-- Example: "Every screen with admin-only actions MUST have a non-admin blocked-access scenario." -->
<!-- Example: "All free-text inputs MUST have XSS + SQL injection scenarios regardless of screen risk level." -->

**Deprioritize / skip:**
<!-- What to move to @low or skip entirely for this project. -->
<!-- Example: "Skip VP-UI cosmetic checks (label/placeholder presence) — handled separately by design review." -->
<!-- Example: "Skip accessibility scenarios — separate audit planned." -->

---

## Global Business Rules

> Rules that apply across multiple screens.
> The AI adds these to the Coverage Map for every screen as `[G]`-tagged Business rules.
> Screen-specific rules belong in `requirements/spec.md`, not here.

<!-- - Soft-delete only: records are never hard-deleted, only marked inactive -->
<!-- - All timestamps stored in UTC, displayed in UTC+7 -->
<!-- - Pagination default: 20 items per page; max 100 -->
<!-- - File uploads: PNG/JPG/PDF only, max 5 MB -->
<!-- - After any write operation, the list view must refresh automatically -->

---

## Error Message Patterns

> If your app follows consistent validation error formats, list them here.
> The AI uses these to fill `test-data.yaml` error keys when `spec.md` doesn't specify exact text.
> Leave empty to let the AI infer from spec.md.

- Required field: `<!-- "This field is required" -->`
- Max length: `<!-- "Must be X characters or less" -->`
- Min length: `<!-- "Must be at least X characters" -->`
- Invalid format: `<!-- "Invalid format" -->`
- Unique constraint: `<!-- "Already exists" -->`
- Not found: `<!-- "Not found" -->`
- Unauthorized: `<!-- "You do not have permission to perform this action" -->`
