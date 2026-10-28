// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.ball.content.api.EnemyRank
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.render.EnemyAffinity
import kinetickk.ball.gameplay.nucleus.render.EnemyProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.ObstacleProjection
import kinetickk.ball.gameplay.nucleus.render.PickupProjection
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The HUD reads the content rank: it tracks the Architect over a secret boss over a mini-boss over an Elite, names the
 * tracked boss from the content in either language, gives a boss with an encounter spec one bar part per phase (a
 * spec-less Elite or Herald keeps its one segmented meter), rings a secret boss's minimap pin in bone and keeps the
 * world clear of the boss block for every rank above ordinary.
 */
class EnemyRankPresentationTest {
    @Test
    fun theHudTracksTheArchitectThenASecretBossThenAMiniBossThenAnEliteTheFirstOfItsRank() {
        val base = hudTestModel()
        fun tracked(vararg enemies: EnemyProjection): Pair<Int, EnemyRank?> {
            val boss = BossTarget().select(base.with("enemies" to enemies.toList().toImmutableList()))
            return boss.id to boss.rank
        }
        val drifter = enemy(1, EnemyType.DRIFTER)
        val comet = enemy(2, EnemyType.COMET)
        val elite = enemy(3, EnemyType.ELITE)
        val herald = enemy(4, EnemyType.HERALD)
        val throne = enemy(5, EnemyType.THRONE)
        val cartographer = enemy(6, EnemyType.CARTOGRAPHER)
        val antecedent = enemy(7, EnemyType.ANTECEDENT)
        val architect = enemy(8, EnemyType.ARCHITECT)
        assertEquals(8 to EnemyRank.BOSS, tracked(drifter, elite, throne, cartographer, antecedent, architect))
        assertEquals(6 to EnemyRank.SECRET_BOSS, tracked(drifter, elite, herald, throne, cartographer, antecedent))
        assertEquals(7 to EnemyRank.SECRET_BOSS, tracked(antecedent, throne, cartographer), "the first secret boss stays tracked")
        assertEquals(5 to EnemyRank.MINI_BOSS, tracked(elite, throne, herald, drifter))
        assertEquals(4 to EnemyRank.MINI_BOSS, tracked(herald, throne), "the Herald is a mini-boss like the others")
        assertEquals(3 to EnemyRank.ELITE, tracked(drifter, comet, elite))
        assertEquals(-1 to null, tracked(drifter, comet), "ordinary kinds are never tracked")
        // A dead or emptied boss gives way to the next rank.
        assertEquals(5 to EnemyRank.MINI_BOSS, tracked(cartographer.copy(dead = true), antecedent.copy(hp = 0f), throne))
        // The feed docks under the block of whichever boss is tracked: the wide block only for the Architect and a secret boss.
        assertEquals(listOf(true, true, false, false, false, false),
            listOf(EnemyRank.BOSS, EnemyRank.SECRET_BOSS, EnemyRank.MINI_BOSS, EnemyRank.ELITE, EnemyRank.ORDINARY, null).map(::wideBossBlock))
    }

    @Test
    fun theBarNamesTheTrackedBossFromTheContentInBothLanguages() {
        val expected = mapOf(
            EnemyType.THRONE to ("THRONE" to "ПРЕСТОЛ"),
            EnemyType.PYRE_HERALD to ("PYRE HERALD" to "ОГНЕННЫЙ ГЛАШАТАЙ"),
            EnemyType.WARDEN_PRIME to ("WARDEN PRIME" to "ВЕРХОВНЫЙ СТРАЖ"),
            EnemyType.CARTOGRAPHER to ("CARTOGRAPHER" to "КАРТОГРАФ"),
            EnemyType.ANTECEDENT to ("ANTECEDENT" to "ПРЕДТЕЧА"),
            EnemyType.HERALD to ("HERALD" to "ГЛАШАТАЙ"),
            EnemyType.ELITE to ("ELITE" to "ЭЛИТНЫЙ ВРАГ"),
        )
        for ((width, height) in listOf(1_440 to 810, 390 to 844)) for ((type, names) in expected) {
            for (language in AppLanguage.entries) {
                val model = hudTestModel(width.toFloat(), height.toFloat()).with("enemies" to immutableListOf(enemy(4, type)))
                render(model, width, height, language = language)
                val layout = assertNotNull(HudDrawCache.peekLayout(HudText.BOSS_NAME), "$type")
                val name = if (language == AppLanguage.English) names.first else names.second
                assertEquals(name, layout.layoutInput.text.text.uppercase(), "$width x $height $type in $language")
                val block = assertNotNull(HudLayoutProbe.rect(HudBlock.BOSS))
                assertTrue(block.left >= 0f && block.right <= width, "$width x $height $type in $language: the name fits ($block)")
            }
        }
    }

