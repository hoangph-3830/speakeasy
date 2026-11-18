# home — Test Viewpoints

> DRAFT for QA review — written from `spec.md` and the app code. Not yet accepted
> (`sungen audit --screen home --accept-viewpoint` is the QA's call).

page-type: mobile-home

## Priority Viewpoints

| VP | Priority | Reason |
|---|---|---|
| VP-ONB-001 | High | First launch must ask for the learner's name (FR-HOME-01) |
| VP-ONB-002 | High | A saved name is shown in the greeting (FR-HOME-02) |
| VP-ONB-003 | High | "Để sau" closes the dialog without a name (FR-HOME-03) |
| VP-ONB-004 | High | Choosing "Để sau" holds when the learner returns to Home (FR-HOME-03) |
| VP-ONB-005 | High | A saved name survives an app restart (FR-HOME-02) |
| VP-UI-001 | High | Goal card, continue button and shortcut row are displayed on load |
| VP-LOGIC-001 | High | New learner starts at 0 / 30 XP (FR-HOME-04) |
| VP-LOGIC-002 | High | Continue card targets the first path topic and opens it (FR-HOME-05) |
| VP-LOGIC-003 | Normal | "Theo chủ đề" shows category chips and topic rows (FR-HOME-08) |
| VP-NAV-001 | High | Placement card opens the placement test (FR-HOME-07) |
| VP-NAV-002 | High | Review shortcut opens Review (FR-HOME-06) |
| VP-NAV-003 | High | Vocabulary shortcut opens the vocabulary hub (FR-HOME-06) |
| VP-NAV-004 | High | Pronunciation shortcut opens the lab (FR-HOME-06) |
| VP-NAV-005 | High | Stories shortcut opens the stories hub (FR-HOME-06) |
| VP-NAV-006 | High | News shortcut opens the news tab (FR-HOME-06) |
| VP-NAV-007 | High | Video shortcut opens the video hub (FR-HOME-06) |
| VP-NAV-008 | High | Bottom tabs switch between the main screens (FR-HOME-09) |

## Known Issues

- DEF-001 — the name dialog asks again after "Để sau" once the learner leaves Home and comes back.
