// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

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
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.gameplay.interaction.input.RunningControlPressLog
import kinetickk.ball.gameplay.interaction.layout.RunningControlTarget
import kinetickk.ball.gameplay.nucleus.render.ApexProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.SkillBarProjection
import kinetickk.ball.gameplay.nucleus.render.SkillSlotProjection
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The skill row's and the Apex control's feedback as drawn by the running HUD (1440 × 810). */
class HudSkillBarRenderingTest {
    private val roles = KkRolePalette.Default

    private fun base(bar: SkillBarProjection, apex: ApexProjection = ApexProjection(unlocked = true, ready = true)) =
        hudTestModel().with(*levelOverrides(HUD_TEST_LEVEL_CAP, apex), "skillBar" to bar, "elapsed" to 50f)

    private fun slot(id: SkillId, ready: Boolean = true, rank: Int = 1, refusal: Int = 0, refusedAt: Float = -1f) =
        SkillSlotProjection(skill = id.ordinal, rank = rank, ready = ready, cooldown01 = if (ready) 0f else 0.5f, refusal = refusal,
            refusedAt = refusedAt)

    private fun bar(first: SkillSlotProjection, second: SkillSlotProjection = SkillSlotProjection.EMPTY) =
        SkillBarProjection(immutableListOf(first, second, SkillSlotProjection.EMPTY))

