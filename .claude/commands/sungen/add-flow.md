---
name: add-flow
description: 'Add a new Sungen flow — scaffolds directories for E2E cross-screen testing, helps fill spec.md, and can capture visuals via the capture skills'
argument-hint: "[flow-name] [--path <start-url>] [--reach <steps>]"
allowed-tools: Read, Grep, Bash, Glob, Edit, Write, AskUserQuestion, mcp__playwright__browser_navigate, mcp__playwright__browser_take_screenshot, mcp__playwright__browser_snapshot, mcp__figma__get_design_context, mcp__figma__get_variable_defs, mcp__figma__get_screenshot, mcp__appium-mcp__select_device, mcp__appium-mcp__appium_session_management, mcp__appium-mcp__generate_locators, mcp__appium-mcp__appium_get_page_source, mcp__appium-mcp__appium_find_element, mcp__appium-mcp__appium_gesture, mcp__appium-mcp__appium_screenshot
---

You are adding a new Sungen flow for E2E cross-screen test generation.

## Platform detection (do this FIRST)

Read `qa/capabilities.yaml` and check the `platform` field.

- If `platform: mobile` → follow the **mobile path** in each step below.
- If `platform: web` or field absent → follow the **web path** (all steps unchanged).

## Parameters

Parse from `$ARGUMENTS`:
- **flow** — flow name (e.g., `award-submission`, `user-onboarding`)
- **--path \<url\>** — starting page URL path for web (default: `/login`). Not used on mobile.
- **--reach \<steps\>** — (mobile only) navigation recipe for the flow's first screen: a `>`-separated list of nav steps from app launch, e.g. `"tap [My Page] tab > tap [Settings] row"`. Scaffolded into the `Background:`. Ignored (with a warning) on web projects.
- **--description \<text\>** — flow description (optional)

If **flow** is missing, ask: "What is the flow name? (e.g., `award-submission`, `user-onboarding`)"
If **path** is missing (web) or **--reach** is missing (mobile), ask for it accordingly — e.g. "What is the starting URL path? (e.g., `/login`)" for web, or "How do you reach the first screen from app launch? (e.g., `tap [My Page] tab > tap [Settings] row`)" for mobile.

## Steps

### 1. Scaffold the flow

**Web:**
```bash
sungen add-flow --flow <name> --path <path>
```

**Mobile** (`platform: mobile` in `qa/capabilities.yaml`):
```bash
sungen add-flow --flow <name> [--reach "tap [My Page] tab > tap [Settings] row"]
# Directories are always qa/flows/<name>/ — there is no qa/mobile/ tree.
```

This creates:
```
qa/flows/<name>/
├── features/<name>.feature        # Gherkin with @flow tag, Background, sample scenarios
├── selectors/<name>.yaml          # Namespaced keys: "login:submit", "awards:submit"
├── test-data/<name>.yaml          # Namespaced data: login.email, submission.nominee
└── requirements/
    ├── spec.md                    # Flow specification
    └── ui/                        # Screenshots, mockups
```

### 1a. Define the flow's BOUNDARY, then its screens

**FIRST: look for a boundary the project has already decided.** Before applying the generic
checklist, search the spec/docs tree for a document that defines the flows —
`*System_Test*.md`, `*Flow*.md`, `BF-*`, a flow inventory, a test-plan section listing flows. If
one exists, **its boundary wins**: use the flows and scope it declares rather than re-deriving them
from a single user story.

This is not a preference, it is where a whole run went wrong. A project whose `System_Test_Flows.md`
defined `BF-B0 = Dashboard` as one flow covering six regions got a boundary re-derived from one
user story instead, narrowing the scope to "view dashboard" and pushing six of eleven requirements
out of the flow **before generation started**. Every later step — coverage map, audit, delivery —
then ran correctly on the wrong scope, and no gate could see it, because each of them measures
against the boundary rather than questioning it.

Apply the generic ISTQB checklist below only when NO project-specific boundary document exists.

> QA teams often call this level **System Test** — same thing: one fully-integrated business
> journey verified against the spec. Use whichever name the team knows; the boundary rules
> below are the ISTQB system-test design rules.

A flow is the **smallest complete business action chain**: one clear trigger ending in ONE
observable, valuable outcome. Before asking for screens, walk this checklist with the user —
if 1, 3 or 8 fails, propose SPLITTING into separate flows:

1. Exactly **one business goal**? (cart correctness + category filtering = two flows)
2. A clear **trigger** and precondition?
3. **One observable final outcome**? (a final assertion you can write in one sentence)
4. Is that outcome **valuable to the actor**? (an order placed, a password reset — not "a page rendered")
5. Is **every step necessary** for that outcome?
6. Are all steps at the **same business abstraction**?
7. Are optional/error branches **flows of this goal** (Alternate / Exception flows), not new goals?
8. Does **no segment** form an independently valuable flow on its own?
9. Can you write **a single clear final assertion**?
10. Can you name it "**Verb + outcome**"? (`place-order`, `reset-password` — not `cart-and-filter`)

Then ask: "Which screens does this flow visit, in order? (e.g., login → dashboard → award-form → confirmation)"

Record the screen list — you will need it for:
- Filling `spec.md` (Step 3)
- Suggesting `[Screen:Element]` namespace prefixes
- Capturing visuals per screen (Step 2)

### 1b. Author the Flow Contract (`requirements/flow-contract.yaml`)

