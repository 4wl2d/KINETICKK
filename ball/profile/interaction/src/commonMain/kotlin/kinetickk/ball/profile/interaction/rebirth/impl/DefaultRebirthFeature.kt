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
import kinetickk.ball.content.api.BuildPolicy
import kinetickk.ball.content.api.CoreShapeDefinition
import kinetickk.ball.content.api.EnemyDefinition
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.MatrixNodeDefinition
import kinetickk.ball.content.api.RebirthPolicySnapshot
import kinetickk.ball.content.api.RelicDefinition
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.profile.api.ProfilePort
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.interaction.audio.ProfileAudioCue
import kinetickk.ball.profile.interaction.audio.ProfileAudioExecutor
import kinetickk.ball.profile.interaction.rebirth.api.RebirthFeature
import kinetickk.ball.profile.interaction.rebirth.api.RebirthOutput
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.foundation.design.LocalKkRolePalette
import kinetickk.foundation.design.kkIntString
import kinetickk.resource.audio.api.AudioService

/**
 * The Rebirth tier ladder. The catalogs come from the UI catalog (required, so a host cannot silently
 * drop them): for the focused tier the screen shows its mutators, its scaling against the selected
 * tier, the content it opens (weapons, characters, Relics, items, Matrix nodes, enemies), the Reborn
 * card chance and the stored Heirlooms, which resolve against [relics].
 */
class DefaultRebirthFeature(
    private val profilePort: ProfilePort,
    private val rebirthPolicy: RebirthPolicySnapshot,
    audioService: AudioService,
    relics: ImmutableList<RelicDefinition>,
    items: ImmutableList<ItemDefinition>,
    buildPolicy: BuildPolicy,
    weapons: ImmutableList<WeaponDefinition>,
    coreShapes: ImmutableList<CoreShapeDefinition>,
    matrixNodes: ImmutableList<MatrixNodeDefinition>,
    enemies: ImmutableList<EnemyDefinition>,
) : RebirthFeature {
    private val audioExecutor = ProfileAudioExecutor(audioService)
    private val buildContent = RebirthBuildContent(relics, items, buildPolicy, weapons, coreShapes, matrixNodes, enemies)

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
        val direction = rebirthPolicy.profile(tier).displayName.localizedContent(language)
        RebirthAdvanceOverlay(progress.value, theme, kkIntString(tier), direction)
    }

    @Composable
    override fun Content(
        routeToken: Long,
        eligible: Boolean,
        confirmationArmed: Boolean,
        onOutput: (RebirthOutput) -> Unit,
    ) {
        val progress = remember(profilePort, routeToken, eligible, confirmationArmed) {
            profilePort.query(ProfileQuery.GetRebirthProgress)
        }
        // The focused tier is presentation state: it opens on the next challenge (or the selected
        // tier) whenever the route is entered and follows ladder presses and keyboard focus.
        var focusTier by remember(profilePort, routeToken) { mutableIntStateOf(progress.defaultFocusTier(rebirthPolicy)) }
        val renderModelValue = remember(profilePort, rebirthPolicy, progress, eligible, focusTier) {
            progress.toRenderModel(
                rebirthPolicy = rebirthPolicy,
                eligible = eligible,
                matter = profilePort.query(ProfileQuery.GetLoadout).snapshot.economy.matter,
                build = buildContent,
                heirloomIds = profilePort.query(ProfileQuery.GetCollection).collection.heirloomRelicIds,
                focusTier = focusTier,
            )
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
                    is RebirthEffect.Focus -> focusTier = effect.tier
                }
            }
        }

        RebirthContent(renderModelValue, confirmationArmed, textScale, ::dispatch)
    }
}