    @Test
    fun aBossWithASpecShowsOnePartPerPhaseAndASpecLessRankKeepsItsOneMeter() {
        val (width, height) = 1_440 to 810
        val base = hudTestModel(width.toFloat(), height.toFloat())
        // Content: the Architect 3 (4 at the Singularity tier), every mini-boss 3, the Cartographer 3, the Antecedent 4.
        assertEquals(3, bossBarParts(base.content, 0, EnemyType.ARCHITECT))
        assertEquals(4, bossBarParts(base.content, 10, EnemyType.ARCHITECT))
        listOf(EnemyType.WARDEN_PRIME, EnemyType.BROOD_QUEEN, EnemyType.THRONE, EnemyType.PYRE_HERALD, EnemyType.CARTOGRAPHER)
            .forEach { assertEquals(3, bossBarParts(base.content, 10, it), "$it") }
        assertEquals(4, bossBarParts(base.content, 0, EnemyType.ANTECEDENT))
        assertEquals(0, bossBarParts(base.content, 0, EnemyType.HERALD))
        assertEquals(0, bossBarParts(base.content, 0, EnemyType.ELITE))

        fun drawnParts(type: EnemyType, rebirth: Int = 0): Int {
            render(base.with("rebirthLevel" to rebirth, "enemies" to immutableListOf(enemy(4, type, phase = 1))), width, height)
            val first = assertNotNull(HudDrawCache.peekRect(HudRect.BOSS_0))
            val center = width * 0.5f
            val barWidth = (center - first.left) * 2f
            if (abs(first.width - barWidth) < 0.5f) return 1
            // Equal parts with equal gaps across the bar, the first one at its left end.
            val gap = assertNotNull(HudDrawCache.peekRect(HudRect.BOSS_1)).left - first.right
            return ((barWidth + gap) / (first.width + gap)).roundToInt()
        }
        assertEquals(3, drawnParts(EnemyType.THRONE))
        assertEquals(4, drawnParts(EnemyType.ANTECEDENT))
        assertEquals(1, drawnParts(EnemyType.HERALD), "the Herald keeps its segmented meter")
        assertEquals(3, drawnParts(EnemyType.CARTOGRAPHER, rebirth = 10), "a secret boss has no Singularity phase")
        assertEquals(4, drawnParts(EnemyType.ARCHITECT, rebirth = 10))
        assertEquals(1, drawnParts(EnemyType.ELITE))
        assertEquals(3, drawnParts(EnemyType.PYRE_HERALD))
    }

    @Test
    fun aSecretBossShowsTheAnomalyGlyphUntilItHasAWeaknessAndAMiniBossTheEliteGlyph() {
        val (width, height) = 1_440 to 810
        val roles = KkRolePalette.Default
        val base = hudTestModel(width.toFloat(), height.toFloat())
        fun nameRow(type: EnemyType, affinity: Int): Pair<PixelMap, Rect> {
            val map = render(base.with("enemies" to immutableListOf(enemy(4, type, affinity = affinity))), width, height, roles = roles)
            val bar = assertNotNull(HudDrawCache.peekRect(HudRect.BOSS_0))
            val block = assertNotNull(HudLayoutProbe.rect(HudBlock.BOSS))
            return map to Rect(block.left, block.top, block.right, bar.top)
        }
        val plain = EnemyAffinity.encode(EnemyAffinity.NONE, false, EnemyAffinity.NONE, armored = true, fragile = false, shifting = false, phase = 0)
        val thermal = EnemyAffinity.encode(StatusElement.THERMAL.ordinal, false, StatusElement.CRYO.ordinal, armored = true, fragile = false,
            shifting = false, phase = 1)
        val cryo = roles.element(StatusElement.CRYO.ordinal)
        val (anomaly, anomalyRow) = nameRow(EnemyType.CARTOGRAPHER, plain)
        assertTrue(near(anomaly, anomalyRow, roles.threat) > 10, "a secret boss without a weakness shows its glyph in threat")
        val (weak, weakRow) = nameRow(EnemyType.ANTECEDENT, thermal)
        assertTrue(near(weak, weakRow, cryo) > 10, "a hardened secret boss shows its weakness")
        assertEquals(0, near(weak, weakRow, roles.threat), "the weakness replaces the anomaly glyph")
        val (mini, miniRow) = nameRow(EnemyType.THRONE, thermal)
        assertTrue(near(mini, miniRow, roles.threat) > 10, "a mini-boss shows the Elite glyph")
        assertEquals(0, near(mini, miniRow, cryo), "a mini-boss's narrow block shows no weakness")
        val (architect, architectRow) = nameRow(EnemyType.ARCHITECT, plain)
        assertEquals(0, near(architect, architectRow, roles.threat), "the Architect without a weakness leaves the slot empty")
    }

