// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CoreStasisTuning
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.gameplay.nucleus.model.SkillCell
import kinetickk.ball.gameplay.nucleus.protocol.FxColorCode
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.dimensions.*
import kinetickk.ball.gameplay.nucleus.simulation.flight.*
import kinetickk.ball.gameplay.nucleus.simulation.skills.*
import kinetickk.ball.gameplay.nucleus.simulation.weapons.*
import kinetickk.ball.gameplay.nucleus.simulation.world.*
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Core Stasis detector rules, driven step by step with a scripted Core (position and velocity written before each
 * [updateCoreStasis]) against the shipped thresholds: anchor radius 240 u, still speed 220 u/s, travel floor 30 u/s,
 * speed EMA 1.5 s, decay 4x, grace 10 s, ramp 12 s.
 */
class CoreStasisSystemTest {
    @Test
    fun aStillCoreFreezesAfterTheGraceAndRampsToFullStasis() {
        val state = buildTestState()
        state.drive(30f, { _, out -> pose(out, 0f, 0f) }) { t ->
            val level = state.coreStasisLevel()
            if (t < 10f - STEP) assertEquals(0f, level, "level at $t")
            if (t > 16f - STEP && t < 16f + STEP) assertEquals(0.5f, level, STEP / 12f + 1e-4f, "level at $t")
            if (t >= 22f + 2f * STEP) assertEquals(1f, level, "level at $t")
            assertEquals(1f - 0.65f * level, state.coreStasisActivationMultiplier(), 1e-6f)
        }
        assertEquals(22f, state.coreStasis.stillSeconds, "the still clock is capped at full stasis")
    }

    @Test
    fun jiggleInPlaceStillFreezes() {
        val state = buildTestState()
        state.drive(30f, { t, out -> pose(out, 30f * sin(TWO_PI * t), 0f, 30f * TWO_PI * cos(TWO_PI * t), 0f) }) { t ->
            if (t >= 23f) assertEquals(1f, state.coreStasisLevel(), "level at $t")
        }
    }

    @Test
    fun aVigorousWiggleIsMovement() {
        val state = buildTestState()
        val frequency = 3f * TWO_PI
        val maxStill = state.drive(30f, { t, out -> pose(out, 60f * sin(frequency * t), 0f, 60f * frequency * cos(frequency * t), 0f) })
        assertTrue(maxStill < 1f, "a vigorous wiggle peaks at $maxStill s")
        assertEquals(0f, state.coreStasisLevel())
    }

    @Test
    fun aFastTightOrbitNeverFreezes() {
        val state = buildTestState()
        val maxStill = state.drive(60f, circle(radius = 125f, speed = 650f)) { t ->
            if (t > 2f) assertEquals(0f, state.coreStasis.stillSeconds, "still clock at $t")
        }
        assertTrue(maxStill < 1f, "the EMA warm-up peaks at $maxStill s")
    }

    @Test
    fun theCollapsingOrbitPaceNeverFreezes() {
        val state = buildTestState()
        val maxStill = state.drive(30f, circle(radius = 155f, speed = 300f))
        assertTrue(maxStill < 2f, "the collapsing orbit pace peaks at $maxStill s")
        assertEquals(0f, state.coreStasis.stillSeconds)
    }

    @Test
    fun aSlowBrakeDriftThatTravelsNeverFreezes() {
        val state = buildTestState()
        val maxStill = state.drive(60f, straight(speed = 80f)) { t ->
            assertEquals(0f, state.coreStasisLevel(), "level at $t")
            if (t >= 4f) assertEquals(0f, state.coreStasis.stillSeconds, "still clock at $t")
        }
        assertTrue(maxStill <= 3.1f, "the drift peaks at $maxStill s, about R / v")
    }

    @Test
    fun aCrawlBelowTheTravelSpeedStaysStill() {
        // Invulnerable, so the fracture that follows cannot end the run before the crawl leaves the radius.
        val state = buildTestState().apply { invulnerableUntil = Float.MAX_VALUE }
        state.drive(200f, straight(speed = 3f)) { t ->
            if (t >= 23f) assertEquals(1f, state.coreStasisLevel(), "level at $t")
        }
        assertEquals(GamePhase.RUNNING, state.phase)
        assertEquals(240f, hypot(state.coreX - state.coreStasis.anchorX, state.coreY - state.coreStasis.anchorY), 0.01f,
            "the crawl left the radius and dragged the leash")
        assertTrue(state.coreStasisFracturing())
    }

