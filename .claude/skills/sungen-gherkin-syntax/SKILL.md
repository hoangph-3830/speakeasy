---
name: sungen-gherkin-syntax
description: 'Sungen Gherkin patterns, selector types, and YAML key rules. Auto-loaded when writing .feature, selectors.yaml, or test-data.yaml.'
user-invocable: false
---

## Standard Syntax

```
[Keyword] User <Action> [Target Name] <Target Type> <in [Parent Name] <Parent Type>> <with {{Value}}> <is State>
```

- **Actor**: Always `User`, always active voice.
- **Value**: `with {{snake_case}}` — never hardcode static data.
- **State**: `is <keyword>` — never use `{{}}` for states.
- **Parent scope**: `in [Parent] parentType` — optional, only when page has 2+ similar blocks needing disambiguation. Container types: `table` `list` `list-item` `section` `dialog` `modal` `drawer` `overlay` `form` `card` `item` `row` `group` `panel` `region` `container` `tab` `menu`. Anything else is not a container, and the clause is DROPPED with `SG-W028` rather than silently widening the step to the whole page.
- **Parent scope BY CONTENT** (web only): `… with {{v}} in [Parent] parentType with {{v2}}` — the SECOND `with` picks the one container whose content is `{{v2}}`, for a list of look-alike cards/rows. One `with` after the clause still belongs to the TARGET (`see [first] row in [apply list] table with {{job_name}}`), so the by-content form always carries two. On the mobile adapter it reports `SG-W028` — an Appium parent chain has no content filter.

## Keyword → Action Rules

```
GIVEN  →  is on
WHEN   →  click · fill · select · press · clear · check · uncheck · hover
         [⚠️ wait for — only for Spinner/Modal, minimize usage]
THEN   →  see
AND    →  inherits from preceding keyword
```

## Step Patterns (70 patterns)

> **Platform legend:** unmarked = `[both]` (compiles on Playwright AND Appium). `[web]` = compiles
> only on Playwright — the Appium template throws, naming the reason. `[mobile]` = mobile-only
> vocabulary with no web counterpart. Every marking is checked against a shipped `.hbs` — see
> **Platform Support** at the end of this section for the full web-only/mobile-only/divergence list.

### Setup / Form / Interaction

```
User is on [T] page | page with {{v}} | dialog      # {{v}} = query/fragment ("?id=200", "#tab") APPENDED to the page value — a bare "200" onto "/x?id=199" gives "?id=199200"
User fill [T] field | textarea | search | slider | date-picker with {{v}}   # date-picker: web only unless qa/app.yaml declares widgets.datepicker — appium refuses it; on web a <button> trigger (calendar popover) needs a fill recipe (variant: / widgets.datepicker) or it is refused (SG-W017)
User fill [T] uploader with {{f}}                                    [web]
User clear [T] field
User check [T] checkbox | toggle | radio
User uncheck [T] checkbox | toggle
User select [T] dropdown with {{v}}
User click [T] button | tab | column | breadcrumb
User click [T] row with {{v}}
User try to click [T] button | link          # DISABLED element only — see rule below (v3.3) [web]
User click [T] overlay at position "top-left"           # corner preset: top-left | top-right | bottom-left | bottom-right [web]
User click [T] overlay at offset x=24px, y=10%          # px or % per axis, from the element's top-left [web]
User double click [T] element
User hover [T] icon | row                    # no-op on mobile (see Platform Support)
User drag [T] to [T2]
User expand | collapse [T] row
```

**click + with {{Value}} rule**: NO value for static (`button`, `link`, `icon`, `tab`). WITH value only for dynamic lists (`row`, `item`, `card`, `option`).

**try to click rule** (v3.3, web only): use ONLY when the element is expected to be DISABLED and the
scenario asserts nothing happens (cooldown re-send, submit before the form is valid). `click` waits for
`enabled` → it can only time out there; `try to click` compiles to `.click({ force: true })`. Always
pair it with the proof: `Then User see [T] TYPE is disabled` + an assertion that the effect did NOT occur.
NEVER use it for a click that is supposed to work — it deletes the actionability wait. (`force: true` is
reachable ONLY through `try to click` — there is no separate `force` keyword.)

**click at position rule** (web only): a plain `click` lands on the element's CENTRE. To click a
modal backdrop where the dialog is not, or any control whose centre is covered, append
`at position "<corner>"` (5% in from that corner) or `at offset x=<n>px|%, y=<n>px|%`. The position
is checked against the element's real box at run time (an offset past it fails naming the size). The
suffix is refused at compile time (`SG-E026`, the step throws instead of clicking the centre) when it
is misspelt or unquoted, when a value is negative / above 100% / missing an axis, when anything
follows it, and when it is combined with `try to click`, a `with {{v}}` text filter, `… and accept [OK]
alert`, or `double click` — those forms take no position.

### Alert / Keyboard / Wait / Scroll

```
User click [T] button and accept [OK] alert       # PREFERRED (v3.3): natural order,
User click [T] button and dismiss [Cancel] alert  # compiler registers the listener first
User click [OK | Cancel] alert                    # two-step form: must come BEFORE the trigger
User fill [T] alert with {{v}}                    # no-op on mobile — native prompt fill is app-specific
User see [message text] alert
User press Escape key | [Enter] key | Tab key 5 times | Enter on [T] field
User wait for N seconds | [T] page                # [T] page: web waits for the URL, then HOLDS it (see Pattern 8); mobile pauses (settle) — see Platform Support
User wait for [T] TYPE is visible | hidden | enabled | disabled     # ANY reference (v3.3)
User wait for [T] TYPE with {{v}}                                    # until it shows the value
User wait for [T] table to refresh                                   # filter/search/pagination round-trip (v3.3)
User scroll to [T] section
```

> **Browser alerts (native `window.confirm/alert/prompt` only):** prefer the compound form —
> it reads in the user's real order and declares the trigger↔dialog causality in one step.
> The two-step form stays valid but the alert step must appear BEFORE the triggering action
> (a late `page.once('dialog')` lets the dialog auto-dismiss silently — compile warning
> `SG-W007`). Custom confirm MODALS (ARIA dialogs — most modern apps) are ordinary buttons:
> write normal click steps, no alert step at all.
> State-based waits (`is hidden`, `with {{v}}`, `table to refresh`) are legitimate; time-based
> `wait for N seconds` stays a last resort. `table to refresh` watches the app's loading
> indicator (`qa/app.yaml` `feedback.loading.indicator`, default `[aria-busy="true"]`).

### Scope control

```
User is on [T] dialog                             # ENTER dialog scope, rooted at [T]'s selectors.yaml entry
User use dialog                                   # ENTER dialog scope, unnamed — plain page.getByRole('dialog')
User close dialog | dismiss dialog | exit dialog  # EXIT dialog scope (three spellings, identical)
User switch to [T] frame                          # ENTER frame scope — web: iframe; mobile: hybrid-app WebView context
User switch to [main] frame                       # EXIT frame scope (no-op if the screen has no WebView)
```

> **Dialog scope is STICKY — it ends only where you end it.** Once a scenario enters dialog scope,
> EVERY later locator in that scenario is prefixed with the dialog root, **including the steps that
> run after the dialog has closed**. Nothing implicit drops it: not a click on the dialog's own
> Close/OK button, and **not** `wait for [T] dialog is hidden` — that step asserts the dialog went
> away and then keeps resolving the following steps INSIDE it, which can never succeed. Only
> `close`/`dismiss`/`exit dialog`, or the start of the next scenario, drops the scope. The failure
> has a recognisable shape: a step after the dialog closed times out reading "element not found"
> while the element is plainly on screen — you are not missing a selector, you are still scoped
> inside a dead dialog.
>
> ```gherkin
> Given User is on [Confirm Delete] dialog   # scope enters here — implicitly, but it still needs an EXPLICIT exit
> When User click [OK] button                # scoped — correct, the button IS in the dialog
> And User exit dialog                       # ← REQUIRED: the click closed the dialog, the scope did not
> Then User see [Deleted] toast              # page-rooted again
> ```
>
> Dialog scope is `[web]`-effective only — no Appium template reads `inDialog`, so on mobile the
> following steps resolve against the whole screen (`SG-W021`). Frame scope works on both.

### Positional table rows (v3.3) `[web]`

```
User remember [Col] column in [T] table row {{n}} as {{var}}   # read a cell by POSITION
User click [Act] button in [T] table row {{n}}                 # act on the SAME row
User click [Act] button in [T] table row 2                     # literal row number
```

