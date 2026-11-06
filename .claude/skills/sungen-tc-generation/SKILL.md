---
name: sungen-tc-generation
description: 'Use when create-test needs to translate spec.md/Figma/UI into .feature + test-data.yaml. Invoke after requirements are read, before any selectors.yaml work.'
user-invocable: false
---

## Traceability — tag the requirement each scenario proves

Every requirement id in `requirements/spec.md` (`FR-`/`TR-`/`NFR-`) must be reachable from a
scenario, and the link must be **written down, not inferred**:

```gherkin
@high @spec:FR-003
Scenario: VP-VAL-002 An email in an invalid format shows the format error
```

- Put `@spec:<id>` on the scenario that actually proves the requirement; repeat the tag when one
  scenario proves several (`@spec:FR-003 @spec:FR-004`).
- One requirement may be proved by several scenarios — tag each of them.
- A requirement you deliberately do not cover here (performance budget, i18n suite, server-side
  concern) needs no tag; record it later in the delivery map's `requirements:` section with a
  status and reason.
- Why it matters: `sungen audit` also counts a requirement as covered when its keywords merely
  resemble a scenario title, and reports `SPEC-TRACE-IMPLICIT` for those. Delivery's requirement
  table follows **only** the explicit tag — an untagged requirement reads as an uncovered gap in
  the customer-facing coverage sheet even when a scenario does prove it.

## ⚠️ Gotchas — read before generating

- **Field-level test-data follows the Data Factory standard — don't hand-invent values.** When the unit has input fields, the field values (valid / boundary / invalid + error codes) come from the **`sungen-data-factory`** catalog via a field-map + `sungen data gen` (create-test step 5.3), not free-form guessing. Weave the standardized boundary/invalid sets into `@cases` with `CHK-*` trace. This skill still owns scenario structure, viewpoints, and non-field oracles.
- **Write incrementally — never emit the whole suite in one response.** Build the `.feature` in batches via successive `Write`/`Edit` (≈10–15 scenarios per call). For **Full coverage**, write tier-by-tier: `Write` Tier 1 → `Edit` append Tier 2 → `Edit` append Tier 3.
  → One huge `Write` can exceed the model's output-token cap → `API Error: Claude's response exceeded the N output token maximum`. Single-pass full coverage only fits when `CLAUDE_CODE_MAX_OUTPUT_TOKENS ≥ 64000`; otherwise batch. Batching also lets the audit/reviewer run per batch — higher quality.

- **Sharded (parallel) generation — keep each shard self-contained.** When `create-test` fans out one `sungen-generator` sub-agent per shard (a viewpoint theme like `VP-SEC`, or a `spec.md` section — see create-test Steps 5a–5c), you are generating **only your shard**: emit your scenarios under **your own `VP-` prefix** (disjoint namespace, so ids never collide), as a **headerless fragment** (no `Feature:` line — the orchestrator owns the single header). Do not reference or renumber other shards. The orchestrator concatenates fragments in stable order (spec-section / theme order), dedups by title, and unions test-data. Small fragments also stay under the output-token cap by construction.

- `spec_figma.md` exists → read file only, **NEVER** call `mcp__figma__*`
  → PAT auth flow already done by `sungen-capture` (mode figma-pat); re-calling fails or duplicates work.

- `selectors.yaml` → do **NOT** generate — handled by `run-test`
  → Selectors need live DOM inspection via Playwright MCP, only `run-test` triggers it.

- `@steps:` scenarios → **NO** priority tag (setup blocks, not test cases)
  → Priority tags filter test runs; setup blocks must always run via `@extend:`, never be filtered out.

- Hardcode exact error messages in test-data.yaml — never leave `{{error}}` vague
  → Assertions are string-equal; vague placeholders produce false-positive passes.

- **Data classification — decide per value which of 3 kinds it is** (the scaffolded test-data header + `qa/TEST-DATA-GUIDE.md` explain this to QA; PRESERVE that header when rewriting the file):
  1. **INVENTED** (backend need not accept it): validation inputs, boundaries, XSS payloads, expected messages → hardcode in the yaml.
  2. **ENV-BOUND** (must exist in the test environment; harmless if leaked): seeded ids, registered emails, valid tokens → keep in yaml with a `# ENV-BOUND: <what to seed>` marker; per-environment values go in `<unit>.<env>.yaml`.
  3. **SECRET** (real login / harmful if leaked): NEVER write the real value — reference `${QA_<NAME>}` (uppercase, `QA_` prefix mandatory; only that shape resolves from `.env.qa` at runtime, any other `${...}` stays literal). Whenever you emit a new `${QA_*}` ref, ALSO append the name to `.env.qa.example`. Note: "password" is not automatically SECRET — a made-up password probing validation is INVENTED; only a real account's password is SECRET (and `@auth:role` via `sungen makeauth` usually removes the need entirely).

- Mixing `@auth:X` + `@no-auth` in one feature is fine by default; just never tag that feature `@serial`
  → Playwright shares browser context per worker; auth state leaks across scenarios without isolation.

- XSS ≠ SQL injection — generate as **2 separate scenarios**, never merge
  → Different attack vectors (client render vs DB query) and different observable failures (alert popup vs DB error).

- **SQL injection has 2 layers — generate both when field reaches a backend query:**
  - Layer 1 → field UI: special chars blocked by input field → `@high` automated scenario
  - Layer 2 → API server: field accepts alphanumeric (e.g. LIKE/search field) → send payload like `1 OR 1=1` directly to API (curl/Postman, bypass UI) → verify server uses parameterized query, not string concat → `@high @manual`
  → Skipping Layer 2 = missing real attack vector even when field validation is correct.
  → Trigger: any free-text field whose value reaches a LIKE, partial-match, or dynamic WHERE clause.

- **Concurrent / race conditions — generate `@manual` when spec or viewpoint mentions multi-tab, multi-user, or simultaneous actions:**
  → Examples: two browser tabs same search with different conditions must not contaminate each other; double-click submit must not create duplicates.
  → `@normal @manual` by default — unless data integrity at risk → `@high @manual`.
  → Do NOT skip because "hard to automate" — document as `@manual` so it appears in the test plan.

- Spec mentions outcomes on a **different user-facing surface** (another URL, mobile app, user portal, widget)
  → "Single screen focus" means don't test other screens' UI patterns — it does NOT exempt you from documenting business outcomes on other surfaces.
  → **Always generate `@manual` scenarios for the output surface** — at minimum one `@high @manual` per cross-surface business rule.
  → If the output surface is reachable with a different auth role in the same test run, use `@auth:role` + `@extend` setup instead of `@manual`.
  → Skipping these entirely = zero test can catch "admin creates record correctly but user never sees it" — the most silent failure class.
  → Add `Display surfaces` + `Cross-surface rules` to the Coverage Map (see Step 2).

- Spec uses **inclusive boundary operators** (`<=`, `>=`, `≤`, `≥`, "at least N", "no more than N", "up to and including")
  → The boundary point itself **must** be a test case — not just "inside" and "outside" regions.
  → Generate: `boundary - 1` (must fail/not-display), `boundary` (must pass/display), `boundary + 1` (must pass or fail depending on direction).
  → Example: `end_at >= now` → test `end_at = now` (inclusive: display), `end_at = now - 1s` (exclusive: no display).
  → Off-by-one at inclusive boundaries is the most common display-logic bug.

- **AND condition in business rule — test each branch failing independently:**
  When spec defines "A AND B AND C must all be true", generate:
  (1) A fails while B/C pass, (2) B fails while A/C pass, (3) C fails while A/B pass.
  OR condition: generate 1 scenario per branch where that branch alone triggers the outcome.
  → Happy-path only = missing the most common multi-condition implementation bug.

- **Many inputs, same steps → ONE data-driven scenario (`@cases`), not N copies:**
  When a rule needs lots of inputs with the *same* step shape (email/format validation,
  BVA boundary triples, EP classes, decision-table rows), tag one scenario `@cases:<dataset>`,
  reference each row's columns as `{{col}}`, and put the rows as a LIST in test-data:
  ```gherkin
  @high @cases:email_validation
  Scenario: VP-VAL-001 The email field rejects invalid formats
    When User fill [Email] field with {{email}}
    Then User see [Error] message with {{expected_error}}
  ```
  ```yaml
  email_validation:
    - { case: "no @",  email: "plainaddress", expected_error: "Invalid email" }
    - { case: "valid", email: "ok@x.com",     expected_error: "" }
  ```
  → one `test()` per row, each labelled by `case`. Adding inputs = editing test-data (no recompile),
  and env overlays apply. Prefer this over duplicating a scenario per value. (Gherkin
  `Scenario Outline`/`Examples` is NOT supported — use `@cases`.)

---

## Tier System

| Tier | Priority | What to generate | When |
|---|---|---|---|
| **Tier 1** (default) | `@high` | Happy paths, required validation, core business rules, security basics | First run of `create-test` |
| **Tier 2** (expand) | `@normal` + `@low` | UI presence, optional validation, edge cases, cosmetic checks | User runs `create-test` with "Add viewpoints" mode |

