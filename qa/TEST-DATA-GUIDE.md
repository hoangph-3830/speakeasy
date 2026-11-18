# Test Data Guide

Every `{{variable}}` in a `.feature` file maps to a key in that unit's
`test-data/*.yaml`. When you create or edit a value, decide which of the
**three kinds** it is — each kind is handled differently.

## 1) INVENTED — made-up values that only probe the screen

**What it is:** the value exists only to trigger a reaction (an error message, a
boundary, a rendering) — the backend does not need to accept it as real data.

**Examples:** a wrong-format email `"abc@"`; a 6-char password to trigger the
"minimum 7 characters" error; a 256-char string to test a 255 limit; an XSS
payload `<script>alert(1)</script>`; the exact expected error message from the spec.

**What to do:** write the value directly in the yaml. Nothing else.

**Self-check:** *"If I swapped this for another value of the same kind, would the
test still mean the same thing?"* → yes = INVENTED.

**Email domains — never a live mailbox provider.** An invented address still gets
mail sent to it: a registration or password-reset test submits it and the system
dispatches for real. `@gmail.com`, `@yahoo.com`, `@outlook.com` and friends all
resolve, so that mail reaches whoever owns the address — a stranger, or someone's
personal account. Use the project domain `@sun-asterisk.com`, or `@example.com`
(RFC 2606 — reserved, guaranteed never to deliver). Make each run unique with
`{{$timestamp}}` in the local part: `"signup+{{$timestamp}}@sun-asterisk.com"`.

## 2) ENV-BOUND — real records of the test environment (not secret)

**What it is:** the value must EXIST in the environment for the test to work — a
seeded record id, a registered email, a valid invite token — but leaking it
causes no harm.

**Examples:**
```yaml
active_user_id: "d4f8-001"              # ENV-BOUND: seeded user on staging
existing_email: "tester01@example.com"  # ENV-BOUND: pre-registered account
```

**What to do:** keep it in the yaml and mark the line with `# ENV-BOUND: ...` so
the next person knows it must be re-seeded/replaced per environment. When the
value differs per environment, override it in `<unit>.<env>.yaml`
(e.g. `login.staging.yaml`) and run with `SUNGEN_ENV=staging`.

**Self-check:** *"If someone deleted this record from the DB, would the test
fail?"* → yes = ENV-BOUND.

## 3) SECRET — real credentials; harmful if leaked

**What it is:** values that grant real access or cause harm when exposed — a
real account password, an API key, a system token.

```yaml
# ❌ WRONG — the real password lives in git; anyone who clones the repo reads it:
admin_password: "Sun@2026"

# ✅ RIGHT — the yaml holds only the variable name; the real value stays in
#    .env.qa, which is gitignored:
admin_password: ${QA_ADMIN_PASSWORD}
```

and in `.env.qa` (NOT committed — copy `.env.qa.example` and fill it):

```bash
QA_ADMIN_PASSWORD=Sun@2026
```

**Rules that matter:**
- The variable name MUST start with `QA_` and be UPPERCASE — only `${QA_...}`
  is replaced at runtime. Any other `${...}` text is treated as ordinary
  characters (so XSS payloads / template strings containing `${` are safe).
- Whenever you add a new `${QA_*}` reference, also add the NAME to
  `.env.qa.example` — that file is the committed checklist of variables each
  environment must provide.
- Delivery reports (CSV/Excel) and the dashboard always print `${QA_ADMIN_PASSWORD}`
  literally — secrets never enter deliverables.
- Per environment: `.env.qa.<env>` overlays `.env.qa` (run with `SUNGEN_ENV=<env>`);
  a variable exported in the shell/CI beats both files.
- If a referenced `QA_*` variable is missing at run time, the test that uses it
  fails immediately with the variable name — check `.env.qa`.

**Self-check:** *"Could sharing this file outside the team cause harm?"* → yes = SECRET.

## Easy to confuse

- **"password" is not automatically SECRET.** `password_below_min: "Aa1bcd"` used
  to trigger a validation error is INVENTED — make it up. Only a password that
  actually logs in to a real account is SECRET.
- **Testing screens behind login usually needs NO password at all** — run
  `sungen makeauth` once: it opens a browser, you log in by hand, and the saved
  session (storageState) is reused by `@auth:<role>` scenarios.

## Quick reference

| Kind | Recognize by | Where the value lives |
|---|---|---|
| INVENTED | backend need not accept it | the yaml, as-is |
| ENV-BOUND | must exist in the environment; harmless if leaked | the yaml + `# ENV-BOUND:` marker (+ `<unit>.<env>.yaml` overlay) |
| SECRET | real access / harmful if leaked | `${QA_*}` in the yaml → real value in `.env.qa` (gitignored), name listed in `.env.qa.example` |