    private fun render(model: GameplayRenderModel, time: Float, memory: HudPresentationMemory? = null, controls: RunningControlPressLog? = null): PixelMap {
        val measurer = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr), 1.25f,
            AppLanguage.English, HudTestFonts.typography, roles)
        val bitmap = ImageBitmap(1_440, 810)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(1_440f, 810f)) {
            drawRect(Kk.Ink)
            drawHud(model, measurer, time, memory = memory, controls = controls)
        }
        return bitmap.toPixelMap()
    }

    private fun slotRect(index: Int): Rect = assertNotNull(HudDrawCache.peekRect(listOf(HudRect.SKILL_0, HudRect.SKILL_1, HudRect.SKILL_2)[index]))

    private fun near(map: PixelMap, rect: Rect, color: Color, tolerance: Float = 0.12f): Int {
        var count = 0
        for (y in rect.top.toInt().coerceAtLeast(0) until rect.bottom.toInt().coerceAtMost(map.height)) {
            for (x in rect.left.toInt().coerceAtLeast(0) until rect.right.toInt().coerceAtMost(map.width)) {
                val p = map[x, y]
                if (abs(p.red - color.red) + abs(p.green - color.green) + abs(p.blue - color.blue) < tolerance) count++
            }
        }
        return count
    }

    private fun differing(a: PixelMap, b: PixelMap, rect: Rect): Int {
        var count = 0
        for (y in rect.top.toInt().coerceAtLeast(0) until rect.bottom.toInt().coerceAtMost(a.height)) {
            for (x in rect.left.toInt().coerceAtLeast(0) until rect.right.toInt().coerceAtMost(a.width)) {
                val p = a[x, y]
                val q = b[x, y]
                if (abs(p.red - q.red) + abs(p.green - q.green) + abs(p.blue - q.blue) > 0.2f) count++
            }
        }
        return count
    }

    private fun Rect.grown(by: Float) = Rect(left - by, top - by, right + by, bottom + by)

    @Test
    fun theFirstDraftSlamsTheSlotAndSlidesTheRowIn() {
        val empty = base(SkillBarProjection.EMPTY)
        val drafted = base(bar(slot(SkillId.STORM_COIL)))
        val memory = HudPresentationMemory()
        render(empty, 0.9f, memory)
        assertNull(HudLayoutProbe.rect(HudBlock.SKILLS), "a row before any draft")
        render(drafted, 1f, memory) // the draft's edge
        val entering = render(drafted, 1.01f, memory)
        val enteringRow = assertNotNull(HudLayoutProbe.rect(HudBlock.SKILLS))
        val settled = render(drafted, 3f, memory)
        val settledRow = assertNotNull(HudLayoutProbe.rect(HudBlock.SKILLS))
        // 10 ms into the 220 ms entrance the row is still right of its place and differs from the settled frame.
        assertTrue(enteringRow.left > settledRow.left + 2f, "row at ${enteringRow.left} vs ${settledRow.left}")
        assertTrue(differing(entering, settled, slotRect(0).grown(40f)) > 400, "no slam or ring on the draft")
    }

    @Test
    fun aSlotTurningReadyFlashesBoneAndRingsThenSettles() {
        val cooling = base(bar(slot(SkillId.FROST_SNAP, ready = false)))
        val ready = base(bar(slot(SkillId.FROST_SNAP, ready = true)))
        val memory = HudPresentationMemory()
        render(cooling, 0.9f, memory)
        render(ready, 1f, memory) // the ready edge
        val flash = render(ready, 1.03f, memory)
        val face = slotRect(0)
        val ringing = render(ready, 1.06f, memory)
        val later = render(ready, 1.15f, memory)
        val settled = render(ready, 3f, memory)
        assertTrue(near(flash, face, Kk.Bone) > 1_000, "no Bone face on the ready edge (${near(flash, face, Kk.Bone)})")
        assertTrue(near(later, face, Kk.Bone) < 200, "the Bone face outlived its 90 ms")
        // The `you` ring grows out past the slot's glow (60 ms in, before it fades).
        val ring = differing(ringing, settled, face.grown(50f)) - differing(ringing, settled, face)
        assertTrue(ring > 30, "no ready ring outside the slot ($ring px)")
    }

    @Test
    fun aPressFlashesTheFaceForNinetyMilliseconds() {
        val model = base(bar(slot(SkillId.METEOR, ready = false)))
        val controls = RunningControlPressLog()
        controls.press(RunningControlTarget.SKILL_1, 2f)
        val pressed = render(model, 2.05f, controls = controls)
        val face = slotRect(0)
        val after = render(model, 2.2f, controls = controls)
        // The face turns Bone (the cooling sweep darkens half of it) and returns after 90 ms.
        assertTrue(near(pressed, face, Kk.Bone) > near(after, face, Kk.Bone) + 500,
            "press ${near(pressed, face, Kk.Bone)} vs after ${near(after, face, Kk.Bone)}")
    }

    @Test
    fun aRefusalShakesTheSlotAndANoTargetRefusalShowsTheReachAroundTheCore() {
        val calm = render(base(bar(slot(SkillId.ARC_HOOK))), 2f)
        val face = slotRect(0)
        // Cooling refusal 60 ms ago (the shake's widest part): the slot moved.
        val shaking = render(base(bar(slot(SkillId.ARC_HOOK, refusal = SkillSlotProjection.REFUSAL_COOLING, refusedAt = 49.94f))), 2f)
        assertTrue(differing(calm, shaking, face.grown(6f)) > 100, "the refused slot did not shake")
        // Long past: nothing left.
        val old = render(base(bar(slot(SkillId.ARC_HOOK, refusal = SkillSlotProjection.REFUSAL_COOLING, refusedAt = 49f))), 2f)
        assertEquals(0, differing(calm, old, face.grown(6f)))
        // No target: a Bone ring of the skill's reach around the Core (the Core is at the screen centre).
        val model = base(bar(slot(SkillId.ARC_HOOK, refusal = SkillSlotProjection.REFUSAL_NO_TARGET, refusedAt = 49.9f)))
        val ringed = render(model, 2f)
        val reach = model.content.skill(SkillId.ARC_HOOK).reach * model.worldScale
        val around = Rect(720f - reach - 6f, 405f - reach - 6f, 720f + reach + 6f, 405f + reach + 6f)
        assertTrue(differing(calm, ringed, around) > 100, "no reach ring after a no-target refusal")
    }

    @Test
    fun keyCapsShowOnlyWhileTheKeyboardIsInUseAndTheApexKeyOnlyAtTheCap() {
        val model = base(bar(slot(SkillId.STORM_COIL), slot(SkillId.METEOR)))
        val touch = RunningControlPressLog()
        render(model, 2f, controls = touch)
        assertNull(HudLayoutProbe.rect(HudBlock.SKILL_KEYS), "key caps without the keyboard")
        assertNull(HudLayoutProbe.rect(HudBlock.APEX_KEY))
        val dataBarLeft = assertNotNull(HudDrawCache.peekRect(HudRect.DATA_BAR)).left
        val keyboard = keyboardControls()
        render(model, 2f, controls = keyboard)
        val keys = assertNotNull(HudLayoutProbe.rect(HudBlock.SKILL_KEYS))
        // Under the drafted squares only (C is locked), 3 u below the row.
        assertTrue(keys.left >= slotRect(0).left - 1f && keys.right <= slotRect(1).right + 1f, "caps $keys")
        assertEquals(slotRect(0).bottom + 3f, keys.top, 0.5f)
        val apexKey = assertNotNull(HudLayoutProbe.rect(HudBlock.APEX_KEY))
        // The Data bar makes room for the Apex key (it starts 24 u right of the badge instead of 4 u).
        assertEquals(dataBarLeft + 20f, assertNotNull(HudDrawCache.peekRect(HudRect.DATA_BAR)).left, 0.5f)
        assertTrue(apexKey.right <= assertNotNull(HudDrawCache.peekRect(HudRect.DATA_BAR)).left)
        // Below the cap the badge is not the Apex: no Apex key.
        render(hudTestModel().with(*levelOverrides(41), "skillBar" to model.skillBar), 2f, controls = keyboard)
        assertNull(HudLayoutProbe.rect(HudBlock.APEX_KEY))
        assertNotNull(HudLayoutProbe.rect(HudBlock.SKILL_KEYS))
    }

    @Test
    fun anApexPressWhileCoolingShakesTheBadge() {
        val model = base(bar(slot(SkillId.STORM_COIL)), ApexProjection(unlocked = true, cooldown01 = 0.5f))
        val calm = render(model, 2f)
        val badge = assertNotNull(HudLayoutProbe.rect(HudBlock.BADGE))
        val controls = RunningControlPressLog()
        controls.refuseApex(1.94f)
        val shaking = render(model, 2f, controls = controls)
        assertTrue(differing(calm, shaking, badge.grown(6f)) > 200, "the cooling Apex press did not shake the badge")
        val later = render(model, 2.5f, controls = controls)
        val calmLater = render(model, 2.5f)
        assertEquals(0, differing(calmLater, later, badge.grown(6f)))
    }

    @Test
    fun theRunningApexShowsItsStrikesLeftAboveTheBossBar() {
        val architect = EnemyProjection(2, EnemyType.ARCHITECT, 0f, 0f, 0f, 0f, 600f, 1_000f, 74f, 0f, 0f, 0f, 0f, 0f, 0f, false)
        fun model(apex: ApexProjection) = base(bar(slot(SkillId.STORM_COIL)), apex).with("enemies" to listOf(architect).toImmutableList())
        val running = render(model(ApexProjection(unlocked = true, active01 = 0.5f, strikesLeft = 4)), 2f)
        val boss = assertNotNull(HudLayoutProbe.rect(HudBlock.BOSS))
        val ready = render(model(ApexProjection(unlocked = true, ready = true)), 2f)
        val strip = Rect(boss.right - 120f, boss.top, boss.right + 4f, boss.bottom)
        val gold = roles.gold.base
        assertTrue(near(running, strip, gold) > 40, "no gold strike pips (${near(running, strip, gold)})")
        assertEquals(0, near(ready, strip, gold), "strike pips while the Apex is not running")
    }
}
