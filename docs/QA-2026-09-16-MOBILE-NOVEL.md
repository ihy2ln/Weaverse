# Mobile Novel v1.4.26 QA

## Build and scope

- Package `com.ihy2ln.weaverse`, version `1.4.26` / code `152`.
- Android-only Novel workspace. No changes under manga or roleplay/chat.
- Room version 23; additive migration from 22 creates reference-link table.
- `assembleDebug`, `assembleDebugAndroidTest`, `assembleRelease` and R8 completed.
- 68 targeted JVM tests, zero failures/errors: Novel writing/Codex/planning, Novelcrafter parser, document model, revision policy, backup archive, illustrated HTML rendering.
- MuMu `127.0.0.1:16384`: two instrumentation tests passed in isolated `com.ihy2ln.weaverse.mangaqa`: migration/reference persistence/isolation; styled-caret Android Parcel round trip.
- Signed using the existing local Android certificate: SHA-256 `f16db5088b05af8c941a0e23eb2364dab470dd4ae07c3023f858708f6451fc05`. APK v2 signature verified. This is the same local certificate used for the previous APK, not a newly provisioned production keystore.
- Local artifact: `Beta.Test.Build/weaverse-v1.4.26-mobile-novel-signed.apk`, 530,690,422 bytes. SHA-256 `facbd73e0c66fa503bf556d68154dd9a44bc0350292d9ff385f02202cddfbddf`.

## Device observations

- Opened existing QA novel and inspected Write, Media and Codex navigation.
- Confirmed manuscript-first Write screen replaces outer shell toolbar; primary workspace destinations stay reachable.
- Opened Android's document picker and imported a non-sensitive QA screenshot as a reference. Gallery reported successful reference-only save.
- Initial build crashed when the file picker saved a styled `TextFieldValue`; Android reported `Parcel: unknown type for value Normal`. Replaced the generic styled-value saver with plain text + integer selection offsets, rebuilding styles from the manuscript. Retested picker import successfully and added instrumentation regression.
- Enlarged font (1.3) exposed Workshop wrapping. Final source uses a single-line, horizontally scrollable destination row.
- MuMu multi-display sizing produced cropped capture/hierarchy behavior during attempted landscape checks; do not count this as a complete landscape pass. Software keyboard-open coverage is also incomplete.
- Test operations used the isolated QA package; production app was not uninstalled or cleared. No paid AI operation or external publishing was performed.

## Evidence and remaining checks

Evidence folder: `Beta.Test.Build/qa-v1.4.26` at repository parent workspace. Includes media gallery and import screenshots. Early files named `novel-landscape` / `novel-keyboard` are exploratory captures, **not proof** of successful landscape/keyboard tests; `novel-write.png` is an unusable black capture from the wrong MuMu display.

Still requires full device workflow: new manuscript creation, novel import, repeated scene navigation while typing, undo/redo, inline media placement/readback, export-open, full backup/restore, portrait/landscape with keyboard and large font, paid generation and failures. Pure regression tests cover prose-preserving insertion, stale candidate rejection, HTML order/escaping/omission and reference persistence; no claim that these replace live provider QA.
