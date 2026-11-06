# GROUP C: DATA EXPLORATION

> The user searches, filters, and browses existing data in the system.

Patterns: 7. Data Table · 8. Search · 9. Filter · 10. List / Card View
See `SKILL.md` for the 4 Viewpoints, Shared Checks, and Security Tag Rules.

---

## 7. Data Table

**Apply when**: the screen shows data as a table with rows and columns, usually with sort, pagination, and row-level actions.

**Shared checks applied**: Loading State, Empty State, XSS/Injection (data from the DB displayed safely)

---

### Tier 1 — @high

**[VP-LOGIC] Table behavior**

- Table finishes loading → the number of rows shown matches the configured page size (e.g. 10, 20, 50 rows/page)
- The total record count in the UI ("Total: X records") matches exactly the real record count in the DB
- Click a sortable column header → data re-sorts in the correct order (ASC first, DESC second), the sort icon changes accordingly
- Click a sortable column header once → sort ASC, ↑ icon shows; twice → sort DESC, ↓ icon shows; three times → back to unsorted, icon disappears
- Click an action button (Edit/Delete/View) on a row → the action applies to that exact row (correct record ID, not a different row)
- *Pagination:* Click page 2 → the table shows the correct next records, no overlap with page 1
- *Pagination:* Change the page size → the table reloads with the new row count, resets to page 1

**[VP-VAL] Data integrity**

- The value in each cell matches exactly the data in the DB (amount, date, status label, unit)
- Long text in a cell → truncated with `...`, does not break the layout or push the column out
- A column with a null/empty value → shows a placeholder (em dash or empty cell), not raw "undefined" or "null", the cell layout does not break

**[VP-SEC]**

- Data from the DB containing HTML/script tags → rendered as literal text, not executed (XSS)
- A user without permission to view a sensitive column (salary, national ID…) → the column is fully hidden from the DOM, not leaked via the API response (override if the spec wants to show `***` so the user knows the field exists)

---

### Tier 2 — @normal + @low

**[VP-UI] Interface states**

- [@normal] Sortable column header: has an arrow/indicator icon and a pointer cursor; a non-sortable column has no icon
- [@normal] Sticky header: scroll down vertically → the header row stays fixed, the column headers are always visible
- [@normal] Sticky action column (if the table scrolls horizontally): the Edit/Delete column is fixed on the right, not scrolled out of the viewport
- [@normal] Row hover: the background color changes slightly, clearly indicating which row is hovered
- [@normal] Pagination controls: Previous is disabled on page 1; Next is disabled on the last page; the current page is highlighted
- [@low] Column resize (if present): drag the header border to change the width, without affecting the data

**[VP-VAL] Edge cases**

- [@normal] Table with 1 row → pagination is fully hidden (override if the spec wants "Page 1 of 1"), no UI glitch
- [@normal] Sort on a datetime column → sorts correctly by the real timestamp, not alphabetically as a string
- [@normal] Sort on a numeric column → sorts correctly by numeric value (10 > 9), not as a string ("10" < "9")

---

### ⚡ Cross-pattern interactions

- **+ Search**: Search submit → the table filters immediately, pagination resets to page 1, the sort state is preserved
- **+ Filter**: Apply a filter → the table shows only matching rows, the total count updates, pagination resets to page 1
- **+ Update/Edit**: Save an edit successfully → the row in the table reflects the new data immediately, no full-page reload
- **+ Delete**: Delete a record → the row disappears immediately, the total count decreases by 1, pagination adjusts itself
- **+ Import/Export**: After import → the table auto-refreshes; Export takes exactly the data currently shown (current filter + sort)

---

## 8. Search

**Apply when**: the screen has a search box (search bar) letting the user filter data by keyword.

**Shared checks applied**: Empty State, XSS/Injection

---

### Tier 1 — @high

**[VP-LOGIC] Search behavior**

*Basic search:*
- Enter a valid keyword → results show the correct records containing the keyword (case-insensitive)
- Partial-match keyword → records containing the keyword anywhere all appear
- Enter a keyword matching no record → the Empty State message appears ("No results found"), the layout does not break
- Clear the keyword → the list reloads to the default state (all records), no need to press Search again

*Advanced search (if the spec has multiple search fields):*
- Fill multiple fields → results satisfy ALL filled conditions (AND logic)
- Leave some fields empty → filter only by the filled fields, empty fields are ignored

**[VP-VAL] Input handling**

- Input containing only whitespace → auto-trimmed, search with empty string → results as if not searched
- Enter special characters (single quote `'`, `%`, `_`) → treated as literal text, the query is not broken, results are valid
- Input beyond max length → field accepts no more characters (HTML maxlength default)

**[VP-SEC]** → Apply Shared Check: XSS/Injection (the search term shown in the results must be escaped correctly) + **SQL injection layer 2** (the search field reaches a LIKE query → test the API-level bypass `@high @manual`)

---

### Tier 2 — @normal + @low

**[VP-UI] Interface states**

- [@normal] A search icon or placeholder clearly hints what the field is for ("Search by name, email…")
- [@normal] Clear button (X): appears when there is text, click → the field empties and the list resets, no navigation away from the page
- [@normal] Search trigger: debounce ~300ms after stopping typing, or press Enter → the API call runs, a loading indicator shows in the table
- [@low] The keyword is highlighted in the results: the matching text portion is bold or a standout color

