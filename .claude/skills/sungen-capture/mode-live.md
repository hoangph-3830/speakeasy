# Capture mode: live

Navigate a running application, take **one accessibility snapshot** and **one screenshot**, and save them as visual context for test generation. Use when the app is live (dev, staging, or production with read-only access) and you want tests grounded in the actual rendered UI. Handles auth gracefully: if the page redirects to login, ask the user to sign in manually rather than injecting cookies.

## Prerequisites

- Playwright MCP connected.
- Dev/staging server reachable (or a public URL).
- `playwright.config.ts` exists at the project root (for `baseURL` fallback).

## Steps

### 1. Resolve target URL

1. `Live URL` field in `requirements/spec.md` (Overview section)
2. `baseURL` from `playwright.config.ts` + `URL Path` from `spec.md`
3. Neither → `AskUserQuestion`: *"Paste the full URL for the page to scan"*

### 2. Navigate

`browser_navigate` to the resolved URL.

### 3. Handle auth redirect

If the page redirects to a login route (URL contains `/login`, `/signin`, `/auth`, or content indicates a login screen):
1. Tell the user which login URL they landed on.
2. `AskUserQuestion`:
   - **I'll log in manually** — wait for confirmation, then re-navigate to the target URL
   - **Skip live scan** — switch to mode `local`
   - **Cancel**
3. **Never** inject cookies or localStorage via `browser_evaluate` / `browser_run_code`. Auth belongs to the user.

### 4. Snapshot

Take **ONE** `browser_snapshot`. This accessibility tree is the primary AI context — roles, names, text, structure that tc-generation uses to identify sections and fields.

### 5. Screenshot (recommended)

Take **ONE** `browser_take_screenshot` with `fullPage: true`. Save to `requirements/ui/live-<timestamp>.png`, where `<timestamp>` is `YYYYMMDD-HHMM` local time (e.g. `live-20260421-1430.png`).

### 6a. Verify unauthenticated redirect target (flow capture only)

When capturing for a **flow** with security scenarios (e.g. "unauthenticated user cannot access X"):
1. Open a **fresh incognito/unauthenticated** context (no storage state).
2. `browser_navigate` to the protected route.
3. Record the **actual redirect URL** — do NOT assume `/login`; it may be `/register`, `/`, etc.
4. Report the redirect target: *"Unauthenticated access to `/dashboard` redirects to `/register`"*.
5. The caller must use the **actual redirect URL** in Gherkin assertions, never an assumed one.

Skip if the flow has no security scenarios or the user says to skip.

### 6. Detect discrepancies vs spec

If `spec.md` exists, cross-check the snapshot against spec sections: fields in spec but not in snapshot → *missing in UI*; elements in snapshot but not in spec → *missing in spec*. Report findings but **do not** auto-edit `spec.md`.

### 7. Report back

> Captured live page `<URL>`: Snapshot N interactive elements · Screenshot `requirements/ui/live-<timestamp>.png` · Discrepancies vs spec: <count or "none">

Hand back to the calling command. Scans **exactly one** page per invocation.
