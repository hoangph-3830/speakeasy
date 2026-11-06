---
name: sungen-test-design-techniques
description: 'Test design techniques (EP, BVA, Decision Table, State Transition) for systematic scenario generation from spec constraints. Auto-loaded by create-test command.'
user-invocable: false
---

## When to Apply

Apply selectively — not every screen needs all four techniques. Use the technique only when the spec provides the trigger condition.

| Technique | Apply when spec mentions | Skip when |
|---|---|---|
| EP (Equivalence Partitioning) | Input types, categories, roles, valid/invalid ranges | No validation or only a required-field check |
| BVA (Boundary Value Analysis) | Numeric range, string length, date range, count limit | No boundary defined in spec |
| Decision Table | 2+ mutually dependent conditions with different outcomes | Conditions are independent of each other |
| State Transition | Entity lifecycle, workflow states, status changes | No state machine in spec |

**Rule:** These techniques determine **how many** and **which** scenarios to generate. `sungen-viewpoint` determines **which viewpoints** to cover.

**Implementing the data table → `@cases` (data-driven):** when EP classes / BVA boundary triples /
decision-table rows share the *same step shape* and differ only by input/expected values, encode
them as ONE `@cases:<dataset>` scenario (each class/boundary/rule = one row in the test-data list,
labelled by a `case` column) instead of N near-duplicate scenarios. The technique still decides the
rows; `@cases` is how you write them compactly. See `sungen-gherkin-syntax` → Data-driven.

---

## 1. Equivalence Partitioning (EP)

**Goal:** One representative per input class. If one value in a partition passes, all values in that partition pass.

**How to apply:**
1. Extract partitions from `spec.md` constraints (e.g., field accepts 1-100)
2. Valid class: 1 <= value <= 100
3. Invalid class (below): value < 1
4. Invalid class (above): value > 100
5. Write **one** scenario per class

**Anti-pattern:**
```gherkin
# BAD — 3 scenarios, same class, same result:
Scenario: VP-VAL-001 Enter value 10
Scenario: VP-VAL-002 Enter value 50
Scenario: VP-VAL-003 Enter value 80
```
```gherkin
# GOOD — one representative per class:
Scenario: VP-VAL-001 Valid range value is accepted       # value = 50
Scenario: VP-VAL-002 Below minimum is rejected           # value = 0
Scenario: VP-VAL-003 Above maximum is rejected           # value = 101
```

---

## 2. Boundary Value Analysis (BVA)

**Goal:** Test exact edges where off-by-one errors occur (`>` vs `>=`, `<` vs `<=`).

### Two modes

| Mode | Values | Use when |
|---|---|---|
| **Compact (default)** | `min-1`, `min`, `max`, `max+1` | Most fields |
| **Full 6-point** | `min-1`, `min`, `min+1`, `max-1`, `max`, `max+1` | High-risk fields with `@high` priority |

**How to apply** (example: "quantity must be 1-10"):
- `min-1` = 0 -> invalid
- `min` = 1 -> valid (lower boundary)
- `max` = 10 -> valid (upper boundary)
- `max+1` = 11 -> invalid
- Midpoint (e.g., 5) already covered by EP valid class

**BVA scenarios** (example: quantity 1-10) — 4 TCs per range:
- `VP-VAL-010 Below minimum (0) is rejected`
- `VP-VAL-011 Minimum boundary (1) is accepted`
- `VP-VAL-012 Maximum boundary (10) is accepted`
- `VP-VAL-013 Above maximum (11) is rejected`

**Priority by field risk:**
- Critical field / inclusive operator (`<=`, `>=`) / business-rule boundary → `@high` (Tier 1)
- Standard field with a spec-defined range → `@normal` (Tier 2)

**Low-risk fields:** Apply EP only (valid class + invalid class) — skip BVA entirely. Reserve BVA for fields where off-by-one errors have user impact (quantity limits, file size, password length, date ranges).

---

## 3. Decision Table

**Goal:** Cover all condition combinations when 2+ conditions constrain each other.

**How to apply:** List conditions from `spec.md` → build combination→outcome table → one scenario per row.

**Cap:** When >3 boolean conditions (>8 rows), prioritize rows with **distinct outcomes** and add `@manual` for exhaustive combos.

**Example** — Submit requires valid form AND permission → 4 combos, 2 distinct outcomes:
- `@normal` Form invalid + no permission → disabled
- `@normal` Form valid + no permission → disabled
- `@normal` Has permission + form invalid → disabled
- `@high` Form valid + has permission → succeeds

**Tier mapping for DT rows:**
- Row with a **distinct outcome not covered by any other row** → Tier 1 `@high`
- Row whose outcome is already tested by another row (same result, different condition path) → Tier 2 `@normal`
- When >3 boolean conditions (>8 rows): keep all distinct-outcome rows as Tier 1, move redundant-outcome rows to Tier 2 or `@manual`.

---

## 4. State Transition

**Goal:** Verify every valid transition AND block invalid ones.

**How to apply:** Extract state diagram from `spec.md` → one scenario per valid transition + key invalid transitions.

**Example** — Order lifecycle (Draft→Pending→Approved→Completed):
- `@high` Valid: Draft → Pending, Pending → Approved, Approved → Completed
- `@normal` Invalid: Completed → Draft (blocked), Pending → Completed (skip approval)

**test-data:** Use named state keys (`order_in_draft`, `order_in_pending`).

**On mobile, three state machines exist that no spec draws** — apply this technique to them even
when `spec.md` has no diagram, because the states are imposed by the platform rather than the
product:

| Machine | States | Transition steps that compile |
|---|---|---|
| App lifecycle | foreground · backgrounded · relaunched · first-run · process-death | `background the app for N seconds` · `restart the app` · `reinstall the app`; process-death is `@manual:M10` |
| Screen entry / exit | entered by tap · entered by deep link · left by OS back · left by in-app control | `open deep link {{url}}` · `go back` (OS/hardware) · a tap on the in-app control — **the last two are different transitions**, since system back skips the screen's own handler unless the app wires it |
| Connectivity | online · offline · recovering | `turn wifi off` / back on → `@platform:android` (XCUITest cannot toggle connectivity) |

Full matrices and the permission axis → `sungen-tc-generation` → "Mobile — the two axes web has no
concept of". Per-pattern checklists → `sungen-viewpoint` → `group-f-mobile-native.md`.
