# Milestone 1 – AdventurePad Dual-Screen Proof of Concept

**Date:** 2026-08-05

## Objective

Prove that the AYN Thor's lower touchscreen can control the real ScummVM cursor while the game runs on the upper display.

---

## Hardware

- AYN Thor
- Android 13

---

## Verified PASS

- Automatic AdventurePad launch on the lower display
- ScummVM runs on the upper display
- Real ScummVM cursor movement
- No green/overlay cursor
- Relative trackpad movement
- Joystick forwarding remains active after using the lower screen
- Tap-to-click
- FLAC/CD audio working
- Monkey Island successfully tested

---

## Architecture

AdventurePad
↓
Messenger IPC
↓
ScummVM RelativeInputService
↓
ScummVM.pushEvent()
↓
JNI
↓
JE_BALL / JE_MOUSE_BUTTON
↓
Real ScummVM cursor

---

## Repository milestones

AdventurePad

Commit:
9b275ed

Milestone:
Prove cross-display relative cursor pipeline

ScummVM

Commit:
73e0a456

Milestone:
AdventurePad dual-screen cursor integration

---

## Remaining work

- Two-finger tap for right-click
- Click-and-drag
- Drag threshold tuning
- Scroll gestures
- Trackpad polish
- UI redesign
- Per-game layouts
- Notes and hint system

---

## Notes

This is the first fully working end-to-end proof that the lower touchscreen on the AYN Thor can function as a native trackpad for ScummVM without an overlay cursor.

