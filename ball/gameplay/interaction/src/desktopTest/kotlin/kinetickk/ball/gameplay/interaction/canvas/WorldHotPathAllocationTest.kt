// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import kinetickk.ball.gameplay.interaction.fixtureWeapon
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.text.TextMeasurer as ComposeTextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.KINETICKK_CONTENT_VERSION
import kinetickk.ball.content.api.SiteKind
import kinetickk.ball.content.api.SignalKind
import kinetickk.ball.content.api.EliteAffix
import kinetickk.ball.content.api.WorldEventKind
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.content.api.RebirthPolicySnapshot
import kinetickk.ball.content.api.RebirthProfile
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicPolicy
import kinetickk.ball.content.api.StatDelta
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.StructureTemplateId
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.WeaponMastery
import kinetickk.ball.gameplay.interaction.fx.DamageNumberProjection
import kinetickk.ball.gameplay.interaction.fx.ElementBurstProjection
import kinetickk.ball.gameplay.interaction.fx.ReactionStampProjection
import kinetickk.ball.gameplay.interaction.rewards.rewardFixtureRelics
import kinetickk.ball.gameplay.interaction.fx.MotionEchoProjection
import kinetickk.ball.gameplay.interaction.fx.ParticleProjection
import kinetickk.ball.gameplay.interaction.fx.ShockwaveProjection
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.interaction.fx.WeaponArcProjection
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.gameplay.interaction.fx.ApexBurstProjection
import kinetickk.ball.gameplay.interaction.fx.InteractionFxLimits
import kinetickk.ball.gameplay.interaction.fx.ReactionFxSpecs
import kinetickk.ball.gameplay.interaction.fx.SkillBurstProjection
import kinetickk.ball.gameplay.interaction.fx.ribbonLength
import kinetickk.ball.gameplay.nucleus.protocol.LinkStyleCode
import kinetickk.ball.gameplay.nucleus.protocol.ReactionCueFlags
import kinetickk.ball.gameplay.nucleus.render.ApexProjection
import kinetickk.ball.gameplay.nucleus.render.CombatFieldProjection
import kinetickk.ball.gameplay.nucleus.render.CombatFieldSource
import kinetickk.ball.gameplay.nucleus.render.CombatFieldVisual
import kinetickk.ball.gameplay.nucleus.render.CoreStateProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyControlBits
import kinetickk.ball.gameplay.nucleus.render.SkillBarProjection
import kinetickk.ball.gameplay.nucleus.render.SkillSlotProjection
import kinetickk.ball.gameplay.nucleus.render.CharacterAbilityProjection
import kinetickk.ball.gameplay.nucleus.render.CharacterDroneProjection
import kinetickk.ball.gameplay.nucleus.render.CharacterLatticePoint
import kinetickk.ball.gameplay.nucleus.render.CharacterScarProjection
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.EnemyProjection
import kinetickk.ball.gameplay.nucleus.render.ObstacleKind
import kinetickk.ball.gameplay.nucleus.render.ObstacleProjection
import kinetickk.ball.content.api.ObstacleKindTuning
import kinetickk.ball.content.api.phaseShardSecondsToSolid
import kinetickk.ball.content.api.phaseShardSolid
import kinetickk.ball.content.api.ventErupting
import kinetickk.ball.content.api.ventSecondsToEruption
import kinetickk.ball.gameplay.nucleus.render.ProjectileKind
import kinetickk.ball.gameplay.nucleus.render.EnemyAffinity
import kinetickk.ball.gameplay.nucleus.render.EnemyBehavior
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.EventSignalProjection
import kinetickk.ball.gameplay.nucleus.render.EventStrikeProjection
import kinetickk.ball.gameplay.nucleus.render.WorldEventProjection
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.PickupProjection
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kinetickk.ball.gameplay.nucleus.render.PortalProjection
import kinetickk.ball.gameplay.nucleus.render.SiteMarkProjection
import kinetickk.ball.gameplay.nucleus.render.SiteProjection
import kinetickk.ball.gameplay.nucleus.render.ProjectileProjection
import kinetickk.ball.gameplay.nucleus.render.TotemProjection
import kinetickk.ball.gameplay.nucleus.render.TrailPointProjection
import kinetickk.ball.gameplay.nucleus.render.WeaponEntityKind
import kinetickk.ball.gameplay.nucleus.render.WeaponEntityProjection
import kinetickk.ball.gameplay.nucleus.render.WeaponNodeProjection
import kinetickk.ball.gameplay.nucleus.render.WeaponOrbitalProjection
import kinetickk.ball.gameplay.nucleus.render.WeaponNodeType
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.immutableSetOf
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.monoStyle
import kotlin.test.assertFalse
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.math.cos
import kotlin.math.sin

/**
 * The world layer runs every frame: once caches are warm (paths, strokes, brushes, strings, text
 * layouts), a busy frame allocates no JVM heap beyond the draw scope's own baseline. Also checks
 * that the world keeps its key colors through the Color vision palette.
 *
 * The per-character and per-weapon sweeps warm one pass over the warm frames and measure 20 fresh
 * frames per pass (the busy frame 100); `KK_FULL_SWEEPS=1` restores the longer runs (60 to 300
 * measured frames).
 */
class WorldHotPathAllocationTest {
    private val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean

    @Test
    fun busyWorldFrameDoesNotAllocate() {
        val warmFrames = if (FULL_SWEEP) 200 else 111
        val measuredFrames = if (FULL_SWEEP) 300 else 100
        KkRolePalette.Default.let { roles -> assertFrameIsAllocationFree(roles, WeaponId.MORNINGSTAR, warmFrames = warmFrames, measuredFrames = measuredFrames) }
        assertFrameIsAllocationFree(KkRolePalette.Mono, WeaponId.ARC_COIL, warmFrames = warmFrames, measuredFrames = measuredFrames)
    }

    @Test
    fun everyReactionFieldGlyphCoreStateSkillApexAndLinkLookDrawsWithoutAllocating() {
        // Every reaction look (mixed progress, forms and weights), every field kind, every control glyph and
        // mark, every Core state, skill and Apex looks with the aura, and styled links: on a packed screen
        // (120 enemies, signature layers only) and on an open one (40 enemies, every layer and glyph).
        for (crowd in listOf(120, 40)) {
            assertFrameIsAllocationFree(KkRolePalette.Default, WeaponId.ARC_COIL, warmFrames = WARM_MODELS, measuredFrames = 20,
                fx = reactionFx(), decorate = { reactionWorld(it, crowd) })
        }
        assertFrameIsAllocationFree(KkRolePalette.Mono, WeaponId.ARC_COIL, CoreShape.PENTAGON, warmFrames = WARM_MODELS, measuredFrames = 20,
            fx = reactionFx(), decorate = { reactionWorld(it, 40) })
    }

    @Test
    fun everyCharacterDrawsItsCoreAndMechanicWithoutAllocating() {
        // Each form's silhouette, marks and world visuals (halo band, ghost, drones, scars, polarity ring,
        // beat pulse, facet ring, nova glow, lattice) on a busy frame.
        for (shape in CoreShape.entries) {
            assertFrameIsAllocationFree(KkRolePalette.Default, WeaponId.MORNINGSTAR, shape, warmFrames = if (FULL_SWEEP) 80 else WARM_MODELS,
                measuredFrames = if (FULL_SWEEP) 60 else 20)
        }
    }

    @Test
    fun everyWeaponDrawsItsWorldVisualsAndEntitiesWithoutAllocating() {
        // Each weapon's Core marks and orbitals at its third form, over every kind of weapon entity at once.
        for (weapon in WeaponId.entries) {
            assertFrameIsAllocationFree(KkRolePalette.Default, weapon, warmFrames = if (FULL_SWEEP) 60 else WARM_MODELS,
                measuredFrames = if (FULL_SWEEP) 60 else 20, level = 10)
        }
    }

    @Test
    fun coreRibbonWithDashGhostsDrawsWithoutAllocating() {
        // busyFx's curved ribbon in a Dash: tapered segments, rails over the curve and the three ghosts on the path.
        check(ribbonLength(busyModel(WeaponId.MORNINGSTAR, dash = 0.12f).speed) > 132f) { "the busy speed draws every ghost" }
        for (roles in listOf(KkRolePalette.Default, KkRolePalette.Mono)) {
            assertFrameIsAllocationFree(roles, WeaponId.MORNINGSTAR, warmFrames = WARM_MODELS, measuredFrames = 20, dash = 0.12f)
        }
    }

    @Test
    fun siteMarksGlyphsAndMarkersOfEveryFamilyDrawWithoutAllocating() {
        assertFrameIsAllocationFree(KkRolePalette.Default, WeaponId.MORNINGSTAR, warmFrames = WARM_MODELS * 2, measuredFrames = 20,
            decorate = ::withSitesOfEveryFamily)
        assertFrameIsAllocationFree(KkRolePalette.Mono, WeaponId.ARC_COIL, warmFrames = WARM_MODELS * 2, measuredFrames = 20,
            decorate = ::withSitesOfEveryFamily)
    }

