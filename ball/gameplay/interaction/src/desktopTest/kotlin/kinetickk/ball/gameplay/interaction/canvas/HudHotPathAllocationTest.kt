// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.ball.content.api.EquippedRelic
import kinetickk.ball.content.api.SiteKind
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.content.api.StructureTemplateId
import kinetickk.ball.gameplay.interaction.fx.BuildNoticeKind
import kinetickk.ball.gameplay.interaction.fx.BuildNotificationProjection
import kinetickk.ball.gameplay.nucleus.render.ApexProjection
import kinetickk.ball.gameplay.nucleus.render.BuildProjection
import kinetickk.ball.gameplay.nucleus.render.SeveredRelicProjection
import kinetickk.ball.gameplay.nucleus.render.SkillBarProjection
import kinetickk.ball.gameplay.nucleus.render.SkillSlotProjection
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.ObstacleKind
import kinetickk.ball.gameplay.nucleus.render.ObstacleProjection
import kinetickk.ball.gameplay.nucleus.render.PickupProjection
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kinetickk.ball.gameplay.nucleus.render.SiteProjection
import kinetickk.ball.gameplay.nucleus.render.TotemProjection
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.Kk
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertTrue

/** The running HUD draws every frame: once its caches are warm it must not allocate. */
class HudHotPathAllocationTest {
    @Test
    fun steadyHudFramesAllocateNothingBeyondTheEmptyFrame() {
        listOf(1_440 to 810, 844 to 390, 390 to 844).forEach { (width, height) ->
            val model = hudTestModel(width.toFloat(), height.toFloat()).with(
                "hp" to 30f, "maxHp" to 180f, "shield" to 20f, "maxShield" to 60f, "heat" to 100f, "overheated" to true,
                "combo" to 14, "comboTime" to 1.4f, "comboWindow" to 2.8f, "keys" to 2, "runMatter" to 12_345L,
                "overdriveTime" to 3f, "overdriveCharge" to 40f, "dashPhaseTime" to 0.1f, "velocityX" to 2_600f,
                *levelOverrides(), "polarityStability" to 0.2f, "message" to "ELITE SIGNAL", "messageTime" to 1f,
                // Relics that keep gauges (Stormheart charge, Stasis meter, Glass Comet's invulnerability), a
                // replaced relic whose cost lingers, Burnout, and an Architect carrying three statuses.
                "equippedRelics" to listOf(EquippedRelic(RelicId.entries[0], 1), EquippedRelic(RelicId.STORMHEART_CAPACITOR, 2),
                    EquippedRelic(RelicId.STASIS_ANCHOR, 1), EquippedRelic(RelicId.GLASS_COMET, 1)).toImmutableList(),
                "build" to BuildProjection(stormCharge = 0.6f, stasisMeter = 0.4f, stasisBank = 3, invulnerable = true, overdriveBurnout = true,
                    severed = immutableListOf(SeveredRelicProjection(RelicId.EVENT_HORIZON, 21f))),
                // Shifting into its second phase, hardened against Thermal (weak to Cryo).
                "enemies" to listOf(EnemyProjection(3, EnemyType.ARCHITECT, 200f, 0f, 0f, 0f, 400f, 1000f, 74f, 0f, 0f, 0f, 0f, 200f, 0f, false,
                    statusBits = 4 or (2 shl 12) or (6 shl 16) or (3 shl 20) or (2 shl 24) or EnemyStatusBits.FROZEN or
                        EnemyStatusBits.BRITTLE,
                    affinityBits = bossAffinity(EnemyType.ARCHITECT)),
                    EnemyProjection(5, EnemyType.ELITE, -500f, -300f, 0f, 0f, 400f, 1000f, 30f, 0f, 0f, 0f, 0f, -500f, -300f, false,
                        affinityBits = bossAffinity(EnemyType.ELITE)),
                    EnemyProjection(6, EnemyType.DRIFTER, 700f, 900f, 0f, 0f, 40f, 40f, 17f, 0f, 0f, 0f, 0f, 700f, 900f, false),
                    EnemyProjection(7, EnemyType.SWARMLING, -50f, 1_900f, 0f, 0f, 9f, 9f, 10f, 0f, 0f, 0f, 0f, -50f, 1_900f, false))
                    .toImmutableList(),
                "sites" to listOf(SiteProjection(SiteKind.COLLAPSING_ORBIT, "Collapsing orbit", 0f, 0f,
                    true, 12f, -1, 0.4f, immutableListOf(), 0f, 0f)).toImmutableList(),
                "trial" to siteTrial(SiteProjection(SiteKind.COLLAPSING_ORBIT, "Collapsing orbit", 0f, 0f,
                    true, 12f, -1, 0.4f, immutableListOf(), 0f, 0f)),
                // The minimap: every obstacle kind, a Key and a Relic past its rim, the totem, an Elite and ordinary enemies.
                "obstacles" to immutableListOf(
                    ObstacleProjection(1L, ObstacleKind.ASTEROID, 600f, 300f, 120f, 3),
                    ObstacleProjection(2L, ObstacleKind.UNSTABLE, -900f, 400f, 90f, 8),
                    ObstacleProjection(3L, ObstacleKind.BUMPER, 200f, -700f, 34f, 1),
                    ObstacleProjection(4L, ObstacleKind.ASTEROID, 9_000f, 0f, 120f, 5),
                ),
                "pickups" to immutableListOf(
                    PickupProjection(PickupType.KEY, 300f, 300f, 0f, 0f, 20f, 300f, 300f),
                    PickupProjection(PickupType.RELIC, 5_000f, -4_000f, 0f, 0f, 20f, 5_000f, -4_000f),
                    PickupProjection(PickupType.DATA, 100f, 100f, 0f, 0f, 20f, 100f, 100f),
                ),
                "totem" to TotemProjection(-1_400f, 800f, 1f),
                "minimapRange" to 2_200f,
                // Tier 10: the Rebirth strip with all ten mutator sigils and the four-part Architect bar.
                "rebirthLevel" to 10,
            )
            val fx = VisualFxProjection.EMPTY.copy(buildNotifications = immutableListOf(
                BuildNotificationProjection("Neon Ram", immutableListOf("Impact damage +0.05", "Weapon power +0.04", "+ Ram II"), 4f),
                BuildNotificationProjection("REACTION DISCOVERED", immutableListOf("+ Thermal Shock"), 5f, BuildNoticeKind.DISCOVERY),
            ))
            val measurer = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr), 1f,
                AppLanguage.Russian)
            val memory = HudPresentationMemory()
            // Values that change every frame: speed, draining polarity (its % label shows), falling
            // integrity (hit ghost + split channels), chain and orbit progress. Models are built up
            // front so the measured frames only draw.
            val frames = List(FRAMES) { frame ->
                val orbit = SiteProjection(SiteKind.COLLAPSING_ORBIT, "Collapsing orbit", 0f, 0f,
                    true, 12f, -1, frame / FRAMES.toFloat(), immutableListOf(), 0f, 0f)
                model.with(
                    "velocityX" to 300f + frame * 7.3f,
                    "polarityStability" to 0.24f - frame * 0.0005f,
                    "elapsed" to frame * GameplayRenderModel.FIXED_STEP,
                    "hp" to 36f - frame * 0.05f,
                    "combo" to 14 + frame,
                    "sites" to listOf(orbit).toImmutableList(),
                    "trial" to siteTrial(orbit),
                )
            }
            val bitmap = ImageBitmap(width, height)
            val canvas = Canvas(bitmap)
            val scope = CanvasDrawScope()
            val size = Size(width.toFloat(), height.toFloat())
            fun empty() = scope.draw(Density(1f), LayoutDirection.Ltr, canvas, size) { drawRect(Kk.Ink) }
            // Time stays inside one animation cycle so the frames only move shaders, pulses and alphas.
            // Toasts run down their life every frame (the reducer copies them, keeping their lines).
            val fxFrames = List(FRAMES) { frame ->
                fx.copy(buildNotifications = fx.buildNotifications.map { it.copy(life = it.life - frame * 0.001f) }.toImmutableList())
            }
            fun hud(frame: Int) = scope.draw(Density(1f), LayoutDirection.Ltr, canvas, size) {
                val current = frames[frame % FRAMES]
                drawHud(current, measurer, 10f + frame * 0.0001f, 0f, 0f, memory, trialInfoOpen = true)
                drawHudFeed(current, fxFrames[frame % FRAMES], measurer, 10f + frame * 0.0001f, memory)
                drawTrialTooltip(current, measurer, open = true)
            }
            // Warm every digit, label and path once, then measure frames whose values all differ.
            repeat(FRAMES) { empty(); hud(it) }
            val emptyBytes = allocated { repeat(FRAMES) { empty() } }
            val hudBytes = allocated { repeat(FRAMES) { hud(it) } }
            val perFrame = (hudBytes - emptyBytes) / FRAMES
            assertTrue(perFrame <= 64, "HUD frame at $width x $height allocates $perFrame bytes above an empty frame")
        }
    }

    /**
     * The level cluster at the last T5 level and in every Apex state at the cap: once warm, frames whose
     * foil, stripes, rays, glow and sheen move and whose Apex recharge or activation changes every frame
     * allocate nothing (one-shots aside, which only fade).
     */
    @Test
    fun levelClusterAtT5AndEveryApexStateAllocatesNothing() {
        listOf(1_440 to 810, 844 to 390, 390 to 844).forEach { (width, height) ->
            val base = hudTestModel(width.toFloat(), height.toFloat()).with("data" to 30, "nextLevelData" to 100)
            val scenarios = listOf<Pair<String, (Int) -> GameplayRenderModel>>(
                "T5" to { frame -> base.with(*levelOverrides(41), "elapsed" to frame * GameplayRenderModel.FIXED_STEP) },
                "cooling" to { frame ->
                    base.with(*levelOverrides(HUD_TEST_LEVEL_CAP, ApexProjection(unlocked = true, cooldown01 = 0.9f - frame * 0.001f)),
                        "elapsed" to frame * GameplayRenderModel.FIXED_STEP)
                },
                "ready" to { frame -> base.with(*levelOverrides(), "elapsed" to frame * GameplayRenderModel.FIXED_STEP) },
                "active" to { frame ->
                    base.with(*levelOverrides(HUD_TEST_LEVEL_CAP, ApexProjection(unlocked = true, active01 = 0.9f - frame * 0.001f)),
                        "elapsed" to frame * GameplayRenderModel.FIXED_STEP)
                },
            )
            val measurer = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr), 1f,
                AppLanguage.Russian)
            val bitmap = ImageBitmap(width, height)
            val canvas = Canvas(bitmap)
            val scope = CanvasDrawScope()
            val size = Size(width.toFloat(), height.toFloat())
            // One density and one draw block per scenario: until the JIT compiles the harness, a fresh
            // Density and a capturing lambda per frame would count against the HUD.
            val density = Density(1f)
            val clear: DrawScope.() -> Unit = { drawRect(Kk.Ink) }
            fun empty() = scope.draw(density, LayoutDirection.Ltr, canvas, size, clear)
            scenarios.forEach { (name, build) ->
                val frames = List(FRAMES) { build(it) }
                val memory = HudPresentationMemory()
                var current = 0
                // 0.8 s of presentation time: the foil, sheen, glow pulse and rays all move.
                val block: DrawScope.() -> Unit = {
                    drawHud(frames[current % FRAMES], measurer, 10f + (current % FRAMES) * 0.002f, 0f, 0f, memory)
                }
                fun hud(frame: Int) {
                    current = frame
                    scope.draw(density, LayoutDirection.Ltr, canvas, size, block)
                }
                repeat(FRAMES) { empty(); hud(it) }
                val emptyBytes = allocated { repeat(FRAMES) { empty() } }
                // The steady frame: the quietest of three full cycles. Each cycle replays the same
                // frames, so an allocation that recurs every frame or every cycle shows in all three;
                // a one-off burst while the JIT recompiles (seen after other HUD tests in the same JVM,
                // ≈ 27 KB once) shows in one only.
                val hudBytes = List(STEADY_CYCLES) { allocated { repeat(FRAMES) { hud(it) } } }.min()
                val perFrame = (hudBytes - emptyBytes) / FRAMES
                assertTrue(perFrame <= 64, "$name level cluster at $width x $height allocates $perFrame bytes above an empty frame")
            }
        }
    }

    /**
     * The skill row in every slot state — locked, ready with two charges at rank III, cooling with its sweep
     * moving, running, in flight, a cooling refusal shaking it with the Bone edge, the press flash held and the
     * key caps (keyboard in use) — and the Apex key cap at the cap: steady frames allocate nothing.
     */
    @Test
    fun skillBarInEveryStateWithFlashesAndKeyCapsAllocatesNothing() {
        listOf(1_440 to 810, 844 to 390, 390 to 844).forEach { (width, height) ->
            val base = hudTestModel(width.toFloat(), height.toFloat()).with(*levelOverrides())
            val frames = List(FRAMES) { frame ->
                val elapsed = 20f + frame * GameplayRenderModel.FIXED_STEP
                val bar = SkillBarProjection(immutableListOf(
                    SkillSlotProjection(skill = SkillId.STORM_COIL.ordinal, rank = 3, ready = true, charges = 2, maxCharges = 2),
                    // A cooling refusal 0.1 s ago, its sweep running down.
                    SkillSlotProjection(skill = SkillId.METEOR.ordinal, rank = 2, cooldown01 = 0.9f - frame * 0.001f, pending01 = 0.5f,
                        refusal = SkillSlotProjection.REFUSAL_COOLING, refusedAt = elapsed - 0.1f),
                    SkillSlotProjection(skill = SkillId.SOLAR_FLARE.ordinal, rank = 1, cooldown01 = 0.5f, active01 = 0.8f - frame * 0.001f),
                ))
                base.with("skillBar" to bar, "elapsed" to elapsed)
            }
            val locked = base.with("skillBar" to MIXED_SKILL_BAR)
            val measurer = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr), 1f,
                AppLanguage.Russian)
            val bitmap = ImageBitmap(width, height)
            val canvas = Canvas(bitmap)
            val scope = CanvasDrawScope()
            val size = Size(width.toFloat(), height.toFloat())
            val controls = keyboardControls()
            // One density and one draw block: the harness allocates nothing per frame itself.
            val density = Density(1f)
            val clear: DrawScope.() -> Unit = { drawRect(Kk.Ink) }
            fun empty() = scope.draw(density, LayoutDirection.Ltr, canvas, size, clear)
            val memory = HudPresentationMemory()
            var current = 0
            val block: DrawScope.() -> Unit = {
                val time = 10f + (current % FRAMES) * 0.002f
                // The X slot is pressed every frame: its Bone press flash stays on.
                controls.press(kinetickk.ball.gameplay.interaction.layout.RunningControlTarget.SKILL_2, time)
                drawHud(frames[current % FRAMES], measurer, time, 0f, 0f, memory, controls = controls)
                drawControls(locked, measurer, time, null, controls)
            }
            fun hud(frame: Int) {
                current = frame
                scope.draw(density, LayoutDirection.Ltr, canvas, size, block)
            }
            repeat(FRAMES) { empty(); hud(it) }
            kotlin.test.assertNotNull(HudLayoutProbe.rect(HudBlock.SKILLS), "the skill row is not drawn")
            val emptyBytes = allocated { repeat(FRAMES) { empty() } }
            val hudBytes = allocated { repeat(FRAMES) { hud(it) } }
            val perFrame = (hudBytes - emptyBytes) / FRAMES
            assertTrue(perFrame <= 64, "skill bar frame at $width x $height allocates $perFrame bytes above an empty frame")
        }
    }

    /**
     * On a landscape phone the chain (bone) and the draining polarity % (threat) are both drawn digit
     * by digit at the same size; with shared digits on screen (×12 while polarity drains from 21 %
     * to 10 %) neither may reshape the other's paragraphs, at the default text size and at 100 %.
     */
    @Test
    fun chainAndDrainingPolarityOnAPhoneShareNoDigitLayouts() {
        listOf(1.25f, 1f).forEach { textScale ->
            val model = hudTestModel(844f, 390f).with(
                "combo" to 12, "comboTime" to 1.4f, "comboWindow" to 2.8f, "pointerX" to 300f, "pointerY" to 190f,
                "polarityStability" to 0.21f,
            )
            val measurer = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr), textScale,
                AppLanguage.English)
            val memory = HudPresentationMemory()
            // 21 % down to 10 % every hundred frames (each step a visible drain).
            val frames = List(FRAMES) { frame ->
                model.with("polarityStability" to 0.215f - (frame % 100) * 0.0011f, "elapsed" to frame * GameplayRenderModel.FIXED_STEP)
            }
            val bitmap = ImageBitmap(844, 390)
            val canvas = Canvas(bitmap)
            val scope = CanvasDrawScope()
            val size = Size(844f, 390f)
            fun empty() = scope.draw(Density(1f), LayoutDirection.Ltr, canvas, size) { drawRect(Kk.Ink) }
            fun hud(frame: Int) = scope.draw(Density(1f), LayoutDirection.Ltr, canvas, size) {
                drawHud(frames[frame % FRAMES], measurer, 10f + frame * 0.0001f, 0f, 0f, memory)
            }
            repeat(FRAMES) { empty(); hud(it) }
            // Both numbers are on screen together.
            kotlin.test.assertNotNull(HudLayoutProbe.rect(HudBlock.POLARITY_LABEL), "the polarity % is not drawn")
            kotlin.test.assertNotNull(HudLayoutProbe.rect(HudBlock.CHAIN), "the chain is not drawn")
            val emptyBytes = allocated { repeat(FRAMES) { empty() } }
            val hudBytes = allocated { repeat(FRAMES) { hud(it) } }
            val perFrame = (hudBytes - emptyBytes) / FRAMES
            assertTrue(perFrame <= 64, "chain + polarity frame at x$textScale allocates $perFrame bytes above an empty frame")
        }
    }

    /**
     * The trial panel of every family (the anomalies, the gate race, the five challenges, the bounty and the sigil
     * duel) with its rules open: once warm, frames whose race clock ticks every frame, whose earned seconds and duel
     * integrity change every frame allocate nothing (a counted goal changes rarely and keeps its line).
     */
    @Test
    fun theTrialPanelOfEveryFamilyAllocatesNothingOnSteadyFrames() {
        listOf(1_440 to 810, 390 to 844).forEach { (width, height) ->
            val base = hudTestModel(width.toFloat(), height.toFloat())
            val measurer = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr), 1f,
                AppLanguage.Russian)
            val bitmap = ImageBitmap(width, height)
            val canvas = Canvas(bitmap)
            val scope = CanvasDrawScope()
            val size = Size(width.toFloat(), height.toFloat())
            val density = Density(1f)
            val clear: DrawScope.() -> Unit = { drawRect(Kk.Ink) }
            fun empty() = scope.draw(density, LayoutDirection.Ltr, canvas, size, clear)
            trialSites().forEach { site ->
                val counted = siteTrial(site).value == kinetickk.ball.gameplay.nucleus.render.TrialValue.COUNT
                val frames = List(FRAMES) { frame ->
                    val running = site.copy(
                        clock = site.clock + frame * GameplayRenderModel.FIXED_STEP,
                        progress = if (counted) site.progress else (site.progress + frame * 0.001f).coerceAtMost(1f),
                    )
                    base.with("elapsed" to frame * GameplayRenderModel.FIXED_STEP).withSites(running)
                }
                val memory = HudPresentationMemory()
                var current = 0
                val block: DrawScope.() -> Unit = {
                    val model = frames[current % FRAMES]
                    drawHud(model, measurer, 10f + (current % FRAMES) * 0.0001f, 0f, 0f, memory, trialInfoOpen = true)
                    drawTrialTooltip(model, measurer, open = true)
                }
                fun hud(frame: Int) {
                    current = frame
                    scope.draw(density, LayoutDirection.Ltr, canvas, size, block)
                }
                repeat(FRAMES) { empty(); hud(it) }
                kotlin.test.assertNotNull(HudLayoutProbe.rect(HudBlock.TRIAL_PANEL), "${site.kind}: no trial panel")
                val emptyBytes = allocated { repeat(FRAMES) { empty() } }
                val hudBytes = allocated { repeat(FRAMES) { hud(it) } }
                val perFrame = (hudBytes - emptyBytes) / FRAMES
                assertTrue(perFrame <= 64, "${site.kind} trial panel at $width x $height allocates $perFrame bytes above an empty frame")
            }
        }
    }

    /**
     * A Baby mode run's HUD at tier 0 (the pacifier plate alone in the Rebirth slot) and at tier 3 (the pacifier, then
     * the tier plate and its sigils), with critical integrity, low polarity and the cursor sweeping from contact range
     * (the soft solid tether) out past it (the soft pulsing tether): steady frames allocate nothing.
     */
    @Test
    fun babyModeHudFramesAllocateNothing() {
        listOf(1_440 to 810, 844 to 390, 390 to 844).forEach { (width, height) ->
            for (tier in listOf(0, 3)) {
                val base = hudTestModel(width.toFloat(), height.toFloat()).with(*levelOverrides(), "easyMode" to true, "rebirthLevel" to tier,
                    "coreX" to 0f, "coreY" to 0f, "cameraX" to 0f, "cameraY" to 0f, "hp" to 30f, "maxHp" to 180f, "polarityStability" to 0.2f)
                val frames = List(FRAMES) { frame ->
                    base.with("pointerX" to width * 0.5f + 40f + (frame % 100) * 1.6f, "pointerY" to height * 0.5f - 30f,
                        "velocityX" to 300f + frame * 7.3f, "elapsed" to frame * GameplayRenderModel.FIXED_STEP)
                }
                val measurer = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr), 1f,
                    AppLanguage.Russian)
                val bitmap = ImageBitmap(width, height)
                val canvas = Canvas(bitmap)
                val scope = CanvasDrawScope()
                val size = Size(width.toFloat(), height.toFloat())
                val density = Density(1f)
                val clear: DrawScope.() -> Unit = { drawRect(Kk.Ink) }
                fun empty() = scope.draw(density, LayoutDirection.Ltr, canvas, size, clear)
                val memory = HudPresentationMemory()
                var current = 0
                val block: DrawScope.() -> Unit = {
                    drawHud(frames[current % FRAMES], measurer, 10f + (current % FRAMES) * 0.002f, 0f, 0f, memory)
                }
                fun hud(frame: Int) {
                    current = frame
                    scope.draw(density, LayoutDirection.Ltr, canvas, size, block)
                }
                repeat(FRAMES) { empty(); hud(it) }
                kotlin.test.assertNotNull(HudLayoutProbe.rect(HudBlock.REBIRTH), "$width x $height tier $tier: no Baby mode plate")
                val emptyBytes = allocated { repeat(FRAMES) { empty() } }
                val hudBytes = List(STEADY_CYCLES) { allocated { repeat(FRAMES) { hud(it) } } }.min()
                val perFrame = (hudBytes - emptyBytes) / FRAMES
                assertTrue(perFrame <= 64, "Baby mode HUD at $width x $height tier $tier allocates $perFrame bytes above an empty frame")
            }
        }
    }

    /**
     * A tracked mini-boss (the Throne: narrow block, three parts) or secret boss (the Antecedent: wide block, four parts,
     * its weakness or anomaly glyph, the bone ring on its minimap pin) beside the other boss ranks: once warm, frames whose
     * integrity falls every frame and which cross a phase gate and shift allocate nothing.
     */
    @Test
    fun aTrackedMiniOrSecretBossAllocatesNothingOnSteadyFrames() {
        listOf(1_440 to 810, 844 to 390, 390 to 844).forEach { (width, height) ->
            val base = hudTestModel(width.toFloat(), height.toFloat())
            val measurer = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr), 1f, AppLanguage.Russian)
            val bitmap = ImageBitmap(width, height)
            val canvas = Canvas(bitmap)
            val scope = CanvasDrawScope()
            val size = Size(width.toFloat(), height.toFloat())
            val density = Density(1f)
            val clear: DrawScope.() -> Unit = { drawRect(Kk.Ink) }
            fun empty() = scope.draw(density, LayoutDirection.Ltr, canvas, size, clear)
            for (type in listOf(EnemyType.THRONE, EnemyType.ANTECEDENT)) for (aspected in listOf(false, true)) {
                val frames = List(FRAMES) { frame ->
                    // From 70 % down across the two-thirds gate, shifting for the frames after it, then into the next phase.
                    val hp = 700f - frame * 0.25f
                    val phase = if (hp > 2_000f / 3f) 0 else 1
                    val shifting = phase == 1 && frame % 200 < 120
                    val aspect = if (aspected) kinetickk.ball.content.api.StatusElement.THERMAL.ordinal else kinetickk.ball.gameplay.nucleus.render.EnemyAffinity.NONE
                    val weakness = if (aspected) kinetickk.ball.content.api.StatusElement.CRYO.ordinal else kinetickk.ball.gameplay.nucleus.render.EnemyAffinity.NONE
                    val affinity = kinetickk.ball.gameplay.nucleus.render.EnemyAffinity.encode(aspect, false, weakness, armored = true, fragile = false,
                        shifting = shifting, phase = phase)
                    base.with(
                        "elapsed" to frame * GameplayRenderModel.FIXED_STEP,
                        "enemies" to listOf(
                            EnemyProjection(3, type, base.coreX + 600f, base.coreY - 300f, 0f, 0f, hp, 1_000f, 48f, 0f, 0f, 0f, 0f,
                                base.coreX + 600f, base.coreY - 300f, false, affinityBits = affinity),
                            EnemyProjection(4, EnemyType.CARTOGRAPHER, base.coreX - 900f, base.coreY + 400f, 0f, 0f, 900f, 1_000f, 40f, 0f, 0f,
                                0f, 0f, base.coreX - 900f, base.coreY + 400f, false),
                            EnemyProjection(5, EnemyType.WARDEN_PRIME, base.coreX + 9_000f, base.coreY, 0f, 0f, 900f, 1_000f, 40f, 0f, 0f,
                                0f, 0f, base.coreX + 9_000f, base.coreY, false),
                            EnemyProjection(6, EnemyType.ELITE, base.coreX - 500f, base.coreY - 300f, 0f, 0f, 400f, 1_000f, 30f, 0f, 0f, 0f, 0f,
                                base.coreX - 500f, base.coreY - 300f, false, affinityBits = bossAffinity(EnemyType.ELITE)),
                            EnemyProjection(7, EnemyType.COMET, base.coreX + 300f, base.coreY + 200f, 0f, 0f, 40f, 40f, 17f, 0f, 0f, 0f, 0f,
                                base.coreX + 300f, base.coreY + 200f, false),
                        ).toImmutableList(),
                        "minimapRange" to 2_200f,
                    )
                }
                val memory = HudPresentationMemory()
                var current = 0
                val block: DrawScope.() -> Unit = {
                    drawHud(frames[current % FRAMES], measurer, 10f + (current % FRAMES) * 0.0001f, 0f, 0f, memory)
                }
                fun hud(frame: Int) {
                    current = frame
                    scope.draw(density, LayoutDirection.Ltr, canvas, size, block)
                }
                repeat(FRAMES) { empty(); hud(it) }
                kotlin.test.assertNotNull(HudLayoutProbe.rect(HudBlock.BOSS), "$type: no boss block")
                val emptyBytes = allocated { repeat(FRAMES) { empty() } }
                val hudBytes = allocated { repeat(FRAMES) { hud(it) } }
                val perFrame = (hudBytes - emptyBytes) / FRAMES
                assertTrue(perFrame <= 64, "$type (aspect $aspected) at $width x $height allocates $perFrame bytes above an empty frame")
            }
        }
    }

    /**
     * The minimap with structures: a sealed furnace's walls and threat doors around the Core, a Vault's locked doors and
     * an entrance pinned to the rim, the Core drifting so every line moves each frame: once warm, nothing is allocated.
     */
    @Test
    fun minimapStructuresAllocateNothingOnSteadyFrames() {
        listOf(1_440 to 810, 390 to 844).forEach { (width, height) ->
            val base = hudTestModel(width.toFloat(), height.toFloat()).with("minimapRange" to 2_400f)
            val templates = base.content.structures
            val foundry = StructureFixture(templates.template(StructureTemplateId.FOUNDRY), base.coreX - 900f, base.coreY,
                clearedRooms = 0b011, engagedRoom = 2)
            val vault = StructureFixture(templates.template(StructureTemplateId.VAULT), base.coreX + 1_800f, base.coreY - 600f, id = 9L shl 8)
            val far = StructureFixture(templates.template(StructureTemplateId.LABYRINTH), base.coreX + 9_000f, base.coreY + 4_000f,
                id = 10L shl 8)
            val structures = structureProjection(foundry, vault, signals = listOf(vault, far), dungeon = foundry, garrisonAlive = 4,
                garrisonTotal = 6)
            val frames = List(FRAMES) { frame ->
                base.with("structures" to structures, "elapsed" to frame * GameplayRenderModel.FIXED_STEP,
                    "coreX" to base.coreX + frame * 0.37f, "coreY" to base.coreY - frame * 0.21f)
            }
            val measurer = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr), 1f,
                AppLanguage.Russian)
            val bitmap = ImageBitmap(width, height)
            val canvas = Canvas(bitmap)
            val scope = CanvasDrawScope()
            val size = Size(width.toFloat(), height.toFloat())
            val memory = HudPresentationMemory()
            fun empty() = scope.draw(Density(1f), LayoutDirection.Ltr, canvas, size) { drawRect(Kk.Ink) }
            fun hud(frame: Int) = scope.draw(Density(1f), LayoutDirection.Ltr, canvas, size) {
                drawHud(frames[frame % FRAMES], measurer, 10f + frame * 0.0001f, 0f, 0f, memory)
            }
            repeat(FRAMES) { empty(); hud(it) }
            kotlin.test.assertNotNull(HudLayoutProbe.rect(HudBlock.MINIMAP), "no minimap at $width x $height")
            val emptyBytes = allocated { repeat(FRAMES) { empty() } }
            val hudBytes = allocated { repeat(FRAMES) { hud(it) } }
            val perFrame = (hudBytes - emptyBytes) / FRAMES
            assertTrue(perFrame <= 64, "Minimap structures at $width x $height allocate $perFrame bytes above an empty frame")
        }
    }

    /**
     * The dungeon panel of a station with two rooms clear and a third sealed (its cell pulsing, the reward row joined
     * from the template's labels, the (!) open) and the vignette, the Core walking deeper into the footprint so the
     * vignette's depth changes every frame: once warm, nothing is allocated.
     */
    @Test
    fun aDungeonPanelAndItsVignetteAllocateNothingOnSteadyFrames() {
        listOf(1_440 to 810, 844 to 390, 390 to 844).forEach { (width, height) ->
            val base = hudTestModel(width.toFloat(), height.toFloat())
            val template = base.content.structures.template(StructureTemplateId.DERELICT_STATION)
            // The Core 60 u inside the footprint's east edge, walking in: the vignette fades in over the frames.
            val station = StructureFixture(template, base.coreX + 60f - template.halfWidth, base.coreY, clearedRooms = 0b0011, engagedRoom = 2)
            val held = base.withDungeon(station)
            val frames = List(FRAMES) { frame ->
                val x = base.coreX - frame * 0.5f
                held.with("elapsed" to frame * GameplayRenderModel.FIXED_STEP, "coreX" to x, "previousCoreX" to x)
            }
            check(structureInteriorAmount(frames.first()) < structureInteriorAmount(frames.last())) { "the vignette deepens" }
            val measurer = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr), 1f,
                AppLanguage.Russian)
            val bitmap = ImageBitmap(width, height)
            val canvas = Canvas(bitmap)
            val scope = CanvasDrawScope()
            val size = Size(width.toFloat(), height.toFloat())
            val memory = HudPresentationMemory()
            fun empty() = scope.draw(Density(1f), LayoutDirection.Ltr, canvas, size) { drawRect(Kk.Ink) }
            fun hud(frame: Int) = scope.draw(Density(1f), LayoutDirection.Ltr, canvas, size) {
                val model = frames[frame % FRAMES]
                drawHud(model, measurer, 10f + frame * 0.0001f, 0f, 0f, memory, trialInfoOpen = true)
                drawTrialTooltip(model, measurer, open = true)
            }
            repeat(FRAMES) { empty(); hud(it) }
            kotlin.test.assertNotNull(HudLayoutProbe.rect(HudBlock.TRIAL_PANEL), "no dungeon panel at $width x $height")
            val emptyBytes = allocated { repeat(FRAMES) { empty() } }
            val hudBytes = allocated { repeat(FRAMES) { hud(it) } }
            val perFrame = (hudBytes - emptyBytes) / FRAMES
            assertTrue(perFrame <= 64, "The dungeon panel and vignette at $width x $height allocate $perFrame bytes above an empty frame")
        }
    }

    private inline fun allocated(block: () -> Unit): Long {
        val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        val id = Thread.currentThread().id
        val before = threads.getThreadAllocatedBytes(id)
        block()
        return threads.getThreadAllocatedBytes(id) - before
    }

    private companion object {
        const val FRAMES = 400
        const val STEADY_CYCLES = 3
    }
}
