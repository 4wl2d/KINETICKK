// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.ItemModifier
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.KINETICKK_CONTENT_VERSION
import kinetickk.ball.content.api.MetaUpgradeDefinition
import kinetickk.ball.content.api.MetaUpgradeId
import kinetickk.ball.content.api.RebirthDirective
import kinetickk.ball.content.api.RebirthPolicySnapshot
import kinetickk.ball.content.api.RebirthProfile
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicAspect
import kinetickk.ball.content.api.RelicDefinition
import kinetickk.ball.content.api.RelicPolicy
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.WeaponMastery
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.api.RunId
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.interaction.layout.choiceLayoutGeometry
import kinetickk.ball.gameplay.interaction.layout.pauseLayoutGeometry
import kinetickk.ball.gameplay.interaction.terminal.TerminalContent
import kinetickk.ball.gameplay.interaction.terminal.drawCoreDeath
import kinetickk.ball.gameplay.interaction.GameplayContent
import kinetickk.ball.gameplay.interaction.GameplayInteractionPort
import kinetickk.ball.gameplay.interaction.GameplayInteractionOutput
import kinetickk.ball.gameplay.api.GameplayAcceptance
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.input.key.Key
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kinetickk.ball.gameplay.interaction.terminal.terminalPresentation
import kinetickk.ball.gameplay.interaction.localization.GameplayText
import kinetickk.ball.gameplay.interaction.rewards.RewardContent
import kinetickk.ball.gameplay.interaction.rewards.RewardPresentation
import kinetickk.ball.gameplay.interaction.rewards.rewardCardPresentation
import kinetickk.ball.gameplay.nucleus.GameplayContext
import kinetickk.ball.gameplay.nucleus.GameplayDecision
import kinetickk.ball.gameplay.nucleus.GameplayNucleus
import kinetickk.ball.gameplay.nucleus.GameplayNucleusPulse
import kinetickk.ball.gameplay.nucleus.GameplayStartContext
import kinetickk.ball.gameplay.nucleus.GameplayStartInputs
import kinetickk.ball.gameplay.nucleus.GameplayState
import kinetickk.ball.gameplay.nucleus.render.ChoiceOption
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.foundation.design.SpaceBlack
import kinetickk.foundation.design.Violet
import kinetickk.foundation.design.White
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertIs

@OptIn(ExperimentalTestApi::class)
class GameplayLocalizedRenderingTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun russianGameplayAndRewardsRenderAtDesktopAndCompactBounds() {
        val scenario = mutableStateOf(Scenario(1000, 720, Scene.HUD))
        compose.setContent {
            val current = scenario.value
            val model = remember(current) { model(current) }
            CompositionLocalProvider(
                LocalDensity provides Density(1f),
                LocalAppLanguage provides AppLanguage.Russian,
            ) {
                val textMeasurer = CanvasTextMeasurer(rememberTextMeasurer(), current.textScale, AppLanguage.Russian)
                Box(Modifier.requiredSize(current.width.dp, current.height.dp).testTag("localized-gameplay")) {
                    if (current.scene == Scene.REWARDS) {
                        val items = model.content.items.take(3)
                        val cards = items.mapIndexed { index, item ->
                            model.rewardCardPresentation(
                                ChoiceOption(ChoiceType.ITEM, item.name, item.description, item.rarity.displayLabel, itemId = item.id),
                                index,
                                AppLanguage.Russian,
                            )
                        }
                        RewardContent(
                            RewardPresentation(
                                AppLanguage.Russian.text(GameplayText.ChooseArtifact),
                                AppLanguage.Russian.text(GameplayText.TimeSuspended),
                                cards, White, Violet, 3,
                            ),
                            choiceLayoutGeometry(current.width.toFloat(), current.height.toFloat(), 1f, 3, true),
                            current.width.toFloat(), 1f, current.textScale, 0f, true, {}, {},
                        )
                    } else if (current.scene == Scene.GAME_OVER || current.scene == Scene.VICTORY) {
                        TerminalContent(model.terminalPresentation(AppLanguage.Russian).copy(
                            victory = current.scene == Scene.VICTORY,
                            reason = if (current.scene == Scene.VICTORY) "Архитектор уничтожен" else "Ядро разрушено",
                        ), current.textScale, false, 3f, true, {})
                    } else {
                        Canvas(Modifier.fillMaxSize()) {
                            if (current.scene == Scene.HUD) {
                                drawGameplay(model, VisualFxProjection.EMPTY, textMeasurer, 0f, null)
                            } else if (current.scene == Scene.PAUSE) {
                                drawRect(SpaceBlack)
                                drawPause(textMeasurer, pauseLayoutGeometry(size.width, size.height, 1f))

                            }
                        }
                    }
                }
            }
        }

