// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.impl

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.EffectAction
import kinetickk.ball.content.api.EffectClause
import kinetickk.ball.content.api.EffectCondition
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.ItemRule
import kinetickk.ball.content.api.MatrixEffect
import kinetickk.ball.content.api.MatrixNodeId
import kinetickk.ball.content.api.RebirthProfile
import kinetickk.ball.content.api.RelicDefinition
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.WeaponId
import kotlin.test.Test
import kotlin.test.assertEquals

class ContentBalanceCharacterizationTest {
    private val catalog = createContentCatalog()
    private val ui = catalog.uiCatalog()

    @Test
    fun itemCatalogCardinalityOrderingAndContentAreGolden() {
        val items = ui.items

        assertEquals(173, items.size)
        assertEquals((0 until items.size).toList(), items.map(ItemDefinition::id))
        assertEquals(
            "353a71756621879d",
            goldenFingerprint {
                add("item-effects")
                ItemEffect.entries.forEach { effect ->
                    add(effect.name)
                    add(effect.displayLabel)
                    add(effect.unit.name)
                }
                add("item-rarities")
                ItemRarity.entries.forEach { rarity ->
                    add(rarity.name)
                    add(rarity.displayLabel)
                    add(rarity.rank)
                }
                add("items")
                add(items.size)
                items.forEach(::addItem)
            },
        )
    }

    @Test
    fun buildCatalogTuningIsGolden() {
        val build = ui.build

        assertEquals(
            "6b94bfd3ccc1e1a6",
            goldenFingerprint {
                build.elements.forEach { element ->
                    add(element.element.name)
                    add(element.statusDescription)
                    add(element.breakDescription)
                    add(element.breakThreshold)
                    add(element.decaySeconds)
                    add(element.perStack)
                    add(element.breakPower)
                    add(element.breakRadius)
                    add(element.breakFloor)
                }
                build.reactions.forEach { reaction ->
                    add(reaction.id.name)
                    add(reaction.name)
                    add(reaction.description)
                    add(reaction.power)
                    add(reaction.radius)
                    add(reaction.residue?.name ?: "-")
                    add(reaction.residueStacks)
                }
                build.resonances.forEach { resonance ->
                    add(resonance.tag.name)
                    add(resonance.description)
                    resonance.tiers.forEach(::add)
                }
                build.affinityTiers.forEach { add(it.description) }
                build.pairings.forEach { pairing ->
                    add(pairing.id.name)
                    add(pairing.first.name)
                    add(pairing.second.name)
                    add(pairing.kind.name)
                    add(pairing.name)
                    add(pairing.description)
                }
                with(build.policy) {
                    resonanceThresholds.forEach(::add)
                    affinityThresholds.forEach(::add)
                    stackMagnitudes.forEach(::add)
                    listOf(
                        rebornMagnitude, rebornChancePerTier, maximumRebornChance, attunementBonus, severanceSeconds,
                        harmonyScale, dissonanceScale, amplificationSoftCap, criticalMultiplierCap, vulnerabilityCap,
                        overdriveGainCap, reactionCooldownSeconds, reactionPairCooldownSeconds,
                    ).forEach(::add)
                    listOf(breaksPerSecond, reactionsPerSecond, maximumFields, masteredTagPoints).forEach(::add)
                }
            },
        )
    }