    @Test
    fun aRunningRaceCourseDrawsWithoutAllocating() {
        // Seven gates (passed, next with its pulsing bar, later, the doubled finish) with their order pips, and the
        // same race with its next gate past the edge (its marker instead).
        assertFrameIsAllocationFree(KkRolePalette.Default, WeaponId.MORNINGSTAR, warmFrames = WARM_MODELS * 2, measuredFrames = 20,
            decorate = { withRunningRace(it, step = 2) })
        assertFrameIsAllocationFree(KkRolePalette.Mono, WeaponId.ARC_COIL, warmFrames = WARM_MODELS * 2, measuredFrames = 20,
            decorate = { withRunningRace(it, step = 6) })
    }

    @Test
    fun runningChallengesDrawWithoutAllocating() {
        // Every challenge's ring or course at once (the brakeless ring and arc, the Ram rite's ring and bracketed foes,
        // the untouched ring with a telegraphed volley, the velocity zone and its progress ring, the slalom's flagged
        // poles), with the slalom's next pole on screen and then past the edge (its marker instead).
        assertFrameIsAllocationFree(KkRolePalette.Default, WeaponId.MORNINGSTAR, warmFrames = WARM_MODELS * 2, measuredFrames = 20,
            decorate = { withRunningChallenges(it, slalomStep = 1) })
        assertFrameIsAllocationFree(KkRolePalette.Mono, WeaponId.ARC_COIL, warmFrames = WARM_MODELS * 2, measuredFrames = 20,
            decorate = { withRunningChallenges(it, slalomStep = 5) })
    }

    @Test
    fun worldEventsSignalsAffixesAndChampionsDrawWithoutAllocating() {
        // Every world event in progress over the busy frame (the meteor's and the ion storm's four strikes filling, the
        // flare's band mid-sweep, the storms' motes drifting with a turning wind), a live signal with its life arc and name,
        // six affixed Elites and four champions; the event clock moves with the measured frames. Then the meteor on the
        // mono palette with the signal past the edge (its marker instead).
        for (kind in WorldEventKind.entries) {
            assertFrameIsAllocationFree(KkRolePalette.Default, WeaponId.MORNINGSTAR, warmFrames = WARM_MODELS * 2, measuredFrames = 20,
                decorate = { withRunVariety(it, kind, signalOnScreen = true) })
        }
        assertFrameIsAllocationFree(KkRolePalette.Mono, WeaponId.ARC_COIL, warmFrames = WARM_MODELS * 2, measuredFrames = 20,
            decorate = { withRunVariety(it, WorldEventKind.METEOR_SHOWER, signalOnScreen = false) })
    }

    @Test
    fun ambientCachesDrawWithoutAllocating() {
        // Drift caches bobbing in view, one past the edge within its reveal distance (its marker instead) and one past
        // the edge beyond it (nothing).
        assertFrameIsAllocationFree(KkRolePalette.Default, WeaponId.MORNINGSTAR, warmFrames = WARM_MODELS * 2, measuredFrames = 20,
            decorate = ::withAmbientCaches)
        assertFrameIsAllocationFree(KkRolePalette.Mono, WeaponId.ARC_COIL, warmFrames = WARM_MODELS * 2, measuredFrames = 20,
            decorate = ::withAmbientCaches)
    }

    @Test
    fun structuresWithASealedRoomTheScrimAndEntranceMarkersDrawWithoutAllocating() {
        // A Foundry's furnace sealed around the Core (threat doors and brackets, its promise plates, cleared halls, the
        // scrim at full depth), an unspent Spire beside it with open doors, and two entrances off screen (their markers).
        assertFrameIsAllocationFree(KkRolePalette.Default, WeaponId.MORNINGSTAR, warmFrames = WARM_MODELS * 2, measuredFrames = 20,
            decorate = ::withStructures)
        assertFrameIsAllocationFree(KkRolePalette.Mono, WeaponId.ARC_COIL, warmFrames = WARM_MODELS * 2, measuredFrames = 20,
            decorate = ::withStructures)
    }

    @Test
    fun worldColorsFollowTheRolePalette() {
        val model = busyModel(WeaponId.ARC_COIL)
        val fx = busyFx()
        listOf(KkRolePalette.Default, KkRolePalette.Protan).forEach { roles ->
            val pixels = render(model, fx, roles)
            fun count(color: androidx.compose.ui.graphics.Color): Int {
                val target = color.toArgb()
                return pixels.count { it == target }
            }
            assertTrue(count(roles.threat) > 400, "Enemies, bullets and the singularity use the threat role")
            assertTrue(count(roles.you) > 150, "Trail, weapon and progress use the you role")
            // The Core alone (Kinetic damage numbers take the preset's volt, counted with the you role above).
            assertTrue(count(Kk.Bone) > 300, "The Core body is bone (${count(Kk.Bone)} px)")
            val other = if (roles == KkRolePalette.Default) KkRolePalette.Protan else KkRolePalette.Default
            assertTrue(count(other.threat) < 20, "No hard-coded threat color leaks into the world")
        }
    }

    @Test
    fun babyModeWorldFrameDrawsWithoutAllocating() {
        // The busy frame of a Baby mode run: the soft cursor with its pacifier wings on screen, the diapered Core, the
        // pastel halftone and the pastel ribbon in a Dash.
        for (roles in listOf(KkRolePalette.Default, KkRolePalette.Mono)) {
            assertFrameIsAllocationFree(roles, WeaponId.MORNINGSTAR, warmFrames = WARM_MODELS, measuredFrames = 20, dash = 0.12f, easy = true)
        }
    }

    @Test
    fun babyModeCursorUsesTheSoftRoleAndNoThreat() {
        // A quiet frame (no enemies, shots, fields, sites or marks) with the cursor 140 u from the Core: Baby mode's
        // cursor is the soft role and nothing in the world is threat; the standard cursor on the same frame is threat.
        listOf(KkRolePalette.Default, KkRolePalette.Protan).forEach { roles ->
            fun IntArray.count(color: androidx.compose.ui.graphics.Color): Int {
                val target = color.toArgb()
                return count { it == target }
            }
            val easy = render(quietModel(easy = true), VisualFxProjection.EMPTY, roles)
            val standard = render(quietModel(easy = false), VisualFxProjection.EMPTY, roles)
            assertTrue(easy.count(roles.soft) > 60, "$roles: the Baby mode cursor is soft (${easy.count(roles.soft)} px)")
            assertTrue(easy.count(roles.threat) < 20, "$roles: no threat in a quiet Baby mode world (${easy.count(roles.threat)} px)")
            assertTrue(standard.count(roles.threat) > 60, "$roles: the standard cursor is threat (${standard.count(roles.threat)} px)")
            assertTrue(standard.count(roles.soft) < 20, "$roles: the soft role is Baby mode's alone (${standard.count(roles.soft)} px)")
        }
    }

    @Test
    fun easterEggsDrawWithoutAllocating() {
        // The Lost Cursor drifting and the Quiet Signal rippling in view, a Quiet Signal past the edge within its reveal
        // distance (its marker instead), the found Lost Cursor circling the Core and the Golden Drifter's glint.
        assertFrameIsAllocationFree(KkRolePalette.Default, WeaponId.MORNINGSTAR, warmFrames = WARM_MODELS * 2, measuredFrames = 20,
            decorate = ::withEasterEggs)
        assertFrameIsAllocationFree(KkRolePalette.Mono, WeaponId.ARC_COIL, warmFrames = WARM_MODELS * 2, measuredFrames = 20,
            decorate = ::withEasterEggs)
    }

    @Test
    fun aRunningBountyArenaDrawsWithoutAllocating() {
        // The bounty's threat edge and faint exit band crossing the screen with its bracketed Warden Prime, then the arena
        // centred farther off (only arcs of both rings in view) on the mono palette.
        assertFrameIsAllocationFree(KkRolePalette.Default, WeaponId.MORNINGSTAR, warmFrames = WARM_MODELS * 2, measuredFrames = 20,
            decorate = { withRunningBounty(it, centerDx = -520f) })
        assertFrameIsAllocationFree(KkRolePalette.Mono, WeaponId.ARC_COIL, warmFrames = WARM_MODELS * 2, measuredFrames = 20,
            decorate = { withRunningBounty(it, centerDx = -1_150f) })
    }

    @Test
    fun everyDimensionLayerDrawsWithoutAllocating() {
        // Each layer's ground, grid, halftone and motes (the Zenith's light wedges, the Nadir's cracks, the Archive's
        // blinking squares and scanline) under its marks: eight rings (the next one at pace, then below it on the mono
        // palette, and the one after it), twenty-four embers, the exit rift, a glitch rift off screen (its marker) and an
        // open and a locked window rift.
        for (layer in listOf(WorldLayer.ZENITH, WorldLayer.NADIR, WorldLayer.ARCHIVE)) {
            assertFrameIsAllocationFree(KkRolePalette.Default, WeaponId.MORNINGSTAR, warmFrames = WARM_MODELS * 2, measuredFrames = 20,
                decorate = { inDimension(it, layer, slow = false) })
            assertFrameIsAllocationFree(KkRolePalette.Mono, WeaponId.ARC_COIL, warmFrames = WARM_MODELS * 2, measuredFrames = 20,
                decorate = { inDimension(it, layer, slow = true) })
        }
    }

