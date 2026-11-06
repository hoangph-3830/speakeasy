---
name: sungen-ingest-legacy
description: 'Import a legacy manual testcase suite from Google Sheets (multi-tab) or a local file into Sungen — fetch via MCP, then sungen ingest. Use when the user wants to convert/evaluate an existing manual testcase spreadsheet.'
user-invocable: true
---

# sungen-ingest-legacy

Bring an existing **manual testcase workbook** into Sungen for evaluation + conversion. The
fetch (Google login + pick file) is done here via MCP; the parsing/audit is deterministic
(`sungen ingest`). **Security:** the workbook is the user's project data — read it on
consent, keep the output in their project, never upload or commit the content.

## Flow

1. **Locate the source.**
   - **Google Sheets (recommended):** use the Google Drive MCP. Authenticate if needed, then
     `search_files` / list to find the workbook; confirm the file with the user.
   - **Local:** if the user points to a `.xlsx`/`.csv`, skip to step 4.

2. **List the tabs.** Read the workbook's sheet/tab names (Drive MCP file metadata or a values
   read). A legacy workbook usually has **many** tabs — some are testcases, some are
   viewpoint/UI matrices.

3. **Assemble a JSON sheet-bundle.** For each tab, read its cell values (a 2-D array of
   strings) and write a local bundle in the user project (e.g.
   `qa/screens/<screen>/requirements/legacy/bundle.json`):
   ```json
   { "source": "<workbook name>", "sheets": [ { "name": "<tab>", "rows": [["TC ID","Page",…],["TC-01",…]] } ] }
   ```
   Record only the **source link** in `requirements/legacy/source.yaml` — never the content.

4. **Classify the tabs.** Run:
   ```bash
   sungen ingest --legacy <bundle.json|file.xlsx|file.csv> --list-sheets
   ```
   It prints each tab + detected type (`testcase` / `viewpoint-matrix` / `ui-checklist`).

5. **Confirm which tabs to ingest.** Use `AskUserQuestion` to let the user pick the
   **testcase** tabs (matrix/UI tabs feed the viewpoint layer later, not the inventory).

6. **Ingest + reconcile.**
   ```bash
   sungen ingest --legacy <source> --screen <screen> --sheets "<Tab A>,<Tab B>" --emit-gherkin
   ```
   Produces: `inventory.json` (+ baseline audit), `*.legacy-draft.feature` + `legacy-trace.json`
   (parity: `@legacy:<id>` per scenario), and `test-viewpoint.draft.md` with **blind-spots**
   (catalog-expected viewpoints the legacy suite lacks).

7. **Hand off to quality.** Tell the user the next step is `/sungen:create-test <screen>` —
   it discovers + refines the draft into real `[Reference]` steps and fills the blind-spots;
   then `sungen audit <screen>` gates quality. A 1:1 convert is NOT the deliverable; the
   harness raises the legacy floor to catalog quality.

## Governance block (important)

Many orgs mark confidential files as **"ineligible for generative AI contexts"** — the
Google Drive MCP will then **refuse** to read the file (metadata + download both error).
This is the org's DLP policy, not a bug, and it is the *expected* outcome for a
confidential testcase suite. When you hit it, **do not retry** — fall back:

> "This sheet is restricted by your org's data policy, so I can't read it through the
> AI connector. Two ways to proceed, both running as **you**, not AI:
>  (1) `sungen ingest --gsheet <url>` — fetches under your own Google identity
>      (read-only; Viewer/Commenter is enough). It offers to install `googleapis`
>      and to open the Google login in your browser (pick your account), then
>      retries automatically. Needs the gcloud SDK for the browser login.
>  (2) Export it manually (**File → Download → Microsoft Excel `.xlsx`**) and I'll run
>      `sungen ingest --legacy <file>.xlsx`."

The local-file path is deterministic and **never sends the content through AI** — the
correct, governance-compliant channel for confidential data. The MCP auto-pick is only
for files the org does *not* restrict.

## Notes
- Multiple local CSVs (one per tab) also work: `--legacy tab1.csv tab2.csv …`.
- Re-run only re-fetches when the user asks; otherwise reuse the saved bundle.
- Do not invent testcases. Only ingest what the workbook contains; the *augmentation*
  (blind-spots) happens in `/sungen:create-test`, flagged for human review.