    @Test
    fun weaponAndMatrixEconomyPoliciesAreGolden() {
        assertEquals(
            listOf(
                WeaponId.FLUX_WAKE to 0,
                WeaponId.MORNINGSTAR to 25,
                WeaponId.PHASE_LATTICE to 55,
                WeaponId.NULL_LANCE to 95,
                WeaponId.GRAVITY_MINES to 145,
                WeaponId.ION_SWARM to 215,
                WeaponId.RIFT_BLADES to 305,
                WeaponId.ARC_COIL to 430,
                WeaponId.QUASAR_CANNON to 610,
                WeaponId.ENTROPY_FIELD to 860,
                WeaponId.SINGULARITY_SPEAR to 1_200,
                WeaponId.PRISM_RELAY to 1_650,
                WeaponId.GYRE_DISC to 120,
                WeaponId.SCATTER_BLOOM to 260,
                WeaponId.TETHER_ARC to 900,
                WeaponId.SENTRY_GRID to 380,
                WeaponId.HUNTER_SALVO to 1_300,
                WeaponId.CONCUSSION_ENGINE to 520,
                WeaponId.GLACIER_BELL to 700,
                WeaponId.EVENT_LENS to 1_100,
                WeaponId.SOLAR_SWEEP to 1_500,
                WeaponId.AEGIS_SHARDS to 1_800,
                WeaponId.HOLLOW_RIFT to 2_100,
                WeaponId.RESONANCE_DRUM to 2_600,
            ),
            ui.weapons.map { weapon -> weapon.id to weapon.permanentUnlockCost },
        )
        assertEquals(
            mapOf(
                WeaponId.GLACIER_BELL to 1,
                WeaponId.TETHER_ARC to 2,
                WeaponId.EVENT_LENS to 3,
                WeaponId.HUNTER_SALVO to 4,
                WeaponId.SOLAR_SWEEP to 5,
                WeaponId.AEGIS_SHARDS to 6,
                WeaponId.HOLLOW_RIFT to 7,
                WeaponId.RESONANCE_DRUM to 9,
            ),
            ui.weapons.filter { it.minimumRebirth > 0 }.associate { it.id to it.minimumRebirth },
        )
        assertEquals(
            listOf(
                MatrixNodeId.IMPACT_DRIVER to listOf(150, 400, 900),
                MatrixNodeId.WEAPON_CALIBRATION to listOf(200, 500, 1_000),
                MatrixNodeId.CRITICAL_LATTICE to listOf(600, 1_400),
                MatrixNodeId.ARMED_START to listOf(800, 2_000),
                MatrixNodeId.CATALYST_CORE to listOf(1_600),
                MatrixNodeId.OVERDRIVE_COIL to listOf(1_800),
                MatrixNodeId.REBORN_ARSENAL to listOf(4_200),
                MatrixNodeId.GLASS_ENGINE to listOf(3_500),
                MatrixNodeId.REINFORCED_HULL to listOf(120, 350, 800),
                MatrixNodeId.HARDLIGHT_RESERVE to listOf(300, 800),
                MatrixNodeId.IMPACT_DAMPERS to listOf(400, 1_000),
                MatrixNodeId.SECOND_WIND to listOf(2_200),
                MatrixNodeId.NANOFORGE to listOf(700, 1_600),
                MatrixNodeId.BASTION_PROTOCOL to listOf(2_600),
                MatrixNodeId.AEGIS_LATTICE to listOf(4_500),
                MatrixNodeId.JUGGERNAUT to listOf(3_800),
                MatrixNodeId.MAGNET_ARRAY to listOf(100, 300, 700),
                MatrixNodeId.CRYO_LOOP to listOf(250, 700),
                MatrixNodeId.VECTOR_THRUSTERS to listOf(400, 1_000),
                MatrixNodeId.NEAR_MISS to listOf(900),
                MatrixNodeId.RICOCHET_RIG to listOf(1_500),
                MatrixNodeId.OVERCLOCKED_REFLEXES to listOf(1_200, 2_600),
                MatrixNodeId.GRAVITY_SURFER to listOf(5_000),
                MatrixNodeId.PERPETUAL_DRIFT to listOf(4_000),
                MatrixNodeId.SALVAGE_PROTOCOL to listOf(150, 400, 900),
                MatrixNodeId.DATA_ARCHIVE to listOf(150, 400, 900),
                MatrixNodeId.REROLL_CACHE to listOf(500, 1_500),
                MatrixNodeId.WIDE_OFFER to listOf(1_200),
                MatrixNodeId.LUCKY_CHARM to listOf(800, 2_000),
                MatrixNodeId.ELITE_BOUNTY to listOf(2_400),
                MatrixNodeId.HEIRLOOM_VAULT to listOf(3_000),
                MatrixNodeId.PROSPECTOR to listOf(3_200),
            ),
            ui.matrixNodes.map { node -> node.id to node.costs.toList() },
        )
        assertEquals(
            "a62a1446cf743de6",
            goldenFingerprint {
                add("weapons")
                ui.weapons.forEach { weapon ->
                    add(weapon.id.name)
                    add(weapon.name)
                    add(weapon.description)
                    add(weapon.family.name)
                    add(weapon.element?.name ?: "-")
                    add(weapon.tags.size)
                    weapon.tags.forEach { add(it.name) }
                    add(weapon.key.name)
                    add(weapon.signature.name)
                    add(weapon.scaling.name)
                    add(weapon.cadence.name)
                    add(weapon.permanentUnlockCost)
                    add(weapon.minimumRebirth)
                    weapon.masteryForms.forEach(::add)
                    add(weapon.signatureText)
                }
                add("matrix")
                ui.matrixNodes.forEach { node ->
                    add(node.id.name)
                    add(node.name)
                    add(node.description)
                    add(node.branch.name)
                    add(node.row)
                    add(node.maxRanks)
                    node.costs.forEach(::add)
                    node.prerequisites.forEach { add(it.name) }
                    add(node.minimumRebirth)
                    add(node.keystone)
                    node.effects.forEach { addMatrixEffect(it) }
                }
            },
        )
    }

