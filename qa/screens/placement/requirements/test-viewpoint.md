# placement — Test Viewpoints

> DRAFT for QA review — written from `spec.md` and the app code. Not yet accepted
> (`sungen audit --screen placement --accept-viewpoint` is the QA's call).

## Priority Viewpoints

| VP | Priority | Reason |
|---|---|---|
| VP-UI-001 | High | A1 question with answer options is displayed first (FR-PL-01) |
| VP-LOGIC-001 | High | One answer keeps the test at A1 and moves on (FR-PL-02) |
| VP-NAV-001 | High | Leaving early keeps the placement card on Home (FR-PL-03) |
| VP-LOGIC-002 | High | Finishing saves the level and hides the card — manual, answers are random (FR-PL-04) |
