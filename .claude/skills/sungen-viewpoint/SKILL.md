---
name: sungen-viewpoint
description: '32 UI patterns (17 web + 15 mobile-native) x 4 viewpoints — structured
  checklist for test case generation and review. Auto-loaded by create-test and review commands.'
user-invocable: false
---

## How to use this skill

This skill is a **router**. The detailed checklists live in 6 group files — load only the ones relevant to the screen under test. Groups A–E are the 17 platform-agnostic patterns; Group F is the mobile-native layer, read IN ADDITION on a `@platform:mobile/android/ios` unit.

1. Read the **4 Viewpoints** and **Shared Checks** below (always).
2. Identify which UI patterns the screen contains, resolve any overlap via **Pattern selection** below, then read **only** the matching group file(s) from the routing table.
3. Generate Tier 1 (`@high`) scenarios first, then Tier 2 (`@normal` + `@low`). Apply each Shared Check **once per screen**, not once per pattern.

> All checklist items are written in English. Render scenario names, step text, and test IDs in English.

## Routing table

| UI element on the screen | Pattern | Read file |
|---|---|---|
| Plain input form (settings, profile, contact) | 1. Form & Inputs **(base)** | `group-a-data-entry.md` |
| File picker / drop zone | 2. File Upload | `group-a-data-entry.md` |
| Bulk import / export | 3. Import / Export | `group-a-data-entry.md` |
| "Add" / "Create" / "New" | 4. Create / Add | `group-b-data-ops.md` |
| "Edit" / pencil icon / inline edit | 5. Update / Edit | `group-b-data-ops.md` |
| "Delete" / trash icon | 6. Delete | `group-b-data-ops.md` |
| Rows + columns grid | 7. Data Table | `group-c-data-explore.md` |
| Search box / search bar | 8. Search | `group-c-data-explore.md` |
| Filter controls (dropdown, date range, checkboxes) | 9. Filter | `group-c-data-explore.md` |
| Card / list grid, infinite scroll, "Load More" | 10. List / Card View | `group-c-data-explore.md` |
| Charts / KPI cards / dashboard | 11. Chart / Analytics | `group-d-display.md` |
| Overlay panel on top of the page | 12. Modal / Dialog | `group-d-display.md` |
| Side menu / tabs / breadcrumb / top nav | 13. Navigation | `group-d-display.md` |
| Toast / snackbar / alert / banner | 14. Notification / Toast / Alert | `group-d-display.md` |
| Login form / logout button | 15. Login / Logout | `group-e-identity.md` |
| Sign-up form / SSO | 16. Register | `group-e-identity.md` |
| Forgot / reset / change password | 17. Password Management | `group-e-identity.md` |
| **Native app screen (`@platform:mobile/android/ios`) — read Group F IN ADDITION to the patterns above** | | |
| Sheet sliding up from the bottom edge | 18. Bottom Sheet | `group-f-mobile-native.md` |
| Persistent bar of 3–5 destinations at the bottom | 19. Tab Bar / Bottom Navigation | `group-f-mobile-native.md` |
| Horizontal swipe on a row reveals actions | 20. Swipe-to-Delete Row | `group-f-mobile-native.md` |
| Field opening an OS wheel / date / dropdown picker | 21. Native Picker | `group-f-mobile-native.md` |
| Runtime permission — camera, location, notifications | 22. Permission Dialog | `group-f-mobile-native.md` |
| Fingerprint / Face ID / app lock | 23. Biometric Prompt | `group-f-mobile-native.md` |
| Leaves the app for the system camera or photo library | 24. Camera / Gallery Picker | `group-f-mobile-native.md` |
| Push messages / notification routing | 25. Push Notification | `group-f-mobile-native.md` |
| External link, notification payload, or QR opens a screen | 26. Deep-link Entry | `group-f-mobile-native.md` |
| Offline banner, cached content, retry control | 27. Offline Banner | `group-f-mobile-native.md` |
| First-launch intro slides / tutorial | 28. Onboarding Carousel | `group-f-mobile-native.md` |
| Feed reloading by drag-down, paging, Load More | 29. Pull-to-Refresh List | `group-f-mobile-native.md` |
| Any text field — the soft keyboard covers the screen | 30. Soft-Keyboard Overlap | `group-f-mobile-native.md` |
| List of switches / preferences | 31. Settings Toggle List | `group-f-mobile-native.md` |
| Every mobile screen (background, rotate, relaunch) | 32. App Lifecycle & Orientation | `group-f-mobile-native.md` |

