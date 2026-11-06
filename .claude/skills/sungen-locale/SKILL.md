---
name: sungen-locale
description: 'Bootstrap i18n for a screen/flow — audit hardcoded selector text, detect locale-switch mechanism via Playwright (web) or device locale (mobile), generate test-data overlay file. Auto-loaded by /sungen:locale command.'
user-invocable: false
---

## Platform Detection (do this FIRST)

Read `qa/capabilities.yaml` and check the `platform` field.

- If `platform: mobile` → follow the **mobile path** (see "Mobile path" section below)
- If `platform: web` or field absent → follow the **web path** (standard, below)

### Mobile path

When `platform: mobile`:

1. **Live capture**: use **Appium MCP** (`select_device` → `appium_session_management(create)` with device at base locale → `appium_get_page_source` / `appium_screenshot`) — NOT Playwright browser tools
2. **Locale-switch mechanism**: **device locale** — `appium:language` / `appium:locale` caps + `noReset:false` (set by wdio `resolveLocaleCaps` at runtime). No URL prefix, query param, or browser-storage injection
3. **Phases skipped for mobile**: audit (Phase 1), capture base (Phase 2), detect mechanism (Phase 3) are unchanged. Phases 3.5 (browser storage diff), 3.6 (storage verify), 6d (Pages URL update), 6f (locale-config.json / locale-fixture) are **web-only** — skip them. wdio `resolveLocaleCaps` handles runtime locale automatically
4. **OFFLINE mode**: scaffold overlay template + ask user to fill manually if device unreachable or `--offline` flag given
5. **Hand-off**: print `SUNGEN_ENV=<locale> npm run test:mobile`. Include note: "If the device locale code differs from the overlay env name, set `SUNGEN_LOCALE=<code>` — wdio `resolveLocaleCaps` reads it as an override."
6. **Phase 6 (mobile) writes only**: 6a selectors `{{var}}`, 6b base `test-data/<feature>.yaml`, 6c overlay `test-data/<feature>.<locale>.yaml`. Skip 6d (Pages URL) and 6f (locale-config.json)
7. **Compile**: `sungen generate --screen <name>` (appium adapter auto-selected in mobile project)

**Stop here for mobile.** Do not continue to the web path below.

---

## Goal (Web)

Take a screen/flow whose `selectors/*.yaml` and `.feature` files were authored against the default locale (usually Vietnamese) and prepare it to run against a second locale. Output:

1. `selectors/<feature>.yaml` — hardcoded `name`/`value` replaced with `{{var}}`
2. `test-data/<feature>.yaml` — base locale, complete with all new keys
3. `test-data/<feature>.<locale>.yaml` — overlay with only the keys that change
4. **`qa/capabilities.yaml` — DECLARE the locale** (required since 3.2.15): append the code to `locales:` and set `base_locale:` (language of the base test-data; tool default is `en`). Delivery/dashboard treat ONLY declared codes as locales — undeclared result-file suffixes are assumed to be ENVIRONMENTS (dev/staging) and excluded from locale sheets:
   ```yaml
   platform: web
   enabled: [web]
   base_locale: en      # or vi/ja — whatever the base test-data is written in
   locales: [ja]        # append every bootstrapped locale here
   ```
   Env × locale combos use the compound `SUNGEN_ENV=<env>-<locale>` (e.g. `staging-ja`, overlay `<feature>.staging-ja.yaml`) — the locale segment LAST; delivery recognizes it when the tail matches a declared locale.
4. (Optional) `selectors/<feature>.yaml` Pages block updated when locale uses URL prefix or query param

After this skill finishes, `sungen run-test <name> --env <locale>` Just Works.

## Run mode — Live (preferred) vs Offline (fallback)

Pick mode **once at start**, based on whether MCP Playwright can reach the page.

```
Try: browser_navigate(baseURL)
  → succeeds + page renders content → LIVE MODE (all 6 phases)
  → fails (auth blocked, network down, app broken) → OFFLINE MODE
    (audit + scaffold template + ask user to fill, skip detection phases)
```

**Live mode is the value-add** — it auto-detects locale switch mechanism and auto-fills translations. Offline mode just makes the file structure right; user fills in text manually.

Announce which mode is being used before Phase 1.

## Phase 1 — Audit selectors (always, no MCP)

For each `.feature` file under the screen, read the matching `selectors/<feature>.yaml`. List every entry whose `name` or `value` field contains literal text WITHOUT `{{…}}` AND is not a CSS/href selector.

