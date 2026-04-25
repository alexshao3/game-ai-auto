# Plans

Decision history for AI Game Bot. Each plan supersedes the previous one; older plans are kept for context.

| Version | Approach | Status |
|---|---|---|
| [v1](plan-v1-pc-controlled.md) | PC + ADB controls Android via scrcpy + minitouch | Superseded |
| [v2](plan-v2-android-native.md) | Standalone Android app with hard-coded TaskGraph per game | Superseded |
| **[v3](plan-v3-pbd-framework.md)** | Generic Android framework. User demonstrates a task once → AI generates a recipe → bot replays with VLM-mediated adaptation | **Current** |

## Why three iterations?

- v1 → v2: User wanted a standalone app for end-users, not a PC tool.
- v2 → v3: User wanted a generic core that works across games, with task definition by demonstration rather than hard-coded Kotlin.

## Where Phase 0 fits in v3

Phase 0 (this PR) only validates the platform plumbing: capture, accessibility, and the backend proxy. The core PbD logic (recorder, recipe generator, executor) lands in Phase 1+.

The single most important question Phase 0 answers is **does `MediaProjection` capture work on Tam Quốc Huyễn Tướng** (no `FLAG_SECURE`). If it doesn't, we revisit the approach (Shizuku, root, or alternative).
