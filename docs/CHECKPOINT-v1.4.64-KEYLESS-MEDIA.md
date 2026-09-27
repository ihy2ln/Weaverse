# WeaverSocial 1.4.64: keyless media first

WeaverSocial can search public Civitai, Gelbooru and Danbooru gallery items
without a paid search key. The 18+ switch and adult post label gate explicit
gallery searches. Danbooru uses its public `posts.json` endpoint, limits
results to explicit-rated image or GIF files, checks tags for known underage
terms, and retains the post page and uploader credit. Existing text relevance,
source URL, checksum and visual-reuse checks still decide whether a result is
attached. OpenRouter image generation continues to use the user's configured
image model for fictional character media.

Brave remains optional for indexed previews from creator sites, adult hubs and
forums; an existing ChatGPT, Claude, Cursor or OpenRouter subscription does not
provide a Brave Search API key. A public gallery may still rate-limit anonymous
requests or deny access in a region. Private and paid content is not imported.

Release: `assembleRelease` succeeded. `weaverse-v1.4.64.apk` was copied to
`S:\AI\Novel\Weaververse\Beta.Test.Build` with version code 196 and version
name 1.4.64. The copy matches the build output, with SHA-256
`79D9C87DCC7AF3A19BAF0F677D3D74B8F1C0707DED265F2A9018063DF209356D`.
APK signature verification passed; its certificate SHA-256 matches 1.4.63:
`f16db5088b05af8c941a0e23eb2364dab470dd4ae07c3023f858708f6451fc05`.
