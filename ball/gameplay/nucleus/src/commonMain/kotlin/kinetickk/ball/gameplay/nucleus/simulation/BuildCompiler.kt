// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.BuffStat
import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.content.api.ConversionKind
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.EffectAction
import kinetickk.ball.content.api.EffectCondition
import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.content.api.ElementPick
import kinetickk.ball.content.api.EquippedRelic
import kinetickk.ball.content.api.FieldAspect
import kinetickk.ball.content.api.ReactionAspect
import kinetickk.ball.content.api.SKILL_HEAT_CONVERSION_COOLDOWN
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.ItemRule
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.RelicAspect
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicPairingKind
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.breakThresholdCut
import kinetickk.ball.content.api.overhealOverfill
import kinetickk.ball.gameplay.nucleus.model.SkillLoadout
import kinetickk.ball.gameplay.nucleus.model.SkillRuntime
import kinetickk.ball.gameplay.nucleus.render.RelicLinkKind
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList

/** A conditional "while X: Y" modifier collected from owned items, already scaled by magnitude. */
internal data class WhileModifier(val condition: EffectCondition, val stat: BuffStat, val amount: Float)

/** A conditional damage or crit modifier collected from owned items, already scaled by magnitude. */
internal data class ConditionalAmount(val condition: EffectCondition, val amount: Float)

/** How a named or generic relic pair currently resolves for presentation and rules. */


internal data class RelicLink(val first: RelicId, val second: RelicId, val kind: RelicLinkKind, val pairing: kinetickk.ball.content.api.RelicPairingId?)

/**
 * The pure, immutable compilation of a build: owned items, their stacks and Reborn flags, the character,
 * bound and severed relics. Recompiled only when one of those inputs changes and shared by every
 * reduction sibling; nothing here is mutated after construction and nothing writes base stat fields.
 */