**Tier 1 stops when:**
- Every core user task has a happy-path scenario
- Every validation rule has at least one negative case (required, format, boundary)
- Every business rule has at least one scenario
- Security basics covered (auth redirect, permission blocked, injection)

**Tier 2** only when the user explicitly chooses "Add viewpoints" or "Add new sections".

**Fast-path** — display-only screens (no form, no state machine, ≤2 sections):
skip Coverage Map (Step 2) entirely. Go straight to Output Format with Tier 1 VP-UI presence checks
and any obvious VP-SEC (auth redirect). Spec drives nothing here — viewpoint covers it.

## Update Mode

When `.feature` already has scenarios, summarize and ask:
1. **Add new sections** — append Tier 1 scenarios, continue numbering
2. **Add viewpoints** — expand existing sections with Tier 2 scenarios
3. **Replace all** — overwrite with fresh Tier 1 generation

For append: read highest `VP-<CAT>-<NNN>`, continue from next number. Never modify existing scenarios.

> **IDs are immutable & append-only.** The dashboard tracks each test case by its ID (namespaced at export as `<SCREEN>-<CAT>-<NNN>`, e.g. `LOGIN-SEC-001`). When updating a `.feature`:
> - **Never renumber** existing scenarios — even when earlier ones were deleted. Renumbering changes the dashboard key and loses that test's pass/fail history.
> - **New scenarios** take the next unused number per `VP-<CAT>` group (continue from the highest; do NOT fill gaps left by deletions).
> - **Deleting** a scenario retires its number permanently — never reuse it.
> - Editing a scenario's title text or steps is fine — the ID stays the same.

---

## Input Sources

Auto-detected by `create-test` before invoking this skill:
- `spec.md` — primary, always read if present
- `spec_figma.md` — Figma supplement (PAT flow already done — just read the file)
- `ui/*.{png,jpg,jpeg,webp,gif}` — design images. **Every one is read**, and turned into the Design Inventory below — not just "visual context".
- `ui/figma-meta.md` — Figma MCP capture (`sungen-capture` mode figma-mcp): `## Components` and `## Text Content` feed the Design Inventory; `## Text Content` also supplies exact labels for `test-data.yaml`.
- `ui-inventory.md` — the Design Inventory (written by create-test, see § Design Inventory). When it already exists, keep its ids and append new items; never renumber.
- `test-viewpoint.md` — test conditions checklist; scan entirely regardless of format.
  Format varies across projects: hierarchical table (Level 1–4 columns), flat checklist,
  prose, or mixed. Regardless of format:
  1. Scan entire file top to bottom.
  2. Each row / bullet / item = 1 viewpoint → add to `Viewpoint items` in Coverage Map.
  3. Do NOT pre-classify into buckets before scanning — classify only when
     writing the scenario.
  4. **If it declares viewpoint IDs** (e.g. `VP0`, `VP1`…`VP12`, `MS-HP-001`), capture each
     item WITH its ID and **reuse that ID as the scenario code** — do not invent a generic
     `VP-<CAT>` scheme (the harness Taxonomy-match gate FAILs on mismatch).
- `qa/context.md` — project-wide context set by the QA lead. Read ONCE before building the Coverage Map; apply to every screen. Extraction rules:
  - **Roles** → for each role in the table: add to the `@auth:X` tag pool; generate a VP-SEC blocked-access scenario for every role boundary relevant to this screen.
  - **Testing strategy → Focus areas** → if `security` listed: VP-SEC is mandatory Tier 1 for every free-text input regardless of spec risk level; if `ui` not listed: all VP-UI scenarios move to Tier 2 minimum.
  - **Testing strategy → Mandatory coverage** → each line is a hard override applied to this screen regardless of spec risk; document in `Context constraints` of the Coverage Map.
  - **Testing strategy → Deprioritize/skip** → record in `Context constraints`; suppress those VP categories from Tier 1 generation.
  - **Global business rules** → add each to the `Business rules` section tagged `[G]` (e.g. `[G1 – soft-delete only]`); treat as `HIGH` risk unless stated otherwise.
  - **Error patterns** → use as fallback only when `spec.md` does not give exact error text; never override spec-specified messages.
  - If `qa/context.md` is absent: proceed without it — no impact on the generation flow.

**Single screen focus**: one URL = one screen. Modals on same page = part of this screen.
This means: do not test other screens' UI layout or navigation. It does NOT mean skip documenting business outcomes that your screen's actions cause on other surfaces. Those cross-surface outcomes must appear in the Coverage Map and be covered by at least `@manual` scenarios.

**Capture real data** from live page or Figma: option labels, error messages, counter keywords. Hardcode in `test-data.yaml` — stale data that fails fast beats `@manual` that never runs.

---

## Design Inventory (UI items)

**When:** the unit holds any design source — images in `requirements/ui/`, `ui/figma-meta.md`, or `spec_figma.md`. Without one, skip this section.

**Why:** a design read "for visual context" leaves no trace. An item that exists only on the design (a column `spec.md` never names) or contradicts the spec is then missed by the author AND the reviewer, and "deliberately deferred" looks exactly like "never seen". `sungen audit` checks this file (`UI-INVENTORY-MISSING`, `UI-INVENTORY-INCOMPLETE`, `UI-ITEM-UNCOVERED`).

**Reading order:** `spec.md` (authoritative) → `spec_figma.md` → `ui/figma-meta.md` (`## Components`, `## Text Content`) → **every** image in `ui/`. Many images: group them (primary screen vs states/variants) but read them all — an unread state screen is an un-inventoried one.

**Write `requirements/ui-inventory.md`** — one row per component, table column, button/link, field, state (empty/error/loading/disabled), and design annotation:

```markdown
# UI Inventory — <screen>
images read: 3/3 · figma-meta.md: read · spec_figma.md: absent

| ID | Item | Kind | Source | Seen in |
|---|---|---|---|---|
| UI-001 | Job List table | table | spec+design | ui/list.png |
| UI-002 | Contract plan | column | spec+design | ui/list.png |
| UI-014 | Salary range | column | design-only | ui/list.png |
| UI-015 | Status badge "Closed" | column | conflict — spec says "Expired" | ui/list.png |
```

- **Source:** `spec+design` (both name it) · `design-only` (only the design shows it) · `conflict` (they disagree — say how in the cell). `spec.md` stays authoritative; a conflict is reported, never silently resolved in either direction.
- **IDs** `UI-NNN` are append-only, like scenario ids: re-runs keep existing rows and continue numbering.
- `images read: N/N` counts the images you actually opened against the images in the folder.

**Account for every `design-only` / `conflict` item in the `.feature`** — either:
- **cover it**: tag the scenario that proves it `@ui:UI-014`, or
- **defer it with a reason** in a feature comment: `# UI-DEFERRED: UI-002..013 — per-column checks are owned by job-detail`. A deferral without a reason defers nothing.

`spec+design` items are covered through the spec (`@spec:`), and citing their ids is optional.

---

## Generation Workflow

### Step 1 — Orient: patterns + risk

Identify core user tasks. Categorize page sections by UI pattern group using `sungen-viewpoint`:
data entry (A), data manipulation (B), data exploration (C), display/feedback (D), identity (E).

Proceed directly to Step 2. Pause to ask the user only when section priority is genuinely ambiguous from spec (e.g. multiple top-level forms without business context).

### Step 2 — Build Coverage Map

Read `spec.md` fully, then extract into a Coverage Map **before writing any scenario**. Nothing is skipped at this stage — risk only affects depth in Step 3.

**Risk tags:** HIGH = complex business rules, cascading fields, multi-step state changes, auth/integration. LOW = display-only, static labels, read-only fields.

```
Context constraints: [populated from qa/context.md before writing any scenario]
                     roles: [list roles, e.g. admin / manager / staff]
                     strategy: [active overrides, e.g. "VP-SEC mandatory T1", "VP-UI → T2 only"]
                     global rules: [G1 – ...] → also appear in Business rules below tagged [G]
                     → leave empty if qa/context.md is absent or has no entries applicable to this screen
User journeys:       [J1 – ...], [J2 – ...]
Validation rules:    [V1 – field → "exact error text"], [V2 – ...]
Business rules:      [B1 HIGH – ...], [B2 LOW – ...]
Secondary behaviors: [SB1 – tiebreaker: when X identical → sort by Y DESC]
                     [SB2 – default state when no condition applies: ...]
                     → capture "otherwise", "when tied", "secondary sort", "fallback" rules here
States:              [StateA → StateB], [blocked: StateC → StateA]
Security:            [S1 HIGH – OAuth/SSO flow], [S2 HIGH – free-text inputs → XSS + SQL injection]
Concurrency risks:   [C1 – spec/viewpoint mentions multi-tab, concurrent edit, or simultaneous action]
                     → leave empty if neither spec nor viewpoint mentions concurrency
Display surfaces:    [Surface1 – /url, auth:role], [Surface2 – /url, auth:user]  ← list every surface spec mentions as output
Cross-surface rules: [B1 – action on /admin → outcome on /user (3-condition AND logic)]  ← mandatory if Display surfaces exist
Inclusive bounds:    [start_at <= now → boundary: start_at=now must display], [end_at >= now → boundary: end_at=now must display]
Viewpoint items:     [TV-01 – <exact wording from test-viewpoint row 1>]
                     [TV-02 – <exact wording from test-viewpoint row 2>]
                     → one entry per row/bullet in test-viewpoint.md, regardless of format
                     → leave empty if no test-viewpoint.md present
UI items:            [UI-014 – Salary range column (design-only) → covered by VP-TAB-012]
                     [UI-002..013 – per-column checks → deferred: owned by job-detail]
                     → one entry per Design Inventory row: covered / deferred-with-reason / uncovered
                     → leave empty if the unit has no design source
Spec gaps:           [G1 – behavior undocumented → verify with dev]
```

