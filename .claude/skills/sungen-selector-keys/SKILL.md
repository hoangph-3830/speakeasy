---
name: sungen-selector-keys
description: 'Rules for generating correct YAML selector keys from Gherkin [Reference] names. Auto-loaded when creating or fixing selectors.yaml files.'
user-invocable: false
---

## Key Generation: `[Reference]` → YAML key

Copy the text from `[Reference]` as-is, then lowercase. Unicode characters (Vietnamese, Japanese, etc.) are preserved.

```
[Submit]              → submit
[Search Content]      → search content
[User's Profile]      → user's profile
[Thời gian]           → thời gian
[Địa điểm]            → địa điểm
[ログイン]              → ログイン
[パスワード]            → パスワード
[Page 2]              → page 2
[Kudos Detail Modal]  → kudos detail modal
```

**Rules:**
1. Lowercase the text
2. Trim leading/trailing whitespace
3. Collapse multiple spaces into one
4. **Keep all Unicode characters as-is** (Vietnamese diacritics, Japanese, etc.)
5. **Keys use spaces** (not dots) as word separators

## Flow Namespaced Keys

In `@flow` features, selectors are namespaced by screen using colon: `[Screen:Element]` → YAML key `"screen:element"` (quoted).

```
[Login:Email]        → "login:email"
[Login:Submit]       → "login:submit"
[Dashboard:Awards]   → "dashboard:awards"
[Awards:Submit]      → "awards:submit"
```

**Rules:**
1. Same lowercase + Unicode rules as standard keys
2. Colon separates screen prefix from element name
3. **YAML keys must be quoted** because of the colon: `"login:email":`
4. Page references don't need prefix: `[Login]` → `login:` (page type)
5. Prevents duplicate names across screens (e.g., `"login:submit"` vs `"awards:submit"`)

```yaml
# Flow selectors — each screen section namespaced
login:
  type: 'page'
  value: '/login'

"login:email":
  type: 'testid'
  value: 'email-input'

"login:submit":
  type: 'role'
  value: 'button'
  name: 'Login'

awards:
  type: 'page'
  value: '/awards'
  anchor:                      # optional — the element that proves the page RENDERED (#662).
    type: 'role'               # `see [Awards] page` asserts the URL, then this; without it the step is
    value: 'heading'           # a URL-only oracle a late 403 redirect can satisfy. One line per screen.
    name: 'Awards'

"awards:submit":
  type: 'role'
  value: 'button'
  name: 'Submit Award'
```

**Type and nth suffixes still apply:** `"login:submit--button"`, `"awards:item--3"`

## Type-Suffixed Keys

When the same label is used for different element types, add `--type` suffix:

```yaml
# [Add Campaign] button AND [Add Campaign] text
add campaign--button:
  type: 'role'
  value: 'button'
  name: 'Add Campaign'

add campaign--text:
  type: 'text'
  value: 'Add Campaign'
```

### Collision rule (MANDATORY)

**Before writing a base key, check whether it already exists with a *different* `type`.**
If it does, DO NOT overwrite it and DO NOT let the two share the base key — give **each** entry
its own `--type` suffix. A key match alone does not carry type: the resolver walks
`key--type` → `key` (see § Lookup Priority), so a base-key entry of the wrong type will be
picked up by any step referencing that label, regardless of the type the step expects.

The `page` type is the sharpest trap. A page step (`Then User see [X] page`) reads the entry's
`value` as a **URL**. If key `X` already holds a `role`/button entry, the compiler guards the type
— it does NOT emit `toHaveURL(/button/)` — but it can't invent the real URL either, so it falls
back to the feature's own path (or `/<ref>/`) and warns `SG-W013`: a test that reads as asserting
`X`'s page but never can, since the fallback path is anchored and almost certainly wrong.

