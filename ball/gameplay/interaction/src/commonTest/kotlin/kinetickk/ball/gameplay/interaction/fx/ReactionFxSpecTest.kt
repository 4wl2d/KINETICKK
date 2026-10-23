// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.fx

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.protocol.LinkStyleCode
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The look tables (hud-fx §4.3–§4.5, §7.3): every reaction and skill has a look within the life,
 * count, width and operation bounds the draw budget relies on, no look can paint the threat colour,
 * and every link style Gameplay emits has a look.
 */
class ReactionFxSpecTest {
    @Test
    fun everyReactionHasALookWithinItsBounds() {
        for (reaction in ReactionId.entries) {
            val spec = ReactionFxSpecs[reaction]
            assertTrue(spec.life in 0.45f..1.1f, "$reaction lives ${spec.life} s")
            assertTrue(spec.layers.any { it.has(FxFlag.SIG) }, "$reaction has a signature layer")
            spec.layers.forEach { layer -> assertLayerBounds("$reaction", layer, rank = 1) }
            // Every combination of the cue's Core branch and the keystone form.
            for (verb in listOf(false, true)) for (keystone in listOf(false, true)) {
                val shown = spec.layers.filter { it.shows(verb, keystone) }
                val full = shown.sumOf { it.ops(it.count) }
                val signature = shown.filter { it.has(FxFlag.SIG) }.sumOf { it.ops(it.count) }
                assertTrue(full <= FULL_OPS, "$reaction (verb $verb, keystone $keystone) draws $full operations")
                assertTrue(signature in 1..REACTION_SIG_OPS, "$reaction (verb $verb, keystone $keystone) signature $signature operations")
            }
        }
        assertEquals(ReactionFxSpecs.maxLife, InteractionFxLimits.REACTION_BURST_SECONDS)
    }

    @Test
    fun discsNeverPassEightTenthsOfTheReachOrAThirdOfOpacity() {
        val looks = ReactionId.entries.map { ReactionFxSpecs[it] } +
            SkillId.entries.flatMap { skill -> (0 until SkillFxSpecs.PHASES).mapNotNull { SkillFxSpecs.of(skill, it) } } +
            ApexFxSpec.activation
        for (spec in looks) for (layer in spec.layers) {
            if (layer.primitive != FxPrimitive.DISC || layer.has(FxFlag.CORE) || layer.has(FxFlag.WORLD)) continue
            assertTrue(maxOf(layer.r0, layer.r1) <= 0.8f, "disc radius ${layer.r1}")
            assertTrue(layer.alpha <= 0.35f, "disc alpha ${layer.alpha}")
        }
    }

    @Test
    fun everySkillHasACastLookAndTheDelayedOnesAnImpact() {
        val delayed = setOf(SkillId.METEOR, SkillId.GRAVITY_WELL, SkillId.VOID_STEP, SkillId.NULL_SHROUD)
        for (skill in SkillId.entries) {
            val cast = assertNotNull(SkillFxSpecs.of(skill, 0), "$skill cast")
            val impact = SkillFxSpecs.of(skill, 1)
            if (skill in delayed) assertNotNull(impact, "$skill impact") else assertNull(impact, "$skill has no impact phase")
            for (spec in listOfNotNull(cast, impact)) {
                assertTrue(spec.life > 0f && spec.life <= 1.1f, "$skill lives ${spec.life} s")
                assertTrue(spec.layers.any { it.has(FxFlag.SIG) }, "$skill has a signature layer")
                for (rank in 1..3) {
                    spec.layers.forEach { layer -> assertLayerBounds("$skill", layer, rank) }
                    val shown = spec.layers.filter { it.flags and FxFlag.RANK3 == 0 || rank == 3 }
                    val full = shown.sumOf { it.ops(it.elements(rank)) }
                    val signature = shown.filter { it.has(FxFlag.SIG) }.sumOf { it.ops(it.elements(rank)) }
                    assertTrue(full <= FULL_OPS, "$skill rank $rank draws $full operations")
                    assertTrue(signature <= SKILL_SIG_OPS, "$skill rank $rank signature $signature operations")
                }
            }
        }
        assertNull(SkillFxSpecs.of(SkillId.entries.size, 0), "no look past the last skill")
        assertNull(SkillFxSpecs.of(SkillId.METEOR, SkillFxSpecs.PHASES), "no third phase")
        assertTrue(ApexFxSpec.activation.layers.any { it.has(FxFlag.SIG) })
    }

