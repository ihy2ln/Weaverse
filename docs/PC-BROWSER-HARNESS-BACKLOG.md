# Deferred: PC/browser harness processing

Status: deferred by user on 2026-09-15. Current development scope is mobile only.
This is a future-work record, not an implemented or verified feature.

## Intended experience

- Mobile continues using OpenRouter without requiring a PC.
- Future PC/browser editor: `Use harness · PC only`, with a harness/model selector.
- A connected harness would coordinate the operation, rather than requiring separate
  OCR and proofreading model choices. Expose only verified capabilities.
- Optional local ComfyUI workflows for image editing/colorization and compatible local
  text/vision models for translation. A checkpoint alone is not a complete workflow.
- Preserve originals and return editable text layers alongside processed images.
- Do not silently fall back to a separately billed provider.

## Findings from repository inspection

- `desktop/launcher/main.go` launches the desktop JAR; the Windows executable is not
  a separate implementation of the Android manga editor.
- `desktop/src/main/kotlin/com/ihy2ln/weaverse/desktop/Main.kt` starts a local web hub.
- Desktop and Android sharing use the web assets in
  `sync-core/src/main/kotlin/com/ihy2ln/weaverse/sync/web/WebAssets.kt`.
- That web UI currently provides library/sharing/reading functions, not the Android
  manga editing surface. A PC processing panel or a browser editor must be built.
- Existing sharing/pairing and MCP access do not submit editing jobs to a harness.
  The current ChatGPT/Codex Settings switch gates incoming MCP tool access only.

## Work to assess when explicitly resumed

1. Choose a smaller PC processing panel or full browser-editor scope with the user.
2. Implement a local companion with authenticated job submission, progress,
   cancellation, bounded retries, result retrieval, and capability/model discovery.
3. Verify supported harness authentication and image-tool access. Standard subscriptions
   must not be presented as generic API credentials or guaranteed automation coverage.
4. Inspect the user's actual ComfyUI installation, models, and workflows before promising
   compatibility. Do not expose raw CLI execution or an unrestricted ComfyUI port publicly.
5. Optionally add paired phone-to-PC processing later: same-network first; private remote
   connectivity or an authenticated relay separately. PC must be awake and reachable.
6. Test save/reopen, image and text-layer round trips, disconnections, revocation, and
   failure reporting before offering the provider as usable.

No PC/browser code was changed during this investigation. No harness processing backend
or functional editor harness selector has been delivered.
