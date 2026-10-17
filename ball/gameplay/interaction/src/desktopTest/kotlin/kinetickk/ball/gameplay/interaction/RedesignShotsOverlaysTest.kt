// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kinetickk.ball.content.api.EquippedRelic
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RewardFocus
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.interaction.canvas.drawGameplay
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.interaction.layout.choiceLayoutGeometry
import kinetickk.ball.gameplay.interaction.layout.pauseLayoutGeometry
import kinetickk.ball.gameplay.interaction.rewards.RewardContent
import kinetickk.ball.gameplay.interaction.rewards.itemChoice
import kinetickk.ball.gameplay.interaction.rewards.rewardFixtureContent
import kinetickk.ball.gameplay.interaction.rewards.rewardFixtureModel
import kinetickk.ball.gameplay.interaction.terminal.TerminalContent
import kinetickk.ball.gameplay.nucleus.render.ChoiceOption
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.RelicChoiceAction
import kinetickk.ball.gameplay.nucleus.render.RewardPreview
import kinetickk.ball.gameplay.nucleus.render.RewardStatChange
import kinetickk.ball.gameplay.nucleus.render.TotemAction
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.foundation.design.LocalKkRolePalette
import kinetickk.foundation.design.rememberInterfaceTypography
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

private const val FocusDescription = "Choose a direction, then one of three matching offers."

/** Local visual QA harness (not committed): renders every overlay state for board comparison. */
@OptIn(ExperimentalTestApi::class)
class RedesignShotsOverlaysTest {
    private val sizes = listOf(1440 to 810, 844 to 390, 390 to 844)
    private val languages = listOf(AppLanguage.English to "en", AppLanguage.Russian to "ru")
    private val output = File(System.getenv("KK_SHOTS_OUT") ?: "build/redesign-shots/overlays").apply { mkdirs() }
    // The game's default text size (125 %) and the setting's maximum.
    private val textScales = listOf(1.25f to "", 1.75f to "-t175") + (if (System.getenv("KK_SHOTS_T100") != null) listOf(1f to "-t100") else emptyList())
    private val only = System.getenv("KK_SHOTS_ONLY")?.split(',')?.toSet()

