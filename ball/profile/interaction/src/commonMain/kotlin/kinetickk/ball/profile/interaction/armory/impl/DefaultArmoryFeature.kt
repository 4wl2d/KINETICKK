// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.armory.impl

import kinetickk.foundation.common.localization.text
import kinetickk.ball.profile.interaction.localization.ProfileText
import kinetickk.ball.content.api.localizedContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import kinetickk.ball.profile.interaction.*
import kinetickk.foundation.design.LocalAppLanguage
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
import androidx.compose.ui.graphics.Color
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

class DefaultArmoryFeature(
    private val profilePort: ProfilePort,
    private val weapons: ImmutableList<WeaponDefinition>,
    private val weaponMasteries: ImmutableList<WeaponMastery>,
    audioService: AudioService,
) : ArmoryFeature {
    private val reducer = ArmoryReducer(weapons)
    private val audioExecutor = ProfileAudioExecutor(audioService)

    @Composable
    override fun Content(activeRunWeapon: WeaponId?, onOutput: (ArmoryOutput) -> Unit) {
        val language = LocalAppLanguage.current
        val weaponMasteryProgressionLabel = remember(weaponMasteries, language) {
            weaponMasteries.drop(1).joinToString("  ") {
                language.text(ProfileText.MasteryLevel, it.minimumLevel, it.displayLabel.localizedContent(language))
            }
        }
        var pageValue by rememberSaveable { mutableIntStateOf(0) }
        var loadoutProjectionValue by remember(profilePort) {
            mutableStateOf(profilePort.query(ProfileQuery.GetLoadout))
        }
        var renderTimeSecondsValue by remember { mutableFloatStateOf(0f) }
        val model = reducer.renderModel(loadoutProjectionValue.snapshot, activeRunWeapon)
        val textScale = profilePort.query(ProfileQuery.GetPreferences).preferences.textScale

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
                renderTimeSecondsValue += selectArmoryPresentationFrameDeltaSeconds(
                    (frame - previousFrame) / 1_000_000_000f,
                )
                previousFrame = frame
            }
        }

        ProfilePanel(language.text(ProfileText.ArmoryTitle), language.text(ProfileText.ArmorySummary,
            weapons.size, model.unlockedWeapons.size, formatCompact(model.totalMatter, language)),
            textScale, "profile-armory", onBack = { dispatch(ArmoryAction.Back) }, contentKey = pageValue,
            footer = {
                ProfileButton("‹", textScale, "profile-armory-previous", enabled = pageValue > 0, description = language.text(ProfileText.PreviousPage)) { dispatch(ArmoryAction.PreviousPage) }
                ProfileLabel("${pageValue + 1}/${reducer.maxPage + 1}", textScale, Muted, size = 10f)
                ProfileButton("›", textScale, "profile-armory-next", enabled = pageValue < reducer.maxPage, description = language.text(ProfileText.NextPage)) { dispatch(ArmoryAction.NextPage) }
            }) { wide ->
            val pageWeapons = armoryPageSlice(weapons, pageValue)
            if (wide) Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                pageWeapons.forEach { definition ->
                    WeaponCard(model, definition, textScale, renderTimeSecondsValue,
                        Modifier.weight(1f)) { dispatch(ArmoryAction.SelectWeapon(definition.id)) }
                }
            } else pageWeapons.forEach { definition ->
                WeaponCard(model, definition, textScale, renderTimeSecondsValue) {
                    dispatch(ArmoryAction.SelectWeapon(definition.id))
                }
            }
            ProfileLabel(weaponMasteryProgressionLabel, textScale, Muted, size = 9f)
        }
    }
}

internal const val MAX_ARMORY_PRESENTATION_FRAME_DELTA_SECONDS: Float = 0.1f
internal fun selectArmoryPresentationFrameDeltaSeconds(frameDeltaSeconds: Float): Float =
    frameDeltaSeconds.coerceAtMost(MAX_ARMORY_PRESENTATION_FRAME_DELTA_SECONDS)

@Composable
private fun WeaponCard(model: ArmoryRenderModel, definition: WeaponDefinition, scale: Float,
    renderTime: Float, modifier: Modifier = Modifier, onSelect: () -> Unit) {
    val language = LocalAppLanguage.current
    val unlocked = definition.id in model.unlockedWeapons
    val equipped = model.selectedWeapon == definition.id
    val active = model.activeRunWeapon == definition.id
    val affordable = unlocked || model.totalMatter >= definition.permanentUnlockCost
    val accent = if (unlocked) armoryWeaponColor(definition.id) else Muted
    Column(modifier.semantics { selected = equipped }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Canvas(Modifier.size(48.dp)) { drawSystemGlyph(armoryWeaponGlyphStyle(definition.id), center, 16.dp.toPx(), renderTime, accent) }
            ProfileLabel(definition.name.localizedContent(language), scale, bold = true, modifier = Modifier.weight(1f))
            if (equipped) Canvas(Modifier.size(18.dp)) { drawInterfaceGlyph(InterfaceGlyph.CHECK, center, 6.dp.toPx(), Cyan) }
        }
        ProfileLabel(definition.description.localizedContent(language), scale, Muted, size = 11f)
        val label = when {
            equipped -> language.text(ProfileText.Equipped)
            active -> language.text(ProfileText.ActiveRun)
            unlocked -> language.text(ProfileText.Equip)
            else -> language.text(ProfileText.Unlock, formatCompact(definition.permanentUnlockCost.toLong(), language))
        }
        ProfileButton(label, scale, "profile-armory-equip-${definition.id}", Modifier.fillMaxWidth(), enabled = affordable && !equipped, accent = Cyan, onClick = onSelect)
        ProfileDivider()
    }
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
