package com.ihy2ln.weaverse.core.manga

/** Shared tag spelling and include/exclude semantics; native extension filters stay untouched. */
object CatalogTags {
    fun normalize(value: String): String = when (val key = value.lowercase().filter(Char::isLetterOrDigit)) {
        "shonen" -> "shounen"
        "shojo" -> "shoujo"
        "shoujoai", "yuri", "girlslove" -> "girlslove"
        "shounenai", "yaoi", "boyslove" -> "boyslove"
        "sciencefiction" -> "scifi"
        "femaleprotagonist", "femalelead" -> "femalelead"
        "maleprotagonist", "malelead" -> "malelead"
        "mahoushoujo", "magicalgirl", "magicalgirls" -> "magicalgirls"
        "longstrip", "longscroll" -> "longstrip"
        "webtoon", "webcomic" -> "webcomic"
        "oneshot" -> "oneshot"
        else -> key
    }

    fun cycle(value: String, option: String): String {
        val items = value.split(',').map(String::trim).filter(String::isNotEmpty)
        val current = items.firstOrNull { normalize(it.removePrefix("!")) == normalize(option) }
        val rest = items.filterNot { normalize(it.removePrefix("!")) == normalize(option) }
        return (rest + when {
            current == null -> listOf(option)
            current.startsWith("!") -> emptyList()
            else -> listOf("!$option")
        }).joinToString(",")
    }

    fun matches(tags: List<String>, selections: String, matchAny: Boolean): Boolean {
        val wanted = selections.split(',').map(String::trim).filter(String::isNotEmpty)
        val actual = tags.map(::normalize).toSet()
        val included = wanted.filterNot { it.startsWith("!") }.map(::normalize)
        val excluded = wanted.filter { it.startsWith("!") }.map { normalize(it.drop(1)) }
        return excluded.none { it in actual } && (included.isEmpty() ||
            if (matchAny) included.any { it in actual } else included.all { it in actual })
    }
}