Classify each candidate:
- **`name` field of `role`-type selector** → very likely locale-dependent
- **`value` field of `text`-type selector** → very likely locale-dependent
- **`value` of `locator`-type selector** that contains `:has-text("…")` → check if the text is in target language
- **`value` of `page`-type selector** → URL — handled separately in Phase 3
- **CSS / href / attribute selectors** (e.g. `a[href="/awards"]`) → skip, locale-invariant

Print the candidate list as a table:

```
selector key       | field | hardcoded value           | classification
-------------------+-------+---------------------------+------------------
nav about          | name  | Giới thiệu SAA 2025       | locale-dependent
nav awards         | name  | Thông tin giải thưởng     | locale-dependent
event date         | name  | 26/12/2025                | maybe (date format)
nav kudos          | name  | Sun* Kudos                | brand — skip
```

If there are zero candidates and no `{{var}}` either, the screen has no localizable text → tell user and stop.

## Phase 2 — Capture base locale (LIVE only)

1. Read `playwright.config.ts` for `baseURL`. Read `Path:` from `.feature` for the entry-point path.
2. If the screen has `@auth:<role>` tags, load `specs/.auth/<role>.json` via `browser_set_storage_state` first.
3. `browser_navigate(baseURL + entryPath)` then `browser_wait_for` something stable.
4. Capture:
   - `browser_snapshot()` — DOM accessibility tree
   - `browser_evaluate(() => location.href)` — full URL
   - `browser_evaluate(() => ({ localStorage: {...localStorage}, cookies: document.cookie }))` — storage state hash
5. For each candidate from Phase 1: verify the hardcoded text actually appears on the page (string contains check against snapshot text). Drop candidates that don't appear (probably stale selectors or false positives).

Save state to memory as `baseLocale = { url, snapshot, storage }`.

If page redirects to `/login` (auth blocker) → stop. Print: *"Auth blocked — cannot capture live page. Fall back to OFFLINE mode by re-running once auth works, or pick `Offline-only` next time."*

## Phase 3 — Switch locale (LIVE only) — detect mechanism + storage delta

Goal: identify (a) HOW to switch the app to the target locale, and (b) WHAT app-side storage state ends up holding the locale preference so a fresh BrowserContext can be primed identically without driving the UI.

Before triggering the switch, capture full storage baseline:

```js
const before = await browser_evaluate(() => ({
  sessionStorage: { ...sessionStorage },
  localStorage:   { ...localStorage },
  cookies:        document.cookie,
  url:            location.href,
}));
```

Then try mechanisms in order. First one that produces visibly different text wins. For EACH attempt, capture `after` state and diff against `before`.

**3a. URL prefix**
- `browser_navigate(baseURL + '/' + locale + entryPath)` (e.g. `https://saa-2025.../en/`)
- Wait, snapshot
- Compare text content with baseLocale snapshot for a known-changed element
- If text differs and page didn't 404 → **URL prefix mechanism**. Save `localePrefix = '/' + locale`.

**3b. Query param** (only if 3a failed)
- `browser_navigate(baseURL + entryPath + '?lang=' + locale)` (also try `?locale=`, `?lng=`, `?l=`, `?language=`)
- Same diff check
- If text differs → **Query param mechanism**. Save `localeQuery = '?lang=' + locale` (or whichever variant worked).

**3c. Language switcher UI** (only if 3a + 3b failed)
- Look in base-locale snapshot for buttons/links matching: `'Select language'`, `'Language'`, `'言語'`, `'Ngôn ngữ'`, role=combobox
- If found, ask user: *"Detected possible language switcher: [X]. Click it and proceed?"*
- On confirm: `browser_click` the switcher, then `browser_click` the locale option
- Verify text changed
- If yes → **UI switcher mechanism**. Save selector + option details.

**3d. None detected**
- AskUserQuestion: *"Couldn't auto-detect locale switching. Either (a) demo it for me in browser then I'll snapshot, (b) provide locale URL manually, (c) skip — assume URL prefix `/<locale>`."*

### Phase 3.5 — Storage diff (always run after Phase 3 succeeds)

After locale text confirmed switched, capture `after` state and diff:

```js
const after = await browser_evaluate(() => ({
  sessionStorage: { ...sessionStorage },
  localStorage:   { ...localStorage },
  cookies:        document.cookie,
  url:            location.href,
}));

// Diff each storage area:
const sessionDiff = diffEntries(before.sessionStorage, after.sessionStorage);
const localDiff   = diffEntries(before.localStorage,   after.localStorage);
const cookieDiff  = diffCookies(before.cookies, after.cookies);
```

**Filter noise** — only keep entries where:
- Key name contains `lang|locale|language|i18n|intl` (case-insensitive), OR
- Value matches a locale code pattern: `^[a-z]{2}(-[A-Z]{2})?$` or matches the target locale code exactly

