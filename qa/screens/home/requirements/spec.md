# home Screen Specification

## Overview
- **App Entry:** com.speakeasy.app/.MainActivity
- **Platform:** Android (Jetpack Compose; test tags exposed as resource-ids)
- **Auth Required:** no
- **Reach:** launcher screen (tab "Học")
- **Screen marker:** resource-id `screen_home`

## Sections

### Section: Name dialog (first launch)
- **Type:** modal
- **Description:** Shown when the learner has no name yet (fresh install).

| Element | Type | Identifier | Notes |
|---|---|---|---|
| Name dialog | dialog | id `dialog_name` | title "Chào bạn mới!" |
| Name input | field | id `input_name` | max 20 characters |
| Start button | button | id `btn_name_confirm` | "Bắt đầu học" — saves the name |
| Later button | button | id `btn_name_skip` | "Để sau" — closes without a name |

### Section: Greeting + daily goal
| Element | Type | Identifier | Notes |
|---|---|---|---|
| Greeting | text | text starts with "Good" | "Good morning/afternoon/evening[, <name>]! 👋" |
| Daily goal | text | text "x / 30 XP" | new learner: "0 / 30 XP" |

### Section: Placement card
- Shown only while the learner has not taken the placement test.
- "🧭 Kiểm tra trình độ · 3 phút" → opens the placement screen (`screen_placement`).

### Section: Continue card
- Shows the next step of the learning path. New learner: stage "Chặng 1 · A1 Mới bắt đầu · Khởi động", topic "Meeting new people".
- "Học tiếp →" (`btn_continue_learning`) opens that topic (`screen_topic`).

### Section: Shortcuts
| Shortcut | Identifier | Opens |
|---|---|---|
| Ôn tập | `shortcut_review` | `screen_review` |
| Từ vựng | `shortcut_vocab` | `screen_vocab` |
| Phát âm | `shortcut_lab` | `screen_lab` |
| Truyện | `shortcut_stories` | `screen_stories` |
| Đọc báo | `shortcut_news` | `screen_news` (tab) |
| Video | `shortcut_videos` | `screen_videos` |

### Section: Path / by-topic switch
- "🧭 Lộ trình" (default) shows stages; "🗂️ Theo chủ đề" shows category chips ("🌈 Tất cả" …) and topic rows (`topic_<id>`).

### Section: Bottom tab bar (app-wide)
| Tab | Identifier | Screen |
|---|---|---|
| Học | `tab_home` | `screen_home` |
| Chơi | `tab_play` | `screen_play` |
| Đọc báo | `tab_news` | `screen_news` |
| Nói với AI | `tab_ai` | `screen_ai` |
| Tiến độ | `tab_me` | `screen_me` |

## Business Rules
- FR-HOME-01: On first launch the app asks for the learner's name.
- FR-HOME-02: A saved name appears in the greeting.
- FR-HOME-03: "Để sau" closes the dialog without a name.
- FR-HOME-04: A new learner starts at 0 / 30 XP of the daily goal.
- FR-HOME-05: The continue card points at the first topic of the path and opens it.
- FR-HOME-06: Each shortcut opens its screen.
- FR-HOME-07: The placement card opens the placement test.
- FR-HOME-08: "Theo chủ đề" switches to the topic list with category chips.
- FR-HOME-09: The bottom tabs switch between the five main screens.

## Notes
- Tests run with `@reset:data` so every scenario starts from a fresh install.
