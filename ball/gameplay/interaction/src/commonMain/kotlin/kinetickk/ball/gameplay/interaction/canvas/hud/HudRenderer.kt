// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas.hud

import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.ball.content.api.EquippedRelic
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.RelicAspect
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.interaction.layout.GameplayLayoutMode
import kinetickk.ball.gameplay.interaction.layout.PORTRAIT_HUD_TOP_DP
import kinetickk.ball.gameplay.interaction.layout.RUNNING_CONTROL_MIN_DP
import kinetickk.ball.gameplay.interaction.layout.compactHudFactor
import kinetickk.ball.gameplay.interaction.layout.gameplayLayoutMode
import kinetickk.ball.gameplay.interaction.layout.regularHudUnit
import kinetickk.ball.gameplay.interaction.layout.runningHudMargin
import kinetickk.ball.gameplay.nucleus.render.formatRunTime
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.design.*
import kotlin.math.max
import kotlin.math.min
import kinetickk.ball.gameplay.interaction.canvas.*
import kinetickk.ball.gameplay.interaction.canvas.look.*
import kinetickk.ball.gameplay.interaction.canvas.world.*
import kinetickk.ball.gameplay.interaction.layout.*

/**
 * The running HUD (SPEC section 7.1): level badge + Data bar top-left, timer (and boss bar) top-center,
 * economy and pause top-right, chain on the right edge, integrity/shield/dash/heat bottom-left,
 * speed gauge bottom-center, relics and weapon bottom-right, world-anchored halos and state
 * effects. Phones use the same modules at the Mobile board sizes. Draws no text warnings.
 */
internal fun DrawScope.drawHud(
    engine: GameplayRenderModel,
    textMeasurer: TextMeasurer,
    renderTime: Float = 0f,
    shakeX: Float = 0f,
    shakeY: Float = 0f,
    memory: HudPresentationMemory? = null,
    trialInfoOpen: Boolean = false,
    controls: RunningControlPressLog? = null,
) {
    val frame = HudScratch.frame.update(size.width, size.height, density)
    HudLayoutProbe.begin()
    val boss = HudScratch.boss.select(engine)
    memory?.observe(engine, renderTime, boss)
    val critical = isIntegrityCritical(engine.hp, engine.maxHp)

    drawHudWorldAnchors(engine, textMeasurer, renderTime, shakeX, shakeY, memory, critical)
    drawHudScreenStates(engine, textMeasurer.roles, renderTime, memory, critical)
    drawDimensionTransition(engine, textMeasurer.roles, renderTime, memory)

    drawDataLine(engine, frame, textMeasurer.roles, renderTime, memory)
    val badgeBottom = drawLevelCluster(engine, textMeasurer, frame, renderTime, memory, controls)
    drawClockAndBoss(engine, textMeasurer, frame, boss, renderTime, memory)
    drawEconomy(engine, textMeasurer, frame, badgeBottom)
    // The chain first: the portrait Rebirth strip shares its row and stops short of it.
    drawChain(engine, textMeasurer, frame)
    drawRebirthStrip(engine, textMeasurer, frame)
    drawCoreStatus(engine, textMeasurer, frame, renderTime, memory, critical)
    drawKineticGauge(engine, textMeasurer, frame, renderTime, memory)
    drawLoadout(engine, textMeasurer, frame)
    drawMinimap(engine, textMeasurer, frame)
    drawTrialPanel(engine, textMeasurer, renderTime, trialInfoOpen)
    drawControls(engine, textMeasurer, renderTime, memory, controls)
}

/** Draw-thread scratch objects reused every frame. */
internal object HudScratch {
    val frame = HudFrame()
    val boss = BossTarget()
    val timer = HudNumberText { formatRunTime(it.toFloat()) }
    val matter = HudKeyedText()
    val keys = HudNumberText { it.toString() }

    /** The compact integrity number, keyed by its shown value (below 1,000 the digits draw without it). */
    val integrity = HudKeyedText()

    /** The Show FPS counter's value ("–" before the first measured window). */
    val fps = HudNumberText { if (it < 0L) "–" else it.toString() }
    val weaponLevel = HudKeyedText()
    val bossName = HudKeyedText()
    val label = HudKeyedText()
    val badgeShots = HudBadgeOneShots()
    val dashLabel = HudKeyedText()
    val brakeLabel = HudKeyedText()
    val overheat = HudKeyedText()
    val coreStasis = HudKeyedText()
    val coreThaw = HudKeyedText()