    @Test
    fun aSecretBossPinCarriesABoneRingThatAMiniBossPinDoesNot() {
        val (width, height) = 1_440 to 810
        val start = hudTestModel(width.toFloat(), height.toFloat())
        val base = start.with(
            "velocityX" to 0f, "velocityY" to 0f, "obstacles" to immutableListOf<ObstacleProjection>(), "totem" to null,
            "pickups" to immutableListOf<PickupProjection>(),
        )
        val dx = base.minimapRange * 0.5f
        fun at(type: EnemyType?) = base.with("enemies" to (if (type == null) immutableListOf() else
            immutableListOf(enemy(4, type, x = base.coreX + dx, y = base.coreY))))
        val none = render(at(null), width, height)
        val minimap = assertNotNull(HudLayoutProbe.rect(HudBlock.MINIMAP))
        val dot = HudScratch.frame.u(1.6f)
        val pin = Offset(minimap.center.x + minimap.width * 0.25f, minimap.center.y)
        val mini = render(at(EnemyType.THRONE), width, height)
        val secret = render(at(EnemyType.CARTOGRAPHER), width, height)
        val ordinary = render(at(EnemyType.DRIFTER), width, height)
        // The ring lies between the pin (3 dots) and 5 dots out; the pin itself is the same big pin.
        assertTrue(differing(secret, mini, pin, dot * 3.6f, dot * 5.2f) >= 12, "a bone ring around the secret boss's pin")
        assertEquals(0, differing(mini, none, pin, dot * 3.6f, dot * 5.2f), "a mini-boss's pin has no ring")
        assertEquals(0, differing(secret, mini, pin, 0f, dot * 2.4f), "both carry the same big pin")
        assertTrue(differing(mini, none, pin, 0f, dot * 2.4f) >= 12, "the mini-boss is pinned")
        assertTrue(differing(mini, ordinary, pin, dot * 1.2f, dot * 2.6f) >= 6, "an ordinary kind is only a dot")
        assertTrue(pixelNear(secret, pin.x, pin.y - dot * SECRET_RING_DOTS, Kk.Bone), "the ring is bone")
    }