    @Test
    fun noLookResolvesToTheThreatColourInAnyPreset() {
        val presets = listOf(KkRolePalette.Default, KkRolePalette.Protan, KkRolePalette.Deutan, KkRolePalette.Tritan, KkRolePalette.Mono)
        val colors = (ReactionId.entries.map { ReactionFxSpecs[it] } +
            SkillId.entries.flatMap { skill -> (0 until SkillFxSpecs.PHASES).mapNotNull { SkillFxSpecs.of(skill, it) } } +
            ApexFxSpec.activation).flatMap { spec -> spec.layers.map { it.color } }.toSet()
        for (roles in presets) {
            // What each colour slot can be: an element, bone, ink, the player's, the gold or the preset's Kinetic.
            val elements = StatusElement.entries.map { roles.element(it.ordinal) }
            for (color in colors) {
                val possible = when (color) {
                    FxColor.A, FxColor.B, FxColor.AB -> elements + roles.kinetic + Kk.Bone + roles.you
                    FxColor.BONE -> listOf(Kk.Bone)
                    FxColor.INK -> listOf(Kk.Ink)
                    FxColor.YOU -> listOf(roles.you)
                    FxColor.GOLD -> listOf(roles.gold.base)
                    FxColor.GOLD_LIGHT -> listOf(roles.gold.light)
                }
                // Mono gives the player's colour (and Kinetic) the threat's white by design; a look never picks the threat role.
                val playerIsThreat = roles.you == roles.threat
                possible.filterNot { playerIsThreat && (it == roles.you || it == roles.kinetic) }
                    .forEach { assertNotEquals(roles.threat, it, "$color in a preset whose threat is ${roles.threat}") }
            }
        }
    }

    @Test
    fun everyLinkStyleGameplayEmitsHasALookOfAtMostAThirdOfASecond() {
        assertEquals(LinkStyleCode.REACTION_BASE, LinkLooks.REACTION_BASE)
        assertEquals(LinkStyleCode.SKILL_BASE, LinkLooks.SKILL_BASE)
        assertEquals(LinkStyleCode.BREAK_BASE, LinkLooks.BREAK_BASE)
        assertEquals(LinkStyleCode.APEX_BASE, LinkLooks.APEX_BASE)
        val codes = ReactionId.entries.map { LinkStyleCode.reaction(it) } + SkillId.entries.map { LinkStyleCode.skill(it) } +
            StatusElement.entries.map { LinkStyleCode.elementBreak(it) } + CoreShape.entries.map { LinkStyleCode.apex(it) }
        for (code in codes) {
            assertTrue(LinkLooks.isKnown(code), "style $code")
            val look = LinkLooks[code]
            assertNotEquals(LinkKind.WEAPON, look.kind, "style $code has its own look")
            assertTrue(look.life in 0.1f..LinkLooks.MAX_LIFE, "style $code lives ${look.life} s")
        }
        assertEquals(LinkKind.WEAPON, LinkLooks[LinkStyleCode.WEAPON].kind)
        // Codes between the families draw the weapon look instead of failing.
        assertEquals(LinkKind.WEAPON, LinkLooks[ReactionId.entries.size + 1].kind)
        assertEquals(LinkKind.WEAPON, LinkLooks[-1].kind)
        assertEquals(LinkKind.WEAPON, LinkLooks[LinkLooks.CODES].kind)
        // The named looks of §4.5.
        assertEquals(LinkKind.EMBER, LinkLooks[LinkStyleCode.reaction(ReactionId.WILDFIRE)].kind)
        assertEquals(LinkKind.BEAM, LinkLooks[LinkStyleCode.reaction(ReactionId.ANNIHILATE)].kind)
        assertEquals(LinkKind.FROST, LinkLooks[LinkStyleCode.reaction(ReactionId.GLACIER_CRUSH)].kind)
        assertEquals(LinkKind.SOUND, LinkLooks[LinkStyleCode.skill(SkillId.RESONATOR)].kind)
        assertEquals(LinkKind.TAUT, LinkLooks[LinkStyleCode.apex(CoreShape.SHARD)].kind)
        assertEquals(0.35f, LinkLooks[LinkStyleCode.reaction(ReactionId.BLACKFIRE)].life)
    }

    private fun assertLayerBounds(name: String, layer: FxLayer, rank: Int) {
        assertTrue(layer.t0 >= 0f && layer.t0 < layer.t1 && layer.t1 <= 1f, "$name ${layer.primitive} window ${layer.t0}..${layer.t1}")
        assertTrue(layer.elements(rank) in 1..MAX_COUNT, "$name ${layer.primitive} count ${layer.elements(rank)}")
        if (layer.stroked) {
            val dotted = layer.has(FxFlag.DOTTED)
            assertTrue(if (dotted) layer.width == 1.6f else FxStrokeWidths.any { it == layer.width },
                "$name ${layer.primitive} width ${layer.width}")
        }
        assertTrue(layer.alpha in 0.05f..1f, "$name ${layer.primitive} alpha ${layer.alpha}")
    }

    /** Whether a layer shows for a cue with the Core branch [verb] and the keystone form (detail aside). */
    private fun FxLayer.shows(verb: Boolean, keystone: Boolean): Boolean {
        if (has(FxFlag.KEY) && !keystone) return false
        if (has(FxFlag.BASE) && keystone) return false
        val forced = keystone && has(FxFlag.KEY_ALWAYS)
        if (has(FxFlag.VERB) && !verb && !forced) return false
        if (has(FxFlag.NOT_VERB) && verb && !forced) return false
        return true
    }

    private companion object {
        const val FULL_OPS = 40
        const val REACTION_SIG_OPS = 14
        const val SKILL_SIG_OPS = 24
        const val MAX_COUNT = 20
    }
}
