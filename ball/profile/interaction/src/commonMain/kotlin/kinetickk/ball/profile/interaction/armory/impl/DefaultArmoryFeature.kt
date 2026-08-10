// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.armory.impl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.WeaponMastery
import kinetickk.foundation.design.*
import kinetickk.ball.profile.api.ProfileAcceptance
import kinetickk.ball.profile.api.ProfilePort
import kinetickk.ball.profile.api.ProfilePulse
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.interaction.audio.ProfileAudioCue
import kinetickk.ball.profile.interaction.audio.ProfileAudioExecutor
import kinetickk.ball.profile.interaction.armory.api.ArmoryFeature
import kinetickk.ball.profile.interaction.armory.api.ArmoryOutput
import kinetickk.ball.profile.interaction.armory.api.ArmoryRenderModel
import kinetickk.foundation.collections.ImmutableList
import kinetickk.resource.audio.api.AudioService
import kotlin.math.min

class DefaultArmoryFeature(
    private val profilePort: ProfilePort,
    private val weapons: ImmutableList<WeaponDefinition>,
    weaponMasteries: ImmutableList<WeaponMastery>,
    audioService: AudioService,
) : ArmoryFeature {
    private val reducer = ArmoryReducer(weapons)
    private val audioExecutor = ProfileAudioExecutor(audioService)
    private val weaponMasteryProgressionLabel = weaponMasteries.drop(1).joinToString("  ") {
        "L${it.minimumLevel} ${it.displayLabel.uppercase()}"
    }

    @Composable
    override fun Content(activeRunWeapon: WeaponId?, onOutput: (ArmoryOutput) -> Unit) {
        val density = LocalDensity.current.density
        val composeTextMeasurer = rememberTextMeasurer(cacheSize = 64)
        var pageValue by rememberSaveable { mutableIntStateOf(0) }
        var loadoutProjectionValue by remember(profilePort) {
            mutableStateOf(profilePort.query(ProfileQuery.GetLoadout))
        }
        var viewportValue by remember { mutableStateOf(ArmoryViewport(1f, 1f, density)) }
        var renderTimeSecondsValue by remember { mutableFloatStateOf(0f) }
        val model = reducer.renderModel(loadoutProjectionValue.snapshot, activeRunWeapon)
        val textMeasurer = CanvasTextMeasurer(
            composeTextMeasurer,
            profilePort.query(ProfileQuery.GetPreferences).preferences.textScale,
        )

        fun dispatch(action: ArmoryAction) {
            val reduction = reducer.reduce(pageValue, action)
            pageValue = reduction.page
            reduction.effects.forEach { effect ->
                when (effect) {
                    is ArmoryEffect.PurchaseOrEquipWeapon -> {
                        val acceptance = profilePort.accept(
                            ProfilePulse.PurchaseOrEquipWeapon(effect.id),
                        )
                        loadoutProjectionValue = profilePort.query(ProfileQuery.GetLoadout)
                        if (acceptance is ProfileAcceptance.Accepted) {
                            audioExecutor.play(ProfileAudioCue.PURCHASE)
                        }
                    }
                    is ArmoryEffect.PlayAudio -> audioExecutor.play(effect.cue)
                    is ArmoryEffect.Emit -> onOutput(effect.output)
                }
            }
        }

        LaunchedEffect(Unit) {
            var previousFrame = withFrameNanos { it }
            while (true) {
                val frame = withFrameNanos { it }
                renderTimeSecondsValue += ((frame - previousFrame) / 1_000_000_000f).coerceAtMost(0.1f)
                previousFrame = frame
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { size ->
                    viewportValue = ArmoryViewport(size.width.toFloat(), size.height.toFloat(), density)
                }
                .pointerInput(viewportValue, pageValue) {
                    detectTapGestures { position ->
                        resolveArmoryPress(
                            viewport = viewportValue,
                            weapons = weapons,
                            page = pageValue,
                            x = position.x,
                            y = position.y,
                        )?.let(::dispatch)
                    }
                },
        ) {
            drawArmory(
                engine = model,
                weapons = weapons,
                weaponMasteryProgressionLabel = weaponMasteryProgressionLabel,
                page = pageValue,
                maxPage = reducer.maxPage,
                textMeasurer = textMeasurer,
                renderTime = renderTimeSecondsValue,
            )
        }
    }
}

private fun DrawScope.drawArmory(
    engine: ArmoryRenderModel,
    weapons: ImmutableList<WeaponDefinition>,
    weaponMasteryProgressionLabel: String,
    page: Int,
    maxPage: Int,
    textMeasurer: TextMeasurer,
    renderTime: Float,
) {
    drawRect(Color(0xD9050610))
    val bounds = overlayBounds()
    drawOverlayFrame(bounds, Cyan)
    drawLabel(textMeasurer, "WEAPON ARMORY", bounds.left + d(25f), bounds.top + d(24f), 20f, Cyan, weight = FontWeight.Bold)
    drawLabel(textMeasurer, "${weapons.size} SYSTEMS // ${engine.unlockedWeapons.size} UNLOCKED // MATTER ${formatCompact(engine.totalMatter)}", bounds.right - d(25f), bounds.top + d(30f), 8f, White, alignRight = true)
    val cardWidth = min(d(245f), (bounds.width - d(80f)) / 3f)
    val gap = d(16f)
    val total = cardWidth * 3f + gap * 2f
    val startX = (size.width - total) * 0.5f
    val cardTop = bounds.top + d(118f)
    val cardBottom = bounds.bottom - d(85f)
    val start = page.coerceIn(0, maxPage) * ARMORY_PAGE_SIZE
    weapons.subList(start, min(start + ARMORY_PAGE_SIZE, weapons.size))
        .forEachIndexed { index, definition ->
            drawWeaponCard(
                engine = engine,
                textMeasurer = textMeasurer,
                definition = definition,
                weaponMasteryProgressionLabel = weaponMasteryProgressionLabel,
                x = startX + index * (cardWidth + gap),
                y = cardTop,
                width = cardWidth,
                height = cardBottom - cardTop,
                renderTime = renderTime,
            )
        }
    drawPagedFooter(textMeasurer, bounds, page.coerceIn(0, maxPage), maxPage, Cyan)
}

private fun DrawScope.drawWeaponCard(
    engine: ArmoryRenderModel,
    textMeasurer: TextMeasurer,
    definition: WeaponDefinition,
    weaponMasteryProgressionLabel: String,
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    renderTime: Float,
) {
    val unlocked = definition.id in engine.unlockedWeapons
    val equipped = engine.selectedWeapon == definition.id
    val active = engine.activeRunWeapon == definition.id
    val accent = if (unlocked) armoryWeaponColor(definition.id) else Muted
    drawRect(Color(0xB00B0D1D), Offset(x, y), Size(width, height))
    drawRect(accent, Offset(x, y), Size(width, height), style = Stroke(d(if (equipped) 2.2f else 1f)))
    drawRect(accent.copy(alpha = 0.12f), Offset(x, y), Size(width, d(50f)))
    drawSystemGlyph(
        armoryWeaponGlyphStyle(definition.id),
        Offset(x + width * 0.5f, y + d(95f)),
        d(28f),
        renderTime,
        accent,
    )
    drawLabel(textMeasurer, definition.name.uppercase(), x + width * 0.5f, y + d(139f), 11f, accent, centered = true, weight = FontWeight.Bold)
    drawLabel(textMeasurer, definition.tags.joinToString(" / "), x + width * 0.5f, y + d(164f), 7f, Muted, centered = true)
    drawLabel(textMeasurer, definition.description, x + d(14f), y + d(193f), 7f, White, maxWidth = width - d(28f), maxLines = 3)
    drawLabel(textMeasurer, weaponMasteryProgressionLabel, x + width * 0.5f, y + d(274f), 6f, accent, centered = true, maxWidth = width - d(20f), maxLines = 2)
    drawLabel(textMeasurer, "MILESTONES BOOST DAMAGE + ACTIVATION", x + width * 0.5f, y + d(295f), 6f, Muted, centered = true)
    val state = when {
        equipped -> "EQUIPPED LOADOUT"
        active -> "ACTIVE THIS RUN"
        unlocked -> "EQUIP"
        else -> "UNLOCK ${formatCompact(definition.permanentUnlockCost.toLong())}"
    }
    drawLabel(textMeasurer, state, x + width * 0.5f, y + height - d(34f), 9f, if (equipped) Acid else accent, centered = true, weight = FontWeight.Bold)
}

private fun armoryWeaponColor(id: WeaponId): Color = when (id) {
    WeaponId.FLUX_WAKE -> Cyan
    WeaponId.MORNINGSTAR -> Violet
    WeaponId.PHASE_LATTICE -> Magenta
    WeaponId.NULL_LANCE -> Acid
    WeaponId.GRAVITY_MINES -> Orange
    WeaponId.ION_SWARM -> Cyan
    WeaponId.RIFT_BLADES -> Magenta
    WeaponId.ARC_COIL -> Violet
    WeaponId.QUASAR_CANNON -> Orange
    WeaponId.ENTROPY_FIELD -> Red
    WeaponId.SINGULARITY_SPEAR -> White
    WeaponId.PRISM_RELAY -> Blue
}

private fun armoryWeaponGlyphStyle(id: WeaponId): SystemGlyphStyle = when (id) {
    WeaponId.FLUX_WAKE -> SystemGlyphStyle.DIAGONAL_SLASH
    WeaponId.MORNINGSTAR -> SystemGlyphStyle.ORBITING_NODE
    WeaponId.PHASE_LATTICE -> SystemGlyphStyle.CONCENTRIC_RING
    WeaponId.NULL_LANCE -> SystemGlyphStyle.ARROW_LINE
    WeaponId.GRAVITY_MINES -> SystemGlyphStyle.HEX_ORBIT
    WeaponId.ION_SWARM -> SystemGlyphStyle.DIAMOND_TRIAD
    WeaponId.RIFT_BLADES -> SystemGlyphStyle.TWIN_DIAMONDS
    WeaponId.ARC_COIL -> SystemGlyphStyle.ZIGZAG_RING
    WeaponId.QUASAR_CANNON -> SystemGlyphStyle.RINGED_BEAM
    WeaponId.ENTROPY_FIELD -> SystemGlyphStyle.HEPTAGON_ORBIT
    WeaponId.SINGULARITY_SPEAR -> SystemGlyphStyle.SPEAR_LINE
    WeaponId.PRISM_RELAY -> SystemGlyphStyle.TRIANGLE_NETWORK
}
