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
import kinetickk.ball.content.api.MetaUpgradeId
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

        assertEquals(90, items.size)
        assertEquals((0 until items.size).toList(), items.map(ItemDefinition::id))
        assertEquals(
            "a1f3dc274648db7d",
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
            "f0e05a0fd34929c0",
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
    fun weaponAndMetaUpgradeEconomyPoliciesAreGolden() {
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
            ),
            ui.weapons.map { weapon -> weapon.id to weapon.permanentUnlockCost },
        )
        assertEquals(
            listOf(
                MetaPolicy(MetaUpgradeId.CORE_INTEGRITY, maxRanks = 10, baseCost = 18),
                MetaPolicy(MetaUpgradeId.KINETIC_AMPLIFIER, maxRanks = 10, baseCost = 22),
                MetaPolicy(MetaUpgradeId.MAGNETIC_RESONANCE, maxRanks = 8, baseCost = 24),
                MetaPolicy(MetaUpgradeId.CRYO_VENTS, maxRanks = 8, baseCost = 26),
                MetaPolicy(MetaUpgradeId.DASH_CAPACITOR, maxRanks = 8, baseCost = 30),
                MetaPolicy(MetaUpgradeId.SALVAGE_PROTOCOL, maxRanks = 10, baseCost = 34),
                MetaPolicy(MetaUpgradeId.DATA_ARCHIVE, maxRanks = 10, baseCost = 38),
                MetaPolicy(MetaUpgradeId.ARMORY_LICENSE, maxRanks = 12, baseCost = 45),
            ),
            ui.metaUpgrades.map { upgrade ->
                MetaPolicy(upgrade.id, upgrade.maxRanks, upgrade.baseCost)
            },
        )
        assertEquals(
            "4542ace8c0367bdd",
            goldenFingerprint {
                add("weapons")
                ui.weapons.forEach { weapon ->
                    add(weapon.id.name)
                    add(weapon.name)
                    add(weapon.description)
                    add(weapon.tags.size)
                    weapon.tags.forEach(::add)
                    add(weapon.permanentUnlockCost)
                }
                add("meta-upgrades")
                ui.metaUpgrades.forEach { upgrade ->
                    add(upgrade.id.name)
                    add(upgrade.name)
                    add(upgrade.description)
                    add(upgrade.maxRanks)
                    add(upgrade.baseCost)
                    add(upgrade.modifierPerRank.effect.name)
                    add(upgrade.modifierPerRank.amount)
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
            "a702047801b99cd7",
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

private data class MetaPolicy(
    val id: MetaUpgradeId,
    val maxRanks: Int,
    val baseCost: Int,
)

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
