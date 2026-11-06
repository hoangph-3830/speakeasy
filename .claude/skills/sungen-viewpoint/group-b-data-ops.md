# GROUP B: DATA OPERATIONS

> The user creates, edits, or deletes a specific record in the system.

Patterns: 4. Create / Add · 5. Update / Edit · 6. Delete
See `SKILL.md` for the 4 Viewpoints, Shared Checks, and Security Tag Rules.

---

## 4. Create / Add

**Apply when**: the screen has a function to create a new record (an "Add", "Create", "New" button, or a link to a create form).

**Inherits**: Form & Inputs — field-level validation (required, format, maxlength, whitespace, real-time error clear). Generate those once here; this pattern adds the create-specific rules below.

**Shared checks applied**: XSS/Injection

---

### Tier 1 — @high

**[VP-LOGIC] Create behavior**

- Open the create form → all fields are empty (blank slate), no data cached from a previous open or a previous submit
- Submit the form with valid data → the record is created successfully, success feedback appears (toast and/or redirect), no error on the form
- The new record appears in the correct position in the list per the current sort rule (usually newest first)
- Submit success → toast shows {{success_message}} from test-data.yaml, auto-dismisses after a few seconds
- Cancel / navigate away while the form is dirty → confirmation dialog "Discard changes?"; choosing Discard → no record is created in the DB
- User clicks Save twice in a row before the response → only 1 record is created, no duplicate

**[VP-VAL] Unique constraints**

- Enter a value duplicating an existing unique field (e.g. email, employee code) → rejected, inline error right at that field
- Required fields empty → the form cannot submit, no record is created in the DB (Submit disabled, or an inline error at each field on Submit click — depends on impl)
- Enter the wrong format → inline error at the field, field highlighted (text comes from {{format_error}} in test-data.yaml)

**[VP-SEC]**

- POST request to create a record without authentication → 401/403, no record is created in the DB
- POST request to create a record beyond the user's privilege (lower role) → 403 Forbidden from the server

---

### Tier 2 — @normal + @low

**[VP-UI] Interface states**

- [@normal] Form opens: required fields have a visual indicator (asterisk, color), optional fields do not
- [@normal] Navigate away while the form is unchanged (pristine) → no confirmation dialog appears
- [@low] Autofocus: the cursor moves to the first field automatically when the form opens
- [@low] "Save & Add Another" (if the spec has it): save succeeds → the form resets to blank, no redirect

**[VP-VAL] Dependency**

- [@normal] Select Field A (parent dropdown) → Field B (child dropdown) updates its options accordingly
- [@normal] Fields with default values: the value is shown when the form opens, the user can clear it and enter another value

---

### ⚡ Cross-pattern interactions

- **+ Form & Inputs**: Apply the entire VP-VAL checklist of Form & Inputs (boundary values, real-time error clear)
- **+ File Upload**: A form with a file upload field → cannot submit if the upload is not finished
- **+ Modal/Dialog**: Form inside a modal → on submit success the modal closes, the background list auto-refreshes
- **+ Data Table**: The new record appears in the table in the correct position, the total count increases by 1

---

## 5. Update / Edit

**Apply when**: the screen has a function to edit an existing record (an "Edit" button, a pencil icon, or inline edit).

**Inherits**: Form & Inputs — field-level validation (required, format, maxlength, whitespace, real-time error clear). Generate those once here; this pattern adds the edit-specific rules below.

**Shared checks applied**: XSS/Injection

---

### Tier 1 — @high

**[VP-LOGIC] Edit behavior**

- Open the edit form → all fields are pre-filled with the correct current data from the DB (text, selected dropdown, date, radio, checkbox)
- Change data and save → the record is updated in the DB, the UI reflects the new data immediately without a full-page reload
- Save success → toast shows {{success_message}} from test-data.yaml, auto-dismisses after a few seconds
- Cancel / navigate away while the form is dirty → confirmation dialog "Discard changes?"; choosing Discard → the DB is unchanged, data stays at the old value
- Save when the form has no changes (pristine) → no redundant DB UPDATE: either the Save button is disabled, or a request is sent but the server returns a no-op (verify via the Network tab: no write operation)

**[VP-VAL] Validation on edit**

