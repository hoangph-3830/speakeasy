---
name: add-api
description: 'Add a new Sungen API area or flow — scaffolds the datasource + qa/api/<area>/ directories and can import an OpenAPI/Swagger or CSV spec so the endpoint catalog (+ field metadata, auth, schemas) is populated for you'
argument-hint: "[area-name] [--base-url <url>] [--flow <name>] [--import <openapi|csv|sheet-url>] [--datasource <name>]"
allowed-tools: Read, Grep, Bash, Glob, Edit, Write, AskUserQuestion
---

You are scaffolding an **API-first** unit for test generation — an area (one resource) or a flow (a cross-endpoint journey). There is **no visual capture and no selectors**: the contract is the named-endpoint catalog (`api/apis.yaml`), referenced by `@api:<name>`. Follow the `sungen-api-design` skill after scaffolding.

## Parameters

Parse from `$ARGUMENTS`:
- **name** — area or flow name (e.g. `orders`, `users`, `user-lifecycle`)
- **`--base-url <url>`** — the API base URL (stored in `.env.qa`, referenced as `${<DS>_URL}`). Needed the first time (project not yet API-initialized); skippable if `qa/datasources.yaml` already has a `kind: api` datasource.
- **`--flow <name>`** — scaffold an ordered `@api` chain (auth → CRUD) under `qa/api/flows/<name>/` instead of a single area.
- **`--import <source>`** — an OpenAPI/Swagger file/URL or a CSV / published Google-Sheet URL. When given, the catalog + `fields:` metadata + inferred auth headers + response `schemas.yaml` are generated from it.
- **`--datasource <name>`** — datasource name (default `app_api`).

If **name** is missing, ask: "What is the API area (resource) name? (e.g., `orders`, `users`)".
If **`--base-url`** is missing AND `qa/datasources.yaml` has no `kind: api` datasource, ask for it (it's needed to run the tests later; you can proceed with scaffolding and add it after).

## Steps

### 1. Initialize the API project (first area only)

Check `qa/datasources.yaml` for a `kind: api` datasource. If none, run:
```bash
sungen api init --base-url <base-url> --datasource <datasource>
```
This creates the `kind: api` datasource, `qa/api/`, and the `.env.qa` URL key. Skip if already initialized (never clobbers).

### 2. Scaffold the area (or flow)

```bash
sungen api add --area <name>        # a single resource → qa/api/<name>/
# or
sungen api add --flow <name>        # an ordered CRUD/auth journey → qa/api/flows/<name>/
```
This creates `features/`, `api/apis.yaml`, `test-data/`, and `requirements/spec.md`.

### 3. Populate the catalog (recommended)

If `--import <source>` was given, run it — importing a spec is the single biggest quality lever (the catalog gets accurate paths, query/body params, `fields:` constraints, inferred auth headers, and response `schemas.yaml`, so `/sungen:create-test` can enumerate a complete case set instead of guessing):
```bash
sungen api import <source> --datasource <datasource>
```
No spec? Hand-author `qa/api/<name>/api/apis.yaml` (or fill `requirements/spec.md` and let `/sungen:create-test` draft it). Review the generated catalog + wire the datasource `base_url`/auth in `datasources.yaml` + `.env.qa`.

### 4. Converge (next actions)

Use `AskUserQuestion` to offer the next step — do NOT just print text:
- **`/sungen:create-test <name>`** (Recommended) — generate `.feature` + `test-data` (auto-detects the API unit; enumerates the coverage model from `fields:`).
- **`/sungen:add-api <another> --base-url …`** — scaffold another area.
- Review `qa/api/<name>/api/apis.yaml` + wire the datasource, then create tests.

## Rules
- **No visual capture, no selectors** — API units are catalog-driven; don't run Playwright/Figma capture.
- **Never clobber** — `init` merges datasources; `add` refuses an existing area; `import` needs `--merge` to combine.
- **Non-prod default** — a `production` datasource is refused at runtime unless `SUNGEN_ALLOW_PROD=1`.
