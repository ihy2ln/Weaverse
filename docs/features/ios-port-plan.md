# Native iOS — plan and current status

**Status as of this document: skeleton only.** A `:shared` Kotlin
Multiplatform module now exists and configures for Android, iOS
(x64/arm64/simulator-arm64), and desktop JVM, but it contains no real app
logic yet — just an expect/actual `Platform` smoke test proving the
toolchain wiring works. No Xcode project exists. **The Android app is
completely unaffected** — `:shared` isn't depended on by `:app` yet, so
nothing about this changes what ships today.

## Why this is a real, multi-session undertaking

The Android app (`app/`) is not portable as-is:

- **Compose (`androidx.compose.*`)** is Android-only. The iOS-capable
  equivalent is **Compose Multiplatform** (JetBrains) — a different set of
  artifacts with the same programming model, so most `@Composable`
  functions should port with modest changes, but it's a real dependency
  swap, not a recompile.
- **Hilt** is Android-only DI. iOS needs either **Koin** (multiplatform,
  recommended — same DSL works everywhere) or hand-written DI.
- **Room** (as used here — `room.runtime`/`room.ktx`) targets Android/JVM.
  Room does have an experimental Kotlin/Native driver now, but
  **SQLDelight** is the more battle-tested cross-platform SQL option for
  KMP and is the more conservative choice for a first real port.
- **Media3/ExoPlayer** (audio/video playback) is Android-only; iOS needs
  AVFoundation via a small expect/actual player abstraction.
- **DataStore, `androidx.security.crypto`** are Android-only; iOS
  equivalents exist (multiplatform DataStore, or Keychain via a small
  expect/actual wrapper) but need wiring.

None of this is exotic — Compose Multiplatform + Koin + SQLDelight is a
common, well-documented stack for exactly this kind of Android→iOS port —
but it's genuinely weeks of restructuring, not a single session's work.

## Hard constraint: this environment cannot build or test iOS

This sandbox has no macOS and no Xcode. Kotlin/Native's Apple targets
(`iosX64`, `iosArm64`, `iosSimulatorArm64`) **configure** fine on Linux —
Gradle project configuration doesn't fail — but their actual
compile/link tasks (`linkDebugFrameworkIosArm64` and friends) can only
run on macOS with Xcode's toolchain installed. `release.yml` runs on
`ubuntu-latest` and only ever requests `assembleRelease` (Android) and
`:desktop:packageDesktopZip` — it never touches the iOS targets, so it
can't validate them either.

**Practical effect**: everything added to `:shared`'s `commonMain` /
`androidMain` / `jvmMain` gets the same CI verification every other
change in this branch gets (`:shared` is now a `com.android.library`
module, so `./gradlew assembleRelease` — the exact command `release.yml`
runs — configures and compiles it as a side effect, same safety net as
`:app`). **`iosMain` gets none of that** — it's written against my best
understanding of the Kotlin/Native UIKit interop APIs, but the first real
compile check it gets will be on an actual Mac. If something in
`Platform.ios.kt` is wrong (e.g. a property vs. zero-arg-method mismatch
importing an Objective-C API), that's where it'll surface.

## Recommended phased plan

1. **(Done)** `:shared` module skeleton — Android/iOS/JVM targets
   configuring, expect/actual smoke test, unit test.
2. Move genuinely platform-agnostic domain code into `:shared`'s
   `commonMain` — the best first candidates are `core/text/DocumentModel.kt`
   and `core/text/MediaGrid.kt` (already pure Kotlin + kotlinx.serialization,
   already unit-tested, zero Android imports). This requires `:app` to
   depend on `:shared` and every `core.text.*` import across the app
   (dozens of files) to update — a real, invasive refactor, deliberately
   *not* done in this pass to avoid colliding with concurrent work on this
   branch or risking the shipping Android build without a careful,
   reviewable step of its own.
3. Swap Hilt → Koin and Room → SQLDelight for the pieces that move into
   `:shared`, one feature area at a time (start with something small and
   self-contained, e.g. Settings/preferences, before touching the novel
   or roleplay data models).
4. Bring in Compose Multiplatform for a shared UI layer, reusing as much
   of the existing `@Composable` tree as the dependency swap allows.
5. Generate the actual Xcode project using JetBrains' [Kotlin
   Multiplatform wizard](https://kmp.jetbrains.com) rather than hand-authoring
   `.pbxproj` — that tool produces a known-working Compose Multiplatform
   iOS entry point (a SwiftUI `App` hosting a `UIViewController` from the
   shared framework) and is far more reliable than a blind hand-written
   project file nobody here can open to verify.
6. Once there's something to build, add a `macos-latest` job to CI (or a
   separate iOS workflow) so the iOS side finally gets real automated
   verification instead of relying on manual Mac builds.

## On testing today

- **appetize.io** streams either an Android APK or an iOS `.ipa` in a
  browser. It can stream the *existing* Android APK to an iPhone/iPad's
  Safari right now — useful for demoing the current app on an iOS
  device's screen, but it's still Android running under the hood. There's
  no `.ipa` to stream until step 5 above produces one.
- The **desktop web hub** (`desktop/` + `sync-core`'s `WebAssets.kt`) is a
  separate, already-working way to use a simpler version of the app from
  Safari on an iPhone/iPad today, over the same local network — see
  `SYNC.md`. It's not this port and won't become it, but it's real and
  available right now with zero new code.
