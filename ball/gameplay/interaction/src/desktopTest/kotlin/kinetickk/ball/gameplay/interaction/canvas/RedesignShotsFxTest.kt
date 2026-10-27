// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.interaction.fx.LinkLooks
import kinetickk.ball.gameplay.interaction.fx.SkillFxSpecs
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.interaction.fx.WeaponArcProjection
import kinetickk.ball.gameplay.nucleus.protocol.LinkStyleCode
import kinetickk.ball.gameplay.nucleus.protocol.ReactionCueFlags
import kinetickk.ball.gameplay.nucleus.render.ApexProjection
import kinetickk.ball.gameplay.nucleus.render.CharacterAbilityProjection
import kinetickk.ball.gameplay.nucleus.render.CharacterDroneProjection
import kinetickk.ball.gameplay.nucleus.render.CharacterLatticePoint
import kinetickk.ball.gameplay.nucleus.render.CharacterScarProjection
import kinetickk.ball.gameplay.nucleus.render.CombatFieldProjection
import kinetickk.ball.gameplay.nucleus.render.CombatFieldSource
import kinetickk.ball.gameplay.nucleus.render.CombatFieldVisual
import kinetickk.ball.gameplay.nucleus.render.CoreStateProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyControlBits
import kinetickk.ball.gameplay.nucleus.render.EnemyProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.SkillBarProjection
import kinetickk.ball.gameplay.nucleus.render.SkillSlotProjection
import kinetickk.ball.profile.api.ReactionEffectsLevel
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import java.io.File
import kotlin.test.Test

/**
 * Contact sheets of the world FX (hud-fx §7.4). With `KK_SHOTS=1` each sheet is written to
 * build/redesign-shots/world/fx-*.png (reactions also in Mono and at the reduced and minimal settings);
 * otherwise each sheet draws once and nothing is written, so a look that fails to draw still fails.
 */
class RedesignShotsFxTest {
    private val fullShots = System.getenv("KK_SHOTS") == "1"

    @Test
    fun rendersReactionAndBreakSheets() {
        val presets = if (fullShots) listOf(KkRolePalette.Default to "default", KkRolePalette.Mono to "mono") else listOf(KkRolePalette.Default to "default")
        for ((roles, name) in presets) {
            sheet("reactions-$name", ReactionId.entries.size, PROGRESS.size, roles) { row, column, cell ->
                val reaction = ReactionId.entries[row]
                val model = model(cell.x - 70f, cell.y + 60f)
                drawElementBursts(model, FxTestScenes.fx(listOf(FxTestScenes.reaction(reaction, cell.x, cell.y, REACH, PROGRESS[column]))),
                    0f, 0f, roles)
            }
        }
        if (fullShots) for (level in listOf(ReactionEffectsLevel.REDUCED, ReactionEffectsLevel.MINIMAL)) {
            sheet("reactions-${level.name.lowercase()}", ReactionId.entries.size, PROGRESS.size, KkRolePalette.Default, level) { row, column, cell ->
                val model = model(cell.x - 70f, cell.y + 60f)
                drawElementBursts(model, FxTestScenes.fx(listOf(FxTestScenes.reaction(ReactionId.entries[row], cell.x, cell.y, REACH,
                    PROGRESS[column]))), 0f, 0f, KkRolePalette.Default)
            }
        }
        // Each reaction's four forms at 0.3: plain, its Core branch, the keystone, both (the Core sits left of the flash).
        sheet("reaction-forms", ReactionId.entries.size, FORMS.size, KkRolePalette.Default) { row, column, cell ->
            val model = model(cell.x - 60f, cell.y + 50f)
            drawCoreMarker(model)
            drawElementBursts(model, FxTestScenes.fx(listOf(FxTestScenes.reaction(ReactionId.entries[row], cell.x, cell.y, REACH, 0.3f,
                flags = FORMS[column]))), 0f, 0f, KkRolePalette.Default)
        }
        sheet("breaks", StatusElement.entries.size, PROGRESS.size, KkRolePalette.Default) { row, column, cell ->
            val model = model()
            drawElementBursts(model, FxTestScenes.fx(listOf(FxTestScenes.breakBurst(row, cell.x, cell.y, PROGRESS[column], REACH))), 0f, 0f,
                KkRolePalette.Default)
        }
    }