    @Test
    fun aSlowHoverInsideTheLeashFreezes() {
        val state = buildTestState()
        state.drive(30f, circle(radius = 100f, speed = 80f)) { t ->
            if (t >= 23f) assertEquals(1f, state.coreStasisLevel(), "level at $t")
        }
    }

    @Test
    fun shortStopsDoNotAccumulate() {
        val state = buildTestState()
        var maxStill = 0f
        repeat(10) {
            maxStill = max(maxStill, state.drive(8f, hold(state)) { assertEquals(0f, state.coreStasisLevel()) })
            maxStill = max(maxStill, state.drive(2f, straight(speed = 600f, fromX = state.coreX)) { assertEquals(0f, state.coreStasisLevel()) })
        }
        assertTrue(maxStill < 10f, "still 8 s and move 2 s peaks at $maxStill s")
    }

    @Test
    fun mostlyStillWithBriefMovesStillFreezes() {
        val state = buildTestState()
        var frozeInCycle = -1
        repeat(6) { cycle ->
            state.drive(9f, hold(state)) { if (frozeInCycle < 0 && state.coreStasisLevel() > 0f) frozeInCycle = cycle }
            state.drive(1f, straight(speed = 600f, fromX = state.coreX))
        }
        assertTrue(frozeInCycle in 0..2, "still 9 s and move 1 s froze in cycle $frozeInCycle")
    }

    @Test
    fun movingThawsItAtFourTimesTheRate() {
        val state = buildTestState()
        state.drive(28.5f, hold(state))
        assertTrue(state.coreStasisFracturing(), "held still long enough to fracture")
        var setOff = Float.NaN
        var previous = state.coreStasis.stillSeconds
        state.drive(8f, straight(speed = 600f, fromX = state.coreX)) { t ->
            val still = state.coreStasis.stillSeconds
            if (setOff.isNaN() && still < previous) {
                setOff = t
                assertFalse(state.coreStasisFracturing(), "the first travelling step stops the fracture")
                assertEquals(0f, state.coreStasis.frozenSeconds)
                assertEquals(0f, state.coreStasis.fractureClock)
            }
            previous = still
            if (!setOff.isNaN()) {
                val thawed = t - setOff + STEP
                if (thawed > 1.5f - STEP && thawed < 1.5f + STEP) assertEquals(0.5f, state.coreStasisLevel(), 0.01f, "level at $thawed s")
                if (thawed >= 3f + STEP) assertEquals(0f, state.coreStasisLevel(), "level at $thawed s")
                if (thawed >= 5.5f + STEP) assertEquals(0f, still, "still clock at $thawed s")
            }
        }
        assertTrue(setOff < 0.45f, "a Core at 600 u/s travels once it drags the leash, at $setOff s")
    }

    @Test
    fun aTeleportInPlaceDoesNotThaw() {
        val state = buildTestState()
        state.drive(15f, hold(state))
        val before = state.coreStasis.stillSeconds
        state.coreX += 600f
        state.updateCoreStasis(STEP)
        val anchorDistance = hypot(state.coreX - state.coreStasis.anchorX, state.coreY - state.coreStasis.anchorY)
        assertEquals(240f, anchorDistance, 0.01f, "the anchor follows the jump to the radius")
        assertEquals(before + STEP, state.coreStasis.stillSeconds, 1e-5f, "a teleport carries no speed")
        state.drive(5f, hold(state))
        assertEquals(before + STEP + 5f, state.coreStasis.stillSeconds, 1e-3f)
    }

    @Test
    fun aDragAtTravelSpeedIsMovement() {
        val state = buildTestState()
        state.drive(15f, hold(state))
        var dragging = false
        var previous = state.coreStasis.stillSeconds
        val peak = state.drive(6f, straight(speed = 100f)) {
            val still = state.coreStasis.stillSeconds
            if (state.coreX > 240f) {
                assertTrue(state.coreStasis.speedEma >= 30f)
                if (dragging && previous > 0f) assertEquals(max(0f, previous - 4f * STEP), still, 1e-4f, "decays at 4x")
                dragging = true
            }
            previous = still
        }
        assertTrue(dragging)
        assertEquals(17.4f, peak, 0.05f, "still until the leash drags at R / v = 2.4 s")
    }

