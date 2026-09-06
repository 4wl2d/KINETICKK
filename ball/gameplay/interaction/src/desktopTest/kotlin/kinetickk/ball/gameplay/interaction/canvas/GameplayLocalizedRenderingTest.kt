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
import kinetickk.ball.content.api.RelicPolicy
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.WeaponMastery
import kinetickk.ball.gameplay.api.GameplayCommandIssuerProvenance
import kinetickk.ball.gameplay.api.GameplayCommandSource
import kinetickk.ball.gameplay.api.GameplayCommandSourceToken
import kinetickk.ball.gameplay.api.GameplayEffectiveProtocolIdentity
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.api.GameplayModuleCommand
import kinetickk.ball.gameplay.api.GameplayModuleCommandPulse
import kinetickk.ball.gameplay.api.GameplaySemanticHandle
import kinetickk.ball.gameplay.api.RunId
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.interaction.layout.choiceLayoutGeometry
import kinetickk.ball.gameplay.interaction.layout.pauseLayoutGeometry
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
                    } else {
                        Canvas(Modifier.fillMaxSize()) {
                            if (current.scene == Scene.HUD) {
                                drawGameplay(model, VisualFxProjection.EMPTY, textMeasurer, 0f, null, null)
                            } else {
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
        } + Scenario(390, 720, Scene.REWARDS, 1.75f)
        scenarios.forEach { current ->
            compose.runOnIdle { scenario.value = current }
            compose.onNodeWithTag("localized-gameplay").assertIsDisplayed()
            if (current.scene == Scene.REWARDS) {
                compose.onNodeWithText("ВЫБРАТЬ [1]", useUnmergedTree = true).assertIsDisplayed()
                compose.onNodeWithTag("kinetickk.gameplay.reroll").assertIsDisplayed()
            }
            capture("gameplay-ru-${current.scene.name.lowercase()}-${current.width}x${current.height}-${current.textScale}")
        }
    }

    private fun model(scenario: Scenario): GameplayRenderModel {
        val content = localizationFixtureContent()
        val profile = PlayerProfile(preferences = PlayerPreferences(language = AppLanguage.Russian, textScale = scenario.textScale))
        val initial = GameplayState.initial(RunId(1), content)
        val started = assertIs<GameplayDecision.Accepted>(GameplayNucleus.decide(
            initial,
            GameplayNucleusPulse.ModuleCommand(GameplayModuleCommandPulse(
                GameplayCommandSourceToken(GameplaySemanticHandle(GameplayCommandSource.LocalSession, 0, 0), initial.instanceId, 1, 0),
                GameplayEffectiveProtocolIdentity.SESSION_START,
                GameplayModuleCommand.StartRun,
                GameplayCommandIssuerProvenance.LOCAL_SESSION_STATIC_BINDING,
            )),
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
        return requireNotNull(GameplayNucleus.renderSnapshot(resized).renderModel)
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

    private enum class Scene { HUD, PAUSE, REWARDS }
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
    relics = immutableListOf(),
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