    @Test
    fun rebirthLevelsZeroThroughTenAreGolden() {
        val rebirth = ui.rebirth
        val profiles = rebirth.profiles

        assertEquals(10, rebirth.maximumLevel)
        assertEquals(120, rebirth.maxActiveEnemies)
        assertEquals(0.09f, rebirth.minSpawnIntervalSeconds)
        assertEquals(24f, rebirth.minEliteIntervalSeconds)
        assertEquals((0..10).toList(), profiles.map(RebirthProfile::tier))
        assertEquals(
            "1bc30ab35636dce9",
            goldenFingerprint {
                profiles.forEach { profile ->
                    add(profile.tier)
                    add(profile.directive.name)
                    add(profile.openingEnemyCount)
                    add(profile.enemyCapMultiplier)
                    add(profile.spawnRateMultiplier)
                    add(profile.enemyHealthMultiplier)
                    add(profile.enemySpeedMultiplier)
                    add(profile.incomingDamageMultiplier)
                    add(profile.eliteRateMultiplier)
                    add(profile.threatTimeOffsetSeconds)
                    add(profile.playerPowerMultiplier)
                    add(profile.playerIntegrityBonus)
                    add(profile.matterGainMultiplier)
                    add(profile.bonusRerolls)
                    add(profile.mutators.size)
                    profile.mutators.forEach { mutator -> add(mutator.name) }
                    add(profile.packSizeBonus)
                    add(profile.obstacleDensityMultiplier)
                }
            },
        )
    }

    @Test
    fun relicLimitsOrderingAndContentIdentityAreGolden() {
        val relics = ui.relics

        assertEquals(RelicId.entries.size, relics.size)
        assertEquals(true, relics.size <= ContentBounds.MAX_RELICS)
        assertEquals(4, ui.relicPolicy.maxSlots)
        assertEquals(3, ui.relicPolicy.maxRank)
        assertEquals(RelicId.entries.toList(), relics.map(RelicDefinition::id))
        assertEquals(
            "c8c02a43bec87968",
            goldenFingerprint {
                add(relics.size)
                relics.forEach { relic ->
                    add(relic.id.name)
                    add(relic.name)
                    add(relic.aspect.name)
                    relic.keys.forEach { add(it.name) }
                    add("|")
                    relic.attunedTags.forEach { add(it.name) }
                    add("|")
                    add(relic.upside)
                    add(relic.liability)
                    relic.values.forEach { value ->
                        add(value.base)
                        add(value.perRank)
                    }
                    add(relic.minimumRebirth)
                    add(relic.meldable)
                }
            },
        )
    }
}

