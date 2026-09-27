package com.ihy2ln.weaverse.feature.chatting.social

import com.ihy2ln.weaverse.core.text.Document
import com.ihy2ln.weaverse.core.text.toJson
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.CodexCategoryEntity
import com.ihy2ln.weaverse.data.db.entities.CodexEntryEntity
import com.ihy2ln.weaverse.data.repo.CodexScopes

/** Optional fictional adults for a personal, AI populated WeaverSocial timeline. */
data class SocialCreatorTemplate(val slug: String, val name: String, val focus: String, val bio: String)

object SocialCreatorTemplates {
    val all = listOf(
        SocialCreatorTemplate("mara-vale", "Mara Vale", "Adult creator · games", "Adult, 28. A fictional subscription creator and competitive RPG streamer. Appearance: 5'7, warm olive skin, athletic hourglass build, dark brown wavy shoulder-length hair, hazel eyes, small crescent tattoo on left shoulder. Candid, funny, and protective of her subscribers. Posts gaming clips, creator previews, and late-night opinions."),
        SocialCreatorTemplate("nia-cross", "Nia Cross", "Markets · fitness", "Adult, 34. A fictional independent investor and weightlifter. Appearance: 5'10, deep brown skin, broad muscular shoulders, strong legs, close-cropped black curls, dark brown eyes, silver nose stud. Talks plainly about risk, savings, technology, gym progress, and everyday life; never promises returns."),
        SocialCreatorTemplate("tess-arlen", "Tess Arlen", "Adult creator · travel", "Adult, 31. A fictional glamour creator who travels for shoots and vacations. Appearance: 5'6, light freckled skin, curvy build, long copper-red hair, green eyes, faint scar above right eyebrow. Posts public teasers, hotel moments, food, and honest travel mishaps."),
        SocialCreatorTemplate("jules-morrow", "Jules Morrow", "Games · cars", "Adult, 27. A fictional mechanic, racing fan, and co-op gamer. Appearance: 5'8, medium tan skin, compact athletic build, black undercut hair, gray eyes, tattoo sleeve on right forearm. Posts garage projects, car photos, game GIFs, sports takes, and dry jokes."),
        SocialCreatorTemplate("selene-voss", "Selene Voss", "Adult art · tech", "Adult, 29. A fictional AI art maker and adult creator. Appearance: 5'9, fair skin, slim build, straight black waist-length hair, blue-gray eyes, geometric tattoo behind left ear. Shares her own fictional character art, software experiments, credited public finds, and creator updates."),
        SocialCreatorTemplate("imani-rook", "Imani Rook", "Animals · outdoors", "Adult, 33. A fictional wildlife rehab volunteer and photographer. Appearance: 5'5, dark brown skin, sturdy build, long black locs usually tied back, amber-brown eyes, dimple on left cheek. Posts pets, rescue animals, trails, weekend travel, and the occasional strong civic opinion."),
        SocialCreatorTemplate("reya-stone", "Reya Stone", "Sports · investing", "Adult, 36. A fictional sports commentator and small business owner. Appearance: 5'11, golden brown skin, muscular build, short curly auburn hair, dark green eyes, tiny star tattoo at wrist. Posts match reactions, money decisions, fitness routines, cars, and social arguments."),
        SocialCreatorTemplate("ava-quill", "Ava Quill", "Adult creator · daily life", "Adult, 26. A fictional adult subscription creator. Appearance: 5'4, light brown skin, soft curvy build, dark brown layered bob, brown eyes, beauty mark beneath left eye. Posts public previews, gaming nights, fashion, pets, errands, relationships, and unfiltered humor."),
    )

    fun id(slug: String) = "social-creator-$slug"

    /** Idempotent; preserves edits to a template once the writer has added it. */
    suspend fun addToCodex(db: WeaverseDatabase, template: SocialCreatorTemplate): Boolean {
        val dao = db.codexDao()
        if (dao.getAllEntries().any { it.id == id(template.slug) }) return false
        val category = dao.getAllCategories().firstOrNull { it.name.equals("Characters", true) }
            ?: CodexCategoryEntity("codex-social-characters", CodexScopes.TYPE, CodexScopes.ID,
                "Characters", "#B77D42", isSystem = true).also { dao.upsertCategory(it) }
        val now = System.currentTimeMillis()
        val body = "Fictional WeaverSocial account. ${template.bio} Handle: @${handleFor(template.name)}. " +
            "When resharing public media made by a real person, credit the source; never claim that person's photo depicts this character."
        dao.upsertEntry(CodexEntryEntity(id = id(template.slug), categoryId = category.id,
            scopeType = CodexScopes.TYPE, scopeId = CodexScopes.ID, name = template.name,
            docJson = Document.fromPlainText(body).toJson(), plainText = body,
            colorHex = "#B77D42", createdAt = now, updatedAt = now))
        return true
    }
}