    @Test
    fun theWorldKeepsClearOfTheBossBlockForEveryRankAboveOrdinary() {
        val base = hudTestModel()
        fun shown(vararg enemies: EnemyProjection) = bossBlockShown(base.with("enemies" to enemies.toList().toImmutableList()))
        listOf(EnemyType.ELITE, EnemyType.HERALD, EnemyType.WARDEN_PRIME, EnemyType.THRONE, EnemyType.CARTOGRAPHER,
            EnemyType.ANTECEDENT, EnemyType.ARCHITECT).forEach { type ->
            assertTrue(shown(enemy(1, EnemyType.DRIFTER), enemy(2, type)), "$type")
        }
        assertFalse(shown(enemy(1, EnemyType.DRIFTER), enemy(2, EnemyType.COMET), enemy(3, EnemyType.SERAPH)), "ordinary kinds only")
        assertFalse(shown(enemy(1, EnemyType.ANTECEDENT).copy(dead = true)), "a dead boss")
        // The keep-out a frame uses covers the block as drawn, narrow (mini-boss) or wide (secret boss), at every text size.
        for ((width, height) in listOf(1_440 to 810, 844 to 390, 390 to 844)) for (textScale in listOf(1f, 1.75f)) {
            for (type in listOf(EnemyType.THRONE, EnemyType.ANTECEDENT)) {
                val model = hudTestModel(width.toFloat(), height.toFloat()).with("enemies" to immutableListOf(enemy(4, type, phase = 2)))
                val where = "$width x $height x$textScale $type"
                val measurer = measurer(AppLanguage.Russian, KkRolePalette.Default, textScale)
                var keepOut: List<Rect> = emptyList()
                CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(ImageBitmap(64, 64)), Size(width.toFloat(), height.toFloat())) {
                    drawHud(model, measurer, 1f)
                    keepOut = worldHudKeepOut(model, measurer).rects()
                }
                val block = assertNotNull(HudLayoutProbe.rect(HudBlock.BOSS), where)
                assertTrue(keepOut.any { it.contains(block.topLeft) && it.contains(Offset(block.right - 0.01f, block.bottom - 0.01f)) },
                    "$where: the keep-out misses the boss block $block")
            }
        }
    }

    private fun enemy(
        id: Int,
        type: EnemyType,
        phase: Int = 0,
        affinity: Int = EnemyAffinity.encode(EnemyAffinity.NONE, false, EnemyAffinity.NONE, armored = false, fragile = false,
            shifting = false, phase = phase),
        x: Float = 200f,
        y: Float = 0f,
    ) = EnemyProjection(id, type, x, y, 0f, 0f, 600f, 1_000f, 40f, 0f, 0f, 0f, 0f, x, y, false, affinityBits = affinity)

    private fun measurer(language: AppLanguage, roles: KkRolePalette, textScale: Float = 1.25f) =
        CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr), textScale, language,
            HudTestFonts.typography, roles)

    private fun render(
        model: GameplayRenderModel,
        width: Int,
        height: Int,
        language: AppLanguage = AppLanguage.English,
        roles: KkRolePalette = KkRolePalette.Default,
    ): PixelMap {
        val measured = model.with("settings" to PlayerPreferences(language = language))
        val bitmap = ImageBitmap(width, height)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
            drawRect(Kk.Ink)
            drawHud(measured, measurer(language, roles), 1f)
        }
        return bitmap.toPixelMap()
    }

    /** Pixels in the ring between [inner] and [outer] around [center] that differ between [a] and [b]. */
    private fun differing(a: PixelMap, b: PixelMap, center: Offset, inner: Float, outer: Float): Int {
        var count = 0
        val reach = outer.toInt() + 1
        for (y in center.y.toInt() - reach..center.y.toInt() + reach) for (x in center.x.toInt() - reach..center.x.toInt() + reach) {
            val distance = hypot(x + 0.5f - center.x, y + 0.5f - center.y)
            if (distance < inner || distance > outer) continue
            val p = a[x, y]
            val q = b[x, y]
            if (abs(p.red - q.red) + abs(p.green - q.green) + abs(p.blue - q.blue) > 0.12f) count++
        }
        return count
    }

    /** Pixels in [rect] within a small distance of [color]. */
    private fun near(map: PixelMap, rect: Rect, color: Color): Int {
        var count = 0
        for (y in rect.top.toInt().coerceAtLeast(0) until rect.bottom.toInt().coerceAtMost(map.height)) {
            for (x in rect.left.toInt().coerceAtLeast(0) until rect.right.toInt().coerceAtMost(map.width)) {
                val p = map[x, y]
                if (abs(p.red - color.red) + abs(p.green - color.green) + abs(p.blue - color.blue) < 0.12f) count++
            }
        }
        return count
    }

    /** Whether a pixel within 1 px of ([x], [y]) lies more than half way from the ink toward [color]. */
    private fun pixelNear(map: PixelMap, x: Float, y: Float, color: Color): Boolean {
        for (dy in -1..1) for (dx in -1..1) {
            val p = map[(x + dx).toInt(), (y + dy).toInt()]
            val base = Kk.Ink
            val ex = color.red - base.red
            val ey = color.green - base.green
            val ez = color.blue - base.blue
            val t = ((p.red - base.red) * ex + (p.green - base.green) * ey + (p.blue - base.blue) * ez) / (ex * ex + ey * ey + ez * ez)
            if (t > 0.35f) return true
        }
        return false
    }
}
