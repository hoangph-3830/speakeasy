# GROUP D: DISPLAY & FEEDBACK

> The user reads visualized data, interacts with overlay panels, moves between sections, and receives feedback from the system.

Patterns: 11. Chart / Analytics · 12. Modal / Dialog · 13. Navigation · 14. Notification / Toast / Alert
See `SKILL.md` for the 4 Viewpoints, Shared Checks, and Security Tag Rules.

---

## 11. Chart / Analytics

**Apply when**: the screen has charts (bar, line, pie, donut, scatter…) or a dashboard showing aggregate figures (KPI cards, metrics).

**Shared checks applied**: Loading State, Empty State

---

### Tier 1 — @high

**[VP-LOGIC] Data accuracy**

- The values shown on the chart (numbers, %, totals) match exactly the raw data in the DB after computation
- Change the time range/filter → the chart reloads with the correct data for the new range, no stale data from the previous fetch
- Hover a data point/bar/slice → the tooltip shows the exact value, matching the corresponding Y/X axis
- Multiple series on one chart → the legend has the correct names and colors, toggling hide/show hides/shows the corresponding data
- Click a data point with drill-down → navigate to a list/detail showing exactly the records of that dimension

**[VP-VAL] Data integrity**

- A chart with a value of 0 → shows 0 clearly (bar height = 0, the data point is not hidden), no crash
- A chart with a single data point → renders correctly, no scale/ratio glitch
- A very large outlier value → the Y-axis scale adjusts to fit, smaller data points remain distinguishable

**[VP-SEC]**

- A chart showing another tenant's data via a changed URL param → 403 Forbidden, no data returned
- Sensitive data (salary, private revenue) for a user without permission → the API returns no data, the chart shows "No permission" instead of an empty state

---

### Tier 2 — @normal + @low

**[VP-UI] Interface states**

- [@normal] Loading: a skeleton chart or spinner shows while fetching, axes and labels do not flash/flicker when the data arrives
- [@normal] Responsive: the chart resizes correctly when the viewport changes, no overflow of the container and no clipping
- [@normal] Legend: the colors in the legend match exactly the colors on the chart, clicking toggles hide/show of a series immediately
- [@low] Axis labels: do not overlap (rotate if needed), have a clear unit (VND, %, people)
- [@low] Export chart (if present): the PNG/PDF file contains the chart currently shown, including title, legend, and axis labels

**[VP-VAL] Edge cases**

- [@normal] API timeout/error → the chart area shows a clear error state + a "Retry" button, not an empty chart or a chart with stale data
- [@normal] A very wide time range (multiple years) → the chart aggregates data correctly (by month/quarter, not by day), no performance issue or layout break

---

### ⚡ Cross-pattern interactions

- **+ Filter**: Apply a filter → the chart re-renders with filtered data, the title/subtitle updates to reflect the active filter
- **+ Data Table**: Chart and table shown on the same screen → the total on the chart matches the total rows in the table
- **+ Export**: Export data from the chart → the CSV/XLSX contains raw data matching exactly the values shown on the chart

---

## 12. Modal / Dialog

**Apply when**: the screen has an overlay panel that opens on top of the main page (confirm an action, a small form, show details).

**Shared checks applied**: (no default shared check)

---

### Tier 1 — @high

**[VP-LOGIC] Open/close behavior**

- Trigger the modal (click a button/link) → the modal opens with the correct content, a backdrop overlay covers the whole background
- Click the X or Cancel button → the modal closes, the user's entered data is cleared, the background is unchanged
- Press Escape → the modal closes, equivalent to Cancel
- Click the backdrop (outside the modal area) → the modal closes if it is dismissible
- A submit action in the modal succeeds → the modal closes itself, the background data/list refreshes immediately without a page reload
- A submit action in the modal fails (server error) → the modal stays open, the error message appears inside the modal, the entered data is not lost

**[VP-VAL]**

- A form inside a modal: apply all the validation rules of the Form & Inputs pattern
- A confirmation dialog: only a message + 2 buttons Confirm/Cancel, no input field

**[VP-SEC]**

- A modal containing sensitive data (PII, credentials) → when the modal closes, the HTML is removed from the DOM (no longer present in inspect element)
- Trigger the modal without authentication → redirect to Login, the modal content is not rendered

---

### Tier 2 — @normal + @low

**[VP-UI] Interface states**

- [@normal] Modal centered: in the middle of the viewport, the backdrop dims the whole screen, background scroll is locked
- [@normal] Focus trap: the Tab key only cycles through elements INSIDE the modal, does not jump out to the backdrop
- [@normal] Stacked modals: Modal B opens on top of Modal A → B has a higher z-index, closing B keeps A still present and usable
- [@normal] Responsive: on mobile the modal takes full width or a bottom-sheet form, the action buttons are not hidden by the keyboard
- [@low] Animation: the modal opens with a fade-in/slide-in, closes with a fade-out, no jitter or flicker

**[VP-VAL] Edge cases**

- [@normal] Reopen the modal after closing → the content resets to the initial state, no data retained from the previous open
- [@low] Modal content overflow: content taller than the viewport → the modal has an internal scroll, the background page does not scroll

---

### ⚡ Cross-pattern interactions

