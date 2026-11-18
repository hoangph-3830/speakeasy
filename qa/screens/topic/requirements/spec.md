# topic Screen Specification

## Overview
- **App Entry:** com.speakeasy.app/.MainActivity
- **Platform:** Android (Jetpack Compose; test tags exposed as resource-ids)
- **Auth Required:** no
- **Reach:** tap [Later Button] > tap [Continue Learning]
- **What it is:** Màn chủ đề (Meeting new people) và 4 bước học: cụm từ, hội thoại, luyện nói, game; luyện nhại

## Business Rules
- FR-TP-01: The topic detail screen shows the topic title, its counts and the learning steps.
- FR-TP-02: Step 1 opens the phrases of the topic.
- FR-TP-03: Step 2 opens the dialogue of the topic.
- FR-TP-04: Step 3 opens speaking practice with a microphone button.
- FR-TP-05: Step 4 opens the topic game at round 1.
- FR-TP-06: The shadowing card opens shadowing practice.
- FR-TP-07: Finishing the vocabulary cards completes step 1: +10 XP toward the daily goal.
- FR-TP-08: Completing step 1 adds the topic's cards to the review deck.

## Notes
- Every scenario starts from a fresh install (`@reset:data`); the first-launch name dialog is closed with "Để sau".
