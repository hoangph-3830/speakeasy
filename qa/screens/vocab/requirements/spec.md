# vocab Screen Specification

## Overview
- **App Entry:** com.speakeasy.app/.MainActivity
- **Platform:** Android (Jetpack Compose; test tags exposed as resource-ids)
- **Auth Required:** no
- **Reach:** tap [Later Button] > tap [Vocab Shortcut]
- **What it is:** 3000 từ vựng thông dụng: tìm kiếm, nhóm từ, đánh dấu đã thuộc

## Business Rules
- FR-VC-01: The hub shows the total word count and how many are learned.
- FR-VC-02: Searching an English word lists it with its meaning.
- FR-VC-03: Searching a Vietnamese meaning finds the English word.
- FR-VC-04: A search with no match says so.
- FR-VC-05: A group lists its words with the learned count.
- FR-VC-06: Marking a word as learned raises the group's learned count.
- FR-VC-07: Learned words are kept after the app restarts.
- FR-VC-08: "Thêm vào ôn tập" adds every word of the group to the review deck and says how many.
- FR-VC-09: Review serves at most 30 due cards per session.
- FR-VC-10: Flipping a card shows its meaning and four grades with their next-review intervals.
- FR-VC-11: Grading "Nhớ" takes the card out of today's queue and counts it as reviewed.
- FR-VC-12: Grading "Quên" keeps the card in today's queue.

## Notes
- Every scenario starts from a fresh install (`@reset:data`); the first-launch name dialog is closed with "Để sau".
- Open question: after a restart Review shows "Còn 30 thẻ · đã ôn 0" again — is "đã ôn" meant to count per session or per day? Not asserted until answered.