    @Test
    fun rendersSkillApexAndLinkSheets() {
        val looks = SkillId.entries.flatMap { skill -> (0 until SkillFxSpecs.PHASES).mapNotNull { phase -> SkillFxSpecs.of(skill, phase)?.let { skill to phase } } }
        sheet("skills", looks.size, SKILL_PROGRESS.size, KkRolePalette.Default) { row, column, cell ->
            val (skill, phase) = looks[row]
            val model = model(cell.x - 40f, cell.y)
            drawCoreMarker(model)
            drawSkillBursts(model, VisualFxProjection.EMPTY.copy(skillBursts = immutableListOf(FxTestScenes.skill(skill, phase, cell.x, cell.y,
                REACH, SKILL_PROGRESS[column], flags = 1))), 0f, 0f, KkRolePalette.Default)
        }
        sheet("apex", CoreShape.entries.size, 2, KkRolePalette.Default) { row, column, cell ->
            val shape = CoreShape.entries[row]
            val model = model(cell.x, cell.y, "coreShape" to shape, "braking" to true,
                "apex" to ApexProjection(unlocked = true, active01 = 0.6f, held = 4), "characterAbility" to apexAbility(cell))
            if (column == 0) {
                val core = Offset(cell.x, cell.y)
                drawCore(model, core, KkRolePalette.Default)
                drawApexBursts(model, FxTestScenes.fx(apex = listOf(FxTestScenes.apex(row, cell.x, cell.y, 0.3f))), 0f, 0f, KkRolePalette.Default)
            } else {
                val core = Offset(cell.x, cell.y)
                drawCore(model, core, KkRolePalette.Default)
                drawApexActive(model, core, 0f, 0f, KkRolePalette.Default)
            }
        }
        val styles = ReactionId.entries.map { LinkStyleCode.reaction(it) } + SkillId.entries.map { LinkStyleCode.skill(it) } +
            StatusElement.entries.map { LinkStyleCode.elementBreak(it) } + CoreShape.entries.map { LinkStyleCode.apex(it) } + LinkStyleCode.WEAPON
        sheet("links", styles.size, 3, KkRolePalette.Default) { row, column, cell ->
            val model = model()
            val style = styles[row]
            val life = if (style == 0) 0.12f else LinkLooks[style].life
            val progress = LINK_PROGRESS[column]
            drawWeaponArcs(model, VisualFxProjection.EMPTY.copy(weaponArcs = immutableListOf(WeaponArcProjection(cell.x - 90f, cell.y + 20f,
                cell.x + 90f, cell.y - 20f, life * (1f - progress), style, life))), 0f, 0f, KkRolePalette.Default)
        }
    }

