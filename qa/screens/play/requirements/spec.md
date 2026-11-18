# play Screen Specification

## Overview
- **App Entry:** com.speakeasy.app/.MainActivity
- **Platform:** Android (Jetpack Compose; test tags exposed as resource-ids)
- **Auth Required:** no
- **Reach:** tap [Later Button] > tap [Play Tab]
- **What it is:** Tab Chơi: các thử thách từ vựng (Đua 60 giây, Lật thẻ, Đúng hay Sai, Nghe & viết…)

## Business Rules
- FR-PY-01: The Play tab lists the vocabulary challenges.
- FR-PY-02: The 60-second race starts with score 0, a timer and answer options.
- FR-PY-03: Memory cards open the memory challenge.
- FR-PY-04: True or false opens its challenge with answer options.
- FR-PY-05: Listen & spell opens with a field to type the word.
- FR-PY-06: Back from a challenge returns to the Play tab.
- FR-PY-07: "Viết câu" shows a Vietnamese prompt to write in English.
- FR-PY-08: "Kiểm tra" stays disabled until something is typed.
- FR-PY-09: Checking shows the model sentence and a match score in %.
- FR-PY-10: The hint shows the first words of the answer.

## Notes
- Every scenario starts from a fresh install (`@reset:data`); the first-launch name dialog is closed with "Để sau".