        val scenarios = listOf(1000 to 720, 390 to 720, 780 to 360).flatMap { (width, height) ->
            Scene.entries.map { scene -> Scenario(width, height, scene) }
        } + listOf(
            Scenario(390, 720, Scene.REWARDS, 1.75f),
            Scenario(780, 360, Scene.PAUSE, 1.75f),
            Scenario(780, 360, Scene.GAME_OVER, 1.75f),
            Scenario(780, 360, Scene.VICTORY, 1.75f),
        )
        scenarios.forEach { current ->
            compose.runOnIdle { scenario.value = current }
            compose.onNodeWithTag("localized-gameplay").assertIsDisplayed()
            if (current.scene == Scene.REWARDS) {
                compose.onNodeWithText("Взять · 1", useUnmergedTree = true).assertIsDisplayed()
                compose.onNodeWithTag("kinetickk.gameplay.reroll").assertIsDisplayed()
            }
            capture("gameplay-ru-${current.scene.name.lowercase()}-${current.width}x${current.height}-${current.textScale}")
        }
    }

    private fun model(scenario: Scenario): GameplayRenderModel =
        requireNotNull(GameplayNucleus.renderSnapshot(startedState(scenario)).renderModel)

    private fun startedState(scenario: Scenario): GameplayState {
        val content = localizationFixtureContent()
        val profile = PlayerProfile(preferences = PlayerPreferences(language = AppLanguage.Russian, textScale = scenario.textScale))
        val initial = GameplayState.initial(RunId(1), content)
        val started = assertIs<GameplayDecision.Accepted>(GameplayNucleus.decide(
            initial,
            GameplayNucleusPulse.StartRun,
            GameplayContext(start = GameplayStartContext.Ready(GameplayStartInputs(
                content,
                GameplayProfileSnapshot(profile.preferences, profile.economy, profile.loadout, profile.labProgress, profile.collection, profile.rebirthProgress),
                seed = 731_991,
            ))),
        )).frame.nextState
        val resized = assertIs<GameplayDecision.Accepted>(GameplayNucleus.decide(
            started,
            GameplayNucleusPulse.Intent(GameplayInteractionPulse.ViewportChanged.fromValidated(
                scenario.width.toFloat(), scenario.height.toFloat(), 1f,
            )),
        )).frame.nextState
        return resized
    }

    @Test
    fun actualDeathKeepsTheRunFrozenWhilePresentationAdvancesAndRestartClicksOnce() {
        var state = startedState(Scenario(1000, 720, Scene.GAME_OVER, 1.25f))
        val port = object : GameplayInteractionPort {
            override val instanceId get() = state.instanceId
            override fun renderSnapshot() = GameplayNucleus.renderSnapshot(state)
            override fun visualFxSnapshot() = VisualFxProjection.EMPTY
            override fun accept(pulse: GameplayInteractionPulse): GameplayAcceptance =
                when (val decision = GameplayNucleus.decide(state, GameplayNucleusPulse.Intent(pulse))) {
                    is GameplayDecision.Accepted -> {
                        state = decision.frame.nextState
                        GameplayAcceptance.Accepted(state.instanceId, state.revision)
                    }
                    is GameplayDecision.Rejected -> GameplayAcceptance.Rejected(state.instanceId, state.revision, decision.reason)
                }
        }
        repeat(100) {
            val current = requireNotNull(port.renderSnapshot().renderModel)
            if (current.phase == GamePhase.RUNNING) {
                port.accept(GameplayInteractionPulse.PointerMoved.fromValidated(
                    current.screenWidth * 0.5f + current.coreX - current.cameraX,
                    current.screenHeight * 0.5f + current.coreY - current.cameraY,
                ))
                port.accept(GameplayInteractionPulse.FrameElapsed.fromValidated(0.1f))
            }
        }
        val terminal = requireNotNull(port.renderSnapshot().renderModel)
        assertEquals(GamePhase.GAME_OVER, terminal.phase)
        val outputs = mutableListOf<GameplayInteractionOutput>()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f), LocalAppLanguage provides AppLanguage.Russian) {
                Box(Modifier.requiredSize(1000.dp, 720.dp).testTag("localized-gameplay")) {
                    GameplayContent(port, true, outputs::add)
                }
            }
        }
        compose.onNodeWithTag("kinetickk.gameplay.restart").assertDoesNotExist()
        compose.onNodeWithTag("kinetickk.gameplay").performKeyInput { pressKey(Key.R) }
        compose.runOnIdle { assertTrue(outputs.isEmpty()) }
        capture("core-death-start")
        compose.mainClock.advanceTimeBy(320)
        compose.onNodeWithTag("kinetickk.gameplay.restart").assertDoesNotExist()
        capture("core-death-fragments")
        compose.mainClock.advanceTimeBy(480)
        capture("core-death-report-entering")
        compose.mainClock.advanceTimeBy(1500)
        compose.onNodeWithTag("kinetickk.gameplay.restart").assertIsEnabled()
        capture("core-death-report-complete")
        compose.onNodeWithTag("kinetickk.gameplay.restart").performMouseInput { click() }
        compose.runOnIdle {
            assertEquals(listOf<GameplayInteractionOutput>(GameplayInteractionOutput.RestartRun), outputs)
            val current = requireNotNull(port.renderSnapshot().renderModel)
            assertEquals(terminal.elapsed, current.elapsed)
            assertEquals(terminal.runStatistics, current.runStatistics)
            assertEquals(terminal.kills, current.kills)
        }
        compose.onNodeWithTag("kinetickk.gameplay.exit").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithTag("kinetickk.gameplay.exit").performKeyInput { pressKey(Key.Enter) }
        compose.runOnIdle {
            assertEquals(listOf<GameplayInteractionOutput>(GameplayInteractionOutput.RestartRun, GameplayInteractionOutput.ExitToHome), outputs)
        }
    }

    @Test
    fun coreFragmentsHaveAFiniteLifetime() {
        val time = mutableStateOf(0f)
        compose.setContent {
            val model = remember { model(Scenario(640, 420, Scene.GAME_OVER)) }
            Box(Modifier.requiredSize(640.dp, 420.dp).testTag("localized-gameplay")) {
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(SpaceBlack)
                    drawCoreDeath(model, time.value)
                }
            }
        }
        fun visiblePixels(): Int {
            val pixels = compose.onNodeWithTag("localized-gameplay").captureToImage().toPixelMap()
            var count = 0
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                if (pixels[x, y].toArgb() != SpaceBlack.toArgb()) count++
            }
            return count
        }
        assertTrue(visiblePixels() > 0)
        compose.runOnIdle { time.value = 0.4f }
        assertTrue(visiblePixels() > 0)
        compose.runOnIdle { time.value = 1.2f }
        assertEquals(0, visiblePixels())
    }

    private fun capture(name: String) {
        val directory = System.getenv("KINETICKK_RENDER_CAPTURE_DIR") ?: return
        val bitmap = compose.onNodeWithTag("localized-gameplay").captureToImage()
        val pixels = bitmap.toPixelMap()
        val image = BufferedImage(bitmap.width, bitmap.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) image.setRGB(x, y, pixels[x, y].toArgb())
        File(directory).mkdirs()
        ImageIO.write(image, "png", File(directory, "$name.png"))
    }

    private enum class Scene { HUD, PAUSE, REWARDS, GAME_OVER, VICTORY }
    private data class Scenario(val width: Int, val height: Int, val scene: Scene, val textScale: Float = 1f)
}

