// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.text.TextMeasurer as ComposeTextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.gameplay.interaction.fx.FxFlag
import kinetickk.ball.gameplay.interaction.fx.InteractionFxLimits
import kinetickk.ball.gameplay.interaction.fx.ReactionFxSpecs
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.interaction.fx.WeaponArcProjection
import kinetickk.ball.gameplay.nucleus.protocol.LinkStyleCode
import kinetickk.ball.gameplay.nucleus.render.CombatFieldProjection
import kinetickk.ball.gameplay.nucleus.render.CombatFieldSource
import kinetickk.ball.gameplay.nucleus.render.CombatFieldVisual
import kinetickk.ball.gameplay.nucleus.render.EnemyControlBits
import kinetickk.ball.gameplay.nucleus.render.EnemyProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.profile.api.ReactionEffectsLevel
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.KkRolePalette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The world FX draw budget (hud-fx §4.11): a saturated frame never draws a non-signature layer past
 * its pool, records at most the pools plus the signatures' own operations, and draws every
 * signature layer.
 */
class FxBudgetTest {
    private val roles = KkRolePalette.Default

    /** The reaction whose full look costs the most operations, and its signature layers at [PROGRESS]. */
    private val heaviest = ReactionId.entries.maxBy { reaction -> ReactionFxSpecs[reaction].layers.filter(::plainForm).sumOf { it.ops(it.count) } }
    private val signatureLayers = ReactionFxSpecs[heaviest].layers.count { plainForm(it) && it.has(FxFlag.SIG) && PROGRESS in it.t0..it.t1 }
    private val signatureOps = ReactionFxSpecs[heaviest].layers.filter { plainForm(it) && it.has(FxFlag.SIG) }.sumOf { it.ops(it.count) }

    @Test
    fun aSaturatedTransientPoolSkipsSecondaryLayersAndDrawsEverySignature() {
        val bursts = List(InteractionFxLimits.MAX_ELEMENT_BURSTS) { index ->
            FxTestScenes.reaction(heaviest, 100f + (index % 6) * 150f, 100f + (index / 6) * 150f, 100f, PROGRESS, seed = index, weight = 4)
        }
        val model = FxTestScenes.model(1_000, 700, -5_000f, -5_000f)
        // Twice the heaviest frame the reducer admits in one budget frame: the pool runs out half way.
        FxTestScenes.render(1_000, 700) {
            drawElementBursts(model, FxTestScenes.fx(bursts), 0f, 0f, roles)
            drawElementBursts(model, FxTestScenes.fx(bursts), 0f, 0f, roles)
        }
        val pool = FxPool.TRANSIENT
        assertTrue(FxDrawBudget.skipped(pool) > 0, "the pool ran out (${FxDrawBudget.used(pool)} operations)")
        assertTrue(FxDrawBudget.used(pool) - FxDrawBudget.overflow(pool) <= pool.ops, "secondary layers stay within ${pool.ops}")
        assertTrue(FxDrawBudget.used(pool) <= pool.ops + 2 * bursts.size * signatureOps,
            "at most the pool plus the signatures: ${FxDrawBudget.used(pool)}")
        assertTrue(signatureOps <= 14)
        assertEquals(2 * bursts.size * signatureLayers, FxDrawBudget.signatures(pool), "every signature layer draws")
    }

