# review Screen Specification

## Overview
- **App Entry:** com.speakeasy.app/.MainActivity
- **Platform:** Android (Jetpack Compose; test tags exposed as resource-ids)
- **Auth Required:** no
- **Reach:** tap [Later Button] > tap [Review Shortcut]
- **What it is:** Ôn tập lặp lại ngắt quãng (mở từ phím tắt Ôn tập)

## Business Rules
- FR-RV-01: With no cards due, Review shows the empty state and how to get cards.
- FR-RV-02: "Về trang chủ" returns to Home.
- FR-RV-03: The back arrow returns to Home.

## Notes
- Every scenario starts from a fresh install (`@reset:data`); the first-launch name dialog is closed with "Để sau".
- Review with due cards is covered from the topic unit (VP-LOGIC-002), where cards are created.
