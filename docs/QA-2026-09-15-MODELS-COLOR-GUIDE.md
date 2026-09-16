# Android editor model categories and color guide

## Implemented

- OCR requires image input and text output. Translation requires text output and excludes image/audio generators, batch entries and entries without capability metadata.
- Color/image edit now uses OpenRouter's dedicated `/images/models` catalog, requiring text/image input, image output and at least one supported input reference. Generation-only and vector entries are excluded. Newly listed compatible models appear on refresh; names alone do not establish compatibility.
- Image requests use `/images` with `input_references`, rather than chat requests with text/image modalities. This addresses a concrete protocol mismatch affecting reference-image models such as FLUX.2; it does not establish the cause of every reported provider failure.
- A saved color guide appears below the editor's color/image model selector. Save the guide before starting page/chapter colorization. Combined color-and-translate operations use it too.
- Removed the old anime/cinematic-lighting instructions. Color-only prompts prohibit redraw, new shading, changed lettering or composition, and place palette preferences below source preservation.
- Preserve original drawing and shading is enabled by default. It transfers proposed chroma while retaining source luminance/alpha and existing colored pixels. Pure black/white pixels stay unchanged, so output can be subtle. Turning it off accepts the AI-rendered image and may alter artwork.
- Preservation rejects aspect-ratio changes and sources exceeding 8 megapixels before processing (source-size validation occurs before generation). Originals are not overwritten. This is not semantic verification that AI colors match every object.
- Failure messages include the actual provider/error detail rather than only suggesting connection checks.

## Verification

- Inspected official OpenRouter Image API documentation and public live catalog, including FLUX.2 Pro reference support: https://openrouter.ai/docs/guides/overview/multimodal/image-generation
- `:app:testReleaseUnitTest --tests '*OpenRouter*Test' --tests '*MangaColorPolicyTest'`: 31 tests, zero failures/errors. Includes mock HTTP request/response, category exclusions, image decoding and luminance preservation.
- `:app:assembleRelease`: successful, including release minification.
- APK signature verified; certificate SHA-256 matches the preceding local build: `f16db5088b05af8c941a0e23eb2364dab470dd4ae07c3023f858708f6451fc05` (existing Android Debug certificate).
- No paid live generation, account-specific provider availability check, or MuMu interaction test performed for this change. Catalog eligibility must not be represented as every model having passed a real generation test. Android bitmap integration still requires device testing.

## Scope

Android/OpenRouter only. PC/browser harness work remains deferred in `PC-BROWSER-HARNESS-BACKLOG.md`. Local APK delivery only; no GitHub release.
