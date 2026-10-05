package com.ihy2ln.weaverse.feature.games.battle.engine

/**
 * The Adams Haven cast and their cards, ported from the Unity card game's BuiltinContent (numbers unchanged) so the
 * native battle plays exactly like the full game.
 */
object BuiltinContent {
    private fun c(
        id: String, name: String, owner: String, ep: Int, ap: Int, power: Float = 0f, target: String = "Enemy",
        status: String = "", magnitude: Float = 0f, duration: Int = 0, heal: Float = 0f, magic: Boolean = false,
        draw: Int = 0, epGain: Int = 0, apGain: Int = 0, transferEp: Boolean = false, transferAp: Boolean = false,
    ) = CardDef(
        id = id, name = name, owner = owner, ep = ep, ap = ap, power = power, target = target, status = status,
        magnitude = magnitude, duration = duration, heal = heal, magic = magic, draw = draw, epGain = epGain,
        apGain = apGain, transferEp = transferEp, transferAp = transferAp, art = "builtin:$id",
    )

    private fun u(
        id: String, name: String, owner: String, power: Float, target: String, status: String = "",
        magnitude: Float = 0f, duration: Int = 0, heal: Float = 0f, magic: Boolean = false,
    ) = c(id, name, owner, 0, 0, power, target, status, magnitude, duration, heal, magic).also { it.kind = "Ultimate" }

    private fun a(
        id: String, name: String, owner: String, target: String, status: String, magnitude: Float, duration: Int,
        heal: Float = 0f, draw: Int = 0,
    ) = c(id, name, owner, 0, 0, 0f, target, status, magnitude, duration, heal, draw = draw).also { it.kind = "Awakening"; it.art = "" }

    private fun hero(
        id: String, name: String, title: String, role: String, element: String, tier: Int, hp: Int, attack: Float,
        magic: Float, defense: Float, resistance: Float, speed: Float, crit: Float, critDamage: Float,
        bonds: List<String>, bio: String,
    ) = CharacterDef(
        id = id, name = name, title = title, role = role, element = element, tier = tier, hp = hp, attack = attack,
        magic = magic, defense = defense, resistance = resistance, speed = speed, crit = crit, critDamage = critDamage,
        portrait = "builtin:$id", bonds = bonds, source = "adams-haven", novel = "Adams Haven", bio = bio,
    )

    private val retainCards = setOf(
        "mv_glacier_guard", "mv_whiteout_counter", "gh_guard_stance", "el_piercing_shot", "he_lend_strength",
        "he_reserve_tonic", "cy_barkeep_tonic", "da_ember_thrust", "ce_aegis_array",
    )
    private val exhaustCards = setOf("gh_reckless_swing", "cy_smite", "da_matriarch_pyre", "he_bulwark_brew", "el_artillery_barrage", "ce_prism_lance")
    private val initiationCards = setOf("mv_frost_jab", "gh_tiger_cleave", "el_forest_scout", "he_hearthfire_mend", "da_bonfire_brand", "cy_radiant_palm", "ce_crystal_palm")

