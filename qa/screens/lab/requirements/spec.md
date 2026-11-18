# lab Screen Specification

## Overview
- **App Entry:** com.speakeasy.app/.MainActivity
- **Platform:** Android (Jetpack Compose; test tags exposed as resource-ids)
- **Auth Required:** no
- **Reach:** tap [Later Button] > tap [Lab Shortcut]
- **What it is:** Phòng phát âm: danh sách bài và bài học theo bước

## Business Rules
- FR-LB-01: The lab lists lessons and a score card that asks for a first lesson.
- FR-LB-02: A lesson opens at step 1 of 4 with minimal pairs to listen to.
- FR-LB-03: "Bắt đầu luyện" moves the lesson to step 2.
- FR-LB-04: Back from a lesson returns to the lab list.
- FR-LB-05: The IPA chart lists the 44 English sounds.
- FR-LB-06: Tapping a sound shows its example word and a pronunciation tip.
- FR-LB-07: Tongue twisters are listed with the sound they train.
- FR-LB-08: Opening a twister shows its Vietnamese meaning, listen buttons and the microphone.

## Notes
- Every scenario starts from a fresh install (`@reset:data`); the first-launch name dialog is closed with "Để sau".
