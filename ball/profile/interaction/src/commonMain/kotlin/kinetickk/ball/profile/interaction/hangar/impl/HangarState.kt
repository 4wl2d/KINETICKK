// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.hangar.impl

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.CoreShapeDefinition
import kinetickk.ball.profile.api.HangarProjection
import kinetickk.ball.profile.interaction.audio.ProfileAudioCue
import kinetickk.ball.profile.interaction.hangar.api.HangarOutput
import kinetickk.ball.profile.interaction.hangar.api.HangarRenderModel
import kinetickk.ball.profile.interaction.hangar.api.HangarShapeRenderModel
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.toImmutableList

internal sealed interface HangarAction {
    /** Hover or keyboard focus inspects a character. */
    data class Inspect(val shape: CoreShape) : HangarAction

    /** A tile press or Enter: a press on the inspected tile selects it, any other press inspects. */
    data class Activate(val shape: CoreShape) : HangarAction

    /** The detail panel's Select button: chooses the inspected character. */
    data object SelectInspected : HangarAction

    data object Back : HangarAction
}

/** Hangar presentation state: the Profile-backed [model] and the inspected character. */
internal data class HangarState(
    val model: HangarRenderModel,
    val inspected: CoreShape = model.selected,
) {
    val inspectedShape: HangarShapeRenderModel? get() = model.shape(inspected) ?: model.shapes.firstOrNull()

    /** The inspected character can become the next run's character. */
    val canSelect: Boolean get() = inspectedShape?.let { it.unlocked && it.shape != model.selected } == true
}

internal sealed interface HangarUiEffect {
    /** Ask Profile to select [shape]; its Nucleus decides whether it is unlocked. */
    data class Select(val shape: CoreShape) : HangarUiEffect
    data class PlayAudio(val cue: ProfileAudioCue) : HangarUiEffect
    data class Emit(val output: HangarOutput) : HangarUiEffect
}

internal data class HangarReduction(
    val state: HangarState,
    val effects: List<HangarUiEffect> = emptyList(),
)

internal object HangarReducer {
    fun reduce(state: HangarState, action: HangarAction): HangarReduction = when (action) {
        is HangarAction.Inspect -> HangarReduction(if (state.inspected == action.shape) state else state.copy(inspected = action.shape))
        is HangarAction.Activate -> if (state.inspected == action.shape) {
            reduce(state, HangarAction.SelectInspected)
        } else {
            HangarReduction(state.copy(inspected = action.shape))
        }
        HangarAction.SelectInspected -> {
            val shape = state.inspectedShape
            if (shape == null || !state.canSelect) HangarReduction(state)
            else HangarReduction(state, listOf(HangarUiEffect.Select(shape.shape)))
        }
        HangarAction.Back -> HangarReduction(
            state = state,
            effects = listOf(
                HangarUiEffect.PlayAudio(ProfileAudioCue.UI_CLICK),
                HangarUiEffect.Emit(HangarOutput.Back),
            ),
        )
    }

    /** Applies the Profile's answer to a selection: the refreshed model, still inspecting the same character. */
    fun selected(state: HangarState, model: HangarRenderModel): HangarState = state.copy(model = model)
}

/** The shortest trait bar: the roster's smallest value still draws a quarter bar. */
internal const val HANGAR_BAR_FLOOR = 0.25f

/**
 * Maps the Profile's Hangar projection onto the content roster. Trait bars are normalized over the
 * roster: mass by its multiplier, integrity as 100 plus the bonus, handling as the inverse drag and
 * heat as the inverse Dash heat multiplier, each placed between the roster's smallest value (a
 * quarter bar, so every bar stays visible) and its largest (a full bar).
 */
internal fun HangarProjection.toRenderModel(
    definitions: ImmutableList<CoreShapeDefinition>,
    matter: Long,
): HangarRenderModel {
    val mass = definitions.map { it.traits.massMultiplier }
    val integrity = definitions.map { 100f + it.traits.integrityBonus }
    val handling = definitions.map { 1f / it.traits.dragMultiplier }
    val heat = definitions.map { 1f / it.traits.dashHeatMultiplier }
    fun bar(values: List<Float>, index: Int): Float {
        val low = values.min()
        val span = values.max() - low
        return if (span <= 0f) 1f else (HANGAR_BAR_FLOOR + (1f - HANGAR_BAR_FLOOR) * (values[index] - low) / span).coerceIn(0f, 1f)
    }
    return HangarRenderModel(
        shapes = definitions.mapIndexed { index, definition ->
            val progress = shapes.firstOrNull { it.shape == definition.id }
            HangarShapeRenderModel(
                definition = definition,
                unlocked = progress?.unlocked ?: (definition.unlockTarget == 0),
                progress = progress?.progress ?: 0L,
                target = progress?.target ?: definition.unlockTarget.toLong(),
                massBar = bar(mass, index),
                integrityBar = bar(integrity, index),
                handlingBar = bar(handling, index),
                heatBar = bar(heat, index),
                ascendant = definition.id in ascendedCharacters,
            )
        }.toImmutableList(),
        selected = selected,
        matter = matter,
    )
}
