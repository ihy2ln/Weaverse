package com.ihy2ln.weaverse.core.manga

/** These choose the follow-up editor action; original page downloads never change format. */
enum class MangaDownloadTreatment(val label: String, val editorAction: String?) {
    Original("Original only", null),
    Translate("Translate", "TranslateChapter"),
    Colorize("Colorize", "ColorChapter"),
    Both("Colorize + Translate", "ColorTranslateChapter");

    companion object {
        fun decode(value: String?): MangaDownloadTreatment = entries.firstOrNull { it.name == value } ?: Original
    }
}
