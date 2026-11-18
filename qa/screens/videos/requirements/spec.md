# videos Screen Specification

## Overview
- **App Entry:** com.speakeasy.app/.MainActivity
- **Platform:** Android (Jetpack Compose; test tags exposed as resource-ids)
- **Auth Required:** no
- **Reach:** tap [Later Button] > tap [Videos Shortcut]
- **What it is:** Học qua video từ kênh chính thức trên YouTube (cần mạng)

## Business Rules
- FR-VD-01: The hub shows the official channels, starting with Super Simple Songs.
- FR-VD-02: Opening a video opens the player screen (needs network).

## Notes
- Every scenario starts from a fresh install (`@reset:data`); the first-launch name dialog is closed with "Để sau".
- Video titles come from YouTube at run time; only the channel list is bundled.