    @Test
    fun pauseAndChoiceFreezeTheClock() {
        val state = buildTestState()
        state.drive(9f, hold(state))
        val held = state.coreStasis.stillSeconds
        state.togglePause()
        assertEquals(GamePhase.PAUSED, state.phase)
        repeat(100) { state.update(0.1f) }
        state.updateCoreStasis(STEP)
        assertEquals(held, state.coreStasis.stillSeconds, "paused")
        state.togglePause()
        state.phase = GamePhase.CHOICE
        repeat(100) { state.update(0.1f) }
        state.updateCoreStasis(STEP)
        assertEquals(held, state.coreStasis.stillSeconds, "choosing")
        state.phase = GamePhase.RUNNING
        state.velocityX = 0f
        state.velocityY = 0f
        state.updateCoreStasis(STEP)
        assertEquals(held + STEP, state.coreStasis.stillSeconds, 1e-6f, "resumes from where it stopped")
    }

    @Test
    fun theNadirIsExemptAndThawsTheCore() {
        val state = buildTestState().apply { quietArenaForTesting() }
        state.enterDimensionForTesting(WorldLayer.NADIR)
        state.drive(40f, hold(state)) { assertEquals(0f, state.coreStasisLevel()) }
        assertEquals(0f, state.coreStasis.stillSeconds, "a pinned Core in the Nadir never counts as still")
        state.coreStasis.stillSeconds = 22f
        state.drive(2f, hold(state))
        assertEquals(22f - 8f, state.coreStasis.stillSeconds, 1e-3f, "a full clock decays at 4x in the Nadir")
        state.worldLayer = WorldLayer.BASE
        state.drive(1f, hold(state))
        assertEquals(15f, state.coreStasis.stillSeconds, 1e-3f, "realspace counts on from the decayed value")
    }

    @Test
    fun rebaseMovesTheAnchorWithTheWorld() {
        val state = buildTestState()
        state.coreX = 249_990f
        state.coreStasis.reset(state.coreX, state.coreY)
        state.drive(5f, hold(state))
        state.coreX = 250_010f
        state.updateCoreStasis(STEP)
        val anchorBefore = state.coreStasis.anchorX
        val stillBefore = state.coreStasis.stillSeconds
        state.rebaseWorldIfNeeded()
        assertEquals(0f, state.coreX)
        assertEquals(anchorBefore - 250_010f, state.coreStasis.anchorX)
        assertTrue(hypot(state.coreX - state.coreStasis.anchorX, state.coreY - state.coreStasis.anchorY) < 240f)
        assertEquals(stillBefore, state.coreStasis.stillSeconds)
        state.updateCoreStasis(STEP)
        assertEquals(stillBefore + STEP, state.coreStasis.stillSeconds, 1e-5f, "the rebase is not a move")
    }

    @Test
    fun startRunClearsEveryStasisField() {
        val state = buildTestState()
        state.populateCoreStasisRetainedState()
        state.startRun()
        assertStasisAtRest(state)
    }

    @Test
    fun disabledContentMovesNoField() {
        val state = buildTestState(content = canonicalGameplayContent.copy(coreStasis = CoreStasisTuning.DISABLED))
        state.drive(60f, hold(state))
        state.drive(10f, straight(speed = 600f))
        assertStasisAtRest(state, anchorX = 0f, anchorY = 0f)
        assertEquals(0f, state.coreStasisLevel())
        assertEquals(1f, state.coreStasisActivationMultiplier())
    }

    @Test
    fun measureOnlyMeasuresButNeverActs() {
        val state = buildTestState(content = canonicalGameplayContent.copy(coreStasis = CoreStasisTuning.MEASURE_ONLY))
        val hp = state.hp
        state.drive(60f, hold(state)) {
            assertEquals(0f, state.coreStasisLevel())
            assertEquals(1f, state.coreStasisActivationMultiplier())
            assertFalse(state.coreStasisFracturing())
        }
        assertEquals(22f, state.coreStasis.stillSeconds, "the still clock runs")
        assertEquals(0f, state.coreStasis.frozenSeconds)
        assertEquals(0f, state.coreStasis.fractureClock)
        state.drive(6f, straight(speed = 600f))
        assertEquals(0f, state.coreStasis.stillSeconds)
        assertTrue(state.takeVisualFxCues().isEmpty(), "measure-only sheds no frost")
        assertEquals(hp, state.hp)
    }