    /** The skill row's fade while it slides in with the first bound active item. */
    val skillRowPaint = androidx.compose.ui.graphics.Paint()
    var aspectContent: GameplayContentSnapshot? = null
    var linkRelics: List<EquippedRelic>? = null
    var linkContent: GameplayContentSnapshot? = null
    var links: List<OverlayRelicLink> = emptyList()
    val brackets = HudBracketLevels()
    /**
     * The level badge's row this frame, the economy's right edge on phones (inline on landscape, the
     * chip row on portrait) and the portrait chain's left edge (+inf without a chain): the Rebirth strip.
     */
    var badgeTop = 0f
    var badgeBottom = 0f
    var badgeRight = 0f
    var economyRight = 0f
    var chainLeft = Float.POSITIVE_INFINITY
    val rebirthTier = HudNumberText { it.toString() }
    val aspects = arrayOfNulls<RelicAspect>(RelicId.entries.size)
    var definitionContent: GameplayContentSnapshot? = null
    val definitions = arrayOfNulls<kinetickk.ball.content.api.RelicDefinition>(RelicId.entries.size)
}

/** Per-frame layout scalars of the running HUD (rewritten every frame, draw-thread confined). */
internal class HudFrame {
    var mode: GameplayLayoutMode = GameplayLayoutMode.REGULAR
        private set
    var width = 0f
        private set
    var height = 0f
        private set
    var density = 1f
        private set

    /** px per reference dp of the board this mode follows. */
    var unit = 1f
        private set

    /** Multiplier for board font sizes (sp): 1 on phones and full-size desktop windows. */
    var textFactor = 1f
        private set
    var margin = 0f
        private set

    /** Horizontal factor of the phone layouts relative to their boards. */
    var factor = 1f
        private set

    /** Top inset of the HUD (portrait status bar). */
    var top = 0f
        private set

    val regular: Boolean get() = mode == GameplayLayoutMode.REGULAR
    val portrait: Boolean get() = mode == GameplayLayoutMode.COMPACT_PORTRAIT

    fun update(width: Float, height: Float, density: Float): HudFrame {
        this.width = width
        this.height = height
        this.density = density.coerceAtLeast(1f)
        mode = gameplayLayoutMode(width, height, density)
        unit = if (mode == GameplayLayoutMode.REGULAR) regularHudUnit(width, density) else density.coerceAtLeast(1f)
        textFactor = unit / density.coerceAtLeast(1f)
        margin = runningHudMargin(width, height, density)
        factor = when (mode) {
            GameplayLayoutMode.REGULAR -> 1f
            GameplayLayoutMode.COMPACT_LANDSCAPE -> compactHudFactor(width, density, portrait = false)
            GameplayLayoutMode.COMPACT_PORTRAIT -> compactHudFactor(width, density, portrait = true)
        }
        top = if (mode == GameplayLayoutMode.COMPACT_PORTRAIT) PORTRAIT_HUD_TOP_DP * unit else 0f
        return this
    }

    fun u(value: Float): Float = value * unit
    fun t(size: Float): Float = size * textFactor

    /**
     * Top of the bottom clusters' reserve (integrity, speed, loadout; on portrait phones also the
     * touch buttons): the floor for transient HUD overlays such as the trial rules.
     */
    val bottomClustersTop: Float
        get() = height - u(
            when (mode) {
                GameplayLayoutMode.REGULAR -> 136f
                GameplayLayoutMode.COMPACT_LANDSCAPE -> 84f
                GameplayLayoutMode.COMPACT_PORTRAIT -> 252f
            },
        )

    /** Portrait phones: the bottom of the integrity cluster and the speed gauge, above the touch buttons. */
    val portraitClustersBottom: Float get() = height - u(36f + 80f + 18f)

    /** Portrait phones: the top of the loadout row (weapon slot, level and relics) over the clusters. */
    val portraitLoadoutTop: Float get() = height - u(238f)

    /** Top of the chain counter on the right edge (REGULAR). */
    val chainTop: Float get() = max(u(76f), min(u(160f), height * 0.2f))

    /** Center of the pause button visual. */
    val pauseCenterX: Float get() = width - margin - if (regular) u(20f) else u(22f)

    /** Center of the Build button (Compose) beside pause on desktop and landscape phones. */
    val buildCenterX: Float
        get() = if (regular) pauseCenterX - max(u(48f), RUNNING_CONTROL_MIN_DP * density) else pauseCenterX - u(104f)
}
