# news Screen Specification

## Overview
- **App Entry:** com.speakeasy.app/.MainActivity
- **Platform:** Android (Jetpack Compose; test tags exposed as resource-ids)
- **Auth Required:** no
- **Reach:** tap [Later Button] > tap [News Tab]
- **What it is:** Đọc báo song ngữ từ BBC, CNN, Reuters… (cần mạng)

## Business Rules
- FR-NW-01: The news tab shows the sources and categories.
- FR-NW-02: Choosing a source shows only that source's articles.
- FR-NW-03: Opening an article opens the reader.
- FR-NW-04: The default source is BBC: every article listed first is from BBC.
- FR-NW-05: Refreshing reloads the list for the same source.

## Notes
- Every scenario starts from a fresh install (`@reset:data`); the first-launch name dialog is closed with "Để sau".
- Articles come from live RSS feeds, so these scenarios need network.