Write the answers down as the flow's contract — `sungen audit` scores the flow **against it**
(the `flowCoverage` axis, per declared flow; `FLOW-OUTCOME-UNPROVEN` when no automated scenario
asserts data on the outcome screen; `FLOW-SCOPE-CREEP` when scenarios never touch it). The audit
also requires **both guarantees** and the **`flows:` inventory** on every contract —
`FLOW-GUARANTEE-MISSING` / `FLOW-INVENTORY-MISSING` otherwise, and the top mark is withheld — so
write them now rather than meet them after the spec and viewpoint are reviewed:

```yaml
goal: "Place an order for a product added from home"   # Verb + outcome
actor: user
trigger: "Add a product to the cart from the home featured list"
precondition: "A registered account; an empty cart"
outcome:
  screen: checkout            # the [Screen:...] namespace carrying the final proof
  assertion: "The confirmation shows the order number and the paid total"
value: "The customer has paid; the shop has a new order"
successGuarantee: "An order exists with the cart's lines and total; the cart is empty"
minimalGuarantee: "No order and no charge without a confirmation; the cart is unchanged"   # what Exception flows assert
phases: [BF, AF, EF]          # Basic / Alternate / Exception flows (default) — the first one must reach `outcome`
flows:                        # the inventory: every flow of the use case, each with a status
  - id: BF
    outcome: "The successGuarantee — order confirmed with its number and total"
    status: covered
  - id: AF01
    branchFrom: "BF step 3, the user edits the quantity in the cart"
    outcome: "Re-enters BF at step 3 with the new total"
    status: covered
  - id: EF01
    branchFrom: "BF step 4, the payment is declined"
    outcome: "The minimalGuarantee — no order, cart kept, an error shown"
    status: pending-clarification   # covered | deferred | pending-clarification | out-of-scope
    reason: "The decline message is not in the spec — asked the BA"
stateful: cart                # the mutated collection, if any — enables regression-depth dims
golden: true                  # optional — release-critical: Final Inspection expects @golden scenarios here
external:                     # optional — legs owned by another team/vendor (System INTEGRATION Testing)
  - name: payment-gateway
    owner: vendor-x
    screens: [payment]        # the flow namespaces that leg passes through
```

`phases: [HP, ER, EH]` (happy path / error recovery / guards) is the lighter vocabulary for a short
single-outcome flow — it still needs both guarantees and a `flows:` inventory. Enumerating the
flows (the step × risk matrix) is described in the `sungen-tc-generation` skill, flow section.

**A filled contract is an INPUT to generation — never an output.** Like `test-viewpoint.md`,
generation must not rewrite it to match what was generated; disagree → propose the diff and ask.

### 2. Capture visual source

**Mobile path** (`platform: mobile`):

Use `AskUserQuestion`: *"Capture a live screen via Appium MCP now?"*
- **Yes, capture now** (Recommended) — invoke the `sungen-capture-mobile` skill (Appium MCP) for the first screen of the flow. It replays the `--reach` nav recipe from the `Background:`, scans the locator tree, and saves a screenshot to `requirements/ui/`. Repeat for each screen in the flow as needed.
- **Skip** — user will capture later via `/sungen:run-test` or manually.

Do NOT invoke `sungen-capture` (web/Figma) for a mobile project — use `sungen-capture-mobile` only.

**Web path:**

Use `AskUserQuestion`: *"Pick a visual source for this flow's screens:"*
- **Figma designs** (Recommended for pre-launch) — invoke `sungen-capture` skill (mode figma-mcp) for each screen
- **Live page scan** (dev/staging is up) — invoke `sungen-capture` skill (mode live) for each screen URL
- **Local images** — invoke `sungen-capture` skill (mode local) to load from `requirements/ui/`
- **Skip** — user will drop images manually into `requirements/ui/` later

Each capture skill writes outputs into `qa/flows/<name>/requirements/ui/` and reports back a summary. Do not inline capture logic here — always delegate to the skill so behavior stays consistent with `/sungen:create-test`.

### 3. Fill spec.md

Use `AskUserQuestion`: *"Fill `spec.md` now? (You can reference the captured visuals)"* — offer **Yes, fill now (Recommended)** / **Skip, fill later**.

If yes → open `qa/flows/<name>/requirements/spec.md` and help the user fill:
- **Screens list** — ordered list of screens with URL paths
- **Flow steps** — what the user does at each screen
- **Transitions** — what triggers navigation between screens
- **Business rules** — cross-screen validation, state that persists
- **Test data** — what data is entered at each screen

Reference the captured visuals from Step 2 to suggest field names, form elements, and UI states.

### 4. Next steps

Tell the user what was created, then use `AskUserQuestion` to offer next steps:

- **`/sungen:create-test <name>`** — Create test scenarios for the flow (Recommended)
- **Done for now** — I'll come back later

If user picks `/sungen:create-test`, **you MUST use the Skill tool** to invoke it. Do NOT generate test cases yourself — the skill auto-loads `sungen-gherkin-syntax` and `sungen-tc-generation`.

## Key Rules

- Flows are **independent** from screens — own selectors, own test-data
- Selectors use `[Screen:Element]` namespace format with colon
- YAML keys must be **quoted** due to colon: `"login:submit":`
- Test data namespaced by phase: `login.email`, `submission.nominee`
- `@flow` tag required at feature level
- `Background:` should only contain the starting navigation — the URL path (web) or the `--reach` nav recipe (mobile)
- Each scenario = one phase of the journey; ids are `FL-<PHASE>-NNN` (`HP`/`ER`/`EH`, optional `UI`)
- One flow = ONE business goal with ONE observable outcome (`requirements/flow-contract.yaml`) — a segment with its own value is its own flow
- Mobile flows are tagged `@platform:mobile` and run via `/sungen:run-test <flow>` (WebdriverIO, not Playwright)
