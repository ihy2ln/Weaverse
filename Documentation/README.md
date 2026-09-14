# WeaverVerse Documentation

This folder is the maintained product and engineering reference for the WeaverVerse Android app.

## Start here

- [App Wiki](WeaverVerse-App-Wiki.md) — player-facing features, campaign flow, modes, and troubleshooting.
- [Technical Reference](WeaverVerse-App-Technical-Reference.md) — architecture, persistence, AI boundaries, testing, and release workflow.
- [Checkpoint](CHECKPOINT-2026-09-09-RPG-SCENE-INTERACTIONS.md) — the hard checkpoint represented by the current commit.

The Android project lives beside this folder under `app/`. The legacy Text Game remains a separate feature and save boundary; RPG changes must not import or modify its reducers.
