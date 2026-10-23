// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.nucleus

import kinetickk.ball.profile.api.FRAME_RATE_CAP_OPTIONS
import kinetickk.ball.profile.api.GameAction
import kinetickk.ball.profile.api.INTERFACE_SCALE_OPTIONS
import kinetickk.ball.profile.api.KeyBindings
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.ball.profile.api.ProfilePreferenceAdjustment
import kinetickk.ball.profile.api.ProfilePulse
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.api.ProfileRejection
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.ProfileSnapshot
import kinetickk.ball.profile.api.ProfileSnapshotReadResult
import kinetickk.ball.profile.api.ProfileWriteResult
import kinetickk.ball.profile.api.REFRESH_RATE_RANGE
import kinetickk.ball.profile.api.ReactionEffectsLevel
import kinetickk.ball.profile.api.WindowMode
import kinetickk.ball.profile.api.WindowSizePreset
import kinetickk.foundation.common.input.BindableKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ProfileDisplayPreferencesTest {
    @Test
    fun everyDisplayChoiceIsAcceptedOnceAndTheCurrentChoiceIsNoChange() {
        val state = readyState()
        val current = state.profile.preferences
        val choices = WindowMode.entries.map { mode ->
            ProfilePreferenceAdjustment.SetWindowMode(mode) to current.copy(windowMode = mode)
        } + WindowSizePreset.entries.map { preset ->
            ProfilePreferenceAdjustment.SetWindowSize(preset) to current.copy(windowSize = preset)
        } + FRAME_RATE_CAP_OPTIONS.map { fps ->
            ProfilePreferenceAdjustment.SetFrameRateCap(fps) to current.copy(frameRateCap = fps)
        } + listOf(0, REFRESH_RATE_RANGE.first, 144, REFRESH_RATE_RANGE.last).map { hz ->
            ProfilePreferenceAdjustment.SetRefreshRate(hz) to current.copy(refreshRateHz = hz)
        } + INTERFACE_SCALE_OPTIONS.map { percent ->
            ProfilePreferenceAdjustment.SetInterfaceScale(percent) to current.copy(interfaceScalePercent = percent)
        } + ReactionEffectsLevel.entries.map { level ->
            ProfilePreferenceAdjustment.SetReactionEffects(level) to current.copy(reactionEffects = level)
        }
        choices.forEach { (adjustment, expected) ->
            val decision = decide(state, adjustment)
            assertEquals(decision, decide(state, adjustment), "decisions are deterministic")
            if (expected == current) {
                assertEquals(ProfileRejection.NoChange, assertIs<ProfileDecision.Rejected>(decision).reason, adjustment.toString())
            } else {
                val frame = assertIs<ProfileDecision.Accepted>(decision).frame
                assertEquals(expected, frame.nextState.profile.preferences, adjustment.toString())
                assertEquals(state.profile.copy(preferences = expected), persisted(frame))
                assertEquals(expected, ProfileNucleus.query(frame.nextState, ProfileQuery.GetPreferences).preferences)
            }
        }
        assertEquals(readyState(), state, "the committed state is untouched")
    }

    @Test
    fun displayTogglesFlipOneFlagAndASecondToggleRestoresIt() {
        val state = readyState()
        val defaults = state.profile.preferences
        listOf(
            ProfilePreferenceAdjustment.ToggleVsync to defaults.copy(vsync = false),
            ProfilePreferenceAdjustment.ToggleImmersive to defaults.copy(immersive = true),
            ProfilePreferenceAdjustment.ToggleShowFps to defaults.copy(showFps = true),
            ProfilePreferenceAdjustment.TogglePauseOnFocusLoss to defaults.copy(pauseOnFocusLoss = false),
        ).forEach { (toggle, flipped) ->
            val frame = assertIs<ProfileDecision.Accepted>(decide(state, toggle)).frame
            assertEquals(flipped, frame.nextState.profile.preferences, toggle.toString())
            assertEquals(state.profile.copy(preferences = flipped), persisted(frame))
            val restored = assertIs<ProfileDecision.Accepted>(decide(settled(frame), toggle)).frame
            assertEquals(defaults, restored.nextState.profile.preferences)
        }
    }

    @Test
    fun bindingAKeyAnotherActionHoldsSwapsTheTwoAndResetRestoresTheDefaults() {
        val state = readyState()
        // Z belongs to Skill 1, so Dash takes it and Skill 1 takes Dash's Space.
        val swap = assertIs<ProfileDecision.Accepted>(
            decide(state, ProfilePreferenceAdjustment.SetKeyBinding(GameAction.DASH, BindableKey.Z)),
        ).frame
        val swapped = swap.nextState.profile.preferences.keyBindings
        assertEquals(BindableKey.Z, swapped.key(GameAction.DASH))
        assertEquals(BindableKey.SPACE, swapped.key(GameAction.SKILL_1))
        assertEquals(KeyBindings.DEFAULT.keys.toSet(), swapped.keys.toSet(), "a swap binds the same keys")
        assertEquals(swapped, persisted(swap).preferences.keyBindings)
        assertEquals(KeyBindings.DEFAULT, state.profile.preferences.keyBindings, "the committed state is untouched")

        // A free key moves only that action.
        val free = settled(assertIs<ProfileDecision.Accepted>(
            decide(settled(swap), ProfilePreferenceAdjustment.SetKeyBinding(GameAction.APEX, BindableKey.F)),
        ).frame)
        assertEquals(KeyBindings.DEFAULT.with(GameAction.DASH, BindableKey.Z).with(GameAction.APEX, BindableKey.F), free.profile.preferences.keyBindings)
        assertEquals(null, free.profile.preferences.keyBindings.action(BindableKey.V))

        // The current key and resetting defaults change nothing.
        assertEquals(ProfileRejection.NoChange, assertIs<ProfileDecision.Rejected>(
            decide(state, ProfilePreferenceAdjustment.SetKeyBinding(GameAction.PAUSE, BindableKey.P)),
        ).reason)
        assertEquals(ProfileRejection.NoChange, assertIs<ProfileDecision.Rejected>(
            decide(state, ProfilePreferenceAdjustment.ResetKeyBindings),
        ).reason)

        val reset = assertIs<ProfileDecision.Accepted>(decide(free, ProfilePreferenceAdjustment.ResetKeyBindings)).frame
        assertEquals(free.profile.preferences.copy(keyBindings = KeyBindings.DEFAULT), reset.nextState.profile.preferences)
        assertEquals(KeyBindings.DEFAULT, persisted(reset).preferences.keyBindings)
    }

    @Test
    fun loadingKeepsListedDisplayValuesAndRefusesAnyOutsideTheirOptionSets() {
        val progress = defaultPlayerProfile(TestProfilePolicy).copy(economy = PlayerEconomy(matter = 9L, lifetimeMatter = 9L))
        val listed = progress.copy(preferences = PlayerPreferences(
            windowMode = WindowMode.FULLSCREEN,
            windowSize = WindowSizePreset.W1280X720,
            vsync = false,
            frameRateCap = FRAME_RATE_CAP_OPTIONS.last(),
            refreshRateHz = REFRESH_RATE_RANGE.last,
            immersive = true,
            interfaceScalePercent = INTERFACE_SCALE_OPTIONS.first(),
            showFps = true,
            pauseOnFocusLoss = false,
            reactionEffects = ReactionEffectsLevel.REDUCED,
            keyBindings = KeyBindings.DEFAULT.with(GameAction.REROLL, BindableKey.F1),
        ))
        assertEquals(listed, loaded(listed).profile)

        // A value outside its option set makes the snapshot incompatible, like every other malformed preference.
        listOf(
            PlayerPreferences(frameRateCap = 59),
            PlayerPreferences(refreshRateHz = REFRESH_RATE_RANGE.first - 1),
            PlayerPreferences(refreshRateHz = REFRESH_RATE_RANGE.last + 1),
            PlayerPreferences(interfaceScalePercent = 101),
        ).forEach { invalid ->
            assertEquals(defaultPlayerProfile(TestProfilePolicy), loaded(progress.copy(preferences = invalid)).profile, invalid.toString())
        }
    }

    private fun readyState(): ProfileState = ProfileState.initial(TestProfilePolicy, ProfileSnapshotReadResult.Observed(null))

    private fun loaded(profile: PlayerProfile): ProfileState = ProfileState.initial(
        TestProfilePolicy,
        ProfileSnapshotReadResult.Observed(ProfileSnapshot(ProfileRevision(4L), profile)),
    )

    private fun decide(state: ProfileState, adjustment: ProfilePreferenceAdjustment): ProfileDecision =
        ProfileNucleus.decide(state, ProfileNucleusPulse.Intent(ProfilePulse.AdjustPreference(adjustment)))

    /** Completes the frame's write so the next decision starts from a settled State. */
    private fun settled(frame: ProfileAcceptedFrame): ProfileState {
        val persist = assertIs<ProfileOutput.PersistSnapshot>(frame.outputs.single())
        return assertIs<ProfileDecision.Accepted>(ProfileNucleus.decide(
            frame.nextState,
            ProfileNucleusPulse.WriteCompleted(persist.effectRef, ProfileWriteResult.Written(persist.snapshot.revision)),
        )).frame.nextState
    }

    /** The one snapshot the accepted frame asks to persist. */
    private fun persisted(frame: ProfileAcceptedFrame): PlayerProfile =
        assertIs<ProfileOutput.PersistSnapshot>(frame.outputs.single()).snapshot.profile
}