**Example — login screen (2 sections, 5 validation rules):**
```
User journeys:       [J1 – account login with valid credentials]
Validation rules:    [V1 – empty email → "Invalid email address"],
                     [V2 – short password → "Password must be at least 6 characters"],
                     [V3 – invalid format → "Invalid email address"],
                     [V4 – wrong credentials → "Invalid email or password"]
Business rules:      [B1 HIGH – toggle is same-page state, not route change]
States:              [Default → Account form], [Account form → Google login],
                     [Account form → Loading → Success/Error]
Security:            [S1 HIGH – free-text email/password → XSS + SQL injection]
Display surfaces:    [Login page – /login, no-auth]  ← same surface, no cross-surface rules needed
Cross-surface rules: (none)
Inclusive bounds:    (none)
Spec gaps:           [G1 – auth error on 401 unconfirmed on live]

Tier 1 output:    VP-LOGIC-001 (toggle reveals form), VP-LOGIC-002 (restore Google state),
                  VP-LOGIC-003 (login success → redirect), VP-VAL-001 (empty fields),
                  VP-VAL-002 (invalid format), VP-VAL-003 (short password),
                  VP-VAL-004 (wrong credentials), VP-SEC-001 (XSS), VP-SEC-002 (SQL injection)
```

**Example — admin notice management screen (cross-surface + inclusive bounds):**
```
User journeys:       [J1 – create notice], [J2 – edit notice], [J3 – toggle active], [J4 – delete notice]
Validation rules:    [V1 – title empty → "必須項目です"], [V2 – end_at <= start_at → "終了日時は開始日時より後に…"],
                     [V3 – title > 255 chars → "255文字以内で入力してください"]
Business rules:      [B1 CRITICAL – notice displays on user-facing portal only when: is_active=ON AND start_at<=now AND end_at>=now],
                     [B2 HIGH – is_active=ON by default on create; not on form],
                     [B3 HIGH – toggle not blocked by date range]
States:              [Scheduled → Live → Expired], [any → OFF via toggle]
Security:            [S1 HIGH – free-text title/content → XSS + SQL injection], [S2 HIGH – admin-only, non-admin blocked]
Display surfaces:    [Admin panel – /admin/notices, auth:admin], [User portal – /user-facing-url, auth:user]  ← TWO surfaces
Cross-surface rules: [B1 – admin creates/toggles → user portal shows/hides banner; 8-combination decision table]
Inclusive bounds:    [start_at <= now → boundary: start_at=now must display], [end_at >= now → boundary: end_at=now must display]
Spec gaps:           [G1 – exact content max length unconfirmed]

Tier 1 output (admin surface):    VP-LOGIC-001 to VP-LOGIC-013, VP-VAL-001 to VP-VAL-007, VP-SEC-001 to VP-SEC-003
Cross-surface output (user portal): VP-LOGIC-CS-001 @manual (M-01: all 3 ON → display),
                                    VP-LOGIC-CS-002 @manual (M-05: is_active=OFF → no display),
                                    VP-LOGIC-CS-003 @manual (M-03: start_at future → no display),
                                    VP-LOGIC-CS-004 @manual (M-02: end_at past → no display),
                                    VP-LOGIC-CS-005 @manual (boundary: start_at=now → display),
                                    VP-LOGIC-CS-006 @manual (boundary: end_at=now → display)
```

**❌ Bad Coverage Map — silently misses validation rules AND cross-surface outcomes:**
```
User journeys:    [J1 – create notice], [J2 – edit notice]
Business rules:   [B1 – display logic: is_active + date range]
Security:         [S1 – admin only]
```
→ Output misses VP-VAL-* entirely (validation rules not listed with exact messages).
→ Output misses user portal scenarios entirely (no `Display surfaces` or `Cross-surface rules` field).
→ Output misses boundary tests for `start_at <= now`, `end_at >= now` (no `Inclusive bounds` field).
→ Root cause: Coverage Map only captured admin CRUD journeys; spec section on "display flow" and "user-facing surface" was not scanned into the map.

**✅ Good** — see admin notice example above: `Display surfaces` lists every URL spec mentions as output, `Cross-surface rules` maps each admin action to its user-facing outcome, `Inclusive bounds` flags every `<=`/`>=` for BVA. Every item maps to a VP-ID in `Tier 1 output`.

#### Critical business-viewpoint pre-gate — pass `sungen audit` on the FIRST pass

> The harness gate FAILS (and forces repair rounds → wasted tokens) when a page-type's critical **business** viewpoints are missing or **shallow**. Generate them correctly the first time. A business-critical `Then` must assert **DATA**, never just `see [X] page/section/modal`.

**By page-type, generate a DEEP scenario for each (before expanding UI/validation/subscription):**

| Page-type | Must-cover viewpoints (each with a data assertion) |
|---|---|
| **e-commerce list / home** | list-data (card has image+name+price+add) · product-detail-consistency · cart-correctness · category-filter-correctness · **brand-filter-correctness (separate from category)** · add-to-cart success · nav-core |
| **form** | required-validation · format/boundary · submit-success |
| **auth** | valid-login · invalid-credential · access-control |

**Required assertion shapes (use these, not bare visibility):**
- Card info: assert at **card level** (image+name+price together), e.g. `User see all [Product Card] contain {{...}}` — not `see [Section]` (section-level passes even if one card lacks price).
- Cross-screen consistency (detail/cart): **capture then compare** —
  ```gherkin
  When User remember [Product Name] text as {{selected_product_name}}
  And User remember [Product Price] text as {{selected_product_price}}
  And User click [View Product] link
  Then User see [Detail Product Name] header with {{selected_product_name}}
  And User see [Detail Product Price] text contains {{selected_product_price}}
  ```
  Cross-screen target → **automate it in the flow** (`/sungen:add-flow`), NOT as a `@manual` screen copy. A single home→target journey runs as one Playwright test, so it is automatable — "needs another screen" is not a reason for `@manual`. The screen keeps its screen-contract scenarios; the flow owns the cross-screen depth.
- Filter result (category AND brand, separately): `Then User see all [Result Product Name] contain {{selected_category}}` — proves EVERY item belongs, not one.

