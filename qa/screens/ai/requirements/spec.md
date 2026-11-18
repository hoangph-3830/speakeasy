# ai Screen Specification

## Overview
- **App Entry:** com.speakeasy.app/.MainActivity
- **Platform:** Android (Jetpack Compose; test tags exposed as resource-ids)
- **Auth Required:** no
- **Reach:** tap [Later Button] > tap [AI Tab]
- **What it is:** Tab Nói với AI: chọn độ khó, chọn tình huống, trò chuyện

## Business Rules
- FR-AI-01: The AI tab offers difficulty levels and conversation scenarios.
- FR-AI-02: A scenario opens the chat with its role, a text field and a microphone.
- FR-AI-03: Send stays disabled while the text field is empty.
- FR-AI-04: The AI answers a sent message (needs network and an AI key).

## Notes
- Every scenario starts from a fresh install (`@reset:data`); the first-launch name dialog is closed with "Để sau".
