# WeaverSocial 1.4.62: fresh writing and matched media

New timeline posts compare their visible text with the newest 300 bot posts and
other accepted drafts in the same refresh. Each writer also receives their own
recent posts, an assigned topic, and a distinct format. A suspected repeat
gets one model rewrite. If the rewrite still repeats, it is not saved.
Generated replies use the same process with shorter thread history.

Automatic image and GIF results are ranked from provider titles, descriptions,
and generation metadata against the post's subject. The original search query
is never treated as result metadata. A Civitai result without relevant prompt
metadata is not selected. Broad random fallback searches were removed.

Before importing external media, WeaverSocial reserves its canonical source
URL. Downloaded bytes are checked against recent checksums and a 64-bit
perceptual hash of the first image frame. The URL, checksum, and image hash
also prevent concurrent posts in one refresh from sharing media. Repeated
creators are limited. A poor match leaves the post text-only with a media
status. Existing saved posts and attachments are not changed by refresh.

The change uses existing post/media records and `MediaEntity.checksum`; Room
remains at version 33. Manual media picker, servers, and DMs retain their
existing behavior.

Verification: five focused unit checks passed (repeated posts and replies,
fresh gaming angles, and the dog/interior, GPU/ballroom, garage/portrait media
failures). `assembleRelease` succeeded. The copied `weaverse-v1.4.62.apk`
reports version code 194 and has the same signing certificate as 1.4.61.
