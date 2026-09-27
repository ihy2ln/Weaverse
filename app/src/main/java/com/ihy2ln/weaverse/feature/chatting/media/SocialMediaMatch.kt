package com.ihy2ln.weaverse.feature.chatting.media

/** Only provider supplied metadata is evidence. The search query itself is never evidence. */
internal object SocialMediaMatch {
    private val generic = setOf("photo", "image", "picture", "media", "video", "gif", "adult", "creator", "nude", "erotic", "beautiful", "woman", "women", "man", "people", "person", "art", "style", "digital", "realistic", "portrait", "reaction", "meme", "screenshot", "today", "life", "good", "new", "the", "and", "with", "from", "this", "that", "about", "some", "their", "your", "just", "clearly", "public", "social", "post", "content")
    private val aliases = listOf(
        setOf("dog", "dogs", "puppy", "puppies", "canine"),
        setOf("cat", "cats", "kitten", "kittens", "feline"),
        setOf("gpu", "graphics", "card", "rtx", "geforce", "radeon"),
        setOf("garage", "mechanic", "workshop", "auto", "car", "vehicle"),
        setOf("game", "gaming", "gamer", "videogame", "esports"),
        setOf("stock", "stocks", "investing", "investment", "market", "trading", "finance"),
        setOf("vacation", "travel", "trip", "holiday", "beach", "resort"),
    )

    private fun words(text: String): Set<String> = text.lowercase().split(Regex("[^a-z0-9]+"))
        .filter { it.length >= 3 && it !in generic }.toSet()

    fun score(picture: WebPicture, query: String, postText: String): Int {
        val evidence = words(picture.title + " " + picture.description)
        if (evidence.isEmpty()) return 0
        val queryWords = words(query)
        val wanted = queryWords + words(postText)
        if (wanted.isEmpty()) return 0
        fun matches(word: String): Boolean = word in evidence || aliases.any { word in it && it.any(evidence::contains) }
        if (queryWords.none(::matches)) return 0
        val matched = wanted.count(::matches)
        val distinctive = wanted.filter { it !in setOf("adult", "nude", "sexual") }
        if (distinctive.isEmpty()) return 0
        return if (matched >= 2 || (distinctive.size <= 2 && matched >= 1)) matched else 0
    }
}
