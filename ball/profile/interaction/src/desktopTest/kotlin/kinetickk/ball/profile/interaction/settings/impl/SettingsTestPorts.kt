// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.settings.impl

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kinetickk.ball.profile.api.CollectionProjection
import kinetickk.ball.profile.api.HangarProjection
import kinetickk.ball.profile.api.HomeProgressProjection
import kinetickk.ball.profile.api.KeyBindings
import kinetickk.ball.profile.api.LOCAL_PROFILE_INSTANCE_ID
import kinetickk.ball.profile.api.LoadoutProjection
import kinetickk.ball.profile.api.MatrixProgressProjection
import kinetickk.ball.profile.api.PersistenceStatusProjection
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.PreferencesProjection
import kinetickk.ball.profile.api.ProfileAcceptance
import kinetickk.ball.profile.api.ProfilePort
import kinetickk.ball.profile.api.ProfilePreferenceAdjustment
import kinetickk.ball.profile.api.ProfilePulse
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.api.ProfileRejection
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.RebirthProgressProjection
import kinetickk.ball.profile.api.RunBootstrapProjection
import kinetickk.ball.profile.interaction.settings.api.SettingsDisplayPort
import kinetickk.ball.profile.interaction.settings.api.SettingsDisplaySupport
import kinetickk.ball.profile.interaction.settings.api.SettingsLiveWindow
import kinetickk.resource.audio.api.AudioPreferences
import kinetickk.resource.audio.api.AudioService
import kinetickk.resource.audio.api.ToneRequest

/**
 * Preferences-only Profile stand-in for Settings tests: applies the adjustments Settings sends the
 * way the Profile Nucleus does (one copy, NoChange when nothing changes) and records them.
 */
internal class SettingsTestProfilePort(initial: PlayerPreferences) : ProfilePort {
    override val instanceId = LOCAL_PROFILE_INSTANCE_ID
    var preferences = initial
    val adjustments = ArrayList<ProfilePreferenceAdjustment>()
    private var revision = ProfileRevision.ZERO

    override fun accept(pulse: ProfilePulse.Business): ProfileAcceptance {
        val adjustment = (pulse as ProfilePulse.AdjustPreference).adjustment
        adjustments += adjustment
        val current = preferences
        val next = when (adjustment) {
            is ProfilePreferenceAdjustment.SetColorVision -> current.copy(colorVision = adjustment.colorVision)
            is ProfilePreferenceAdjustment.SetLanguage -> current.copy(language = adjustment.language)
            is ProfilePreferenceAdjustment.SetWindowMode -> current.copy(windowMode = adjustment.mode)
            is ProfilePreferenceAdjustment.SetWindowSize -> current.copy(windowSize = adjustment.preset)
            ProfilePreferenceAdjustment.ToggleVsync -> current.copy(vsync = !current.vsync)
            is ProfilePreferenceAdjustment.SetFrameRateCap -> current.copy(frameRateCap = adjustment.fps)
            is ProfilePreferenceAdjustment.SetRefreshRate -> current.copy(refreshRateHz = adjustment.hz)
            ProfilePreferenceAdjustment.ToggleImmersive -> current.copy(immersive = !current.immersive)
            is ProfilePreferenceAdjustment.SetInterfaceScale -> current.copy(interfaceScalePercent = adjustment.percent)
            ProfilePreferenceAdjustment.ToggleShowFps -> current.copy(showFps = !current.showFps)
            ProfilePreferenceAdjustment.TogglePauseOnFocusLoss -> current.copy(pauseOnFocusLoss = !current.pauseOnFocusLoss)
            is ProfilePreferenceAdjustment.SetReactionEffects -> current.copy(reactionEffects = adjustment.level)
            is ProfilePreferenceAdjustment.SetKeyBinding ->
                current.copy(keyBindings = current.keyBindings.with(adjustment.action, adjustment.key))
            ProfilePreferenceAdjustment.ResetKeyBindings -> current.copy(keyBindings = KeyBindings.DEFAULT)
            else -> current
        }
        if (next == current) return ProfileAcceptance.Rejected(instanceId, revision, ProfileRejection.NoChange)
        preferences = next
        revision = ProfileRevision(revision.value + 1)
        return ProfileAcceptance.Accepted(instanceId, revision)
    }

    override fun query(query: ProfileQuery.GetPreferences) = PreferencesProjection(instanceId, revision, preferences)
    override fun query(query: ProfileQuery.GetHomeProgress): HomeProgressProjection = error("Unused")
    override fun query(query: ProfileQuery.GetCollection): CollectionProjection = error("Unused")
    override fun query(query: ProfileQuery.GetRunBootstrap): RunBootstrapProjection = error("Unused")
    override fun query(query: ProfileQuery.GetMatrixProgress): MatrixProgressProjection = error("Unused")
    override fun query(query: ProfileQuery.GetHangar): HangarProjection = error("Unused")
    override fun query(query: ProfileQuery.GetLoadout): LoadoutProjection = error("Unused")
    override fun query(query: ProfileQuery.GetRebirthProgress): RebirthProgressProjection = error("Unused")
    override fun query(query: ProfileQuery.GetPersistenceStatus): PersistenceStatusProjection = error("Unused")
}

/**
 * A platform display host stand-in: reports [support] and records what Settings asks of it. Like
 * the real hosts, its live window and browser fullscreen are snapshot state, so a change recomposes.
 */
internal class SettingsTestDisplayPort(
    private val support: SettingsDisplaySupport,
    live: SettingsLiveWindow? = null,
    fullscreen: Boolean = false,
) : SettingsDisplayPort {
    var live: SettingsLiveWindow? by mutableStateOf(live)
    private var fullscreenState by mutableStateOf(fullscreen)
    override val browserFullscreen: Boolean get() = fullscreenState
    var reapplied = 0
        private set

    override fun support(): SettingsDisplaySupport = support
    override fun liveWindow(): SettingsLiveWindow? = live

    override fun setBrowserFullscreen(on: Boolean) {
        fullscreenState = on
    }

    override fun reapply() {
        reapplied++
    }
}

internal object SettingsTestSilentAudio : AudioService {
    override fun updatePreferences(preferences: AudioPreferences) = Unit
    override fun advance(realDeltaSeconds: Float, requests: List<ToneRequest>) = Unit
    override fun ensureUnlocked() = Unit
    override fun close() = Unit
}