    private fun assertFrameIsAllocationFree(
        roles: KkRolePalette,
        weapon: WeaponId,
        shape: CoreShape = CoreShape.TESSERACT,
        warmFrames: Int,
        measuredFrames: Int,
        level: Int = 3,
        fx: VisualFxProjection = busyFx(),
        decorate: (GameplayRenderModel) -> GameplayRenderModel = { it },
        dash: Float = 0f,
        easy: Boolean = false,
    ) {
        // The off-screen totem and sealed-anomaly offer move every frame, so their edge-marker
        // distances change every frame: they must be drawn from cached digit layouts, never
        // measured as text. Warm-up frames draw distances from a range the measured frames never
        // use, and every measured frame draws two distances no earlier frame drew (a measuring
        // pass never repeats an earlier pass's), so measuring a distance string would allocate.
        val warm = Array(WARM_MODELS) { frame ->
            decorate(busyModel(weapon, drift = WARM_DRIFT + frame * 23f, shape = shape, level = level, dash = dash, easy = easy))
        }
        fun measured(pass: Int) = Array(measuredFrames) { frame ->
            decorate(busyModel(weapon, drift = (pass * measuredFrames + frame) * 20f, shape = shape, level = level, dash = dash, easy = easy))
        }.also { measured ->
            check(measured.map { roundedDistance(edgeDistance(it)) }.toSet().size == measuredFrames)
            check(measured.maxOf(::edgeDistance) < warm.minOf(::edgeDistance))
        }
        val measurer = measurer(roles)
        val bitmap = ImageBitmap(WIDTH, HEIGHT)
        val scope = CanvasDrawScope()
        val canvas = Canvas(bitmap)
        val thread = Thread.currentThread().id
        fun bytesPerFrame(passes: Int, block: DrawScope.(GameplayRenderModel) -> Unit, accept: (Long) -> Boolean): Long {
            fun frame(model: GameplayRenderModel) =
                scope.draw(Density(1f), LayoutDirection.Ltr, canvas, Size(WIDTH.toFloat(), HEIGHT.toFloat())) { block(model) }
            repeat(warmFrames) { frame(warm[it % warm.size]) }
            // A short pass can catch a stray one-off allocation, so a failing pass is measured again on fresh
            // frames; an allocation the frames make on every pass fails all of them.
            var best = Long.MAX_VALUE
            for (pass in 0 until passes) {
                val models = measured(pass)
                val before = threads.getThreadAllocatedBytes(thread)
                models.forEach { frame(it) }
                best = minOf(best, (threads.getThreadAllocatedBytes(thread) - before) / measuredFrames)
                if (accept(best)) break
            }
            return best
        }
        val baseline = bytesPerFrame(1, { }) { true }
        val world = bytesPerFrame(minOf(PASSES, MEASURED_DRIFT_FRAMES / measuredFrames), { model ->
            drawBackdrop(model, 1.5f, -1f, 2f, roles)
            drawWorld(model, fx, 1.5f, -1f, measurer)
            drawScreenFx(model, 2f, roles)
        }) { it - baseline < 96 }
        assertTrue(world - baseline < 96, "A busy $shape world frame allocates ${world - baseline} bytes above the $baseline byte baseline")
    }

    /**
     * The busy frame inside [layer]: the next course ring and the one after it on screen, six ambient rings (one far off),
     * twenty-four embers around the Core, the exit rift on screen, the glitch rift off screen, and an open and a locked
     * window rift ahead of the busy frame's sites; [slow] puts the layer's pace above the Core's speed.
     */
    private fun inDimension(model: GameplayRenderModel, layer: WorldLayer, slow: Boolean): GameplayRenderModel {
        val embers = List(24) { index ->
            val angle = index * 0.26f
            val reach = 150f + (index % 4) * 40f
            (reach * cos(angle)) to (reach * sin(angle))
        }
        val visit = dimensionVisit(layer, CORE, CORE, course = listOf(260f to -60f, -240f to 200f),
            ambient = listOf(-380f to -180f, 400f to 230f, 60f to 250f, -60f to -260f, 330f to -230f, 2_400f to 900f), embers = embers,
            pace = if (slow) 10_000f else 1f, completed = false, localClock = 4.2f)
        val dimension = visit.copy(
            exitPortal = PortalProjection(1, CORE + 300f, CORE + 160f, WorldLayer.BASE, locked = false, glitch = false, remaining = 30f),
            glitchPortal = PortalProjection(2, CORE - 2_100f, CORE + 500f, WorldLayer.ARCHIVE, locked = false, glitch = true, remaining = 30f),
            lockedRifts = WorldLayer.NADIR.bit,
        )
        fun rift(kind: SiteKind, x: Float, y: Float) = SiteProjection(kind, kind.name, x, y, false, 20f, -1, 0f, immutableListOf(), 0f, 0f)
        return model.with(
            "dimension" to dimension,
            // The moving sealed offer stays last: its distance drives the measured frames.
            "sites" to (listOf(rift(SiteKind.ZENITH_RIFT, CORE - 330f, CORE - 170f), rift(SiteKind.NADIR_RIFT, CORE + 380f, CORE + 220f)) +
                model.sites).toImmutableList(),
        )
    }

    /** The larger of the two edge-marker distances (totem, sealed-anomaly offer) in [model]. */
    private fun edgeDistance(model: GameplayRenderModel): Float {
        val totem = requireNotNull(model.totem)
        val offer = model.sites.last()
        return maxOf(
            kotlin.math.hypot(totem.x - model.coreX, totem.y - model.coreY),
            kotlin.math.hypot(offer.x - model.coreX, offer.y - model.coreY),
        )
    }