- **+ Form & Inputs**: A modal form submit succeeds → the modal closes, a success toast shows on the background page
- **+ Create/Add**: A create form in a modal → on success: the modal closes, the list auto-refreshes, the total count increases by 1
- **+ Delete**: A confirmation modal → Confirm: the modal closes, the record is deleted, the list refreshes; Cancel: the modal closes, the record remains
- **+ Notification**: An action in the modal triggers a toast → the toast shows on the background page after the modal closes, not hidden by the modal

---

## 13. Navigation

**Apply when**: the screen has a side menu, tab bar, breadcrumb, or top navigation bar to move between sections/pages.

**Shared checks applied**: (no default shared check)

---

### Tier 1 — @high

**[VP-LOGIC] Navigation behavior**

- Click a menu item/tab → navigate to the correct corresponding page/section, the URL changes correctly
- The active item is clearly highlighted (visually distinct from inactive items)
- Breadcrumb: click a parent-level link → navigate to the correct parent page, no 404
- Breadcrumb: the last item (current page) is plain text, not a link, not navigable
- Sub-menu: click a parent menu → the sub-menu expands; click again → it collapses; the state is preserved when navigating to another menu and back

**[VP-VAL]**

- A menu item with a badge/counter (notification count) → the number matches the actual unread count from the API, updates after reading
- A tab with dynamic content → switching tabs loads the correct content of that tab, not another tab's content

**[VP-SEC]**

- A menu item for a restricted page is shown but the user has no permission → click → a 403 page; or the item is hidden from the menu (verify the DOM has no link and the API returns no data)
- Direct URL access to a restricted page while not logged in → redirect to Login, the URL is preserved so post-login redirects correctly
- **The oracle for "role X can open screen Y" is rendered content, not the URL.** A client-side router shows the destination URL before the permission check answers; assert a heading/landmark of the screen (or give its page selector an `anchor:`) so a late 403 bounce fails the case

---

### Tier 2 — @normal + @low

**[VP-UI] Interface states**

- [@normal] Side menu collapsed: icon-only mode, a tooltip shows the full menu name on icon hover
- [@normal] Side menu expanded: menu item names are fully shown, not truncated at normal lengths
- [@normal] Tab bar: tabs do not wrap to a second line, scroll horizontally if there are too many tabs
- [@normal] Responsive: the side menu becomes a hamburger menu on mobile, toggling open/close does not cover the main content
- [@low] Scroll restoration: navigate away and back via the browser Back button → the scroll position of the old page is restored

**[VP-VAL] Edge cases**

- [@normal] Browser back/forward button: navigates correctly per the history stack, no blank page or wrong URL
- [@low] Deep link: paste a sub-page URL into a new browser → loads that exact page (no redirect to home)

---

### ⚡ Cross-pattern interactions

- **+ Filter/Search**: Navigate to another section/tab and back → the filter and search state are preserved, not reset to default
- **+ Notification**: The badge counter on a menu item updates when a new notification is received (without a page reload)

---

## 14. Notification / Toast / Alert

**Apply when**: the screen has a temporary feedback message (toast, snackbar) or a persistent alert (banner, inline alert) to report the result of an action.

**Shared checks applied**: (no default shared check)

---

### Tier 1 — @high

**[VP-LOGIC] Message behavior**

- A successful action (save, delete, submit) → a success toast shows the correct message, auto-dismisses after ~3–5 seconds
- A failed action (validation error, server error) → an error toast/alert shows the correct message, does NOT auto-dismiss, the user must dismiss it actively
- Multiple actions in a row → each action has its own toast, the toasts stack in order of appearance, not overwriting each other
- Click X to dismiss a toast → the toast disappears immediately, the remaining toasts fill the gap

**[VP-VAL]**

- The message text matches exactly {{success_message}} / {{error_message}} in test-data.yaml, not hardcoded wrong
- The toast type matches the semantic: success → check icon + green background; error → X icon + red background; warning → ! icon + yellow background; info → i icon + blue background

**[VP-SEC]**

- A message containing data from user input (e.g. "Created '[record name]' successfully") → the name is rendered as literal text, no script execution (XSS)

---

### Tier 2 — @normal + @low

**[VP-UI] Interface states**

- [@normal] Position: the toast appears in the defined corner (top-right, bottom-center…), not covering the active action buttons or form fields
- [@normal] Progress bar (if present): the countdown bar runs for the correct duration, the toast auto-dismisses when it finishes
- [@normal] A toast appearing while a modal is open → the toast appears on top of the modal (higher z-index), not hidden by the backdrop
- [@low] Pause on hover: hover the toast → the auto-dismiss timer pauses; mouse leaves → the timer resumes from where it stopped

**[VP-VAL] Edge cases**

- [@normal] Very long message text → the toast wraps text within its container, no overflow out of the viewport
- [@low] Rapid fire: trigger 5+ actions in a row → toasts queue in the correct order, no UI crash, the oldest toast dismisses first

---

### ⚡ Cross-pattern interactions

- **+ Create/Add**: Submit success → the success toast auto-dismisses; submit failure → the error toast stays until the user dismisses it
- **+ Delete**: Delete success → a success toast; blocked by a foreign key → an error toast stating the module still using the record
- **+ Import/Export**: An async operation completes → a toast or the notification bell updates with the result summary
- **+ Modal/Dialog**: An action in a modal → the modal closes first, then the toast shows on the background page
