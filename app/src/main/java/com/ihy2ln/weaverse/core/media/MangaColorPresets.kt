package com.ihy2ln.weaverse.core.media

/** Palette/treatment suggestions only; source-preservation rules always take precedence. */
object MangaColorPresets {
    val guides: List<Pair<String, String>> = listOf(
        "Original art / restrained" to MangaColorPolicy.DEFAULT_GUIDE,
        "Classic anime cel color" to "Clean flat cel colors, restrained saturation and clear color separation. Color existing shadows only; no extra highlights or shading.",
        "1990s anime palette" to "Warm off-whites, muted teal and navy, dusty reds and gentle skin tones. Preserve the source shadows; no film grain or redraw.",
        "Shoujo pastel palette" to "Soft rose, lavender, peach and pale blue with delicate low-saturation color. No added flowers, sparkles, gradients or changes to faces.",
        "Manga color-page inks" to "Crisp printed color-page palette with bold primary accents and warm neutrals. Keep every ink line, hatch and screentone unchanged.",
        "Toriyama-inspired palette" to "Palette inspiration only: cheerful orange, cobalt blue, leaf green and warm cream with clean flat color separation. Do not imitate character designs or change linework, proportions or shading.",
        "Tezuka-inspired palette" to "Palette inspiration only: simple bright primary colors, warm cream and restrained secondary colors. Preserve the original drawing, anatomy and existing shadows exactly.",
        "Dark fantasy manga palette" to "Desaturated iron blue, charcoal, leather brown and small burgundy accents. Keep the existing contrast and hatching; no added shadows, texture or objects.",
    )
}