- Change a unique field to a value already used by another record → rejected, inline error at the field
- Leave a unique field of the same record unchanged → save succeeds, no self-duplicate error
- Clear a required field → the Submit button becomes disabled again (override if the spec validates on submit: an inline error appears at the field on Submit click)
- Readonly / identity fields (ID, created date, username) → cannot be edited, accept no click/input

**[VP-SEC]**

- PUT/PATCH request without authentication → 401/403, the DB is unchanged
- Edit a record belonging to another user/tenant (IDOR) → 403 Forbidden, the DB is unchanged
- Edit a record that has been deleted (stale link) → 404 Not Found

---

### Tier 2 — @normal + @low

**[VP-UI] Interface states**

- [@normal] Readonly fields: visually dimmed, cursor not-allowed, accept no input
- [@normal] Unsaved-changes indicator: the title or tab shows a "•" or "(unsaved)" mark while the form is dirty
- [@normal] Concurrent edit: the server returns 409 Conflict on save → the error message clearly states "data was changed by someone else", the user does not lose entered data and is guided to reload to see the latest version
- [@low] "Last updated by [user] at [time]": the username and timestamp match exactly the last edit in the DB

**[VP-VAL] Dependency on edit**

- [@normal] Field dependency works as in Create: changing Field A → Field B updates its options, without resetting unrelated fields

---

### ⚡ Cross-pattern interactions

- **+ Form & Inputs**: Apply the entire VP-VAL checklist of Form & Inputs
- **+ File Upload**: The current file is shown → the user can replace it (new upload) or remove it, no need to re-upload if unchanged
- **+ Modal/Dialog**: Edit form inside a modal → on submit success the modal closes, the background data refreshes immediately
- **+ Data Table**: The row in the table reflects the new data immediately after a successful edit, without reloading the whole table

---

## 6. Delete

**Apply when**: the screen has a function to delete a record (a "Delete" button, a trash icon, or a context-menu action).

**Shared checks applied**: (no default shared check)

---

### Tier 1 — @high

**[VP-LOGIC] Delete behavior**

- Click Delete → a confirmation dialog must appear (no immediate delete), the confirm button uses a warning color (red/orange)
- Confirm the delete → the record disappears from the UI immediately, a toast shows the delete success message
- Cancel the confirmation → the dialog closes, the record stays in the UI and in the DB, no API is called
- Delete the last record on the current page (pagination) → automatically go to the previous page, no blank page is shown
- Delete a record referenced by another record (foreign key) → blocked, error message states the name of the module still using this record, the record is not deleted

**[VP-VAL] Delete type**

- Soft delete (default): the record is hidden from the main list, still in the DB with the flag `is_deleted = true`, the direct URL → 404 (override if the spec uses hard delete: the record is fully removed from the DB)

**[VP-SEC]**

- DELETE request without authentication → 401/403, the record is not deleted from the DB
- DELETE a record belonging to another user/tenant (IDOR via API) → 403 Forbidden, the record is not deleted
- Directly access the URL of a deleted record (soft or hard) → 404 Not Found

---

### Tier 2 — @normal + @low

**[VP-UI] Interface states**

- [@normal] Confirmation dialog: a clear title "Delete [record name]?", a body explaining the action cannot be undone
- [@normal] Deleting (loading): the confirm button shows a spinner and is disabled to prevent a double-click
- [@normal] Delete button: visually a warning (red color or a warning icon), not placed next to Save to avoid mis-clicks
- [@low] Bulk delete (if the spec has it): select multiple records → one confirmation for all, count stated clearly "Delete 3 records?"

**[VP-VAL] Dependency**

- [@normal] Cascade delete (if the spec has it): delete a parent → the confirmation dialog lists how many children will be deleted with it
- [@normal] Set Null (if the spec has it): delete a parent → child records still exist, the reference field becomes "Unassigned" or null

---

### ⚡ Cross-pattern interactions

- **+ Data Table**: The record disappears from the table immediately, the total count decreases by 1, the pagination adjusts itself
- **+ Modal/Dialog**: The delete confirmation is a modal → close the modal after deleting, the background table refreshes
- **+ List/Card View**: The card/row disappears immediately after a successful delete, no page reload needed
- **+ Notification**: The delete-success toast shows {{delete_success_message}} from test-data.yaml
