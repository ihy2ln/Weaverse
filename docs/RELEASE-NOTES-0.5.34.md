# Weaverse v0.5.34 — Detailed release notes

## Summary

Writing-focused polish: reliable color formatting, a calmer Format menu, Review under Write with real LLM notes, scene snapshots, Focus mode, Codex peek, and smarter prompt/context tooling — plus an in-app Help section for new users.

## Changes by area

### Core UI
| Item | Detail |
|------|--------|
| Color utils | `toRgbHexString()` / `parseHexColor()` via RGB ints |
| HSV wheel | Editable Hex `OutlinedTextField`; no HSV reset loop on every emit |
| Chrome | Search icon; Help icon; Focus in Write toolbar |

### Editor
| Item | Detail |
|------|--------|
| EditMenuGate | `allowReopen` / `onUserPressInSelection` / `onSystemShowMenu` |
| Gestures | `TEXT_LONG_PRESS_MS = 650`, media haptic long-press |
| Mentions | Tap → Codex peek sheet |

### Data
| Item | Detail |
|------|--------|
| DB | version 6; `SceneSnapshotEntity`; `MIGRATION_5_6` |
| Settings | `ActionModelKeys` + encoded `actionModelRefs` map |

### AI / context
| Item | Detail |
|------|--------|
| ContextBuilder | Budget-aware include; dropped entries excluded from `codexBlock` |
| ReviewViewModel | Scene/chapter assembly + `AiGenerationService.complete` |
| GlobalPrompt | Preview + meter + dock action model persistence |
| Write overlay | Models row (including scene beat strip) + context meter |

### Help / docs
| Item | Detail |
|------|--------|
| `feature/help` | Tutorial · Manual · What's new |
| `docs/` | User manual, wiki pages, these release notes |

## Test coverage added/updated
- `ColorHexTest`, `ActionModelsTest`
- `ContextBuilderTest` (budget drop)
- `EditMenuGateTest` (reopen on press)
- `PromptModelSelectionTest`, `RailTabDefaultsTest`, `UsageFormatTest`

## Install
Prefer the GitHub Release APK for this tag once CI finishes. Sideload over a previous debug-signed build only if your signing config matches.