    @Test
    fun rendersFieldControlAndCoreStateSheets() {
        sheet("fields", CombatFieldVisual.entries.size, FIELD_AGES.size, KkRolePalette.Default) { row, column, cell ->
            val kind = CombatFieldVisual.entries[row]
            val inside = kind == CombatFieldVisual.PLASMA || kind == CombatFieldVisual.TIME_BUBBLE
            val total = 4f
            val line = kind == CombatFieldVisual.FROST_LINE
            val field = CombatFieldProjection(kind, if (row % 3 == 0) CombatFieldSource.SKILL else CombatFieldSource.REACTION,
                x = if (line) cell.x - 70f else cell.x, y = cell.y, x2 = if (line) cell.x + 70f else cell.x, y2 = cell.y,
                radius = 80f, createdAt = 10f - total * FIELD_AGES[column], expiresAt = 10f - total * FIELD_AGES[column] + total, charge01 = 0.5f)
            val model = model(cell.x + if (inside) 20f else 400f, cell.y, "combatFields" to immutableListOf(field),
                "coreState" to CoreStateProjection(inFieldMask = if (inside) 1 shl kind.ordinal else 0))
            drawCombatFields(model, 0f, 0f, KkRolePalette.Default)
        }
        val glyphs = listOf(EnemyControlBits.STOPPED, EnemyControlBits.SILENCED, EnemyControlBits.THROWN, EnemyControlBits.BLACKFIRE,
            EnemyControlBits.UNMAKE_PENDING, EnemyControlBits.VOID_ANCHOR, EnemyControlBits.ATTRACTOR_ANCHOR, EnemyControlBits.APEX_STRIKE_TARGET) +
            (0 until 5).map { (it + 1) shl EnemyControlBits.MARK_SHIFT }
        val crowds = listOf(0, FxFrame.CROWD_SECONDARY_OFF + 1, FxFrame.CROWD_SIG_ONLY + 1)
        for ((index, crowd) in crowds.withIndex()) {
            sheet("control-crowd$index", glyphs.size, 1, KkRolePalette.Default, crowd = crowd) { row, _, cell ->
                val enemy = EnemyProjection(1, EnemyType.DRIFTER, cell.x, cell.y, 60f, 20f, 50f, 100f, 22f, 0f, 0f, 0f, 0f, cell.x, cell.y, false,
                    controlBits = glyphs[row] or (glyphs.reduce(Int::or).takeIf { row == glyphs.lastIndex } ?: 0))
                val model = model(cell.x - 150f, cell.y, "enemies" to immutableListOf(enemy), "braking" to true)
                drawEnemy(model, enemy, 0f, 0f, KkRolePalette.Default)
            }
        }
        val states = listOf(
            CoreStateProjection(ablaze01 = 1f), CoreStateProjection(frictionless01 = 1f), CoreStateProjection(blastJumpReady = true),
            CoreStateProjection(weave01 = 1f), CoreStateProjection(guardShards = 5), CoreStateProjection(shroudStored = 6),
            CoreStateProjection(phased = true), CoreStateProjection(primedDash = true), CoreStateProjection(coiledDash = true),
            CoreStateProjection(inFieldMask = 1 shl CombatFieldVisual.PLASMA.ordinal),
            CoreStateProjection(inFieldMask = 1 shl CombatFieldVisual.TIME_BUBBLE.ordinal),
            CoreStateProjection(0.8f, 0.8f, true, 0.6f, 5, 4, true, true, true, (1 shl CombatFieldVisual.PLASMA.ordinal) or (1 shl CombatFieldVisual.TIME_BUBBLE.ordinal)),
        )
        sheet("core-states", states.size, 1, KkRolePalette.Default) { row, _, cell ->
            val model = model(cell.x, cell.y, "coreState" to states[row], "velocityX" to 400f, "velocityY" to 100f,
                "skillBar" to markerBar(row))
            val core = Offset(cell.x, cell.y)
            drawSkillMarkers(model, 0f, 0f, KkRolePalette.Default)
            drawCore(model, core, KkRolePalette.Default)
            drawCoreStates(model, core, KkRolePalette.Default)
        }
    }

    /** Skill bars that show each zone marker on some core-state cells (Meteor and Void Step in flight, three skills running). */
    private fun markerBar(row: Int): SkillBarProjection = when (row % 3) {
        0 -> SkillBarProjection(immutableListOf(
            SkillSlotProjection(SkillId.METEOR.ordinal, 2, pending01 = 0.5f, anchorX = 0f, anchorY = 0f),
            SkillSlotProjection(SkillId.MASS_DRIVER.ordinal, 1, active01 = 0.7f), SkillSlotProjection.EMPTY))
        1 -> SkillBarProjection(immutableListOf(SkillSlotProjection(SkillId.DUAL_INFUSION.ordinal, 1, active01 = 0.7f),
            SkillSlotProjection(SkillId.SLINGSHOT.ordinal, 1, active01 = 0.5f), SkillSlotProjection.EMPTY))
        else -> SkillBarProjection.EMPTY
    }

