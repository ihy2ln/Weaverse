# WeaverSocial public previews and fictional accounts — v1.4.61

## What changed

- Public reshares can rotate through indexed creator, adult video, gaming, and social sites through an optional Brave Image & Video Search key. The source requests are read-only.
- Search video results use their public thumbnail in the feed and link to the credited watch page. Images and GIFs remain local feed attachments.
- Eight optional, named adult accounts can be added from Privacy & filters to the shared Characters Codex. Existing entries are preserved when the picker is reopened; the writer can edit them in the Codex.
- Their Codex entries specify face, hair, skin tone, body build, and distinguishing features for original image prompts. Selfie-style posts use a named character picture or the configured image generator rather than attaching a stranger's face from public search.
- A Codex portrait is passed as a reference to OpenRouter models that support image editing. If that endpoint does not accept reference images, generation retries using the written appearance description.
- GIF requests keep only genuine animated GIFs and retry broader keyless searches. Optional GIPHY and Tenor keys can be entered directly in WeaverSocial media settings.
- Civitai gallery search can use adult public results when a strict prompt match is unavailable, improving media coverage without concealing its origin.
- In-app Help, the manual, guide, and wiki explain the source and account behavior.

## Limits

- A Brave Image & Video Search subscription key is required for indexed OnlyFans, Pornhub, RedGIFs, Fansly, Patreon, and other site previews. Search visibility depends on indexing, the source site's public previews, region, and the account's API access.
- Private, paid, and login-only posts are not imported. Videos are linked public previews, not downloaded video files or inline playback.
- Fictional accounts never claim that a real creator's media depicts them. Their public reshares carry an outside source card.
- Consistent original character portraits require an image-capable OpenRouter model or reachable ComfyUI workflow. Text-only models cannot generate the photos, and public image search cannot guarantee a fictional person's likeness.