Drop known noise:
- Auth tokens: `sb-*`, `*-token`, `*-jwt`, `*-session-id`
- Analytics: `_ga*`, `_gid`, `_fbp`
- App state: `csrf*`, `last-*`, `visit-*`

**Auto-confidence scoring** per remaining entry:
- High confidence: key name contains locale-related word AND value is a locale code matching target → KEEP, no user prompt
- Medium: only one signal matches → present to user for confirmation
- Low: neither matches but key changed → present to user, default to skip

### Phase 3.6 — Verification (always run after Phase 3.5)

For each high/medium-confidence storage entry, verify by replaying in a FRESH context:

```js
// 1. Capture current page state (have JWT in memory) — note: can't replay JWT separately
// 2. Open new browser_navigate to baseURL but pre-set the detected key via addInitScript
// 3. Snapshot new page and confirm a known target-locale string appears

// Pseudo-code — real impl uses Playwright MCP `browser_evaluate` with init pattern
```

Actually since MCP browser sessions don't expose `addInitScript` directly, use a softer verification:
- Manually set the candidate key via `browser_evaluate(() => sessionStorage.setItem('key', 'value'))`
- Hard reload the page: `browser_navigate(baseURL)` to force re-init
- Snapshot, look for known target-locale text
- Caveat: hard reload kills any in-memory JWT — auth blocker amplifies failure surface. Skip verification if the screen has `@auth:*` tags and the staging app has known JWT-persistence issues.

If verification fails / can't run → drop confidence by one tier, ask user.

Save mechanism + verified storage delta to memory for use in Phase 6.

## Phase 4 — Diff base ↔ target (LIVE only)

For each candidate from Phase 1 that survived Phase 2:
- Find the SAME element in the target-locale snapshot (match by `role`+position, by `aria-label` if available, by neighbor structure)
- Extract its text → `targetText`
- Pair: `(selectorKey, hardcoded baseText, observed targetText)`

If matching fails for a candidate → mark as "needs manual" — flag for user input rather than skip.

Result: list of triples `{ selectorKey, baseText, targetText, confidence }`.

## Phase 5 — Confirm + edit (always)

Present the proposal table:

```
selector key       | base text                | target text          | proposed var     | apply?
-------------------+--------------------------+----------------------+------------------+-------
nav about          | Giới thiệu SAA 2025      | About SAA 2025       | nav_about        | [✓]
nav awards         | Thông tin giải thưởng    | Awards Info          | nav_awards       | [✓]
event location     | Au Co Arts Centre, Hanoi | Au Co Arts Centre…   | event_location   | (already has {{}})
nav kudos          | Sun* Kudos               | Sun* Kudos           | —                | [skip — same]
event date         | 26/12/2025               | 26/12/2025           | —                | [skip — same]
```

Propose **var names** by snake_casing the selector key. Avoid collisions with existing test-data keys.