**[VP-VAL] Edge cases**

- [@normal] Keyword is pure digits (e.g. "12345") → searches correctly in numeric fields and text fields
- [@normal] Keyword is accented Vietnamese → results are correct, no encoding glitch

---

### ⚡ Cross-pattern interactions

- **+ Data Table**: Search submit → the table filters immediately, pagination resets to page 1
- **+ Filter**: Search + Filter both active → results satisfy BOTH conditions (AND)
- **+ Pagination**: Search returns many results → pagination shows correctly, the total count updates

---

## 9. Filter

**Apply when**: the screen has filters (dropdown, date range picker, checkbox group, radio group…) to narrow down the displayed dataset.

**Shared checks applied**: Empty State, URL Manipulation

---

### Tier 1 — @high

**[VP-LOGIC] Filter behavior**

- Select 1 filter value → the list/table shows only matching records, the total count updates correctly
- Select multiple values within the same filter group → show records matching ANY value in the group (OR logic within the group)
- Apply multiple filter groups at once → show records matching ALL groups (AND across groups)
- Reset / Clear All filters → the list returns to default, all filter controls return to their initial values
- A filter combination yielding 0 results → the Empty State appears, the layout does not break
- Dependent filter (if the screen has one): select Filter A → Filter B's options reset and reload per A's value immediately, no stale old options retained

**[VP-VAL] Input validation**

- Date range: start date > end date → the Apply button is disabled, the filter is not activated
- Numeric range: min > max → the Apply button is disabled, the filter is not activated
- Dropdown filter: options match exactly the real data in the DB (no orphan option)

**[VP-SEC]** → Apply Shared Check: URL Manipulation (tampered filter params → fallback to default, no crash)

---

### Tier 2 — @normal + @low

**[VP-UI] Interface states**

- [@normal] Active filters: shown as clear tags/chips, each tag has an X to remove that filter individually
- [@normal] Collapse/expand the filter panel: the selected state of the filters is preserved, not reset on collapse
- [@normal] Filter count badge: "Filters (3)" or an indicator shows the number of active filters
- [@low] Filter options in alphabetical order (override if the spec uses frequency sort or a custom order), not random on each open

**[VP-VAL] Edge cases**

- [@normal] A filter option removed from the DB (orphan option) → does not appear in the dropdown, no crash even if the URL still has the old param
- [@normal] Filter with a date range spanning a different timezone → results are correct per the server's timezone

---

### ⚡ Cross-pattern interactions

- **+ Search**: Filter + Search both active → AND logic, total records = the intersection of both conditions
- **+ Data Table**: Apply a filter → the table reloads, pagination resets to page 1, the sort state is preserved
- **+ Export**: Export with an active filter → the file contains only filtered records, not the entire dataset
- **+ Pagination**: A filter changing the total count → pagination recalculates, automatically goes to page 1

---

## 10. List / Card View

**Apply when**: the screen shows many records as a list (rows) or cards (grid), possibly with infinite scroll or a "Load More" button instead of traditional pagination.

**Shared checks applied**: Loading State, Empty State

---

### Tier 1 — @high

**[VP-LOGIC] Navigation & actions**

- Click a card/row → navigate to the correct detail page of that record (correct ID, not a different record)
- A quick action directly on the card (Like, Bookmark, Add to Cart, Status toggle) → changes the state immediately without navigating to another page
- *Infinite scroll:* Scroll to the bottom → the next records are appended to the list, the scroll position does not jump back to the top
- *Load More button:* Click "Load More" → the next records are appended, the total count updates
- Broken image (image URL is broken or returns 404) → a placeholder image is shown, the card layout does not break

**[VP-VAL] Data integrity**

- The values shown on the card (price, status, tag, rating) match exactly the data in the DB
- The total count ("Showing X of Y") matches the real total records after applying the current filter/search

**[VP-SEC]**

- Sensitive data (national ID, bank account number) of another user is not shown in the card → verify the DOM does not contain this data
- A quick action (Like, Follow) without auth → redirect to Login, the action is not performed

---

### Tier 2 — @normal + @low

**[VP-UI] Interface states**

- [@normal] Interactive cards: a clear hover effect (shadow, scale, or overlay) + pointer cursor; non-interactive (informational only) cards: no hover effect, default cursor
- [@normal] Grid/List layout toggle (if present): the switch changes the layout immediately, the data is unchanged, the preference is saved on page reload
- [@normal] Skeleton loading: while fetching → skeleton cards show the correct count and layout, no blank screen
- [@low] Lazy-loading images: images in the viewport load first, images outside load when scrolled to

**[VP-VAL] Edge cases**

- [@normal] A card with very long text (title, description) → text truncates with single-line ellipsis, does not break the card width (override if the spec uses a multi-line clamp: max 2-3 lines)
- [@normal] Load More / infinite scroll with total < page size → the "Load More" button is fully hidden (override if the spec wants it shown disabled with "No more records")

---

### ⚡ Cross-pattern interactions

- **+ Search**: Keyword submit → the list filters immediately, showing only cards matching the keyword
- **+ Filter**: Apply a filter → the list reloads, showing only cards matching the condition
- **+ Delete**: Delete a record → the card disappears from the list immediately, the total count decreases by 1
- **+ Update/Edit**: Edit successfully → the card reflects the new data immediately (name, status, image)
