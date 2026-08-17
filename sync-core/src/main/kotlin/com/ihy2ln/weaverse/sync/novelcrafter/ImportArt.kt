package com.ihy2ln.weaverse.sync.novelcrafter

/**
 * Bundled pictures attached after a Novelcrafter import so Novel / Manga / Roleplay
 * each have visible art (the Word ZIP itself has no images).
 */
data class ImportArtPiece(
    val id: String,
    val fileName: String,
    val caption: String,
    val section: String,
    val attach: String,
)

object ImportArt {
    val pieces: List<ImportArtPiece> = listOf(
        ImportArtPiece(
            id = "art-novel-forest",
            fileName = "novel-forest-path.jpg",
            caption = "Elysium Vale — forest path",
            section = "novel",
            attach = "first-scene",
        ),
        ImportArtPiece(
            id = "art-novel-planet",
            fileName = "novel-elysium-orbit.jpg",
            caption = "Elysium Vale from orbit",
            section = "novel",
            attach = "cover",
        ),
        ImportArtPiece(
            id = "art-manga-void",
            fileName = "manga-cosmic-void.jpg",
            caption = "Pulled through the void",
            section = "manga",
            attach = "manga-panel",
        ),
        ImportArtPiece(
            id = "art-rp-temple",
            fileName = "roleplay-temple-glass.jpg",
            caption = "Hidden temple in a glass",
            section = "roleplay",
            attach = "rp-background",
        ),
        ImportArtPiece(
            id = "art-rp-farm",
            fileName = "roleplay-farmstead.jpg",
            caption = "Starting farmstead",
            section = "roleplay",
            attach = "location",
        ),
        ImportArtPiece(
            id = "art-codex-plant",
            fileName = "codex-gacha-plant.jpg",
            caption = "Gacha relic plant",
            section = "novel",
            attach = "object",
        ),
    )

    fun loadBytes(fileName: String): ByteArray? {
        val path = "import-art/$fileName"
        return ImportArt::class.java.classLoader.getResourceAsStream(path)?.use { it.readBytes() }
    }

    fun mimeFor(fileName: String): String = when {
        fileName.endsWith(".png", ignoreCase = true) -> "image/png"
        fileName.endsWith(".webp", ignoreCase = true) -> "image/webp"
        else -> "image/jpeg"
    }
}