```yaml
# Gherkin: `When User click [読取結果一覧] button`  AND  `Then User see [読取結果一覧] page`
# WRONG — one base key: the page step can't use the button's value, falls back to the feature path
# instead, and the compiler warns SG-W013 rather than asserting the real page:
読取結果一覧:
  type: 'role'
  value: 'button'
  name: '読取結果一覧'

# RIGHT — disambiguate by type; the page entry carries the real URL:
読取結果一覧--button:
  type: 'role'
  value: 'button'
  name: '読取結果一覧'

読取結果一覧--page:
  type: 'page'
  value: '/reading-results'   # captured from the live page, or the feature Path:
```

If the label is used by only ONE type, the base key (no suffix) is fine — suffix only on collision.

Type aliases (normalized automatically). The suffix and the step may use **different spellings of
the same type** — `confirm--modal` resolves for `[Confirm] dialog` and `confirm--dialog` resolves
for `[Confirm] modal`. Prefer the normalized suffix for consistency; either is found.

| Gherkin type | Normalized suffix |
|---|---|
| title, label, caption, message | `text` |
| heading, header | `heading` |
| logo, image, icon | `img` |
| btn | `button` |
| input, textbox, textarea, editor | `field` |
| search | `searchbox` |
| toggle | `switch` |
| alert | `alertdialog` |
| modal, drawer | `dialog` |
| column | `columnheader` |
| list-item | `listitem` |

When the step names a type from this table, only a key whose suffix is a spelling of that type is
used — `[Confirm] dialog` will not fall back to an unrelated `confirm--popup` entry. It reports the
keys it looked for, so the fix is to add one of them.

A bare `[Confirm]` keeps the older, looser fallback and matches any `confirm--*` entry, because with
no type there is nothing to match a suffix against. So does a `[Ref]` followed by a word that is not
an element type at all — `Then User see [Quick Panel] is hidden` reads `is` as that word. This is
what keeps state-clause steps working on a label whose keys are all suffixed.

## Nth-Suffixed Keys

When targeting a specific occurrence by index, add `--N` suffix:

```yaml
# [Gửi lời cảm ơn] button 3
gửi lời cảm ơn--3:
  type: 'role'
  value: 'button'
  name: 'Gửi lời cảm ơn'
```

## i18n: Template Variables in Selectors

For multilingual sites without `data-testid`, use `{{variable}}` in `name` or `value` fields to reference locale-dependent text from `test-data.yaml`.

```yaml
# selectors — one file for all locales
submit:
  type: role
  value: button
  name: "{{lbl_submit}}"

search:
  type: placeholder
  value: "{{lbl_search}}"

logo:
  type: testid
  value: app-logo            # testid is locale-independent — no variable needed
```

```yaml
# test-data/login.yaml (base — English)
lbl_submit: "Sign in"
lbl_search: "Search..."

# test-data/login.vi.yaml (Vietnamese)
lbl_submit: "Đăng nhập"
lbl_search: "Tìm kiếm..."
```

Run: `SUNGEN_ENV=vi npx playwright test`

**Rules:**
1. Prefix i18n keys with `lbl_`, `msg_`, `txt_` to separate from test data
2. Prefer `data-testid` — only use `{{variable}}` when no stable selector exists
3. Feature file stays identical across locales
4. Requires runtime data mode (default, not `--inline-data`)

## Lookup Priority

Resolver searches in this order:
1. `key--N` (nth-suffixed)
2. `key--type` (type-suffixed, normalized)
3. `key` (base key)
4. Auto-infer from element type if no YAML entry
5. Any `key--*` (first suffixed match)

## Auto-Infer (no YAML entry needed)

If no YAML key exists, the resolver infers from the Gherkin element type:

