// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.game.domain.characterization

import kinetickk.feature.game.domain.model.*
import kinetickk.feature.game.domain.protocol.VisualFxCue
import kinetickk.feature.game.domain.simulation.*
import kotlin.math.roundToLong
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

class GameScenarioTest {
    @Test
    fun dashAddsVelocityAndHeat() {
        val engine = GameScenario(seed = 1, initialMatter = 0)
        engine.resize(1280f, 720f)
        engine.startRun()

        engine.requestDash()
        engine.update(1f / 60f)

        assertTrue(engine.speed > 500f)
        assertTrue(engine.heat > 30f)
        assertTrue(engine.dashPhaseTime > 0f)
    }

    @Test
    fun repeatedDashesTriggerOverheat() {
        val engine = GameScenario(seed = 2, initialMatter = 0)
        engine.resize(1280f, 720f)
        engine.startRun()

        repeat(3) {
            engine.requestDash()
            engine.update(1f / 60f)
        }

        assertTrue(engine.overheated)
        assertEquals(GameScenario.MAX_HEAT, engine.heat)
    }

    @Test
    fun resizingARunKeepsThePointerInsideTheArenaAtTheSameRelativeAim() {
        val engine = GameScenario(seed = 20, initialMatter = 0)
        engine.resize(1280f, 720f)
        engine.startRun()
        engine.updatePointer(1200f, 700f)

        engine.resize(720f, 540f)

        assertEquals(675f, engine.pointerX, 0.001f)
        assertEquals(525f, engine.pointerY, 0.001f)
        assertTrue(engine.pointerX in 0f..engine.screenWidth)
        assertTrue(engine.pointerY in 0f..engine.screenHeight)
    }

    @Test
    fun touchingSingularityEndsTheRunAfterGracePeriod() {
        val engine = GameScenario(seed = 3, initialMatter = 0)
        engine.resize(1280f, 720f)
        engine.startRun()
        engine.updatePointer(640f, 360f)

        repeat(40) { engine.update(1f / 24f) }

        assertEquals(GamePhase.GAME_OVER, engine.phase)
        assertEquals("SINGULARITY CONTACT", engine.message)
    }

    @Test
    fun runClockFormatsForHud() {
        assertEquals("00:00", formatRunTime(0f))
        assertEquals("03:07", formatRunTime(187.9f))
        assertEquals("20:00", formatRunTime(GameScenario.RUN_DURATION_SECONDS))
    }

    @Test
    fun percentageSettingsMoveInExactOnePercentSteps() {
        val engine = GameScenario(seed = 4, initialMatter = 0)
        engine.resize(1280f, 720f)
        engine.openSettings()

        engine.adjustSetting(SettingsRow.MASTER_VOLUME, direction = 1)
        assertEquals(0.66f, engine.settings.masterVolume)

        engine.adjustSetting(SettingsRow.TEXT_SIZE, direction = 1)
        assertEquals(1.26f, engine.settings.textScale)
    }

    @Test
    fun damageNumberSettingsCycleSizeFormatAndHeatThresholds() {
        val engine = GameScenario(seed = 21, initialMatter = 0)
        engine.resize(1280f, 720f)
        engine.openSettings()

        engine.adjustSetting(SettingsRow.DAMAGE_NUMBER_SIZE, direction = 1)
        assertEquals(DamageNumberSize.LARGE, engine.settings.damageNumberSize)

        engine.adjustSetting(SettingsRow.DAMAGE_NUMBER_FORMAT, direction = 1)
        assertEquals(DamageNumberFormat.FULL, engine.settings.damageNumberFormat)

        engine.adjustSetting(SettingsRow.DAMAGE_COLOR_THRESHOLDS, direction = 1)
        assertEquals(100, engine.settings.damageNumberTierThreshold)
    }

    @Test
    fun damageEventsEmitTypedVisualCuesAndRespectTheVisibilitySetting() {
        val enabledEngine = GameScenario(seed = 22, initialMatter = 0)
        val enabledTarget = enabledEngine.addEnemyForTesting(x = 20f, y = 30f, hp = 100_000f)

        val appliedDamage = enabledEngine.damageEnemyForTesting(enabledTarget, 14_000.6f)

        val number = enabledEngine.takeVisualFxCues()
            .filterIsInstance<VisualFxCue.DamageNumberAdded>()
            .single()
        assertEquals(appliedDamage.roundToLong(), number.amount)
        assertFalse(number.critical)
        assertEquals("14.4K", formatDamageNumber(number.amount, DamageNumberFormat.COMPACT))
        assertEquals(number.amount.toString(), formatDamageNumber(number.amount, DamageNumberFormat.FULL))

        val disabledEngine = GameScenario(seed = 23, initialMatter = 0)
        disabledEngine.resize(1280f, 720f)
        disabledEngine.openSettings()
        disabledEngine.adjustSetting(SettingsRow.DAMAGE_NUMBERS, direction = 1)
        val disabledTarget = disabledEngine.addEnemyForTesting(x = 20f, y = 30f, hp = 100_000f)

        disabledEngine.damageEnemyForTesting(disabledTarget, 500f)

        assertTrue(
            disabledEngine.takeVisualFxCues()
                .filterIsInstance<VisualFxCue.DamageNumberAdded>()
                .isEmpty(),
        )
    }

