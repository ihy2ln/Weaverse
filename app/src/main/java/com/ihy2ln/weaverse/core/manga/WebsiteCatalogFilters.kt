package com.ihy2ln.weaverse.core.manga

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup

/** Labels and opaque option values are kept together; never send a translated label as an ID. */
internal data class WebsiteOption(val value: String, val label: String)
internal class WebsiteSelect(val parameter: String, name: String, val options: List<WebsiteOption>, initial: Int = 0) :
    Filter.Select<String>(name, options.map { it.label }.toTypedArray(), initial)
internal class WebsiteCheck(val value: String, name: String) : Filter.CheckBox(name)
internal class WebsiteTri(val value: String, name: String) : Filter.TriState(name)
internal class WebsiteGroup(val parameter: String, name: String, options: List<WebsiteOption>, val excludeParameter: String? = null) :
    Filter.Group<Filter<*>>(name, options.map { if (excludeParameter == null) WebsiteCheck(it.value, it.label) else WebsiteTri(it.value, it.label) })
internal class WebsiteText(val parameter: String, name: String) : Filter.Text(name)

internal object WebsiteCatalogFilters {
    fun mangaFire(json: String): FilterList {
        val options = Json.parseToJsonElement(json).jsonObject.getValue("data").jsonObject
        fun choices(key: String) = (options[key] as? JsonArray).orEmpty().map { item ->
            val obj = item.jsonObject
            WebsiteOption((obj["value"] ?: obj.getValue("id")).jsonPrimitive.content,
                (obj["label"] ?: obj.getValue("name")).jsonPrimitive.content)
        }
        val sorts = choices("sorts")
        return FilterList(listOf(
            WebsiteSelect("sort", "Sort by", sorts, sorts.indexOfFirst { it.value == "chapter_updated_at:desc" }.coerceAtLeast(0)),
            WebsiteGroup("content_rating", "Content rating", choices("contentRatings")).apply {
                state.filterIsInstance<WebsiteCheck>().forEach { it.state = it.value in setOf("safe", "suggestive") }
            },
            WebsiteGroup("types", "Type", choices("types")),
            WebsiteGroup("statuses", "Publication status", choices("statuses")),
            WebsiteSelect("genres_mode", "Match included genres", listOf(WebsiteOption("and", "All (AND)"), WebsiteOption("or", "Any (OR)"))),
            WebsiteGroup("genres_in", "Genres", choices("genres"), "genres_ex"),
            WebsiteGroup("genres_in", "Formats", choices("formats"), "genres_ex"),
            WebsiteGroup("theme_ids", "Themes", choices("themes")),
            WebsiteSelect("theme_mode", "Match themes", listOf(WebsiteOption("and", "All (AND)"), WebsiteOption("or", "Any (OR)"))),
            WebsiteGroup("demographics", "Demographic", choices("demographics")),
            WebsiteGroup("languages", "Chapter language", listOf("en" to "English", "fr" to "French", "es" to "Spanish", "es-la" to "Spanish (LATAM)", "pt" to "Portuguese", "pt-br" to "Portuguese (BR)", "ja" to "Japanese").map { WebsiteOption(it.first, it.second) }),
            WebsiteText("min_chap", "Minimum chapters"), WebsiteText("year_from", "Release year from"), WebsiteText("year_to", "Release year to"),
        ))
    }
    fun rawkuma(html: String): FilterList {
        val doc = Jsoup.parse(html)
        val filters = doc.select("[data-group-selector]").mapNotNull { group ->
            val input = group.selectFirst("input[name^=the_]") ?: return@mapNotNull null
            val parameter = input.attr("name")
            if (parameter !in setOf("the_status", "the_type", "the_genre", "the_orderby")) return@mapNotNull null
            val options = group.select("li[value]").map { WebsiteOption(it.attr("value"), it.text().trim()) }.distinctBy { it.value }
            if (options.isEmpty()) return@mapNotNull null
            val name = when (parameter) { "the_status" -> "Status"; "the_type" -> "Type"; "the_genre" -> "Genres"; else -> "Sort by" }
            if (parameter == "the_orderby") WebsiteSelect(parameter, name, options, options.indexOfFirst { it.value == input.attr("value") }.coerceAtLeast(0))
            else WebsiteGroup(parameter, name, options)
        }
        require(filters.isNotEmpty()) { "Rawkuma's filter form changed. Open the website or retry; filters were not guessed." }
        return FilterList(filters)
    }

