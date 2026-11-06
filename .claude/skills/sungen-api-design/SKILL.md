---
name: sungen-api-design
description: The API-first design loop for an api unit (qa/api/<area> or qa/api/flows/<flow>) — discover the catalog, lay out the API viewpoints, generate @api/@cases/flow/@concurrent scenarios, then drive the sungen audit --area gate + reviewer + repair to a high businessDepth (≥0.7). Use when create-test/run-test detects an api unit (no selectors, no visual capture).
---

# API design loop (driver-api · Orchestration + Harness)

Use this when the unit is **api-first** — `qa/api/<area>/` or `qa/api/flows/<flow>/`. There are **no selectors and no visual capture**: the contract is the **named-endpoint catalog** (`api/apis.yaml`), referenced by `@api:<name>`. QA writes **no HTTP code**. Full annotation reference: the **API Steps** guide (`@api` / `@cases` / flows / `@concurrent` / `@hybrid`).

## The loop (mirror of /sungen:design, API-native)

### 1. Discover (no capture)
Run `sungen context --area <name>` — it reads the catalog and prints the **endpoints**, their **`fields:` metadata** (required/type/format/range/enum), any **`schemaRef`**, and the **generation units** (one `matrix` unit per endpoint, an `async` unit per mutating endpoint, a `flow` unit for an api flow). Read `qa/api/<name>/requirements/spec.md` if present. No `apis.yaml` yet? → `sungen api import <openapi|csv>` or `sungen api add --area <name>` first.

### 2. Enumerate the coverage model (don't guess the matrix)
**Load the `sungen-api-coverage-model` skill** and, for each endpoint, walk its checklist to produce the **concrete Tier-1 case list** (a read endpoint → contract + not-found + unauthorized; a mutating endpoint → happy+side-effect + required-field matrix + unauthorized + idempotency). Expand the validation matrix **mechanically from `fields:`** — one `@cases` row per (required/constrained field × technique), each with its `expect_status`. This is the difference between 3-of-8 and a complete first pass.

**Mapping Contract** — endpoint profile → cases → route:

| Profile | Viewpoint | Cases (Tier 1) | Technique | Route |
|---|---|---|---|---|
| GET/HEAD | contract | 1 | happy + body-field assert (+`matches schema [Ref]` if `schemaRef`) | `@api` |
| GET/HEAD | not-found | 1 (by-id) | missing id → 404 | `@api` |
| GET/HEAD | auth | 1 | no/invalid token → 401 | `@api`/`@cases` |
| GET/HEAD | param validation | N (Tier 2) | EP/BVA per query field | `@cases` |
| POST/PUT/PATCH/DELETE | contract + side-effect | 1 | happy + `@query` read-back | `@api`+`@query` |
| POST/PUT/PATCH/DELETE | required matrix | N = #required fields | one row per omitted field → 400 | `@cases` |
| POST/PUT/PATCH/DELETE | auth | 1 | no/invalid token → 401 | `@api`/`@cases` |
| POST/PUT/PATCH/DELETE | idempotency | 1 | `@concurrent:N` + `@query` | `@concurrent`+`@query` |
| POST/PUT/PATCH/DELETE | type/boundary/format | N (Tier 2) | per constrained field | `@cases` |

Bands: **~70%** success+failure matrix · **~20%** flows (auth/CRUD chains) · **~10%** async/idempotency.

### 3. Generate (incremental — never the whole suite in one Write)
Follow the coverage model's **assertion depth tiers** — a mutating success case must **prove the effect** (body value + `@query`/read-back), never status-only, and must not merely re-assert the input it just sent.
- **Contract**: `@api:<name>` + `expect {{name.status}} is …` **and a body assertion** on a server-derived field (`{{name.body.<path>}}`); when the entry has a `schemaRef`, add `expect {{name.body}} matches schema [<Ref>]` for the full-shape contract (validates against `qa/api/schemas.yaml`).
- **Error matrix**: `@api:<name>(p={{p}}) @cases:<dataset>` — one scenario, a dataset of `input → expected status`.
- **Flow**: ordered `@api` tags threading a prior response (`token={{login.body.token}}` → the catalog `Bearer :token` header; `id={{create.body.id}}` → a path param). Self-clean (delete what you create).
- **Idempotency**: `@api:<name> @concurrent:N` + `expect {{name.ok_count}} is 1`, cross-checked with `@query` (the DB is the oracle).