## Pattern selection (precedence & inheritance)

A screen often matches several patterns at once — a login screen is *both* a form and an authentication flow. Use these rules so the choice is deterministic and scenarios are never duplicated:

1. **Most specific wins.** Pick the most specialized pattern as the screen's primary section. Auth and CRUD forms route to their specific pattern, NOT to Form & Inputs:
   - Login/logout → **15**, sign-up → **16**, forgot/reset/change password → **17**
   - Create form → **4**, edit form → **5**
2. **Form & Inputs (1) is a BASE pattern, not a sibling.** Generate it as its own section only for a plain form with no more-specific role (settings, profile, contact). When a specialization applies, do NOT also create a separate "Form & Inputs" section.
3. **Inheritance.** A specialized form pattern (4, 5, 15, 16, 17) **inherits** Form & Inputs field-level validation (required, format, maxlength, whitespace, real-time error clear) and adds its own rules. Apply the inherited checks inside the specialized section — generate each check once, never twice.
4. **Genuinely parallel pairs** — these cover different concerns; choose per the table:

   | If the screen has… | Decision |
   |---|---|
   | A grid of records | Pick **7. Data Table** *or* **10. List/Card** by layout (rows+columns → Table; cards/tiles/infinite-scroll → List/Card) — not both for the same surface |
   | Both a keyword box and filter controls | Apply **8. Search** *and* **9. Filter** (Search = free-text match; Filter = structured narrowing) + one combined AND-logic scenario |
   | A form rendered inside an overlay | Apply the form's pattern (1/4/5/15…) for fields/submit **and** **12. Modal/Dialog** for open/close/focus-trap/backdrop |

5. **On mobile, Group F is ADDITIVE — never a replacement.** A native screen is still whatever it functionally is: a native login screen is **15**, a native product list is **10**, and the harness routes such a screen to `auth` / `ecommerce-list` deliberately, not to a `mobile-*` page-type. Route by function first (1–17), then add the Group F patterns for the native surfaces present, and generate each check **once** — a native form is **1. Form & Inputs** *and* **30. Soft-Keyboard Overlap**, with the field rules living only in pattern 1. `group-f-mobile-native.md` carries the theme↔catalog map and the precedence detail.

## 4 Viewpoints

| VP | Focus | Tag |
|---|---|---|
| **UI/UX** | Interface state, layout, visual feedback | VP-UI |
| **Data & Validate** | Input constraints, data integrity, error messages | VP-VAL |
| **Logic** | Business rules, interactions, state changes | VP-LOGIC |
| **Security** | Authentication, authorization, injection | VP-SEC |

**Classification rules:**
- VP-UI = state that is always true regardless of what the user does (element present, layout, label)
- VP-VAL = outcome depends on the input *value* (valid / invalid / boundary)
- VP-LOGIC = outcome depends on the user's *action* (click, submit, navigate)
- VP-SEC = checks access control and malicious input

### Domain category codes — required for the coverage-balance gate

The 4 viewpoints above are the *generic* axes. On a domain screen, the `VP-<CAT>` code must use the **canonical short code** for what the scenario exercises, so the audit's coverage-balance gate buckets it correctly. Use these exact codes — **never long-form or freeform** (`VP-NAV` not `VP-NAVIGATION`, `VP-SUB` not `VP-SUBSCRIPTION`, `VP-FILTER` not `VP-FILTERING`):

| Bucket | Codes | Use for |
|---|---|---|
| **business-core** | `LIST` · `CART` · `PRODUCT` · `FILTER` · `CHECKOUT` · `ORDER` | the screen's core domain data/actions (product list, cart, checkout, order, filtered results) |
| presentation | `UI` | layout / visual state |
| validation-security | `VAL` · `SEC` · `SUB` | input validation · access/injection · subscribe/newsletter |
| behavior | `LOGIC` | action-driven state changes |
| navigation | `NAV` | landing on / moving between pages |

