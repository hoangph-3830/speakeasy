# GROUP A: DATA ENTRY

> The user feeds data into the system — field by field, file by file, or in bulk.

Patterns: 1. Form & Inputs · 2. File Upload · 3. Import / Export
See `SKILL.md` for the 4 Viewpoints, Shared Checks, and Security Tag Rules.

---

## 1. Form & Inputs

> **Base pattern.** The field-level validation here is inherited by Create (4), Update (5), Login (15), Register (16), Password (17). Generate this as a standalone section only for a plain form with no more-specific role (settings, profile, contact) — see *Pattern selection* in `SKILL.md`.

**Apply when**: the screen has at least 1 input field and 1 submit button that sends data to the server, and no more-specific pattern (4/5/15/16/17) applies.

**Shared checks applied**: XSS/Injection

---

### Tier 1 — @high

**[VP-LOGIC] Business behavior**

- All required fields empty → Submit button is disabled, no request is sent on click
- All required fields filled validly → Submit button becomes enabled and clickable
- Submit the form with valid data → success feedback appears (toast/message/redirect), data is saved to the system
- Submit the form → server returns an error (5xx, business error) → error message appears, all entered data is preserved on the form
- User clicks submit twice in a row before the response arrives → only 1 request is sent, no duplicate record is created
- Field A value changes → Field B updates its options accordingly immediately (field dependency; override if the spec uses field visibility: Field B is shown/hidden instead of changing options)

**[VP-VAL] Input validation**

- Submit while a required field is empty (applies if Submit is not disabled — Pattern B) → error message appears right at that field, not only a generic toast
- Enter beyond max length → field accepts no more characters (HTML maxlength default; override if the spec uses server-side: input accepted but rejected on submit with an error stating the limit)
- Enter the wrong format (email, phone, date…) → error message appears at the field, field is highlighted (text comes from {{format_error}} in test-data.yaml)
- User fixes the field after an error → the error clears immediately once the value becomes valid (real-time clear)
- Input containing only whitespace → treated as empty, submit shows the required error (override if the spec requires auto-trim and accept)

**[VP-SEC]** → Apply Shared Check: XSS/Injection

---

### Tier 2 — @normal + @low

**[VP-UI] Interface states**

- [@normal] Required fields have a visual indicator (asterisk, color, "Required" label)
- [@normal] Disabled fields: visually dimmed, cursor not-allowed, accept no input
- [@normal] Focused field: border/outline highlight is clear, distinguishable from an unfocused field
- [@low] Tab order: the Tab key moves focus in a logical order, top to bottom, left to right
- [@low] Placeholder text: shows the correct hint text per spec, disappears once the user starts typing
- [@low] Character counter (if the spec has one): shows the remaining character count, changes color near or at the limit

**[VP-VAL] Boundary values**

4 TCs per range (min-1, min, max, max+1). Default tag = @normal (Tier 2).
**Override to @high (Tier 1)** when the field is on a critical business rule or the spec uses inclusive operators (`<=`, `>=`).

- [@normal] Exactly the min value → accepted, no error
- [@normal] Exactly the max value → accepted, no error
- [@normal] Min - 1 unit → rejected, appropriate error
- [@normal] Max + 1 unit → rejected, appropriate error

---

### ⚡ Cross-pattern interactions

- **+ Modal/Dialog**: Form inside a modal → on submit success the modal closes, background data updates immediately without a full-page reload
- **+ Create/Add**: Apply this entire checklist. Add: the form opens as a blank slate, no data cached from a previous submit or a previous open
- **+ Update/Edit**: Apply this entire checklist. Add: the form pre-fills the correct current data from the DB
- **+ Notification**: Submit success → toast shows {{success_message}} from test-data.yaml, auto-dismisses after a few seconds

---

## 2. File Upload

**Apply when**: the screen has a component letting the user upload a file (image, PDF, video, document…). It can be standalone or a field within a larger form.

**Shared checks applied**: XSS/Injection (via file name)

---

### Tier 1 — @high

**[VP-LOGIC] Upload behavior**

- Upload a file of the right type and size → upload succeeds, file name/thumbnail/preview appears in the UI
- Upload in progress → progress indicator appears, the form's submit button is disabled until the upload completes
- Upload fails (network error, server timeout) → error message states the reason clearly, the user can retry
- User removes an uploaded file → the file is removed from the form and deleted from the server immediately (override if the spec uses lazy delete: only deleted on form submit)
- Multiple files (if the spec allows) → each file has its own upload progress and status

**[VP-VAL] File constraints**

- Upload a file with a disallowed extension (not in the whitelist) → rejected immediately, error lists the allowed types
- Upload a file exceeding the size limit → rejected, error states the limit and the actual size of the selected file
- Submit the form without uploading a file (field required) → "file required" error appears, the form does not submit
- Upload an empty file (0 bytes) → rejected with an appropriate error

**[VP-SEC]**

- Upload a file with a valid extension but whose real MIME type is executable (file-type spoofing) → server validates the real MIME type and rejects it
- File name containing a path-traversal string (`../../etc/passwd`, `../config`) → file name is sanitized, not executed, no path is exposed
- Upload without authentication → 401/403, the file is not stored on the server