**Depth is a GATE dimension (harness-roadmap P1) — self-raise, never silently go shallow:**
- For every data-correctness theme the catalog marks `depth.requires: data-assertion`, emit its `depth.template` shape by **default** — don't wait for the repair loop. `sungen audit` measures `businessDepth` (ratio of these scenarios that assert data) against an intent threshold (functional ≥ 0.70); below it the **gate FAILs**.
- **Verify depth deterministically before the gate:** run `sungen depth-lint --screen <name>`. It classifies every shallow business-critical scenario into **deepen-in-place** (add the theme's value assertion — the printed `template` is a hint, fit it to the actual claim) vs **cross-screen** (route to a flow / `@manual:Mx`). Clear the `deepen` list first — this is the mechanical way to hit `businessDepth` on the first pass instead of churning repair rounds. Never fake a value assertion onto a visibility/behavior scenario the lint over-counts; leave it and note the over-count.
- `depth.cross_screen: true` (cart / detail / filter / brand correctness) → write the deep capture/compare shape as an **automated flow scenario** (in the flow — do NOT leave a full-step `@manual` duplicate on the screen). `@manual` is **only** for genuine judgment (M6 visual/UX · M8 not-worth · M9 human), a device/OS limit on mobile (M10 OS capability · M11 real-device only), or a missing capability (M1–M5/M7), and it **must** carry a reason code (`@manual:Mx`, or a reason comment the planner can infer). A `@manual` scenario that still has full automatable steps (a data assertion, no visual/mock/a11y judgment) is now flagged by `sungen audit` as `MANUAL-AUTOMATABLE`, and business-critical scenarios you defer to `@manual` are reported as `DEPTH-DEFERRED` (they do NOT silently inflate `businessDepth`). Deferring automatable work to `@manual` lowers quality — automate it in the flow instead.
- **Pick the right `@manual:Mx` code — it decides which driver can later automate the case** (`sungen audit` flags a code↔reason mismatch). Tag the code that matches the **oracle the reason describes**:

  | The reason needs… | Code | Unblocked by |
  |---|---|---|
  | a data state you can't make from the UI (empty list, seeded record, missing-image product) | `M1` | data-factory / db |
  | an **API/DB/persistence** assertion (stored value, parameterized-query / SQLi-safe, server-side effect) | `M2` | **api / db** |
  | network / fault injection (offline, slow, request failure) | `M3` | mock |
  | a stable selector / test-id that doesn't exist | `M4` | — (locator contract) |
  | an external dependency (email, payment gateway, download) | `M5` | mail-file / contract |
  | visual / UX / responsive / a11y judgment | `M6` | — (keep manual) |
  | not worth automating · true human judgment | `M8` / `M9` | — (keep manual) |
  | **mobile** — what the OS/driver will not expose (notification shade, app switcher/recents, permission variance by OS version, call interruption) | `M10` | — (keep manual) |
  | **mobile** — what an emulator/simulator cannot be (biometrics, real camera, NFC, a real push token) | `M11` | — (keep manual) |

  e.g. "submit a payload then check the subscribers **table**" is an API+DB oracle → `@manual:M2` (NOT `M1`); "seed a DB with zero products" is a data state → `M1`; "throttle the network" → `M3`.
- **Prefer automation-ready `@requires:<cap>` over prose `@manual`.** When you *can* write the steps for a capability-manual case (an API/DB oracle, a seeded state), write it **automation-ready** — the real `@api`/`@query`/… steps tagged `@requires:<cap>` (e.g. `@requires:db @query:subscriber_row`) — instead of a prose `@manual:M2`. It compiles to a skipped-with-reason stub until `sungen capability add <cap>`, then runs as a real test with **no rewrite**. Reserve prose `@manual:Mx` for cases whose steps genuinely can't be expressed (M6/M8/M9 judgment, M10/M11 device-or-OS limits on mobile, or a capability with no driver). `sungen audit` reports these as `AUTOMATION-READY-PENDING` (not a gap, not manual).
- **If the spec lacks the concrete value** a deep assertion needs (exact message, price, count): still write the deep shape with a `{{var}}` placeholder and leave a `# SPEC-GAP: <field> value not in spec` comment — do **not** downgrade to `see [X] section`. A visible gap is better than a silent shallow pass.
- **Blind-Spot Memory:** before finishing, run `sungen blindspot list --prompt` (Bash) and make sure the suite satisfies each recorded pattern (e.g. "for any Add/Create action: check success + resulting data state + duplicate/double-submit"). These are gaps QA hit before — don't repeat them.

**First-pass anti-patterns (these are exactly what the gate/reviewer reject — avoid them):**
- Title↔steps mismatch: e.g. a "no-result state" scenario that clicks a query which **returns** products. Steps must create the condition the title claims.
- Tautology `Then`: `click [Next Slide]` → `see [Carousel] section` (always visible, proves nothing). Assert the change (new slide title differs).
- Business-critical scenario ending at `see [Added] modal` / `see [Cart] page` / `see [Category Products] page` with no data assertion.
- Brand filter covered only as navigation (must assert products belong to the brand).

**Balance:** cover all the above (deep) BEFORE expanding subscription / UI-presence / extra validation edge cases. Do not over-invest in subscription while cart/detail/filter correctness are shallow.

#### Harness gates — satisfy on the FIRST pass (don't make the repair loop fix them)

`sungen audit` enforces these. Generate compliant output up front:

1. **Taxonomy-match** (`VP-TAXONOMY-MISMATCH`, gate-FAIL) — when `test-viewpoint.md` declares its own viewpoint IDs (e.g. `VP0`, `VP1`, … `VP12`, `MS-HP-001`, `MS-EH-001`), **reuse those IDs verbatim as the scenario codes**. Do NOT invent a generic `VP-UI / VP-LOGIC / VP-VAL` scheme — that breaks the coverage matrix. Only fall back to `VP-<CATEGORY>-<NNN>` when the viewpoint file declares no IDs.
   - **`--accept-viewpoint` is the QA's command, not yours.** `sungen next` shows it as a `⚖ decide`
     step. Present the declared-id diff and stop; a yardstick accepted by the process it measures
     reads 100% by construction.
   - **Match the scenarios to the file — never the file to the scenarios.** A filled `test-viewpoint.md` is an input; do not rewrite its declarations to fit what you generated. That is not compliance, it is moving the yardstick: `traceability` + `atomicLedger` then read 100% by construction and a dropped viewpoint stops being missing from anything. Disagree with the taxonomy → propose the diff and ask. `sungen audit` reports `VIEWPOINT-BASELINE-CHANGED` and excludes both axes until a human accepts the change (`sungen audit --screen <name> --accept-viewpoint`).
2. **Spec-coverage triggers** (`TRIGGER-UNCOVERED`, gate-FAIL) — the Validation-Rules table lists a **trigger** per constraint (e.g. `blur, submit`). Generate one scenario **per (constraint × trigger)** — a `format` rule validating *on blur AND on submit* needs BOTH a blur scenario (`press Tab`) and a submit scenario (`click [Submit]` / `press Enter`). Never collapse the trigger × input matrix to one representative case.
3. **Claim-Proof** (`CLAIM-UNPROVEN`) — a title claiming `all`/`only`/`every`/`single`/`correct`/`same`/`changes`/`hidden`/`cleared`/`restored`/`independent`/`sanitized`/`announces` MUST have the matching assertion (`see all …`, count, `remember`+compare, `is hidden`, return-and-assert-empty, etc.). If the title promises it, the steps must prove it.
   - **Negative / absence claims** (`does not` / `no` / `never` / `prevents` / `không` / `chưa` — any language; `no-side-effect/no-duplicate`, `negative-claim/absence`): the `Then` must **differ** between the claim holding and not holding. A terminal `see [X] page` that looks identical whether or not the bad thing happened proves nothing. For a side-effect that should NOT repeat (re-submit on back, re-charge, duplicate order, resend OTP), assert the **count is unchanged** (`User see [Records] table with {{one}}` / `row with {{count}}`); if it's not UI-observable, mark `@manual` with a request-count oracle (shape below). This is general — it covers any side-effect, not a fixed verb list.
4. **Downstream-scope** (`DOWNSTREAM-SCOPE-MISSING`) — when the spec's Navigation Flow / success target is **another screen** (e.g. a confirmation/sent page), don't stop at a terminal `see [X] page`. Either cover that screen's content/guards (if its viewpoint items are in scope — they often have their own `MS-*` IDs), or scaffold it (`sungen add --screen <name>`) and note the handoff. Do not silently drop the downstream surface.
5. **Manual-oracle** (`MANUAL-STEPS-INSUFFICIENT` at audit, `SG-W008` at generate) — a `@manual` scenario has exactly TWO valid shapes; the hybrid (Gherkin steps with the oracle stashed in comments) is **banned**:
   - **Shape A — documentation-style** (NO Gherkin steps at all): the whole procedure lives in comments — numbered **action steps first**, then labeled `Observable:`/`Oracle:` lines. Use whenever the expected is unconfirmed (SPEC-GAP) or any part of the procedure is outside the DSL (password manager, DevTools, browser zoom/viewport, multi-tab, DB seeding). Do NOT keep a stray Gherkin fragment (`Given … page` + one fill) — a `When` without `Then` is invalid Gherkin, and a partial fragment can INVERT the chronology (e.g. "save a credential first" rendering AFTER "open the page").
   - **Shape B — complete Gherkin**: `Given/When/Then`, all steps present. Use when the expected IS known and only execution is manual.
   Gate (`SG-W008`, both directions): a `@manual` with Gherkin steps but no `Then` fires — comments (even labeled `Oracle:` lines) can NOT substitute for the missing `Then`. A `@manual` with NO steps ALSO fires when its comments lack ≥1 numbered action line + ≥1 labeled `Observable:`/`Oracle:`/`Expected:` line — prose paragraphs and rationale-only comments export EMPTY Steps/Expected cells.
   In shape A the observable/oracle lines MUST carry their **label** (`Observable:` / `Oracle:` / `Expected:`) — the delivery parser extracts Expected results **by label**; unlabeled prose all lands in the Steps column and the Expected cell exports EMPTY. And the numbered action steps MUST precede them — without the actions the tester has an oracle but no procedure.
   **Oracle layer must match the unit** — a screen-unit manual defaults to a **UI oracle**: phrase the expected as what the tester SEES (on-screen message, list/table state after reload). If the business effect IS UI-observable, do NOT cite API responses or DB state as the oracle — "server rejects the request" is verified by *the error message on screen + no new row after reload*, not by reading the API response. Only when the effect has NO UI observation (mail content, stored hash, request count, response-body leak) may the oracle use another layer — then: (a) put the **layer label inside the line**: `Oracle: UI — …` / `Oracle: API — …` / `Oracle: DB — …` so QA knows the tooling needed from the deliverable alone; (b) tag `@manual:M2` so the capability planner recommends the api/db driver; (c) prefer the automation-ready `@requires:api`/`@query` form when the steps are expressible. A deep engineering check may ride along as `(optional deep check: …)` appended to the UI oracle — it must never REPLACE a UI-observable oracle.
   **Respect the project's VERIFICATION SCOPE (the recorded test type).** Read `qa/capabilities.yaml` — the effective scope is `verification:` if set, else derived from `enabled` (`ui` always; `api`/`db` only if that driver is on). If the project is **E2E/UI-only** (no `api`/`db`) **and the test-viewpoint does not ask for API/DB verification**, do **NOT** author `@api`/`@query`/`@requires:api|db` verification — keep the oracle **UI-observable**, or leave the deep check as an `(optional deep check: …)` note. Author API/DB verification only when the scope (or an explicit viewpoint) calls for it, and even then keep the API/DB-in-E2E combination to a **≤20% band** of scenarios (the effects with no UI observation) — never the default. `sungen audit` reports `VERIFICATION-OUT-OF-SCOPE` when a unit uses API/DB verify the project never opted into.
   Use this comment shape:
   ```gherkin
   @high @manual
   Scenario: VP-… <claim>
     # MANUAL: <why it can't be automated — needs network capture / inbox / screen-reader / multi-tab>
     # Tester verifies:
     #   1. <setup>            e.g. seed a registered email; throttle the network
     #   2. <action>           e.g. click [Submit] with the request in flight
     #   3. Observable: <what> e.g. Observable: only ONE POST is dispatched
     #   4. Oracle: <layer — tool>  e.g. Oracle: API — DevTools Network panel · Oracle: UI — visual inspection · Oracle: DB — read the row via DB client
   ```

#### Tier 1 guard — minimum before writing scenarios

| Spec section | Minimum requirement | Tag |
|---|---|---|
| Required field | 1 required-error TC per field | `@high` |
| Validation rule | 1 exact-message TC per rule | `@high` |
| Business rule | 1 behavioral TC per rule | `@high` |
| **Secondary behavior / tiebreaker** | **1 TC per tiebreaker or fallback rule in `Secondary behaviors`** | **`@high`** |
| Auth / OAuth / permissions | 1 VP-SEC TC — **with a rendered-content oracle**: `see [X] page` alone is a URL check a late 403 redirect passes; give the page selector an `anchor:` or add a content assertion (`ORACLE-URL-ONLY`) | `@high` |
| Free-text input | 1 XSS TC **and** 1 SQL injection TC (separate) | `@high` |
| **Free-text LIKE / partial-match field** | **1 field-level SQL TC + 1 API-level SQL `@manual` TC** | **`@high`** |
| Lifecycle states | 1 key state transition TC | `@high` |
| Public page | 1 accessible-without-auth TC | `@high` |
| **Cross-surface business rule** | **1 `@high @manual` TC per affected surface per rule** — even if not automated today | **`@high`** |
| **Inclusive boundary condition** (`<=`, `>=`) | **4 TCs per range: `min-1` (fail), `min` (pass), `max` (pass), `max+1` (fail)** — never skip the exact boundary point | **`@high`** |
| **Concurrency risk (from `Concurrency risks`)** | **1 `@manual` TC per risk — `@normal` unless data integrity at risk → `@high`** | **`@normal`** |
| States row (Default / Loading / Error / Success) | 1 visual state TC per named state — separate from lifecycle transition TC | `@normal` |
| Actions secondary (cancel, reset, export) | 1 behavior TC per secondary action | `@normal` |

> **Completeness check** — after generating all scenarios, scan Coverage Map line by line.
> Every spec item must map to at least one scenario. Every `Viewpoint items` entry must be mapped to a VP-ID or marked `[covered: VP-ID]`. Do NOT silently skip either source.
> Specifically verify: `Display surfaces` each have ≥1 scenario; `Inclusive bounds` each have a boundary-point TC.

#### Tier 2 strategy

Walk `spec.md` top-to-bottom and apply `sungen-viewpoint` Tier 2 checklist for each identified pattern. Depth by risk: high-risk section → all items; low-risk → presence checks only. Skip items already covered by Tier 1.
For HIGH-tagged sections: use the same depth as Step 3 (full BVA + Decision Table) when generating new Tier 2 VP-VAL items not already covered by Step 3. For LOW-tagged sections: EP valid + EP invalid only — skip BVA and Decision Table. Do NOT re-generate scenarios already created in Step 3.

**When `test-viewpoint.md` is present — mandatory line-by-line scan:**
Every item in `test-viewpoint.md` must appear in `Viewpoint items` of the Coverage Map (done in Step 2). In Tier 2, walk `Viewpoint items` line by line:
- Item already covered by Tier 1 → mark `[covered: VP-ID]`, skip.
- Item automatable → generate `@normal` scenario.
- Item requires DB setup / network manipulation → `@normal @manual`.
- Item marked as known bug → `@manual` with comment `# Known issue: <ID>`.

> **Viewpoint completeness check** — after Tier 2, every `Viewpoint items`
> entry must be mapped to a VP-ID or marked `[covered: VP-ID]`.
> If any item has neither → generate missing scenario before finishing.
> Do NOT silently skip viewpoint items because they seem redundant or
> hard to automate.

### Step 3 — Apply test design techniques

Delegate to `sungen-test-design-techniques` for technique selection and depth.
Risk level from Coverage Map drives depth: HIGH section → full BVA / Decision Table; LOW section → EP valid+invalid only.

**BVA constraint:** Use compact 4-point mode only (min-1, min, max, max+1). Do not apply Full 6-point mode.

### Step 4 — Supplement with viewpoint checklists

Use `sungen-viewpoint` for defect-prone patterns spec never documents. Skip items already covered by spec-driven scenarios.

> **XSS and SQL injection** are generated at Tier 1 guard (Step 2). Skip them when applying `sungen-viewpoint` Shared Checks — do not generate again.

#### 4a. Shared Checks — generate ONCE per screen

Apply the **Shared Checks table in `sungen-viewpoint`** (single source). Generate each at most once per screen, not per pattern. Since XSS and SQL injection are already covered at the Tier 1 guard (Step 2, above), at this step generate only the remaining ones that fit the screen: **Loading State** (any async fetch), **Empty State** (any list/table/card that can return 0 records), **URL Manipulation** (URL params affecting displayed data).

#### 4b. Pattern Tier 1 / Tier 2

**Gap check rule — Step 4b fills gaps from Step 3, not a new generation source:**
- **VP-VAL BVA/EP items**: skip if Step 3 already generated scenarios for that field. Generate only if Step 3 missed the field entirely.
- **VP-LOGIC, VP-UI, VP-SEC items**: generate normally — Step 3 does not cover these.

- **Tier 1**: all items from `#### Tier 1 — @high` → VP-LOGIC, VP-VAL, VP-SEC
- **Tier 2**: items from `#### Tier 2 — @normal + @low` → VP-UI

Risk weighting: high-risk section → all Tier 1 items. Low-risk (e.g. read-only table) → only clearly applicable items.

#### 4c. Cross-pattern interactions — where spec-invisible bugs live

For every pair of patterns present on the screen, walk the **⚡ Cross-pattern interactions** list in each `sungen-viewpoint` group file (single source). Never-miss pairs:
- **Create / Update / Delete × Data Table** — new record missing or count not updated; row shows stale data; last-record delete → blank page.
- **Search / Filter × Pagination** — results don't reset to page 1.
- **Form × Modal/Dialog** — modal stays open on success / data wiped on failure.
- **Import × Data Table** — table not refreshed / count wrong after import.
- **Sort × Data Table** — all cells in a row must reorder together (data must not shift to wrong columns).
- **Filter (≥2 active)** — generate 1 scenario with all filters active simultaneously to verify AND logic.

Cross-pattern: `@high` by default.

**Assert values, not existence.** Never bare "is visible". Capture exact error messages into test-data.yaml.

---

## Format over value

When the spec states a label's contract as a **shape** ("Format: yyyy/mm/dd ~ yyyy/mm/dd", "Format: X件", "Format: 残りX個"), generate `matches "<format>"` — never `with {{v}}`. An exact value pins data that changes daily (red while the app is right); `contains` proves nothing. Tokens: `yyyy mm dd hh ss X *`. Anchored — use `*` when a node fuses a prefix with the value. See `sungen-gherkin-syntax` → Format assertion.

```gherkin
Then User see [Application Period] label matches "yyyy/mm/dd ~ yyyy/mm/dd"
Then User see [Total Plans] label matches "*X件"
```

## Priority Tags

Every scenario **MUST** have exactly one priority tag. Assign by user impact per the **"Assign priority by user impact"** table in `sungen-gherkin-syntax`; override only when context differs.

Quick fallback: auth / CRUD / security / required-field / unique-constraint → `@high`; format validation / standard-range boundary / search / filter / sort → `@normal`; presence / label / placeholder / cosmetic → `@low`. (Inclusive-operator or critical boundary → `@high`.)

---

## SPA Wait-For Steps

```gherkin
Given User is on [Screen] page
And User wait for [Page Title] heading is visible
```

---

## Mobile — the two axes web has no concept of

Applies to a `@platform:mobile/android/ios` unit. Read `sungen-viewpoint` →
`group-f-mobile-native.md` for *what to check* per native pattern (it carries the theme↔catalog
map the audit scores against); this section is the two **cross-cutting** axes that multiply every
other scenario on a device, and that a web-shaped coverage map has no row for.

### Axis 1 — the app lifecycle state machine

A browser tab is either open or closed. An app has five states, and a screen can be wrong in any of
them. Generate lifecycle scenarios from the transitions, not from the states.

```
                         launch (cold)
                               │
                               ▼
        ┌──────────────► FOREGROUND ──────────────┐
        │                     │                   │
        │       background N seconds        restart (kill)
        │                     ▼                   ▼
        │               BACKGROUNDED         RELAUNCHED  ── reinstall ──► FIRST-RUN
        │                     │                                          (install state cleared)
        └──── resume ─────────┤
                              ▼
                        PROCESS DEATH   (the OS reclaims a backgrounded app)
```

| Transition | Step | Availability |
|---|---|---|
| foreground → backgrounded → resumed | `User background the app for 5 seconds` | both OSes |
| foreground → killed → relaunched | `User restart the app` | both OSes — **the** persistence oracle |
| → first-run | `User reinstall the app` | both OSes; needs the session to have installed via `APP_APK` / `IOS_APP` |
| a clean state *between* scenarios | `@reset:none\|soft\|data\|full` on the Feature | a tag, never a step — a step that tries reports **SG-W023** |
| backgrounded → process death → resumed | *no step* | `@manual:M10` (OS capability) |

**Every lifecycle scenario needs a DATA oracle.** "The screen is still there" passes on an empty
shell and on a silently re-logged-in session. Assert the value that must have survived:

```gherkin
@high @platform:mobile
Scenario: VP-LOGIC-001 The session survives a cold restart
  Given User is on [Account] screen
  When User restart the app
  Then User see [Account Name] text contains {{user_name}}
```

Minimum for any mobile unit: **one resume scenario** (the screen owes its own data back) and, when
the screen persists anything — a session, a setting, a dismissal, a show-once card — **one restart
scenario per persisted thing**. Rotation belongs here too: `User rotate to landscape` must not lose
entered values or fire the request twice.

### Axis 2 — permission × network decision table

These two multiply: a permission-gated feature behaves differently offline, and the honest expected
result is often "which message wins". Build the table, then **test only the rows with distinct
outcomes** — the usual decision-table discipline, not all 12 combinations.

| # | Permission | Network | Expected | How |
|---|---|---|---|---|
| 1 | granted | online | the happy path, feature content asserted | `User grant camera permission` — both OSes (iOS Simulator needs `applesimutils`) |
| 2 | granted | offline | the spec's cached/queued behaviour + a clear message; entered data never silently lost | `User turn wifi off` → `@platform:android` (XCUITest cannot toggle connectivity); iOS is `@manual:M10` |
| 3 | granted | off → on | recovers and refreshes with no manual reload | `@platform:android` |
| 4 | denied | online | an in-app explanation with a route to Settings — never a crash or a dead button | `@manual:M10` — no step denies a permission |
| 5 | denied | offline | one message wins per spec; do NOT generate this row unless the spec says which | `@manual:M10` |
| 6 | never-ask-again | online | no request loop; the Settings route is shown | `@manual:M10` |
| 7 | revoked while running | online | the app re-checks instead of trusting a stale grant | `@manual:M10` |

Row 1 is `@high` and automated. Rows 2–3 are `@high` and automated **on Android only** — tag them,
do not write them platform-neutral and hope. Rows 4–7 carry `@manual:M10`, which is a reason code
and a real oracle, not a shrug: write the steps a human follows and the value they must see.

### Two mobile traps worth naming while generating

- **A `page` assertion proves nothing on mobile.** A native app has no URL, so `Then User see [Login]
  page` compiles to an empty comment (**SG-W022**) and the scenario passes without looking. The
  `screen` spelling is different and is what you want: it treats the target as a plain element and
  emits a real visibility assertion — so name a marker only that screen has (`Then User see [Login
  Submit] button`, or `Then User see [Login] screen` against a screen-root selector), and pair it
  with the previous screen's marker going `is hidden`.
- **An `open [X] page` / `navigate to` step on mobile navigates nowhere** (**SG-W026**) — the app was
  already launched by the capabilities. Change screens with a real tap or gesture.

---

## Cleanup & Hooks

Add cleanup tags per the `sungen-gherkin-syntax` Cleanup table. Key rules:
- **Always `@cleanup:overlay`** if ANY section opens a dialog; **always `@cleanup:forms`** if the screen has inline search, filter, or editable forms.
- **`@parallel`** names the default isolation, so mixing auth groups (`@auth:X` + `@no-auth`) needs no tag at all — only `@serial` breaks it, because one shared context cannot hold two sessions. Still worth stating explicitly on validation-heavy features that want a clean form state per scenario.
- **`@afterEach`** hook only when `@cleanup:*` tags aren't enough (feature-specific reset logic).

---

## Output Format

**Files:** `qa/screens/<screen>/features/<screen>.feature` + `qa/screens/<screen>/test-data/<screen>.yaml`

Use step patterns and element types from `sungen-gherkin-syntax`. For a `@platform:mobile/android/ios`
screen or flow, first check that skill's **Platform Support** section — never author a step marked
`[web]` there (tables, browser storage, tab order, `@mock`, uploader, `Then User is on [T] page`) for
a mobile unit, and read the divergence table before relying on `see [T] page` or `hover` to prove
anything on mobile. Reach for `sungen-mobile-gestures` for the gesture vocabulary instead.
**Naming**: reuse the **project's `test-viewpoint.md` IDs** when it declares them (e.g. `VP0`, `MS-HP-001`); otherwise `VP-<CATEGORY>-<NNN>`. Scenario name must use the **same element type** as the steps.

**Test data** — grouped by section, loaded at runtime:

```yaml
# login.yaml (base)
valid_email: admin@dev.example.com
valid_password: DevPass123

# login.staging.yaml (override)
valid_email: admin@staging.example.com
valid_password: StagingPass456
```

**DataTable vs Row Scope:**

| Pattern | Use when |
|---|---|
| `table match data:` + DataTable | Verifying **multiple rows** exist with expected values |
| `row in [Table] table with {{v}}` + `column with {{v}}` | Single row detail or action on a row |

---

## Worked Example — spec → Coverage Map → output

A minimal end-to-end pass. Use this to calibrate output: every Coverage Map line below maps to exactly one scenario, and every `{{var}}` exists in the YAML.

**① Input — `spec.md` (excerpt):**
```
## Coupon Form
- Code: required, free text, max 20 chars, must be unique.
- Discount %: required, integer 1–100 inclusive.
- Submit creates a coupon; it appears at the top of the Coupon List.
- Only authenticated admins can create coupons.
```

**② Coverage Map (Step 2 output):**
```
User journeys:       [J1 – admin creates coupon → appears top of list]
Validation rules:    [V1 – code empty → "Code is required"],
                     [V2 – code > 20 chars → "Code must be 20 characters or less"],
                     [V3 – duplicate code → "Code already exists"],
                     [V4 – discount empty → "Discount is required"]
Business rules:      [B1 HIGH – new coupon sorts to top of list]
States:              (none — no lifecycle)
Security:            [S1 HIGH – free-text code → XSS + SQL injection], [S2 HIGH – admin-only]
Display surfaces:    [Coupon admin – /admin/coupons, auth:admin]  ← single surface
Cross-surface rules: (none)
Inclusive bounds:    [discount 1–100 inclusive → BVA: 0, 1, 100, 101]
Spec gaps:           (none)

Tier 1 output:  VP-LOGIC-001 (create → top of list), VP-VAL-001 (code empty),
                VP-VAL-002 (code > 20), VP-VAL-003 (duplicate code), VP-VAL-004 (discount empty),
                VP-VAL-005 (discount=0 reject), VP-VAL-006 (discount=1 accept),
                VP-VAL-007 (discount=100 accept), VP-VAL-008 (discount=101 reject),
                VP-SEC-001 (XSS), VP-SEC-002 (SQL injection), VP-SEC-003 (non-admin blocked)
```
> Note how `Inclusive bounds` line forces 4 BVA scenarios (0/1/100/101), not just "valid + invalid".

**③ Output — `coupons.feature` (excerpt, 4 of 12 scenarios):**
```gherkin
@parallel @auth:admin
@cleanup:overlay
@cleanup:forms
Feature: Coupons Screen

  Background:
    Given User is on [Coupons] page

  @steps:open_create_form
  Scenario: Open create form
    When User click [Create Coupon] button
    Then User see [Create Coupon] dialog

  # --- Section: Create Coupon (Tier 1) ---

  @high @extend:open_create_form
  Scenario: VP-LOGIC-001 Submit valid coupon adds it to top of list
    Given User is on [Create Coupon] dialog
    When User fill [Code] field with {{valid_coupon.code}}
    And User fill [Discount] field with {{valid_coupon.discount}}
    And User click [Save] button
    Then User see [Create Coupon] dialog is hidden
    And User see [Coupon List] row in [Coupon List] table with {{valid_coupon.code}}

  @high @extend:open_create_form
  Scenario: VP-VAL-005 Discount of 0 is rejected (boundary min-1)
    Given User is on [Create Coupon] dialog
    When User fill [Code] field with {{valid_coupon.code}}
    And User fill [Discount] field with {{discount_zero}}
    And User click [Save] button
    Then User see [Discount Error] message with {{error.discount_range}}

  @high @extend:open_create_form
  Scenario: VP-VAL-006 Discount of 1 is accepted (boundary min)
    Given User is on [Create Coupon] dialog
    When User fill [Code] field with {{valid_coupon.code}}
    And User fill [Discount] field with {{discount_min}}
    And User click [Save] button
    Then User see [Create Coupon] dialog is hidden

  @high @no-auth
  Scenario: VP-SEC-003 Unauthenticated user is redirected to login
    Then User see [Login] page
```

**④ Output — `coupons.yaml` (excerpt):**
```yaml
valid_coupon:
  code: "SAVE-{{$uuid}}"
  discount: "20"
discount_zero: "0"      # boundary min-1 (reject)
discount_min: "1"       # boundary min (accept)
discount_max: "100"     # boundary max (accept)
discount_over: "101"    # boundary max+1 (reject)
error:
  discount_range: "Discount must be between 1 and 100"
```

---

## Flow Test Generation

> **Terminology**: many QA teams call this level **System Test** (ISTQB: verify the fully
> integrated system against its specified requirements, designed around business
> processes/use cases). In sungen a *flow* is that system-test suite run through the real UI;
> its automation then serves as the regression suite on later runs. The names are
> interchangeable in conversation; the artifacts stay `qa/flows/<name>/`.

**Representative, not exhaustive (ISTQB system-test design rule).** A flow case takes ONE
representative input per branch — one valid value for the main flow, one error value that
triggers the exception branch and proves the journey recovers. The full equivalence/boundary
matrix belongs to the OWNING SCREEN (as `@cases` there), never re-run inside the flow: if the
screen already proved "the field rejects negatives", the flow only proves "the journey survives
one rejected input". `sungen audit` reports `FLOW-EXHAUSTIVE-INPUTS` when ≥3 same-shape flow
scenarios differ only in data. **Mocked dependencies**: a flow scenario using `@mock` is
delivered as *Pass (Mocked)* — verified handling logic, not the real dependency; the delivery's
External Dependency Tracking table lists it until re-verified real.

**Release selection & external legs**: when the contract declares `golden: true`, tag the
happy-path scenario(s) that prove the outcome with **`@golden`** — that is the set
`sungen inspect` (Final Inspection, Go/No-Go) runs before a release; `GOLDEN-MISSING` fires
until it exists, and a @golden scenario that passes only against mocks BLOCKS the release.
When the contract declares `external:` legs (another team/vendor's system), scenarios touching
those namespaces are the **System INTEGRATION** group — keep their oracles about the boundary
(contract/format/handoff), and expect `SIT-MOCK-ONLY` until they are re-verified real.

> **Auto-detect**: if path is `qa/flows/<name>/` → use this section. Skip Steps 1–4 above.

**Read `requirements/flow-contract.yaml` FIRST — it is the flow's boundary and the yardstick
`sungen audit` scores the flow against** (`flowCoverage` axis = the declared flows / phases
automated; `FLOW-OUTCOME-UNPROVEN`; `FLOW-SCOPE-CREEP`; `FLOW-GUARANTEE-MISSING`;
`FLOW-INVENTORY-MISSING`). **A filled contract is an INPUT — never rewrite it
to match your output** (same rule as `test-viewpoint.md`).

> **HARD RULE — the contract must be AUTHORED, never merely cited.** If
> `requirements/flow-contract.yaml` is absent, WRITE it (with the user, via the boundary checklist
> in `add-flow`: one business goal · clear trigger · ONE observable outcome valuable to the actor ·
> name = "Verb + outcome") **before** generating scenarios. Citing a contract you did not create
> leaves the flow scored as a generic screen — `flowCoverage` never applies, `coverage` falls back
> to page-type themes that do not fit a journey, and the audit reports `FLOW-CONTRACT-MISSING`.
> A contract also needs **both** guarantees, not just the happy one:
>
> - `successGuarantee` — everything true once the goal IS reached (what the Basic Flow proves).
> - `minimalGuarantee` — what must still hold when the journey **FAILS** (no duplicate record, no
>   mail on a rejected submit, no half-written state). Without it an Exception Flow has nothing to
>   assert against beyond "an error appeared", and `FLOW-GUARANTEE-MISSING` is reported.

| Aspect | Screen | Flow |
|---|---|---|
| Section focus | UI patterns per section | Journey phases toward ONE declared outcome |
| Selector format | `[Element]` | `[Screen:Element]` (namespaced) |
| Test data keys | `{{variable}}` | `{{phase.variable}}` |
| Feature tag | `@auto` / `@smoke` etc. | `@flow` (required) |
| Scenario ids | `VP-<CATEGORY>-NNN` | `FL-<PHASE>-NNN` — use-case phases `BF`/`AF0n`/`EF0n` (see below), or the flat `HP` (happy path) / `ER` (error recovery) / `EH` (guards) / `UI` for a short single-outcome flow — the contract still needs both guarantees and a `flows:` inventory either way |

**Scenarios to generate — every phase demanded by the contract, automated:**

| Phase | What to test | Scoring |
|---|---|---|
| `FL-HP` happy path | The complete journey ending in the contract's `outcome.assertion` — an AUTOMATED **data** assertion on `outcome.screen` (an order number, a summed total — not just "page visible"). This scenario is WHY the flow exists: it is the regression proof after a lib/framework upgrade. | uncovered → `flowCoverage` drops + `FLOW-OUTCOME-UNPROVEN` |
| `FL-ER` error recovery | Invalid input mid-flow → error shown → fix → the journey still completes. Validation must not trap the journey. | uncovered → `flowCoverage` drops |
| `FL-EH` guards | Direct URL access without the precondition · browser back · refresh · expired context — each ends in a safe observable state. | uncovered → `flowCoverage` drops |
| Cross-screen handoff | After every screen transition, assert the CARRIED state on the new screen (the added product's name in the cart, the email echoed on the sent screen). | blind tails cap `businessDepth` (`FLOW-HANDOFF-SHALLOW`) |
| Stateful regression (when `stateful:` declared) | Count/quantity proof · teardown (remove → empty) · multi-source add. | missing dims cap `businessDepth` (`FLOW-DEPTH`) |

### Use-case decomposition — how a flow becomes 15 scenarios instead of 3

`HP`/`ER`/`EH` are phase *buckets*; they do not tell you how many flows a use case HAS. The
use-case framework does, and it is the default decomposition for a multi-screen journey:

**1 Use Case = 1 Basic Flow + N Alternate Flows + N Exception Flows.**

| Phase | Meaning | Test |
|---|---|---|
| `BF` Basic Flow | **Exactly one.** The path where nothing goes wrong, end to end. | Reaches `outcome.assertion` and satisfies `successGuarantee`. |
| `AF` Alternate Flow | A **designed-for branch** — the user does something else legitimate. NOT an error. | Its own outcome: either re-enters the BF at a named step, or ends elsewhere on purpose. |
| `EF` Exception Flow | **Blocked.** The goal is not reached. | Asserts `minimalGuarantee` — what must STILL be true now that this failed. |

`AF` is the bucket most generators skip entirely, and it is where the interesting bugs live
(going back and finding the form re-hydrated, two tabs, an escape hatch out of the journey).
Use `FL-BF-NNN` / `FL-AF0n-NNN` / `FL-EF0n-NNN` ids — `sungen audit` buckets them (BF→business
core, AF→behaviour, EF→validation/security) and traces them to the viewpoint's `FL-BF`/`FL-AF`/
`FL-EF` priority rows. Declare `phases: [BF, AF, EF]` in the contract; the FIRST phase is the
Basic Flow and is the one that must reach `outcome`.

**Declare the flow INVENTORY, not just the phases.** `phases: [BF, AF, EF]` says which
vocabulary the suite uses; it cannot say how many flows the use case has — a phase counts as
covered the moment ONE scenario carries it, so a fifteen-flow use case with three scenarios
reported full phase coverage while a reviewer counting flows read it as a third done. List every
flow in the contract, and give each one a status:

```yaml
flows:
  - id: BF
    outcome: "The successGuarantee — verified, recorded, signed in"
    status: covered
  - id: AF02
    branchFrom: "BF step 4, before submitting"
    outcome: "Re-enters BF at step 4 with the buffer restored"
    status: covered
  - id: EF08
    branchFrom: "BF step 1, the control tapped twice"
    outcome: "One request, one record"
    status: pending-clarification      # the spec is silent — ASK, never assume a guard
    reason: "The guard lives in SCREEN_A_001's spec, which this project does not hold."
```

Four statuses, and **silence is not one of them**: `covered` · `deferred` · `pending-clarification`
(the behaviour is not agreed yet, so no scenario can be right) · `out-of-scope` (another suite owns
it). Anything but `covered` needs a `reason:` — a deferral nobody can audit is the same as a
missing flow. `sungen audit` then measures `flowCoverage` per DECLARED FLOW and reports
`FLOW-UNCOVERED` (declared, nobody wrote it), `FLOW-UNDECLARED` (a scenario claiming a flow id the
inventory never declares), `FLOW-STATUS-UNREASONED` and `FLOW-INVENTORY-MISSING`.

**Enumerate every branch the spec declares — do not rely on reading carefully.** Before writing
scenarios, list each mutually-exclusive branch the spec makes explicit, one line per arm:

- every `### Exception Flow X` / `### Alternative Flow X` heading;
- every row of a decision table (`DEC-*`, `BR-*`, any table of conditions → outcomes);
- every arm of an if/elif/else cascade in pseudocode;
- every state transition a state machine declares.

Each arm needs its OWN scenario, or a written exclusion. **Covering a sibling arm proves nothing
about this one** — and two scenarios that assert the same thing under different names are one test
counted twice, which is worse than a gap because the coverage sheet reports it as two.
`sungen audit` reports `SPEC-BRANCH-UNCOVERED` and `BRANCH-OVERCLAIMED`. This rule existed as
prose before and was missed three times in three flows; it is enumerated now for that reason.

**Two viewpoints per UI region, not one.** A region that has both conditional rendering (`DEC-*`)
and an acceptance scenario describing a click needs a case for EACH: "render — DEC-xxx" and
"interaction — click → destination". Finishing the render half is the easy half, and stopping there
is the common failure. Every navigation target the spec names needs a click-through case
(`NAV-TARGET-ABSENT`), even when this flow does not consume the destination — a context param
handed to another screen is that screen's input, and the handoff is where it goes wrong.

**A hidden case needs its shown case.** "permission denied → content hidden" passes just as
happily when the content is hidden for everyone. Pair it with "permission granted → content
shown", the way a boundary needs `min-1 / min / max / max+1` (`PERMISSION-PAIR-MISSING`).

The same holds for ANY condition, not just permission: an element you assert `is hidden` in one
state must be asserted PRESENT in some other state, or nothing distinguishes a working rule from a
feature that is broken for everyone — and a mistyped selector matches nothing, so `is hidden`
passes forever on its own (`VISIBILITY-PAIR-MISSING`). Conditional buttons, empty-vs-populated
lists, the active tab, a radio state: "show A or B, never both" is a very common implementation
defect, and only the pair catches it.

**A requirement that states both directions is TWO requirements.** "`X` MUST be shown when no
score is set; MUST NOT be shown once a score exists" is one line and two mutually-exclusive states,
so one scenario cannot prove it — tagging `@spec:FR-013` on the positive case closes the ID, not
the rule. Write one case per direction; `sungen audit` counts them as two coverage units and
reports the unproved half as `SPEC-CLAUSE-UNCOVERED`. The gate only presses a requirement that
NAMES its element and speaks about visibility or enabledness — but the habit is worth keeping for
every opposing rule, gated or not.

**State that ARRIVES with the data is a different case from state the test creates.** A
discriminator field you can only observe at load (an item already favourited before the page
opens) is a different oracle timing from the same field set by an action inside the test. Both are
needed; the first is usually `@manual:M1` or a data-factory fixture.

**One id per atomic CLAIM in the viewpoint.** The flow id groups; the viewpoint id identifies.
Writing `FL-BF-001` on four different claims makes the ledger count four items while traceability
sees one, so the coverage arithmetic and "which test proves this?" are both wrong and neither
number looks suspicious (`VIEWPOINT-ID-REUSED`). Number them within the flow —
`FL-BF-001`, `FL-BF-002`, … — and never cite an id no scenario carries (`TRACE-REF-DANGLING`
checks the ids mentioned in `test-data/*.yaml` too).

**Rank the priorities; do not stamp them.** A Priority-Viewpoints table where every row says
`High` is a formality, not a priority: nothing can be selected for a smoke run and the release
conversation has no input (`VIEWPOINT-PRIORITY-UNIFORM`). Rank by business impact and risk — the
basic flow, data integrity and the auth/context guards are High; a recoverable back-navigation or
a network blip is usually Normal; an exit path a screen suite already owns is Low.

**A continuity claim is an EXACTNESS claim.** A title saying values are *unchanged / preserved /
intact* must be proven with `see [X] text with {{v}}`, never `contains {{v}}`: a substring match
passes when the UI concatenates, prefixes or truncates — exactly the defect "unchanged" exists to
catch, so `contains` proves the opposite of the claim (`CLAIM-WEAK`). If the screen genuinely
renders extra text around the value, change the title instead of weakening the oracle.

**`status: covered` is about the DESIGN, not the evidence.** A flow whose only scenarios are
`@manual` is accounted for and still has nothing that runs: a regression suite would not notice if
it broke. The audit reports `FLOW-MANUAL-ONLY` and credits those flows at half in `flowCoverage`,
so do not treat a manual procedure as equivalent to automation. Check the capability plan for a
driver that would automate it; if none can, say so plainly in the delivery rather than letting
`covered` imply a passing test.

**One flow, one id — a viewpoint is not a flow.** Several scenarios may prove one flow: give them
the same flow id and different sequence numbers (`VP-VAL-EF01-001`, `VP-VAL-EF01-002`), never a
fresh flow id per assertion. Two shapes the audit reports as `FLOW-PHASE-MISFILED`: an `EF` that
reaches the outcome with nothing failing (that is a success-path postcondition, so it belongs to
the BF), and an `AF` with no branch point (it walks the basic path asserting extra content — also
the BF's). Both inflate the flow count while adding no branch coverage.

**Declare once, then declare only the differences.** Actor · Trigger · Goal · Precondition ·
`successGuarantee` · `minimalGuarantee` are use-case-level: they live in `flow-contract.yaml`
and are never repeated per flow. Each flow in `test-viewpoint.md` then states only three things:
**where it branches from · its own steps · its own outcome.** Repeating the precondition in
fifteen rows is noise; the branch point is the information.

**Enumerate flows with the step × risk matrix.** Walk every BF step and ask each column. A hit
with a basis in the spec becomes a flow; a hit with no basis becomes an **open question** — never
an invented behaviour:

| Risk family | The question at this step |
|---|---|
| Double submit | Is the control tapped twice guarded? One request, one record? (Spec silent → open question, not an assumed guard.) |
| Client-side buffer | Is state held client-side between this step and the next? Then test **both** directions — it survives a legitimate back, and it is **LOST** on reload. A suite proving only the surviving side has tested half the mechanism (`CONTINUITY-ONE-SIDED`). |
| Concurrency | Two tabs / two devices at this step — whose state wins? |
| Server error | This step's submit fails server-side: what is kept, what is retryable, is anything half-written? |
| Abandonment / TTL | The user stops here. Does the partial state lapse, expire, or linger forever? |
| Direct access | This step's URL opened cold, without the preceding state → refused, and the screen never renders. |
| Auth transition | Where exactly does the session begin? Before that point the actor is unauthenticated — prove it. |
| Escape hatch | Is there a documented way OUT of the journey here (back to top, cancel)? That is an `AF`, and its outcome is "goal deliberately not reached". |

**Do NOT re-derive field validation.** Per-field equivalence and boundary values belong to the
OWNING SCREEN's suite. A flow takes representative inputs (see the system-test rule above). State
that exclusion in the viewpoint's Design Decisions — and if the screen suite does not exist, that
is a gap to REPORT, not to quietly absorb.

**Boundary discipline while generating:** every scenario must serve the contract's goal. A scenario
that never touches `outcome.screen` and is not a guard (`EH`) or error-recovery (`ER`) belongs in a
DIFFERENT flow — propose the split instead of writing it here (`FLOW-SCOPE-CREEP` will flag it).
Auth persistence across transitions is part of `EH` unless the project declares it its own phase.
**The exception is declared, not widened:** when the spec KEEPS a variant deliberately light — an
entry variant that only proves the alternative entry reaches the action bar, without repeating the
full assertion the basic flow already carries — declare that flow in `flows:` with its own
`outcome:` and the audit judges it against that outcome. Never widen the scenario to satisfy the
gate; a reviewer reads the result as over-testing, and the finding was the thing that was wrong.
When a gate and the spec's stated intent collide and no declaration route exists, stop and put the
conflict to the user — do not edit toward the score.

**Manual in flows**: always `@manual:Mx` with the reason code — bare `@manual` is flagged
(`MANUAL-CODE-MISSING`) because the capability planner cannot route it. Typical flow deferrals:
inbox/mail oracle → `M5`, network-request count → `M3`, context expiry control → `M7`. A
cross-screen scenario inside the flow's own goal is NOT manual — automate it here.

```gherkin
@flow @auth:user
Feature: Award Submission Flow

  Background:
    Given User is on [Login] page

  @high
  Scenario: FL-HP-001 A signed-in user's nomination is submitted and confirmed
    When User fill [Login:Email] field with {{login.email}}
    And User fill [Login:Password] field with {{login.password}}
    And User click [Login:Submit] button
    Then User see [Dashboard] page
    When User click [Dashboard:Awards] link
    And User fill [Awards:Nominee] field with {{submission.nominee}}
    And User click [Awards:Submit] button
    Then User see [Awards:Success Message] text with {{success_message}}

  @high
  Scenario: FL-EH-001 Direct access to the award form without login redirects to login
    Given User is on [Awards] page
    Then User see [Login] page
```

```yaml
# flows/<name>/test-data/<name>.yaml
login:
  email: "admin@example.com"
  password: "secret123"
submission:
  nominee: "John Doe"
success_message: "Award submitted successfully"
```

**Do NOT generate**: `selectors.yaml` (created during `run-test`), Playwright code (sungen compiles).
