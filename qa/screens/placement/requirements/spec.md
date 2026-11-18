# placement Screen Specification

## Overview
- **App Entry:** com.speakeasy.app/.MainActivity
- **Platform:** Android (Jetpack Compose; test tags exposed as resource-ids)
- **Auth Required:** no
- **Reach:** tap [Later Button] > tap [Placement Card]
- **What it is:** Bài kiểm tra trình độ (mở từ thẻ trên Home hoặc màn Tiến độ)

## Business Rules
- FR-PL-01: The test starts at level A1 with a question and answer options.
- FR-PL-02: Answering moves to the next question of the same level until the level is done.
- FR-PL-03: Leaving before the end saves nothing: Home still offers the placement card.
- FR-PL-04: Finishing saves the level, shows the result and Home stops offering the test.

## Notes
- Every scenario starts from a fresh install (`@reset:data`); the first-launch name dialog is closed with "Để sau".