---

### Tier 2 — @normal + @low

**[VP-UI] Interface states**

- [@normal] Drop zone or "Choose file" button is clearly visible and easy to recognize
- [@normal] The list of accepted file types and the size limit appear in the UI (helper text or tooltip)
- [@normal] During upload: progress bar or percentage is shown, a cancel button is available
- [@normal] Upload success: thumbnail (image) or file icon + file name + size are clearly shown
- [@normal] Multiple upload with a limit: a "2/5 files" counter shows, the upload button hides/disables when the limit is reached
- [@low] Drag & drop: dragging a file over the zone → drop zone highlights, upload starts automatically on drop

**[VP-VAL] Edge cases**

- [@normal] Upload a file with the same name as an existing one → rejected, error "File already exists" (override if the spec defines overwrite or auto-rename to file(1))
- [@normal] File name with special characters or Unicode (accented Vietnamese) → upload succeeds, the name is displayed correctly

---

### ⚡ Cross-pattern interactions

- **+ Form & Inputs**: Upload is a field in the form → the form cannot submit if the upload is incomplete or failed
- **+ Create/Add**: The created record includes the correct file reference, the file is accessible after reopening the record
- **+ Update/Edit**: The current file is shown → the user can replace it (new upload overwrites the old) or remove it separately
- **+ Modal/Dialog**: Upload inside a modal → the modal does not close while uploading, progress is not lost
- **+ Notification**: Upload success/fail → toast shows the correct message and the correct type (success/error)

---

## 3. Import / Export

**Apply when**: the screen has a function to bulk-import data from a file (CSV, Excel) or export the current data to a file.

**Shared checks applied**: Loading State

---

### Tier 1 — @high

**[VP-LOGIC] Import behavior**

*No preview:*
- Upload a valid file (correct format, all required columns, valid data) → import succeeds, the number of imported records is clearly shown
- After import → new data appears in the list/table immediately without a reload (override if the spec requires a manual reload)
- Large file (many records, async processing) → UI shows "processing", the user gets a notification when done

*With preview:*
- Upload a file → a preview table appears with the parsed data, the user sees it before confirming
- Preview shows inline errors for each invalid row: highlights the row, states the column and the reason
- User confirms after viewing the preview → valid records are imported, invalid rows are skipped with a row-level error summary (override if the spec requires rejecting all when any row is invalid)
- User cancels at the preview step → no record is created, the system returns to the state before the upload

*Export:*
- User clicks Export → the file download starts automatically, format CSV (override if the spec uses XLSX or another format)
- Export with an active filter → the file contains only the filtered data, not the entire dataset
- Export success → the file downloads without navigating the user away from the current page

**[VP-VAL] File & data validation**

- Import a file in the wrong format (not CSV/Excel, wrong delimiter) → rejected, error states the required format with an example
- Import a file missing required columns → rejected, error lists the missing column names
- Import a file with a wrong column header name → rejected, error lists the required column names (override if the spec allows partial import ignoring unknown columns)
- Import records with a key that already exists → skip that row, count it under "Y skipped" in the result summary (override if the spec requires overwrite or rejecting the whole file)
- Import a file with invalid data in cells (wrong type, over max length) → row-level errors state the row number, column, and reason

**[VP-SEC]**

- Import an Excel file containing formula injection (`=HYPERLINK(...)`, `=CMD(...)` in a cell) → the cell value is sanitized to plain text, not executed
- Export another user's/tenant's data via IDOR (changing a param in the URL/request) → blocked, 403 Forbidden

---

### Tier 2 — @normal + @low

**[VP-UI] Interface states**

- [@normal] Import: accepted file types, size limit, and a "download template" link appear clearly before the user picks a file
- [@normal] Import processing: progress/spinner shows, the Import button is disabled to prevent a double submit
- [@normal] Import result summary: "X records imported, Y skipped, Z failed" is clearly shown, with a downloadable error report if the spec requires
- [@normal] Export: the Export button switches to a loading state while generating the file
- [@low] Template download: the template file has the correct column headers and at least 1 row of example data

**[VP-VAL] Edge cases**

- [@normal] Import an empty file (header row only, no data) → warning "No data to import", no crash, no record created
- [@normal] Export when data is empty → the file still downloads, with the header row only and no data rows (override if the spec wants to block the download and show a "No data to export" toast)
- [@normal] Export a large file (10k+ records) → the file generates within the allowed timeout, no 504 error
- [@normal] Export encoding is correct: opening the CSV in Excel shows no broken Vietnamese font (UTF-8 BOM)

---

### ⚡ Cross-pattern interactions

- **+ Data Table**: After import → the table auto-refreshes to show new records; Export takes exactly the data currently shown in the table
- **+ Filter**: Export with an active filter → the exported file count matches the number of records shown in the UI
- **+ Pagination**: After import → the total count in the pagination updates correctly
- **+ Notification**: Import/Export complete (especially async) → toast or notification shows the result summary
