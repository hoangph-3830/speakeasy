# progress Screen Specification

## Overview
- **App Entry:** com.speakeasy.app/.MainActivity
- **Platform:** Android (Jetpack Compose; test tags exposed as resource-ids)
- **Auth Required:** no
- **Reach:** tap [Later Button] > tap [Progress Tab]
- **What it is:** Tab Tiến độ: thống kê, mục tiêu ngày, giọng đọc, nhắc học, làm lại kiểm tra trình độ, xoá tiến độ

## Business Rules
- FR-PG-01: A new learner sees level 1 with 0/100 XP and no learned words.
- FR-PG-02: Choosing a daily goal changes the goal shown on Home.
- FR-PG-03: Choosing the UK accent selects it.
- FR-PG-04: "Xoá tiến độ" asks first; confirming resets settings such as the daily goal.
- FR-PG-05: Cancelling the reset keeps everything.
- FR-PG-06: "Làm lại bài kiểm tra trình độ" opens the placement test.
- FR-PG-07: The chosen daily goal is kept after the app restarts.
- FR-PG-08: Turning the daily reminder on (notification permission granted) shows the reminder time, 20:00 by default.
- FR-PG-09: Choosing the kids' path starts the learning path with the kids' topics.

## Notes
- Every scenario starts from a fresh install (`@reset:data`); the first-launch name dialog is closed with "Để sau".