internal class CompiledBuild private constructor(
    private val tagPointValues: IntArray,
    private val affinityValues: IntArray,
    val dominant: StatusElement?,
    val secondary: StatusElement?,
    private val clauseLists: Array<IntArray>,
    private val itemMagnitudes: FloatArray,
    private val itemBonuses: FloatArray,
    private val potencyValues: FloatArray,
    private val durationValues: FloatArray,
    private val thresholdCuts: IntArray,
    private val breakPowerValues: FloatArray,
    private val breakSpreadStacks: IntArray,
    private val breakSpreadRadii: FloatArray,
    val reactionPower: Float,
    val reactionResidue: Int,
    val reactionRecoil: Float,
    val reactionRecoilRadius: Float,
    val alwaysCrit: ImmutableList<EffectCondition>,
    val critDamageVersus: ImmutableList<ConditionalAmount>,
    val damageVersus: ImmutableList<ConditionalAmount>,
    val nonCriticalDamage: Float,
    val comboDamagePer: Float,
    val comboDamageCap: Float,
    val offerCards: Int,
    val overhealOverfill: Float,
    val regenerationToDecay: Float,
    val critConversionRate: Float,
    val whileModifiers: ImmutableList<WhileModifier>,
    val guaranteedCritAfter: Int,
    val freeDashHeat: Float,
    val freeDashCooldown: Float,
    private val relicUpsides: FloatArray,
    /** Bits over RelicId ordinals whose upside is active (bound and not spent). */
    val upsideMask: Int,
    /** Bits over RelicId ordinals whose liability is active (bound or severed). */
    val liabilityMask: Int,
    /** Bits over RelicPairingId ordinals whose two relics are both bound. */
    val pairingMask: Int,
    val links: ImmutableList<RelicLink>,
    /** Bits over ReactionId ordinals whose two elements both have a source in the build. */
    val reachableReactionMask: Int,
    val ownedItemCount: Int,
    /** Bits over ReactionId ordinals whose keystone form the build switched on. */
    val keystoneMask: Int = 0,
    /** By RelicKey ordinal: the first bound Relic carrying that key (a weapon with the key harmonizes with it). */
    private val keyHarmonies: Array<RelicId?> = arrayOfNulls(kinetickk.ball.content.api.RelicKey.entries.size),
    /** Skill damage bonus over the base per skill element ([StatusElement] ordinal; the last cell: element-less skills). */
    private val skillPowerBonuses: FloatArray = FloatArray(SKILL_ELEMENT_CELLS),
    /** Static cooldown scale per skill element (rules and conversions), never below [MIN_SKILL_COOLDOWN_SCALE]. */
    private val skillCooldownScales: FloatArray = FloatArray(SKILL_ELEMENT_CELLS) { 1f },
    /** Scale of Catalysis refunds (reactions recharging element-matched skills). */
    val catalysisRate: Float = 1f,
    /** Share cut from the Apex's base cooldown (the ApexCooldown rule; at most [MAX_APEX_COOLDOWN_CUT] applies). */
    val apexCooldownCut: Float = 0f,
    /** Multipliers per (reaction, [ReactionAspect]): 1 plus the tuning the build adds, within [MAX_REACTION_TUNING]. */
    private val reactionTunings: FloatArray = FloatArray(ReactionId.entries.size * ReactionAspect.entries.size) { 1f },
    /** Scales of every player field's radius and life (FieldTuning, within [MAX_FIELD_TUNING]). */
    val fieldRadiusScale: Float = 1f,
    val fieldDurationScale: Float = 1f,
    /** Every skill holds a second charge (the SkillCharges rule). */
    val skillCharges: Boolean = false,
    /** The element every skill takes (the SkillAttunement rule), or null. */
    val skillAttunement: ElementPick? = null,
    /** Extra reach of the Core link (CoreLinkRadius), at most [MAX_CORE_LINK_BONUS]. */
    val coreLinkBonus: Float = 0f,
    /** Conversion amounts; 0 while the build holds none. */
    val dashHeatToSkills: Float = 0f,
    val dataToSkills: Float = 0f,
    val skillsCostHeat: Float = 0f,
    val skillFieldsFollow: Float = 0f,
    val knockbackToDamage: Float = 0f,
) {
    /** Skill damage bonus for a skill of [element] (null: an element-less skill): the shared bonus plus its element's. */
    fun skillPowerBonus(element: StatusElement?): Float =
        skillPowerBonuses[SKILL_ELEMENT_CELLS - 1] + (element?.let { skillPowerBonuses[it.ordinal] } ?: 0f)

    /** Static cooldown scale of a skill of [element]; never below [MIN_SKILL_COOLDOWN_SCALE]. */
    fun skillCooldownScale(element: StatusElement?): Float = skillCooldownScales[element?.ordinal ?: (SKILL_ELEMENT_CELLS - 1)]

    /** The multiplier [ReactionTuning] rules give [reaction]'s [aspect] (1 without any). */
    fun reactionTuning(reaction: ReactionId, aspect: ReactionAspect): Float =
        reactionTunings[reaction.ordinal * ReactionAspect.entries.size + aspect.ordinal]

    /** The first bound Relic carrying [key], or null: a weapon with that key harmonizes with it. */
    fun harmonizing(key: kinetickk.ball.content.api.RelicKey): RelicId? = keyHarmonies[key.ordinal]

    fun tagPoints(tag: BuildTag): Int = tagPointValues[tag.ordinal]
    fun affinity(element: StatusElement): Int = affinityValues[element.ordinal]
    fun clauses(trigger: EffectTrigger): IntArray = clauseLists[trigger.ordinal]
    fun magnitude(itemId: Int): Float = itemMagnitudes[itemId]

    /** Reborn and relic attunement bonus; applies to percentage and amount parts, never to counts. */
    fun bonus(itemId: Int): Float = itemBonuses[itemId]
    fun potency(element: StatusElement): Float = potencyValues[element.ordinal]
    fun durationMultiplier(element: StatusElement): Float = durationValues[element.ordinal]
    fun thresholdCut(element: StatusElement): Int = thresholdCuts[element.ordinal]
    fun breakPower(element: StatusElement): Float = breakPowerValues[element.ordinal]
    fun breakSpread(element: StatusElement): Int = breakSpreadStacks[element.ordinal]
    fun breakSpreadRadius(element: StatusElement): Float = breakSpreadRadii[element.ordinal]
    fun relicUpside(id: RelicId): Float = relicUpsides[id.ordinal]
    fun upside(id: RelicId): Boolean = upsideMask and (1 shl id.ordinal) != 0
    fun liability(id: RelicId): Boolean = liabilityMask and (1 shl id.ordinal) != 0
    fun pairing(id: kinetickk.ball.content.api.RelicPairingId): Boolean = pairingMask and (1 shl id.ordinal) != 0
    fun reactionReachable(id: ReactionId): Boolean = reachableReactionMask and (1 shl id.ordinal) != 0
    fun keystone(id: ReactionId): Boolean = keystoneMask and (1 shl id.ordinal) != 0

    companion object {
        /** Clause references pack the item id and the clause index. */
        fun packClause(itemId: Int, clause: Int): Int = itemId * 4 + clause
        fun clauseItem(packed: Int): Int = packed / 4
        fun clauseIndex(packed: Int): Int = packed % 4

        fun empty(content: GameplayContentSnapshot): CompiledBuild = compile(
            content = content,
            itemStacks = IntArray(content.items.size),
            rebornItems = IntArray(content.items.size),
            acquisitionOrder = emptyList(),
            coreShape = null,
            equipped = emptyList(),
            severed = emptyList(),
            spentOuroboros = false,
        )

        fun compile(
            content: GameplayContentSnapshot,
            itemStacks: IntArray,
            rebornItems: IntArray,
            acquisitionOrder: List<Int>,
            coreShape: CoreShape?,
            equipped: List<EquippedRelic>,
            severed: List<RelicId>,
            spentOuroboros: Boolean,
            statusPotencyBonus: Float = 0f,
            reactionPowerBonus: Float = 0f,
            /** The equipped weapon's Relic key: a bound Relic carrying it harmonizes with the weapon. */
            weaponKey: kinetickk.ball.content.api.RelicKey? = null,
            /** Reaction keystone forms switched on beyond the items' own (no item rule declares one yet). */
            keystoneMask: Int = 0,
            /** Owned skills: each adds tag points and element affinity like an item. */
            skills: SkillLoadout = SkillLoadout.EMPTY,
        ): CompiledBuild {
            val policy = content.build.policy
            val itemCount = content.items.size
            val tagPoints = IntArray(BuildTag.entries.size)
            val affinity = IntArray(StatusElement.entries.size)
            val affinityStamp = IntArray(StatusElement.entries.size)
            val magnitudes = FloatArray(itemCount)
            val bonuses = FloatArray(itemCount)

            val attuned = HashSet<BuildTag>()
            equipped.forEach { relic -> attuned += content.relic(relic.id).attunedTags }

            // Gain order decides affinity ties: each entry is stamped by its position, every owned item
            // appears once, and owned items missing from the order count as the newest gains.
            val itemStamps = IntArray(itemCount)
            val relicStamps = IntArray(RelicId.entries.size)
            val skillStamps = IntArray(SkillId.entries.size)
            var stamp = 1
            val order = ArrayList<Int>(acquisitionOrder.size)
            acquisitionOrder.forEach { entry ->
                if (entry <= SKILL_GAIN_BASE) {
                    val skill = SKILL_GAIN_BASE - entry
                    if (skill in skillStamps.indices) skillStamps[skill] = stamp
                } else if (entry < 0) {
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
            // Owned skills count like items: a point in each tag and in the element, rank III one more.
            val skillPoints = policy.skillTagPoints
            for (slot in 0 until SkillRuntime.SLOTS) {
                val id = skills.skill(slot) ?: continue
                val definition = content.skill(id)
                definition.tags.forEach { tag -> tagPoints[tag.ordinal] += skillPoints }
                val element = definition.element
                val rankThree = skills.rank(slot) >= ContentBounds.MAX_SKILL_RANK
                if (element != null) {
                    affinity[element.ordinal] += if (rankThree) skillPoints * 2 else skillPoints
                    affinityStamp[element.ordinal] = maxOf(affinityStamp[element.ordinal], skillStamps[id.ordinal])
                } else if (rankThree) {
                    tagPoints[definition.tags.first().ordinal] += skillPoints
                }
            }
            coreShape?.characterTag()?.let { tagPoints[it.ordinal] += 1 }
            equipped.forEach { relic ->
                val element = content.relic(relic.id).aspect.element ?: return@forEach
                affinity[element.ordinal] += if (relic.rank >= 3) 2 else 1
                affinityStamp[element.ordinal] = maxOf(affinityStamp[element.ordinal], relicStamps[relic.id.ordinal])
            }

            var dominant: StatusElement? = null
            var secondary: StatusElement? = null
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
            val namedTuning = FloatArray(ReactionId.entries.size * ReactionAspect.entries.size)
            val sharedTuning = FloatArray(ReactionAspect.entries.size)
            var fieldRadius = 0f
            var fieldDuration = 0f
            val skillCooldownCuts = FloatArray(SKILL_ELEMENT_CELLS)
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

            for (id in order) {
                val item = content.items[id]
                item.clauses.forEachIndexed { index, clause -> clauseBuffers[clause.trigger.ordinal] += packClause(id, index) }
                val scale = magnitudes[id] * bonuses[id]
                item.rules.forEach { rule ->
                    when (rule) {
                        is ItemRule.WhileModifier -> whileModifiers += WhileModifier(rule.condition, rule.stat, rule.amount * scale)
                        is ItemRule.ElementPotency -> potency[rule.element.ordinal] += rule.amount * scale
                        is ItemRule.StatusDuration -> duration[rule.element.ordinal] *= 1f + (rule.multiplier - 1f) * magnitudes[id]
                        is ItemRule.BreakThreshold -> cuts[rule.element.ordinal] += breakThresholdCut(rule.reduction, itemStacks[id])
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
                            comboCap = maxOf(comboCap, rule.cap * bonuses[id])
                        }
                        is ItemRule.BonusRerolls -> Unit
                        is ItemRule.OfferCards -> offerCards += rule.count
                        is ItemRule.Convert -> when (rule.kind) {
                            ConversionKind.OVERHEAL_TO_SHIELD -> overfill = maxOf(overfill, overhealOverfill(rule.amount, itemStacks[id]))
                            ConversionKind.REGENERATION_TO_DECAY -> regenDecay = maxOf(regenDecay, rule.amount)
                            ConversionKind.CRIT_CHANCE_TO_DAMAGE -> critConversion = maxOf(critConversion, rule.amount * bonuses[id])
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
                    }
                }
            }
            // Affinity tiers: potency, threshold, overflow (overflow is read directly by the status system).
            for (element in StatusElement.entries) {
                val tier = policy.tier(affinity[element.ordinal], policy.affinityThresholds)
                if (tier >= 1) potency[element.ordinal] += 0.25f
                if (tier >= 2) {
                    cuts[element.ordinal] += 1
                    breakPower[element.ordinal] += 0.4f
                }
            }

            var upsideMask = 0
            var liabilityMask = 0
            equipped.forEach { relic ->
                if (!(relic.id == RelicId.OUROBOROS_SEAL && spentOuroboros)) upsideMask = upsideMask or (1 shl relic.id.ordinal)
                liabilityMask = liabilityMask or (1 shl relic.id.ordinal)
            }
            severed.forEach { liabilityMask = liabilityMask or (1 shl it.ordinal) }

            val upsides = FloatArray(RelicId.entries.size)
            var pairingMask = 0
            val links = ArrayList<RelicLink>()
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

            // Tuning: a shared (all-reaction) row adds at most MAX_SHARED_REACTION_TUNING, each cell at most MAX_REACTION_TUNING.
            val tunings = FloatArray(namedTuning.size) { cell ->
                val shared = minOf(MAX_SHARED_REACTION_TUNING, sharedTuning[cell % ReactionAspect.entries.size])
                1f + minOf(MAX_REACTION_TUNING, namedTuning[cell] + shared)
            }
            // A skill's cooldown cut is the shared cut plus its element's, within MAX_SKILL_COOLDOWN_CUT; Skills Cost
            // Heat multiplies on top; nothing goes below MIN_SKILL_COOLDOWN_SCALE.
            val conversionScale = if (skillsCostHeat > 0f) SKILL_HEAT_CONVERSION_COOLDOWN else 1f
            val cooldownScales = FloatArray(SKILL_ELEMENT_CELLS) { cell ->
                val shared = skillCooldownCuts[SKILL_ELEMENT_CELLS - 1]
                val cut = minOf(MAX_SKILL_COOLDOWN_CUT, if (cell == SKILL_ELEMENT_CELLS - 1) shared else shared + skillCooldownCuts[cell])
                maxOf(MIN_SKILL_COOLDOWN_SCALE, (1f - cut) * conversionScale)
            }

            var reachable = 0
            for (reaction in ReactionId.entries) {
                if (affinity[reaction.first.ordinal] > 0 && affinity[reaction.second.ordinal] > 0) {
                    reachable = reachable or (1 shl reaction.ordinal)
                }
            }

            return CompiledBuild(
                tagPointValues = tagPoints,
                affinityValues = affinity,
                dominant = dominant,
                secondary = secondary,
                clauseLists = Array(clauseBuffers.size) { clauseBuffers[it].toIntArray() },
                itemMagnitudes = magnitudes,
                itemBonuses = bonuses,
                potencyValues = potency,
                durationValues = duration,
                thresholdCuts = cuts,
                breakPowerValues = breakPower,
                breakSpreadStacks = spreadStacks,
                breakSpreadRadii = spreadRadius,
                reactionPower = reactionPower,
                reactionResidue = residue,
                reactionRecoil = recoil,
                reactionRecoilRadius = recoilRadius,
                alwaysCrit = alwaysCrit.toImmutableList(),
                critDamageVersus = critVersus.toImmutableList(),
                damageVersus = damageVersus.toImmutableList(),
                nonCriticalDamage = nonCrit,
                comboDamagePer = comboPer,
                comboDamageCap = comboCap,
                offerCards = offerCards,
                overhealOverfill = overfill,
                regenerationToDecay = regenDecay,
                critConversionRate = critConversion,
                whileModifiers = whileModifiers.toImmutableList(),
                guaranteedCritAfter = critAfter,
                freeDashHeat = freeDashHeat,
                freeDashCooldown = freeDashCooldown,
                relicUpsides = upsides,
                upsideMask = upsideMask,
                liabilityMask = liabilityMask,
                pairingMask = pairingMask,
                links = links.toImmutableList(),
                reachableReactionMask = reachable,
                ownedItemCount = order.size,
                keystoneMask = keystones and ((1 shl ReactionId.entries.size) - 1),
                keyHarmonies = arrayOfNulls<RelicId>(kinetickk.ball.content.api.RelicKey.entries.size).also { harmonies ->
                    for (relic in equipped) for (key in content.relic(relic.id).keys) {
                        if (harmonies[key.ordinal] == null) harmonies[key.ordinal] = relic.id
                    }
                },
                skillPowerBonuses = skillPowerBonuses,
                skillCooldownScales = cooldownScales,
                catalysisRate = 1f + minOf(MAX_CATALYSIS_BONUS, catalysis),
                apexCooldownCut = minOf(MAX_APEX_COOLDOWN_CUT, apexCut),
                reactionTunings = tunings,
                fieldRadiusScale = 1f + minOf(MAX_FIELD_TUNING, fieldRadius),
                fieldDurationScale = 1f + minOf(MAX_FIELD_TUNING, fieldDuration),
                skillCharges = skillCharges,
                skillAttunement = attunement,
                coreLinkBonus = minOf(MAX_CORE_LINK_BONUS, linkBonus),
                dashHeatToSkills = dashHeatToSkills,
                dataToSkills = dataToSkills,
                skillsCostHeat = skillsCostHeat,
                skillFieldsFollow = skillFieldsFollow,
                knockbackToDamage = knockbackToDamage,
            )
        }
    }
}

/** Skill-indexed compiled cells: one per [StatusElement] plus a last one for every skill (or element-less skills). */
internal val SKILL_ELEMENT_CELLS: Int = StatusElement.entries.size + 1

/** ReactionTuning: at most this much per (reaction, aspect) in total, and at most [MAX_SHARED_REACTION_TUNING] from all-reaction rows. */
internal const val MAX_REACTION_TUNING: Float = 0.75f
internal const val MAX_SHARED_REACTION_TUNING: Float = 0.5f
/** FieldTuning: at most this much per aspect. */
internal const val MAX_FIELD_TUNING: Float = 0.5f
/** A tuned reaction field grows at most this much in total (reaction tuning times field tuning). */
internal const val MAX_TUNED_FIELD_SCALE: Float = 1.75f
/** No tuned radius reaches beyond this. */
internal const val MAX_TUNED_RADIUS: Float = 600f
/** SkillCooldown: at most this cut on any skill. */
internal const val MAX_SKILL_COOLDOWN_CUT: Float = 0.4f
/** CatalysisRate: at most doubles Catalysis. */
internal const val MAX_CATALYSIS_BONUS: Float = 1f
/** CoreLinkRadius: at most this much farther. */
internal const val MAX_CORE_LINK_BONUS: Float = 240f

/** Characters lean toward an archetype without locking the build. */
internal fun CoreShape.characterTag(): BuildTag = when (this) {
    CoreShape.ORB, CoreShape.TESSERACT -> BuildTag.DRIVE
    CoreShape.PRISM, CoreShape.DIAMOND -> BuildTag.ANCHOR
    CoreShape.SHARD, CoreShape.CRESCENT -> BuildTag.PRECISION
    CoreShape.RING, CoreShape.SPIRAL -> BuildTag.BULWARK
    CoreShape.STAR -> BuildTag.RAM
    CoreShape.DIPOLE -> BuildTag.CHAIN
    CoreShape.PENTAGON -> BuildTag.OVERCLOCK
    CoreShape.HEXAGON -> BuildTag.ARSENAL
}

internal fun MutableGameState.tagTier(tag: BuildTag): Int =
    content.build.policy.tier(build.tagPoints(tag), content.build.policy.resonanceThresholds)

internal fun MutableGameState.affinityTier(element: StatusElement): Int =
    content.build.policy.tier(build.affinity(element), content.build.policy.affinityThresholds)

/** A relic bind or rank-three upgrade in [MutableGameState.acquisitionOrder]; item ids stay non-negative. */
internal fun relicGain(id: RelicId): Int = -(1 + id.ordinal)

/** Skill gains sit far below every relic gain in [MutableGameState.acquisitionOrder]. */
internal const val SKILL_GAIN_BASE: Int = -1_000

/** A skill draft or rank-up in [MutableGameState.acquisitionOrder]. */
internal fun skillGain(id: SkillId): Int = SKILL_GAIN_BASE - id.ordinal

/** Records [id]'s affinity gain as the newest one; each skill appears at most once. */
internal fun MutableGameState.recordSkillGain(id: SkillId) {
    val entry = skillGain(id)
    acquisitionOrder = acquisitionOrder.filter { it != entry } + entry
}

private fun relicOfGain(entry: Int): Int = -entry - 1

/** Records [id]'s affinity gain as the newest one; each relic appears at most once. */
internal fun MutableGameState.recordRelicGain(id: RelicId) {
    val entry = relicGain(id)
    acquisitionOrder = acquisitionOrder.filter { it != entry } + entry
}

/** Recompiles after an item, relic or severance change. */
internal fun MutableGameState.recompileBuild() {
    build = compileBuild()
    reconcileIntegrityLiability()
}

/** The build the run's items, Relics, character, Matrix and weapon compile to, with [keystoneMask] forms on top. */
internal fun MutableGameState.compileBuild(keystoneMask: Int = 0): CompiledBuild = CompiledBuild.compile(
    content = content,
    itemStacks = IntArray(itemStacks.size) { itemStacks[it] },
    rebornItems = IntArray(rebornItems.size) { rebornItems[it] },
    acquisitionOrder = acquisitionOrder,
    coreShape = coreShape,
    equipped = equippedRelics,
    severed = severedRelics.map { it.id },
    spentOuroboros = ouroborosSpent,
    statusPotencyBonus = matrix.statusPotencyBonus,
    reactionPowerBonus = matrix.reactionPowerBonus,
    weaponKey = currentWeaponDefinition.key,
    keystoneMask = keystoneMask,
    skills = skillLoadout,
)

/** True when bound relic liabilities disable every effect of [item] (offers weight it 0; cards strike it). */
internal fun MutableGameState.itemSuppressedShare(item: ItemDefinition): Float {
    var total = 0
    var suppressed = 0
    fun count(disabled: Boolean) {
        total++
        if (disabled) suppressed++
    }
    val verdant = build.liability(RelicId.VERDANT_ROT)
    val glass = build.liability(RelicId.GLASS_COMET)
    val frozenOut = build.liability(RelicId.ABSOLUTE_ZERO)
    item.stats.forEach { stat ->
        count(
            (verdant && stat.effect == ItemEffect.REGEN && build.regenerationToDecay == 0f) ||
                (glass && (stat.effect == ItemEffect.SHIELD_CAPACITY || stat.effect == ItemEffect.DAMAGE_REDUCTION)),
        )
    }
    item.clauses.forEach { clause ->
        clause.actions.forEach { action ->
            count(
                (verdant && action is EffectAction.Heal) ||
                    (glass && action is EffectAction.Shield) ||
                    (glass && clause.trigger == EffectTrigger.SHIELD_BREAK),
            )
        }
        clause.conditions.forEach { condition ->
            if (frozenOut && condition is EffectCondition.ElementIs && condition.element == StatusElement.THERMAL) count(true)
        }
    }
    item.rules.forEach { rule ->
        count(
            frozenOut && when (rule) {
                is ItemRule.ElementPotency -> rule.element == StatusElement.THERMAL
                is ItemRule.StatusDuration -> rule.element == StatusElement.THERMAL
                is ItemRule.BreakThreshold -> rule.element == StatusElement.THERMAL
                is ItemRule.BreakPower -> rule.element == StatusElement.THERMAL
                is ItemRule.BreakSpread -> rule.element == StatusElement.THERMAL
                else -> false
            } || glass && rule is ItemRule.Convert && rule.kind == ConversionKind.OVERHEAL_TO_SHIELD,
        )
    }
    return if (total == 0) 0f else suppressed.toFloat() / total
}

/**
 * The share of [item]'s parts this run cannot use: skill parts (skill triggers and conditions, skill refunds,
 * skill buffs, skill rules, Catalysis and the skill conversions) while no skill is owned, and parts gated on a
 * weapon family other than the equipped one. Kept apart from [itemSuppressedShare], which Relics cause; Apex parts
 * are never dormant (each refunds skills before the Apex unlocks).
 */
internal fun MutableGameState.itemDormantShare(item: ItemDefinition): Float {
    val noSkills = skillLoadout.ownedCount == 0
    val family = currentWeaponDefinition.family
    fun conditionDormant(condition: EffectCondition): Boolean = when (condition) {
        is EffectCondition.SkillIs, is EffectCondition.SkillTagged, is EffectCondition.SkillsCharging -> noSkills
        is EffectCondition.WeaponFamilyIs -> condition.family != family
        else -> false
    }
    var total = 0
    var dormant = 0
    fun count(isDormant: Boolean) {
        total++
        if (isDormant) dormant++
    }
    total += item.stats.size
    item.clauses.forEach { clause ->
        val clauseDormant = noSkills && (clause.trigger == EffectTrigger.SKILL_CAST || clause.trigger == EffectTrigger.SKILL_HIT) ||
            clause.conditions.any(::conditionDormant)
        clause.actions.forEach { action ->
            count(
                clauseDormant || noSkills && (action is EffectAction.RefundSkills ||
                    action is EffectAction.Buff && action.stat.isSkillStat),
            )
        }
    }
    item.rules.forEach { rule ->
        count(
            when (rule) {
                is ItemRule.WhileModifier -> conditionDormant(rule.condition) || noSkills && rule.stat.isSkillStat
                is ItemRule.SkillCooldown, is ItemRule.SkillPower, ItemRule.SkillCharges, is ItemRule.SkillAttunement,
                is ItemRule.CatalysisRate,
                -> noSkills
                is ItemRule.Convert -> noSkills && rule.kind in SkillConversions
                else -> false
            },
        )
    }
    return if (total == 0 || dormant == 0) 0f else dormant.toFloat() / total
}

private val BuffStat.isSkillStat: Boolean get() = this == BuffStat.SKILL_RECHARGE || this == BuffStat.SKILL_POWER

private val SkillConversions: Set<ConversionKind> = setOf(
    ConversionKind.DASH_HEAT_TO_SKILLS, ConversionKind.DATA_TO_SKILLS, ConversionKind.SKILLS_COST_HEAT,
    ConversionKind.SKILL_FIELDS_FOLLOW,
)

internal val RelicAspect.isElemental: Boolean get() = element != null

internal fun emptyRelicLinks(): ImmutableList<RelicLink> = immutableListOf()
