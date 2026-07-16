// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.features.settings.nucleus.transition

import kinetickk.application.runtime.Accepted
import kinetickk.application.runtime.Rejected
import kinetickk.features.settings.nucleus.domain.DamageNumberFormat
import kinetickk.features.settings.nucleus.domain.DamageNumberSize
import kinetickk.features.settings.nucleus.domain.ParticleDensity
import kinetickk.features.settings.nucleus.domain.SettingsAdjustmentDirection.DECREASE
import kinetickk.features.settings.nucleus.domain.SettingsAdjustmentDirection.INCREASE
import kinetickk.features.settings.nucleus.domain.SettingsValues
import kinetickk.features.settings.nucleus.protocol.SettingsDecisionContext
import kinetickk.features.settings.nucleus.protocol.SettingsIntent
import kinetickk.features.settings.nucleus.protocol.SettingsOperationId
import kinetickk.features.settings.nucleus.protocol.SettingsOutputKind
import kinetickk.features.settings.nucleus.protocol.SettingsProjectionOutput
import kinetickk.features.settings.nucleus.protocol.SettingsProjectionPayload
import kinetickk.features.settings.nucleus.protocol.SettingsRejection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SettingsNucleusTest {
    private val nucleus = SettingsNucleus()

    @Test
    fun muteChangesBothAudioFlagsAtomically() {
        val muted = decide(SettingsValues(), SettingsIntent.MuteToggled)

        assertFalse(muted.soundEnabled)
        assertFalse(muted.musicEnabled)

        val enabled = decide(muted, SettingsIntent.MuteToggled, operation = 2uL)
        assertTrue(enabled.soundEnabled)
        assertTrue(enabled.musicEnabled)

        val mixed = decide(
            SettingsValues(soundEnabled = true, musicEnabled = false),
            SettingsIntent.MuteToggled,
            operation = 3uL,
        )
        assertFalse(mixed.soundEnabled)
        assertFalse(mixed.musicEnabled)
    }

    @Test
    fun percentageAndDiscreteAdjustmentsPreserveLegacyStepsAndBounds() {
        assertEquals(
            0.66f,
            decide(SettingsValues(), SettingsIntent.MasterVolumeAdjusted(INCREASE)).masterVolume,
        )
        assertEquals(
            1.26f,
            decide(SettingsValues(), SettingsIntent.TextScaleAdjusted(INCREASE)).textScale,
        )
        assertEquals(
            1.35f,
            decide(SettingsValues(), SettingsIntent.SimulationSpeedAdjusted(INCREASE)).simulationSpeed,
        )
        assertEquals(
            ParticleDensity.HIGH,
            decide(SettingsValues(), SettingsIntent.ParticleDensityAdjusted(INCREASE)).particleDensity,
        )
        assertEquals(
            DamageNumberSize.LARGE,
            decide(SettingsValues(), SettingsIntent.DamageNumberSizeAdjusted(INCREASE)).damageNumberSize,
        )
        assertEquals(
            DamageNumberFormat.FULL,
            decide(SettingsValues(), SettingsIntent.DamageNumberFormatAdjusted(INCREASE)).damageNumberFormat,
        )
        assertEquals(
            100,
            decide(
                SettingsValues(),
                SettingsIntent.DamageNumberTierThresholdAdjusted(INCREASE),
            ).damageNumberTierThreshold,
        )

        val minimum = SettingsValues(masterVolume = 0f, textScale = 1f, simulationSpeed = 0.75f)
        assertEquals(0f, decide(minimum, SettingsIntent.MasterVolumeAdjusted(DECREASE)).masterVolume)
        assertEquals(1f, decide(minimum, SettingsIntent.TextScaleAdjusted(DECREASE)).textScale)
        assertEquals(
            0.75f,
            decide(minimum, SettingsIntent.SimulationSpeedAdjusted(DECREASE)).simulationSpeed,
        )
    }

    @Test
    fun normalizationQuarantinesNonFiniteValuesAndClampsNumericFields() {
        val normalized = SettingsValues(
            masterVolume = Float.NaN,
            simulationSpeed = Float.POSITIVE_INFINITY,
            textScale = -20f,
            damageNumberTierThreshold = Int.MAX_VALUE,
        ).normalized()

        assertEquals(SettingsValues.DEFAULT_MASTER_VOLUME, normalized.masterVolume)
        assertEquals(SettingsValues.DEFAULT_SIMULATION_SPEED, normalized.simulationSpeed)
        assertEquals(SettingsValues.MIN_TEXT_SCALE, normalized.textScale)
        assertEquals(100_000_000, normalized.damageNumberTierThreshold)
    }

    @Test
    fun decisionIsPureDeterministicAndBuildsOneCanonicalProjection() {
        val state = SettingsBallState(SettingsValues())
        val context = context(41uL)
        val intent = SettingsIntent.ScreenShakeToggled

        val first = nucleus.decide(state, intent, context)
        val second = nucleus.decide(state, intent, context)

        assertEquals(first, second)
        val decision = assertIs<Accepted<SettingsBallState, SettingsProjectionOutput>>(first).decision
        assertEquals(1, decision.nextState.transitionSteps)
        val output = decision.outputs.single()
        assertEquals(0u, output.sourceOrdinal)
        assertEquals(SettingsOutputKind.SETTINGS_CHANGED, output.semanticHandle.outputKind)
        assertEquals(context.operationId, output.semanticHandle.operationId)
        val changed = assertIs<SettingsProjectionPayload.SettingsChanged>(output.payload)
        assertFalse(changed.settings.screenShake)
    }

    @Test
    fun invalidContextRejectsWithoutAStateCandidate() {
        val result = nucleus.decide(
            SettingsBallState(SettingsValues()),
            SettingsIntent.SoundToggled,
            context(1uL).copy(transitionArtifact = "wrong"),
        )

        val rejection = assertIs<Rejected>(result)
        assertEquals(
            SettingsRejection.InvalidContext("transitionArtifact", "unsupported artifact"),
            rejection.rejection,
        )
    }

    private fun decide(
        values: SettingsValues,
        intent: SettingsIntent,
        operation: ULong = 1uL,
    ): SettingsValues {
        val result = nucleus.decide(SettingsBallState(values), intent, context(operation))
        return assertIs<Accepted<SettingsBallState, SettingsProjectionOutput>>(result)
            .decision
            .nextState
            .values
    }

    private fun context(operation: ULong) = SettingsDecisionContext(
        operationId = SettingsOperationId(operation),
    )
}
