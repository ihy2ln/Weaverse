# Native iOS — plan and current status

**Status as of this document: Compose Multiplatform is now wired up on
iOS, replacing the plain-SwiftUI placeholder.** The pipeline is
`:shared` (Kotlin Multiplatform, Android/iOS/JVM) → `iosApp/` (a thin
SwiftUI shell that hosts a `UIViewControllerRepresentable` wrapping
Compose Multiplatform's `ComposeUIViewController`) →
`.github/workflows/ios-build.yml` / `release.yml`'s
`ios-simulator-build` job, which build it for the iOS Simulator on a
`macos-latest` runner. That zip is exactly what appetize.io wants for
iOS Simulator testing — **no Apple Developer account, certificates, or
provisioning profiles needed**, since simulator builds don't require
code signing at all (the Xcode project explicitly sets
`CODE_SIGNING_ALLOWED=NO`/`CODE_SIGNING_REQUIRED=NO`).

**Why the appetize.io screen looked unchanged between builds**: it
wasn't a bug — the walking-skeleton `ContentView.swift` was static
SwiftUI text with nothing behind it to change, so every build rendered
the same screen. Confirmed working (walking-skeleton phase): the user
ran it on appetize.io (iPhone 14 Pro, iOS 16.2) and saw the expected
"Weaverse — iOS shell — shared Kotlin module linked — Running on iOS
16.2," proving Kotlin compiles for iOS, the framework links into Swift,
and the app boots in the Simulator. That plain-text screen is now gone
— `ContentView.swift` hosts real Compose Multiplatform UI
(`RootScreen` in `:shared`'s `commonMain`), so the *next* release's
build will visibly look different, and is the foundation every
subsequent ported screen renders through.

**The Android app is unaffected apart from a new module dependency** —
`:app` now depends on `:shared` (needed so `:app` can keep using
`core.text.*`, which partly lives in `:shared` now), but the package
names didn't change, so no other Android file needed an import update.
Compose Multiplatform in `:shared` doesn't touch `:app`'s existing
Android UI at all — it's new code, not a replacement.

## Platform priority

The user's explicit ordering, for any future trade-off between the three
targets: **Android (APK) first, iOS second, Desktop third.** Android is
the mature, actively-shipping platform and stays the priority for new
Roleplay/Novel features. iOS is the current active build-out (this doc).
Desktop (`desktop/` + the web hub) is lowest priority — it already works
and needs no further investment unless specifically asked for; if
something has to give, iOS work wins over Desktop work.

## How to get a test build

1. Every push to the `Release` workflow (`vX.Y.Z` tag, same as
   Android/Desktop) now attaches `Weaverse-iOS-Simulator-<tag>.zip` to
   that GitHub Release once the `ios-simulator-build` job finishes — it
   can lag a few minutes behind the Android/Desktop assets since macOS
   runners are slower. There's also a faster, separate
   `.github/workflows/ios-build.yml` that fires on any push touching
   `shared/`, `iosApp/`, or the workflow files, for quicker iteration
   without waiting on a full release.
2. Download the zip from the Release page (or the workflow run's
   artifact) — it contains `iosApp.app`.
3. Upload that zip directly to appetize.io as an iOS app.

If a run fails, the workflow's logs will show whether it died in the
"Compile shared module for iOS Simulator" step (a Kotlin/KMP problem) or
the "Build iOS app for Simulator" step (an Xcode project problem).

## Known risk areas — resolved (walking skeleton), new ones open (Compose Multiplatform)

The Xcode project, Swift code, and CI pipeline were originally written
without access to a Mac and flagged here as unverified. They're no
longer a risk: the pipeline has both built successfully in CI and run
correctly on real hardware via appetize.io (iPhone 14 Pro, iOS 16.2),
confirming the hand-written `project.pbxproj`, the `Platform()` Swift/
Kotlin interop, and the `ENABLE_USER_SCRIPT_SANDBOXING = NO` Gradle
build-phase workaround all work as intended.

New, currently-unverified risk from wiring up Compose Multiplatform:

- **`compose-multiplatform = "1.7.3"`** was chosen to match
  `kotlin = "2.0.21"` per JetBrains' published compatibility table, but
  this is from memory, not a live check against a Mac toolchain — if
  it's off, expect a clear Gradle/compiler-plugin version-mismatch
  error, not a silent failure. Because Compose Multiplatform's
  `commonMain` code (the new `RootScreen`) also compiles for the
  Android target as part of `./gradlew assembleRelease`, most of this
  risk actually gets caught by the same Linux CI that already runs on
  every push — only the iOS-specific linking is Mac-only.
- **`MainViewControllerKt.MainViewController()`** relies on the same
  file-name-based Kotlin/Native export convention already confirmed
  reliable for `Platform()` — the file is named `MainViewController.kt`
  specifically so the generated wrapper class matches what
  `ComposeView.swift` calls.
- **`ComposeUIViewController`** (from `androidx.compose.ui.window`) is
  the standard, JetBrains-documented entry point for hosting Compose
  Multiplatform UI in a UIKit view controller — used here exactly as
  their official templates do.

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
2. **(Done)** Walking-skeleton `iosApp/` SwiftUI shell + hand-written
   Xcode project/scheme linking the shared framework via the standard
   `embedAndSignAppleFrameworkForXcode` direct-integration pattern, plus
   CI (`ios-build.yml` and `release.yml`'s `ios-simulator-build` job)
   building it for the Simulator and publishing an appetize.io-ready
   `.app` zip. **Verified on real hardware** via appetize.io — see
   "Known risk areas" above.
3. **(Done)** Moved `core/text/DocumentModel.kt`, `MediaGrid.kt`,
   `DocumentJson.kt`, and `DocumentSerializers.kt` into `:shared`'s
   `commonMain`, with their JUnit tests ported to `kotlin.test` in
   `:shared`'s `commonTest`. This turned out to be far less invasive
   than originally expected: because the package
   (`com.ihy2ln.weaverse.core.text`) didn't change, `:app`'s existing
   `import com.ihy2ln.weaverse.core.text.MediaGrid`-style imports kept
   working unmodified once `:app` gained
   `implementation(project(":shared"))` — no per-file import rewrite
   needed. Left behind deliberately: `MediaStackOps.kt` (uses
   `java.util.UUID`, JVM-only — would need Kotlin's multiplatform
   `kotlin.uuid.Uuid` instead), `SpanEdit.kt`/`FontOption.kt` (depend on
   `androidx.compose.*`), and `Aliases.kt`/`CodexMentionMatcher.kt`/
   `SceneBeatOps.kt` (pure but deferred to keep this diff reviewable).
4. Swap Hilt → Koin and Room → SQLDelight for the pieces that move into
   `:shared`, one feature area at a time (start with something small and
   self-contained, e.g. Settings/preferences, before touching the novel
   or roleplay data models).
5. **(Infrastructure done, screens not yet ported)** `:shared` now
   applies the Compose Multiplatform + Compose compiler plugins;
   `iosApp/ContentView.swift` hosts a `ComposeUIViewController` (via
   `MainViewController()` in `:shared`'s `iosMain`) instead of plain
   SwiftUI, rendering a real `RootScreen` composable from `commonMain`.
   That's the plumbing every ported screen needs — actual app screens
   (Write, Roleplay, Codex, etc.) still need to move from `:app`'s
   `androidx.compose.*` into `:shared`'s `commonMain`, which in turn
   needs their ViewModels' Hilt/Room dependencies swapped out first
   (item 4) since Compose screens are driven by ViewModel state.
6. **(Done)** `ios-simulator-build` in `release.yml` already runs
   automatically on every version tag, attaching the Simulator zip
   straight to the GitHub Release alongside the APK and Desktop zip.

## On testing today

- **appetize.io** — see "How to get a test build" above. Every release
  now ships a Simulator build; it's still the walking-skeleton shell
  plus the moved document/media-grid models, not the full app's UI yet.
- The **desktop web hub** (`desktop/` + `sync-core`'s `WebAssets.kt`) is a
  separate, already-working way to use a simpler version of the app from
  Safari on an iPhone/iPad today, over the same local network — see
  `SYNC.md`. It's not this port and won't become it, but it's real and
  available right now with zero new code.
