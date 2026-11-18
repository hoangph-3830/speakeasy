# daily Screen Specification

## Overview
- **App Entry:** com.speakeasy.app/.MainActivity
- **Platform:** Android (Jetpack Compose; test tags exposed as resource-ids)
- **Auth Required:** no
- **Reach:** tap [Later Button] > tap [Play Tab] > tap [Daily Word Card]
- **What it is:** Từ của ngày: đoán từ 5 chữ cái trong 6 lần, mỗi ngày một từ (mở từ tab Chơi)

## Business Rules
- FR-DW-01: The puzzle shows the colour legend, an on-screen keyboard and a Guess key.
- FR-DW-02: After six wrong guesses the puzzle ends and reveals today's word.
- FR-DW-03: A finished puzzle stays finished for the day, also after the app restarts.
- FR-DW-04: The Play tab offers the daily word card.

## Notes
- Every scenario starts from a fresh install (`@reset:data`); the first-launch name dialog is closed with "Để sau".
- The word changes every day, so scenarios lose on purpose with "zzzzz" (never a real word) instead of guessing it.