    @Test
    fun worldAndHudDigitsKeepSeparateParagraphs() {
        // The game's measurer caches 64 layouts, and Compose shares one paragraph between layouts
        // whose styles differ only in color. At 1440 x 810 the trial panel draws the collapsing
        // orbit's clock in bone mono digits, and the off-screen totem's edge marker draws its
        // distance in you-color mono digits. Both digit sets are first measured in the same frame;
        // if they shared paragraphs, each would be repainted in the other color every frame.
        val width = 1_440
        val height = 810
        val base = hudTestModel(width.toFloat(), height.toFloat())
        val frames = List(HUD_FRAMES) { frame ->
            base.with("totem" to TotemProjection(base.coreX - 1_300f - frame * 11f, base.coreY + 90f, 1f))
                .withSites(SiteProjection(SiteKind.COLLAPSING_ORBIT, "Collapsing orbit",
                    base.coreX + 60f, base.coreY + 40f, true, 12f, -1, frame / HUD_FRAMES.toFloat(), immutableListOf(), 0f, 0f))
        }
        val measurer = CanvasTextMeasurer(
            ComposeTextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr, cacheSize = 64),
            1.25f, AppLanguage.English, HudTestFonts.typography,
        )
        val memory = HudPresentationMemory()
        val canvas = Canvas(ImageBitmap(width, height))
        val scope = CanvasDrawScope()
        val size = Size(width.toFloat(), height.toFloat())
        fun empty() = scope.draw(Density(1f), LayoutDirection.Ltr, canvas, size) { drawRect(Kk.Ink) }
        fun frame(index: Int) = scope.draw(Density(1f), LayoutDirection.Ltr, canvas, size) {
            drawGameplay(frames[index], VisualFxProjection.EMPTY, measurer, 10f + index * 0.0001f, pauseLayout = null, hudMemory = memory)
        }
        val thread = Thread.currentThread().id
        repeat(HUD_FRAMES) { empty(); frame(it) }
        var before = threads.getThreadAllocatedBytes(thread)
        repeat(HUD_FRAMES) { empty() }
        val emptyBytes = threads.getThreadAllocatedBytes(thread) - before
        before = threads.getThreadAllocatedBytes(thread)
        repeat(HUD_FRAMES) { frame(it) }
        val frameBytes = threads.getThreadAllocatedBytes(thread) - before
        val perFrame = (frameBytes - emptyBytes) / HUD_FRAMES
        assertTrue(perFrame <= 96, "A world + HUD frame with an orbit trial and an off-screen totem allocates $perFrame bytes above an empty frame")
    }

    @Test
    fun edgeMarkerDigitsDifferFromEachOtherAndTheHudMonoDigitsInLayout() {
        // Color alone does not separate paragraphs: every edge-marker color and the HUD's plain
        // mono digits (trial clock) differ in a layout-affecting attribute at the same size.
        val typography = HudTestFonts.typography
        val roles = KkRolePalette.Default
        val styles = listOf(EdgeMarkerIcon.TOTEM, EdgeMarkerIcon.SEALED_ANOMALY, EdgeMarkerIcon.COLLAPSING_ORBIT)
            .map { edgeMarkerStyle(typography, it, roles) }
        assertTrue(edgeMarkerStyle(typography, EdgeMarkerIcon.RESONANT_CIRCUIT, roles) === styles[0], "the two you-color markers share digits")
        styles.forEachIndexed { index, style ->
            val size = style.fontSize.value
            assertFalse(style.hasSameLayoutAffectingAttributes(typography.monoStyle(size)), "marker style $index vs the HUD's mono digits")
            styles.forEachIndexed { other, otherStyle ->
                if (other != index) assertFalse(style.hasSameLayoutAffectingAttributes(otherStyle), "marker styles $index and $other")
            }
        }
    }

    @Test
    fun everyEdgeMarkerColorHasItsOwnDigitParagraphs() {
        // The site families add colors (race volt, bounty threat, ambient mute, sigil void): markers of one color share
        // their digit style, markers of two colors never share a paragraph, and none shares the HUD's mono digits.
        val typography = HudTestFonts.typography
        for (roles in listOf(KkRolePalette.Default, KkRolePalette.Mono)) {
            val icons = EdgeMarkerIcon.entries
            icons.forEach { icon ->
                val style = edgeMarkerStyle(typography, icon, roles)
                assertFalse(style.hasSameLayoutAffectingAttributes(typography.monoStyle(style.fontSize.value)), "$icon vs the HUD's mono digits")
                icons.forEach { other ->
                    val otherStyle = edgeMarkerStyle(typography, other, roles)
                    if (edgeMarkerColorGroup(icon) == edgeMarkerColorGroup(other)) {
                        assertTrue(edgeMarkerColor(icon, roles) == edgeMarkerColor(other, roles), "$icon and $other share a group, not a color")
                    } else {
                        assertFalse(style.hasSameLayoutAffectingAttributes(otherStyle), "$icon and $other share digit paragraphs")
                    }
                }
            }
        }
        assertTrue(edgeMarkerColor(EdgeMarkerIcon.RACE, KkRolePalette.Default) == KkRolePalette.Default.you, "the race is the player's course")
        assertTrue(edgeMarkerColor(EdgeMarkerIcon.BOUNTY, KkRolePalette.Default) == KkRolePalette.Default.threat, "a bounty is a threat")
        assertFalse(edgeMarkerColor(EdgeMarkerIcon.CHALLENGE, KkRolePalette.Default) == KkRolePalette.Default.threat, "a rule is no threat")
    }

    private fun render(model: GameplayRenderModel, fx: VisualFxProjection, roles: KkRolePalette): IntArray {
        val bitmap = ImageBitmap(WIDTH, HEIGHT)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(WIDTH.toFloat(), HEIGHT.toFloat())) {
            drawRect(Kk.Ink)
            drawBackdrop(model, 0f, 0f, 2f, roles)
            drawWorld(model, fx, 0f, 0f, measurer(roles))
            drawScreenFx(model, 2f, roles)
        }
        val map = bitmap.toPixelMap()
        return IntArray(WIDTH * HEIGHT) { map[it % WIDTH, it / WIDTH].toArgb() }
    }

    private fun measurer(roles: KkRolePalette) = CanvasTextMeasurer(
        ComposeTextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr),
        1.25f, AppLanguage.English, roles = roles,
    )

    private fun busyFx(): VisualFxProjection = VisualFxProjection(
        particles = (0 until 40).map { index ->
            ParticleProjection(CORE + (index % 8) * 30f - 120f, CORE - 150f + (index / 8) * 20f, 90f, -40f + index, 0.3f, 0.6f, index % 5,
                if (index % 10 < 4) 6f else 2f)
        }.toImmutableList(),
        motionEchoes = immutableListOf(MotionEchoProjection(CORE - 50f, CORE + 20f, 0.2f, 0.3f, 1f)),
        shockwaves = immutableListOf(
            ShockwaveProjection(CORE + 200f, CORE - 60f, 0.25f, 0.3f, 60f, 1),
            ShockwaveProjection(CORE + 120f, CORE + 80f, 0.22f, 0.26f, 110f, 3),
        ),
        damageNumbers = listOf(96L, 184L, 742L, 4_800L).mapIndexed { index, amount ->
            DamageNumberProjection(CORE + index * 70f, CORE - 90f, amount, critical = index == 3, life = 0.4f, driftX = 0.6f, driftY = -0.8f,
                tone = index)
        }.toImmutableList(),
        weaponArcs = immutableListOf(WeaponArcProjection(CORE, CORE, CORE + 180f, CORE + 60f, 0.1f)),
        // Every Break figure, two reactions (one merged) and a first-discovery name stamp.
        elementBursts = ((0 until 5).map { element ->
            ElementBurstProjection(CORE - 220f + element * 90f, CORE + 160f, element, -1, -1, 80f, 0.3f, 0.5f, 1)
        } + listOf(
            ElementBurstProjection(CORE + 160f, CORE - 160f, 0, 1, 0, 90f, 0.4f, 0.7f, 3),
            ElementBurstProjection(CORE - 160f, CORE - 160f, 2, 4, 8, 120f, 0.2f, 0.7f, 1),
        )).toImmutableList(),
        reactionStamps = immutableListOf(ReactionStampProjection(CORE + 40f, CORE - 40f, 0, 0.5f, 0.9f)),
        // A 48-sample ribbon on a 520 u arc ending at the Core, 14 u apart, aged 0 to 0.4 s, the newest 8 from a Dash.
        coreRibbon = curvedRibbon(CORE, CORE - 520f, 520f, (kotlin.math.PI / 2).toFloat(), 47 * 14f / 520f, 48, dashNewest = 8),
    )

    /** [busyFx] plus every reaction's look, three skill looks, an Apex activation and 40 styled links. */
    private fun reactionFx(): VisualFxProjection {
        val base = busyFx()
        val forms = intArrayOf(0, ReactionCueFlags.VERB, ReactionCueFlags.KEYSTONE, ReactionCueFlags.VERB or ReactionCueFlags.KEYSTONE,
            ReactionCueFlags.ECHO, ReactionCueFlags.CASCADE)
        val reactions = ReactionId.entries.map { reaction ->
            val index = reaction.ordinal
            val life = ReactionFxSpecs[reaction].life
            ElementBurstProjection(CORE - 420f + (index % 7) * 140f, CORE - 240f + (index / 7) * 200f, reaction.first.ordinal,
                reaction.second.ordinal, index, 60f + index * 4f, life * (1f - (index % 5 + 0.5f) / 5f), life, 1 + index % 4,
                if (index % 2 == 0) index * 0.3f else Float.NaN, forms[index % forms.size], index * 31)
        }
        val skills = listOf(
            SkillBurstProjection(SkillId.METEOR.ordinal, 1, CORE + 120f, CORE + 80f, 0.6f, 0.8f, 150f, 3, 1, 5, 0.3f, 0.7f),
            SkillBurstProjection(SkillId.SONIC_BOOM.ordinal, 0, CORE, CORE, 1f, 0f, 300f, 2, 0, 6, 0.2f, 0.5f),
            SkillBurstProjection(SkillId.VOID_STEP.ordinal, 0, CORE - 60f, CORE + 40f, 0f, 1f, 240f, 1, 0, 7, 0.3f, 0.45f),
        )
        val styles = ReactionId.entries.map { LinkStyleCode.reaction(it) } + listOf(LinkStyleCode.skill(SkillId.ARC_HOOK),
            LinkStyleCode.skill(SkillId.STORM_COIL), LinkStyleCode.skill(SkillId.RESONATOR), LinkStyleCode.skill(SkillId.CATALYZE),
            LinkStyleCode.skill(SkillId.SOLAR_FLARE)) + StatusElement.entries.map { LinkStyleCode.elementBreak(it) } +
            CoreShape.entries.take(7).map { LinkStyleCode.apex(it) }
        val links = styles.mapIndexed { index, style ->
            val x = CORE - 450f + (index % 10) * 90f
            val y = CORE - 280f + (index / 10) * 150f
            WeaponArcProjection(x, y, x + 70f, y + 50f, 0.15f, style, 0.3f)
        }
        return base.copy(
            elementBursts = (base.elementBursts + reactions).takeLast(InteractionFxLimits.MAX_ELEMENT_BURSTS).toImmutableList(),
            weaponArcs = (base.weaponArcs + links).toImmutableList(),
            skillBursts = skills.toImmutableList(),
            apexBursts = immutableListOf(ApexBurstProjection(CoreShape.TESSERACT.ordinal, CORE, CORE, 3, 0.6f, 1f)),
        )
    }

    /**
     * [model] with [enemies] enemies cycling every control bit and mark, one field of each kind (two on
     * the Core), every Core state, three skill zones and the Apex running.
     */
    private fun reactionWorld(model: GameplayRenderModel, enemies: Int): GameplayRenderModel {
        val glyphs = intArrayOf(EnemyControlBits.STOPPED, EnemyControlBits.SILENCED, EnemyControlBits.THROWN, EnemyControlBits.BLACKFIRE,
            EnemyControlBits.UNMAKE_PENDING, EnemyControlBits.VOID_ANCHOR, EnemyControlBits.ATTRACTOR_ANCHOR, EnemyControlBits.APEX_STRIKE_TARGET)
        val crowd = List(enemies) { index ->
            val x = CORE - 440f + (index % 12) * 78f
            val y = CORE - 270f + (index / 12) * 54f
            val mark = ((index % 5) + 1) shl EnemyControlBits.MARK_SHIFT
            EnemyProjection(index + 1, EnemyType.DRIFTER, x, y, 40f, -20f, 50f, 100f, 14f, 0f, 0f, 0f, 0f, x - 1f, y, false,
                statusBits = BusyStatuses[index % BusyStatuses.size],
                controlBits = glyphs[index % glyphs.size] or glyphs[(index + 3) % glyphs.size] or mark)
        }
        val fields = CombatFieldVisual.entries.mapIndexed { index, kind ->
            val x = CORE - 380f + (index % 6) * 150f
            val y = CORE - 150f + (index / 6) * 260f
            CombatFieldProjection(kind, CombatFieldSource.entries[index % 3], x, y, x + 80f, y + 20f, 70f + index * 5f,
                element = if (index % 4 == 0) index % 7 else -1, reaction = if (index % 4 == 1) index else -1,
                createdAt = 3f - (index % 4) * 0.7f, expiresAt = 3f + 0.1f + index * 0.4f, charge01 = (index % 5) / 4f,
                anchorId = if (kind == CombatFieldVisual.ATTRACTOR) 3 else -1, followsCore = index == 2 || index == 6)
        }
        return model.with(
            "enemies" to crowd.toImmutableList(),
            "combatFields" to fields.toImmutableList(),
            "coreState" to CoreStateProjection(0.7f, 0.6f, true, 0.5f, 5, 4, true, true, true,
                (1 shl CombatFieldVisual.PLASMA.ordinal) or (1 shl CombatFieldVisual.TIME_BUBBLE.ordinal)),
            "skillBar" to SkillBarProjection(immutableListOf(
                SkillSlotProjection(SkillId.METEOR.ordinal, 2, pending01 = 0.4f, anchorX = CORE + 150f, anchorY = CORE - 60f),
                SkillSlotProjection(SkillId.MASS_DRIVER.ordinal, 1, active01 = 0.6f),
                SkillSlotProjection(SkillId.DUAL_INFUSION.ordinal, 3, active01 = 0.5f),
            )),
            "apex" to ApexProjection(unlocked = true, active01 = 0.6f, held = 3, strikesLeft = 4),
        )
    }

    /** Every mechanic's projected state at once; the renderer draws only the active character's. */
    private fun busyAbility(drift: Float): CharacterAbilityProjection = CharacterAbilityProjection(
        charge = 0.6f, ringRadius = 120f, parryWindow = 0.1f,
        lattice = immutableListOf(CharacterLatticePoint(CORE - 120f, CORE - 90f), CharacterLatticePoint(CORE + 140f, CORE - 80f),
            CharacterLatticePoint(CORE + 60f, CORE + 150f)),
        facets = 2, facetProgress = 0.4f, edge = 3, stellarMass = 12, novaFlash = 0.4f, haloBurstFlash = 0.5f,
        polarity = 1, polarityFlash = 0.5f, beatPeriod = 0.75f, beatWindow = 0.11f, beatStreak = 3,
        blinkCharges = 1, blinkRecharge = 0.5f,
        scars = immutableListOf(
            CharacterScarProjection(CORE - 200f, CORE + 40f, CORE - 20f, CORE + 60f, fuse = 0.5f, fade = 0f),
            CharacterScarProjection(CORE + 30f, CORE - 200f, CORE + 210f, CORE - 160f, fuse = 1f, fade = 0.1f),
        ),
        drones = immutableListOf(
            CharacterDroneProjection(CORE + 70f, CORE, CORE + 68f, CORE - 4f, darting = false),
            CharacterDroneProjection(CORE - 35f, CORE + 60f, CORE - 30f, CORE + 62f, darting = true),
            CharacterDroneProjection(CORE - 35f, CORE - 60f, CORE - 37f, CORE - 58f, darting = false),
        ),
        ghostActive = true, ghostX = CORE - 160f + drift * 0.01f, ghostY = CORE + 30f,
        previousGhostX = CORE - 165f + drift * 0.01f, previousGhostY = CORE + 28f,
    )

    /** The shifting bit alone, for the glitched Cipher of the busy frame. */
    private val ShiftingCipher = shiftingAffinity(true) xor shiftingAffinity(false)

    private fun shiftingAffinity(shifting: Boolean): Int = EnemyAffinity.encode(
        aspect = EnemyAffinity.NONE, immune = false, weakness = EnemyAffinity.NONE, armored = false, fragile = false,
        shifting = shifting, phase = 0,
    )

    private fun busyModel(
        weapon: WeaponId,
        drift: Float = 0f,
        shape: CoreShape = CoreShape.TESSERACT,
        level: Int = 3,
        dash: Float = 0f,
        easy: Boolean = false,
    ): GameplayRenderModel {
        val enemies = EnemyType.entries.mapIndexed { index, type ->
            val angle = index * 0.7f
            val distance = if (type == EnemyType.ARCHITECT) 260f else 180f + index * 12f
            EnemyProjection(index + 1, type, CORE + kotlin.math.cos(angle) * distance, CORE + kotlin.math.sin(angle) * distance, 0f, 0f,
                50f, 100f, if (type == EnemyType.ARCHITECT) 74f else 20f, if (type == EnemyType.HOUND) -0.2f else 0.2f,
                if (index == 2) 1f else 0f, 0f, 0f, CORE, CORE, false,
                statusBits = BusyStatuses[index % BusyStatuses.size],
                // Every type's aspect, weakness and frame, an immune Gravitic elite, a shifting Thermal Architect and a
                // Cipher glitched out of phase.
                affinityBits = runAffinity(type, eliteAspect = StatusElement.GRAVITIC, architectAspect = StatusElement.THERMAL,
                    architectPhase = 1, shifting = true) or if (type == EnemyType.CIPHER) ShiftingCipher else 0,
                // The 0.4.0 telegraphs: a showing lane, a locked sight line, a burning fuse, a wind-up, a raised
                // shield, a turned front, and a Drifter tethered to the Aegis. The 0.6.0 ones: a Comet marking its lane
                // or streaking it (alternate frames), a Pylon's locked sight line and a Sentry's bolt line on their mounts;
                // a Seraph about to mend, a Choir singing, an Imp's fuse, a Hound winding up (its timer below zero), a
                // glitched Cipher, a Mirror's line to the cursor and a Drifter in a Seraph's impact-only shield; Warden
                // Prime's well pushing, a Throne judging behind its three arcs and a Brood Queen with three egg sacs; the
                // Cartographer marking its three lanes and the Antecedent shielded behind two arcs.
                behavior = when (type) {
                    EnemyType.LANCER, EnemyType.SNIPER, EnemyType.PHANTOM, EnemyType.THRONE, EnemyType.CARTOGRAPHER ->
                        EnemyBehavior.TELEGRAPH
                    EnemyType.BOMBER, EnemyType.IMP, EnemyType.WARDEN_PRIME -> EnemyBehavior.PRIMED
                    EnemyType.COMET -> if ((drift / 20f).toInt() % 2 == 0) EnemyBehavior.STRIKE else EnemyBehavior.TELEGRAPH
                    EnemyType.PYLON, EnemyType.SENTRY, EnemyType.CHOIR -> EnemyBehavior.TELEGRAPH
                    EnemyType.CIPHER -> EnemyBehavior.PHASED
                    else -> EnemyBehavior.IDLE
                },
                behaviorRemaining = when (type) {
                    EnemyType.SNIPER -> 0.2f
                    EnemyType.PYLON -> 0.15f
                    else -> 0.3f
                },
                facing = index * 0.4f,
                linkedTo = if (type == EnemyType.DRIFTER) EnemyType.AEGIS.ordinal + 1 else -1,
                shielded = type == EnemyType.HERALD || type == EnemyType.DRIFTER || type == EnemyType.ANTECEDENT,
                // Rebirth R8: an Armored Vanguard plate on every third ordinary enemy.
                armored = index % 3 == 0 && type != EnemyType.ARCHITECT,
                charges = when (type) {
                    EnemyType.THRONE, EnemyType.BROOD_QUEEN -> 3
                    EnemyType.ANTECEDENT -> 2
                    else -> 0
                },
            )
        }
        return GameplayRenderModel(
            content = Content, phase = GamePhase.RUNNING, settings = PlayerPreferences(textScale = 1.25f), rebirthLevel = 5,
            screenWidth = WIDTH.toFloat(), screenHeight = HEIGHT.toFloat(), uiScale = 1f, coreX = CORE, coreY = CORE,
            velocityX = 420f, velocityY = -160f, cameraX = CORE, cameraY = CORE, pointerX = WIDTH * 0.5f + 180f,
            pointerY = HEIGHT * 0.5f - 120f, pointerActive = true, braking = true, elapsed = BUSY_ELAPSED, heat = 20f, overheated = false,
            dashPhaseTime = dash, hp = 100f, maxHp = 180f, shield = 1f, maxShield = 3f, level = 5, data = 1, nextLevelData = 5,
            keys = 1, kills = 3, combo = 3, comboTime = 1f, runMatter = 10L, totalMatter = 10L, lastImpact = 20f, lastImpactTime = 0.3f,
            damageFlash = 0.6f, runGrace = 0f, screenShake = 0f, message = "", messageTime = 0f, mass = 1f, damageMultiplier = 1f,
            weaponPower = 1f, coolingRate = 1f, magnetStrength = 1f, dashImpulse = 1f, dashHeatCost = 20f, regenPerSecond = 0f,
            critChance = 0f, critMultiplier = 2f, pickupRadius = 150f, luck = 0f, dataGain = 1f, matterGain = 1f, attackSpeed = 1f,
            damageReduction = 0f, comboWindow = 2f, overdriveGain = 1f, dragCoefficient = 1f, polarityStability = 0.8f,
            weapon = weapon, weaponLevel = level, overdriveCharge = 10f, overdriveTime = 0f, rerollsRemaining = 1, acquiredItemCount = 0,
            recentItem = null, equippedRelics = immutableListOf(), morningstarAngle = 0.3f, morningstarX = CORE + 60f,
            morningstarY = CORE + 40f, weaponBeamTime = 0f, weaponBeamStartX = 0f, weaponBeamStartY = 0f, weaponBeamEndX = 0f,
            weaponBeamEndY = 0f, totem = TotemProjection(CORE - 1_600f - drift, CORE + 100f + drift * 0.3f, 1f), coreShape = shape,
            enemies = enemies.toImmutableList(),
            projectiles = ((0 until 12).map { index ->
                ProjectileProjection(CORE - 200f + index * 25f, CORE + 120f, 0f, 0f, 4.5f, 1f, index % 3 != 0, 1f, 0, 0, null,
                    CORE - 206f + index * 25f, CORE + 123f)
            } + listOf(
                // An armed mine and an arming one.
                ProjectileProjection(CORE + 120f, CORE - 140f, 0f, 0f, 9f, 5f, true, 14f, 0, 0, null, CORE + 120f, CORE - 140f,
                    kind = ProjectileKind.MINE),
                ProjectileProjection(CORE + 160f, CORE - 140f, 0f, 0f, 9f, 7.9f, true, 14f, 0, 0, null, CORE + 160f, CORE - 140f,
                    kind = ProjectileKind.MINE),
            )).toImmutableList(),
            pickups = PickupType.entries.mapIndexed { index, type ->
                PickupProjection(type, CORE - 150f + index * 30f, CORE - 60f, 0f, 0f, 3f, CORE - 155f + index * 30f, CORE - 57f)
            }.toImmutableList(),
            trail = (0 until 24).map { index ->
                TrailPointProjection(CORE - index * 15f, CORE + index * 8f, index * 0.08f, life = if (index % 3 == 0) 2.7f else 1.8f,
                    scale = if (index % 3 == 0) 1.5f else 1f)
            }.toImmutableList(),
            weaponNodes = immutableListOf(WeaponNodeProjection(WeaponNodeType.GRAVITY_MINE, CORE - 250f, CORE - 200f, 2f, 3f, 55f)),
            weaponOrbitals = (0 until 3).map { index ->
                WeaponOrbitalProjection(index, CORE + kotlin.math.cos(index * 2.1f) * 140f, CORE + kotlin.math.sin(index * 2.1f) * 140f, 10f,
                    CORE + kotlin.math.cos(index * 2.1f - 0.03f) * 140f, CORE + kotlin.math.sin(index * 2.1f - 0.03f) * 140f)
            }.toImmutableList(),
            weaponEntities = BusyWeaponEntities,
            choices = immutableListOf(), choiceType = ChoiceType.ITEM, pendingRelicChoiceCount = 0,
            itemStacks = immutableListOf(), discoveredItemIds = immutableSetOf(), relicRanks = immutableListOf(),
            sites = immutableListOf(
                // The orbit's seconds in the ring change every frame (drawn from cached digits).
                SiteProjection(SiteKind.COLLAPSING_ORBIT, "Orbit", CORE + 60f, CORE + 40f, true, 14f, 0,
                    (drift % 80f) / 80f, immutableListOf(), 0.5f, 0.3f),
                // An offer on screen: its name and timer under the mark.
                SiteProjection(SiteKind.RESONANT_CIRCUIT, "Resonant circuit", CORE - 280f, CORE + 40f, false, 20f, 0, 0f,
                    immutableListOf(), 0f, 0f),
                SiteProjection(SiteKind.SEALED_ANOMALY, "Sealed", CORE + 900f + drift, CORE - 300f, false, 20f, 0, 0f,
                    immutableListOf(), 0f, 0f),
            ),
            obstacles = BusyObstacles,
            characterAbility = busyAbility(drift),
            // Rebirth R4: Volatile Remains telegraphs filling toward detonation.
            volatileBursts = immutableListOf(
                kinetickk.ball.gameplay.nucleus.render.VolatileBurstProjection(CORE + 90f, CORE + 70f, 70f, 2.8f, 3.15f),
                kinetickk.ball.gameplay.nucleus.render.VolatileBurstProjection(CORE - 120f, CORE + 30f, 70f, 2.9f, 3.25f),
            ),
            // The running orbit's panel, so the world keeps clear of it.
            trial = siteTrial(SiteProjection(SiteKind.COLLAPSING_ORBIT, "Orbit", CORE + 60f, CORE + 40f, true, 14f, 0,
                (drift % 80f) / 80f, immutableListOf(), 0.5f, 0.3f)),
            easyMode = easy,
        )
    }

    /** [busyModel] emptied of everything but the Core, its weapon and the cursor, 140 u to the Core's right. */
    private fun quietModel(easy: Boolean): GameplayRenderModel = busyModel(WeaponId.MORNINGSTAR, easy = easy).with(
        "enemies" to immutableListOf<EnemyProjection>(), "projectiles" to immutableListOf<ProjectileProjection>(),
        "pickups" to immutableListOf<PickupProjection>(), "weaponNodes" to immutableListOf<WeaponNodeProjection>(),
        "weaponEntities" to immutableListOf<WeaponEntityProjection>(), "obstacles" to immutableListOf<ObstacleProjection>(),
        "volatileBursts" to immutableListOf<kinetickk.ball.gameplay.nucleus.render.VolatileBurstProjection>(),
        "sites" to immutableListOf<SiteProjection>(), "trial" to null, "totem" to null, "characterAbility" to CharacterAbilityProjection(),
        "damageFlash" to 0f, "lastImpactTime" to 0f, "pointerX" to WIDTH * 0.5f + 140f, "pointerY" to HEIGHT * 0.5f,
    )

    /**
     * The busy frame with an offer of every window family in view (race, a challenge, the bounty, a rift) and the
     * sigil, a slalom offer and the sigil past the edge (their edge markers), and a cache and the quiet signal off
     * screen within their reveal distance: the marks, glyphs and markers draw without allocating.
     */
    private fun withSitesOfEveryFamily(model: GameplayRenderModel): GameplayRenderModel {
        fun offer(kind: SiteKind, x: Float, y: Float) = SiteProjection(kind, kind.name, x, y, false, 20f, -1, 0f, immutableListOf(), 0f, 0f)
        fun ambient(kind: SiteKind, x: Float, y: Float, reveal: Float) =
            SiteProjection(kind, kind.name, x, y, false, 0f, -1, 0f, immutableListOf(), 0f, 0f, reveal = reveal)
        return model.with(
            // The moving sealed offer stays last: its distance drives the measured frames.
            "sites" to (listOf(
                offer(SiteKind.GATE_RACE, CORE - 300f, CORE - 180f),
                offer(SiteKind.UNTOUCHED_TRIAL, CORE + 260f, CORE - 200f),
                offer(SiteKind.BOUNTY_ARENA, CORE + 300f, CORE + 160f),
                offer(SiteKind.ZENITH_RIFT, CORE - 340f, CORE + 200f),
                offer(SiteKind.FAR_SIGIL, CORE + 40f, CORE - 230f),
                offer(SiteKind.SLALOM_TRIAL, CORE - 2_200f, CORE - 900f),
            ) + model.sites).toImmutableList(),
            "ambientSites" to immutableListOf(
                ambient(SiteKind.DRIFT_CACHE, CORE + 1_200f, CORE + 900f, 1_800f),
                ambient(SiteKind.QUIET_SIGNAL, CORE - 1_500f, CORE + 1_900f, 3_000f),
                // Easter eggs are never drawn off screen.
                ambient(SiteKind.LOST_CURSOR, CORE + 1_000f, CORE - 1_200f, 0f),
            ),
        )
    }

    /**
     * The busy frame with a running seven-gate race around the Core: gates 0..4 on screen on a loop of 340, gates 5 and 6
     * past the edge; [step] picks the next gate (on screen at 2, past the edge at 6), and the race's trial joins the HUD.
     */
    private fun withRunningRace(model: GameplayRenderModel, step: Int): GameplayRenderModel {
        val course = List(7) { index ->
            val radius = if (index < 5) 340f else 1_400f
            val angle = index * 0.76f
            SiteMarkProjection(kotlin.math.cos(angle) * radius, kotlin.math.sin(angle) * radius)
        }.toImmutableList()
        val race = SiteProjection(SiteKind.GATE_RACE, "Gate race", CORE + 40f, CORE - 30f, true, 11f, step, step / 7f,
            immutableListOf(), 0f, 0f, course = course, clock = 4.2f, medal = 2)
        return model.with("sites" to (listOf(race) + model.sites).toImmutableList(), "trial" to siteTrial(race, model.content.sites))
    }

    /**
     * The busy frame with all five challenges running around the Core (one runs at a time in play; together they draw
     * every challenge branch): the slalom's poles 0..3 on screen and 4..5 past the edge, [slalomStep] its next pole; the
     * Ram rite marks four of the busy frame's enemies, and the first challenge's trial joins the HUD.
     */
    private fun withRunningChallenges(model: GameplayRenderModel, slalomStep: Int): GameplayRenderModel {
        fun challenge(kind: SiteKind, x: Float, y: Float, radius: Float, progress: Float, foes: ImmutableList<Int> = immutableListOf(),
            warning: Float = 0f) = SiteProjection(kind, kind.name, CORE + x, CORE + y, true, 11f, 0, progress, foes, warning, 0.6f,
            radius = radius, aimDx = 240f, aimDy = 160f)
        val poles = List(6) { index ->
            val along = if (index < 4) 120f + 150f * index else 1_500f + 300f * index
            SiteMarkProjection(along, if (index % 2 == 0) 120f else -120f)
        }.toImmutableList()
        val slalom = SiteProjection(SiteKind.SLALOM_TRIAL, "Slalom", CORE - 260f, CORE + 60f, true, 9f, slalomStep, slalomStep / 6f,
            immutableListOf(), 0f, 0f, course = poles, sides = 0b010101)
        val running = listOf(
            challenge(SiteKind.BRAKELESS_TRIAL, -40f, 30f, 520f, 0.4f),
            challenge(SiteKind.RAM_TRIAL, 60f, -20f, 650f, 0.3f, foes = immutableListOf(1, 2, 3, 4)),
            challenge(SiteKind.UNTOUCHED_TRIAL, 20f, 40f, 650f, 0.55f, warning = 0.5f),
            challenge(SiteKind.VELOCITY_TRIAL, -80f, -60f, 1_800f, 0.7f),
            slalom,
        )
        return model.with("sites" to (running + model.sites).toImmutableList(), "trial" to siteTrial(running.first(), model.content.sites))
    }

    /**
     * The busy frame with [kind] in progress (its clock following the moving sealed offer, so every measured frame draws a
     * new moment): four strikes armed half a second ago, a sweep 0.6 s in, a wind turning with the clock; a Merchant signal
     * on screen or past the edge ([signalOnScreen]); six Elites, one per affix, and four champion Drifters around the Core.
     */
    private fun withRunVariety(model: GameplayRenderModel, kind: WorldEventKind, signalOnScreen: Boolean): GameplayRenderModel {
        val elapsed = 3f + (model.sites.last().x - CORE - 900f) * 0.0005f
        val strikes = List(4) { index ->
            EventStrikeProjection(CORE - 300f + index * 200f, CORE + (if (index % 2 == 0) -150f else 120f), 95f, elapsed - 0.5f, elapsed + 0.9f)
        }.toImmutableList()
        val event = WorldEventProjection(kind, telegraphAt = 0f, startsAt = 2f, endsAt = 40f, windAngle = elapsed * 0.4f, windStrength = 0.8f,
            sweepStartedAt = elapsed - 0.6f, sweepAngle = 0.4f, strikes = strikes)
        val signal = if (signalOnScreen) EventSignalProjection(900, SignalKind.MERCHANT, CORE + 120f, CORE + 110f, elapsed + 20f)
        else EventSignalProjection(900, SignalKind.MERCHANT, CORE + 2_600f, CORE - 400f, elapsed + 20f)
        val affixed = EliteAffix.entries.mapIndexed { index, affix ->
            val angle = index * 1.05f
            EnemyProjection(300 + index, EnemyType.ELITE, CORE + cos(angle) * 230f, CORE + sin(angle) * 230f, 0f, 0f, 400f, 1000f, 30f, 0f, 0f,
                0f, 0f, CORE + cos(angle) * 230f, CORE + sin(angle) * 230f, false,
                affinityBits = EnemyAffinity.encode(StatusElement.GRAVITIC.ordinal, false, EnemyAffinity.NONE, false, false, false, 0,
                    affix = affix.ordinal))
        }
        val champions = List(4) { index ->
            val x = CORE - 330f + index * 70f
            EnemyProjection(320 + index, EnemyType.DRIFTER, x, CORE + 200f, 0f, 0f, 90f, 90f, 17f, 0f, 0f, 0f, 0f, x, CORE + 200f, false,
                affinityBits = EnemyAffinity.encode(EnemyAffinity.NONE, false, EnemyAffinity.NONE, index == 1, index == 2, false, 0,
                    champion = true))
        }
        return model.with(
            "elapsed" to elapsed,
            "worldEvent" to event,
            "eventSignals" to immutableListOf(signal),
            "enemies" to (model.enemies + affixed + champions).toImmutableList(),
        )
    }

    /** The busy frame with drift caches: two in view, one past the edge within its reveal distance, one beyond it. */
    private fun withAmbientCaches(model: GameplayRenderModel): GameplayRenderModel {
        fun cache(x: Float, y: Float) = SiteProjection(SiteKind.DRIFT_CACHE, "Drift cache", CORE + x, CORE + y, false, 0f, -1, 0f,
            immutableListOf(), 0f, 0f, activationRadius = 70f, reveal = 1_800f)
        return model.with(
            "ambientSites" to immutableListOf(cache(220f, -140f), cache(-280f, 120f), cache(1_300f, -900f), cache(2_600f, 1_400f)),
        )
    }

    /** The busy frame with the eggs: a Lost Cursor and a Quiet Signal in view, a Quiet Signal off screen, both companions. */
    private fun withEasterEggs(model: GameplayRenderModel): GameplayRenderModel {
        fun egg(kind: SiteKind, x: Float, y: Float, reveal: Float) = SiteProjection(kind, kind.name, CORE + x, CORE + y, false, 0f, -1, 0f,
            immutableListOf(), 0f, 0f, activationRadius = 90f, reveal = reveal)
        return model.with(
            "ambientSites" to immutableListOf(
                egg(SiteKind.LOST_CURSOR, -260f, -180f, 0f), egg(SiteKind.QUIET_SIGNAL, 300f, 160f, 3_000f),
                egg(SiteKind.QUIET_SIGNAL, 1_500f, -1_000f, 3_000f), egg(SiteKind.GOLDEN_DRIFTER, 200f, 260f, 0f),
            ),
            "secretFlags" to 1, "goldenDrifterId" to EnemyType.DRIFTER.ordinal + 1,
        )
    }

    /**
     * The busy frame with a running bounty whose arena is centred [centerDx] left of the Core, the busy frame's Warden
     * Prime its bracketed target, and the bounty's trial on the HUD.
     */
    private fun withRunningBounty(model: GameplayRenderModel, centerDx: Float): GameplayRenderModel {
        val bounty = SiteProjection(SiteKind.BOUNTY_ARENA, "Bounty", CORE + centerDx, CORE + 40f, true, 0f, 0, 0.35f,
            immutableListOf(EnemyType.WARDEN_PRIME.ordinal + 1), 0f, 0f, radius = model.content.sites.bounty.arenaRadius)
        return model.with("sites" to (listOf(bounty) + model.sites).toImmutableList(), "trial" to siteTrial(bounty, model.content.sites))
    }

    /**
     * The busy frame inside a Foundry's sealed furnace (the Core 300 inside its east wall, so the scrim is full), an
     * unspent Spire just east of it, and the Spire's and a far Vault's entrances off screen.
     */
    private fun withStructures(model: GameplayRenderModel): GameplayRenderModel {
        val templates = model.content.structures
        val foundry = StructureFixture(templates.template(StructureTemplateId.FOUNDRY), CORE - 900f, CORE, clearedRooms = 0b011, engagedRoom = 2)
        val spire = StructureFixture(templates.template(StructureTemplateId.SPIRE), CORE + 860f, CORE, id = 9L shl 8)
        val vault = StructureFixture(templates.template(StructureTemplateId.VAULT), CORE - 1_200f, CORE - 3_400f, id = 10L shl 8)
        check(structureInteriorAmount(model.with("structures" to structureProjection(foundry, dungeon = foundry))) == 1f)
        return model.with("structures" to structureProjection(foundry, spire, signals = listOf(foundry, spire, vault), dungeon = foundry,
            garrisonAlive = 4, garrisonTotal = 6))
    }

    private companion object {
        /**
         * Every obstacle kind on screen (and one off it), with outlines of several corner counts, plus a
         * crowded view of rocks whose radii all differ (every continuous radius is its own outline).
         */
        val BusyObstacles = (
            listOf(
                ObstacleProjection(1L, ObstacleKind.ASTEROID, CORE + 300f, CORE + 160f, 120f, 11),
                ObstacleProjection(2L, ObstacleKind.ASTEROID, CORE - 360f, CORE - 200f, 60f, 4),
                ObstacleProjection(3L, ObstacleKind.UNSTABLE, CORE + 330f, CORE - 230f, 80f, 27),
                ObstacleProjection(4L, ObstacleKind.BUMPER, CORE - 120f, CORE + 220f, 34f, 0),
                ObstacleProjection(5L, ObstacleKind.ASTEROID, CORE + 2_400f, CORE, 140f, 9),
                // A Mass Knot and a Repulsor, their rings drifting every frame.
                ObstacleProjection(6L, ObstacleKind.GRAVITY_WELL, CORE - 520f, CORE + 60f, 34f, 13),
                ObstacleProjection(7L, ObstacleKind.GRAVITY_WELL, CORE + 560f, CORE + 300f, 34f, 17, repels = true),
                // An accelerator gate, its bars pulsing, and three vents: quiet, glowing before they erupt and erupting.
                ObstacleProjection(8L, ObstacleKind.ACCEL_GATE, CORE + 120f, CORE - 300f, 100f, 9_000),
                ObstacleProjection(9L, ObstacleKind.THERMAL_VENT, CORE - 300f, CORE + 260f, 90f,
                    busyVentSeed { ventSecondsToEruption(BUSY_ELAPSED, it) in 1.5f..2.5f }),
                ObstacleProjection(10L, ObstacleKind.THERMAL_VENT, CORE + 380f, CORE + 20f, 70f,
                    busyVentSeed { ventSecondsToEruption(BUSY_ELAPSED, it) in 0.2f..0.4f }),
                ObstacleProjection(11L, ObstacleKind.THERMAL_VENT, CORE - 120f, CORE - 80f, 110f,
                    busyVentSeed { ventErupting(BUSY_ELAPSED - 0.1f, it) && ventErupting(BUSY_ELAPSED + 0.1f, it) }),
                // A crystal, a dormant mine and a half-fused one blinking inside its blast ring.
                ObstacleProjection(12L, ObstacleKind.CRYSTAL, CORE + 200f, CORE + 330f, 90f, 21),
                ObstacleProjection(13L, ObstacleKind.DERELICT_MINE, CORE - 420f, CORE + 330f, 64f, 22),
                ObstacleProjection(14L, ObstacleKind.DERELICT_MINE, CORE + 470f, CORE - 330f, 50f, 23, fuseAt = BUSY_ELAPSED + 0.3f),
                // Phase shards: solid, a dashed ghost, and one fading in before it materialises.
                ObstacleProjection(15L, ObstacleKind.PHASE_SHARD, CORE - 230f, CORE - 140f, 70f,
                    busyShardSeed { phaseShardSolid(BUSY_ELAPSED, it) }),
                ObstacleProjection(16L, ObstacleKind.PHASE_SHARD, CORE + 60f, CORE + 200f, 60f,
                    busyShardSeed { phaseShardSecondsToSolid(BUSY_ELAPSED, it) > phaseTelegraphSeconds + 0.2f }),
                ObstacleProjection(17L, ObstacleKind.PHASE_SHARD, CORE + 250f, CORE + 120f, 80f,
                    busyShardSeed { phaseShardSecondsToSolid(BUSY_ELAPSED, it) in 0.1f..0.35f }),
            ) + List(30) { index ->
                ObstacleProjection(100L + index, if (index % 5 == 0) ObstacleKind.UNSTABLE else ObstacleKind.ASTEROID,
                    CORE - 450f + (index % 6) * 180f, CORE - 260f + (index / 6) * 130f, 46f + index * 3.47f, 200 + index * 13)
            }
            ).toImmutableList()

        /** The busy frame's run time. */
        const val BUSY_ELAPSED = 3f

        /** The first vent shape seed whose cycle, under the default kind tuning, [accept]s. */
        fun busyVentSeed(accept: ObstacleKindTuning.(Int) -> Boolean): Int {
            val kinds = ObstacleKindTuning()
            return (1..10_000).first { kinds.accept(it) }
        }

        /** The first phase shard shape seed whose cycle, under the default kind tuning, [accept]s. */
        fun busyShardSeed(accept: ObstacleKindTuning.(Int) -> Boolean): Int = busyVentSeed(accept)

        /**
         * Statuses of the busy frame's enemies: a reaction ready (Thermal with Cryo), a primed Ion arc,
         * Frozen and Brittle, Embrittled, a full Entropy fill, Sonic with Void, none.
         */
        val BusyStatuses = intArrayOf(
            5 or (3 shl 4),
            (6 shl 8) or EnemyStatusBits.PRIMED,
            (4 shl 4) or EnemyStatusBits.FROZEN or EnemyStatusBits.BRITTLE,
            (2 shl 12) or EnemyStatusBits.EMBRITTLED,
            9 shl 16,
            (5 shl 20) or (3 shl 24),
            0,
        )
        /** Every weapon entity kind, with each rift, ring, disc and tether phase. */
        val BusyWeaponEntities = listOf(
            weaponEntity(WeaponEntityKind.DISC, CORE + 90f, CORE - 40f, radius = 16f),
            weaponEntity(WeaponEntityKind.DISC, CORE - 90f, CORE - 60f, radius = 16f, phase = 1),
            weaponEntity(WeaponEntityKind.SENTRY, CORE - 140f, CORE + 90f, radius = 12f, life = 6f, maxLife = 10f),
            weaponEntity(WeaponEntityKind.SENTRY, CORE + 140f, CORE + 100f, radius = 12f, life = 0.6f, maxLife = 10f),
            weaponEntity(WeaponEntityKind.LENS, CORE + 60f, CORE - 120f, radius = 14f),
            weaponEntity(WeaponEntityKind.WELL, CORE - 200f, CORE - 120f, radius = 170f, life = 1.1f, maxLife = 1.6f),
            weaponEntity(WeaponEntityKind.RIFT, CORE - 240f, CORE + 10f, endX = CORE - 80f, endY = CORE + 60f, radius = 40f, phase = 0),
            weaponEntity(WeaponEntityKind.RIFT, CORE + 40f, CORE + 160f, endX = CORE + 220f, endY = CORE + 120f, radius = 40f, phase = 1),
            weaponEntity(WeaponEntityKind.RIFT, CORE - 60f, CORE - 200f, endX = CORE + 100f, endY = CORE - 220f, radius = 28f, life = 0.2f, phase = 4),
            weaponEntity(WeaponEntityKind.SHARD, CORE + 30f, CORE + 70f, radius = 9f),
            weaponEntity(WeaponEntityKind.MISSILE, CORE - 30f, CORE + 110f, radius = 7f),
            weaponEntity(WeaponEntityKind.RING, CORE, CORE, radius = 240f, life = 0.2f, maxLife = 0.35f, phase = 0),
            weaponEntity(WeaponEntityKind.RING, CORE + 50f, CORE, radius = 210f, life = 0.1f, maxLife = 0.35f, phase = 1),
            weaponEntity(WeaponEntityKind.RING, CORE - 50f, CORE, radius = 160f, life = 0.3f, maxLife = 0.35f, phase = 2),
            weaponEntity(WeaponEntityKind.RING, CORE, CORE + 50f, radius = 170f, life = 0.25f, maxLife = 0.35f, phase = 3),
            weaponEntity(WeaponEntityKind.TETHER, CORE + 200f, CORE - 30f, endX = CORE, endY = CORE, phase = 0),
            weaponEntity(WeaponEntityKind.TETHER, CORE - 180f, CORE + 40f, endX = CORE, endY = CORE, phase = 1),
            weaponEntity(WeaponEntityKind.BEAM, CORE, CORE, radius = 0f, extent = 240f, angle = 0.4f),
            weaponEntity(WeaponEntityKind.BEAM, CORE, CORE, radius = 55f, extent = 240f, angle = 0.4f + 3.14159f, phase = 1),
        ).toImmutableList()

        fun weaponEntity(
            kind: WeaponEntityKind,
            x: Float,
            y: Float,
            endX: Float = x,
            endY: Float = y,
            radius: Float = 0f,
            extent: Float = 0f,
            angle: Float = 0f,
            life: Float = 0.5f,
            maxLife: Float = 1f,
            phase: Int = 0,
        ) = WeaponEntityProjection(kind, x, y, x - 4f, y + 2f, endX, endY, endX - 3f, endY, angle, angle - 0.03f, radius, extent, life, maxLife,
            phase)

        const val HUD_FRAMES = 400

        /** Distinct warm-up frames (their drifts lie past every measured frame's). */
        const val WARM_MODELS = 37

        /** Measuring passes before a frame fails; each draws fresh distances below the warm range. */
        const val PASSES = 3

        /** Measured frames (20 drift apart) whose distances all stay below the warm range. */
        const val MEASURED_DRIFT_FRAMES = 320

        val FULL_SWEEP = System.getenv("KK_FULL_SWEEPS") == "1"

        /** Warm-up drifts start here, so their distances lie past every measured frame's. */
        const val WARM_DRIFT = 6_400f
        const val WIDTH = 960
        const val HEIGHT = 600
        const val CORE = 1_000f

        val Content = GameplayContentSnapshot(
            version = KINETICKK_CONTENT_VERSION,
            items = immutableListOf(ItemDefinition(0, "Ram", "Ram", ItemRarity.COMMON, immutableListOf(BuildTag.RAM),
                stats = immutableListOf(StatDelta(ItemEffect.IMPACT_DAMAGE, 0.05f), StatDelta(ItemEffect.WEAPON_POWER, 0.04f)),
                maxStacks = 3, unlockLevel = 1)),
            weapons = WeaponId.entries.map { fixtureWeapon(it, description = "Fixture") }.toImmutableList(),
            weaponMasteries = WeaponMastery.entries.toImmutableList(),
            relics = rewardFixtureRelics.toImmutableList(),
            rebirth = RebirthPolicySnapshot(
                minimumLevel = 0, maximumLevel = 0,
                profiles = immutableListOf(RebirthProfile(0, "Baseline", 5, 1f, 1f, 1f, 1f, 1f, 1f, 0f, 1f, 0f, 1f, 0, 120, 0.09f, 24f)),
                maxActiveEnemies = 120, minSpawnIntervalSeconds = 0.09f, minEliteIntervalSeconds = 24f,
            ),
            relicPolicy = RelicPolicy(4, 3),
        )
    }
}