    private fun apexAbility(cell: Offset) = CharacterAbilityProjection(
        ringRadius = 70f, facets = 3, stellarMass = 10, beatPeriod = 0.75f, polarity = 0,
        lattice = immutableListOf(CharacterLatticePoint(cell.x - 60f, cell.y - 50f), CharacterLatticePoint(cell.x + 70f, cell.y - 40f),
            CharacterLatticePoint(cell.x + 20f, cell.y + 70f)),
        scars = immutableListOf(CharacterScarProjection(cell.x - 80f, cell.y + 40f, cell.x + 60f, cell.y + 70f, fuse = 1f, fade = 0.05f)),
        drones = immutableListOf(CharacterDroneProjection(cell.x + 50f, cell.y, cell.x + 50f, cell.y, darting = false)),
        ghostActive = true, ghostX = cell.x - 60f, ghostY = cell.y + 40f, previousGhostX = cell.x - 60f, previousGhostY = cell.y + 40f,
    )

    /** A bone dot where the cell's Core is (CORE layers draw around it). */
    private fun DrawScope.drawCoreMarker(model: GameplayRenderModel) {
        drawCircle(Kk.Bone, model.coreRadius * 0.5f, Offset(model.coreX, model.coreY), alpha = 0.6f)
    }

    /** The sheet being drawn: world (x, y) draws at its pixel (x, y). */
    private var sheetWidth = 0
    private var sheetHeight = 0

    private fun model(coreX: Float = sheetWidth * 0.5f, coreY: Float = sheetHeight * 0.5f, vararg overrides: Pair<String, Any?>) =
        FxTestScenes.model(sheetWidth, sheetHeight, coreX, coreY, *overrides)

    /**
     * A grid of [rows] × [columns] cells of [CELL] px drawn by [cell] (row, column, cell centre), each
     * clipped to its cell, written as fx-[name].png with `KK_SHOTS=1`.
     */
    private fun sheet(
        name: String,
        rows: Int,
        columns: Int,
        roles: KkRolePalette,
        level: ReactionEffectsLevel = ReactionEffectsLevel.FULL,
        crowd: Int = 0,
        cell: DrawScope.(Int, Int, Offset) -> Unit,
    ) {
        sheetWidth = columns * CELL
        sheetHeight = rows * CELL
        val bitmap: ImageBitmap = FxTestScenes.render(sheetWidth, sheetHeight, level, crowd) {
            for (row in 0 until rows) for (column in 0 until columns) {
                val left = column * CELL.toFloat()
                val top = row * CELL.toFloat()
                clipRect(left, top, left + CELL, top + CELL) {
                    drawRect(Kk.Ink2, Offset(left, top), androidx.compose.ui.geometry.Size(CELL.toFloat(), CELL.toFloat()), alpha = if ((row + column) % 2 == 0) 0.35f else 0.15f)
                    FxFrame.begin(crowd, level)
                    cell(row, column, Offset(left + CELL * 0.5f, top + CELL * 0.5f))
                }
            }
        }
        if (fullShots) FxTestScenes.write(bitmap, File("build/redesign-shots/world/fx-$name.png"))
    }

    private companion object {
        const val CELL = 220
        const val REACH = 90f

        val PROGRESS = floatArrayOf(0.1f, 0.3f, 0.5f, 0.7f, 0.9f)
        val SKILL_PROGRESS = floatArrayOf(0.2f, 0.5f, 0.8f)
        val LINK_PROGRESS = floatArrayOf(0.1f, 0.4f, 0.7f)
        val FIELD_AGES = floatArrayOf(0.1f, 0.5f, 0.95f)
        val FORMS = intArrayOf(0, ReactionCueFlags.VERB, ReactionCueFlags.KEYSTONE, ReactionCueFlags.VERB or ReactionCueFlags.KEYSTONE)
    }
}
