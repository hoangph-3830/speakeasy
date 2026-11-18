# E2E coverage (Android, sungen + Appium)

Last reviewed 2026-11-18. One row per screen; "Automated" counts scenarios that run, "Manual" those tagged `@manual`.

| Unit | Routes | Automated | Manual | What is proven |
|---|---|---|---|---|
| home | `home` + tab bar | 17 | 0 | first-launch name dialog, greeting, daily goal, continue card, 6 shortcuts, by-topic switch, tabs, name kept after restart; DEF-001 (`@known-defect`) |
| placement | `placement` | 3 | 1 | opens at A1, one answer stays at A1, leaving early keeps the card |
| topic | `topic/{id}`, `/phrases`, `/dialogue`, `/speak`, `/shadow`, `game/{id}/{mode}` | 10 | 0 | each step opens; finishing phrases gives +10 XP and fills the review deck; card ↔ detail consistency |
| review | `review` | 3 | 0 | empty state and its two exits (cards: see vocab) |
| vocab | `vocab`, `vocab/{id}` | 12 | 0 | search EN/VI/no match, group, mark learned (+restart), add group to review, 30-card session, flip, grade Nhớ / Quên |
| lab | `lab`, `lab/{id}`, `lab/ipa`, `lab/twisters` | 8 | 0 | lesson steps, IPA chart (44 sounds, tip on tap), tongue twisters (list, open) |
| stories | `stories`, `story/{id}`, `book/{id}` | 5 | 0 | audio list, picture-book tab, open story, "Tiếp tục" reveals the next line, open book |
| videos | `videos`, `video/{id}` | 2 | 0 | channel list, open a video (network) |
| news | `news`, `article/{link}` | 6 | 0 | sources, BBC default list, CNN filter, refresh, scroll, open article (network) |
| play | `play`, `challenge/{id}`, write mode | 11 | 0 | challenge list and entries, 60-second race start, write mode: prompt, required/blank answer, score, hint |
| daily | `daily` | 3 | 0 | legend + keyboard, six wrong guesses reveal the word, finished puzzle kept after restart |
| ai | `ai`, `ai/{scenario}` | 3 | 1 | levels and scenarios, chat opens, empty message cannot be sent |
| progress | `me` | 9 | 0 | level/XP, daily goal choice (+restart), accent, reset confirm/cancel, retake placement, reminder toggle, kids' path |

## Deliberately not automated

| Area | Why | Tag / next step |
|---|---|---|
| Placement test to the end | questions and answer order are random on every run | `@manual:M7` |
| AI reply | external AI service and key | `@manual:M5` |
| Speaking scores (speak, shadow, lab practice, tongue-twister score) | needs real microphone input | not written; would need audio injection on the emulator |
| Daily word win path | the answer changes every day | only the losing path is automated |
| Exam (`exam`, `exam/{id}`, `/test`, `/s/{section}`) | still being built (uncommitted on 2026-11-18) | add a unit once it lands |

## Open questions for QA / product

- Review: after a restart the counter shows "Còn 30 thẻ · đã ôn 0" again. Is "đã ôn" meant per session or per day?
- DEF-001: should "Để sau" hold for the session, or for good until a name is set?
