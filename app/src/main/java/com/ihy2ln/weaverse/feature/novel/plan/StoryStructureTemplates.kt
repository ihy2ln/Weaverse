package com.ihy2ln.weaverse.feature.novel.plan

data class StoryBeat(val title: String, val description: String)

data class StoryStructureTemplate(
    val id: String,
    val templateName: String,
    val summary: String,
    val beats: List<StoryBeat>,
)

object StoryStructureTemplates {
    val all: List<StoryStructureTemplate> = listOf(
        StoryStructureTemplate(
            id = "save-the-cat",
            templateName = "Save the Cat! Beat Sheet",
            summary = "15 granular beats for tight, commercial pacing.",
            beats = listOf(
                StoryBeat("Opening Image", "A snapshot of the hero's world before the story begins."),
                StoryBeat("Theme Stated", "Someone states the story's underlying truth, usually to the hero."),
                StoryBeat("Set-Up", "Establish the hero's flaws, world, and status quo."),
                StoryBeat("Catalyst", "The inciting incident that upends the hero's world."),
                StoryBeat("Debate", "The hero hesitates — can they really do this?"),
                StoryBeat("Break Into Two", "The hero commits and enters a new, unfamiliar world."),
                StoryBeat("B Story", "A secondary story begins, often carrying the theme."),
                StoryBeat("Fun and Games", "The \"promise of the premise\" — the trailer moments."),
                StoryBeat("Midpoint", "A false victory or false defeat raises the stakes."),
                StoryBeat("Bad Guys Close In", "External and internal pressure mounts on the hero."),
                StoryBeat("All Is Lost", "The hero's lowest point; something (or someone) dies."),
                StoryBeat("Dark Night of the Soul", "The hero grieves and searches for a way forward."),
                StoryBeat("Break Into Three", "The solution arrives, often from the B Story."),
                StoryBeat("Finale", "The hero storms the castle and resolves the story's problem."),
                StoryBeat("Final Image", "A mirror of the opening image, showing how much has changed."),
            ),
        ),
        StoryStructureTemplate(
            id = "hero-journey",
            templateName = "The Hero's Journey",
            summary = "12 stages of transformation through trials and return — ideal for epic fantasy and adventure.",
            beats = listOf(
                StoryBeat("Ordinary World", "The hero's normal life before the story begins."),
                StoryBeat("Call to Adventure", "The hero is presented with a problem or challenge."),
                StoryBeat("Refusal of the Call", "The hero hesitates, fearing the unknown."),
                StoryBeat("Meeting the Mentor", "A mentor gives the hero guidance, training, or an item."),
                StoryBeat("Crossing the Threshold", "The hero commits to leaving the ordinary world."),
                StoryBeat("Tests, Allies, and Enemies", "The hero learns the rules of the new world."),
                StoryBeat("Approach to the Inmost Cave", "The hero nears the story's central danger."),
                StoryBeat("Ordeal", "The hero faces their greatest fear or biggest crisis."),
                StoryBeat("Reward (Seizing the Sword)", "The hero survives and gains what they sought."),
                StoryBeat("The Road Back", "The hero begins the journey home, consequences in tow."),
                StoryBeat("Resurrection", "A final test where the hero applies everything they've learned."),
                StoryBeat("Return with the Elixir", "The hero returns home transformed, sharing what they gained."),
            ),
        ),
        StoryStructureTemplate(
            id = "story-circle",
            templateName = "Dan Harmon's Story Circle",
            summary = "A simplified 8-step version of the Hero's Journey, popular for character-driven stories.",
            beats = listOf(
                StoryBeat("You", "A character is in a zone of comfort."),
                StoryBeat("Need", "They want something."),
                StoryBeat("Go", "They enter an unfamiliar situation."),
                StoryBeat("Search", "They adapt to it."),
                StoryBeat("Find", "They get what they wanted."),
                StoryBeat("Take", "They pay a heavy price for it."),
                StoryBeat("Return", "They return to their familiar situation."),
                StoryBeat("Change", "Having changed."),
            ),
        ),
        StoryStructureTemplate(
            id = "fichtean-curve",
            templateName = "Fichtean Curve",
            summary = "Constant rising tension through a series of crises — best for thrillers and fast-paced narratives.",
            beats = listOf(
                StoryBeat("Inciting Crisis", "Drop straight into the first crisis — no lengthy setup."),
                StoryBeat("Rising Crisis 2", "A second, escalating complication."),
                StoryBeat("Rising Crisis 3", "Tension rises further; the stakes grow personal."),
                StoryBeat("Rising Crisis 4", "The situation nears its breaking point."),
                StoryBeat("Climax", "The final, highest-stakes crisis."),
                StoryBeat("Falling Action", "The immediate aftermath of the climax."),
                StoryBeat("Resolution", "The new normal is established."),
            ),
        ),
        StoryStructureTemplate(
            id = "seven-point",
            templateName = "Seven-Point Story Structure",
            summary = "Dan Wells' structure — plan backward from the resolution through key turning points.",
            beats = listOf(
                StoryBeat("Hook", "The opposite of the resolution — where the hero starts."),
                StoryBeat("Plot Turn 1", "An event that sets the plot in motion."),
                StoryBeat("Pinch Point 1", "Pressure is applied; show what the antagonist is capable of."),
                StoryBeat("Midpoint", "The hero moves from reaction to action."),
                StoryBeat("Pinch Point 2", "More pressure; things look their worst."),
                StoryBeat("Plot Turn 2", "The hero gains the final piece needed to win."),
                StoryBeat("Resolution", "The hero's growth pays off and the story concludes."),
            ),
        ),
    )
}