/** Interaction consumes Content's public snapshots; a small fixture exercises real text grammars. */
private fun localizationFixtureContent() = GameplayContentSnapshot(
    version = KINETICKK_CONTENT_VERSION,
    items = listOf("Cinder", "Neon", "Gravitic").mapIndexed { index, component ->
        val name = "$component Ram"
        ItemDefinition(
            index, name,
            "$name binds the Impact family to a $component component: +5% Impact damage and +4% Weapon power per stack (max 8).",
            if (index == 1) ItemRarity.UNCOMMON else ItemRarity.COMMON,
            ItemModifier(ItemEffect.IMPACT_DAMAGE, 0.05f), ItemModifier(ItemEffect.WEAPON_POWER, 0.04f),
            maxStacks = 8, unlockLevel = 1, family = "Impact",
        )
    }.toImmutableList(),
    weapons = immutableListOf(WeaponDefinition(
        WeaponId.FLUX_WAKE, "Flux Wake", "Your trail hardens into a cutting wake; enemies crossing it take damage.",
        listOf("TRAIL"), 0,
    )),
    weaponMasteries = WeaponMastery.entries.toImmutableList(),
    metaUpgrades = MetaUpgradeId.entries.map { id ->
        MetaUpgradeDefinition(id, "Fixture", "Fixture", 1, 1, ItemModifier(ItemEffect.MAX_INTEGRITY, 1f))
    }.toImmutableList(),
    relics = RelicId.entries.map { id ->
        RelicDefinition(id, id.name, RelicAspect.entries.first(), "Fixture", "Fixture")
    }.toImmutableList(),
    rebirth = RebirthPolicySnapshot(
        minimumLevel = 0, maximumLevel = 0,
        profiles = immutableListOf(RebirthProfile(
            0, RebirthDirective.BASELINE, 5, 1f, 1f, 1f, 1f, 1f, 1f, 0f, 1f, 0f, 1f, 0,
            120, 0.09f, 24f,
        )),
        maxActiveEnemies = 120, minSpawnIntervalSeconds = 0.09f, minEliteIntervalSeconds = 24f,
    ),
    relicPolicy = RelicPolicy(4, 5),
)