    @Test
    fun aSaturatedWorldFrameStaysWithinEveryPoolPlusItsSignatures() {
        val width = 1_440
        val height = 900
        val bursts = List(InteractionFxLimits.MAX_ELEMENT_BURSTS) { index ->
            FxTestScenes.reaction(heaviest, 120f + (index % 8) * 160f, 150f + (index / 8) * 250f, 100f, PROGRESS, seed = index, weight = 4)
        }
        val links = List(InteractionFxLimits.MAX_WEAPON_ARCS) { index ->
            val x = 40f + (index % 16) * 85f
            val y = 60f + (index / 16) * 100f
            WeaponArcProjection(x, y, x + 70f, y + 40f, 0.2f, LinkStyleCode.reaction(ReactionId.FEEDBACK), 0.22f)
        }
        val fields = CombatFieldVisual.entries.mapIndexed { index, kind ->
            val x = 150f + (index % 6) * 220f
            val y = 250f + (index / 6) * 400f
            CombatFieldProjection(kind, CombatFieldSource.SKILL, x, y, x + 60f, y, radius = 90f, createdAt = 8f, expiresAt = 12f, charge01 = 0.5f)
        }
        val everyGlyph = EnemyControlBits.STOPPED or EnemyControlBits.SILENCED or EnemyControlBits.THROWN or EnemyControlBits.BLACKFIRE or
            EnemyControlBits.UNMAKE_PENDING or (2 shl EnemyControlBits.MARK_SHIFT) or EnemyControlBits.VOID_ANCHOR or
            EnemyControlBits.ATTRACTOR_ANCHOR or EnemyControlBits.APEX_STRIKE_TARGET
        val enemies = List(ENEMIES) { index ->
            val x = 40f + (index % 15) * 92f
            val y = 40f + (index / 15) * 105f
            EnemyProjection(index + 1, EnemyType.DRIFTER, x, y, 30f, 10f, 50f, 100f, 14f, 0f, 0f, 0f, 0f, x, y, false, controlBits = everyGlyph)
        }
        val model = FxTestScenes.model(width, height, width * 0.5f, height * 0.5f, "enemies" to enemies.toImmutableList(),
            "combatFields" to fields.toImmutableList())
        val fx = VisualFxProjection.EMPTY.copy(elementBursts = bursts.toImmutableList(), weaponArcs = links.toImmutableList())
        val measurer = CanvasTextMeasurer(ComposeTextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr), 1.25f,
            AppLanguage.English, roles = roles)
        FxTestScenes.render(width, height) { drawWorld(model, fx, 0f, 0f, measurer) }
        assertTrue(FxFrame.crowd > FxFrame.CROWD_SIG_ONLY, "a packed screen (${FxFrame.crowd} enemies)")
        for (pool in FxPool.entries) {
            assertTrue(FxDrawBudget.used(pool) - FxDrawBudget.overflow(pool) <= pool.ops, "$pool non-signature operations stay within ${pool.ops}")
        }
        assertTrue(FxDrawBudget.used(FxPool.TRANSIENT) <= FxPool.TRANSIENT.ops + bursts.size * 14)
        // Over the link pool each styled link draws as one line (a signature of one operation); every
        // anchored enemy's Void Anchor tether (three operations) always draws.
        assertTrue(FxDrawBudget.skipped(FxPool.LINKS) > 0, "the links ran out of operations")
        assertTrue(FxDrawBudget.used(FxPool.LINKS) <= FxPool.LINKS.ops + links.size + 3 * ENEMIES)
        // On a packed screen the Void Anchor rings and Apex strike brackets of every enemy still draw.
        assertEquals(2 * ENEMIES, FxDrawBudget.signatures(FxPool.GLYPHS))
        // Every burst keeps its signature layers on a packed screen (and nothing else).
        assertEquals(bursts.size * signatureLayers, FxDrawBudget.signatures(FxPool.TRANSIENT))
    }

    @Test
    fun theMinimalSettingDrawsLinksAsOneLineAndKeepsFieldRimsAndGlyphs() {
        val links = List(10) { index -> WeaponArcProjection(20f, 20f + index * 30f, 300f, 40f + index * 30f, 0.2f, LinkStyleCode.reaction(ReactionId.FEEDBACK), 0.22f) }
        val fields = listOf(CombatFieldProjection(CombatFieldVisual.HUSH, CombatFieldSource.REACTION, 500f, 200f, radius = 80f, createdAt = 9f, expiresAt = 12f))
        val enemy = EnemyProjection(1, EnemyType.DRIFTER, 700f, 200f, 0f, 0f, 50f, 100f, 14f, 0f, 0f, 0f, 0f, 700f, 200f, false,
            controlBits = EnemyControlBits.STOPPED or EnemyControlBits.APEX_STRIKE_TARGET)
        val model = FxTestScenes.model(800, 400, 400f, 350f, "combatFields" to fields.toImmutableList(), "enemies" to listOf(enemy).toImmutableList())
        fun frame(level: ReactionEffectsLevel): Triple<Int, Int, Int> {
            FxTestScenes.render(800, 400, level) {
                drawWeaponArcs(model, VisualFxProjection.EMPTY.copy(weaponArcs = links.toImmutableList()), 0f, 0f, roles)
                drawCombatFields(model, 0f, 0f, roles)
                drawEnemy(model, enemy, 0f, 0f, roles)
            }
            return Triple(FxDrawBudget.used(FxPool.LINKS), FxDrawBudget.signatures(FxPool.FIELDS), FxDrawBudget.used(FxPool.GLYPHS))
        }
        val (fullLinks, fullRims, fullGlyphs) = frame(ReactionEffectsLevel.FULL)
        val (minimalLinks, minimalRims, minimalGlyphs) = frame(ReactionEffectsLevel.MINIMAL)
        assertEquals(links.size, minimalLinks, "one line per link")
        assertTrue(fullLinks > minimalLinks)
        assertEquals(fullRims, minimalRims, "field rims never change")
        assertEquals(fullGlyphs, minimalGlyphs, "control glyphs never change")
    }

    private fun plainForm(layer: kinetickk.ball.gameplay.interaction.fx.FxLayer): Boolean =
        layer.flags and (FxFlag.KEY or FxFlag.VERB) == 0

    private companion object {
        const val PROGRESS = 0.2f
        const val ENEMIES = 120
    }
}
