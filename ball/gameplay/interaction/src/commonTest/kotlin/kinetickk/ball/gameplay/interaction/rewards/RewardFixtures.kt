// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import kinetickk.ball.gameplay.interaction.ShippedWeaponFixtures
import kinetickk.ball.content.api.BuffStat
import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.DamageBasis
import kinetickk.ball.content.api.EffectAction
import kinetickk.ball.content.api.EffectClause
import kinetickk.ball.content.api.EffectCondition
import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.content.api.ElementPick
import kinetickk.ball.content.api.EquippedRelic
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.ItemRule
import kinetickk.ball.content.api.KINETICKK_CONTENT_VERSION
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.RebirthPolicySnapshot
import kinetickk.ball.content.api.RebirthProfile
import kinetickk.ball.content.api.RelicAspect
import kinetickk.ball.content.api.RelicDefinition
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicKey
import kinetickk.ball.content.api.RelicPairingId
import kinetickk.ball.content.api.RelicPolicy
import kinetickk.ball.content.api.RelicValue
import kinetickk.ball.content.api.StatDelta
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.WeaponMastery
import kinetickk.ball.gameplay.nucleus.render.BuildProjection
import kinetickk.ball.gameplay.nucleus.render.ChoiceOption
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.RewardPreview
import kinetickk.ball.gameplay.nucleus.render.RunStatistics
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.collections.toImmutableSet
import kinetickk.foundation.common.localization.AppLanguage

/**
 * The shipped relic catalog's copy (names, keys, upsides and liabilities), so localized overlay
 * tests read real phrases. The Gameplay interaction tests cannot depend on the content implementation.
 */