/** Every Matrix effect kind with its numbers; floats enter by bits, so JVM and Wasm agree. */
private fun GoldenFingerprint.addMatrixEffect(effect: MatrixEffect) {
    when (effect) {
        is MatrixEffect.Stat -> { add("stat"); add(effect.delta.effect.name); add(effect.delta.amount) }
        is MatrixEffect.DamageMultiplier -> { add("damage"); add(effect.fraction) }
        is MatrixEffect.ReactionPower -> { add("reaction"); add(effect.fraction) }
        is MatrixEffect.StatusPotency -> { add("potency"); add(effect.fraction) }
        is MatrixEffect.DashHeat -> { add("dash-heat"); add(effect.multiplier) }
        is MatrixEffect.Drag -> { add("drag"); add(effect.multiplier) }
        is MatrixEffect.StartingWeaponLevel -> { add("weapon-level"); add(effect.levels) }
        is MatrixEffect.StartingOverdrive -> { add("overdrive"); add(effect.charge) }
        is MatrixEffect.SecondWind -> { add("second-wind"); add(effect.integrityFraction); add(effect.invulnerableSeconds) }
        is MatrixEffect.ObstacleGuard -> { add("obstacle-guard"); add(effect.damageMultiplier); add(effect.lethalSpeedBonus) }
        is MatrixEffect.RicochetRig -> { add("ricochet-rig"); add(effect.restitution); add(effect.burstRadius) }
        is MatrixEffect.NearMiss -> {
            add("near-miss"); add(effect.distance); add(effect.minSpeed); add(effect.overdrive); add(effect.cooldown)
        }
        is MatrixEffect.OfferCards -> { add("offer-cards"); add(effect.everyNthOffer) }
        is MatrixEffect.RarityPity -> { add("pity"); add(effect.offers) }
        is MatrixEffect.PickupValue -> { add("pickup-value"); add(effect.fraction) }
        MatrixEffect.EliteBounty -> add("elite-bounty")
        is MatrixEffect.HeirloomOffers -> { add("heirlooms"); add(effect.extra) }
        is MatrixEffect.RebornChance -> { add("reborn"); add(effect.bonus) }
        is MatrixEffect.EnemyIntegrity -> { add("enemy-integrity"); add(effect.multiplier) }
        is MatrixEffect.ContactDamage -> { add("contact"); add(effect.multiplier) }
        is MatrixEffect.FreeDashInterval -> { add("free-dash"); add(effect.seconds) }
        is MatrixEffect.Rerolls -> { add("rerolls"); add(effect.count) }
        is MatrixEffect.WeaponRunGrowth -> { add("weapon-growth"); add(effect.bonusPerProgress) }
        is MatrixEffect.ShieldRegenDelay -> { add("shield-delay"); add(effect.multiplier) }
        is MatrixEffect.RicochetPhase -> { add("ricochet-phase"); add(effect.seconds) }
    }
}

private fun GoldenFingerprint.addItem(item: ItemDefinition) {
    add(item.id)
    add(item.name)
    add(item.description)
    add(item.rarity.name)
    item.tags.forEach { add(it.name) }
    add("|")
    item.elements.forEach { add(it.name) }
    add("|")
    item.stats.forEach { stat ->
        add(stat.effect.name)
        add(stat.amount)
    }
    add("|")
    item.clauses.forEach(::addClause)
    add("|")
    item.rules.forEach(::addRule)
    add("|")
    add(item.maxStacks)
    add(item.unlockLevel)
    add(item.minimumRebirth)
    add(item.risk)
}

private fun GoldenFingerprint.addClause(clause: EffectClause) {
    add(clause.trigger.name)
    clause.conditions.forEach(::addCondition)
    add("actions")
    clause.actions.forEach(::addAction)
    add(clause.cooldownSeconds)
    add(clause.everyNth)
    add(clause.luckShortened)
}

private fun GoldenFingerprint.addCondition(condition: EffectCondition) {
    when (condition) {
        is EffectCondition.SpeedAtLeast -> { add("speed-at-least"); add(condition.speed) }
        is EffectCondition.SpeedBelow -> { add("speed-below"); add(condition.speed) }
        is EffectCondition.IntegrityBelow -> { add("integrity-below"); add(condition.fraction) }
        is EffectCondition.HeatAtLeast -> { add("heat-at-least"); add(condition.heat) }
        EffectCondition.ShieldUp -> add("shield-up")
        EffectCondition.OverdriveActive -> add("overdrive-active")
        EffectCondition.Braking -> add("braking")
        is EffectCondition.ComboAtLeast -> { add("combo-at-least"); add(condition.combo) }
        is EffectCondition.TargetHas -> { add("target-has"); add(condition.element?.name ?: "-"); add(condition.stacks) }
        EffectCondition.TargetElite -> add("target-elite")
        EffectCondition.TargetFrozen -> add("target-frozen")
        is EffectCondition.TargetBelow -> { add("target-below"); add(condition.fraction) }
        EffectCondition.SourceImpact -> add("source-impact")
        EffectCondition.SourceWeapon -> add("source-weapon")
        is EffectCondition.ElementIs -> { add("element-is"); add(condition.element.name) }
    }
}

