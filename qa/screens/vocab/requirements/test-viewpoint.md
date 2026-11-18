# vocab — Test Viewpoints

> DRAFT for QA review — written from `spec.md` and the app code. Not yet accepted
> (`sungen audit --screen vocab --accept-viewpoint` is the QA's call).

## Priority Viewpoints

| VP | Priority | Reason |
|---|---|---|
| VP-UI-001 | High | Hub displays the word total and search (FR-VC-01) |
| VP-LOGIC-001 | High | English search lists the meaning (FR-VC-02) |
| VP-LOGIC-002 | Normal | Vietnamese search finds the English word (FR-VC-03) |
| VP-VAL-001 | Normal | Unknown word shows the not-found message (FR-VC-04) |
| VP-NAV-001 | High | Group shows words and learned count (FR-VC-05) |
| VP-LOGIC-003 | High | Marking learned raises the count (FR-VC-06) |
| VP-LOGIC-004 | High | Learned words persist after restart (FR-VC-07) |
| VP-LOGIC-005 | High | Add-to-review reports the cards added (FR-VC-08) |
| VP-LOGIC-006 | High | Review serves 30 cards per session (FR-VC-09) |
| VP-LOGIC-007 | Normal | Flipped card shows grades + intervals (FR-VC-10) |
| VP-LOGIC-008 | High | Nhớ removes the card from today's queue (FR-VC-11) |
| VP-LOGIC-009 | High | Quên keeps the card in today's queue (FR-VC-12) |