    fun characters(): List<CharacterDef> {
        val list = mutableListOf<CharacterDef>()
        var h: CharacterDef

        h = hero("kaela", "Kaela Stormfang", "Frost-Fang Guardian", "Tank", "Water", 6, 175, 20f, 8f, 16f, 10f, 13f, .12f, 1.60f,
            listOf("ghislaine", "elara"), "A frost-touched brawler who meets every blow head on.")
        h.cards = mutableListOf(
            c("mv_frost_jab", "Frost Jab", h.id, 1, 1, 1.3f),
            c("mv_rime_sweep", "Rime Sweep", h.id, 2, 1, 1.1f, status = "Slow", magnitude = .25f, duration = 2),
            c("mv_glacier_guard", "Glacier Guard", h.id, 1, 0, target = "Self", status = "DefenseUp", magnitude = .35f, duration = 2),
            c("mv_shatter_hook", "Shatter Hook", h.id, 3, 1, 2.2f, status = "DefenseDown", magnitude = .2f, duration = 2),
            c("mv_whiteout_counter", "Whiteout Counter", h.id, 2, 0, target = "Self", status = "IceCounter", magnitude = 1.4f, duration = 1),
            c("mv_permafrost_brace", "Permafrost Brace", h.id, 2, 0, target = "Self", status = "DefenseUp", magnitude = .4f, duration = 2, heal = 24f),
        )
        h.ultimates = mutableListOf(
            u("ult_kaela_avalanche", "Avalanche Breaker", h.id, 3f, "AllEnemies", "Slow", .25f, 2),
            u("ult_kaela_bastion", "Winter Bastion", h.id, 0f, "AllAllies", "DefenseUp", .45f, 2, 32f),
        )
        h.awakening = a("aw_kaela", "Winter Resolve", h.id, "AllAllies", "DefenseUp", .25f, 2, 12f)
        list.add(h)

        h = hero("ghislaine", "Ghislaine Dedoldia", "White Tiger", "Tank", "Fire", 6, 170, 26f, 5f, 16f, 6f, 11f, .10f, 1.75f,
            listOf("kaela", "daisy"), "A tiger-blooded swordswoman who guards by striking first.")
        h.cards = mutableListOf(
            c("gh_tiger_cleave", "Tiger Cleave", h.id, 1, 1, 1.4f),
            c("gh_guard_stance", "Guard Stance", h.id, 1, 0, target = "Self", status = "DefenseUp", magnitude = .4f, duration = 2),
            c("gh_fireline_cut", "Fireline Cut", h.id, 2, 1, 1.5f, "AllEnemies"),
            c("gh_ember_chain", "Ember Chain", h.id, 2, 1, 1.2f),
            c("gh_reckless_swing", "Reckless Swing", h.id, 3, 1, 3f),
            c("gh_tiger_riposte", "Tiger Riposte", h.id, 2, 1, 1.8f, status = "DefenseDown", magnitude = .3f, duration = 2),
        )
        h.ultimates = mutableListOf(
            u("ult_ghislaine", "White Tiger Rend", h.id, 4.6f, "Enemy", "DefenseDown", .35f, 2),
            u("ult_ghislaine_oath", "Warden's Oath", h.id, 0f, "AllAllies", "DefenseUp", .45f, 2, 32f),
        )
        h.awakening = a("aw_ghislaine", "Tiger's Return", h.id, "AllAllies", "AttackUp", .25f, 2)
        list.add(h)

        h = hero("elara", "Elara Vellum", "Storm Archivist", "Ranger", "Lightning", 4, 105, 7f, 24f, 7f, 14f, 12f, .08f, 1.50f,
            listOf("kaela", "helda", "clarity"), "A scholar of storms who writes lightning into the air.")
        h.cards = mutableListOf(
            c("el_arc_bolt", "Arc Bolt", h.id, 1, 1, 1.3f, magic = true),
            c("el_piercing_shot", "Piercing Shot", h.id, 2, 1, 2.2f, magic = true),
            c("el_forest_scout", "Forest Scout", h.id, 1, 0, target = "None", draw = 2),
            c("el_sapping_volley", "Sapping Volley", h.id, 2, 1, 1f, status = "AttackDown", magnitude = .25f, duration = 2, magic = true),
            c("el_artillery_barrage", "Artillery Barrage", h.id, 3, 1, 1.5f, "AllEnemies", magic = true),
            c("el_storm_tally", "Storm Tally", h.id, 2, 1, 1f, "AllEnemies", magic = true),
        )
        h.ultimates = mutableListOf(
            u("ult_elara", "Stormfall Barrage", h.id, 2.9f, "AllEnemies", magic = true),
            u("ult_elara_convergence", "Lightning Convergence", h.id, 4.6f, "Enemy", "DefenseDown", .35f, 2, magic = true),
        )
        h.awakening = a("aw_elara", "Clear Script", h.id, "AllEnemies", "DefenseDown", .20f, 2, draw = 1)
        list.add(h)

        h = hero("helda", "Helda Frostmane", "Hearth Keeper", "Support", "Water", 3, 130, 11f, 17f, 13f, 15f, 8f, .08f, 1.50f,
            listOf("elara", "clarity"), "The party's hearth: warm broth, cold wards and steady hands.")
        h.cards = mutableListOf(
            c("he_hearthfire_mend", "Hearthfire Mend", h.id, 1, 1, target = "Ally", heal = 28f),
            c("he_chilling_touch", "Chilling Touch", h.id, 1, 1, 1.1f, magic = true),
            c("he_frosted_ward", "Frosted Ward", h.id, 2, 1, target = "AllAllies", status = "DefenseUp", magnitude = .25f, duration = 2),
            c("he_reserve_tonic", "Reserve Tonic", h.id, 1, 0, target = "Ally", draw = 1, epGain = 1),
            c("he_lend_strength", "Lend Strength", h.id, 0, 0, target = "Ally", transferEp = true),
            c("he_bulwark_brew", "Bulwark Brew", h.id, 3, 1, target = "AllAllies", status = "DefenseUp", magnitude = .5f, duration = 2),
        )
        h.ultimates = mutableListOf(
            u("ult_helda", "Hearth Renewal", h.id, 0f, "AllAllies", "Regen", 10f, 3, 55f),
            u("ult_helda_sanctuary", "Winter Sanctuary", h.id, 0f, "AllAllies", "DefenseUp", .45f, 2, 32f),
        )
        h.awakening = a("aw_helda", "Hearth Reprieve", h.id, "AllAllies", "Regen", 6f, 2, 18f)
        list.add(h)

        h = hero("daisy", "Daisy Wildheart", "Island Matriarch", "Dps", "Fire", 5, 160, 24f, 12f, 13f, 10f, 13f, .18f, 1.70f,
            listOf("ghislaine", "clarity"), "A spear-and-wand firebrand who burns a path for everyone behind her.")
        h.cards = mutableListOf(
            c("da_bonfire_brand", "Bonfire Brand", h.id, 1, 1, 1.5f),
            c("da_wand_sweep", "Wand Sweep", h.id, 2, 1, 1.5f, "AllEnemies"),
            c("da_ember_thrust", "Ember Thrust", h.id, 1, 1, 1.4f),
            c("da_matriarch_pyre", "Matriarch's Pyre", h.id, 3, 1, 1.8f, "AllEnemies", "DefenseDown", .2f, 2),
            c("da_spearflame_rush", "Spearflame Rush", h.id, 3, 1, 2.2f, status = "DefenseDown", magnitude = .2f, duration = 2),
            c("da_burning_circle", "Burning Circle", h.id, 3, 1, 1.8f, "AllEnemies", "DefenseDown", .2f, 2),
        )
        h.ultimates = mutableListOf(
            u("ult_daisy", "Island Conflagration", h.id, 3.2f, "AllEnemies", "DefenseDown", .3f, 2),
            u("ult_daisy_inferno", "Matriarch's Inferno", h.id, 4.6f, "Enemy", "DefenseDown", .35f, 2),
        )
        h.awakening = a("aw_daisy", "Wildheart Surge", h.id, "AllAllies", "AttackUp", .25f, 2, draw = 1)
        list.add(h)

        h = hero("clarity", "Clarity Lockhart", "Radiant Barkeep", "Support", "Light", 4, 140, 18f, 18f, 11f, 13f, 12f, .14f, 1.60f,
            listOf("helda", "daisy", "elara"), "Pours tonics and throws light in equal measure.")
        h.cards = mutableListOf(
            c("cy_radiant_palm", "Radiant Palm", h.id, 1, 1, 1.4f),
            c("cy_barkeep_tonic", "Barkeep's Tonic", h.id, 1, 0, target = "Ally", heal = 22f),
            c("cy_flare", "Flare", h.id, 2, 1, 1.6f, "AllEnemies", magic = true),
            c("cy_uplift", "Uplift", h.id, 2, 1, target = "AllAllies", status = "AttackUp", magnitude = .25f, duration = 2),
            c("cy_smite", "Smite", h.id, 3, 1, 2.9f, magic = true),
            c("cy_rejuvenating_draught", "Rejuvenating Draught", h.id, 2, 0, target = "Ally", heal = 22f, epGain = 1),
        )
        h.ultimates = mutableListOf(
            u("ult_clarity", "Final Heaven", h.id, 4.2f, "Enemy", heal = 20f),
            u("ult_clarity_feast", "Luminous Feast", h.id, 0f, "AllAllies", "AttackUp", .5f, 3, 25f),
        )
        h.awakening = a("aw_clarity", "Recovery", h.id, "AllAllies", "DefenseUp", .20f, 2, 20f)
        list.add(h)

        // Celestium, the crystal automaton: a Support who shields and repairs, with magic damage of her own.
        h = hero("celestium", "Celestium", "Crystal Automaton", "Support", "Earth", 5, 150, 10f, 21f, 15f, 14f, 10f, .10f, 1.60f,
            listOf("kaela", "helda"), "An ivory-armoured automaton whose crystal joints hum with old temple light.")
        h.cards = mutableListOf(
            c("ce_crystal_palm", "Crystal Palm", h.id, 1, 1, 1.3f, magic = true),
            c("ce_prism_lance", "Prism Lance", h.id, 3, 1, 2.6f, status = "DefenseDown", magnitude = .2f, duration = 2, magic = true),
            c("ce_aegis_array", "Aegis Array", h.id, 2, 1, target = "AllAllies", status = "Shield", magnitude = 18f, duration = 1),
            c("ce_shard_orbit", "Shard Orbit", h.id, 2, 1, 1.2f, "AllEnemies", magic = true),
            c("ce_resonance_pulse", "Resonance Pulse", h.id, 2, 1, .9f, "AllEnemies", "Slow", .25f, 2, magic = true),
            c("ce_lattice_repair", "Lattice Repair", h.id, 1, 1, target = "Ally", status = "DefenseUp", magnitude = .2f, duration = 2, heal = 20f),
        )
        h.ultimates = mutableListOf(
            u("ult_celestium_supernova", "Crystal Supernova", h.id, 3.1f, "AllEnemies", "DefenseDown", .3f, 2, magic = true),
            u("ult_celestium_citadel", "Prismatic Citadel", h.id, 0f, "AllAllies", "Shield", 40f, 2, 20f),
        )
        h.awakening = a("aw_celestium", "Lattice Reboot", h.id, "AllAllies", "Shield", 16f, 1, 10f)
        list.add(h)

        for (ch in list) for (card in ch.cards) {
            card.retain = card.id in retainCards
            card.exhaust = card.id in exhaustCards
            card.initiation = card.id in initiationCards
        }
        return list
    }