> ⚠️ **Auto-infer pitfall — the #1 cause of selector failures in production.**
>
> `[X] button` auto-infers as `getByRole('button', { name: 'X' })`. This **only works** when the button's accessible name in the DOM is **exactly `X`** — same language, same text, same casing.
>
> The Gherkin `[Reference]` is your human label for the element, **not** the DOM name. If the app is in Vietnamese (or any language where the Gherkin label differs from DOM text), auto-infer will produce `No element found` at runtime. **Write an explicit YAML entry** with the real DOM name instead.
>
> **Decision rule**: auto-infer is safe ONLY when you have confirmed in the snapshot that the DOM element's accessible name / placeholder text is literally `X`. When in doubt → write YAML.

| Gherkin | Inferred locator | Safe when… |
|---|---|---|
| `[X] button` | `getByRole('button', { name: 'X' })` | Button's accessible name = X |
| `[X] link` | `getByRole('link', { name: 'X' })` | Link text = X |
| `[X] heading` / `header` | `getByRole('heading', { name: 'X' })` | Heading text = X |
| `[X] checkbox` | `getByRole('checkbox', { name: 'X' })` | Checkbox label = X — a checkbox-LIKE tile with no `role`/`aria-checked` needs an explicit entry plus `checked: { class: <cls> }` or `checked: { attribute: <attr> }` (read on the located element itself; `expanded.class` reads a descendant) |
| `[X] radio` | `getByRole('radio', { name: 'X' })` | Radio label = X |
| `[X] field` | `getByPlaceholder('X')` | Placeholder text = X AND field has a placeholder |
| `[X] text` / `message` / `label` | `getByText('X')` | Visible text = X (partial match) |
| `[X] logo/image/icon` | `getByRole('img', { name: 'X' })` | Image alt = X |
| `[X] search` | `getByRole('searchbox', { name: 'X' })` | Searchbox label = X |
| `[X] option` | `getByRole('option')` — **no name** | web only (see the list-member note below) |
| `[X] slider` | `getByRole('slider', { name: 'X' })` | Slider label = X |
| `[X] toggle` | `getByRole('switch', { name: 'X' })` | Toggle label = X |
| `[X] tab` | `getByRole('tab', { name: 'X' })` | Tab text = X |
| `[X] table` | `getByRole('table', { name: 'X' })` | Table aria-label = X |
| `[X] list` | `getByRole('list', { name: 'X' })` | List aria-label = X |
| `[X] column` | `getByRole('columnheader', { name: 'X' })` | Column header text = X — **overridable**, see the column-header note below |
| `[X] dialog` / `modal` / `drawer` | `getByRole('dialog', { name: 'X' })` | Dialog aria-label/heading = X |
| `[X] dropdown` / `select` | `getByRole('combobox', { name: 'X' })` | Combobox label = X |
| `[X] menuitem` | `getByRole('menuitem', { name: 'X' })` | Menu item text = X |
| `[X] progressbar` | `getByRole('progressbar', { name: 'X' })` | Progressbar label = X |
| `[X] section` | `getByRole('region', { name: 'X' })` | Section aria-label = X |
| `[X] card` | `getByRole('article', { name: 'X' })` | Card aria-label = X |
| `[X] item` | `getByRole('listitem')` — **no name** | web only (see the list-member note below) |
| `[X] cell` | `getByRole('cell')` — **no name** | web only (see the list-member note below) |
| `[X] spinner` | `getByRole('status', { name: 'X' })` | Spinner aria-label = X |
| `[X] breadcrumb` | `getByRole('navigation', { name: 'X' })` | Navigation aria-label = X |
| `[X] badge` / `tooltip` / `tag` | `getByText('X')` | Visible text = X |

**Column headers: the `[Reference]` is not required to be the DOM text.** `[X] column` defaults its accessible name to the reference, which is unmatchable when the page is not in the reference's language (a `[Category]` column whose header reads 記事分類). Declare the real header instead of renaming the reference — the reference is what ships in the delivered test case. Most specific first: the table entry's `columns.<col>.header`, then a `<col>--columnheader` entry's `name:`, then the reference text. A `header` may hold `{{variables}}` for per-locale text, like any `name:`. A same-named entry that is NOT a column header is ignored and cannot hijack the lookup.