internal val rewardFixtureRelics: List<RelicDefinition> = listOf(
    relic(RelicId.PERPETUAL_VECTOR, "Perpetual Vector", RelicAspect.KINETIC, listOf(RelicKey.MOTION), listOf(BuildTag.DRIVE, BuildTag.RAM),
        "Drag falls by 85 %. Every 400 u/s above 600, up to 2,200, adds {0} % damage.",
        "Brake grips half as hard, and impacts below 600 u/s deal 30 % less.", RelicValue(12f, 3f)),
    relic(RelicId.BLINK_DRIVE, "Blink Drive", RelicAspect.KINETIC, listOf(RelicKey.MOTION, RelicKey.CRITICAL), listOf(BuildTag.DRIVE, BuildTag.PRECISION),
        "Dash becomes a Blink: you jump up to {1} u along your aim, stopping short of the singularity, " +
            "and strike every enemy on the line for {0} % impact damage. Blinks cost 35 % less heat.",
        "After a Blink you keep only 35 % of your speed.", RelicValue(110f, 30f), RelicValue(260f)),
    relic(RelicId.GYRE_ENGINE, "Gyre Engine", RelicAspect.KINETIC, listOf(RelicKey.MOTION), listOf(BuildTag.DRIVE, BuildTag.CHAIN),
        "Every real turn releases a shock ring for {0} % impact damage and 2 stacks of your dominant element.",
        "Polarity no longer recovers on its own or while braking; only turns restore it, and less than before.", RelicValue(55f, 20f)),
    relic(RelicId.GLASS_COMET, "Glass Comet", RelicAspect.KINETIC, listOf(RelicKey.FRAGILE, RelicKey.CRITICAL), listOf(BuildTag.PRECISION, BuildTag.RAM),
        "All your damage +{0} %. Impact kills above 900 u/s grant a quarter second of invulnerability, " +
            "at most once every 1.5 seconds.",
        "You take double damage, and shields and damage reduction no longer protect you.", RelicValue(100f, 25f)),
    relic(RelicId.EXECUTIONERS_PRISM, "Executioner's Prism", RelicAspect.KINETIC, listOf(RelicKey.CRITICAL), listOf(BuildTag.PRECISION),
        "Critical hits are no longer random. Every 1 % critical chance, up to 50 %, becomes {0} % critical " +
            "damage. Hits that can crit always crit against enemies below 35 % integrity, Frozen or Brittle enemies and " +
            "enemies whose status is three quarters of the way to its Break.",
        "Hits that do not crit deal 20 % less.", RelicValue(3f, 0.5f)),
    relic(RelicId.ECHO_CHAMBER, "Echo Chamber", RelicAspect.KINETIC, listOf(RelicKey.ECHO), listOf(BuildTag.ARSENAL),
        "Weapon hits echo 0.35 seconds later for {0} % damage and apply their on-hit effects again.",
        "Impact damage −50 %.", RelicValue(50f, 10f)),
    relic(RelicId.FURNACE_COVENANT, "Furnace Covenant", RelicAspect.THERMAL, listOf(RelicKey.HEAT), listOf(BuildTag.OVERCLOCK),
        "Overheat never locks your Dash. Every 10 heat above 40 adds {0} % damage, and above 70 heat your " +
            "hits apply Thermal.",
        "Cooling −55 %. Above 80 heat you lose 3 integrity per second, and heat past full burns integrity instead.", RelicValue(8f, 2f)),
    relic(RelicId.CINDER_CROWN, "Cinder Crown", RelicAspect.THERMAL, listOf(RelicKey.HEAT, RelicKey.SPREAD), listOf(BuildTag.OVERCLOCK, BuildTag.CHAIN),
        "Thermal stacks no longer burn over time. They bank {0} % of their damage and Ignite at 5 stacks, " +
            "releasing the bank and spreading 2 Thermal.",
        "Thermal fades twice as fast, and Ignites near you burn 4 % of your integrity.", RelicValue(160f, 30f)),
    relic(RelicId.OVERDRIVE_CASCADE, "Overdrive Cascade", RelicAspect.THERMAL, listOf(RelicKey.HEAT), listOf(BuildTag.OVERCLOCK, BuildTag.ARSENAL),
        "Overdrive gain +{0} %. Each kill during Overdrive extends it, up to 2.5 extra seconds.",
        "Outside Overdrive all your damage −30 %.", RelicValue(60f, 20f)),
    relic(RelicId.ABSOLUTE_ZERO, "Absolute Zero", RelicAspect.CRYO, listOf(RelicKey.COLD, RelicKey.CRITICAL), listOf(BuildTag.RAM, BuildTag.PRECISION),
        "Impacts apply {0} Cryo and Dash chills everything around you. Frozen enemies shatter on the next " +
            "direct hit for {1} % damage, chilling their neighbors.",
        "Half of the Thermal you apply turns to Cryo and the rest is lost. Overdrive gain −40 %.", RelicValue(3f), RelicValue(250f, 50f)),
    relic(RelicId.STASIS_ANCHOR, "Stasis Anchor", RelicAspect.CRYO, listOf(RelicKey.STILLNESS, RelicKey.COLD), listOf(BuildTag.ANCHOR, BuildTag.ARSENAL),
        "While braking, enemies near you are slowed by {0} % and hostile bullets near you are caught, up to " +
            "24. Release Brake to fire them as your own. Stasis holds for 3 seconds and refills when you let go.",
        "Weapon activation −30 % while you are not braking.", RelicValue(45f, 10f)),
    relic(RelicId.STORMHEART_CAPACITOR, "Stormheart Capacitor", RelicAspect.ION, listOf(RelicKey.CHARGE, RelicKey.MOTION), listOf(BuildTag.DRIVE, BuildTag.RAM),
        "Above 700 u/s the Core charges in 4 seconds. The next impact discharges into five enemies for " +
            "{0} % impact damage and 3 Ion each.",
        "Taking a hit while half charged or more discharges into you for 10 % of your integrity.", RelicValue(200f, 40f)),
    relic(RelicId.AEGIS_INVERSION, "Aegis Inversion", RelicAspect.ION, listOf(RelicKey.SHIELD, RelicKey.LIFE), listOf(BuildTag.BULWARK),
        "All healing becomes shield, and shield capacity grows by 60 % of your maximum integrity. A breaking " +
            "shield releases a nova worth {0} % of the damage it absorbed.",
        "Integrity can never be restored, and shields no longer recharge on their own.", RelicValue(150f, 50f)),
    relic(RelicId.NEUTRON_HEART, "Neutron Heart", RelicAspect.GRAVITIC, listOf(RelicKey.GRAVITY, RelicKey.STILLNESS), listOf(BuildTag.RAM, BuildTag.ANCHOR),
        "Mass +{0} %, and enemies barely move you. Slow collisions hurt you only half as much.",
        "Dash impulse −35 % and magnetism −20 %.", RelicValue(80f, 30f)),
    relic(RelicId.EVENT_HORIZON, "Event Horizon", RelicAspect.GRAVITIC, listOf(RelicKey.GRAVITY), listOf(BuildTag.ANCHOR, BuildTag.RAM),
        "Enemies within 280 u are dragged toward you. Impacts against enemies carrying Gravitic deal {0} % more, and " +
            "your Collapses implode toward you.",
        "Contact damage taken ×1.5. Data no longer latches onto you, and level-ups no longer pull it in.",
        RelicValue(40f, 20f)),
    relic(RelicId.VERDANT_ROT, "Verdant Rot", RelicAspect.ENTROPY, listOf(RelicKey.DECAY, RelicKey.LIFE), listOf(BuildTag.BULWARK),
        "Impacts apply 2 Entropy. Every Entropy stack within 320 u heals you {0} per second, up to 3.5.",
        "All other healing is disabled, and every Entropy stack close to you makes you take 1.5 % more damage.", RelicValue(0.25f, 0.05f)),
    relic(RelicId.PANDEMIC_CODEX, "Pandemic Codex", RelicAspect.ENTROPY, listOf(RelicKey.DECAY, RelicKey.SPREAD), listOf(BuildTag.CHAIN),
        "Statuses are contagious: when an enemy dies, {0} % of each of its statuses jumps to its two nearest " +
            "neighbors.",
        "Enemies carrying a status move 20 % faster.", RelicValue(60f, 10f)),
    relic(RelicId.AGONY_SCEPTER, "Agony Scepter", RelicAspect.SOVEREIGN, listOf(RelicKey.ECHO), listOf(BuildTag.ARSENAL),
        "Your weapon takes its Forbidden form. Every rank deepens the mutation.",
        "Weapon activations add heat: 6 per shot, or 5 per second for continuous weapons.", minimumRebirth = 1),
    relic(RelicId.HOLLOW_CROWN, "Hollow Crown", RelicAspect.SOVEREIGN, listOf(RelicKey.VOID), listOf(BuildTag.FORTUNE),
        "Each empty Relic slot grants {0} % damage and 8 % activation speed.",
        "It never ranks up, every other bound Relic is 25 % weaker, and each empty slot makes you take 10 % " +
            "more damage.", RelicValue(35f), minimumRebirth = 3, meldable = false),
    relic(RelicId.OUROBOROS_SEAL, "Ouroboros Seal", RelicAspect.SOVEREIGN, listOf(RelicKey.LIFE, RelicKey.VOID), listOf(BuildTag.BULWARK, BuildTag.FORTUNE),
        "Once per run, lethal damage instead restores your integrity, releases a nova for {0} damage and " +
            "grants two seconds of invulnerability. Then the seal is spent but keeps its slot.",
        "Data gain −25 % while the seal is bound. The singularity still kills, and a spent seal locks its " +
            "slot for two minutes.", RelicValue(120f, 60f), minimumRebirth = 5),
)

