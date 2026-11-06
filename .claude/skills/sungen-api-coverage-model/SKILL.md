---
name: sungen-api-coverage-model
description: 'The per-endpoint API coverage model — the systematic case set to enumerate for each endpoint (read / mutating / flow), keyed to the catalog''s fields: metadata, tiered so a first pass is complete (not "happy path + 1 error"). Load this in API unit mode alongside sungen-api-design; it turns endpoints × field constraints into a concrete case list, mechanically, instead of guessing.'
---

# API coverage model (what to test per endpoint)

The gap this closes: a bare `@api` generation defaults to **one happy path + maybe one error** per endpoint — 3 of 8 needed cases. This skill is the **enumeration rule**: for each endpoint, walk the checklist below and emit **every applicable case**. Cases are derived **mechanically** from the catalog `fields:` block (`required` / `type` / `format` / `min`/`max` / `minLength`/`maxLength` / `pattern` / enum `values`) surfaced by `sungen context --area <name>` — do NOT guess the matrix from prose when `fields:` exists.

**Tiers** — always emit **Tier 1** (the complete-first-pass minimum). Emit **Tier 2** when the user asks for full coverage or the spec flags the concern as business-critical. Don't stop at Tier 1's *count* if an endpoint has more required/constrained fields — the matrix scales with the fields.

## Read endpoint (GET / HEAD)

| # | Case | Tier | Route | From |
|---|------|------|-------|------|
| R1 | **Contract** — 2xx + response body asserted (≥1 server-derived field value; when a `schemaRef` exists, add `expect {{name.body}} matches schema [Ref]` for the full shape) | 1 | `@api` | always |
| R2 | **Not found** — by-id read with a missing id → 404 (or the spec's code) | 1 | `@api` | path has an id param |
| R3 | **Unauthorized** — no / invalid token → 401 | 1 | `@api` + `@cases` | endpoint requires auth |
| R4 | **Empty state** — query that returns 0 rows → 2xx + empty array / count 0 | 2 | `@api` | list endpoint |
| R5 | **Forbidden** — valid token, wrong role → 403 | 2 | `@api` | role-scoped |
| R6 | **Per query param validation** — for each `fields:` query param: required→omit→400; type mismatch→400; enum→invalid value→400; `min/max`→BVA rows | 2 | `@cases` | each constrained query field |
| R7 | **Pagination / filter / sort** — offset/limit boundaries, sort direction, filter match | 2 | `@api`/`@cases` | paginated list |

## Mutating endpoint (POST / PUT / PATCH / DELETE)

| # | Case | Tier | Route | From |
|---|------|------|-------|------|
| M1 | **Happy path** — 2xx + response body asserted (id/echoed fields) **and prove the effect** (`@query` DB read-back, or a follow-up GET) | 1 | `@api` (+`@query`) | always |
| M2 | **Required-field matrix** — one row per `required` field omitted → 400 + error message | 1 | `@cases` | each `required` field |
| M3 | **Unauthorized** — no / invalid token → 401 | 1 | `@api`/`@cases` | endpoint requires auth |
| M4 | **Idempotency / duplicate** — `@concurrent:N` + `@query` oracle → exactly one effect (or 409 on the 2nd) | 1 | `@concurrent`+`@query` | create / non-idempotent |
| M5 | **Type + boundary matrix** — per constrained field: wrong type; `min-1/min/max/max+1`; `minLength/maxLength` edges | 2 | `@cases` | each constrained field |
| M6 | **Format / enum matrix** — per `format`/`pattern`/enum field: an invalid value → 400 | 2 | `@cases` | each format/enum field |
| M7 | **Forbidden** — valid token, wrong role → 403 | 2 | `@api`/`@cases` | role-scoped |
| M8 | **Semantic (422)** — fields individually valid but violate a business rule (e.g. end < start) | 2 | `@cases` | cross-field rule |
| M9 | **Payload size / content-type** — oversized body → 413/400; missing/wrong `Content-Type` → 400 | 2 | `@cases` | as spec dictates |

## Flow (multi-endpoint journey — qa/api/flows/<flow>)

| # | Case | Tier |
|---|------|------|
| F1 | **CRUD chain** — create → read-back → update → read-back → delete → 404 (each step asserts + self-cleans) | 1 |
| F2 | **Auth flow** — login → authed call 2xx → logout → same call 401 | 1 |
| F3 | **Cross-endpoint invariant** — create in A, verify in B (data consistency) | 2 |

## Deriving the matrix from `fields:`

For an endpoint with `fields:` metadata, expand mechanically:

- `required: true` → **one M2/R6 missing-field row** (omit it, expect 400 + message).
- `type` (integer/number/boolean) → a **wrong-type row** (send a string).
- `min`/`max` (or `minLength`/`maxLength`) → **BVA rows**: `min-1` (invalid), `min` (valid edge), `max` (valid edge), `max+1` (invalid).
- `values` (enum) → a **not-in-enum row**.
- `format`/`pattern` → an **ill-formed row** (bad email, wrong pattern).

Collapse these into compact `@cases` datasets — one dataset per endpoint, one row per (field, technique), each row carrying its `expect_status`. Don't write one scenario per row.

## Assertion depth (tie every case to a proof)

Never emit a status-only assertion for a success case. Three tiers — see also `sungen-tc-review` (API assertion rubric):

- **Shallow ❌** — `expect {{x.status}} is 201` alone. Proves the server didn't 500; not that anything happened.
- **Medium ⚠️** — status + a response body field (`{{x.body.id}}`). OK for a read contract.
- **Strict ✅ (required for mutating success + Tier-1 reads)** — status + body field **value that isn't just the input echoed back** + a **proof of effect**: a `@query` DB read-back, a follow-up GET, `matches schema [Ref]` (full-shape contract, when a `schemaRef` exists), or a `@concurrent` `ok_count` invariant.

> `expect {{name.body}} matches schema [Ref]` validates the whole response body against the named JSON Schema in `qa/api/schemas.yaml` (populated by `sungen api import`). Use it in addition to — not instead of — a couple of concrete field-value asserts on the critical fields.

Anti-pattern to avoid: re-asserting the value you just sent (`expect {{x.body.username}} is {{username}}` on the thing you created proves little) — assert a **server-derived** field (generated id, timestamp, computed status) or read it back independently.

## Alias entries (binding one endpoint twice)

To bind two responses of the SAME endpoint in one scenario (a control call next to the call under
test), clone the catalog entry under a second name (`create_order_control`) — identical to the
original except `name`/`description`. The audit gate counts identical entries as ONE endpoint: its
must-cover viewpoints (`contract`, `error`) are satisfied when ANY alias asserts them, so the clone
needs no error scenario of its own. An entry on the same route that differs in any request or
response detail (`encoding`, `body`, `headers`, `expect`, `fields`, schema) is a distinct endpoint
and keeps its own viewpoints. An omitted `datasource` resolves to the project default before comparing.

## Output discipline
- Enumerate the **full Tier-1 list per endpoint first**, then write incrementally (≈10–15 scenarios per Write call) — never the whole suite in one response (output-token cap).
- Name scenarios `VP-API-NNN <what it proves>`; keep the endpoint + technique legible.
- Enumerate the matrix **mechanically from `fields:`** — that is the coverage guarantee this phase; don't rely on a gate to backfill it. (A future audit gate will additionally flag a `required`/constrained field with no matching negative case — `VIEWPOINT-API-REQUIRED-MATRIX` / `-BOUNDARY` / `-FORMAT` — but until it ships, completeness comes from the enumeration above, not from repair.)
