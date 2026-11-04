// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.builds

import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.ConversionKind
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.EffectCondition
import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.content.api.ElementPick
import kinetickk.ball.content.api.EquippedRelic
import kinetickk.ball.content.api.FieldAspect
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.ItemRule
import kinetickk.ball.content.api.ObstacleAspect
import kinetickk.ball.content.api.ReactionAspect
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicPairingKind
import kinetickk.ball.content.api.SKILL_HEAT_CONVERSION_COOLDOWN
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.breakThresholdCut
import kinetickk.ball.content.api.overhealOverfill
import kinetickk.ball.gameplay.nucleus.render.RelicLinkKind
import kinetickk.ball.gameplay.nucleus.simulation.skills.*
import kinetickk.ball.gameplay.nucleus.simulation.weapons.*

/**
 * The totals [CompiledBuild.compile] folds from a build's inputs, in its order: gain stamps, item affinity, the
 * dominant and secondary elements, item and boon rules with the affinity tiers, relic masks, relic weights and links,
 * the capped tunings and the reachable reactions. [fold] runs every pass once; [CompiledBuild.compile] then reads the
 * totals into the immutable build; an instance serves one compilation.
 */
internal class BuildCompilerTotals(
    private val content: GameplayContentSnapshot,
    private val itemStacks: IntArray,
    private val rebornItems: IntArray,
    private val acquisitionOrder: List<Int>,
    private val coreShape: CoreShape?,
    private val equipped: List<EquippedRelic>,
    private val severed: List<RelicId>,
    private val spentOuroboros: Boolean,
    statusPotencyBonus: Float,
    reactionPowerBonus: Float,
    private val weaponKey: kinetickk.ball.content.api.RelicKey?,
    keystoneMask: Int,
    private val boonRules: List<ItemRule>,
) {
    private val policy = content.build.policy
    private val itemCount = content.items.size
    val tagPoints = IntArray(BuildTag.entries.size)
    val affinity = IntArray(StatusElement.entries.size)
    private val affinityStamp = IntArray(StatusElement.entries.size)
    val magnitudes = FloatArray(itemCount)
    val bonuses = FloatArray(itemCount)

    private val attuned = HashSet<BuildTag>()

    private val itemStamps = IntArray(itemCount)
    private val relicStamps = IntArray(RelicId.entries.size)
    val order = ArrayList<Int>(acquisitionOrder.size)

    var dominant: StatusElement? = null
    var secondary: StatusElement? = null

    val clauseBuffers = Array(EffectTrigger.entries.size) { ArrayList<Int>() }
    val potency = FloatArray(StatusElement.entries.size) { 1f + statusPotencyBonus }
    val duration = FloatArray(StatusElement.entries.size) { 1f }
    val cuts = IntArray(StatusElement.entries.size)
    val breakPower = FloatArray(StatusElement.entries.size) { 1f }
    val spreadStacks = IntArray(StatusElement.entries.size)
    val spreadRadius = FloatArray(StatusElement.entries.size)
    var reactionPower = 1f + reactionPowerBonus
    var residue = 0
    var recoil = 0f
    var recoilRadius = 0f
    val alwaysCrit = ArrayList<EffectCondition>()
    val critVersus = ArrayList<ConditionalAmount>()
    val damageVersus = ArrayList<ConditionalAmount>()
    var nonCrit = 0f
    var comboPer = 0f
    var comboCap = 0f
    var offerCards = 0
    var overfill = 0f
    var regenDecay = 0f
    var critConversion = 0f
    val whileModifiers = ArrayList<WhileModifier>()
    var critAfter = 0
    var freeDashHeat = 0f
    var freeDashCooldown = 0f
    var keystones = keystoneMask
    private val namedTuning = FloatArray(ReactionId.entries.size * ReactionAspect.entries.size)
    private val sharedTuning = FloatArray(ReactionAspect.entries.size)
    var fieldRadius = 0f
    var fieldDuration = 0f
    private val skillCooldownCuts = FloatArray(SKILL_ELEMENT_CELLS)
    val skillPowerBonuses = FloatArray(SKILL_ELEMENT_CELLS)
    var skillCharges = false
    var attunement: ElementPick? = null
    var catalysis = 0f
    var apexCut = 0f
    var linkBonus = 0f
    var dashHeatToSkills = 0f
    var dataToSkills = 0f
    var skillsCostHeat = 0f
    var skillFieldsFollow = 0f
    var knockbackToDamage = 0f
    var obstacleRestitution = 0f
    var obstacleDamageCut = 0f
    var obstacleLethal = 0f
    var boostPower = 0f
    var wellPull = 0f

    var upsideMask = 0
    var liabilityMask = 0

    val upsides = FloatArray(RelicId.entries.size)
    var pairingMask = 0
    val links = ArrayList<RelicLink>()
    lateinit var tunings: FloatArray
    lateinit var cooldownScales: FloatArray
    var reachable = 0

    fun fold() {
        equipped.forEach { relic -> attuned += content.relic(relic.id).attunedTags }
        stampGains()
        foldItems()
        rankElements()
        foldRules()
        maskRelics()
        weighRelics()
        capTunings()
        findReachable()
    }

    /**
     * Gain order decides affinity ties: each entry is stamped by its position, every owned item
     * appears once, and owned items missing from the order count as the newest gains.
     */
    private fun stampGains() {
        var stamp = 1
        acquisitionOrder.forEach { entry ->
            if (entry < 0) {
                val relic = relicOfGain(entry)
                if (relic in relicStamps.indices) relicStamps[relic] = stamp
            } else if (entry < itemCount && itemStacks[entry] > 0 && itemStamps[entry] == 0) {
                order += entry
                itemStamps[entry] = stamp
            }
            stamp++
        }
        for (id in 0 until itemCount) if (itemStacks[id] > 0 && itemStamps[id] == 0) {
            order += id
            itemStamps[id] = stamp++
        }
    }

    /** Item magnitudes, bonuses, tag points and element affinity, then the character tag and the bound relics' elements. */
    private fun foldItems() {
        for (id in order) {
            val item = content.items[id]
            val stacks = itemStacks[id].coerceIn(0, item.maxStacks)
            val reborn = rebornItems[id] != 0
            val weight = if (reborn) 2 else 1
            magnitudes[id] = policy.stackMagnitude(stacks)
            val attunement = if (item.tags.any { it in attuned }) 1f + policy.attunementBonus else 1f
            bonuses[id] = (if (reborn) policy.rebornMagnitude else 1f) * attunement
            item.tags.forEach { tag -> tagPoints[tag.ordinal] += weight }
            item.elements.forEach { element ->
                affinity[element.ordinal] += weight
                affinityStamp[element.ordinal] = maxOf(affinityStamp[element.ordinal], itemStamps[id])
            }
            if (stacks >= item.maxStacks) {
                val masteredElement = item.elements.firstOrNull()
                if (masteredElement != null) {
                    affinity[masteredElement.ordinal] += policy.masteredTagPoints
                } else {
                    item.tags.firstOrNull()?.let { tagPoints[it.ordinal] += policy.masteredTagPoints }
                }
            }
        }
        coreShape?.characterTag()?.let { tagPoints[it.ordinal] += 1 }
        equipped.forEach { relic ->
            val element = content.relic(relic.id).aspect.element ?: return@forEach
            affinity[element.ordinal] += if (relic.rank >= 3) 2 else 1
            affinityStamp[element.ordinal] = maxOf(affinityStamp[element.ordinal], relicStamps[relic.id.ordinal])
        }
    }

    /** The dominant and secondary elements by affinity; ties go to the newer gain. */
    private fun rankElements() {
        for (element in StatusElement.entries) {
            val value = affinity[element.ordinal]
            if (value <= 0) continue
            val current = dominant
            if (current == null || value > affinity[current.ordinal] ||
                (value == affinity[current.ordinal] && affinityStamp[element.ordinal] > affinityStamp[current.ordinal])
            ) {
                secondary = current
                dominant = element
            } else {
                val second = secondary
                if (second == null || value > affinity[second.ordinal] ||
                    (value == affinity[second.ordinal] && affinityStamp[element.ordinal] > affinityStamp[second.ordinal])
                ) secondary = element
            }
        }
    }

    private fun applyRule(rule: ItemRule, magnitude: Float, bonus: Float, stacks: Int) {
        val scale = magnitude * bonus
        when (rule) {
            is ItemRule.WhileModifier -> whileModifiers += WhileModifier(rule.condition, rule.stat, rule.amount * scale)
            is ItemRule.ElementPotency -> potency[rule.element.ordinal] += rule.amount * scale
            is ItemRule.StatusDuration -> duration[rule.element.ordinal] *= 1f + (rule.multiplier - 1f) * magnitude
            is ItemRule.BreakThreshold -> cuts[rule.element.ordinal] += breakThresholdCut(rule.reduction, stacks)
            is ItemRule.BreakPower -> breakPower[rule.element.ordinal] += rule.amount * scale
            is ItemRule.BreakSpread -> {
                spreadStacks[rule.element.ordinal] = maxOf(spreadStacks[rule.element.ordinal], rule.stacks)
                spreadRadius[rule.element.ordinal] = maxOf(spreadRadius[rule.element.ordinal], rule.radius)
            }
            is ItemRule.ReactionPower -> reactionPower += rule.amount * scale
            is ItemRule.ReactionResidue -> residue = maxOf(residue, rule.stacks)
            is ItemRule.ReactionRecoil -> {
                recoil += rule.damage
                recoilRadius = maxOf(recoilRadius, rule.radius)
            }
            is ItemRule.AlwaysCrit -> alwaysCrit += rule.condition
            is ItemRule.CritDamageVersus -> critVersus += ConditionalAmount(rule.condition, rule.amount * scale)
            is ItemRule.DamageVersus -> damageVersus += ConditionalAmount(rule.condition, rule.amount * scale)
            is ItemRule.NonCriticalDamage -> nonCrit += rule.amount
            is ItemRule.ComboDamage -> {
                comboPer += rule.perCombo * scale
                comboCap = maxOf(comboCap, rule.cap * bonus)
            }
            is ItemRule.BonusRerolls -> Unit
            is ItemRule.OfferCards -> offerCards += rule.count
            is ItemRule.Convert -> when (rule.kind) {
                ConversionKind.OVERHEAL_TO_SHIELD -> overfill = maxOf(overfill, overhealOverfill(rule.amount, stacks))
                ConversionKind.REGENERATION_TO_DECAY -> regenDecay = maxOf(regenDecay, rule.amount)
                ConversionKind.CRIT_CHANCE_TO_DAMAGE -> critConversion = maxOf(critConversion, rule.amount * bonus)
                ConversionKind.DASH_HEAT_TO_SKILLS -> dashHeatToSkills = maxOf(dashHeatToSkills, rule.amount)
                ConversionKind.DATA_TO_SKILLS -> dataToSkills = maxOf(dataToSkills, rule.amount)
                ConversionKind.SKILLS_COST_HEAT -> skillsCostHeat = maxOf(skillsCostHeat, rule.amount)
                // Several followers keep the largest radius share.
                ConversionKind.SKILL_FIELDS_FOLLOW -> skillFieldsFollow = maxOf(skillFieldsFollow, rule.amount)
                ConversionKind.KNOCKBACK_TO_DAMAGE -> knockbackToDamage = maxOf(knockbackToDamage, rule.amount)
            }
            is ItemRule.GuaranteedCritAfter -> critAfter = if (critAfter == 0) rule.misses else minOf(critAfter, rule.misses)
            is ItemRule.FreeDash -> {
                freeDashHeat = maxOf(freeDashHeat, rule.heat)
                freeDashCooldown = if (freeDashCooldown == 0f) rule.cooldownSeconds else minOf(freeDashCooldown, rule.cooldownSeconds)
            }
            is ItemRule.ReactionKeystone -> keystones = keystones or (1 shl rule.reaction.ordinal)
            is ItemRule.ReactionTuning -> when (val reaction = rule.reaction) {
                null -> sharedTuning[rule.aspect.ordinal] += rule.amount * scale
                else -> namedTuning[reaction.ordinal * ReactionAspect.entries.size + rule.aspect.ordinal] += rule.amount * scale
            }
            is ItemRule.FieldTuning -> when (rule.aspect) {
                FieldAspect.RADIUS -> fieldRadius += rule.amount * scale
                FieldAspect.DURATION -> fieldDuration += rule.amount * scale
            }
            is ItemRule.SkillCooldown -> skillCooldownCuts[rule.element?.ordinal ?: (SKILL_ELEMENT_CELLS - 1)] += rule.amount * scale
            is ItemRule.SkillPower -> skillPowerBonuses[rule.element?.ordinal ?: (SKILL_ELEMENT_CELLS - 1)] += rule.amount * scale
            ItemRule.SkillCharges -> skillCharges = true
            is ItemRule.SkillAttunement -> if (attunement == null) attunement = rule.pick
            is ItemRule.CatalysisRate -> catalysis += rule.amount * scale
            is ItemRule.ApexCooldown -> apexCut += rule.amount * scale
            is ItemRule.CoreLinkRadius -> linkBonus += rule.bonus * scale
            is ItemRule.ObstacleTuning -> when (rule.aspect) {
                ObstacleAspect.RESTITUTION -> obstacleRestitution += rule.amount * scale
                ObstacleAspect.COLLISION_DAMAGE -> obstacleDamageCut += rule.amount * scale
                ObstacleAspect.LETHAL_SPEED -> obstacleLethal += rule.amount * scale
                ObstacleAspect.BOOST_POWER -> boostPower += rule.amount * scale
                ObstacleAspect.WELL_PULL -> wellPull += rule.amount * scale
            }
        }
    }

    /** Item clauses and rules in gain order, then the boon rules, then the affinity tiers. */
    private fun foldRules() {
        for (id in order) {
            val item = content.items[id]
            item.clauses.forEachIndexed { index, clause -> clauseBuffers[clause.trigger.ordinal] += CompiledBuild.packClause(id, index) }
            item.rules.forEach { applyRule(it, magnitudes[id], bonuses[id], itemStacks[id]) }
        }
        boonRules.forEach { applyRule(it, magnitude = 1f, bonus = 1f, stacks = 1) }
        // Affinity tiers: potency, threshold, overflow (overflow is read directly by the status system).
        for (element in StatusElement.entries) {
            val tier = policy.tier(affinity[element.ordinal], policy.affinityThresholds)
            if (tier >= 1) potency[element.ordinal] += 0.25f
            if (tier >= 2) {
                cuts[element.ordinal] += 1
                breakPower[element.ordinal] += 0.4f
            }
        }
    }

    private fun maskRelics() {
        equipped.forEach { relic ->
            if (!(relic.id == RelicId.OUROBOROS_SEAL && spentOuroboros)) upsideMask = upsideMask or (1 shl relic.id.ordinal)
            liabilityMask = liabilityMask or (1 shl relic.id.ordinal)
        }
        severed.forEach { liabilityMask = liabilityMask or (1 shl it.ordinal) }
    }

    /** Each bound relic's upside weight from its pairings, dissonance, harmony, the Crown and the weapon key; the links. */
    private fun weighRelics() {
        // The Crown's weakening is its liability, so it lingers through severance.
        val crownBound = liabilityMask and (1 shl RelicId.HOLLOW_CROWN.ordinal) != 0
        for (index in equipped.indices) {
            val relic = content.relic(equipped[index].id)
            var multiplier = 1f
            for (otherIndex in equipped.indices) {
                if (otherIndex == index) continue
                val other = content.relic(equipped[otherIndex].id)
                val named = content.build.pairing(relic.id, other.id)
                if (named != null) {
                    pairingMask = pairingMask or (1 shl named.id.ordinal)
                    if (otherIndex > index) links += RelicLink(relic.id, other.id,
                        if (named.kind == RelicPairingKind.CONCORD) RelicLinkKind.CONCORD else RelicLinkKind.DISCORD, named.id)
                } else if (relic.dissonates(other)) {
                    multiplier *= policy.dissonanceScale
                    if (otherIndex > index) links += RelicLink(relic.id, other.id, RelicLinkKind.DISSONANCE, null)
                } else if (relic.harmonizes(other)) {
                    multiplier *= policy.harmonyScale
                    if (otherIndex > index) links += RelicLink(relic.id, other.id, RelicLinkKind.HARMONY, null)
                }
            }
            if (crownBound && relic.id != RelicId.HOLLOW_CROWN) multiplier *= 0.75f
            if (weaponKey != null && weaponKey in relic.keys) multiplier *= HARMONY_MAGNITUDE
            upsides[relic.id.ordinal] = multiplier
        }
    }

    private fun capTunings() {
        // Tuning: a shared (all-reaction) row adds at most MAX_SHARED_REACTION_TUNING, each cell at most MAX_REACTION_TUNING.
        tunings = FloatArray(namedTuning.size) { cell ->
            val shared = minOf(MAX_SHARED_REACTION_TUNING, sharedTuning[cell % ReactionAspect.entries.size])
            1f + minOf(MAX_REACTION_TUNING, namedTuning[cell] + shared)
        }
        // A skill's cooldown cut is the shared cut plus its element's, within MAX_SKILL_COOLDOWN_CUT; Skills Cost
        // Heat multiplies on top; nothing goes below MIN_SKILL_COOLDOWN_SCALE.
        val conversionScale = if (skillsCostHeat > 0f) SKILL_HEAT_CONVERSION_COOLDOWN else 1f
        cooldownScales = FloatArray(SKILL_ELEMENT_CELLS) { cell ->
            val shared = skillCooldownCuts[SKILL_ELEMENT_CELLS - 1]
            val cut = minOf(MAX_SKILL_COOLDOWN_CUT, if (cell == SKILL_ELEMENT_CELLS - 1) shared else shared + skillCooldownCuts[cell])
            maxOf(MIN_SKILL_COOLDOWN_SCALE, (1f - cut) * conversionScale)
        }
    }

    private fun findReachable() {
        for (reaction in ReactionId.entries) {
            if (affinity[reaction.first.ordinal] > 0 && affinity[reaction.second.ordinal] > 0) {
                reachable = reachable or (1 shl reaction.ordinal)
            }
        }
    }
}

private fun relicOfGain(entry: Int): Int = -entry - 1