private fun GoldenFingerprint.addAction(action: EffectAction) {
    when (action) {
        is EffectAction.ApplyStatus -> { add("apply"); add(action.pick.name); add(action.stacks); add(action.radius) }
        is EffectAction.Burst -> { add("burst"); add(action.radius); add(action.basis.name); add(action.scale) }
        is EffectAction.Strike -> { add("strike"); add(action.basis.name); add(action.scale) }
        is EffectAction.Chain -> {
            add("chain"); add(action.count); add(action.range); add(action.basis.name); add(action.scale)
            add(action.pick?.name ?: "-"); add(action.stacks)
        }
        is EffectAction.Shards -> { add("shards"); add(action.count); add(action.basis.name); add(action.scale); add(action.pick?.name ?: "-") }
        is EffectAction.Pull -> { add("pull"); add(action.radius); add(action.strength) }
        is EffectAction.Push -> { add("push"); add(action.radius); add(action.strength) }
        is EffectAction.Heal -> { add("heal"); add(action.amount) }
        is EffectAction.Shield -> { add("shield"); add(action.amount) }
        is EffectAction.Vent -> { add("vent"); add(action.heat) }
        is EffectAction.Overdrive -> { add("overdrive"); add(action.charge) }
        is EffectAction.Buff -> { add("buff"); add(action.stat.name); add(action.amount); add(action.seconds); add(action.maxStacks) }
        is EffectAction.ExtendCombo -> { add("extend-combo"); add(action.seconds) }
        is EffectAction.RefreshWeapon -> { add("refresh-weapon"); add(action.seconds) }
        is EffectAction.GainData -> { add("gain-data"); add(action.amount) }
        is EffectAction.Detonate -> { add("detonate"); action.elements.forEach { add(it.name) }; add(action.scale) }
        is EffectAction.Well -> {
            add("well"); add(action.radius); add(action.seconds); add(action.basis.name); add(action.scale)
            add(action.pick?.name ?: "-")
        }
    }
}

private fun GoldenFingerprint.addRule(rule: ItemRule) {
    when (rule) {
        is ItemRule.WhileModifier -> { add("while"); addCondition(rule.condition); add(rule.stat.name); add(rule.amount) }
        is ItemRule.ElementPotency -> { add("potency"); add(rule.element.name); add(rule.amount) }
        is ItemRule.StatusDuration -> { add("duration"); add(rule.element.name); add(rule.multiplier) }
        is ItemRule.BreakThreshold -> { add("threshold"); add(rule.element.name); add(rule.reduction) }
        is ItemRule.BreakPower -> { add("break-power"); add(rule.element.name); add(rule.amount) }
        is ItemRule.BreakSpread -> { add("break-spread"); add(rule.element.name); add(rule.stacks); add(rule.radius) }
        is ItemRule.ReactionPower -> { add("reaction-power"); add(rule.amount) }
        is ItemRule.ReactionResidue -> { add("reaction-residue"); add(rule.stacks) }
        is ItemRule.ReactionRecoil -> { add("reaction-recoil"); add(rule.damage); add(rule.radius) }
        is ItemRule.AlwaysCrit -> { add("always-crit"); addCondition(rule.condition) }
        is ItemRule.CritDamageVersus -> { add("crit-damage-versus"); addCondition(rule.condition); add(rule.amount) }
        is ItemRule.DamageVersus -> { add("damage-versus"); addCondition(rule.condition); add(rule.amount) }
        is ItemRule.NonCriticalDamage -> { add("non-critical"); add(rule.amount) }
        is ItemRule.ComboDamage -> { add("combo-damage"); add(rule.perCombo); add(rule.cap) }
        is ItemRule.BonusRerolls -> { add("rerolls"); add(rule.count) }
        is ItemRule.GuaranteedCritAfter -> { add("guaranteed-crit"); add(rule.misses) }
        is ItemRule.FreeDash -> { add("free-dash"); add(rule.heat); add(rule.cooldownSeconds) }
        is ItemRule.OfferCards -> { add("offer-cards"); add(rule.count) }
        is ItemRule.Convert -> { add("convert"); add(rule.kind.name); add(rule.amount) }
    }
}

private fun goldenFingerprint(values: GoldenFingerprint.() -> Unit): String =
    GoldenFingerprint().apply(values).finish()

private class GoldenFingerprint {
    private var hash = FNV_OFFSET_BASIS

    fun add(value: String) {
        value.forEach { character -> mix(character.code) }
        mix(FIELD_SEPARATOR)
    }

    fun add(value: Int) = add(value.toString())

    fun add(value: Float) = add(value.toBits())

    fun add(value: Boolean) = add(if (value) 1 else 0)

    fun finish(): String = hash.toString(radix = 16).padStart(16, '0')

    private fun mix(value: Int) {
        hash = (hash xor value.toULong()) * FNV_PRIME
    }

    private companion object {
        const val FIELD_SEPARATOR = 0x1F
        const val FNV_OFFSET_BASIS = 14_695_981_039_346_656_037uL
        const val FNV_PRIME = 1_099_511_628_211uL
    }
}
