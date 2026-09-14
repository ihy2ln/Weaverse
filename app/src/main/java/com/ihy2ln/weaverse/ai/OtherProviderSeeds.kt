package com.ihy2ln.weaverse.ai

object OtherProviderSeeds {
    val openai = listOf(
        ModelInfo("gpt-4o-mini", "GPT-4o mini", contextLength = 128_000),
        ModelInfo("gpt-4o", "GPT-4o", contextLength = 128_000),
        ModelInfo("gpt-4.1-mini", "GPT-4.1 mini", contextLength = 1_047_576),
    )
    val anthropic = listOf(
        ModelInfo("claude-sonnet-4-5", "Claude Sonnet 4.5", contextLength = 200_000),
        ModelInfo("claude-3-5-haiku-latest", "Claude Haiku 3.5", contextLength = 200_000),
    )
    val gemini = listOf(
        ModelInfo("gemini-2.0-flash", "Gemini 2.0 Flash", contextLength = 1_048_576),
        ModelInfo("gemini-2.5-flash", "Gemini 2.5 Flash", contextLength = 1_048_576),
    )

    fun seeded(openai: Boolean, anthropic: Boolean, gemini: Boolean): List<ModelInfo> = buildList {
        if (openai) addAll(OtherProviderSeeds.openai.map { it.copy(id = "openai/${it.id}") })
        if (anthropic) addAll(OtherProviderSeeds.anthropic.map { it.copy(id = "anthropic/${it.id}") })
        if (gemini) addAll(OtherProviderSeeds.gemini.map { it.copy(id = "gemini/${it.id}") })
    }
}