    fun comix(html: String): FilterList {
        val data = Jsoup.parse(html).selectFirst("script#initial-data")?.data().orEmpty()
        val options = Json.parseToJsonElement(data).jsonObject["list"]?.jsonObject?.get("options")?.jsonObject
            ?: error("Comix's filter definitions are unavailable. Retry or open the website.")
        fun choices(key: String) = (options[key] as? JsonArray).orEmpty().mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val id = (obj["id"] as? JsonPrimitive)?.content ?: return@mapNotNull null
            val label = (obj["label"] as? JsonPrimitive)?.content ?: return@mapNotNull null
            WebsiteOption(id, label)
        }
        val sorts = (options["sorts"] as? JsonArray).orEmpty().map { WebsiteOption(it.jsonArray[0].jsonPrimitive.content, it.jsonArray[1].jsonPrimitive.content) }
        require(sorts.isNotEmpty()) { "Comix did not return sorting options." }
        return FilterList(listOf(
            WebsiteSelect("sort", "Sort by", sorts, sorts.indexOfFirst { it.value == "chapter_updated_at:desc" }.coerceAtLeast(0)),
            WebsiteGroup("content_rating", "Content rating", listOf("safe", "suggestive", "erotica", "pornographic").map { WebsiteOption(it, it.replaceFirstChar(Char::uppercase)) }).apply {
                state.filterIsInstance<WebsiteCheck>().forEach { it.state = it.value in setOf("safe", "suggestive") }
            },
            WebsiteGroup("types", "Type", choices("types")),
            WebsiteSelect("genres_mode", "Match included tags", listOf(WebsiteOption("and", "All (AND)"), WebsiteOption("or", "Any (OR)"))),
            WebsiteGroup("genres_in", "Genres", choices("genres"), "genres_ex"),
            WebsiteGroup("genres_in", "Formats", choices("formats"), "genres_ex"),
            WebsiteGroup("demos", "Demographic", choices("demographics")),
            WebsiteGroup("statuses", "Publication status", choices("statuses")),
            WebsiteText("min_chap", "Minimum chapters"),
            WebsiteSelect("year_from", "Release year from", listOf(WebsiteOption("", "Any")) + (options["years"] as? JsonArray).orEmpty().map { WebsiteOption(it.jsonPrimitive.content, it.jsonPrimitive.content) }),
            WebsiteSelect("year_to", "Release year to", listOf(WebsiteOption("", "Any")) + (options["years"] as? JsonArray).orEmpty().map { WebsiteOption(it.jsonPrimitive.content, it.jsonPrimitive.content) }),
        ))
    }

    fun url(base: String, source: String, query: String, page: Int, filters: FilterList): String {
        require(page >= 0)
        val params = linkedMapOf<String, MutableList<String>>()
        fun put(key: String, value: String) { if (value.isNotBlank()) params.getOrPut(key) { mutableListOf() }.add(value) }
        filters.forEach { filter -> when (filter) {
            is WebsiteSelect -> put(filter.parameter, filter.options[filter.state].value)
            is WebsiteText -> {
                require(filter.state.isBlank() || filter.state.toIntOrNull()?.let { it >= 0 } == true) { "${filter.name} must be a positive whole number." }
                put(filter.parameter, filter.state.trim())
            }
            is WebsiteGroup -> filter.state.forEach { option -> when (option) {
                is WebsiteCheck -> if (option.state) put(filter.parameter, option.value)
                is WebsiteTri -> when (option.state) {
                    Filter.TriState.STATE_INCLUDE -> put(filter.parameter, option.value)
                    Filter.TriState.STATE_EXCLUDE -> filter.excludeParameter?.let { put(it, option.value) }
                    else -> Unit
                }
                else -> Unit
            } }
            else -> Unit
        } }
        val url = base.toHttpUrl().newBuilder().encodedPath(if (source == "rawkuma") "/manga/" else "/browse")
        put(when (source) { "rawkuma" -> "search_term"; "mangafire" -> "keyword"; else -> "q" }, query.trim())
        if (source != "rawkuma") put("page", (page + 1).toString())
        params.forEach { (key, values) -> url.addQueryParameter(key, values.distinct().joinToString(",")) }
        return url.build().toString()
    }
}