    @Test
    fun theFactorReachesBothWeaponRateSinks() {
        val state = buildTestState()
        val bonus = state.buildActivationBonus()
        val cooldown = state.cooldown(1f)
        val rate = state.attackSpeed * (1f + state.currentWeaponMastery.activationSpeedBonus) * bonus
        assertEquals(max(0.25f, 1f + 0.75f * (rate - 1f)), state.continuousWeaponRate(), 1e-6f)
        state.coreStasis.stillSeconds = state.content.coreStasis.fullStasisSeconds
        assertEquals(0.35f, state.coreStasisActivationMultiplier(), 1e-6f)
        assertEquals(cooldown / 0.35f, state.cooldown(1f), cooldown * 1e-5f)
        assertEquals(max(0.25f, 1f + 0.75f * (rate * 0.35f - 1f)), state.continuousWeaponRate(), 1e-6f)
        if (rate == 1f) assertEquals(0.5125f, state.continuousWeaponRate(), 1e-6f)
        assertEquals(bonus, state.buildActivationBonus(), "the build's activation bonus does not show the freeze")
    }

    @Test
    fun skillsAreNotSlowed() {
        val ready = listOf(0f, 22f).map { still ->
            buildTestState().run {
                coreStasis.stillSeconds = still
                acquireActive(SkillId.SOLAR_FLARE)
                val slot = skillLoadout.slotOf(SkillId.SOLAR_FLARE)
                aimAt(coreX + 300f, coreY)
                assertTrue(castSkill(slot))
                updateSkills(1f)
                skillRuntime[slot, SkillCell.READY_AT]
            }
        }
        assertEquals(ready[0], ready[1], "a frozen Core recharges its actives at the usual rate")
    }

    @Test
    fun thawShedsFrostOnQuarterCrossings() {
        val state = buildTestState()
        state.coreStasis.stillSeconds = state.content.coreStasis.fullStasisSeconds
        state.coreStasis.speedEma = 600f
        state.takeVisualFxCues()
        state.drive(6f, straight(speed = 600f))
        val cryo = FxColorCode.elementTone(StatusElement.CRYO)
        val cues = state.takeVisualFxCues()
        assertEquals(4, cues.count { it is VisualFxCue.Burst && it.colorIndex == cryo }, "one burst per quarter")
        assertEquals(1, cues.count { it is VisualFxCue.ShockwaveAdded && it.colorIndex == cryo }, "one ring at level 0")
        assertEquals(5, cues.size)
    }

    private fun assertStasisAtRest(state: MutableGameState, anchorX: Float = state.coreX, anchorY: Float = state.coreY) {
        val stasis = state.coreStasis
        assertEquals(anchorX, stasis.anchorX)
        assertEquals(anchorY, stasis.anchorY)
        assertEquals(0f, stasis.speedEma)
        assertEquals(0f, stasis.stillSeconds)
        assertEquals(0f, stasis.frozenSeconds)
        assertEquals(0f, stasis.fractureClock)
    }
}

private const val STEP = MutableGameState.FIXED_STEP
private const val TWO_PI = (2.0 * PI).toFloat()

/**
 * Steps the detector for [seconds] along [path] (time since the segment start to x, y, vx, vy) and calls [onStep] after
 * each step with that time. Returns the largest still clock seen.
 */
private fun MutableGameState.drive(
    seconds: Float,
    path: (Float, FloatArray) -> Unit,
    onStep: (Float) -> Unit = {},
): Float {
    val pose = FloatArray(4)
    var maxStill = coreStasis.stillSeconds
    val steps = (seconds / STEP).roundToInt()
    for (index in 1..steps) {
        val t = index * STEP
        path(t, pose)
        coreX = pose[0]
        coreY = pose[1]
        velocityX = pose[2]
        velocityY = pose[3]
        updateCoreStasis(STEP)
        maxStill = max(maxStill, coreStasis.stillSeconds)
        onStep(t)
    }
    return maxStill
}

private fun pose(out: FloatArray, x: Float, y: Float, vx: Float = 0f, vy: Float = 0f) {
    out[0] = x
    out[1] = y
    out[2] = vx
    out[3] = vy
}

/** The Core held where it is, at rest. */
private fun hold(state: MutableGameState): (Float, FloatArray) -> Unit {
    val x = state.coreX
    val y = state.coreY
    return { _, out -> pose(out, x, y) }
}

/** A straight flight along +x at [speed] from ([fromX], 0). */
private fun straight(speed: Float, fromX: Float = 0f): (Float, FloatArray) -> Unit =
    { t, out -> pose(out, fromX + speed * t, 0f, speed, 0f) }

/** A circle of [radius] around the origin at [speed]. */
private fun circle(radius: Float, speed: Float): (Float, FloatArray) -> Unit {
    val omega = speed / radius
    return { t, out ->
        val angle = omega * t
        pose(out, radius * cos(angle), radius * sin(angle), -speed * sin(angle), speed * cos(angle))
    }
}
