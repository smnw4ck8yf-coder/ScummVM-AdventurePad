# AdventurePad fork publication status

Status reviewed: 2026-09-05

| Item | Current value |
| --- | --- |
| Upstream base | `4edac15aae5e1fe475eb5d4767b4c5ece3636164` |
| Publication branch | `adventurepad` |
| Runtime integration baseline before publication commits | `a8bf225b2725eebea1be5a92d587b85d5d521385` plus the reviewed 11-file worktree captured by `refs/phase4-backups/pre-phase4-20260905` |
| Publication branch HEAD | The commit containing this status file; resolve with `git rev-parse adventurepad` (a literal self-SHA cannot be embedded in its own commit) |
| Android package | `org.scummvm.scummvm.debug` |
| Tested ABI | `arm64-v8a` (Android API 21 native target) |
| Repository history | Full upstream history present; no longer shallow |

## Preserved AdventurePad integration commits

1. `73e0a456f68190315a6887aeb2c654cc74a3e653` — dual-screen cursor/input integration
2. `91ade6ef0851362c5f7ea79dddf80e6370ece545` — Milestone 1 development notes
3. `0de116f6b81c64f2ac8f9bf566f3cfc1740e5357` — dual-surface rendering pipeline
4. `43811ac8b00c2d6269de171da42c540e0b8e5302` — Split View rendering
5. `1312a62a497a728f5db4c866a39e1896ae034cf6` — stretched lower Split View presentation
6. `d0505c6160c4452f5b5d0bc66930b5452a75ba9e` — launcher integration
7. `a8bf225b2725eebea1be5a92d587b85d5d521385` — upper-display black surround

## Readiness

The candidate has been reviewed for local paths, credentials, keys, APKs,
objects, dependency prefixes, IDE metadata, and temporary artifacts. Generated
build products remain ignored and must not be committed.

Validation for the final branch tip is recorded in the Phase 4 completion
report. The remaining source-publication blocker for a future binary release is
a checked-in, clean-machine dependency bootstrap with pinned sources/checksums,
especially for FLAC. A release must also establish durable matching signing and
identify its exact fork commit. The historical v0.1.0-preview APK cannot be
claimed to have exact source correspondence unless its build provenance is
independently recovered.

The closest known committed revision to the historical preview build is
`1312a62a497a728f5db4c866a39e1896ae034cf6`: the APK identifies itself as a
dirty build made on 2026-08-08, after that commit and before the next preserved
commit. The dirty marker means uncommitted inputs were present, so this is only
a nearest baseline, not exact corresponding source and not a basis for a tag.