private fun relic(
    id: RelicId,
    name: String,
    aspect: RelicAspect,
    keys: List<RelicKey>,
    attuned: List<BuildTag>,
    upside: String,
    liability: String,
    vararg values: RelicValue,
    minimumRebirth: Int = 0,
    meldable: Boolean = true,
) = RelicDefinition(id, name, aspect, keys.toImmutableList(), attuned.toImmutableList(), upside, liability,
    values.toList().toImmutableList(), minimumRebirth, meldable)

/** Catalog-shaped content for overlay tests: real weapon, relic and item names. */
internal val rewardFixtureContent: GameplayContentSnapshot = GameplayContentSnapshot(
    version = KINETICKK_CONTENT_VERSION,
    items = listOf(
        // Shipped items, one per rarity: a Cryo control rule, a legendary Thermal Break rule, a high-risk
        // stat trade, a weapon proc and a collision clause; then a shield clause Glass Comet voids.
        ItemDefinition(0, "Stillpoint", "At the center of a stop, the air itself freezes.", ItemRarity.RARE,
            immutableListOf(BuildTag.ANCHOR), immutableListOf(StatusElement.CRYO),
            clauses = immutableListOf(EffectClause(EffectTrigger.BRAKE_RELEASE,
                actions = immutableListOf(EffectAction.ApplyStatus(ElementPick.CRYO, 3, 220f)))),
            rules = immutableListOf(ItemRule.WhileModifier(EffectCondition.Braking, BuffStat.COOLING, 1f)),
            maxStacks = 2, unlockLevel = 10),
        ItemDefinition(1, "Solar Choir", "When one fire sings, the others answer.", ItemRarity.LEGENDARY,
            immutableListOf(BuildTag.CHAIN), immutableListOf(StatusElement.THERMAL),
            rules = immutableListOf(ItemRule.BreakSpread(StatusElement.THERMAL, 3, 140f)),
            maxStacks = 1, unlockLevel = 16, minimumRebirth = 2),
        ItemDefinition(2, "Overcharged Capacitor", "More power than the cooling loop was ever rated for.", ItemRarity.EPIC,
            immutableListOf(BuildTag.ARSENAL, BuildTag.OVERCLOCK),
            stats = immutableListOf(StatDelta(ItemEffect.WEAPON_POWER, 0.35f), StatDelta(ItemEffect.COOLING, -0.25f)),
            maxStacks = 1, unlockLevel = 13, risk = true),
        ItemDefinition(3, "Calibrated Coil", "A cleaner signal into the weapon.", ItemRarity.COMMON,
            immutableListOf(BuildTag.ARSENAL),
            stats = immutableListOf(StatDelta(ItemEffect.WEAPON_POWER, 0.08f)),
            clauses = immutableListOf(EffectClause(EffectTrigger.WEAPON_HIT,
                actions = immutableListOf(EffectAction.Strike(DamageBasis.HIT, 0.6f)), everyNth = 6)),
            maxStacks = 3, unlockLevel = 1),
        ItemDefinition(4, "Shock Plate", "A reactive plate that answers every collision with a wave.", ItemRarity.UNCOMMON,
            immutableListOf(BuildTag.RAM, BuildTag.ANCHOR),
            clauses = immutableListOf(EffectClause(EffectTrigger.IMPACT,
                actions = immutableListOf(EffectAction.Push(140f, 260f), EffectAction.Burst(140f, DamageBasis.IMPACT, 0.2f)),
                cooldownSeconds = 0.8f)),
            maxStacks = 2, unlockLevel = 4),
        ItemDefinition(5, "Capacitor Aegis", "A collapsing shield spills its charge.", ItemRarity.UNCOMMON,
            immutableListOf(BuildTag.BULWARK), immutableListOf(StatusElement.ION),
            clauses = immutableListOf(EffectClause(EffectTrigger.SHIELD_BREAK,
                actions = immutableListOf(EffectAction.ApplyStatus(ElementPick.ION, 3, 220f), EffectAction.Burst(220f, DamageBasis.SHIELD, 1f)))),
            maxStacks = 3, unlockLevel = 8),
    ).toImmutableList(),
    weapons = ShippedWeaponFixtures,
    weaponMasteries = WeaponMastery.entries.toImmutableList(),
    relics = rewardFixtureRelics.toImmutableList(),
    rebirth = RebirthPolicySnapshot(
        minimumLevel = 0, maximumLevel = 0,
        profiles = immutableListOf(RebirthProfile(0, "Baseline", 5, 1f, 1f, 1f, 1f, 1f, 1f, 0f, 1f, 0f, 1f, 0, 120, 0.09f, 24f)),
        maxActiveEnemies = 120, minSpawnIntervalSeconds = 0.09f, minEliteIntervalSeconds = 24f,
    ),
    relicPolicy = RelicPolicy(4, 3),
)

