package com.ihy2ln.weaverse.core.text

/** Snapshot the visible state before switching, including manual changes to a version. */
fun MediaBlock.savedMangaVersions(): List<MangaPageVersion> {
    val original = originalMediaId ?: mediaId
    val versions = mangaVersions.toMutableList()
    if (versions.none { it.id == "original" }) versions.add(0, MangaPageVersion("original", "Original", original))
    val currentId = activeMangaVersionId.ifBlank { if (mediaId == original && overlays.isEmpty()) "original" else "previous" }
    if (currentId != "original") {
        val index = versions.indexOfFirst { it.id == currentId }
        val snapshot = versions.getOrNull(index)?.copy(mediaId = mediaId, overlays = overlays)
            ?: MangaPageVersion(currentId, "Previous edits", mediaId, overlays)
        if (index >= 0) versions[index] = snapshot else versions.add(snapshot)
    } else if (overlays.isNotEmpty() || mediaId != original) {
        versions.add(MangaPageVersion(java.util.UUID.randomUUID().toString(), "Manual edits", mediaId, overlays))
    }
    return versions
}

fun MediaBlock.selectMangaVersion(id: String): MediaBlock {
    val saved = savedMangaVersions()
    val version = saved.firstOrNull { it.id == id } ?: return this
    return copy(mediaId = version.mediaId, overlays = version.overlays,
        originalMediaId = originalMediaId ?: mediaId, mangaVersions = saved,
        activeMangaVersionId = id, variantKind = if (id == "original") "original" else "translated")
}
