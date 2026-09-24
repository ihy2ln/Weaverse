# Translation freeze/crash investigation

MuMu's recorded Weaverse exit history did not contain the reported crash. Its crash buffer contained old uiautomator native crashes, not evidence of a Weaverse translation crash. Exact user-device failure remains unconfirmed.

Confirmed code defects repaired:

- Translation performed synchronous bitmap decoding, cleanup, layout and encoding from a main-thread coroutine. The translation job now runs on Dispatchers.Default; history/persistence coordination returns to Main.
- The translation job caught only cancellation. Ordinary failures now report a failed translation instead of escaping the coroutine into the uncaught exception handler. Memory exhaustion has a separate message. Cancellation is rethrown after updating status.
- Job startup now claims busy state before suspension and rejects an already-active job.
- Path-based vision encoding releases its owned bitmap in finally.
- Rendering releases the cleaned bitmap on failed/cancelled verification and rendering exceptions; successful output ownership still transfers to the caller.

Debug compilation and signed release build, including R8, passed. Certificate matched the previously installed APK. Installed with adb install -r in MuMu; app launched and stayed running without a new AndroidRuntime fatal exception. A complete live translation has not been verified: the shared prompt dock obscures the imported editor's bottom translation controls in this emulator layout.

Artifact: S:/AI/Novel/Weaververse/Beta.Test.Build/weaverse-v1.4.17-translation-stability-20260915-signed.apk

## Colorization follow-up

The same main-thread launch and cancellation-only catch existed in startMangaColorization. The image generation service explicitly throws AIError for invalid provider, missing key, HTTP failures, and absent image output. Those exceptions previously escaped the colorization job.

Colorization now claims busy state before suspension, rejects overlapping jobs, runs processing on Dispatchers.Default, returns to Main for history coordination, rethrows cancellation, and reports ordinary failures or insufficient memory while preserving saved pages. This extends the shared bitmap release fix from the translation update. A successful live provider colorization remains unverified; handling provider errors does not establish that the user's exact crash has been reproduced.

Combined release build and signature verification passed. Installed in MuMu with `adb install -r`, launched, and confirmed running without a new startup AndroidRuntime error. Focused ImageOpsPanelDetectionTest, OpenRouterModelsTest and StoryboardPagesTest passed. Earlier focused MangaTranslationPlanTest and MangaEnglishValidationTest also passed.

Combined artifact: S:/AI/Novel/Weaververse/Beta.Test.Build/weaverse-v1.4.17-translation-colorization-stability-20260915-signed.apk