### 4. Gate + repair (always — businessDepth ≥ 0.7 is the bar)
Run `sungen audit --area <name>`; read `gateStatus` + `findings`. Then the **semantic reviewer** (sungen-reviewer sub-agent, API criteria). Repair **both** (budget 3 rounds), re-audit until PASS:

| Finding | Repair |
|---|---|
| `VIEWPOINT-API-CONTRACT` | the endpoint is invoked but its response is never asserted → add `expect {{name.status}}` + a `{{name.body.…}}` check |
| `VIEWPOINT-API-ERROR` | a mutating endpoint has no failure scenario → add a `@cases` error matrix (or an explicit 4xx) |
| `VIEWPOINT-API-IDEMPOTENCY` | a mutating endpoint has no race check → add `@concurrent:N` + a `@query` DB cross-check |
| `VIEWPOINT-API-MANUAL-AUTOMATABLE` | a `@manual` scenario whose endpoint resolves is automatable → drop `@manual`, use `@api` (+ `@cases`); reserve `@manual` for genuine judgment cases |
| `VIEWPOINT-API-REQUIRED-MATRIX` | a `required` field (from `fields:`) has no missing-field case → add a `@cases` row omitting it, asserting 4xx |
| `VIEWPOINT-API-BOUNDARY` | a field with a `min/max/minLength/maxLength` constraint has no boundary case → add BVA `@cases` rows (`min-1/min/max/max+1`) |
| `VIEWPOINT-API-FORMAT` | a `format`/`pattern`/enum field has no invalid-value case → add a `@cases` row with an ill-formed value → 4xx |
| **`DEPTH-FAIL`** (businessDepth < 0.7) | a **mutating success** scenario asserts only `status` → make it **prove the effect**: assert a response **body** field, a **`@query`** side-effect, or a **`@concurrent` `ok_count`** invariant. (An error/`@cases` scenario proving the status is correct — it is *not* depth-required.) |

The `VIEWPOINT-API-REQUIRED-MATRIX`/`-BOUNDARY`/`-FORMAT` rows are the repair actions for those findings **once the field-driven gate sensors ship** (they will fire only when the endpoint has `fields:`, so a bare catalog raises none — no false demands). Until then, completeness comes from enumerating the matrix mechanically from `fields:` at generation time (see `sungen-api-coverage-model`), not from these gate findings.

Stop when the gate PASSes + businessDepth ≥ 0.7, or the budget is exhausted → report residual gaps honestly (mark genuinely-unautomatable cases `@manual` with an oracle). Never fake a pass.

### 5. Record + converge
`sungen manifest --area <name>` (reuse) and ledger each phase; show the trace + the HUMAN-LOOP FOCUS. (Integrity `script-check`/`trace` for api: see run-test.)

## Taxonomy (label scenarios correctly)

| Class | What | Examples |
|---|---|---|
| **Functional** | single-endpoint behaviour | happy contract · error/validation (`@cases`) · boundary/edge |
| **Functional — flow/integration** | multi-endpoint journeys | auth/CRUD lifecycle (`create → login → get → delete`), cross-endpoint invariants |
| **Non-Functional** | performance · reliability · **security** · concurrency/idempotency | `@concurrent` race/idempotency |

A flow (`create → login → delete`) is a **Functional integration** test, **not** non-functional — don't file it under "Non-Functional". Reserve non-functional for perf/security/concurrency.

