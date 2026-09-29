// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.armory.impl

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.WeaponMastery
import kinetickk.ball.profile.api.ProfileAcceptance
import kinetickk.ball.profile.api.ProfilePort
import kinetickk.ball.profile.api.ProfilePulse
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.interaction.armory.api.ArmoryFeature
import kinetickk.ball.profile.interaction.armory.api.ArmoryOutput
import kinetickk.ball.profile.interaction.audio.ProfileAudioCue
import kinetickk.ball.profile.interaction.audio.ProfileAudioExecutor
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
        var loadoutProjectionValue by remember(profilePort) {
            mutableStateOf(profilePort.query(ProfileQuery.GetLoadout))
        }
        val model = reducer.renderModel(loadoutProjectionValue.snapshot, activeRunWeapon)
        // The inspected weapon is screen-local presentation state; it opens on the starter.
        var inspectedValue by rememberSaveable { mutableStateOf(model.selectedWeapon.name) }
        val state = ArmoryViewState(
            WeaponId.entries.firstOrNull { it.name == inspectedValue && weapons.any { weapon -> weapon.id == it } }
                ?: weapons.firstOrNull { it.id == model.selectedWeapon }?.id
                ?: weapons.first().id,
        )
        val textScale = profilePort.query(ProfileQuery.GetPreferences).preferences.textScale
        val gridScroll = rememberScrollState()
        val holder = remember { ArmoryLayoutHolder() }
        val gridStepper = rememberArmoryGridStepper(gridScroll)

        fun dispatch(action: ArmoryAction) {
            val reduction = reducer.reduce(state, model, action)
            inspectedValue = reduction.state.inspected.name
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
                    is ArmoryEffect.ScrollGrid -> {
                        val layout = holder.layout ?: return@forEach
                        val target = armoryGridScrollTarget(gridScroll.value.toFloat(), gridScroll.maxValue.toFloat(),
                            layout.gridViewport.height, layout.rowPitch, effect.forward)
                        gridStepper.step(target.toInt())
                    }
                }
            }
        }

        ArmoryContent(model, weapons, weaponMasteries, state, textScale, gridScroll, holder, ::dispatch)
    }
}

/**
 * Page steps for the weapon grid. Each step starts its own animation to its target: the effect
 * is keyed on the step count, so a step whose target equals an earlier one (whose animation a
 * drag or wheel scroll cancelled) still moves the grid.
 */
internal class ArmoryGridStepper {
    var steps by mutableIntStateOf(0)
        private set
    var target = 0
        private set

    fun step(target: Int) {
        this.target = target
        steps += 1
    }
}

@Composable
internal fun rememberArmoryGridStepper(gridScroll: ScrollState): ArmoryGridStepper {
    val stepper = remember { ArmoryGridStepper() }
    val steps = stepper.steps
    LaunchedEffect(steps) {
        if (steps > 0) gridScroll.animateScrollTo(stepper.target)
    }
    return stepper
}
