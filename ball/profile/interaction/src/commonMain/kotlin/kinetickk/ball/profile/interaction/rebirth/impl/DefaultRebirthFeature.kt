// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.rebirth.impl

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kinetickk.ball.content.api.RebirthPolicySnapshot
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.profile.api.ProfilePort
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.interaction.audio.ProfileAudioCue
import kinetickk.ball.profile.interaction.audio.ProfileAudioExecutor
import kinetickk.ball.profile.interaction.rebirth.api.RebirthFeature
import kinetickk.ball.profile.interaction.rebirth.api.RebirthOutput
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.foundation.design.LocalKkRolePalette
import kinetickk.foundation.design.kkIntString
import kinetickk.resource.audio.api.AudioService

class DefaultRebirthFeature(
    private val profilePort: ProfilePort,
    private val rebirthPolicy: RebirthPolicySnapshot,
    audioService: AudioService,
) : RebirthFeature {
    private val audioExecutor = ProfileAudioExecutor(audioService)

    /** Counts accepted advances; each new value plays the advance sequence once. */
    private var acceptedAdvances by mutableIntStateOf(0)

    override fun playAcceptedFeedback() {
        audioExecutor.play(ProfileAudioCue.PURCHASE)
        acceptedAdvances += 1
    }

    @Composable
    override fun AcceptedFeedback() {
        val token = acceptedAdvances
        if (token == 0) return
        val progress = remember(token) { Animatable(0f) }
        LaunchedEffect(token) {
            progress.animateTo(1f, tween(REBIRTH_ADVANCE_MS, easing = LinearEasing))
        }
        if (progress.value >= 1f) return
        val language = LocalAppLanguage.current
        val roles = LocalKkRolePalette.current
        val tier = remember(token) {
            profilePort.query(ProfileQuery.GetRebirthProgress).snapshot.progress.level
                .coerceIn(rebirthPolicy.minimumLevel, rebirthPolicy.maximumLevel)
        }
        val theme = remember(tier, roles) { RebirthTheme(tier, roles) }
        val direction = rebirthPolicy.profile(tier).directive.displayName.localizedContent(language)
        RebirthAdvanceOverlay(progress.value, theme, kkIntString(tier), direction)
    }

    @Composable
    override fun Content(
        routeToken: Long,
        eligible: Boolean,
        confirmationArmed: Boolean,
        onOutput: (RebirthOutput) -> Unit,
    ) {
        val renderModelValue = remember(
            profilePort,
            rebirthPolicy,
            routeToken,
            eligible,
            confirmationArmed,
        ) {
            profilePort
                .query(ProfileQuery.GetRebirthProgress)
                .toRenderModel(rebirthPolicy, eligible, profilePort.query(ProfileQuery.GetLoadout).snapshot.economy.matter)
        }
        val textScale = remember(profilePort, routeToken) {
            profilePort.query(ProfileQuery.GetPreferences).preferences.textScale
        }
        fun dispatch(action: RebirthAction) {
            val reduction = RebirthReducer.reduce(
                state = RebirthState(renderModelValue, confirmationArmed),
                action = action,
            )
            reduction.effects.forEach { effect ->
                when (effect) {
                    is RebirthEffect.PlayAudio -> audioExecutor.play(effect.cue)
                    is RebirthEffect.Emit -> onOutput(effect.output)
                }
            }
        }

        RebirthContent(renderModelValue, confirmationArmed, textScale, ::dispatch)
    }
}
