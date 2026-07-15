// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.model

import kotlin.math.roundToLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameEngineTest {
    @Test
    fun dashAddsVelocityAndHeat() {
        val engine = GameEngine(seed = 1, initialMatter = 0)
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
        val engine = GameEngine(seed = 2, initialMatter = 0)
        engine.resize(1280f, 720f)
        engine.startRun()

        repeat(3) {
            engine.requestDash()
            engine.update(1f / 60f)
        }

        assertTrue(engine.overheated)
        assertEquals(GameEngine.MAX_HEAT, engine.heat)
    }

    @Test
    fun resizingARunKeepsThePointerInsideTheArenaAtTheSameRelativeAim() {
        val engine = GameEngine(seed = 20, initialMatter = 0)
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
        val engine = GameEngine(seed = 3, initialMatter = 0)
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
        assertEquals("20:00", formatRunTime(GameEngine.RUN_DURATION_SECONDS))
    }

    @Test
    fun percentageSettingsMoveInExactOnePercentSteps() {
        val engine = GameEngine(seed = 4, initialMatter = 0)
        engine.resize(1280f, 720f)
        engine.openSettings()

        clickSettingIncrement(engine, SettingsRow.MASTER_VOLUME)
        assertEquals(0.66f, engine.settings.masterVolume)

        clickSettingIncrement(engine, SettingsRow.TEXT_SIZE)
        assertEquals(1.26f, engine.settings.textScale)
    }

    @Test
    fun damageNumberSettingsCycleSizeFormatAndHeatThresholds() {
        val engine = GameEngine(seed = 21, initialMatter = 0)
        engine.resize(1280f, 720f)
        engine.openSettings()

        clickSettingIncrement(engine, SettingsRow.DAMAGE_NUMBER_SIZE)
        assertEquals(DamageNumberSize.LARGE, engine.settings.damageNumberSize)

        clickSettingIncrement(engine, SettingsRow.DAMAGE_NUMBER_FORMAT)
        assertEquals(DamageNumberFormat.FULL, engine.settings.damageNumberFormat)

        clickSettingIncrement(engine, SettingsRow.DAMAGE_COLOR_THRESHOLDS)
        assertEquals(100, engine.settings.damageNumberTierThreshold)
    }

    @Test
    fun damageEventsCacheLongLabelsAndRespectTheVisibilitySetting() {
        val enabledEngine = GameEngine(seed = 22, initialMatter = 0)
        val enabledTarget = enabledEngine.addEnemyForTesting(x = 20f, y = 30f, hp = 100_000f)

        val appliedDamage = enabledEngine.damageEnemyForTesting(enabledTarget, 14_000.6f)

        val number = enabledEngine.damageNumbers.single()
        assertEquals(appliedDamage.roundToLong(), number.amount)
        assertFalse(number.critical)
        assertEquals(formatDamageNumber(number.amount, DamageNumberFormat.COMPACT), number.formattedAmount(DamageNumberFormat.COMPACT))
        assertEquals(number.amount.toString(), number.formattedAmount(DamageNumberFormat.FULL))

        val disabledEngine = GameEngine(seed = 23, initialMatter = 0)
        disabledEngine.resize(1280f, 720f)
        disabledEngine.openSettings()
        clickSettingIncrement(disabledEngine, SettingsRow.DAMAGE_NUMBERS)
        val disabledTarget = disabledEngine.addEnemyForTesting(x = 20f, y = 30f, hp = 100_000f)

        disabledEngine.damageEnemyForTesting(disabledTarget, 500f)

        assertTrue(disabledEngine.damageNumbers.isEmpty())
    }

    @Test
    fun shortSettingsViewUsesPagesInsteadOfCollapsingRows() {
        val width = 720f
        val height = 360f
        val engine = GameEngine(seed = 24, initialMatter = 0)
        engine.resize(width, height)
        engine.openSettings()

        val panelHeight = minOf(620f, height - 30f)
        val startY = (height - panelHeight) * 0.5f + 72f
        val availableHeight = (height + panelHeight) * 0.5f - 64f - startY
        assertEquals(6, settingsRowsPerPage(availableHeight, density = 1f))

        clickSettingsNextPage(engine, width, height)
        assertEquals(1, engine.settingsPage)

        clickSettingIncrement(engine, SettingsRow.DAMAGE_NUMBER_FORMAT, width, height)
        assertEquals(DamageNumberFormat.FULL, engine.settings.damageNumberFormat)
    }

    @Test
    fun cannotRebirthBeforeCurrentTierIsCleared() {
        val engine = GameEngine(seed = 5, initialMatter = 0)

        assertFalse(engine.canRebirth)
        assertFalse(engine.requestRebirth())
        assertFalse(engine.rebirthConfirmationArmed)
        assertEquals(0, engine.rebirthLevel)
        assertEquals(GamePhase.MENU, engine.phase)
    }

    @Test
    fun confirmedRebirthAdvancesExactlyOneTierPerClear() {
        val engine = GameEngine(seed = 6, initialMatter = 0)
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
        val engine = GameEngine(seed = 7, initialMatter = 10_000)
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
        val baseline = GameEngine(seed = 8, initialMatter = 0, initialRebirthLevel = 0)
        val maximum = GameEngine(
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

    @Test
    fun rebirthMenuVictoryAndConfirmationHitTargetsMatchTheRenderer() {
        val outsideMenuEngine = GameEngine(seed = 8, initialMatter = 0)
        outsideMenuEngine.resize(1280f, 720f)
        val menuSpacing = minOf(132f, 1280f * 0.19f)
        val firstMenuItemCenter = 1280f * 0.5f - menuSpacing * 2f

        outsideMenuEngine.pointerPressed(firstMenuItemCenter - menuSpacing * 0.5f, 720f * 0.9f)
        outsideMenuEngine.pointerReleased()

        assertEquals(UiScreen.GAME, outsideMenuEngine.screen)

        val menuEngine = GameEngine(seed = 9, initialMatter = 0)
        menuEngine.resize(1280f, 720f)

        menuEngine.pointerPressed(640f, 720f * 0.9f)
        menuEngine.pointerReleased()

        assertEquals(UiScreen.REBIRTH, menuEngine.screen)
        assertEquals(GamePhase.MENU, menuEngine.phase)

        val victoryEngine = GameEngine(seed = 10, initialMatter = 0)
        victoryEngine.resize(1280f, 720f)
        victoryEngine.markCurrentRebirthClearedForTesting()
        val endButtonY = 720f * 0.72f

        victoryEngine.pointerPressed(640f, endButtonY + 70f)
        victoryEngine.pointerReleased()

        assertEquals(UiScreen.REBIRTH, victoryEngine.screen)
        assertEquals(GamePhase.VICTORY, victoryEngine.phase)

        val overlayBottom = (720f + 650f) * 0.5f
        repeat(2) {
            victoryEngine.pointerPressed(640f, overlayBottom - 93f)
            victoryEngine.pointerReleased()
        }

        assertEquals(1, victoryEngine.rebirthLevel)
        assertEquals(UiScreen.GAME, victoryEngine.screen)
        assertEquals(GamePhase.RUNNING, victoryEngine.phase)
    }

    private fun clickSettingIncrement(
        engine: GameEngine,
        row: SettingsRow,
        width: Float = 1280f,
        height: Float = 720f,
        density: Float = 1f,
    ) {
        val panelWidth = minOf(640f * density, width - 30f * density)
        val panelHeight = minOf(620f * density, height - 30f * density)
        val boundsTop = (height - panelHeight) * 0.5f
        val boundsBottom = boundsTop + panelHeight
        val boundsRight = (width + panelWidth) * 0.5f
        val startY = boundsTop + 72f * density
        val availableHeight = boundsBottom - 64f * density - startY
        val rowsPerPage = settingsRowsPerPage(availableHeight, density)
        val pageStart = engine.settingsPage * rowsPerPage
        val visibleCount = minOf(rowsPerPage, SettingsRow.entries.size - pageStart)
        require(row.ordinal in pageStart until pageStart + visibleCount)
        val spacing = minOf(48f * density, availableHeight / visibleCount)
        val visibleIndex = row.ordinal - pageStart
        val rowCenterY = startY + visibleIndex * spacing + (spacing - 4f * density) * 0.5f
        engine.pointerPressed(boundsRight - 50f * density, rowCenterY)
        engine.pointerReleased()
    }

    private fun clickSettingsNextPage(engine: GameEngine, width: Float, height: Float, density: Float = 1f) {
        val panelWidth = minOf(640f * density, width - 30f * density)
        val panelHeight = minOf(620f * density, height - 30f * density)
        val boundsRight = (width + panelWidth) * 0.5f
        val boundsBottom = (height + panelHeight) * 0.5f
        engine.pointerPressed(boundsRight - 42f * density, boundsBottom - 25f * density)
        engine.pointerReleased()
    }
}
