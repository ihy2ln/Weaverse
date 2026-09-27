# WeaverSocial 18+ and media feed checkpoint

Delivered version: 1.4.60 (code 192), Room schema 33.

## Implemented

- Independent WeaverSocial 18+ preference, enabled by default. Turning it off hides sexual posts, replies, alerts and quoted content and stops new adult topic prompts and adult image search, including the Social picture picker.
- Tappable title badge and Privacy & filters control. Media settings hold optional encrypted Civitai and Brave keys, an OpenRouter image model reference, and an optional ComfyUI URL/API workflow with `__PROMPT__`.
- Twelve rotating post directions with four adult slots. Other directions cover games, investing, animals, travel, everyday life, sports, cars, technology and fitness.
- Public image previews from Civitai and other sources are read-only, downloaded to Pictures, and displayed as attributed bot reshares. Source-classified adult images are labeled sexual on attached posts. Web and AI media have structured source fields. The 32→33 migration preserves existing rows.
- One original fictional image attempt per refresh, with web/library fallback. Search retries alternate and broader results; GIFs are scheduled in the feed. Home shows two posts before server/activity/trend sections.
- In-app Help, user guide, manual and wiki page updated.
- Civitai images use the public `/api/v1/images` gallery with local metadata matching, uploader credit, source links, and a PG/PG-13 mask when 18+ is off. The optional token is sent as a bearer header. Civitai's video gallery is linked from media settings; inline posts currently attach images and GIFs.

## Verification

- `:app:compileDebugKotlin` and `:app:assembleRelease` passed after the Civitai integration.
- `:app:testDebugUnitTest` passed: 566 tests, zero failures or skips. `:app:compileDebugAndroidTestKotlin` also passed.
- Applied the 32→33 attribution SQL to tables created from the checked-in v32 Room schema in an in-memory SQLite database; existing social and media rows and new defaults were preserved.
- The release APK reports package `com.ihy2ln.weaverse`, version code 192 and version name 1.4.60. Its signing certificate SHA-256 is `f16db5088b05af8c941a0e23eb2364dab470dd4ae07c3023f858708f6451fc05`, matching v1.4.59.
- The copied APK at `Beta.Test.Build/weaverse-v1.4.60.apk` has SHA-256 `55bc5cd586bec0d866515ec4c758392bdac03fd37c11a75e1ab431f9f859959e` and is 180,583,646 bytes.

No Android emulator was installed on this host, so the instrumentation migration test was compiled but not run on a device. Provider-specific media density and adult availability still depend on network access, account settings, and the configured image service.