## File upload (real files)
`body:` carries only scalar fields — to send a **real file**, declare it in the catalog entry; the runtime reads the fixture from disk. Two forms, by how the server accepts the file:
- **`files:`** → `multipart/form-data`, the file is a form part: `files: { file: { path: ":avatar", mimeType: image/png } }` (+ optional text `body:` fields alongside). Shorthand `files: { file: ":avatar" }`.
  - **Multiple files under ONE field name** → make the field's value an **array** (each element a spec or shorthand string):
    ```yaml
    files:
      gift_image:
        - ":gift_image_1"    # test-data: gift_image_1 → fixtures/a.png
        - ":gift_image_2"    # test-data: gift_image_2 → fixtures/b.png
    ```
    Each element binds its own `:param` from test-data. Use this for endpoints that accept a list of files under the same field — a single object value can only hold one file. **Automate multi-file uploads with `@api`; don't defer to `@manual`.** (File uploads are sent as `multipart/form-data` built on Node's global `FormData`/`Blob` — **Node 18+, no Playwright dependency**, so the same upload runs in mobile specs too.)
- **`bodyFile:`** → raw binary body (the whole body IS the file's bytes, e.g. `application/octet-stream`): `bodyFile: { path: ":image", mimeType: application/octet-stream }`.

Fixture path resolves cwd-relative/absolute first, else `qa/fixtures/<path>` (drop sample files there, reference by name from `test-data`). An empty resolved file param omits the part → use for missing-file `@cases` error rows. `files:`/`bodyFile:` are mutually exclusive. A **`GET`/`HEAD` entry may carry no body at all** (no `body:`/`files:`/`bodyFile:`) — HTTP forbids it and the catalog lint refuses it; put the inputs in the path or `query`, or use `POST`. **Automate the upload success case with `@api`** — don't defer it to `@manual`.

## XML APIs (`encoding: xml`)

For services that accept/return XML (OTA/SOAP-style backends). Works with ANY XML shape — attributes, namespaces, CDATA, nesting.

**Send** — the body is a raw string template (YAML block scalar); declared `params:` bind as `:name` tokens:
```yaml
stock_check:
  method: POST
  path: /agent-api
  encoding: xml
  params: [agent_code, signature, item_code]
  body: |
    <?xml version="1.0" encoding="UTF-8" ?>
    <request>
      <method>StockCheck</method>
      <agent_code>:agent_code</agent_code>
      <signature>:signature</signature>
      <item_code>:item_code</item_code>
    </request>
```
Only DECLARED params bind (namespace prefixes like `soapenv:Envelope` are never touched). Bound values are XML-escaped (`& < > " '`) so data can't corrupt the document; wrap a token as `<![CDATA[:param]]>` to bind raw. Content-Type defaults to `application/xml` (override via `headers:`).

**Read** — a response whose Content-Type contains `xml` (never `html`) parses to an object for `{{name.body.<path>}}`; a missing/octet-stream Content-Type is sniffed only when the body starts with `<?xml`. Charset comes from the Content-Type, else the XML declaration, else UTF-8 (Shift_JIS responses decode correctly). Mapping:

| XML | Bound value |
|---|---|
| `<error_code>01000001</error_code>` | `{{r.body.response.error_code}}` = `"01000001"` (always strings — no numeric coercion, leading zeros survive) |
| `<smok></smok>` / `<smok/>` | `""` |
| `<shop id="7">` | `{{r.body.response.shop.@_id}}` = `"7"` (attributes keep an `@_` prefix) |
| `<soapenv:Body>` | key stays `soapenv:Body` (`{{r.body.soapenv:Envelope.soapenv:Body...}}`) |
| `<memo><![CDATA[x & y]]></memo>` | plain text `x & y`; `&amp;`-style entities decode (the 5 predefined only) |
| repeated `<item>` | declare `arrayPaths: [r.items.item]` (dot path INCLUDING the root element) — then 1 item and N items are BOTH arrays and `[0]` access never breaks. Without it, one item is an object and two are an array. |

Caveats: an element literally named `count`/`first`/`last`/`length` under an array parent is shadowed by the path helper's own accessors; parse failure keeps the raw string body (same as any non-JSON response); the 5 predefined entities decode even inside response CDATA sections (a deliberate deviation from strict XML — assert the decoded form). Field metadata / `matches schema` / OpenAPI import for XML are not supported yet. The project needs the `fast-xml-parser` dev dependency (new projects get it from `sungen init`; older ones: `npm install --save-dev fast-xml-parser` — generate warns when it's missing).