**On a business-core page** (product list, cart, checkout, search results), the core data scenarios MUST carry a **business-core** code (`VP-LIST-*`, `VP-CART-*`, `VP-PRODUCT-*`, …) — not a generic `VP-UI`/`VP-LOGIC` or a freeform `VP-<word>`. A freeform/long-form prefix parses as `NONE`, scores **0 on the balance axis**, and drops the audit score (~9.3 → ~7.7 in practice). Keep `VP-UI/VAL/LOGIC/SEC` for the cross-cutting checks; give the domain scenarios their domain code.

---

## Shared Checks

Generate **once per screen**, do not repeat for each pattern.
Each pattern only points back with "Shared checks applied: [name]".

The `Platform` column decides which checks a screen owes. Applying the web three to a native
screen is wasted work — a `<script>` payload cannot execute in a native text view and there is no
URL to manipulate — while the native surfaces (deep-link params, clipboard, the recents thumbnail)
go unchecked unless they are named here.

| Check | Condition → Expected | VP | Priority | Platform |
|---|---|---|---|---|
| **Loading State** | Data fetch in progress → spinner/skeleton shown, user cannot interact | UI | @normal | both |
| **Empty State** | Query returns 0 records → clear message shown, layout does not break | UI | @normal | both |
| **XSS** | Script tag entered into a field → rendered as literal text, not executed | SEC | @high | web |
| **SQL injection** | SQL payload entered into a field → DB unaffected, no data exposed | SEC | @high | web |
| **URL Manipulation** | URL params wrong/missing/out-of-range → fallback to default, no 500 crash | SEC | @high | web |
| **Deep-link param injection** | An external link carries a traversal / script / oversized param → treated as data, no crash, no privileged screen reached | SEC | @high | mobile |
| **Clipboard leak** | A field holding sensitive data (password, token, card) → the value cannot be copied out, or is excluded from the clipboard | SEC | @high | mobile |
| **Recents / screenshot leak** | Background the app on a screen showing sensitive data → the app-switcher thumbnail is masked, screenshots blocked where the spec requires it | SEC | @high | mobile |
| **Root / jailbreak posture** | The app runs on a rooted/jailbroken device → the spec's response (warn, degrade, or block), never a silent full-trust run | SEC | @normal | mobile |
| **Certificate pinning** | Traffic through an intercepting proxy with a substituted certificate → the app refuses the connection rather than transmitting | SEC | @high | mobile |
| **Permission over-request** | Cold launch → only the permissions the current feature needs are requested, and each is asked in context | SEC | @normal | mobile |

> **SQL injection — 2 layers for search/LIKE fields**: (1) field-level: UI blocks special chars → `@high` automated; (2) API-level: if the field reaches a LIKE query (search, partial-match), send `1 OR 1=1` straight to the API endpoint (bypassing the UI) → verify a parameterized query is used → `@high @manual`. Missing layer 2 = a real attack vector is overlooked even when field validation is correct.

> **Two mobile edges the table simplifies.** A screen rendered inside a **WebView** is web for these
> purposes — apply XSS there. And a native app hits the same API as the web client, so the API-level
> SQL-injection layer above is still owed by the *system*; it is just not a screen-unit check on a
> mobile unit. Record it once at the API layer (`qa/api/`), not per mobile screen.

---

## Security Tag Rules

For VP-SEC scenarios testing **unauthorized access** (no login, wrong role, direct URL):
- Use the **`@no-auth`** tag — runs without authentication to verify the redirect/block.
- Do NOT use `@manual` for these — they are automatable.

On **web** the redirect target is a URL, so asserting the page is a real check:

```gherkin
@high @no-auth
Scenario: VP-SEC-001 Unauthenticated user cannot access admin page
  Given User is on [Admin] page
  Then User see [Login] page
```

On **mobile** that same shape is a false pass. A native app has no URL: the assertion compiles to an
empty comment and reports **SG-W022**, so the scenario goes green having proven nothing. Assert a
marker element that only exists on the login screen, plus the protected screen's own marker going
`is hidden`:

```gherkin
@high @no-auth @platform:mobile
Scenario: VP-SEC-001 An unauthenticated launch stops at the login screen
  Given User is on [Login] screen
  Then User see [Login Submit] button
  And User see [Account Name] is hidden
```

Use `@manual` only when the environment truly cannot be set up automatically.