    @Test
    fun rendersOverlays() {
        for ((width, height) in sizes) for ((language, tag) in languages) for ((textScale, suffix) in textScales) {
            runDesktopComposeUiTest(width, height) {
                val scene = mutableStateOf<Scene?>(null)
                setContent {
                    val current = scene.value ?: return@setContent
                    CompositionLocalProvider(LocalDensity provides Density(1f), LocalAppLanguage provides language) {
                        Box(Modifier.requiredSize(width.dp, height.dp).testTag("shot")) { current.content(width.toFloat(), height.toFloat(), language) }
                    }
                }
                scenes(width.toFloat(), height.toFloat(), language, textScale).filter { only == null || it.name in only }.forEach { next ->
                    scene.value = next
                    waitForIdle()
                    next.hover?.let { target -> onNodeWithTag(target).performMouseInput { moveTo(center) }; waitForIdle() }
                    mainClock.advanceTimeBy(2_000)
                    waitForIdle()
                    val bitmap = onNodeWithTag("shot").captureToImage()
                    val pixels = bitmap.toPixelMap()
                    val image = BufferedImage(bitmap.width, bitmap.height, BufferedImage.TYPE_INT_ARGB)
                    for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) image.setRGB(x, y, pixels[x, y].toArgb())
                    ImageIO.write(image, "png", File(output, "${next.name}$suffix-${width}x$height-$tag.png"))
                }
            }
        }
    }

    private class Scene(val name: String, val hover: String? = null, val content: @Composable (Float, Float, AppLanguage) -> Unit)

    private fun scenes(width: Float, height: Float, language: AppLanguage, textScale: Float): List<Scene> {
        val relics3 = listOf(EquippedRelic(RelicId.PERPETUAL_VECTOR, 2), EquippedRelic(RelicId.GYRE_ENGINE, 1), EquippedRelic(RelicId.FURNACE_COVENANT, 1))
        val relics4 = relics3 + EquippedRelic(RelicId.AEGIS_INVERSION, 1)
        fun model(block: () -> GameplayRenderModel) = block()
        return listOf(
            Scene("levelup-cards") { w, h, l ->
                RewardShot(model {
                    rewardFixtureModel(
                        choices = listOf(itemChoice(0), itemChoice(1), itemChoice(2)),
                        itemStacks = listOf(2, 0, 0, 0, 0), discoveredItems = setOf(0), language = l, width = w, height = h, textScale = textScale,
                        previews = listOf(
                            RewardPreview(immutableListOf(RewardStatChange("Dash impulse", 873f, 1083f, ""), RewardStatChange("Cooling", 23.2f, 24.8f, "/s"))),
                            RewardPreview(immutableListOf(RewardStatChange("Critical chance", 8f, 27f, "%"), RewardStatChange("Critical power", 1.5f, 1.64f, "×"))),
                            RewardPreview(immutableListOf(RewardStatChange("Overdrive gain", 1.2f, 1.51f, "×"), RewardStatChange("Combo window", 2.8f, 2.98f, "s"))),
                        ),
                    )
                })
            },
            Scene("levelup-rarities") { w, h, l ->
                RewardShot(model {
                    rewardFixtureModel(
                        choices = listOf(itemChoice(3), itemChoice(4), itemChoice(0), itemChoice(1)),
                        itemStacks = listOf(1, 0, 0, 3, 1), discoveredItems = setOf(0, 3, 4), language = l, width = w, height = h, textScale = textScale,
                    )
                })
            },
            Scene("weapon") { w, h, l ->
                RewardShot(model {
                    rewardFixtureModel(
                        choiceType = ChoiceType.WEAPON, language = l, width = w, height = h, textScale = textScale,
                        // The Pentagon runs: Arc Coil, its signature weapon, carries the Signature chip.
                        coreShape = kinetickk.ball.content.api.CoreShape.PENTAGON,
                        choices = listOf(WeaponId.ARC_COIL, WeaponId.GRAVITY_MINES, WeaponId.NULL_LANCE).map { id ->
                            val weapon = rewardFixtureContent.weapon(id)
                            ChoiceOption(ChoiceType.WEAPON, weapon.name, weapon.description, weapon.tags.first().displayLabel.uppercase(), weaponId = id)
                        },
                    )
                })
            },
            Scene("totem") { w, h, l ->
                RewardShot(model {
                    rewardFixtureModel(
                        choiceType = ChoiceType.TOTEM, language = l, width = w, height = h, textScale = textScale, weaponLevel = 5,
                        choices = listOf(
                            ChoiceOption(ChoiceType.TOTEM, "Amplify Flux Wake", "Advance the current system from level 5 to 6 immediately.", "RESONANT",
                                weaponId = WeaponId.FLUX_WAKE, totemAction = TotemAction.AMPLIFY_CURRENT),
                            ChoiceOption(ChoiceType.TOTEM, "Change weapon", "Recalibrate the Totem, then choose another weapon system you own.", "RECALIBRATE",
                                totemAction = TotemAction.CHANGE_WEAPON),
                        ),
                    )
                })
            },
            Scene("totem-directed") { w, h, l ->
                RewardShot(model {
                    rewardFixtureModel(
                        choiceType = ChoiceType.TOTEM, language = l, width = w, height = h, textScale = textScale, weaponLevel = 5, directedChoice = true,
                        coreShape = kinetickk.ball.content.api.CoreShape.PENTAGON,
                        choices = listOf(
                            ChoiceOption(ChoiceType.TOTEM, "Amplify Flux Wake", "Raise current weapon mastery by one level.", "AMPLIFY",
                                weaponId = WeaponId.FLUX_WAKE, totemAction = TotemAction.AMPLIFY_CURRENT),
                        ) + listOf(WeaponId.ARC_COIL, WeaponId.GRAVITY_MINES).map { id ->
                            val weapon = rewardFixtureContent.weapon(id)
                            ChoiceOption(ChoiceType.WEAPON, weapon.name, weapon.description, "CHANGE", weaponId = id)
                        },
                    )
                })
            },
            Scene("relic-acquire") { w, h, l ->
                RewardShot(model {
                    val offers = listOf(RelicId.AEGIS_INVERSION, RelicId.EVENT_HORIZON, RelicId.GYRE_ENGINE)
                    rewardFixtureModel(
                        choiceType = ChoiceType.RELIC, relics = relics3, language = l, width = w, height = h, textScale = textScale,
                        discoveredRelics = setOf(RelicId.PERPETUAL_VECTOR, RelicId.GYRE_ENGINE, RelicId.FURNACE_COVENANT, RelicId.AEGIS_INVERSION),
                        choices = offers.map { id ->
                            val relic = rewardFixtureContent.relic(id)
                            ChoiceOption(ChoiceType.RELIC, relic.name, relic.upside, relic.aspect.displayLabel.uppercase(),
                                relicId = id, relicAction = RelicChoiceAction.ACQUIRE)
                        },
                        previews = listOf(
                            RewardPreview(immutableListOf(RewardStatChange("Arc: hit, every 0.28s", 0f, 16f, "%", sourceRelic = RelicId.AEGIS_INVERSION))),
                            RewardPreview(immutableListOf(RewardStatChange("Damage: distance >300", 0f, 8f, "%", sourceRelic = RelicId.EVENT_HORIZON))),
                            RewardPreview(immutableListOf(RewardStatChange("Damage: dash", 24f, 48f, "", sourceRelic = RelicId.GYRE_ENGINE))),
                        ),
                    )
                })
            },
            Scene("relic-replace", hover = "kinetickk.gameplay.choice.4") { w, h, l ->
                RewardShot(model {
                    rewardFixtureModel(
                        choiceType = ChoiceType.RELIC_BIND, relics = relics4, language = l, width = w, height = h, textScale = textScale,
                        discoveredRelics = relics4.map { it.id }.toSet(),
                        choices = relics4.mapIndexed { index, equipped ->
                            val current = rewardFixtureContent.relic(equipped.id)
                            ChoiceOption(ChoiceType.RELIC_BIND, "Replace ${current.name}", "Break slot ${index + 1} and bind Periapsis Hook at rank 1.",
                                "SLOT ${index + 1} // REPLACE", relicId = RelicId.EVENT_HORIZON, relicAction = RelicChoiceAction.REPLACE, relicSlot = index)
                        },
                        previews = listOf(
                            RewardPreview(),
                            RewardPreview(),
                            RewardPreview(),
                            RewardPreview(immutableListOf(RewardStatChange("Damage: distance >300", 0f, 8f, "%", sourceRelic = RelicId.EVENT_HORIZON))),
                        ),
                    )
                })
            },
            Scene("relic-meld") { w, h, l ->
                RewardShot(model {
                    rewardFixtureModel(
                        choiceType = ChoiceType.RELIC_BIND, relics = relics3, language = l, width = w, height = h, textScale = textScale,
                        discoveredRelics = relics3.map { it.id }.toSet(),
                        choices = relics3.mapIndexed { index, equipped ->
                            val current = rewardFixtureContent.relic(equipped.id)
                            ChoiceOption(ChoiceType.RELIC_BIND, "Meld ${current.name}", "Collapse the offering into slot ${index + 1}.",
                                "SLOT ${index + 1} // RANK ${equipped.rank}", relicId = equipped.id, relicAction = RelicChoiceAction.MELD_TARGET, relicSlot = index)
                        },
                        previews = listOf(RewardPreview(immutableListOf(RewardStatChange("Damage: speed ≥500", 10f, 15f, "%", sourceRelic = RelicId.PERPETUAL_VECTOR)))),
                    )
                })
            },
            // Directed reward focus steps (after every resolved anomaly trial) with the game's strings.
            Scene("focus-items") { w, h, l ->
                RewardShot(model {
                    rewardFixtureModel(
                        choiceType = ChoiceType.ITEM, directedChoice = true, language = l, width = w, height = h, textScale = textScale,
                        choices = listOf(RewardFocus.OFFENSE, RewardFocus.DEFENSE, RewardFocus.ECONOMY).map { focus ->
                            ChoiceOption(ChoiceType.ITEM, focus.label, FocusDescription, "FOCUS", rewardFocus = focus)
                        },
                    )
                })
            },
            Scene("focus-relics") { w, h, l ->
                RewardShot(model {
                    rewardFixtureModel(
                        choiceType = ChoiceType.RELIC, directedChoice = true, relics = relics3, language = l, width = w, height = h, textScale = textScale,
                        choices = listOf(RewardFocus.KINETIC, RewardFocus.GRAVITIC, RewardFocus.ION).map { focus ->
                            ChoiceOption(ChoiceType.RELIC, focus.label, FocusDescription, "FOCUS", rewardFocus = focus)
                        },
                    )
                })
            },
            Scene("focus-weapons") { w, h, l ->
                RewardShot(model {
                    rewardFixtureModel(
                        choiceType = ChoiceType.TOTEM, directedChoice = true, language = l, width = w, height = h, textScale = textScale,
                        choices = listOf(RewardFocus.MOTION, RewardFocus.CONTROL, RewardFocus.STRIKE).map { focus ->
                            ChoiceOption(ChoiceType.TOTEM, focus.label, FocusDescription, "FOCUS", rewardFocus = focus)
                        },
                    )
                })
            },
            Scene("relic-offer-meld") { w, h, l ->
                RewardShot(model {
                    val offers = listOf(RelicId.EVENT_HORIZON, RelicId.ECHO_CHAMBER)
                    rewardFixtureModel(
                        choiceType = ChoiceType.RELIC, relics = relics4, language = l, width = w, height = h, textScale = textScale,
                        discoveredRelics = relics4.map { it.id }.toSet(),
                        choices = offers.map { id ->
                            val relic = rewardFixtureContent.relic(id)
                            ChoiceOption(ChoiceType.RELIC, relic.name, relic.upside, relic.aspect.displayLabel.uppercase(),
                                relicId = id, relicAction = RelicChoiceAction.ACQUIRE)
                        } + ChoiceOption(ChoiceType.RELIC, "Meld resonance",
                            "Collapse this offering into one of the four bound Relics and raise its rank.", "MELD", relicAction = RelicChoiceAction.MELD),
                    )
                })
            },
            Scene("pause") { w, h, l ->
                WorldShot(rewardFixtureModel(phase = GamePhase.PAUSED, relics = relics3, language = l, width = w, height = h, textScale = textScale))
            },
            // The widest slot level ("Lvl 10" / "Ур. 10") in the phone pause slot.
            Scene("pause-weapon10") { w, h, l ->
                WorldShot(rewardFixtureModel(phase = GamePhase.PAUSED, relics = relics3, language = l, width = w, height = h, textScale = textScale,
                    weaponLevel = 10))
            },
            Scene("report-victory") { w, h, l ->
                TerminalShot(rewardFixtureModel(phase = GamePhase.VICTORY, relics = relics4, language = l, width = w, height = h, textScale = textScale,
                    message = "ARCHITECT DISMANTLED", runMatter = 640L, totalMatter = 1_924L, kills = 1_412, elapsed = 846f, level = 22, weaponLevel = 10))
            },
            // The final tier's clear: the gold crown and the Ascendant line.
            Scene("report-victory-ascendant") { w, h, l ->
                TerminalShot(rewardFixtureModel(phase = GamePhase.VICTORY, relics = relics4, language = l, width = w, height = h, textScale = textScale,
                    message = "ARCHITECT DISMANTLED", runMatter = 640L, totalMatter = 1_924L, kills = 1_412, elapsed = 846f, level = 22, weaponLevel = 10,
                    rebirthLevel = 10))
            },
            Scene("report-defeat") { w, h, l ->
                TerminalShot(rewardFixtureModel(phase = GamePhase.GAME_OVER, relics = relics4, language = l, width = w, height = h, textScale = textScale,
                    message = "SINGULARITY CONTACT", runMatter = 640L, totalMatter = 1_284L, kills = 901, elapsed = 554f, level = 16, weaponLevel = 8))
            },
        )
    }
}

@Composable
private fun WorldShot(model: GameplayRenderModel) {
    val typography = rememberInterfaceTypography()
    val measurer = CanvasTextMeasurer(rememberTextMeasurer(), model.settings.textScale, LocalAppLanguage.current, typography, LocalKkRolePalette.current)
    Canvas(Modifier.fillMaxSize()) {
        val pause = if (model.phase == GamePhase.PAUSED) pauseLayoutGeometry(size.width, size.height, 1f) else null
        drawGameplay(model, VisualFxProjection.EMPTY, measurer, 1.3f, pause)
    }
}

@Composable
private fun RewardShot(model: GameplayRenderModel) {
    WorldShot(model)
    RewardContent(
        model,
        choiceLayoutGeometry(model.screenWidth, model.screenHeight, 1f, model.choices.size, model.choicesCanReroll),
        1.3f, true, {}, {}, {},
    )
}

@Composable
private fun TerminalShot(model: GameplayRenderModel) {
    WorldShot(model)
    TerminalContent(model, 4f, true) {}
}
