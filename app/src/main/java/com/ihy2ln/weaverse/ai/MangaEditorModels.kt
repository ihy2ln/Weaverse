package com.ihy2ln.weaverse.ai

/** Only synchronous text models with explicit text-output metadata belong in OCR/translation. */
object MangaEditorModels {
    fun text(models: List<ModelInfo>) = models.filter {
        it.available && "Text output" in it.tags && !it.isTts && !it.generatesImages &&
            !it.id.contains(":batch") && !it.id.contains("~")
    }.distinctBy { it.id }.sortedBy { it.displayName.lowercase() }
    fun vision(models: List<ModelInfo>) = text(models).filter { it.supportsImages }
}