Row numbers are 1-based data rows (header not counted; override via `qa/app.yaml`
`conventions.table`). Use positional rows when you must READ then ACT on the same row —
two content-filtered queries can hit different rows if the table re-renders in between.

### Assertions (8 patterns → determines Playwright assertion)

```
# 1. Visibility: User see [T] type (NEVER add "is visible") | is hidden
# 2. Text (toHaveText): User see [T] message | header | label with {{v}}
#    `label` forks on the entry: declared by IDENTITY (xpath/accessibility-id/testid/id/css/locator)
#    → toContainText on THAT element; `exact: true` → toHaveText, and an EMPTY {{v}} asserts the
#    element shows no text (contains '' would pass on anything); no entry, or `type: text` → matches
#    the caption+value text pair anywhere on screen
# 3. Partial (toContainText): User see [T] text contains {{v}}
# 3b. Format (shape, not value): User see [T] label matches "yyyy/mm/dd ~ yyyy/mm/dd"
# 4. Input (toHaveValue): User see [T] field | search | slider with {{v}}
#    dropdown | select | combobox | date-picker → the entry's `assertValue` recipe if it has one
#    (variant: / widgets.select / widgets.datepicker), else the tag is probed at runtime
#    (toHaveValue on a native input/select, toHaveText on a custom trigger such as a <button>).
#    Any element whose entry has a `variant:` reads back through that recipe's `assertValue`
# 5. State: User see [T] button is disabled | checkbox is checked | dialog with {{v}} is hidden
#    open/closed: User see [T] button | section is expanded | is collapsed   [web — reads `expanded:`, else aria-expanded;
#    an entry with `expanded: { state: none }` has no state to read → refused, SG-W017]
# 6. Attribute (toHaveAttribute): User see [T] image | link with {{v}}
#    WHICH attribute comes from the entry's `attribute:` — any name, not just href/src
# 7. Count: User see [T] row with {{count}}
# 8. Page: User see [T] page | page with {{url}}   ← `with` also asserts the query string
#    NEVER a state: `page is hidden` / `page is empty` … are NOT step forms (see Pattern 8 below)
```

> **`page` takes NO state clause.** The only two forms are `see [T] page` and `see [T] page with
> {{url}}`. `see [X] page is hidden` is not a supported step: `page` compiles to a URL assertion, so
> the clause is dropped and the step asserts the page IS displayed — the opposite of what it reads
> as. The compiler now rejects it (`SG-E021`) instead of generating that. To say "we left page X",
> assert the page you expect (`Then User see [Other] page`); to say "this went away", assert a marker
> element (`Then User see [Some Element] is hidden`).

**Pattern 8 is a URL oracle, and a URL is not a rendered page.** `see [T] page` proves the router
pushed a path — it does NOT prove the page rendered for this user. A client-side router pushes the
destination BEFORE the route guard or the permission API has answered, so a user with no permission
is "on" `/items/detail` for a few hundred milliseconds before being bounced to the 403 page. Since
3.2.28 the step compiles to `expectPage(page, {…})` — arrive, let the page settle, HOLD the URL, and
re-assert — so a late redirect fails and names where the user ended up. But the page having
rendered is still unproven: **for permission / auth-guard / "role X can open screen Y" scenarios,
give the page selector an `anchor:`** (a heading or landmark only the rendered page has) — every
`see [T] page` on that screen then asserts the anchor too — or follow the page step with a content
assertion. A scenario whose every `Then` is a page assertion is reported as `ORACLE-URL-ONLY`.

```yaml
detail:
  type: 'page'
  value: '/items/detail'
  anchor: { type: 'role', value: 'heading', name: 'Item Detail' }   # the rendered-content proof
```

**`wait for [T] page` shares that hold, and only that.** Since 3.2.30 the wait compiles to
`waitForPage(page, {…})` on the same helper: it arrives, settles, holds the URL, and fails naming
where the user went — so the steps AFTER the wait no longer run against a page mid-redirect, and
`/vi/search` no longer resolves on `/vi/search-results`. Two differences from the `Then` side:

- **A wait does not constrain the query.** `see [Results] page` with no `with {{v}}` demands a bare
  URL; `wait for [Results] page` after submitting a search tolerates the `?q=…` it lands on. Prove
  the query in the `Then` that follows.
- **A wait is not an oracle.** It never counts toward a scenario's assertions — `ORACLE-URL-ONLY`
  and the assertionless-test check both ignore it. Always follow it with a real `Then`.

An absolute page `value` (`https://app.example.com/reception/:id`) compiles its host into a
separate anchored `hostname:` — the same path on another host no longer satisfies the wait.

**Pattern 8 — the page assertion judges pathname AND query together.** Both `see [T] page` and
`is on [T] page` (Then-side) compile to ONE web-first check — `expectPage(page, {…})`, built on
`toHaveURL(urlMatches({…}))` and retrying like any other assertion — comparing the **exact** pathname
(anchored, trailing slash normalised) plus the query:

| step | asserts |
|---|---|
| `Then User see [Search] page` | pathname `/vi/search` **and no query at all** — `/vi/search?x=1` FAILS |
| `… page with {{v}}`, `v` = `?q=abc` | pathname + **every declared param must match**; extra params are tolerated (a UTM/tracking param cannot turn the test red); a repeated param (`?tag=a&tag=b`) matches per value, so param ORDER never matters |
| `… page with {{v}}`, `v` = `?q=abc#tab` | the fragment is asserted too |
| `… page with {{v}}`, `v` = `/vi/search?q=abc` | only the query part is used — the page selector already owns the path |
| `… page with {{v}}`, `v` = `/vi/search` (no query) | **path only**, and the compiler warns (`SG-W014`): the step asserts less than it reads as |
| `… page with {{v}}`, `v` empty or key missing at run time | **throws, naming the key** — an unusable value is never a silent pass |

**Navigating** with data (`Given User is on [T] page with {{v}}`) appends `{{v}}` to the page selector's value **verbatim** — no merging, by design (the primary use is injecting malformed query input, which the compiler must not sanitize). So the page value carries the PATH only and `{{v}}` carries the whole query: `value: '/job/detail'` + `job_query: "?id=200"`. A page value that already holds `?id=199` plus `{{v}}` = `"200"` navigates to `?id=199200`. A rooted `{{v}}` (`/other/path`) replaces the page's path; an absolute URL replaces everything.

So pass `with {{v}}` whenever the URL carries state worth proving, and leave it off when the page
must be reached clean. A dynamic segment in the selector value (`/users/:id/profile`) wildcards to
`[^/]+` on its own, still anchored — `/users/42/profile/edit` does not match. Params compare
decoded on both sides, so a deliberately malformed query still asserts what was written.

Two asymmetries worth knowing rather than discovering:

1. **`Given`/`When` navigate, `Then` asserts** — so they read `{{v}}` differently. `Then` uses only
   its query/fragment; `Given User is on [T] page with {{v}}` also honours a path the value carries
   (`/vi/other?q=1` navigates to `/vi/other?q=1`, and a value repeating the page's own path no
   longer doubles it). Anything that is not a path, query, or fragment is appended **verbatim** —
   nothing is encoded or normalised, so a test can still drive a deliberately malformed query.
2. **A repeated param matches as a subset per key**, which is the same rule as "extra params are
   tolerated": every value you declare must be present, and the URL may carry more.

> **Mobile:** `Then User is on [T] page` **throws** (no URL/address bar on native). `Then User see
> [T] page` \| `page with {{v}}` **silently no-ops** — the compiled step asserts nothing and the
> scenario passes regardless. Never use Pattern 8 to prove "landed on screen X" on mobile — assert
> a screen-marker element instead (`Then User see [X] header`).

The predicate itself lives in `specs/url-assert.ts` (auto-generated, `DO NOT EDIT`). If `[T]` has no
`type: page` selector entry — or its key collides with a non-page entry, so `value` is something like
`button` rather than a URL — the step falls back to another path and cannot match the real URL. The
compiler warns (`SG-W013`); declare the page (or rename the colliding key) instead of chasing the red.

### Collection / all-card (P5)

```
# Every element's TEXT matches a value (filter correctness, all items belong):
User see all [Result Product Name] text contains {{category_term}}
# Every CONTAINER holds a CHILD element (structural per-card proof — prove "each card has X"):
User see all [Product Card] contain [Product Name]
User see all [Product Card] contain [Add To Cart] button
```