**Special note on the list members `[X] item` / `[X] option` / `[X] cell`** — the only rows above that behave differently per platform:

- **Web**: they infer the role with **no `name`**, on purpose. A list member takes its accessible name from its own CONTENT, so a name defaulted from the `[Reference]` text matches zero elements — and `toBeHidden()` PASSES on an empty locator, so the scenario would go green having proved nothing. The role alone is the whole locator; `nth` or a count assertion narrows it. An explicit `name:` you write yourself is always honoured.
- **Mobile**: they do **not** auto-infer at all. A native a11y tree has no role concept, so with no name there is nothing to build an accessibility-id from — the old behaviour emitted the role token itself (`~listitem`), which no tree ever contains. `sungen generate` now fails naming the key to add, and selector generation (Phase 0) scans the live page for it instead of skipping. **Write an explicit entry**, e.g. `job list--item: { type: accessibility-id, value: 'job-row' }`.

**On mobile, `type: role` needs a `name:`.** The rule above is not about these three types — it is about a role with **no accessible name**, whichever way it got that way. On mobile such a role compiles to the role token (`~status`) and can never match, so it is refused at compile time. That includes `name: null`, which is the web opt-out for a content-named role: it has no mobile meaning, and `SG-W003` (which recommends it) does not fire on mobile for that reason. On a native app, prefer `type: accessibility-id`, `type: xpath` or `type: id` over `type: role` — the device exposes content-desc / accessibilityIdentifier, not ARIA roles.

**Special note on `[X] field`**: `getByPlaceholder('X')` only works when (1) the field has a placeholder attribute AND (2) the placeholder text equals X. For fields without placeholders (floating labels, aria-label), write explicit YAML: `type: label, value: "Actual label text"`.

**Only add a YAML entry when** auto-infer cannot work: DOM name differs from Gherkin label, need `testid`, need `nth`, need `exact: true`, need `name: null`, or the field type requires explicit config.

**`nth:` takes an integer `>= -1`** — `0`, `1`, `2`… are zero-based indexes and `-1` is the LAST
match (`.last()` on web, `.at(-1)` on mobile). Nothing else is an index: `nth: -2`, `nth: 'last'`
and `nth: 1.5` are ignored, the selector stops filtering by position, and the compiler reports
`SG-W018`. Reach for `nth` only when `exact: true` or `scope` cannot disambiguate.

### `name: null` — name-from-content roles (toast, alert, spinner)

For a `type: role` entry **without** `name:`, the compiler defaults the accessible-name filter to the Gherkin `[Reference]` text. That is right for buttons/links — and **always wrong** for roles whose accessible name derives from their CONTENT (`status`, `alert`, `log`, `marquee`, `timer`): a toast's name is its message text, never the string "Success Toast". The compiler warns (`SG-W003`). Opt out explicitly:

```yaml
success toast:
  type: 'role'
  value: 'status'
  name: null        # deliberate: match by role only, no name filter
```

Use this for every toast/alert/spinner content assertion. Do NOT fall back to `type: locator, value: '[role="status"]'` — `name: null` keeps role semantics.

**List-member roles need no opt-out.** For `option`, `listitem` and `cell` the compiler never defaults
the name at all — those name themselves from their content, and the value being asserted is already
carried by `.filter({ hasText })`. Omit `name:` and the locator comes out as `getByRole('option')`.
Declare `name:` only when the DOM really does expose an accessible name worth filtering on; an
explicit one is always honoured.

### Types requiring YAML entry (no auto-infer)

These types need explicit `selectors.yaml` entries:

| Type | Reason |
|------|--------|
| `date-picker` | Custom component, needs testid or CSS |
| `uploader` | File input, needs upload type selector |
| `overlay` | No standard ARIA role, needs CSS/testid |
| `frame` | Needs iframe selector |
| `step` | Custom stepper component, needs testid |