/** A committed frame with the given reward choice, relic matrix and run totals ([content] defaults to the fixture catalog). */
internal fun rewardFixtureModel(
    phase: GamePhase = GamePhase.CHOICE,
    content: GameplayContentSnapshot = rewardFixtureContent,
    choiceType: ChoiceType = ChoiceType.ITEM,
    choices: List<ChoiceOption> = emptyList(),
    previews: List<RewardPreview> = emptyList(),
    relics: List<EquippedRelic> = emptyList(),
    itemStacks: List<Int> = List(content.items.size) { 0 },
    discoveredItems: Set<Int> = emptySet(),
    discoveredRelics: Set<RelicId> = emptySet(),
    discoveredPairings: Set<RelicPairingId> = emptySet(),
    discoveredReactions: Set<ReactionId> = emptySet(),
    build: BuildProjection = BuildProjection(),
    level: Int = 15,
    weapon: WeaponId = WeaponId.FLUX_WAKE,
    weaponLevel: Int = 7,
    rerolls: Int = 2,
    directedChoice: Boolean = false,
    language: AppLanguage = AppLanguage.English,
    textScale: Float = 1f,
    width: Float = 1440f,
    height: Float = 810f,
    message: String = "",
    kills: Int = 612,
    runMatter: Long = 486L,
    totalMatter: Long = 1_924L,
    elapsed: Float = 462f,
    statistics: RunStatistics = RunStatistics(
        damageDealt = 184_320.0, damageTaken = 388.0, damageAbsorbed = 240.0, dataCollected = 31_260L,
        pickupsCollected = 2_904L, keysCollected = 4L, eliteKills = 2, bestCombo = 31,
    ),
    rebirthLevel: Int = 3,
    runStatisticsOnLeft: Boolean = false,
): GameplayRenderModel = GameplayRenderModel(
    content = content,
    phase = phase,
    settings = PlayerPreferences(language = language, textScale = textScale, runStatisticsOnLeft = runStatisticsOnLeft),
    rebirthLevel = rebirthLevel,
    screenWidth = width,
    screenHeight = height,
    uiScale = 1f,
    coreX = 0f, coreY = 0f, velocityX = 0f, velocityY = 0f, cameraX = 0f, cameraY = 0f,
    pointerX = width * 0.5f, pointerY = height * 0.5f, pointerActive = false, braking = false,
    elapsed = elapsed,
    heat = 0f, overheated = false, dashPhaseTime = 0f,
    hp = 142f, maxHp = 180f, shield = 0f, maxShield = 0f,
    level = level, data = 0, nextLevelData = 100, keys = 1, kills = kills,
    runStatistics = statistics,
    combo = 0, comboTime = 0f, runMatter = runMatter, totalMatter = totalMatter,
    lastImpact = 0f, lastImpactTime = 0f, damageFlash = 0f, runGrace = 0f, screenShake = 0f,
    message = message, messageTime = 0f,
    mass = 1.18f, damageMultiplier = 1.64f, weaponPower = 1.36f, coolingRate = 23.2f, magnetStrength = 6.7f,
    dashImpulse = 873f, dashHeatCost = 36f, regenPerSecond = 0f, critChance = 0.08f, critMultiplier = 1.5f,
    pickupRadius = 174f, luck = 0f, dataGain = 1f, matterGain = 1f, attackSpeed = 1f, damageReduction = 0f,
    comboWindow = 2.8f, overdriveGain = 1.2f, dragCoefficient = 0f, polarityStability = 1f,
    weapon = weapon, weaponLevel = weaponLevel,
    overdriveCharge = 0f, overdriveTime = 0f,
    rerollsRemaining = rerolls,
    acquiredItemCount = 4,
    recentItem = null,
    equippedRelics = relics.toImmutableList(),
    morningstarAngle = 0f, morningstarX = 0f, morningstarY = 0f,
    weaponBeamTime = 0f, weaponBeamStartX = 0f, weaponBeamStartY = 0f, weaponBeamEndX = 0f, weaponBeamEndY = 0f,
    totem = null,
    coreShape = CoreShape.ORB,
    enemies = immutableListOf(), projectiles = immutableListOf(), pickups = immutableListOf(), trail = immutableListOf(),
    weaponNodes = immutableListOf(), weaponOrbitals = immutableListOf(),
    choices = choices.toImmutableList(),
    choiceType = choiceType,
    pendingRelicChoiceCount = 0,
    itemStacks = itemStacks.toImmutableList(),
    discoveredItemIds = discoveredItems.toImmutableSet(),
    relicRanks = RelicId.entries.map { id -> relics.firstOrNull { it.id == id }?.rank ?: 0 }.toImmutableList(),
    directedChoice = directedChoice,
    rewardPreviews = previews.toImmutableList(),
    discoveredRelicMask = discoveredRelics.fold(0L) { mask, id -> mask or (1L shl id.ordinal) },
    build = build,
    discoveredPairingMask = discoveredPairings.fold(0) { mask, id -> mask or (1 shl id.ordinal) },
    discoveredReactionMask = discoveredReactions.fold(0) { mask, id -> mask or (1 shl id.ordinal) },
)

internal fun itemChoice(id: Int, reborn: Boolean = false): ChoiceOption {
    val item = requireNotNull(rewardFixtureContent.item(id))
    return ChoiceOption(ChoiceType.ITEM, item.name, item.description, item.rarity.displayLabel.uppercase(), itemId = id, reborn = reborn)
}