Auto-skip rows where `baseText === targetText` (brand names, dates that don't change between locales).

Ask user via AskUserQuestion: *"Review the table above. Confirm to apply / edit individual rows / re-run capture / cancel."*

If user wants to edit a row → fall through to a per-row prompt asking for `var name` or `targetText` correction.

In OFFLINE mode this phase is the same table but `target text` column is blank — user fills via subsequent prompts or by editing the overlay file after the skill finishes.

## Phase 6 — Apply changes (always, after confirmation)

For each confirmed row:

**6a. Update `selectors/<feature>.yaml`**

Replace `name: '<baseText>'` with `name: '{{<varName>}}'`. Preserve quoting style.

**6b. Update `test-data/<feature>.yaml`** (base locale, complete dictionary)

Append new keys at the end, grouped under a `# === i18n: <screen> ===` comment. Example:

```yaml
# === i18n: home ===
nav_about: 'Giới thiệu SAA 2025'
nav_awards: 'Thông tin giải thưởng'
```

**6c. Create `test-data/<feature>.<locale>.yaml`** (overlay, only diffs)

```yaml
# home — <locale> overlay. Only keys that change vs base.
# Run with: SUNGEN_ENV=<locale> npx playwright test ...

nav_about: 'About SAA 2025'
nav_awards: 'Awards Info'
```

**6d. (URL/query mechanism only) Update Pages selectors**

If Phase 3 detected URL prefix:

```yaml
# selectors/<feature>.yaml — Pages block becomes locale-aware
home:
  type: 'page'
  value: '{{base_path}}/'
awards:
  type: 'page'
  value: '{{base_path}}/awards'
```

And add to test-data:
```yaml
# test-data/<feature>.yaml (base)
base_path: ''

# test-data/<feature>.<locale>.yaml (overlay)
base_path: '/en'
```

If Phase 3 detected query param: similar but with `query_suffix: ''` / `query_suffix: '?lang=en'` appended to each page value.

If Phase 3 detected UI switcher: do NOT modify Pages. Instead, write the storage delta from Phase 3.5 into `specs/generated/locale-config.json` (sibling of `specs/generated/base.ts` + `specs/generated/locale-fixture.ts`) (see 6f below).

**6f. Storage injection config — `specs/generated/locale-config.json` (sibling of `specs/generated/base.ts` + `specs/generated/locale-fixture.ts`)**

Always write `specs/generated/locale-config.json` (sibling of `specs/generated/base.ts` + `specs/generated/locale-fixture.ts`) with the verified storage delta from Phase 3.5/3.6. This file is consumed by `specs/locale-fixture.ts` (auto-generated alongside `specs/base.ts`) — the fixture wraps Playwright's context, calls `addInitScript` to seed sessionStorage / localStorage, and `addCookies` for any cookies, BEFORE the first page navigation. Without this file, `SUNGEN_ENV=<locale>` only swaps test-data overlay — the browser would still boot in base locale.

Schema (only include areas that actually changed):

```json
{
  "$schema": "sungen-locale-config-v1",
  "sessionStorage": {
    "saa-language-preference": "${SUNGEN_ENV}"
  },
  "localStorage": {},
  "cookies": [],
  "notes": "Detected by /sungen:locale on YYYY-MM-DD. App persists locale in sessionStorage; initial trigger uses ?language=<code> query param."
}
```

Notes on the schema:
- Use the literal placeholder `"${SUNGEN_ENV}"` (or `"{{SUNGEN_ENV}}"`) when the stored value equals the locale code itself. The fixture substitutes `process.env.SUNGEN_ENV` at runtime, so the same file works for `en`, `ja`, etc.
- Use a hardcoded literal (e.g. `"en_US"`) when the stored value is fixed regardless of which locale runs.
- `cookies[]` entries need either `domain` or `url`. Other fields (`path`, `httpOnly`, `secure`, `sameSite`) are optional and pass through to `context.addCookies()`.
- Do not commit auth tokens / session IDs into this file even if they showed up in the Phase 3.5 diff — they are noise. The skill's Phase 3.5 filter must drop them; if one slipped through, edit it out before applying.
- `notes` is free-form, informational; the fixture ignores it.

Multi-locale projects: the same `locale-config.json` works for every locale because the value templating uses `${SUNGEN_ENV}`. No per-locale file needed.

The file lives at `specs/generated/locale-config.json` (sibling of `specs/generated/base.ts` + `specs/generated/locale-fixture.ts`) (sibling of `specs/base.ts`). If a different output directory is used, mirror the existing `specs/base.ts` location.

**6e. Compile**

Run `sungen generate --screen <name>` (or `--flow`) and report any compile errors back to the user. The selectors changed so compile must succeed before run-test.

## Phase 7 — Hand off

Print summary:
- N selectors converted to `{{var}}`
- M base keys added to `test-data/<feature>.yaml`
- K overlay keys written to `test-data/<feature>.<locale>.yaml`
- Pages selectors updated: yes/no
- Locale-switching mechanism: URL prefix `/en` / query `?lang=en` / UI switcher / manual

Suggest:

```
/sungen:run-test <name> --env <locale>
```

## Multi-feature screens

If the screen has multiple `.feature` files (e.g. `home.feature` + `home-modal.feature`), repeat Phase 1 → Phase 6 for each feature file with its own selectors + test-data pair. Phase 2 + 3 (live capture + locale switch detection) run **once per screen** — the mechanism is the same. Phase 4 (text diff) runs per feature because each has its own scope of UI.

## What NOT to do

- Do not edit `.feature` files. Their `[Target]` labels are locale-stable keys — they are matched literally and must NOT be turned into `[{{var}}]`. Localised text lives in the selector entry's `name:`/`value:`, so only selectors + test-data need surgery.
- Do not write a separate selectors file per locale (`home.en.yaml`). One selectors file with `{{var}}` works across all locales — that's the whole point.
- Do not delete keys from the base `test-data/<feature>.yaml`. Always append. Existing tests rely on base values being complete.
- Do not run tests in this skill. Hand off to `/sungen:run-test` for execution.
- Do not commit. Hand off to user.

## Memory link

Related: [[saa-auth-blocker]] — Live mode will not work on SAA staging while the upstream Supabase `persistSession: false` issue is open. Default to Offline mode on SAA until that ships.
