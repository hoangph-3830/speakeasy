# stories Screen Specification

## Overview
- **App Entry:** com.speakeasy.app/.MainActivity
- **Platform:** Android (Jetpack Compose; test tags exposed as resource-ids)
- **Auth Required:** no
- **Reach:** tap [Later Button] > tap [Stories Shortcut]
- **What it is:** Truyện nghe và truyện tranh song ngữ

## Business Rules
- FR-ST-01: The hub lists audio stories by level with their sentence and question counts.
- FR-ST-02: The picture-book tab lists bilingual books with their page counts.
- FR-ST-03: Opening a story shows its first sentence with the Vietnamese translation.
- FR-ST-04: Opening a picture book opens the reader.
- FR-ST-05: "Tiếp tục" reveals the next line of the story.

## Notes
- Every scenario starts from a fresh install (`@reset:data`); the first-launch name dialog is closed with "Để sau".