    /** JD, the Summoner: off the field, never attacks, plays support cards with CP and fires the decrees with full SP. */
    fun summoner(): BattleUnit = BattleUnit(
        id = Catalog.SUMMONER_ID, name = "JD", art = "builtin:jd", element = BattleElement.Light,
        maxHp = 260, hp = 260, defense = 14f, resistance = 14f, speed = 0f,
    )

    fun summonerKit(): List<CardDef> {
        val o = Catalog.SUMMONER_ID
        val kit = listOf(
            c("sm_rally", "Rally", o, 0, 1, target = "None", draw = 2),
            c("sm_surge", "Surge", o, 0, 1, target = "Ally", epGain = 2),
            c("sm_second", "Second Wind", o, 0, 2, target = "Ally", apGain = 1),
            c("sm_banner", "Banner", o, 0, 1, target = "AllAllies", status = "AttackUp", magnitude = .3f, duration = 2),
            c("sm_aegis", "Aegis", o, 0, 1, target = "AllAllies", status = "DefenseUp", magnitude = .3f, duration = 2),
            c("sm_wither", "Wither", o, 0, 1, target = "AllEnemies", status = "DefenseDown", magnitude = .25f, duration = 2),
            c("sm_hush", "Hush", o, 0, 1, target = "AllEnemies", status = "AttackDown", magnitude = .25f, duration = 2),
            c("sm_mend", "Blessing", o, 0, 2, target = "AllAllies", heal = 30f),
            c("sm_draw_eye", "Draw the Eye", o, 0, 1, target = "Ally", status = "Taunt", duration = 2),
        )
        for (card in kit) { card.kind = "Summoner"; card.art = "" }
        return kit
    }

    fun summonerUltimates(): List<CardDef> {
        val o = Catalog.SUMMONER_ID
        val x = c("ult_sum_a", "Rally Decree", o, 0, 0, target = "AllAllies", status = "AttackUp", magnitude = .5f, duration = 3, heal = 25f)
        val y = c("ult_sum_b", "Sunder Decree", o, 0, 0, target = "AllEnemies", status = "DefenseDown", magnitude = .4f, duration = 3)
        x.kind = "Summoner"; y.kind = "Summoner"; x.art = ""; y.art = ""
        return listOf(x, y)
    }

    /** The Summoner cards a new save starts with; the rest come from end-of-run drafts. */
    val SUMMONER_STARTERS = listOf("sm_rally", "sm_surge", "sm_banner", "sm_aegis")
}
