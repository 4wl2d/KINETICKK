// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.builds

import kinetickk.ball.content.api.BuffStat
import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.EffectCondition
import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.content.api.ElementPick
import kinetickk.ball.content.api.EquippedRelic
import kinetickk.ball.content.api.ReactionAspect
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.ItemRule
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.RelicAspect
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.render.RelicLinkKind
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.toImmutableList
import kinetickk.ball.gameplay.nucleus.simulation.characters.*

/** A conditional "while X: Y" modifier collected from owned items, already scaled by magnitude. */
internal data class WhileModifier(val condition: EffectCondition, val stat: BuffStat, val amount: Float)

/** A conditional damage or crit modifier collected from owned items, already scaled by magnitude. */
internal data class ConditionalAmount(val condition: EffectCondition, val amount: Float)

/** How a named or generic relic pair currently resolves for presentation and rules. */


internal data class RelicLink(val first: RelicId, val second: RelicId, val kind: RelicLinkKind, val pairing: kinetickk.ball.content.api.RelicPairingId?)

/**
 * The pure, immutable compilation of a build: owned items, their stacks and Reborn flags, the character,
 * bound and severed relics, and lasting dungeon aspects. Recompiled only when one of those inputs changes and shared by every
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
    /** ObstacleTuning: restitution added to obstacle bounces, at most [MAX_OBSTACLE_RESTITUTION_BONUS]. */
    val obstacleRestitutionBonus: Float = 0f,
    /** ObstacleTuning: scale of obstacle damage to the Core, cut by at most [MAX_OBSTACLE_DAMAGE_CUT]. */
    val obstacleDamageScale: Float = 1f,
    /** ObstacleTuning: lethal collision speed added, at most [MAX_ITEM_LETHAL_BONUS]. */
    val obstacleLethalBonus: Float = 0f,
    /** ObstacleTuning: scale of accelerator boosts, raised by at most [MAX_BOOST_POWER_BONUS]. */
    val boostPowerScale: Float = 1f,
    /** ObstacleTuning: scale of gravity knot pull on enemies, raised by at most [MAX_WELL_PULL_BONUS]. */
    val wellPullScale: Float = 1f,
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
            /** Lasting dungeon rules, folded once without item stack or Reborn multipliers. */
            boonRules: List<ItemRule> = emptyList(),
        ): CompiledBuild {
            val folded = BuildCompilerTotals(
                content, itemStacks, rebornItems, acquisitionOrder, coreShape, equipped, severed, spentOuroboros,
                statusPotencyBonus, reactionPowerBonus, weaponKey, keystoneMask, boonRules,
            )
            folded.fold()
            return CompiledBuild(
                tagPointValues = folded.tagPoints,
                affinityValues = folded.affinity,
                dominant = folded.dominant,
                secondary = folded.secondary,
                clauseLists = Array(folded.clauseBuffers.size) { folded.clauseBuffers[it].toIntArray() },
                itemMagnitudes = folded.magnitudes,
                itemBonuses = folded.bonuses,
                potencyValues = folded.potency,
                durationValues = folded.duration,
                thresholdCuts = folded.cuts,
                breakPowerValues = folded.breakPower,
                breakSpreadStacks = folded.spreadStacks,
                breakSpreadRadii = folded.spreadRadius,
                reactionPower = folded.reactionPower,
                reactionResidue = folded.residue,
                reactionRecoil = folded.recoil,
                reactionRecoilRadius = folded.recoilRadius,
                alwaysCrit = folded.alwaysCrit.toImmutableList(),
                critDamageVersus = folded.critVersus.toImmutableList(),
                damageVersus = folded.damageVersus.toImmutableList(),
                nonCriticalDamage = folded.nonCrit,
                comboDamagePer = folded.comboPer,
                comboDamageCap = folded.comboCap,
                offerCards = folded.offerCards,
                overhealOverfill = folded.overfill,
                regenerationToDecay = folded.regenDecay,
                critConversionRate = folded.critConversion,
                whileModifiers = folded.whileModifiers.toImmutableList(),
                guaranteedCritAfter = folded.critAfter,
                freeDashHeat = folded.freeDashHeat,
                freeDashCooldown = folded.freeDashCooldown,
                relicUpsides = folded.upsides,
                upsideMask = folded.upsideMask,
                liabilityMask = folded.liabilityMask,
                pairingMask = folded.pairingMask,
                links = folded.links.toImmutableList(),
                reachableReactionMask = folded.reachable,
                ownedItemCount = folded.order.size,
                keystoneMask = folded.keystones and ((1 shl ReactionId.entries.size) - 1),
                keyHarmonies = arrayOfNulls<RelicId>(kinetickk.ball.content.api.RelicKey.entries.size).also { harmonies ->
                    for (relic in equipped) for (key in content.relic(relic.id).keys) {
                        if (harmonies[key.ordinal] == null) harmonies[key.ordinal] = relic.id
                    }
                },
                skillPowerBonuses = folded.skillPowerBonuses,
                skillCooldownScales = folded.cooldownScales,
                catalysisRate = 1f + minOf(MAX_CATALYSIS_BONUS, folded.catalysis),
                apexCooldownCut = minOf(MAX_APEX_COOLDOWN_CUT, folded.apexCut),
                reactionTunings = folded.tunings,
                fieldRadiusScale = 1f + minOf(MAX_FIELD_TUNING, folded.fieldRadius),
                fieldDurationScale = 1f + minOf(MAX_FIELD_TUNING, folded.fieldDuration),
                skillCharges = folded.skillCharges,
                skillAttunement = folded.attunement,
                coreLinkBonus = minOf(MAX_CORE_LINK_BONUS, folded.linkBonus),
                dashHeatToSkills = folded.dashHeatToSkills,
                dataToSkills = folded.dataToSkills,
                skillsCostHeat = folded.skillsCostHeat,
                skillFieldsFollow = folded.skillFieldsFollow,
                knockbackToDamage = folded.knockbackToDamage,
                obstacleRestitutionBonus = minOf(MAX_OBSTACLE_RESTITUTION_BONUS, folded.obstacleRestitution),
                obstacleDamageScale = 1f - minOf(MAX_OBSTACLE_DAMAGE_CUT, folded.obstacleDamageCut),
                obstacleLethalBonus = minOf(MAX_ITEM_LETHAL_BONUS, folded.obstacleLethal),
                boostPowerScale = 1f + minOf(MAX_BOOST_POWER_BONUS, folded.boostPower),
                wellPullScale = 1f + minOf(MAX_WELL_PULL_BONUS, folded.wellPull),
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
/** ObstacleTuning caps: summed restitution bonus, damage cut, item lethal-speed bonus, boost and knot pull bonuses. */
internal const val MAX_OBSTACLE_RESTITUTION_BONUS: Float = 0.3f
internal const val MAX_OBSTACLE_DAMAGE_CUT: Float = 0.5f
internal const val MAX_ITEM_LETHAL_BONUS: Float = 400f
internal const val MAX_BOOST_POWER_BONUS: Float = 1f
internal const val MAX_WELL_PULL_BONUS: Float = 1f
/** CoreLinkRadius: at most this much farther. */
internal const val MAX_CORE_LINK_BONUS: Float = 240f
