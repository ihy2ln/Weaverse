package com.ihy2ln.weaverse.feature.chatting.social

import com.ihy2ln.weaverse.data.db.entities.RpCharacterEntity

/**
 * The made-up everyday people who fill WeaverSocial between the writer's own characters:
 * they share what they found around the real web, argue in the replies and like things,
 * the way strangers and acquaintances do on Twitter and Facebook. They are fictional,
 * never stored in the Codex, and their ids all start with [PREFIX].
 */
object SocialNpcs {
    const val PREFIX = "npc-"

    /** Topics each person follows; real web posts are matched to people by these. */
    data class Npc(val slug: String, val name: String, val interests: Set<String>, val bio: String, val voice: String)

    val all: List<Npc> = listOf(
        Npc("dana-ruiz", "Dana Ruiz", setOf("games", "tech"), "Night-shift nurse, day-off gamer. Co-op or nothing.", "dry, tired, funny; lowercase; short"),
        Npc("marcus-hale", "Marcus Hale", setOf("sports", "cars"), "Weekend mechanic. Opinions about every ref.", "blunt, loud, sports slang, CAPS when excited"),
        Npc("priya-nair", "Priya Nair", setOf("science", "tech", "news"), "Lab tech. Explains things nobody asked about, lovingly.", "curious, precise, a little nerdy"),
        Npc("tom-becker", "Tom Becker", setOf("money", "news"), "Index funds and bad coffee. Not financial advice.", "measured, skeptical, uses numbers"),
        Npc("kayla-brooks", "Kayla Brooks", setOf("pets", "food"), "Two rescue dogs, one feral cat, zero free time.", "warm, lots of emoji, exclamation marks"),
        Npc("jin-park", "Jin Park", setOf("games", "art"), "Pixel artist. Speedruns things that shouldn't be speedrun.", "hype, gamer shorthand, memes"),
        Npc("rosa-delgado", "Rosa Delgado", setOf("food", "travel"), "Cooks for twelve every Sunday. Travels for the food.", "chatty, family stories, generous"),
        Npc("ethan-cole", "Ethan Cole", setOf("tech", "science"), "Backend dev. Has strong feelings about tabs.", "sarcastic, technical, terse"),
        Npc("grace-okafor", "Grace Okafor", setOf("news", "books"), "Teacher, reader, occasional ranter.", "thoughtful, firm, full sentences"),
        Npc("liam-walsh", "Liam Walsh", setOf("outdoors", "photography"), "Hiking every trail within 300 miles, slowly.", "calm, descriptive, nature-y"),
        Npc("zoe-carter", "Zoe Carter", setOf("music", "art"), "Bassist in a band you haven't heard of yet.", "enthusiastic, niche references"),
        Npc("omar-haddad", "Omar Haddad", setOf("cars", "tech"), "EV convert. Will tell you about range.", "friendly, evangelist energy"),
        Npc("bella-nguyen", "Bella Nguyen", setOf("fashion", "photography"), "Thrift finds and film cameras.", "stylish, playful, short captions"),
        Npc("frank-miller", "Frank Miller", setOf("news", "sports"), "Retired. Has time and a keyboard.", "grumpy uncle, Facebook-style, ellipses..."),
        Npc("aisha-khan", "Aisha Khan", setOf("science", "space"), "Astronomy club president. Look up.", "awestruck, earnest, facts"),
        Npc("noah-fischer", "Noah Fischer", setOf("games", "movies"), "Film student. Every game is cinema, apparently.", "pretentious on purpose, self-aware"),
        Npc("maya-lopez", "Maya Lopez", setOf("fitness", "food"), "Lifts heavy, eats tacos, repeats.", "motivational but self-mocking"),
        Npc("sam-oconnor", "Sam O'Connor", setOf("memes", "games"), "Professional lurker. Amateur meme poster.", "deadpan, one-liners, reaction-heavy"),
        Npc("hana-sato", "Hana Sato", setOf("art", "anime"), "Illustrator. Drawing the thing you just said.", "cute, excitable, kaomoji"),
        Npc("diego-santos", "Diego Santos", setOf("sports", "memes"), "Football twice a week, highlights daily.", "hype, banter, rivalries"),
        Npc("claire-dubois", "Claire Dubois", setOf("travel", "books"), "Train windows and paperbacks.", "wistful, well-written"),
        Npc("reggie-james", "Reggie James", setOf("music", "news"), "Radio DJ, local legend (self-appointed).", "smooth, jokey, big personality"),
        Npc("yara-haddad", "Yara Haddad", setOf("pets", "outdoors"), "Horse girl, grown up. Still a horse girl.", "sweet, outdoorsy, earnest"),
        Npc("ben-kowalski", "Ben Kowalski", setOf("tech", "money"), "Builds PCs, sells PCs, regrets GPU prices.", "wry, specs-obsessed"),
        Npc("lucia-romano", "Lucia Romano", setOf("food", "fashion"), "Pastry chef. Butter is a personality.", "dramatic, passionate, Italian flair"),
        Npc("ivan-petrov", "Ivan Petrov", setOf("space", "science", "tech"), "Aerospace engineer. Rockets are just tubes, mostly.", "understated, precise, dry humor"),
        Npc("chloe-adams", "Chloe Adams", setOf("movies", "memes"), "Watches trailers frame by frame.", "fangirl, theories, spoilers warnings"),
        Npc("kwame-mensah", "Kwame Mensah", setOf("sports", "fitness"), "Marathon runner. Asks what your pace is.", "upbeat, disciplined"),
        Npc("emily-stone", "Emily Stone", setOf("news", "pets"), "Local reporter, cat foster.", "concise, factual, soft for animals"),
        Npc("raj-patel", "Raj Patel", setOf("money", "games"), "Accountant by day, strategy gamer by night.", "analytical, punny"),
    )

    val characters: List<RpCharacterEntity> = all.map { npc ->
        RpCharacterEntity(
            id = PREFIX + npc.slug,
            name = npc.name,
            description = "${npc.bio} Interests: ${npc.interests.joinToString(", ")}. An ordinary person on WeaverSocial, from our own world.",
            personality = "Voice: ${npc.voice}.",
            creatorNotes = npc.bio,
            createdAt = 1_690_000_000_000L + npc.slug.hashCode().toLong().mod(90_000_000_000L),
        )
    }

    fun isNpc(id: String?): Boolean = id?.startsWith(PREFIX) == true

    fun byId(id: String?): Npc? = id?.takeIf(::isNpc)?.let { key -> all.firstOrNull { PREFIX + it.slug == key } }

    /** People who'd plausibly share something on [topic]; everyone when nobody matches. */
    fun forTopic(topic: String): List<RpCharacterEntity> {
        val matched = all.filter { topic in it.interests }.map { npc -> characters.first { it.id == PREFIX + npc.slug } }
        return matched.ifEmpty { characters }
    }
}