    @Test
    fun shortSettingsViewUsesPagesInsteadOfCollapsingRows() {
        val width = 720f
        val height = 360f
        val engine = GameScenario(seed = 24, initialMatter = 0)
        engine.resize(width, height)
        engine.openSettings()

        val panelHeight = minOf(620f, height - 30f)
        val startY = (height - panelHeight) * 0.5f + 72f
        val availableHeight = (height + panelHeight) * 0.5f - 64f - startY
        assertEquals(6, settingsRowsPerPage(availableHeight, density = 1f))

        engine.selectSettingsPage(1)
        assertEquals(1, engine.settingsPage)

        engine.adjustSetting(SettingsRow.DAMAGE_NUMBER_FORMAT, direction = 1)
        assertEquals(DamageNumberFormat.FULL, engine.settings.damageNumberFormat)
    }

    @Test
    fun cannotRebirthBeforeCurrentTierIsCleared() {
        val engine = GameScenario(seed = 5, initialMatter = 0)

        assertFalse(engine.canRebirth)
        assertFalse(engine.requestRebirth())
        assertFalse(engine.rebirthConfirmationArmed)
        assertEquals(0, engine.rebirthLevel)
        assertEquals(GamePhase.MENU, engine.phase)
    }

    @Test
    fun confirmedRebirthAdvancesExactlyOneTierPerClear() {
        val engine = GameScenario(seed = 6, initialMatter = 0)
        engine.markCurrentRebirthClearedForTesting()

        assertTrue(engine.canRebirth)
        assertFalse(engine.requestRebirth())
        assertTrue(engine.rebirthConfirmationArmed)
        assertEquals(0, engine.rebirthLevel)

        assertTrue(engine.requestRebirth())
        assertFalse(engine.rebirthConfirmationArmed)
        assertEquals(1, engine.rebirthLevel)
        assertEquals(0, engine.highestClearedRebirth)
        assertEquals(GamePhase.RUNNING, engine.phase)
        assertFalse(engine.canRebirth)

        assertFalse(engine.requestRebirth())
        assertEquals(1, engine.rebirthLevel)

        engine.markCurrentRebirthClearedForTesting()
        assertFalse(engine.requestRebirth())
        assertTrue(engine.requestRebirth())
        assertEquals(2, engine.rebirthLevel)
        assertEquals(1, engine.highestClearedRebirth)
        assertFalse(engine.canRebirth)
    }

    @Test
    fun rebirthPreservesMatterLabAndArmoryProgress() {
        val engine = GameScenario(seed = 7, initialMatter = 10_000)
        val upgrade = MetaUpgradeId.CORE_INTEGRITY
        val weapon = WeaponId.SINGULARITY_SPEAR

        assertTrue(engine.buyMetaUpgrade(upgrade))
        assertTrue(engine.buyOrEquipWeapon(weapon))
        val matterBeforeRebirth = engine.totalMatter
        val lifetimeMatterBeforeRebirth = engine.lifetimeMatter
        val metaLevelBeforeRebirth = engine.metaLevel(upgrade)

        engine.markCurrentRebirthClearedForTesting()
        assertFalse(engine.requestRebirth())
        assertTrue(engine.requestRebirth())

        assertEquals(1, engine.rebirthLevel)
        assertEquals(matterBeforeRebirth, engine.totalMatter)
        assertEquals(lifetimeMatterBeforeRebirth, engine.lifetimeMatter)
        assertEquals(metaLevelBeforeRebirth, engine.metaLevel(upgrade))
        assertTrue(engine.isWeaponUnlocked(weapon))
        assertEquals(weapon, engine.startingWeapon)
        assertEquals(weapon, engine.weapon)
    }

    @Test
    fun openingEnemyCountIncreasesWithRebirthTier() {
        val baseline = GameScenario(seed = 8, initialMatter = 0, initialRebirthLevel = 0)
        val maximum = GameScenario(
            seed = 8,
            initialMatter = 0,
            initialRebirthLevel = RebirthProgression.MAX_LEVEL,
        )

        baseline.startRun()
        maximum.startRun()

        assertEquals(RebirthProgression.profile(0).openingEnemyCount, baseline.enemies.size)
        assertTrue(baseline.enemies.all { it.type == EnemyType.DRIFTER })
        assertEquals(RebirthProgression.profile(RebirthProgression.MAX_LEVEL).openingEnemyCount, maximum.enemies.size)
        assertTrue(maximum.enemies.size > baseline.enemies.size)
    }

}
