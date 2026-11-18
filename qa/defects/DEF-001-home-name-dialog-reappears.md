---
id: DEF-001
title: Name dialog asks again after "Để sau" when the learner comes back to the Home tab
severity: minor
status: open
units: [home]
scenarios: ["VP-ONB-004 Home tab shows Home again after choosing Later"]
---

# DEF-001 — Name dialog reappears after "Để sau"

## Steps
1. Fresh install, open the app. The "Chào bạn mới!" dialog asks for a name.
2. Tap **Để sau**.
3. Open the **Tiến độ** tab, then tap the **Học** tab again.

## Expected
The dialog stays closed: the learner already chose to answer later (FR-HOME-03).

## Actual
The dialog opens again every time Home is shown, until a name is saved.

## Cause
`HomeScreen.kt` keeps the choice in `remember { mutableStateOf(progress.name.isEmpty()) }`.
Switching tabs disposes Home, so the next visit starts from `name.isEmpty()` again.
Remembering the choice for the session (or persisting "asked once") would fix it.

## Evidence
Automated: `qa/screens/home` → `VP-ONB-004` (`@known-defect:DEF-001`), red on Android emulator API 35.