## Per-endpoint knobs & auth patterns
- **Timeout** — a slow endpoint can override the datasource default (15s) with `timeout_ms: 30000` on its catalog entry (else the datasource `timeout_ms` applies).
- **OAuth2 (pure API, no browser)** — no new grammar; it's `@api` chaining. Call the token endpoint, bind the token, thread it into later calls' `Bearer :token` header:
  ```gherkin
  @api:get_token(client_id={{client_id}},client_secret={{client_secret}}) @api:list_orders(token={{get_token.body.access_token}})
  Scenario: VP-API-FLOW client-credentials token authorizes the call
    Then expect {{get_token.status}} is 200
    And expect {{list_orders.status}} is 200
  ```
  The token endpoint is a normal catalog entry (`POST /oauth/token`, form/JSON body); `list_orders` carries `headers: { authorization: "Bearer :token" }`. For a browser-authorized session instead, use `@hybrid` + `@auth:<role>`.
- **Pagination** — use query params (`?page=:page&limit=:limit`, imported into `params`) + assert the page shape with plain field access (`{{list.body.next_page_token}}`, `{{list.body.items.length}}`); no special primitive needed.
- **Download an absolute URL from a response** (export/report endpoints returning a `body.url` on blob storage): declare a catalog entry whose `path:` is EXACTLY one token, and thread the URL as its param — this is the only supported syntax:
  ```yaml
  download_export:
    method: GET
    path: ":url"
    params: [url]
  ```
  ```gherkin
  @api:export_report @api:download_export(url={{export_report.body.url}})
  Scenario: VP-API-FLOW exported file is downloadable
    Then expect {{export_report.status}} is 200
    And expect {{download_export.status}} is 200
  ```
  The bound value must be an ABSOLUTE http(s) URL — an empty string, a relative path, or any other scheme (`file:`, `data:`, `mailto:`) fails the run loudly, so a broken export returning `{"url": ""}` can never false-pass. Presigned query escapes survive verbatim; a non-JSON body (CSV/text) is kept as text for `expect {{download_export.body}}` assertions. A catalog entry may also declare a literal absolute `path: "https://…"` (its `:params` bind with normal encoding). **Security:** when the REQUEST URL's origin differs from the datasource `base_url`, none of the datasource headers are sent (auth/tenant defaults must not leak to another origin) — a cross-origin download that needs a header must declare it in the entry's own `headers:`. This boundary applies to the initial request only: a same-origin request that REDIRECTS to another origin still carries the datasource headers (fetch auto-strips only `authorization`/`cookie`/`proxy-authorization` cross-origin) — prefer binding the final URL over following an API-origin redirect. The 15s default timeout may be short for large files (`timeout_ms:` on the entry).

## Rules
- **No HTTP, no selectors** — only `.feature` + the reviewed `apis.yaml` + `test-data`.
- **Non-prod default** — a `production` datasource is refused unless `SUNGEN_ALLOW_PROD=1`.
- **The DB is the oracle** for idempotency/side-effects — HTTP status alone can lie; pair `@api` with `@query`.
- **`@parallel` + mutating endpoints** — give each scenario **isolated data** (a `{{$uuid}}` email, a `@cases` row, or its own created resource) and **self-clean** (delete what it created); shared inputs race under parallel execution.
- **No dead data** — every `test-data` key must be bound into a scenario (`{{key}}`, a `@cases` dataset, or an override). `sungen audit`/the generate lint flag unreferenced keys.
