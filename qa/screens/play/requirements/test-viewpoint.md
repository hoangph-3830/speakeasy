# play — Test Viewpoints

> DRAFT for QA review — written from `spec.md` and the app code. Not yet accepted
> (`sungen audit --screen play --accept-viewpoint` is the QA's call).

## Priority Viewpoints

| VP | Priority | Reason |
|---|---|---|
| VP-UI-001 | High | Challenges listed (FR-PY-01) |
| VP-NAV-001 | High | Race starts at score 0 with options (FR-PY-02) |
| VP-NAV-002 | Normal | Memory challenge opens (FR-PY-03) |
| VP-NAV-003 | Normal | True/false opens (FR-PY-04) |
| VP-NAV-004 | Normal | Listen & spell shows the typing field (FR-PY-05) |
| VP-NAV-005 | Normal | Back returns to Play (FR-PY-06) |
| VP-NAV-006 | High | Write mode shows a prompt + field (FR-PY-07) |
| VP-VAL-001 | High | Check disabled until typed (FR-PY-08) |
| VP-VAL-002 | Normal | Whitespace-only answer counts as empty (FR-PY-08) |
| VP-LOGIC-001 | High | Submit shows model sentence + score (FR-PY-09) |
| VP-LOGIC-002 | Normal | Hint reveals the start (FR-PY-10) |