Use the all-card form whenever a title claims *every / each* card/row exposes something — a single `User see [Add To Cart] button` does NOT prove "each card" and the harness Claim-Proof gate will flag it.

### Table `[web]`

```
User see [Col] column in [Table] table
User see [Ref] row in [Table] table with {{v}}
User see [Ref] row in [Table] table with {{v}} is hidden
User see [Table] table with {{count}} | is empty
User see [Col] column with {{v}}
User click [Act] button in [Table] table with {{v}}
User see [Table] table match data:
    | Header1    | Header2    |
    | {{value1}} | {{value2}} |
```

Row scope: `see [Ref] row in [Table] table with {{v}}` enters scope. Subsequent `see [Col] column with {{v}}` checks cell in that row. Use `table match data:` for multi-row verification.

**[Col] does not have to be the DOM header text.** `see [Col] column in [T] table` looks for a header whose accessible name is the reference, which cannot match a page in another language. Keep the reference readable — it is what the delivered test case shows — and declare the real header on the table entry (or on a `<col>--columnheader` entry's `name:`, which also takes `{{variables}}` for per-locale text):
```yaml
article list:
  type: role
  value: table
  columns:
    category: { index: 2, header: '記事分類' }
```

**[Ref] of a row step is a display label UNLESS declared in selectors.yaml.** Without an entry the step checks the value exists in ANY row — a name like `[First Contact Row]` does NOT position the row, and an ordering rule would pass even when the row moved (SG-W010 warns on positional-looking undeclared labels). To assert a POSITION, declare the row (a `type: locator` value RELATIVE to the table):
```yaml
first contact row:
  type: locator
  value: 'tbody tr:first-child'
```
→ compiles to `expect(table.locator('tbody tr:first-child')).toContainText(v)` — the exact row must hold the value — and still enters row scope for `[Col] column` checks.

**A list built from `<div>`s is a table only if you tell sungen what a row is.** Every table step finds rows by `getByRole('row')`; a `<div>` list has none, so `is hidden` and `is empty` would pass on 0 rows (asserting nothing) and counts/clicks would time out. Declare the row SHAPE on the **table** entry — `rows:` is relative to the table and drives all seven table steps:
```yaml
job table:
  type: locator
  value: 'div.job-list'
  rows: 'div.job-row'        # what EVERY row matches — NOT a row label's entry
```
Two different keys, two meanings: `rows:` on the table = every row (the shape); a `type: locator` entry on a **row label** = ONE positioned row (`tbody tr:first-child`). A shape put on a row label fails at run time with a `toHaveCount(1)` message that points back here. A table located by CSS — or by a role that holds no rows, such as `role: list` or `role: region` — with no `rows:` gets `SG-W016`. On a shaped table `table row {{n}}` counts from the first data row (no header offset).

**A shaped row has no CELLS either, so the two cell-reading steps need one more declaration.** `see [Col] column with {{v}}` reaches the cell through `getByRole('cell')`, and `table match data:` reads its header row from `thead tr` and addresses cells as `td, th` — a `<div>` row has none of those. Name the cell on the column:
```yaml
job table:
  type: locator
  value: 'div.job-list'
  rows: 'div.job-row'
  columns:
    title:
      index: 0
      header: 'Title'
      elements:
        cell: { type: locator, value: '.job-title' }   # relative to ONE row
```
With it, `see [Title] column with {{v}}` compiles to `tableRow.locator('.job-title')` and `table match data:` stops reading `thead` altogether — the header-to-column mapping is then known at compile time, so there is nothing left to race with. Without it on a shaped table, `see [Col] column` warns (`SG-W034`) and `table match data:` is refused outright (`SG-E035`) rather than waiting out every timeout against a header row that will never exist.

### Browser storage `[web]`

```
Then User see [KEY] in local storage exists
Then User see [KEY] in local storage does not exist
Then User see [KEY] in local storage is not empty
Then User see [KEY] in local storage has length {{n}}
Then User see key matching "PATTERN" in local storage      # regex over key names
# same five forms with `session storage`
```

`[KEY]` is a **storage key, not a selector** — never add it to selectors.yaml. It may embed `{{vars}}`: `[{{exclusive_code}}_ACCESS_TOKEN]`. **This is the ONLY bracket that accepts a `{{var}}`.** An element `[Reference]` is a selectors.yaml key matched **literally** — it is never interpolated, so `[{{var}}]` for an element is invalid and the compiler rejects it. To target text that varies (locale, `@cases` column), keep the reference static and put the variable in the selector entry's `name:`/`value:` field, or pick by text with `with {{var}}` (`User click [Answer List] item with {{confirm_value}}`). The check runs inside the browser and returns only a boolean, so a failure message never contains the stored value (safe for tokens). There is deliberately no `equals {{expected}}` form. ⚠️ `expect [KEY] in local storage …` is NOT valid (`expect` reads `{{response}}` refs only) — the compiler warns SG-W011.

### Format assertion `see [T] type matches "<format>"`

A spec often states a label's contract as a **shape**, not a value — "Format: yyyy/mm/dd ~ yyyy/mm/dd",
"Format: 残りX個". Write it verbatim; no regex belongs in a `.feature`:

```gherkin
Then User see [Application Period] label matches "yyyy/mm/dd ~ yyyy/mm/dd"
Then User see [Remaining Stock] label matches "残りX個"
Then User see [Total Plans] label matches "*X件"
```

| Token | Matches | | Token | Matches |
|---|---|---|---|---|
| `yyyy` | a 4-digit year | | `X` | an integer, `1,280` allowed |
| `mm` `dd` `hh` `ss` | a zero-padded 2-digit part | | `*` | any run of characters |

Everything else is a **literal**. The match is **anchored** — `"X件"` is not satisfied by `12件です`;
use `*` where the label legitimately carries more (a mobile node that fuses a prefix and its count,
`開催中の企画\n2件` → `"*X件"`). A token glued to an ASCII letter stays literal, so `"Comment: X件"`
keeps its `mm`.

Use this instead of `with {{v}}` when the VALUE varies but the SHAPE is the requirement: an exact
value pins data that changes daily (red while the app is right), and `contains` passes on anything.

### Tab order `[web]`

```
Then User see tab order:
    | Ref |
    | [Station Dropdown] |
    | [Departure Date] |
    | [Search Button] button |
```

Focuses row 1 (the pinned origin — there is no separate `focus [X]` step) and asserts it actually HOLDS focus (a non-focusable ref fails loudly), then presses Tab per following row and asserts it receives focus. Cell refs ARE selector references (resolved via selectors.yaml; optional element type after the ref). Requires the `| Ref |` header and ≥2 element rows — a missing header or single row is a compile error, never an empty pass. A mismatch reports the expected ref + the actual focused element (shadow-DOM-aware tag/role/name). Limitations: web only (no Appium); declare tab order OUTSIDE `use dialog`/frame scope (cell locators render page-rooted — put `scope: dialog` on the selector ENTRIES if the elements live in a dialog); focus traps / dynamic comboboxes / `tabindex=-1` reordering / WebKit differences may need `@manual` keyboard-only audits — those stay legitimate manuals.

### Network mocking `[web]` (optional Mock Driver — `sungen capability add mock`)

```gherkin
@mock
Scenario: search shows the error state on server failure
  Given [reservations-check-500] mock is active        # install a NAMED catalog entry
  When User click [Search] button
  Then User see [Error Toast] message
  And User see [reservations-check-500] mock called {{n}} times   # exact count; "called 1 time" also valid
```

`[name]` is a **catalog reference, not a selector** — entries live in `qa/screens/<name>/mock/mocks.yaml` (`method`, `url` glob or `re:` regex, `status`, optional `headers`/`body`/`delay_ms`; a `"{{var}}"` body reads test-data at runtime). No inline `[GET /path]` forms. Rules:
- **Fail-on-unmocked (default):** once a scenario has any mock, unmatched fetch/XHR requests FAIL the test (documents/assets still load). Opt out per scenario with `@mock:passthrough`.
- **Mocks that must intercept page-load requests go in Background, BEFORE the navigation step** — installing after a navigation warns SG-W012 (load-time requests were not intercepted).
- No `abort` entry (a network error is not a timeout) — simulate timeouts with a large `delay_ms`.
- mocks.yaml is committed: credentials in bodies must be `${QA_*}` refs, never pasted DevTools responses (the secret scan covers `mock/*.yaml`).
- Web only. Requires the driver: without it, `@mock` is a hard compile error pointing at `sungen capability add mock`.

### Database verification (optional Data Driver)

Read-only DB-state checks. **Prefer named queries** — SQL lives in `qa/screens/<screen>/database/queries.yaml` (reviewed once, parameterized). Invoke with the `@query:<name>` annotation; it binds the result rows to `{{name}}`, then assert with `expect`:

```gherkin
@query:active_user                 # precondition: run query, bind {{active_user}}
@query:orders(buyer={{email}})     # …with explicit param override
Scenario: ...
  Then expect {{active_user.count}} is at least {{one}}   # ≥1 row
  And  expect {{active_user.first.status}} is "active"     # first row's column
  And  expect {{orders.count}} is {{expected}}             # exact count
  And  User see [Total] text is {{orders.first.total}}     # UI ↔ DB
```

Path access on a bound result: `{{q.count}}`/`{{q.length}}`, `{{q.first.col}}`, `{{q.last.col}}`, `{{q[2].col}}`, `{{q.col}}` (= first row's col). `expect A is B` also supports `is at least` / `is at most` / `is not`. Tier-2 declarative (trivial inline, no catalog): `User see [<table>] row where [<col>] is {{v}} [has [<col2>] = "x"]`, `… no row where …`, `… count is {{n}}`. Full grammar + catalog/datasource/secret rules → **Advanced → Database** doc. Only emit DB steps when the project has a `database/` catalog / `datasources.yaml`.

### NoSQL engines (Cosmos, MongoDB, DynamoDB) — natively supported

Beyond the three SQL engines, the Data Driver has native NoSQL engines. Declarative steps (`User see [table] row where …`, `… no row …`, `… count is …`) work across ALL of them; only the raw `@query` catalog shape differs per engine:

- **`engine: cosmos`** (Azure Cosmos DB, NoSQL API) — SQL dialect. Author `sql:` catalog entries against the mandatory `c` alias (`SELECT * FROM c WHERE c.<field> = :p`); named params bind automatically. Config: `endpoint`+`key` (or a connection-string `url`), `database`, `container` (default when no `[table]`). Read-only by construction (the query API can't mutate).
- **`engine: mongodb`** — no SQL. Author a `find:` (`{collection, filter, projection?, sort?, limit?}`) or `pipeline:` (`{collection, stages: […]}`) catalog entry; `:param` placeholders bind at runtime. `$out`/`$merge`/`$where`/`$function`/`$accumulator`/`mapReduce` are refused. Config: a `mongodb://` `url`. Read-only = a server-side read role.
- **`engine: dynamodb`** — raw `@query` is PartiQL SELECT (`sql: SELECT … FROM "table" WHERE pk = :p`). Declarative steps route GetItem/Query only for partition-/sort-key filters via a `keySchema:` registry; a **non-key filter is refused** (use a raw PartiQL SELECT — never a full-table Scan). Config: `region` + `keySchema` (credentials from the AWS env chain). Read-only = IAM `PartiQLSelect`-only.

Full config + catalog examples → **Advanced → Database** doc.

### Unsupported DB engine — fallback (ask, don't improvise)

Direct SQL verification (`@query`, the Data Driver) covers these SQL engines: **`{postgres, sqlite, device-sqlite, mysql}`** (`device-sqlite` = an ON-DEVICE app database on a mobile project — pulled fresh per query through the Appium session via `package:` + `db_path:`; debuggable build or emulator required) — plus the three native NoSQL engines above (Cosmos, MongoDB, DynamoDB). When a datasource's `engine` (or the DB the user describes) is **outside all of those** — e.g. Cassandra, Oracle, Neo4j, a proprietary store:

1. **Do not** attempt a direct connect. **Do not** improvise a verification method.
2. Present a fixed `AskUserQuestion` with exactly these 3 branches (always these, no others invented):
   - **@api** — verify state via the project's internal API (route to the API driver).
   - **@manual** — emit a `@manual` step + manual-check guidance (e.g. the vendor console / CLI — `cqlsh` for Cassandra, `cypher-shell` for Neo4j).
   - **Proxy / read-replica SQL** — if the user can supply a SQL-compatible endpoint (including Azure Cosmos DB **for PostgreSQL** / Citus), treat it as `engine: postgres` (or `mysql`) and use the normal Data Driver path above.
3. **Recommend, don't decide.** Check the project for signals and put the best-fit branch **first**, tagged **"(Recommended)"** with a one-line reason:
   - a project API driver / `qa/api/` catalog / `PEERCONNE_URL`-style internal API exists → **@api** first;
   - else the user hints at a SQL-compatible replica/proxy → **proxy-SQL** first;
   - else → **@manual** first.
   The other two branches plus an **"Other"** option (user describes their own approach) stay available.
4. **The user always overrides** — they may pick any branch or "Other" regardless of the recommendation.
5. **Never auto-run** a branch and never silently pick one on the user's behalf — the question must always be asked.

Example:
```
AskUserQuestion:
  question: "This datasource's engine (cassandra) isn't a supported Data Driver engine ({postgres, sqlite, device-sqlite, mysql} + Cosmos/MongoDB/DynamoDB) — direct connect isn't supported. How should DB state be verified?"
  options:
    - "@api — verify via the project's internal API (Recommended: qa/api/ catalog found)"
    - "@manual — emit a @manual step + cqlsh check"
    - "Proxy / read-replica SQL — supply a SQL-compatible endpoint → engine: postgres"
    - "Other — describe your own verification approach"
```

### API response assertions (optional API Driver)

An `@api:<name>` call binds the response to `{{name}}` = `{ status, ok, body, headers }` (+ `ok_count`/`status_counts` under `@concurrent`). Assert with the same `expect A is B` grammar + path access:

```gherkin
@api:create_user(email={{email}}) @query:user_by_email(email={{email}})
Scenario: ...
  Then expect {{create_user.status}} is 201                    # status
  And  expect {{create_user.body.id}} is at least {{one}}      # server-derived body field
  And  expect {{user_by_email.count}} is {{one}}               # proof of effect (DB oracle)
```

Path access: `{{name.status}}`, `{{name.body.<path>}}` (dot/`[i]` nav), `{{name.headers.x-request-id}}` (dot form — hyphens are fine, no quotes/brackets), `{{name.ok_count}}`.

Full-shape contract check: `expect {{name.body}} matches schema [Ref]` validates the response body against the named JSON Schema in `qa/api/schemas.yaml` (populated by `sungen api import`). Use alongside a couple of concrete field asserts, not instead of them.

**Depth rule** (see `sungen-tc-review` API rubric):
- **Success (2xx)** → assert status **and** ≥1 body field; a **mutating** success must also prove the effect (`@query` read-back, follow-up GET, or `@concurrent` `ok_count`). Never status-only.
- **Error (4xx/5xx)** → assert the status (via `@cases` `expect_status` or an explicit `is 4xx`); assert the error message field when the contract defines one.
- **Anti-pattern** — re-asserting the value you just sent (`{{x.body.email}} is {{email}}` on the thing you created) proves little; assert a **server-derived** field (id, timestamp, computed status) or read it back.

### Platform Support — web-only / mobile-only / divergences

Every claim below is checked against a shipped `.hbs` under
`adapters/{playwright,appium}/templates/steps/` or a shipped diagnostic. The authoritative
per-template answer is **generated from the adapters themselves** — `docs/spec/step-support-matrix.md`
(regenerate: `npm run gen:step-matrix`; `DIVERGENCES` lives in `tools/step-support-matrix/sources.ts`). Read
it when this prose and the compiler seem to disagree: the matrix is derived, this section is authored.

The matrix maps **templates**, not phrasings — a phrasing no pattern requests is absent from it
entirely (a *pattern* gap, not a template gap), which is what the catalogue below is for.

**Web-only `[web]`** — the Appium template throws, naming the reason: `fill [T] uploader with
{{f}}`, Positional table rows, the whole Table section, Browser storage, Tab order, `@mock`,
`Then User is on [T] page` (route-assertion). Each targets something native has no equivalent for
(HTML `<table>`, `<input type=file>`, DOM storage, keyboard-focus traversal, `page.route`, a URL bar).

**Mobile-only `[mobile]`** — the gesture catalog (swipe, long-press, pinch-zoom, pull-to-refresh,
rotate, background/foreground, notifications, grant-permission, clipboard set, set-geolocation,
hide-keyboard, tap-top-of, `go back`) has no web counterpart, and neither do the lifecycle / device-state /
timing steps (`restart the app`, `reinstall the app`, `turn airplane mode|wifi|mobile data on|off`,
`turn device location on|off` — the latter two Android-only, throw on iOS — the window
assertions `see [X] disappear within N seconds` / `still visible after N seconds`, and the
device-oracle trio `open deep link {{url}}` · `see device log contains {{v}}` · `pass|fail
biometric authentication` (biometric = emulator/Simulator only).
Full syntax → `sungen-mobile-gestures`.

**Divergences — compiles on both, means something different:**

| Step | Web | Mobile |
|---|---|---|
| `see [T] page` \| `page with {{v}}` | asserts path+query | **silent no-op** — asserts nothing; the scenario passes regardless. Assert a screen-marker element instead |
| `is on [T] page` \| `open [T] page` (Given/When) | navigates via URL | `is on` = the app-launch anchor, correctly a no-op. `open`/`navigate to` = a no-op that **reports `SG-W026`** — it promises a screen change and performs none; use tap/gesture steps instead |
| `wait for [T] page` | waits for the URL, then settles and HOLDS it (`waitForPage`) | fixed `driver.pause(500)` settle — not a real wait condition |
| `go back` (OS/hardware back) | **not a web step** — reports `SG-W020`; re-enter with `is on [X] page` or tag `@manual` | Android: `KEYCODE_BACK`, the real hardware key. iOS: WDA **navigation**-back, *not* the user's edge swipe-back (iOS has no hardware back). Distinct from `tap [Back] button`: system back skips the screen's own handler unless the app wires it |
| `hover [T] icon \| row` | real hover | no-op — hover-revealed content is normally already visible on mobile; use `tap` |
| `fill [T] alert with {{v}}` | fills native `prompt()` | no-op (comment only) — app-specific, handle manually |
| `switch to [T] frame` | enters an `<iframe>` | switches a hybrid app's WebView context; no-op on a pure-native screen. **Needs `APPIUM_CHROMEDRIVER_AUTODOWNLOAD=1`** (.env.appium) or the switch fails with "No Chromedriver found". INSIDE the webview, selectors are **css/xpath only** — UiSelector-based types and the text-value asserts (`label with {{v}}`) don't apply there; assert element presence via css/xpath refs, then `switch to [main] frame` back |
| `see [X] with {{v}}` (filtered visibility forms) | CSS `hasText` filter | hand-rolled substring match over `getText()`/`content-desc` (Android) or `label`/`value` (iOS) — same substring semantics, different attribute set |
| `… is sorted …` / `… is loading` inside a filtered row/state check | reads `aria-sort`/`aria-busy` | **throws** — no native analog for these two states specifically (the plain, unfiltered `is loading` on a spinner still works on both) |
| `scope: dialog` selector option | resolves inside the dialog | no effect (`SG-W021`) — steps resolve against the whole screen |
| `open notification panel` (mobile gesture) | n/a | Android-only — throws on iOS |

**Partial support, not full unsupported** — read the `.hbs` before marking anything `[web]`:
`all-contain-*`, `check`/`uncheck`/`toggle-action`, `contain-text`/`have-text-assertion`,
`visible-filtered-assertion`, `disabled`/`hidden-with-filter-assertion` are fully supported on
mobile even though a grep shows a `throw` somewhere in their file — the throw is a normal
zero-match assertion failure, not a platform gap.

### States

`hidden` `visible` `disabled` `enabled` `checked` `unchecked` `focused` `empty` `loading` `selected` `expanded` `collapsed` `sorted ascending` `sorted descending`

Any other trailing `is <word>` / `is not <word>` (`is on`, `is active`, `is open`, `is not visible`) is
**not** a state: the compiler refuses it (SG-E023) rather than check only visibility. `is displayed` /
`is shown` / `is present` read as `is visible`, and `is not hidden` as visible too.

### Element Types

| Group | Types |
|---|---|
| **Context** | `page` `dialog` `modal` `drawer` `tab` `alert` `overlay` `step` |

> `page` is the one Context type with **no state**: `dialog`/`modal`/`drawer`/`tab` etc. all take
> `is hidden` / `is visible`, `page` never does. It asserts a URL. See Pattern 8.

| **Input** | `field` `textarea` `search` `dropdown` `option` `checkbox` `radio` `toggle` `uploader` `slider` `date-picker` |
| **Trigger** | `button` `link` `icon` `menuitem` `tag` |
| **Data** | `table` `row` `column` `cell` `list` `item` `card` `section` |
| **Feedback** | `message` `header` `label` `text` `tooltip` `badge` `breadcrumb` `image` |
| **System** | `key` `frame` `spinner` `progressbar` |

### Auto-infer (no YAML entry needed)

Most elements auto-infer from `[Label] type` → `getByRole(type, { name: 'Label' })`. Only add YAML when the accessible name differs, needs `nth`, or needs `testid`. Full auto-infer table → see `sungen-selector-keys` skill.

**Types requiring YAML entry:** `date-picker`, `uploader`, `overlay`, `frame`, `step` - these have no standard ARIA role and need explicit selectors.

## YAML Keys

`[Reference]` → **lowercase, keep Unicode**: `[Search Content]` → `search content:`, `[Thời gian]` → `thời gian:`

- Keys use **spaces** (not dots) as word separators
- Same label, different element types → add `--type` suffix
- Same label, nth occurrence → add `--N` suffix
- Target Name > 30 chars → shorten to 1–3 meaningful words

## Dynamic Variables (test-data YAML)

Use `{{$var}}` in test-data YAML for values that must be unique per test run. Resolved at **runtime** by `TestDataLoader` — the compiler passes them through unchanged.

| Variable | Example | Output |
|---|---|---|
| `{{$timestamp}}` | `"User-{{$timestamp}}"` | `"User-1714000000000"` (milliseconds) |
| `{{$uuid}}` | `"{{$uuid}}"` | `"a1b2c3d4-..."` |
| `{{$random:min:max}}` | `"{{$random:1:100}}"` | `"42"` |
| `{{$date}}` | `"{{$date}}"` | `"2026-04-24"` |
| `{{$date:+Nd}}` / `{{$date:-Nd}}` | `"{{$date:+1d}}"` | `"2026-04-25"` — today shifted by N days |
| `{{$datetime}}` | `"{{$datetime}}"` | `"2026-04-24T10:30:00.000Z"` |

`{{$date}}` is the LOCAL calendar date of the machine running the tests — the "today" the browser shows
when `playwright.config.ts` sets no `timezoneId`. If the config pins `timezoneId`, export the same zone as
`TZ` (e.g. `TZ=Asia/Tokyo npx playwright test`) so both agree. `{{$datetime}}` is a UTC instant (`…Z`). Any `{{$date:…}}` argument other than `±Nd`
(`tz=…`, a format) and any `{{$datetime:…}}` argument are not supported and stay literal.

**Rules:**
- `$timestamp` and `$uuid` → same value across all keys in one `load()` call (stable within a test file)
- `$random` → unique per occurrence (each key gets a different random)
- Resolved once at load time → every `get()` returns the same resolved value
- Use for CRUD flows to avoid data collision between parallel runs

```yaml
# test-data/crud-award.yaml
award:
  name: "Award-{{$timestamp}}"
  email: "test+{{$uuid}}@example.com"
  score: "{{$random:1:100}}"
```

## Selectors (priority order)

| type | value | name | use |
|---|---|---|---|
| `testid` | data-testid | — | when exists |
| `role` | button/heading/link… | accessible name | interactive elements |
| `placeholder` | placeholder text | — | inputs |
| `label` | label text | — | labeled inputs |
| `text` | — | — | static text |
| `locator` | CSS selector | — | last resort |
| `page` | relative URL | — | navigation |
| `upload` | — | — | file inputs |
| `frame` | iframe selector | — | iframes |
| `id` | `#the-id` (a bare `the-id` also works) | — | an element whose only stable handle is its DOM id |

Options: `nth` `exact` `scope` `match` `variant` `frame` `contenteditable` `columns` `checked` `selected`

`checked: { class: <cls> }` or `checked: { attribute: <attr> }` — how a checkbox-like control that is
NOT a real checkbox (a `<div>` tile with no `role`/`aria-checked`; `.check()` throws "Not a checkbox or
radio button") shows its state. `selected:` is an ALIAS of the same signal, for a card/tab picker where
`checked:` reads wrong; declare ONE spelling and ONE of class/attribute. `check`/`uncheck` click only
when the state differs, then assert it; `is checked` / `is unchecked` / `is selected` (plain or
`with {{v}}`) assert it. The class is read on the located element OR on a descendant (a checkmark
`<svg>` that only mounts when on); the attribute is read on the located element itself. Other states on
the same entry (`is focused`, …) keep their native matchers. The value must be ONE plain class token or
attribute name — whitespace, a quote or a backslash is refused at compile time, naming the entry.

`attribute:` names the attribute an `image`/`link` assertion reads, and it takes ANY attribute name
straight from the YAML — nothing is hardcoded to `href`/`src`. To assert several attributes of ONE
element, declare several keys with the SAME locator and different `attribute:`; each key is then a
separate reference in the Gherkin:
```yaml
job link:            # the link's TEXT
  type: locator
  value: 'a.job-link'
job link href:       # the same element, its href
  type: locator
  value: 'a.job-link'
  attribute: href
job link target:
  type: locator
  value: 'a.job-link'
  attribute: target
```

**A `page` value that is an ABSOLUTE URL now anchors the HOST as well as the path.** `value:
'https://admin.example.com/jobs'` means "that path on that host": `see [X] page`, `is on [X] page`
and `should remain on` all fail on `https://admin-stg.example.com/jobs`. Before, only `wait for [X]
page` checked the host and the assertions matched the path on any host at all — so an assertion
naming one environment silently passed in another. If the page is the same in every environment,
declare it **relative** (`value: '/jobs'`) and let `baseURL` supply the host; keep the absolute form
for a destination that really is on another host.

`anchor:` is a `page` entry's rendered-content proof: `Then User see [X] page` asserts the URL AND
that the anchor element is present, so a route guard that leaves the URL alone cannot pass it. Three
things it does NOT do, each verified rather than assumed: it is read only by the `Then`-side page
assertion, so `Given User is on [X] page` ignores it; the `should see route` / `should remain on`
forms ignore it too — they compile through the route assertion, which is never handed an anchor; and
nothing warns when a page entry declares one and no step asserts that page.

`scope` (e.g. `scope: dialog`) is `[web]`-effective only — no Appium template reads `inDialog`, so
on mobile a dialog-scoped ref still resolves against the whole screen (`SG-W021`).

## Tags

### Functional tags (affect code generation)

| Tag | Effect |
|---|---|
| `@spec:<id>` | **Traceability**: this scenario proves requirement `<id>` from `requirements/spec.md` (`@spec:FR-003`). Repeatable — a scenario may prove several. Read by `sungen audit` (requirement coverage) and by delivery (the Coverage sheet's requirement table). Without it the link is only inferred from wording and cannot survive an edit. |
| `@ui:<UI-NNN>` | **Design traceability**: this scenario proves design item `UI-NNN` from `requirements/ui-inventory.md` (`@ui:UI-014`). Repeatable. Read by `sungen audit` (`UI-ITEM-UNCOVERED`). An item deliberately not tested is recorded as a comment instead: `# UI-DEFERRED: UI-002..013 — <reason>`. Rules: `sungen-tc-generation` § Design Inventory. |
| `@manual` | Skip in generation |
| `@known-defect` | The scenario is EXPECTED to be red — it asserts a live defect. It still runs and still reports as failed (never skipped, never "fixed"), and it stays a pass-through tag so `--grep @known-defect` selects it. `@known-defect:DEF-NNN` names the record in `qa/defects/DEF-NNN-*.md` (`sungen defect draft` writes it); `sungen audit` reports `DEFECT-REF-MISSING` when that file does not exist. **`[mobile]`** Its failure does NOT escalate the next scenario's `@reset:` tier: a red that was designed in is not evidence of leftover state |
| `@auth:role` | Use auth storage state for role |
| `@no-auth` | Disable inherited auth |
| `@steps:name` | Define reusable step block (base scenario) |
| `@extend:name` | Prepend Given→When from @steps block (skip Then) |
| `@cleanup:overlay` | Auto-cleanup: dismiss dialogs/overlays after each test (cleanupPage) |
| `@cleanup:forms` | Auto-cleanup: clear form fields after each test (cleanupPage) |
| `@cleanup:scroll` | Auto-cleanup: scroll to top after each test (cleanupPage) |
| `@cleanup:storage` | Auto-cleanup: clear sessionStorage after each test (cleanupPage) |
| `@screenshot:on-failure` | Auto-capture screenshot when test fails (base.ts fixture) |
| `@serial` | Opt-in: one shared page for an ordered, genuinely stateful sequence → `test.describe.serial()`. A failure skips the rest |
| `@parallel` | Names the default (fresh page per test) — changes nothing. Compiles on mobile too, but a single device/emulator gets no session-isolation benefit from it |
| `@beforeAll` | Hook: runs once before all tests → `test.beforeAll()` |
| `@afterEach` | Hook: runs after each test → `test.afterEach()` (custom cleanup) |
| `@afterAll` | Hook: runs once after all tests → `test.afterAll()` |
| `@flow` | Mark feature as E2E flow (cross-screen testing) |
| `@reset:none\|soft\|data\|full` | **`[mobile]`** How much state each scenario starts from. Default `soft`. Feature-level, or on a Scenario to RAISE (never lower) that one scenario's tier. See "Reset tiers" below |
| `@cases:dataset` | Data-driven: run the scenario once per row of the `dataset` LIST in test-data → one `test()` per row |
| `@query:name` | Database: run the named query from `database/queries.yaml` (precondition) and bind its rows to `{{name}}`; assert with `expect {{name.count}} …` + path access. Override params `@query:name(p={{v}})`. Repeatable. (Optional Data Driver — see Database verification above) |
| `@api:name` | API: run the named request from `api/apis.yaml` (precondition) and bind the response to `{{name}}`; assert with `expect {{name.status}} …` + path access (`{{name.body.<path>}}`). Override params `@api:name(p={{v}})`. Repeatable. (Optional API Driver) |
| `@concurrent:N` | API idempotency: fire the bound `@api` request N times in parallel, then bind aggregates on the `@api` name — `{{name.ok_count}}` (2xx count) and `{{name.status_counts}}` (status→count map). Assert the exactly-once invariant (`expect {{name.ok_count}} is 1`); pair with `@query` as a DB oracle. Tag order = run order: `@api` (mutate) before `@query` (verify). (Optional API Driver) |
| `@hybrid` | One unit, two capabilities: a signed-in browser session (UI) authorizes the `@api` call — the API request reuses the UI `storageState`. (Optional API + UI Drivers) |

### Reset tiers (`@reset`) `[mobile]`

Web gets a clean slate for free — a URL reload is effectively fresh state. Mobile does not: to save
time the app stays alive across the whole session, so the end state of one scenario becomes the start
state of the next. Left uncontrolled that breaks test independence (scenarios can't run in isolation
or in parallel, a leftover "nearly right" state makes the FOLLOWING scenario fail, and a scenario that
dies mid-flow poisons every one after it). Nobody just reinstalls between scenarios because a cold
start is 5–30s — hence a ladder rather than one all-or-nothing switch.

| Tier | Mechanism | Cost | Clears |
|---|---|---|---|
| `none` | nothing — only the NATIVE-context + portrait guards | 0s | — state carries over BY DESIGN |
| `soft` | `terminateApp` + `activateApp` — **the default** | ~1–3s | in-memory state, navigation stack |
| `data` | `soft` + wipe the app's data layer | ~2–5s | + local DB, cache, prefs, keychain |
| `full` | `removeApp` + `installApp` | ~5–30s | + permissions, install-time state |

```gherkin
@platform:mobile @reset:data
Feature: Onboarding
  Path: com.example.app/.MainActivity | com.example.app

  Scenario: VP-001 First-run tutorial shows for a brand-new user
    Then User see [Tutorial Slide 1] section

  @reset:full
  Scenario: VP-002 Needs a clean install, unlike its neighbours
    Then User see [Permission Prompt] dialog
```

Rules that matter when generating:

- **`soft` is the default** — omit the tag unless the scenario actually needs more. An untagged
  feature behaves exactly as it always did.
- **Pick `data` for first-run / onboarding / empty-state oracles.** A plain relaunch leaves a
  completed tutorial completed, so the scenario passes on run 1 and fails on run 2.
- **Pick `full` only for permissions or install-time state.** It costs a cold start every scenario.
- **Pick `none` only for a deliberate carryover chain** (a cart accumulating across scenarios). Order
  becomes load-bearing, and the scenarios can no longer run independently — that is the trade.
- **A scenario tier RAISES the feature tier, never lowers it.** A `@reset:full` feature is not weakened
  by a `@reset:soft` scenario; use a separate feature if you need a weaker one.
- **A failed scenario escalates the NEXT one by one rung** (`soft`→`data`→`full`), because a process
  relaunch does not undo a half-written local DB. One rung, one scenario, and never from `none`.
  A **retry** of the failed scenario is NOT escalated — it re-runs at its declared tier, so a
  state-dependent failure fails both attempts and is reported honestly instead of passing on attempt 2.
  A scenario tagged **`@known-defect`** escalates nothing either: its red was designed in, so making
  the next scenario pay a harder reset for it buys nothing, on every run, forever.
- **Escalation never picks a tier this session cannot perform.** Reinstalling needs the app's build
  file, so when the app is attached by package the ladder caps at `data` (Android) / `soft` (iOS) and
  says it capped, instead of throwing. Nothing asked for the escalated tier, and the throw would land
  in `beforeEach` — a hook failure, which skips every remaining scenario in the FILE. The cap never
  goes BELOW the declared tier: a tier you asked for that the session cannot do still throws by name,
  because that one is an unmet contract rather than a recovery the runner chose.
- **`SUNGEN_RESET_TIER=data` raises the floor for a whole run** without regenerating specs — CI can run
  stricter than local dev. It never lowers a declared tier.
- **`data`/`full` on iOS reinstall the app**, because `mobile: clearApp` is Android-only (UiAutomator2).
  That needs `IOS_APP` (or `APP_APK` on Android) so the `appium:app` capability is present, or the
  reset throws naming it.
- **`data` and `full` wipe app data on the target device.** Emulator / dedicated test device only.

Mobile app-state preconditions have no per-step equivalent: `Given the user is logged out` compiles to
a COMMENT on Appium (auth lives in the keychain, not in cookies) and reports **SG-W023**. Declare
`@reset:data` on the Feature instead of writing a step that establishes nothing.

### Data-driven scenarios (`@cases`)

For one test case × many inputs (email/format/boundary validation, decision tables), tag the
scenario `@cases:<dataset>` and reference each row's columns as `{{col}}`. Put the rows as a LIST
in test-data — NOT inline; data stays runtime + env-overlayable.

```gherkin
@high @cases:email_validation
Scenario: VP-VAL-001 The email field rejects invalid formats
  When User fill [Email] field with {{email}}
  Then User see [Login Error] message with {{expected_error}}
```
```yaml
# test-data/<screen>.yaml
email_validation:
  - { case: "no @",    email: "plainaddress", expected_error: "Invalid email" }
  - { case: "valid",   email: "ok@x.com",     expected_error: "" }
```
An optional `case`/`name`/`label` column labels each run. Each row → its own pass/fail. Prefer
`@cases` over duplicating a scenario per value. (Gherkin `Scenario Outline`/`Examples` is NOT
supported — use `@cases`.)

### Pass-through tags (filter at runtime via Playwright --grep)

Any tag not listed above passes through to Playwright `{ tag: [...] }`. Feature-level tags inherit to all scenarios.

| Tag | Purpose |
|---|---|
| `@smoke` | Quick sanity check — run after every deploy |
| `@regression` | Full test suite — run nightly or before release |
| `@high` | Priority: must pass — login, auth, core CRUD, primary business flow |
| `@normal` | Priority: important — validation rules, secondary features, search/filter |
| `@low` | Priority: minor/cosmetic — tooltips, hover states, element presence |
| `@auto` | Standard scenario, ready for automation |
| Any custom | e.g., `@sprint-42`, `@team-payment` — any tag works |

**Assign priority by user impact** (canonical mapping — override only when context differs):

| Scenario type | Tag |
|---|---|
| Auth redirect / unauthenticated access | `@high` |
| CRUD happy path (create / update / delete — success) | `@high` |
| Core business rule, state transition | `@high` |
| XSS, SQL injection, permission blocked | `@high` |
| Required field error, unique/duplicate constraint | `@high` |
| Format validation (email, phone, date…) | `@normal` |
| Boundary value — inclusive (`<=`, `>=`) → `@high`; standard range → `@normal` | `@normal` |
| Secondary features (search, filter, sort, pagination) | `@normal` |
| Element presence, label, placeholder, tooltip | `@low` |

**Run filtered:**
```bash
npx playwright test --grep "@smoke"          # only smoke tests
npx playwright test --grep "@high"           # only high priority
npx playwright test --grep "@smoke|@high"    # smoke OR high
```

### Serial vs Parallel (test execution mode)

**Default: isolated** — `test.describe()` with a fresh page per test. Background runs as `beforeEach`; a failing scenario does not skip the others. Write every scenario so it stands alone — no scenario may rely on state another one left behind. `@parallel` only names this default.

**`@serial` opt-in** — `test.describe.serial()` with one shared page. Background runs once in `beforeAll`; a failure skips the rest. Use it only for a genuinely stateful, ordered sequence.

| Mode | Generated | Page | Background | On fail |
|---|---|---|---|---|
| Isolated (default, `@parallel`) | `test.describe()` | Fresh per test | `beforeEach` (N goto) | Continue |
| `@serial` | `test.describe.serial()` | Shared | `beforeAll` (1 goto) | Skip remaining |

**Multiple auth groups need the default isolation.** A feature mixing `@auth:user` with `@no-auth` compiles as-is — features are parallel by default, and `@parallel` only names that default. What breaks it is `@serial`: one shared browser context cannot carry two different sessions, so the compiler errors and asks you to drop `@serial` or split the feature.

`@no-auth` is a demand for an **empty** session, not merely the absence of `@auth:`. Its scenarios compile into their own `test.describe('no-auth')` with an inline empty `storageState`, so they stay signed out even when `playwright.config.ts` sets a global one. An untagged scenario is different again: it inherits whatever that config says.

### `@flow` tag (E2E cross-screen testing)

`@flow` marks a feature as a **flow** — an E2E journey spanning multiple screens. Flows live in `qa/flows/<name>/` with their own selectors, test-data, and requirements.

**Key differences from screen tests:**

| Aspect | Screen (`qa/screens/`) | Flow (`qa/flows/`) |
|---|---|---|
| Scope | Single page | Multiple pages |
| Selectors | `[Element]` → own YAML | `[Screen:Element]` → own YAML (namespaced) |
| Test data | `{{variable}}` | `{{phase.variable}}` (namespaced by phase) |
| Tag | `@auto` / `@smoke` etc. | `@flow` (required at feature level) |
| Multi-domain | N/A | Absolute URL in selector `path:` skips baseURL |

**Selector namespace format:** `[Screen:Element]` where colon separates screen prefix from element name. The YAML key is `"screen:element"` (quoted, lowercase).

```gherkin
# Feature file
When User fill [Login:Email] field with {{login.email}}
And User click [Login:Submit] button
Then User see [Dashboard] page
When User click [Dashboard:Awards] link
```

```yaml
# selectors.yaml — keys are namespaced, quoted due to colon
"login:email":
  type: 'testid'
  value: 'email-input'

"login:submit":
  type: 'role'
  value: 'button'
  name: 'Login'

dashboard:
  type: 'page'
  value: '/dashboard'

"dashboard:awards":
  type: 'role'
  value: 'link'
  name: 'Awards'
```

**Flow structure:**
- `Background:` — set starting page only (e.g., `Given User is on [Login] page`)
- Each `Scenario:` — one phase/step of the flow (login, navigate, submit, etc.)
- Page navigation between scenarios uses `[Screen] page` references

**CLI:** `sungen add-flow --flow <name>`, `sungen generate --flow <name>`, `sungen generate --all` (includes flows)

### @extend behavior

- Tool executes **only Given→When** of `@steps` scenario (skips Then)
- The `Given` in `@extend` scenario is the **entry assertion** (confirms state after base steps)
- **Entry assertion MUST use `Given User is on [X] page`** (or `[X] dialog`) — NEVER `Given User see [X] type`. `is on` takes ONLY `page` (`screen` on mobile) and `dialog`: it names a navigation target. For a state inside the current page (a wizard step, a panel) assert it with `Then User see [X] section` and reach it via the flow's When steps or an `@steps`/`@extend` base — the compiler rejects `is on [X] <other type>`.
- `Given` keyword ONLY allows `is on` action. `see` = `Then` only.
- If `@steps` scenario fails, `@extend` scenario is **skipped**
- Name format: `snake_case` or `kebab-case` with module prefix: `@steps:kudos__open_modal`

## Common Syntax Errors

| Error | Wrong | Correct |
|---|---|---|
| Wrong keyword | `Given User click [T] button` | `When User click [T] button` |
| `see` after Given | `Given User see [T] heading with {{v}}` | `Then User see [T] heading with {{v}}` (or `Given User is on [T] page` for entry assertion) |
| Name ≠ step type | Scenario name says "modal" but step uses `dialog` | Use the **same element type** in both: "...dialog opens" + `[X] dialog` |
| Wrong action for type | `When User click [T] checkbox` | `When User check [T] checkbox` |
| press wrong target | `When User press [Submit] button` | `When User press Enter key` |
| uncheck radio | `When User uncheck [Male] radio` | `When User check [Female] radio` |
| Hardcode data | `with {{admin@mail.com}}` | `with {{invalid_email}}` |
| Missing `is` for state | `with {{text}} hidden` | `with {{text}} is hidden` |
| State as value | `with {{disabled}}` | `is disabled` |
| State on a `page` | `Then User see [Home] page is hidden` | `Then User see [Other] page` — or assert a marker element: `Then User see [Home Banner] is hidden`. `page` asserts a URL; the clause is dropped and the assertion INVERTS. Rejected by `SG-E021` |
| State the step form cannot assert | `wait for [X] spinner is checked` · `see [R] row in [T] table with {{v}} is disabled` | Assert the state on the element itself: `Then User see [X] checkbox is checked`. A `wait` only honours `hidden`/`visible`/`disabled`/`enabled`; a row/column step only honours `is hidden`. Anything else is dropped → `SG-E021` |
| Invented step form | any sentence with no matching pattern | Use a form from this reference. There is no silent fallback any more — an unmatched step compiles to a runtime `throw` (`SG-W001`). If the check cannot be automated, tag the scenario `@manual` |
| Assertion clause the pattern drops | `Then User see [Code] text matches "<format>"` compiling to a bare `toBeVisible()` | Use the exact supported phrasing for the clause (see the `matches` / `contains` / `has text` forms above). A clause that gets swallowed leaves an assertion that passes whatever the element says, so the step is rejected with `SG-E023` instead. If the step still compiles bare, you are on an older global sungen — run `npx sungen` |
| Mobile lifecycle step with no app id | `When User restart the app` in a feature whose `Path:` carries only a route | Declare the app id on the Feature: `Path: com.example.app/.MainActivity \| com.example.app`. The lifecycle steps resolve the app from `Path`; without it the whole spec would die at runtime, so the step is rejected with `SG-E024`. For a mobile unit `--path` carries the APP ID, `--reach` carries the navigation recipe |
| Missing target type | `fill [email] with {{v}}` | `fill [email] field with {{v}}` |
| Asserting a cell with the row filter | `Then User see [Status] text in [Job Table] table with {{v}}` | The single `with` is read as the EXPECTED VALUE, not as a row filter, so the assertion runs against the WHOLE table and can pass on a row the sentence never named. Use the two-`with` form — `Then User see [Status] text with <expected> in [Job Row] row with {{v}}` — pointing the second `[…]` at a ROW entry, not at the table entry (a filter applied to the table narrows nothing). Warned with `SG-W032`. A `fill` is NOT flagged: a write needs a value, so its `with {{v}}` is that value and the step is an ordinary container-scoped write |
| Background with scope | `Background: ... And User is on [X] dialog` | Use `@steps` + `@extend` for scope-dependent flows |
| `is on` after When | `When ... And User is on [X] dialog` | `And User see [X] dialog` or separate Given |
| Literal URL navigate | `User navigate to "/dashboard"` | `User is on [Dashboard] page` (add page selector in `selectors.yaml`) |

## Background vs @steps/@extend

Both `Background` and `@steps`/`@extend` are valid — they serve different purposes.

| Pattern | Use when | Generates |
|---|---|---|
| `Background` | Simple shared setup (navigate to page) | `test.beforeEach()` |
| `@steps`/`@extend` | Complex reusable flows with scope (dialog, frame) | Inline merged steps in `test()` |

**Use `Background` for simple navigation:**
```gherkin
Background:
  Given User is on [Dashboard] page

Scenario: View stats
  Then User see [Revenue Chart] section
```

**Use `@steps`/`@extend` when scope matters (dialog, frame):**
```gherkin
@steps:open_modal
Scenario: Open modal
  Given User is on [Kudos] page
  When User click [Open] button
  Then User see [Modal] dialog

@extend:open_modal
Scenario: VP-UI-001 Title visible
  Given User is on [Modal] dialog
  Then User see [Title] heading
```

**Avoid `Background` with scope-dependent steps** — `When` + `And User is on [X] dialog` creates keyword mismatch (`is on` = Given, not When). Use `@steps`/`@extend` instead.

## Hooks & Cleanup

Two layers for test lifecycle management. Prefer `@cleanup:*` tags (Layer 1) — they work with base.ts automatically. Use hook scenarios (Layer 2) only for custom logic.

### Layer 1: `@cleanup:*` tags (automatic via base.ts)

Feature-level tags that activate cleanup fixtures in base.ts. No Gherkin steps needed.

```gherkin
@auth:admin
@cleanup:overlay
@cleanup:forms
Feature: User Management
  Path: /users

  Background:
    Given User is on [User Management] page

  Scenario: Create user shows form
    When User click [Add User] button
    Then User see [Create User] dialog

  Scenario: Search user by name
    When User fill [Search] field with {{search_name}}
    Then User see [User Row] row
```

| Tag | What base.ts does after each test |
|---|---|
| `@cleanup:overlay` | Press Escape, click body, dismiss fixed overlays |
| `@cleanup:forms` | Clear all input/textarea fields, reset selects |
| `@cleanup:scroll` | Scroll to top of page |
| `@cleanup:storage` | Clear sessionStorage |

### Layer 2: `@afterEach` scenario (custom cleanup)

Only when `@cleanup:*` tags aren't enough — feature-specific logic. `@afterEach` gets the per-test
`page`, so UI cleanup steps work in it.

It runs after every test it applies to — and it does NOT apply to the `@no-auth` group when the
feature mixes `@no-auth` with other scenarios: an anonymous test has an empty session, so a cleanup
that needs the signed-in page would fail it although its own steps passed. The hook is emitted inside
each other group instead. A feature whose scenarios are ALL `@no-auth` keeps the hook for them.

### Layer 3: `@beforeAll` / `@afterAll` (optional)

For one-time setup/teardown. A hook scenario takes `@api:` / `@query:` preconditions exactly like an
ordinary scenario (override args and `@concurrent:N` included) — they run inside the hook and bind
`{{name}}`:

```gherkin
@beforeAll @api:login(email={{email}},password={{pw}}) @api:list_items(token={{login.body.token}})
Scenario: seed the session once before any scenario
  Then expect {{login.status}} is 200
  And expect {{list_items.status}} is 200

@afterAll @api:cleanup
Scenario: drop the seeded data after the last scenario
  Then expect {{cleanup.status}} is 204
```

- A variable bound in `@beforeAll` (`{{login.body.token}}`) is readable from EVERY scenario of the
  feature (it runs before each of them). `@afterEach`/`@afterAll` bindings stay hook-local.
- A failing hook skips every test in the file, so hook steps are COLLECTED: each `Then` that fails is
  recorded and the next step still runs; a failing action step (`Given`/`When`) stops the hook there.
  The hook fails ONCE with the full list (`Hook preconditions not met`), not with the first failure.
- `@cases` and `@extend` are not supported on a hook — they are ignored with a compile warning.
- `@beforeAll` / `@afterAll` have no `page` fixture on an isolated (non-`@serial`) feature — keep
  their steps to `@api:`/`@query:` preconditions and `expect` assertions there. `@afterEach` does get
  the per-test `page`.

**Rendering order in `.spec.ts`:**
`test.describe` → `test.use(storageState)` → `test.use(autoCleanup)` → `test.beforeAll` → `test.beforeEach` → `test.afterEach` → `test.afterAll` → `test()` blocks.
On a `@serial` feature the user `@beforeAll`/`@afterAll` blocks are registered right after the built-in
`beforeAll` (page creation) and BEFORE the built-in `afterAll` (page close): Playwright runs `afterAll`
hooks in registration order, so the shared `page` is still open for the user's teardown steps.
